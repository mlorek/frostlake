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
 * The lines after a first fault that stands straight after a select item's alias inside a query in parentheses that
 * a FROM clause or a WITH clause holds — {@code SELECT a FROM (SELECT 1 x 'y') WHERE a b}. The account names the
 * token and then, by what the token is and what follows it:
 *
 * <pre>
 *   (SELECT 1 x 'y') WHERE a b             'y', then 'b': the query closes and the statement reads on
 *   (SELECT 1 x 'y' 'z') WHERE a b         the same: only literals stand between the token and the ')'
 *   (SELECT 1 x 'y' FROM fz) WHERE a b     'y', then the query's ')': a literal, a '*', a '?', an operator or a
 *                                          value in parentheses, with more of the query after it
 *   (SELECT 1 x (id) FROM fz) WHERE a b    '(', then the ')' after the id: a name alone in the parentheses
 *   (SELECT 1 x AS y FROM fz) WHERE a b    'AS', then 'b': an AS, a path, a cast or a list in parentheses is
 *                                          passed over up to the FROM of a query that reads one table
 *   (SELECT 1 x AS FROM fz WHERE …) …      'AS', then the query's ')': the query reads more than one table
 *   (SELECT 1 x 'y', 2 FROM fz) WHERE a b  'y', then the 2 and 'b': what the comma begins is read as the FROM
 *                                          clause's next table
 *   WITH c AS (SELECT 1 x 'y' FROM fz) …   'y', and the statement reads on as if it were not there
 *   FROM fz JOIN (SELECT 1 x 'y') d ON …   'y', then the ON
 * </pre>
 *
 * <p>A word after the alias (see {@code SelectListResync}), any other query joined to a table, an empty pair of parentheses before the query's end and a list of values in
 * parentheses are left to the other readings (all live-verified).
 */
final class SubqueryAliasFaultLines {

    private SubqueryAliasFaultLines() {
    }

