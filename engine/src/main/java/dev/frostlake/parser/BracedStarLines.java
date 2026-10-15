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
 * The lines the account reports for a fault inside the braces of a star read as a value, {@code {*}}, or straight
 * after them inside a call. The account reads the braces as a bracket of the call around them: after a comma it
 * closes them where they stand and reads the rest as the call's arguments, so the closing brace is the token left
 * over (all live-verified, each for a text outside any block):
 *
 * <pre>
 *   SELECT TO_JSON({*, 1}) FROM t            ',', then the '}'
 *   SELECT TO_JSON({*, x}) FROM t            ',', then the '}' twice: an argument that is a name is read again
 *   SELECT TO_JSON({*, t.id}) FROM t         ',', the '}', then the '.' the name is read up to
 *   SELECT TO_JSON({*, x y}) FROM t          ',', then 'y' twice
 *   SELECT TO_JSON({*,}) FROM t              ',' alone, and the rest of the statement is read
 *   SELECT TO_JSON({*,,}) FROM t             ',', the second ',', then the '}'
 *   SELECT {*, x} FROM t                     ',', then the '}' once: a select item is not read again
 *   SELECT TO_JSON({* x y}) FROM t           'x', then the '}': the name after the word is taken
 *   SELECT TO_JSON({* x y z}) FROM t         'x', then 'z'
 *   SELECT {* x y} FROM t                    'x', then 'y'
 *   SELECT TO_JSON({* 1 2}) FROM t           '1' alone
 *   SELECT TO_JSON({* EXCLUDE b ILIKE 'i%' x}) FROM t   'ILIKE', 'x', then the '}'
 *   SELECT TO_JSON({* EXCLUDE (a) EXCLUDE (b)}) FROM t  the second 'EXCLUDE', then its '('
 *   SELECT TO_JSON({* RENAME id}) FROM t     'RENAME', then the '}'
 *   SELECT TO_JSON({DISTINCT *}) FROM t      'DISTINCT', then the '}' — and so ALL
 *   SELECT TO_JSON({DISTINCT *, 1}) FROM t   'DISTINCT', the ',', then the '}'
 *   SELECT TO_JSON({1, 2}) FROM t            '1', then the '}'
 *   SELECT TO_JSON({id, b}) FROM t           ',', then the '}' twice
 *   SELECT TO_JSON({id b}) FROM t            'b' alone
 *   SELECT TO_JSON({*} x y) FROM t           'x', then 'y'
 *   SELECT TO_JSON({*} x, 1) FROM t          'x', then the call's ')'
 *   SELECT TO_JSON({*} 1 2) FROM t           '1' alone
 *   SELECT TO_JSON({*} {*} {*}) FROM t       the second '{', then each '}' after it
 * </pre>
 *
 * <p>Where the braces are a call's argument and close, the statement is read on past the call: a later fault of
 * the select list, of a clause or of a statement after is reported too. A word after the star's own part is read
 * this way in the select list alone; in a clause it is the one line.
 */
