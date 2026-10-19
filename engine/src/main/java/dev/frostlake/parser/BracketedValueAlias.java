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
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The lines live reports when a word is run into a value inside a bracket of a select item — plain parentheses, a
 * call's or a CAST's, or an array's brackets. Before the bracket's own close the word is set aside and the bracket
 * read on; anywhere else live gives the bracket up, takes the word as the item's alias and reads on from there as
 * the select list's own, and the next fault it meets ends the report. This parser named the word and stopped (all
 * live-verified):
 *
 * <pre>
 *   SELECT (1 x y)                 'x', then 'y'
 *   SELECT ABS(1, 2 x y) FROM t    'x', then 'y'
 *   SELECT (1 x 2), SELECT (1 x AS y)   'x', then '2' and 'AS'
 *   SELECT (1 x, y)                'x', then the ')': the comma reads the next select item
 *   SELECT (1 x FROM y)            'x', then the ')': FROM y is read as the query's own
 *   SELECT (1 AS x y)              'AS', then 'y'
 *   SELECT (1 x) y z               'x', then 'z': the bracket closes, and y is the alias
 *   SELECT (1 x), (2 z w)          'x', then 'z' and 'w'
 *   SELECT (1 x) 'q'; SELECT 1 x y 'x', then the later 'y': a literal after the bracket is passed over unnamed
 *   SELECT (1 x)                   'x' alone
 * </pre>
 *
 * <p>It holds for the select list of a statement's own query, of a derived table and of a common table expression,
 * inside any depth of plain parentheses or inside one call; not for a scalar subquery, a call inside another
 * bracket, a CASE, or a bracket of any other clause, where the word's line stands alone, and not for a call's
 * argument that opens with a name (see {@link CallArgumentRetryLine}). The reading is rebuilt on the text: the
 * bracket up to the word becomes a one-character value, or the word a blank.
 */
final class BracketedValueAlias {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    /** A bracket's kinds: plain parentheses, a call's, an array's, a query's, and any other. */
    private static final int PLAIN = 0;
    private static final int CALL = 1;
    private static final int ARRAY = 2;
    private static final int QUERY = 3;
    private static final int OTHER = 4;

    private BracketedValueAlias() {
    }

