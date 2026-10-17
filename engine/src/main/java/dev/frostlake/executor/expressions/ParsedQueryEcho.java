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
import dev.frostlake.executor.SelectItemAccessors;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.KeyContexts;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prints a query the way the account names one inside an expression it echoes from the tree as PARSED (see
 * {@link ParsedEchoPrinter}): no name resolved, every clause in the place the tree keeps it (live-verified, shape
 * by shape):
 *
 * <pre>
 *   SELECT 1                              SELECT 1 AS "1" FROM (VALUES (null)) DUAL
 *   SELECT a + 1, b AS z FROM rc_t t      SELECT "+"(A, 1) AS "A + 1", B AS "Z" FROM RC_T AS T
 *   … FROM t JOIN u ON t.a = u.a          … FROM T AS T INNER JOIN U AS U ON ("="(T.A, U.A))
 *   … FROM t, u   … t CROSS JOIN u        … FROM T AS T INNER JOIN U AS U
 *   … FROM (SELECT a FROM t) d            … FROM (SELECT A AS "A" FROM T AS T) D
 *   … FROM TABLE(GENERATOR(ROWCOUNT =&gt; 3))   … FROM TABLE (GENERATOR(ROWCOUNT =&gt; 3))
 *   … ORDER BY a LIMIT 1                  …  ORDER BY A ASC NULLS LAST LIMIT 1 OFFSET 0
 *   SELECT 1 UNION ALL SELECT 2           SELECT * FROM (SELECT 1 AS …) OB$0 UNION ALL (SELECT 2 AS …) OB$1
 * </pre>
 *
 * <p>An item is named by its alias, a lone quoted name by itself, and anything else by its own text upper-cased; a
 * table by its alias or the last part of its name. A join names its kind in full — INNER for a comma, a CROSS and
 * a NATURAL join alike — and keeps an ON in parentheses, a USING not at all. The clauses follow in the tree's
 * order, which is not the written one: WHERE, GROUP BY, ORDER BY, HAVING, QUALIFY and the row limit, with two
 * spaces before ORDER BY and before HAVING. A row limit is always LIMIT n OFFSET m, whether written LIMIT, TOP or
 * FETCH. A ROLLUP and a CUBE are the grouping sets they stand for. A WITH is not printed: a reference to one of
 * its names reads as a table's. A set operation selects everything from its operands, each in parentheses and
 * named OB$n in the order they close, MINUS for EXCEPT; INTERSECT binds first, and operands under one operator
 * share one level.
 *
 * <p>A shape this printer does not model makes {@link #echo} answer null, and the caller keeps the query's text
 * as written.
 */
final class ParsedQueryEcho {

    /** How many operands of set operations have been named so far. */
    private int operands;
    /** Whether the query being printed holds a shape this printer does not model. */
    private boolean unmodelled;

    /**
     * A query as the tree as parsed prints it.
     *
     * @param query the query's text
     * @return its echo, or null where the text does not parse as one query or holds a shape not modelled
     */
    String echo(final String query) {
        final boolean outer = unmodelled;
        final int named = operands;
        unmodelled = false;
        try {
            final FrostlakeParser.SelectStatementContext parsed = SubqueryPlanPrint.parsed(query);
            final String text = parsed == null ? null : statement(parsed);
            if (text == null || unmodelled) {
                operands = named;
                return null;
            }
            return text;
        } catch (final RuntimeException notRead) {
            operands = named;
            return null;
        } finally {
            unmodelled = outer;
        }
    }

    private String statement(final FrostlakeParser.SelectStatementContext ctx) {
        final List<FrostlakeParser.SelectOperandContext> written = ctx.selectOperand();
        if (written.size() == 1 && written.get(0).selectClause() != null) {
            return block(written.get(0).selectClause(), ctx);
        }
        final ParsedEchoPrinter printer = new ParsedEchoPrinter(true, true, this);
        final String tail = order(ctx.orderByClause(), printer) + rowLimit(ctx, null);
        if (written.size() == 1) {
            // Brackets around a whole query; an ORDER BY or a row limit written after them is not modelled.
            unmodelled = unmodelled || !tail.isEmpty();
            return statement(written.get(0).selectStatement());
        }
        return setOperation(ctx) + tail;
    }

    /**
     * A set operation: the operands between INTERSECTs close first, then the rest from the left, and operands
     * that follow one another under one operator stand side by side.
     */
    private String setOperation(final FrostlakeParser.SelectStatementContext ctx) {
        final List<FrostlakeParser.SelectOperandContext> written = ctx.selectOperand();
        final List<String> level = new ArrayList<>();
        String levelOperator = null;
        int from = 0;
        for (int i = 1; i <= written.size(); i++) {
            final boolean ends = i == written.size() || ctx.setOperator(i - 1).INTERSECT() == null;
            if (!ends) {
                continue;
            }
            if (from == 0 && i == written.size()) {
                return intersection(ctx, 0, i);
            }
            // Operands from..i-1 are one operand, or several an INTERSECT joins.
            final String operator = from == 0 ? null : operator(ctx.setOperator(from - 1));
            if (levelOperator != null && !levelOperator.equals(operator)) {
                final String closed = named(joined(level, levelOperator));
                level.clear();
                level.add(closed);
            }
            level.add(named(i - from == 1 ? operand(written.get(from)) : intersection(ctx, from, i)));
            levelOperator = operator == null ? levelOperator : operator;
            from = i;
        }
        return joined(level, levelOperator);
    }

    /** The operands from..to-1, which INTERSECTs join. */
    private String intersection(final FrostlakeParser.SelectStatementContext ctx, final int from, final int to) {
        final List<String> level = new ArrayList<>();
        String levelOperator = null;
        for (int i = from; i < to; i++) {
            final String operator = i == from ? null : operator(ctx.setOperator(i - 1));
            if (levelOperator != null && !levelOperator.equals(operator)) {
                final String closed = named(joined(level, levelOperator));
                level.clear();
                level.add(closed);
            }
            level.add(named(operand(ctx.selectOperand(i))));
            levelOperator = operator == null ? levelOperator : operator;
        }
        return joined(level, levelOperator);
    }

    private String operand(final FrostlakeParser.SelectOperandContext ctx) {
        return ctx.selectClause() != null ? block(ctx.selectClause(), null) : statement(ctx.selectStatement());
    }

    /** An operand of a set operation, in parentheses and under the next name. */
    private String named(final String query) {
        return "(" + query + ") OB$" + operands++;
    }

    private static String joined(final List<String> level, final String operator) {
        final StringBuilder text = new StringBuilder("SELECT * FROM ");
        for (int i = 0; i < level.size(); i++) {
            text.append(i > 0 ? " " + operator + " " : "").append(level.get(i));
        }
        return text.toString();
    }

    private static String operator(final FrostlakeParser.SetOperatorContext ctx) {
        final String all = ctx.ALL() != null ? " ALL" : "";
        if (ctx.UNION() != null) {
            return "UNION" + all + (ctx.BY() != null ? " BY NAME" : "");
        }
        return (ctx.INTERSECT() != null ? "INTERSECT" : "MINUS") + all;
    }

    /**
     * One SELECT, its clauses in the tree's order.
     *
     * @param select the SELECT
     * @param whole  the statement whose ORDER BY and row limit are this SELECT's, or null for an operand's
     */
    private String block(final FrostlakeParser.SelectClauseContext select,
                         final FrostlakeParser.SelectStatementContext whole) {
        unmodelled = unmodelled || select.intoClause() != null || select.connectByClause() != null;
        final ParsedEchoPrinter printer = new ParsedEchoPrinter(true, true, this);
        final StringBuilder text = new StringBuilder("SELECT ");
        if (select.DISTINCT() != null) {
            text.append("DISTINCT ");
        }
        final List<FrostlakeParser.SelectItemContext> items = select.selectList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            text.append(i > 0 ? ", " : "").append(item(items.get(i), printer));
        }
        text.append(" FROM ").append(from(select.tableExpression(), printer));
        if (select.whereClause() != null) {
            text.append(" WHERE ").append(condition(select.whereClause().booleanExpr(), printer));
        }
        text.append(group(select.groupByClause(), printer));
        text.append(order(whole == null ? null : whole.orderByClause(), printer));
        if (select.havingClause() != null) {
            text.append("  HAVING ").append(condition(select.havingClause().booleanExpr(), printer));
        }
        if (select.qualifyClause() != null) {
            text.append(" QUALIFY ").append(condition(select.qualifyClause().booleanExpr(), printer));
        }
        return text.append(rowLimit(whole, select.topClause())).toString();
    }

    private static String condition(final FrostlakeParser.BooleanExprContext ctx, final ParsedEchoPrinter printer) {
        return new ExpressionAstBuilder().build(ctx).accept(printer);
    }

    private static String value(final ParserRuleContext ctx, final ParsedEchoPrinter printer) {
        return new ExpressionAstBuilder().visit(ctx).accept(printer);
    }

    /** A select item and its name: a star has none, and a modifier written after one is not kept. */
    private String item(final FrostlakeParser.SelectItemContext item, final ParsedEchoPrinter printer) {
        if (SelectItemAccessors.isStarItem(item)) {
            return "*";
        }
        if (item instanceof FrostlakeParser.QualifiedStarItemContext) {
            final String star = spelled(ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedStarItemContext) item).starQualifiedName())) + ".*";
            return star + " AS \"" + star + "\"";
        }
        if (!(item instanceof FrostlakeParser.ExprItemContext)) {
            unmodelled = true;
            return "";
        }
        final FrostlakeParser.BooleanExprContext written = ((FrostlakeParser.ExprItemContext) item).booleanExpr();
        return condition(written, printer) + " AS \"" + itemName(item, written) + "\"";
    }

    /** An item's name: its alias, a lone quoted name itself, and its own text upper-cased otherwise. */
    private static String itemName(final FrostlakeParser.SelectItemContext item,
                                   final FrostlakeParser.BooleanExprContext written) {
        final String alias = SelectItemAccessors.getItemAlias(item);
        if (alias != null) {
            return alias;
        }
        final String text = ParseTreeText.getOriginalText(written);
        final FrostlakeParser.ExpressionContext value = SelectItemAccessors.unwrapValue(written);
        if (value instanceof FrostlakeParser.QualifiedNameExprContext && text.startsWith("\"")) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName());
            if (parts.length == 1) {
                return parts[0];
            }
        }
        return text.toUpperCase(Locale.ROOT);
    }

    /** A name's parts, each in quotes only where it needs them. */
    private static String spelled(final String[] parts) {
        final StringBuilder name = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            name.append(i > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(parts[i]));
        }
        return name.toString();
    }

    /** The FROM clause, or the one row a SELECT without one reads. */
    private String from(final FrostlakeParser.TableExpressionContext tables, final ParsedEchoPrinter printer) {
        if (tables == null) {
            return "(VALUES (null)) DUAL";
        }
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < tables.getChildCount(); i++) {
            final ParseTree child = tables.getChild(i);
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) child;
                if (text.length() > 0) {
                    // A comma joins two relations as an INNER JOIN does, and stays a comma before a table function.
                    text.append(isTableFunction(reference.tableSource()) ? ", " : " INNER JOIN ");
                }
                text.append(reference(reference, printer));
            } else if (child instanceof FrostlakeParser.JoinClauseContext) {
                text.append(join((FrostlakeParser.JoinClauseContext) child, printer));
            }
        }
        return text.toString();
    }

    private static boolean isTableFunction(final FrostlakeParser.TableSourceContext source) {
        return source.TABLE() != null || source.FLATTEN() != null || source.tableFunctionExpr() != null;
    }

    private String join(final FrostlakeParser.JoinClauseContext ctx, final ParsedEchoPrinter printer) {
        unmodelled = unmodelled || ctx.ASOF() != null;
        final FrostlakeParser.JoinTypeContext type = ctx.joinType();
        final String kind = type == null || type.INNER() != null ? "INNER"
            : type.LEFT() != null ? "LEFT OUTER" : type.RIGHT() != null ? "RIGHT OUTER" : "FULL OUTER";
        final StringBuilder text = new StringBuilder(" ").append(kind).append(" JOIN ")
            .append(reference(ctx.tableReference(), printer));
        if (ctx.booleanExpr() != null) {
            text.append(" ON (").append(condition(ctx.booleanExpr(), printer)).append(')');
        }
        return text.toString();
    }

    /** A relation in FROM under its alias; a LATERAL and a SAMPLE written with it are not kept. */
    private String reference(final FrostlakeParser.TableReferenceContext ctx, final ParsedEchoPrinter printer) {
        unmodelled = unmodelled || ctx.pivotClause() != null || ctx.unpivotClause() != null
            || ctx.matchRecognizeClause() != null;
        final String alias;
        if (ctx.aliasName() != null) {
            alias = ParseTreeText.getIdentifier(ctx.aliasName());
        } else if (ctx.nonJoinKeywordIdentifier() == null) {
            alias = null;
        } else if (ctx.nonJoinKeywordIdentifier().identifier() != null) {
            alias = ParseTreeText.getIdentifier(ctx.nonJoinKeywordIdentifier().identifier());
        } else {
            alias = ctx.nonJoinKeywordIdentifier().getText().toUpperCase(Locale.ROOT);
        }
        return source(ctx.tableSource(), alias, printer);
    }

    private String source(final FrostlakeParser.TableSourceContext ctx, final String alias,
                          final ParsedEchoPrinter printer) {
        if (ctx.tableQualifiedName() != null) {
            unmodelled = unmodelled || ctx.timeTravelClause() != null;
            return table(ParseTreeText.qualifiedNameParts(ctx.tableQualifiedName()), alias);
        }
        if (ctx.KW_IDENTIFIER_REF() != null && ctx.identifierArgument().STRING_LITERAL() != null) {
            return table(SqlIdentifiers.identifierReferenceParts(
                ParseTreeText.extractStringLiteral(ctx.identifierArgument().STRING_LITERAL())), alias);
        }
        if (ctx.selectStatement() != null) {
            // A query in FROM that nobody named reads under the word the tree gives it.
            return "(" + statement(ctx.selectStatement()) + ") " + (alias != null ? alias : "values");
        }
        if (ctx.valueTupleList() != null) {
            final String inner = ctx.identifier() != null ? ParseTreeText.getIdentifier(ctx.identifier()) : alias;
            return "(VALUES " + tuples(ctx.valueTupleList(), printer) + ") " + (inner != null ? inner : "\"VALUES\"");
        }
        if (ctx.TABLE() != null && ctx.expression() != null) {
            final Expression call = new ExpressionAstBuilder().visit(ctx.expression());
            if (call instanceof FunctionCallExpression) {
                return "TABLE (" + printer.namedCall((FunctionCallExpression) call) + ")";
            }
        }
        if (ctx.FLATTEN() != null) {
            return "TABLE (FLATTEN(" + flattenArguments(ctx.flattenArgList(), printer) + "))";
        }
        if (ctx.tableReference() != null) {
            // A group in parentheses is the relations it holds.
            final StringBuilder text = new StringBuilder(reference(ctx.tableReference(), printer));
            for (final FrostlakeParser.JoinClauseContext joined : ctx.joinClause()) {
                text.append(join(joined, printer));
            }
            return text.toString();
        }
        unmodelled = true;
        return "";
    }

    /** A table by its name as written, under its alias or the last part of that name. */
    private static String table(final String[] parts, final String alias) {
        return spelled(parts) + " AS " + (alias != null ? alias : parts[parts.length - 1]);
    }

    private String tuples(final FrostlakeParser.ValueTupleListContext ctx, final ParsedEchoPrinter printer) {
        final StringBuilder text = new StringBuilder();
        for (final FrostlakeParser.ValueTupleContext tuple : ctx.valueTuple()) {
            text.append(text.length() > 0 ? ", (" : "(");
            final List<FrostlakeParser.BooleanExprContext> values = tuple.valueList().booleanExpr();
            for (int i = 0; i < values.size(); i++) {
                text.append(i > 0 ? ", " : "").append(condition(values.get(i), printer));
            }
            text.append(')');
        }
        return text.toString();
    }

    private String flattenArguments(final FrostlakeParser.FlattenArgListContext ctx, final ParsedEchoPrinter printer) {
        final StringBuilder text = new StringBuilder();
        if (ctx.expression() != null) {
            text.append(value(ctx.expression(), printer));
        }
        final List<FrostlakeParser.NamedArgumentContext> named = ctx.namedArgumentList() != null
            ? ctx.namedArgumentList().namedArgument() : ctx.namedArgument();
        for (final FrostlakeParser.NamedArgumentContext argument : named) {
            unmodelled = unmodelled || argument.expression() == null;
            if (argument.expression() != null) {
                text.append(text.length() > 0 ? ", " : "").append(ParseTreeText.getIdentifier(argument.variableName()))
                    .append(" => ").append(value(argument.expression(), printer));
            }
        }
        return text.toString();
    }

    /** The GROUP BY: its keys, or the grouping sets a ROLLUP, a CUBE or GROUPING SETS among them stands for. */
    private String group(final FrostlakeParser.GroupByClauseContext ctx, final ParsedEchoPrinter printer) {
        if (ctx == null) {
            return "";
        }
        if (ctx.ALL() != null) {
            return " GROUP BY ALL";
        }
        boolean plain = true;
        List<List<String>> sets = new ArrayList<>();
        sets.add(new ArrayList<>());
        for (final FrostlakeParser.GroupByElementContext element : ctx.groupByElement()) {
            plain = plain && element.ROLLUP() == null && element.CUBE() == null && element.GROUPING() == null;
            final List<List<String>> crossed = new ArrayList<>();
            for (final List<String> set : sets) {
                for (final List<String> added : setsOf(element, printer)) {
                    final List<String> both = new ArrayList<>(set);
                    both.addAll(added);
                    crossed.add(both);
                }
            }
            sets = crossed;
        }
        if (plain) {
            return " GROUP BY " + String.join(", ", sets.get(0));
        }
        final StringBuilder text = new StringBuilder(" GROUP BY GROUPING SETS (");
        for (int i = 0; i < sets.size(); i++) {
            text.append(i > 0 ? ", (" : "(").append(String.join(", ", sets.get(i))).append(')');
        }
        return text.append(')').toString();
    }

    /** The grouping sets one GROUP BY element stands for: a key is one set of itself. */
    private List<List<String>> setsOf(final FrostlakeParser.GroupByElementContext element,
                                      final ParsedEchoPrinter printer) {
        final List<List<String>> sets = new ArrayList<>();
        if (element.GROUPING() != null) {
            for (final FrostlakeParser.GroupingSetContext set : element.groupingSetList().groupingSet()) {
                sets.add(keys(KeyContexts.members(set), printer));
            }
            return sets;
        }
        if (element.groupByColumnList() == null) {
            final List<String> key = new ArrayList<>();
            key.add(value(KeyContexts.of(element), printer));
            sets.add(key);
            return sets;
        }
        final List<String> keys = keys(KeyContexts.members(element.groupByColumnList()), printer);
        if (element.ROLLUP() != null) {
            // Every leading run of the keys, the empty one first.
            for (int length = 0; length <= keys.size(); length++) {
                sets.add(new ArrayList<>(keys.subList(0, length)));
            }
            return sets;
        }
        // A CUBE is every choice of its keys, counted up with the first key as the lowest digit.
        for (int chosen = 0; chosen < 1 << keys.size(); chosen++) {
            final List<String> set = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                if ((chosen & 1 << i) != 0) {
                    set.add(keys.get(i));
                }
            }
            sets.add(set);
        }
        return sets;
    }

    private static List<String> keys(final List<ParserRuleContext> written, final ParsedEchoPrinter printer) {
        final List<String> keys = new ArrayList<>();
        for (final ParserRuleContext key : written) {
            keys.add(value(key, printer));
        }
        return keys;
    }

    /** The ORDER BY, each key with its direction and where its nulls sort, or nothing. */
    private static String order(final FrostlakeParser.OrderByClauseContext ctx, final ParsedEchoPrinter printer) {
        if (ctx == null) {
            return "";
        }
        final StringBuilder text = new StringBuilder("  ORDER BY ");
        final List<FrostlakeParser.OrderItemContext> items = ctx.orderItem();
        for (int i = 0; i < items.size(); i++) {
            final FrostlakeParser.OrderItemContext item = items.get(i);
            text.append(i > 0 ? ", " : "").append(value(KeyContexts.of(item), printer))
                .append(ParsedEchoPrinter.sortSuffix(item.DESC() == null,
                    item.NULLS() == null ? null : Boolean.valueOf(item.FIRST() != null)));
        }
        return text.toString();
    }

    /**
     * The row limit, however written — LIMIT, TOP or FETCH — as a count and an offset, or nothing.
     *
     * @param whole the statement that carries a LIMIT or a FETCH, or null
     * @param top   the SELECT's TOP, or null
     */
    private String rowLimit(final FrostlakeParser.SelectStatementContext whole,
                            final FrostlakeParser.TopClauseContext top) {
        final FrostlakeParser.LimitClauseContext limit = whole == null ? null : whole.limitClause();
        final FrostlakeParser.FetchClauseContext fetch = whole == null ? null : whole.fetchClause();
        if (limit != null) {
            unmodelled = unmodelled || top != null;
            return " LIMIT " + rowNumber(limit, limit.LIMIT()) + " OFFSET "
                + (limit.OFFSET() == null ? "0" : rowNumber(limit, limit.OFFSET()));
        }
        if (fetch != null) {
            unmodelled = unmodelled || top != null;
            final ParseTree first = fetch.FIRST() != null ? fetch.FIRST() : fetch.NEXT();
            return " LIMIT " + rowNumber(fetch, first != null ? first : fetch.FETCH()) + " OFFSET "
                + (fetch.OFFSET() == null ? "0" : rowNumber(fetch, fetch.OFFSET()));
        }
        return top == null ? "" : " LIMIT " + top.INTEGER_LITERAL().getText() + " OFFSET 0";
    }

    /** The row count or offset written after a keyword: a whole number, or the null that sets no bound. */
    private String rowNumber(final ParserRuleContext clause, final ParseTree keyword) {
        for (int i = 0; i < clause.getChildCount() - 1; i++) {
            if (clause.getChild(i) == keyword && clause.getChild(i + 1) instanceof TerminalNode) {
                final TerminalNode number = (TerminalNode) clause.getChild(i + 1);
                if (number.getSymbol().getType() == FrostlakeParser.INTEGER_LITERAL) {
                    return number.getText();
                }
                if (number.getSymbol().getType() == FrostlakeParser.NULL) {
                    return "null";
                }
            }
        }
        unmodelled = true;
        return "";
    }
}