final class BracedStarLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private BracedStarLines() {
    }

    /**
     * The lines of a text whose first fault stands inside such braces or straight after them, or null.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        if (sql == null || faultIndex < 0 || spoken.size() < 5) {
            return null;
        }
        final int end = spoken.size() - 1;
        int fault = -1;
        int start = 0;
        boolean nested = false;
        final List<Integer> open = new ArrayList<>();
        for (int i = 0; i < end && fault < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.BEGIN || type == FrostlakeLexer.DECLARE) {
                return null;
            }
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            } else if (type == FrostlakeLexer.SEMI) {
                start = i + 1;
                nested = false;
                open.clear();
            } else if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE
                    || type == FrostlakeLexer.LBRACKET) {
                open.add(Integer.valueOf(i));
            } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACE
                    || type == FrostlakeLexer.RBRACKET) {
                if (open.isEmpty()) {
                    return null;
                }
                open.remove(open.size() - 1);
            } else if (type == FrostlakeLexer.SELECT && !open.isEmpty()) {
                nested = true;
            }
        }
        if (fault < start + 2) {
            return null;
        }
        final int inner = open.isEmpty() ? -1 : open.get(open.size() - 1).intValue();
        if (inner >= 0 && spoken.get(inner).getType() == FrostlakeLexer.LBRACE) {
            final int around = open.size() < 2 ? -1 : open.get(open.size() - 2).intValue();
            return inside(sql, spoken, inner, fault, around, end,
                !nested && inSelectList(spoken, start, inner));
        }
        if (spoken.get(fault - 1).getType() == FrostlakeLexer.RBRACE && inner >= 0
                && spoken.get(inner).getType() == FrostlakeLexer.LPAREN && isName(spoken.get(inner - 1).getType())) {
            final int opener = openerOf(spoken, fault - 1, inner);
            if (opener < 0 || !opensWithStar(spoken, opener)) {
                return null;
            }
            return after(sql, spoken, fault, closerOf(spoken, inner, end),
                !nested && inSelectList(spoken, start, inner));
        }
        return null;
    }

    /** A fault inside the braces opened at {@code brace}. */
    private static List<String> inside(final String sql, final List<Token> spoken, final int brace, final int fault,
                                       final int around, final int end, final boolean selectList) {
        for (int i = brace + 1; i < fault; i++) {
            final int before = spoken.get(i).getType();
            if (before == FrostlakeLexer.REPLACE || before == FrostlakeLexer.RENAME) {
                // A star's RENAME or REPLACE before the fault is the line: see StarModifierLines.
                return null;
            }
        }
        final int close = closerOf(spoken, brace, end);
        final Token refused = spoken.get(fault);
        final int type = refused.getType();
        final int first = spoken.get(brace + 1).getType();
        final boolean star = opensWithStar(spoken, brace);
        final boolean named = !star && isName(first);
        final List<String> lines = told(refused);
        if (type == FrostlakeLexer.COMMA && (star || named)) {
            return afterComma(sql, spoken, brace, fault, close, around, end, lines);
        }
        if (fault == brace + 1 && (first == FrostlakeLexer.DISTINCT || first == FrostlakeLexer.ALL)) {
            if (fault + 1 == close) {
                return lines;
            }
            if (spoken.get(fault + 1).getType() == FrostlakeLexer.STAR
                    && spoken.get(fault + 2).getType() == FrostlakeLexer.COMMA) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 2)));
            }
            return close < 0 ? lines : with(lines, spoken.get(close));
        }
        if (fault == brace + 1 && (first == FrostlakeLexer.INTEGER_LITERAL || first == FrostlakeLexer.FLOAT_LITERAL)) {
            return close < 0 || fault + 1 == close ? lines : with(lines, spoken.get(close));
        }
        if (type == FrostlakeLexer.RBRACE && fault == close && (star || named)) {
            return lines;
        }
        if (named && fault == brace + 2 && isName(type)) {
            return lines;
        }
        if (!star || close < 0) {
            return null;
        }
        if (type == FrostlakeLexer.ILIKE && isName(spoken.get(fault - 1).getType())
                && spoken.get(fault - 2).getType() == FrostlakeLexer.EXCLUDE
                && spoken.get(fault + 1).getType() == FrostlakeLexer.STRING_LITERAL) {
            // The pattern is read as the operator's, and what is left before the brace is refused with it.
            if (fault + 2 == close) {
                return with(lines, spoken.get(close));
            }
            return isName(spoken.get(fault + 2).getType()) && fault + 3 == close
                ? with(with(lines, spoken.get(fault + 2)), spoken.get(close)) : lines;
        }
        if (type == FrostlakeLexer.EXCLUDE && spoken.get(fault + 1).getType() == FrostlakeLexer.LPAREN) {
            return with(lines, spoken.get(fault + 1));
        }
        if ((type == FrostlakeLexer.RENAME || type == FrostlakeLexer.REPLACE) && around >= 0 && selectList
                && isName(spoken.get(fault + 1).getType()) && fault + 2 == close) {
            // One name after the word: the name is taken, and the brace refused.
            return with(lines, spoken.get(close));
        }
        if (type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
                || type == FrostlakeLexer.STRING_LITERAL) {
            return lines;
        }
        if (!isPlainWord(type) || !selectList) {
            return null;
        }
        if (spoken.get(fault - 1).getType() == FrostlakeLexer.ILIKE) {
            // A word where the pattern is due is the one line.
            return lines;
        }
        if (fault + 1 == close) {
            // The word is passed over, and the statement read on.
            lines.addAll(readOn(sql, refused.getStartIndex(), refused.getStopIndex(), " "));
            return lines;
        }
        if (around < 0) {
            // Braces that are a select item: the word is the item's alias, and the token after it refused.
            return with(lines, spoken.get(fault + 1));
        }
        for (int i = fault + 1; i < close; i++) {
            if (isName(spoken.get(i).getType())) {
                return with(lines, spoken.get(i + 1));
            }
        }
        return lines;
    }

    /** A comma inside the braces: they are closed there, and what follows is read as the call's arguments. */
    private static List<String> afterComma(final String sql, final List<Token> spoken, final int brace,
                                           final int comma, final int close, final int around, final int end,
                                           final List<String> lines) {
        if (close < 0) {
            return lines;
        }
        final Token refused = spoken.get(comma);
        if (comma + 1 == close) {
            lines.addAll(readOn(sql, refused.getStartIndex(), refused.getStopIndex(), " "));
            return lines;
        }
        if (spoken.get(comma + 1).getType() == FrostlakeLexer.COMMA) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(comma + 1)));
        }
        int last = comma + 1;
        int depth = 0;
        for (int i = comma + 1; i < close; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE || type == FrostlakeLexer.LBRACKET) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACE
                    || type == FrostlakeLexer.RBRACKET) {
                depth--;
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                last = i + 1;
            }
        }
        final int size = close - last;
        final boolean leadsWithName = size > 0 && isName(spoken.get(last).getType());
        if (size == 2 && leadsWithName && isName(spoken.get(last + 1).getType())) {
            // Two words: the second is refused, and again as the argument is read from its name.
            return with(with(lines, spoken.get(last + 1)), spoken.get(last + 1));
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
        if (around >= 0 && spoken.get(around).getType() == FrostlakeLexer.LPAREN && leadsWithName) {
            if (size == 1) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
            } else if (isPath(spoken, last, close)) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(last + 1)));
            }
        }
        if (around < 0) {
            return lines;
        }
        final int outer = closerOf(spoken, around, end);
        if (outer < 0) {
            return lines;
        }
        // Read on past the bracket around the braces, the braces read as a bare star's.
        lines.addAll(readOn(sql, spoken.get(brace + 1).getStartIndex(), spoken.get(outer).getStartIndex() - 1, "*}"));
        return lines;
    }

    /** A fault straight after the braces, inside the call whose parenthesis closes at {@code outer}. */
    private static List<String> after(final String sql, final List<Token> spoken, final int fault, final int outer,
                                      final boolean selectList) {
        final Token refused = spoken.get(fault);
        final int type = refused.getType();
        final List<String> lines = told(refused);
        if (type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
                || type == FrostlakeLexer.STRING_LITERAL) {
            return lines;
        }
        if (type == FrostlakeLexer.LBRACE && outer < 0) {
            return lines;
        }
        if (outer < 0) {
            return null;
        }
        if (type == FrostlakeLexer.LBRACE) {
            for (int i = fault + 1; i < outer; i++) {
                if (spoken.get(i).getType() == FrostlakeLexer.RBRACE) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                }
            }
            return isName(spoken.get(outer - 1).getType()) ? with(lines, spoken.get(outer)) : lines;
        }
        if (!isName(type) || !selectList) {
            return null;
        }
        if (fault + 1 == outer) {
            lines.addAll(readOn(sql, refused.getStartIndex(), refused.getStopIndex(), " "));
            return lines;
        }
        return with(lines, spoken.get(fault + 1).getType() == FrostlakeLexer.COMMA ? spoken.get(outer)
            : spoken.get(fault + 1));
    }

    /** The lines of the text with a stretch blanked — {@code put} written where it began — read afresh. */
    private static List<String> readOn(final String sql, final int from, final int to, final String put) {
        final char[] text = sql.toCharArray();
        if (to - from + 1 < put.length()) {
            return new ArrayList<>();
        }
        for (int c = from; c <= to && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
        for (int c = 0; c < put.length() && from + c < text.length; c++) {
            text[from + c] = put.charAt(c);
        }
        return SyntaxErrorListener.linesReportedAfresh(new String(text));
    }

    /** Whether the braces at {@code brace} open with a star, bare or qualified by one name. */
    private static boolean opensWithStar(final List<Token> spoken, final int brace) {
        final int first = spoken.get(brace + 1).getType();
        return first == FrostlakeLexer.STAR || isName(first) && brace + 3 < spoken.size()
            && spoken.get(brace + 2).getType() == FrostlakeLexer.DOT
            && spoken.get(brace + 3).getType() == FrostlakeLexer.STAR;
    }

    /** Whether the tokens from {@code from} up to {@code to} are names with dots between them and nothing else. */
    private static boolean isPath(final List<Token> spoken, final int from, final int to) {
        if ((to - from) % 2 == 0) {
            return false;
        }
        for (int i = from; i < to; i++) {
            final int type = spoken.get(i).getType();
            if ((i - from) % 2 == 0 ? !isName(type) : type != FrostlakeLexer.DOT) {
                return false;
            }
        }
        return to - from >= 3;
    }

    /** Whether the token at {@code index} stands in the select list of the statement that starts at {@code start}. */
    private static boolean inSelectList(final List<Token> spoken, final int start, final int index) {
        int depth = 0;
        boolean select = false;
        for (int i = start; i < index; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE || type == FrostlakeLexer.LBRACKET) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACE
                    || type == FrostlakeLexer.RBRACKET) {
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.SELECT) {
                select = true;
            } else if (depth == 0 && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
                    || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING
                    || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER
                    || type == FrostlakeLexer.LIMIT)) {
                select = false;
            }
        }
        return select;
    }

    /** The token that closes the bracket at {@code open}, or -1 when the statement ends first. */
    private static int closerOf(final List<Token> spoken, final int open, final int end) {
        int depth = 0;
        for (int i = open; i < end; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE || type == FrostlakeLexer.LBRACKET) {
                depth++;
            } else if ((type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACE
                    || type == FrostlakeLexer.RBRACKET) && --depth == 0) {
                return closes(spoken.get(open).getType(), type) ? i : -1;
            }
        }
        return -1;
    }

    private static boolean closes(final int opener, final int closer) {
        return opener == FrostlakeLexer.LPAREN ? closer == FrostlakeLexer.RPAREN
            : opener == FrostlakeLexer.LBRACE ? closer == FrostlakeLexer.RBRACE : closer == FrostlakeLexer.RBRACKET;
    }

    /** The '{' the brace at {@code close} closes, looked for no further back than {@code floor}; or -1. */
    private static int openerOf(final List<Token> spoken, final int close, final int floor) {
        int depth = 0;
        for (int i = close; i > floor; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACE || type == FrostlakeLexer.RBRACKET) {
                depth++;
            } else if ((type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE
                    || type == FrostlakeLexer.LBRACKET) && --depth == 0) {
                return type == FrostlakeLexer.LBRACE ? i : -1;
            }
        }
        return -1;
    }

    private static List<String> told(final Token token) {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(token));
        return lines;
    }

    private static List<String> with(final List<String> lines, final Token token) {
        lines.add(SyntaxErrorListener.sentence(token));
        return lines;
    }

    private static boolean isName(final int type) {
        return WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    /** A name that is none of a star's own modifier words. */
    private static boolean isPlainWord(final int type) {
        return isName(type) && type != FrostlakeLexer.REPLACE && type != FrostlakeLexer.RENAME
            && type != FrostlakeLexer.EXCLUDE && type != FrostlakeLexer.ILIKE;
    }
}
