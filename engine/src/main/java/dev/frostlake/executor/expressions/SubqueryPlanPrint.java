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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SelectItemAccessors;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The re-print a refusal's echo gives a membership test over a SUBQUERY. The account names
 * {@code x IN (SELECT …)} as the comparison it plans, {@code x = ANY(SELECT …)}, and
 * {@code x NOT IN (SELECT …)} as {@code x != ALL(SELECT …)}, and re-prints the SELECT from its plan:
 *
 * <pre>
 *   'b' IN (SELECT 'abc')              'b' = ANY(SELECT 'abc' AS "'ABC'" FROM (VALUES (null)) DUAL)
 *   'b' IN (SELECT g FROM rt r)        'b' = ANY(SELECT R.G AS "G" FROM RT AS R)
 *   … FROM rt, re                      … FROM RT AS RT INNER JOIN RE AS RE
 *   … FROM rt JOIN re ON rt.n = re.i   … FROM RT AS RT INNER JOIN RE AS RE ON (RT.N = RE.I)
 *   … WHERE b AND n > 1                … WHERE (CAST(RT.B AS BOOLEAN)) AND (RT.N > 1)
 *   … GROUP BY g HAVING g = 'x'        … GROUP BY RT.G  HAVING RT.G = 'x'
 *   … ORDER BY g LIMIT 1               …  ORDER BY G ASC NULLS LAST LIMIT 1 OFFSET 0
 *   1 IN (SELECT 1.5)                  (CAST(1 AS NUMBER(2,1))) = ANY(SELECT 1.5 AS "1.5" FROM …)
 * </pre>
 *
 * <p>Every item is named: by its alias ({@code AS x} is {@code "X"}, {@code AS "q"} keeps its case), a
 * column by its own name, anything else by its source text upper-cased. The clauses come in the plan's
 * order — WHERE, GROUP BY, ORDER BY, HAVING, QUALIFY, LIMIT — with two spaces before ORDER BY and before
 * HAVING. An ORDER BY key naming an output column prints that column's bare name, where a GROUP BY key
 * prints the input column it reads; an ordinal is the output column it counts to. TOP, FETCH and a
 * LIMIT without an OFFSET all print {@code LIMIT n OFFSET m}. The subject and the one item meet in a
 * single type: an untyped NULL item is the subject family's typed null, and of two exact numbers with
 * different scales the lower-scale side is rescaled to the higher scale. In an invalid-type sentence
 * the same re-print speaks that sentence's vocabulary: {@code (VALUES (NULL))},
 * {@code BOOLEAN_TO_ROWINDEX(RT.B)}, {@code FIXED_TO_FIXED(RT.N AS NUMBER(6,1)[UNKNOWN])}.
 *
 * <p>Only what was measured is modelled. A set operation, a WITH, a derived table, a NATURAL, USING or
 * ASOF join, a correlated reference, a pair of families that meet through any other conversion and
 * every other shape answer null, and the membership test then prints as written.
 */
final class SubqueryPlanPrint {

    private static final int MAX_PRECISION = 38;
    /** The name the FROM-less relation goes by, which a refusal's echo never prints. */
    private static final String FROMLESS_RELATION = "DUMMY";

    private final ExpressionEvaluatorVisitor outer;
    private final StrictPrintMode mode;

    /** The relations the FROM clause names, keyed as a refusal's echo qualifies their columns. */
    private final Map<String, Table> aliasToTable = new LinkedHashMap<>();
    private final List<Table> tables = new ArrayList<>();
    /** Each relation's name as a written qualifier carries it: its alias, else its table's name. */
    private final Set<String> qualifiers = new HashSet<>();

    SubqueryPlanPrint(final ExpressionEvaluatorVisitor outer, final StrictPrintMode mode) {
        this.outer = outer;
        this.mode = mode;
    }

