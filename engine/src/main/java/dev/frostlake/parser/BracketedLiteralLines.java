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
 * The lines live reports for a stray word after a literal in a bracket that is an argument of a call in a select
 * item, where the bracket is read through: its ')' closes the call instead, each later ')' the bracket around that, and
 * the first token the statement then cannot take is reported after the word — in a CTE's or a derived table's query
 * the token after the ')' of the select item's outermost bracket, where the query now ends (all live-verified):
 *
 * <pre>
 *   WITH c AS (SELECT ABS((1 x y))) SELECT * FROM c          'x', then the CTE's ')'
 *   SELECT * FROM (SELECT ABS(ABS((1 x y))) FROM T) d        'x', then the FROM
 *   SELECT IFF(TRUE, (1 x y), 2)                             'x', then IFF's ')'
 *   SELECT IFF(TRUE, 1, (1 x y))                             'x' alone: the last argument of the item's call
 * </pre>
 *
 * <p>A query's own select item is read through when the bracket is not its call's last argument; a CTE's or a
 * derived table's query reads it through in any place. A CAST's operand is a construct of its own
 * ({@link #castOperandLines}), and a select item of the query's own calls two deep or more is told by
 * {@link NestedCallArgumentLines}.
 */
final class BracketedLiteralLines {

    private BracketedLiteralLines() {
    }

    /**
     * The lines for a text whose first fault is such a word, or null.
     *
     * @param sql        the text
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (sql == null || fault < 4 || !StarModifierResync.isName(spoken.get(fault)) || !literal(spoken.get(fault - 1))
                || spoken.get(fault - 2).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final int bracket = fault - 2;
        final List<String> cast = castOperandLines(spoken, fault, bracket);
        if (cast != null) {
            return cast;
        }
        final int call = enclosingOpen(spoken, bracket);
        final int before = spoken.get(bracket - 1).getType();
        if (call < 1 || before != FrostlakeLexer.LPAREN && before != FrostlakeLexer.COMMA
                || !StarModifierResync.isName(spoken.get(call - 1))) {
            return null;
        }
        final int close = closing(spoken, bracket);
        final int select = queryStart(spoken, call - 1);
        if (close < 0 || select < 0) {
            return null;
        }
        final boolean subquery = select > 0 && spoken.get(select - 1).getType() == FrostlakeLexer.LPAREN;
        final boolean last = spoken.get(close + 1).getType() == FrostlakeLexer.RPAREN;
        if (!subquery && last) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        if (subquery) {
            // The subquery ends at the ')' of the select item's outermost bracket, and the token after it is refused.
            int outer = bracket;
            for (int open = enclosingOpen(spoken, bracket); open > select; open = enclosingOpen(spoken, open)) {
                outer = open;
            }
            final int outerClose = closing(spoken, outer);
            if (outerClose > 0 && outerClose + 1 < spoken.size()) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(outerClose + 1)));
            }
            return lines;
        }
        final char[] text = sql.toCharArray();
        blank(text, spoken.get(bracket));
        for (int i = fault; i < close; i++) {
            blank(text, spoken.get(i));
        }
        final String next = StatementSeparation.firstFaultOf(new String(text), -1);
        final int[] at = next == null ? null : SyntaxErrorListener.sentenceCoordinates(next);
        final Token word = spoken.get(fault);
        if (at != null && (at[0] > word.getLine() || at[0] == word.getLine() && at[1] > word.getCharPositionInLine())) {
            lines.add(next);
        }
        return lines;
    }

    /**
     * The lines for such a word in the bracketed operand of a CAST or a TRY_CAST in a select item, or null (all
     * live-verified): the word, then the token after it, read as the cast's type, and then the token after that, at
     * which the cast cannot close — or, where that is the bracket's ')' and the cast closes there, the token past the
     * {@code AS} and the word after it, read as the item's alias. A bracket around the operand's own closes the cast
     * one parenthesis early, and the token after the operand is refused:
     *
     * <pre>
     *   SELECT CAST((1 x) AS INT)                'x' alone
     *   SELECT CAST((1 x y) AS INT)              'x', 'y', then the cast's own ')'
     *   SELECT CAST((1 x y) AS NUMBER(5, 2))     'x', 'y', then the type's '('
     *   SELECT CAST((1 x y z) AS INT)            'x', 'y', 'z'
     *   SELECT CAST(((1 x y)) AS INT)            'x', then the ')' of the bracket around the operand
     * </pre>
     */
    private static List<String> castOperandLines(final List<Token> spoken, final int fault, final int bracket) {
        final boolean single = bracket > 1 && spoken.get(bracket - 1).getType() == FrostlakeLexer.LPAREN
            && CastFaultPlace.isCast(spoken, bracket - 2);
        final boolean doubled = !single && bracket > 2 && spoken.get(bracket - 1).getType() == FrostlakeLexer.LPAREN
            && spoken.get(bracket - 2).getType() == FrostlakeLexer.LPAREN && CastFaultPlace.isCast(spoken, bracket - 3);
        final int close = closing(spoken, bracket);
        if (!single && !doubled || close < 0
                || queryStart(spoken, bracket - (single ? 2 : 3)) < 0) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        if (doubled) {
            if (closing(spoken, bracket - 1) != close + 1) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(close + 1)));
            return lines;
        }
        final Token type = spoken.get(fault + 1);
        if (fault + 1 == close) {
            return lines;
        }
        if (!StarModifierResync.isName(type) && !literal(type)) {
            return null;
        }
        lines.add(SyntaxErrorListener.sentence(type));
        if (fault + 2 != close) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 2)));
            return lines;
        }
        if (close + 3 >= spoken.size() || spoken.get(close + 1).getType() != FrostlakeLexer.AS
                || !word(spoken.get(close + 2)) || spoken.get(close + 3).getType() == Token.EOF) {
            return null;
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(close + 3)));
        return lines;
    }

    /** Whether a token is a word written with letters: a name, a keyword or a type's own word. */
    private static boolean word(final Token token) {
        final String text = token.getText();
        return token.getType() != Token.EOF && text != null && !text.isEmpty()
            && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_');
    }

    /**
     * The index of the SELECT whose select list holds the token at {@code at} — no clause word stands between them at
     * their depth — or -1.
     */
    private static int queryStart(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                depth--;
            } else if (depth <= 0 && type == FrostlakeLexer.SELECT) {
                return i;
            } else if (depth <= 0 && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
                    || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY
                    || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.SEMI)) {
                return -1;
            }
        }
        return -1;
    }

    /** The index of the parenthesis that opens the bracket the token at {@code at} stands in, or -1. */
    private static int enclosingOpen(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && depth-- == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the parenthesis that closes the one at {@code open}, or -1. */
    private static int closing(final List<Token> spoken, final int open) {
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

    private static void blank(final char[] text, final Token token) {
        for (int c = token.getStartIndex(); c <= token.getStopIndex() && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
    }

    private static boolean literal(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL;
    }
}
