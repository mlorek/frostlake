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

    private final Catalog catalog;

    public SystemViews(final Catalog catalog) {
        this.catalog = catalog;
    }

    // ── DATABASES ─────────────────────────────────────────────────────────────

    public ResultSet queryDatabases() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("DATABASE_NAME"), col("DATABASE_OWNER"), col("IS_TRANSIENT"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : catalog.getAllDatabases()) {
            result.addRow(new Row(
                db.getName(), db.getOwner(), "N",
                db.getCreatedTime(), db.getCreatedTime(), db.getComment()
            ));
        }
        return result;
    }

    // ── SCHEMATA ──────────────────────────────────────────────────────────────

    public ResultSet querySchemata(final String databaseName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("CATALOG_NAME"), col("SCHEMA_NAME"), col("SCHEMA_OWNER"),
            col("IS_TRANSIENT"), col("IS_MANAGED_ACCESS"),
            col("RETENTION_TIME"), colInt("RETENTION_TIME_DAYS"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : db.getAllSchemas()) {
                result.addRow(new Row(
                    db.getName(), schema.getName(), schema.getOwner(),
                    "N", "N", "1", 1L,
                    schema.getCreatedTime(), schema.getCreatedTime(), schema.getComment()
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
            colLong("ROW_COUNT"), colLong("BYTES"),
            col("RETENTION_TIME"), col("CREATED"), col("LAST_ALTERED"),
            col("LAST_DDL"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : schema.getTables()) {
                    String type = table.isTemporary() ? "LOCAL TEMPORARY" : "BASE TABLE";
                    result.addRow(new Row(
                        db.getName(), schema.getName(), table.getName(), table.getOwner(),
                        type, table.isTransient() ? "Y" : "N",
                        table.getClusterKeys().isEmpty() ? null : String.join(", ", table.getClusterKeys()),
                        (long) table.getRowCount(), 0L, "1",
                        table.getCreatedTime(), table.getCreatedTime(), table.getCreatedTime(),
                        table.getComment()
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
    private Row tableShapedRow(final Database db, final Schema schema, final String name,
                               final String owner, final String type, final Instant created,
                               final String comment) {
        return new Row(
            db.getName(), schema.getName(), name, owner, type,
            null, null, null, null, "1", created, created, created, comment
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
            colInt("NUMERIC_PRECISION"), colInt("NUMERIC_SCALE"), colInt("DATETIME_PRECISION"),
            col("COLLATION_NAME"), col("IS_IDENTITY"),
            col("IDENTITY_START"), col("IDENTITY_INCREMENT"),
            col("COMMENT"), col("IS_PRIMARY_KEY")
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
        return new Row(
            db.getName(), schema.getName(), relationName,
            col.getName(), position,
            fromBaseTable && col.getDefaultValue() != null ? col.getDefaultValue().toString() : null,
            !fromBaseTable || col.isNullable() ? "YES" : "NO",
            canonical, charLen, octetLen, numPrec, numScale, dateTimePrec,
            col.getCollation(),
            identity ? "YES" : "NO",
            identity ? "1" : null,
            identity ? "1" : null,
            fromBaseTable ? col.getComment() : null,
            fromBaseTable && col.isPrimaryKey() ? "YES" : "NO"
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
            col("IS_UPDATABLE"), col("IS_INSERTABLE_INTO"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final View view : schema.getViews()) {
                    // Snowflake's VIEW_DEFINITION is the view's full executable DDL, not just its
                    // query: deployment tooling recreates views via EXECUTE IMMEDIATE of this text.
                    result.addRow(new Row(
                        db.getName(), schema.getName(), view.getName(), view.getOwner(),
                        view.ddl(schema.getName() + "." + view.getName()), "NONE", "NO", "NO",
                        view.getCreatedTime(), view.getCreatedTime(), view.getComment()
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
            col("ENFORCED"), col("RELY"), col("COMMENT")
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
        final String relyText = rely != null && rely ? "YES" : "NO";
        return new Row(db.getName(), schema.getName(), name,
                       db.getName(), schema.getName(), table.getName(),
                       type, "NO", "YES", "NO", relyText, null);
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
            col("MATCH_OPTION"), col("UPDATE_RULE"), col("DELETE_RULE"), col("COMMENT")
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
                                "NONE", "NO ACTION", "NO ACTION", null
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
            col("PROCEDURE_OWNER"), col("PROCEDURE_LANGUAGE"),
            col("ARGUMENT_SIGNATURE"), col("DATA_TYPE"),
            col("PROCEDURE_DEFINITION"), col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Procedure proc : schema.getProcedures()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), proc.getName(), proc.getOwner(),
                        proc.getLanguage(), buildArgSignature(proc.getParameters()),
                        proc.getReturnType().getName(),
                        proc.getBody(), proc.getCreatedTime(), proc.getCreatedTime(), proc.getComment()
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
            col("FUNCTION_OWNER"), col("FUNCTION_LANGUAGE"), col("IS_TABLE_FUNCTION"),
            col("ARGUMENT_SIGNATURE"), col("DATA_TYPE"),
            col("FUNCTION_DEFINITION"), col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Function func : schema.getFunctions()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), func.getName(), func.getOwner(),
                        func.getLanguage(), func.isTableFunction() ? "Y" : "N",
                        buildArgSignature(func.getParameters()),
                        func.getReturnType().getName(),
                        func.getBody(), func.getCreatedTime(), func.getCreatedTime(), func.getComment()
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
            colLong("INCREMENT"), col("CYCLE_OPTION"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Sequence seq : schema.getSequences()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), seq.getName().toUpperCase(), seq.getOwner(),
                        "NUMBER", 38L, 10L, 0L,
                        seq.getStartValue(), Long.MIN_VALUE, Long.MAX_VALUE,
                        seq.getIncrement(), "N",
                        null, null, seq.getComment()
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
            col("STAGE_OWNER"), col("STAGE_TYPE"), col("STAGE_URL"),
            col("STAGE_REGION"), col("STAGE_FILE_FORMAT"), col("STAGE_COPY_OPTIONS"),
            col("CREATED"), col("LAST_ALTERED"), col("HAS_CREDENTIALS"),
            col("HAS_ENCRYPTION_KEY"), col("COMMENT")
        ));
        try {
            String dbN = databaseName != null ? databaseName : catalog.getCurrentDatabase();
            String scN = schemaName != null ? schemaName : catalog.getCurrentSchema();
            if (dbN != null && scN != null) {
                for (final Stage stage : catalog.getDatabase(dbN).getSchema(scN).getStages()) {
                    String url = stage.getUrl() != null ? stage.getUrl() : "";
                    String type = (url.startsWith("s3://") || url.startsWith("azure://") || url.startsWith("gcs://"))
                        ? "External Stage" : "Internal Named Stage";
                    result.addRow(new Row(
                        dbN, scN, stage.getName().toUpperCase(), stage.getOwner(),
                        type, url, null, stage.getFileFormat(), null,
                        stage.getCreatedAt(), stage.getCreatedAt(), "N", "N", stage.getComment()
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
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Pipe pipe : schema.getPipes()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), pipe.getName().toUpperCase(), pipe.getOwner(),
                        pipe.getCopyStatement(), pipe.isAutoIngest() ? "Y" : "N",
                        null, pipe.getCreatedTime(), pipe.getCreatedTime(), pipe.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── STREAMS ───────────────────────────────────────────────────────────────

    public ResultSet queryStreams(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("STREAM_CATALOG"), col("STREAM_SCHEMA"), col("STREAM_NAME"),
            col("STREAM_OWNER"), col("TABLE_NAME"), col("SOURCE_TYPE"),
            col("BASE_TABLES"), col("TYPE"), col("STALE"), col("MODE"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Stream stream : schema.getStreams()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), stream.getName(), stream.getOwner(),
                        stream.getSourceTableName(),
                        stream.getSourceType() != null ? stream.getSourceType().name() : null,
                        stream.getSourceTableName(), "Delta", "N", "DEFAULT",
                        stream.getCreatedAt(), stream.getCreatedAt(), stream.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── TASKS ─────────────────────────────────────────────────────────────────

    public ResultSet queryTasks(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("TASK_CATALOG"), col("TASK_SCHEMA"), col("TASK_NAME"),
            col("TASK_OWNER"), col("WAREHOUSE"), col("SCHEDULE"),
            col("PREDECESSORS"), col("STATE"), col("DEFINITION"),
            col("CONDITION"), col("ALLOW_OVERLAPPING_EXECUTION"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Task task : schema.getTasks()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), task.getName().toUpperCase(), task.getOwner(),
                        task.getWarehouse(), task.getSchedule(),
                        null,
                        task.getState() != null ? task.getState().name() : null,
                        task.getSqlStatement(), null, "FALSE",
                        task.getCreatedAt(), task.getCreatedAt(), task.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── ENABLED_ROLES ─────────────────────────────────────────────────────────

    public ResultSet queryEnabledRoles() {
        ResultSet result = new ResultSet(Arrays.asList(col("ROLE_NAME"), col("ROLE_OWNER"), col("COMMENT")));
        for (final Role role : catalog.getAllRoles()) {
            result.addRow(new Row(role.getName(), role.getOwner(), role.getComment()));
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
            col("GRANTOR"), col("GRANTEE"), col("TABLE_CATALOG"),
            col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("PRIVILEGE_TYPE"), col("IS_GRANTABLE"), col("WITH_HIERARCHY")
        ));
        // Privileges are not fully tracked yet; return empty result set
        return result;
    }

    // ── OBJECT_PRIVILEGES ─────────────────────────────────────────────────────

    public ResultSet queryObjectPrivileges() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), col("OBJECT_CATALOG"),
            col("OBJECT_SCHEMA"), col("OBJECT_NAME"), col("OBJECT_TYPE"),
            col("PRIVILEGE_TYPE"), col("IS_GRANTABLE")
        ));
        return result;
    }

    // ── USAGE_PRIVILEGES ──────────────────────────────────────────────────────

    public ResultSet queryUsagePrivileges() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), col("OBJECT_CATALOG"),
            col("OBJECT_SCHEMA"), col("OBJECT_NAME"), col("OBJECT_TYPE"),
            col("PRIVILEGE_TYPE"), col("IS_GRANTABLE")
        ));
        return result;
    }

    // ── TAGS ──────────────────────────────────────────────────────────────────

    public ResultSet queryTags(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("TAG_DATABASE"), col("TAG_SCHEMA"), col("TAG_NAME"),
            col("TAG_OWNER"), col("DATA_TYPE"), col("ALLOWED_VALUES"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        try {
            String dbN = databaseName != null ? databaseName : catalog.getCurrentDatabase();
            String scN = schemaName != null ? schemaName : catalog.getCurrentSchema();
            if (dbN != null && scN != null) {
                for (final Tag tag : catalog.getDatabase(dbN).getSchema(scN).getTags()) {
                    result.addRow(new Row(
                        dbN, scN, tag.getName().toUpperCase(), tag.getOwner(),
                        "VARCHAR", null, tag.getCreatedTime(), tag.getCreatedTime(), tag.getComment()
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

    public ResultSet queryTagReferences(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("TAG_DATABASE"), col("TAG_SCHEMA"), col("TAG_NAME"), col("TAG_VALUE"),
            col("OBJECT_DATABASE"), col("OBJECT_SCHEMA"), col("OBJECT_NAME"),
            col("COLUMN_NAME"), col("DOMAIN")
        ));
        try {
            String dbN = databaseName != null ? databaseName : catalog.getCurrentDatabase();
            if (dbN == null) {
                return result;
            }
            Database db = catalog.getDatabase(dbN);
            addTagRows(result, dbN, null, db.getName(), null, "DATABASE", db.getTagValues());
            for (final Schema sc : db.getAllSchemas()) {
                if (schemaName != null && !sc.getName().equalsIgnoreCase(schemaName)) {
                    continue;
                }
                addTagRows(result, dbN, sc.getName(), sc.getName(), null, "SCHEMA", sc.getTagValues());
                for (final Table t : sc.getTables()) {
                    addTagRows(result, dbN, sc.getName(), t.getName(), null, "TABLE", t.getTagValues());
                    for (final TableColumn c : t.getColumns()) {
                        addTagRows(result, dbN, sc.getName(), t.getName(), c.getName(), "COLUMN", c.getTagValues());
                    }
                }
                for (final View v : sc.getViews()) {
                    addTagRows(result, dbN, sc.getName(), v.getName(), null, "VIEW", v.getTagValues());
                }
            }
        } catch (final Exception e) {
            logger.debug("Skipping tag-reference rows that could not be built: {}", e.getMessage(), e);
        }
        return result;
    }

    private void addTagRows(final ResultSet result, final String tagDb, final String objSchema,
                            final String objName, final String columnName, final String domain,
                            final Map<String, String> tagValues) {
        for (final Map.Entry<String, String> entry : tagValues.entrySet()) {
            result.addRow(new Row(
                tagDb, objSchema, entry.getKey(), entry.getValue(),
                tagDb, objSchema, objName, columnName, domain
            ));
        }
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

    private ResultSetColumn col(final String name) {
        return new ResultSetColumn(name, StringType.VARCHAR);
    }

    private ResultSetColumn colInt(final String name) {
        return new ResultSetColumn(name, NumericType.INTEGER);
    }

    private ResultSetColumn colLong(final String name) {
        return new ResultSetColumn(name, NumericType.BIGINT);
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
