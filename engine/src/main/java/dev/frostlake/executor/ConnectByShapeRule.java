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
import dev.frostlake.parser.SqlSyntaxException;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a CONNECT BY query refuses that a query reads elsewhere (live-verified). The account plans the hierarchy as a
 * recursive CTE, so its pseudo-columns stand only where the select list reads them:
 *
 * <ul>
 *   <li>CONNECT_BY_ROOT inside a call is a syntax error at the column it names, the line given twice:
 *       {@code MAX(CONNECT_BY_ROOT id)};</li>
 *   <li>SYS_CONNECT_BY_PATH inside a call is {@code Unknown function SYS_CONNECT_BY_PATH.};</li>
 *   <li>a window call anywhere is {@code Window functions are not allowed in a CTEs recursive term.};</li>
 *   <li>LEVEL, bare or qualified, inside an aggregate's arguments, in the WHERE, or in the ORDER BY of a select
 *       list that projects no LEVEL is an invalid identifier at it: {@code MAX(LEVEL)}, {@code MAX(e.level)},
 *       {@code WHERE LEVEL > 1}, {@code SELECT id … ORDER BY LEVEL}.</li>
 * </ul>
 *
 * LEVEL, an expression over it and CONNECT_BY_ROOT as select items, an aggregate over a source column, and a
 * derived table reading LEVEL are answered.
 */
final class ConnectByShapeRule {

    private ConnectByShapeRule() {
    }

