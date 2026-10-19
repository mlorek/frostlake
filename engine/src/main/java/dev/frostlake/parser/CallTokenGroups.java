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
 * How a statement's default-channel tokens nest in parentheses, for the readings that give a call up: the
 * parentheses open at a token, the call one belongs to, the text with a part of it blanked, and the lines a parse of
 * such a text reports past a place.
 */
final class CallTokenGroups {

    private CallTokenGroups() {
    }

    /**
     * The positions of the parentheses still open at the token at {@code at}, outermost first.
     *
     * @param spoken the default-channel tokens
     * @param at     the position read up to
     * @return the positions of the open parentheses
     */
    static List<Integer> openAt(final List<Token> spoken, final int at) {
        final List<Integer> open = new ArrayList<Integer>();
        for (int i = 0; i < at; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                open.add(Integer.valueOf(i));
            } else if (type == FrostlakeLexer.RPAREN && !open.isEmpty()) {
                open.remove(open.size() - 1);
            } else if (type == FrostlakeLexer.SEMI) {
                open.clear();
            }
        }
        return open;
    }

    /**
     * The position of the parenthesis closing the one at {@code open}, or -1 when its statement never closes it.
     *
     * @param spoken the default-channel tokens
     * @param open   the position of an opening parenthesis
     * @return the position of its closing parenthesis, or -1
     */
    static int closeOf(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (--depth == 0) {
                    return i;
                }
            } else if (type == FrostlakeLexer.SEMI || type == Token.EOF) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * The position of the first token of the name the parenthesis at {@code open} follows as a call's — the word
     * before it with whatever qualifies that word — or -1 when what stands before it names no function.
     *
     * @param spoken the default-channel tokens
     * @param open   the position of an opening parenthesis
     * @return the position the call's name starts at, or -1
     */
    static int callNameStart(final List<Token> spoken, final int open) {
        if (open < 1 || !isCallName(spoken.get(open - 1))) {
            return -1;
        }
        int start = open - 1;
        while (start >= 2 && spoken.get(start - 1).getType() == FrostlakeLexer.DOT
                && isCallName(spoken.get(start - 2))) {
            start -= 2;
        }
        return start;
    }

    /**
     * Whether a token may name a function or an argument: a word that may stand as a name, the words that open a
     * list of another kind apart.
     *
     * @param token the token
     * @return true when it may be such a name
     */
    static boolean isCallName(final Token token) {
        switch (token.getType()) {
            case FrostlakeLexer.OVER:
            case FrostlakeLexer.TABLE:
            case FrostlakeLexer.LATERAL:
            case FrostlakeLexer.VALUES:
            case FrostlakeLexer.USING:
            case FrostlakeLexer.EXISTS:
            case FrostlakeLexer.ANY:
            case FrostlakeLexer.SOME:
            case FrostlakeLexer.ALL:
            case FrostlakeLexer.IN:
            case FrostlakeLexer.AS:
            case FrostlakeLexer.ON:
                return false;
            default:
                final ATN atn = FrostlakeParser._ATN;
                return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(token.getType());
        }
    }

    /**
     * Whether the parenthesis at {@code open} holds a query: a SELECT or a WITH is the first token inside it.
     *
     * @param spoken the default-channel tokens, end of input included
     * @param open   the position of an opening parenthesis
     * @return true when a query stands inside it
     */
    static boolean holdsQuery(final List<Token> spoken, final int open) {
        final int first = spoken.get(open + 1).getType();
        return first == FrostlakeLexer.SELECT || first == FrostlakeLexer.WITH;
    }

    /**
     * Whether a query in parentheses stands where the statement itself, a FROM or a JOIN reads it — the places a
     * call given up inside it is still read on from.
     *
     * @param spoken the default-channel tokens
     * @param open   the position of the query's opening parenthesis
     * @return true when the query is a table of its FROM clause
     */
    static boolean isDerivedTable(final List<Token> spoken, final int open) {
        if (open < 1) {
            return false;
        }
        final int before = spoken.get(open - 1).getType();
        return before == FrostlakeLexer.FROM || before == FrostlakeLexer.JOIN;
    }

    /**
     * The text with the characters from {@code start} up to {@code end} blanked — line breaks kept, so every token
     * after them stays where it was — and {@code put} written over the first of them.
     *
     * @param sql   the text
     * @param start the first character blanked
     * @param end   the character after the last one blanked
     * @param put   what the blanked part begins with, possibly nothing
     * @return the text so changed
     */
    static String blanked(final String sql, final int start, final int end, final String put) {
        final char[] text = sql.toCharArray();
        for (int c = start; c < end && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = c - start < put.length() ? put.charAt(c - start) : ' ';
            }
        }
        return new String(text);
    }

    /**
     * The lines a parse of {@code text} reports past {@code token}'s place.
     *
     * @param text  a statement's text, a part of it blanked
     * @param token the token the lines are wanted after
     * @return the lines, none when the text parses cleanly from there on
     */
    static List<String> linesAfter(final String text, final Token token) {
        final List<String> after = new ArrayList<String>();
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(token));
        if (at == null || text.trim().isEmpty()) {
            return after;
        }
        for (final String line : SyntaxErrorListener.linesReportedFor(text, true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                after.add(line);
            }
        }
        return after;
    }
}
