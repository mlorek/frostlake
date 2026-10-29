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

import dev.frostlake.parser.FrostlakeLexer;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The lines the account gives after the first when it refuses a SQL function's query body at its first word — for a
 * row count that is no count or a parameter, in the body or in a query nested in it, and for a WINDOW clause. The
 * account reads the framed body once more as an expression, recovering from each fault it meets, and refuses a token
 * wherever it breaks off with a token read since its last refusal (all live-verified over {@code t (a INT, b INT)}).
 * Positions count in the frame, {@code (} + body + {@code )}:
 * <ul>
 *   <li>after the first word it skips to a parenthesis, a comma, a {@code ::} or a word that may follow an operand
 *       (an arithmetic or comparison operator, {@code ||}, AND, OR, LIKE, IN, IS, NOT, BETWEEN);</li>
 *   <li>a parenthesis met so is read as an outer-join marker {@code (+)}: the token after it is refused unless it is
 *       {@code +}, and a {@code )} there gives the marker up and skips to an OVER —
 *       {@code SELECT MAX(a) FROM t LIMIT n} refuses {@code a} at 12, {@code SELECT COUNT(*) …} the {@code *} and,
 *       read as a product, the {@code )} after it;</li>
 *   <li>an operator met so is read with its operand and the expression goes on; the token where it ends is refused,
 *       the frame's {@code )} too — {@code SELECT a FROM t LIMIT n + 1} refuses that {@code )}, and
 *       {@code … WHERE a > 0 LIMIT n} the LIMIT;</li>
 *   <li>a comma met so opens a list the frame holds, read item by item to its {@code )};</li>
 *   <li>a query in an operand's parentheses is read past when it reads, and when it holds the body's fault it is
 *       refused at its first word and read as the frame is, handing back only at its own {@code )}.</li>
 * </ul>
 * After a marker that is none or a postfix refused it skips to an operator alone; after an operand that is none, to
 * a parenthesis, an OVER or an operator.
 */
final class FramedQueryReading {

    /** A refusal is unwinding: the reading recovers where {@link #failedAt} and {@link #failedIn} say. */
    private static final int REFUSED = -1;

    /** The binary operators the reading takes an operand after. */
    private static final Set<Integer> BINARY = Set.of(FrostlakeLexer.PLUS, FrostlakeLexer.MINUS, FrostlakeLexer.STAR,
        FrostlakeLexer.SLASH, FrostlakeLexer.PERCENT, FrostlakeLexer.PIPE_PIPE, FrostlakeLexer.EQ, FrostlakeLexer.LT,
        FrostlakeLexer.GT, FrostlakeLexer.LTE, FrostlakeLexer.GTE, FrostlakeLexer.NEQ, FrostlakeLexer.AND,
        FrostlakeLexer.OR, FrostlakeLexer.LIKE, FrostlakeLexer.ILIKE, FrostlakeLexer.RLIKE, FrostlakeLexer.REGEXP);

    /** The words a NOT may open after an operand. */
    private static final Set<Integer> NEGATED = Set.of(FrostlakeLexer.IN, FrostlakeLexer.LIKE, FrostlakeLexer.ILIKE,
        FrostlakeLexer.RLIKE, FrostlakeLexer.REGEXP, FrostlakeLexer.BETWEEN);

