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
 * The lines the account reports for a window frame's offset that is no literal. The offset's first wrong token is
 * refused, and the account then names one more token of the frame (live-verified):
 *
 * <pre>
 *   ROWS BETWEEN v PRECEDING AND CURRENT ROW                 'v', 'AND'
 *   ROWS BETWEEN 1 + 1 PRECEDING AND CURRENT ROW             '+', 'AND'
 *   RANGE BETWEEN INTERVAL '1' DAY PRECEDING AND CURRENT ROW 'DAY', 'PRECEDING'
 *   ROWS BETWEEN -1 FOLLOWING AND CURRENT ROW                '-', 'CURRENT'
 *   ROWS BETWEEN CURRENT ROW AND v FOLLOWING                 'v', 'FOLLOWING'
 *   ROWS v PRECEDING                                         'v', 'PRECEDING'
 *   ROWS - 1 PRECEDING                                       '-', ')'
 * </pre>
 *
 * <p>An offset of the first bound before PRECEDING names the BETWEEN's AND, unless the wrong token stands after the
 * offset's first token right before PRECEDING; one before FOLLOWING names the second bound's first token. An offset of
 * the second bound names its PRECEDING or FOLLOWING. A lone bound names its PRECEDING or FOLLOWING when the wrong
 * token stands right before it, and the token after it otherwise. A text or a NULL offset names nothing more.
 */
final class FrameOffsetLines {

    private FrameOffsetLines() {
    }

    /**
     * The lines for a text whose first fault is a window frame offset's, or null for any other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 1 || fault + 1 >= spoken.size()) {
            return null;
        }
        final int type = spoken.get(fault).getType();
        if (type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.NULL || type == FrostlakeLexer.COLON) {
            return null;
        }
        final int frame = frameWord(spoken, fault);
        if (frame < 0) {
            return null;
        }
        final boolean between = spoken.get(frame + 1).getType() == FrostlakeLexer.BETWEEN;
        final int and = between ? nextOf(spoken, frame + 2, FrostlakeLexer.AND) : -1;
        final int direction = nextDirection(spoken, fault);
        if (direction < 0 || between && and < 0) {
            return null;
        }
        final Token second;
        if (!between) {
            second = direction == fault + 1 ? spoken.get(direction) : spoken.get(direction + 1);
        } else if (fault > and) {
            second = spoken.get(direction);
        } else if (direction > and) {
            return null;
        } else if (spoken.get(direction).getType() == FrostlakeLexer.FOLLOWING) {
            second = spoken.get(and + 1);
        } else {
            second = fault > frame + 2 && direction == fault + 1 ? spoken.get(direction) : spoken.get(and);
        }
        if (second.getType() == Token.EOF) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(second));
        return lines;
    }

    /**
     * The index of the ROWS or RANGE whose frame the token at {@code fault} stands in, read back over the frame's own
     * words and offsets, or -1.
     */
    private static int frameWord(final List<Token> spoken, final int fault) {
        for (int i = fault - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.ROWS || type == FrostlakeLexer.RANGE) {
                return i;
            }
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.COMMA
                    || type == FrostlakeLexer.SEMI) {
                return -1;
            }
        }
        return -1;
    }

    /** The index of the first PRECEDING or FOLLOWING at or after {@code from} within the frame, or -1. */
    private static int nextDirection(final List<Token> spoken, final int from) {
        int depth = 0;
        for (int i = from; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return -1;
                }
                depth--;
            } else if (depth == 0 && (type == FrostlakeLexer.PRECEDING || type == FrostlakeLexer.FOLLOWING)) {
                return i;
            } else if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return -1;
            }
        }
        return -1;
    }

    /** The index of the first token of {@code wanted} at or after {@code from} before the frame closes, or -1. */
    private static int nextOf(final List<Token> spoken, final int from, final int wanted) {
        int depth = 0;
        for (int i = from; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return -1;
                }
                depth--;
            } else if (depth == 0 && type == wanted) {
                return i;
            } else if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return -1;
            }
        }
        return -1;
    }
}
