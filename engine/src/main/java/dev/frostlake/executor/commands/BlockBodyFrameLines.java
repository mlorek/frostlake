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
 * The first bracket is read on from the token refused in it ({@link #readOn}). Wherever the frame reads, a
 * second test after a finished IS test is refused and ends the reading — {@code RETURN a IS NULL IS NULL;}
 * names the second IS — and a quantifier no predicate takes is refused before the token that opens its list:
 * {@code RETURN a NOT LIKE ANY ('x', 'y');} names the ANY and the {@code 'x'}, as a negated LIKE, ILIKE or
 * RLIKE and an RLIKE or REGEXP with ANY or ALL do; an ILIKE ALL names the token alone. Nothing after the
 * semicolon is read: {@code BEGIN RETURN 1; RETURN 2 +; END} is the RETURN alone. A comma the semicolon follows
 * before the first parenthesis or operator is refused, where one an item follows is passed over:
 * {@code BEGIN RETURN [1, ; END} names the comma, {@code BEGIN RETURN 1, 2; END} the RETURN alone; a parenthesis of
 * the body's own that closes the frame early is passed over as any token there is (all live-verified).
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
            if (isSecondTest(framed, i)
                    || type == FrostlakeLexer.COMMA && framed.get(i + 1).getType() == FrostlakeLexer.SEMI) {
                refused.add(framed.get(i));
                return refused;
            }
            if (type == FrostlakeLexer.LPAREN) {
                if (refusesQuantifier(framed, i)) {
                    refused.add(framed.get(i - 1));
                }
                if (i + 1 < end) {
                    refused.add(framed.get(i + 1));
                }
                final int after = pastBracket(framed, i + 1, end);
                if (after < 0) {
                    return refused;
                }
                if (!isQuantifierList(framed, i) && i + 1 < after - 1 && readOn(framed, i + 1, after - 1, refused)) {
                    return refused;
                }
                i = after;
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
            if (isSecondTest(framed, i)) {
                refused.add(token);
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
            } else if (operandRead && type == FrostlakeLexer.IS) {
                // A test of the operand read: the frame goes on after it, and a truth test is no test it reads.
                final int past = pastTest(framed, i, end);
                if (past < 0) {
                    return refused;
                }
                i = past;
                continue;
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
     * Read the first bracket on from the token refused in it. What the account reports there, in the order
     * written, at the bracket's own depth:
     * <ul>
     *   <li>a quantifier no predicate takes, and the token that opens its list, as at the frame's own level —
     *       {@code nosuch(a NOT LIKE ANY ('x', 'y'))} names the {@code a}, the ANY and the {@code 'x'};</li>
     *   <li>a second test after a finished IS test, which ends the reading — {@code nosuch(a IS NULL IS NULL)},
     *       {@code nosuch(a + 1 IS NULL IS NULL)} and {@code nosuch(a IS DISTINCT FROM 1 + 1 IS NULL)} name the
     *       second IS, and nothing after it;</li>
     *   <li>once an operator or a sign has its operand, an IS NULL or IS DISTINCT FROM test is finished, a LIKE
     *       has its pattern or a query in parentheses is read, the next comma or the bracket's closing parenthesis,
     *       which ends the reading: {@code nosuch(a.b.c + 1)} and {@code nosuch(a.b.c, -1)} name the {@code )},
     *       {@code nosuch(a.b.c + 1, 2)} the comma, {@code nosuch(EXISTS (SELECT 1) IS NULL)} the
     *       {@code )}; an IN list is such an operand, {@code nosuch(a.b.c IN (1, 2))} names the {@code )}; an
     *       operator still owed its operand there asks nothing, {@code RETURN (1 +);}.</li>
     * </ul>
     * Names, dots, commas, literals and calls in parentheses ask nothing: {@code nosuch(a.b.c, 1)} and
     * {@code nosuch(SUBSTRING('abc' FROM 1 FOR 2))} report nothing more, and neither does a truth test,
     * {@code nosuch(a IS TRUE IS NULL)}. A bracket the refused token opens is read from within, and one whose
     * refused token is a query's SELECT is not read on (all live-verified).
     *
     * @param framed the frame's tokens
     * @param first  the index of the token refused in the bracket
     * @param close  the index of the bracket's closing parenthesis
     * @param refused the tokens refused so far, added to
     * @return whether the reading ends here
     */
    private static boolean readOn(final List<Token> framed, final int first, final int close,
                                  final List<Token> refused) {
        final int from = first + 1;
        int last = close;
        if (isQueryStart(framed.get(first).getType())) {
            return false;
        }
        if (framed.get(first).getType() == FrostlakeLexer.LPAREN) {
            if (isQueryStart(framed.get(first + 1).getType())) {
                return false;
            }
            final int inner = pastBracket(framed, first + 1, close);
            if (inner < 0 || inner > close) {
                return false;
            }
            last = inner - 1;
        }
        // An operator waiting for its operand, and a finished operation whose delimiter is then refused.
        boolean operandOwed = false;
        boolean delimiterDue = false;
        int j = from;
        while (j < last) {
            final Token token = framed.get(j);
            final int type = token.getType();
            if (type == FrostlakeLexer.LPAREN) {
                final int after = pastBracket(framed, j + 1, last + 1);
                if (after < 0) {
                    return true;
                }
                if (isQuantifierList(framed, j)) {
                    if (refusesQuantifier(framed, j)) {
                        refused.add(framed.get(j - 1));
                    }
                    if (refusesOpener(framed, j) && j + 1 < after - 1) {
                        refused.add(framed.get(j + 1));
                    } else if (!refusesOpener(framed, j)) {
                        delimiterDue = true;
                    }
                    operandOwed = false;
                } else if (operandOwed || framed.get(j - 1).getType() == FrostlakeLexer.IN
                        || j + 1 < last && isQueryStart(framed.get(j + 1).getType())) {
                    delimiterDue = true;
                    operandOwed = false;
                }
                j = after;
                continue;
            }
            if (type == FrostlakeLexer.COMMA) {
                if (operandOwed) {
                    return true;
                }
                if (delimiterDue) {
                    refused.add(token);
                    return true;
                }
            } else if (isSecondTest(framed, j)) {
                refused.add(token);
                return true;
            } else if (type == FrostlakeLexer.IS && isTruthTest(framed, j, last)) {
                return true;
            } else if (completesTest(framed, j) && type == FrostlakeLexer.NULL) {
                delimiterDue = true;
                operandOwed = false;
            } else if (isOperator(type) || isLikeWithPattern(framed, j) || completesTest(framed, j)) {
                operandOwed = true;
            } else if (operandOwed && isOperand(type)) {
                delimiterDue = true;
                operandOwed = false;
            }
            j++;
        }
        if (operandOwed) {
            return true;
        }
        if (delimiterDue && framed.get(last).getType() == FrostlakeLexer.RPAREN) {
            refused.add(framed.get(last));
            return true;
        }
        return false;
    }

    private static boolean isQueryStart(final int type) {
        return type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WITH;
    }

    /** Whether a token is an operand of its own: a name, a literal, TRUE, FALSE or NULL. */
    private static boolean isOperand(final int type) {
        return type == FrostlakeLexer.IDENTIFIER || type == FrostlakeLexer.QUOTED_IDENTIFIER
            || type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.TRUE || type == FrostlakeLexer.FALSE
            || type == FrostlakeLexer.NULL;
    }

    /** Whether the parenthesis at {@code open} opens the list of a LIKE, ILIKE, RLIKE or REGEXP's ANY or ALL. */
    private static boolean isQuantifierList(final List<Token> framed, final int open) {
        if (open < 2) {
            return false;
        }
        final int quantifier = framed.get(open - 1).getType();
        return (quantifier == FrostlakeLexer.ANY || quantifier == FrostlakeLexer.ALL)
            && isLikeWord(framed.get(open - 2).getType());
    }

    /**
     * Whether the quantifier before the list at {@code open} is one no predicate takes: after a negated LIKE,
     * ILIKE, RLIKE or REGEXP, and after an RLIKE or a REGEXP.
     */
    private static boolean refusesQuantifier(final List<Token> framed, final int open) {
        if (!isQuantifierList(framed, open)) {
            return false;
        }
        final int predicate = framed.get(open - 2).getType();
        return open >= 3 && framed.get(open - 3).getType() == FrostlakeLexer.NOT
            || predicate == FrostlakeLexer.RLIKE || predicate == FrostlakeLexer.REGEXP;
    }

    /** Whether the token that opens the list at {@code open} is refused: after a refused quantifier, or ILIKE ALL. */
    private static boolean refusesOpener(final List<Token> framed, final int open) {
        return refusesQuantifier(framed, open) || isQuantifierList(framed, open)
            && framed.get(open - 2).getType() == FrostlakeLexer.ILIKE
            && framed.get(open - 1).getType() == FrostlakeLexer.ALL;
    }

    private static boolean isLikeWord(final int type) {
        return type == FrostlakeLexer.LIKE || type == FrostlakeLexer.ILIKE || type == FrostlakeLexer.RLIKE
            || type == FrostlakeLexer.REGEXP;
    }

    /** Whether the token at {@code at} is a LIKE, ILIKE, RLIKE or REGEXP given a pattern, not a quantifier. */
    private static boolean isLikeWithPattern(final List<Token> framed, final int at) {
        if (!isLikeWord(framed.get(at).getType()) || at + 1 >= framed.size()) {
            return false;
        }
        final int next = framed.get(at + 1).getType();
        return next != FrostlakeLexer.ANY && next != FrostlakeLexer.ALL;
    }

    /**
     * Whether the token at {@code at} finishes an IS test the account reads: the NULL of {@code IS [NOT] NULL}, or
     * the DISTINCT of {@code IS [NOT] DISTINCT FROM}.
     */
    private static boolean completesTest(final List<Token> framed, final int at) {
        final int type = framed.get(at).getType();
        if (type != FrostlakeLexer.NULL && type != FrostlakeLexer.DISTINCT || at < 1) {
            return false;
        }
        final int before = framed.get(at - 1).getType();
        return before == FrostlakeLexer.IS
            || before == FrostlakeLexer.NOT && at >= 2 && framed.get(at - 2).getType() == FrostlakeLexer.IS;
    }

    /**
     * Whether the IS at {@code at} follows a finished test of the same operand: {@code IS [NOT] NULL}, or
     * {@code IS [NOT] DISTINCT FROM} and its operand, one token or operands joined by operators ({@code 1 + 1}).
     */
    private static boolean isSecondTest(final List<Token> framed, final int at) {
        if (framed.get(at).getType() != FrostlakeLexer.IS || at < 2) {
            return false;
        }
        if (framed.get(at - 1).getType() == FrostlakeLexer.NULL) {
            return completesTest(framed, at - 1);
        }
        int operand = at - 1;
        while (operand >= 2 && isOperator(framed.get(operand - 1).getType())
                && isOperand(framed.get(operand - 2).getType())) {
            operand -= 2;
        }
        return operand >= 3 && framed.get(operand - 1).getType() == FrostlakeLexer.FROM
            && framed.get(operand - 2).getType() == FrostlakeLexer.DISTINCT && completesTest(framed, operand - 2);
    }

    /** Whether the IS at {@code at} opens a truth test, {@code IS [NOT] TRUE} or {@code IS [NOT] FALSE}. */
    private static boolean isTruthTest(final List<Token> framed, final int at, final int end) {
        int next = at + 1;
        if (next < end && framed.get(next).getType() == FrostlakeLexer.NOT) {
            next++;
        }
        if (next >= end) {
            return false;
        }
        final int type = framed.get(next).getType();
        return type == FrostlakeLexer.TRUE || type == FrostlakeLexer.FALSE;
    }

    /**
     * The index after the IS test starting at {@code at}: {@code IS [NOT] NULL}, or {@code IS [NOT] DISTINCT
     * FROM} and a single-token operand; -1 for a truth test or one the frame does not read.
     */
    private static int pastTest(final List<Token> framed, final int at, final int end) {
        int next = at + 1;
        if (next < end && framed.get(next).getType() == FrostlakeLexer.NOT) {
            next++;
        }
        if (next >= end) {
            return -1;
        }
        final int type = framed.get(next).getType();
        if (type == FrostlakeLexer.NULL) {
            return next + 1;
        }
        if (type == FrostlakeLexer.DISTINCT && next + 2 < end
                && framed.get(next + 1).getType() == FrostlakeLexer.FROM) {
            return next + 3;
        }
        return -1;
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
