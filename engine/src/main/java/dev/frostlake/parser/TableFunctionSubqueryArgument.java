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

package dev.frostlake.parser;

import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A table function's first argument that opens with a query in brackets is that query alone on the account: an
 * operator, a cast, a path, a bracket or a predicate after its closing bracket is a syntax error there, inside
 * {@code TABLE( … )} and after LATERAL alike, whether the argument is named or not. A later argument, a value that only
 * holds such a query and a call that is no table function's take any expression (all live-verified).
 *
 * <p>The account's recovery stacks the lines after it as follows, for a call in a FROM of the statement's own query:
 *
 * <pre>
 *   TABLE(GENERATOR(ROWCOUNT => (SELECT …) + 1))            '+', then the second ')' after it
 *   TABLE(GENERATOR(ROWCOUNT => (SELECT …) + ABS(1)))       '+' alone: a call after it is read through
 *   TABLE(GENERATOR(ROWCOUNT => (SELECT …)::INT))           '::' alone; after LATERAL the call's ')' too
 *   LATERAL FLATTEN(INPUT => (SELECT …) || '')              '||' alone
 *   TABLE(GENERATOR(ROWCOUNT => (SELECT …) + 1, TIMELIMIT => 1))
 *                                                           '+', then the '=>': past the comma the text reads on
 *                                                           as the FROM list's next tables, each '=>' refused
 *   TABLE(SPLIT_TO_TABLE((SELECT 'a') || 'b', ','))         '||', then the ')' after the string
 *   TABLE(SPLIT_TO_TABLE((SELECT 'a') || 'b', 5))           '||', then the '5'
 * </pre>
 *
 * A call in the FROM of a query written in brackets as a value is refused at that query's SELECT: the account takes
 * the bracket for a value once the query in it fails, and reads on from there in the context the bracket stands in.
 *
 * <pre>
 *   SELECT (SELECT COUNT(*) FROM TABLE(…))                  'SELECT', then the '(' after COUNT: the first name of
 *                                                           the select list is read as the item's alias
 *   WHERE n IN (SELECT COUNT(*) FROM TABLE(…))              'SELECT', then the FROM after the first ')': the list
 *                                                           runs to the first closing bracket
 * </pre>
 *
 * A call in any other query written in brackets is refused too, its lines worked out as for the statement's own.
 */
public final class TableFunctionSubqueryArgument {

    private static final IntervalSet NAME_START =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private TableFunctionSubqueryArgument() {
    }

