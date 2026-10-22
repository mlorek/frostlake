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
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports for a text holding a number its lexer gives up on — one that opens an exponent and
 * stops ({@code 1e}, {@code 1.5e}, {@code 1e+}), a hexadecimal prefix ({@code 0x}), a second point no digit follows
 * ({@code 1.5.}). The lexer tells the character after the number by its code, or the end of the text, and drops the
 * number and that character; the text is then read without them (all live-verified):
 *
 * <pre>
 *   SELECT 1e FROM t1               near '32' at 9, then 'FROM' at 10: the select list is left empty
 *   SELECT ABS(1e)                  near '41' at 13, then '&lt;EOF&gt;' at 14 and the '(' at 10: the ')' went with it
 *   SELECT 1e; SELECT 2             near '59' at 9, then 'SELECT' at 11: so did the ';'
 *   SELECT 1 FROM t1 LIMIT 1e       near '&lt;EOF&gt;' alone: LIMIT is the table's alias
 *   BEGIN RETURN 1e x; END          near '32' alone: RETURN x is a statement
 * </pre>
 *
 * <p>A fault the reading tells before the number stands is told first, as for a literal left open (see
 * {@link UnclosedLiteralLines}); a line break after the number is told and kept, so the lines after it keep their
 * numbers.
 */
public final class MalformedNumberLines {

    private MalformedNumberLines() {
    }

    /**
     * Refuse a text holding such a number with every line the account reports for it.
     *
     * @param sql      the script's source text
     * @param tokens   the script's tokens
     * @param listener the parse's error listener
     */
    public static void requireBefore(final String sql, final CommonTokenStream tokens,
                                     final SyntaxErrorListener listener) {
        if (sql == null || tokens == null || !listener.hasErrors()) {
            return;
        }
        Token number = null;
        for (final Token token : tokens.getTokens()) {
            if (token.getType() == FrostlakeLexer.MALFORMED_EXPONENT && token.getStartIndex() >= 0) {
                number = token;
                break;
            }
        }
        if (number == null) {
            return;
        }
        final List<String> lines = linesOf(sql, number);
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }

    /** The lexer's line for the number, among the lines the text reports without it. */
    private static List<String> linesOf(final String sql, final Token number) {
        final int after = number.getStopIndex() + 1;
        final String lexerLine;
        if (after >= sql.length()) {
            final int[] end = LeadingCommentOffset.rebase(EndOfInput.line(sql), EndOfInput.position(sql));
            lexerLine = "parse error line " + end[0] + " at position " + end[1] + " near '<EOF>'.";
        } else {
            final int[] at = UnclosedLiteralLines.placeOf(sql, after);
            lexerLine = "parse error line " + at[0] + " at position " + at[1] + " near '" + (int) sql.charAt(after)
                + "'.";
        }
        final char[] text = sql.toCharArray();
        for (int i = number.getStartIndex(); i <= after && i < text.length; i++) {
            if (text[i] != '\n' && text[i] != '\r') {
                text[i] = ' ';
            }
        }
        final String repaired = new String(text);
        final List<String> rest = repaired.trim().isEmpty() ? new ArrayList<String>()
            : SyntaxErrorListener.linesReportedFor(repaired, true);
        final int[] at = UnclosedLiteralLines.placeOf(sql, Math.min(after, sql.length()));
        final List<String> lines = new ArrayList<>();
        int taken = 0;
        while (taken < rest.size() && UnclosedLiteralLines.standsBefore(rest.get(taken), at)) {
            lines.add(rest.get(taken++));
        }
        lines.add(lexerLine);
        lines.addAll(rest.subList(taken, rest.size()));
        return lines;
    }
}
