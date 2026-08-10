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
import dev.frostlake.metastore.model.CheckConstraint;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ColumnTypeJson;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SHOW / DESCRIBE handlers for relational objects: databases, schemas, tables, views, columns and the
 * cross-kind {@code SHOW OBJECTS} listing. Extracted from {@link ShowCommandExecutor}, which delegates here.
 */
final class ShowRelationalExecutor {

    /** Live reports the data-retention window as text, and the engine keeps every table at one day. */
    private static final String DEFAULT_RETENTION_TIME = "1";

    /**
     * DESCRIBE's default cell: an identity column spells its whole generator here —
     * {@code IDENTITY START 5 INCREMENT 2 NOORDER} — any other column its declared expression
     * verbatim, and a column with no default NULL.
     *
     * <p>SHOW COLUMNS DOES NOT AGREE WITH THIS, and the two must not share one helper: there the same
     * unset cell is the EMPTY STRING, and an identity column's default cell is empty because the
     * generator is spelled in that surface's own AUTOINCREMENT cell instead. Both measured on a live
     * account. It is the same split already recorded between SHOW and INFORMATION_SCHEMA — the same
     * fact, spelled differently per surface.
     */
    private static String describeDefaultText(final TableColumn col) {
        if (col.isAutoIncrement()) {
            return identityGenerator(col);
        }
        return col.getDefaultValue() != null
            ? ShowResultHelpers.renderDefaultExpression(col.getDefaultValue()) : null;
    }

    /**
     * SHOW COLUMNS' default cell: the declared expression, or the EMPTY STRING when there is none —
     * and empty for an IDENTITY column too, whose generator belongs in {@link #columnAutoincrementText}.
     */
    private static String columnDefaultText(final TableColumn col) {
        if (col.isAutoIncrement()) {
            return "";
        }
        return col.getDefaultValue() != null
            ? ShowResultHelpers.renderDefaultExpression(col.getDefaultValue()) : "";
    }

    /** {@code IDENTITY START n INCREMENT m NOORDER}, the spelling both surfaces use for a generator. */
    private static String identityGenerator(final TableColumn col) {
        return "IDENTITY START " + col.getIdentityStart()
            + " INCREMENT " + col.getIdentityIncrement() + " NOORDER";
    }

    /**
     * The autoincrement cell: an identity column spells its whole generator here —
     * {@code IDENTITY START 1 INCREMENT 1 NOORDER} — and every other column the empty string.
     *
     * <p>Frostlake does not model the ORDER / NOORDER half of the generator, so it always spells
     * NOORDER, which is what a plain {@code AUTOINCREMENT} declaration produces on a live account.
     */
    private static String columnAutoincrementText(final TableColumn col) {
        if (!col.isAutoIncrement()) {
            return "";
        }
        return identityGenerator(col);
    }

    /** How live renders a clustering key in SHOW output: LINEAR(a, b), or empty when unclustered. */
    private String clusterByText(final List<String> clusterKeys) {
        if (clusterKeys.isEmpty()) {
            return "";
        }
        return "LINEAR(" + String.join(", ", clusterKeys) + ")";
    }

