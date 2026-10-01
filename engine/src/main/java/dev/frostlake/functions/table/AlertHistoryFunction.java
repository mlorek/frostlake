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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.AlertExecution;
import dev.frostlake.metastore.model.AlertState;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The INFORMATION_SCHEMA.ALERT_HISTORY table function: one row per evaluation of an alert in the account, plus
 * a SCHEDULED row for the next evaluation of every started alert due within eight days, newest scheduled time
 * first.
 *
 * <p>All arguments are optional and named: {@code SCHEDULED_TIME_RANGE_START} and
 * {@code SCHEDULED_TIME_RANGE_END} bound the scheduled time, {@code RESULT_LIMIT} (at most 10000, default 100;
 * below one it answers nothing) caps the rows, and {@code ALERT_NAME} keeps the evaluations of the alerts of that
 * unqualified name, matched case-insensitively.
 */
public class AlertHistoryFunction extends TableFunction {

    /** How far ahead a pending evaluation is listed. */
    private static final Duration LOOK_AHEAD = Duration.ofDays(8);
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 10000;

    private final Catalog catalog;

    /** @param catalog the catalog whose alerts are reported */
    public AlertHistoryFunction(final Catalog catalog) {
        super("ALERT_HISTORY");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Every argument is optional; values are checked where they are read.
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        final String alertName = text(namedArgs.get("ALERT_NAME"));
        final Instant start = instant(namedArgs.get("SCHEDULED_TIME_RANGE_START"));
        final Instant end = instant(namedArgs.get("SCHEDULED_TIME_RANGE_END"));
        final int limit = limit(namedArgs.get("RESULT_LIMIT"));
        final List<Object[]> entries = new ArrayList<>();
        final Instant horizon = Instant.now().plus(LOOK_AHEAD);
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Alert alert : schema.getAlerts()) {
                    if (alertName != null && !alertName.equalsIgnoreCase(alert.getName())) {
                        continue;
                    }
                    final Instant next = alert.getNextScheduledTime();
                    if (alert.getState() == AlertState.STARTED && next != null && !next.isAfter(horizon)) {
                        entries.add(new Object[] {next, database.getName(), schema.getName(), alert, null});
                    }
                    for (final AlertExecution execution : alert.getHistory()) {
                        entries.add(new Object[] {execution.getScheduledTime(), database.getName(), schema.getName(),
                            alert, execution});
                    }
                }
            }
        }
        Collections.sort(entries, new Comparator<Object[]>() {
            @Override
            public int compare(final Object[] left, final Object[] right) {
                return ((Instant) right[0]).compareTo((Instant) left[0]);
            }
        });
        final List<Row> rows = new ArrayList<>();
        for (final Object[] entry : entries) {
            final Instant scheduled = (Instant) entry[0];
            if (start != null && scheduled.isBefore(start) || end != null && scheduled.isAfter(end)) {
                continue;
            }
            if (rows.size() >= limit) {
                break;
            }
            rows.add(row(scheduled, (String) entry[1], (String) entry[2], (Alert) entry[3],
                (AlertExecution) entry[4]));
        }
        return new ResultSet(columns(), rows);
    }

    /**
     * One run. An evaluation that ran names the query that evaluated the condition, and — when the condition
     * held — the one that ran the action; a run without an error reports error code 0.
     */
    private static Row row(final Instant scheduled, final String databaseName, final String schemaName,
                           final Alert alert, final AlertExecution execution) {
        final boolean acted = execution != null
            && ("TRIGGERED".equals(execution.getState()) || "ACTION_FAILED".equals(execution.getState()));
        return new Row(Arrays.<Object>asList(
            alert.getName(),
            databaseName,
            schemaName,
            alert.getCondition(),
            execution == null ? null : queryId("CONDITION", databaseName, schemaName, alert, scheduled),
            alert.getAction(),
            acted ? queryId("ACTION", databaseName, schemaName, alert, scheduled) : null,
            execution == null ? "SCHEDULED" : execution.getState(),
            execution == null || execution.failed() ? null : Long.valueOf(0L),
            execution == null ? null : execution.getErrorMessage(),
            ShowResultHelpers.createdOn(scheduled),
            execution == null || execution.getCompletedTime() == null ? null
                : ShowResultHelpers.createdOn(execution.getCompletedTime()),
            execution == null ? "SCHEDULE" : execution.getScheduledFrom(),
            alert.getRunbook(), Boolean.FALSE, null));
    }

    /** A stable query id for one of a run's two queries. */
    private static String queryId(final String part, final String databaseName, final String schemaName,
                                  final Alert alert, final Instant scheduled) {
        return UUID.nameUUIDFromBytes((part + ":" + databaseName + "." + schemaName + "." + alert.getName() + ":"
            + scheduled.toEpochMilli()).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static List<ResultSetColumn> columns() {
        return Arrays.asList(
            new ResultSetColumn("NAME", StringType.VARCHAR),
            new ResultSetColumn("DATABASE_NAME", StringType.VARCHAR),
            new ResultSetColumn("SCHEMA_NAME", StringType.VARCHAR),
            new ResultSetColumn("CONDITION", StringType.VARCHAR),
            new ResultSetColumn("CONDITION_QUERY_ID", StringType.VARCHAR),
            new ResultSetColumn("ACTION", StringType.VARCHAR),
            new ResultSetColumn("ACTION_QUERY_ID", StringType.VARCHAR),
            new ResultSetColumn("STATE", StringType.VARCHAR),
            new ResultSetColumn("SQL_ERROR_CODE", NumericType.BIGINT),
            new ResultSetColumn("SQL_ERROR_MESSAGE", StringType.VARCHAR),
            new ResultSetColumn("SCHEDULED_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("COMPLETED_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("SCHEDULED_FROM", StringType.VARCHAR),
            new ResultSetColumn("RUNBOOK", StringType.VARCHAR),
            // The account carries two more at the end: whether the run suspended the alert, and the
            // configuration it ran under.
            new ResultSetColumn("WAS_AUTO_SUSPENDED", BooleanType.BOOLEAN),
            new ResultSetColumn("CONFIG", StringType.VARCHAR));
    }

    private static String text(final Object value) {
        if (value == null) {
            return null;
        }
        final String text = value.toString();
        return text.length() >= 2 && text.startsWith("'") && text.endsWith("'")
            ? text.substring(1, text.length() - 1) : text;
    }

    private static int limit(final Object value) {
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        final long limit;
        try {
            limit = value instanceof Number ? ((Number) value).longValue() : Long.parseLong(text(value).trim());
        } catch (final NumberFormatException notNumeric) {
            throw new RuntimeException("Invalid argument RESULT_LIMIT: expected an integer, got " + value + ".");
        }
        if (limit > MAX_LIMIT) {
            throw new RuntimeException(SqlCompilationError.of(
                "Value for parameter RESULT_LIMIT exceeds maximum allowable value (10,000)."));
        }
        // A limit below one is taken, and answers no rows.
        return (int) Math.max(0L, limit);
    }

    /** A time-range bound as an instant: a timestamp value, or ISO-8601 text; a local time is read as UTC. */
    private static Instant instant(final Object value) {
        if (value == null) {
            return null;
        }
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
            return ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
        }
        final String text = text(value).trim().replace(' ', 'T');
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (final DateTimeParseException notOffset) {
            try {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            } catch (final DateTimeParseException notLocal) {
                throw new RuntimeException("Invalid argument for ALERT_HISTORY: '" + text(value).toUpperCase(Locale.ROOT)
                    + "' is not a timestamp.");
            }
        }
    }
}
