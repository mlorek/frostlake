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
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines the account reports for a SQL function body whose query reads a table function written bare in its
 * FROM clause, {@code FROM FLATTEN(INPUT => …)}, where only {@code TABLE(…)} or {@code LATERAL} takes a call. The
 * account cannot read that query, so it refuses its SELECT, in the frame of parentheses it reads the body in, and
 * then reads on through the query's first select item and the call. What it refuses there depends on how many
 * parentheses stand open before the SELECT, the frame's included (one for a table function's query, two for a
 * scalar function's {@code (SELECT …)}), and on the first item (live-verified):
 * <ul>
 *   <li>an operand — a name, a dotted name or a literal, with or without its alias — refuses the call's first
 *       argument: with one parenthesis open it alone, then the frame's closing parenthesis when a WHERE follows
 *       the call; with two, the call's own closing parenthesis too; with more, the query's;</li>
 *   <li>a star, or an operand the list goes on after, refuses the FROM, then as an operand does;</li>
 *   <li>any other expression refuses the FROM alone;</li>
 *   <li>a call of its own refuses its first argument, and its closing parenthesis when that argument is a star;
 *       with one parenthesis open that is all, but for a star the bare call's first argument follows, and a WHERE
 *       after the bare call refuses the frame's closing parenthesis; with two or more, the first FROM, AS or comma
 *       after the item's call follows, and with three or more the bare call's first argument after it;</li>
 *   <li>a call of no arguments refuses nothing more with one parenthesis open, and the bare call's first argument
 *       with more;</li>
 *   <li>a bare call written after a comma in the FROM list is not read on to: only an item's own refusals stand.</li>
 * </ul>
 * <pre>
 *   AS 'SELECT value FROM FLATTEN(INPUT =&gt; PARSE_JSON(''[1]''))'          'SELECT' 1, 'INPUT' 27
 *   AS 'SELECT * FROM FLATTEN(INPUT =&gt; PARSE_JSON(''[1]'')) WHERE 1 = 1'  'SELECT' 1, 'FROM' 10, 'INPUT' 23, ')' 62
 *   AS 'SELECT COUNT(*) FROM FLATTEN(INPUT =&gt; PARSE_JSON(''[1]''))'       'SELECT' 1, '*' 14, ')' 15, 'INPUT' 30
 *   AS '(SELECT COUNT(*) FROM FLATTEN(INPUT =&gt; PARSE_JSON(''[1]'')))'     'SELECT' 2, '*' 15, ')' 16, 'FROM' 18
 *   AS '(SELECT MAX(a) FROM FLATTEN(INPUT =&gt; PARSE_JSON(''[1]'')))'       'SELECT' 2, 'a' 13, 'FROM' 16
 *   AS '(SELECT a FROM FLATTEN(INPUT =&gt; PARSE_JSON(''[1]'')))'            'SELECT' 2, 'INPUT' 24, ')' 50
 *   AS '((SELECT a FROM FLATTEN(INPUT =&gt; PARSE_JSON(''[1]''))))'          'SELECT' 3, 'INPUT' 25, ')' 52
 * </pre>
 * A query whose bare call stands in a query nested in its clauses is read otherwise, and is left to the caller.
 */
final class SqlUdfBareCallLines {

    private SqlUdfBareCallLines() {
    }

    /**
     * The lines for a body whose query reads a bare table function in its FROM clause, or null when its first query
     * reads none there.
     *
     * @param framed the body's spoken tokens inside its frame of parentheses, end of input excluded
     * @return the lines, one a refusal, joined by line ends, or null
     */
    static String lines(final List<Token> framed) {
        int select = -1;
        for (int i = 0; i < framed.size() && select < 0; i++) {
            select = framed.get(i).getType() == FrostlakeLexer.SELECT ? i : -1;
        }
        if (select < 0) {
            return null;
        }
        final int open = openBefore(framed, select);
        final int queryClose = closerAfter(framed, select);
        final int from = queryClose < 0 ? -1 : next(framed, select + 1, queryClose, FrostlakeLexer.FROM);
        final int call = from < 0 ? -1 : bareCall(framed, from, queryClose);
        if (open < 1 || call < 0) {
            return null;
        }
        final boolean reached = framed.get(call - 1).getType() != FrostlakeLexer.COMMA;
        final Token argument = framed.get(call + 2);
        final int callClose = matching(framed, call + 1);
        final boolean whereAfter = next(framed, callClose + 1, queryClose, FrostlakeLexer.WHERE) >= 0;
        final int comma = next(framed, select + 1, from, FrostlakeLexer.COMMA);
        final int itemEnd = comma >= 0 ? comma : from;
        final List<Token> refused = new ArrayList<>();
        refused.add(framed.get(select));
        if (isName(framed.get(select + 1)) && framed.get(select + 2).getType() == FrostlakeLexer.LPAREN) {
            itemCallLines(framed, select, from, open, reached, argument, whereAfter, queryClose, refused);
        } else if (framed.get(select + 1).getType() == FrostlakeLexer.STAR && itemEnd == select + 2 || comma >= 0) {
            refused.add(framed.get(from));
            if (reached) {
                operandLines(framed, open, argument, callClose, whereAfter, queryClose, refused);
            }
        } else if (!isOperand(framed, select + 1, itemEnd)) {
            refused.add(framed.get(from));
        } else if (reached) {
            operandLines(framed, open, argument, callClose, whereAfter, queryClose, refused);
        }
        final StringBuilder lines = new StringBuilder();
        for (final Token token : refused) {
            lines.append(lines.length() == 0 ? "" : "\n").append("syntax error line ").append(token.getLine())
                .append(" at position ").append(token.getCharPositionInLine()).append(" unexpected '")
                .append(token.getText()).append("'.");
        }
        return lines.toString();
    }

