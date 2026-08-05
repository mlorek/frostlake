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
                ShowResultHelpers.createdOnText(db.getCreatedTime()),
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
                ShowResultHelpers.createdOnText(schema.getCreatedTime()),
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
        // Live's full SHOW TABLES layout, measured column for column on a real account — the tail
        // (search_optimization*, is_hybrid, is_iceberg, …) is read back through
        // TABLE(RESULT_SCAN(LAST_QUERY_ID())) by real migration scripts, so the names must exist
        // even where the feature is a constant here.
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
            new ResultSetColumn("search_optimization", StringType.VARCHAR),
            new ResultSetColumn("search_optimization_progress", StringType.VARCHAR),
            new ResultSetColumn("search_optimization_bytes", NumericType.BIGINT),
            new ResultSetColumn("is_external", StringType.VARCHAR),
            new ResultSetColumn("enable_schema_evolution", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("is_event", StringType.VARCHAR),
            new ResultSetColumn("is_hybrid", StringType.VARCHAR),
            new ResultSetColumn("is_iceberg", StringType.VARCHAR),
            new ResultSetColumn("is_dynamic", StringType.VARCHAR),
            new ResultSetColumn("is_immutable", StringType.VARCHAR),
            new ResultSetColumn("is_interactive", StringType.VARCHAR),
            new ResultSetColumn("row_timestamp", StringType.VARCHAR),
            new ResultSetColumn("error_logging", StringType.VARCHAR)
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
                    ShowResultHelpers.createdOnText(table.getCreatedTime()),
                    table.getName(),
                    dbName, scName, kind,
                    table.getComment(),
                    table.getClusterKeys().isEmpty() ? null : String.join(", ", table.getClusterKeys()),
                    (long) table.getRowCount(),
                    0L,
                    table.getOwner(),
                    1,
                    "OFF", "OFF",
                    "OFF", null, null,
                    "N", "N", "ROLE", "N",
                    table.isHybrid() ? "Y" : "N",
                    "N", "N", "N", "N", "OFF", "OFF"
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
        if (schemaName != null && schemaName.indexOf('.') >= 0) {
            // A db.schema scope (SHOW ... IN db1.schema1) — resolve it as a qualified name.
            return Arrays.asList(catalog.resolveSchema(schemaName));
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
                    ShowResultHelpers.createdOnText(view.getCreatedTime()),
                    view.getName(),
                    "",
                    dbName, scName,
                    view.getOwner(),
                    view.getComment(),
                    // Snowflake's text column carries the view's full CREATE statement.
                    view.ddl(scName + "." + view.getName()),
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
                    ShowResultHelpers.createdOnText(mv.getCreatedTime()),
                    mv.getName(),
                    dbName, scName,
                    mv.getOwner(),
                    mv.getComment(),
                    // Snowflake's text column carries the materialized view's full CREATE statement.
                    mv.ddl(scName + "." + mv.getName()),
                    mv.isSecure() ? "Y" : "N"
                )));
            }
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showColumns(final String tableName) {
        return showColumnsScoped(tableName, false);
    }

    /**
     * SHOW COLUMNS variants: a named table (or, with {@code view}, a named view), or — with a null
     * name — every table (or every view) in the current schema. The trailing table_name/schema_name
     * columns identify the owner in the multi-object listings (appended so the per-table positional
     * shape stays stable). View columns come from the view's declared column list; a view without one
     * contributes no rows (deriving them would mean executing the definition).
     */
    public ResultSet showColumnsScoped(final String name, final boolean view) {
        // Snowflake's SHOW COLUMNS shape, live-captured in this exact order.
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("table_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("column_name", StringType.VARCHAR),
            new ResultSetColumn("data_type", StringType.VARCHAR),
            new ResultSetColumn("null?", StringType.VARCHAR),
            new ResultSetColumn("default", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("expression", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("autoincrement", StringType.VARCHAR),
            new ResultSetColumn("schema_evolution_record", StringType.VARCHAR),
            new ResultSetColumn("write_default", StringType.VARCHAR)
        );
        final Schema schema = ShowResultHelpers.resolveDescribeSchema(catalog);
        final List<Row> rows = new ArrayList<>();
        if (view) {
            final List<View> views = new ArrayList<>();
            if (name != null) {
                // Snowflake requires a VIEW to be named by its FULL search path here — the
                // unqualified form is rejected outright (live-verified: "Must specify the full
                // search path starting from database for CV"), while SHOW COLUMNS IN TABLE takes
                // an unqualified name. Mirror that rather than resolving against the current schema.
                if (QualifiedName.parse(name).size() < 3) {
                    throw new RuntimeException("Must specify the full search path starting from database for "
                        + QualifiedName.parse(name).last().toUpperCase());
                }
                views.add(catalog.resolveView(name));
            } else {
                views.addAll(schema.getViews());
            }
            for (final View v : views) {
                final List<String> colNames = v.getColumnNames();
                if (colNames == null) {
                    continue;
                }
                for (final String colName : colNames) {
                    rows.add(new Row(Arrays.asList(v.getName(), schema.getName(), colName, null,
                        "true", null, "COLUMN", null, null, catalog.getCurrentDatabase(), null,
                        null, null)));
                }
            }
            return new ResultSet(columns, rows);
        }
        final List<Table> tables = new ArrayList<>();
        if (name != null) {
            tables.add(catalog.resolveTable(name));
        } else {
            tables.addAll(schema.getTables());
        }
        for (final Table table : tables) {
            for (final TableColumn col : table.getColumns()) {
                rows.add(new Row(Arrays.asList(
                    table.getName(),
                    schema.getName(),
                    col.getName(),
                    col.getDataType().getName(),
                    // Snowflake reports NOT_NULL for a non-nullable column and the text "true" for a
                    // nullable one (live-captured), not Y/N.
                    col.isNullable() ? "true" : "NOT_NULL",
                    col.getDefaultValue() != null ? col.getDefaultValue().toString() : null,
                    "COLUMN",
                    null,
                    col.getComment(),
                    catalog.getCurrentDatabase(),
                    col.isAutoIncrement() ? "Y" : null,
                    null,
                    null
                )));
            }
        }
        return new ResultSet(columns, rows);
    }

    /** SHOW VIEWS IN ACCOUNT: the views of every database, in database order. */
    public ResultSet showViewsInAccount() {
        List<ResultSetColumn> cols = null;
        final List<Row> rows = new ArrayList<>();
        for (final Database db : catalog.getAllDatabases()) {
            final ResultSet part = showViewsInDatabase(db.getName());
            cols = part.getColumns();
            rows.addAll(part.getRows());
        }
        return cols != null ? new ResultSet(cols, rows) : showViews(null);
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
                rows.add(new Row(Arrays.asList(ShowResultHelpers.createdOnText(t.getCreatedTime()), t.getName(), scName,
                    t.isTemporary() ? "TEMPORARY TABLE" : t.isTransient() ? "TRANSIENT TABLE" : "TABLE",
                    dbName, t.getOwner(), t.getComment())));
            }
            for (final View v : schema.getViews()) {
                rows.add(new Row(Arrays.asList(ShowResultHelpers.createdOnText(v.getCreatedTime()), v.getName(), scName,
                    "VIEW", dbName, v.getOwner(), v.getComment())));
            }
            for (final Function f : schema.getFunctions()) {
                rows.add(new Row(Arrays.asList(ShowResultHelpers.createdOnText(f.getCreatedTime()), f.getName(), scName,
                    f.isTableFunction() ? "TABLE FUNCTION" : "FUNCTION", dbName, f.getOwner(), f.getComment())));
            }
            for (final Procedure p : schema.getProcedures()) {
                rows.add(new Row(Arrays.asList(ShowResultHelpers.createdOnText(p.getCreatedTime()), p.getName(), scName,
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
                rows.add(new Row(Arrays.asList(ShowResultHelpers.createdOnText(dt.getCreatedTime()),
                    dt.getName(), scName, "DYNAMIC TABLE", dbName, dt.getOwner(), dt.getComment())));
            }
        }
        return new ResultSet(columns, rows);
    }

    /**
     * DESCRIBE TABLE has its OWN shape in Snowflake — {@code name, type, kind, null?, default,
     * primary key, unique key, check, expression, comment, policy name} — which is NOT the
     * SHOW COLUMNS shape (that one leads with table_name/schema_name and carries no key columns).
     * The two commands are deliberately rendered separately.
     */
    public ResultSet describeTable(final String tableName) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
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
        final List<Row> rows = new ArrayList<>();
        // DESCRIBE echoes the name as written — live: DESCRIBE TABLE nosuch answers
        // "Table 'NOSUCH' …", not the fully qualified form a DROP would report.
        for (final TableColumn col
                : catalog.resolveTableAsWritten(tableName, "Table").getColumns()) {
            rows.add(new Row(Arrays.asList(
                col.getName(),
                col.getDataType().getName(),
                "COLUMN",
                col.isNullable() ? "Y" : "N",
                col.getDefaultValue() != null ? col.getDefaultValue().toString() : null,
                col.isPrimaryKey() ? "Y" : "N",
                col.isUnique() ? "Y" : "N",
                null,
                null,
                col.getComment(),
                null
            )));
        }
        return new ResultSet(columns, rows);
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
