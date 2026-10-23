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
 * The lines live reports for two words left hanging after an operand (all live-verified).
 *
 * <p>An OVER with no window after it, written after a value, is no fault of its own: the token after it is the
 * one named, and alone (an OVER after an alias is refused itself) —
 * {@code SELECT x OVER w FROM t} is {@code 'w'}, {@code SELECT ROW_NUMBER() OVER FROM t} the {@code 'FROM'}. A
 * parenthesis after it opens a window, whose first token is named when it starts none:
 * {@code SELECT x OVER (nosuch) FROM t} is {@code 'nosuch'}, and nothing after it.
 *
 * <p>A {@code !} calls a method of the name before it, so what follows it is a method's name and its
 * parenthesis. A token that is no name is named, and then — where the name is a select item, bracketed or not — the
 * {@code !} itself, or the dot of a name written with a path: {@code x!1} is {@code '1'} and {@code '!'},
 * {@code x! FROM t} is {@code 'FROM'} and {@code '!'}, {@code x !! y} the second {@code '!'} and the first,
 * {@code t.a ! 2 FROM t} the {@code '2'} and the dot; in a call's argument and in any other clause the token alone.
 * After a value that is no name — a parenthesis, a number, a call — the method's name is the one named, and then its
 * parenthesis: {@code (x)!y()} is {@code 'y'} and {@code '('}; a token that is no method's name is named alone, and a
 * select list read on without the {@code !} and a value written after it: {@code SELECT 1 ! 2 x y} is {@code '2'} and
 * then {@code 'y'}.
 */
final class OperandTailLines {

    private OperandTailLines() {
    }

    /**
     * The lines for a statement whose first fault is such a word, or null when it is not one.
     *
     * @param sql        the text parsed
     * @param spoken     the default-channel tokens of the text parsed, end of input included
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
        if (at < 1 || at >= spoken.size() - 1) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        final Token fault = spoken.get(at);
        if (fault.getType() == FrostlakeLexer.OVER) {
            if (at >= 2 && CallTokenGroups.isCallName(spoken.get(at - 1))
                    && (endsAValue(spoken.get(at - 2)) || spoken.get(at - 2).getType() == FrostlakeLexer.AS)) {
                // The word before it is an alias, which takes no window: the OVER is refused itself.
                return null;
            }
            final Token next = spoken.get(at + 1);
            lines.add(SyntaxErrorListener.sentence(next.getType() == FrostlakeLexer.LPAREN
                ? spoken.get(at + 2) : next));
            return lines;
        }
        if (at >= 2 && spoken.get(at - 1).getType() == FrostlakeLexer.LPAREN
                && spoken.get(at - 2).getType() == FrostlakeLexer.OVER && !opensWindow(fault)) {
            lines.add(SyntaxErrorListener.sentence(fault));
            return lines;
        }
        if (fault.getType() == FrostlakeLexer.BANG && endsUnnamedValue(spoken.get(at - 1))) {
            // The parse stopped at the '!' itself: the token after it is the one live names.
            return afterValueLines(sql, spoken, at + 1);
        }
        if (at >= 3 && spoken.get(at - 2).getType() == FrostlakeLexer.BANG
                && CallTokenGroups.isCallName(spoken.get(at - 1))
                && fault.getType() != FrostlakeLexer.LPAREN && fault.getType() != Token.EOF) {
            return methodArgumentLines(sql, spoken, at);
        }
        if (spoken.get(at - 1).getType() != FrostlakeLexer.BANG || at < 2) {
            return null;
        }
        lines.add(SyntaxErrorListener.sentence(fault));
        if (CallTokenGroups.isCallName(fault) && spoken.get(at + 1).getType() == FrostlakeLexer.LPAREN) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(at + 1)));
            return lines;
        }
        if (!CallTokenGroups.isCallName(spoken.get(at - 2))) {
            return afterValueLines(sql, spoken, at);
        }
        if (standsAsSelectItem(spoken, at - 1)) {
            lines.add(SyntaxErrorListener.sentence(at >= 3 && spoken.get(at - 3).getType() == FrostlakeLexer.DOT
                ? spoken.get(at - 3) : spoken.get(at - 1)));
        }
        return lines;
    }

    /**
     * The lines for the token after a {@code !} that follows a value that is no name: the token, and in a select list
     * whatever the list reports read on without the {@code !} and a value written after it.
     *
     * @param at the token's place among the spoken tokens, the {@code !} straight before it
     */
    private static List<String> afterValueLines(final String sql, final List<Token> spoken, final int at) {
        final List<String> lines = new ArrayList<String>();
        if (at >= spoken.size()) {
            return null;
        }
        final Token fault = spoken.get(at);
        lines.add(SyntaxErrorListener.sentence(fault));
        if (fault.getType() != Token.EOF && endsAValue(fault) && standsAsSelectItem(spoken, at - 1)) {
            final char[] text = sql.toCharArray();
            for (int i = spoken.get(at - 1).getStartIndex(); i <= fault.getStopIndex() && i < text.length; i++) {
                if (text[i] != '\n' && text[i] != '\r') {
                    text[i] = ' ';
                }
            }
            lines.addAll(SyntaxErrorListener.linesReportedFor(new String(text), true));
        }
        return lines;
    }

