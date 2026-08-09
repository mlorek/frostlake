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

import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemViews {

    private static final Logger logger = LoggerFactory.getLogger(SystemViews.class);

    /** The YES / NO spelling INFORMATION_SCHEMA uses for booleans (SHOW output uses Y / N). */
    private static final String YES_NO_TRUE = "YES";
    private static final String YES_NO_FALSE = "NO";
    /** Data retention, in days: this engine keeps no time-travel history, and a real account's
     *  default for a standard database is 1 — reported as text, as live does. */
    private static final String DEFAULT_RETENTION_TIME = "1";
    /** Every object here is owned by a role, never directly by a user. */
    private static final String OWNER_ROLE_TYPE = "ROLE";

    private final Catalog catalog;

    public SystemViews(final Catalog catalog) {
        this.catalog = catalog;
    }

    // ── DATABASES ─────────────────────────────────────────────────────────────

    public ResultSet queryDatabases() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("DATABASE_NAME"), col("DATABASE_OWNER"), col("IS_TRANSIENT"), col("COMMENT"),
            col("CREATED"), col("LAST_ALTERED"), col("RETENTION_TIME"), col("TYPE"),
            col("REPLICABLE_WITH_FAILOVER_GROUPS"), col("OWNER_ROLE_TYPE")
        ));
        for (final Database db : catalog.getAllDatabases()) {
            result.addRow(new Row(
                db.getName(), db.getOwner(), YES_NO_FALSE, db.getComment(),
                db.getCreatedTime(), db.getCreatedTime(), DEFAULT_RETENTION_TIME, "STANDARD",
                "UNSET", OWNER_ROLE_TYPE
            ));
        }
        return result;
    }

    // ── SCHEMATA ──────────────────────────────────────────────────────────────

    public ResultSet querySchemata(final String databaseName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("CATALOG_NAME"), col("SCHEMA_NAME"), col("SCHEMA_OWNER"),
            col("IS_TRANSIENT"), col("IS_MANAGED_ACCESS"), col("RETENTION_TIME"),
            col("DEFAULT_CHARACTER_SET_CATALOG"), col("DEFAULT_CHARACTER_SET_SCHEMA"),
            col("DEFAULT_CHARACTER_SET_NAME"), col("SQL_PATH"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT"),
            col("REPLICABLE_WITH_FAILOVER_GROUPS"), col("OWNER_ROLE_TYPE")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : db.getAllSchemas()) {
                result.addRow(new Row(
                    db.getName(), schema.getName(), schema.getOwner(),
                    YES_NO_FALSE, YES_NO_FALSE, DEFAULT_RETENTION_TIME,
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
        ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("TABLE_OWNER"), col("TABLE_TYPE"),
            col("IS_TRANSIENT"), col("CLUSTERING_KEY"),
            colLong("ROW_COUNT"), colLong("BYTES"), col("RETENTION_TIME"),
            col("SELF_REFERENCING_COLUMN_NAME"), col("REFERENCE_GENERATION"),
            col("USER_DEFINED_TYPE_CATALOG"), col("USER_DEFINED_TYPE_SCHEMA"),
            col("USER_DEFINED_TYPE_NAME"), col("IS_INSERTABLE_INTO"), col("IS_TYPED"),
            col("COMMIT_ACTION"), col("CREATED"), col("LAST_ALTERED"),
            col("LAST_DDL"), col("LAST_DDL_BY"), col("AUTO_CLUSTERING_ON"), col("COMMENT"),
            col("IS_TEMPORARY"), col("IS_ICEBERG"), col("IS_DYNAMIC"), col("IS_IMMUTABLE"),
            col("IS_HYBRID"), col("ROW_TIMESTAMP_ON"), col("ERROR_LOGGING"), col("IS_INTERACTIVE")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : schema.getTables()) {
                    String type = table.isTemporary() ? "LOCAL TEMPORARY" : "BASE TABLE";
                    result.addRow(new Row(
                        db.getName(), schema.getName(), table.getName(), table.getOwner(),
                        type, yesNo(table.isTransient()),
                        table.getClusterKeys().isEmpty() ? null : String.join(", ", table.getClusterKeys()),
                        (long) table.getRowCount(), 0L, DEFAULT_RETENTION_TIME,
                        null, null, null, null, null, YES_NO_TRUE, YES_NO_TRUE, null,
                        table.getCreatedTime(), table.getCreatedTime(), table.getCreatedTime(),
                        table.getOwner(), YES_NO_FALSE, table.getComment(),
                        yesNo(table.isTemporary()), YES_NO_FALSE, YES_NO_FALSE, YES_NO_FALSE,
                        yesNo(table.isHybrid()), YES_NO_FALSE, YES_NO_FALSE, YES_NO_FALSE
                    ));
                }
                for (final View view : schema.getViews()) {
                    result.addRow(tableShapedRow(db, schema, view.getName(), view.getOwner(),
                        "VIEW", view.getCreatedTime(), view.getComment()));
                }
                for (final MaterializedView view : schema.getMaterializedViews()) {
                    result.addRow(tableShapedRow(db, schema, view.getName(), view.getOwner(),
                        "MATERIALIZED VIEW", view.getCreatedTime(), view.getComment()));
                }
                for (final DynamicTable dynamicTable : schema.getDynamicTables()) {
                    result.addRow(tableShapedRow(db, schema, dynamicTable.getName(), dynamicTable.getOwner(),
                        "BASE TABLE", dynamicTable.getCreatedTime(), dynamicTable.getComment()));
                }
            }
        }
        return result;
    }

    /**
     * A TABLES row for an object with no stored rows of its own — a view, a materialized view or a
     * dynamic table. Live leaves IS_TRANSIENT, CLUSTERING_KEY and the size columns empty for these.
     */
    /** A non-table relation (view, materialized view, dynamic table) in the TABLES row shape. */
    private Row tableShapedRow(final Database db, final Schema schema, final String name,
                               final String owner, final String type, final Instant created,
                               final String comment) {
        return new Row(
            db.getName(), schema.getName(), name, owner, type,
            YES_NO_FALSE, null, null, null, DEFAULT_RETENTION_TIME,
            null, null, null, null, null, YES_NO_TRUE, YES_NO_TRUE, null,
            created, created, created, owner, YES_NO_FALSE, comment,
            YES_NO_FALSE, YES_NO_FALSE, "DYNAMIC TABLE".equals(type) ? YES_NO_TRUE : YES_NO_FALSE,
            YES_NO_FALSE, YES_NO_FALSE, YES_NO_FALSE, YES_NO_FALSE, YES_NO_FALSE
        );
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
        ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("COLUMN_NAME"), colInt("ORDINAL_POSITION"),
            col("COLUMN_DEFAULT"), col("IS_NULLABLE"),
            col("DATA_TYPE"), colInt("CHARACTER_MAXIMUM_LENGTH"), colInt("CHARACTER_OCTET_LENGTH"),
            colInt("NUMERIC_PRECISION"), colInt("NUMERIC_PRECISION_RADIX"), colInt("NUMERIC_SCALE"),
            colInt("DATETIME_PRECISION"), col("INTERVAL_TYPE"), col("INTERVAL_PRECISION"),
            col("CHARACTER_SET_CATALOG"), col("CHARACTER_SET_SCHEMA"), col("CHARACTER_SET_NAME"),
            col("COLLATION_CATALOG"), col("COLLATION_SCHEMA"), col("COLLATION_NAME"),
            col("DOMAIN_CATALOG"), col("DOMAIN_SCHEMA"), col("DOMAIN_NAME"),
            col("UDT_CATALOG"), col("UDT_SCHEMA"), col("UDT_NAME"),
            col("SCOPE_CATALOG"), col("SCOPE_SCHEMA"), col("SCOPE_NAME"),
            col("MAXIMUM_CARDINALITY"), col("DTD_IDENTIFIER"), col("IS_SELF_REFERENCING"),
            col("IS_IDENTITY"), col("IDENTITY_GENERATION"),
            col("IDENTITY_START"), col("IDENTITY_INCREMENT"),
            col("IDENTITY_MAXIMUM"), col("IDENTITY_MINIMUM"),
            col("IDENTITY_CYCLE"), col("IDENTITY_ORDERED"),
            col("SCHEMA_EVOLUTION_RECORD"), col("DATA_TYPE_ALIAS"), col("COMMENT"),
            col("EXPRESSION"), col("KIND")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : tablesFor(schema, tableName)) {
                    int pos = 1;
                    for (final TableColumn col : table.getColumns()) {
                        result.addRow(columnRow(db, schema, table.getName(), col, pos++, true));
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

    /** The base tables a COLUMNS read covers: one named table, or all of them. A name that is not a
     *  table (a view's, say) selects no table rather than failing the whole read. */
    private List<Table> tablesFor(final Schema schema, final String tableName) {
        if (tableName == null) {
            return schema.getTables();
        }
        return schema.hasTable(tableName)
            ? Collections.singletonList(schema.getTable(tableName))
            : Collections.<Table>emptyList();
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
        final String canonical = canonicalDataType(typeName);
        final Integer charLen = dataType instanceof StringType
            ? Integer.valueOf(((StringType) dataType).getMaxLength()) : null;
        Integer octetLen = charLen;
        if (dataType instanceof BinaryType) {
            octetLen = Integer.valueOf(((BinaryType) dataType).getMaxLength());
        }
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
            !fromBaseTable || col.isNullable() ? YES_NO_TRUE : YES_NO_FALSE,
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
            identity ? "1" : null,
            identity ? "1" : null,
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
        ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("TABLE_OWNER"), col("VIEW_DEFINITION"), col("CHECK_OPTION"),
            col("IS_UPDATABLE"), col("INSERTABLE_INTO"), col("IS_SECURE"),
            col("CREATED"), col("LAST_ALTERED"), col("LAST_DDL"), col("LAST_DDL_BY"),
            col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final View view : schema.getViews()) {
                    // Snowflake's VIEW_DEFINITION is the view's full executable DDL, not just its
                    // query: deployment tooling recreates views via EXECUTE IMMEDIATE of this text.
                    result.addRow(new Row(
                        db.getName(), schema.getName(), view.getName(), view.getOwner(),
                        view.ddl(schema.getName() + "." + view.getName()), "NONE",
                        YES_NO_FALSE, YES_NO_FALSE, yesNo(view.isSecure()),
                        view.getCreatedTime(), view.getCreatedTime(), view.getCreatedTime(),
                        view.getOwner(), view.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── TABLE_CONSTRAINTS ─────────────────────────────────────────────────────

    public ResultSet queryTableConstraints(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("CONSTRAINT_CATALOG"), col("CONSTRAINT_SCHEMA"), col("CONSTRAINT_NAME"),
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("CONSTRAINT_TYPE"), col("IS_DEFERRABLE"), col("INITIALLY_DEFERRED"),
            col("ENFORCED"), col("COMMENT"), col("CREATED"), col("LAST_ALTERED"), col("RELY")
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
        ResultSet result = new ResultSet(Arrays.asList(
            col("CONSTRAINT_CATALOG"), col("CONSTRAINT_SCHEMA"), col("CONSTRAINT_NAME"),
            col("UNIQUE_CONSTRAINT_CATALOG"), col("UNIQUE_CONSTRAINT_SCHEMA"), col("UNIQUE_CONSTRAINT_NAME"),
            col("MATCH_OPTION"), col("UPDATE_RULE"), col("DELETE_RULE"), col("COMMENT"),
            col("CREATED"), col("LAST_ALTERED")
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
                                "NONE", "NO ACTION", "NO ACTION", null,
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
                            "NONE",
                            fk.getOnUpdate() != null ? fk.getOnUpdate() : "NO ACTION",
                            fk.getOnDelete() != null ? fk.getOnDelete() : "NO ACTION",
                            null
                        ));
                    }
                }
            }
        }
        return result;
    }

    // ── PROCEDURES ────────────────────────────────────────────────────────────

    public ResultSet queryProcedures(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("PROCEDURE_CATALOG"), col("PROCEDURE_SCHEMA"), col("PROCEDURE_NAME"),
            col("PROCEDURE_OWNER"), col("ARGUMENT_SIGNATURE"), col("DATA_TYPE"),
            colInt("CHARACTER_MAXIMUM_LENGTH"), colInt("CHARACTER_OCTET_LENGTH"),
            colInt("NUMERIC_PRECISION"), colInt("NUMERIC_PRECISION_RADIX"), colInt("NUMERIC_SCALE"),
            col("PROCEDURE_LANGUAGE"), col("PROCEDURE_DEFINITION"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT"),
            col("EXTERNAL_ACCESS_INTEGRATIONS"), col("SECRETS"),
            col("RUNTIME_VERSION"), col("PACKAGES"), col("INSTALLED_PACKAGES"),
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
        ResultSet result = new ResultSet(Arrays.asList(
            col("FUNCTION_CATALOG"), col("FUNCTION_SCHEMA"), col("FUNCTION_NAME"),
            col("FUNCTION_OWNER"), col("ARGUMENT_SIGNATURE"), col("DATA_TYPE"),
            colInt("CHARACTER_MAXIMUM_LENGTH"), colInt("CHARACTER_OCTET_LENGTH"),
            colInt("NUMERIC_PRECISION"), colInt("NUMERIC_PRECISION_RADIX"), colInt("NUMERIC_SCALE"),
            col("FUNCTION_LANGUAGE"), col("FUNCTION_DEFINITION"),
            col("VOLATILITY"), col("IS_NULL_CALL"), col("IS_SECURE"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT"),
            col("IS_EXTERNAL"), col("API_INTEGRATION"), col("CONTEXT_HEADERS"),
            col("MAX_BATCH_ROWS"), col("REQUEST_TRANSLATOR"), col("RESPONSE_TRANSLATOR"),
            col("COMPRESSION"), col("IMPORTS"), col("HANDLER"), col("TARGET_PATH"),
            col("RUNTIME_VERSION"), col("PACKAGES"), col("INSTALLED_PACKAGES"),
            col("IS_MEMOIZABLE"), col("EXTERNAL_ACCESS_INTEGRATIONS"), col("SECRETS"),
            col("IS_DATA_METRIC"), col("IS_AGGREGATE"), col("ARTIFACT_REPOSITORY")
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
                        YES_NO_FALSE, null, null,
                        YES_NO_FALSE, YES_NO_FALSE, null
                    ));
                }
            }
        }
        return result;
    }

    // ── SEQUENCES ─────────────────────────────────────────────────────────────

    public ResultSet querySequences(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("SEQUENCE_CATALOG"), col("SEQUENCE_SCHEMA"), col("SEQUENCE_NAME"),
            col("SEQUENCE_OWNER"), col("DATA_TYPE"),
            colLong("NUMERIC_PRECISION"), colLong("NUMERIC_PRECISION_RADIX"), colLong("NUMERIC_SCALE"),
            colLong("START_VALUE"), colLong("MINIMUM_VALUE"), colLong("MAXIMUM_VALUE"),
            colLong("NEXT_VALUE"), colLong("INCREMENT"), col("CYCLE_OPTION"),
            col("CREATED"), col("LAST_ALTERED"), col("ORDERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Sequence seq : schema.getSequences()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), seq.getName().toUpperCase(), seq.getOwner(),
                        "NUMBER", 38L, 10L, 0L,
                        seq.getStartValue(), Long.MIN_VALUE, Long.MAX_VALUE,
                        seq.peekNextValue(), seq.getIncrement(), YES_NO_FALSE,
                        null, null, YES_NO_FALSE, seq.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── STAGES ────────────────────────────────────────────────────────────────

    public ResultSet queryStages(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("STAGE_CATALOG"), col("STAGE_SCHEMA"), col("STAGE_NAME"),
            col("STAGE_URL"), col("STAGE_REGION"), col("STAGE_TYPE"), col("STAGE_OWNER"),
            col("COMMENT"), col("CREATED"), col("LAST_ALTERED"),
            col("ENDPOINT"), col("DIRECTORY_ENABLED")
        ));
        try {
            String dbN = databaseName != null ? databaseName : catalog.getCurrentDatabase();
            String scN = schemaName != null ? schemaName : catalog.getCurrentSchema();
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
                        null, null
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
        ResultSet result = new ResultSet(Arrays.asList(
            col("PIPE_CATALOG"), col("PIPE_SCHEMA"), col("PIPE_NAME"),
            col("PIPE_OWNER"), col("DEFINITION"), col("IS_AUTOINGEST_ENABLED"),
            col("NOTIFICATION_CHANNEL_NAME"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT"), col("PATTERN")
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
        ResultSet result = new ResultSet(Arrays.asList(col("ROLE_NAME"), col("ROLE_OWNER")));
        for (final Role role : catalog.getAllRoles()) {
            result.addRow(new Row(role.getName(), role.getOwner()));
        }
        return result;
    }

    // ── APPLICABLE_ROLES ──────────────────────────────────────────────────────

    public ResultSet queryApplicableRoles() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTEE"), col("ROLE_NAME"), col("ROLE_OWNER"), col("IS_GRANTABLE")
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
        ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), col("GRANTED_TO"), col("TABLE_CATALOG"),
            col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("PRIVILEGE_TYPE"), col("IS_GRANTABLE"), col("WITH_HIERARCHY"), col("CREATED")
        ));
        // Privileges are not fully tracked yet; return empty result set
        return result;
    }

    // ── OBJECT_PRIVILEGES ─────────────────────────────────────────────────────

    public ResultSet queryObjectPrivileges() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), col("GRANTED_TO"), col("OBJECT_CATALOG"),
            col("OBJECT_SCHEMA"), col("OBJECT_NAME"), col("OBJECT_TYPE"),
            col("PRIVILEGE_TYPE"), col("IS_GRANTABLE"), col("CREATED")
        ));
        return result;
    }

    // ── USAGE_PRIVILEGES ──────────────────────────────────────────────────────

    public ResultSet queryUsagePrivileges() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), col("GRANTED_TO"), col("OBJECT_CATALOG"),
            col("OBJECT_SCHEMA"), col("OBJECT_NAME"), col("OBJECT_TYPE"),
            col("PRIVILEGE_TYPE"), col("IS_GRANTABLE"), col("CREATED")
        ));
        return result;
    }

    // ── FILE_FORMATS ──────────────────────────────────────────────────────────

    /** Column shape measured on a real account; the per-option columns read the format's own
     *  options, so a CREATE FILE FORMAT written with any of them reports them back here. */
    public ResultSet queryFileFormats(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("FILE_FORMAT_CATALOG"), col("FILE_FORMAT_SCHEMA"), col("FILE_FORMAT_NAME"),
            col("FILE_FORMAT_OWNER"), col("FILE_FORMAT_TYPE"), col("RECORD_DELIMITER"),
            col("FIELD_DELIMITER"), colLong("SKIP_HEADER"), col("DATE_FORMAT"), col("TIME_FORMAT"),
            col("TIMESTAMP_FORMAT"), col("BINARY_FORMAT"), col("ESCAPE"),
            col("ESCAPE_UNENCLOSED_FIELD"), col("TRIM_SPACE"), col("FIELD_OPTIONALLY_ENCLOSED_BY"),
            col("NULL_IF"), col("COMPRESSION"), col("ERROR_ON_COLUMN_COUNT_MISMATCH"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
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
                        format.getOption("NULL_IF"), format.getOption("COMPRESSION"),
                        format.getOption("ERROR_ON_COLUMN_COUNT_MISMATCH"), null, null,
                        format.getComment()
                    ));
                }
            }
        }
        return result;
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
        ResultSet result = new ResultSet(Arrays.asList(
            col("CATALOG"), col("SCHEMA"), col("NAME"), col("OWNER"),
            colLong("ROW_COUNT"), colLong("BYTES"), colLong("RETENTION_TIME"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : schema.getTables()) {
                    if (!table.isHybrid()) {
                        continue;
                    }
                    result.addRow(new Row(
                        db.getName(), schema.getName(), table.getName(), table.getOwner(),
                        table.getRowCount(), 0L, 1L,
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
        ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"), colLong("ID"),
            colLong("CLONE_GROUP_ID"), col("IS_TRANSIENT"), colLong("ACTIVE_BYTES"),
            colLong("TIME_TRAVEL_BYTES"), colLong("FAILSAFE_BYTES"),
            colLong("RETAINED_FOR_CLONE_BYTES"), col("TABLE_CREATED"), col("TABLE_DROPPED"),
            col("TABLE_ENTERED_FAILSAFE"), col("CATALOG_CREATED"), col("CATALOG_DROPPED"),
            col("SCHEMA_CREATED"), col("SCHEMA_DROPPED"), col("COMMENT")
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
        ResultSet result = new ResultSet(Arrays.asList(col("CATALOG_NAME")));
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

    private ResultSetColumn col(final String name) {
        return new ResultSetColumn(name, StringType.VARCHAR);
    }

    private ResultSetColumn colInt(final String name) {
        return new ResultSetColumn(name, NumericType.INTEGER);
    }

    private ResultSetColumn colLong(final String name) {
        return new ResultSetColumn(name, NumericType.BIGINT);
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
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getName()).append(" ").append(params.get(i).getDataType().getName());
        }
        sb.append(")");
        return sb.toString();
    }
}
