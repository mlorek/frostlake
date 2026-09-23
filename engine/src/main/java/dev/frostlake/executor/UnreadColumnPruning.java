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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The columns of a view that the statement reading it never reads, and the view's body with those columns
 * blanked; and the query blocks that keep no row at all.
 *
 * <p>Live inlines a view into the query that reads it, so a column nobody reads is never computed, and a value
 * it would fault on is never produced. {@code SELECT COUNT(*) FROM v} and {@code SELECT n FROM v} both answer
 * over a view whose other column is {@code CAST(s AS VARCHAR(5))} of an eight-character string, while
 * reading that column is refused: {@code String 'abcdefgh' is too long and would be truncated}. Frostlake
 * runs the body whole, so an unread select item is replaced by {@code NULL AS "<name>"} before the body runs,
 * padded with blanks to the item's own width so the positions after it stay where they were.
 *
 * <p>What the reader reads is decided conservatively, from the reading statement's own tokens. A column is
 * read when its name appears anywhere in the statement outside a select-item alias. A statement that can
 * read a column without naming it prunes nothing: a star other than COUNT(*), a positional $n, a NATURAL
 * join, PIVOT or UNPIVOT, IDENTIFIER().
 *
 * <p>The body is pruned only where a blank cannot change which rows it produces: no DISTINCT, no
 * hierarchical query, no aggregate without GROUP BY, no GROUP BY ALL, no ordinal GROUP BY or ORDER BY key,
 * and nothing but UNION ALL between its arms. An item the body names elsewhere is kept, unless only other
 * pruned items name it.
 */
final class UnreadColumnPruning {

    /** The tokens a WHERE conjunct may consist of to be decided before any row is read. */
    private static final Set<Integer> CONSTANT_TOKENS = new HashSet<Integer>();

    static {
        final int[] tokens = {
            FrostlakeParser.INTEGER_LITERAL, FrostlakeParser.FLOAT_LITERAL, FrostlakeParser.STRING_LITERAL,
            FrostlakeParser.TRUE, FrostlakeParser.FALSE, FrostlakeParser.NULL, FrostlakeParser.NOT,
            FrostlakeParser.IS, FrostlakeParser.OR, FrostlakeParser.EQ, FrostlakeParser.NEQ,
            FrostlakeParser.LT, FrostlakeParser.LTE, FrostlakeParser.GT, FrostlakeParser.GTE,
            FrostlakeParser.LPAREN, FrostlakeParser.RPAREN, FrostlakeParser.PLUS, FrostlakeParser.MINUS,
        };
        for (final int token : tokens) {
            CONSTANT_TOKENS.add(token);
        }
    }

    private UnreadColumnPruning() {
    }

    /**
     * The names read by the statement that holds {@code reference}, upper-cased, or null when the statement
     * can read a column without naming it.
     */
    static Set<String> namesRead(final ParserRuleContext reference) {
        ParseTree root = reference;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        final Set<String> names = new HashSet<String>();
        return collect(root, names, null) ? names : null;
    }

    /**
     * {@link #namesRead(ParserRuleContext)} with one subtree left out: a derived table's or a CTE's own body
     * is not its reader, so the names and the stars inside it say nothing about what the reader reads.
     *
     * @param reference where the statement names the relation
     * @param exclude   the body to leave out
     * @return the names read, or null when the statement can read a column without naming it
     */
    static Set<String> namesRead(final ParserRuleContext reference, final ParseTree exclude) {
        ParseTree root = reference;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        final Set<String> names = new HashSet<String>();
        final Set<ParseTree> skipped = new HashSet<ParseTree>();
        skipped.add(exclude);
        return collect(root, names, skipped) ? names : null;
    }

