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
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The lines live reports for a comma written before the first option of a parenthesized option list — a stage's
 * DIRECTORY, FILE_FORMAT, ENCRYPTION, COPY_OPTIONS or CREDENTIALS, a table's staging properties, the lists of
 * ALTER STAGE and COPY INTO (all live-verified). A comma stands between two options only, so each leading one is
 * named, then the parenthesis closing the list, and nothing more of the statement:
 *
 * <pre>
 *   DIRECTORY = (, ENABLE = TRUE)          ',' then the list's ')'
 *   DIRECTORY = (, , ENABLE = TRUE)        ',' ',' then ')'
 *   DIRECTORY = (,)                        ',' then ')'
 *   DIRECTORY = (, ENABLE = TRUE) FILE_FORMAT = (, TYPE = CSV)     the first list's two lines alone
 * </pre>
 *
 * <p>A list of values is read the same way up to the value, which is no option and is named itself; its own
 * parenthesis then closes it, so the one reported is the parenthesis of a list around it, when there is one:
 *
 * <pre>
 *   NULL_IF = (, 'a')                                  ',' then ''a''
 *   FILE_FORMAT = (TYPE = CSV NULL_IF = (, 'a'))       ',' ''a'' then the outer ')'
 * </pre>
 *
 * <p>A list the statement never closes has no line for a parenthesis:
 *
 * <pre>
 *   DIRECTORY = (, ENABLE = TRUE           ',' alone                   a whole option is read
 *   DIRECTORY = (, ENABLE                  ',' then the end of input   the text stops inside an option
 *   DIRECTORY = (,                         ',' then the end of input
 *   DIRECTORY = (, ,                       ',' ','
 *   DIRECTORY = (, , ,                     ',' ',' then the end of input: the third comma gives way to it
 * </pre>
 */
final class OptionListCommaLines {

    private OptionListCommaLines() {
    }

    /**
     * The lines for a statement whose first fault is such a comma, or null when it is not one: the fault is no
     * comma, the comma does not open an option list of a CREATE, an ALTER or a COPY, or another statement follows.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        if (at < 3 || spoken.get(at).getType() != FrostlakeLexer.COMMA
                || spoken.get(at - 1).getType() != FrostlakeLexer.LPAREN
                || spoken.get(at - 2).getType() != FrostlakeLexer.EQ || !inOptionStatement(spoken, at)) {
            return null;
        }
        final List<Token> commas = new ArrayList<Token>();
        int next = at;
        while (spoken.get(next).getType() == FrostlakeLexer.COMMA) {
            commas.add(spoken.get(next++));
        }
        final int rest = next;
        int close = -1;
        int depth = 0;
        for (; close < 0 && !endsStatement(spoken.get(next)); next++) {
            final int type = spoken.get(next).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && depth-- == 0) {
                close = next;
            }
        }
        final int end = close >= 0 ? statementEnd(spoken, close) : next;
        if (spoken.get(end).getType() == FrostlakeLexer.SEMI && spoken.get(end + 1).getType() != Token.EOF) {
            // Another statement follows, and is read on its own.
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        if (close >= 0) {
            for (final Token comma : commas) {
                lines.add(SyntaxErrorListener.sentence(comma));
            }
            if (spoken.get(rest).getType() != FrostlakeLexer.STRING_LITERAL) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
            } else {
                lines.add(SyntaxErrorListener.sentence(spoken.get(rest)));
                if (spoken.get(close + 1).getType() == FrostlakeLexer.RPAREN) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(close + 1)));
                }
            }
            return lines;
        }
        final boolean stops;
        if (rest == end) {
            // Nothing but commas: the end of input is named after one and after three, not after two.
            stops = commas.size() != 2;
            for (int i = 0; i < commas.size() && i < 2; i++) {
                lines.add(SyntaxErrorListener.sentence(commas.get(i)));
            }
        } else {
            for (final Token comma : commas) {
                lines.add(SyntaxErrorListener.sentence(comma));
            }
            // A whole option ends in its value, which follows the '=' or closes a list of its own.
            stops = commas.size() == 1 && spoken.get(end - 1).getType() != FrostlakeLexer.RPAREN
                && spoken.get(end - 2).getType() != FrostlakeLexer.EQ;
        }
        if (stops) {
            final int[] shown = LeadingCommentOffset.rebase(EndOfInput.line(sql), EndOfInput.position(sql));
            lines.add("syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '<EOF>'.");
        }
        return lines;
    }

    private static boolean endsStatement(final Token token) {
        return token.getType() == Token.EOF || token.getType() == FrostlakeLexer.SEMI;
    }

    /** The position of the token ending the statement that holds the token at {@code from}. */
    private static int statementEnd(final List<Token> spoken, final int from) {
        int end = from;
        while (!endsStatement(spoken.get(end))) {
            end++;
        }
        return end;
    }

    /**
     * Whether the comma stands in a CREATE, an ALTER or a COPY outside any query: the statement opens with one of
     * the three words, and no SELECT is written in a group still open at the comma — a query in parentheses the
     * statement has closed again, as a COPY's source, is behind it.
     */
    private static boolean inOptionStatement(final List<Token> spoken, final int comma) {
        int opener = comma;
        while (opener > 0 && spoken.get(opener - 1).getType() != FrostlakeLexer.SEMI) {
            opener--;
        }
        final int word = spoken.get(opener).getType();
        if (word != FrostlakeLexer.CREATE && word != FrostlakeLexer.ALTER && word != FrostlakeLexer.COPY) {
            return false;
        }
        // One entry per group open at the token read, the statement itself first: whether a SELECT stands in it.
        final List<Boolean> queries = new ArrayList<Boolean>();
        queries.add(Boolean.FALSE);
        for (int i = opener; i < comma; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                queries.add(Boolean.FALSE);
            } else if (type == FrostlakeLexer.RPAREN && queries.size() > 1) {
                queries.remove(queries.size() - 1);
            } else if (type == FrostlakeLexer.SELECT) {
                queries.set(queries.size() - 1, Boolean.TRUE);
            }
        }
        return !queries.contains(Boolean.TRUE);
    }
}
