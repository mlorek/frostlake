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
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The lines live reports for an ALIAS followed by '(' in a statement of a control construct's body — an IF branch, a
 * LOOP, WHILE, FOR, REPEAT or CASE — when the bracket ends that statement. What follows its semicolon decides (all
 * live-verified):
 *
 * <pre>
 *   BEGIN IF (TRUE) THEN SELECT 1 foo (2); END IF; END                      '(' alone
 *   BEGIN WHILE (TRUE) DO SELECT 1 foo (2); END WHILE; END                  '(' alone
 *   BEGIN IF (TRUE) THEN SELECT 1 foo (2); ELSE RETURN 1; END IF; END       '(', 'ELSE', then '1'
 *   BEGIN IF (TRUE) THEN SELECT 1 foo (2); ELSEIF (FALSE) THEN …            '(', 'ELSEIF', then '('
 *   BEGIN IF (TRUE) THEN SELECT 1 foo (2) x; RETURN 1; END IF; END          '(' alone
 * </pre>
 *
 * <p>The construct's END, or a REPEAT's UNTIL, ends the report. An ELSE is refused, and so is the token after the first
 * name that follows it. An ELSEIF is refused with the token right after it. A name between the bracket and the
 * statement's semicolon ends the report too.
 *
 * <p>After a further statement of the same body live has given the construct up: an ELSE, ELSEIF, CASE's WHEN or
 * REPEAT's UNTIL there is refused as above — ELSEIF, WHEN and UNTIL each with the token right after it — and the
 * construct's own END closes the frame around it instead, taking the word after it as that frame's label or kind.
 * Every END after it closes the next frame out, and the first token once no frame is left is refused. A word after an
 * END that closes a construct of another kind is refused on the way, and a FOR — which no label can be — ends the
 * report (all live-verified):
 *
 * <pre>
 *   BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; END                    '(', then 'END' at 57
 *   BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; RETURN 3; END          '(', then 'RETURN' at 57
 *   BEGIN BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; END; END         '(', then 'END' at 68
 *   BEGIN LOOP IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; END LOOP; END     '(', 'IF' at 58, 'END' at 72
 *   BEGIN FOR i IN 1 TO 2 DO SELECT 1 foo (2); RETURN 1; END FOR; END               '(', then 'FOR' at 57
 * </pre>
 */
final class ConstructBodyAliasParen {

    private ConstructBodyAliasParen() {
    }

