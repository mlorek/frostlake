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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A subquery as a refusal echoes the plan the account holds for it (live-verified):
 *
 * <pre>
 *   (SELECT SUM(n) FROM gn)                        (SELECT SUM(CAST(GN.N AS NUMBER(38,0))) AS "SUM(N)" FROM GN AS GN)
 *   (SELECT n FROM gn ORDER BY n LIMIT 1)          (SELECT GN.N AS "N" FROM GN AS GN  ORDER BY N ASC NULLS LAST
 *                                                   LIMIT 1 OFFSET 0)
 *   (SELECT MAX(x.n) FROM gn x WHERE x.n <= gn.n)  (SELECT MAX(X.N) AS "MAX(X.N)" FROM GN AS X WHERE
 *                                                   X.N <= CORRELATION(GN.N))
 * </pre>
 *
 * each item as the plan computes it, named by its text upper-cased, a SUM of an exact number adding it as its column's
 * NUMBER, NUMBER(38,0) for a whole one, and an AVG of a whole number as that sum over its count; the relation by its
 * name and the name it goes by; a column of any other relation in scope read as a correlation. A GROUP BY of columns
 * prints them as read, a HAVING its condition upper-cased after two spaces; a select of constants alone over a table
 * read whole is a GENERATOR of the table's rows, as many as the LIMIT keeps (all live-verified):
 *
 * <pre>
 *   (SELECT AVG(id) FROM dz WHERE id &gt; 0)   (SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(DZ.ID AS NUMBER(38,0))) AS
 *                                           NUMBER(38,0)), CAST(COUNT(DZ.ID) AS NUMBER(18,0))) AS "AVG(ID)" FROM DZ
 *                                           AS DZ WHERE DZ.ID &gt; 0)
 *   (SELECT MIN(id) FROM dz GROUP BY id)    (SELECT MIN(DZ.ID) AS "MIN(ID)" FROM DZ AS DZ GROUP BY DZ.ID)
 *   (SELECT MIN(id) FROM dz HAVING MIN(id) &gt; 0)   (… FROM DZ AS DZ  HAVING MIN(ID) &gt; 0)
 *   (SELECT 2 FROM dz LIMIT 1)              (SELECT 2 AS "2" FROM TABLE (GENERATOR(ROWCOUNT =&gt; 1,
 *                                           rowCount =&gt; 1)))
 * </pre>
 *
 * A table of one row is folded into the query: an item read from the row is its value over as many GENERATOR rows as
 * the LIMIT and its OFFSET leave of the one, a grouped one beside the row's values as the keys, a filter false of the
 * row is FALSE, and a whole number's SUM and AVG add the row's value (all live-verified):
 *
 * <pre>
 *   (SELECT v FROM one ORDER BY v)          (SELECT 12345 AS "V" FROM TABLE (GENERATOR(ROWCOUNT =&gt; 1,
 *                                           rowCount =&gt; 1)))
 *   (SELECT v FROM one LIMIT 1 OFFSET 1)    (… FROM TABLE (GENERATOR(ROWCOUNT =&gt; 0, rowCount =&gt; 0)))
 *   (SELECT v FROM one GROUP BY v)          (SELECT 12345 AS "V" FROM ONE AS ONE GROUP BY 12345)
 *   (SELECT v FROM one WHERE v &lt; 0)        (SELECT ONE.V AS "V" FROM ONE AS ONE WHERE FALSE)
 *   (SELECT AVG(v) FROM one)                (SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(12345) AS NUMBER(38,0)), 1) AS
 *                                           "AVG(V)" FROM ONE AS ONE)
 * </pre>
 *
 * A select over a set operation, a FROM-less select or a VALUES list, and a set operation alone, are printed as
 * SetOperationPlanText has them. A query beyond these and one relation's SELECT, or one that sums or averages
 * anything else, is echoed as written, upper-cased.
 */
final class SubqueryPlanText {

    private SubqueryPlanText() {
    }

    /**
     * The plan text of a subquery.
     *
     * @param executor the executor, whose catalog holds the relation
     * @param query    the subquery's query
     * @param around   the qualifiers of the relations in scope around the query, upper-cased
     * @return the text, in parentheses
     */
    static String of(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext query,
                     final Set<String> around) {
        final String written = "(" + ParseTreeText.getOriginalText(query).toUpperCase(Locale.ROOT) + ")";
        try {
            final String printed = printed(executor, query, around);
            return printed != null ? printed : written;
        } catch (final RuntimeException unprintable) {
            return written;
        }
    }

