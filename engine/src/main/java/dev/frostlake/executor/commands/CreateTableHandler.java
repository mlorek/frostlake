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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SelectItemAccessors;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.CheckConstraint;
import dev.frostlake.metastore.model.ContainerType;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles CREATE TABLE (plain, CLONE, and CREATE TABLE AS SELECT), extracted from
 * {@link DDLCommandHandler}, which keeps the CREATE dispatch and delegates here. Column parsing is
 * delegated to {@link ColumnDefinitionParser}; shared schema/name helpers are reached via the
 * {@code ddl} back-reference.
 */
public class CreateTableHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateTableHandler.class);

    private final DDLCommandHandler ddl;
    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final ColumnDefinitionParser columnParser;

    CreateTableHandler(final DDLCommandHandler ddl, final Catalog catalog, final QueryExecutor queryExecutor,
                       final ColumnDefinitionParser columnParser) {
        this.ddl = ddl;
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.columnParser = columnParser;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public Object handleCreateTable(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qualifiedName = queryExecutor.resolveObjectName(ctx.objectName());
        // Parts come from the parse tree (or the IDENTIFIER() value per dotted level) — never by
        // re-splitting the joined spelling, which breaks a quoted name containing a dot.
        final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
        final boolean isTransient = ctx.TRANSIENT() != null;
        // Snowflake spells one thing five ways. TEMPORARY, TEMP, LOCAL TEMPORARY, GLOBAL TEMPORARY and
        // VOLATILE all produce a table SHOW TABLES reports as kind=TEMPORARY — measured, each created
        // on a real account and read back. LOCAL/GLOBAL are accepted and ignored: Snowflake has no
        // cross-session temporary table for either spelling to mean.
        final boolean isTemporary = ctx.TEMPORARY() != null || ctx.TEMP() != null || ctx.VOLATILE() != null;
        // CREATE HYBRID TABLE is accepted and stored as an ordinary table; the flag is kept only so
        // SHOW HYBRID TABLES and the reported kind reflect the declaration.
        final boolean isHybrid = ctx.HYBRID() != null;
        final boolean orReplace = ctx.or_replace() != null;

        try {
            final Schema schema;
            final String tableName;
            final String databaseName;

            if (parts.length == 1) {
                schema = ddl.resolveCurrentSchema();
                databaseName = catalog.getCurrentDatabase();
                tableName = parts[0];
            } else if (parts.length == 2) {
                if (catalog.getCurrentDatabase() == null) {
                    throw new RuntimeException("No database selected");
                }
                databaseName = catalog.getCurrentDatabase();
                schema = catalog.getDatabase(databaseName).getSchema(parts[0]);
                tableName = parts[1];
            } else if (parts.length == 3) {
                databaseName = parts[0];
                schema = catalog.getDatabase(databaseName).getSchema(parts[1]);
                tableName = parts[2];
            } else {
                throw new RuntimeException("Invalid table name: " + qualifiedName);
            }

            ddl.checkCreatePrivilege(Privilege.CREATE_TABLE, ContainerType.SCHEMA, schema.getName());

            // CTAS evaluates its source BEFORE any OR REPLACE drop: Snowflake's replace is an atomic
            // swap, so CREATE OR REPLACE TABLE t AS SELECT ... FROM t reads the OLD table — and a
            // failing source SELECT must leave the existing table untouched.
            //
            // The BODY compiles before either column check, which is the order live reports in: over a
            // statement that is BOTH unnamable and uncompilable, live answers with the body's refusal —
            // `CREATE TABLE t AS SELECT UPPER(o) FROM ss` is the argument-type error, not "Missing
            // column specification", and the same holds for an unknown function and for a wrong-count
            // column list. This ran the naming check first and reported it instead. It is the CTAS twin
            // of the ordering a VIEW already follows.
            ResultSet ctasSnapshot = null;
            if (ctx.AS() != null && ctx.selectStatement() != null) {
                ctasSnapshot = queryExecutor.executeCtasSourceSelect(ctx.selectStatement());
                if (ctx.columnList() == null && ctx.columnListOptional() == null) {
                    rejectUnnamedCtasColumns(ctx.selectStatement());
                }
                rejectWrongColumnCount(ctx, ctasSnapshot);
            }

            // Handle OR REPLACE - drop table if it exists
            if (orReplace) {
                try {
                    final Table existingTable = schema.getTable(tableName);
                    if (existingTable != null) {
                        schema.dropTable(tableName);
                        final String fullyQualifiedName = databaseName.toUpperCase() + "." + schema.getName().toUpperCase() + "." + tableName.toUpperCase();
                        queryExecutor.getStorageEngine().dropTable(fullyQualifiedName);
                        // Discard buffered writes for the replaced table so pre-replace rows from this same
                        // transaction aren't flushed to (or resurrected in) the new table's storage at commit.
                        queryExecutor.getTransactionManager().discardBufferedWritesFor(fullyQualifiedName);
                        if (ddl.getStreamManager() != null) {
                            ddl.getStreamManager().onTableDropped(fullyQualifiedName);
                        }
                        logger.trace("Dropped existing table for OR REPLACE: {}", qualifiedName);
                    }
                } catch (final RuntimeException e) {
                    // Table doesn't exist, which is fine for OR REPLACE
                    logger.trace("No existing table to replace: {}", qualifiedName);
                }
            }

            final Table table;

            if (ctx.CLONE() != null) {
                // The table name is now objectName, so CLONE's source is qualifiedName(0) (was (1)).
                final String sourceTableName = getText(ctx.qualifiedName(0));
                final Table sourceTable = catalog.resolveTable(sourceTableName);

                // A transient table cannot become a permanent one by cloning (live-verified). The
                // other three combinations are all legal: transient→transient, permanent→transient
                // and permanent→permanent.
                if (sourceTable.isTransient() && !isTransient && !isTemporary) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "Transient object cannot be cloned to a permanent object."));
                }

                final List<TableColumn> clonedColumns = new ArrayList<>();
                for (final TableColumn col : sourceTable.getColumns()) {
                    final TableColumn clonedCol = new TableColumn(
                        col.getName(),
                        col.getDataType(),
                        col.isNullable(),
                        col.getDefaultValue(),
                        col.isPrimaryKey(),
                        col.isUnique(),
                        col.isAutoIncrement()
                    );
                    clonedCol.setComment(col.getComment());
                    clonedCol.setCollation(col.getCollation());
                    clonedColumns.add(clonedCol);
                }

                // If TEMPORARY/TRANSIENT is explicitly specified, use that; otherwise inherit from source
                final boolean tableIsTemporary = isTemporary ? isTemporary : sourceTable.isTemporary();
                final boolean tableIsTransient = isTransient ? isTransient : sourceTable.isTransient();
                table = new Table(tableName, clonedColumns, tableIsTemporary, tableIsTransient);

                // Extract comment from either position (after table name or at end)
                // If specified, override the cloned comment
                String comment = null;
                final List<FrostlakeParser.CommentClauseContext> comments = tailComments(ctx);
                if (comments.size() > 0) {
                    comment = ddl.extractComment(comments.get(0));
                }
                if (comment == null && comments.size() > 1) {
                    comment = ddl.extractComment(comments.get(1));
                }
                if (comment != null) {
                    table.setComment(comment);
                } else {
                    table.setComment(sourceTable.getComment());
                }

                table.setClusterKeys(sourceTable.getClusterKeys());
                table.setOwner(catalog.currentRoleForOwner());
                InlineTags.applyFrom(table, ctx.tableTailOption());
                applyChangeTracking(ctx, table);
                table.setHybrid(isHybrid);
                attachRowAccessPolicy(ctx, table);
            attachAggregationPolicy(ctx, table);
            attachJoinPolicy(ctx, table);
                schema.addTable(table);

                final String fullyQualifiedName = databaseName.toUpperCase() + "." + schema.getName().toUpperCase() + "." + tableName.toUpperCase();
                queryExecutor.getStorageEngine().createTable(fullyQualifiedName, table);
                queryExecutor.resetInternalTableStageDir(fullyQualifiedName);
                queryExecutor.resetCopyLoadHistory(fullyQualifiedName);

                final String sourceQualifiedName = ddl.resolveFullyQualifiedName(sourceTableName);
                queryExecutor.getStorageEngine().cloneTableData(sourceQualifiedName, fullyQualifiedName);

                logger.trace("Cloned table: {} from {}", qualifiedName, sourceTableName);
            } else if (ctx.LIKE() != null) {
                // CREATE TABLE … LIKE <source> — copy the source's column structure into a new empty
                // table (structure only, no data — unlike CLONE).
                final String sourceTableName = getText(ctx.qualifiedName(0));
                final Table sourceTable = catalog.resolveTable(sourceTableName);

                final List<TableColumn> likeColumns = new ArrayList<>();
                for (final TableColumn col : sourceTable.getColumns()) {
                    final TableColumn likeCol = new TableColumn(
                        col.getName(),
                        col.getDataType(),
                        col.isNullable(),
                        col.getDefaultValue(),
                        col.isPrimaryKey(),
                        col.isUnique(),
                        col.isAutoIncrement()
                    );
                    likeCol.setComment(col.getComment());
                    likeCol.setCollation(col.getCollation());
                    likeColumns.add(likeCol);
                }

                final boolean tableIsTemporary = isTemporary ? isTemporary : sourceTable.isTemporary();
                final boolean tableIsTransient = isTransient ? isTransient : sourceTable.isTransient();
                table = new Table(tableName, likeColumns, tableIsTemporary, tableIsTransient);

                String comment = null;
                final List<FrostlakeParser.CommentClauseContext> comments = tailComments(ctx);
                if (comments.size() > 0) {
                    comment = ddl.extractComment(comments.get(0));
                }
                if (comment == null && comments.size() > 1) {
                    comment = ddl.extractComment(comments.get(1));
                }
                if (comment != null) {
                    table.setComment(comment);
                }

                table.setClusterKeys(sourceTable.getClusterKeys());
                table.setOwner(catalog.currentRoleForOwner());
                InlineTags.applyFrom(table, ctx.tableTailOption());
                applyChangeTracking(ctx, table);
                table.setHybrid(isHybrid);
                attachRowAccessPolicy(ctx, table);
            attachAggregationPolicy(ctx, table);
            attachJoinPolicy(ctx, table);
                schema.addTable(table);

                final String fullyQualifiedName = databaseName.toUpperCase() + "." + schema.getName().toUpperCase() + "." + tableName.toUpperCase();
                queryExecutor.getStorageEngine().createTable(fullyQualifiedName, table);
                queryExecutor.resetInternalTableStageDir(fullyQualifiedName);
                queryExecutor.resetCopyLoadHistory(fullyQualifiedName);

                logger.trace("Created table: {} LIKE {}", qualifiedName, sourceTableName);
            } else if (ctx.AS() != null && ctx.selectStatement() != null) {
                // CREATE TABLE AS SELECT (CTAS). The source was evaluated above, before any OR REPLACE
                // drop. A stream read by the source is consumed once the table is created + populated
                // (consumeCtasStreams below), like a consuming DML.
                final ResultSet resultSet = ctasSnapshot;

                // Columns come from an explicit list before AS, in two forms:
                //   • a fully typed column list — CREATE TABLE t (id NUMBER, name VARCHAR) AS SELECT … —
                //     the declared names AND types define the table; the SELECT only supplies rows.
                //   • a names-only list — CREATE TABLE t (name, total) AS SELECT … — the names override
                //     the SELECT's output names and the types are taken from the result columns.
                // With neither, the SELECT's result columns define both names and types.
                final List<TableColumn> columns;
                if (ctx.columnList() != null) {
                    columns = columnParser.parseColumnList(ctx.columnList());
                } else {
                    final List<String> providedNames = new ArrayList<>();
                    if (ctx.columnListOptional() != null) {
                        for (final FrostlakeParser.NamePartContext id : ctx.columnListOptional().namePart()) {
                            providedNames.add(ParseTreeText.namePartText(id));
                        }
                    }
                    columns = new ArrayList<>();
                    int ctasColIdx = 0;
                    for (final ResultSetColumn rsCol : resultSet.getColumns()) {
                        final String colName = ctasColIdx < providedNames.size()
                            ? providedNames.get(ctasColIdx) : rsCol.getName();
                        columns.add(new TableColumn(colName, ctasColumnType(rsCol, resultSet, ctasColIdx), true, null, false, false, false));
                        ctasColIdx++;
                    }
                }

                // Determine if table should be temporary/transient
                final boolean tableIsTemporary = isTemporary || isTransient;
                table = new Table(tableName, columns, isTemporary, isTransient);
                applyTableConstraints(ctx.columnList(), table);

                // Extract comment from either position
                String comment = null;
                final List<FrostlakeParser.CommentClauseContext> comments = tailComments(ctx);
                if (comments.size() > 0) {
                    comment = ddl.extractComment(comments.get(0));
                }
                if (comment == null && comments.size() > 1) {
                    comment = ddl.extractComment(comments.get(1));
                }
                if (comment != null) {
                    table.setComment(comment);
                }

                // Extract cluster keys (CLUSTER BY may appear before or after the column list);
                // every referenced column must exist (live-verified, positioned refusal).
                ClusterKeyValidation.requireResolvable(clusterExpressions(ctx), table);
                final List<String> clusterKeys = extractClusterKeys(ctx);
                if (!clusterKeys.isEmpty()) {
                    table.setClusterKeys(clusterKeys);
                }

                table.setOwner(catalog.currentRoleForOwner());
                InlineTags.applyFrom(table, ctx.tableTailOption());
                applyChangeTracking(ctx, table);
                table.setHybrid(isHybrid);
                attachRowAccessPolicy(ctx, table);
            attachAggregationPolicy(ctx, table);
            attachJoinPolicy(ctx, table);
                schema.addTable(table);

                final String fullyQualifiedName = databaseName.toUpperCase() + "." + schema.getName().toUpperCase() + "." + tableName.toUpperCase();
                queryExecutor.getStorageEngine().createTable(fullyQualifiedName, table);
                queryExecutor.resetInternalTableStageDir(fullyQualifiedName);
                queryExecutor.resetCopyLoadHistory(fullyQualifiedName);

                // Insert data from SELECT into the new table, coercing each row to the declared column
                // types like a plain INSERT: a UNION of literal branches can carry STRINGS in a
                // TIMESTAMP-typed result column, and storing them raw made the values compare unequal to
                // real timestamps under EXCEPT / EQUAL_NULL despite rendering identically. LENIENTLY —
                // the SELECT's reported column types are best-effort, so a value that does not fit its
                // reported type is stored as produced rather than rejected.
                for (final Row resultRow : resultSet.getRows()) {
                    final Row typedRow = new Row(new ArrayList<>(resultRow.getValues()));
                    queryExecutor.coerceRowTypesLenient(table, typedRow);
                    queryExecutor.getStorageEngine().getTableStorage(fullyQualifiedName).insert(typedRow);
                }

                // Table created + populated: now consume any stream the source SELECT read.
                queryExecutor.consumeCtasStreams();
                logger.trace("Created table from SELECT: {}", qualifiedName);
            } else {
                // A body-less CREATE TABLE (only tail options like TAG / CLUSTER BY) makes an empty
                // table — lenient acceptance; columns arrive later via ALTER TABLE ... ADD.
                final List<TableColumn> columns = ctx.columnList() != null
                    ? columnParser.parseColumnList(ctx.columnList()) : new ArrayList<>();
                final List<ForeignKeyConstraint> foreignKeys = ctx.columnList() != null
                    ? columnParser.parseForeignKeys(ctx.columnList()) : new ArrayList<>();
                table = new Table(tableName, columns, isTemporary, isTransient);
                applyTableConstraints(ctx.columnList(), table);

                // Add foreign key constraints (final metadata only, final not enforced)
                for (final ForeignKeyConstraint fk : foreignKeys) {
                    table.addForeignKey(fk);
                }

                // Extract comment from either position (after table name or at end)
                // Prioritize the one right after table name if both are specified
                String comment = null;
                final List<FrostlakeParser.CommentClauseContext> comments = tailComments(ctx);
                if (comments.size() > 0) {
                    comment = ddl.extractComment(comments.get(0));
                }
                if (comment == null && comments.size() > 1) {
                    comment = ddl.extractComment(comments.get(1));
                }
                if (comment != null) {
                    table.setComment(comment);
                }

                // Extract cluster keys (CLUSTER BY may appear before or after the column list);
                // every referenced column must exist (live-verified, positioned refusal).
                ClusterKeyValidation.requireResolvable(clusterExpressions(ctx), table);
                final List<String> clusterKeys = extractClusterKeys(ctx);
                if (!clusterKeys.isEmpty()) {
                    table.setClusterKeys(clusterKeys);
                }

                table.setOwner(catalog.currentRoleForOwner());
                InlineTags.applyFrom(table, ctx.tableTailOption());
                applyChangeTracking(ctx, table);
                table.setHybrid(isHybrid);
                attachRowAccessPolicy(ctx, table);
            attachAggregationPolicy(ctx, table);
            attachJoinPolicy(ctx, table);
                schema.addTable(table);

                final String fullyQualifiedName = databaseName.toUpperCase() + "." + schema.getName().toUpperCase() + "." + tableName.toUpperCase();
                queryExecutor.getStorageEngine().createTable(fullyQualifiedName, table);
                queryExecutor.resetInternalTableStageDir(fullyQualifiedName);
                queryExecutor.resetCopyLoadHistory(fullyQualifiedName);

                logger.trace("Created table: {}", qualifiedName);
            }
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, qualifiedName);
        }
        return null;
    }

    /**
     * Applies the table-level constraint metadata a column list carries that the columns themselves cannot
     * hold: the name an explicit {@code CONSTRAINT <name> PRIMARY KEY} gave the key, and every table-level
     * UNIQUE constraint — one constraint per declaration, however many columns it spans, so a
     * {@code UNIQUE (a, b)} reports as ONE constraint under ONE name rather than one per column.
     */
    private void applyTableConstraints(final FrostlakeParser.ColumnListContext columnList, final Table table) {
        if (columnList == null) {
            return;
        }
        ColumnDefinitionParser.rejectDuplicateTableConstraints(table.getName(), columnList);
        table.setPrimaryKeyConstraintName(columnParser.parsePrimaryKeyConstraintName(columnList));
        for (final UniqueConstraint unique : columnParser.parseUniqueConstraints(columnList)) {
            table.addUniqueConstraint(unique);
        }
        final List<String> columnNames = new ArrayList<>();
        for (final TableColumn column : table.getColumns()) {
            columnNames.add(column.getName());
        }
        for (final CheckConstraint check : columnParser.parseCheckConstraints(columnList, columnNames)) {
            table.addCheckConstraint(check);
        }
    }

    /**
     * Snowflake's CTAS column validation (live-verified): with no explicit column list, every select
     * item must be able to NAME its table column — an unaliased item that is not a (possibly
     * parenthesized, possibly qualified) column reference is refused with "Missing column
     * specification" before the source query runs. A column reference of any name qualifies (even one
     * whose name needs quoting), an alias always qualifies, and a star item names the projection
     * wholesale, so its presence skips the check. A SET OPERATION is exempt entirely
     * (live-verified): the union output names its columns even where the same items in a single
     * query block would be refused.
     */
    /**
     * A CTAS column list must have exactly as many columns as the body produces, in BOTH of its forms —
     * live answers "Invalid column definition list" for the names-only {@code (a, b) AS SELECT s AS c}
     * and for the fully typed {@code (a INT, b INT) AS SELECT s AS c} alike. The names-only form padded
     * the missing name from the result and created the table; the typed form reached the write path and
     * failed there with "Row column count mismatch", which is not a sentence the account has.
     *
     * <p>Checked AFTER the body, which is where live checks it: a wrong count over an uncompilable body
     * reports the BODY's refusal, measured in both forms.
     *
     * @param ctx      the CREATE TABLE statement
     * @param snapshot the body's result, whose column count is the truth
     */
    private void rejectWrongColumnCount(final FrostlakeParser.CreateStatementContext ctx,
                                        final ResultSet snapshot) {
        if (snapshot == null) {
            return;
        }
        final int declared;
        if (ctx.columnListOptional() != null) {
            declared = ctx.columnListOptional().namePart().size();
        } else if (ctx.columnList() != null) {
            declared = columnParser.parseColumnList(ctx.columnList()).size();
        } else {
            return;
        }
        if (declared != snapshot.getColumns().size()) {
            throw new RuntimeException(SqlCompilationError.of("Invalid column definition list"));
        }
    }

    private void rejectUnnamedCtasColumns(final FrostlakeParser.SelectStatementContext select) {
        if (select.selectOperand().size() != 1) {
            return;
        }
        final FrostlakeParser.SelectOperandContext operand = select.selectOperand(0);
        if (operand.selectClause() == null || operand.selectClause().selectList() == null) {
            return;
        }
        boolean hasStar = false;
        boolean unnamedExpression = false;
        for (final FrostlakeParser.SelectItemContext item : operand.selectClause().selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                hasStar = true;
                continue;
            }
            if (SelectItemAccessors.isUnnamedExpressionItem(item)) {
                unnamedExpression = true;
            }
        }
        if (unnamedExpression && !hasStar) {
            throw new RuntimeException(SqlCompilationError.of("Missing column specification"));
        }
    }

    /**
     * Collects the {@code CLUSTER BY} key expressions for a CREATE TABLE. Snowflake accepts the clause
     * either immediately after the table name (before the column list) or after the column list; the
     * grammar allows {@code clusterByClause} in both positions, so this reads whichever one was supplied.
     * Returns an empty list when no clustering key was given.
     */
    /**
     * The column type for a CTAS result column: the result's own type when it is specific; when it is
     * the generic VARCHAR many computed/aggregate columns default to, the type inferred from the first
     * non-null value in that column — MAX(ts) must create a TIMESTAMP column (as Snowflake types it),
     * not a VARCHAR that stringifies every value on write and then never joins back to the source.
     */
    private DataType ctasColumnType(final ResultSetColumn rsCol, final ResultSet resultSet, final int colIdx) {
        final DataType declared = rsCol.getDataType();
        if (!(declared instanceof StringType)) {
            return declared;
        }
        // A measured numeric STATIC — a literal's own (p,s), a set operation's supertype fold, a
        // string branch's unification — is the live CTAS column type. The value scan below recovers
        // only the widest SCALE from the rows; it cannot see the declared width, so it stored
        // NUMBER(38,0) where live declares NUMBER(1,0) for {@code SELECT '5' UNION ALL SELECT 1}.
        final DataType staticType = rsCol.getStaticType();
        if (staticType instanceof NumericType && "NUMBER".equalsIgnoreCase(staticType.getName())) {
            return staticType;
        }
        for (final Row row : resultSet.getRows()) {
            final Object value = colIdx < row.getValues().size() ? row.getValue(colIdx) : null;
            if (value == null) {
                continue;
            }
            if (value instanceof java.time.LocalDateTime) {
                return DateTimeType.TIMESTAMP_NTZ;
            }
            if (value instanceof java.time.LocalDate) {
                return DateTimeType.DATE;
            }
            if (value instanceof java.time.LocalTime) {
                return DateTimeType.TIME;
            }
            if (value instanceof Boolean) {
                return BooleanType.BOOLEAN;
            }
            if (value instanceof Double || value instanceof Float) {
                // Approximate types never round on write.
                return NumericType.FLOAT;
            }
            if (value instanceof Number) {
                return numericCtasType(resultSet, colIdx);
            }
            return declared;
        }
        return declared;
    }

    /**
     * NUMBER carrying the widest scale seen in the column — Snowflake's CTAS column types keep the
     * literals' scale, and a bare scale-0 NUMBER would ROUND every fractional write into the new
     * table (live-verified write behavior), silently corrupting CTAS-then-INSERT fixtures.
     */
    private DataType numericCtasType(final ResultSet resultSet, final int colIdx) {
        int scale = 0;
        for (final Row row : resultSet.getRows()) {
            final Object value = colIdx < row.getValues().size() ? row.getValue(colIdx) : null;
            if (value instanceof BigDecimal && ((BigDecimal) value).scale() > scale) {
                scale = ((BigDecimal) value).scale();
            }
        }
        return scale == 0 ? NumericType.NUMBER : new NumericType("NUMBER", 38, scale);
    }

    /** CREATE TABLE ... CHANGE_TRACKING = TRUE|FALSE — the one modeled tail key=value option. */
    private void applyChangeTracking(final FrostlakeParser.CreateStatementContext ctx, final Table table) {
        // A key=value table property may be given ONCE (live-verified). The order they come in is
        // free, and a repeated CLUSTER BY stays legal — only the key=value family is checked here.
        final List<String> keys = new ArrayList<>();
        for (final FrostlakeParser.TableTailOptionContext tail : ctx.tableTailOption()) {
            if (tail.optionKey() != null) {
                keys.add(tail.optionKey().getText());
            }
        }
        PropertyDuplicates.reject(keys);
        for (final FrostlakeParser.TableTailOptionContext tail : ctx.tableTailOption()) {
            if (tail.optionKey() == null || tail.copyOptionValue() == null) {
                continue;
            }
            final String key = tail.optionKey().getText();
            final String valueText = tail.copyOptionValue().getText();
            // The values the account refuses inline, live-measured: a negative retention (the
            // bracketed invalid-value shape) and an extension time past the 90-day cap.
            if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(key) && valueText.startsWith("-")) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                    valueText, "DATA_RETENTION_TIME_IN_DAYS"));
            }
            if ("MAX_DATA_EXTENSION_TIME_IN_DAYS".equalsIgnoreCase(key)
                    && valueText.matches("[0-9]+") && Long.parseLong(valueText) > 90L) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Exceeds maximum allowable extension time (90)."));
            }
            if ("CHANGE_TRACKING".equalsIgnoreCase(key)) {
                table.setChangeTracking("TRUE".equalsIgnoreCase(valueText));
            } else if ("ENABLE_SCHEMA_EVOLUTION".equalsIgnoreCase(key)) {
                // Recorded at CREATE too, so SHOW TABLES answers Y without an ALTER first.
                table.setSchemaEvolution("TRUE".equalsIgnoreCase(valueText));
            }
        }
    }

    /** CREATE TABLE ... WITH JOIN POLICY p: the ALTER form's twin. */
    private void attachJoinPolicy(final FrostlakeParser.CreateStatementContext ctx, final Table table) {
        for (final FrostlakeParser.TableTailOptionContext tail : ctx.tableTailOption()) {
            if (tail.joinPolicyClause() == null) {
                continue;
            }
            final String written = getText(tail.joinPolicyClause().qualifiedName());
            if (catalog.findJoinPolicy(written) == null) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Join policy",
                    catalog.qualifiedObjectName(written)));
            }
            table.setJoinPolicyName(catalog.qualifiedObjectName(written));
        }
    }

    /** CREATE TABLE ... WITH AGGREGATION POLICY p [ENTITY KEY (cols)]: the ALTER form's twin. */
    private void attachAggregationPolicy(final FrostlakeParser.CreateStatementContext ctx, final Table table) {
        for (final FrostlakeParser.TableTailOptionContext tail : ctx.tableTailOption()) {
            if (tail.aggregationPolicyClause() == null) {
                continue;
            }
            final String written = getText(tail.aggregationPolicyClause().qualifiedName());
            if (catalog.findAggregationPolicy(written) == null) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Aggregation policy",
                    catalog.qualifiedObjectName(written)));
            }
            table.setAggregationPolicyName(catalog.qualifiedObjectName(written));
            final List<String> entityKey = new ArrayList<>();
            if (tail.aggregationPolicyClause().identifierList() != null) {
                for (final FrostlakeParser.IdentifierContext id
                        : tail.aggregationPolicyClause().identifierList().identifier()) {
                    entityKey.add(getText(id));
                }
            }
            table.setAggregationEntityKey(entityKey);
        }
    }

    /** CREATE TABLE ... ROW ACCESS POLICY p ON (cols): attach it like the ALTER form does. */
    private void attachRowAccessPolicy(final FrostlakeParser.CreateStatementContext ctx, final Table table) {
        for (final FrostlakeParser.TableTailOptionContext tail : ctx.tableTailOption()) {
            if (tail.rowAccessPolicyClause() == null) {
                continue;
            }
            final String written = getText(tail.rowAccessPolicyClause().qualifiedName());
            final RowAccessPolicy policy = RowAccessPolicyAttachment.require(catalog, written);
            final List<String> policyCols = new ArrayList<>();
            for (final FrostlakeParser.IdentifierContext id : tail.rowAccessPolicyClause().identifierList().identifier()) {
                final String colName = getText(id);
                if (!table.hasColumn(colName)) {
                    // At CREATE an unknown policy column is a plain "does not exist" — the ALTER form
                    // reports the same miss as a positioned invalid identifier (live-verified).
                    throw new RuntimeException(SqlCompilationError.columnDoesNotExist(
                        colName.toUpperCase(Locale.ROOT)));
                }
                policyCols.add(colName);
            }
            RowAccessPolicyAttachment.check(policy, table, table.getName(), written, policyCols);
            table.setRowAccessPolicyName(written.toUpperCase());
            table.setRowAccessPolicyColumns(policyCols);
        }
    }

    /**
     * The comment clauses of a CREATE TABLE, in order — they arrive inside the tableTailOption
     * groups. A table may carry only ONE, wherever it sits: a second clause is refused
     * (live-verified, and the refusal is specific to COMMENT — a repeated CLUSTER BY is accepted).
     */
    private static List<FrostlakeParser.CommentClauseContext> tailComments(final FrostlakeParser.CreateStatementContext ctx) {
        final List<FrostlakeParser.CommentClauseContext> comments = new ArrayList<>(ctx.commentClause());
        for (final FrostlakeParser.TableTailOptionContext tail : ctx.tableTailOption()) {
            if (tail.commentClause() != null) {
                comments.add(tail.commentClause());
            }
        }
        if (comments.size() > 1) {
            throw new RuntimeException(SqlCompilationError.of("duplicate property 'COMMENT';"));
        }
        return comments;
    }

    private static List<FrostlakeParser.ClusterByClauseContext> tailClusterBy(final FrostlakeParser.CreateStatementContext ctx) {
        final List<FrostlakeParser.ClusterByClauseContext> clauses = new ArrayList<>();
        for (final FrostlakeParser.TableTailOptionContext tail : ctx.tableTailOption()) {
            if (tail.clusterByClause() != null) {
                clauses.add(tail.clusterByClause());
            }
        }
        return clauses;
    }

    /** The first CLUSTER BY clause's expressions — the clause FL applies — for validation. */
    private static List<FrostlakeParser.ExpressionContext> clusterExpressions(final FrostlakeParser.CreateStatementContext ctx) {
        final List<FrostlakeParser.ClusterByClauseContext> clauses = tailClusterBy(ctx);
        return clauses.isEmpty()
            ? new ArrayList<>()
            : clauses.get(0).expressionList().expression();
    }

    private static List<String> extractClusterKeys(final FrostlakeParser.CreateStatementContext ctx) {
        final List<String> clusterKeys = new ArrayList<>();
        final List<FrostlakeParser.ClusterByClauseContext> clauses = tailClusterBy(ctx);
        if (!clauses.isEmpty()) {
            for (final FrostlakeParser.ExpressionContext exprCtx : clauses.get(0).expressionList().expression()) {
                clusterKeys.add(exprCtx.getText());
            }
        }
        return clusterKeys;
    }

}