    /**
     * The lines live reports when the line named the '(' at token index {@code parenIndex}, or null for any other '('.
     *
     * @param spoken     the default-channel tokens of the input, end of input included
     * @param parenIndex the token index of the '(' the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int parenIndex) {
        int open = -1;
        for (int i = 2; i < spoken.size() && open < 0; i++) {
            if (spoken.get(i).getTokenIndex() == parenIndex && spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                open = i;
            }
        }
        if (open < 0 || !afterAlias(spoken, open) || !inConstructBody(spoken, open)) {
            return null;
        }
        final int close = closingParen(spoken, open);
        if (close < 0 || close + 2 >= spoken.size()) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(open)));
        if (spoken.get(close + 1).getType() != FrostlakeLexer.SEMI) {
            return names().contains(spoken.get(close + 1).getType()) ? lines : null;
        }
        final int nextIndex = close + 2;
        final Token next = spoken.get(nextIndex);
        if (next.getType() == FrostlakeLexer.END || next.getType() == FrostlakeLexer.UNTIL) {
            return lines;
        }
        if (next.getType() == FrostlakeLexer.ELSEIF || next.getType() == FrostlakeLexer.ELSE) {
            return branchLines(spoken, nextIndex, lines);
        }
        if (next.getType() == FrostlakeLexer.WHEN || next.getType() == FrostlakeLexer.EXCEPTION) {
            return null;
        }
        return afterFurtherStatements(spoken, open, nextIndex, lines);
    }

    /**
     * The lines for the construct's own ELSE, ELSEIF, WHEN or UNTIL at {@code at}: the keyword, then for an ELSE the
     * token after the first name that follows it and for the others the token right after the keyword.
     */
    private static List<String> branchLines(final List<Token> spoken, final int at, final List<String> lines) {
        final Token keyword = spoken.get(at);
        lines.add(SyntaxErrorListener.sentence(keyword));
        if (keyword.getType() != FrostlakeLexer.ELSE) {
            if (at + 1 < spoken.size() && spoken.get(at + 1).getType() != Token.EOF) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(at + 1)));
            }
            return lines;
        }
        final IntervalSet names = names();
        for (int i = at + 1; i + 1 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                break;
            }
            if (names.contains(type)) {
                if (spoken.get(i + 1).getType() != Token.EOF) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(i + 1)));
                }
                break;
            }
        }
        return lines;
    }

    /**
     * The lines once further statements of the body follow the bracket's: read up to the body's end at this level,
     * then as the class describes. Null where the body's end is none of the measured kinds.
     */
    private static List<String> afterFurtherStatements(final List<Token> spoken, final int open, final int from,
                                                       final List<String> lines) {
        final List<Integer> frames = openFrames(spoken, open);
        if (frames.size() < 2) {
            return null;
        }
        frames.remove(frames.size() - 1);
        int depth = 0;
        for (int i = from; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            final int type = token.getType();
            if (type == Token.EOF) {
                return null;
            }
            if (opensFrame(spoken, i)) {
                depth++;
            } else if (type == FrostlakeLexer.END && depth > 0) {
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.EXCEPTION) {
                return lines;
            } else if (depth == 0 && (type == FrostlakeLexer.ELSE || type == FrostlakeLexer.ELSEIF
                    || type == FrostlakeLexer.WHEN || type == FrostlakeLexer.UNTIL)) {
                return branchLines(spoken, i, lines);
            } else if (depth == 0 && type == FrostlakeLexer.END) {
                return closingFrames(spoken, i, frames, lines);
            }
        }
        return null;
    }

    /**
     * The lines once the construct's END at {@code end} closes the frames around the construct, innermost first.
     */
    private static List<String> closingFrames(final List<Token> spoken, final int end, final List<Integer> frames,
                                              final List<String> lines) {
        final IntervalSet names = names();
        int depth = 0;
        int i = end;
        while (i < spoken.size()) {
            final Token token = spoken.get(i);
            final int type = token.getType();
            if (type == Token.EOF) {
                return lines;
            }
            if (frames.isEmpty()) {
                lines.add(SyntaxErrorListener.sentence(token));
                return lines;
            }
            if (opensFrame(spoken, i)) {
                depth++;
                i++;
                continue;
            }
            if (type != FrostlakeLexer.END || depth > 0) {
                if (type == FrostlakeLexer.END) {
                    depth--;
                }
                i++;
                continue;
            }
            final int frame = frames.remove(frames.size() - 1).intValue();
            final Token kind = spoken.get(i + 1);
            i++;
            if (frame == FrostlakeLexer.BEGIN) {
                if (kind.getType() == FrostlakeLexer.FOR) {
                    lines.add(SyntaxErrorListener.sentence(kind));
                    return lines;
                }
                if (isLabelWord(names, kind)) {
                    i++;
                }
            } else if (kind.getType() == frame) {
                i++;
            } else if (isLabelWord(names, kind) || kind.getType() == FrostlakeLexer.FOR) {
                lines.add(SyntaxErrorListener.sentence(kind));
                i++;
            }
            while (i < spoken.size() && spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                i++;
            }
        }
        return lines;
    }

    /** Whether a word after END reads as a label: a name, CASE too, but not BEGIN. */
    private static boolean isLabelWord(final IntervalSet names, final Token token) {
        if (token.getType() == FrostlakeLexer.CASE) {
            return true;
        }
        return token.getType() != FrostlakeLexer.BEGIN && names.contains(token.getType());
    }

    private static IntervalSet names() {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]);
    }

    /** Whether the '(' at {@code open} follows a bare alias: a name right after a finished value. */
    private static boolean afterAlias(final List<Token> spoken, final int open) {
        final int word = spoken.get(open - 1).getType();
        final int before = spoken.get(open - 2).getType();
        return (word == FrostlakeLexer.IDENTIFIER || word == FrostlakeLexer.QUOTED_IDENTIFIER)
            && (before == FrostlakeLexer.INTEGER_LITERAL || before == FrostlakeLexer.STRING_LITERAL
                || before == FrostlakeLexer.FLOAT_LITERAL || before == FrostlakeLexer.RPAREN);
    }

    /**
     * Whether the token at {@code index} stands in the body of an IF, LOOP, WHILE, FOR, REPEAT or CASE inside a block:
     * the innermost construct still open there is one of those, read off the tokens.
     */
    private static boolean inConstructBody(final List<Token> spoken, final int index) {
        final List<Integer> open = openFrames(spoken, index);
        return open.size() >= 2 && open.get(0).intValue() == FrostlakeLexer.BEGIN
            && open.get(open.size() - 1).intValue() != FrostlakeLexer.BEGIN;
    }

    /** The kinds of the blocks and constructs still open at {@code index}, outermost first. */
    private static List<Integer> openFrames(final List<Token> spoken, final int index) {
        final List<Integer> open = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            final int type = spoken.get(i).getType();
            if (opensFrame(spoken, i)) {
                open.add(Integer.valueOf(type));
            } else if (type == FrostlakeLexer.END && !open.isEmpty()) {
                open.remove(open.size() - 1);
            }
        }
        return open;
    }

    /**
     * Whether the token at {@code i} opens a frame one END will close: a BEGIN block, or an IF, LOOP, WHILE, FOR,
     * REPEAT or CASE that is not the kind word of an END and not an IF [NOT] EXISTS.
     */
    private static boolean opensFrame(final List<Token> spoken, final int i) {
        final int type = spoken.get(i).getType();
        final boolean closing = i > 0 && spoken.get(i - 1).getType() == FrostlakeLexer.END;
        final int next = i + 1 < spoken.size() ? spoken.get(i + 1).getType() : Token.EOF;
        if (type == FrostlakeLexer.BEGIN) {
            return next != FrostlakeLexer.SEMI && next != FrostlakeLexer.WORK && next != FrostlakeLexer.TRANSACTION;
        }
        return !closing && (type == FrostlakeLexer.IF && next != FrostlakeLexer.EXISTS && next != FrostlakeLexer.NOT
            || type == FrostlakeLexer.LOOP || type == FrostlakeLexer.WHILE || type == FrostlakeLexer.FOR
            || type == FrostlakeLexer.REPEAT || type == FrostlakeLexer.CASE);
    }

    /** The index in {@code spoken} of the ')' closing the '(' at {@code open}, or -1. */
    private static int closingParen(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