    /**
     * Refuses what a CONNECT BY query may not hold, in the order the account judges it.
     *
     * @param clause    the query's select clause, the one with the CONNECT BY
     * @param orderBy   the statement's ORDER BY, or null
     * @param functions the registry that tells an aggregate
     * @param sql       the statement's text, for a syntax error
     */
    static void require(final FrostlakeParser.SelectClauseContext clause,
                        final FrostlakeParser.OrderByClauseContext orderBy, final FunctionRegistry functions,
                        final String sql) {
        final List<ParseTree> judged = new ArrayList<ParseTree>();
        judged.add(clause);
        if (orderBy != null) {
            judged.add(orderBy);
        }
        for (final ParseTree node : judged) {
            final FrostlakeParser.ConnectByRootExprContext root = rootInsideCall(node);
            if (root != null) {
                final Token at = root.qualifiedName().getStart();
                final String line = "syntax error line " + at.getLine() + " at position " + at.getCharPositionInLine()
                    + " unexpected '" + at.getText() + "'.";
                final List<String> lines = new ArrayList<String>();
                lines.add(line);
                lines.add(line);
                throw new SqlSyntaxException(SqlCompilationError.of(line + "\n" + line), lines, sql);
            }
        }
        for (final ParseTree node : judged) {
            if (pathInsideCall(node)) {
                throw new RuntimeException(SqlCompilationError.of("Unknown function SYS_CONNECT_BY_PATH."));
            }
        }
        for (final ParseTree node : judged) {
            if (holdsWindow(node)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Window functions are not allowed in a CTEs recursive term."));
            }
        }
        final List<FrostlakeParser.QualifiedNameExprContext> levels =
            new ArrayList<FrostlakeParser.QualifiedNameExprContext>();
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            levelsInAggregates(item, functions, false, levels);
        }
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(clause);
        if (where != null) {
            levelsInAggregates(where, functions, true, levels);
        }
        if (orderBy != null && !projectsLevel(clause)) {
            // An ORDER BY reads the select list's LEVEL where it projects one (live-verified).
            levelsInAggregates(orderBy, functions, true, levels);
        }
        if (!levels.isEmpty()) {
            final FrostlakeParser.QualifiedNameExprContext level = levels.get(0);
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(level.getStart().getLine(),
                level.getStart().getCharPositionInLine(),
                ParseTreeText.getOriginalText(level).toUpperCase(Locale.ROOT)));
        }
    }

    /** The first CONNECT_BY_ROOT a call's arguments hold, outside any nested select. */
    private static FrostlakeParser.ConnectByRootExprContext rootInsideCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return null;
        }
        if (node instanceof FrostlakeParser.ConnectByRootExprContext && insideCall(node)) {
            return (FrostlakeParser.ConnectByRootExprContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.ConnectByRootExprContext found = rootInsideCall(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Whether a SYS_CONNECT_BY_PATH call stands inside another call, outside any nested select. */
    private static boolean pathInsideCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext && "SYS_CONNECT_BY_PATH".equalsIgnoreCase(
                ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText()) && insideCall(node)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (pathInsideCall(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a node stands among a call's arguments before the select item or clause it belongs to. */
    private static boolean insideCall(final ParseTree node) {
        for (ParseTree at = node.getParent(); at != null; at = at.getParent()) {
            if (isCall(at)) {
                return true;
            }
            if (at instanceof FrostlakeParser.SelectItemContext || at instanceof FrostlakeParser.SelectStatementContext
                    || at instanceof FrostlakeParser.WhereClauseContext) {
                return false;
            }
        }
        return false;
    }

    private static boolean isCall(final ParseTree node) {
        return node instanceof FrostlakeParser.FunctionCallExprContext
            || node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
            || node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext
            || node instanceof FrostlakeParser.FunctionCallStarExprContext
            || node instanceof FrostlakeParser.SystemFuncExprContext;
    }

    /** Whether a node holds a window call, outside any nested select. */
    private static boolean holdsWindow(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.OverClauseContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsWindow(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects the LEVEL references a node holds where the account refuses them: anywhere when {@code everywhere}, or
     * else inside an aggregate's arguments, outside any nested select.
     */
    private static void levelsInAggregates(final ParseTree node, final FunctionRegistry functions,
                                           final boolean everywhere,
                                           final List<FrostlakeParser.QualifiedNameExprContext> levels) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            if (everywhere && namesLevel((FrostlakeParser.QualifiedNameExprContext) node)) {
                levels.add((FrostlakeParser.QualifiedNameExprContext) node);
            }
            return;
        }
        final boolean aggregate = isCall(node) && isAggregate((ParserRuleContext) node, functions);
        for (int i = 0; i < node.getChildCount(); i++) {
            levelsInAggregates(node.getChild(i), functions, everywhere || aggregate, levels);
        }
    }

    /** Whether the select list projects LEVEL: the bare name, or an item named LEVEL. */
    private static boolean projectsLevel(final FrostlakeParser.SelectClauseContext clause) {
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                continue;
            }
            final FrostlakeParser.ExprItemContext expression = (FrostlakeParser.ExprItemContext) item;
            final Token alias = expression.aliasName() != null ? expression.aliasName().getStop()
                : expression.identifier() != null ? expression.identifier().getStop()
                : expression.bareAliasWord() != null ? expression.bareAliasWord().getStop() : null;
            if (alias != null) {
                if (alias.getType() == FrostlakeParser.IDENTIFIER
                        && ConnectByExpander.LEVEL_COLUMN.equalsIgnoreCase(alias.getText())) {
                    return true;
                }
                continue;
            }
            ParseTree sole = expression.booleanExpr();
            while (sole.getChildCount() == 1 && !(sole instanceof FrostlakeParser.QualifiedNameExprContext)) {
                sole = sole.getChild(0);
            }
            if (sole instanceof FrostlakeParser.QualifiedNameExprContext
                    && namesLevel((FrostlakeParser.QualifiedNameExprContext) sole)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a call names an aggregate function. */
    private static boolean isAggregate(final ParserRuleContext call, final FunctionRegistry functions) {
        final FrostlakeParser.FunctionNameContext name =
            call.getRuleContext(FrostlakeParser.FunctionNameContext.class, 0);
        return name != null && functions.getAggregateFunction(name.getText().toUpperCase(Locale.ROOT)) != null;
    }

    /** Whether a name reads the LEVEL pseudo-column, bare or qualified: its last part is the unquoted word. */
    private static boolean namesLevel(final FrostlakeParser.QualifiedNameExprContext name) {
        final Token last = name.qualifiedName().getStop();
        return last != null && last.getType() == FrostlakeParser.IDENTIFIER
            && ConnectByExpander.LEVEL_COLUMN.equalsIgnoreCase(last.getText());
    }
}