    /**
     * The membership test as the plan re-prints it, or null when its subquery is a shape this does not
     * model.
     *
     * @param in the membership test, over a subquery
     * @param outerPrinter the printer the test's subject is printed by
     * @return {@code x = ANY(SELECT …)}, {@code x != ALL(SELECT …)}, or null
     */
    String membership(final InExpression in, final StrictMessagePrinter outerPrinter) {
        final QueryExecutor queryExecutor = outer.getQueryExecutor();
        if (queryExecutor == null) {
            return null;
        }
        try {
            final FrostlakeParser.SelectStatementContext statement =
                soleStatement(parsed(in.getSubquery().getSubquery()));
            return statement == null ? null : planned(statement, in, outerPrinter, queryExecutor);
        } catch (final RuntimeException notModelled) {
            return null;
        }
    }

    private String planned(final FrostlakeParser.SelectStatementContext statement, final InExpression in,
                           final StrictMessagePrinter outerPrinter, final QueryExecutor queryExecutor) {
        final FrostlakeParser.SelectClauseContext select = statement.selectOperand(0).selectClause();
        if (select.connectByClause() != null) {
            return null;
        }
        final Catalog catalog = queryExecutor.getCatalog();
        final List<String> segments = new ArrayList<>();
        final List<FrostlakeParser.BooleanExprContext> conditions = new ArrayList<>();
        if (!collectFrom(select.tableExpression(), catalog, segments, conditions)) {
            return null;
        }
        final String limit = limitText(statement, select);
        if (limit == null) {
            return null;
        }
        final ExpressionEvaluatorVisitor scope = scope(catalog, queryExecutor);
        final StrictMessagePrinter printer = new StrictMessagePrinter(scope, mode);
        final List<String> bareColumns = new ArrayList<>();
        final List<String> writtenQualifiers = new ArrayList<>();
        printer.watchColumns(bareColumns, writtenQualifiers);

        final List<Expression> items = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!collectItem(item, items, names)) {
                return null;
            }
        }
        if (items.size() != 1) {
            return null;
        }
        final String[] meeting = meeting(in.getValue(), items.get(0), outerPrinter, printer);
        if (meeting == null) {
            return null;
        }

        final StringBuilder from = new StringBuilder();
        if (segments.isEmpty()) {
            from.append("(VALUES (").append(mode == StrictPrintMode.CONVERSION ? "NULL" : "null").append(")) DUAL");
        }
        for (int i = 0; i < segments.size(); i++) {
            from.append(segments.get(i));
            if (conditions.get(i) != null) {
                from.append(" ON (")
                    .append(printer.conditionText(new ExpressionAstBuilder().build(conditions.get(i)), false))
                    .append(')');
            }
        }
        final String where = select.whereClause() == null ? ""
            : " WHERE " + printer.conditionText(new ExpressionAstBuilder().build(select.whereClause().booleanExpr()), false);
        final String group = groupText(select.groupByClause(), items, names, printer);
        if (group == null) {
            return null;
        }
        final String having = select.havingClause() == null ? ""
            : "  HAVING " + printer.conditionText(new ExpressionAstBuilder().build(select.havingClause().booleanExpr()), false);
        final String qualify = select.qualifyClause() == null ? ""
            : " QUALIFY " + printer.conditionText(new ExpressionAstBuilder().build(select.qualifyClause().booleanExpr()), false);
        if (!bareColumns.isEmpty() || !allKnown(writtenQualifiers)) {
            return null;
        }
        final String order = orderText(statement.orderByClause(), scope, names);
        if (order == null) {
            return null;
        }

        final StringBuilder text = new StringBuilder("SELECT ");
        if (select.DISTINCT() != null) {
            text.append("DISTINCT ");
        }
        text.append(meeting[1]).append(" AS \"").append(names.get(0)).append('"')
            .append(" FROM ").append(from)
            .append(where).append(group).append(order).append(having).append(qualify).append(limit);
        return meeting[0] + (in.isNot() ? " != ALL(" : " = ANY(") + text + ")";
    }

    /**
     * The subject and the item as they meet in one type, {@code [subject, item]}, or null for a pair of
     * families this does not model.
     */
    private String[] meeting(final Expression subject, final Expression item,
                             final StrictMessagePrinter outerPrinter, final StrictMessagePrinter printer) {
        final DataType subjectType = outerPrinter.typeOf(subject);
        if (isUntypedNull(item)) {
            final String family = subjectType instanceof StringType ? "TEXT"
                : isExactNumber(subjectType) ? "FIXED" : null;
            return family == null ? null : new String[] {outerPrinter.operandText(subject),
                "SYSTEM$NULL_TO_" + family + (mode == StrictPrintMode.CONVERSION ? "(NULL)" : "(null)")};
        }
        final DataType itemType = printer.typeOf(item);
        final String printedItem = item.accept(printer);
        if (isExactNumber(subjectType) && isExactNumber(itemType)) {
            final NumericType s = (NumericType) subjectType;
            final NumericType t = (NumericType) itemType;
            if (s.getScale() == t.getScale()) {
                return new String[] {outerPrinter.operandText(subject), printedItem};
            }
            final int scale = Math.max(s.getScale(), t.getScale());
            final int integerDigits = Math.max(s.getPrecision() - s.getScale(), t.getPrecision() - t.getScale());
            final String target = "NUMBER(" + Math.min(MAX_PRECISION, integerDigits + scale) + "," + scale + ")";
            return t.getScale() < s.getScale()
                ? new String[] {outerPrinter.operandText(subject), printer.rescaledText(printedItem, target)}
                : new String[] {"(" + outerPrinter.rescaledText(subject.accept(outerPrinter), target) + ")", printedItem};
        }
        if (sameFamily(subjectType, itemType)) {
            return new String[] {outerPrinter.operandText(subject), printedItem};
        }
        return null;
    }

    private static boolean sameFamily(final DataType first, final DataType second) {
        if (first == null || second == null) {
            return false;
        }
        if (first instanceof StringType || first instanceof BooleanType) {
            return first.getClass() == second.getClass()
                || (first instanceof StringType && second instanceof StringType);
        }
        final String name = SqlTypeNames.canonical(first);
        return name != null && name.equals(SqlTypeNames.canonical(second));
    }

    /** The FROM clause's relations and joins into {@code segments}, each join's ON beside it; false when not modelled. */
    private boolean collectFrom(final FrostlakeParser.TableExpressionContext from, final Catalog catalog,
                                final List<String> segments,
                                final List<FrostlakeParser.BooleanExprContext> conditions) {
        if (from == null) {
            return true;
        }
        for (int i = 0; i < from.getChildCount(); i++) {
            final ParseTree child = from.getChild(i);
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                final String relation = relation((FrostlakeParser.TableReferenceContext) child, catalog);
                if (relation == null) {
                    return false;
                }
                // A comma join is planned as the inner join it is, with no condition of its own.
                segments.add(segments.isEmpty() ? relation : " INNER JOIN " + relation);
                conditions.add(null);
            } else if (child instanceof FrostlakeParser.JoinClauseContext
                    && !join((FrostlakeParser.JoinClauseContext) child, catalog, segments, conditions,
                        i == from.getChildCount() - 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * One JOIN: INNER and CROSS as {@code INNER JOIN}, LEFT and FULL as their OUTER spelling, and a RIGHT
     * join of two relations as the LEFT join of the pair swapped.
     */
    private boolean join(final FrostlakeParser.JoinClauseContext join, final Catalog catalog,
                         final List<String> segments, final List<FrostlakeParser.BooleanExprContext> conditions,
                         final boolean last) {
        if (join.NATURAL() != null || join.ASOF() != null || join.DIRECTED() != null || join.LATERAL() != null
                || join.asofMatchCondition() != null || join.USING() != null) {
            return false;
        }
        final String relation = relation(join.tableReference(), catalog);
        if (relation == null) {
            return false;
        }
        final FrostlakeParser.JoinTypeContext type = join.joinType();
        final FrostlakeParser.BooleanExprContext on = join.booleanExpr();
        if (type != null && type.RIGHT() != null) {
            if (segments.size() != 1 || !last || on == null) {
                return false;
            }
            final String left = segments.get(0);
            segments.set(0, relation);
            segments.add(" LEFT OUTER JOIN " + left);
            conditions.add(on);
            return true;
        }
        final String keyword;
        if (type == null || type.INNER() != null) {
            keyword = " INNER JOIN ";
        } else if (type.CROSS() != null && on == null) {
            keyword = " INNER JOIN ";
        } else if (type.LEFT() != null && on != null) {
            keyword = " LEFT OUTER JOIN ";
        } else if (type.FULL() != null && on != null) {
            keyword = " FULL OUTER JOIN ";
        } else {
            return false;
        }
        segments.add(keyword + relation);
        conditions.add(on);
        return true;
    }

    /** One table of the FROM clause, {@code PUBLIC.RT AS R}, registered in the scope; null when not modelled. */
    private String relation(final FrostlakeParser.TableReferenceContext reference, final Catalog catalog) {
        if (reference.LATERAL() != null || reference.identifierList() != null || reference.pivotClause() != null
                || reference.unpivotClause() != null || reference.sampleClause() != null) {
            return null;
        }
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        for (final String part : parts) {
            if (!isPlainName(part)) {
                return null;
            }
        }
        final Table table = catalog.resolveTable(QualifiedName.of(parts));
        final String alias = aliasOf(reference);
        final String shown = alias != null ? alias : parts[parts.length - 1];
        final String key = isPlainName(shown) ? shown : "\"" + shown + "\"";
        if (table == null || aliasToTable.containsKey(key)) {
            return null;
        }
        aliasToTable.put(key, table);
        tables.add(table);
        qualifiers.add(shown);
        return String.join(".", parts) + " AS " + shown;
    }

    private static String aliasOf(final FrostlakeParser.TableReferenceContext reference) {
        if (reference.aliasName() != null) {
            return ParseTreeText.getIdentifier(reference.aliasName());
        }
        final FrostlakeParser.NonJoinKeywordIdentifierContext bare = reference.nonJoinKeywordIdentifier();
        if (bare == null) {
            return null;
        }
        return bare.identifier() != null ? ParseTreeText.getIdentifier(bare.identifier())
            : bare.getText().toUpperCase(Locale.ROOT);
    }

    /** The evaluation scope the subquery's own FROM clause makes, which its columns are qualified and typed in. */
    private ExpressionEvaluatorVisitor scope(final Catalog catalog, final QueryExecutor queryExecutor) {
        final Table first = tables.isEmpty()
            ? new Table(FROMLESS_RELATION, new ArrayList<TableColumn>(), false) : tables.get(0);
        final ExpressionEvaluatorVisitor scope =
            new ExpressionEvaluatorVisitor(first, null, outer.getFunctionRegistry(), catalog);
        scope.setQueryExecutor(queryExecutor);
        if (!tables.isEmpty()) {
            scope.setMultiTableContext(aliasToTable, tables);
        }
        return scope;
    }

    /** One select item and its name, a star expanded over the one relation; false when not modelled. */
    private boolean collectItem(final FrostlakeParser.SelectItemContext item, final List<Expression> items,
                                final List<String> names) {
        if (SelectItemAccessors.isStarItem(item)) {
            if (tables.size() != 1 || !SelectItemAccessors.getStarModifiers(item).isEmpty()) {
                return false;
            }
            final String qualifier = qualifiers.iterator().next();
            if (!isPlainName(qualifier)) {
                return false;
            }
            for (final TableColumn column : tables.get(0).getColumns()) {
                items.add(new ColumnReferenceExpression(qualifier, column.getName(), null));
                names.add(column.getName());
            }
            return true;
        }
        if (!SelectItemAccessors.isExprItem(item)) {
            return false;
        }
        items.add(new ExpressionAstBuilder().build(((FrostlakeParser.ExprItemContext) item).booleanExpr()));
        final String alias = SelectItemAccessors.getItemAlias(item);
        final String name = alias != null ? alias : derivedName(item);
        if (name.indexOf('"') >= 0) {
            return false;
        }
        names.add(name);
        return true;
    }

    /** An unaliased item's name: a column's own name, anything else its source text upper-cased. */
    private static String derivedName(final FrostlakeParser.SelectItemContext item) {
        final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(item);
        if (value == null) {
            return ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)).toUpperCase(Locale.ROOT);
        }
        final FrostlakeParser.ExpressionContext simple = SelectItemAccessors.unwrapParens(value);
        if (simple instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) simple).qualifiedName());
            return parts[parts.length - 1];
        }
        return ParseTreeText.getOriginalText(value).toUpperCase(Locale.ROOT);
    }

    /** The GROUP BY the plan prints, "" for none and null when not modelled. */
    private String groupText(final FrostlakeParser.GroupByClauseContext group, final List<Expression> items,
                             final List<String> names, final StrictMessagePrinter printer) {
        if (group == null) {
            return "";
        }
        final List<String> keys = new ArrayList<>();
        if (group.ALL() != null) {
            // GROUP BY ALL groups by the projected columns, and names them as projected.
            for (int i = 0; i < items.size(); i++) {
                if (!(items.get(i) instanceof ColumnReferenceExpression)) {
                    return null;
                }
                keys.add(names.get(i));
            }
            return " GROUP BY " + String.join(", ", keys);
        }
        for (final FrostlakeParser.GroupByElementContext element : group.groupByElement()) {
            if (element.expression() == null) {
                return null;
            }
            final Expression key = new ExpressionAstBuilder().visit(element.expression());
            final Integer ordinal = ordinalOf(key, names.size());
            if (ordinal != null) {
                keys.add(names.get(ordinal.intValue() - 1));
            } else if (key instanceof ColumnReferenceExpression && !((ColumnReferenceExpression) key).isQualified()
                    && !readByARelation(((ColumnReferenceExpression) key).getColumnName())
                    && names.contains(((ColumnReferenceExpression) key).getColumnName())) {
                // A name no relation carries is the output column it names, and prints bare.
                keys.add(((ColumnReferenceExpression) key).getColumnName());
            } else {
                keys.add(key.accept(printer));
            }
        }
        return " GROUP BY " + String.join(", ", keys);
    }

    /** The ORDER BY the plan prints, "" for none and null when not modelled. */
    private String orderText(final FrostlakeParser.OrderByClauseContext order, final ExpressionEvaluatorVisitor scope,
                             final List<String> names) {
        if (order == null) {
            return "";
        }
        final Set<String> outputNames = new HashSet<>(names);
        scope.setOutputScopeNames(outputNames);
        try {
            final StrictMessagePrinter printer = new StrictMessagePrinter(scope, mode);
            final List<String> bareColumns = new ArrayList<>();
            final List<String> writtenQualifiers = new ArrayList<>();
            printer.watchColumns(bareColumns, writtenQualifiers);
            final List<String> keys = new ArrayList<>();
            for (final FrostlakeParser.OrderItemContext item : order.orderItem()) {
                final Expression key = new ExpressionAstBuilder().visit(item.expression());
                final Integer ordinal = ordinalOf(key, names.size());
                final boolean descending = item.DESC() != null;
                final boolean nullsFirst = item.FIRST() != null || (item.LAST() == null && descending);
                keys.add((ordinal != null ? names.get(ordinal.intValue() - 1) : key.accept(printer))
                    + (descending ? " DESC" : " ASC") + " NULLS " + (nullsFirst ? "FIRST" : "LAST"));
            }
            for (final String bare : bareColumns) {
                if (!outputNames.contains(bare) || !isPlainName(bare)) {
                    return null;
                }
            }
            return allKnown(writtenQualifiers) ? "  ORDER BY " + String.join(", ", keys) : null;
        } finally {
            scope.setOutputScopeNames(null);
        }
    }

    /**
     * The LIMIT the plan prints, {@code LIMIT n OFFSET m} with the offset 0 when none was written — from a
     * LIMIT, a FETCH or a TOP alike — "" for none and null when not modelled (a NULL, an empty string or
     * a bind as the count).
     */
    private static String limitText(final FrostlakeParser.SelectStatementContext statement,
                                    final FrostlakeParser.SelectClauseContext select) {
        final FrostlakeParser.LimitClauseContext limit = statement.limitClause();
        final FrostlakeParser.FetchClauseContext fetch = statement.fetchClause();
        final FrostlakeParser.TopClauseContext top = select.topClause();
        final int written = (limit != null ? 1 : 0) + (fetch != null ? 1 : 0) + (top != null ? 1 : 0);
        if (written == 0) {
            return "";
        }
        if (written > 1) {
            return null;
        }
        final TerminalNode count;
        final TerminalNode offset;
        if (limit != null) {
            if (!limit.NULL().isEmpty() || !limit.STRING_LITERAL().isEmpty() || !limit.COLON().isEmpty()) {
                return null;
            }
            final List<TerminalNode> numbers = limit.INTEGER_LITERAL();
            count = numbers.get(0);
            offset = numbers.size() > 1 ? numbers.get(1) : null;
        } else if (fetch != null) {
            if (!fetch.NULL().isEmpty() || !fetch.COLON().isEmpty()) {
                return null;
            }
            final List<TerminalNode> numbers = fetch.INTEGER_LITERAL();
            count = numbers.get(numbers.size() - 1);
            offset = fetch.OFFSET() != null ? numbers.get(0) : null;
        } else {
            count = top.INTEGER_LITERAL();
            offset = null;
        }
        return " LIMIT " + new BigInteger(count.getText()) + " OFFSET "
            + (offset != null ? new BigInteger(offset.getText()).toString() : "0");
    }

    /** The 1-based ordinal a written integer key counts to, or null for any other key or one out of range. */
    private static Integer ordinalOf(final Expression key, final int items) {
        if (!(key instanceof LiteralExpression) || ((LiteralExpression) key).getType() != LiteralType.INTEGER) {
            return null;
        }
        final long ordinal = ((Number) ((LiteralExpression) key).getValue()).longValue();
        if (ordinal < 1 || ordinal > items) {
            throw new IllegalStateException("An ordinal out of range is not modelled: " + ordinal);
        }
        return Integer.valueOf((int) ordinal);
    }

    private boolean readByARelation(final String column) {
        for (final Table table : tables) {
            if (table.hasColumn(column)) {
                return true;
            }
        }
        return false;
    }

    /** Whether every qualifier the subquery wrote names one of its own relations, as printed. */
    private boolean allKnown(final List<String> writtenQualifiers) {
        for (final String qualifier : writtenQualifiers) {
            if (!qualifiers.contains(qualifier) || !isPlainName(qualifier)) {
                return false;
            }
        }
        return true;
    }

    /** A name that prints the same quoted or not: upper-case letters, digits, '_' and '$', not led by a digit. */
    private static boolean isPlainName(final String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            final boolean leading = (c >= 'A' && c <= 'Z') || c == '_';
            final boolean following = (c >= '0' && c <= '9') || c == '$';
            if (!(leading || (i > 0 && following))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUntypedNull(final Expression expression) {
        return expression instanceof LiteralExpression
            && ((LiteralExpression) expression).getType() == LiteralType.NULL;
    }

    private static boolean isExactNumber(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type);
    }

    /** The subquery's text as a SELECT statement, or null when it is not one whole statement. */
    private static FrostlakeParser.SelectStatementContext parsed(final String text) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        final FrostlakeParser.SelectStatementContext statement = parser.selectStatement();
        return parser.getCurrentToken().getType() == Token.EOF ? statement : null;
    }

    /**
     * The statement whose one operand is a plain SELECT clause, through any brackets written around it,
     * or null for a WITH, a set operation, or brackets that carry an ORDER BY or a LIMIT of their own.
     */
    private static FrostlakeParser.SelectStatementContext soleStatement(
            final FrostlakeParser.SelectStatementContext written) {
        FrostlakeParser.SelectStatementContext statement = written;
        while (statement != null) {
            if (statement.withClause() != null || statement.selectOperand().size() != 1) {
                return null;
            }
            final FrostlakeParser.SelectOperandContext operand = statement.selectOperand(0);
            if (operand.selectClause() != null) {
                return statement;
            }
            if (statement.orderByClause() != null || statement.limitClause() != null
                    || statement.fetchClause() != null) {
                return null;
            }
            statement = operand.selectStatement();
        }
        return null;
    }
}