    /**
     * {@code text}, a view's body, with every select item the reader never reads blanked, or null when none
     * can be.
     *
     * @param body the body, parsed from {@code text}
     * @param names the view's own column list, or null when its select items name its columns
     * @param read what the reader reads, from {@link #namesRead}
     */
    static String pruned(final String text, final FrostlakeParser.SelectStatementContext body,
                         final List<String> names, final Set<String> read, final FunctionRegistry functions) {
        if (!prunable(body, functions)) {
            return null;
        }
        final List<FrostlakeParser.SelectOperandContext> arms = body.selectOperand();
        final List<FrostlakeParser.SelectItemContext> heads = arms.get(0).selectClause().selectList().selectItem();
        final List<Integer> unread = new ArrayList<Integer>();
        final List<String> unreadNames = new ArrayList<String>();
        for (int i = 0; i < heads.size(); i++) {
            final String name = names != null ? (i < names.size() ? names.get(i) : null) : nameOf(heads.get(i));
            if (name != null && !read.contains(name.toUpperCase(Locale.ROOT)) && expressionInEveryArm(arms, i)) {
                unread.add(i);
                unreadNames.add(name);
            }
        }
        // An unread item the body names elsewhere stays, unless only items pruned too name it.
        boolean changed = true;
        while (changed && !unread.isEmpty()) {
            final Set<ParseTree> pruning = new HashSet<ParseTree>();
            for (final Integer index : unread) {
                for (final FrostlakeParser.SelectOperandContext arm : arms) {
                    pruning.add(itemAt(arm, index));
                }
            }
            final Set<String> named = new HashSet<String>();
            collect(body, named, pruning);
            changed = false;
            for (int k = unread.size() - 1; k >= 0; k--) {
                if (named.contains(unreadNames.get(k).toUpperCase(Locale.ROOT))) {
                    unread.remove(k);
                    unreadNames.remove(k);
                    changed = true;
                }
            }
        }
        if (unread.isEmpty()) {
            return null;
        }
        final List<ParserRuleContext> items = new ArrayList<ParserRuleContext>();
        final List<String> replacements = new ArrayList<String>();
        for (int k = 0; k < unread.size(); k++) {
            for (final FrostlakeParser.SelectOperandContext arm : arms) {
                items.add(itemAt(arm, unread.get(k)));
                replacements.add("NULL AS " + quoted(unreadNames.get(k)));
            }
        }
        // Blank from the last item back, so every earlier offset stays valid.
        final StringBuilder out = new StringBuilder(text);
        final boolean[] done = new boolean[items.size()];
        for (int n = 0; n < items.size(); n++) {
            int last = -1;
            for (int j = 0; j < items.size(); j++) {
                if (!done[j] && (last < 0 || items.get(j).getStart().getStartIndex()
                        > items.get(last).getStart().getStartIndex())) {
                    last = j;
                }
            }
            done[last] = true;
            final int start = items.get(last).getStart().getStartIndex();
            final int stop = items.get(last).getStop().getStopIndex();
            out.replace(start, stop + 1, padded(replacements.get(last), text.substring(start, stop + 1)));
        }
        return out.toString();
    }

    /** Whether the query block reading {@code reference} is cut to no row by LIMIT 0. */
    static boolean limitedToNothing(final ParserRuleContext reference) {
        final FrostlakeParser.SelectStatementContext statement =
            ancestor(reference, FrostlakeParser.SelectStatementContext.class);
        if (statement == null || statement.limitClause() == null) {
            return false;
        }
        final ParseTree count = statement.limitClause().getChild(1);
        return count instanceof TerminalNode
            && ((TerminalNode) count).getSymbol().getType() == FrostlakeParser.INTEGER_LITERAL
            && "0".equals(count.getText());
    }

    /** The WHERE conjuncts of the query block reading {@code reference} that are built from literals alone. */
    static List<ParserRuleContext> constantConjuncts(final ParserRuleContext reference) {
        final List<ParserRuleContext> constants = new ArrayList<ParserRuleContext>();
        final FrostlakeParser.SelectClauseContext block = ancestor(reference, FrostlakeParser.SelectClauseContext.class);
        if (block != null && block.whereClause() != null) {
            conjuncts(block.whereClause().booleanExpr(), constants);
        }
        return constants;
    }

    private static void conjuncts(final FrostlakeParser.BooleanExprContext expr,
                                  final List<ParserRuleContext> constants) {
        if (expr instanceof FrostlakeParser.AndExprContext) {
            conjuncts(((FrostlakeParser.AndExprContext) expr).booleanExpr(0), constants);
            conjuncts(((FrostlakeParser.AndExprContext) expr).booleanExpr(1), constants);
            return;
        }
        final List<TerminalNode> tokens = new ArrayList<TerminalNode>();
        terminals(expr, tokens);
        for (final TerminalNode token : tokens) {
            if (!CONSTANT_TOKENS.contains(token.getSymbol().getType())) {
                return;
            }
        }
        constants.add(expr);
    }

