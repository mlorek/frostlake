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
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports after a time-zone phrase a data type cannot take, and after a COMMENT target of two
 * words (all live-verified).
 *
 * <p>A cast's complete type followed by WITH is read as WITH opening a name, and the word after that name is told
 * too. WITHOUT is told with the second word after it when a precision closes the type, and with the first word after
 * it when a type word stands before it:
 *
 * <pre>
 *   CAST(x AS TIMESTAMP(3) WITH TIME ZONE)          'WITH', then 'ZONE'
 *   CAST(x AS TIMESTAMP(3) WITH LOCAL TIME ZONE)    'WITH', then 'TIME'
 *   CAST(x AS TIME WITH TIME ZONE)                  'WITH', then 'ZONE'
 *   CAST(x AS TIMESTAMP(3) WITHOUT TIME ZONE)       'WITHOUT', then 'ZONE'
 *   CAST(x AS TIMESTAMP_NTZ WITHOUT TIME ZONE)      'WITHOUT', then 'TIME'
 * </pre>
 *
 * <p>A TIMESTAMP whose words break off is told at the word that opened them as well:
 *
 * <pre>
 *   CAST(x AS TIMESTAMP WITH ZONE)                  'WITH', then the ')' after ZONE
 *   CAST(x AS TIMESTAMP WITH TIME)                  the ')', then 'WITH'
 *   CAST(x AS TIMESTAMP WITHOUT ZONE)               'ZONE', then 'WITHOUT'
 * </pre>
 *
 * <p>In a DESCRIBE's argument list, a TIMESTAMP whose words break off reads on once more:
 *
 * <pre>
 *   DESCRIBE TABLE t (TIMESTAMP WITH)               the ')', then 'WITH'; DESCRIBE VIEW and DESCRIBE FUNCTION alike
 *   DESCRIBE TABLE t (TIMESTAMP WITH, INT)          the ',', 'WITH', then the list's ')'
 *   DESCRIBE TABLE t (TIMESTAMP WITH TIME)          the ')', 'WITH', then the ')' again, as WITH LOCAL is
 *   DESCRIBE TABLE t (TIMESTAMP WITH ZONE)          'WITH', then the ')'
 *   DESCRIBE TABLE t (TIMESTAMP WITHOUT ZONE)       'ZONE', 'WITHOUT', then the ')'
 *   DESCRIBE TABLE t (TIMESTAMP WITHOUT TIME)       the ')' alone
 * </pre>
 *
 * <p>A column's type followed by WITH and a word no column clause opens with is told at that word alone, as WITH
 * opens the column's policies and tags: {@code CREATE TABLE t (c TIMESTAMP(3) WITH TIME ZONE)} is 'TIME'. And a
 * COMMENT target of two words is told at its second word alone: {@code COMMENT ON ICEBERG TABLES t IS 'x'} is
 * 'TABLES'.
 */
final class WordedTypeFaultLines {

    private WordedTypeFaultLines() {
    }

