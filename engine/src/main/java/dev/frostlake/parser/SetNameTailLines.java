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
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * What the account reports after the name of a session SET that its '=' does not follow, when a comma or a closing
 * parenthesis stands further on in the statement. The name is refused (see {@link SetNameSyntax}); the account then
 * passes over everything up to the first comma or closing parenthesis and reads on from there as the list form,
 * {@code SET (a, b) = (…)}, would stand at that point (all live-verified):
 *
 * <pre>
 *   SET x (1)                  'x', then '&lt;EOF&gt;': after the ')' an '=' is due
 *   SET x (1) = 2              'x' alone: the '=' and its value are read
 *   SET x (1) y z              'x', then 'y' — the '=' is missing there — and 'z', which follows the value y
 *   SET x ((1))                'x', then the second ')'
 *   SET x (1, 2)               'x', then '2' — after a comma a name is due — and '&lt;EOF&gt;'
 *   SET x, y                   'x', then '&lt;EOF&gt;': after the names their ')' is due
 *   SET x, y = 1               'x', then '='
 *   SET x,, y                  'x', then the second ',' and '&lt;EOF&gt;'
 *   SET x (1); SELECT 1 x y    'x', ';', then the later 'y': the statement after is read
 *   SET x, y; SELECT 1 x y     'x', ';' alone: the statement after is passed over, and only those after it are read
 *   SET x ]                    'x', then ']': a closing bracket straight after the name is refused with it
 *   SET ()                     ')', then '&lt;EOF&gt;'
 * </pre>
 *
 * <p>Where the statement goes on past its '=', what stands there is read as the value of an ordinary SET, and so
 * are the statements after it; the lines of both are those of the text with the statement rebuilt that way.
 */
