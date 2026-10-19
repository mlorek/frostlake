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
 *   (SELECT 1 x (1, 2) FROM fz) WHERE a b  '(', then the 2 and 'b': a list in parentheses is read as the comma is,
 *                                          from its first comma
 *   (SELECT 1 x ()) WHERE a b              '(' alone: an empty pair before the query's end
 *   (SELECT 1 x = 1) WHERE a b             '=', then 'b' — the alias is the item's, and the operator the fault; so
 *                                          IN and its list
 *   WITH c AS (SELECT 1 x 'y' FROM fz) …   'y', and the statement reads on as if it were not there
 *   WHERE EXISTS (SELECT 1 x 'y' FROM fz) AND a b    'y', then 'b': an EXISTS in a WHERE reads on the same way,
 *                                          and one in the select list names the query's ')'
 *   FROM fz JOIN (SELECT 1 x 'y') d ON …   'y', then the ON — and so after a word; with no ON the statement reads on
 * </pre>
 *
 * <p>A word after the alias (see {@code SelectListResync}) and any other query joined to a table are left to the
 * other readings (all live-verified).
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
        int fault = statementParse && sql != null ? CastFaultPlace.positionOf(spoken, faultIndex) : -1;
        if (fault >= 2 && fault + 1 < spoken.size() && comparesAnAlias(spoken, fault)) {
            // The word is the item's alias, and the operator after it the fault.
            fault++;
        }
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
        final boolean group = spoken.get(fault).getType() == FrostlakeLexer.LPAREN;
        if (spoken.get(open - 1).getType() == FrostlakeLexer.EXISTS) {
            return existsLines(sql, spoken, open, fault, close, group);
        }
        if (!common && !opensADerivedTable(spoken, open)) {
            return joinedLines(sql, spoken, open, fault, close);
        }
        final int grouped = group ? CastFaultPlace.closeOf(spoken, fault) : fault;
        // An operator takes the literal after it with it; an IN its list.
        final int end = !group && isOperator(spoken.get(fault)) && isLiteral(spoken.get(fault + 1)) ? fault + 1
            : spoken.get(fault).getType() == FrostlakeLexer.IN && spoken.get(fault + 1).getType() == FrostlakeLexer.LPAREN
                ? CastFaultPlace.closeOf(spoken, fault + 1) : grouped;
        if (end < 0 || end >= close) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        if (group && common) {
            return null;
        }
        final int listComma = group ? firstComma(spoken, fault, end) : end;
        if (listComma < end) {
            // A list in parentheses: the query is given up at the '(', and what the list's first comma begins is
            // read as the FROM clause's next table; the rest of the list is passed over.
            final char[] text = CastFaultPlace.blanked(sql.toCharArray(), spoken, open, listComma - 1);
            text[spoken.get(open).getStartIndex()] = 't';
            if (listComma + 2 <= end - 1) {
                CastFaultPlace.blanked(text, spoken, listComma + 2, end - 1);
            }
            return readOn(lines, new String(text));
        }
        final int next = end + 1;
        final boolean comma = spoken.get(next).getType() == FrostlakeLexer.COMMA && next + 1 < close;
        if (common) {
            if (!comma) {
                return readOn(lines, sql, spoken, fault, end);
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
                // An empty pair before the query's end is the one line.
                return lines;
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
    private static List<String> joinedLines(final String sql, final List<Token> spoken, final int open,
                                            final int fault, final int close) {
        if (spoken.get(open - 1).getType() != FrostlakeLexer.JOIN || fault + 1 != close
                || !isLiteral(spoken.get(fault)) && !readsAsAName(spoken.get(fault)) || close + 2 >= spoken.size()
                || !SyntaxErrorListener.isNameLike(spoken.get(close + 1))) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        if (spoken.get(close + 2).getType() == FrostlakeLexer.ON) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(close + 2)));
            return lines;
        }
        // A join that takes no ON reads on after a literal as if it were not there.
        return isLiteral(spoken.get(fault)) ? readOn(lines, sql, spoken, fault, fault) : null;
    }

    /**
     * The lines for a fault after the alias in an EXISTS's query: in a statement's own WHERE the token — a pair of
     * parentheses with it — is passed over and the statement read on; in its select list the query's ')' is
     * refused.
     */
    private static List<String> existsLines(final String sql, final List<Token> spoken, final int open,
                                            final int fault, final int close, final boolean group) {
        final int end = group ? CastFaultPlace.closeOf(spoken, fault) : fault;
        final int clause = CastFaultPlace.clauseOf(spoken, open);
        if (end < 0 || end >= close || clause < 0) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        if (spoken.get(clause).getType() == FrostlakeLexer.SELECT) {
            // A value run into the alias with more of the query after it; a word there has a reading of its own.
            if (readsAsAName(spoken.get(fault)) || end + 1 >= close) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
            return lines;
        }
        return spoken.get(clause).getType() == FrostlakeLexer.WHERE ? readOn(lines, sql, spoken, fault, end) : null;
    }

    /**
     * The line for an '=' after a select item's alias in a statement's own query: the alias is the item's, the '='
     * is refused, and nothing after it is reported — {@code SELECT 1 x = 1 FROM fz WHERE a b} is the '=' alone
     * (live-verified).
     *
     * @param spoken     the text's tokens on the default channel
     * @param faultIndex the token index of the fault the parse names, the alias
     * @return the line, or null where this reading does not apply
     */
    static List<String> comparedAliasLine(final List<Token> spoken, final int faultIndex) {
        final int fault = CastFaultPlace.positionOf(spoken, faultIndex);
        if (fault < 2 || fault + 1 >= spoken.size() || spoken.get(0).getType() != FrostlakeLexer.SELECT
                || spoken.get(fault + 1).getType() != FrostlakeLexer.EQ || !comparesAnAlias(spoken, fault)
                || CastFaultPlace.enclosingParen(spoken, fault) >= 0) {
            return null;
        }
        final int clause = CastFaultPlace.clauseOf(spoken, fault);
        if (clause != 0) {
            return null;
        }
        for (int i = 1; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                return null;
            }
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 1)));
        return lines;
    }

    /** Whether the word at {@code at} is an alias after a value that an '=' or an IN follows. */
    private static boolean comparesAnAlias(final List<Token> spoken, final int at) {
        final int after = spoken.get(at + 1).getType();
        if (after != FrostlakeLexer.EQ && after != FrostlakeLexer.IN || !SyntaxErrorListener.isNameLike(spoken.get(at))) {
            return false;
        }
        final Token value = spoken.get(at - 1);
        return isLiteral(value) || value.getType() == FrostlakeLexer.RPAREN || value.getType() == FrostlakeLexer.AS;
    }

    private static boolean isOperator(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.EQ || type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE;
    }

    /** The first comma inside the parentheses from {@code open} to {@code close}, at any depth. */
    private static int firstComma(final List<Token> spoken, final int open, final int close) {
        for (int i = open + 1; i < close; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.COMMA) {
                return i;
            }
        }
        return close;
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