    /** One SHOW OBJECTS row in live's column order.
     *  Byte counts are always zero: the engine holds rows in memory and has no on-disk footprint. */
    private Row objectRow(final LocalDateTime createdOn, final String name, final String dbName,
                          final String scName, final String kind, final String comment,
                          final String clusterBy, final long rowCount, final String owner,
                          final boolean hybrid, final boolean dynamic) {
        return new Row(Arrays.asList(createdOn, name, dbName, scName, kind,
            ShowResultHelpers.text(comment), clusterBy, rowCount, 0L, owner,
            DEFAULT_RETENTION_TIME, ShowResultHelpers.ownerRoleType(owner),
            hybrid ? "Y" : "N", dynamic ? "Y" : "N", "N", "N"));
    }

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
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("is_default", StringType.VARCHAR),
            new ResultSetColumn("is_current", StringType.VARCHAR),
            new ResultSetColumn("origin", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("options", StringType.VARCHAR),
            new ResultSetColumn("retention_time", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("object_visibility", new ObjectType()),
            new ResultSetColumn("data_quality_monitoring_settings", new ObjectType()),
            new ResultSetColumn("resharing_settings", new ObjectType())
        );
        final String cur = catalog.getCurrentDatabase();
        final List<Row> rows = new ArrayList<>();
        for (final Database db : catalog.getAllDatabases()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(db.getCreatedTime()),
                db.getName(),
                "N",
                db.getName().equals(cur) ? "Y" : "N",
                "",
                db.getOwner(),
                ShowResultHelpers.text(db.getComment()),
                "",
                "1",
                "STANDARD",
                ShowResultHelpers.OWNER_ROLE_TYPE,
                null, null, null
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showSchemas(final String databaseName) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("is_default", StringType.VARCHAR),
            new ResultSetColumn("is_current", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("options", StringType.VARCHAR),
            new ResultSetColumn("retention_time", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("classification_profile_database", StringType.VARCHAR),
            new ResultSetColumn("classification_profile_schema", StringType.VARCHAR),
            new ResultSetColumn("classification_profile", StringType.VARCHAR),
            new ResultSetColumn("object_visibility", new ObjectType()),
            new ResultSetColumn("is_nested", StringType.VARCHAR)
        );
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database selected");
        final String curSchema = catalog.getCurrentSchema();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(schema.getCreatedTime()),
                schema.getName(),
                "N",
                // Schema names are stored verbatim but the current schema is tracked upper-cased,
                // so compare case-insensitively (matches the engine's case-insensitive name lookups).
                schema.getName().equalsIgnoreCase(curSchema) ? "Y" : "N",
                dbName,
                ShowResultHelpers.text(schema.getOwner()),
                ShowResultHelpers.text(schema.getComment()),
                "",
                DEFAULT_RETENTION_TIME,
                ShowResultHelpers.ownerRoleType(schema.getOwner()),
                null, null, null, null,
                // Spelled as the text false, not N — live's is_nested convention differs from the
                // Y/N flags beside it.
                "false"
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showTables(final String schemaName) {
        return showTablesScoped(schemaName, null, false);
    }

    /** SHOW TABLES IN ACCOUNT: every table of every database, ordered database/schema/name. */
    public ResultSet showTablesInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showTablesInDatabase(databaseName);
            }
        });
        return across != null ? across : showTables(null);
    }

    /** SHOW SCHEMAS IN ACCOUNT: every schema of every database, ordered database/name. */
    public ResultSet showSchemasInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showSchemas(databaseName);
            }
        });
        return across != null ? across : showSchemas(null);
    }

    /** SHOW OBJECTS IN ACCOUNT: every table and view of every database, ordered database/schema/name. */
    public ResultSet showObjectsInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showObjectsInDatabase(databaseName);
            }
        });
        return across != null ? across : showObjects(null);
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
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("cluster_by", StringType.VARCHAR),
            new ResultSetColumn("rows", NumericType.BIGINT),
            new ResultSetColumn("bytes", NumericType.BIGINT),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("retention_time", StringType.VARCHAR),
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
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName, true)) {
            final String scName = schema.getName();
            for (final Table table : schema.getTables()) {
                if (hybridOnly && !table.isHybrid()) {
                    continue;
                }
                final String kind = table.isHybrid() ? "HYBRID TABLE"
                    : table.isTemporary() ? "TEMPORARY"
                    : table.isTransient() ? "TRANSIENT" : "TABLE";
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(table.getCreatedTime()),
                    table.getName(),
                    dbName, scName, kind,
                    ShowResultHelpers.text(table.getComment()),
                    clusterByText(table.getClusterKeys()),
                    (long) table.getRowCount(),
                    0L,
                    ShowResultHelpers.text(table.getOwner()),
                    DEFAULT_RETENTION_TIME,
                    // Live turns automatic clustering on for any table that carries a clustering key.
                    // Change tracking is the table's own flag — the option, ALTER … SET, or a stream's
                    // creation flips it on, and it survives the stream.
                    table.getClusterKeys().isEmpty() || table.isReclusterSuspended() ? "OFF" : "ON",
                    table.isChangeTracking() ? "ON" : "OFF",
                    // Search optimization: on once anything is configured, and the progress/bytes
                    // cells read 100 and 0 while it is (live-verified; they are null when it is off).
                    table.hasSearchOptimization() ? "ON" : "OFF",
                    table.hasSearchOptimization() ? "100" : null,
                    table.hasSearchOptimization() ? 0L : null,
                    "N", table.isSchemaEvolution() ? "Y" : "N", "ROLE", "N",
                    table.isHybrid() ? "Y" : "N",
                    "N", "N", "N", "N", "OFF", "OFF"
                )));
            }
        }
        return new ResultSet(columns, rows);
    }

    /**
     * Schemas to scan for a {@code SHOW … [IN {SCHEMA | DATABASE}]} command: every schema of
     * {@code databaseName} when given, otherwise the single current-or-named schema.
     *
     * <p>{@code reportMissingGenerically} picks how an unreachable scope is reported, which live decides
     * per kind and not per scope form. Measured: TABLES, VIEWS and OBJECTS answer the generic
     * {@code Object does not exist, or operation cannot be performed.}, while MATERIALIZED VIEWS — served
     * by this same method — names the schema like the rest of the catalog does.
     */
    private List<Schema> resolveScopeSchemas(final String schemaName, final String databaseName,
                                             final boolean reportMissingGenerically) {
        try {
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
        } catch (final RuntimeException missing) {
            if (reportMissingGenerically) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            throw missing;
        }
    }

    public ResultSet showViews(final String schemaName) {
        return showViewsScoped(schemaName, null);
    }

    /** SHOW VIEWS IN DATABASE &lt;db&gt;: every view across all schemas of the database. */
    public ResultSet showViewsInDatabase(final String databaseName) {
        return showViewsScoped(null, databaseName);
    }

    private ResultSet showViewsScoped(final String schemaName, final String databaseName) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("reserved", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("text", StringType.VARCHAR),
            new ResultSetColumn("is_secure", StringType.VARCHAR),
            new ResultSetColumn("is_materialized", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("change_tracking", StringType.VARCHAR)
        );
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName, true)) {
            final String scName = schema.getName();
            for (final View view : schema.getViews()) {
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(view.getCreatedTime()),
                    view.getName(),
                    "",
                    dbName, scName,
                    ShowResultHelpers.text(view.getOwner()),
                    ShowResultHelpers.text(view.getComment()),
                    // Snowflake's text column carries the view's full CREATE statement.
                    view.ddl(scName + "." + view.getName()),
                    // These two read "true"/"false" on a real account, not the Y/N used elsewhere.
                    String.valueOf(view.isSecure()), "false",
                    ShowResultHelpers.ownerRoleType(view.getOwner()), "OFF"
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
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
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
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName, false)) {
            final String scName = schema.getName();
            for (final MaterializedView mv : schema.getMaterializedViews()) {
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(mv.getCreatedTime()),
                    mv.getName(),
                    dbName, scName,
                    ShowResultHelpers.text(mv.getOwner()),
                    ShowResultHelpers.text(mv.getComment()),
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

    /** Snowflake's SHOW COLUMNS shape, live-captured in this exact order. */
    private static List<ResultSetColumn> describeColumnsShape() {
        return Arrays.asList(
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
    }

    /** Every column of every relation in one schema — tables, views and materialized views alike. */
    private void addSchemaColumnRows(final List<Row> rows, final Schema schema,
                                     final String databaseName) {
        for (final Table table : schema.getTables()) {
            addTableColumnRows(rows, table, schema.getName(), databaseName);
        }
        for (final View v : schema.getViews()) {
            rows.addAll(viewColumnRows(v.getName(), v.getResolvedColumns(), v.getColumnNames(),
                databaseName, schema.getName()));
        }
        for (final MaterializedView mv : schema.getMaterializedViews()) {
            rows.addAll(viewColumnRows(mv.getName(), mv.getResolvedColumns(), mv.getColumnNames(),
                databaseName, schema.getName()));
        }
    }

    /**
     * {@code SHOW COLUMNS IN SCHEMA <db>.<schema>} — every column of every relation in that schema.
     *
     * <p>The schema must be named by its FULL search path, exactly as the VIEW spelling requires and
     * with the same sentence; an unqualified name is refused BEFORE the schema is looked up, so a
     * missing schema named without its database earns the qualification complaint rather than the
     * does-not-exist one (live-verified).
     */
    public ResultSet showColumnsInSchema(final String name) {
        final QualifiedName qualified = QualifiedName.parse(name);
        if (qualified.size() < 2) {
            throw new RuntimeException(SqlCompilationError.of(
                "Must specify the full search path starting from database for "
                    + qualified.last().toUpperCase(Locale.ROOT)));
        }
        final String[] parts = qualified.parts();
        final String databaseName = parts[parts.length - 2];
        final String schemaName = parts[parts.length - 1];
        final Schema schema = resolveSchemaOrRefuse(databaseName, schemaName);
        final List<Row> rows = new ArrayList<>();
        addSchemaColumnRows(rows, schema, catalog.getDatabase(databaseName).getName());
        return new ResultSet(describeColumnsShape(), rows);
    }

    /** {@code SHOW COLUMNS IN DATABASE <db>} — every schema of that database, in catalog order. */
    public ResultSet showColumnsInDatabase(final String databaseName) {
        final Database database = resolveDatabaseOrRefuse(databaseName);
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : database.getAllSchemas()) {
            addSchemaColumnRows(rows, schema, database.getName());
        }
        return new ResultSet(describeColumnsShape(), rows);
    }

    /** {@code SHOW COLUMNS IN ACCOUNT} — every database, every schema. */
    public ResultSet showColumnsInAccount() {
        final List<Row> rows = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                addSchemaColumnRows(rows, schema, database.getName());
            }
        }
        return new ResultSet(describeColumnsShape(), rows);
    }

    /** The schema, or live's refusal naming it with its database. */
    private Schema resolveSchemaOrRefuse(final String databaseName, final String schemaName) {
        try {
            return resolveDatabaseOrRefuse(databaseName).getSchema(schemaName);
        } catch (final RuntimeException missing) {
            throw new RuntimeException(SqlCompilationError.of("Schema '"
                + databaseName.toUpperCase(Locale.ROOT) + "." + schemaName.toUpperCase(Locale.ROOT)
                + "' does not exist or not authorized."));
        }
    }

    /** The database, or live's refusal naming it. */
    private Database resolveDatabaseOrRefuse(final String databaseName) {
        try {
            return catalog.getDatabase(databaseName);
        } catch (final RuntimeException missing) {
            throw new RuntimeException(SqlCompilationError.of("Database '"
                + databaseName.toUpperCase(Locale.ROOT) + "' does not exist or not authorized."));
        }
    }

    public ResultSet showColumnsScoped(final String name, final boolean view) {
        final List<ResultSetColumn> columns = describeColumnsShape();
        final Schema schema = ShowResultHelpers.resolveDescribeSchema(catalog);
        final List<Row> rows = new ArrayList<>();
        if (name == null) {
            // The unnamed listing walks EVERY relation in the current schema, not just the tables:
            // over one table, one view and one materialized view a live account answers five rows,
            // one per column of all three (live-verified).
            addSchemaColumnRows(rows, schema, catalog.getCurrentDatabase());
            return new ResultSet(columns, rows);
        }
        if (view && QualifiedName.parse(name).size() < 3) {
            // The VIEW spelling requires the FULL search path, and requires it whatever the object
            // turns out to be — live refuses `SHOW COLUMNS IN VIEW t` for a plain TABLE t with this
            // same sentence. It is a property of the keyword, not of the relation (live-verified).
            throw new RuntimeException(SqlCompilationError.of(
                "Must specify the full search path starting from database for "
                    + QualifiedName.parse(name).last().toUpperCase()));
        }
        // The named kind is NOT a filter: TABLE, VIEW and the bare form each resolve a table, a view
        // or a materialized view alike, exactly as DESCRIBE does. The keyword decides only the
        // wording when nothing by that name exists (live-verified).
        final View named = findViewOrNull(name);
        if (named != null) {
            rows.addAll(viewColumnRows(named.getName(), named.getResolvedColumns(),
                named.getColumnNames(), relationDatabase(name), relationSchema(name)));
            return new ResultSet(columns, rows);
        }
        final MaterializedView materialized = findMaterializedViewOrNull(name);
        if (materialized != null) {
            rows.addAll(viewColumnRows(materialized.getName(), materialized.getResolvedColumns(),
                materialized.getColumnNames(), relationDatabase(name), relationSchema(name)));
            return new ResultSet(columns, rows);
        }
        // Nothing derived by that name: the table path owns the lookup and its refusal, which echoes
        // the name as the statement WROTE it and speaks the named kind.
        final Table table = catalog.resolveTableAsWritten(name, view ? "View" : "Table");
        addTableColumnRows(rows, table, schema.getName(), catalog.getCurrentDatabase());
        return new ResultSet(columns, rows);
    }

    /** The owning database of a named relation: the qualified name's own, else the current one. */
    private String relationDatabase(final String name) {
        final String[] parts = QualifiedName.parse(name).parts();
        return parts.length == 3 ? catalog.getDatabase(parts[0]).getName() : catalog.getCurrentDatabase();
    }

    /** The owning schema of a named relation: the qualified name's own, else the current one. */
    private String relationSchema(final String name) {
        final String[] parts = QualifiedName.parse(name).parts();
        if (parts.length == 1) {
            return ShowResultHelpers.resolveDescribeSchema(catalog).getName();
        }
        final String database = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
        return catalog.getDatabase(database).getSchema(parts[parts.length - 2]).getName();
    }

    /** One row per column of a table, in the SHOW COLUMNS shape. */
    private void addTableColumnRows(final List<Row> rows, final Table table, final String schemaName,
                                    final String databaseName) {
        for (final TableColumn col : table.getColumns()) {
            rows.add(new Row(Arrays.asList(
                table.getName(),
                schemaName,
                col.getName(),
                // The data_type cell is a JSON descriptor in Snowflake's internal vocabulary,
                // never a type name — see ColumnTypeJson.
                ColumnTypeJson.render(col.getDataType(), col.isNullable()),
                // Snowflake reports NOT_NULL for a non-nullable column and the text "true" for a
                // nullable one (live-captured), not Y/N.
                col.isNullable() ? "true" : "NOT_NULL",
                columnDefaultText(col),
                "COLUMN",
                // The expression cell is empty for an ordinary column, never NULL (live-verified).
                "",
                // SHOW spells an unset comment as the empty string, never NULL (live-verified;
                // DESCRIBE is the surface that keeps NULL for unset).
                ShowResultHelpers.text(col.getComment()),
                databaseName,
                columnAutoincrementText(col),
                null,
                null
            )));
        }
    }

    /**
     * One row per column of a view. The types come off the column list frozen onto the catalog entry
     * when the view was created — the same list INFORMATION_SCHEMA.COLUMNS reports — so a view without
     * a declared column list describes its columns too. Only a view whose definition would not plan
     * (a forward reference, a function the engine lacks) falls back to its declared names alone, with
     * no type to report; live rejects the CREATE outright in those cases.
     *
     * <p>KNOWN DIVERGENCE: every column here is reported nullable. Live carries a source column's NOT
     * NULL through a view when — and only when — the projected item is a bare column reference, which
     * needs nullability on the projection itself; Frostlake's derived columns do not carry it yet.
     */
    private List<Row> viewColumnRows(final String relationName, final List<TableColumn> resolved,
                                     final List<String> declared, final String databaseName,
                                     final String schemaName) {
        final List<Row> rows = new ArrayList<>();
        if (resolved != null && !resolved.isEmpty()) {
            for (final TableColumn col : resolved) {
                rows.add(viewColumnRow(relationName, databaseName, schemaName, col.getName(),
                    ColumnTypeJson.render(col.getDataType(), col.isNullable()), col.isNullable(),
                    col.getComment()));
            }
            return rows;
        }
        if (declared != null) {
            for (final String colName : declared) {
                rows.add(viewColumnRow(relationName, databaseName, schemaName, colName, null, true, null));
            }
        }
        return rows;
    }

    private Row viewColumnRow(final String relationName, final String databaseName, final String schemaName,
                              final String columnName, final String dataType, final boolean nullable,
                              final String comment) {
        // The unset text cells are the EMPTY STRING here too — default, expression and autoincrement
        // alike. A derived column has no default and no identity, so all three are always empty.
        return new Row(Arrays.asList(relationName, schemaName, columnName, dataType,
            nullable ? "true" : "NOT_NULL", "", "COLUMN", "",
            // The view's OWN declared column comment, spelled the way SHOW spells every unset text
            // value — the empty string, never NULL. A base column's comment does not reach here
            // (live-verified), and the view's does.
            ShowResultHelpers.text(comment),
            // autoincrement is empty; schema_evolution_record and write_default are genuinely NULL on
            // live, for a table column and a derived one alike — the one pair that is NOT empty-string.
            databaseName, "", null, null));
    }

    /** SHOW VIEWS IN ACCOUNT: the views of every database, in database order. */
    public ResultSet showViewsInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showViewsInDatabase(databaseName);
            }
        });
        return across != null ? across : showViews(null);
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
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("cluster_by", StringType.VARCHAR),
            new ResultSetColumn("rows", NumericType.BIGINT),
            new ResultSetColumn("bytes", NumericType.BIGINT),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("retention_time", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("is_hybrid", StringType.VARCHAR),
            new ResultSetColumn("is_dynamic", StringType.VARCHAR),
            new ResultSetColumn("is_iceberg", StringType.VARCHAR),
            new ResultSetColumn("is_interactive", StringType.VARCHAR)
        );
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName, true)) {
            final String scName = schema.getName();
            for (final Table t : schema.getTables()) {
                // Live reports transient tables as plain TABLE here, unlike SHOW TABLES.
                rows.add(objectRow(ShowResultHelpers.createdOn(t.getCreatedTime()), t.getName(),
                    dbName, scName, t.isTemporary() ? "TEMPORARY" : "TABLE",
                    ShowResultHelpers.text(t.getComment()),
                    clusterByText(t.getClusterKeys()), (long) t.getRowCount(), ShowResultHelpers.text(t.getOwner()),
                    t.isHybrid(), false));
            }
            for (final View v : schema.getViews()) {
                rows.add(objectRow(ShowResultHelpers.createdOn(v.getCreatedTime()), v.getName(),
                    dbName, scName, "VIEW", ShowResultHelpers.text(v.getComment()),
                    "", 0L, ShowResultHelpers.text(v.getOwner()), false, false));
            }
            for (final DynamicTable dt : schema.getDynamicTables()) {
                rows.add(objectRow(ShowResultHelpers.createdOn(dt.getCreatedTime()), dt.getName(),
                    dbName, scName, "TABLE", dt.getComment(), "", 0L,
                    ShowResultHelpers.text(dt.getOwner()), false, true));
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
        return describeRelation(tableName, "Table");
    }

    /**
     * DESCRIBE for the relation family. The three kinds are INTERCHANGEABLE on a live account —
     * {@code DESCRIBE TABLE} on a view, {@code DESCRIBE VIEW} on a table and {@code DESCRIBE
     * MATERIALIZED VIEW} on either all answer the object's own column shape — so the named kind
     * decides only ONE thing: the wording when nothing by that name exists ({@code View 'X' does not
     * exist or not authorized.}). Everything else follows the object that was found.
     */
    public ResultSet describeRelation(final String name, final String reportedKind) {
        final View view = findViewOrNull(name);
        if (view != null) {
            return describeDerivedColumns(view.getResolvedColumns(), view.getColumnNames());
        }
        final MaterializedView materialized = findMaterializedViewOrNull(name);
        if (materialized != null) {
            return describeDerivedColumns(materialized.getResolvedColumns(), null);
        }
        // Nothing derived by that name: the table path owns the lookup and its refusal, which is
        // where the named kind is spoken.
        return describeTableColumns(catalog.resolveTableAsWritten(name, reportedKind));
    }

    /** The view by that name, or null — a name that resolves to nothing at all is the table path's. */
    private View findViewOrNull(final String name) {
        try {
            return resolveView(name);
        } catch (final RuntimeException notAView) {
            return null;
        }
    }

    private MaterializedView findMaterializedViewOrNull(final String name) {
        try {
            final String[] parts = QualifiedName.parse(name).parts();
            final Schema schema = parts.length == 1
                ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema())
                : parts.length == 2
                    ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                    : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            return schema.getMaterializedView(parts[parts.length - 1]);
        } catch (final RuntimeException notMaterialized) {
            return null;
        }
    }

    /**
     * A view's columns in the DESCRIBE shape. A derived column carries no default, no key flags and no
     * policy — only its name, its canonical type and the comment the view DECLARED for it, which is
     * the view's own and never the base column's (live-verified). {@code declaredNames} is the view's
     * column list, used only to report the names of a view whose definition would not plan.
     */
    private ResultSet describeDerivedColumns(final List<TableColumn> resolved,
                                             final List<String> declaredNames) {
        final List<Row> rows = new ArrayList<>();
        if (resolved != null) {
            for (final TableColumn col : resolved) {
                rows.add(new Row(Arrays.asList(col.getName(),
                    SqlTypeNames.columnMetadata(col.getDataType()),
                    "COLUMN", col.isNullable() ? "Y" : "N", null, "N", "N", null, null,
                    col.getComment(), null, null, null)));
            }
        } else if (declaredNames != null) {
            for (final String colName : declaredNames) {
                rows.add(new Row(Arrays.asList(colName, null, "COLUMN", "Y", null, "N", "N",
                    null, null, null, null, null, null)));
            }
        }
        return new ResultSet(describeColumns(), rows);
    }

    /** DESCRIBE's own column shape — the same thirteen for a table, a view and a materialized view. */
    private List<ResultSetColumn> describeColumns() {
        return Arrays.asList(
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
            new ResultSetColumn("policy name", StringType.VARCHAR),
            new ResultSetColumn("privacy domain", StringType.VARCHAR),
            new ResultSetColumn("write default", StringType.VARCHAR)
        );
    }

    private ResultSet describeTableColumns(final Table described) {
        final List<ResultSetColumn> columns = describeColumns();
        final List<Row> rows = new ArrayList<>();
        // A CHECK constraint reaches the `check` cell of the ONE column it names — a check spanning
        // several columns, or naming none, appears in no cell at all, and where two checks name the
        // same column the later one wins (all live-verified).
        final Map<String, String> checksByColumn = new HashMap<>();
        for (final CheckConstraint check : described.getCheckConstraints()) {
            if (check.describedColumn() != null) {
                checksByColumn.put(check.describedColumn().toUpperCase(Locale.ROOT), check.getExpression());
            }
        }
        for (final TableColumn col : described.getColumns()) {
            // A collated column carries its collation inside the type cell — VARCHAR … COLLATE 'spec'.
            // The type is spelled CANONICALLY, never as the alias the column was declared with:
            // INT and BIGINT both read NUMBER(38,0), STRING reads VARCHAR(16777216) (live-verified).
            final String canonicalType = SqlTypeNames.columnMetadata(col.getDataType());
            final String typeText = col.getCollation() != null
                ? canonicalType + " COLLATE '" + col.getCollation() + "'"
                : canonicalType;
            rows.add(new Row(Arrays.asList(
                col.getName(),
                typeText,
                "COLUMN",
                col.isNullable() ? "Y" : "N",
                describeDefaultText(col),
                col.isPrimaryKey() ? "Y" : "N",
                col.isUnique() ? "Y" : "N",
                checksByColumn.get(col.getName().toUpperCase(Locale.ROOT)),
                null,
                col.getComment(),
                // Only a MASKING policy reaches this cell. A projection policy on the same column
                // leaves it null, and so does a row access policy on the table (live-verified).
                col.getMaskingPolicyName(),
                null,
                null
            )));
        }
        return new ResultSet(columns, rows);
    }

    /** DESCRIBE VIEW: the same column shape as every other relation, refusing as a View. */
    public ResultSet describeView(final String viewName) {
        return describeRelation(viewName, "View");
    }
}