    private static <C extends ParserRuleContext> C ancestor(final ParseTree node, final Class<C> kind) {
        ParseTree at = node.getParent();
        while (at != null && !kind.isInstance(at)) {
            at = at.getParent();
        }
        return at == null ? null : kind.cast(at);
    }

    /** Collects the names under {@code node} outside {@code skipped}; false when a column is read unnamed. */
    private static boolean collect(final ParseTree node, final Set<String> names, final Set<ParseTree> skipped) {
        if (skipped != null && skipped.contains(node)) {
            return true;
        }
        if (node instanceof TerminalNode) {
            return collectToken((TerminalNode) node, names);
        }
        if (node instanceof FrostlakeParser.PivotClauseContext || node instanceof FrostlakeParser.UnpivotClauseContext
                || node instanceof FrostlakeParser.StarItemContext
                || node instanceof FrostlakeParser.QualifiedStarItemContext
                || node instanceof FrostlakeParser.ObjectStarItemContext) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree child = node.getChild(i);
            if (!definesName(node, child) && !collect(child, names, skipped)) {
                return false;
            }
        }
        return true;
    }

    private static boolean collectToken(final TerminalNode terminal, final Set<String> names) {
        final Token token = terminal.getSymbol();
        switch (token.getType()) {
            case FrostlakeParser.POSITIONAL_PARAMETER:
            case FrostlakeParser.NATURAL:
            case FrostlakeParser.KW_IDENTIFIER:
            case FrostlakeParser.KW_IDENTIFIER_REF:
                return false;
            case FrostlakeParser.STAR:
                return starReadsNothing(terminal);
            case FrostlakeParser.QUOTED_IDENTIFIER:
                names.add(unquoted(token.getText()).toUpperCase(Locale.ROOT));
                return true;
            default:
                names.add(token.getText().toUpperCase(Locale.ROOT));
                return true;
        }
    }

    /** Whether {@code child} DEFINES a name rather than reading one: a select item's alias, a column list. */
    private static boolean definesName(final ParseTree parent, final ParseTree child) {
        if (parent instanceof FrostlakeParser.ExprItemContext) {
            return child instanceof FrostlakeParser.AliasNameContext || child instanceof FrostlakeParser.IdentifierContext;
        }
        return parent instanceof FrostlakeParser.TableReferenceContext && child instanceof FrostlakeParser.IdentifierListContext
            || parent instanceof FrostlakeParser.CteDefinitionContext
                && child instanceof FrostlakeParser.ColumnListOptionalContext;
    }

    /** Whether this star leaves every column unread: the multiplication operator, or COUNT(*). */
    private static boolean starReadsNothing(final TerminalNode star) {
        final ParseTree parent = star.getParent();
        if (parent instanceof FrostlakeParser.MultiplicativeExprContext) {
            return true;
        }
        if (parent instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext call = (FrostlakeParser.FunctionCallStarExprContext) parent;
            return call.DISTINCT() == null && call.starQualifiedName() == null
                && "COUNT".equals(SqlIdentifiers.canonicalText(call.functionName().getText()).toUpperCase(Locale.ROOT));
        }
        if (parent instanceof FrostlakeParser.FunctionArgContext) {
            // COUNT(*) OVER (…) — the star is an ARGUMENT there, and reads no column either.
            final FrostlakeParser.FunctionArgContext argument = (FrostlakeParser.FunctionArgContext) parent;
            final FrostlakeParser.FunctionCallExprContext call =
                ancestor(argument, FrostlakeParser.FunctionCallExprContext.class);
            return argument.starQualifiedName() == null && call != null && call.DISTINCT() == null
                && "COUNT".equals(SqlIdentifiers.canonicalText(call.functionName().getText()).toUpperCase(Locale.ROOT));
        }
        return false;
    }

    private static boolean prunable(final FrostlakeParser.SelectStatementContext body, final FunctionRegistry functions) {
        if (!collect(body, new HashSet<String>(), null)) {
            return false;   // the body itself reads some column unnamed
        }
        for (final FrostlakeParser.SetOperatorContext op : body.setOperator()) {
            if (op.UNION() == null || op.ALL() == null || op.BY() != null) {
                return false;
            }
        }
        if (body.orderByClause() != null) {
            for (final FrostlakeParser.OrderItemContext item : body.orderByClause().orderItem()) {
                if (isOrdinal(item.expression())) {
                    return false;
                }
            }
        }
        for (final FrostlakeParser.SelectOperandContext arm : body.selectOperand()) {
            final FrostlakeParser.SelectClauseContext clause = arm.selectClause();
            if (clause == null || clause.DISTINCT() != null || clause.connectByClause() != null) {
                return false;
            }
            final FrostlakeParser.GroupByClauseContext groupBy = clause.groupByClause();
            if (groupBy == null) {
                // Without GROUP BY a blanked aggregate would turn the body into a row-per-row query.
                if (clause.havingClause() != null || containsAggregate(clause.selectList(), functions)) {
                    return false;
                }
            } else {
                if (groupBy.ALL() != null) {
                    return false;
                }
                for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
                    if (element.expression() != null && isOrdinal(element.expression())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** As the window evaluator decides it: an aggregate call not under OVER, outside any subquery. */
    private static boolean containsAggregate(final ParseTree node, final FunctionRegistry functions) {
        if (node instanceof FrostlakeParser.SelectStatementContext || node instanceof FrostlakeParser.OverClauseContext) {
            return false;
        }
            if (node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == FrostlakeParser.WITHIN) {
                return true;    // LISTAGG … WITHIN GROUP and its kin aggregate too
            }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            if (call.overClause() == null && isAggregate(call.functionName().getText(), functions)) {
                return true;
            }
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext
                && isAggregate(((FrostlakeParser.FunctionCallStarExprContext) node).functionName().getText(), functions)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsAggregate(node.getChild(i), functions)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAggregate(final String written, final FunctionRegistry functions) {
        return functions.hasAggregateFunction(SqlIdentifiers.canonicalText(written).toUpperCase(Locale.ROOT));
    }

    private static boolean isOrdinal(final ParseTree expression) {
        final List<TerminalNode> tokens = new ArrayList<TerminalNode>();
        terminals(expression, tokens);
        return tokens.size() == 1 && tokens.get(0).getSymbol().getType() == FrostlakeParser.INTEGER_LITERAL;
    }

    private static boolean expressionInEveryArm(final List<FrostlakeParser.SelectOperandContext> arms, final int index) {
        for (final FrostlakeParser.SelectOperandContext arm : arms) {
            final List<FrostlakeParser.SelectItemContext> items = arm.selectClause().selectList().selectItem();
            if (index >= items.size() || !(items.get(index) instanceof FrostlakeParser.ExprItemContext)) {
                return false;
            }
        }
        return true;
    }

    private static ParserRuleContext itemAt(final FrostlakeParser.SelectOperandContext arm, final int index) {
        return arm.selectClause().selectList().selectItem(index);
    }

    /** The column a select item names: its alias, or a bare column reference's own name; null otherwise. */
    private static String nameOf(final FrostlakeParser.SelectItemContext item) {
        if (!(item instanceof FrostlakeParser.ExprItemContext)) {
            return null;
        }
        final FrostlakeParser.ExprItemContext expr = (FrostlakeParser.ExprItemContext) item;
        if (expr.aliasName() != null) {
            return ParseTreeText.getIdentifier(expr.aliasName());
        }
        if (expr.identifier() != null) {
            return ParseTreeText.getIdentifier(expr.identifier());
        }
        final List<TerminalNode> tokens = new ArrayList<TerminalNode>();
        terminals(expr.booleanExpr(), tokens);
        if (tokens.size() != 1) {
            return null;
        }
        final Token only = tokens.get(0).getSymbol();
        if (only.getType() == FrostlakeParser.IDENTIFIER) {
            return only.getText().toUpperCase(Locale.ROOT);
        }
        return only.getType() == FrostlakeParser.QUOTED_IDENTIFIER ? unquoted(only.getText()) : null;
    }

    private static void terminals(final ParseTree node, final List<TerminalNode> out) {
        if (node instanceof TerminalNode) {
            out.add((TerminalNode) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            terminals(node.getChild(i), out);
        }
    }

    /** The replacement, then the original's own line breaks, so the lines after it keep their numbers. */
    private static String padded(final String replacement, final String original) {
        final StringBuilder sb = new StringBuilder(replacement);
        for (int i = 0; i < original.length(); i++) {
            final char c = original.charAt(i);
            if (i >= replacement.length()) {
                sb.append(c == '\n' || c == '\r' ? c : ' ');
            } else if (c == '\n') {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String quoted(final String name) {
        return '"' + name.replace("\"", "\"\"") + '"';
    }

    private static String unquoted(final String text) {
        return text.substring(1, text.length() - 1).replace("\"\"", "\"");
    }
}
