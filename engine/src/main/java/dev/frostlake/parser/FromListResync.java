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
import org.antlr.v4.runtime.atn.ATN;

/**
 * The lines live reports after a first fault in a query's FROM list: its recovery skips to the first token that may
 * follow the broken table reference and reads on from there as if the reference had been whole (all live-verified):
 *
 * <pre>
 *   SELECT 1 FROM 2, 1                 '2' at 14, then '1' at 17: the list goes on after its comma
 *   SELECT 1 FROM 2, t, 1              '2', then '1' at 20
 *   SELECT 1 FROM 2 WHERE x y          '2', then 'y' at 24: so does a clause
 *   SELECT 1 FROM 2 WHERE              '2', then '&lt;EOF&gt;' at 21
 *   SELECT 1 FROM d + 1 JOIN t ON x y  '+', then 'y' at 32: a join after a table read whole
 *   SELECT 1 FROM 2 JOIN t ON x y      '2' alone: with no table read, the joins are skipped too
 *   SELECT 1 FROM 2 FOR x              '2', then 'x' at 20: a FOR's next token is refused
 *   SELECT 1 FROM t, 2, 1              '2' alone: after a later reference's fault the list does not go on
 *   SELECT 1 FROM 2 x;  SELECT 1 x y   '2', then the next statement's 'y'
 * </pre>
 *
 * <p>The reading is rebuilt on the text: the broken reference is replaced — by a name when nothing of it was read,
 * by nothing when a table was — every position staying where it was written, and the lines after the fault are what
 * a parse of that text reports. A comma's continuation reports no end of input, and a bracket closing the query ends
 * the report.
 */
final class FromListResync {

    private FromListResync() {
    }