    /**
     * The lines for a first fault after a select item's alias in a FROM clause's or a WITH clause's query.
     *
     * @param sql            the text
     * @param spoken         the text's tokens on the default channel
     * @param faultIndex     the token index of the fault
     * @param statementParse whether the text is read as statements
     * @return the lines, or null where this reading does not apply
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse) {
        final int fault = statementParse && sql != null ? CastFaultPlace.positionOf(spoken, faultIndex) : -1;
        final int open = fault < 3 ? -1 : CastFaultPlace.enclosingParen(spoken, fault);
        if (open < 1 || spoken.get(open + 1).getType() != FrostlakeLexer.SELECT || !followsAnAlias(spoken, open, fault)
                || inBlock(spoken, open)) {
            return null;
        }
        final int close = CastFaultPlace.closeOf(spoken, open);
        final boolean common = spoken.get(open - 1).getType() == FrostlakeLexer.AS;
        if (close < 0) {
            return null;
        }
        if (!common && !opensADerivedTable(spoken, open)) {
            return joinedLines(spoken, open, fault, close);
        }
        final boolean group = spoken.get(fault).getType() == FrostlakeLexer.LPAREN;
        final int end = group ? CastFaultPlace.closeOf(spoken, fault) : fault;
        if (end < 0 || end >= close || group && (common || holds(spoken, fault, end, FrostlakeLexer.COMMA))) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        final int next = end + 1;
        final boolean comma = spoken.get(next).getType() == FrostlakeLexer.COMMA && next + 1 < close;
        if (common) {
            if (!comma) {
                return readOn(lines, sql, spoken, fault, fault);
            }
            if (!isNumber(spoken.get(next + 1))) {
                return null;
            }
            // The number after the comma is refused too, and the rest of the query passed over.
            lines.add(SyntaxErrorListener.sentence(spoken.get(next + 1)));
            return readOn(lines, sql, spoken, fault, close - 1);
        }
        if (comma && !readsAsAName(spoken.get(fault))) {
            // The query is given up at the token, and what the comma begins is read as the FROM clause's next table.
            final char[] text = CastFaultPlace.blanked(sql.toCharArray(), spoken, open, end);
            text[spoken.get(open).getStartIndex()] = 't';
            return readOn(lines, new String(text));
        }
        if (readsAsAName(spoken.get(fault)) || group && spoken.get(open - 1).getType() == FrostlakeLexer.LPAREN) {
            // A word after the alias has a reading of its own, and so have parentheses in a query that stands in
            // parentheses twice.
            return null;
        }
        final boolean empty = group && end == fault + 1;
        final boolean oneName = group && end == fault + 2 && SyntaxErrorListener.isNameLike(spoken.get(fault + 1));
        if (next == close) {
            if (empty) {
                return null;
            }
            if (oneName) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(end)));
                return lines;
            }
            return readOn(lines, sql, spoken, fault, end);
        }
        if (onlyLiterals(spoken, next, close)) {
            return readOn(lines, sql, spoken, fault, close - 1);
        }
        if (readsAsAValue(spoken, fault, end, group)) {
            // The query is given up at the token, so the ')' of the outermost table around it closes nothing.
            int outermost = open;
            for (int around = CastFaultPlace.enclosingParen(spoken, open); around >= 0;
                    around = CastFaultPlace.enclosingParen(spoken, around)) {
                outermost = around;
            }
            final int last = CastFaultPlace.closeOf(spoken, outermost);
            lines.add(SyntaxErrorListener.sentence(spoken.get(oneName ? end : last < 0 ? close : last)));
            return lines;
        }
        final int from = fromAfter(spoken, next, close);
        if (from < 0) {
            return null;
        }
        if (namesOneTable(spoken, from + 1, close)) {
            return readOn(lines, sql, spoken, fault, from - 1);
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
        return lines;
    }

    /**
     * The lines for a literal that ends the query of a join's table: the table's alias is read, and the ON after it
     * refused — {@code FROM fz JOIN (SELECT 1 x 'y') d ON 1 = 1} is the 'y', then the ON.
     */
    private static List<String> joinedLines(final List<Token> spoken, final int open, final int fault,
                                            final int close) {
        if (spoken.get(open - 1).getType() != FrostlakeLexer.JOIN || fault + 1 != close
                || !isLiteral(spoken.get(fault)) || close + 2 >= spoken.size()
                || !SyntaxErrorListener.isNameLike(spoken.get(close + 1))
                || spoken.get(close + 2).getType() != FrostlakeLexer.ON) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(close + 2)));
        return lines;
    }

    /** The lines so far and those of the text read again with the tokens from one position through another blanked. */
    private static List<String> readOn(final List<String> lines, final String sql, final List<Token> spoken,
                                       final int from, final int through) {
        return readOn(lines, new String(CastFaultPlace.blanked(sql.toCharArray(), spoken, from, through)));
    }

    /** The lines so far and those a rebuilt text reports after the first of them. */
    private static List<String> readOn(final List<String> lines, final String text) {
        final int[] fault = SyntaxErrorListener.sentenceCoordinates(lines.get(0));
        for (final String line : SyntaxErrorListener.linesReportedFor(text, true)) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
            if (at != null && fault != null && (at[0] > fault[0] || at[0] == fault[0] && at[1] > fault[1])) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Whether the token at {@code fault} follows the alias of an item of the select list that the '(' at
     * {@code open} begins: a name after a value or an AS, with no clause of the query before it.
     */
    private static boolean followsAnAlias(final List<Token> spoken, final int open, final int fault) {
        if (fault - open < 4 || !SyntaxErrorListener.isNameLike(spoken.get(fault - 1))) {
            return false;
        }
        final Token valueEnd = spoken.get(fault - 2);
        if (valueEnd.getType() != FrostlakeLexer.AS && valueEnd.getType() != FrostlakeLexer.RPAREN
                && !isLiteral(valueEnd) && !SyntaxErrorListener.isNameLike(valueEnd)) {
            return false;
        }
        int depth = 0;
        for (int i = open + 2; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
                    || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT
                    || type == FrostlakeLexer.UNION || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY)) {
                return false;
            }
        }
        return depth == 0;
    }

    /** Whether the '(' opens a table of a FROM clause's list, alone or in parentheses of its own, and no join's. */
    private static boolean opensADerivedTable(final List<Token> spoken, final int open) {
        int at = open - 1;
        while (at > 0 && spoken.get(at).getType() == FrostlakeLexer.LPAREN) {
            at--;
        }
        final int before = spoken.get(at).getType();
        if (before == FrostlakeLexer.FROM) {
            return true;
        }
        final int clause = before == FrostlakeLexer.COMMA ? CastFaultPlace.clauseOf(spoken, at) : -1;
        return clause >= 0 && spoken.get(clause).getType() == FrostlakeLexer.FROM
            && CastFaultPlace.nesting(spoken, clause, at) == 0;
    }

    /** Whether the statement holding a position is a block's or a declaration's. */
    private static boolean inBlock(final List<Token> spoken, final int at) {
        int start = at;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        final int first = spoken.get(start).getType();
        final int script = spoken.get(0).getType();
        return first == FrostlakeLexer.BEGIN || first == FrostlakeLexer.DECLARE || script == FrostlakeLexer.BEGIN
            || script == FrostlakeLexer.DECLARE;
    }

    /**
     * Whether the fault reads as a value run into the alias — a literal, a '*', a '?', an operator, or parentheses
     * holding a value — which the account does not pass over.
     */
    private static boolean readsAsAValue(final List<Token> spoken, final int fault, final int end,
                                         final boolean group) {
        if (group) {
            return end > fault + 1 && !holds(spoken, fault, end, FrostlakeLexer.AS);
        }
        final int type = spoken.get(fault).getType();
        return isLiteral(spoken.get(fault)) || type == FrostlakeLexer.STAR || type == FrostlakeLexer.QUESTION
            || type == FrostlakeLexer.EQ || type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE;
    }

    /** Whether a token is a word other than CASE, TRUE, FALSE or a positional column: read as a name there. */
    private static boolean readsAsAName(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.TRUE || type == FrostlakeLexer.FALSE
            || type == FrostlakeLexer.POSITIONAL_PARAMETER
            || type != FrostlakeLexer.CASE && SyntaxErrorListener.isNameLike(token);
    }

    /** Whether the tokens from one position up to another are one name, qualified or not, with or without an alias. */
    private static boolean namesOneTable(final List<Token> spoken, final int from, final int until) {
        int at = from;
        if (at >= until || !SyntaxErrorListener.isNameLike(spoken.get(at))) {
            return false;
        }
        at++;
        while (at + 1 < until && spoken.get(at).getType() == FrostlakeLexer.DOT
                && SyntaxErrorListener.isNameLike(spoken.get(at + 1))) {
            at += 2;
        }
        if (at < until && spoken.get(at).getType() == FrostlakeLexer.AS) {
            at++;
        }
        return at == until || at + 1 == until && SyntaxErrorListener.isNameLike(spoken.get(at));
    }

    private static boolean isNumber(final Token token) {
        return token.getType() == FrostlakeLexer.INTEGER_LITERAL || token.getType() == FrostlakeLexer.FLOAT_LITERAL;
    }

    private static boolean isLiteral(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.INTEGER_LITERAL
            || type == FrostlakeLexer.FLOAT_LITERAL || type == FrostlakeLexer.NULL;
    }

    /** Whether every token from one position up to another is a literal. */
    private static boolean onlyLiterals(final List<Token> spoken, final int from, final int until) {
        for (int i = from; i < until; i++) {
            if (!isLiteral(spoken.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Whether a token of a type stands directly inside the parentheses from {@code open} to {@code close}. */
    private static boolean holds(final List<Token> spoken, final int open, final int close, final int type) {
        int depth = 0;
        for (int i = open + 1; i < close; i++) {
            final int each = spoken.get(i).getType();
            if (each == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (each == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && each == type) {
                return true;
            }
        }
        return false;
    }

    /** The query's FROM from a position on, when no comma of the select list stands before it; -1 otherwise. */
    private static int fromAfter(final List<Token> spoken, final int from, final int close) {
        int depth = 0;
        for (int i = from; i < close; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.COMMA) {
                return -1;
            } else if (depth == 0 && type == FrostlakeLexer.FROM) {
                return i;
            }
        }
        return -1;
    }
}