    /**
     * The lines for a statement whose first fault is such a phrase, or {@code lines} unchanged for any other.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @param lines      the lines the parse reports
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex, final List<String> lines) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (faultIndex < 0 ? spoken.get(i).getType() == Token.EOF : spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        if (at < 1) {
            return lines;
        }
        int start = at;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        if (spoken.get(start).getType() == FrostlakeLexer.COMMENT && start + 3 == at
                && spoken.get(start + 1).getType() == FrostlakeLexer.ON && lines.size() > 1) {
            return Collections.singletonList(lines.get(0));
        }
        final List<String> column = columnLines(spoken, start, at);
        if (column != null) {
            return column;
        }
        final List<String> described = describeLines(spoken, start, at);
        if (described != null) {
            return described;
        }
        final List<String> cast = castLines(spoken, at);
        return cast != null ? cast : lines;
    }

    /** The line of a column type followed by WITH and a word no column clause opens with, or null. */
    private static List<String> columnLines(final List<Token> spoken, final int start, final int at) {
        if (spoken.get(start).getType() != FrostlakeLexer.CREATE || spoken.get(at).getType() != FrostlakeLexer.WITH
                || at + 1 >= spoken.size()) {
            return null;
        }
        boolean table = false;
        int depth = 0;
        for (int i = start; i < at; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.TABLE && depth == 0) {
                table = true;
            } else if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
        }
        final Token before = spoken.get(at - 1);
        final Token after = spoken.get(at + 1);
        if (!table || depth != 1 || !(before.getType() == FrostlakeLexer.RPAREN || isWord(before))
                || !isWord(after) || after.getType() == FrostlakeLexer.MASKING || after.getType() == FrostlakeLexer.TAG
                || after.getType() == FrostlakeLexer.PROJECTION) {
            return null;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(after));
    }

    /** The lines of a TIMESTAMP in a DESCRIBE's argument list whose time-zone words break off, or null. */
    private static List<String> describeLines(final List<Token> spoken, final int start, final int at) {
        final int first = spoken.get(start).getType();
        if (first != FrostlakeLexer.DESCRIBE && first != FrostlakeLexer.DESC || at < 3 || at + 2 >= spoken.size()
                || spoken.get(at - 1).getType() != FrostlakeLexer.TIMESTAMP) {
            return null;
        }
        final int listed = spoken.get(at - 2).getType();
        final int open = listed == FrostlakeLexer.LPAREN ? at - 2 : listed == FrostlakeLexer.COMMA ? opening(spoken, at)
            : -1;
        if (open < start + 2) {
            return null;
        }
        final Token opener = spoken.get(at);
        final boolean with = opener.getType() == FrostlakeLexer.WITH;
        final boolean without = ClauseWords.isWord(opener, "WITHOUT");
        final Token next = spoken.get(at + 1);
        final Token after = spoken.get(at + 2);
        final boolean closes = after.getType() == FrostlakeLexer.RPAREN;
        final List<String> told = new ArrayList<>();
        if (with && (next.getType() == FrostlakeLexer.RPAREN || next.getType() == FrostlakeLexer.COMMA)) {
            told.add(SyntaxErrorListener.sentence(next));
            told.add(SyntaxErrorListener.sentence(opener));
            final int close = next.getType() == FrostlakeLexer.COMMA ? closing(spoken, open) : -1;
            if (close > 0) {
                told.add(SyntaxErrorListener.sentence(spoken.get(close)));
            }
        } else if (with && closes && (next.getType() == FrostlakeLexer.TIME
                || next.getType() == FrostlakeLexer.LOCAL)) {
            told.add(SyntaxErrorListener.sentence(after));
            told.add(SyntaxErrorListener.sentence(opener));
            told.add(SyntaxErrorListener.sentence(after));
        } else if (with && closes && next.getType() == FrostlakeLexer.ZONE) {
            told.add(SyntaxErrorListener.sentence(opener));
            told.add(SyntaxErrorListener.sentence(after));
        } else if (without && closes && next.getType() == FrostlakeLexer.ZONE) {
            told.add(SyntaxErrorListener.sentence(next));
            told.add(SyntaxErrorListener.sentence(opener));
            told.add(SyntaxErrorListener.sentence(after));
        } else if (without && closes && next.getType() == FrostlakeLexer.TIME) {
            told.add(SyntaxErrorListener.sentence(after));
        } else {
            return null;
        }
        return told;
    }

    /** The index of the '(' whose list holds the token at {@code at}, or -1. */
    private static int opening(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && depth-- == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the ')' that closes the '(' at {@code open}, or -1. */
    private static int closing(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The lines of a cast's type followed by a time-zone phrase it cannot take, or null. */
    private static List<String> castLines(final List<Token> spoken, final int at) {
        int as = at - 1;
        while (as > 0 && spoken.get(as).getType() != FrostlakeLexer.AS && at - as <= 6) {
            as--;
        }
        if (as < 2 || spoken.get(as).getType() != FrostlakeLexer.AS || !inCast(spoken, as)) {
            return null;
        }
        final int typeWord = spoken.get(as + 1).getType();
        int phrase = as + 2;
        boolean precision = false;
        if (phrase < spoken.size() && spoken.get(phrase).getType() == FrostlakeLexer.LPAREN) {
            while (phrase < spoken.size() && spoken.get(phrase).getType() != FrostlakeLexer.RPAREN) {
                phrase++;
            }
            phrase++;
            precision = true;
        }
        if (phrase + 1 >= spoken.size() || at < phrase) {
            return null;
        }
        final Token opener = spoken.get(phrase);
        final boolean with = opener.getType() == FrostlakeLexer.WITH;
        final boolean without = ClauseWords.isWord(opener, "WITHOUT");
        if (!with && !without) {
            return null;
        }
        final List<String> told = new ArrayList<>();
        if (at == phrase) {
            final int second = with || precision ? phrase + 2 : phrase + 1;
            if (second >= spoken.size()) {
                return null;
            }
            told.add(SyntaxErrorListener.sentence(opener));
            told.add(SyntaxErrorListener.sentence(spoken.get(second)));
            return told;
        }
        if (typeWord != FrostlakeLexer.TIMESTAMP || precision) {
            return null;
        }
        final Token next = spoken.get(phrase + 1);
        if (with && next.getType() == FrostlakeLexer.ZONE && at == phrase + 1 && phrase + 2 < spoken.size()) {
            told.add(SyntaxErrorListener.sentence(opener));
            told.add(SyntaxErrorListener.sentence(spoken.get(phrase + 2)));
            return told;
        }
        if (with && next.getType() == FrostlakeLexer.TIME && at == phrase + 2
                && spoken.get(at).getType() == FrostlakeLexer.RPAREN) {
            told.add(SyntaxErrorListener.sentence(spoken.get(at)));
            told.add(SyntaxErrorListener.sentence(opener));
            return told;
        }
        if (without && next.getType() == FrostlakeLexer.ZONE && at == phrase + 1) {
            told.add(SyntaxErrorListener.sentence(next));
            told.add(SyntaxErrorListener.sentence(opener));
            return told;
        }
        return null;
    }

    /** Whether the AS at {@code as} stands in a CAST's or a TRY_CAST's brackets. */
    private static boolean inCast(final List<Token> spoken, final int as) {
        int depth = 0;
        for (int i = as - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    final int call = spoken.get(i - 1).getType();
                    return call == FrostlakeLexer.CAST || call == FrostlakeLexer.TRY_CAST;
                }
                depth--;
            }
        }
        return false;
    }

    /** Whether the token is a word: a plain or a quoted identifier, or a keyword. */
    private static boolean isWord(final Token token) {
        if (SyntaxErrorListener.isIdentifierToken(token)) {
            return true;
        }
        final String text = token.getText();
        return token.getType() != Token.EOF && text != null && !text.isEmpty()
            && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_');
    }
}