    /**
     * The lines for the token after a method's name that no parenthesis follows: live reads on as if one opened before
     * the token, so the token is named and then the first fault that reading meets — {@code SELECT x!y z FROM t} is
     * {@code 'z'} and the {@code 'FROM'} no argument list takes, {@code SELECT x!y z} the end of the input, and a
     * function's body {@code x ! y} the end of the input after the ')' that closes the arguments (all live-verified).
     */
    private static List<String> methodArgumentLines(final String sql, final List<Token> spoken, final int at) {
        final Token fault = spoken.get(at);
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(fault));
        final int start = fault.getStartIndex();
        final boolean spaced = start > 0 && (sql.charAt(start - 1) == ' ' || sql.charAt(start - 1) == '\t');
        final String opened = sql.substring(0, spaced ? start - 1 : start) + '(' + sql.substring(start);
        int next = SyntaxErrorListener.firstFaultStartOf(opened);
        // A parenthesis written in, not over a blank, moves what follows it by one.
        if (!spaced && next > start) {
            next--;
        }
        for (int i = at + 1; i < spoken.size() && next > start; i++) {
            if (spoken.get(i).getStartIndex() == next) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                break;
            }
        }
        return lines;
    }

    /** Whether a token ends a value that is no name: a closing bracket or a literal. */
    private static boolean endsUnnamedValue(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET
            || type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.DOLLAR_QUOTED_STRING;
    }

    /**
     * Whether the token at {@code index} stands in a select item: SELECT reached back through brackets that are no
     * call's, before any clause, and not within a call's arguments.
     */
    private static boolean standsAsSelectItem(final List<Token> spoken, final int index) {
        int depth = 0;
        for (int i = index - 1; i >= 0; i--) {
            final Token token = spoken.get(i);
            switch (token.getType()) {
                case FrostlakeLexer.RPAREN:
                    depth++;
                    break;
                case FrostlakeLexer.LPAREN:
                    if (depth == 0 && i > 0 && CallTokenGroups.isCallName(spoken.get(i - 1))) {
                        return false;
                    }
                    depth = Math.max(0, depth - 1);
                    break;
                case FrostlakeLexer.SELECT:
                    if (depth == 0) {
                        return true;
                    }
                    break;
                case FrostlakeLexer.FROM:
                case FrostlakeLexer.WHERE:
                case FrostlakeLexer.BY:
                case FrostlakeLexer.HAVING:
                case FrostlakeLexer.QUALIFY:
                case FrostlakeLexer.LIMIT:
                case FrostlakeLexer.ON:
                case FrostlakeLexer.SEMI:
                case FrostlakeLexer.BEGIN:
                case FrostlakeLexer.RETURN:
                    if (depth == 0) {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    /** Whether a token may end a value: a closing parenthesis or bracket, a literal or a name. */
    private static boolean endsAValue(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.INTEGER_LITERAL
            || type == FrostlakeLexer.FLOAT_LITERAL || type == FrostlakeLexer.QUOTED_IDENTIFIER
            || CallTokenGroups.isCallName(token);
    }

    /** Whether a token may start a window's specification: a closing parenthesis, PARTITION, ORDER or a frame. */
    private static boolean opensWindow(final Token token) {
        switch (token.getType()) {
            case FrostlakeLexer.RPAREN:
            case FrostlakeLexer.PARTITION:
            case FrostlakeLexer.ORDER:
            case FrostlakeLexer.ROWS:
            case FrostlakeLexer.RANGE:
                return true;
            default:
                return false;
        }
    }
}
