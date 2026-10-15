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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.parser.FrostlakeLexer;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * What the account goes on to refuse in a function's body that opens a block and reads as none. Such a body is
 * read as the function's expression instead: its first word is a name, the token after it is refused, and the
 * frame then reads on to the body's first semicolon, outside every bracket of the body. What stands before the
 * first parenthesis or operator is passed over; from there on:
 * <ul>
 *   <li>the token after that first parenthesis is refused, whatever it is — {@code BEGIN RETURN (1; END} names
 *       the {@code 1}, {@code RETURN ABS((SELECT …))} the second parenthesis, {@code IF (TRUE)} the TRUE — and
 *       the rest of that bracket is passed over, as is what follows it up to the next operator or bracket;</li>
 *   <li>after an operator an operand is due: the semicolon or a closing parenthesis there is refused, a sign of
 *       its own between them or not — {@code RETURN 1 +;}, {@code RETURN 1 = ;}, {@code RETURN 1 + + ;},
 *       {@code RETURN 1 AND ;}, {@code RETURN 1 :: ;};</li>
 *   <li>a later bracket is read as the operand it is: a query in it that does not read is refused at its SELECT,
 *       and its closing parenthesis when an operator stands before it — {@code RETURN 1 + (2 +);};</li>
 *   <li>a word or a literal after a finished operand is refused, and nothing after it is read:
 *       {@code RETURN 1 + 2 x;} names the {@code x}.</li>
 * </ul>
 * Nothing after the semicolon is read: {@code BEGIN RETURN 1; RETURN 2 +; END} is the RETURN alone (all
 * live-verified).
 */
final class BlockBodyFrameLines {

    private BlockBodyFrameLines() {
    }

    /**
     * Whether the frame's reading on is known for a body refused at {@code token}: a scripting statement's
     * opening word — RETURN, LET, IF — a name, or a VALUES. After a SQL statement's keyword nothing more is
     * reported.
     */
    static boolean isReadOnFrom(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.RETURN || type == FrostlakeLexer.LET || type == FrostlakeLexer.IF
            || type == FrostlakeLexer.VALUES || type == FrostlakeLexer.IDENTIFIER
            || type == FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    /**
     * @param text   the body inside its frame of parentheses
     * @param framed the tokens of that text
     * @param stop   the index of the token the frame was refused at first
     * @return the tokens refused after it, in the order read
     */
    static List<Token> refusedAfter(final String text, final List<Token> framed, final int stop) {
        final List<Token> refused = new ArrayList<>();
        // The frame's own closing parenthesis is the last token and is no part of the body.
        final int end = framed.size() - 1;
        int i = stop + 1;
        // What is passed over until the frame can go on: at a parenthesis or at an operator.
        boolean operandDue = false;
        boolean passingOver = true;
        while (i < end && passingOver) {
            final int type = framed.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return refused;
            }
            if (type == FrostlakeLexer.LPAREN) {
                if (i + 1 < end) {
                    refused.add(framed.get(i + 1));
                }
                i = pastBracket(framed, i + 1, end);
                if (i < 0) {
                    return refused;
                }
                break;
            }
            if (isOperator(type)) {
                operandDue = true;
                passingOver = false;
            }
            i++;
        }
        // After the first bracket what follows is passed over again, up to an operator or a bracket.
        boolean operandRead = false;
        while (i < end) {
            final Token token = framed.get(i);
            final int type = token.getType();
            if (type == FrostlakeLexer.SEMI) {
                if (operandDue) {
                    refused.add(token);
                }
                return refused;
            }
            if (type == FrostlakeLexer.LPAREN) {
                final int after = pastBracket(framed, i + 1, end);
                if (after < 0) {
                    return refused;
                }
                final Token inside = refusedInBracket(text, framed, i, after - 1);
                if (inside != null) {
                    refused.add(inside);
                }
                i = after;
                operandDue = false;
                operandRead = true;
                continue;
            }
            if (operandDue) {
                if (type == FrostlakeLexer.RPAREN) {
                    refused.add(token);
                    return refused;
                }
                // A sign is the operand's own; any other token is the operand.
                if (type != FrostlakeLexer.PLUS && type != FrostlakeLexer.MINUS) {
                    operandDue = false;
                    operandRead = true;
                }
            } else if (isOperator(type)) {
                operandDue = true;
            } else if (operandRead) {
                if (isWordOrLiteral(token)) {
                    refused.add(token);
                }
                return refused;
            }
            i++;
        }
        return refused;
    }

    /**
     * What a bracket read as an operand is refused at: its query's SELECT when the query does not read, its
     * closing parenthesis when an operator stands before it, or null.
     *
     * @param open  the index of the bracket's opening parenthesis
     * @param close the index of its closing one
     */
    private static Token refusedInBracket(final String text, final List<Token> framed, final int open,
                                          final int close) {
        if (close <= open + 1) {
            return null;
        }
        final int first = framed.get(open + 1).getType();
        if (first == FrostlakeLexer.SELECT || first == FrostlakeLexer.WITH) {
            final String bracket = text.substring(framed.get(open).getStartIndex(),
                framed.get(close).getStopIndex() + 1);
            return AntlrExpressionParser.offendingToken(bracket) == null ? null : framed.get(open + 1);
        }
        return isOperator(framed.get(close - 1).getType()) ? framed.get(close) : null;
    }

    /**
     * The index after the parenthesis closing a bracket whose first token stands at {@code from}, or -1 when the
     * body's first semicolon comes before it.
     */
    private static int pastBracket(final List<Token> framed, final int from, final int end) {
        int depth = 1;
        for (int i = from; i < end; i++) {
            final int type = framed.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i + 1;
            }
        }
        return end;
    }

    private static boolean isWordOrLiteral(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.IDENTIFIER || type == FrostlakeLexer.QUOTED_IDENTIFIER
            || type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL;
    }

    private static boolean isOperator(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE
            || type == FrostlakeLexer.EQ || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.LT
            || type == FrostlakeLexer.LTE || type == FrostlakeLexer.GT || type == FrostlakeLexer.GTE
            || type == FrostlakeLexer.AND || type == FrostlakeLexer.OR || type == FrostlakeLexer.DOUBLE_COLON;
    }
}
