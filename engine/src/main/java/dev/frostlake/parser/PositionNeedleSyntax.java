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

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The first argument of POSITION's comma form is a value below the comparison operators on the account: a
 * comparison, a LIKE / ILIKE / RLIKE, a BETWEEN, an AND or an OR there is a syntax error at that operator,
 * and so is a leading NOT or EXISTS, where Frostlake parsed the argument and refused its type (all
 * live-verified). IS NULL and IS DISTINCT FROM are values at that level, and a parenthesized predicate is an
 * ordinary operand. The later arguments take any expression.
 *
 * <p>Live's recovery stacks a second line. After an infix operator it is the first closing parenthesis left
 * unmatched once POSITION's own opening one is set aside: POSITION's own at the top level, the enclosing
 * call's inside {@code UPPER(POSITION(1 = 1, 'x'))}. After a leading NOT or EXISTS it is POSITION's opening
 * parenthesis itself:
 *
 * <pre>
 *   SELECT POSITION(1 = 1, 'x')          unexpected '=' at 18, unexpected ')' at 26
 *   SELECT UPPER(POSITION(1 = 1, 'x'))   unexpected '=' at 24, unexpected ')' at 33
 *   SELECT POSITION(NOT TRUE, 'x')       unexpected 'NOT' at 16, unexpected '(' at 15
 * </pre>
 */
public final class PositionNeedleSyntax {

    private static final String POSITION = "POSITION";

    private PositionNeedleSyntax() {
    }

    /**
     * Refuse the first POSITION call, in the order written, whose first argument is a predicate.
     *
     * @param script the parsed script
     * @param tokens the token stream it was parsed from
     * @param sql the script's source text
     */
    public static void requireValueNeedles(final FrostlakeParser.SqlScriptContext script,
                                           final TokenStream tokens, final String sql) {
        if (script == null || tokens == null || !mayCallPosition(tokens)) {
            return;
        }
        Token first = null;
        FrostlakeParser.FunctionCallExprContext firstCall = null;
        FrostlakeParser.PositionInExprContext firstInForm = null;
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(script);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (node instanceof FrostlakeParser.FunctionCallExprContext) {
                final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
                final Token refused = refusedNeedle(call);
                if (refused != null && (first == null || refused.getTokenIndex() < first.getTokenIndex())) {
                    first = refused;
                    firstCall = call;
                    firstInForm = null;
                }
            } else if (node instanceof FrostlakeParser.PositionInExprContext) {
                final FrostlakeParser.PositionInExprContext inForm = (FrostlakeParser.PositionInExprContext) node;
                final Token refused = isBarePosition(inForm.functionName()) ? refusedIn(inForm.expression(0)) : null;
                if (refused != null && (first == null || refused.getTokenIndex() < first.getTokenIndex())) {
                    first = refused;
                    firstCall = null;
                    firstInForm = inForm;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        if (firstInForm != null) {
            final List<String> lines = new ArrayList<>();
            lines.add(sentence(first));
            lines.addAll(inFormTail(tokens, firstInForm.LPAREN().getSymbol(), firstInForm.IN().getSymbol(), first));
            throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
        }
        if (first != null) {
            final boolean leading = first.getTokenIndex() == firstCall.LPAREN().getSymbol().getTokenIndex() + 1
                && (first.getType() == FrostlakeLexer.NOT || first.getType() == FrostlakeLexer.EXISTS);
            refuse(first, leading ? firstCall.LPAREN().getSymbol() : unmatchedClose(tokens, first), sql);
        }
    }

    /** Whether a call's name is the bare, unqualified POSITION. */
    private static boolean isBarePosition(final FrostlakeParser.FunctionNameContext name) {
        return name != null && name.getChildCount() == 1 && POSITION.equalsIgnoreCase(name.getText());
    }

    /** Whether the statement names POSITION at all, so the walk runs only where it can find something. */
    private static boolean mayCallPosition(final TokenStream tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL && POSITION.equalsIgnoreCase(token.getText())) {
                return true;
            }
        }
        return false;
    }

