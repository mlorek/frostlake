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
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;

/**
 * The lines live stacks after a star's misplaced RENAME or REPLACE outside a select item of its own: in a clause and
 * under another call of a select item. Live abandons the star's call at the keyword and reads on from the first token
 * that may follow it; a '(' there is the outer-join marker {@code (+)}, whose inner token is refused (all live-verified
 * over {@code fz (id INT, b BOOLEAN)}):
 *
 * <pre>
 *   SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1 AND x y   'REPLACE' 30, '1' 39, 'y' 59
 *   SELECT 1 FROM fz WHERE HASH(* REPLACE x) = 1 AND x y           'REPLACE' 30, 'y' 51
 *   SELECT 1 FROM fz WHERE HASH(* REPLACE ()) = 1                  'REPLACE' 30, ')' 39, ')' 40
 *   SELECT 1 FROM fz WHERE HASH(* REPLACE (1 + 2)) = 1 AND x y     'REPLACE' 30, '1' 39
 *   SELECT 1 FROM fz WHERE HASH(* REPLACE (+ 1)) = 1 AND x y       'REPLACE' 30, '1' 41
 *   SELECT 1 FROM fz ORDER BY HASH(* REPLACE (1 AS id)), x y       'REPLACE' 33, '1' 42, 'y' 55
 *   SELECT 1 FROM fz WHERE UPPER(HASH(* REPLACE x y)) = 1          'REPLACE' 36, ')' 48
 *   SELECT UPPER(HASH(* REPLACE (1 AS id))) FROM fz                'REPLACE' 20, '1' 29, ')' 36
 *   SELECT UPPER(HASH(* REPLACE x y)) FROM fz                      'REPLACE' 20, 'y' 30
 *   SELECT UPPER(HASH(* REPLACE 'x')) FROM fz                      'REPLACE' 20, ')' 32
 * </pre>
 *
 * <p>In a clause the reading resumes where the star's call, closed at the keyword, may be followed; the marker's token
 * is refused the way a parser recovering one token at a time does — a ')' stands for the missing '+', a single token
 * before a '+' or before the marker's ')' is dropped, and a fault right after a ')' reached that way is silent, as is
 * the rest of the statement — and the reading resumes after it the same way. Under another call of a select item a
 * word, an AS or an operator after the keyword closes that call too (one level up a word may be the item's alias), a
 * comma or a ')' carries that call on, FROM or a clause word ends the item, and the marker is read on the item itself,
 * whose alias may follow; a word right before the star call's own ')' is dropped.
 *
 * <p>Every reading is rebuilt on the text: the tokens live skips are blanked, the brackets it takes as closed are
 * written where the keyword stood, every position staying where it was, and the lines after the refused tokens are
 * what a parse of that text reports.
 */
final class StarModifierResync {

    /** How many tokens after the keyword a reading looks for the token it resumes at. */
    private static final int SCAN = 48;

    private StarModifierResync() {
    }

    /**
     * The lines after the keyword at {@code keyword} for a star call standing in a clause.
     *
     * @param sql     the text parsed
     * @param spoken  its default-channel tokens, end of input included
     * @param keyword the index of the refused RENAME or REPLACE
     * @param closer  the character that closes the star's call
     * @return the lines after the keyword's own
     */
    static List<String> clause(final String sql, final List<Token> spoken, final int keyword, final String closer) {
        final Token refused = spoken.get(keyword);
        for (int i = keyword + 1; i < spoken.size() && i <= keyword + SCAN; i++) {
            final Token token = spoken.get(i);
            if (token.getType() == Token.EOF) {
                return Collections.emptyList();
            }
            if (token.getType() == FrostlakeLexer.LPAREN) {
                return operandMarker(sql, spoken, keyword, i, closer);
            }
            if (token.getType() == FrostlakeLexer.FROM) {
                continue;
            }
            final String text = closedAt(sql, refused, closer, token.getStartIndex());
            if (resumesAt(text, token)) {
                return linesAfter(text, refused);
            }
        }
        return Collections.emptyList();
    }