    /**
     * The lines live reports when the parse's first line names a token of a query's FROM list, or null.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse) {
        if (sql == null || !statementParse || sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        int fault = -1;
        for (int i = 1; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        final int faultType = fault < 0 ? Token.EOF : spoken.get(fault).getType();
        if (fault < 2 || isEnd(faultType) || faultType == FrostlakeLexer.LPAREN || faultType == FrostlakeLexer.RPAREN) {
            // A refused bracket has readings of its own: see UnmatchedCloseParen and the listener's group rules.
            return null;
        }
        final int[] list = owningFrom(spoken, fault);
        if (list == null) {
            return null;
        }
        final int from = list[0];
        final int faultDepth = list[1];
        final boolean topLevel = list[2] == 1;
        int slotStart = from + 1;
        int depth = 0;
        for (int i = from + 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.COMMA) {
                slotStart = i + 1;
            }
        }
        if (faultDepth > 0 && inCallBracket(spoken, slotStart, fault)) {
            // A fault inside a call's parentheses — IDENTIFIER(…), TABLE(…), a table function — is that call's.
            return null;
        }
        final boolean firstSlot = slotStart == from + 1;
        if (!firstSlot && faultDepth == 0 && slotStart == fault) {
            fault = faultAsFirstReference(sql, spoken, from, slotStart);
        }
        // An AS that no alias follows is not the fault: the token after it is, the end of input included — SELECT 1
        // FROM d AS 1 is '1' at 19 and SELECT 1 FROM d AS '<EOF>' at 18 (live-verified).
        final int brokenFrom = fault;
        if (spoken.get(fault).getType() == FrostlakeLexer.AS && !isName(spoken.get(fault + 1))) {
            fault++;
        }
        final boolean tableRead = faultDepth == 0 && slotStart < fault;
        final Token refused = spoken.get(fault);
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(refused));
        if (refused.getType() == Token.EOF) {
            return lines;
        }
        depth = faultDepth;
        for (int i = fault + 1; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            final int type = token.getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
                continue;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth < 0) {
                    return topLevel ? lines : null;
                }
                continue;
            }
            if (depth > 0) {
                continue;
            }
            if (type == Token.EOF) {
                return lines;
            }
            if (type == FrostlakeLexer.SEMI) {
                // Empty statements after it are no statement of their own: SELECT 1 FROM 2; ; is '2' alone.
                int last = i;
                while (spoken.get(last + 1).getType() == FrostlakeLexer.SEMI) {
                    last++;
                }
                lines.addAll(linesAfter(blankedThrough(sql, spoken.get(last)), spoken.get(last)));
                return lines;
            }
            if (type == FrostlakeLexer.FOR) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i + 1)));
                return lines;
            }
            final boolean comma = type == FrostlakeLexer.COMMA;
            if (comma && !firstSlot) {
                return lines;
            }
            if (type == FrostlakeLexer.LIMIT && spoken.get(i + 1).getType() == Token.EOF) {
                // With no table read, LIMIT is the clause and not the table's alias: SELECT 1 FROM 2 LIMIT is '2', then
                // '<EOF>', where SELECT 1 FROM t LIMIT runs.
                lines.add(SyntaxErrorListener.sentence(spoken.get(i + 1)));
                return lines;
            }
            if (comma || isClauseWord(type) || tableRead && isJoinWord(type)) {
                final String text = tableRead
                    ? replaced(sql, spoken.get(brokenFrom).getStartIndex(), token.getStartIndex(), false)
                    : replaced(sql, spoken.get(slotStart).getStartIndex(), token.getStartIndex(), true);
                final List<String> after = linesAfter(text, refused);
                if (comma && !after.isEmpty() && after.get(0).endsWith("unexpected '<EOF>'.")) {
                    after.remove(0);
                }
                lines.addAll(after);
                return lines;
            }
        }
        return lines;
    }

    /**
     * The FROM whose list holds the token at {@code fault} — {@code [its index, the fault's bracket depth within the
     * list, 1 when the query stands in no bracket]} — or null when the fault stands anywhere else: a FROM of a query,
     * not of a call's ANSI form or another statement's, with no join or condition between it and the fault.
     */
    private static int[] owningFrom(final List<Token> spoken, final int fault) {
        int depth = 0;
        int from = -1;
        for (int i = fault - 1; i >= 0 && from < 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                depth--;
            } else if (depth <= 0 && type == FrostlakeLexer.FROM) {
                from = i;
            } else if (depth <= 0 && (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.ON
                    || type == FrostlakeLexer.USING || isEnd(type) || isClauseWord(type) || isJoinWord(type))) {
                return null;
            }
        }
        if (from < 0) {
            return null;
        }
        final int faultDepth = -depth;
        depth = 0;
        for (int i = from - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return null;
                }
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.SELECT) {
                return new int[] {from, faultDepth, enclosed(spoken, i) ? 0 : 1};
            } else if (depth == 0 && isEnd(type)) {
                return null;
            }
        }
        return null;
    }

    /**
     * The index of the token a later reference of the list is refused at: the fault its reference meets as the list's
     * first. This parser leaves the list at the comma when the reference after it cannot be read whole, and names the
     * reference's first word, where live refuses the token that breaks it — SELECT 1 FROM t, t2 + 1 is '+' at 20
     * (live-verified). The reference's own first token when no other is found.
     */
    private static int faultAsFirstReference(final String sql, final List<Token> spoken, final int from,
                                             final int slotStart) {
        final String text = replaced(sql, spoken.get(from + 1).getStartIndex(),
            spoken.get(slotStart).getStartIndex(), false);
        final List<String> reported = SyntaxErrorListener.linesReportedFor(text, true);
        if (reported.isEmpty()) {
            return slotStart;
        }
        for (int i = slotStart; i < spoken.size() && !isEnd(spoken.get(i).getType()); i++) {
            if (SyntaxErrorListener.sentence(spoken.get(i)).equals(reported.get(0))) {
                return i;
            }
        }
        return slotStart;
    }

    /** Whether the innermost bracket open at {@code fault}, within the reference opening at {@code slotStart}, is a call's. */
    private static boolean inCallBracket(final List<Token> spoken, final int slotStart, final int fault) {
        int depth = 0;
        for (int i = fault - 1; i >= slotStart; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    final int before = i > 0 ? spoken.get(i - 1).getType() : FrostlakeLexer.FROM;
                    return before != FrostlakeLexer.FROM && before != FrostlakeLexer.COMMA
                        && before != FrostlakeLexer.LPAREN && before != FrostlakeLexer.LATERAL && !isJoinWord(before);
                }
                depth--;
            }
        }
        return false;
    }

    /** Whether the token at {@code at} stands inside a bracket still open there. */
    private static boolean enclosed(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return true;
                }
                depth--;
            } else if (type == FrostlakeLexer.SEMI) {
                return false;
            }
        }
        return false;
    }

    /** Whether a token can be a name. */
    private static boolean isName(final Token token) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(token.getType());
    }

    private static boolean isEnd(final int type) {
        return type == Token.EOF || type == FrostlakeLexer.SEMI;
    }

    /** Whether a word opens a clause that may follow a FROM list. */
    private static boolean isClauseWord(final int type) {
        return type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING
            || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT
            || type == FrostlakeLexer.UNION || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS_KW
            || type == FrostlakeLexer.INTERSECT;
    }

    /** Whether a word opens a join of the table before it. */
    private static boolean isJoinWord(final int type) {
        return type == FrostlakeLexer.JOIN || type == FrostlakeLexer.INNER || type == FrostlakeLexer.LEFT
            || type == FrostlakeLexer.RIGHT || type == FrostlakeLexer.FULL || type == FrostlakeLexer.CROSS
            || type == FrostlakeLexer.NATURAL || type == FrostlakeLexer.ASOF;
    }

    /** The text with the characters from {@code start} up to {@code end} blanked — the first one a name when asked. */
    private static String replaced(final String sql, final int start, final int end, final boolean named) {
        final char[] text = sql.toCharArray();
        for (int c = start; c < end && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = named && c == start ? 'x' : ' ';
            }
        }
        return new String(text);
    }

    /** The text with every character up to and including {@code token} blanked, line breaks kept. */
    private static String blankedThrough(final String sql, final Token token) {
        return replaced(sql, 0, token.getStopIndex() + 1, false);
    }

    /** The lines a parse of {@code text} reports after {@code token}'s place. */
    private static List<String> linesAfter(final String text, final Token token) {
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(token));
        final List<String> after = new ArrayList<>();
        if (text.trim().isEmpty()) {
            return after;
        }
        for (final String line : SyntaxErrorListener.linesReportedFor(text, true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && at != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                after.add(line);
            }
        }
        return after;
    }
}
