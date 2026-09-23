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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CREATE EVENT TABLE and the SHOW EVENT TABLES listing. An event table is an ordinary table with the fixed
 * OpenTelemetry column set — TIMESTAMP, START_TIMESTAMP, OBSERVED_TIMESTAMP, TRACE, RESOURCE,
 * RESOURCE_ATTRIBUTES, SCOPE, SCOPE_ATTRIBUTES, RECORD_TYPE, RECORD, RECORD_ATTRIBUTES, VALUE and EXEMPLARS —
 * marked as an event table, so SHOW TABLES reports it with {@code is_event = Y} and SHOW EVENT TABLES lists it.
 */
final class EventTableCommandHandler {

    private static final String[] COLUMN_NAMES = {"TIMESTAMP", "START_TIMESTAMP", "OBSERVED_TIMESTAMP", "TRACE",
        "RESOURCE", "RESOURCE_ATTRIBUTES", "SCOPE", "SCOPE_ATTRIBUTES", "RECORD_TYPE", "RECORD",
        "RECORD_ATTRIBUTES", "VALUE", "EXEMPLARS"};
    private static final String[] COLUMN_COMMENTS = {"timestamp when event record was added",
        "event period starting timestamp for metrics and spans",
        "used when capturing logs that do not have an accompanying timestamp", "tracing context", "for future use",
        "attributes that identify the source of an event", "scope for signals", "for future use",
        "type of the value in the RECORD field", "fixed fields for each signal type",
        "variable attributes for each signal type", "primary event value", "exemplars for metrics"};
    private static final DataType[] COLUMN_TYPES = {DateTimeType.TIMESTAMP_NTZ, DateTimeType.TIMESTAMP_NTZ,
        DateTimeType.TIMESTAMP_NTZ, ObjectType.OBJECT, ObjectType.OBJECT, ObjectType.OBJECT, ObjectType.OBJECT,
        ObjectType.OBJECT, StringType.VARCHAR, ObjectType.OBJECT, ObjectType.OBJECT, VariantType.VARIANT,
        ArrayType.ARRAY};

    private final DDLCommandHandler ddl;
    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final CreateTableHandler tables;

    EventTableCommandHandler(final DDLCommandHandler ddl, final Catalog catalog, final QueryExecutor queryExecutor,
                             final CreateTableHandler tables) {
        this.ddl = ddl;
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.tables = tables;
    }

    /** The event table column set, fresh for each table. */
    static List<TableColumn> columns() {
        final List<TableColumn> columns = new ArrayList<>();
        for (int i = 0; i < COLUMN_NAMES.length; i++) {
            final TableColumn column = new TableColumn(COLUMN_NAMES[i], COLUMN_TYPES[i], true, null, false, false,
                false);
            column.setComment(COLUMN_COMMENTS[i]);
            columns.add(column);
        }
        return columns;
    }

    /**
     * CREATE [OR REPLACE] EVENT TABLE [IF NOT EXISTS]: the table with the fixed columns, its comment, clustering
     * key, change tracking, retention and tags taken as a table takes them.
     */
    Object create(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String[] parts = catalog.withoutAccount(queryExecutor.resolveObjectNameParts(ctx.objectName()), 3);
        final Schema schema;
        final String databaseName;
        final String tableName;
        if (parts.length == 1) {
            schema = ddl.resolveCurrentSchema();
            databaseName = catalog.getCurrentDatabase();
            tableName = parts[0];
        } else if (parts.length == 2) {
            if (catalog.getCurrentDatabase() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            databaseName = catalog.getCurrentDatabase();
            schema = catalog.getDatabase(databaseName).getSchema(parts[0]);
            tableName = parts[1];
        } else {
            databaseName = parts[0];
            schema = catalog.getDatabase(databaseName).getSchema(parts[1]);
            tableName = parts[2];
        }
        final String key = QualifiedName.key(databaseName, schema.getName(), tableName);
        if (schema.hasTable(tableName)) {
            if (ifNotExists) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            if (ctx.or_replace() == null) {
                // The name is spelled as the statement wrote it, qualified as far as it was.
                final StringBuilder written = new StringBuilder();
                for (final String part : parts) {
                    if (written.length() > 0) {
                        written.append('.');
                    }
                    written.append(SqlIdentifiers.spellCanonical(part));
                }
                throw new RuntimeException(SqlCompilationError.of("Object '" + written + "' already exists."));
            }
            schema.dropTable(tableName);
            queryExecutor.getStorageEngine().dropTable(key);
            queryExecutor.getTransactionManager().discardBufferedWritesFor(key);
            if (ddl.getStreamManager() != null) {
                ddl.getStreamManager().onTableDropped(key);
            }
        }
        final Table table = new Table(tableName, columns(), false, false);
        table.setOwner(catalog.currentRoleForOwner());
        table.setEventTable(true);
        tables.applyTailProperties(ctx, table);
        schema.addTable(table);
        queryExecutor.getStorageEngine().createTable(key, table);
        queryExecutor.resetInternalTableStageDir(key);
        queryExecutor.resetCopyLoadHistory(key);
        return null;
    }

    /**
     * SHOW EVENT TABLES: the event tables among a SHOW TABLES listing of the same scope, in the columns the
     * event table listing reports, ending with {@code cluster_at_ingest_time}.
     */
    static ResultSet listing(final ResultSet tableListing) {
        final int event = tableListing.getColumnIndex("is_event");
        final List<String> keep = Arrays.asList("created_on", "name", "database_name", "schema_name", "owner",
            "comment", "rows", "bytes", "automatic_clustering", "cluster_by", "retention_time", "change_tracking",
            "search_optimization", "search_optimization_progress", "search_optimization_bytes", "owner_role_type");
        final List<Row> rows = new ArrayList<>();
        for (final Row row : tableListing.getRows()) {
            if ("Y".equals(row.getValue(event))) {
                rows.add(row);
            }
        }
        final ResultSet projected = ShowProjection.project(new ResultSet(tableListing.getColumns(), rows), keep);
        final List<ResultSetColumn> columns = new ArrayList<>(projected.getColumns());
        columns.add(new ResultSetColumn("cluster_at_ingest_time", StringType.VARCHAR));
        final List<Row> withFlag = new ArrayList<>();
        for (final Row row : projected.getRows()) {
            final List<Object> values = new ArrayList<>(row.getValues());
            values.add("false");
            withFlag.add(new Row(values));
        }
        return new ResultSet(columns, withFlag);
    }
}