    /**
     * The lines after the keyword at {@code keyword} for a star call under another call or CAST of a select item.
     *
     * @param sql       the text parsed
     * @param spoken    its default-channel tokens, end of input included
     * @param keyword   the index of the refused RENAME or REPLACE
     * @param callClose the index of the ')' closing the star's call, or -1
     * @param place     where the star's call stands
     * @return the lines after the keyword's own
     */
    static List<String> nested(final String sql, final List<Token> spoken, final int keyword, final int callClose,
                               final StarModifierPlace place) {
        final Token refused = spoken.get(keyword);
        final String twoLevels = closersFrom(spoken, indexOf(spoken, place.enclosingOpener()), keyword);
        for (int i = keyword + 1; i < spoken.size() && i <= keyword + SCAN; i++) {
            final Token token = spoken.get(i);
            final int type = token.getType();
            if (type == Token.EOF) {
                return Collections.emptyList();
            }
            if (type == FrostlakeLexer.LPAREN) {
                return itemMarker(sql, spoken, keyword, i, place);
            }
            if (isName(token) || type == FrostlakeLexer.AS) {
                if (callClose == i + 1) {
                    // The word is dropped and the call's ')' taken for the enclosing call's: a fault right after it
                    // is silent.
                    final String text = closedAt(sql, refused, ")", spoken.get(callClose).getStartIndex());
                    return unlessRightAfter(linesAfter(text, refused), spoken.get(callClose + 1));
                }
                // A word closes the enclosing call as well: one level up it may be the item's alias.
                return twoLevels == null ? Collections.<String>emptyList()
                    : resume(sql, spoken, refused, twoLevels, i, refused);
            }
            if (type == FrostlakeLexer.FROM || type == FrostlakeLexer.SEMI || isClauseWord(type)) {
                return outerAt(sql, spoken, place, i);
            }
            final String text = closedAt(sql, refused, ")", token.getStartIndex());
            if (resumesAt(text, token)) {
                if (type == FrostlakeLexer.COMMA || type == FrostlakeLexer.RPAREN) {
                    // The enclosing call's list goes on, or its ')' closes it.
                    return linesAfter(text, refused);
                }
                // An operator applies one level up, to the enclosing call's value.
                return twoLevels == null ? Collections.<String>emptyList()
                    : resume(sql, spoken, refused, twoLevels, i, refused);
            }
        }
        return Collections.emptyList();
    }

    /** The marker read on the star call's operand in a clause: its '(' at {@code open}. */
    private static List<String> operandMarker(final String sql, final List<Token> spoken, final int keyword,
                                              final int open, final String closer) {
        final Token refused = spoken.get(keyword);
        final Token inner = spoken.get(open + 1);
        final List<String> lines = new ArrayList<>();
        if (inner.getType() == Token.EOF) {
            lines.add(SyntaxErrorListener.sentence(inner));
            return lines;
        }
        if (inner.getType() == FrostlakeLexer.PLUS) {
            final Token close = spoken.get(open + 2);
            if (close.getType() == FrostlakeLexer.RPAREN) {
                final String text = closedAt(sql, refused, closer, close.getStopIndex() + 1);
                return linesAfter(text, close);
            }
            lines.add(SyntaxErrorListener.sentence(close));
            if (close.getType() != Token.EOF && spoken.get(open + 3).getType() == FrostlakeLexer.RPAREN) {
                // The token is dropped and the ')' after it closes the marker: a fault right after it is silent.
                final Token skipped = spoken.get(open + 3);
                lines.addAll(unlessRightAfter(linesAfter(closedAt(sql, refused, closer, skipped.getStopIndex() + 1),
                    skipped), spoken.get(open + 4)));
            } else if (close.getType() != Token.EOF) {
                lines.addAll(resume(sql, spoken, refused, closer, open + 3, close));
            }
            return lines;
        }
        lines.add(SyntaxErrorListener.sentence(inner));
        if (inner.getType() == FrostlakeLexer.RPAREN) {
            // The ')' stands for the missing '+' and closes the marker.
            final String text = closedAt(sql, refused, closer, inner.getStopIndex() + 1);
            lines.addAll(linesAfter(text, inner));
            return lines;
        }
        if (spoken.get(open + 2).getType() == FrostlakeLexer.PLUS) {
            // The token before the '+' is dropped and the '+' taken; the marker's ')' is then matched or skipped.
            final Token close = spoken.get(open + 3);
            if (close.getType() == FrostlakeLexer.RPAREN) {
                lines.addAll(linesAfter(closedAt(sql, refused, closer, close.getStopIndex() + 1), close));
            } else if (close.getType() != Token.EOF && spoken.get(open + 4).getType() == FrostlakeLexer.RPAREN) {
                final Token skipped = spoken.get(open + 4);
                lines.addAll(unlessRightAfter(linesAfter(closedAt(sql, refused, closer, skipped.getStopIndex() + 1),
                    skipped), spoken.get(open + 5)));
            } else if (close.getType() != Token.EOF) {
                lines.addAll(resume(sql, spoken, refused, closer, open + 4, close));
            }
            return lines;
        }
        lines.addAll(resume(sql, spoken, refused, closer, open + 2, inner));
        return lines;
    }

