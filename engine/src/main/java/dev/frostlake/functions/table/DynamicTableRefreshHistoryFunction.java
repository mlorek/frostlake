/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.functions.table;

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.DynamicTableRefreshHistory;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.DynamicTableRefreshInput;
import dev.frostlake.metastore.model.DynamicTableRefreshRecord;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.values.VariantValue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY: a row for each refresh of each dynamic table of the account, by
 * table, the last name first, and each table's latest refresh first, as the account lists them (live-verified): the
 * table's names, SUCCEEDED, when the refresh ran and the data it read, the target lag in seconds, the dynamic table it
 * read last, the rows and micro-partitions it changed, what it did (INCREMENTAL, FULL or NO_DATA) and what started it
 * (CREATION, MANUAL or SCHEDULED), the warehouse it ran on and the inputs whose data changed for it, each with its
 * partitions added and removed. Each refresh is listed under the names its table held when it ran, whatever became of
 * the table since (see the catalog's DynamicTableRefreshHistory). NAME names one table as a statement names it,
 * NAME_PREFIX the tables whose qualified name starts so in any case, RESULT_LIMIT caps the rows (100, or that many up
 * to 10000), ERROR_ONLY keeps the failed refreshes, and DATA_TIMESTAMP_START and DATA_TIMESTAMP_END bound the data
 * timestamps: the end is now when it is not given, the start a day before the end, and no refresh whose data timestamp
 * is more than seven days old is listed (documented).
 */
public class DynamicTableRefreshHistoryFunction extends TableFunction {

    /** How a data timestamp is written inside an OBJECT cell: in the session's zone, to the millisecond. */
    private static final DateTimeFormatter DATA_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS xx");

    /** How far before its end a range starts when DATA_TIMESTAMP_START is not given. */
    private static final Duration DEFAULT_RANGE = Duration.ofDays(1);

    private final Catalog catalog;

    public DynamicTableRefreshHistoryFunction(final Catalog catalog) {
        super("DYNAMIC_TABLE_REFRESH_HISTORY");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Every argument is optional.
    }

    /** The listing's columns are fixed, so a query over it is planned with their types before any row is read. */
    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return columns();
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        final String name = namedArgs.get("NAME") == null ? null : TaskGraphRuns.text(namedArgs.get("NAME"));
        final String prefix = namedArgs.get("NAME_PREFIX") == null ? null
            : TaskGraphRuns.text(namedArgs.get("NAME_PREFIX")).toUpperCase(Locale.ROOT);
        final Object limitWritten = namedArgs.get("RESULT_LIMIT");
        // A limit of none or below none is the default one (live-verified).
        final int written = limitWritten instanceof Number && ((Number) limitWritten).doubleValue() < 1 ? 0
            : TaskGraphRuns.limit(limitWritten, 100);
        final int limit = written == 0 ? 100 : written;
        final Instant now = Instant.now();
        final Instant givenTo = instant(namedArgs.get("DATA_TIMESTAMP_END"));
        final Instant to = givenTo == null ? now : givenTo;
        final Instant givenFrom = instant(namedArgs.get("DATA_TIMESTAMP_START"));
        final Instant from = givenFrom == null ? to.minus(DEFAULT_RANGE) : givenFrom;
        final Instant kept = now.minus(DynamicTableRefreshHistory.KEPT_FOR);
        final String named = name == null ? null : qualified(name);
        final List<DynamicTableRefreshRecord> found = new ArrayList<>();
        if (!Boolean.TRUE.equals(namedArgs.get("ERROR_ONLY"))) {
            // A refresh that fails is no refresh the engine keeps: only the ones that ran are listed.
            for (final DynamicTableRefreshRecord record : catalog.getDynamicTableRefreshHistory().records()) {
                final String qualified = QualifiedName.join(record.getDatabase(), record.getSchema(),
                    record.getTable());
                if (named != null ? !named.equals(qualified)
                        : prefix != null && !qualified.toUpperCase(Locale.ROOT).startsWith(prefix)) {
                    continue;
                }
                if (!record.getStart().isBefore(from) && !record.getStart().isAfter(to)
                        && !record.getStart().isBefore(kept)) {
                    found.add(record);
                }
            }
        }
        // By table, the last name first, and each table's latest refresh first: a RESULT_LIMIT keeps the first rows
        // of that order, so a table of a later name holds its rows against a later refresh of another (live-verified).
        Collections.sort(found, new Comparator<DynamicTableRefreshRecord>() {
            @Override
            public int compare(final DynamicTableRefreshRecord left, final DynamicTableRefreshRecord right) {
                final int byTable = QualifiedName.join(right.getDatabase(), right.getSchema(), right.getTable())
                    .compareTo(QualifiedName.join(left.getDatabase(), left.getSchema(), left.getTable()));
                return byTable != 0 ? byTable : right.getStart().compareTo(left.getStart());
            }
        });
        final List<Row> rows = new ArrayList<>();
        for (int i = 0; i < found.size() && i < limit; i++) {
            rows.add(row(found.get(i)));
        }
        return new ResultSet(columns(), rows);
    }

    /** A table NAME names, qualified from the session's database and schema as a statement's name is. */
    private String qualified(final String name) {
        final List<String> canonical = new ArrayList<>(Arrays.asList(SqlIdentifiers.canonicalTextParts(name)));
        if (canonical.size() < 2) {
            canonical.add(0, catalog.getCurrentSchema());
        }
        if (canonical.size() < 3) {
            canonical.add(0, catalog.getCurrentDatabase());
        }
        return QualifiedName.join(canonical.toArray(new String[0]));
    }

    private static Row row(final DynamicTableRefreshRecord record) {
        final Long lag = lagSeconds(record.getTargetLag());
        final long[] changed = record.getChanged();
        final boolean noData = "NO_DATA".equals(record.getAction());
        return new Row(Arrays.<Object>asList(
            record.getTable(), record.getSchema(), record.getDatabase(), "SUCCEEDED", "SUCCESS", null,
            record.getQueryId(), ShowResultHelpers.createdOn(record.getStart()),
            ShowResultHelpers.createdOn(record.getGraphValidFrom()), ShowResultHelpers.createdOn(record.getStart()),
            ShowResultHelpers.createdOn(record.getEnd()),
            lag == null ? null : ShowResultHelpers.createdOn(record.getStart().plusSeconds(lag.longValue())), lag,
            QualifiedName.join(record.getDatabase(), record.getSchema(), record.getTable()), dependency(record),
            statistics(record, changed), record.getAction(), record.getTrigger().name(), record.getWarehouse(),
            noData ? null : inputs(record), null, null, null));
    }

    /** The dynamic table the refresh read last, with its data timestamp; none when it read only tables. */
    private static VariantValue dependency(final DynamicTableRefreshRecord record) {
        if (record.getDependency() == null) {
            return VariantValue.of("{}");
        }
        final String timestamp = record.getDependencyTimestamp() == null ? null
            : ShowResultHelpers.createdOn(record.getDependencyTimestamp()).format(DATA_TIMESTAMP);
        return VariantValue.of("{\"data_timestamp\":" + (timestamp == null ? "null" : json(timestamp))
            + ",\"qualified_name\":" + json(record.getDependency()) + "}");
    }

    /** What the refresh did: its times, the partitions it added and removed, the rows it changed. */
    private static VariantValue statistics(final DynamicTableRefreshRecord record, final long[] changed) {
        final long executed = Math.max(0L, record.getEnd().toEpochMilli() - record.getStart().toEpochMilli());
        return VariantValue.of("{\"compilationTimeMs\":0,\"executionTimeMs\":" + executed
            + ",\"numAddedPartitions\":" + changed[3] + ",\"numCopiedRows\":" + changed[1]
            + ",\"numDeletedRows\":" + changed[2] + ",\"numInsertedRows\":" + changed[0]
            + ",\"numRemovedPartitions\":" + changed[4] + ",\"queuedTimeMs\":0}");
    }

    /**
     * The tables and dynamic tables the refresh read whose data changed for it, each by its kind and name, with the
     * micro-partitions added since the refresh before and the rows they hold, and the ones removed and the rows they
     * held.
     */
    private static VariantValue inputs(final DynamicTableRefreshRecord record) {
        final StringBuilder json = new StringBuilder("[");
        for (final DynamicTableRefreshInput input : record.getInputs()) {
            json.append(json.length() > 1 ? "," : "").append("{\"kind\":").append(json(input.getKind()))
                .append(",\"name\":").append(json(input.getName()))
                .append(",\"statistics\":{\"numAddedPartitions\":").append(input.getAddedPartitions())
                .append(",\"numRegisteredRows\":").append(input.getRegisteredRows())
                .append(",\"numRemovedPartitions\":").append(input.getRemovedPartitions())
                .append(",\"numUnregisteredRows\":").append(input.getUnregisteredRows()).append("}}");
        }
        return VariantValue.of(json.append(']').toString());
    }

    private static String json(final String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A target lag in seconds, {@code '1 hour'} 3600; none for DOWNSTREAM. */
    private static Long lagSeconds(final String lag) {
        if (lag == null) {
            return null;
        }
        final String[] parts = lag.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (parts.length != 2) {
            return null;
        }
        final long count;
        try {
            count = Long.parseLong(parts[0]);
        } catch (final NumberFormatException notACount) {
            return null;
        }
        final long unit = parts[1].startsWith("second") ? 1L : parts[1].startsWith("minute") ? 60L
            : parts[1].startsWith("hour") ? 3600L : parts[1].startsWith("day") ? 86400L : -1L;
        return unit < 0 ? null : Long.valueOf(count * unit);
    }

    /** A bound on the data timestamps, or none. */
    private static Instant instant(final Object value) {
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toInstant();
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toInstant();
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant();
        }
        return null;
    }

    /** The account's 23 columns in its order and types: the texts unsized, the times TIMESTAMP_LTZ. */
    private static List<ResultSetColumn> columns() {
        return DeclaredColumnTypes.typed(Arrays.asList(
            new ResultSetColumn("NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("SCHEMA_NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("DATABASE_NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("STATE", TaskGraphRuns.TEXT),
            new ResultSetColumn("STATE_CODE", TaskGraphRuns.TEXT),
            new ResultSetColumn("STATE_MESSAGE", TaskGraphRuns.TEXT),
            new ResultSetColumn("QUERY_ID", TaskGraphRuns.TEXT),
            new ResultSetColumn("DATA_TIMESTAMP", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("GRAPH_HISTORY_VALID_FROM", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("REFRESH_START_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("REFRESH_END_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("COMPLETION_TARGET", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("TARGET_LAG_SEC", TaskGraphRuns.WHOLE_NUMBER),
            new ResultSetColumn("QUALIFIED_NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("LAST_COMPLETED_DEPENDENCY", new ObjectType()),
            new ResultSetColumn("STATISTICS", new ObjectType()),
            new ResultSetColumn("REFRESH_ACTION", TaskGraphRuns.TEXT),
            new ResultSetColumn("REFRESH_TRIGGER", TaskGraphRuns.TEXT),
            new ResultSetColumn("WAREHOUSE", TaskGraphRuns.TEXT),
            new ResultSetColumn("INPUTS_WITH_CHANGED_DATA", ArrayType.ARRAY),
            new ResultSetColumn("REINIT_REASON", TaskGraphRuns.TEXT),
            new ResultSetColumn("EXECUTE_AS_USER", TaskGraphRuns.TEXT),
            new ResultSetColumn("SECONDARY_ROLE_NAMES", TaskGraphRuns.TEXT)));
    }
}
