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

package dev.frostlake.executor;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SHOW / DESCRIBE handlers for relational objects: databases, schemas, tables, views, columns and the
 * cross-kind {@code SHOW OBJECTS} listing. Extracted from {@link ShowCommandExecutor}, which delegates here.
 */
final class ShowRelationalExecutor {

    private final Catalog catalog;

    ShowRelationalExecutor(final Catalog catalog) {
        this.catalog = catalog;
    }

    // Copied from QueryExecutor (catalog-only helper) so describeView stays self-contained here.
    private View resolveView(final String viewName) {
        final String[] parts = QualifiedName.parse(viewName).parts();
        final Schema schema;
        final String actualViewName;
        if (parts.length == 1) {
            schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
            actualViewName = viewName;
        } else if (parts.length == 2) {
            schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
            actualViewName = parts[1];
        } else {
            schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
            actualViewName = parts[2];
        }
        return schema.getView(actualViewName);
    }

    public ResultSet showDatabases() {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("is_default", StringType.VARCHAR),
            new ResultSetColumn("is_current", StringType.VARCHAR),
            new ResultSetColumn("origin", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("options", StringType.VARCHAR),
            new ResultSetColumn("retention_time", StringType.VARCHAR)
        );
        String cur = catalog.getCurrentDatabase();
        List<Row> rows = new ArrayList<>();
        for (final Database db : catalog.getAllDatabases()) {
            rows.add(new Row(Arrays.asList(
                db.getCreatedTime().toString(),
                db.getName(),
                "N",
                db.getName().equals(cur) ? "Y" : "N",
                "",
                db.getOwner(),
                db.getComment(),
                "",
                "1"
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showSchemas(final String databaseName) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("is_default", StringType.VARCHAR),
            new ResultSetColumn("is_current", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("options", StringType.VARCHAR),
            new ResultSetColumn("retention_time", NumericType.INTEGER)
        );
        String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database selected");
        String curSchema = catalog.getCurrentSchema();
        List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            rows.add(new Row(Arrays.asList(
                schema.getCreatedTime().toString(),
                schema.getName(),
                "N",
                // Schema names are stored verbatim but the current schema is tracked upper-cased,
                // so compare case-insensitively (matches the engine's case-insensitive name lookups).
                schema.getName().equalsIgnoreCase(curSchema) ? "Y" : "N",
                dbName,
                schema.getOwner(),
                schema.getComment(),
                "",
                1
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showTables(final String schemaName) {
        return showTablesScoped(schemaName, null, false);
    }

    /** SHOW TABLES IN DATABASE &lt;db&gt;: every table across all schemas of the database. */
    public ResultSet showTablesInDatabase(final String databaseName) {
        return showTablesScoped(null, databaseName, false);
    }

    /** SHOW HYBRID TABLES: only tables declared with CREATE HYBRID TABLE. */
    public ResultSet showHybridTables(final String schemaName) {
        return showTablesScoped(schemaName, null, true);
    }

    public ResultSet showHybridTablesInDatabase(final String databaseName) {
        return showTablesScoped(null, databaseName, true);
    }

    private ResultSet showTablesScoped(final String schemaName, final String databaseName,
                                       final boolean hybridOnly) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("cluster_by", StringType.VARCHAR),
            new ResultSetColumn("rows", NumericType.BIGINT),
            new ResultSetColumn("bytes", NumericType.BIGINT),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("retention_time", NumericType.INTEGER),
            new ResultSetColumn("automatic_clustering", StringType.VARCHAR),
            new ResultSetColumn("change_tracking", StringType.VARCHAR),
            new ResultSetColumn("is_external", StringType.VARCHAR)
        );
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName)) {
            final String scName = schema.getName();
            for (final Table table : schema.getTables()) {
                if (hybridOnly && !table.isHybrid()) {
                    continue;
                }
                final String kind = table.isHybrid() ? "HYBRID TABLE"
                    : table.isTemporary() ? "TEMPORARY TABLE"
                    : table.isTransient() ? "TRANSIENT TABLE" : "TABLE";
                rows.add(new Row(Arrays.asList(
                    table.getCreatedTime().toString(),
                    table.getName(),
                    dbName, scName, kind,
                    table.getComment(),
                    table.getClusterKeys().isEmpty() ? null : String.join(", ", table.getClusterKeys()),
                    (long) table.getRowCount(),
                    0L,
                    table.getOwner(),
                    1,
                    "OFF", "OFF", "N"
                )));
            }
        }
        return new ResultSet(columns, rows);
    }

    /** Schemas to scan for a {@code SHOW … [IN {SCHEMA | DATABASE}]} command: every schema of
     *  {@code databaseName} when given, otherwise the single current-or-named schema. */
    private List<Schema> resolveScopeSchemas(final String schemaName, final String databaseName) {
        if (databaseName != null) {
            return catalog.getDatabase(databaseName).getAllSchemas();
        }
        final String db = catalog.getCurrentDatabase();
        final String sc = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (db == null || sc == null) {
            throw new RuntimeException("No database or schema selected");
        }
        return Arrays.asList(catalog.getDatabase(db).getSchema(sc));
    }

    public ResultSet showViews(final String schemaName) {
        return showViewsScoped(schemaName, null);
    }

    /** SHOW VIEWS IN DATABASE &lt;db&gt;: every view across all schemas of the database. */
    public ResultSet showViewsInDatabase(final String databaseName) {
        return showViewsScoped(null, databaseName);
    }

    private ResultSet showViewsScoped(final String schemaName, final String databaseName) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("reserved", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("text", StringType.VARCHAR),
            new ResultSetColumn("is_secure", StringType.VARCHAR),
            new ResultSetColumn("is_materialized", StringType.VARCHAR)
        );
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName)) {
            final String scName = schema.getName();
            for (final View view : schema.getViews()) {
                rows.add(new Row(Arrays.asList(
                    view.getCreatedTime().toString(),
                    view.getName(),
                    "",
                    dbName, scName,
                    view.getOwner(),
                    view.getComment(),
                    view.getDefinition(),
                    view.isSecure() ? "Y" : "N", "N"
                )));
            }
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showMaterializedViews(final String schemaName) {
        return showMaterializedViewsScoped(schemaName, null);
    }

    /** SHOW MATERIALIZED VIEWS IN DATABASE &lt;db&gt;: every materialized view across all schemas of the database. */
    public ResultSet showMaterializedViewsInDatabase(final String databaseName) {
        return showMaterializedViewsScoped(null, databaseName);
    }

    private ResultSet showMaterializedViewsScoped(final String schemaName, final String databaseName) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("text", StringType.VARCHAR),
            new ResultSetColumn("is_secure", StringType.VARCHAR)
        );
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName)) {
            final String scName = schema.getName();
            for (final MaterializedView mv : schema.getMaterializedViews()) {
                rows.add(new Row(Arrays.asList(
                    mv.getCreatedTime().toString(),
                    mv.getName(),
                    dbName, scName,
                    mv.getOwner(),
                    mv.getComment(),
                    mv.getDefinition(),
                    mv.isSecure() ? "Y" : "N"
                )));
            }
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showColumns(final String tableName) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("column_name", StringType.VARCHAR),
            new ResultSetColumn("data_type", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("null?", StringType.VARCHAR),
            new ResultSetColumn("default", StringType.VARCHAR),
            new ResultSetColumn("primary key", StringType.VARCHAR),
            new ResultSetColumn("unique key", StringType.VARCHAR),
            new ResultSetColumn("check", StringType.VARCHAR),
            new ResultSetColumn("expression", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("policy name", StringType.VARCHAR)
        );
        Table table = catalog.resolveTable(tableName);
        List<Row> rows = new ArrayList<>();
        for (final TableColumn col : table.getColumns()) {
            rows.add(new Row(Arrays.asList(
                col.getName(),
                col.getDataType().getName(),
                "COLUMN",
                col.isNullable() ? "Y" : "N",
                col.getDefaultValue() != null ? col.getDefaultValue().toString() : null,
                col.isPrimaryKey() ? "Y" : "N",
                col.isUnique() ? "Y" : "N",
                null, null,
                col.getComment(),
                null
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showObjects(final String schemaName) {
        return showObjectsScoped(schemaName, null);
    }

    /** SHOW OBJECTS IN DATABASE &lt;db&gt;: every object across all schemas of the database. */
    public ResultSet showObjectsInDatabase(final String databaseName) {
        return showObjectsScoped(null, databaseName);
    }

    private ResultSet showObjectsScoped(final String schemaName, final String databaseName) {
        // Returns tables, views, functions, procedures, sequences, streams, tasks and dynamic tables.
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR)
        );
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName)) {
            final String scName = schema.getName();
            for (final Table t : schema.getTables()) {
                rows.add(new Row(Arrays.asList(t.getCreatedTime().toString(), t.getName(), scName,
                    t.isTemporary() ? "TEMPORARY TABLE" : t.isTransient() ? "TRANSIENT TABLE" : "TABLE",
                    dbName, t.getOwner(), t.getComment())));
            }
            for (final View v : schema.getViews()) {
                rows.add(new Row(Arrays.asList(v.getCreatedTime().toString(), v.getName(), scName,
                    "VIEW", dbName, v.getOwner(), v.getComment())));
            }
            for (final Function f : schema.getFunctions()) {
                rows.add(new Row(Arrays.asList(f.getCreatedTime().toString(), f.getName(), scName,
                    f.isTableFunction() ? "TABLE FUNCTION" : "FUNCTION", dbName, f.getOwner(), f.getComment())));
            }
            for (final Procedure p : schema.getProcedures()) {
                rows.add(new Row(Arrays.asList(p.getCreatedTime().toString(), p.getName(), scName,
                    "PROCEDURE", dbName, p.getOwner(), p.getComment())));
            }
            for (final Sequence s : schema.getSequences()) {
                rows.add(new Row(Arrays.asList(null, s.getName(), scName,
                    "SEQUENCE", dbName, s.getOwner(), s.getComment())));
            }
            for (final Stream s : schema.getStreams()) {
                rows.add(new Row(Arrays.asList(s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                    s.getName(), scName, "STREAM", dbName, s.getOwner(), s.getComment())));
            }
            for (final Task t : schema.getTasks()) {
                rows.add(new Row(Arrays.asList(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                    t.getName(), scName, "TASK", dbName, t.getOwner(), t.getComment())));
            }
            for (final DynamicTable dt : schema.getDynamicTables()) {
                rows.add(new Row(Arrays.asList(dt.getCreatedTime().toString(),
                    dt.getName(), scName, "DYNAMIC TABLE", dbName, dt.getOwner(), dt.getComment())));
            }
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet describeTable(final String tableName) {
        return showColumns(tableName);
    }

    public ResultSet describeView(final String viewName) {
        List<Row> rows = new ArrayList<>();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)
        );

        View view = resolveView(viewName);
        rows.add(new Row(Arrays.asList("name", view.getName())));
        rows.add(new Row(Arrays.asList("definition", view.getDefinition())));

        return new ResultSet(columns, rows);
    }
}
