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
 *   ROWS BETWEEN v v PRECEDING AND CURRENT ROW               'v', 'PRECEDING'
 * </pre>
 *
 * <p>An offset of the first bound before PRECEDING names the BETWEEN's AND, unless the wrong token stands after the
 * offset's first token right before PRECEDING, or the offset is two names; one before FOLLOWING names the second
 * bound's first token. An offset of the second bound names its PRECEDING or FOLLOWING. A lone bound names its
 * PRECEDING or FOLLOWING when the wrong token stands right before it, and the token after it otherwise. A text or a
 * NULL offset names nothing more, and neither does an offset of a window a QUALIFY reads.
 *
 * <p>A bind written as the offset, {@code :k}, names its colon and the word after its name, and in a block one token
 * more: in a statement of the block the token after that word; in a query the block assigns,
 * {@code LET res RESULTSET := (…)}, the token after the name its FROM reads first — or, when the word is followed by
 * {@code AND UNBOUNDED FOLLOWING}, that FOLLOWING. Outside a block it names nothing more.
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
        if (type == FrostlakeLexer.COLON) {
            return bindLines(spoken, fault);
        }
        if (type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.NULL) {
            return null;
        }
        final int frame = frameWord(spoken, fault);
        if (frame < 0) {
            return null;
        }
        if (readByQualify(spoken, frame)) {
            return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(fault)));
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
            final boolean twoNames = direction == fault + 2 && SyntaxErrorListener.isNameLike(spoken.get(fault))
                && SyntaxErrorListener.isNameLike(spoken.get(fault + 1));
            second = fault > frame + 2 && direction == fault + 1 || twoNames ? spoken.get(direction) : spoken.get(and);
        }
        if (second.getType() == Token.EOF) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(second));
        return lines;
    }

    /** The lines for a bind written as a frame's offset, or null when the colon opens none. */
    private static List<String> bindLines(final List<Token> spoken, final int fault) {
        if (fault + 2 >= spoken.size() || !SyntaxErrorListener.isNameLike(spoken.get(fault + 1))) {
            return null;
        }
        final int frame = frameWord(spoken, fault);
        final int direction = fault + 2;
        final int word = spoken.get(direction).getType();
        if (frame < 0 || word != FrostlakeLexer.PRECEDING && word != FrostlakeLexer.FOLLOWING) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(direction)));
        final Token third = inBlock(spoken, frame) ? blockToken(spoken, frame, direction) : null;
        if (third != null && third.getType() != Token.EOF) {
            lines.add(SyntaxErrorListener.sentence(third));
        }
        return lines;
    }

    /**
     * The token a block's bind offset names last: the one after the FROM's first name in an assigned query, the
     * FOLLOWING of {@code AND UNBOUNDED FOLLOWING} right after the offset's word there, and the token after the word
     * in a statement of the block; null when none is found.
     */
    private static Token blockToken(final List<Token> spoken, final int frame, final int direction) {
        final int select = querySelect(spoken, frame);
        final boolean assigned = select >= 2 && spoken.get(select - 1).getType() == FrostlakeLexer.LPAREN
            && spoken.get(select - 2).getType() == FrostlakeLexer.COLON_EQ;
        if (!assigned) {
            return direction + 1 < spoken.size() ? spoken.get(direction + 1) : null;
        }
        if (direction + 3 < spoken.size() && spoken.get(direction + 1).getType() == FrostlakeLexer.AND
                && spoken.get(direction + 2).getType() == FrostlakeLexer.UNBOUNDED
                && spoken.get(direction + 3).getType() == FrostlakeLexer.FOLLOWING) {
            return spoken.get(direction + 3);
        }
        int depth = 0;
        for (int i = select + 1; i + 2 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return null;
                }
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.FROM) {
                return spoken.get(i + 2);
            } else if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return null;
            }
        }
        return null;
    }

    /** The index of the SELECT whose query the frame stands in, read back over the parentheses around it, or -1. */
    private static int querySelect(final List<Token> spoken, final int frame) {
        int depth = 0;
        for (int i = frame - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth > 0) {
                    depth--;
                }
            } else if (type == FrostlakeLexer.SELECT) {
                return i;
            } else if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
        }
        return -1;
    }

    /** Whether the frame stands in a block: a BEGIN is written before it. */
    private static boolean inBlock(final List<Token> spoken, final int frame) {
        for (int i = frame - 1; i >= 0; i--) {
            if (spoken.get(i).getType() == FrostlakeLexer.BEGIN) {
                return true;
            }
        }
        return false;
    }

    /** Whether the frame's window stands in a QUALIFY, outside the parentheses the frame is written in. */
    private static boolean readByQualify(final List<Token> spoken, final int frame) {
        int depth = 0;
        boolean outside = false;
        for (int i = frame - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth > 0) {
                    depth--;
                } else {
                    outside = true;
                }
            } else if (outside && depth == 0) {
                if (type == FrostlakeLexer.QUALIFY) {
                    return true;
                }
                if (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
                        || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.SEMI) {
                    return false;
                }
            }
        }
        return false;
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
