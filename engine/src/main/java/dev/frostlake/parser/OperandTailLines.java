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
 * parenthesis. A token that is no name is named, and then the {@code !} itself: {@code x!1} is {@code '1'} and
 * {@code '!'}, {@code x! FROM t} is {@code 'FROM'} and {@code '!'}, {@code x !! y} the second {@code '!'} and the
 * first. After a value that is no name — a parenthesis, a number, a call — the method's name is the one named,
 * and then its parenthesis: {@code (x)!y()} is {@code 'y'} and {@code '('}.
 */
final class OperandTailLines {

    private OperandTailLines() {
    }

    /**
     * The lines for a statement whose first fault is such a word, or null when it is not one.
     *
     * @param spoken     the default-channel tokens of the text parsed, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
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
        if (spoken.get(at - 1).getType() != FrostlakeLexer.BANG || at < 2) {
            return null;
        }
        lines.add(SyntaxErrorListener.sentence(fault));
        final boolean method = CallTokenGroups.isCallName(fault)
            && spoken.get(at + 1).getType() == FrostlakeLexer.LPAREN;
        lines.add(SyntaxErrorListener.sentence(method ? spoken.get(at + 1) : spoken.get(at - 1)));
        return lines;
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
