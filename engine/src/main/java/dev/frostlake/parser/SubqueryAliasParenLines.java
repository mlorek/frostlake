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
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The lines live reports for an ALIAS followed by '(' as the last thing in a subquery (all live-verified). In a scalar
 * subquery that stands inside a call's brackets or in a clause after the select list they are the subquery's SELECT,
 * then the first token inside the bracket that is not another '('; in a derived table or an EXISTS the '(' alone. A
 * scalar subquery standing in a select list itself is reported the other way, the '(' after its SELECT.
 *
 * <pre>
 *   SELECT ABS((SELECT 1 foo (2)))                  'SELECT' at 12, '2' at 26
 *   SELECT UPPER((SELECT 'a' foo ('b')))            'SELECT' at 14, ''b'' at 30
 *   SELECT 1 FROM t WHERE a = (SELECT 1 foo (2))    'SELECT' at 27, '2' at 41
 *   SELECT * FROM (SELECT 1 foo (2)) AS x           '(' at 28
 *   SELECT 1 WHERE EXISTS (SELECT 1 foo (2))        '(' at 36
 * </pre>
 *
 * <p>The rule applies only where that bracket is the statement's one fault: the text must parse cleanly once the
 * bracket is blanked out, every position where it was written.
 */
final class SubqueryAliasParenLines {

    private SubqueryAliasParenLines() {
    }

    /**
     * The lines live reports for such a bracket, or null when the text holds none or has another fault.
     *
     * @param sql    the text parsed
     * @param spoken its default-channel tokens, end of input included
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken) {
        if (sql == null || sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        for (int open = 2; open < spoken.size(); open++) {
            if (spoken.get(open).getType() != FrostlakeLexer.LPAREN || !afterAlias(spoken, open)) {
                continue;
            }
            final int close = closingParen(spoken, open);
            if (close < 0 || close + 1 >= spoken.size() || spoken.get(close + 1).getType() != FrostlakeLexer.RPAREN) {
                continue;
            }
            final char[] blanked = sql.toCharArray();
            for (int c = spoken.get(open).getStartIndex(); c <= spoken.get(close).getStopIndex(); c++) {
                if (blanked[c] != '\n' && blanked[c] != '\r') {
                    blanked[c] = ' ';
                }
            }
            final FrostlakeParser.SqlScriptContext tree = SyntaxErrorListener.cleanParse(new String(blanked));
            if (tree != null && sourceClosedAt(tree, spoken.get(close + 1).getStartIndex())) {
                final List<String> alone = new ArrayList<>();
                alone.add(SyntaxErrorListener.sentence(spoken.get(open)));
                return alone;
            }
            final FrostlakeParser.ScalarSubqueryExprContext subquery = tree == null ? null
                : subqueryClosedAt(tree, spoken.get(close + 1).getStartIndex());
            if (subquery == null || !outsideASelectList(subquery)) {
                return null;
            }
            int inner = open + 1;
            while (inner < close && spoken.get(inner).getType() == FrostlakeLexer.LPAREN) {
                inner++;
            }
            if (inner >= close) {
                return null;
            }
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(subquery.selectStatement().getStart()));
            lines.add(SyntaxErrorListener.sentence(spoken.get(inner)));
            return lines;
        }
        return null;
    }

    /** Whether the '(' at {@code open} follows a bare alias: a word right after a finished value. */
    private static boolean afterAlias(final List<Token> spoken, final int open) {
        final int word = spoken.get(open - 1).getType();
        final int before = spoken.get(open - 2).getType();
        final boolean wordIsName = word == FrostlakeLexer.IDENTIFIER || word == FrostlakeLexer.QUOTED_IDENTIFIER;
        return wordIsName && (before == FrostlakeLexer.INTEGER_LITERAL || before == FrostlakeLexer.STRING_LITERAL
            || before == FrostlakeLexer.FLOAT_LITERAL || before == FrostlakeLexer.RPAREN);
    }

    /** The index in {@code spoken} of the ')' closing the '(' at {@code open}, or -1. */
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

    /** Whether a derived table or an EXISTS subquery in the tree closes with the ')' starting at character {@code start}. */
    private static boolean sourceClosedAt(final ParseTree tree, final int start) {
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(tree);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (node instanceof FrostlakeParser.TableSourceContext
                    && ((FrostlakeParser.TableSourceContext) node).selectStatement() != null
                    && ((FrostlakeParser.TableSourceContext) node).RPAREN() != null
                    && ((FrostlakeParser.TableSourceContext) node).RPAREN().getSymbol().getStartIndex() == start) {
                return true;
            }
            if (node instanceof FrostlakeParser.ExistsExprContext
                    && ((FrostlakeParser.ExistsExprContext) node).RPAREN().getSymbol().getStartIndex() == start) {
                return true;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        return false;
    }

    /** The scalar subquery in the tree whose ')' starts at character {@code start}, or null. */
    private static FrostlakeParser.ScalarSubqueryExprContext subqueryClosedAt(final ParseTree tree, final int start) {
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(tree);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (node instanceof FrostlakeParser.ScalarSubqueryExprContext
                    && ((FrostlakeParser.ScalarSubqueryExprContext) node).RPAREN() != null
                    && ((FrostlakeParser.ScalarSubqueryExprContext) node).RPAREN().getSymbol().getStartIndex() == start) {
                return (FrostlakeParser.ScalarSubqueryExprContext) node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        return null;
    }

    /** Whether the subquery stands inside a call's brackets or in a clause, not in a select list itself. */
    private static boolean outsideASelectList(final ParserRuleContext subquery) {
        for (ParserRuleContext up = subquery.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.FunctionArgContext || up instanceof FrostlakeParser.WhereClauseContext
                    || up instanceof FrostlakeParser.HavingClauseContext) {
                return true;
            }
            if (up instanceof FrostlakeParser.ExprItemContext || up instanceof FrostlakeParser.SelectStatementContext) {
                return false;
            }
        }
        return false;
    }
}
