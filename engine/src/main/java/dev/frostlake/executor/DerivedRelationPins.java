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
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DecimalText;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Whether the statistics pin a column of a derived relation - a subquery's or a CTE's - to one value on every row by
 * what the query making it computes, which the account reads a GENERATOR bound over it as (all live-verified). Over
 * {@code g1} holding 1, {@code g2} holding 2 and 3 and {@code g3} holding 5 twice:
 *
 * <ul>
 *   <li>an expression over pinned columns and literals is pinned: {@code (SELECT n + 1 AS n FROM g1) d} bounds
 *       {@code ROWCOUNT => d.n} at 2, {@code n * 2} and {@code ABS(n)} alike, {@code n + 1} over g3 bounds each of its
 *       two rows at 6, and a FROM-less select's items are pinned, {@code (SELECT 2 AS n, 3 AS m) d};</li>
 *   <li>a select of MAX, MIN or COUNT over a whole table is one row the statistics answer, {@code MAX(n)} of g2 being
 *       3 and {@code COUNT(*) + 1} of g3 being 3, where SUM, AVG, a WHERE beside the aggregate or a GROUP BY making
 *       several groups leaves the column varying;</li>
 *   <li>DISTINCT and GROUP BY keep a pinned column pinned, {@code SELECT DISTINCT n FROM g3} being 5;</li>
 *   <li>a UNION, with or without ALL, of branches pinned to one value is pinned to it, {@code 4 UNION ALL 4} bounding
 *       each of its two rows at 4 and {@code n FROM g1 UNION ALL n FROM g1} each of its two at 1, where
 *       {@code 4 UNION ALL 5} varies;</li>
 *   <li>a CTE is read as its body, a CTE over another CTE as that one's.</li>
 * </ul>
 *
 * <p>A call the account does not fold while it judges a bound, {@code LENGTH('ab')}, leaves the column varying.
 */
final class DerivedRelationPins {

    /** How many relations deep a column is followed. */
    private static final int MAX_DEPTH = 16;

    /** The answer for a column the statistics do not pin. */
    private static final Object VARYING = new Object();

    /** The answer for a pinned column whose one value is not worked out here. */
    private static final Object UNKNOWN = new Object();

    /** The aggregates whose answer over a whole table the statistics hold. */
    private static final Set<String> ANSWERED = Set.of("MAX", "MIN", "COUNT");

    private final QueryExecutor executor;
    private final StoredColumnValues values;

    /**
     * @param executor the executor whose catalog and storage the relations are read from
     * @param values   the stored columns' summaries
     */
    DerivedRelationPins(final QueryExecutor executor, final StoredColumnValues values) {
        this.executor = executor;
        this.values = values;
    }

    /**
     * Whether a derived relation's column holds one value on every row.
     *
     * @param node      a node of the query whose FROM holds the relation
     * @param qualifier the name the relation answers to, upper-cased
     * @param column    the column's name
     * @return true where the statistics pin it
     */
    boolean pins(final ParseTree node, final String qualifier, final String column) {
        final FrostlakeParser.TableReferenceContext item = fromItem(node, qualifier);
        return item != null && sourceValue(item, column, 0) != VARYING;
    }