    /** The words that are no column's or function's name in the reading. */
    private static final Set<Integer> NO_NAMES = Set.of(FrostlakeLexer.SELECT, FrostlakeLexer.FROM,
        FrostlakeLexer.WHERE, FrostlakeLexer.LIMIT, FrostlakeLexer.OFFSET, FrostlakeLexer.FETCH, FrostlakeLexer.FIRST,
        FrostlakeLexer.NEXT, FrostlakeLexer.ROWS, FrostlakeLexer.ROW, FrostlakeLexer.ONLY, FrostlakeLexer.AS,
        FrostlakeLexer.ORDER, FrostlakeLexer.BY, FrostlakeLexer.PARTITION, FrostlakeLexer.OVER, FrostlakeLexer.IN,
        FrostlakeLexer.AND, FrostlakeLexer.OR, FrostlakeLexer.IS, FrostlakeLexer.NOT, FrostlakeLexer.NULL,
        FrostlakeLexer.DISTINCT, FrostlakeLexer.CASE, FrostlakeLexer.WHEN, FrostlakeLexer.THEN, FrostlakeLexer.ELSE,
        FrostlakeLexer.END, FrostlakeLexer.CAST, FrostlakeLexer.WITH, FrostlakeLexer.GROUP, FrostlakeLexer.HAVING,
        FrostlakeLexer.TRUE, FrostlakeLexer.FALSE, FrostlakeLexer.BETWEEN, FrostlakeLexer.LIKE, FrostlakeLexer.ILIKE,
        FrostlakeLexer.RLIKE, FrostlakeLexer.REGEXP, FrostlakeLexer.JOIN, FrostlakeLexer.ON, FrostlakeLexer.QUALIFY,
        FrostlakeLexer.UNION, FrostlakeLexer.EXISTS, FrostlakeLexer.TOP);

    private final List<Token> tokens;
    private final int end;
    private final List<Integer> refused = new ArrayList<>();
    /** No token has been read since the last refusal, which keeps the next one unreported. */
    private boolean quiet;
    /** A comma the reading stopped at opened a list of the frame's; its comma and parenthesis are looked for. */
    private boolean inList;
    private int failedAt;
    private FramedRefusalKind failedIn;
    private FramedReadingMode nextMode;
    private int nextAt;

    private FramedQueryReading(final List<Token> framed) {
        this.tokens = framed;
        this.end = framed.size();
    }

    /**
     * The refusal's lines, the body's first word first.
     *
     * @param framed the framed body's spoken tokens, the frame's parentheses included, its first word second
     * @return the lines
     */
    static String lines(final List<Token> framed) {
        final FramedQueryReading reading = new FramedQueryReading(framed);
        reading.report(1);
        reading.resume(2, FramedReadingMode.FIRST, -1);
        final StringBuilder lines = new StringBuilder();
        for (final Integer at : reading.refused) {
            lines.append(lines.length() == 0 ? "" : "\n").append(reading.describe(at.intValue()));
        }
        return lines.toString();
    }

    private String describe(final int at) {
        if (at < end) {
            final Token token = tokens.get(at);
            return "syntax error line " + token.getLine() + " at position " + token.getCharPositionInLine()
                + " unexpected '" + token.getText() + "'.";
        }
        final Token last = tokens.get(end - 1);
        return "syntax error line " + last.getLine() + " at position "
            + (last.getCharPositionInLine() + last.getText().length()) + " unexpected '<EOF>'.";
    }

    private int kind(final int at) {
        return at < end ? tokens.get(at).getType() : Token.EOF;
    }

    private void report(final int at) {
        if (!quiet) {
            refused.add(at);
        }
        quiet = true;
    }

    private int match(final int at) {
        quiet = false;
        return at + 1;
    }

    private int refuse(final int at, final FramedRefusalKind kind) {
        report(at);
        failedAt = at;
        failedIn = kind;
        return REFUSED;
    }

    private boolean goOn(final FramedReadingMode mode, final int at) {
        nextMode = mode;
        nextAt = at;
        return true;
    }

    /** Whether the token at the index is a column's or a function's name in the reading. */
    private boolean name(final int at) {
        if (at >= end) {
            return false;
        }
        final Token token = tokens.get(at);
        if (token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            return true;
        }
        final String text = String.valueOf(token.getText());
        final boolean word = !text.isEmpty() && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_');
        return word && !NO_NAMES.contains(token.getType()) && !isWindow(at);
    }

    private boolean isWindow(final int at) {
        return at < end && tokens.get(at).getType() == FrostlakeLexer.IDENTIFIER
            && "WINDOW".equals(tokens.get(at).getText().toUpperCase(Locale.ROOT));
    }

    private boolean literal(final int at) {
        final int kind = kind(at);
        return kind == FrostlakeLexer.INTEGER_LITERAL || kind == FrostlakeLexer.FLOAT_LITERAL
            || kind == FrostlakeLexer.STRING_LITERAL || kind == FrostlakeLexer.DOLLAR_QUOTED_STRING
            || kind == FrostlakeLexer.HEX_LITERAL || kind == FrostlakeLexer.NULL || kind == FrostlakeLexer.TRUE
            || kind == FrostlakeLexer.FALSE;
    }