    /**
     * Refuse the first table function call, in the order written, whose first argument opens with a query in
     * brackets and goes on past it.
     *
     * @param script the parsed script
     * @param tokens the token stream it was parsed from
     * @param sql    the script's source text
     */
    public static void requireAlone(final FrostlakeParser.SqlScriptContext script, final TokenStream tokens,
                                    final String sql) {
        if (script == null || tokens == null || !mayHoldOne(tokens)) {
            return;
        }
        ParserRuleContext firstSubquery = null;
        ParserRuleContext firstReference = null;
        Token firstClose = null;
        Token firstTableClose = null;
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(script);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
            if (!(node instanceof FrostlakeParser.TableReferenceContext)) {
                continue;
            }
            final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) node;
            final FrostlakeParser.TableSourceContext source = reference.tableSource();
            final boolean lateral = reference.LATERAL() != null
                || reference.getParent() instanceof FrostlakeParser.JoinClauseContext
                && ((FrostlakeParser.JoinClauseContext) reference.getParent()).LATERAL() != null;
            ParserRuleContext call = null;
            if (source != null && source.TABLE() != null && source.expression() != null) {
                call = source.expression();
            } else if (source != null && lateral && source.FLATTEN() != null) {
                call = source;
            } else if (source != null && lateral && source.tableFunctionExpr() != null) {
                call = source.tableFunctionExpr();
            }
            final Token open = call == null ? null : childToken(call, FrostlakeLexer.LPAREN);
            final Token close = call == null ? null : childToken(call, FrostlakeLexer.RPAREN);
            final ParserRuleContext subquery = open == null || close == null ? null
                : openingSubquery(call, firstValueStart(tokens, open));
            if (subquery != null && (firstSubquery == null
                    || subquery.getStop().getTokenIndex() < firstSubquery.getStop().getTokenIndex())) {
                firstSubquery = subquery;
                firstReference = reference;
                firstClose = close;
                firstTableClose = call == source.expression() ? childToken(source, FrostlakeLexer.RPAREN) : null;
            }
        }
        if (firstSubquery != null) {
            final List<String> enclosed = enclosedLines(tokens, firstReference);
            if (enclosed != null) {
                throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", enclosed)), enclosed, sql);
            }
            refuse(tokens, firstSubquery, firstClose, firstTableClose, sql);
        }
    }

    /**
     * The lines of a call in the FROM of a query written in brackets as a value: a select item's whole value or the
     * list of an IN. Null for a call in any other query.
     */
    private static List<String> enclosedLines(final TokenStream tokens, final ParserRuleContext reference) {
        ParseTree node = reference.getParent();
        while (node != null && !(node instanceof FrostlakeParser.SelectStatementContext)) {
            node = node.getParent();
        }
        if (node == null || ((FrostlakeParser.SelectStatementContext) node).getStart().getType()
                != FrostlakeLexer.SELECT) {
            return null;
        }
        final Token first = ((FrostlakeParser.SelectStatementContext) node).getStart();
        final ParseTree bracket = node.getParent();
        final int second;
        if (bracket instanceof FrostlakeParser.ScalarSubqueryExprContext
                && isWholeItem((ParserRuleContext) bracket)) {
            second = afterName(tokens, first.getTokenIndex());
        } else if (bracket instanceof FrostlakeParser.InSubqueryExprContext) {
            second = afterClose(tokens, first.getTokenIndex());
        } else {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(first));
        if (second >= 0) {
            lines.add(SyntaxErrorListener.sentence(tokens.get(second)));
        }
        return lines;
    }

    /** Whether a bracket is the whole value of a select item. */
    private static boolean isWholeItem(final ParserRuleContext bracket) {
        ParseTree node = bracket.getParent();
        while (node instanceof ParserRuleContext && !(node instanceof FrostlakeParser.ExprItemContext)) {
            final ParserRuleContext rule = (ParserRuleContext) node;
            if (rule.getStart() != bracket.getStart() || rule.getStop() != bracket.getStop()) {
                return false;
            }
            node = node.getParent();
        }
        return node instanceof FrostlakeParser.ExprItemContext;
    }

    /** The token after the name that follows the token {@code first}, or -1 where no name follows it. */
    private static int afterName(final TokenStream tokens, final int first) {
        final int name = next(tokens, first);
        return name >= 0 && NAME_START.contains(tokens.get(name).getType()) ? next(tokens, name) : -1;
    }

    /** The token after the first closing bracket past the token {@code first}, or -1 where a comma comes first. */
    private static int afterClose(final TokenStream tokens, final int first) {
        for (int i = next(tokens, first); i >= 0; i = next(tokens, i)) {
            final int type = tokens.get(i).getType();
            if (type == FrostlakeLexer.COMMA) {
                return -1;
            }
            if (type == FrostlakeLexer.RPAREN) {
                return next(tokens, i);
            }
        }
        return -1;
    }

    /** Whether a TABLE or a LATERAL has a query in brackets after it somewhere: the walk is due only then. */
    private static boolean mayHoldOne(final TokenStream tokens) {
        boolean called = false;
        for (int i = 0; i + 1 < tokens.size(); i++) {
            final int type = tokens.get(i).getType();
            if (type == FrostlakeLexer.TABLE || type == FrostlakeLexer.LATERAL) {
                called = true;
            } else if (called && type == FrostlakeLexer.LPAREN && next(tokens, i) >= 0) {
                final int after = tokens.get(next(tokens, i)).getType();
                if (after == FrostlakeLexer.SELECT || after == FrostlakeLexer.WITH) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Where the first argument's value starts: after the call's bracket, and after its name and arrow when named. */
    private static int firstValueStart(final TokenStream tokens, final Token open) {
        final int first = next(tokens, open.getTokenIndex());
        final int arrow = first < 0 ? -1 : next(tokens, first);
        return arrow >= 0 && tokens.get(arrow).getType() == FrostlakeLexer.ARROW ? next(tokens, arrow) : first;
    }

    /**
     * The query in brackets the value starting at {@code start} opens with, when the value goes on past it; null
     * otherwise.
     */
    private static ParserRuleContext openingSubquery(final ParserRuleContext call, final int start) {
        final ParserRuleContext value = outermostAt(call, start);
        if (value == null) {
            return null;
        }
        ParseTree node = value;
        while (node instanceof ParserRuleContext && node.getChildCount() > 0) {
            if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
                final ParserRuleContext subquery = (ParserRuleContext) node;
                return subquery.getStop().getTokenIndex() < value.getStop().getTokenIndex() ? subquery : null;
            }
            node = node.getChild(0);
        }
        return null;
    }

    /** The outermost expression under {@code node} that starts at the token {@code start}. */
    private static ParserRuleContext outermostAt(final ParserRuleContext node, final int start) {
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree child = node.getChild(i);
            if (!(child instanceof ParserRuleContext)) {
                continue;
            }
            final ParserRuleContext rule = (ParserRuleContext) child;
            if (rule.getStart() == null || rule.getStop() == null || rule.getStart().getTokenIndex() > start
                    || rule.getStop().getTokenIndex() < start) {
                continue;
            }
            if (rule.getStart().getTokenIndex() == start && (rule instanceof FrostlakeParser.ExpressionContext
                    || rule instanceof FrostlakeParser.BooleanExprContext)) {
                return rule;
            }
            return outermostAt(rule, start);
        }
        return null;
    }

    private static void refuse(final TokenStream tokens, final ParserRuleContext subquery, final Token close,
                               final Token tableClose, final String sql) {
        final int fault = next(tokens, subquery.getStop().getTokenIndex());
        final Token refused = tokens.get(fault);
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(refused));
        final int comma = commaBefore(tokens, fault, close.getTokenIndex());
        final int type = refused.getType();
        if (comma >= 0) {
            readAsTables(tokens, comma, lines);
        } else if (type == FrostlakeLexer.DOUBLE_COLON || type == FrostlakeLexer.COLON
                || type == FrostlakeLexer.COLLATE) {
            if (tableClose == null) {
                lines.add(SyntaxErrorListener.sentence(close));
            }
        } else if (tableClose != null && !holdsCall(tokens, fault, close.getTokenIndex())) {
            final int second = closingBracket(tokens, closingBracket(tokens, fault));
            if (second >= 0) {
                lines.add(SyntaxErrorListener.sentence(tokens.get(second)));
            }
        }
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }

    /**
     * The lines the text past a comma of the call's arguments earns, read as the FROM list's next tables: a name or a
     * string is a table, and the token after it that is no comma is refused; a '=>' is read past to the next comma.
     */
    private static void readAsTables(final TokenStream tokens, final int comma, final List<String> lines) {
        int at = comma;
        while (at >= 0) {
            int item = next(tokens, at);
            if (item < 0) {
                return;
            }
            final Token first = tokens.get(item);
            if (first.getType() == FrostlakeLexer.STRING_LITERAL) {
                item = next(tokens, item);
            } else if (NAME_START.contains(first.getType())) {
                item = next(tokens, item);
                while (item >= 0 && tokens.get(item).getType() == FrostlakeLexer.DOT && next(tokens, item) >= 0
                        && NAME_START.contains(tokens.get(next(tokens, item)).getType())) {
                    item = next(tokens, next(tokens, item));
                }
            } else {
                lines.add(SyntaxErrorListener.sentence(first));
                return;
            }
            if (item < 0) {
                return;
            }
            final Token after = tokens.get(item);
            if (after.getType() == FrostlakeLexer.COMMA) {
                at = item;
                continue;
            }
            lines.add(SyntaxErrorListener.sentence(after));
            at = after.getType() == FrostlakeLexer.ARROW ? commaBefore(tokens, item, tokens.size()) : -1;
        }
    }

    /** The first comma at the depth of {@code from} before {@code until}, or -1. */
    private static int commaBefore(final TokenStream tokens, final int from, final int until) {
        int depth = 0;
        for (int i = from; i < until && i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int type = token.getType();
            if (type == Token.EOF) {
                return -1;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth < 0) {
                    return -1;
                }
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Whether a name with a bracket after it, a call, stands between two tokens. */
    private static boolean holdsCall(final TokenStream tokens, final int from, final int until) {
        for (int i = from; i >= 0 && i < until; i = next(tokens, i)) {
            final int after = next(tokens, i);
            if (after < 0) {
                return false;
            }
            if (NAME_START.contains(tokens.get(i).getType()) && tokens.get(after).getType() == FrostlakeLexer.LPAREN) {
                return true;
            }
        }
        return false;
    }

    /** The first closing bracket after the token {@code from}, at any depth, or -1. */
    private static int closingBracket(final TokenStream tokens, final int from) {
        if (from < 0) {
            return -1;
        }
        for (int i = next(tokens, from); i >= 0; i = next(tokens, i)) {
            if (tokens.get(i).getType() == FrostlakeLexer.RPAREN) {
                return i;
            }
        }
        return -1;
    }

    /** The next default-channel token after {@code from}, the end of input excluded, or -1. */
    private static int next(final TokenStream tokens, final int from) {
        for (int i = from + 1; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getType() == Token.EOF) {
                return -1;
            }
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                return i;
            }
        }
        return -1;
    }

    /** The first token of a type among a rule's own children. */
    private static Token childToken(final ParserRuleContext rule, final int type) {
        for (int i = 0; i < rule.getChildCount(); i++) {
            final ParseTree child = rule.getChild(i);
            if (child instanceof TerminalNode && ((TerminalNode) child).getSymbol().getType() == type) {
                return ((TerminalNode) child).getSymbol();
            }
        }
        return null;
    }
}
