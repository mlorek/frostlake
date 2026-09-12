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
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.SecurableObjectType;
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
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        executor.checkNotReadOnly();
        try {
            // Auto-start transaction if not active
            if (!executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().beginTransaction();
            }

            // WITH-prefixed DML is not Snowflake syntax (live-verified); CTEs reach a DML statement
            // only inside its subqueries.
            final Map<String, ResultSet> cteResults = null;

            final String tableName = ctx.objectName().KW_IDENTIFIER() != null
                ? executor.resolveObjectName(ctx.objectName())
                : executor.getQualifiedName(ctx.objectName().qualifiedName());
            // A missing source relation is reported ahead of a missing target (live-verified).
            if (ctx.selectStatement() != null) {
                executor.requireSourceRelations(ctx.selectStatement());
            }
            final Table table = executor.getCatalog().resolveTableAsWritten(tableName, "Table");

            // Check INSERT permission
            if (executor.getSecurityManager() != null) {
                executor.getSecurityManager().checkPermission(Privilege.INSERT, SecurableObjectType.TABLE, tableName);
            }

            // OVERWRITE replaces the table's contents, but the truncate must happen only AFTER the source
            // rows have been produced: a self-referencing INSERT OVERWRITE INTO t … SELECT … FROM t reads the
            // table it overwrites, so truncating first made the source SELECT see an empty table and the
            // statement silently wiped the data (0 rows in, 0 rows out).
            final boolean isOverwrite = ctx.OVERWRITE() != null;

            // Get column names if specified
            List<String> columnNames = null;
            if (ctx.columnListOptional() != null) {
                columnNames = new ArrayList<>();
                for (final FrostlakeParser.NamePartContext id : ctx.columnListOptional().namePart()) {
                    // Checked by requireColumnList once the values' count is known: live weighs the
                    // count first, and the source query's own errors ahead of the list's.
                    columnNames.add(ParseTreeText.namePartText(id));
                }
            }

            // Parse values - either from VALUES clause or SELECT statement
            final List<List<Object>> valuesList = new ArrayList<>();

            if (ctx.valueTupleList() != null) {
                if (ctx.columnListOptional() != null) {
                    requireColumnList(table, ctx.columnListOptional(),
                        ctx.valueTupleList().valueTuple(0).valueList().booleanExpr().size());
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
                for (int position = 0; position < foldedTypes.size(); position++) {
                    final TableColumn target = valuesTarget(table, columnNames, position);
                    if (target != null) {
                        ColumnTypeFamilies.rejectMismatch(target, foldedTypes.get(position));
                    }
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
                            value = parsed instanceof DefaultMarkerExpression
                                ? parsed : evaluator.evaluate(exprText, dummyRow);
                        } finally {
                            ExpressionSource.end(displaced);
                        }
                        values.add(convertToFold(value, rowTypes.get(row).get(position),
                            foldedTypes.get(position), table, columnNames, position, tableName));
                        position++;
                    }
                    valuesList.add(values);
                }
            } else if (ctx.selectStatement() != null) {
                // INSERT ... SELECT. A stream read in this subquery is consumed when the txn commits (the DML
                // window is marked centrally in SQLCommandVisitor.visitDmlStatement); pass CTE results along.
                ProjectionSlot.reset();
                final ResultSet selectResult;
                try {
                    selectResult = executor.executeSelectFromContextWithCTEs(
                        ctx.selectStatement(), null, cteResults);
                } catch (final RuntimeException failed) {
                    throw sourceQueryFailure(tableName, table, columnNames, failed);
                }
                if (ctx.columnListOptional() != null) {
                    requireColumnList(table, ctx.columnListOptional(), selectResult.getColumns().size());
                }
                rejectMismatchedSelectColumnTypes(table, columnNames, selectResult.getColumns());
                for (final Row row : selectResult.getRows()) {
                    valuesList.add(row.getValues());
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
                executor.emptyTableContents(fullyQualifiedName);
                table.setRowCount(0);
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
            for (final List<Object> values : valuesList) {
                final Row row = buildInsertRow(table, fullyQualifiedName, columnNames, valueIndexes, values);
                insertRowInto(table, fullyQualifiedName, row, guard, boundToColumnSlot, tableName);
                rowsInserted++;
            }

            logger.trace("Inserted {} row(s) into table: {}", rowsInserted, tableName);
            return executor.dmlCountResult("number of rows inserted", rowsInserted);

        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            throw StatementErrors.propagate(e);
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

    /** Build a row in table-column order from positional or column-listed values (auto-increment + defaults applied). */
    /** A VALUES item's parse, or null when the parser does not take its text; evaluating it reports that. */
    private static Expression parsedValueOrNull(final String exprText) {
        try {
            return ExpressionEvaluator.parse(exprText);
        } catch (final RuntimeException unparsed) {
            return null;
        }
    }

    /** A VALUES item's static type: null for a NULL, a DEFAULT, or an item the channel cannot type. */
    private static DataType valueStaticType(final Expression parsed, final ExpressionEvaluator evaluator) {
        if (parsed == null || parsed instanceof DefaultMarkerExpression
                || parsed instanceof LiteralExpression && ((LiteralExpression) parsed).getType() == LiteralType.NULL) {
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
            final String target = ((CastExpression) ast).getTargetType().toUpperCase();
            semiStructured = target.startsWith("VARIANT") || target.startsWith("OBJECT") || target.startsWith("ARRAY");
        } else if (ast instanceof FunctionCallExpression) {
            final String name = ((FunctionCallExpression) ast).getFunctionName().toUpperCase();
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
        // refuses UPPER(UUID_STRING()) and CONCAT('p', RANDOM()) too, naming the OUTER expression.
        semiStructured = semiStructured || containsRejectedFunction(ast);
        if (semiStructured) {
            throw new RuntimeException(SqlCompilationError.of("Invalid expression [" + exprText + "] in VALUES clause"));
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
            if (VALUES_REJECTED_FUNCTIONS.contains(call.getFunctionName().toUpperCase())) {
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
            if (at >= 0 && !(values.get(at) instanceof DefaultMarkerExpression)) {
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
        final Map<String, Integer> positionByName = new HashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            positionByName.put(columnNames.get(i).toUpperCase(), i);
        }
        final List<TableColumn> cols = table.columnsView();
        for (int c = 0; c < mapping.length; c++) {
            final Integer position = positionByName.get(cols.get(c).getName().toUpperCase());
            mapping[c] = position == null ? -1 : position;
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
        if (executor.isDeferredApply()) {
            final TableStorage base = executor.getStorageEngine().getTableStorage(fullyQualifiedName);
            final TransactionWriteSet writeSet = executor.getTransactionManager().getCurrentTransaction().getWriteSet();
            final DeferredInsertGuard rowGuard = guard != null ? guard
                : new DeferredInsertGuard(table, base, writeSet, fullyQualifiedName,
                    executor.getStorageEngine().isEnforcePrimaryKey(),
                    executor.getStorageEngine().isEnforceUniqueKey());
            rowGuard.validate(row);
            writeSet.recordInsert(fullyQualifiedName, row);
        } else {
            final int insertIndex = executor.getStorageEngine().getTableStorage(fullyQualifiedName).getRowCount();
            executor.getStorageEngine().getTableStorage(fullyQualifiedName).insert(row);
            if (executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().getCurrentTransaction().logInsert(fullyQualifiedName, insertIndex, row);
            }
            if (executor.getStreamManager() != null) {
                executor.getStreamManager().trackInsert(fullyQualifiedName, row);
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
                executor.getCatalog().resolveTableAsWritten(executor.getQualifiedName(into.qualifiedName()), "Table");
            }
            final ResultSet source = executor.executeSelectFromContext(ctx.selectStatement());
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
                        executor.emptyTableContents(fqn);
                        executor.getCatalog().resolveTableAsWritten(tableName, "Table").setRowCount(0);
                    }
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
                    if (executor.getSecurityManager() != null) {
                        executor.getSecurityManager().checkPermission(Privilege.INSERT, SecurableObjectType.TABLE, tableName);
                    }
                    List<String> columnNames = null;
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
                    }
                    insertRowInto(target, fqn,
                        buildInsertRow(target, fqn, columnNames, insertValueIndexes(target, columnNames), values),
                        null, false, tableName);
                    rowsInserted++;
                }
            }
            logger.trace("Multi-table INSERT routed {} row insertion(s)", rowsInserted);
            return null;
        } catch (final SecurityException e) {
            throw e;
        } catch (final Exception e) {
            throw StatementErrors.propagate(e);
        }
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
            ? into.columnListOptional().namePart().size() : targetOf(into).columnCount();
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
                    if (SqlCompilationError.isCompilationError(unjudged.getMessage())) {
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
    }

    /**
     * An INSERT's column list, checked the way live checks it once the values' count is known: the count
     * first, then each name in the order written. A name the table does not have is an invalid identifier
     * where it stands, and one already named is a duplicate (live-verified).
     */
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
        return DmlWriteTarget.failedOnColumn(writtenName, column.toUpperCase(), failed);
    }
}