    /** The marker read on the select item a nested star call stands in: its '(' at {@code open}. */
    private static List<String> itemMarker(final String sql, final List<Token> spoken, final int keyword,
                                           final int open, final StarModifierPlace place) {
        final Token refused = spoken.get(keyword);
        final Token inner = spoken.get(open + 1);
        final List<String> lines = new ArrayList<>();
        if (inner.getType() == Token.EOF) {
            lines.add(SyntaxErrorListener.sentence(inner));
            return lines;
        }
        if (inner.getType() == FrostlakeLexer.RPAREN) {
            // The '(' is dropped and its ')' taken for the enclosing call's: a fault right after it is silent.
            final String text = closedAt(sql, refused, ")", inner.getStartIndex());
            return unlessRightAfter(linesAfter(text, refused), spoken.get(open + 2));
        }
        if (inner.getType() == FrostlakeLexer.PLUS) {
            final Token close = spoken.get(open + 2);
            if (close.getType() == FrostlakeLexer.RPAREN) {
                return outerAt(sql, spoken, place, open + 3);
            }
            lines.add(SyntaxErrorListener.sentence(close));
            if (close.getType() != Token.EOF && spoken.get(open + 3).getType() == FrostlakeLexer.RPAREN) {
                // The token is dropped and the ')' after it closes the marker: a fault right after it is silent.
                lines.addAll(unlessRightAfter(outerAt(sql, spoken, place, open + 4), spoken.get(open + 4)));
            } else if (close.getType() != Token.EOF) {
                lines.addAll(outerResume(sql, spoken, place, open + 3));
            }
            return lines;
        }
        lines.add(SyntaxErrorListener.sentence(inner));
        if (spoken.get(open + 2).getType() == FrostlakeLexer.PLUS) {
            final Token close = spoken.get(open + 3);
            if (close.getType() == FrostlakeLexer.RPAREN) {
                lines.addAll(outerAt(sql, spoken, place, open + 4));
            } else if (close.getType() != Token.EOF && spoken.get(open + 4).getType() == FrostlakeLexer.RPAREN) {
                lines.addAll(unlessRightAfter(outerAt(sql, spoken, place, open + 5), spoken.get(open + 5)));
            } else if (close.getType() != Token.EOF) {
                lines.addAll(outerResume(sql, spoken, place, open + 4));
            }
            return lines;
        }
        lines.addAll(outerResume(sql, spoken, place, open + 1));
        return lines;
    }

    /**
     * The lines of the text with the star's call closed at the keyword by {@code closers} and read on from the first
     * token, from {@code from} on, that may stand there — those after {@code after}.
     */
    private static List<String> resume(final String sql, final List<Token> spoken, final Token refused,
                                       final String closers, final int from, final Token after) {
        for (int i = from; i < spoken.size() && i <= from + SCAN; i++) {
            final Token token = spoken.get(i);
            if (token.getType() == Token.EOF) {
                return Collections.emptyList();
            }
            // A FROM inside a call's brackets is read here as the ANSI argument form, which live never reads.
            if (token.getType() == FrostlakeLexer.FROM) {
                continue;
            }
            final String text = closedAt(sql, refused, closers, token.getStartIndex());
            if (resumesAt(text, token)) {
                return linesAfter(text, after);
            }
        }
        return Collections.emptyList();
    }

    /** The lines of the item's own reading from the token at {@code at} on, whatever that token is. */
    private static List<String> outerAt(final String sql, final List<Token> spoken, final StarModifierPlace place,
                                        final int at) {
        return linesAfter(outerText(sql, place, spoken.get(at)), spoken.get(at - 1));
    }

    /** The lines of the item's own reading from the first token, from {@code from} on, that may stand there. */
    private static List<String> outerResume(final String sql, final List<Token> spoken, final StarModifierPlace place,
                                            final int from) {
        for (int i = from; i < spoken.size() && i <= from + SCAN; i++) {
            final Token token = spoken.get(i);
            final String text = outerText(sql, place, token);
            if (token.getType() == Token.EOF || resumesAt(text, token)) {
                return linesAfter(text, spoken.get(from - 1));
            }
        }
        return Collections.emptyList();
    }