    /**
     * The FROM item a name answers to, in the nearest FROM around a node that holds one so named.
     *
     * @param node      the node
     * @param qualifier the name, upper-cased
     * @return the item, or null
     */
    static FrostlakeParser.TableReferenceContext fromItem(final ParseTree node, final String qualifier) {
        for (ParseTree at = node; at != null; at = at.getParent()) {
            if (!(at instanceof FrostlakeParser.TableExpressionContext)) {
                continue;
            }
            final FrostlakeParser.TableExpressionContext from = (FrostlakeParser.TableExpressionContext) at;
            for (final FrostlakeParser.TableReferenceContext item : fromItems(from)) {
                if (qualifier.equals(GeneratorBounds.qualifierOf(item))) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * The items of a FROM in the order written, those a join adds included.
     *
     * @param from the FROM
     * @return the items
     */
    static List<FrostlakeParser.TableReferenceContext> fromItems(final FrostlakeParser.TableExpressionContext from) {
        final List<FrostlakeParser.TableReferenceContext> items = new ArrayList<>();
        for (int i = 0; i < from.getChildCount(); i++) {
            final ParseTree child = from.getChild(i);
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                items.add((FrostlakeParser.TableReferenceContext) child);
            } else if (child instanceof FrostlakeParser.JoinClauseContext
                    && ((FrostlakeParser.JoinClauseContext) child).tableReference() != null) {
                items.add(((FrostlakeParser.JoinClauseContext) child).tableReference());
            }
        }
        return items;
    }

    /**
     * The CTE a one-part name reads where it stands: the nearest WITH around it defining one so named.
     *
     * @param node the node the name is read at
     * @param name the name
     * @return the CTE, or null
     */
    static FrostlakeParser.CteDefinitionContext cteNamed(final ParseTree node, final String name) {
        for (ParseTree at = node; at != null; at = at.getParent()) {
            if (!(at instanceof FrostlakeParser.SelectStatementContext)
                    || ((FrostlakeParser.SelectStatementContext) at).withClause() == null) {
                continue;
            }
            for (final FrostlakeParser.CteDefinitionContext cte
                    : ((FrostlakeParser.SelectStatementContext) at).withClause().cteDefinition()) {
                if (name.equalsIgnoreCase(ParseTreeText.namePartText(cte.nameStartPart()))) {
                    return cte;
                }
            }
        }
        return null;
    }

    /** The one value a FROM item's column holds, UNKNOWN where it is not worked out, or VARYING. */
    private Object sourceValue(final FrostlakeParser.TableReferenceContext item, final String column, final int depth) {
        final FrostlakeParser.TableSourceContext source = item.tableSource();
        if (depth > MAX_DEPTH || source == null || item.pivotClause() != null || item.unpivotClause() != null
                || item.sampleClause() != null || item.matchRecognizeClause() != null) {
            return VARYING;
        }
        if (source.selectStatement() != null) {
            return queryValue(source.selectStatement(), column, depth + 1);
        }
        if (source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return VARYING;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        final FrostlakeParser.CteDefinitionContext cte = parts.length == 1 ? cteNamed(item, parts[0]) : null;
        if (cte != null) {
            return cteValue(cte, column, depth + 1);
        }
        final Table table = catalogTable(ParseTreeText.getQualifiedName(source.tableQualifiedName()));
        if (table == null || !table.hasColumn(column)) {
            return VARYING;
        }
        final String declared = table.getColumn(column).getName();
        if (values.holdsOneValue(table, declared)) {
            final Object one = values.oneValueOf(table, declared);
            return one == null ? UNKNOWN : one;
        }
        return values.holdsOnlyNulls(table, declared) ? UNKNOWN : VARYING;
    }

    private Table catalogTable(final String written) {
        try {
            final Table table = executor.getCatalog().resolveTable(written);
            return table != null && table.isCatalogResident() ? table : null;
        } catch (final RuntimeException noTable) {
            return null;
        }
    }

    private Object cteValue(final FrostlakeParser.CteDefinitionContext cte, final String column, final int depth) {
        if (cte.columnListOptional() == null) {
            return queryValue(cte.selectStatement(), column, depth);
        }
        final List<FrostlakeParser.NamePartContext> names = cte.columnListOptional().namePart();
        for (int i = 0; i < names.size(); i++) {
            if (column.equalsIgnoreCase(ParseTreeText.namePartText(names.get(i)))) {
                return queryValueAt(cte.selectStatement(), i, depth);
            }
        }
        return VARYING;
    }

    /** A query's column by its name: the item so named, or the column a lone star passes through. */
    private Object queryValue(final FrostlakeParser.SelectStatementContext query, final String column,
                              final int depth) {
        final FrostlakeParser.SelectOperandContext first = query.selectOperand(0);
        if (first.selectStatement() != null && query.selectOperand().size() == 1) {
            return queryValue(first.selectStatement(), column, depth + 1);
        }
        final FrostlakeParser.SelectClauseContext select = first.selectClause();
        if (select == null) {
            return VARYING;
        }
        final List<FrostlakeParser.SelectItemContext> items = select.selectList().selectItem();
        if (items.size() == 1 && items.get(0) instanceof FrostlakeParser.StarItemContext
                && ((FrostlakeParser.StarItemContext) items.get(0)).starModifier().isEmpty()
                && query.selectOperand().size() == 1 && singleSource(select) != null && plainSelect(select)
                && select.groupByClause() == null && select.DISTINCT() == null) {
            return sourceValue(singleSource(select), column, depth + 1);
        }
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof FrostlakeParser.ExprItemContext)) {
                return VARYING;
            }
            if (column.equalsIgnoreCase(labelOf((FrostlakeParser.ExprItemContext) items.get(i)))) {
                return queryValueAt(query, i, depth);
            }
        }
        return VARYING;
    }