    private boolean afterOperand(final int at) {
        final int kind = kind(at);
        return BINARY.contains(kind) || kind == FrostlakeLexer.IN || kind == FrostlakeLexer.IS
            || kind == FrostlakeLexer.NOT || kind == FrostlakeLexer.BETWEEN;
    }

    private boolean opensQuery(final int at) {
        return kind(at) == FrostlakeLexer.SELECT || kind(at) == FrostlakeLexer.WITH;
    }

    /** The index of the parenthesis closing the group whose content starts at the index, or the end. */
    private int closing(final int from) {
        int depth = 1;
        for (int at = from; at < end; at++) {
            if (kind(at) == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (kind(at) == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return at;
                }
            }
        }
        return end;
    }

    private int close(final int from) {
        final int at = closing(from);
        return at < end ? match(at) : at;
    }

    /** Whether the query from its first word to its closing parenthesis holds the fault the body is refused for. */
    private boolean faulty(final int from, final int to) {
        for (int at = from; at < to; at++) {
            if (RowCountFaults.countsRows(tokens, at)
                    && RowCountFaults.noCount(tokens, at + (kind(at) == FrostlakeLexer.FETCH ? 2 : 1))
                    || isWindow(at)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A query in an operand's parentheses: read past when it reads; refused at its first word when it holds the
     * body's fault, and then read as the frame is, handing back to the operand only at its own parenthesis.
     */
    private int subquery(final int first) {
        final int closes = closing(first);
        if (!faulty(first, closes)) {
            return close(first);
        }
        report(first);
        final int back = resume(first + 1, FramedReadingMode.FIRST, closes);
        if (back == REFUSED) {
            failedIn = FramedRefusalKind.OVER;
        }
        return back;
    }

    private int operand(final int from) {
        final int kind = kind(from);
        if (kind == FrostlakeLexer.MINUS || kind == FrostlakeLexer.PLUS || kind == FrostlakeLexer.NOT) {
            return operand(match(from));
        }
        if (kind == FrostlakeLexer.LPAREN) {
            int at = match(from);
            if (opensQuery(at)) {
                return subquery(at);
            }
            at = expression(at);
            while (at != REFUSED && kind(at) == FrostlakeLexer.COMMA) {
                at = expression(match(at));
            }
            if (at == REFUSED) {
                return REFUSED;
            }
            return kind(at) == FrostlakeLexer.RPAREN ? match(at) : refuse(at, FramedRefusalKind.OPERAND);
        }
        if (name(from) || kind == FrostlakeLexer.CAST || kind == FrostlakeLexer.EXISTS) {
            return call(from);
        }
        return literal(from) ? match(from) : refuse(from, FramedRefusalKind.OPERAND);
    }

    /** A column's name, its outer-join marker, or a function's call. */
    private int call(final int from) {
        int at = match(from);
        while (kind(at) == FrostlakeLexer.DOT && name(at + 1)) {
            at = match(match(at));
        }
        if (kind(at) == FrostlakeLexer.LPAREN && kind(at + 1) == FrostlakeLexer.PLUS
                && kind(at + 2) == FrostlakeLexer.RPAREN) {
            return match(match(match(at)));
        }
        if (kind(at) != FrostlakeLexer.LPAREN) {
            return at;
        }
        at = match(at);
        if (opensQuery(at)) {
            return subquery(at);
        }
        if (kind(at) == FrostlakeLexer.STAR) {
            at = match(at);
        } else if (kind(at) != FrostlakeLexer.RPAREN) {
            if (kind(at) == FrostlakeLexer.DISTINCT) {
                at = match(at);
            }
            at = expression(at);
            if (at != REFUSED && kind(from) == FrostlakeLexer.CAST && kind(at) == FrostlakeLexer.AS) {
                at = typed(match(at));
            }
            while (at != REFUSED && kind(at) == FrostlakeLexer.COMMA) {
                at = expression(match(at));
            }
            if (at == REFUSED) {
                return REFUSED;
            }
        }
        return kind(at) == FrostlakeLexer.RPAREN ? match(at) : refuse(at, FramedRefusalKind.OPERAND);
    }

    /** A type's name and its parenthesized arguments. */
    private int typed(final int from) {
        final int at = match(from);
        return kind(at) == FrostlakeLexer.LPAREN ? close(match(at)) : at;
    }

    private int postfix(final int from) {
        int at = from;
        while (true) {
            final int kind = kind(at);
            if (kind == FrostlakeLexer.DOUBLE_COLON) {
                at = typed(match(at));
            } else if (kind == FrostlakeLexer.OVER) {
                at = match(at);
                if (kind(at) != FrostlakeLexer.LPAREN) {
                    return refuse(at, FramedRefusalKind.POSTFIX);
                }
                at = close(match(at));
            } else if (kind == FrostlakeLexer.IS) {
                at = match(at);
                if (kind(at) == FrostlakeLexer.NOT) {
                    at = match(at);
                }
                at = match(at);
            } else if (kind == FrostlakeLexer.NOT && NEGATED.contains(kind(at + 1))) {
                at = match(at);
            } else if (kind == FrostlakeLexer.NOT) {
                return refuse(at + 1, FramedRefusalKind.END);
            } else if (kind == FrostlakeLexer.BETWEEN) {
                at = operand(match(at));
                at = at == REFUSED ? REFUSED : postfix(at);
                if (at == REFUSED) {
                    return REFUSED;
                }
                if (kind(at) != FrostlakeLexer.AND) {
                    return refuse(at, FramedRefusalKind.POSTFIX);
                }
                at = operand(match(at));
            } else if (kind == FrostlakeLexer.IN) {
                at = inList(at);
            } else {
                return at;
            }
            if (at == REFUSED) {
                return REFUSED;
            }
        }
    }

    private int inList(final int in) {
        int at = match(in);
        if (kind(at) != FrostlakeLexer.LPAREN) {
            return refuse(at, FramedRefusalKind.POSTFIX);
        }
        at = match(at);
        if (opensQuery(at)) {
            return subquery(at);
        }
        at = expression(at);
        while (at != REFUSED && kind(at) == FrostlakeLexer.COMMA) {
            at = expression(match(at));
        }
        if (at == REFUSED) {
            return REFUSED;
        }
        return kind(at) == FrostlakeLexer.RPAREN ? match(at) : refuse(at, FramedRefusalKind.POSTFIX);
    }

    private int expression(final int from) {
        int at = operand(from);
        at = at == REFUSED ? REFUSED : postfix(at);
        while (at != REFUSED && BINARY.contains(kind(at))) {
            at = operand(match(at));
            at = at == REFUSED ? REFUSED : postfix(at);
        }
        return at;
    }

    /** Where the reading goes on after a refusal unwound to the top of the expression. */
    private boolean recover() {
        if (failedIn == FramedRefusalKind.END || failedIn == FramedRefusalKind.OVER) {
            return false;
        }
        return goOn(failedIn == FramedRefusalKind.OPERAND ? FramedReadingMode.OPERAND : FramedReadingMode.MISMATCH,
            failedAt);
    }

    /** After an operand at the top: the expression goes on, or the token there is refused and the reading ends. */
    private boolean after(final int from) {
        int at = postfix(from);
        while (at != REFUSED && BINARY.contains(kind(at))) {
            at = operand(match(at));
            at = at == REFUSED ? REFUSED : postfix(at);
        }
        if (at == REFUSED) {
            return recover();
        }
        if (at < end) {
            report(at);
        }
        return false;
    }

    private int skip(final int from, final FramedReadingMode mode, final boolean toClosing) {
        int at = from;
        while (at < end && !stops(at, mode, toClosing)) {
            at++;
        }
        return at;
    }

    private boolean stops(final int at, final FramedReadingMode mode, final boolean toClosing) {
        final int kind = kind(at);
        if (afterOperand(at)) {
            return true;
        }
        if ((inList || toClosing) && kind == FrostlakeLexer.RPAREN || inList && kind == FrostlakeLexer.COMMA) {
            return true;
        }
        switch (mode) {
            case FIRST:
                return kind == FrostlakeLexer.LPAREN || kind == FrostlakeLexer.COMMA
                    || kind == FrostlakeLexer.DOUBLE_COLON;
            case ITEM:
                return kind == FrostlakeLexer.LPAREN;
            case OPERAND:
                return kind == FrostlakeLexer.LPAREN || kind == FrostlakeLexer.OVER;
            default:
                return false;
        }
    }

    /** The next item of the list the frame holds, at a comma. */
    private boolean listItem(final int comma) {
        inList = true;
        int at = comma;
        while (true) {
            at = expression(match(at));
            if (at == REFUSED) {
                if (failedIn == FramedRefusalKind.OVER) {
                    return false;
                }
                return failedIn == FramedRefusalKind.OPERAND ? goOn(FramedReadingMode.OPERAND, failedAt)
                    : goOn(FramedReadingMode.MISMATCH, failedAt + 1);
            }
            if (kind(at) != FrostlakeLexer.COMMA) {
                break;
            }
        }
        // The list's own refusal gives it up: its parenthesis is looked for no longer.
        inList = false;
        if (kind(at) == FrostlakeLexer.RPAREN) {
            return after(match(at));
        }
        report(at);
        return goOn(FramedReadingMode.ITEM, at + 1);
    }

    /** A parenthesis read as an outer-join marker. */
    private boolean marker(final int open) {
        if (kind(open + 1) == FrostlakeLexer.PLUS && kind(open + 2) == FrostlakeLexer.RPAREN) {
            return after(match(match(match(open))));
        }
        if (kind(open + 1) == FrostlakeLexer.RPAREN) {
            int at = open + 2;
            while (at < end && kind(at) != FrostlakeLexer.OVER) {
                at++;
            }
            return at < end && after(at);
        }
        final int at = match(open);
        report(at);
        if (kind(at + 1) == FrostlakeLexer.PLUS) {
            if (kind(at + 2) == FrostlakeLexer.RPAREN) {
                return after(at + 3);
            }
            if (kind(at + 3) == FrostlakeLexer.RPAREN) {
                return after(at + 4);
            }
            return goOn(FramedReadingMode.MISMATCH, at + 2);
        }
        return goOn(FramedReadingMode.MISMATCH, at);
    }

    /** An operator, or a word that may follow an operand, where a skip stopped. */
    private boolean operator(final int at) {
        if (!BINARY.contains(kind(at))) {
            return after(at);
        }
        final int read = operand(match(at));
        if (read == REFUSED) {
            return failedIn != FramedRefusalKind.OVER && goOn(FramedReadingMode.OPERAND, failedAt);
        }
        return after(read);
    }

    /**
     * Reads on from the index.
     *
     * @param from    where the skip starts
     * @param first   how it skips first
     * @param closes  the parenthesis closing the query read, or -1 for the frame
     * @return the index after that parenthesis when the first skip reaches it; otherwise {@link #REFUSED}, the
     *     reading being over
     */
    private int resume(final int from, final FramedReadingMode first, final int closes) {
        int at = from;
        FramedReadingMode mode = first;
        int closing = closes;
        while (true) {
            if (mode == FramedReadingMode.OPERAND) {
                at++;
            }
            at = skip(at, mode, closing >= 0);
            if (closing >= 0 && at <= closing && kind(at) == FrostlakeLexer.RPAREN) {
                if (at == closing) {
                    return match(at);
                }
                at++;
                continue;
            }
            closing = -1;
            if (at >= end) {
                return REFUSED;
            }
            final int kind = kind(at);
            final boolean goesOn;
            if (kind == FrostlakeLexer.RPAREN && inList) {
                inList = false;
                goesOn = after(match(at));
            } else if (kind == FrostlakeLexer.LPAREN) {
                goesOn = marker(at);
            } else if (kind == FrostlakeLexer.COMMA) {
                goesOn = listItem(at);
            } else if (kind == FrostlakeLexer.DOUBLE_COLON) {
                goesOn = after(typed(match(at)));
            } else {
                goesOn = operator(at);
            }
            if (!goesOn) {
                return REFUSED;
            }
            mode = nextMode;
            at = nextAt;
        }
    }
}