    /**
     * The lines live reports when the parse's first line names such a word, or null.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse) {
        if (sql == null || !statementParse || faultIndex < 0) {
            return null;
        }
        int fault = -1;
        for (int i = 1; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 3 || fault + 1 >= spoken.size()) {
            return null;
        }
        final Token word = spoken.get(fault);
        final boolean as = word.getType() == FrostlakeLexer.AS;
        if (!as && !isName(word.getType()) || !endsOperand(spoken.get(fault - 1).getType())) {
            return null;
        }
        final int outermost = outermostBracket(spoken, fault);
        if (outermost < 0) {
            return null;
        }
        final char[] text = sql.toCharArray();
        final int innermost = innermostBracket(spoken, fault);
        final int after = spoken.get(fault + 1).getType();
        final boolean closes = !as && (spoken.get(innermost).getType() == FrostlakeLexer.LBRACKET
            ? after == FrostlakeLexer.RBRACKET : after == FrostlakeLexer.RPAREN);
        if (closes) {
            blank(text, word.getStartIndex(), word.getStopIndex());
        } else {
            int start = outermost;
            if (kindOf(spoken, outermost) == CALL) {
                start--;
                while (start > 1 && spoken.get(start - 1).getType() == FrostlakeLexer.DOT
                        && isName(spoken.get(start - 2).getType())) {
                    start -= 2;
                }
            }
            blank(text, spoken.get(start).getStartIndex(), spoken.get(fault - 1).getStopIndex());
            text[spoken.get(start).getStartIndex()] = '1';
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(word));
        final int[] at = SyntaxErrorListener.sentenceCoordinates(lines.get(0));
        // A number or a string straight after the bracket the word was set aside in is passed over unnamed.
        final Token passed = closes && fault + 2 < spoken.size() && isLiteral(spoken.get(fault + 2).getType())
            ? spoken.get(fault + 2) : null;
        final String unnamed = passed == null ? null : SyntaxErrorListener.sentence(passed);
        for (final String line : SyntaxErrorListener.linesReportedAfresh(new String(text))) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])
                    && !line.equals(unnamed)) {
                lines.add(line);
                if (!closes) {
                    break;
                }
            }
        }
        return lines;
    }

    /**
     * The position of the outermost bracket still open at {@code fault} within its select item, or -1 unless the
     * fault stands in the select list of a query that is the statement's own, a derived table's or a common table
     * expression's, inside plain parentheses only or inside a single call's, CAST's or array's bracket.
     */
    private static int outermostBracket(final List<Token> spoken, final int fault) {
        int start = fault;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        // Per open bracket: its position, its kind, and the clause last opened at its level (-1 for none).
        final List<int[]> frames = new ArrayList<>();
        frames.add(new int[] {-1, QUERY, -1});
        for (int i = start; i < fault; i++) {
            final int type = spoken.get(i).getType();
            final int[] top = frames.get(frames.size() - 1);
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET) {
                frames.add(new int[] {i, kindOf(spoken, i), -1});
            } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET) {
                if (frames.size() == 1) {
                    return -1;
                }
                frames.remove(frames.size() - 1);
            } else if (isClause(type)) {
                top[2] = type;
            }
        }
        int query = frames.size() - 1;
        while (query >= 0 && frames.get(query)[2] == -1) {
            query--;
        }
        if (query < 0 || query == frames.size() - 1 || frames.get(query)[2] != FrostlakeLexer.SELECT
                || frames.get(query)[1] != QUERY) {
            return -1;
        }
        final int first = frames.get(query + 1)[1];
        if (first == CALL || first == ARRAY) {
            if (frames.size() - query != 2) {
                return -1;
            }
            final int open = frames.get(query + 1)[0];
            final int quantifier = spoken.get(open + 1).getType();
            if (quantifier == FrostlakeLexer.DISTINCT || quantifier == FrostlakeLexer.ALL) {
                return -1;
            }
            // An argument that opens with a name is retried as a named one instead: see CallArgumentRetryLine.
            int argument = open + 1;
            for (int i = open + 1; i < fault; i++) {
                if (spoken.get(i).getType() == FrostlakeLexer.COMMA) {
                    argument = i + 1;
                }
            }
            return first == CALL && isName(spoken.get(argument).getType())
                && spoken.get(argument + 1).getType() != FrostlakeLexer.STRING_LITERAL ? -1 : open;
        }
        for (int f = query + 1; f < frames.size(); f++) {
            if (frames.get(f)[1] != PLAIN) {
                return -1;
            }
        }
        return frames.get(query + 1)[0];
    }

    /** The position of the innermost bracket still open at {@code fault}. */
    private static int innermostBracket(final List<Token> spoken, final int fault) {
        int depth = 0;
        for (int i = fault - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET) {
                depth++;
            } else if ((type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET) && depth-- == 0) {
                return i;
            }
        }
        return 0;
    }

    /**
     * The kind of the bracket at {@code open}: a query's after a FROM, a JOIN or an AS, where a derived table or a
     * common table expression's body stands; a call's after a name or CAST; plain after the select list's SELECT, a
     * quantifier, a TOP's count, a comma, another plain bracket, an arithmetic or comparison operator, NOT or IN; an
     * array's for a '[' that follows no value.
     */
    private static int kindOf(final List<Token> spoken, final int open) {
        final int before = open == 0 ? Token.EOF : spoken.get(open - 1).getType();
        if (spoken.get(open).getType() == FrostlakeLexer.LBRACKET) {
            return isName(before) || endsOperand(before) ? OTHER : ARRAY;
        }
        if (before == FrostlakeLexer.FROM || before == FrostlakeLexer.JOIN || before == FrostlakeLexer.AS) {
            return QUERY;
        }
        if (isName(before) || before == FrostlakeLexer.CAST || before == FrostlakeLexer.TRY_CAST) {
            return CALL;
        }
        if (before == FrostlakeLexer.SELECT || before == FrostlakeLexer.DISTINCT || before == FrostlakeLexer.ALL
                || before == FrostlakeLexer.COMMA || before == FrostlakeLexer.LPAREN
                || before == FrostlakeLexer.PLUS || before == FrostlakeLexer.MINUS || before == FrostlakeLexer.STAR
                || before == FrostlakeLexer.SLASH || before == FrostlakeLexer.PERCENT
                || before == FrostlakeLexer.PIPE_PIPE || before == FrostlakeLexer.NOT || before == FrostlakeLexer.IN
                || before == FrostlakeLexer.EQ || before == FrostlakeLexer.NEQ || before == FrostlakeLexer.LT
                || before == FrostlakeLexer.LTE || before == FrostlakeLexer.GT || before == FrostlakeLexer.GTE) {
            return PLAIN;
        }
        return before == FrostlakeLexer.INTEGER_LITERAL && open > 1
            && spoken.get(open - 2).getType() == FrostlakeLexer.TOP ? PLAIN : OTHER;
    }

    private static void blank(final char[] text, final int from, final int to) {
        for (int c = from; c <= to && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
    }

    private static boolean isName(final int type) {
        return WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    private static boolean isLiteral(final int type) {
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.DOLLAR_QUOTED_STRING;
    }

    /** Whether a token can end the value a word is run into: a name, a literal or a closing bracket. */
    private static boolean endsOperand(final int type) {
        return isName(type) || type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.DOLLAR_QUOTED_STRING
            || type == FrostlakeLexer.TRUE || type == FrostlakeLexer.FALSE || type == FrostlakeLexer.NULL
            || type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET;
    }

    /** Whether a token opens one of a query's clauses. */
    private static boolean isClause(final int type) {
        return type == FrostlakeLexer.SELECT || type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
            || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY
            || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.SET
            || type == FrostlakeLexer.VALUES || type == FrostlakeLexer.ON || type == FrostlakeLexer.WHEN;
    }
}
