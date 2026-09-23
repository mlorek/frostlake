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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The lines live reports for {@code ILIKE ALL}, which it does not have: {@code LIKE ALL}, {@code LIKE ANY} and
 * {@code ILIKE ANY} are the quantified forms. Live refuses the ALL, and its recovery then reads the bracket after it
 * the way it reads an outer-join marker's, {@code (+)}: the first token inside is refused as well. When a comma inside
 * the bracket may carry on the list the predicate stands in — a select list, an ORDER BY or GROUP BY list, a call's
 * arguments — the bracket closes before that comma, and the first closing parenthesis left unmatched after it is refused
 * too; in a WHERE, HAVING, QUALIFY or join condition nothing more is. An ESCAPE after the bracket is never reported (all
 * live-verified):
 *
 * <pre>
 *   SELECT 'ab' ILIKE ALL ('a%') ESCAPE '!'                'ALL' at 18, ''a%'' at 23
 *   SELECT 'ab' ILIKE ALL ('x%', 'a!')                     'ALL' at 18, ''x%'' at 23, ')' at 33
 *   SELECT UPPER('ab' ILIKE ALL ('x%', 'a!'))              'ALL' at 24, ''x%'' at 29, ')' at 40
 *   SELECT a FROM t WHERE 'ab' ILIKE ALL ('x%', 'a!')      'ALL' at 33, ''x%'' at 38
 * </pre>
 *
 * <p>The rule applies only where the ALL and its bracket are the statement's one fault: the text must parse cleanly once
 * they read as a plain pattern.
 */
final class IlikeAllSyntax {

    private IlikeAllSyntax() {
    }

    /**
     * The lines live reports for the first {@code ILIKE ALL (}, or null when the text holds none or has another fault.
     *
     * @param sql    the text parsed
     * @param spoken its default-channel tokens, end of input included
     * @return the lines, or null
     */
    static List<String> refusal(final String sql, final List<Token> spoken) {
        int all = -1;
        for (int i = 1; i + 2 < spoken.size() && all < 0; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.ALL && spoken.get(i - 1).getType() == FrostlakeLexer.ILIKE
                    && spoken.get(i + 1).getType() == FrostlakeLexer.LPAREN) {
                all = i;
            }
        }
        if (all < 0 || sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        final Token quantifier = spoken.get(all);
        final int bracketClose = closingParen(spoken, all + 1);
        if (bracketClose < 0) {
            return null;
        }
        final char[] text = sql.toCharArray();
        for (int c = quantifier.getStartIndex(); c <= spoken.get(bracketClose).getStopIndex(); c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = c == quantifier.getStartIndex() ? '1' : ' ';
            }
        }
        final FrostlakeParser.SqlScriptContext tree = SyntaxErrorListener.cleanParse(new String(text));
        final ParserRuleContext predicate = tree == null ? null
            : predicateAt(tree, spoken.get(all - 1).getStartIndex());
        if (predicate == null) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(quantifier));
        if (bracketClose == all + 3 && spoken.get(all + 2).getType() == FrostlakeLexer.PLUS) {
            // (+) is a whole outer-join marker: nothing inside it is refused.
            return lines;
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(all + 2)));
        final int comma = firstListComma(spoken, all + 1);
        if (comma >= 0 && commaCarriesOn(predicate)) {
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
            lexer.removeErrorListeners();
            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            tokens.fill();
            final Token unmatched = PositionNeedleSyntax.unmatchedClose(tokens,
                tokens.get(spoken.get(comma).getTokenIndex()));
            if (unmatched != null) {
                lines.add(SyntaxErrorListener.sentence(unmatched));
            }
        }
        return lines;
    }

    /** The ILIKE predicate in the tree whose keyword starts at character {@code start}, or null. */
    private static ParserRuleContext predicateAt(final ParseTree tree, final int start) {
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(tree);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (node instanceof FrostlakeParser.LikeExprContext
                    && ((FrostlakeParser.LikeExprContext) node).ILIKE() != null
                    && ((FrostlakeParser.LikeExprContext) node).ILIKE().getSymbol().getStartIndex() == start) {
                return (ParserRuleContext) node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        return null;
    }

    /** The index in {@code spoken} of the ')' closing the bracket opened at {@code open}, or -1. */
    private static int closingParen(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The index in {@code spoken} of the first comma directly inside the bracket opened at {@code open}, or -1. */
    private static int firstListComma(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return -1;
                }
            } else if (type == FrostlakeLexer.COMMA && depth == 1) {
                return i;
            }
        }
        return -1;
    }

    /** Whether the nearest list or clause the predicate stands in may continue after a comma. */
    private static boolean commaCarriesOn(final ParserRuleContext predicate) {
        for (ParserRuleContext up = predicate.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExprItemContext || up instanceof FrostlakeParser.OrderItemContext
                    || up instanceof FrostlakeParser.GroupByElementContext
                    || up instanceof FrostlakeParser.FunctionArgListContext) {
                return true;
            }
            if (up instanceof FrostlakeParser.WhereClauseContext || up instanceof FrostlakeParser.HavingClauseContext
                    || up instanceof FrostlakeParser.QualifyClauseContext
                    || up instanceof FrostlakeParser.JoinClauseContext) {
                return false;
            }
        }
        return false;
    }
}
