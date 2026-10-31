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

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines live reports for a block's value that opens with CASE, CAST or WHEN, as a variable or a parameter so
 * named would be written - {@code RETURN case}, {@code LET x := case}, {@code RETURN (case)} - in a block or a SQL
 * procedure's body. The account reads each word there as what it opens in an expression, never as a name, so the
 * value does not read (live-verified):
 * <ul>
 *   <li>CASE opens a CASE expression whose operand reads on, and the token that ends the value - the statement's
 *       ';', or the ')' of a bracket around the word - is refused;</li>
 *   <li>CAST must be followed by its opening parenthesis: the token after it is refused, and the token that ends the
 *       value too when another stands between them;</li>
 *   <li>WHEN opens nothing: it is refused, then the token after it, or the token after the statement's ';' when that
 *       ';' follows it; in an IF's condition the word is refused alone.</li>
 * </ul>
 * <pre>
 *   BEGIN LET case := 1; RETURN case; END;          the ';' after case
 *   BEGIN LET case := 1; RETURN (case); END;        the ')' after case
 *   BEGIN LET cast := 1; RETURN cast + 1; END;      the '+', then the ';'
 *   BEGIN LET when := 1; RETURN when; END;          'when', then the END
 *   BEGIN LET when := 1; RETURN when + 1; END;      'when', then the '+'
 *   BEGIN IF (when &gt; 1) THEN RETURN 1; END IF; RETURN 0; END;    'when' alone
 * </pre>
 * The rule applies only where the word is the text's one fault: the text must parse cleanly once a name stands in
 * the word's place.
 */
public final class BlockKeywordValueLines {

    private BlockKeywordValueLines() {
    }

    /**
     * The lines live reports for such a value, or null when the text holds none or has another fault.
     *
     * @param sql    the text parsed
     * @param spoken its default-channel tokens
     * @return the lines, or null
     */
    public static List<String> lines(final String sql, final List<Token> spoken) {
        if (sql == null || spoken == null) {
            return null;
        }
        for (int i = 1; i + 1 < spoken.size(); i++) {
            final Token word = spoken.get(i);
            final int type = word.getType();
            final Token next = spoken.get(i + 1);
            final boolean condition = type == FrostlakeLexer.WHEN && inCondition(spoken, i);
            if (!condition && !opensValue(spoken, i)
                    || type == FrostlakeLexer.CAST && next.getType() == FrostlakeLexer.LPAREN) {
                continue;
            }
            final int end = valueEnd(spoken, i);
            if (end < 0 || type == FrostlakeLexer.CASE && holdsWhen(spoken, i, end)) {
                continue;
            }
            if (SyntaxErrorListener.cleanParse(sql.substring(0, word.getStartIndex()) + "x"
                    + sql.substring(word.getStopIndex() + 1)) == null) {
                return null;
            }
            final List<String> lines = new ArrayList<>();
            if (type == FrostlakeLexer.CASE) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(end)));
            } else if (type == FrostlakeLexer.CAST) {
                lines.add(SyntaxErrorListener.sentence(next));
                if (i + 1 != end) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(end)));
                }
            } else {
                lines.add(SyntaxErrorListener.sentence(word));
                if (!condition) {
                    final boolean semicolon = next.getType() == FrostlakeLexer.SEMI && i + 2 < spoken.size();
                    lines.add(SyntaxErrorListener.sentence(semicolon ? spoken.get(i + 2) : next));
                }
            }
            return lines;
        }
        return null;
    }

    /**
     * Whether the word at {@code index} opens a RETURN's or an assignment's value: right after the RETURN or the
     * ':=', or, for CASE and CAST, right after a bracket that does.
     */
    private static boolean opensValue(final List<Token> spoken, final int index) {
        final int type = spoken.get(index).getType();
        if (type != FrostlakeLexer.CASE && type != FrostlakeLexer.CAST && type != FrostlakeLexer.WHEN) {
            return false;
        }
        final int before = spoken.get(index - 1).getType();
        if (before == FrostlakeLexer.RETURN || before == FrostlakeLexer.COLON_EQ) {
            return true;
        }
        if (type == FrostlakeLexer.WHEN || before != FrostlakeLexer.LPAREN || index < 2) {
            return false;
        }
        final int opener = spoken.get(index - 2).getType();
        return opener == FrostlakeLexer.RETURN || opener == FrostlakeLexer.COLON_EQ;
    }

    /** Whether the WHEN at {@code index} opens an IF's bracketed condition. */
    private static boolean inCondition(final List<Token> spoken, final int index) {
        return index >= 2 && spoken.get(index - 1).getType() == FrostlakeLexer.LPAREN
            && spoken.get(index - 2).getType() == FrostlakeLexer.IF;
    }

    /**
     * The index of the token that ends the value the word at {@code index} stands in: the statement's ';' at the
     * word's own level, or the ')' closing a bracket around the word; -1 when the text ends first.
     */
    private static int valueEnd(final List<Token> spoken, final int index) {
        int depth = 0;
        for (int i = index + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            } else if (type == FrostlakeLexer.SEMI && depth == 0) {
                return i;
            } else if (type == Token.EOF) {
                return -1;
            }
        }
        return -1;
    }

    /** Whether a WHEN stands between the CASE at {@code index} and the value's end: a CASE expression in full. */
    private static boolean holdsWhen(final List<Token> spoken, final int index, final int end) {
        for (int i = index + 1; i < end; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.WHEN) {
                return true;
            }
        }
        return false;
    }
}