    /**
     * {@link #of}, or null where the plan's text is not known.
     *
     * @param executor the executor that resolves the subquery's names
     * @param query    the subquery
     * @param around   the qualifiers of the relations in scope around it
     * @return the subquery as the plan holds it, or null
     */
    static String printedOrNull(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext query,
                                final Set<String> around) {
        try {
            return printed(executor, query, around);
        } catch (final RuntimeException unprintable) {
            return null;
        }
    }

    private static String printed(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext query,
                                  final Set<String> around) {
        if (query.selectOperand().size() > 1) {
            // A set operation standing alone (see SetOperationPlanText).
            return SetOperationPlanText.combined(query);
        }
        if (query.withClause() != null || query.fetchClause() != null || query.selectOperand().size() != 1
                || query.selectOperand(0).selectClause() == null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext select = query.selectOperand(0).selectClause();
        if (select.tableExpression() == null || select.topClause() != null
                || select.qualifyClause() != null || select.connectByClause() != null
                || select.tableExpression().tableReference().size() != 1
                || !select.tableExpression().joinClause().isEmpty()
                || (select.groupByClause() != null || select.havingClause() != null) && query.orderByClause() != null
                || select.groupByClause() != null && select.groupByClause().ALL() != null) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext reference = select.tableExpression().tableReference(0);
        if (reference.tableSource() != null && (reference.tableSource().selectStatement() != null
                || reference.tableSource().VALUES() != null)) {
            // Over a set operation, a FROM-less select or a VALUES list (see SetOperationPlanText).
            return SetOperationPlanText.overDerived(executor, query, select, reference);
        }
        if (reference.tableSource() == null || reference.tableSource().tableQualifiedName() == null
                || reference.pivotClause() != null || reference.unpivotClause() != null
                || reference.sampleClause() != null || reference.matchRecognizeClause() != null) {
            return null;
        }
        final Table table;
        try {
            table = executor.getCatalog().resolveTable(
                ParseTreeText.getQualifiedName(reference.tableSource().tableQualifiedName()));
        } catch (final RuntimeException noTable) {
            return null;
        }
        final FrostlakeParser.NonJoinKeywordIdentifierContext bare = reference.nonJoinKeywordIdentifier();
        final String relation = reference.aliasName() != null
            ? ParseTreeText.getIdentifier(reference.aliasName()).toUpperCase(Locale.ROOT)
            : bare != null && bare.identifier() != null
                ? ParseTreeText.getIdentifier(bare.identifier()).toUpperCase(Locale.ROOT)
                : table.getName().toUpperCase(Locale.ROOT);
        final Map<String, Table> relations = new LinkedHashMap<>();
        relations.put(relation, table);
        final List<Table> tables = new ArrayList<>();
        tables.add(table);
        final ExpressionEvaluator printer =
            new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        printer.setMultiTableContext(relations, tables);
        final Set<String> outer = new HashSet<>(around);
        outer.remove(relation);
        printer.setEnclosingQualifiers(outer);
        final List<Row> statistics = executor.statisticsRowsFor(executor.getFullyQualifiedTableName(
            ParseTreeText.getQualifiedName(reference.tableSource().tableQualifiedName())));
        final Row only = statistics.size() == 1 ? statistics.get(0) : null;
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(select);
        final StringBuilder text = new StringBuilder("(SELECT ");
        final List<FrostlakeParser.SelectItemContext> items = select.selectList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof FrostlakeParser.ExprItemContext)) {
                return null;
            }
            final FrostlakeParser.ExprItemContext item = (FrostlakeParser.ExprItemContext) items.get(i);
            final String itemText = ParseTreeText.getOriginalText(item.booleanExpr());
            final String alias = SelectItemAccessors.getItemAlias(item);
            final String name = alias != null ? alias : itemText.toUpperCase(Locale.ROOT);
            final String computed =
                computed(printer, table, relation, item.booleanExpr(), where == null ? only : null);
            if (computed == null) {
                return null;
            }
            text.append(i > 0 ? ", " : "").append(computed).append(" AS \"").append(name).append('"');
        }
        if (where == null && select.DISTINCT() == null && select.groupByClause() == null
                && select.havingClause() == null && query.orderByClause() == null && constantsAlone(items)) {
            // Constants read over a table whole are as many rows of a GENERATOR: the LIMIT counts them.
            long rows = executor.statisticsRowsFor(executor.getFullyQualifiedTableName(
                ParseTreeText.getQualifiedName(reference.tableSource().tableQualifiedName()))).size();
            if (query.limitClause() != null) {
                if (query.limitClause().OFFSET() != null) {
                    return null;
                }
                rows = Math.min(rows, Long.parseLong(query.limitClause().getChild(1).getText()));
            }
            return text.append(" FROM TABLE (GENERATOR(ROWCOUNT => ").append(rows).append(", rowCount => ")
                .append(rows).append(")))").toString();
        }
        if (only != null) {
            final String pinned = pinnedRow(executor, printer, query, select, only, table, relation);
            if (pinned != null) {
                return pinned;
            }
        }
        if (select.DISTINCT() != null) {
            return null;
        }
        text.append(" FROM ").append(table.getName().toUpperCase(Locale.ROOT)).append(" AS ").append(relation);
        if (where != null) {
            // A filter false of a table's one row is folded to FALSE, and a conjunct the statistics prove true of
            // every row is dropped from the filter.
            final String condition = unprovenCondition(executor, select, reference, table, where.booleanExpr());
            text.append(" WHERE ").append(only != null && Boolean.FALSE.equals(
                printer.evaluate(ExpressionEvaluator.parse(condition), only))
                ? "FALSE" : printer.boundPlannedText(ExpressionEvaluator.parse(condition)));
        }
        if (select.groupByClause() != null) {
            final StringBuilder keys = new StringBuilder();
            for (final FrostlakeParser.GroupByElementContext element : select.groupByClause().groupByElement()) {
                if (element.booleanExpr() == null
                        || !(valueOf(element.booleanExpr()) instanceof FrostlakeParser.QualifiedNameExprContext)) {
                    return null;
                }
                keys.append(keys.length() > 0 ? ", " : "").append(printer.boundPlannedText(
                    ExpressionEvaluator.parse(ParseTreeText.getOriginalText(element.booleanExpr()))));
            }
            text.append(" GROUP BY ").append(keys);
        }
        if (select.havingClause() != null) {
            text.append(where == null && select.groupByClause() == null ? "  HAVING " : " HAVING ").append(
                ParseTreeText.getOriginalText(select.havingClause().booleanExpr()).toUpperCase(Locale.ROOT));
        }
        if (query.orderByClause() != null) {
            if (query.orderByClause().orderItem().isEmpty()) {
                return null;
            }
            // No WHERE leaves its place empty before the ORDER BY.
            text.append(where == null ? "  ORDER BY " : " ORDER BY ");
            final List<FrostlakeParser.OrderItemContext> keys = query.orderByClause().orderItem();
            for (int i = 0; i < keys.size(); i++) {
                final FrostlakeParser.OrderItemContext key = keys.get(i);
                final boolean descending = key.DESC() != null;
                final boolean nullsFirst =
                    SessionNullOrdering.nullsFirst(!descending, ValueComparisons.nullsFirstFlag(key));
                text.append(i > 0 ? ", " : "")
                    .append(ParseTreeText.getOriginalText(key.booleanExpr()).toUpperCase(Locale.ROOT))
                    .append(descending ? " DESC" : " ASC").append(nullsFirst ? " NULLS FIRST" : " NULLS LAST");
            }
        }
        if (query.limitClause() != null) {
            final FrostlakeParser.LimitClauseContext limit = query.limitClause();
            final String count = limit.getChild(1).getText();
            final String offset = limit.OFFSET() != null ? limit.getChild(3).getText() : "0";
            text.append(" LIMIT ").append(count).append(" OFFSET ").append(offset);
        }
        return text.append(')').toString();
    }

    /**
     * A filter as the plan keeps it: the top-level conjuncts the table's statistics prove true of every row dropped
     * where one other remains, {@code id > 0 AND id < 5} over 0, 1 and 2 kept as {@code DZ.ID > 0} (live-verified);
     * the filter as written otherwise.
     */
    private static String unprovenCondition(final QueryExecutor executor,
                                            final FrostlakeParser.SelectClauseContext select,
                                            final FrostlakeParser.TableReferenceContext reference, final Table table,
                                            final FrostlakeParser.BooleanExprContext where) {
        final String written = ParseTreeText.getOriginalText(where);
        if (!(where instanceof FrostlakeParser.AndExprContext)) {
            return written;
        }
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(where, conjuncts);
        final Map<String, Table> aliases = new LinkedHashMap<>();
        aliases.put(reference.aliasName() != null ? reference.aliasName().getText()
            : reference.nonJoinKeywordIdentifier() != null ? reference.nonJoinKeywordIdentifier().getText()
            : table.getName(), table);
        final CountStatisticsBound bound = new CountStatisticsBound(executor, select, table, aliases, null, false,
            null);
        String kept = null;
        int unproven = 0;
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            final String text = ParseTreeText.getOriginalText(conjunct);
            final Boolean verdict;
            try {
                verdict = bound.verdictOver(ExpressionEvaluator.parse(text));
            } catch (final RuntimeException unreadable) {
                return written;
            }
            if (!Boolean.TRUE.equals(verdict)) {
                kept = text;
                unproven++;
            }
        }
        return unproven == 1 ? kept : written;
    }

    private static void collectConjuncts(final FrostlakeParser.BooleanExprContext condition,
                                         final List<FrostlakeParser.BooleanExprContext> into) {
        if (condition instanceof FrostlakeParser.AndExprContext) {
            for (final FrostlakeParser.BooleanExprContext operand
                    : ((FrostlakeParser.AndExprContext) condition).booleanExpr()) {
                collectConjuncts(operand, into);
            }
            return;
        }
        into.add(condition);
    }

    /**
     * A query over a table of one row, every item read from that row and the WHERE true of it, as the plan folds it:
     * the row's values over as many of a GENERATOR's rows as the LIMIT and its OFFSET leave of the one,
     * {@code (SELECT 12345 AS "V" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))}, an item computed over the row
     * as its value, an ORDER BY dropped and a DISTINCT kept; grouped, the table is read still and the keys are the
     * row's values, {@code (SELECT 12345 AS "V" FROM SRC AS SRC GROUP BY 12345)} (all live-verified). Null for any
     * other query.
     */
    private static String pinnedRow(final QueryExecutor executor, final ExpressionEvaluator printer,
                                    final FrostlakeParser.SelectStatementContext query,
                                    final FrostlakeParser.SelectClauseContext select, final Row row,
                                    final Table table, final String relation) {
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(select);
        if (select.havingClause() != null || where != null && !Boolean.TRUE.equals(printer.evaluate(
                ExpressionEvaluator.parse(ParseTreeText.getOriginalText(where.booleanExpr())), row))) {
            return null;
        }
        long kept = 1;
        if (query.limitClause() != null) {
            final FrostlakeParser.LimitClauseContext limit = query.limitClause();
            final String count = limit.getChild(1).getText();
            final String offset = limit.OFFSET() != null ? limit.getChild(3).getText() : "0";
            if (!count.matches("[0-9]{1,18}") || !offset.matches("[0-9]{1,18}")) {
                return null;
            }
            kept = Math.max(0, Math.min(1 - Long.parseLong(offset), Long.parseLong(count)));
        }
        final List<FrostlakeParser.SelectItemContext> items = select.selectList().selectItem();
        final StringBuilder text = new StringBuilder(select.DISTINCT() != null ? "(SELECT DISTINCT " : "(SELECT ");
        for (int i = 0; i < items.size(); i++) {
            final FrostlakeParser.ExprItemContext item = (FrostlakeParser.ExprItemContext) items.get(i);
            if (UnreadColumnPruning.containsAggregate(item.booleanExpr(), executor.getFunctionRegistry())) {
                return null;
            }
            final String itemText = ParseTreeText.getOriginalText(item.booleanExpr());
            final String literal = literalOf(printer.evaluate(ExpressionEvaluator.parse(itemText), row));
            if (literal == null) {
                return null;
            }
            final String alias = SelectItemAccessors.getItemAlias(item);
            text.append(i > 0 ? ", " : "").append(literal).append(" AS \"")
                .append(alias != null ? alias : itemText.toUpperCase(Locale.ROOT)).append('"');
        }
        if (select.groupByClause() == null) {
            return text.append(" FROM TABLE (GENERATOR(ROWCOUNT => ").append(kept).append(", rowCount => ")
                .append(kept).append(")))").toString();
        }
        if (select.DISTINCT() != null || query.limitClause() != null) {
            return null;
        }
        final StringBuilder keys = new StringBuilder();
        for (final FrostlakeParser.GroupByElementContext element : select.groupByClause().groupByElement()) {
            if (element.booleanExpr() == null
                    || !(valueOf(element.booleanExpr()) instanceof FrostlakeParser.QualifiedNameExprContext)) {
                return null;
            }
            final String literal = literalOf(printer.evaluate(
                ExpressionEvaluator.parse(ParseTreeText.getOriginalText(element.booleanExpr())), row));
            if (literal == null) {
                return null;
            }
            keys.append(keys.length() > 0 ? ", " : "").append(literal);
        }
        return text.append(" FROM ").append(table.getName().toUpperCase(Locale.ROOT)).append(" AS ").append(relation)
            .append(" GROUP BY ").append(keys).append(')').toString();
    }

    /** A value as the plan prints it as a literal: a number by its digits, a text in quotes; null for any other. */
    private static String literalOf(final Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        return value instanceof String ? "'" + ((String) value).replace("'", "\\'") + "'" : null;
    }

    /**
     * An item as the plan computes it, or null for a sum or an average of a shape not known here. Over a table of one
     * row read whole, a whole number's SUM adds the row's value and an AVG divides that sum by one,
     * {@code SCALED_ROUND_INT_DIVIDE(CAST(SUM(12345) AS NUMBER(38,0)), 1)} (live-verified).
     */
    private static String computed(final ExpressionEvaluator printer, final Table table, final String relation,
                                   final FrostlakeParser.BooleanExprContext item, final Row only) {
        final ParseTree value = valueOf(item);
        if (value instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) value;
            final String name = call.functionName().getText().toUpperCase(Locale.ROOT);
            final boolean single = call.DISTINCT() == null && call.overClause() == null
                && call.functionArgList() != null && call.functionArgList().functionArg().size() == 1
                && call.functionArgList().functionArg(0).booleanExpr() != null;
            final NumericType type = single
                ? exactColumn(table, relation, call.functionArgList().functionArg(0).booleanExpr()) : null;
            if (type != null && ("SUM".equals(name) || "AVG".equals(name) && type.getScale() == 0)) {
                // A SUM adds an exact number as its column's NUMBER, a whole one as NUMBER(38,0); an AVG of a whole
                // number divides that sum by its count.
                final String argument = printer.boundPlannedText(ExpressionEvaluator.parse(
                    ParseTreeText.getOriginalText(call.functionArgList().functionArg(0).booleanExpr())));
                final String pinned = only == null || type.getScale() != 0 ? null : literalOf(printer.evaluate(
                    ExpressionEvaluator.parse(
                        ParseTreeText.getOriginalText(call.functionArgList().functionArg(0).booleanExpr())), only));
                if (pinned != null) {
                    final String sum = "SUM(" + pinned + ")";
                    return "SUM".equals(name) ? sum : "SCALED_ROUND_INT_DIVIDE(CAST(" + sum + " AS NUMBER(38,0)), 1)";
                }
                final String sum = "SUM(CAST(" + argument + " AS " + (type.getScale() == 0 ? "NUMBER(38,0)"
                    : "NUMBER(" + type.getPrecision() + "," + type.getScale() + ")") + "))";
                return "SUM".equals(name) ? sum : "SCALED_ROUND_INT_DIVIDE(CAST(" + sum
                    + " AS NUMBER(38,0)), CAST(COUNT(" + argument + ") AS NUMBER(18,0)))";
            }
        }
        if (sumsOrAverages(value)) {
            return null;
        }
        return printer.boundPlannedText(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(item)));
    }

    /** Whether every item is a literal, reading no column. */
    private static boolean constantsAlone(final List<FrostlakeParser.SelectItemContext> items) {
        for (final FrostlakeParser.SelectItemContext item : items) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return false;
            }
            final ParseTree value = valueOf(((FrostlakeParser.ExprItemContext) item).booleanExpr());
            if (!(value instanceof FrostlakeParser.LiteralExprContext)) {
                return false;
            }
        }
        return true;
    }

    /** The exact NUMBER of the column of the query's relation a value names, or null. */
    private static NumericType exactColumn(final Table table, final String relation,
                                           final FrostlakeParser.BooleanExprContext argument) {
        final ParseTree value = valueOf(argument);
        if (!(value instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(
            ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName());
        if (parts.length > 2 || parts.length == 2 && !relation.equalsIgnoreCase(parts[0])) {
            return null;
        }
        final TableColumn column = table.findColumn(parts[parts.length - 1]);
        final DataType type = column != null ? column.getDataType() : null;
        return type instanceof NumericType && !NumericType.isApproximate(type) && !NumericType.isDecfloat(type)
            ? (NumericType) type : null;
    }

    /** Whether a value sums or averages anything, outside any query nested in it. */
    private static boolean sumsOrAverages(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final String name = ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText()
                .toUpperCase(Locale.ROOT);
            if ("SUM".equals(name) || "AVG".equals(name)) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (sumsOrAverages(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static ParseTree valueOf(final ParseTree written) {
        return written instanceof FrostlakeParser.ValueExprContext
            ? ((FrostlakeParser.ValueExprContext) written).expression() : written;
    }
}