    /** What an operand item is read on to: the bare call's first argument, then the parenthesis its depth names. */
    private static void operandLines(final List<Token> framed, final int open, final Token argument,
                                     final int callClose, final boolean whereAfter, final int queryClose,
                                     final List<Token> refused) {
        refused.add(argument);
        if (open == 2) {
            refused.add(framed.get(callClose));
        } else if (open > 2 || whereAfter) {
            refused.add(framed.get(queryClose));
        }
    }

    /** What an item that is a call of its own is read on to (see the class comment). */
    private static void itemCallLines(final List<Token> framed, final int select, final int from, final int open,
                                      final boolean reached, final Token argument, final boolean whereAfter,
                                      final int queryClose, final List<Token> refused) {
        final Token inside = framed.get(select + 3);
        if (inside.getType() == FrostlakeLexer.RPAREN) {
            if (open > 1 && reached) {
                refused.add(argument);
            }
            return;
        }
        final int itemClose = matching(framed, select + 2);
        final boolean star = inside.getType() == FrostlakeLexer.STAR;
        refused.add(inside);
        if (star) {
            refused.add(framed.get(itemClose));
        }
        if (open == 1) {
            if (star && reached) {
                refused.add(argument);
            } else if (!star && whereAfter) {
                refused.add(framed.get(queryClose));
            }
            return;
        }
        final int stop = itemStop(framed, itemClose + 1, from);
        if (stop >= 0) {
            refused.add(framed.get(stop));
        }
        if (open > 2 && reached) {
            refused.add(argument);
        }
    }

    /** The first FROM, AS, comma or name after an item's call, at the query's own level, or -1. */
    private static int itemStop(final List<Token> framed, final int start, final int from) {
        int depth = 0;
        for (int i = start; i <= from; i++) {
            final int type = framed.get(i).getType();
            if (depth == 0 && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.AS
                    || type == FrostlakeLexer.COMMA || type == FrostlakeLexer.IDENTIFIER
                    || type == FrostlakeLexer.QUOTED_IDENTIFIER)) {
                return i;
            }
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
        }
        return -1;
    }

    /**
     * Whether an item is a single operand: one token, or names joined by dots, with or without an alias after it.
     */
    private static boolean isOperand(final List<Token> framed, final int start, final int end) {
        int last = end;
        if (last - start >= 3 && framed.get(last - 2).getType() == FrostlakeLexer.AS) {
            last -= 2;
        } else if (last - start >= 2 && isName(framed.get(last - 1))
                && framed.get(last - 2).getType() != FrostlakeLexer.DOT) {
            last -= 1;
        }
        if (last - start == 1) {
            return true;
        }
        for (int i = start; i < last; i++) {
            final boolean dot = (i - start) % 2 == 1;
            if (dot ? framed.get(i).getType() != FrostlakeLexer.DOT : !isName(framed.get(i))) {
                return false;
            }
        }
        return (last - start) % 2 == 1;
    }

    /**
     * The index of the name of the first call written bare in the FROM list after {@code from}, at the query's own
     * level, or -1: a name after the FROM, a JOIN or a comma, followed by its parenthesis.
     */
    private static int bareCall(final List<Token> framed, final int from, final int queryClose) {
        int depth = 0;
        for (int i = from + 1; i + 2 < queryClose; i++) {
            final int type = framed.get(i).getType();
            final int before = framed.get(i - 1).getType();
            // FLATTEN is a word of this grammar's own; the account's names it as any function.
            final boolean called = isName(framed.get(i)) || type == FrostlakeLexer.FLATTEN;
            if (depth == 0 && called && framed.get(i + 1).getType() == FrostlakeLexer.LPAREN
                    && (before == FrostlakeLexer.FROM || before == FrostlakeLexer.JOIN
                        || before == FrostlakeLexer.COMMA)
                    && type != FrostlakeLexer.TABLE && type != FrostlakeLexer.VALUES
                    && type != FrostlakeLexer.LATERAL && type != FrostlakeLexer.KW_IDENTIFIER) {
                return i;
            }
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
        }
        return -1;
    }

    /** The index of the first token of the type at the level of {@code start}, before {@code end}, or -1. */
    private static int next(final List<Token> framed, final int start, final int end, final int type) {
        int depth = 0;
        for (int i = start; i < end; i++) {
            final int at = framed.get(i).getType();
            if (depth == 0 && at == type) {
                return i;
            }
            depth += at == FrostlakeLexer.LPAREN ? 1 : at == FrostlakeLexer.RPAREN ? -1 : 0;
            if (depth < 0) {
                return -1;
            }
        }
        return -1;
    }

    /** The number of parentheses standing open before the token at {@code index}. */
    private static int openBefore(final List<Token> framed, final int index) {
        int open = 0;
        for (int i = 0; i < index; i++) {
            final int type = framed.get(i).getType();
            open += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
        }
        return open;
    }

    /** The index of the parenthesis closing the one that stands open around the token at {@code index}, or -1. */
    private static int closerAfter(final List<Token> framed, final int index) {
        int depth = 0;
        for (int i = index; i < framed.size(); i++) {
            final int type = framed.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && depth-- == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the parenthesis closing the one at {@code open}, or the last token when none does. */
    private static int matching(final List<Token> framed, final int open) {
        int depth = 0;
        for (int i = open; i < framed.size(); i++) {
            final int type = framed.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            if (depth == 0) {
                return i;
            }
        }
        return framed.size() - 1;
    }

    private static boolean isName(final Token token) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(token.getType());
    }
}