final class SetNameTailLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    /** A statement no parse refuses, put where a statement is passed over. */
    private static final String FILLER = "COMMIT";

    /** How the text is read on once the statement's own lines are known. */
    private static final int PASS_NEXT = 0;
    private static final int READ_NEXT = 1;
    private static final int READ_VALUE = 2;

    private SetNameTailLines() {
    }

    /**
     * The lines of a text that holds such a SET, or null when it holds none.
     *
     * @param sql    the text parsed
     * @param spoken its default-channel tokens, end of input included
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken) {
        if (sql == null || spoken.size() < 4) {
            return null;
        }
        int start = 0;
        for (int i = 0; i < spoken.size() - 1; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.BEGIN || type == FrostlakeLexer.DECLARE) {
                return null;
            }
            if (i == start && type == FrostlakeLexer.SET && i + 3 == spoken.size() - 1
                    && spoken.get(i + 1).getType() == FrostlakeLexer.LPAREN
                    && spoken.get(i + 2).getType() == FrostlakeLexer.RPAREN && i == 0) {
                // A list of no names: its ')' stands where a name is due, and its '=' where the input ends.
                final List<String> lines = new ArrayList<>();
                lines.add(SyntaxErrorListener.sentence(spoken.get(i + 2)));
                lines.add(SyntaxErrorListener.sentence(spoken.get(i + 3)));
                return lines;
            }
            if (i == start && type == FrostlakeLexer.SET && isName(spoken.get(i + 1).getType())
                    && !followsAName(spoken.get(i + 2).getType())) {
                final List<String> lines = linesFor(sql, spoken, i);
                if (lines != null) {
                    return lines;
                }
            }
            if (type == FrostlakeLexer.SEMI) {
                start = i + 1;
            }
        }
        return null;
    }

    /** Whether a token after a SET's name leaves the statement to its other readings. */
    private static boolean followsAName(final int type) {
        return type == FrostlakeLexer.EQ || type == FrostlakeLexer.EQ_EQ || type == FrostlakeLexer.DOT;
    }

    private static List<String> linesFor(final String sql, final List<Token> spoken, final int set) {
        int end = set;
        while (spoken.get(end).getType() != FrostlakeLexer.SEMI && spoken.get(end).getType() != Token.EOF) {
            end++;
        }
        final List<Token> refused = new ArrayList<>();
        int mode = PASS_NEXT;
        // Where the value of the rebuilt SET begins, and the '=' it follows when one is written.
        int value = -1;
        int sign = -1;
        int at = set + 2;
        if (spoken.get(at).getType() == FrostlakeLexer.RBRACKET) {
            refused.add(spoken.get(at));
        } else {
            at = nextStop(spoken, at, end);
            if (at == end) {
                return null;
            }
            // 0: a name is due; 1: a name was read; 2: an '=' is due.
            int state = spoken.get(at).getType() == FrostlakeLexer.COMMA ? 0 : 2;
            at++;
            boolean reading = true;
            while (reading) {
                final Token token = spoken.get(at);
                final int type = token.getType();
                if (state == 0) {
                    if (isName(type)) {
                        state = 1;
                        at++;
                    } else if (at == end) {
                        refused.add(token);
                        reading = false;
                    } else if (type == FrostlakeLexer.COMMA) {
                        refused.add(token);
                        at++;
                    } else if (type == FrostlakeLexer.RPAREN) {
                        refused.add(token);
                        state = 2;
                        at++;
                    } else {
                        refused.add(token);
                        at = nextStop(spoken, at + 1, end);
                        if (at == end) {
                            reading = false;
                        } else {
                            state = spoken.get(at).getType() == FrostlakeLexer.COMMA ? 0 : 2;
                            at++;
                        }
                    }
                } else if (state == 1) {
                    if (type == FrostlakeLexer.COMMA) {
                        state = 0;
                        at++;
                    } else if (type == FrostlakeLexer.RPAREN) {
                        state = 2;
                        at++;
                    } else {
                        refused.add(token);
                        reading = false;
                        if (type == FrostlakeLexer.EQ || type == FrostlakeLexer.EQ_EQ) {
                            // The ')' of the names is missing at the '=', and the value is read after it.
                            mode = READ_VALUE;
                            sign = at;
                            value = at + 1;
                        }
                    }
                } else {
                    reading = false;
                    if (type == FrostlakeLexer.EQ || type == FrostlakeLexer.EQ_EQ) {
                        mode = READ_VALUE;
                        sign = at;
                        value = at + 1;
                    } else if (at == end) {
                        refused.add(token);
                        mode = READ_NEXT;
                    } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.COMMA) {
                        refused.add(token);
                    } else {
                        // The '=' is missing where a value begins.
                        refused.add(token);
                        mode = READ_VALUE;
                        value = at;
                    }
                }
            }
        }
        final Token name = spoken.get(set + 1);
        final char[] text = sql.toCharArray();
        final int first = spoken.get(set).getStartIndex();
        if (mode == READ_VALUE) {
            // SET <name> = <value>: the name stays, and what stood between it and the value is the '='.
            final int from = name.getStopIndex() + 1;
            final int to = sign >= 0 ? spoken.get(sign).getStopIndex() : spoken.get(value).getStartIndex() - 1;
            if (to < from) {
                return null;
            }
            blank(text, from, to);
            text[to] = '=';
        } else {
            fill(text, first, spoken.get(end).getStartIndex() - 1);
            if (mode == PASS_NEXT && spoken.get(end).getType() == FrostlakeLexer.SEMI) {
                int next = end + 1;
                while (spoken.get(next).getType() != FrostlakeLexer.SEMI && spoken.get(next).getType() != Token.EOF) {
                    next++;
                }
                if (next > end + 1) {
                    fill(text, spoken.get(end + 1).getStartIndex(), spoken.get(next).getStartIndex() - 1);
                }
            }
        }
        final List<String> others = SyntaxErrorListener.linesReportedAfresh(new String(text));
        final List<String> lines = new ArrayList<>();
        final int[] nameAt = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(name));
        for (final String line : others) {
            if (before(SyntaxErrorListener.sentenceCoordinates(line), nameAt)) {
                lines.add(line);
            }
        }
        lines.add(SyntaxErrorListener.sentence(name));
        int[] last = nameAt;
        for (final Token token : refused) {
            lines.add(SyntaxErrorListener.sentence(token));
            last = SyntaxErrorListener.sentenceCoordinates(lines.get(lines.size() - 1));
        }
        for (final String line : others) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && before(last, place)) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The first comma or closing parenthesis from {@code from} on, or {@code end} when the statement holds none. */
    private static int nextStop(final List<Token> spoken, final int from, final int end) {
        int at = from;
        while (at < end && spoken.get(at).getType() != FrostlakeLexer.COMMA
                && spoken.get(at).getType() != FrostlakeLexer.RPAREN) {
            at++;
        }
        return Math.min(at, end);
    }

    /** Whether a place stands before another in the text. */
    private static boolean before(final int[] place, final int[] other) {
        return place != null && other != null
            && (place[0] < other[0] || place[0] == other[0] && place[1] < other[1]);
    }

    /** Puts the filler statement over a stretch of the text, blank where the filler ends. */
    private static void fill(final char[] text, final int from, final int to) {
        if (to - from + 1 < FILLER.length()) {
            return;
        }
        blank(text, from, to);
        for (int c = 0; c < FILLER.length(); c++) {
            text[from + c] = FILLER.charAt(c);
        }
    }

    private static void blank(final char[] text, final int from, final int to) {
        for (int c = from; c <= to && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
    }

    private static boolean isName(final int type) {
        return WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