    /** The operator refused in the first argument of an unqualified POSITION call, or null. */
    private static Token refusedNeedle(final FrostlakeParser.FunctionCallExprContext call) {
        final FrostlakeParser.FunctionNameContext name = call.functionName();
        if (name == null || name.getChildCount() != 1 || !POSITION.equalsIgnoreCase(name.getText())
                || call.functionArgList() == null || call.functionArgList().functionArg().isEmpty()) {
            return null;
        }
        final FrostlakeParser.BooleanExprContext needle = call.functionArgList().functionArg(0).booleanExpr();
        return needle == null ? null : refusedIn(needle);
    }

    /** The first operator, in the order written, that ends a value at this level, or null. */
    private static Token refusedIn(final ParseTree node) {
        if (node instanceof FrostlakeParser.NotExprContext) {
            return ((FrostlakeParser.NotExprContext) node).NOT().getSymbol();
        }
        if (node instanceof FrostlakeParser.AndExprContext) {
            final FrostlakeParser.AndExprContext and = (FrostlakeParser.AndExprContext) node;
            return leftOr(refusedIn(and.booleanExpr(0)), and.AND());
        }
        if (node instanceof FrostlakeParser.OrExprContext) {
            final FrostlakeParser.OrExprContext or = (FrostlakeParser.OrExprContext) node;
            return leftOr(refusedIn(or.booleanExpr(0)), or.OR());
        }
        if (node instanceof FrostlakeParser.ValueExprContext) {
            return refusedIn(((FrostlakeParser.ValueExprContext) node).expression());
        }
        if (node instanceof FrostlakeParser.ExistsExprContext) {
            return ((FrostlakeParser.ExistsExprContext) node).EXISTS().getSymbol();
        }
        if (node instanceof FrostlakeParser.ComparisonExprContext) {
            final FrostlakeParser.ComparisonExprContext comparison = (FrostlakeParser.ComparisonExprContext) node;
            final Token left = refusedIn(comparison.expression(0));
            return left != null ? left : comparison.op;
        }
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            final FrostlakeParser.QuantifiedComparisonExprContext comparison =
                (FrostlakeParser.QuantifiedComparisonExprContext) node;
            final Token left = refusedIn(comparison.expression());
            return left != null ? left : comparison.op;
        }
        if (node instanceof FrostlakeParser.LikeExprContext) {
            final FrostlakeParser.LikeExprContext like = (FrostlakeParser.LikeExprContext) node;
            return leftOr(refusedIn(like.expression(0)), firstOf(like.NOT(), like.LIKE(), like.ILIKE()));
        }
        if (node instanceof FrostlakeParser.LikeAnyAllExprContext) {
            final FrostlakeParser.LikeAnyAllExprContext like = (FrostlakeParser.LikeAnyAllExprContext) node;
            return leftOr(refusedIn(like.expression(0)), firstOf(null, like.LIKE(), like.ILIKE()));
        }
        if (node instanceof FrostlakeParser.RlikeExprContext) {
            final FrostlakeParser.RlikeExprContext rlike = (FrostlakeParser.RlikeExprContext) node;
            return leftOr(refusedIn(rlike.expression(0)), firstOf(rlike.NOT(), rlike.RLIKE(), rlike.REGEXP()));
        }
        if (node instanceof FrostlakeParser.BetweenExprContext) {
            final FrostlakeParser.BetweenExprContext between = (FrostlakeParser.BetweenExprContext) node;
            return leftOr(refusedIn(between.expression(0)), firstOf(between.NOT(), between.BETWEEN(), null));
        }
        if (node instanceof FrostlakeParser.IsNullExprContext) {
            return refusedIn(((FrostlakeParser.IsNullExprContext) node).expression());
        }
        if (node instanceof FrostlakeParser.IsDistinctExprContext) {
            return refusedIn(((FrostlakeParser.IsDistinctExprContext) node).expression(0));
        }
        return null;
    }

    /** The refusal inside the left operand when there is one, else the operator's own token. */
    private static Token leftOr(final Token left, final TerminalNode operator) {
        if (left != null) {
            return left;
        }
        return operator == null ? null : operator.getSymbol();
    }

    /** The first of the terminals present, in the order given. */
    private static TerminalNode firstOf(final TerminalNode first, final TerminalNode second, final TerminalNode third) {
        if (first != null) {
            return first;
        }
        return second != null ? second : third;
    }

    /**
     * The line live stacks after a comma that breaks POSITION's IN form — {@code POSITION('a' IN 'abc', 'x')} is
     * "unexpected ','" and then "unexpected ')'" at the parenthesis the same recovery trips over — or null when
     * the refused token is no such comma. Read off the tokens: the comma's innermost open parenthesis follows the
     * bare name POSITION, and an IN stands between them outside any inner parentheses.
     *
     * @param tokens  the token stream the parse reads
     * @param refused the token the parser refused
     * @return the stacked token, or null
     */
    static Token stackedAfterInForm(final TokenStream tokens, final Token refused) {
        if (refused.getType() != FrostlakeLexer.COMMA) {
            return null;
        }
        if (tokens instanceof BufferedTokenStream) {
            ((BufferedTokenStream) tokens).fill();
        }
        int depth = 0;
        boolean sawIn = false;
        for (int i = refused.getTokenIndex() - 1; i >= 0; i--) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.LPAREN) {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                final Token name = i > 0 ? previousSpoken(tokens, i) : null;
                final Token beforeName = name == null ? null : previousSpoken(tokens, name.getTokenIndex());
                final boolean bareName = name != null && POSITION.equalsIgnoreCase(name.getText())
                    && (beforeName == null || beforeName.getType() != FrostlakeLexer.DOT);
                return bareName && sawIn ? unmatchedClose(tokens, refused) : null;
            } else if (depth == 0 && token.getType() == FrostlakeLexer.IN) {
                sawIn = true;
            }
        }
        return null;
    }

    /**
     * The lines live reports when this parser's first fault is the second half of a comparison written inside the
     * first argument of POSITION, or null for any other first fault. {@code 1 <=> 1} lexes as '<=' then '>', so the
     * parse fails on the '>' before the needle rule can refuse the '<='. Live refuses the needle's first comparison,
     * then the fault, then every later '>' that follows a '<=' in the needle; then, in the comma form, the closing
     * parenthesis {@link #unmatchedClose} finds after the fault, and in the IN form what {@link #inFormTail} reports
     * (all live-verified):
     *
     * <pre>
     *   SELECT POSITION(1 &lt;=&gt; 1, 'x')            unexpected '&lt;=' at 18, '&gt;' at 20, ')' at 28
     *   SELECT UPPER(POSITION(1 &lt;=&gt; 1, 'x'))     unexpected '&lt;=' at 24, '&gt;' at 26, ')' at 35
     *   SELECT POSITION(1 = &gt; 1, 'x')            unexpected '=' at 18, '&gt;' at 20, ')' at 28
     *   SELECT POSITION(1 &lt;=&gt;, 'x')              unexpected '&lt;=' at 18, '&gt;' at 20, ',' at 21, ')' at 26
     *   SELECT POSITION(1 = 1 &lt;=&gt; 1, 'x')        unexpected '=' at 18, '&gt;' at 24, ')' at 32
     *   SELECT POSITION(1 &lt;=&gt; 1 &lt;=&gt; 1, 'x')      unexpected '&lt;=' at 18, '&gt;' at 20, '&gt;' at 26, ')' at 34
     *   SELECT POSITION(1 &lt;=&gt; 1 IN 'x')          unexpected '&lt;=' at 18, '&gt;' at 20, ''x'' at 27
     * </pre>
     *
     * @param sql        the text parsed
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> linesAroundOperatorFault(final String sql, final int faultIndex) {
        if (sql == null || faultIndex < 1) {
            return null;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        if (faultIndex >= tokens.size()) {
            return null;
        }
        final Token fault = tokens.get(faultIndex);
        final Token operator = previousSpoken(tokens, faultIndex);
        final Token after = nextSpoken(tokens, faultIndex);
        if (fault.getType() == Token.EOF || operator == null || !isComparison(operator.getType()) || after == null) {
            return null;
        }
        final Token open = needleOpen(tokens, operator);
        final Token end = open == null ? null : needleEnd(tokens, operator);
        if (end == null || end.getTokenIndex() < faultIndex) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(sentence(firstComparison(tokens, open, operator)));
        lines.add(sentence(fault));
        int depth = 0;
        for (int i = faultIndex + 1; i < end.getTokenIndex(); i++) {
            final Token each = tokens.get(i);
            if (each.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (each.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (each.getType() == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && each.getType() == FrostlakeLexer.GT) {
                final Token before = previousSpoken(tokens, i);
                if (before != null && before.getType() == FrostlakeLexer.LTE) {
                    lines.add(sentence(each));
                }
            }
        }
        if (end.getType() == FrostlakeLexer.IN) {
            lines.addAll(inFormTail(tokens, open, end, fault));
            return lines;
        }
        if (after.getType() == FrostlakeLexer.COMMA) {
            // The comparison has no right operand: the comma is refused as well.
            lines.add(sentence(after));
        }
        final Token close = unmatchedClose(tokens, fault);
        if (close != null) {
            lines.add(sentence(close));
        }
        return lines;
    }

    /**
     * The lines live reports after the needle of POSITION's IN form: the token after the IN when it is not a
     * parenthesis — and then the token after it for a name, when the call stands in no other bracket, or the
     * closing parenthesis left over for a value in one or before a comma — and after a parenthesized haystack the
     * closing parenthesis {@link #unmatchedClose} finds (all live-verified):
     *
     * <pre>
     *   SELECT POSITION(1 = 1 IN 'x')                 unexpected '=', then ''x''
     *   SELECT POSITION(1 &lt;=&gt; 1 IN x)                '&lt;=', '&gt;', 'x', then ')' at 28
     *   SELECT UPPER(POSITION(1 &lt;=&gt; 1 IN 'x'))       '&lt;=', '&gt;', ''x'', then ')' at 37
     *   SELECT POSITION(1 &lt;=&gt; 1 IN ('x'))            '&lt;=', '&gt;', then ')' at 32
     *   SELECT POSITION(1 &lt;=&gt; 1 IN 'x', 'y')         '&lt;=', '&gt;', ''x'', then ')' at 35
     * </pre>
     *
     * @param tokens  the token stream
     * @param open    POSITION's opening parenthesis
     * @param in      the IN that ends the needle
     * @param refused a token of the needle already refused
     * @return the lines, possibly none
     */
    private static List<String> inFormTail(final TokenStream tokens, final Token open, final Token in,
                                           final Token refused) {
        final List<String> lines = new ArrayList<>();
        final Token haystack = nextSpoken(tokens, in.getTokenIndex());
        if (haystack == null || haystack.getType() == Token.EOF) {
            return lines;
        }
        if (haystack.getType() == FrostlakeLexer.LPAREN) {
            final Token close = unmatchedClose(tokens, refused);
            if (close != null) {
                lines.add(sentence(close));
            }
            return lines;
        }
        lines.add(sentence(haystack));
        if (haystack.getType() == FrostlakeLexer.RPAREN) {
            return lines;
        }
        final boolean enclosed = openBefore(tokens, open) > 0;
        final ATN atn = FrostlakeParser._ATN;
        if (atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(haystack.getType())) {
            final Token next = enclosed ? null : nextSpoken(tokens, haystack.getTokenIndex());
            if (next != null && next.getType() != Token.EOF) {
                lines.add(sentence(next));
            }
        } else if (enclosed || isComma(nextSpoken(tokens, haystack.getTokenIndex()))) {
            final Token close = unmatchedClose(tokens, haystack);
            if (close != null) {
                lines.add(sentence(close));
            }
        }
        return lines;
    }

    private static boolean isComma(final Token token) {
        return token != null && token.getType() == FrostlakeLexer.COMMA;
    }

    /** How many parentheses stand open before {@code open}. */
    private static int openBefore(final TokenStream tokens, final Token open) {
        int depth = 0;
        for (int i = 0; i < open.getTokenIndex(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                depth--;
            }
        }
        return depth;
    }

    /** The first comparison operator of the needle, from POSITION's parenthesis up to {@code operator}. */
    private static Token firstComparison(final TokenStream tokens, final Token open, final Token operator) {
        int depth = 0;
        for (int i = open.getTokenIndex() + 1; i < operator.getTokenIndex(); i++) {
            final Token each = tokens.get(i);
            if (each.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (each.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (each.getType() == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && isComparison(each.getType())) {
                return each;
            }
        }
        return operator;
    }

    private static boolean isComparison(final int type) {
        return type == FrostlakeLexer.EQ || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.LT
            || type == FrostlakeLexer.LTE || type == FrostlakeLexer.GT || type == FrostlakeLexer.GTE;
    }

    /**
     * POSITION's opening parenthesis when {@code token} stands in the call's first argument — its innermost open
     * parenthesis follows the bare name and no comma stands between them — or null.
     */
    private static Token needleOpen(final TokenStream tokens, final Token token) {
        int depth = 0;
        for (int i = token.getTokenIndex() - 1; i >= 0; i--) {
            final Token each = tokens.get(i);
            if (each.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (each.getType() == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (each.getType() == FrostlakeLexer.LPAREN) {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                final Token name = previousSpoken(tokens, i);
                final Token beforeName = name == null ? null : previousSpoken(tokens, name.getTokenIndex());
                return name != null && POSITION.equalsIgnoreCase(name.getText())
                    && (beforeName == null || beforeName.getType() != FrostlakeLexer.DOT) ? each : null;
            } else if (depth == 0 && each.getType() == FrostlakeLexer.COMMA) {
                return null;
            }
        }
        return null;
    }

    /**
     * The comma or IN that ends the needle {@code token} stands in, outside any inner parenthesis — or null when the
     * call's parenthesis closes, or the input ends, first.
     */
    private static Token needleEnd(final TokenStream tokens, final Token token) {
        int depth = 0;
        for (int i = token.getTokenIndex() + 1; i < tokens.size(); i++) {
            final Token each = tokens.get(i);
            if (each.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int type = each.getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return null;
                }
                depth--;
            } else if (depth == 0 && (type == FrostlakeLexer.COMMA || type == FrostlakeLexer.IN)) {
                return each;
            } else if (type == Token.EOF) {
                return null;
            }
        }
        return null;
    }

    /** The spoken token after index {@code index}, or null. */
    private static Token nextSpoken(final TokenStream tokens, final int index) {
        for (int i = index + 1; i < tokens.size(); i++) {
            if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return tokens.get(i);
            }
        }
        return null;
    }

    /** The spoken token before index {@code index}, or null. */
    private static Token previousSpoken(final TokenStream tokens, final int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return tokens.get(i);
            }
        }
        return null;
    }

    /**
     * The closing parenthesis live's recovery trips over after {@code refused}: the first one left unmatched once
     * the parenthesis POSITION opened is set aside, so every one opened before it still closes normally.
     */
    static Token unmatchedClose(final TokenStream tokens, final Token refused) {
        int enclosing = -1;
        for (int i = 0; i < refused.getTokenIndex(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.LPAREN) {
                enclosing++;
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                enclosing--;
            }
        }
        int depth = 0;
        for (int i = refused.getTokenIndex() + 1; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                if (depth > 0) {
                    depth--;
                } else if (enclosing > 0) {
                    enclosing--;
                } else {
                    return token;
                }
            }
        }
        return null;
    }

    private static void refuse(final Token refused, final Token stacked, final String sql) {
        final List<String> lines = new ArrayList<>();
        lines.add(sentence(refused));
        if (stacked != null) {
            lines.add(sentence(stacked));
        }
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }

    private static String sentence(final Token token) {
        final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '" + token.getText() + "'.";
    }
}
