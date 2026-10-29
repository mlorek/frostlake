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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.DefaultMarkerExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.JsonArrayExpression;
import dev.frostlake.executor.expressions.JsonObjectExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.MultiColumnScalarSubqueryException;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;
import dev.frostlake.executor.expressions.RowExpression;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.StreamVersionKind;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.transaction.TransactionWriteSet;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StatementResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INSERT write-path query stage extracted from {@link QueryExecutor}: single-table
 * {@code INSERT ... VALUES}/{@code INSERT ... SELECT} (incl. OVERWRITE and WITH-clause CTEs), Snowflake
 * multi-table {@code INSERT [FIRST|ALL] ... WHEN ... INTO ...}, per-row construction (auto-increment +
 * DEFAULT + explicit-column mapping) and the buffered (deferred-apply) vs. immediate write with
 * transaction logging + stream tracking, plus the deferred-INSERT PK/UK duplicate checks. The shared
 * write-constraint enforcement ({@code enforceColumnConstraints}), per-column value resolution
 * ({@code insertColumnValue}), DML-count result shaping and all non-INSERT-specific helpers stay on the
 * owning executor and are reached through {@code executor}; mutable per-query flags (deferred-apply) are
 * read live from the executor so semantics stay byte-for-byte identical to the pre-extraction code.
 */
final class InsertExecutor {

    private static final Logger logger = LoggerFactory.getLogger(InsertExecutor.class);

    private final QueryExecutor executor;

    InsertExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    Object executeInsertFromContext(final FrostlakeParser.InsertStatementContext ctx) {
        // An EXPLAIN compiles the statement and changes nothing (see ExplainPlan).
        final boolean planning = ExplainPlan.planning();
        if (!planning) {
            executor.checkNotReadOnly();
        }
        try {
            // Auto-start transaction if not active
            if (!planning && !executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().beginTransaction();
            }

            // WITH-prefixed DML is not Snowflake syntax (live-verified); CTEs reach a DML statement
            // only inside its subqueries.
            final Map<String, ResultSet> cteResults = null;

            // A target the current schema does not hold is looked for along the session's SEARCH_PATH.
            final String tableName = executor.searchPathQualified(ctx.objectName().qualifiedName() == null
                ? executor.resolveObjectName(ctx.objectName())
                : executor.getQualifiedName(ctx.objectName().qualifiedName()));
            // A missing source relation is reported ahead of a missing target (live-verified).
            if (ctx.selectStatement() != null) {
                executor.requireSourceRelations(ctx.selectStatement());
            }
            DmlTarget.requireTable(executor, tableName, "INSERT");
            final Table table = executor.getCatalog().resolveTableAsWritten(tableName, "Table");
            DmlTarget.refuseEventTable(table, "INSERT");

            // Check INSERT permission
            if (executor.getSecurityManager() != null) {
                // What the source reads is asked for first, then INSERT on the target (live-verified).
                if (ctx.selectStatement() != null) {
                    final Set<String> sourceCteNames = new HashSet<>();
                    QueryExecutor.collectCteNames(ctx.selectStatement(), sourceCteNames);
                    executor.requireSelectIn(ctx.selectStatement(), sourceCteNames);
                }
                executor.requirePrivilege(Privilege.INSERT, "TABLE", tableName);
            }

            // OVERWRITE replaces the table's contents, but the truncate must happen only AFTER the source
            // rows have been produced: a self-referencing INSERT OVERWRITE INTO t … SELECT … FROM t reads the
            // table it overwrites, so truncating first made the source SELECT see an empty table and the
            // statement silently wiped the data (0 rows in, 0 rows out).
            final boolean isOverwrite = ctx.OVERWRITE() != null;

            // Get column names if specified
            List<String> columnNames = null;
            if (ctx.insertColumnList() != null) {
                columnNames = new ArrayList<>();
                for (final FrostlakeParser.InsertColumnItemContext item
                        : ctx.insertColumnList().insertColumnItem()) {
                    // Checked by requireColumnList once the values' count is known: live weighs the
                    // count first, and the source query's own errors ahead of the list's.
                    columnNames.add(ParseTreeText.namePartText(
                        item.namePart(item.namePart().size() - 1)));
                }
            } else {
                // A positional INSERT fills every column but a virtual one (see VirtualColumns).
                columnNames = VirtualColumns.writtenNames(table);
            }

            // Parse values - either from VALUES clause or SELECT statement
            final List<List<Object>> valuesList = new ArrayList<>();
            // The columns a query writes with a declared width that fits theirs, which no value is measured for.
            boolean[] fitByDeclaredWidth = null;
            boolean[] nullableSources = null;
            // The columns an aggregate over no table writes, whose range refusal is bare (see FoldedAggregateSources).
            boolean[] foldedSources = null;

            if (ctx.valueTupleList() != null) {
                if (ctx.insertColumnList() != null) {
                    requireColumnList(table, ctx.insertColumnList(),
                        ctx.valueTupleList().valueTuple(0).valueList().booleanExpr().size());
                } else if (ctx.valueTupleList().valueTuple(0).valueList().booleanExpr().size()
                        != VirtualColumns.writtenCount(table)) {
                    // Positionally too, the count is weighed before any item's own refusal (live-verified).
                    throw new RuntimeException(SqlCompilationError.of(
                        "Insert value list does not match column list expecting " + VirtualColumns.writtenCount(table)
                        + " but got " + ctx.valueTupleList().valueTuple(0).valueList().booleanExpr().size()));
                }
                // INSERT ... VALUES — one evaluator serves every cell (the dummy table and row carry
                // no per-cell state, so per-cell construction was pure allocation).
                final Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
                final Row dummyRow = new Row(new ArrayList<>());
                // No scripting variables are offered to the evaluator: INSERT is an embedded SQL
                // statement, where a stored-procedure parameter / DECLAREd / LET name must be
                // written :name (a bare one is an identifier — live: "invalid identifier 'V'").
                final ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
                // Every item's own refusals come first, row after row; then each column's rows fold to one
                // type (see ValuesColumnFold) and the column's type match judges that type, all before a
                // value is computed.
                final List<FrostlakeParser.ValueTupleContext> tuples = ctx.valueTupleList().valueTuple();
                final List<List<Expression>> rowCells = new ArrayList<>(tuples.size());
                final List<List<DataType>> rowTypes = new ArrayList<>(tuples.size());
                for (final FrostlakeParser.ValueTupleContext tuple : tuples) {
                    final List<Expression> cells = new ArrayList<>();
                    final List<DataType> types = new ArrayList<>();
                    for (final FrostlakeParser.BooleanExprContext expr : tuple.valueList().booleanExpr()) {
                        final String exprText = executor.getOriginalText(expr);
                        rejectSemiStructuredValueExpression(exprText, evaluator);
                        final Expression parsed = parsedValueOrNull(exprText);
                        cells.add(parsed);
                        types.add(valueStaticType(parsed, evaluator));
                    }
                    rowCells.add(cells);
                    rowTypes.add(types);
                }
                final List<DataType> foldedTypes = foldValuesColumns(rowCells, rowTypes);
                nullableSources = subqueryCells(table, columnNames, rowCells);
                // A column whose rows fold to a DECFLOAT is written into a VARIANT or a text column in the
                // column's own form of it (see DecfloatWrites), as INSERT … SELECT writes it.
                final DataType[] decfloatTargets = new DataType[foldedTypes.size()];
                for (int position = 0; position < foldedTypes.size(); position++) {
                    final TableColumn target = valuesTarget(table, columnNames, position);
                    if (target != null) {
                        rejectRowValue(rowCells, position, target, evaluator);
                        ColumnTypeFamilies.rejectMismatch(target, foldedTypes.get(position));
                        if (DecfloatWrites.reshapes(foldedTypes.get(position), target.getDataType())) {
                            decfloatTargets[position] = target.getDataType();
                        }
                    }
                }
                // A value a subquery hands out of a projection-restricted column is judged as that projection is:
                // INSERT INTO t VALUES ((SELECT p FROM pt LIMIT 1)) writes it into t.
                final List<FrostlakeParser.BooleanExprContext> inserted = new ArrayList<>();
                for (final FrostlakeParser.ValueTupleContext tuple : tuples) {
                    inserted.addAll(tuple.valueList().booleanExpr());
                }
                executor.rejectRestrictedAssignments(inserted, null, null, null);
                if (planning) {
                    return null;
                }
                for (int row = 0; row < tuples.size(); row++) {
                    final List<Object> values = new ArrayList<>();
                    int position = 0;
                    for (final FrostlakeParser.BooleanExprContext expr : tuples.get(row).valueList().booleanExpr()) {
                        final String exprText = executor.getOriginalText(expr);
                        // Where this value starts in the statement, so a refusal raised while evaluating
                        // it can report live's position — an unresolvable :bind in a VALUES list is
                        // reported at the colon, which is this fragment's own origin.
                        final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
                            expr.getStart().getLine(), expr.getStart().getCharPositionInLine()));
                        final Object value;
                        try {
                            // The bare DML DEFAULT is not a value: it means "this column's declared
                            // default", which only the row builder knows, so the MARKER travels in the
                            // value list and is substituted there. Read from the parse tree, never from
                            // the text — and evaluating it would raise the refusal live gives a DEFAULT
                            // that is not standing alone.
                            final Expression parsed = ExpressionEvaluator.parse(exprText);
                            if (!(parsed instanceof DefaultMarkerExpression)) {
                                // A name stands for nothing in a VALUES row: a date part's word too, which is a
                                // value only as a function's argument (live-verified: VALUES (s) is invalid
                                // identifier 'S').
                                evaluator.validateColumnScope(parsed);
                            }
                            value = parsed instanceof DefaultMarkerExpression
                                ? parsed : evaluator.evaluate(exprText, dummyRow);
                        } finally {
                            ExpressionSource.end(displaced);
                        }
                        final Object folded = convertToFold(value, rowTypes.get(row).get(position),
                            foldedTypes.get(position), table, columnNames, position, tableName);
                        values.add(decfloatTargets[position] != null && !(folded instanceof DefaultMarkerExpression)
                            ? DecfloatWrites.stored(folded, decfloatTargets[position]) : folded);
                        position++;
                    }
                    valuesList.add(values);
                }
            } else if (ctx.selectStatement() != null) {
                // INSERT ... SELECT. A stream read in this subquery is consumed when the txn commits (the DML
                // window is marked centrally in SQLCommandVisitor.visitDmlStatement); pass CTE results along.
                ProjectionSlot.reset();
                if (planning) {
                    compileSelectSource(ctx, table, tableName, columnNames);
                    return null;
                }
                final ResultSet selectResult;
                try {
                    // Its values leave the query into the table, so a projection policy judges it as a result's.
                    selectResult = executor.executeExportedSelect(ctx.selectStatement(), cteResults);
                    // Writing reads every cell, so a fault a relation deferred raises here.
                    DeferredFault.requireNone(selectResult.getRows());
                } catch (final RuntimeException failed) {
                    if (refusesColumnCount(failed)) {
                        rejectRowItems(ctx.selectStatement(), table, columnNames);
                    }
                    if (refusesRowItem(failed)) {
                        rejectRowItemSource(ctx, table, columnNames);
                    }
                    // The statement compiles whole before a row is read, so a source column its target cannot take
                    // is refused before a row's fault is (live-verified: an ARRAY of a text that is no number written
                    // to an ARRAY(NUMBER(10,2)) column is "incompatible types", not the text's refusal).
                    rejectPlannedSource(ctx, table, columnNames);
                    throw sourceQueryFailure(tableName, table, columnNames, failed);
                }
                requireSourceWidth(ctx, table, selectResult.getColumns().size());
                rejectMismatchedSelectColumnTypes(table, columnNames, selectResult.getColumns());
                fitByDeclaredWidth = fitByDeclaredWidth(table, columnNames, selectResult.getColumns());
                nullableSources = nullableSources(table, columnNames, selectResult.getColumns());
                foldedSources = FoldedAggregateSources.query(executor, table, ctx.selectStatement());
                final DataType[] decfloatMembers = decfloatIntoVariant(table, columnNames, selectResult.getColumns());
                for (final Row row : selectResult.getRows()) {
                    valuesList.add(asDecfloatMembers(row.getValues(), decfloatMembers));
                }
            }