    /** The text read on at the level of the star's select item from {@code resume} on, the item's value a literal. */
    private static String outerText(final String sql, final StarModifierPlace place, final Token resume) {
        final char[] text = sql.toCharArray();
        final int start = place.itemStart().getStartIndex();
        blank(text, start, resume.getStartIndex());
        text[start] = '1';
        return new String(text);
    }

    /**
     * The closing characters of every bracket opened from the token at {@code from} up to the one at {@code keyword},
     * innermost first, or null when they are unbalanced or would not fit in the keyword's place.
     */
    static String closersFrom(final List<Token> spoken, final int from, final int keyword) {
        if (from < 0) {
            return null;
        }
        final Deque<Integer> open = new ArrayDeque<>();
        for (int i = from; i < keyword; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE || type == FrostlakeLexer.LBRACKET) {
                open.push(Integer.valueOf(type));
            } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACE || type == FrostlakeLexer.RBRACKET) {
                if (open.isEmpty()) {
                    return null;
                }
                open.pop();
            }
        }
        final StringBuilder closers = new StringBuilder();
        while (!open.isEmpty()) {
            final int type = open.pop().intValue();
            closers.append(type == FrostlakeLexer.LPAREN ? ')' : type == FrostlakeLexer.LBRACE ? '}' : ']');
        }
        final Token refused = spoken.get(keyword);
        return closers.length() == 0 || closers.length() > refused.getStopIndex() - refused.getStartIndex() + 1
            ? null : closers.toString();
    }

    /** The index of {@code token} among the spoken tokens, or -1. */
    static int indexOf(final List<Token> spoken, final Token token) {
        if (token == null) {
            return -1;
        }
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getStartIndex() == token.getStartIndex()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The text with {@code closers} written where the keyword stands and every other character from the keyword up to
     * {@code end} blanked, line breaks kept.
     */
    static String closedAt(final String sql, final Token keyword, final String closers, final int end) {
        final char[] text = sql.toCharArray();
        blank(text, keyword.getStartIndex(), end);
        for (int c = 0; c < closers.length(); c++) {
            text[keyword.getStartIndex() + c] = closers.charAt(c);
        }
        return new String(text);
    }

    /** The text with every character from {@code start} up to {@code end} blanked, line breaks kept. */
    static String blanked(final String sql, final int start, final int end) {
        final char[] text = sql.toCharArray();
        blank(text, start, end);
        return new String(text);
    }

    private static void blank(final char[] text, final int start, final int end) {
        for (int c = start; c < end && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
    }

    /** Whether a parse of {@code text} reads the token at {@code token}'s place without a fault there or before it. */
    private static boolean resumesAt(final String text, final Token token) {
        final String reported = SyntaxErrorListener.firstLineReportedFor(text);
        if (reported == null) {
            return true;
        }
        final int[] first = SyntaxErrorListener.sentenceCoordinates(reported);
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(token));
        return first != null && at != null && isAfter(first, at);
    }

    /** The lines a parse of {@code text} reports after {@code token}'s place. */
    static List<String> linesAfter(final String text, final Token token) {
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(token));
        final List<String> after = new ArrayList<>();
        for (final String line : SyntaxErrorListener.linesReportedFor(text, true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && at != null && isAfter(place, at)) {
                after.add(line);
            }
        }
        return after;
    }

    /** The lines, or none when the first of them names {@code next}: a fault read before any token was taken. */
    static List<String> unlessRightAfter(final List<String> lines, final Token next) {
        return !lines.isEmpty() && lines.get(0).equals(SyntaxErrorListener.sentence(next))
            ? Collections.<String>emptyList() : lines;
    }

    /** Whether a word opens a clause that may follow a select list. */
    private static boolean isClauseWord(final int type) {
        return type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING
            || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT
            || type == FrostlakeLexer.UNION || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS_KW
            || type == FrostlakeLexer.INTERSECT;
    }

    private static boolean isAfter(final int[] candidate, final int[] reference) {
        return candidate[0] > reference[0] || candidate[0] == reference[0] && candidate[1] > reference[1];
    }

    /** Whether a token can be a name: an identifier, a quoted one, or a keyword the grammar reads as one. */
    static boolean isName(final Token token) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(token.getType());
    }
}
