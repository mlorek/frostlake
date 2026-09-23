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
import dev.frostlake.metastore.DroppedObject;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
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
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.types.ColumnTypeJson;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

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
    private Row objectRow(final OffsetDateTime createdOn, final String name, final String dbName,
                          final String scName, final String kind, final String comment,
                          final String clusterBy, final long rowCount, final String owner,
                          final String retentionTime, final boolean hybrid, final boolean dynamic) {
        return new Row(Arrays.asList(createdOn, name, dbName, scName, kind,
            ShowResultHelpers.text(comment), clusterBy, rowCount, 0L, owner,
            retentionTime, ShowResultHelpers.ownerRoleType(owner),
            hybrid ? "Y" : "N", dynamic ? "Y" : "N", "N", "N"));
    }

    /**
     * The retention SHOW reports, resolved through the chain: the object's own declared value, else
     * its container's, else the account default of 1 — INHERITANCE IS LIVE (live-verified: a schema
     * with no value of its own follows its database's CURRENT value, ALTER included, and UNSET
     * restores exactly that fallback).
     */
    private String effectiveRetention(final Database database, final Schema schema,
                                      final Table table) {
        if (table != null && table.getDataRetentionTimeInDays() != null) {
            return String.valueOf(table.getDataRetentionTimeInDays());
        }
        if (schema != null && schema.getDataRetentionTimeInDays() != null) {
            return String.valueOf(schema.getDataRetentionTimeInDays());
        }
        if (database != null && database.getDataRetentionTimeInDays() != null) {
            return String.valueOf(database.getDataRetentionTimeInDays());
        }
        return DEFAULT_RETENTION_TIME;
    }

    private Database databaseOrNull(final String name) {
        try {
            return name == null ? null : catalog.getDatabase(name);
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    /** Where SHOW DATABASES | SCHEMAS HISTORY places dropped_on: right after retention_time. */
    private static final int DROPPED_ON_AT = 9;

    private final Catalog catalog;

    /** Where a table's rows are, so SHOW TABLES and SHOW OBJECTS can count them. */
    private final StorageEngine storageEngine;

    ShowRelationalExecutor(final Catalog catalog, final StorageEngine storageEngine) {
        this.catalog = catalog;
        this.storageEngine = storageEngine;
    }

    /**
     * The {@code rows} cell of a visible table: the rows committed to it right now. Its {@code bytes}
     * neighbour stays 0 — live's figure is the compressed size of its micro-partitions (512 for one INT
     * row, 1024 for two to a hundred), which in-memory rows cannot reproduce.
     */
    private long storedRows(final String dbName, final Schema schema, final Table table) {
        final String database = schema.getDatabaseName() != null ? schema.getDatabaseName() : dbName;
        return storageEngine.storedRowCount(QualifiedName.key(database, schema.getName(), table.getName()), false);
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
        return showDatabases(false);
    }

    /**
     * SHOW DATABASES; with {@code history}, also the dropped databases UNDROP can still restore, and a
     * {@code dropped_on} column that is NULL for the live ones.
     */
    public ResultSet showDatabases(final boolean history) {
        final List<ResultSetColumn> columns = new ArrayList<>(Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
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
        ));
        if (history) {
            // Right after retention_time, where the account lists it.
            columns.add(DROPPED_ON_AT, new ResultSetColumn("dropped_on", DateTimeType.TIMESTAMP_LTZ));
        }
        final String cur = catalog.getCurrentDatabase();
        final List<Row> rows = new ArrayList<>();
        for (final Database db : catalog.getAllDatabases()) {
            rows.add(databaseRow(db, db.getName().equals(cur), history, null));
        }
        if (history) {
            for (final DroppedObject dropped : catalog.droppedOfKind("DATABASE")) {
                if (dropped.getObject() instanceof Database) {
                    rows.add(databaseRow((Database) dropped.getObject(), false, true, dropped.getDroppedOn()));
                }
            }
            sortByName(rows, 1);
        }
        return new ResultSet(columns, rows);
    }

    /** One SHOW DATABASES row; with {@code history}, followed by its {@code dropped_on}. */
    private Row databaseRow(final Database db, final boolean current, final boolean history,
                            final Instant droppedOn) {
        final List<Object> cells = new ArrayList<>(Arrays.asList(
                ShowResultHelpers.createdOn(db.getCreatedTime()),
                db.getName(),
                "N",
                current ? "Y" : "N",
                "",
                db.getOwner(),
                ShowResultHelpers.text(db.getComment()),
                // options is where SHOW spells transience: the word TRANSIENT, or nothing at all for
                // a permanent object — not a YES/NO cell like INFORMATION_SCHEMA's.
                db.isTransientObject() ? "TRANSIENT" : "",
                effectiveRetention(db, null, null),
                "STANDARD",
                ShowResultHelpers.OWNER_ROLE_TYPE,
                null, null, null
            ));
        if (history) {
            cells.add(DROPPED_ON_AT, ShowResultHelpers.createdOn(droppedOn));
        }
        return new Row(cells);
    }

    /** Orders listing rows by the name in the given column, keeping equal names in their order. */
    private static void sortByName(final List<Row> rows, final int nameColumn) {
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(final Row a, final Row b) {
                return String.valueOf(a.getValue(nameColumn)).compareTo(String.valueOf(b.getValue(nameColumn)));
            }
        });
    }

    public ResultSet showSchemas(final String databaseName) {
        return showSchemas(databaseName, false);
    }

    /**
     * SHOW SCHEMAS; with {@code history}, also the dropped schemas UNDROP can still restore, and a
     * {@code dropped_on} column that is NULL for the live ones.
     */
    public ResultSet showSchemas(final String databaseName, final boolean history) {
        final List<ResultSetColumn> columns = new ArrayList<>(Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
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
        ));
        if (history) {
            // Right after retention_time, where the account lists it.
            columns.add(DROPPED_ON_AT, new ResultSetColumn("dropped_on", DateTimeType.TIMESTAMP_LTZ));
        }
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database selected");
        final String curSchema = catalog.getCurrentSchema();
        final List<Row> rows = new ArrayList<>();
        final Database database = catalog.getDatabase(dbName);
        for (final Schema schema : database.getAllSchemas()) {
            // The current schema is tracked by its stored name; names are unique ignoring case.
            rows.add(schemaRow(database.getName(), schema, schema.getName().equalsIgnoreCase(curSchema), history,
                null));
        }
        if (history) {
            for (final DroppedObject dropped : catalog.droppedOfKind("SCHEMA")) {
                if (dropped.getObject() instanceof Schema
                        && database.getName().equals(((Schema) dropped.getObject()).getDatabaseName())) {
                    rows.add(schemaRow(database.getName(), (Schema) dropped.getObject(), false, true,
                        dropped.getDroppedOn()));
                }
            }
            sortByName(rows, 1);
        }
        return new ResultSet(columns, rows);
    }

    /** One SHOW SCHEMAS row; with {@code history}, followed by its {@code dropped_on}. */
    private Row schemaRow(final String dbName, final Schema schema, final boolean current, final boolean history,
                          final Instant droppedOn) {
        final List<Object> cells = new ArrayList<>(Arrays.asList(
                ShowResultHelpers.createdOn(schema.getCreatedTime()),
                schema.getName(),
                "N",
                current ? "Y" : "N",
                dbName,
                ShowResultHelpers.text(schema.getOwner()),
                ShowResultHelpers.text(schema.getComment()),
                schemaOptions(schema),
                schemaRetention(dbName, schema),
                ShowResultHelpers.ownerRoleType(schema.getOwner()),
                null, null, null, null,
                // Spelled as the text false, not N — live's is_nested convention differs from the
                // Y/N flags beside it.
                "false"
            ));
        if (history) {
            cells.add(DROPPED_ON_AT, ShowResultHelpers.createdOn(droppedOn));
        }
        return new Row(cells);
    }

    /** A schema's retention_time: a transient schema keeps at most one day of what it inherits. */
    private String schemaRetention(final String dbName, final Schema schema) {
        final String effective = effectiveRetention(databaseOrNull(dbName), schema, null);
        if (schema.isTransientObject() && schema.getDataRetentionTimeInDays() == null
                && effective.matches("[0-9]+") && Integer.parseInt(effective) > 1) {
            return "1";
        }
        return effective;
    }

    /** A schema's options cell: TRANSIENT and MANAGED ACCESS as they apply, comma-separated, else empty. */
    private static String schemaOptions(final Schema schema) {
        if (schema.isTransientObject() && schema.isManagedAccess()) {
            return "TRANSIENT, MANAGED ACCESS";
        }
        if (schema.isManagedAccess()) {
            return "MANAGED ACCESS";
        }
        return schema.isTransientObject() ? "TRANSIENT" : "";
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
        return showSchemasInAccount(false);
    }

    /** SHOW SCHEMAS [HISTORY] IN ACCOUNT: every schema of every database, ordered database/name. */
    public ResultSet showSchemasInAccount(final boolean history) {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showSchemas(databaseName, history);
            }
        });
        return across != null ? across : showSchemas(null, history);
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

    /** SHOW HYBRID TABLES IN ACCOUNT: every hybrid table of every database, ordered database/schema/name. */
    public ResultSet showHybridTablesInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showHybridTablesInDatabase(databaseName);
            }
        });
        return across != null ? across : showHybridTables(null);
    }

    /** SHOW TABLES IN DATABASE &lt;db&gt;: every table across all schemas of the database. */
    public ResultSet showTablesInDatabase(final String databaseName) {
        return showTablesScoped(null, databaseName, false);
    }

    /** SHOW HYBRID TABLES: only tables declared with CREATE HYBRID TABLE. */
    public ResultSet showHybridTables(final String schemaName) {
        return hybridLayout(showTablesScoped(schemaName, null, true));
    }

    public ResultSet showHybridTablesInDatabase(final String databaseName) {
        return hybridLayout(showTablesScoped(null, databaseName, true));
    }

    /**
     * A hybrid table's listing has NINE columns of its own, not the table listing's twenty-seven: it
     * carries no kind, no clustering, no retention and none of the is_* flags — a hybrid table is the
     * only thing the listing answers, so it says nothing to tell one from another (live-verified).
     *
     * @param tables the rows as the table listing built them
     * @return the same rows under the hybrid layout
     */
    private ResultSet hybridLayout(final ResultSet tables) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("rows", NumericType.BIGINT),
            new ResultSetColumn("bytes", NumericType.BIGINT),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        for (final Row row : tables.getRows()) {
            rows.add(new Row(Arrays.asList(
                cellOf(tables, row, "created_on"), cellOf(tables, row, "name"),
                cellOf(tables, row, "database_name"), cellOf(tables, row, "schema_name"),
                cellOf(tables, row, "owner"), cellOf(tables, row, "rows"),
                cellOf(tables, row, "bytes"), cellOf(tables, row, "comment"),
                cellOf(tables, row, "owner_role_type"))));
        }
        return new ResultSet(columns, rows);
    }

    /** One named cell of a row built under another listing's layout. */
    private static Object cellOf(final ResultSet listing, final Row row, final String column) {
        final List<ResultSetColumn> columns = listing.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(column)) {
                return row.getValue(i);
            }
        }
        return null;
    }

    private ResultSet showTablesScoped(final String schemaName, final String databaseName,
                                       final boolean hybridOnly) {
        // Live's full SHOW TABLES layout, measured column for column on a real account — the tail
        // (search_optimization*, is_hybrid, is_iceberg, …) is read back through
        // TABLE(RESULT_SCAN(LAST_QUERY_ID())) by real migration scripts, so the names must exist
        // even where the feature is a constant here.
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
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
        final String dbName = databaseName != null ? databaseName : ShowResultHelpers.scopeDatabase(catalog, schemaName);
        // A DYNAMIC table is listed here too, in NAME order among the rest — it is a table that keeps
        // itself up to date, so SHOW TABLES carries it with is_dynamic Y and the kind its transience
        // gives it. SHOW DYNAMIC TABLES is the listing that shows what makes it dynamic.
        final Map<String, Row> byName = new TreeMap<>();
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName, true)) {
            final String scName = schema.getName();
            if (!hybridOnly) {
                for (final DynamicTable dt : schema.getDynamicTables()) {
                    byName.put(scName + "." + dt.getName(), new Row(Arrays.asList(
                        ShowResultHelpers.createdOn(dt.getCreatedTime()),
                        dt.getName(),
                        dbName, scName, dt.isTransient() ? "TRANSIENT" : "TABLE",
                        ShowResultHelpers.text(dt.getComment()),
                        clusterByText(dt.getClusterKeys()),
                        0L,
                        0L,
                        ShowResultHelpers.text(dt.getOwner()),
                        effectiveRetention(databaseOrNull(dbName), schema, null),
                        "OFF",
                        // A dynamic table tracks its changes whether it was asked to or not: the
                        // refresh that keeps it current is what reads them.
                        "ON",
                        "OFF", null, null,
                        "N", "N", "ROLE", "N", "N", "N", "Y", "N", "N", "OFF", "OFF"
                    )));
                }
            }
            for (final Table table : schema.getTables()) {
                if (hybridOnly && !table.isHybrid()) {
                    continue;
                }
                final String kind = table.isHybrid() ? "HYBRID TABLE"
                    : table.isTemporary() ? "TEMPORARY"
                    : table.isTransient() ? "TRANSIENT" : "TABLE";
                byName.put(scName + "." + table.getName(), new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(table.getCreatedTime()),
                    table.getName(),
                    dbName, scName, kind,
                    ShowResultHelpers.text(table.getComment()),
                    clusterByText(table.getClusterKeys()),
                    storedRows(dbName, schema, table),
                    0L,
                    ShowResultHelpers.text(table.getOwner()),
                    effectiveRetention(databaseOrNull(dbName), schema, table),
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
                    "N", table.isSchemaEvolution() ? "Y" : "N", "ROLE", table.isEventTable() ? "Y" : "N",
                    table.isHybrid() ? "Y" : "N",
                    table.getIcebergMetadata() != null ? "Y" : "N", "N", "N", "N", "OFF", "OFF"
                )));
            }
        }
        return new ResultSet(columns, new ArrayList<>(byName.values()));
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
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
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
        final String dbName = databaseName != null ? databaseName : ShowResultHelpers.scopeDatabase(catalog, schemaName);
        // A MATERIALIZED view is listed here too, with is_materialized true — and the two kinds are
        // listed together in NAME order, not one kind after the other (live-verified).
        final Map<String, Row> byName = new TreeMap<>();
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName, true)) {
            final String scName = schema.getName();
            for (final View view : schema.getViews()) {
                byName.put(scName + "." + view.getName(), new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(view.getCreatedTime()),
                    view.getName(),
                    "",
                    dbName, scName,
                    ShowResultHelpers.text(view.getOwner()),
                    ShowResultHelpers.text(view.getComment()),
                    // Snowflake's text column carries the view's full CREATE statement; INFORMATION_SCHEMA's
                    // views were never created and carry none (live-verified).
                    "INFORMATION_SCHEMA".equals(scName) ? ""
                        : view.getListedText() != null ? view.getListedText() : view.ddl(scName + "." + view.getName()),
                    // These two read "true"/"false" on a real account, not the Y/N used elsewhere.
                    String.valueOf(view.isSecure()), "false",
                    ShowResultHelpers.ownerRoleType(view.getOwner()), "OFF"
                )));
            }
            for (final MaterializedView mv : schema.getMaterializedViews()) {
                byName.put(scName + "." + mv.getName(), new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(mv.getCreatedTime()),
                    mv.getName(),
                    "",
                    dbName, scName,
                    ShowResultHelpers.text(mv.getOwner()),
                    ShowResultHelpers.text(mv.getComment()),
                    mv.ddl(scName + "." + mv.getName()),
                    String.valueOf(mv.isSecure()), "true",
                    ShowResultHelpers.ownerRoleType(mv.getOwner()), "OFF"
                )));
            }
        }
        return new ResultSet(columns, new ArrayList<>(byName.values()));
    }

    public ResultSet showMaterializedViews(final String schemaName) {
        return showMaterializedViewsScoped(schemaName, null);
    }

    /** SHOW MATERIALIZED VIEWS IN DATABASE &lt;db&gt;: every materialized view across all schemas of the database. */
    public ResultSet showMaterializedViewsInDatabase(final String databaseName) {
        return showMaterializedViewsScoped(null, databaseName);
    }

    /** SHOW MATERIALIZED VIEWS with no current database: every materialized view of every database. */
    public ResultSet showMaterializedViewsInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showMaterializedViewsInDatabase(databaseName);
            }
        });
        return across != null ? across : showMaterializedViews(null);
    }

    private ResultSet showMaterializedViewsScoped(final String schemaName, final String databaseName) {
        // Live's layout, measured column for column; TERSE answers it whole too. The view names the one
        // table it reads, and a suspended view reads invalid with live's own reason.
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("reserved", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("cluster_by", StringType.VARCHAR),
            new ResultSetColumn("rows", NumericType.BIGINT),
            new ResultSetColumn("bytes", NumericType.BIGINT),
            new ResultSetColumn("source_database_name", StringType.VARCHAR),
            new ResultSetColumn("source_schema_name", StringType.VARCHAR),
            new ResultSetColumn("source_table_name", StringType.VARCHAR),
            new ResultSetColumn("refreshed_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("compacted_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("invalid", StringType.VARCHAR),
            new ResultSetColumn("invalid_reason", StringType.VARCHAR),
            new ResultSetColumn("behind_by", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("text", StringType.VARCHAR),
            new ResultSetColumn("is_secure", StringType.VARCHAR),
            new ResultSetColumn("automatic_clustering", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR)
        );
        final String dbName = databaseName != null ? databaseName : ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName, false)) {
            final String scName = schema.getName();
            for (final MaterializedView mv : schema.getMaterializedViews()) {
                // The times and the lag describe the data the view materialized, not the view itself.
                final Object refreshedOn = MaterializedViewRefresh.refreshedOn(mv);
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(mv.getCreatedTime()),
                    mv.getName(),
                    "",
                    dbName, scName,
                    "",
                    // The counts read as SHOW TABLES reads a table's.
                    0L,
                    0L,
                    ShowResultHelpers.text(mv.getSourceDatabase()),
                    ShowResultHelpers.text(mv.getSourceSchema()),
                    ShowResultHelpers.text(mv.getSourceTable()),
                    refreshedOn,
                    refreshedOn,
                    ShowResultHelpers.text(mv.getOwner()),
                    mv.isSuspended() ? "true" : "false",
                    mv.isSuspended() ? "Marked Materialized View as invalid manually." : null,
                    MaterializedViewRefresh.behindBy(catalog, mv),
                    ShowResultHelpers.text(mv.getComment()),
                    // The full CREATE statement, as written.
                    mv.getListedText() != null ? mv.getListedText() : mv.ddl(scName + "." + mv.getName()),
                    mv.isSecure() ? "true" : "false",
                    "OFF",
                    "ROLE"
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
        // Ordered by RELATION NAME across the three kinds, not kind by kind (live-verified).
        final Map<String, List<Row>> byRelation = new TreeMap<>();
        for (final Table table : schema.getTables()) {
            final List<Row> relationRows = new ArrayList<>();
            addTableColumnRows(relationRows, table, schema.getName(), databaseName);
            byRelation.put(table.getName(), relationRows);
        }
        for (final View v : schema.getViews()) {
            byRelation.put(v.getName(), viewColumnRows(v.getName(), v.getResolvedColumns(),
                v.getColumnNames(), databaseName, schema.getName()));
        }
        for (final MaterializedView mv : schema.getMaterializedViews()) {
            byRelation.put(mv.getName(), viewColumnRows(mv.getName(), mv.getResolvedColumns(),
                mv.getColumnNames(), databaseName, schema.getName()));
        }
        for (final Map.Entry<String, List<Row>> relation : byRelation.entrySet()) {
            rows.addAll(relation.getValue());
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
            throw new RuntimeException(SqlCompilationError.doesNotExistAsSpelled("Schema",
                databaseName.toUpperCase(Locale.ROOT) + "." + schemaName.toUpperCase(Locale.ROOT)));
        }
    }

    /** The database, or live's refusal naming it. */
    private Database resolveDatabaseOrRefuse(final String databaseName) {
        try {
            return catalog.getDatabase(databaseName);
        } catch (final RuntimeException missing) {
            throw new RuntimeException(SqlCompilationError.doesNotExistAsSpelled("Database",
                databaseName.toUpperCase(Locale.ROOT)));
        }
    }

    public ResultSet showColumnsScoped(final String name, final boolean view) {
        final List<ResultSetColumn> columns = describeColumnsShape();
        final List<Row> rows = new ArrayList<>();
        if (name == null) {
            // The unnamed listing walks EVERY relation in the current schema, not just the tables:
            // over one table, one view and one materialized view a live account answers five rows,
            // one per column of all three (live-verified).
            addSchemaColumnRows(rows, ShowResultHelpers.resolveDescribeSchema(catalog),
                catalog.getCurrentDatabase());
            return new ResultSet(columns, rows);
        }
        if (view && QualifiedName.parse(name).size() != 3) {
            // The VIEW spelling requires the FULL search path, and requires it whatever the object
            // turns out to be — live refuses `SHOW COLUMNS IN VIEW t` for a plain TABLE t with this
            // same sentence, and a path of four parts as well, naming its last part as it resolves
            // ("p" is p). It is a property of the keyword, not of the relation (live-verified).
            throw new RuntimeException(SqlCompilationError.of(
                "Must specify the full search path starting from database for "
                    + QualifiedName.parse(name).last()));
        }
        rejectWithoutCurrentDatabase(name);
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
        addTableColumnRows(rows, table, relationSchema(name), relationDatabase(name));
        return new ResultSet(columns, rows);
    }

    /**
     * A one- or two-part name in a session with no current database: live misses the bare name the way
     * any bare lookup misses, and refuses the schema-qualified one by the operation's name.
     */
    private void rejectWithoutCurrentDatabase(final String name) {
        if (catalog.getCurrentDatabase() != null) {
            return;
        }
        final int parts = QualifiedName.parse(name).size();
        if (parts == 1) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", name));
        }
        if (parts == 2) {
            throw NoCurrentDatabaseRefusal.naming("SHOW COLUMNS");
        }
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
                ColumnTypeJson.render(col.getDataType(), col.isNullable(), col.getCollation()),
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
                    ColumnTypeJson.render(col.getDataType(), col.isNullable(), col.getCollation()), col.isNullable(),
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
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
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
        final String dbName = databaseName != null ? databaseName : ShowResultHelpers.scopeDatabase(catalog, schemaName);
        // Every kind is listed in NAME order, not kind by kind, and a materialized view is a VIEW here
        // (live-verified).
        final Map<String, Row> byName = new TreeMap<>();
        for (final Schema schema : resolveScopeSchemas(schemaName, databaseName, true)) {
            final String scName = schema.getName();
            for (final Table t : schema.getTables()) {
                // Live reports transient tables as plain TABLE here, unlike SHOW TABLES.
                byName.put(scName + "." + t.getName(),
                    objectRow(ShowResultHelpers.createdOn(t.getCreatedTime()), t.getName(),
                    dbName, scName, t.isTemporary() ? "TEMPORARY" : "TABLE",
                    ShowResultHelpers.text(t.getComment()),
                    clusterByText(t.getClusterKeys()), storedRows(dbName, schema, t), ShowResultHelpers.text(t.getOwner()),
                    effectiveRetention(databaseOrNull(dbName), schema, t), t.isHybrid(), false));
            }
            for (final View v : schema.getViews()) {
                byName.put(scName + "." + v.getName(),
                    objectRow(ShowResultHelpers.createdOn(v.getCreatedTime()), v.getName(),
                    dbName, scName, "VIEW", ShowResultHelpers.text(v.getComment()),
                    "", 0L, ShowResultHelpers.text(v.getOwner()),
                    effectiveRetention(databaseOrNull(dbName), schema, null), false, false));
            }
            for (final MaterializedView mv : schema.getMaterializedViews()) {
                byName.put(scName + "." + mv.getName(),
                    objectRow(ShowResultHelpers.createdOn(mv.getCreatedTime()), mv.getName(),
                    dbName, scName, "VIEW", ShowResultHelpers.text(mv.getComment()),
                    "", 0L, ShowResultHelpers.text(mv.getOwner()),
                    effectiveRetention(databaseOrNull(dbName), schema, null), false, false));
            }
            for (final DynamicTable dt : schema.getDynamicTables()) {
                byName.put(scName + "." + dt.getName(),
                    objectRow(ShowResultHelpers.createdOn(dt.getCreatedTime()), dt.getName(),
                    // As for a transient TABLE, this listing reports a transient dynamic table as
                    // plain TABLE — SHOW TABLES is the one that spells the transience.
                    dbName, scName, "TABLE", dt.getComment(), "", 0L,
                    ShowResultHelpers.text(dt.getOwner()),
                    effectiveRetention(databaseOrNull(dbName), schema, null), false, true));
            }
        }
        return new ResultSet(columns, new ArrayList<>(byName.values()));
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

    /**
     * Whether a name reaches a table, a view or a materialized view here — the relations a DESCRIBE reads
     * by name, without the dynamic tables, and without refusing a name that reaches none of them.
     *
     * @param name the written name
     * @return whether one of those three carries it
     */
    public boolean namesAStoredRelation(final String name) {
        if (findViewOrNull(name) != null || findMaterializedViewOrNull(name) != null) {
            return true;
        }
        try {
            catalog.resolveTableAsWritten(name, "Table");
            return true;
        } catch (final RuntimeException notATable) {
            return false;
        }
    }

    /**
     * Whether a relation described by name is a table rather than a view or a materialized view, refusing
     * a name that resolves to nothing in the named kind's words.
     */
    boolean namesTable(final String name, final String reportedKind) {
        if (findViewOrNull(name) != null || findMaterializedViewOrNull(name) != null) {
            return false;
        }
        catalog.resolveTableAsWritten(name, reportedKind);
        return true;
    }

    /** See {@link ShowCommandExecutor#describeSchema}. */
    ResultSet describeSchema(final String schemaName) {
        final ResultSet objects = showObjects(schemaName);
        final int createdOn = objects.getColumnIndex("created_on");
        final int name = objects.getColumnIndex("name");
        final int schema = objects.getColumnIndex("schema_name");
        final int kind = objects.getColumnIndex("kind");
        final List<Row> rows = new ArrayList<>();
        for (final Row object : objects.getRows()) {
            final Object created = "INFORMATION_SCHEMA".equals(object.getValue(schema))
                ? ShowResultHelpers.createdOn(Instant.EPOCH) : object.getValue(createdOn);
            rows.add(new Row(Arrays.asList(created, object.getValue(name), object.getValue(kind))));
        }
        return new ResultSet(describedListingColumns(), rows);
    }

    /** See {@link ShowCommandExecutor#describeDatabase}. */
    ResultSet describeDatabase(final String databaseName) {
        final Map<String, Row> byName = new TreeMap<>();
        for (final Schema schema : catalog.getDatabase(databaseName).getAllSchemas()) {
            final Object created = "INFORMATION_SCHEMA".equals(schema.getName())
                ? ShowResultHelpers.createdOn(StatementClock.instant())
                : ShowResultHelpers.createdOn(schema.getCreatedTime());
            byName.put(schema.getName(), new Row(Arrays.asList(created, schema.getName(), "SCHEMA")));
        }
        return new ResultSet(describedListingColumns(), new ArrayList<>(byName.values()));
    }

    /** DESCRIBE SCHEMA's and DESCRIBE DATABASE's three columns. */
    private static List<ResultSetColumn> describedListingColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR));
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
                // A derived column carries its collation in the type cell exactly as a declared one
                // does — the view's SELECT settled it, and DESCRIBE reports what the view produces.
                final String derivedType = SqlTypeNames.columnMetadata(col.getDataType());
                rows.add(new Row(Arrays.asList(col.getName(),
                    col.getCollation() != null && !col.getCollation().isEmpty()
                        ? derivedType + " COLLATE '" + col.getCollation() + "'" : derivedType,
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

    /**
     * DESCRIBE's own column shape — the same thirteen for a table, a view, a materialized view and a dynamic
     * table. The privacy domain is typed OBJECT, whatever it holds (live-verified over the driver's metadata).
     */
    static List<ResultSetColumn> describeColumns() {
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
            new ResultSetColumn("privacy domain", ObjectType.OBJECT),
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