            // Insert rows
            int rowsInserted = 0;
            // Use fully qualified name for storage access
            final String fullyQualifiedName = executor.getFullyQualifiedTableName(tableName);

            // The source rows are now materialized, so it is safe to replace the table's contents. Route through
            // the same transaction-aware path as TRUNCATE: clearing the base store directly left rows this
            // transaction had inserted earlier in place, so the OVERWRITE appended to them.
            if (isOverwrite) {
                // An INSERT OVERWRITE that removes rows waits for another session's transaction that holds the table,
                // as a TRUNCATE does, where a plain INSERT never waits (live-verified).
                if (executor.getStorageEngine().getTableStorage(fullyQualifiedName).getRowCount() > 0) {
                    executor.requireTableUnlocked(fullyQualifiedName, table.getName());
                }
                executor.emptyTableContents(fullyQualifiedName);
                logger.trace("Truncated table {} due to INSERT OVERWRITE", tableName);
            }

            // Statement-scoped hoists: the column mapping and (in deferred mode) the duplicate-key
            // guard's prefetched sets serve every row. The guard is created AFTER the OVERWRITE
            // truncation so its pending-insert prefetch sees the post-truncate write set.
            final int[] valueIndexes = insertValueIndexes(table, columnNames);
            final DeferredInsertGuard guard = executor.isDeferredApply()
                ? new DeferredInsertGuard(table,
                    executor.getStorageEngine().getTableStorage(fullyQualifiedName),
                    executor.getTransactionManager().getCurrentTransaction().getWriteSet(),
                    fullyQualifiedName,
                    executor.getStorageEngine().isEnforcePrimaryKey(),
                    executor.getStorageEngine().isEnforceUniqueKey())
                : null;
            // A VALUES list binds its items straight into the columns' slots; rows arriving from a
            // query are converted as expressions. The out-of-range refusal prints which of the two
            // happened, so the distinction travels with the row.
            final boolean boundToColumnSlot = ctx.valueTupleList() != null;
            // A column the statement leaves out is no VALUES item: its DEFAULT is classed as a query's value is.
            final boolean[] filledByDefault = boundToColumnSlot ? new boolean[table.columnCount()] : null;
            for (final List<Object> values : valuesList) {
                final Row row = buildInsertRow(table, fullyQualifiedName, columnNames, valueIndexes, values,
                    filledByDefault);
                executor.enforceColumnConstraintsForDml(table, row, boundToColumnSlot, tableName, filledByDefault,
                    fitByDeclaredWidth, nullableSources, foldedSources);
                storeInsertedRow(table, fullyQualifiedName, row, guard);
                rowsInserted++;
            }

