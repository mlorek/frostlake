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

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The lines live reports for a fault inside a CAST's or a TRY_CAST's parentheses that is no comma: the fault, and
 * then whatever the statement holds wrong once the cast is read past (all live-verified):
 *
 * <pre>
 *   SELECT CAST(1 2) FROM t                     '2' at 14
 *   SELECT CAST(1 AS 2), a FROM t               '2' at 17
 *   SELECT CAST(AS INT) x y FROM t              'AS' at 12, then 'y' at 22
 *   SELECT CAST(1 2), CAST(3 4) FROM t          '2' at 14, then '4' at 25
 *   SELECT CAST(1 2) FROM t WHERE a b           '2' at 14, then 'b' at 32
 *   SELECT UPPER(CAST(1 AS 2)) FROM t           '2' at 23
 *   SELECT UPPER(CAST(1 2)) FROM t              '2' at 20, then ')' at 22
 *   SELECT (CAST(1 2) + 1) FROM t               '2' at 15, then ')' at 21
 *   SELECT a FROM t WHERE CAST(1 2) = 1 ORDER BY a      '2' at 29, then 'ORDER' at 36
 *   … WHERE CAST(1 2) = 1 AND CAST(3 4) = 2             '2', '4', then the end of the input
 * </pre>
 *
 * <p>A cast whose fault lies at or after its AS, and a select item's own cast, is read past as a whole. A token
 * where the AS belongs, in a cast nested in brackets, gives the cast's parenthesis up instead, so the bracket
 * around it closes one parenthesis early and the last one is refused. Directly after WHERE or HAVING the same
 * token leaves the condition unfinished, and the token that ends the clause is refused.
 */
final class CastFaultLines {

    private CastFaultLines() {
    }

    /**
     * The lines live reports when the parse's first line names a token, no comma, inside a cast's parentheses; or null.
     *
     * @param sql            the text
     * @param spoken         the text's default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse) {
        final int fault = statementParse && sql != null ? CastFaultPlace.positionOf(spoken, faultIndex) : -1;
        if (fault < 3 || spoken.get(fault).getType() == FrostlakeLexer.COMMA
                || spoken.get(fault).getType() == FrostlakeLexer.RPAREN) {
            return null;
        }
        int open = CastFaultPlace.enclosingParen(spoken, fault);
        final boolean direct = open > 0 && CastFaultPlace.isCast(spoken, open - 1);
        while (open > 0 && !CastFaultPlace.isCast(spoken, open - 1)) {
            open = CastFaultPlace.enclosingParen(spoken, open);
        }
        if (!direct && spoken.get(fault).getType() == FrostlakeLexer.LPAREN) {
            return null;
        }
        final int close = open < 1 ? -1 : CastFaultPlace.closeOf(spoken, open);
        final int clause = open < 1 ? -1 : CastFaultPlace.clauseOf(spoken, open - 1);
        if (close < 0 || clause < 0 || spoken.get(clause).getType() == FrostlakeLexer.ON
                || spoken.get(clause).getType() == FrostlakeLexer.FROM) {
            return null;
        }
        final int cast = open - 1;
        boolean typed = spoken.get(fault).getType() == FrostlakeLexer.AS;
        int depth = 0;
        for (int i = open + 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            typed = typed || depth == 0 && type == FrostlakeLexer.AS;
        }
        if (typed && (!direct || spoken.get(fault).getType() == FrostlakeLexer.LPAREN)) {
            // A fault among a type's parameters is the type's own.
            return null;
        }
        final int nest = CastFaultPlace.nesting(spoken, clause, cast);
        final boolean selectItem = spoken.get(clause).getType() == FrostlakeLexer.SELECT;
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        if (typed || !direct || nest == 0 && selectItem) {
            // The cast is read past as a whole: a value stands where it stood.
            final char[] text = CastFaultPlace.blanked(sql.toCharArray(), spoken, cast, close);
            text[spoken.get(cast).getStartIndex()] = '1';
            lines.addAll(SyntaxErrorListener.linesReportedFor(new String(text), true));
            return lines;
        }
        if (nest > 0) {
            // The cast's parenthesis is given up at the token: the brackets around it close one early.
            final char[] text = CastFaultPlace.blanked(sql.toCharArray(), spoken, cast, open);
            CastFaultPlace.blanked(text, spoken, fault, fault);
            lines.addAll(SyntaxErrorListener.linesReportedFor(new String(text), true));
            return lines;
        }
        final int before = spoken.get(cast - 1).getType();
        if (before == FrostlakeLexer.WHERE || before == FrostlakeLexer.HAVING) {
            // The rest of the condition is read on, the cast standing for a value, and a later fault in it is told
            // before the token that ends the clause.
            final Token end = spoken.get(clauseEnd(spoken, close));
            final char[] text = CastFaultPlace.blanked(sql.toCharArray(), spoken, cast, close);
            text[spoken.get(cast).getStartIndex()] = '1';
            for (final String later : SyntaxErrorListener.linesReportedFor(new String(text), true)) {
                final int[] at = SyntaxErrorListener.sentenceCoordinates(later);
                if (at == null || at[0] > end.getLine()
                        || at[0] == end.getLine() && at[1] >= end.getCharPositionInLine()) {
                    break;
                }
                lines.add(later);
            }
            lines.add(SyntaxErrorListener.sentence(end));
        }
        return lines;
    }

    /** The token that ends the clause a position stands in: the next clause word outside brackets, or the end. */
    private static int clauseEnd(final List<Token> spoken, final int after) {
        int depth = 0;
        for (int i = after + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (type == Token.EOF || type == FrostlakeLexer.SEMI || depth <= 0
                    && (type == FrostlakeLexer.GROUP || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.HAVING
                        || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.LIMIT
                        || type == FrostlakeLexer.UNION)) {
                return i;
            }
        }
        return spoken.size() - 1;
    }
}