    /** A query's column by its place: one value where every branch of a UNION holds the same one. */
    private Object queryValueAt(final FrostlakeParser.SelectStatementContext query, final int index, final int depth) {
        final List<FrostlakeParser.SelectOperandContext> operands = query.selectOperand();
        if (operands.size() == 1) {
            return operandValueAt(operands.get(0), index, depth);
        }
        for (final FrostlakeParser.SetOperatorContext operator : query.setOperator()) {
            if (operator.UNION() == null || operator.BY() != null) {
                return VARYING;
            }
        }
        Object common = VARYING;
        for (int i = 0; i < operands.size(); i++) {
            final Object value = operandValueAt(operands.get(i), index, depth);
            if (value == VARYING || value == UNKNOWN || i > 0 && !same(common, value)) {
                return VARYING;
            }
            common = value;
        }
        return common;
    }

    private Object operandValueAt(final FrostlakeParser.SelectOperandContext operand, final int index,
                                  final int depth) {
        if (depth > MAX_DEPTH) {
            return VARYING;
        }
        if (operand.selectStatement() != null) {
            return queryValueAt(operand.selectStatement(), index, depth + 1);
        }
        final FrostlakeParser.SelectClauseContext select = operand.selectClause();
        if (select == null || !plainSelect(select)) {
            return VARYING;
        }
        final List<FrostlakeParser.SelectItemContext> items = select.selectList().selectItem();
        for (final FrostlakeParser.SelectItemContext item : items) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return VARYING;
            }
        }
        if (index < 0 || index >= items.size()) {
            return VARYING;
        }
        final ParserRuleContext expression = ((FrostlakeParser.ExprItemContext) items.get(index)).booleanExpr();
        if (select.tableExpression() == null) {
            return ParseTreeText.getWhereClause(select) != null || select.groupByClause() != null ? VARYING
                : constantValue(expression);
        }
        final FrostlakeParser.TableReferenceContext source = singleSource(select);
        if (source == null) {
            return VARYING;
        }
        if (select.groupByClause() != null) {
            return groupedValue(select, expression, source, depth);
        }
        if (holdsAggregate(select.selectList())) {
            // One row the statistics answer: an aggregate over the whole table, no WHERE beside it.
            return ParseTreeText.getWhereClause(select) == null && answeredAggregates(expression, source)
                ? UNKNOWN : VARYING;
        }
        return projectedValue(expression, source, depth);
    }

    /** Whether a select stands without TOP, HAVING, QUALIFY or a hierarchy, which the rules here do not read. */
    private static boolean plainSelect(final FrostlakeParser.SelectClauseContext select) {
        return select.topClause() == null && select.havingClause() == null && select.qualifyClause() == null
            && select.connectByClause() == null && select.intoClause() == null;
    }

    /** The FROM's one item where it holds one and no join, or null. */
    private static FrostlakeParser.TableReferenceContext singleSource(
            final FrostlakeParser.SelectClauseContext select) {
        final FrostlakeParser.TableExpressionContext from = select.tableExpression();
        return from == null || from.tableReference().size() != 1 || !from.joinClause().isEmpty() ? null
            : from.tableReference(0);
    }

    /**
     * The name a select item answers to: its alias, or the column it reads.
     *
     * @param item the item
     * @return the name, or null for an unnamed expression
     */
    static String labelOf(final FrostlakeParser.ExprItemContext item) {
        final String alias = SelectItemAccessors.getItemAlias(item);
        if (alias != null) {
            return alias;
        }
        final String[] parts = columnParts(item.booleanExpr());
        return parts == null ? null : parts[parts.length - 1];
    }

    /** The parts of the column an expression is, or null where it is no column. */
    private static String[] columnParts(final ParseTree written) {
        final ParseTree node = valueOf(written);
        return node instanceof FrostlakeParser.QualifiedNameExprContext
            ? ParseTreeText.qualifiedNameParts(((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName())
            : null;
    }

    /** A FROM-less item: the value it folds to before any row is read, or VARYING; a NULL is no pinned value. */
    private Object constantValue(final ParserRuleContext expression) {
        if (readsAColumn(expression) || holdsQuery(expression) || !GeneratorBounds.foldsItsCalls(expression)) {
            return VARYING;
        }
        try {
            final ExpressionEvaluator evaluator = new ExpressionEvaluator(null, executor.getFunctionRegistry(),
                executor.getCatalog(), executor);
            final Object value = evaluator.evaluate(ExpressionEvaluator.parse(
                ParseTreeText.getOriginalText(expression)), null);
            return value == null ? VARYING : value;
        } catch (final RuntimeException unfoldable) {
            return VARYING;
        }
    }

    /** A grouped select's item: a grouping column keeps the value its source pins it to. */
    private Object groupedValue(final FrostlakeParser.SelectClauseContext select, final ParserRuleContext expression,
                                final FrostlakeParser.TableReferenceContext source, final int depth) {
        final String[] parts = columnParts(expression);
        if (parts == null || !readsSource(parts, source)) {
            return VARYING;
        }
        final String column = parts[parts.length - 1];
        final FrostlakeParser.GroupByClauseContext grouping = select.groupByClause();
        boolean grouped = grouping.ALL() != null;
        for (final FrostlakeParser.GroupByElementContext element : grouping.groupByElement()) {
            final String[] key = element.booleanExpr() == null ? null : columnParts(element.booleanExpr());
            grouped |= key != null && readsSource(key, source) && column.equalsIgnoreCase(key[key.length - 1]);
        }
        return grouped ? sourceValue(source, column, depth + 1) : VARYING;
    }

    /** A projected item: pinned where every column it reads is; its value where it is one such column. */
    private Object projectedValue(final ParserRuleContext expression,
                                  final FrostlakeParser.TableReferenceContext source, final int depth) {
        if (holdsQuery(expression) || !GeneratorBounds.foldsItsCalls(expression) || callsWindow(expression)) {
            return VARYING;
        }
        final List<FrostlakeParser.QualifiedNameExprContext> columns = new ArrayList<>();
        collectColumns(expression, columns);
        Object only = UNKNOWN;
        for (final FrostlakeParser.QualifiedNameExprContext reference : columns) {
            final String[] parts = ParseTreeText.qualifiedNameParts(reference.qualifiedName());
            if (!readsSource(parts, source)) {
                return VARYING;
            }
            final Object value = sourceValue(source, parts[parts.length - 1], depth + 1);
            if (value == VARYING) {
                return VARYING;
            }
            only = value;
        }
        return columns.size() == 1 && columnParts(expression) != null ? only : UNKNOWN;
    }

    /** Whether an aggregate query's item reads its table through the aggregates the statistics answer alone. */
    private boolean answeredAggregates(final ParseTree node, final FrostlakeParser.TableReferenceContext source) {
        if (node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext star = (FrostlakeParser.FunctionCallStarExprContext) node;
            return star.functionName() != null && "COUNT".equalsIgnoreCase(star.functionName().getText())
                && star.DISTINCT() == null && star.starQualifiedName() == null && star.starArgumentModifier() == null
                && wholeTable(source);
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final String name = call.functionName().getText().toUpperCase(Locale.ROOT);
            if (call.overClause() != null) {
                return false;
            }
            if (executor.getFunctionRegistry().hasAggregateFunction(name)) {
                if (!ANSWERED.contains(name) || call.DISTINCT() != null || call.functionArgList() == null
                        || call.functionArgList().functionArg().size() != 1
                        || call.functionArgList().functionArg(0).booleanExpr() == null) {
                    return false;
                }
                final String[] parts = columnParts(call.functionArgList().functionArg(0).booleanExpr());
                return parts != null && readsSource(parts, source) && wholeTable(source);
            }
            if (!GeneratorBounds.foldsItsCalls(call)) {
                return false;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!answeredAggregates(node.getChild(i), source)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a FROM item is a catalog table read whole, whose statistics answer an aggregate over it. */
    private boolean wholeTable(final FrostlakeParser.TableReferenceContext source) {
        final FrostlakeParser.TableSourceContext table = source.tableSource();
        if (table == null || table.tableQualifiedName() == null || table.timeTravelClause() != null
                || source.sampleClause() != null) {
            return false;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(table.tableQualifiedName());
        return (parts.length > 1 || cteNamed(source, parts[0]) == null)
            && catalogTable(ParseTreeText.getQualifiedName(table.tableQualifiedName())) != null;
    }

    /** Whether a column's qualifier, where it has one, names the FROM's one item. */
    private static boolean readsSource(final String[] parts, final FrostlakeParser.TableReferenceContext source) {
        return parts.length == 1
            || parts.length == 2 && parts[0].toUpperCase(Locale.ROOT).equals(GeneratorBounds.qualifierOf(source));
    }

    /** Whether a select list holds an aggregate call outside the queries nested in it and any window. */
    private boolean holdsAggregate(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            if (call.overClause() == null && executor.getFunctionRegistry().hasAggregateFunction(
                    call.functionName().getText().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsAggregate(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean callsWindow(final ParseTree node) {
        if (node instanceof FrostlakeParser.OverClauseContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (callsWindow(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static void collectColumns(final ParseTree node,
                                       final List<FrostlakeParser.QualifiedNameExprContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.add((FrostlakeParser.QualifiedNameExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectColumns(node.getChild(i), into);
        }
    }

    private static boolean readsAColumn(final ParseTree node) {
        final List<FrostlakeParser.QualifiedNameExprContext> columns = new ArrayList<>();
        collectColumns(node, columns);
        return !columns.isEmpty();
    }

    private static boolean holdsQuery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsQuery(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether two pinned values are one value: numbers by their value, anything else by equality. */
    private static boolean same(final Object first, final Object second) {
        if (first instanceof Number && second instanceof Number) {
            return DecimalText.parse(first.toString()).compareTo(DecimalText.parse(second.toString())) == 0;
        }
        return first == null ? second == null : first.equals(second);
    }

    private static ParseTree valueOf(final ParseTree written) {
        ParseTree node = written;
        while (true) {
            if (node instanceof FrostlakeParser.ValueExprContext) {
                node = ((FrostlakeParser.ValueExprContext) node).expression();
            } else if (node instanceof FrostlakeParser.ParenExprContext) {
                node = ((FrostlakeParser.ParenExprContext) node).booleanExpr();
            } else {
                return node;
            }
        }
    }
}
