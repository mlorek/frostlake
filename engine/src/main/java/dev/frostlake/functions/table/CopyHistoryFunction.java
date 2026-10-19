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

import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.LoadRecord;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.LengthlessStringType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * INFORMATION_SCHEMA.COPY_HISTORY(TABLE_NAME => '…', START_TIME => …[, END_TIME => …]): the files COPY loaded into one
 * table whose load finished within the range, in the function's live shape — the pipe columns NULL, since every load
 * here is a COPY statement's. A table that does not exist is refused by its name; the call's arguments are judged
 * before it runs (see {@code TableFunctionArguments}).
 */
public class CopyHistoryFunction extends TableFunction {

    /** The texts of the result, in the account's order, with the numbers and times between them. */
    private static final List<String> COLUMNS = Arrays.asList("FILE_NAME", "STAGE_LOCATION", "LAST_LOAD_TIME",
        "ROW_COUNT", "ROW_PARSED", "FILE_SIZE", "FIRST_ERROR_MESSAGE", "FIRST_ERROR_LINE_NUMBER",
        "FIRST_ERROR_CHARACTER_POS", "FIRST_ERROR_COLUMN_NAME", "ERROR_COUNT", "ERROR_LIMIT", "STATUS",
        "TABLE_CATALOG_NAME", "TABLE_SCHEMA_NAME", "TABLE_NAME", "PIPE_CATALOG_NAME", "PIPE_SCHEMA_NAME", "PIPE_NAME",
        "PIPE_RECEIVED_TIME", "BYTES_BILLED");

    private static final List<String> NUMBERS = Arrays.asList("ROW_COUNT", "ROW_PARSED", "FILE_SIZE",
        "FIRST_ERROR_LINE_NUMBER", "FIRST_ERROR_CHARACTER_POS", "ERROR_COUNT", "ERROR_LIMIT", "BYTES_BILLED");

    private static final List<String> TIMES = Arrays.asList("LAST_LOAD_TIME", "PIPE_RECEIVED_TIME");

    private final Catalog catalog;

    public CopyHistoryFunction(final Catalog catalog) {
        super("COPY_HISTORY");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Judged with the call's text and positions, before the function runs.
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        return history(namedArgs.get("TABLE_NAME"), namedArgs.get("START_TIME"), namedArgs.get("END_TIME"));
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        return history(positionalArgs.isEmpty() ? null : positionalArgs.get(0),
            positionalArgs.size() < 2 ? null : positionalArgs.get(1),
            positionalArgs.size() < 3 ? null : positionalArgs.get(2));
    }

    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return columns();
    }

    private ResultSet history(final Object tableName, final Object startTime, final Object endTime) {
        final String written = String.valueOf(tableName);
        final String canonical = SqlIdentifiers.canonicalText(written);
        final QualifiedName name = QualifiedName.parse(canonical);
        if (!catalog.hasTableAsWritten(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", canonical));
        }
        final Schema schema = catalog.resolveOwningSchema(canonical);
        final Instant start = instantOf(startTime);
        final Instant end = instantOf(endTime);
        final List<Row> rows = new ArrayList<Row>();
        for (final LoadRecord load : catalog.getLoadHistory().records()) {
            if (!load.getDatabase().equals(schema.getDatabaseName()) || !load.getSchema().equals(schema.getName())
                    || !load.getTable().equals(name.last())) {
                continue;
            }
            if (start != null && load.getLoadTime().isBefore(start) || end != null && load.getLoadTime().isAfter(end)) {
                continue;
            }
            rows.add(new Row(load.getFileName(), load.getStageLocation(),
                ShowResultHelpers.createdOn(load.getLoadTime()), load.getRowsLoaded(), load.getRowsParsed(),
                load.getFileSize(), load.getFirstErrorMessage(),
                load.getFirstErrorLine() == null ? null : Long.valueOf(load.getFirstErrorLine().longValue()),
                load.getFirstErrorCharacter() == null ? null : Long.valueOf(load.getFirstErrorCharacter().longValue()),
                load.getFirstErrorColumnName(), load.getErrorCount(), load.getErrorLimit(), status(load.getStatus()),
                load.getDatabase(), load.getSchema(), load.getTable(), null, null, null, null, null));
        }
        return new ResultSet(columns(), rows);
    }

    /** A load's status as COPY_HISTORY spells it: Loaded, Load failed or Partially loaded (documented). */
    private static String status(final String status) {
        if ("LOAD_FAILED".equals(status)) {
            return "Load failed";
        }
        if ("PARTIALLY_LOADED".equals(status)) {
            return "Partially loaded";
        }
        return "Loaded";
    }

    /** A time bound as an instant: a timestamp of any kind, a date at its midnight in the session's zone. */
    private static Instant instantOf(final Object value) {
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toInstant();
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toInstant();
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).atZone(SessionZone.current()).toInstant();
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay(SessionZone.current()).toInstant();
        }
        return null;
    }

    /** The result's columns: texts, NUMBER(38,0) counts and TIMESTAMP_LTZ times (live-verified). */
    private static List<ResultSetColumn> columns() {
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (final String name : COLUMNS) {
            if (NUMBERS.contains(name)) {
                columns.add(new ResultSetColumn(name, NumericType.BIGINT, null, new NumericType("NUMBER", 38, 0)));
            } else if (TIMES.contains(name)) {
                final DateTimeType type = new DateTimeType("TIMESTAMP_LTZ", 3, true);
                columns.add(new ResultSetColumn(name, type, null, type));
            } else {
                columns.add(new ResultSetColumn(name, StringType.VARCHAR, null, new LengthlessStringType()));
            }
        }
        return columns;
    }
}
