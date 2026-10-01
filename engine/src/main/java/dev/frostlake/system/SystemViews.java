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

package dev.frostlake.system;

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.LengthlessStringType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemViews {

    private static final Logger logger = LoggerFactory.getLogger(SystemViews.class);

    /** The YES / NO spelling INFORMATION_SCHEMA uses for booleans (SHOW output uses Y / N). */
    private static final String YES_NO_TRUE = "YES";
    private static final String YES_NO_FALSE = "NO";
    /** Data retention, in days: this engine keeps no time-travel history, and a real account's
     *  default for a standard database is 1 — reported as text, as live does. */
    private static final Long DEFAULT_RETENTION_TIME = 1L;
    /** A foreign key's MATCH_OPTION: FULL for every key, inline REFERENCES or table-level (live-verified). */
    private static final String FOREIGN_KEY_MATCH = "FULL";

    /** Every object here is owned by a role, never directly by a user. */
    private static final String OWNER_ROLE_TYPE = "ROLE";

    private final Catalog catalog;

    /** Where a table's rows are, so TABLES can count them as it lists the table. */
    private final StorageEngine storageEngine;

    public SystemViews(final Catalog catalog, final StorageEngine storageEngine) {
        this.catalog = catalog;
        this.storageEngine = storageEngine;
    }

    // ── DATABASES ─────────────────────────────────────────────────────────────

    public ResultSet queryDatabases() {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("DATABASE_NAME"), col("DATABASE_OWNER"), colYesNo("IS_TRANSIENT"), col("COMMENT"),
            colLtz("CREATED"), colLtz("LAST_ALTERED"), colLong("RETENTION_TIME"), colText("TYPE", 19),
            col("REPLICABLE_WITH_FAILOVER_GROUPS"), col("OWNER_ROLE_TYPE")
        ));
        for (final Database db : catalog.getAllDatabases()) {
            result.addRow(new Row(
                // The same fact SHOW spells as the word TRANSIENT reads YES/NO here — the two
                // metadata surfaces disagree on spelling by design.
                db.getName(), db.getOwner(), db.isTransientObject() ? "YES" : YES_NO_FALSE,
                db.getComment(),
                db.getCreatedTime(), db.getCreatedTime(),
                db.getDataRetentionTimeInDays() != null
                    ? Long.valueOf(db.getDataRetentionTimeInDays().longValue()) : DEFAULT_RETENTION_TIME,
                "STANDARD",
                "UNSET", OWNER_ROLE_TYPE
            ));
        }
        return result;
    }

    // ── SCHEMATA ──────────────────────────────────────────────────────────────

    public ResultSet querySchemata(final String databaseName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("CATALOG_NAME"), col("SCHEMA_NAME"), colText("SCHEMA_OWNER", 134217728),
            colYesNo("IS_TRANSIENT"), colYesNo("IS_MANAGED_ACCESS"), colLong("RETENTION_TIME"),
            colNull("DEFAULT_CHARACTER_SET_CATALOG"), colNull("DEFAULT_CHARACTER_SET_SCHEMA"),
            colNull("DEFAULT_CHARACTER_SET_NAME"), colNull("SQL_PATH"),
            colLtz("CREATED"), colLtz("LAST_ALTERED"), col("COMMENT"),
            colText("REPLICABLE_WITH_FAILOVER_GROUPS", 134217728), colText("OWNER_ROLE_TYPE", 134217728)
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : db.getAllSchemas()) {
                result.addRow(new Row(
                    db.getName(), schema.getName(), schema.getOwner(),
                    schema.isTransientObject() ? "YES" : YES_NO_FALSE,
                    YES_NO_FALSE,
                    // The schema's own value, else its database's, else the account default — the
                    // same live-resolved inheritance SHOW SCHEMAS reports.
                    schema.getDataRetentionTimeInDays() != null
                        ? Long.valueOf(schema.getDataRetentionTimeInDays().longValue())
                        : db.getDataRetentionTimeInDays() != null
                            ? Long.valueOf(db.getDataRetentionTimeInDays().longValue())
                            : DEFAULT_RETENTION_TIME,
                    null, null, null, null,
                    schema.getCreatedTime(), schema.getCreatedTime(), schema.getComment(),
                    "UNSET", OWNER_ROLE_TYPE
                ));
            }
        }
        return result;
    }

    // ── TABLES ────────────────────────────────────────────────────────────────

    /**
     * INFORMATION_SCHEMA.TABLES — every table-shaped object in the database, not just base tables.
     *
     * <p>Live Snowflake (verified against a database holding one of each) lists views,
     * materialized views and dynamic tables here alongside tables, and reports TABLE_TYPE from a
     * four-value vocabulary:
     *
     * <ul>
     *   <li>{@code BASE TABLE} — a permanent table, a transient table (told apart by
     *       {@code IS_TRANSIENT}) and a dynamic table alike;</li>
     *   <li>{@code LOCAL TEMPORARY} — a temporary table;</li>
     *   <li>{@code VIEW} — a view;</li>
     *   <li>{@code MATERIALIZED VIEW} — a materialized view.</li>
     * </ul>
     *
     * <p>Snowflake also uses {@code EXTERNAL TABLE}, which Frostlake has no concept of.
     * "TRANSIENT TABLE" and "TEMPORARY TABLE" — the values emitted before — are not Snowflake
     * vocabulary at all; as live, transience is carried by {@code IS_TRANSIENT} instead, which keeps
     * the Y/N spelling it shares with the DATABASES and SCHEMATA views here (live spells it YES/NO).
     */
    public ResultSet queryTables(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("TABLE_OWNER"), colText("TABLE_TYPE", 134217728),
            colText("IS_TRANSIENT", 134217728), colText("CLUSTERING_KEY", 134217728),
            colLong("ROW_COUNT"), colLong("BYTES"), colLong("RETENTION_TIME"),
            colNull("SELF_REFERENCING_COLUMN_NAME"), colNull("REFERENCE_GENERATION"),
            colNull("USER_DEFINED_TYPE_CATALOG"), colNull("USER_DEFINED_TYPE_SCHEMA"),
            colNull("USER_DEFINED_TYPE_NAME"), colYesNo("IS_INSERTABLE_INTO"), colYesNo("IS_TYPED"),
            colNull("COMMIT_ACTION"), colLtz("CREATED"), colLtz("LAST_ALTERED"),
            colLtz("LAST_DDL"), col("LAST_DDL_BY"), colYesNo("AUTO_CLUSTERING_ON"), col("COMMENT"),
            colYesNo("IS_TEMPORARY"), colYesNo("IS_ICEBERG"), colYesNo("IS_DYNAMIC"), colYesNo("IS_IMMUTABLE"),
            colYesNo("IS_HYBRID"), colYesNo("ROW_TIMESTAMP_ON"), colYesNo("ERROR_LOGGING"), colYesNo("IS_INTERACTIVE")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                // A permanent table a temporary one hides is listed beside it (live-verified), and
                // counted from the rows hidden under the shared name.
                final List<Table> tables = schema.getTables();
                final int visible = tables.size();
                tables.addAll(schema.getShadowedTables());
                for (int i = 0; i < tables.size(); i++) {
                    final Table table = tables.get(i);
                    final String type = table.isTemporary() ? "LOCAL TEMPORARY" : "BASE TABLE";
                    // ROW_COUNT is the committed rows; BYTES stays 0 — live's figure is the compressed
                    // size of its micro-partitions (512 for one INT row, 1024 for two to a hundred,
                    // 2048 for one row of a thousand random characters), which in-memory rows cannot
                    // reproduce.
                    final long rowCount = storageEngine.storedRowCount(
                        QualifiedName.key(db.getName(), schema.getName(), table.getName()), i >= visible);
                    result.addRow(new Row(
                        db.getName(), schema.getName(), table.getName(), table.getOwner(),
                        type, yesNo(table.isTransient()),
                        table.getClusterKeys().isEmpty() ? null : String.join(", ", table.getClusterKeys()),
                        rowCount, 0L, DEFAULT_RETENTION_TIME,
                        null, null, null, null, null, YES_NO_TRUE, YES_NO_TRUE, null,
                        table.getCreatedTime(), table.getCreatedTime(), table.getCreatedTime(),
                        table.getLastDdlBy(), YES_NO_FALSE, table.getComment(),
                        yesNo(table.isTemporary()), YES_NO_FALSE, YES_NO_FALSE, YES_NO_FALSE,
                        yesNo(table.isHybrid()), YES_NO_FALSE, YES_NO_FALSE, YES_NO_FALSE
                    ));
                }
                for (final View view : schema.getViews()) {
                    result.addRow(viewRow(db, schema, view.getName(), view.getOwner(), view.getLastDdlBy(),
                        "VIEW", systemViewTime(schema, view), view.getComment(), null));
                }
                for (final MaterializedView view : schema.getMaterializedViews()) {
                    result.addRow(viewRow(db, schema, view.getName(), view.getOwner(), view.getLastDdlBy(),
                        "MATERIALIZED VIEW", view.getCreatedTime(), view.getComment(), DEFAULT_RETENTION_TIME));
                }
                for (final DynamicTable dynamicTable : schema.getDynamicTables()) {
                    result.addRow(tableShapedRow(db, schema, dynamicTable.getName(), dynamicTable.getOwner(),
                        dynamicTable.getLastDdlBy(), "BASE TABLE", dynamicTable.getCreatedTime(), dynamicTable.getComment(),
                        dynamicTable.isTransient()));
                }
            }
        }
        return result;
    }

    /**
     * A TABLES row for a view or a materialized view (live-verified): no transience at all, and a
     * retention time only for a materialized view, which keeps data — a view's, INFORMATION_SCHEMA's own
     * included, is empty.
     */
    private Row viewRow(final Database db, final Schema schema, final String name, final String owner,
                        final String lastDdlBy, final String type, final Instant created, final String comment,
                        final Long retention) {
        return shapedRow(db, schema, name, owner, lastDdlBy, type, created, comment, null, retention);
    }

    /** A dynamic table's TABLES row: listed as a BASE TABLE, TRANSIENT as it was declared, retained a day. */
    private Row tableShapedRow(final Database db, final Schema schema, final String name,
                               final String owner, final String lastDdlBy, final String type, final Instant created,
                               final String comment, final boolean transientRelation) {
        return shapedRow(db, schema, name, owner, lastDdlBy, type, created, comment,
            transientRelation ? YES_NO_TRUE : YES_NO_FALSE, DEFAULT_RETENTION_TIME);
    }

    /** A relation with no stored rows of its own in the TABLES row shape: no clustering key, no sizes. */
    private Row shapedRow(final Database db, final Schema schema, final String name, final String owner,
                          final String lastDdlBy, final String type, final Instant created, final String comment,
                          final String transience, final Long retention) {
        return new Row(
            db.getName(), schema.getName(), name, owner, type,
            transience, null, null, null, retention,
            null, null, null, null, null, YES_NO_TRUE, YES_NO_TRUE, null,
            created, created, created, lastDdlBy, YES_NO_FALSE, comment,
            YES_NO_FALSE, YES_NO_FALSE, "DYNAMIC TABLE".equals(type) ? YES_NO_TRUE : YES_NO_FALSE,
            YES_NO_FALSE, YES_NO_FALSE, YES_NO_FALSE, YES_NO_FALSE, YES_NO_FALSE
        );
    }

    /**
     * A view's creation time as INFORMATION_SCHEMA.TABLES and VIEWS report it: none at all for one of
     * INFORMATION_SCHEMA's own views, which were never created (live-verified), and the view's own otherwise.
     */
    private static Instant systemViewTime(final Schema schema, final View view) {
        return "INFORMATION_SCHEMA".equals(schema.getName()) ? null : view.getCreatedTime();
    }

    // ── COLUMNS ───────────────────────────────────────────────────────────────

    /**
     * INFORMATION_SCHEMA.COLUMNS — one row per column of every table-shaped object, VIEWS AND
     * MATERIALIZED VIEWS INCLUDED.
     *
     * <p>Live Snowflake (verified on a real account, over a table carrying a spread of
     * types) reports a view's columns in exactly the same row shape as a table's — same DATA_TYPE
     * vocabulary, same precision/scale/length — so a browsing tool that lists views (which
     * {@code getTables} does) can expand one and see its columns. Three of the columns are NOT
     * inherited from the base table, all three measured:
     *
     * <ul>
     *   <li>{@code COLUMN_DEFAULT} is null for a view over a column that has a DEFAULT;</li>
     *   <li>{@code IS_IDENTITY} is NO for a view over an IDENTITY column;</li>
     *   <li>{@code IS_PRIMARY_KEY} is NO — a view has no constraints.</li>
     * </ul>
     *
     * <p>{@code IS_NULLABLE}, by contrast, IS inherited live: a view over a NOT NULL column reports
     * NO. Frostlake reports YES for every view column, because the nullability of a projected
     * expression is not something the projection channel carries — the one deliberate divergence here,
     * and the lenient direction of it.
     *
     * <p>What a view reports comes off {@link View#getResolvedColumns()}, frozen when the view was
     * created; nothing in this method plans a query, so a metadata read can never re-enter the
     * metadata layer that serves it.
     */
    public ResultSet queryColumns(final String databaseName, final String schemaName, final String tableName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("COLUMN_NAME"), colInt("ORDINAL_POSITION"),
            colText("COLUMN_DEFAULT", 134217728), colYesNo("IS_NULLABLE"),
            col("DATA_TYPE"), colInt("CHARACTER_MAXIMUM_LENGTH"), colInt("CHARACTER_OCTET_LENGTH"),
            colInt("NUMERIC_PRECISION"), colInt("NUMERIC_PRECISION_RADIX", 2), colInt("NUMERIC_SCALE"),
            colInt("DATETIME_PRECISION"), colNull("INTERVAL_TYPE"), colLong("INTERVAL_PRECISION"),
            colNull("CHARACTER_SET_CATALOG"), colNull("CHARACTER_SET_SCHEMA"), colNull("CHARACTER_SET_NAME"),
            colNull("COLLATION_CATALOG"), colNull("COLLATION_SCHEMA"), col("COLLATION_NAME"),
            colNull("DOMAIN_CATALOG"), colNull("DOMAIN_SCHEMA"), colNull("DOMAIN_NAME"),
            colNull("UDT_CATALOG"), colNull("UDT_SCHEMA"), colNull("UDT_NAME"),
            colNull("SCOPE_CATALOG"), colNull("SCOPE_SCHEMA"), colNull("SCOPE_NAME"),
            colNull("MAXIMUM_CARDINALITY"), colText("DTD_IDENTIFIER", 134217728), colText("IS_SELF_REFERENCING", 2),
            colYesNo("IS_IDENTITY"), colText("IDENTITY_GENERATION", 134217728),
            colLong("IDENTITY_START"), colLong("IDENTITY_INCREMENT"),
            colNull("IDENTITY_MAXIMUM"), colNull("IDENTITY_MINIMUM"),
            colText("IDENTITY_CYCLE", 134217728), colText("IDENTITY_ORDERED", 134217728),
            col("SCHEMA_EVOLUTION_RECORD"), col("DATA_TYPE_ALIAS"), col("COMMENT"),
            col("EXPRESSION"), colText("KIND", 134217728)
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : tablesFor(schema, tableName)) {
                    int pos = 1;
                    for (final TableColumn col : table.getColumns()) {
                        // The position the column was GIVEN, not where it sits today: a dropped column
                        // leaves its number behind, and no later column takes it.
                        result.addRow(columnRow(db, schema, table.getName(), col,
                            col.getOrdinalPosition() > 0 ? col.getOrdinalPosition() : pos, true));
                        pos++;
                    }
                }
                for (final View view : schema.getViews()) {
                    if (matchesName(view.getName(), tableName)) {
                        addDerivedColumnRows(result, db, schema, view.getName(), view.getResolvedColumns());
                    }
                }
                for (final MaterializedView view : schema.getMaterializedViews()) {
                    if (matchesName(view.getName(), tableName)) {
                        addDerivedColumnRows(result, db, schema, view.getName(), view.getResolvedColumns());
                    }
                }
            }
        }
        return result;
    }

    /** The base tables a COLUMNS read covers: one named table, or all of them, each with the permanent
     *  table a temporary one of its name hides (live-verified). A name that is not a table (a view's,
     *  say) selects no table rather than failing the whole read. */
    private List<Table> tablesFor(final Schema schema, final String tableName) {
        if (tableName == null) {
            final List<Table> tables = schema.getTables();
            tables.addAll(schema.getShadowedTables());
            return tables;
        }
        if (!schema.hasTable(tableName)) {
            return Collections.<Table>emptyList();
        }
        final Table table = schema.getTable(tableName);
        final Table hidden = schema.shadowedTable(table.getName());
        return hidden == null ? Collections.singletonList(table) : Arrays.asList(table, hidden);
    }

    private boolean matchesName(final String name, final String requested) {
        return requested == null || name.equalsIgnoreCase(requested);
    }

    /** The COLUMNS rows for a view or materialized view, from the column list frozen at its creation.
     *  A relation whose columns were never resolved contributes nothing, as it always did. */
    private void addDerivedColumnRows(final ResultSet result, final Database db, final Schema schema,
                                      final String relationName, final List<TableColumn> columns) {
        if (columns == null) {
            return;
        }
        int pos = 1;
        for (final TableColumn col : columns) {
            result.addRow(columnRow(db, schema, relationName, col, pos++, false));
        }
    }

    /**
     * One COLUMNS row. {@code fromBaseTable} tells apart the columns of a real table — which carry a
     * DEFAULT, an IDENTITY and a PRIMARY KEY — from a view's, which live reports without any of the
     * three however the underlying column was declared.
     */
    private Row columnRow(final Database db, final Schema schema, final String relationName,
                          final TableColumn col, final int position, final boolean fromBaseTable) {
        final DataType dataType = col.getDataType();
        final String typeName = dataType.getName().toUpperCase();
        // An interval reports its fields without their precisions, INTERVAL DAY TO SECOND (live-verified).
        final String canonical = dataType instanceof IntervalDayTimeType
            ? "INTERVAL " + ((IntervalDayTimeType) dataType).getQualifier().fieldsName()
            : dataType instanceof IntervalYearMonthType
            ? "INTERVAL " + ((IntervalYearMonthType) dataType).getQualifier().fieldsName()
            : canonicalDataType(typeName);
        // A string's length stops at the widest a column can declare, as DESCRIBE's does — a view over
        // UPPER or || of a 16MB column reads 16777216 here although the expression itself is wider —
        // and its octet length is four bytes a character, uncapped: VARCHAR(5) reads 20 and the
        // 16MB maximum 67108864 (live-verified).
        // A UUID reports neither length, as a BINARY does not (live-verified).
        final Integer charLen = dataType instanceof StringType && !(dataType instanceof UuidType)
            ? Integer.valueOf((int) Math.min(((StringType) dataType).getMaxLength(),
                SqlTypeNames.DESCRIBED_STRING_MAXIMUM)) : null;
        // A BINARY column reports NEITHER length, declared width or not — live leaves both NULL for a
        // table column, a view column and a CTAS column alike; its size reaches JDBC through SHOW
        // COLUMNS instead (live-verified).
        final Integer octetLen = charLen == null ? null : Integer.valueOf(charLen.intValue() * 4);
        // Live reports precision/scale only for the exact-numeric family: a FLOAT column
        // leaves both NULL, and every non-numeric type leaves both NULL too.
        Integer numPrec = null;
        Integer numScale = null;
        if (dataType instanceof NumericType && !"FLOAT".equals(canonical)) {
            numPrec = Integer.valueOf(((NumericType) dataType).getPrecision());
            numScale = Integer.valueOf(((NumericType) dataType).getScale());
        }
        final Integer dateTimePrec = dataType instanceof DateTimeType && !"DATE".equals(canonical)
            ? Integer.valueOf(((DateTimeType) dataType).getPrecision()) : null;
        final boolean identity = fromBaseTable && col.isAutoIncrement();
        // The SQL-standard placeholder columns (character-set / collation / domain / UDT / scope
        // catalogs, cardinality, DTD identifier) are NULL on a real account for every column, so
        // they are NULL here too rather than invented.
        return new Row(
            db.getName(), schema.getName(), relationName,
            col.getName(), position,
            fromBaseTable && col.getDefaultValue() != null ? col.getDefaultValue().toString() : null,
            // A view's column carries its NOT NULL here exactly as it does in DESCRIBE VIEW's null?
            // cell — live-verified, including an expression column reading YES and an outer join's
            // null-extended side losing it. This cell is NOT one of the three the fromBaseTable flag
            // suppresses.
            col.isNullable() ? YES_NO_TRUE : YES_NO_FALSE,
            canonical, charLen, octetLen,
            numPrec, numPrec == null ? null : Integer.valueOf(10), numScale,
            dateTimePrec, null, null,
            null, null, null,
            null, null, col.getCollation(),
            null, null, null,
            null, null, null,
            null, null, null,
            null, null, YES_NO_FALSE,
            identity ? YES_NO_TRUE : YES_NO_FALSE, identity ? "BY DEFAULT" : null,
            identity ? Long.valueOf(col.getIdentityStart()) : null,
            identity ? Long.valueOf(col.getIdentityIncrement()) : null,
            null, null,
            identity ? YES_NO_FALSE : null, identity ? YES_NO_FALSE : null,
            null, dataType.getName(), fromBaseTable ? col.getComment() : null,
            null, null
        );
    }

    /**
     * Snowflake's INFORMATION_SCHEMA.COLUMNS reports DATA_TYPE by canonical family name
     * (live-verified): every integer/NUMBER/DECIMAL flavor is NUMBER, every character flavor is
     * TEXT, every floating-point flavor is FLOAT, and plain TIMESTAMP surfaces as TIMESTAMP_NTZ.
     * DATE/TIME/BOOLEAN/BINARY/VARIANT/OBJECT/ARRAY and the zoned timestamps pass through.
     * This mapping applies ONLY here — DESCRIBE TABLE and SHOW COLUMNS keep their own shapes.
     */
    private String canonicalDataType(final String typeName) {
        switch (typeName) {
            case "INTEGER":
            case "INT":
            case "BIGINT":
            case "SMALLINT":
            case "TINYINT":
            case "BYTEINT":
            case "NUMBER":
            case "DECIMAL":
            case "NUMERIC":
                return "NUMBER";
            case "VARCHAR":
            case "CHAR":
            case "CHARACTER":
            case "STRING":
            case "TEXT":
                return "TEXT";
            case "FLOAT":
            case "FLOAT4":
            case "FLOAT8":
            case "DOUBLE":
            case "DOUBLE PRECISION":
            case "REAL":
                return "FLOAT";
            case "TIMESTAMP":
            case "DATETIME":
                return "TIMESTAMP_NTZ";
            case "VARBINARY":
                return "BINARY";
            default:
                return typeName;
        }
    }

    // ── VIEWS ─────────────────────────────────────────────────────────────────

    public ResultSet queryViews(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("TABLE_OWNER"), col("VIEW_DEFINITION"), colText("CHECK_OPTION", 4),
            colText("IS_UPDATABLE", 2), colText("INSERTABLE_INTO", 2), colYesNo("IS_SECURE"),
            colLtz("CREATED"), colLtz("LAST_ALTERED"), colLtz("LAST_DDL"), col("LAST_DDL_BY"),
            col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final View view : schema.getViews()) {
                    // Snowflake's VIEW_DEFINITION is the view's full executable DDL, not just its
                    // query: deployment tooling recreates views via EXECUTE IMMEDIATE of this text.
                    // INFORMATION_SCHEMA's own views were never created: no definition, no times.
                    final Instant created = systemViewTime(schema, view);
                    result.addRow(new Row(
                        db.getName(), schema.getName(), view.getName(), view.getOwner(),
                        created == null ? null : view.getDefinitionText() != null ? view.getDefinitionText()
                            : view.ddl(schema.getName() + "." + view.getName()), "NONE",
                        YES_NO_FALSE, YES_NO_FALSE, yesNo(view.isSecure()),
                        created, created, created,
                        view.getLastDdlBy(), view.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── TABLE_CONSTRAINTS ─────────────────────────────────────────────────────

    public ResultSet queryTableConstraints(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("CONSTRAINT_CATALOG"), col("CONSTRAINT_SCHEMA"), col("CONSTRAINT_NAME"),
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            colText("CONSTRAINT_TYPE", 134217728), colYesNo("IS_DEFERRABLE"), colYesNo("INITIALLY_DEFERRED"),
            colYesNo("ENFORCED"), col("COMMENT"), colLtz("CREATED"), colLtz("LAST_ALTERED"), colYesNo("RELY")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : schema.getTables()) {
                    // ONE row per CONSTRAINT, never one per column (live-verified): a composite
                    // PRIMARY KEY (a, b) is a single constraint under a single name, and so is a
                    // multi-column UNIQUE. Frostlake models both as per-column flags, so the row count
                    // comes from the table's constraint view of them, not from its column list.
                    final String primaryKeyName = table.primaryKeyConstraintName();
                    if (primaryKeyName != null) {
                        result.addRow(constraintRow(db, schema, table, primaryKeyName, "PRIMARY KEY",
                            relyOfColumns(table, table.getPrimaryKeys())));
                    }
                    for (final UniqueConstraint unique : table.getUniqueConstraints()) {
                        result.addRow(constraintRow(db, schema, table, unique.getConstraintName(), "UNIQUE",
                            relyOfColumns(table, unique.getColumnNames())));
                    }
                    // An inline REFERENCES is its own single-column FOREIGN KEY constraint.
                    for (final TableColumn col : table.getColumns()) {
                        if (col.hasForeignKey()) {
                            result.addRow(constraintRow(db, schema, table,
                                table.columnForeignKeyConstraintName(col.getName()), "FOREIGN KEY",
                                col.getRely()));
                        }
                    }
                    // Table-level FOREIGN KEY constraints (FOREIGN KEY (cols) REFERENCES …) live in the
                    // constraint list rather than as a column reference like an inline REFERENCES, so emit
                    // them too. Snowflake does not distinguish the two declaration forms in TABLE_CONSTRAINTS.
                    for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
                        result.addRow(constraintRow(db, schema, table, fk.getConstraintName(), "FOREIGN KEY",
                            fk.getRely()));
                    }
                }
            }
        }
        return result;
    }

    // One TABLE_CONSTRAINTS row for one constraint, under the name it reports everywhere else: the name an
    // explicit CONSTRAINT <name> clause gave it, else the SYS_CONSTRAINT_<uuid> it auto-named itself with.
    private Row constraintRow(final Database db, final Schema schema, final Table table,
                              final String name, final String type, final Boolean rely) {
        // Live-verified on a real account: Snowflake reports IS_DEFERRABLE = NO but
        // INITIALLY_DEFERRED = YES for PRIMARY KEY, UNIQUE and FOREIGN KEY alike, and ENFORCED is a
        // constant NO — a table declared PRIMARY KEY RELY still shows ENFORCED = NO with RELY = YES,
        // so ENFORCED does not track RELY.
        final String relyText = rely != null && rely ? YES_NO_TRUE : YES_NO_FALSE;
        return new Row(db.getName(), schema.getName(), name,
                       db.getName(), schema.getName(), table.getName(),
                       type, YES_NO_FALSE, YES_NO_TRUE, YES_NO_FALSE, null,
                       table.getCreatedTime(), table.getCreatedTime(), relyText);
    }

    // RELY for a constraint spanning several columns: RELY is declared per column in Frostlake, so the
    // constraint relies when any of its columns does.
    private Boolean relyOfColumns(final Table table, final List<String> columnNames) {
        for (final String columnName : columnNames) {
            if (table.hasColumn(columnName) && Boolean.TRUE.equals(table.getColumn(columnName).getRely())) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    // The name of the PRIMARY KEY constraint a foreign key points at — REFERENTIAL_CONSTRAINTS reports it
    // as the referenced (unique) constraint. Null when the referenced table is unknown or has no PK.
    private String referencedPrimaryKeyName(final Schema schema, final String referencedTable) {
        final String bare = QualifiedName.parse(referencedTable).last();
        Table target = schema.hasTable(bare) ? schema.getTable(bare) : null;
        if (target == null) {
            try {
                target = catalog.resolveTable(referencedTable);
            } catch (final RuntimeException e) {
                logger.debug("Referenced table {} not resolvable for REFERENTIAL_CONSTRAINTS", referencedTable);
                return null;
            }
        }
        return target != null ? target.primaryKeyConstraintName() : null;
    }

    // ── REFERENTIAL_CONSTRAINTS ───────────────────────────────────────────────

    public ResultSet queryReferentialConstraints(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("CONSTRAINT_CATALOG"), col("CONSTRAINT_SCHEMA"), col("CONSTRAINT_NAME"),
            col("UNIQUE_CONSTRAINT_CATALOG"), col("UNIQUE_CONSTRAINT_SCHEMA"), col("UNIQUE_CONSTRAINT_NAME"),
            colText("MATCH_OPTION", 134217728), colText("UPDATE_RULE", 134217728), colText("DELETE_RULE", 134217728), col("COMMENT"),
            colLtz("CREATED"), colLtz("LAST_ALTERED")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : schema.getTables()) {
                    for (final TableColumn col : table.getColumns()) {
                        if (col.hasForeignKey()) {
                            result.addRow(new Row(
                                db.getName(), schema.getName(),
                                table.columnForeignKeyConstraintName(col.getName()),
                                db.getName(), schema.getName(),
                                referencedPrimaryKeyName(schema, col.getReferencedTable()),
                                FOREIGN_KEY_MATCH, "NO ACTION", "NO ACTION", null,
                                table.getCreatedTime(), table.getCreatedTime()
                            ));
                        }
                    }
                    // Table-level FOREIGN KEY constraints (one referential row per constraint).
                    for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
                        result.addRow(new Row(
                            db.getName(), schema.getName(),
                            fk.getConstraintName(),
                            db.getName(), schema.getName(),
                            referencedPrimaryKeyName(schema, fk.getReferencedTable()),
                            FOREIGN_KEY_MATCH,
                            fk.getOnUpdate() != null ? fk.getOnUpdate() : "NO ACTION",
                            fk.getOnDelete() != null ? fk.getOnDelete() : "NO ACTION",
                            null, table.getCreatedTime(), table.getCreatedTime()
                        ));
                    }
                }
            }
        }
        return result;
    }

    // ── PROCEDURES ────────────────────────────────────────────────────────────

    public ResultSet queryProcedures(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("PROCEDURE_CATALOG"), col("PROCEDURE_SCHEMA"), col("PROCEDURE_NAME"),
            col("PROCEDURE_OWNER"), col("ARGUMENT_SIGNATURE"), col("DATA_TYPE"),
            colInt("CHARACTER_MAXIMUM_LENGTH"), colInt("CHARACTER_OCTET_LENGTH"),
            colInt("NUMERIC_PRECISION"), colInt("NUMERIC_PRECISION_RADIX", 2), colInt("NUMERIC_SCALE"),
            colText("PROCEDURE_LANGUAGE", 134217728), col("PROCEDURE_DEFINITION"),
            colLtz("CREATED"), colLtz("LAST_ALTERED"), col("COMMENT"),
            col("EXTERNAL_ACCESS_INTEGRATIONS"), col("SECRETS"),
            col("RUNTIME_VERSION"), col("PACKAGES"), colText("INSTALLED_PACKAGES", 134217728),
            col("ARTIFACT_REPOSITORY")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Procedure proc : schema.getProcedures()) {
                    final DataType returns = proc.getReturnType();
                    final boolean exactNumeric = returns instanceof NumericType
                        && !"FLOAT".equals(returns.getName().toUpperCase());
                    result.addRow(new Row(
                        db.getName(), schema.getName(), proc.getName(), proc.getOwner(),
                        buildArgSignature(proc.getParameters()), returns.getName(),
                        null, null,
                        exactNumeric ? Integer.valueOf(((NumericType) returns).getPrecision()) : null,
                        exactNumeric ? Integer.valueOf(10) : null,
                        exactNumeric ? Integer.valueOf(((NumericType) returns).getScale()) : null,
                        proc.getLanguage(), proc.getBody(),
                        proc.getCreatedTime(), proc.getCreatedTime(), proc.getComment(),
                        null, null,
                        proc.getRuntimeVersion(), listOrNull(proc.getPackages()), null, null
                    ));
                }
            }
        }
        return result;
    }

    // ── FUNCTIONS ─────────────────────────────────────────────────────────────

    public ResultSet queryFunctions(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("FUNCTION_CATALOG"), col("FUNCTION_SCHEMA"), col("FUNCTION_NAME"),
            col("FUNCTION_OWNER"), col("ARGUMENT_SIGNATURE"), col("DATA_TYPE"),
            colInt("CHARACTER_MAXIMUM_LENGTH"), colInt("CHARACTER_OCTET_LENGTH"),
            colInt("NUMERIC_PRECISION"), colInt("NUMERIC_PRECISION_RADIX", 2), colInt("NUMERIC_SCALE"),
            colText("FUNCTION_LANGUAGE", 134217728), col("FUNCTION_DEFINITION"),
            colText("VOLATILITY", 134217728), colYesNo("IS_NULL_CALL"), colYesNo("IS_SECURE"),
            colLtz("CREATED"), colLtz("LAST_ALTERED"), col("COMMENT"),
            colYesNo("IS_EXTERNAL"), col("API_INTEGRATION"), col("CONTEXT_HEADERS"),
            colLong("MAX_BATCH_ROWS"), col("REQUEST_TRANSLATOR"), col("RESPONSE_TRANSLATOR"),
            col("COMPRESSION"), col("IMPORTS"), col("HANDLER"), col("TARGET_PATH"),
            col("RUNTIME_VERSION"), col("PACKAGES"), colText("INSTALLED_PACKAGES", 134217728),
            colYesNo("IS_MEMOIZABLE"), col("EXTERNAL_ACCESS_INTEGRATIONS"), col("SECRETS"),
            colYesNo("IS_DATA_METRIC"), colYesNo("IS_AGGREGATE"), col("ARTIFACT_REPOSITORY")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Function func : schema.getFunctions()) {
                    // A table function has no IS_TABLE_FUNCTION flag on a real account — its
                    // DATA_TYPE reads "TABLE (COL TYPE, …)" instead, which is what marks it.
                    final DataType returns = func.getReturnType();
                    final String dataType = func.isTableFunction()
                        ? "TABLE (" + columnSignature(func.getReturnColumns()) + ")"
                        : returns.getName();
                    final boolean exactNumeric = !func.isTableFunction()
                        && returns instanceof NumericType && !"FLOAT".equals(returns.getName().toUpperCase());
                    result.addRow(new Row(
                        db.getName(), schema.getName(), func.getName(), func.getOwner(),
                        buildArgSignature(func.getParameters()), dataType,
                        null, null,
                        exactNumeric ? Integer.valueOf(((NumericType) returns).getPrecision()) : null,
                        exactNumeric ? Integer.valueOf(10) : null,
                        exactNumeric ? Integer.valueOf(((NumericType) returns).getScale()) : null,
                        func.getLanguage(), func.getBody(),
                        func.getVolatility(), yesNo("CALLED ON NULL INPUT".equals(func.getNullHandling())),
                        yesNo(func.isSecure()),
                        func.getCreatedTime(), func.getCreatedTime(), func.getComment(),
                        YES_NO_FALSE, null, null, null, null, null,
                        null, listOrNull(func.getImports()), func.getHandler(), null,
                        func.getRuntimeVersion(), null, null,
                        yesNo(func.isMemoizable()), null, null,
                        YES_NO_FALSE, YES_NO_FALSE, null
                    ));
                }
            }
        }
        return result;
    }

    // ── SEQUENCES ─────────────────────────────────────────────────────────────

    public ResultSet querySequences(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("SEQUENCE_CATALOG"), col("SEQUENCE_SCHEMA"), col("SEQUENCE_NAME"),
            col("SEQUENCE_OWNER"), colText("DATA_TYPE", 6),
            colNumber("NUMERIC_PRECISION", 2), colNumber("NUMERIC_PRECISION_RADIX", 2), colNumber("NUMERIC_SCALE", 1),
            // The five bounds are TEXT on the account, as the standard's view defines them — only the
            // three NUMERIC_* cells are numbers.
            col("START_VALUE"), colText("MINIMUM_VALUE", 20), colText("MAXIMUM_VALUE", 19),
            col("NEXT_VALUE"), col("INCREMENT"), colText("CYCLE_OPTION", 2),
            colLtz("CREATED"), colLtz("LAST_ALTERED"), colYesNo("ORDERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Sequence seq : schema.getSequences()) {
                    // A never-altered sequence's LAST_ALTERED is its creation instant (live-verified),
                    // and ORDERED spells the ORDER clause YES/NO where SHOW spells it Y/N.
                    final OffsetDateTime created = ShowResultHelpers.createdOn(seq.getCreatedTime());
                    result.addRow(new Row(
                        db.getName(), schema.getName(), seq.getName().toUpperCase(), seq.getOwner(),
                        "NUMBER", 38L, 10L, 0L,
                        String.valueOf(seq.getStartValue()), String.valueOf(Long.MIN_VALUE),
                        String.valueOf(Long.MAX_VALUE), String.valueOf(seq.peekNextValue()),
                        String.valueOf(seq.getIncrement()), YES_NO_FALSE,
                        created, created, seq.isOrder() ? YES_NO_TRUE : YES_NO_FALSE, seq.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── STAGES ────────────────────────────────────────────────────────────────

    public ResultSet queryStages(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("STAGE_CATALOG"), col("STAGE_SCHEMA"), col("STAGE_NAME"),
            col("STAGE_URL"), col("STAGE_REGION"), colText("STAGE_TYPE", 134217728), col("STAGE_OWNER"),
            col("COMMENT"), colLtz("CREATED"), colLtz("LAST_ALTERED"),
            col("ENDPOINT"), colBool("DIRECTORY_ENABLED")
        ));
        try {
            final String dbN = databaseName != null ? databaseName : catalog.getCurrentDatabase();
            final String scN = schemaName != null ? schemaName : catalog.getCurrentSchema();
            if (dbN != null && scN != null) {
                for (final Stage stage : catalog.getDatabase(dbN).getSchema(scN).getStages()) {
                    final String url = stage.getUrl();
                    // Live's stage-type vocabulary is "External Named" / "Internal Named";
                    // an internal stage reports a null URL and null region.
                    final boolean external = url != null && (url.startsWith("s3://")
                        || url.startsWith("azure://") || url.startsWith("gcs://"));
                    result.addRow(new Row(
                        dbN, scN, stage.getName().toUpperCase(),
                        external ? url : null, null,
                        external ? "External Named" : "Internal Named", stage.getOwner(),
                        stage.getComment(), stage.getCreatedAt(), stage.getCreatedAt(),
                        // A stage declared without DIRECTORY reports NULL here, one with it enabled TRUE (live-verified).
                        null, stage.isDirectoryEnabled() ? Boolean.TRUE : null
                    ));
                }
            }
        } catch (final Exception e) {
            // Skip a row that failed to build rather than failing the whole system view, but log at
            // debug so the underlying error stays diagnosable.
            logger.debug("Skipping a system-view row that could not be built: {}", e.getMessage(), e);
        }
        return result;
    }

    // ── PIPES ─────────────────────────────────────────────────────────────────

    public ResultSet queryPipes(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("PIPE_CATALOG"), col("PIPE_SCHEMA"), col("PIPE_NAME"),
            col("PIPE_OWNER"), col("DEFINITION"), colYesNo("IS_AUTOINGEST_ENABLED"),
            col("NOTIFICATION_CHANNEL_NAME"),
            colLtz("CREATED"), colLtz("LAST_ALTERED"), col("COMMENT"), col("PATTERN")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Pipe pipe : schema.getPipes()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), pipe.getName().toUpperCase(), pipe.getOwner(),
                        pipe.getCopyStatement(), yesNo(pipe.isAutoIngest()),
                        null, pipe.getCreatedTime(), pipe.getCreatedTime(), pipe.getComment(), null
                    ));
                }
            }
        }
        return result;
    }

    // ── ENABLED_ROLES ─────────────────────────────────────────────────────────

    public ResultSet queryEnabledRoles() {
        // Live carries exactly these two columns — no COMMENT.
        final ResultSet result = new ResultSet(Arrays.asList(col("ROLE_NAME"), col("ROLE_OWNER")));
        for (final Role role : catalog.getAllRoles()) {
            result.addRow(new Row(role.getName(), role.getOwner()));
        }
        return result;
    }

    // ── APPLICABLE_ROLES ──────────────────────────────────────────────────────

    public ResultSet queryApplicableRoles() {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTEE"), col("ROLE_NAME"), col("ROLE_OWNER"), colText("IS_GRANTABLE", 2)
        ));
        for (final User user : catalog.getAllUsers()) {
            for (final String role : user.getGrantedRoles()) {
                result.addRow(new Row(user.getName(), role, catalog.getRole(role).getOwner(), "NO"));
            }
        }
        return result;
    }

    // ── TABLE_PRIVILEGES ──────────────────────────────────────────────────────

    public ResultSet queryTablePrivileges(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), colText("GRANTED_TO", 134217728), col("TABLE_CATALOG"),
            col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("PRIVILEGE_TYPE"), colText("IS_GRANTABLE", 134217728), colText("WITH_HIERARCHY", 2), colLtz("CREATED")
        ));
        // Privileges are not fully tracked yet; return empty result set
        return result;
    }

    // ── OBJECT_PRIVILEGES ─────────────────────────────────────────────────────

    public ResultSet queryObjectPrivileges() {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), colText("GRANTED_TO", 134217728), col("OBJECT_CATALOG"),
            col("OBJECT_SCHEMA"), col("OBJECT_NAME"), colText("OBJECT_TYPE", 134217728),
            col("PRIVILEGE_TYPE"), colText("IS_GRANTABLE", 134217728), colLtz("CREATED")
        ));
        return result;
    }

    // ── USAGE_PRIVILEGES ──────────────────────────────────────────────────────

    public ResultSet queryUsagePrivileges() {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), colText("GRANTED_TO", 134217728), col("OBJECT_CATALOG"),
            col("OBJECT_SCHEMA"), col("OBJECT_NAME"), colText("OBJECT_TYPE", 30),
            colText("PRIVILEGE_TYPE", 5), colText("IS_GRANTABLE", 134217728), colLtz("CREATED")
        ));
        return result;
    }

    // ── FILE_FORMATS ──────────────────────────────────────────────────────────

    /** Column shape measured on a real account; the per-option columns read the format's own
     *  options, so a CREATE FILE FORMAT written with any of them reports them back here. */
    public ResultSet queryFileFormats(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("FILE_FORMAT_CATALOG"), col("FILE_FORMAT_SCHEMA"), col("FILE_FORMAT_NAME"),
            col("FILE_FORMAT_OWNER"), col("FILE_FORMAT_TYPE"), col("RECORD_DELIMITER"),
            col("FIELD_DELIMITER"), colLong("SKIP_HEADER"), col("DATE_FORMAT"), col("TIME_FORMAT"),
            col("TIMESTAMP_FORMAT"), col("BINARY_FORMAT"), col("ESCAPE"),
            col("ESCAPE_UNENCLOSED_FIELD"), col("TRIM_SPACE"), col("FIELD_OPTIONALLY_ENCLOSED_BY"),
            col("NULL_IF"), col("COMPRESSION"), col("ERROR_ON_COLUMN_COUNT_MISMATCH"),
            colLtz("CREATED"), colLtz("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final FileFormat format : schema.getFileFormats()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), format.getName().toUpperCase(), null,
                        format.getType(), format.getOption("RECORD_DELIMITER"),
                        format.getOption("FIELD_DELIMITER"), skipHeaderCount(format),
                        format.getOption("DATE_FORMAT"), format.getOption("TIME_FORMAT"),
                        format.getOption("TIMESTAMP_FORMAT"), format.getOption("BINARY_FORMAT"),
                        format.getOption("ESCAPE"), format.getOption("ESCAPE_UNENCLOSED_FIELD"),
                        format.getOption("TRIM_SPACE"), format.getOption("FIELD_OPTIONALLY_ENCLOSED_BY"),
                        nullIfText(format), format.getOption("COMPRESSION"),
                        format.getOption("ERROR_ON_COLUMN_COUNT_MISMATCH"), format.getCreatedTime(), format.getCreatedTime(),
                        format.getComment()
                    ));
                }
            }
        }
        return result;
    }

    /** The format's NULL_IF values joined by commas, or null when it sets none. */
    private String nullIfText(final FileFormat format) {
        final List<String> values = format.getNullIfValues();
        return values == null ? null : String.join(",", values);
    }

    /** The format's SKIP_HEADER as a count; 0 when unset or not a number (live reports a number). */
    private long skipHeaderCount(final FileFormat format) {
        final String value = format.getOption("SKIP_HEADER");
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (final NumberFormatException notANumber) {
            return 0L;
        }
    }

    // ── HYBRID_TABLES ─────────────────────────────────────────────────────────

    public ResultSet queryHybridTables(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("CATALOG"), col("SCHEMA"), col("NAME"), col("OWNER"),
            colLong("ROW_COUNT"), colLong("BYTES"), colLong("RETENTION_TIME"),
            colLtz("CREATED"), colLtz("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : schema.getTables()) {
                    if (!table.isHybrid()) {
                        continue;
                    }
                    result.addRow(new Row(
                        db.getName(), schema.getName(), table.getName(), table.getOwner(),
                        storageEngine.storedRowCount(
                            QualifiedName.key(db.getName(), schema.getName(), table.getName()), false),
                        0L, 1L,
                        table.getCreatedTime(), table.getCreatedTime(), table.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── TABLE_STORAGE_METRICS ─────────────────────────────────────────────────

    /** Byte counts are all zero: this engine stores rows in memory and keeps no byte accounting,
     *  no time-travel copies and no fail-safe, so zero is the honest figure for each. */
    public ResultSet queryTableStorageMetrics(final String databaseName, final String schemaName) {
        final ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), colText("TABLE_NAME", 134217728), colLong("ID"),
            colLong("CLONE_GROUP_ID"), colYesNo("IS_TRANSIENT"), colLong("ACTIVE_BYTES"),
            colLong("TIME_TRAVEL_BYTES"), colLong("FAILSAFE_BYTES"),
            colLong("RETAINED_FOR_CLONE_BYTES"), colLtz("TABLE_CREATED"), colLtz("TABLE_DROPPED"),
            colLtz("TABLE_ENTERED_FAILSAFE"), colLtz("CATALOG_CREATED"), colLtz("CATALOG_DROPPED"),
            colLtz("SCHEMA_CREATED"), colLtz("SCHEMA_DROPPED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : schema.getTables()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), table.getName(), null, null,
                        table.isTransient() ? "YES" : "NO", 0L, 0L, 0L, 0L,
                        table.getCreatedTime(), null, null,
                        db.getCreatedTime(), null, schema.getCreatedTime(), null,
                        table.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── INFORMATION_SCHEMA_CATALOG_NAME ───────────────────────────────────────

    /** One row naming the database whose INFORMATION_SCHEMA is being read. */
    public ResultSet queryInformationSchemaCatalogName(final String databaseName) {
        final ResultSet result = new ResultSet(Arrays.asList(col("CATALOG_NAME")));
        final String name = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (name != null) {
            result.addRow(new Row(name));
        }
        return result;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<Database> databases(final String databaseName) {
        return databaseName != null
            ? Collections.singletonList(catalog.getDatabase(databaseName))
            : catalog.getAllDatabases();
    }

    private List<Schema> schemas(final Database db, final String schemaName) {
        return schemaName != null
            ? Collections.singletonList(db.getSchema(schemaName))
            : db.getAllSchemas();
    }

    /** INFORMATION_SCHEMA spells booleans YES / NO — never Y / N, which is the SHOW convention. */
    private static String yesNo(final boolean value) {
        return value ? YES_NO_TRUE : YES_NO_FALSE;
    }

    /**
     * A name, a comment, a definition: the VARCHAR the account's views declare with no length of
     * their own, which SYSTEM$TYPEOF spells as the bare word VARCHAR (live-verified over TABLES,
     * COLUMNS, SCHEMATA, DATABASES, VIEWS and SEQUENCES).
     */
    private ResultSetColumn col(final String name) {
        return new ResultSetColumn(name, StringType.VARCHAR, null, new LengthlessStringType());
    }

    /**
     * A column the account's view fills with a bare NULL (the SQL-standard placeholders): VARCHAR(0) in the result
     * metadata and NULL to SYSTEM$TYPEOF. Like a bare NULL select item it reports the zero width and leaves its
     * static type undetermined, so no INSERT type check reads it as a string.
     */
    private ResultSetColumn colNull(final String name) {
        return new ResultSetColumn(name, new StringType("VARCHAR", 0));
    }

    /** A BOOLEAN cell (STAGES.DIRECTORY_ENABLED). */
    private ResultSetColumn colBool(final String name) {
        return new ResultSetColumn(name, new BooleanType(), null, new BooleanType());
    }

    /** A YES / NO cell: VARCHAR(3) on the account (IS_NULLABLE, IS_SECURE, ORDERED, …). */
    private ResultSetColumn colYesNo(final String name) {
        return new ResultSetColumn(name, StringType.VARCHAR, null, new StringType("VARCHAR", 3));
    }

    /** A text column the account declares at a measured width — VARCHAR(134217728) for TABLE_TYPE. */
    private ResultSetColumn colText(final String name, final int length) {
        return new ResultSetColumn(name, StringType.VARCHAR, null, new StringType("VARCHAR", length));
    }

    /** A whole-number cell: NUMBER(38,0) on the account, whatever it counts. */
    private ResultSetColumn colInt(final String name) {
        return new ResultSetColumn(name, NumericType.INTEGER, null, new NumericType("NUMBER", 38, 0));
    }

    /** A whole-number cell held as an Integer that the account declares narrower — NUMBER(2,0) for a radix. */
    private ResultSetColumn colInt(final String name, final int precision) {
        return new ResultSetColumn(name, NumericType.INTEGER, null, new NumericType("NUMBER", precision, 0));
    }

    private ResultSetColumn colLong(final String name) {
        return new ResultSetColumn(name, NumericType.BIGINT, null, new NumericType("NUMBER", 38, 0));
    }

    /** A whole-number cell the account declares narrower — NUMBER(2,0) for SEQUENCES.NUMERIC_PRECISION. */
    private ResultSetColumn colNumber(final String name, final int precision) {
        return new ResultSetColumn(name, NumericType.BIGINT, null, new NumericType("NUMBER", precision, 0));
    }

    /** A creation or alteration instant — TIMESTAMP_LTZ(3) on the account, rendered at the session zone. */
    private ResultSetColumn colLtz(final String name) {
        return new ResultSetColumn(name, ShowResultHelpers.CREATED_ON, null, ShowResultHelpers.CREATED_ON);
    }

    /** A list rendered as live renders IMPORTS / PACKAGES, or null when it holds nothing. */
    private String listOrNull(final List<String> values) {
        return values == null || values.isEmpty() ? null : "[" + String.join(", ", values) + "]";
    }

    /** A table function's returned columns as {@code NAME TYPE, …}, the body of live's
     *  {@code TABLE (…)} DATA_TYPE. */
    private String columnSignature(final List<Parameter> columns) {
        if (columns == null || columns.isEmpty()) {
            return "";
        }
        final StringBuilder signature = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                signature.append(", ");
            }
            signature.append(columns.get(i).getName()).append(' ')
                .append(columns.get(i).getDataType().getName());
        }
        return signature.toString();
    }

    private String buildArgSignature(final List<Parameter> params) {
        if (params == null || params.isEmpty()) return "()";
        final StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getName()).append(" ").append(params.get(i).getDataType().getName());
        }
        sb.append(")");
        return sb.toString();
    }
}