            logger.trace("Inserted {} row(s) into table: {}", rowsInserted, tableName);
            if (!isOverwrite) {
                executor.noteStatementTarget(fullyQualifiedName, StreamVersionKind.INSERT);
            }
            // LAST_ALTERED moves for the statement, whether or not a row reached the table.
            table.markDmlRan(StatementClock.commitInstant());
            return executor.dmlCountResult("number of rows inserted", rowsInserted);

        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            throw StatementErrors.propagate(e);
        }
    }

    /**
     * Compiles an INSERT's source query as the statement compiles it before a row is read, which is all an EXPLAIN
     * does: the query is planned for its shape, its columns counted against the ones they fill, and their types
     * matched against theirs.
     */
    private void compileSelectSource(final FrostlakeParser.InsertStatementContext ctx, final Table table,
                                     final String tableName, final List<String> columnNames) {
        final List<ResultSetColumn> columns;
        try {
            columns = executor.planSelectStatement(ctx.selectStatement()).shape.getColumns();
        } catch (final RuntimeException failed) {
            if (refusesColumnCount(failed)) {
                rejectRowItems(ctx.selectStatement(), table, columnNames);
            }
            if (refusesRowItem(failed)) {
                rejectRowItemSource(ctx, table, columnNames);
            }
            throw sourceQueryFailure(tableName, table, columnNames, failed);
        }
        requireSourceWidth(ctx, table, columns.size());
        rejectMismatchedSelectColumnTypes(table, columnNames, columns);
    }

    /**
     * Refuses an INSERT's source query whose run failed by what its plan alone shows, as the statement compiles it
     * before a row is read: its width, then its columns' types. A query that cannot even be planned keeps its own
     * failure, and so does one with a select of no FROM, whose items its planning would compute a second time - a
     * sequence's NEXTVAL among them.
     */
    private void rejectPlannedSource(final FrostlakeParser.InsertStatementContext ctx, final Table table,
                                     final List<String> columnNames) {
        for (final FrostlakeParser.SelectOperandContext operand : ctx.selectStatement().selectOperand()) {
            if (operand.selectClause() == null || operand.selectClause().tableExpression() == null) {
                return;
            }
        }
        final List<ResultSetColumn> columns;
        try {
            columns = executor.planSelectStatement(ctx.selectStatement()).shape.getColumns();
        } catch (final RuntimeException unplanned) {
            return;
        }
        requireSourceWidth(ctx, table, columns.size());
        rejectMismatchedSelectColumnTypes(table, columnNames, columns);
    }

    /**
     * Counts the columns an INSERT's source query answers against the ones they fill — its column list's, else
     * every column of the table — before any row is read, so an empty source is refused too (live-verified).
     */
    private void requireSourceWidth(final FrostlakeParser.InsertStatementContext ctx, final Table table,
                                    final int width) {
        if (ctx.insertColumnList() != null) {
            requireColumnList(table, ctx.insertColumnList(), width);
        } else if (width != VirtualColumns.writtenCount(table)) {
            throw new RuntimeException(SqlCompilationError.of(
                "Insert value list does not match column list expecting " + VirtualColumns.writtenCount(table)
                + " but got " + width));
        }
    }

    /**
     * Snowflake's compile-time INSERT ... SELECT type matching (live-verified matrix): a source
     * column whose STATIC type family cannot implicitly convert to its target column's family is
     * refused before any row is written — {@code Expression type does not match column data type,
     * expecting <target> but got <source> for column <name>}, both types spelled with their
     * parameters. VARCHAR still converts to numbers, booleans, temporals and BINARY at row time
     * (value-parse errors), and VARIANT casts to any scalar or container at row time — but a
     * VARCHAR, temporal or BINARY source never reaches a VARIANT column, containers never leave
     * their own family except through VARIANT, and BINARY accepts nothing but strings. A TIME
     * source against a TIMESTAMP column has its own sentence:
     * {@code incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]}. A column with NO audited
     * static type is left alone — the channel never guesses.
     */
    /**
     * The column type each DECFLOAT source column is written into, where that column holds it in its own form: a
     * VARIANT as the DECFLOAT member it is, {@code TYPEOF(v)} over such a row being DECFLOAT (live-verified), and a
     * text column as its text (see DecfloatWrites). A runtime DECFLOAT is a decimal like a NUMBER's, so the source
     * column's type decides. Null for every other source column.
     */
    private static DataType[] decfloatIntoVariant(final Table table, final List<String> columnNames,
                                                  final List<ResultSetColumn> sourceColumns) {
        final DataType[] marked = new DataType[sourceColumns.size()];
        for (int i = 0; i < sourceColumns.size(); i++) {
            final TableColumn target = columnNames != null
                ? (i < columnNames.size() ? table.getColumn(columnNames.get(i)) : null)
                : (i < table.getColumns().size() ? table.getColumns().get(i) : null);
            if (target != null && DecfloatWrites.reshapes(sourceColumns.get(i).getDataType(), target.getDataType())) {
                marked[i] = target.getDataType();
            }
        }
        return marked;
    }

    /** A source row with each marked DECFLOAT as its column holds it (see decfloatIntoVariant). */
    private static List<Object> asDecfloatMembers(final List<Object> values, final DataType[] marked) {
        List<Object> out = values;
        for (int i = 0; i < marked.length && i < values.size(); i++) {
            if (marked[i] != null) {
                final Object stored = DecfloatWrites.stored(values.get(i), marked[i]);
                if (stored != values.get(i)) {
                    if (out == values) {
                        out = new ArrayList<>(values);
                    }
                    out.set(i, stored);
                }
            }
        }
        return out;
    }

    /**
     * The text columns a query writes from a column whose declared width fits theirs: the write is judged by that
     * width and not by the value, so the six characters a table function declared VARCHAR(3) answers are written into
     * a VARCHAR(3) column (live-verified). One flag per column of the table; null when no column is so written.
     */
    private static boolean[] fitByDeclaredWidth(final Table table, final List<String> columnNames,
                                                final List<ResultSetColumn> sourceColumns) {
        boolean[] fit = null;
        final int pairs = columnNames != null
            ? Math.min(columnNames.size(), sourceColumns.size())
            : Math.min(table.getColumns().size(), sourceColumns.size());
        for (int i = 0; i < pairs; i++) {
            final TableColumn target = columnNames != null
                ? table.getColumn(columnNames.get(i)) : table.getColumns().get(i);
            final DataType source = sourceColumns.get(i).getStaticType();
            if (target != null && target.getDataType() instanceof StringType && source instanceof StringType
                    && !((StringType) source).isUnsized()
                    && ((StringType) source).getMaxLength() <= ((StringType) target.getDataType()).getMaxLength()) {
                fit = fit != null ? fit : new boolean[table.getColumns().size()];
                fit[table.getColumns().indexOf(target)] = true;
            }
        }
        return fit;
    }

    /**
     * The columns a query writes from a value that can be NULL, which a refusal of a number too wide for its column
     * prints as {@code {nullable}} even when the column is NOT NULL (see
     * {@link QueryExecutor#enforceColumnConstraintsForDml}): the projection's own judgement of its item where it made
     * one, else the column it carries out. One flag per column of the table; null when no column is so written.
     */
    /**
     * The columns a VALUES list writes a scalar subquery into, which the account types nullable whatever the
     * subquery reads: a number too wide for a NOT NULL column prints its type {@code {nullable}} then,
     * {@code (SELECT 12345)} included (live-verified). One flag per column; null when none.
     */
    private static boolean[] subqueryCells(final Table table, final List<String> columnNames,
                                           final List<List<Expression>> rowCells) {
        boolean[] nullable = null;
        for (final List<Expression> cells : rowCells) {
            for (int position = 0; position < cells.size(); position++) {
                final TableColumn target = cells.get(position) instanceof SubqueryExpression
                    ? valuesTarget(table, columnNames, position) : null;
                if (target != null) {
                    nullable = nullable != null ? nullable : new boolean[table.getColumns().size()];
                    nullable[table.getColumns().indexOf(target)] = true;
                }
            }
        }
        return nullable;
    }

    private static boolean[] nullableSources(final Table table, final List<String> columnNames,
                                             final List<ResultSetColumn> sourceColumns) {
        boolean[] nullable = null;
        final int pairs = columnNames != null
            ? Math.min(columnNames.size(), sourceColumns.size())
            : Math.min(table.getColumns().size(), sourceColumns.size());
        for (int i = 0; i < pairs; i++) {
            final TableColumn target = columnNames != null
                ? table.getColumn(columnNames.get(i)) : table.getColumns().get(i);
            if (target != null && sourceColumns.get(i).canBeNull()) {
                nullable = nullable != null ? nullable : new boolean[table.getColumns().size()];
                nullable[table.getColumns().indexOf(target)] = true;
            }
        }
        return nullable;
    }

    private void rejectMismatchedSelectColumnTypes(final Table table, final List<String> columnNames,
                                                   final List<ResultSetColumn> sourceColumns) {
        final int pairs = columnNames != null
            ? Math.min(columnNames.size(), sourceColumns.size())
            : Math.min(table.getColumns().size(), sourceColumns.size());
        for (int i = 0; i < pairs; i++) {
            final TableColumn target = columnNames != null
                ? table.getColumn(columnNames.get(i)) : table.getColumns().get(i);
            if (target == null) {
                continue;
            }
            ColumnTypeFamilies.rejectMismatch(target, sourceColumns.get(i).getStaticType());
        }
    }

    /**
     * A VALUES column holding a scalar subquery of several columns, where Frostlake reached the value and refused
     * its column count. A single row types it ROW(ANY), a type no column takes; across several rows the column
     * cannot fold it at all: "Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] in VALUES clause" (live-verified).
     * A parenthesized list is ROW(ANY) in one row and in several.
     */
    private static void rejectRowValue(final List<List<Expression>> rowCells, final int position,
                                       final TableColumn target, final ExpressionEvaluator evaluator) {
        for (final List<Expression> cells : rowCells) {
            final Expression cell = position < cells.size() ? cells.get(position) : null;
            final String rowType = cell == null ? null : evaluator.multiColumnRowText(cell);
            if (rowType == null) {
                continue;
            }
            if (rowCells.size() > 1 && !(cell instanceof RowExpression)) {
                throw new RuntimeException(SqlCompilationError.of("Invalid data type [" + rowType + "] in VALUES clause"));
            }
            ColumnTypeFamilies.rejectRowValue(target, "ROW(ANY)");
        }
    }

    /**
     * An INSERT … SELECT item that is a scalar subquery of several columns is typed as the ROW of its items and
     * matched against its column, a type no column takes: {@code INSERT INTO t (a) SELECT (SELECT 1, 2)} is
     * "expecting NUMBER(38,0) but got ROW(NUMBER(1,0), NUMBER(1,0)) for column A", where Frostlake ran the source
     * query and refused the subquery's column count. The source's own names are judged first, while it runs. An item
     * of a derived table the source reads through a star is matched the same way (live-verified).
     *
     * @param source      the source query
     * @param table       the target table
     * @param columnNames the written column list, or null for the table's columns in order
     */
    private void rejectRowItems(final FrostlakeParser.SelectStatementContext source, final Table table,
                                final List<String> columnNames) {
        final List<ParserRuleContext> items = sourceItems(source);
        if (items == null) {
            return;
        }
        final ExpressionEvaluator scope = new ExpressionEvaluator(null, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        // In the table's column order, whatever order the column list writes them in (live-verified).
        for (final TableColumn column : table.getColumns()) {
            for (int i = 0; i < items.size(); i++) {
                if (valuesTarget(table, columnNames, i) != column) {
                    continue;
                }
                final String rowType;
                try {
                    rowType = scope.multiColumnRowText(ExpressionEvaluator.parse(executor.getOriginalText(items.get(i))));
                } catch (final RuntimeException unparseable) {
                    continue;
                }
                if (rowType != null) {
                    ColumnTypeFamilies.rejectRowValue(column, rowType);
                }
            }
        }
    }

    /** Whether a failure is, or was caused by, the refusal of a select item that is a ROW. */
    private static boolean refusesRowItem(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RowSelectItemException) {
                return true;
            }
        }
        return false;
    }

    /**
     * An INSERT … SELECT whose source's one item is a ROW is judged as a write before the item's own refusal: the
     * source's width against the columns it fills, then the ROW's type against its column - {@code Insert value list
     * does not match column list expecting 2 but got 1}, {@code Expression type does not match column data type,
     * expecting NUMBER(38,0) but got ROW(NUMBER(1,0), NUMBER(1,0)) for column A} (live-verified).
     */
    private void rejectRowItemSource(final FrostlakeParser.InsertStatementContext ctx, final Table table,
                                     final List<String> columnNames) {
        final String members = SelectItemAccessors.rowMembersQuery(ctx.selectStatement());
        if (members == null) {
            return;
        }
        requireSourceWidth(ctx, table, 1);
        final List<ResultSetColumn> columns;
        try {
            columns = executor.planSelectStatement(executor.subqueryStatement(members)).shape.getColumns();
        } catch (final RuntimeException unplanned) {
            return;
        }
        final StringBuilder row = new StringBuilder("ROW(");
        for (int i = 0; i < columns.size(); i++) {
            final DataType type = columns.get(i).getStaticType();
            row.append(i > 0 ? ", " : "").append(type == null ? "NULL" : SqlTypeNames.canonical(type));
        }
        ColumnTypeFamilies.rejectRowValue(valuesTarget(table, columnNames, 0), row.append(')').toString());
    }

    /** Whether a failure is, or was caused by, the refusal of a scalar subquery's column count. */
    private static boolean refusesColumnCount(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MultiColumnScalarSubqueryException) {
                return true;
            }
        }
        return false;
    }

    /**
     * The items of a source query of one SELECT, in order: each item's expression, a star over a single derived
     * table expanded to that table's own items. Null when the positions cannot be told: a set operation, a star over
     * anything else.
     */
    private static List<ParserRuleContext> sourceItems(final FrostlakeParser.SelectStatementContext source) {
        if (source == null || source.selectOperand().size() != 1 || source.selectOperand(0).selectClause() == null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext clause = source.selectOperand(0).selectClause();
        final List<ParserRuleContext> items = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            if (SelectItemAccessors.isExprItem(item)) {
                items.add(SelectItemAccessors.getItemExpression(item));
                continue;
            }
            final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
            if (!SelectItemAccessors.isStarItem(item) || from == null || from.tableReference().size() != 1
                    || !from.joinClause().isEmpty() || from.tableReference(0).tableSource().selectStatement() == null) {
                return null;
            }
            final List<ParserRuleContext> derived = sourceItems(from.tableReference(0).tableSource().selectStatement());
            if (derived == null) {
                return null;
            }
            items.addAll(derived);
        }
        return items;
    }

    /** Build a row in table-column order from positional or column-listed values (auto-increment + defaults applied). */
    /** A VALUES item's parse, or null when the parser does not take its text; evaluating it reports that. */
    private static Expression parsedValueOrNull(final String exprText) {
        try {
            return ExpressionEvaluator.parse(exprText);
        } catch (final RuntimeException unparsed) {
            return null;
        }
    }

    /**
     * A VALUES item's static type: null for a NULL, a DEFAULT, a scripting variable written alone that holds NULL —
     * which fits any column, whatever its declared type (live-verified) — or an item the channel cannot type.
     */
    private static DataType valueStaticType(final Expression parsed, final ExpressionEvaluator evaluator) {
        if (parsed == null || parsed instanceof DefaultMarkerExpression
                || parsed instanceof LiteralExpression && ((LiteralExpression) parsed).getType() == LiteralType.NULL
                || evaluator.isNullVariable(parsed)) {
            return null;
        }
        try {
            return evaluator.inferStaticType(parsed);
        } catch (final RuntimeException untyped) {
            return null;
        }
    }

    /** Each VALUES column's rows folded to one type (see {@link ValuesColumnFold}), by position. */
    private static List<DataType> foldValuesColumns(final List<List<Expression>> rowCells,
                                                    final List<List<DataType>> rowTypes) {
        int width = 0;
        for (final List<DataType> types : rowTypes) {
            width = Math.max(width, types.size());
        }
        final List<DataType> folded = new ArrayList<>(width);
        for (int position = 0; position < width; position++) {
            final List<Expression> cells = new ArrayList<>(rowCells.size());
            final List<DataType> types = new ArrayList<>(rowTypes.size());
            for (int row = 0; row < rowTypes.size(); row++) {
                if (position < rowTypes.get(row).size()) {
                    cells.add(rowCells.get(row).get(position));
                    types.add(rowTypes.get(row).get(position));
                }
            }
            folded.add(ValuesColumnFold.fold(cells, types));
        }
        return folded;
    }

    /** The column a VALUES position feeds, or null when it feeds none. */
    private static TableColumn valuesTarget(final Table table, final List<String> columnNames,
                                            final int position) {
        if (columnNames != null) {
            return position < columnNames.size() && table.hasColumn(columnNames.get(position))
                ? table.getColumn(columnNames.get(position)) : null;
        }
        return position < table.getColumns().size() ? table.getColumns().get(position) : null;
    }

    /**
     * A VALUES value converted to its column's folded type. A failure there belongs to the row and names
     * the column the value feeds, as a failed write does.
     */
    private static Object convertToFold(final Object value, final DataType cellType, final DataType folded,
                                        final Table table, final List<String> columnNames, final int position,
                                        final String writtenName) {
        if (value instanceof DefaultMarkerExpression) {
            return value;
        }
        try {
            return ValuesColumnFold.convert(value, cellType, folded);
        } catch (final RuntimeException failed) {
            final TableColumn target = valuesTarget(table, columnNames, position);
            if (target == null || !DmlWriteTarget.isRowTimeFailure(failed)) {
                throw failed;
            }
            throw DmlWriteTarget.failedOnColumn(writtenName != null ? writtenName : table.getName(),
                target.getName(), failed);
        }
    }

    /**
     * Snowflake rejects semi-structured expressions in a VALUES clause outright (live-verified:
     * OBJECT_CONSTRUCT / ARRAY_CONSTRUCT / PARSE_JSON / TO_VARIANT, the {@code [..]} and
     * {@code {..}} literals and {@code ::VARIANT} casts all raise
     * {@code Invalid expression [...] in VALUES clause}); INSERT ... SELECT is the supported route.
     * The check is on the expression AST's top-level shape.
     */
    private void rejectSemiStructuredValueExpression(final String exprText, final ExpressionEvaluator evaluator) {
        final Expression ast;
        try {
            ast = ExpressionEvaluator.parse(exprText);
        } catch (final RuntimeException notAnExpression) {
            return;
        }
        boolean semiStructured = false;
        if (ast instanceof JsonObjectExpression || ast instanceof JsonArrayExpression) {
            semiStructured = true;
        } else if (ast instanceof CastExpression) {
            // A VECTOR cast is rejected too, but Snowflake names the TYPE rather than the expression
            // (live: "Invalid data type [VECTOR(FLOAT, 3)] in VALUES clause").
            if (((CastExpression) ast).getDeclaredTarget() instanceof VectorType) {
                throw new RuntimeException(SqlCompilationError.of("Invalid data type ["
                    + ((CastExpression) ast).getDeclaredTarget().getName() + "] in VALUES clause"));
            }
            final String target = ((CastExpression) ast).getTargetType().toUpperCase(Locale.ROOT);
            semiStructured = target.startsWith("VARIANT") || target.startsWith("OBJECT") || target.startsWith("ARRAY");
        } else if (ast instanceof FunctionCallExpression) {
            final String name = ((FunctionCallExpression) ast).getFunctionName().toUpperCase(Locale.ROOT);
            final BuiltInFunction fn = executor.getFunctionRegistry().getFunction(name);
            semiStructured = fn != null && (fn.getReturnType() instanceof VariantType
                || fn.getReturnType() instanceof ObjectType || fn.getReturnType() instanceof ArrayType
                || fn.getReturnType() instanceof GeographyType || fn.getReturnType() instanceof GeometryType);
            if (semiStructured && fn.getReturnType() instanceof VariantType) {
                // A conditional declares a nominal VARIANT in the registry, but it answers its branches'
                // type, which the static channel knows: IFF(1 = 1, 1, 0) is a NUMBER, and live takes it.
                DataType typed = null;
                try {
                    typed = evaluator.inferStaticType(ast);
                } catch (final RuntimeException untyped) {
                    typed = null;
                }
                if (typed != null && !(typed instanceof VariantType) && !(typed instanceof ObjectType)
                        && !(typed instanceof ArrayType) && !(typed instanceof GeographyType)
                        && !(typed instanceof GeometryType)) {
                    semiStructured = false;
                }
            }
            // Beyond the semi-structured families, live rejects a further per-FUNCTION set in VALUES —
            // measured: COMPRESS, MD5_BINARY, HEX_DECODE_BINARY, SHA2 and
            // RANDOM all raise the same sentence, while TO_BINARY and UPPER pass. The boundary is not
            // a return-type rule (SHA2 returns VARCHAR and is rejected; TO_BINARY returns BINARY and
            // passes), so only the measured names are listed.
            semiStructured = semiStructured || VALUES_REJECTED_FUNCTIONS.contains(name);
        }
        // A rejected function poisons the whole item wherever it sits, not only at the top: live
        // refuses UPPER(UUID_STRING()) and CONCAT('p', RANDOM()) too, naming the OUTER expression. So does a
        // semi-structured part anywhere in it, ARRAY_SIZE([1]) (see ValuesSemiStructuredParts).
        semiStructured = semiStructured || containsRejectedFunction(ast)
            || ValuesSemiStructuredParts.holdsOne(ast, evaluator, executor.getFunctionRegistry());
        if (semiStructured) {
            // The item is echoed as the statement compiled it: [1, 2] as ARRAY_CONSTRUCT(1, 2), a :: as a CAST to the
            // type spelled in full, an element appended to a structured ARRAY converted to its type (live-verified).
            throw new RuntimeException(SqlCompilationError.of("Invalid expression ["
                + ParsedEchoPrinter.print(ast, new ValuesItemEcho(evaluator)) + "] in VALUES clause"));
        }
        // A function a handler answers is refused wherever it sits in the item, the item echoed as the plan
        // prints it: INSERT INTO t VALUES (jcnt(1) + 1) is "Invalid expression [(EXTENSION_FUNC_V(1)) + 1] in
        // VALUES clause" (live-verified, for Java and JavaScript handlers).
        final String handled = new HandlerCallEcho(evaluator.scopeVisitor()).echoedIfHandled(ast);
        if (handled != null) {
            throw new RuntimeException(SqlCompilationError.of("Invalid expression [" + handled + "] in VALUES clause"));
        }
        // A division or a math function live does not fold is refused too, echoed as live rewrites it.
        final RuntimeException unfolded = ValuesFoldRule.refusalOf(ast, exprText, evaluator, executor);
        if (unfolded != null) {
            throw unfolded;
        }
    }

    /**
     * The measured non-semi-structured functions live refuses inside a VALUES clause.
     *
     * <p>Membership is by NAME, not by determinism: the two-argument {@code UUID_STRING(ns, name)} is
     * an exact RFC 4122 function and is refused all the same, while {@code CURRENT_TIMESTAMP()},
     * {@code CURRENT_DATE()}, {@code CURRENT_USER()} and {@code UPPER()} all pass. {@code UNIFORM} is
     * refused even with constant arguments ({@code UNIFORM(1, 10, 5)}), so it is the function and not
     * its randomness. SEQ2 is the one entry inferred rather than measured — SEQ1, SEQ4 and SEQ8 were
     * each refused, and the family is uniform.
     *
     * <p>{@code INSERT ... SELECT} is the supported route for every one of them.
     */
    private static final Set<String> VALUES_REJECTED_FUNCTIONS = new HashSet<>(Arrays.asList(
        "COMPRESS", "MD5_BINARY", "HEX_DECODE_BINARY", "SHA2", "RANDOM",
        "UUID_STRING", "UNIFORM", "SEQ1", "SEQ2", "SEQ4", "SEQ8",
        "ENCRYPT", "ENCRYPT_RAW", "NORMAL", "ZIPF"));

    /** Whether a refused function appears ANYWHERE under {@code node}. */
    private static boolean containsRejectedFunction(final Expression node) {
        if (node instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) node;
            if (VALUES_REJECTED_FUNCTIONS.contains(call.getFunctionName().toUpperCase(Locale.ROOT))) {
                return true;
            }
            for (final Expression argument : call.getArguments()) {
                if (containsRejectedFunction(argument)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof CastExpression) {
            return containsRejectedFunction(((CastExpression) node).getExpression());
        }
        if (node instanceof BinaryOperationExpression) {
            return containsRejectedFunction(((BinaryOperationExpression) node).getLeft())
                || containsRejectedFunction(((BinaryOperationExpression) node).getRight());
        }
        if (node instanceof UnaryOperationExpression) {
            return containsRejectedFunction(((UnaryOperationExpression) node).getOperand());
        }
        return false;
    }

    Row buildInsertRow(final Table table, final String fullyQualifiedName,
                               final List<String> columnNames, final int[] valueIndexes,
                               final List<Object> values) {
        return buildInsertRow(table, fullyQualifiedName, columnNames, valueIndexes, values, null);
    }

    /**
     * The same row, recording which of its columns the statement left out, so that they took their DEFAULT.
     *
     * @param filledByDefault one flag per table column, set for this row; null when the caller has no use for it
     */
    private Row buildInsertRow(final Table table, final String fullyQualifiedName,
                               final List<String> columnNames, final int[] valueIndexes,
                               final List<Object> values, final boolean[] filledByDefault) {
        // Snowflake requires the value count to match exactly — both with an explicit column list
        // and positionally (live-verified: "Insert value list does not match column list"); defaults
        // apply only to columns omitted from an explicit list.
        if (columnNames != null && values.size() != columnNames.size()) {
            throw new RuntimeException(SqlCompilationError.of(
                "Insert value list does not match column list expecting "
                + columnNames.size() + " but got " + values.size()));
        }
        if (columnNames == null && values.size() != table.columnCount()) {
            throw new RuntimeException(SqlCompilationError.of(
                "Insert value list does not match column list expecting "
                + table.columnCount() + " but got " + values.size()));
        }
        final List<TableColumn> cols = table.columnsView();
        final List<Object> rowValues = new ArrayList<>(valueIndexes.length);
        for (int c = 0; c < valueIndexes.length; c++) {
            final int at = valueIndexes[c];
            // Snowflake applies DEFAULT / AUTOINCREMENT only to columns OMITTED from the insert's
            // column list (or, positionally, missing trailing columns). A covered column keeps its
            // explicit value — including an explicit NULL (which must NOT be silently replaced by
            // the column default).
            final boolean written = at >= 0 && !(values.get(at) instanceof DefaultMarkerExpression);
            // Only a column the statement leaves out: a DEFAULT written in the VALUES list sits in the slot.
            if (filledByDefault != null) {
                filledByDefault[c] = at < 0;
            }
            if (written) {
                rowValues.add(values.get(at));
            } else {
                // An OMITTED column and an explicit DEFAULT take the same path — live writes the
                // declared default for both, the AUTOINCREMENT value where there is one, and NULL when
                // the column has neither (which a NOT NULL column then refuses, as live does).
                rowValues.add(executor.insertColumnValue(cols.get(c), fullyQualifiedName, null));
            }
        }
        return new Row(rowValues);
    }

    /**
     * Table-column → value-position mapping for one INSERT statement: entry {@code c} is the index in
     * the value list that fills table column {@code c}, or -1 when the column is omitted and takes
     * its DEFAULT / AUTOINCREMENT. Computed once per statement so the per-row builder does no name
     * lookups; with a name duplicated in the column list the last occurrence wins.
     */
    private int[] insertValueIndexes(final Table table, final List<String> columnNames) {
        final int[] mapping = new int[table.columnCount()];
        if (columnNames == null) {
            // Positional: the exact-count rule (checked per row before this mapping is consulted)
            // means column c is always fed by value c.
            for (int c = 0; c < mapping.length; c++) {
                mapping[c] = c;
            }
            return mapping;
        }
        // Each listed name finds its column through the table's own lookup, which matches the spelling
        // exactly before it folds case: INSERT INTO t ("x", "X") fills two different columns.
        for (int c = 0; c < mapping.length; c++) {
            mapping[c] = -1;
        }
        for (int i = 0; i < columnNames.size(); i++) {
            if (table.hasColumn(columnNames.get(i))) {
                mapping[table.getColumnIndex(columnNames.get(i))] = i;
            }
        }
        return mapping;
    }

    /** Single-row variant of {@link #insertRowInto(Table, String, Row, DeferredInsertGuard)}: builds a fresh guard when deferred. */
    void insertRowInto(final Table table, final String fullyQualifiedName, final Row row) {
        insertRowInto(table, fullyQualifiedName, row, null);
    }

    /**
     * Insert one fully-built row: enforce constraints, then buffer (deferred-apply) or write + log +
     * track streams. In deferred mode duplicate keys are refused by {@code guard}; a caller inserting
     * many rows passes one statement-scoped guard, a null builds a single-row one here.
     */
    void insertRowInto(final Table table, final String fullyQualifiedName, final Row row,
                       final DeferredInsertGuard guard) {
        insertRowInto(table, fullyQualifiedName, row, guard, false);
    }

    /**
     * The same insert, told whether the row's values came from a VALUES list — see
     * {@link QueryExecutor#enforceColumnConstraintsForDml(Table, Row, boolean)}, the one refusal that
     * spells the difference out.
     */
    void insertRowInto(final Table table, final String fullyQualifiedName, final Row row,
                       final DeferredInsertGuard guard, final boolean boundToColumnSlot) {
        insertRowInto(table, fullyQualifiedName, row, guard, boundToColumnSlot, null);
    }

    /** @param writtenName the table as the INSERT wrote it, for the envelope, or null for the bare name */
    void insertRowInto(final Table table, final String fullyQualifiedName, final Row row,
                       final DeferredInsertGuard guard, final boolean boundToColumnSlot,
                       final String writtenName) {
        executor.enforceColumnConstraintsForDml(table, row, boundToColumnSlot, writtenName);
        storeInsertedRow(table, fullyQualifiedName, row, guard);
    }

    /**
     * Write rows a load already shaped and checked, as INSERT writes its rows: buffered in the statement's
     * transaction, begun here when none is, or written at once and logged where the transaction is applied as it
     * goes. One guard refuses a key the rows repeat.
     *
     * @param table              the table loaded
     * @param fullyQualifiedName its storage name
     * @param rows               the rows, in load order
     */
    void storeLoadedRows(final Table table, final String fullyQualifiedName, final List<Row> rows) {
        if (!executor.getTransactionManager().hasActiveTransaction()) {
            executor.getTransactionManager().beginTransaction();
        }
        DeferredInsertGuard guard = null;
        if (executor.isDeferredApply()) {
            guard = new DeferredInsertGuard(table, executor.getStorageEngine().getTableStorage(fullyQualifiedName),
                executor.getTransactionManager().getCurrentTransaction().getWriteSet(), fullyQualifiedName,
                executor.getStorageEngine().isEnforcePrimaryKey(), executor.getStorageEngine().isEnforceUniqueKey());
        }
        for (final Row row : rows) {
            // A virtual column takes no value of the load's: it is computed from the row (see VirtualColumns).
            VirtualColumns.compute(executor, table, row);
            storeInsertedRow(table, fullyQualifiedName, row, guard);
        }
    }

    /** Write a row whose constraints have been enforced: buffered in the transaction's write set, or at once. */
    private void storeInsertedRow(final Table table, final String fullyQualifiedName, final Row row,
                                  final DeferredInsertGuard guard) {
        new HybridKeyGuard(executor).refuseOrphanRow(table, fullyQualifiedName, row);
        if (executor.isDeferredApply()) {
            final TableStorage base = executor.getStorageEngine().getTableStorage(fullyQualifiedName);
            final TransactionWriteSet writeSet = executor.getTransactionManager().getCurrentTransaction().getWriteSet();
            final DeferredInsertGuard rowGuard = guard != null ? guard
                : new DeferredInsertGuard(table, base, writeSet, fullyQualifiedName,
                    executor.getStorageEngine().isEnforcePrimaryKey(),
                    executor.getStorageEngine().isEnforceUniqueKey());
            rowGuard.validate(row);
            // An INSERT takes the table's lock where the table holds committed rows, and none on an empty one.
            writeSet.recordInsert(fullyQualifiedName, row, base != null && base.getRowCount() > 0);
        } else {
            final int insertIndex = executor.getStorageEngine().getTableStorage(fullyQualifiedName).getRowCount();
            executor.getStorageEngine().getTableStorage(fullyQualifiedName).insert(row);
            if (executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().getCurrentTransaction().logInsert(fullyQualifiedName, insertIndex, row);
            }
            if (executor.getStreamManager() != null) {
                executor.getStreamManager().trackInsert(fullyQualifiedName, row,
                    executor.getStorageEngine().getTableStorage(fullyQualifiedName).getRowId(insertIndex));
            }
        }
    }

    /**
     * Snowflake multi-table INSERT — route each row of the subquery to one or more target tables.
     * INSERT [OVERWRITE] ALL INTO ... (unconditional), or INSERT [OVERWRITE] {FIRST | ALL}
     * WHEN cond THEN INTO ... [ELSE INTO ...]. FIRST stops at the first matching WHEN; ALL applies every
     * matching WHEN; ELSE applies only when no WHEN matched.
     */
    Object executeMultiTableInsert(final FrostlakeParser.MultiTableInsertStatementContext ctx) {
        executor.checkNotReadOnly();
        try {
            if (!executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().beginTransaction();
            }
            // Live's order: the source's relations, every target, the source query itself, then every
            // INTO's value count, every column list's names, the WHEN conditions and the INTO … VALUES
            // expressions, all before a row is routed, so an empty source still refuses.
            executor.requireSourceRelations(ctx.selectStatement());
            for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
                final String target = executor.getQualifiedName(into.qualifiedName());
                DmlTarget.requireTable(executor, target, "INSERT (MULTI-TABLE)");
                DmlTarget.refuseEventTable(executor.getCatalog().resolveTableAsWritten(target, "Table"),
                    "INSERT (MULTI-TABLE)");
            }
            final ResultSet source;
            try {
                source = executor.executeSelectFromContext(ctx.selectStatement());
            } catch (final RuntimeException failed) {
                if (refusesColumnCount(failed)) {
                    // A source item of several columns is a ROW the target's column cannot take, and
                    // live names it that way rather than refusing the subquery's shape.
                    for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
                        if (into.expressionList() == null) {
                            rejectRowItems(ctx.selectStatement(), targetOf(into), columnNamesOf(into));
                        }
                    }
                }
                throw failed;
            }
            // Every count ahead of every name: a later INTO's miscount is refused before an earlier
            // INTO's unknown column.
            for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
                requireValueCount(into, source);
            }
            for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
                if (into.columnListOptional() != null) {
                    requireColumnList(targetOf(into), into.columnListOptional(), valueCountOf(into, source));
                }
            }
            final Table sourceTable = executor.resultSetToTable(source, "multi_insert_source");
            requireResolvableRoutingExpressions(ctx, sourceTable);
            // Each INTO's DECFLOATs written into a VARIANT or a text column, by the INTO (see DecfloatWrites).
            final Map<FrostlakeParser.MultiInsertIntoContext, DataType[]> decfloatTargets =
                rejectMistypedRoutedValues(ctx, source, sourceTable);
            // Each INTO's values are judged by a projection policy as a projection is; the source query itself was,
            // as a statement's own select.
            final List<FrostlakeParser.ExpressionContext> routed = new ArrayList<>();
            for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
                if (into.expressionList() != null) {
                    routed.addAll(into.expressionList().expression());
                }
            }
            executor.rejectRestrictedAssignments(routed, sourceTable, null, null);
            final boolean overwrite = ctx.OVERWRITE() != null;
            final boolean first = ctx.FIRST() != null;
            final boolean conditional = !ctx.multiInsertWhen().isEmpty();

            // OVERWRITE: truncate each distinct target table once, before inserting.
            if (overwrite) {
                final Set<String> truncated = new HashSet<>();
                for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
                    final String tableName = executor.getQualifiedName(into.qualifiedName());
                    final String fqn = executor.getFullyQualifiedTableName(tableName);
                    if (truncated.add(fqn)) {
                        // Each target that loses rows waits for a transaction holding it, as INSERT OVERWRITE does.
                        if (executor.getStorageEngine().getTableStorage(fqn).getRowCount() > 0) {
                            executor.requireTableUnlocked(fqn,
                                executor.getCatalog().resolveTableAsWritten(tableName, "Table").getName());
                        }
                        executor.emptyTableContents(fqn);
                    }
                }
            }

            // One count per distinct target, in the order the statement first names it — the grid live
            // answers with, one "number of rows inserted into <table>" column each.
            final Map<String, long[]> insertedInto = new LinkedHashMap<>();
            final Map<String, String> targetNames = new LinkedHashMap<>();
            for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
                final String fqn = executor.getFullyQualifiedTableName(executor.getQualifiedName(into.qualifiedName()));
                if (!insertedInto.containsKey(fqn)) {
                    insertedInto.put(fqn, new long[1]);
                    targetNames.put(fqn, targetOf(into).getName());
                }
            }
            int rowsInserted = 0;
            for (final Row srcRow : source.getRows()) {
                final List<FrostlakeParser.MultiInsertIntoContext> applicable = new ArrayList<>();
                if (!conditional) {
                    applicable.addAll(ctx.multiInsertInto());
                } else {
                    boolean matched = false;
                    for (final FrostlakeParser.MultiInsertWhenContext whenCtx : ctx.multiInsertWhen()) {
                        if (evaluateRowCondition(whenCtx.booleanExpr(), sourceTable, srcRow)) {
                            matched = true;
                            applicable.addAll(whenCtx.multiInsertInto());
                            if (first) {
                                break;   // FIRST: only the first matching WHEN's targets
                            }
                        }
                    }
                    if (!matched && ctx.multiInsertElse() != null) {
                        applicable.addAll(ctx.multiInsertElse().multiInsertInto());
                    }
                }

                for (final FrostlakeParser.MultiInsertIntoContext into : applicable) {
                    final String tableName = executor.getQualifiedName(into.qualifiedName());
                    final Table target = executor.getCatalog().resolveTableAsWritten(tableName, "Table");
                    final String fqn = executor.getFullyQualifiedTableName(tableName);
                    executor.requirePrivilege(Privilege.INSERT, "TABLE", tableName);
                    List<String> columnNames = VirtualColumns.writtenNames(target);
                    if (into.columnListOptional() != null) {
                        columnNames = new ArrayList<>();
                        for (final FrostlakeParser.NamePartContext id : into.columnListOptional().namePart()) {
                            columnNames.add(ParseTreeText.namePartText(id));
                        }
                    }
                    final List<Object> values;
                    if (into.expressionList() != null) {
                        values = new ArrayList<>();
                        for (final FrostlakeParser.ExpressionContext exprCtx : into.expressionList().expression()) {
                            values.add(routedValue(exprCtx, sourceTable, srcRow));
                        }
                    } else {
                        values = srcRow.getValues();
                        // Writing reads every cell, so a fault a relation deferred raises here.
                        for (final Object value : values) {
                            DeferredFault.read(value);
                        }
                    }
                    insertRowInto(target, fqn,
                        buildInsertRow(target, fqn, columnNames, insertValueIndexes(target, columnNames),
                            decfloatTargets.containsKey(into)
                                ? asDecfloatMembers(values, decfloatTargets.get(into)) : values),
                        null, false, tableName);
                    insertedInto.get(fqn)[0]++;
                    rowsInserted++;
                }
            }
            logger.trace("Multi-table INSERT routed {} row insertion(s)", rowsInserted);
            // LAST_ALTERED moves on every table the statement names, whether or not a row reached it.
            for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
                targetOf(into).markDmlRan(StatementClock.commitInstant());
            }
            return multiTableInsertCounts(insertedInto, targetNames);
        } catch (final SecurityException e) {
            throw e;
        } catch (final Exception e) {
            throw StatementErrors.propagate(e);
        }
    }

    /**
     * A multi-table INSERT's count grid, as live answers it: one column per distinct target in the order the
     * statement first names it, "number of rows inserted into T" (the table's own name, however the INTO wrote
     * it), and the plain "number of rows inserted" when every INTO names one table. A target no row reached
     * still has its column, holding 0.
     */
    private ResultSet multiTableInsertCounts(final Map<String, long[]> insertedInto,
                                             final Map<String, String> targetNames) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        final List<Object> counts = new ArrayList<>();
        long total = 0;
        for (final Map.Entry<String, long[]> target : insertedInto.entrySet()) {
            columns.add(new ResultSetColumn(insertedInto.size() == 1 ? "number of rows inserted"
                : "number of rows inserted into " + targetNames.get(target.getKey()), StatementResultWidths.DML_COUNT));
            counts.add(Long.valueOf(target.getValue()[0]));
            total += target.getValue()[0];
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(counts));
        return new ResultSet(columns, rows).markUpdateCount(total);
    }

    private List<FrostlakeParser.MultiInsertIntoContext> allMultiInsertIntos(final FrostlakeParser.MultiTableInsertStatementContext ctx) {
        final List<FrostlakeParser.MultiInsertIntoContext> all = new ArrayList<>(ctx.multiInsertInto());
        for (final FrostlakeParser.MultiInsertWhenContext whenCtx : ctx.multiInsertWhen()) {
            all.addAll(whenCtx.multiInsertInto());
        }
        if (ctx.multiInsertElse() != null) {
            all.addAll(ctx.multiInsertElse().multiInsertInto());
        }
        return all;
    }

    private Table targetOf(final FrostlakeParser.MultiInsertIntoContext into) {
        return executor.getCatalog().resolveTableAsWritten(executor.getQualifiedName(into.qualifiedName()), "Table");
    }

    /** How many values an INTO supplies: its own VALUES list, else one per column of the source. */
    private static int valueCountOf(final FrostlakeParser.MultiInsertIntoContext into, final ResultSet source) {
        return into.expressionList() != null ? into.expressionList().expression().size()
            : source.getColumns().size();
    }

    /**
     * An INTO's values counted against the columns they fill — its column list when it has one, else
     * every column of its target — before any row is routed, so an empty source is refused too.
     */
    private void requireValueCount(final FrostlakeParser.MultiInsertIntoContext into, final ResultSet source) {
        final int valueCount = valueCountOf(into, source);
        final int expected = into.columnListOptional() != null
            ? into.columnListOptional().namePart().size() : VirtualColumns.writtenCount(targetOf(into));
        if (valueCount != expected) {
            throw new RuntimeException(SqlCompilationError.of(
                "Insert value list does not match column list expecting " + expected + " but got " + valueCount));
        }
    }

    /**
     * Every WHEN condition and INTO … VALUES expression judged against the source query's columns before
     * a row is routed, so a name the source does not carry is refused where it is written, over an empty
     * source too. Live orders these refusals by kind — every invalid identifier, the conditions' ahead of
     * the values', before any unknown function name — and resolves a name against the source's output
     * columns alone: neither the source's own alias nor a target's name qualifies anything here.
     */
    /**
     * The compile-time type match of every value a multi-table INSERT writes, against the column it
     * feeds — the same rule the single-table INSERT applies, which this path never reached. An INTO
     * with a VALUES list types each of its expressions in the source's scope; one without takes the
     * source query's own column types.
     *
     * @param ctx         the statement
     * @param source      the source query's result, for its columns' static types
     * @param sourceTable the source as a relation, the scope a routed expression reads
     * @return for each INTO that writes a DECFLOAT into a VARIANT or a text column, the column type each such value is
     *         written into, by its position (see decfloatIntoVariant)
     */
    private Map<FrostlakeParser.MultiInsertIntoContext, DataType[]> rejectMistypedRoutedValues(
            final FrostlakeParser.MultiTableInsertStatementContext ctx, final ResultSet source,
            final Table sourceTable) {
        final Map<FrostlakeParser.MultiInsertIntoContext, DataType[]> decfloatTargets = new HashMap<>();
        final ExpressionEvaluator scope = new ExpressionEvaluator(sourceTable, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        final Map<String, Table> unnamed = new HashMap<>();
        unnamed.put("", sourceTable);
        final List<Table> allTables = new ArrayList<>();
        allTables.add(sourceTable);
        scope.setMultiTableContext(unnamed, allTables);
        for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
            final Table target = targetOf(into);
            final List<String> columnNames = columnNamesOf(into);
            if (into.expressionList() == null) {
                rejectMismatchedSelectColumnTypes(target, columnNames, source.getColumns());
                final DataType[] marked = decfloatIntoVariant(target, columnNames, source.getColumns());
                for (final DataType written : marked) {
                    if (written != null) {
                        decfloatTargets.put(into, marked);
                        break;
                    }
                }
                continue;
            }
            final List<FrostlakeParser.ExpressionContext> values = into.expressionList().expression();
            for (int i = 0; i < values.size(); i++) {
                final TableColumn column = valuesTarget(target, columnNames, i);
                if (column == null) {
                    continue;
                }
                final DataType written;
                try {
                    final Expression value = ExpressionEvaluator.parse(executor.getOriginalText(values.get(i)));
                    if (scope.isNullVariable(value)) {
                        continue;
                    }
                    written = scope.inferStaticType(value);
                } catch (final RuntimeException untyped) {
                    continue;
                }
                ColumnTypeFamilies.rejectMismatch(column, written);
                if (DecfloatWrites.reshapes(written, column.getDataType())) {
                    if (!decfloatTargets.containsKey(into)) {
                        decfloatTargets.put(into, new DataType[values.size()]);
                    }
                    decfloatTargets.get(into)[i] = column.getDataType();
                }
            }
        }
        return decfloatTargets;
    }

    /** An INTO clause's column list, or null when it writes every column of its target. */
    private List<String> columnNamesOf(final FrostlakeParser.MultiInsertIntoContext into) {
        if (into.columnListOptional() == null) {
            return VirtualColumns.writtenNames(targetOf(into));
        }
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.NamePartContext id : into.columnListOptional().namePart()) {
            names.add(ParseTreeText.namePartText(id));
        }
        return names;
    }

    private void requireResolvableRoutingExpressions(final FrostlakeParser.MultiTableInsertStatementContext ctx,
                                                     final Table sourceTable) {
        final List<ParserRuleContext> expressions = new ArrayList<>();
        for (final FrostlakeParser.MultiInsertWhenContext whenCtx : ctx.multiInsertWhen()) {
            expressions.add(whenCtx.booleanExpr());
        }
        for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
            if (into.expressionList() != null) {
                expressions.addAll(into.expressionList().expression());
            }
        }
        if (expressions.isEmpty()) {
            return;
        }
        final ExpressionEvaluator scope = new ExpressionEvaluator(sourceTable, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        // The source is in scope under no name: a key no written qualifier can spell, so every
        // qualified reference is refused, as live refuses both s.x and T.x here.
        final Map<String, Table> unnamed = new HashMap<>();
        unnamed.put("", sourceTable);
        final List<Table> allTables = new ArrayList<>();
        allTables.add(sourceTable);
        scope.setMultiTableContext(unnamed, allTables);
        for (int phase = 0; phase < 2; phase++) {
            for (final ParserRuleContext expression : expressions) {
                final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
                    expression.getStart().getLine(), expression.getStart().getCharPositionInLine()));
                try {
                    final Expression parsed = ExpressionEvaluator.parse(executor.getOriginalText(expression));
                    if (phase == 0) {
                        scope.validateColumnScope(parsed);
                    } else {
                        scope.validateFunctionNames(parsed);
                    }
                } catch (final RuntimeException unjudged) {
                    // A function the role may not use is refused as the statement compiles too.
                    if (SqlCompilationError.isCompilationError(unjudged.getMessage())
                            || unjudged instanceof PrivilegeRefusal) {
                        throw unjudged;
                    }
                } finally {
                    ExpressionSource.end(displaced);
                }
            }
        }
    }

    /**
     * One INTO … VALUES item for a routed row. A bare DEFAULT is not a value but the column's declared
     * default, which only the row builder knows, so its marker travels in the value list as it does in
     * a single-table INSERT; evaluating it would raise the refusal a DEFAULT inside an expression gets.
     */
    private Object routedValue(final ParserRuleContext exprCtx, final Table sourceTable, final Row srcRow) {
        final Expression parsed = ExpressionEvaluator.parse(executor.getOriginalText(exprCtx));
        return parsed instanceof DefaultMarkerExpression ? parsed : evaluateRowExpression(exprCtx, sourceTable, srcRow);
    }

    private Object evaluateRowExpression(final ParserRuleContext exprCtx, final Table sourceTable, final Row srcRow) {
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(sourceTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        return evaluator.evaluate(executor.getOriginalText(exprCtx), srcRow);
    }

    private boolean evaluateRowCondition(final FrostlakeParser.BooleanExprContext condCtx, final Table sourceTable, final Row srcRow) {
        final Object result = evaluateRowExpression(condCtx, sourceTable, srcRow);
        return SqlTruth.isTrue(result)
            ? true : result != null && Boolean.parseBoolean(String.valueOf(result));
    }

    /**
     * Confirm a named INSERT column exists, reporting an unknown one where it was written. Resolution
     * is delegated to the table, so this can only add a refusal live already makes — never invent one.
     */
    private void requireColumn(final Table table, final String columnName,
                               final FrostlakeParser.NamePartContext where) {
        // The table's lookup folds case, but a quoted name must match exactly: "x" names no column X.
        if (!table.hasColumn(columnName) || !table.getColumn(columnName).getName().equals(columnName)) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(
                where.getStart().getLine(), where.getStart().getCharPositionInLine(),
                SqlIdentifiers.spellCanonical(columnName)));
        }
        VirtualColumns.refuseTarget(table, columnName);
    }

    /**
     * An INSERT's column list, checked the way live checks it once the values' count is known: the count
     * first, then each name in the order written. A name the table does not have is an invalid identifier
     * where it stands, and one already named is a duplicate (live-verified).
     */
    /**
     * An INSERT's column list, whose items may be qualified. The qualifier the account accepts is the
     * target table's own bare name and nothing else — not a schema, not a database — and it accepts it
     * whether the target was written bare or fully qualified. Any other qualifier, and any column the
     * table does not have, is an invalid identifier spelled as the item was WRITTEN: {@code X.A},
     * {@code T1.ZZ}.
     */
    private void requireColumnList(final Table table, final FrostlakeParser.InsertColumnListContext list,
                                   final int valueCount) {
        final List<FrostlakeParser.InsertColumnItemContext> items = list.insertColumnItem();
        if (valueCount != items.size()) {
            throw new RuntimeException(SqlCompilationError.of(
                "Insert value list does not match column list expecting " + items.size()
                    + " but got " + valueCount));
        }
        final Set<String> seen = new HashSet<>();
        for (final FrostlakeParser.InsertColumnItemContext item : items) {
            final List<FrostlakeParser.NamePartContext> parts = item.namePart();
            final String columnName = ParseTreeText.namePartText(parts.get(parts.size() - 1));
            if (parts.size() > 1) {
                final String qualifier = ParseTreeText.namePartText(parts.get(0));
                if (!table.getName().equals(qualifier) || !table.hasColumn(columnName)
                        || !table.getColumn(columnName).getName().equals(columnName)) {
                    throw new RuntimeException(SqlCompilationError.invalidIdentifier(
                        item.getStart().getLine(), item.getStart().getCharPositionInLine(),
                        SqlIdentifiers.spellCanonical(qualifier) + "."
                            + SqlIdentifiers.spellCanonical(columnName)));
                }
                VirtualColumns.refuseTarget(table, columnName);
            } else {
                requireColumn(table, columnName, parts.get(0));
            }
            if (!seen.add(columnName)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "duplicate column name '" + SqlIdentifiers.spellCanonical(columnName) + "'"));
            }
        }
    }

    private void requireColumnList(final Table table, final FrostlakeParser.ColumnListOptionalContext list,
                                   final int valueCount) {
        final List<FrostlakeParser.NamePartContext> names = list.namePart();
        if (valueCount != names.size()) {
            throw new RuntimeException(SqlCompilationError.of(
                "Insert value list does not match column list expecting " + names.size() + " but got " + valueCount));
        }
        final Set<String> seen = new HashSet<>();
        for (final FrostlakeParser.NamePartContext id : names) {
            final String columnName = ParseTreeText.namePartText(id);
            requireColumn(table, columnName, id);
            if (!seen.add(columnName)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "duplicate column name '" + SqlIdentifiers.spellCanonical(columnName) + "'"));
            }
        }
    }

    /**
     * A failure raised while the SOURCE QUERY was evaluated, wrapped in live's write envelope. The
     * query fails as a whole, so the column it is attributed to is the TARGET of the projection that
     * stopped — {@link ProjectionSlot} records which one that was.
     *
     * <pre>
     *   INSERT INTO vempty SELECT va, COALESCE(va, d) FROM vf
     *       DML operation to table VEMPTY failed on column D with error:
     *       Failed to cast variant value 1 to DATE
     * </pre>
     *
     * <p>A failure with no projection behind it — one raised before the select list is reached, or by
     * a clause rather than an item — is left unwrapped, which is what live does with it too.
     */
    private RuntimeException sourceQueryFailure(final String writtenName, final Table table,
                                                final List<String> columnNames,
                                                final RuntimeException failed) {
        if (!DmlWriteTarget.isRowTimeFailure(failed)) {
            ProjectionSlot.takeFailedSlot();
            return failed;
        }
        final int slot = ProjectionSlot.takeFailedSlot();
        if (slot < 0) {
            return failed;
        }
        final String column;
        if (columnNames != null && !columnNames.isEmpty() && slot < columnNames.size()) {
            column = columnNames.get(slot);
        } else if (slot < table.getColumns().size()) {
            column = table.getColumns().get(slot).getName();
        } else {
            return failed;
        }
        return DmlWriteTarget.failedOnColumn(writtenName, column.toUpperCase(Locale.ROOT), failed);
    }
}
