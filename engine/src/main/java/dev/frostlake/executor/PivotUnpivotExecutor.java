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

import dev.frostlake.executor.expressions.AggregateResultTypes;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.operators.Operator;
import dev.frostlake.executor.operators.StageOperator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.WidthlessStringType;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * PIVOT / UNPIVOT query stages extracted from {@link QueryExecutor}. Both are planned with their shape and
 * run as pipeline stages that transform their input rows: identifier text comes from the stateless
 * {@link ParseTreeText} helper and each cell is the aggregate a grouped query would compute over the cell's
 * rows. The owning executor answers a subquery-driven IN list and builds the relation shape.
 */
final class PivotUnpivotExecutor {

    /** The aggregates that only count what they read, which hand out none of its values. */
    private static final List<String> COUNTING = List.of("COUNT", "COUNT_IF", "APPROX_COUNT_DISTINCT", "HLL");

    private final QueryExecutor executor;

    PivotUnpivotExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * The PIVOT stage and the relation it answers. Its columns are known while planning: the grouping
     * columns, then one per pivot value — the written IN list, a subquery's first column evaluated now,
     * or for {@code IN (ANY)} the distinct FOR-column values read off {@code rowsForAny}.
     *
     * @param table          the relation pivoted
     * @param pivotCtx       the clause
     * @param rowsForAny     the rows the FOR column's distinct values are read from, needed for ANY only
     * @param renamedColumns the alias list of {@code PIVOT(…) AS p (c1, c2)}, renaming the columns
     *                       positionally, or null
     * @param relationName   the name the pivoted relation takes
     * @return the stage and its shape
     */
    PlannedRelation planPivot(final Table table, final FrostlakeParser.PivotClauseContext pivotCtx,
                              final List<Row> rowsForAny, final List<String> renamedColumns,
                              final String relationName) {
        // The aggregate is one registered aggregate over one column of the relation (see PivotAggregateCall).
        final String aggColumn = PivotAggregateCall.column(pivotCtx, table, executor.getFunctionRegistry());
        final String aggFuncName = pivotCtx.aggregateFunction().functionName().getText().toUpperCase(Locale.ROOT);
        final String aggregateText = ParseTreeText.getOriginalText(pivotCtx.aggregateFunction());
        final FrostlakeParser.ExpressionContext aggregateCall = executor.parsedExpression(aggregateText);
        final String pivotColumn = ParseTreeText.getIdentifier(pivotCtx.pivotColumn);
        if (!table.hasColumn(pivotColumn)) {
            // Refused where the FOR column is written (live-verified).
            final Token at = pivotCtx.pivotColumn.getStart();
            final SourcePosition shown = ExpressionSource.place(at.getLine(), at.getCharPositionInLine());
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(shown.getLine(),
                shown.getCharPositionInLine(), pivotColumn));
        }
        PivotAggregateCall.requireArity(pivotCtx, table, executor.getFunctionRegistry(),
            PivotRewrite.relation((FrostlakeParser.TableReferenceContext) pivotCtx.getParent()), aggColumn,
            pivotColumn);
        final String aggregateAlias = pivotCtx.pivotAggregateAlias == null ? null
            : ParseTreeText.getIdentifier(pivotCtx.pivotAggregateAlias);
        // Get column indices
        final int aggColIndex = table.getColumnIndex(aggColumn);
        final int pivotColIndex = table.getColumnIndex(pivotColumn);
        // Get pivot values: an explicit list, ANY (dynamic: the distinct FOR-column values), or a subquery.
        final List<PivotValue> pivotValues = new ArrayList<>();
        final FrostlakeParser.PivotInListContext inList = pivotCtx.pivotInList();
        if (inList.pivotValueList() != null) {
            for (final FrostlakeParser.PivotValueContext pvCtx : inList.pivotValueList().pivotValue()) {
                if (pvCtx.COLON() != null) {
                    // A bind among the values is never bound there: it is one nothing sets, refused at its colon
                    // whatever a block's variable of the name holds (live-verified).
                    final Token colon = pvCtx.COLON().getSymbol();
                    final SourcePosition at = ExpressionSource.place(colon.getLine(), colon.getCharPositionInLine());
                    throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                        "Bind variable :" + pvCtx.identifier().getText() + " not set."));
                }
                // The value used to MATCH rows is the literal's value (q1), but the output column is NAMED after
                // the literal AS WRITTEN — Snowflake keeps the quotes inside the identifier, so a string pivot
                // value 'q1' produces a column called 'q1' that is referenced as "'q1'" (which is exactly how
                // real queries write it, e.g. MAX("'q1'")). Naming it `q1` instead made "'q1'" unresolvable and,
                // worse, made MAX("'q1'") silently return the STRING q1. An explicit alias still wins.
                final String valueAlias = pvCtx.identifier() != null ? ParseTreeText.getIdentifier(pvCtx.identifier())
                    : null;
                if (pvCtx.literal() == null) {
                    // A constant written as an expression names its column as the plan prints it: TRUE::BOOLEAN is
                    // CAST(TRUE AS BOOLEAN), NOT FALSE is NOT(FALSE), +2 is UNARY PLUS(2) (live-verified).
                    pivotValues.add(PivotValue.computed(
                        ExpressionEvaluator.parse(ParseTreeText.getOriginalText(pvCtx.booleanExpr())),
                        evaluateConstant(pvCtx.booleanExpr()), valueAlias));
                    continue;
                }
                pivotValues.add(PivotValue.written(pvCtx.literal(), literalValue(pvCtx.literal()), valueAlias));
            }
            rejectRepeatedColumns(inList.pivotValueList(), pivotValues);
        } else if (inList.ANY() != null) {
            // Dynamic pivot: the distinct values of the FOR column in their own order — ascending, or as the
            // ORDER BY inside ANY says — with the rows holding none last, or first when descending.
            final boolean descending = !inList.orderItem().isEmpty() && inList.orderItem(0).DESC() != null;
            final Map<Object, Object> distinct = new LinkedHashMap<>();
            boolean anyNull = false;
            for (final Row row : rowsForAny) {
                final Object value = DeferredFault.read(row.getValue(pivotColIndex));
                if (value == null) {
                    anyNull = true;
                } else {
                    distinct.putIfAbsent(ValueComparisons.canonicalGroupKeyValue(value), value);
                }
            }
            final List<Object> ordered = new ArrayList<>(distinct.values());
            Collections.sort(ordered, new Comparator<Object>() {
                @Override
                public int compare(final Object left, final Object right) {
                    final int order = ValueComparisons.compareValues(left, right);
                    return descending ? -order : order;
                }
            });
            if (anyNull && descending) {
                pivotValues.add(PivotValue.read(null));
            }
            for (final Object value : ordered) {
                pivotValues.add(PivotValue.read(value));
            }
            if (anyNull && !descending) {
                pivotValues.add(PivotValue.read(null));
            }
        } else if (inList.selectStatement() != null) {
            // Subquery-driven pivot columns: the first column of the subquery result, in result order, a NULL
            // it answers among them.
            final ResultSet sub = executor.executeSelectFromContext(inList.selectStatement());
            final Set<Object> seen = new HashSet<>();
            boolean nullSeen = false;
            for (final Row row : sub.getRows()) {
                final Object value = DeferredFault.read(row.getValue(0));
                if (value == null) {
                    if (!nullSeen) {
                        pivotValues.add(PivotValue.read(null));
                    }
                    nullSeen = true;
                } else if (seen.add(ValueComparisons.canonicalGroupKeyValue(value))) {
                    pivotValues.add(PivotValue.read(value));
                }
            }
        }
        // DEFAULT ON NULL (expr): the constant substituted for empty pivot cells.
        final Object defaultOnNull = pivotCtx.DEFAULT() != null
            ? evaluateConstant(pivotCtx.expression()) : null;
        // Get all non-pivot/non-agg columns for grouping
        final List<Integer> groupByColIndices = new ArrayList<>();
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (i != aggColIndex && i != pivotColIndex) {
                groupByColIndices.add(i);
            }
        }
        // Build result columns
        final List<ResultSetColumn> resultColumns = new ArrayList<>();
        for (final int idx : groupByColIndices) {
            final TableColumn col = table.getColumns().get(idx);
            resultColumns.add(withPolicy(new ResultSetColumn(col.getName(), col.getDataType(), null),
                col.getProjectionPolicyName()));
        }
        // A pivoted column hands out the aggregated column's values, and comes under its projection policy, unless
        // the aggregate only counts them (live-verified: MAX(ssn) is refused by the pivoted columns, COUNT(ssn) is
        // not).
        final String pivotedPolicy = aggColIndex < 0 || COUNTING.contains(aggFuncName.toUpperCase(Locale.ROOT)) ? null
            : table.getColumns().get(aggColIndex).getProjectionPolicyName();
        final DataType aggregated = aggColIndex >= 0 ? table.getColumns().get(aggColIndex).getDataType() : null;
        requireAggregable(aggFuncName, aggregated);
        final DataType pivotedType = pivotColumnType(aggFuncName, aggregated, aggregateText, table,
            inList.pivotValueList() != null);
        for (final PivotValue pivotValue : pivotValues) {
            resultColumns.add(withPolicy(new ResultSetColumn(pivotValue.name(aggregateAlias), pivotedType, null),
                pivotedPolicy));
        }
        final Table shape = executor.resultSetToTable(
            new ResultSet(renamed(resultColumns, renamedColumns), new ArrayList<Row>()), relationName);
        final Operator stage = new StageOperator("PIVOT[" + aggFuncName + "(" + aggColumn + ") FOR " + pivotColumn
                + (inList.ANY() != null ? " IN (ANY)" : "") + "]") {
            @Override
            protected List<Row> apply(final List<Row> rows) {
                // Group rows by the non-pivot columns. Buckets use canonicalized keys (so equal numbers with
                // different runtime types group together); output rows keep the first row's raw values.
                final Map<List<Object>, Map<Integer, List<Row>>> groups = new LinkedHashMap<>();
                final Map<List<Object>, List<Object>> groupKeyDisplayValues = new HashMap<>();
                // The pivoted column each FOR-column value belongs to, found once per value.
                final Map<Object, Integer> columnOf = new HashMap<>();
                if (groupByColIndices.isEmpty()) {
                    // With nothing to group by the pivot is one aggregate row, over no rows too.
                    final List<Object> everything = new ArrayList<>();
                    groups.put(everything, new HashMap<Integer, List<Row>>());
                    groupKeyDisplayValues.put(everything, new ArrayList<Object>());
                }
                for (final Row row : rows) {
                    // Build group key from non-pivot columns
                    final List<Object> rawKey = new ArrayList<>();
                    final List<Object> groupKey = new ArrayList<>();
                    for (final int idx : groupByColIndices) {
                        // Pivoting reads its grouping, pivot and aggregated cells, so a deferred fault raises here.
                        final Object value = DeferredFault.read(row.getValue(idx));
                        rawKey.add(value);
                        groupKey.add(ValueComparisons.canonicalGroupKeyValue(value));
                    }
                    // A row belongs to its group, and to the pivoted column its FOR column's value is — a NULL to
                    // the column written NULL, when there is one.
                    final Object pivotCell = DeferredFault.read(row.getValue(pivotColIndex));
                    DeferredFault.read(row.getValue(aggColIndex));
                    Map<Integer, List<Row>> groupBuckets = groups.get(groupKey);
                    if (groupBuckets == null) {
                        groupBuckets = new HashMap<>();
                        groups.put(groupKey, groupBuckets);
                        groupKeyDisplayValues.put(groupKey, rawKey);
                    }
                    final Object cellKey = ValueComparisons.canonicalGroupKeyValue(pivotCell);
                    Integer column = columnOf.get(cellKey);
                    if (column == null) {
                        column = Integer.valueOf(-1);
                        for (int i = 0; i < pivotValues.size(); i++) {
                            if (pivotValues.get(i).holds(pivotCell)) {
                                column = Integer.valueOf(i);
                                break;
                            }
                        }
                        columnOf.put(cellKey, column);
                    }
                    if (column.intValue() < 0) {
                        continue;
                    }
                    List<Row> pivotBucket = groupBuckets.get(column);
                    if (pivotBucket == null) {
                        pivotBucket = new ArrayList<>();
                        groupBuckets.put(column, pivotBucket);
                    }
                    pivotBucket.add(row);
                }
                // Build result rows: each cell is the aggregate over its rows, as a grouped query computes it, so
                // a cell no row reaches holds what the aggregate answers over none — a zero count, an empty list.
                final List<Row> resultRows = new ArrayList<>();
                for (final Map.Entry<List<Object>, Map<Integer, List<Row>>> entry : groups.entrySet()) {
                    final List<Object> rowValues = new ArrayList<>(groupKeyDisplayValues.get(entry.getKey()));
                    for (int i = 0; i < pivotValues.size(); i++) {
                        final List<Row> cellRows = entry.getValue().getOrDefault(Integer.valueOf(i),
                            new ArrayList<Row>());
                        Object aggResult = executor.aggregateOverRows(aggregateCall, cellRows, table);
                        if (aggResult == null && defaultOnNull != null) {
                            aggResult = defaultOnNull;
                        }
                        rowValues.add(aggResult);
                    }
                    resultRows.add(new Row(rowValues));
                }
                return resultRows;
            }
        };
        return new PlannedRelation(stage, shape, null);
    }

    /**
     * The UNPIVOT stage and the relation it answers: the preserved columns, then the name column — a text of no
     * width, VARCHAR(134217728) — and the value column, of the unpivoted columns' one type (see
     * {@link #unpivotedType}).
     *
     * @param table          the relation unpivoted
     * @param unpivotCtx     the clause
     * @param renamedColumns the alias list of {@code UNPIVOT(…) AS u (c1, c2)}, renaming the columns
     *                       positionally, or null
     * @param relationName   the name the unpivoted relation takes
     * @return the stage and its shape
     */
    PlannedRelation planUnpivot(final Table table, final FrostlakeParser.UnpivotClauseContext unpivotCtx,
                                final List<String> renamedColumns, final String relationName) {
        // Extract UNPIVOT components
        final String valueColumn = ParseTreeText.getIdentifier(unpivotCtx.identifier(0)); // value_column
        final String nameColumn = ParseTreeText.getIdentifier(unpivotCtx.identifier(1));  // name_column
        // Get columns to unpivot, and the name each one's rows carry: its alias when it has one
        final List<String> unpivotColumns = new ArrayList<>();
        final List<String> unpivotNames = new ArrayList<>();
        for (final FrostlakeParser.UnpivotColumnContext column : unpivotCtx.unpivotColumnList().unpivotColumn()) {
            unpivotColumns.add(ParseTreeText.getIdentifier(column.identifier(0)));
            unpivotNames.add(ParseTreeText.getIdentifier(column.identifier(column.identifier().size() - 1)));
        }
        // Get column indices for unpivot columns
        final List<Integer> unpivotColIndices = new ArrayList<>();
        for (final String colName : unpivotColumns) {
            unpivotColIndices.add(table.getColumnIndex(colName));
        }
        // Get indices of columns to preserve
        final List<Integer> preserveColIndices = new ArrayList<>();
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (!unpivotColIndices.contains(i)) {
                preserveColIndices.add(i);
            }
        }
        // Build result columns: preserved columns + name column + value column
        final List<ResultSetColumn> resultColumns = new ArrayList<>();
        // A column keeps the type it is known to have: a table's own column, and the name and value columns, which
        // SYSTEM$TYPEOF reads as VARCHAR and as the unpivoted columns' type (live-verified).
        final boolean declared = table.residentSource() != null;
        boolean unpivotedKnown = true;
        for (final int idx : unpivotColIndices) {
            final TableColumn col = table.getColumns().get(idx);
            unpivotedKnown &= declared || col.isStaticallyTyped();
        }
        for (final int idx : preserveColIndices) {
            final TableColumn col = table.getColumns().get(idx);
            resultColumns.add(withPolicy(new ResultSetColumn(col.getName(), col.getDataType(), null,
                declared || col.isStaticallyTyped() ? col.getDataType() : null), col.getProjectionPolicyName()));
        }
        resultColumns.add(new ResultSetColumn(nameColumn, WidthlessStringType.WIDTHLESS, null,
            WidthlessStringType.WIDTHLESS));
        final DataType valueType = unpivotedType(table, unpivotColIndices);
        // The value column hands out the unpivoted columns' values, and comes under a projection policy any of them
        // carries (live-verified).
        String valuePolicy = null;
        for (final int idx : unpivotColIndices) {
            if (valuePolicy == null) {
                valuePolicy = table.getColumns().get(idx).getProjectionPolicyName();
            }
        }
        resultColumns.add(withPolicy(new ResultSetColumn(valueColumn, valueType, null,
            unpivotedKnown ? valueType : null), valuePolicy));
        // Snowflake EXCLUDEs NULL values by default (a row is emitted only for a non-NULL unpivoted
        // value); the optional INCLUDE NULLS modifier keeps them, EXCLUDE NULLS is the explicit default.
        final boolean includeNulls = unpivotCtx.unpivotNulls() != null
            && unpivotCtx.unpivotNulls().INCLUDE() != null;
        final Table shape = executor.resultSetToTable(
            new ResultSet(renamed(resultColumns, renamedColumns), new ArrayList<Row>()), relationName);
        final Operator stage = new StageOperator("UNPIVOT[" + valueColumn + " FOR " + nameColumn + "]") {
            @Override
            protected List<Row> apply(final List<Row> rows) {
                // Build result rows
                final List<Row> resultRows = new ArrayList<>();
                for (final Row row : rows) {
                    // For each unpivot column, create a new row
                    for (int i = 0; i < unpivotColumns.size(); i++) {
                        final Object value = DeferredFault.read(row.getValue(unpivotColIndices.get(i)));
                        if (value == null && !includeNulls) {
                            continue;
                        }
                        final List<Object> rowValues = new ArrayList<>();
                        // Add preserved column values
                        for (final int idx : preserveColIndices) {
                            rowValues.add(row.getValue(idx));
                        }
                        // Add column name
                        rowValues.add(unpivotNames.get(i));
                        // Add column value
                        rowValues.add(value);
                        resultRows.add(new Row(rowValues));
                    }
                }
                return resultRows;
            }
        };
        return new PlannedRelation(stage, shape, null);
    }

    /**
     * Refuse a written IN list two of whose values name one column: {@code The column ''JAN'' was specified multiple
     * times for 'FROM'} for a value written twice, and {@code Duplicate column alias 'X'.} for two equal aliases,
     * placed at the value the second one names (live-verified).
     */
    private static void rejectRepeatedColumns(final FrostlakeParser.PivotValueListContext list,
                                              final List<PivotValue> values) {
        final Set<String> named = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            if (named.add(values.get(i).name())) {
                continue;
            }
            final FrostlakeParser.PivotValueContext written = list.pivotValue(i);
            if (written.identifier() != null) {
                final Token value = written.getStart();
                final SourcePosition at = ExpressionSource.place(value.getLine(), value.getCharPositionInLine());
                throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    "Duplicate column alias '" + values.get(i).name() + "'."));
            }
            throw new RuntimeException(SqlCompilationError.of("The column '" + values.get(i).name()
                + "' was specified multiple times for 'FROM'"));
        }
    }

    /**
     * The one type the unpivoted columns share, which the value column declares. They are read in the relation's
     * column order, the first setting the type, and the first column of another type is refused: {@code The type of
     * column 'B' conflicts with the type of other columns in the UNPIVOT list.} Types are compared whole — a
     * NUMBER's precision and scale, a BINARY's width, a timestamp's precision — but for text, whose widths join in
     * the widest: VARCHAR(3) beside VARCHAR(10) is VARCHAR(10) (live-verified).
     *
     * @param table   the relation unpivoted
     * @param indices the unpivoted columns' positions, as listed
     * @return the value column's type
     */
    private static DataType unpivotedType(final Table table, final List<Integer> indices) {
        final List<Integer> inColumnOrder = new ArrayList<>(indices);
        Collections.sort(inColumnOrder);
        DataType shared = table.getColumns().get(inColumnOrder.get(0)).getDataType();
        for (int i = 1; i < inColumnOrder.size(); i++) {
            final TableColumn column = table.getColumns().get(inColumnOrder.get(i));
            final DataType type = column.getDataType();
            if (shared instanceof StringType && type instanceof StringType) {
                if (((StringType) type).getMaxLength() > ((StringType) shared).getMaxLength()) {
                    shared = type;
                }
            } else if (!sameUnpivotedType(shared, type)) {
                throw new RuntimeException(SqlCompilationError.of("The type of column '" + column.getName()
                    + "' conflicts with the type of other columns in the UNPIVOT list."));
            }
        }
        return shared;
    }

    /**
     * Whether two types of unpivoted columns are one: of one kind, and of one precision, scale or width. A column
     * whose type is not known is not judged.
     */
    private static boolean sameUnpivotedType(final DataType first, final DataType second) {
        if (first == null || second == null) {
            return true;
        }
        if (NumericType.isApproximate(first) || NumericType.isApproximate(second)) {
            return NumericType.isApproximate(first) && NumericType.isApproximate(second);
        }
        if (first.getClass() != second.getClass()) {
            return false;
        }
        if (first instanceof NumericType) {
            return ((NumericType) first).getPrecision() == ((NumericType) second).getPrecision()
                && ((NumericType) first).getScale() == ((NumericType) second).getScale();
        }
        if (first instanceof BinaryType) {
            return ((BinaryType) first).getMaxLength() == ((BinaryType) second).getMaxLength();
        }
        if (first instanceof DateTimeType) {
            return first.getName().equals(second.getName())
                && ((DateTimeType) first).getPrecision() == ((DateTimeType) second).getPrecision();
        }
        return true;
    }

    /**
     * A pivot alias's derived-column alias list — {@code PIVOT(…) AS p (empid, q1, q2)} — renaming the
     * pivoted result columns positionally. A pivot's column names are otherwise derived from the pivoted
     * values, so this list is the only way to name them, and an outer query that references those names (the
     * reason the list is written at all) cannot work without it.
     */
    /**
     * What a pivoted column declares: its aggregate's type over the aggregated column, as the same aggregate
     * declares in a grouped SELECT. COUNT declares the counter's width, MIN and MAX the column's own type, SUM
     * and AVG widen it by the type inferencer's rules, and any other aggregate declares what its call does.
     * Declaring every pivoted column BIGINT told a driver that reads the declared scale to round an average of
     * 92.5 to 92. A text answer over a written list of values declares no width of its own — VARCHAR(134217728) —
     * where one over ANY or a subquery keeps the column's (live-verified).
     *
     * @param aggFuncName   the aggregate's upper-case name
     * @param aggregated    the aggregated column's type, or null when it is not a column of the source
     * @param aggregateText the aggregate call as written
     * @param table         the relation pivoted
     * @param writtenValues whether the IN list writes its values out
     * @return the pivoted columns' type
     */
    private DataType pivotColumnType(final String aggFuncName, final DataType aggregated, final String aggregateText,
                                     final Table table, final boolean writtenValues) {
        final DataType declared = declaredAggregateType(aggFuncName, aggregated, aggregateText, table);
        if (writtenValues && declared instanceof StringType) {
            return new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);
        }
        return declared;
    }

    /**
     * The aggregates a pivot cannot compute over its column's type, refused as the account refuses them, whether or
     * not a row is there (live-verified): SUM and AVG over a BOOLEAN — both named SUM, the call an average is
     * computed from — at no position of the statement, and MIN or MAX over an ARRAY.
     *
     * @param aggFuncName the aggregate's upper-case name
     * @param aggregated  the aggregated column's type, or null when it is not a column of the source
     */
    private static void requireAggregable(final String aggFuncName, final DataType aggregated) {
        if (aggregated instanceof BooleanType && ("SUM".equals(aggFuncName) || "AVG".equals(aggFuncName))) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " error line 0 at position -1\n"
                + "Invalid argument types for function 'SUM': (BOOLEAN)");
        }
        if (aggregated instanceof ArrayType && ("MIN".equals(aggFuncName) || "MAX".equals(aggFuncName))) {
            throw new RuntimeException(SqlCompilationError.of("Function " + aggFuncName
                + " does not support ARRAY argument type"));
        }
    }

    private DataType declaredAggregateType(final String aggFuncName, final DataType aggregated,
                                           final String aggregateText, final Table table) {
        final DataType inferred = typedCall(aggregateText, table);
        if (aggFuncName.equals("COUNT")) {
            return IntegerResultWidths.COUNTER;
        }
        if (aggregated == null) {
            return NumericType.BIGINT;
        }
        if (aggFuncName.equals("MIN") || aggFuncName.equals("MAX")) {
            return aggregated;
        }
        final DataType computed = AggregateResultTypes.computing(aggFuncName, aggregated);
        if (computed != null) {
            return computed;
        }
        return inferred != null ? inferred : NumericType.BIGINT;
    }

    /** The type the aggregate call declares over the relation, or null when it cannot be told. */
    private DataType typedCall(final String aggregateText, final Table table) {
        try {
            return new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor)
                .inferStaticType(ExpressionEvaluator.parse(aggregateText));
        } catch (final RuntimeException notTypeable) {
            return null;
        }
    }

    private static List<ResultSetColumn> renamed(final List<ResultSetColumn> columns, final List<String> names) {
        if (names == null) {
            return columns;
        }
        final List<ResultSetColumn> renamedColumns = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            final ResultSetColumn column = columns.get(i);
            renamedColumns.add(i < names.size() ? new ResultSetColumn(names.get(i), column.getDataType()) : column);
        }
        return renamedColumns;
    }

    /** Evaluate a constant expression (the PIVOT DEFAULT ON NULL value) with no row context. */
    /** The value a pivot value's literal spells; null for the word NULL. */
    private Object literalValue(final FrostlakeParser.LiteralContext literal) {
        final Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(
            dummyTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        return evaluator.evaluate(ParseTreeText.getOriginalText(literal), new Row(new ArrayList<>()));
    }

    private Object evaluateConstant(final ParserRuleContext expr) {
        final Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(
            dummyTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        return evaluator.evaluate(ParseTreeText.getOriginalText(expr), new Row(new ArrayList<>()));
    }


    /** A result column put under a projection policy, or as it is when there is none. */
    private static ResultSetColumn withPolicy(final ResultSetColumn column, final String policy) {
        return policy == null ? column : column.withProjectionPolicy(policy);
    }
}
