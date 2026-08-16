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
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
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
                    final String columnName = ParseTreeText.namePartText(id);
                    // A named column that the table does not have is refused HERE, at the place it was
                    // written. Frostlake used to ACCEPT the statement and quietly insert nothing for it,
                    // where live rejects the whole INSERT — an accepted-but-invalid statement, which is
                    // a fidelity bug of its own and not merely a missing position.
                    requireColumn(table, columnName, id);
                    columnNames.add(columnName);
                }
            }

            // Parse values - either from VALUES clause or SELECT statement
            final List<List<Object>> valuesList = new ArrayList<>();

            if (ctx.valueTupleList() != null) {
                // INSERT ... VALUES — one evaluator serves every cell (the dummy table and row carry
                // no per-cell state, so per-cell construction was pure allocation).
                final Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
                final Row dummyRow = new Row(new ArrayList<>());
                // No scripting variables are offered to the evaluator: INSERT is an embedded SQL
                // statement, where a stored-procedure parameter / DECLAREd / LET name must be
                // written :name (a bare one is an identifier — live: "invalid identifier 'V'").
                final ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
                for (final FrostlakeParser.ValueTupleContext tuple : ctx.valueTupleList().valueTuple()) {
                    final List<Object> values = new ArrayList<>();
                    // Parse each value as an expression (supports literals, JSON objects, arrays, etc.)
                    int valuePosition = 0;
                    for (final FrostlakeParser.ExpressionContext expr : tuple.valueList().expression()) {
                        rejectStringLiteralIntoSemiStructured(table, columnNames, valuePosition, expr);
                        valuePosition++;
                        final String exprText = executor.getOriginalText(expr);
                        rejectSemiStructuredValueExpression(exprText);
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
                        values.add(value);
                    }
                    valuesList.add(values);
                }
            } else if (ctx.selectStatement() != null) {
                // INSERT ... SELECT. A stream read in this subquery is consumed when the txn commits (the DML
                // window is marked centrally in SQLCommandVisitor.visitDmlStatement); pass CTE results along.
                final ResultSet selectResult = executor.executeSelectFromContextWithCTEs(ctx.selectStatement(), null, cteResults);
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
            for (final List<Object> values : valuesList) {
                final Row row = buildInsertRow(table, fullyQualifiedName, columnNames, valueIndexes, values);
                insertRowInto(table, fullyQualifiedName, row, guard);
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
            final DataType sourceType = sourceColumns.get(i).getStaticType();
            if (sourceType == null) {
                continue;
            }
            final String targetFamily = typeFamily(target.getDataType());
            final String sourceFamily = typeFamily(sourceType);
            if (targetFamily == null || sourceFamily == null) {
                continue;
            }
            if ("TIMESTAMP".equals(targetFamily) && "TIME".equals(sourceFamily)) {
                throw new RuntimeException(SqlCompilationError.of("incompatible types: ["
                    + spellType(sourceType) + "] and [" + spellType(target.getDataType()) + "]"));
            }
            if (!insertFamilyAccepts(targetFamily, sourceFamily)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Expression type does not match column data type, expecting "
                    + spellType(target.getDataType()) + " but got " + spellType(sourceType)
                    + " for column " + target.getName()));
            }
        }
    }

    /**
     * The conversion families the type-matching rule reasons in; null means the type takes no part
     * in the rule (GEOGRAPHY, VECTOR, FILE and other engine-specific types keep their own paths).
     */
    private String typeFamily(final DataType type) {
        if (type instanceof NumericType) {
            return "NUMBER";
        }
        if (type instanceof StringType) {
            return "STRING";
        }
        if (type instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (type instanceof DateTimeType) {
            final String name = type.getName().toUpperCase();
            if (name.equals("DATE")) {
                return "DATE";
            }
            if (name.equals("TIME")) {
                return "TIME";
            }
            return "TIMESTAMP";
        }
        if (type instanceof BinaryType) {
            return "BINARY";
        }
        if (type instanceof ArrayType) {
            return "ARRAY";
        }
        if (type instanceof ObjectType) {
            return "OBJECT";
        }
        if (type instanceof VariantType) {
            return "VARIANT";
        }
        return null;
    }

    /** Whether a source family reaches a target column family without a compile refusal. */
    private boolean insertFamilyAccepts(final String target, final String source) {
        if (target.equals(source)) {
            return true;
        }
        if ("VARIANT".equals(source)) {
            // A VARIANT source casts at row time into every family except BINARY.
            return !"BINARY".equals(target);
        }
        if ("NUMBER".equals(target) || "BOOLEAN".equals(target)) {
            return "STRING".equals(source)
                || ("BOOLEAN".equals(target) && "NUMBER".equals(source));
        }
        if ("STRING".equals(target)) {
            return "NUMBER".equals(source) || "BOOLEAN".equals(source) || "DATE".equals(source)
                || "TIME".equals(source) || "TIMESTAMP".equals(source);
        }
        if ("DATE".equals(target)) {
            return "STRING".equals(source) || "TIMESTAMP".equals(source);
        }
        if ("TIME".equals(target)) {
            return "STRING".equals(source) || "TIMESTAMP".equals(source);
        }
        if ("TIMESTAMP".equals(target)) {
            return "STRING".equals(source) || "DATE".equals(source);
        }
        if ("BINARY".equals(target)) {
            return "STRING".equals(source);
        }
        if ("VARIANT".equals(target)) {
            return "NUMBER".equals(source) || "BOOLEAN".equals(source)
                || "ARRAY".equals(source) || "OBJECT".equals(source);
        }
        // ARRAY and OBJECT accept only themselves and VARIANT, both handled above.
        return false;
    }

    /** A type spelled the way live's type-matching refusal spells it, parameters included. */
    private String spellType(final DataType type) {
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            final String name = numeric.getName().toUpperCase();
            if (name.equals("FLOAT") || name.equals("DOUBLE")) {
                return "FLOAT";
            }
            return "NUMBER(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            final int length = ((StringType) type).getMaxLength();
            return "VARCHAR(" + (length > 0 ? length : 16777216) + ")";
        }
        if (type instanceof DateTimeType) {
            final String name = type.getName().toUpperCase();
            if (name.equals("DATE")) {
                return "DATE";
            }
            final int precision = ((DateTimeType) type).getPrecision();
            if (name.equals("TIME")) {
                return "TIME(" + precision + ")";
            }
            if (name.equals("DATETIME") || name.equals("TIMESTAMP")) {
                return "TIMESTAMP_NTZ(" + precision + ")";
            }
            return name + "(" + precision + ")";
        }
        if (type instanceof BinaryType) {
            final int length = ((BinaryType) type).getMaxLength();
            return "BINARY(" + (length > 0 ? length : 8388608) + ")";
        }
        return type.getName().toUpperCase();
    }

    /** Build a row in table-column order from positional or column-listed values (auto-increment + defaults applied). */
    /** Snowflake rejects a VARCHAR string literal for a VARIANT/OBJECT/ARRAY column in a VALUES
     *  clause (live-verified: "Expression type does not match column data type, expecting VARIANT
     *  but got VARCHAR"); use INSERT ... SELECT with PARSE_JSON/TO_VARIANT instead. The check is on
     *  the EXPRESSION (a string literal), because the engine's VARIANT values are JSON text and a
     *  legitimate PARSE_JSON result is indistinguishable from a raw string by value. */
    private void rejectStringLiteralIntoSemiStructured(final Table table, final List<String> columnNames,
                                                       final int valuePosition,
                                                       final FrostlakeParser.ExpressionContext expr) {
        if (!(expr instanceof FrostlakeParser.LiteralExprContext)
                || ((FrostlakeParser.LiteralExprContext) expr).literal().STRING_LITERAL() == null) {
            return;
        }
        final TableColumn col;
        if (columnNames != null) {
            if (valuePosition >= columnNames.size() || !table.hasColumn(columnNames.get(valuePosition))) {
                return;
            }
            col = table.getColumn(columnNames.get(valuePosition));
        } else {
            if (valuePosition >= table.getColumns().size()) {
                return;
            }
            col = table.getColumns().get(valuePosition);
        }
        final String typeName = col.getDataType().getName();
        if ("VARIANT".equals(typeName) || "OBJECT".equals(typeName) || "ARRAY".equals(typeName)) {
            throw new RuntimeException("Expression type does not match column data type, expecting "
                + typeName + " but got VARCHAR for column " + col.getName());
        }
    }

    /**
     * Snowflake rejects semi-structured expressions in a VALUES clause outright (live-verified:
     * OBJECT_CONSTRUCT / ARRAY_CONSTRUCT / PARSE_JSON / TO_VARIANT, the {@code [..]} and
     * {@code {..}} literals and {@code ::VARIANT} casts all raise
     * {@code Invalid expression [...] in VALUES clause}); INSERT ... SELECT is the supported route.
     * The check is on the expression AST's top-level shape.
     */
    private void rejectSemiStructuredValueExpression(final String exprText) {
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
                throw new RuntimeException("Invalid data type ["
                    + ((CastExpression) ast).getDeclaredTarget().getName() + "] in VALUES clause");
            }
            final String target = ((CastExpression) ast).getTargetType().toUpperCase();
            semiStructured = target.startsWith("VARIANT") || target.startsWith("OBJECT") || target.startsWith("ARRAY");
        } else if (ast instanceof FunctionCallExpression) {
            final String name = ((FunctionCallExpression) ast).getFunctionName().toUpperCase();
            final BuiltInFunction fn = executor.getFunctionRegistry().getFunction(name);
            semiStructured = fn != null && (fn.getReturnType() instanceof VariantType
                || fn.getReturnType() instanceof ObjectType || fn.getReturnType() instanceof ArrayType
                || fn.getReturnType() instanceof GeographyType || fn.getReturnType() instanceof GeometryType);
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
            throw new RuntimeException("Invalid expression [" + exprText + "] in VALUES clause");
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
            throw new RuntimeException("INSERT value count (" + values.size()
                + ") does not match the number of target columns (" + columnNames.size() + ")");
        }
        if (columnNames == null && values.size() != table.columnCount()) {
            throw new RuntimeException("Insert value list does not match column list expecting "
                + table.columnCount() + " but got " + values.size());
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
        executor.enforceColumnConstraintsForDml(table, row);
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
            final ResultSet source = executor.executeSelectFromContext(ctx.selectStatement());
            final Table sourceTable = executor.resultSetToTable(source, "multi_insert_source");
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
                            values.add(evaluateRowExpression(exprCtx, sourceTable, srcRow));
                        }
                    } else {
                        values = srcRow.getValues();
                    }
                    insertRowInto(target, fqn,
                        buildInsertRow(target, fqn, columnNames, insertValueIndexes(target, columnNames), values));
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
        try {
            table.getColumn(columnName);
        } catch (final RuntimeException unknown) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(
                where.getStart().getLine(), where.getStart().getCharPositionInLine(),
                columnName.toUpperCase()), unknown);
        }
    }
}
