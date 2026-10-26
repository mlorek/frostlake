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
 * The lines live reports for a comma in a MATCH_RECOGNIZE pattern's bracket, or a reserved word opening one (all
 * live-verified). A PERMUTE's list takes commas only while each of its items is a pattern; a reserved word among them
 * makes the PERMUTE a symbol and its list a bracket. The first such comma — or the reserved word — is the first line:
 *
 * <pre>
 *   PATTERN ((lo, hi))                    ',', then the bracket's ')' and the pattern's
 *   PATTERN (PERMUTE(lo, hi, any) lo)     ',' at the first comma, as for (zz(lo, hi, any) lo)
 *   PATTERN ((any, lo))                   'any', then the brackets' ')'s
 * </pre>
 *
 * <p>In a bracket inside the pattern's own, live then reads on as if the pattern ended at the fault and the clause's
 * definitions began there: the word after the comma is a definition's symbol, and what follows it is read as its
 * {@code AS} and its value would be. A token where the AS is due is reported — a name is read as the value, a ')' is
 * taken as the clause's own closing parenthesis, a comma begins another definition, whose symbol a reserved word
 * cannot be. A value is read to the first token that cannot go on with it: a comma begins another definition, a ')'
 * closes the clause. Past the clause's closing parenthesis the relation may take an alias or a comma and another
 * relation, and the first token after those that the query cannot go on with is reported, and nothing after it:
 *
 * <pre>
 *   PATTERN ((lo, hi) lo)          ',', ')' at the AS's place, then the ')' after the alias lo
 *   PATTERN ((lo, hi lo))          ',', 'lo' at the AS's place and read as the value, then the pattern's ')'
 *   PATTERN ((lo, hi, any))        ',', ',' at the AS's place, 'any' where a symbol is due, then the pattern's ')'
 *   PATTERN ((lo, hi)|lo)          ',', ')', then the '|' no relation can be followed by
 *   PATTERN (((lo, hi)))           ',', then the ')' of the outer two brackets: a deeper bracket closes unread
 * </pre>
 *
 * <p>When the token after the comma is no name, live skips to the first name, comma or DEFINE: a name is a
 * definition's symbol, a comma follows the relation with another one, and the clause's DEFINE reads on without a
 * further fault — {@code PATTERN ((lo, any))} is the comma alone, {@code PATTERN ((lo, any) lo)} the comma, the ')'
 * at lo's AS and the DEFINE's symbol after the alias DEFINE. A reserved word opening a bracket skips to the first
 * name the same way, passing commas too.
 *
 * <p>A comma in the pattern's own bracket is reported alone, unless a second comma follows it in that bracket - the
 * reserved word after that comma is reported, or else the bracket's ')' - or a bracket right after it holds a comma
 * of its own - its ')' is reported - or a reserved word alone stands between it and the bracket's ')' - the DEFINE's
 * symbol after the alias DEFINE is reported. A comma opening the pattern's own bracket before a name and the
 * bracket's ')' makes the name a definition's symbol. A comma opening a bracket inside the pattern's own reports a
 * second comma in that bracket, or else what follows the bracket's ')': the pattern's ')', or a name, read on as a
 * definition's symbol. Any other comma opening a bracket is reported alone:
 *
 * <pre>
 *   PATTERN (lo, hi, lo)           ',', then the pattern's ')'
 *   PATTERN (lo, hi, any)          ',', then 'any'
 *   PATTERN (lo, (hi, lo))         ',', then the ')' of the bracket after it
 *   PATTERN (lo, any)              ',', then the DEFINE's symbol after the alias DEFINE
 *   PATTERN (, lo)                 ',', ')' at lo's AS, then the DEFINE's symbol
 *   PATTERN ((, lo, hi))           ',', then the second ','
 *   PATTERN ((, lo))               ',', then the pattern's ')'
 *   PATTERN ((, lo) hi)            ',', 'hi', ')' at hi's AS, then the DEFINE's symbol
 * </pre>
 */
final class MatchRecognizePatternLines {

    private final List<Token> spoken;
    private final List<String> lines = new ArrayList<>();
    private final boolean[] skipped;

    private MatchRecognizePatternLines(final List<Token> spoken) {
        this.spoken = spoken;
        this.skipped = new boolean[spoken.size()];
    }

    /**
     * The lines for a MATCH_RECOGNIZE clause whose PATTERN holds such a fault, or null when it holds none or the
     * parse's own first fault stands before the pattern's PATTERN, past the clause's opening parenthesis.
     *
     * @param spoken    the text's default-channel tokens, end of input included
     * @param open      the index of the clause's opening parenthesis
     * @param close     the index of the clause's closing parenthesis
     * @param fault     the index of the parse's own first fault
     * @param firstOnly whether only the first line is read
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int open, final int close, final int fault,
                              final boolean firstOnly) {
        int bracket = -1;
        int depth = 0;
        for (int i = open + 1; i < close && bracket < 0; i++) {
            final int type = spoken.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            if (depth == 0 && type == FrostlakeLexer.PATTERN && spoken.get(i + 1).getType() == FrostlakeLexer.LPAREN) {
                bracket = i + 1;
            }
        }
        final int end = bracket < 0 ? -1 : closing(spoken, bracket);
        if (end < 0 || end > close || fault != open && fault < bracket - 1) {
            return null;
        }
        // The first fault: a comma in a bracket that is no PERMUTE's list, or a reserved word opening a bracket.
        final List<Integer> opened = new ArrayList<>();
        int first = -1;
        for (int i = bracket; i < end && first < 0; i++) {
            final Token token = spoken.get(i);
            if (token.getType() == FrostlakeLexer.LPAREN) {
                opened.add(Integer.valueOf(i));
                if (reserved(spoken.get(i + 1))) {
                    first = i + 1;
                }
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                opened.remove(opened.size() - 1);
            } else if (token.getType() == FrostlakeLexer.COMMA
                    && !permuteList(spoken, opened.get(opened.size() - 1).intValue())) {
                first = i;
            } else if (reserved(token)) {
                return null;
            }
        }
        if (first < 0) {
            return null;
        }
        final MatchRecognizePatternLines reading = new MatchRecognizePatternLines(spoken);
        reading.lines.add(SyntaxErrorListener.sentence(spoken.get(first)));
        final boolean comma = spoken.get(first).getType() == FrostlakeLexer.COMMA;
        if (firstOnly || !comma && opened.size() < 2) {
            return reading.lines;
        }
        if (opened.size() < 2) {
            reading.ownBracket(first, end);
            return reading.lines;
        }
        if (comma && spoken.get(first - 1).getType() == FrostlakeLexer.LPAREN) {
            if (opened.size() == 2) {
                reading.openingComma(first, closing(spoken, first - 1), end);
            }
            return reading.lines;
        }
        // The ')'s of the brackets deeper than the one inside the pattern's own are read with the pattern.
        for (int k = 2; k < opened.size(); k++) {
            final int shut = closing(spoken, opened.get(k).intValue());
            if (shut > 0) {
                reading.skipped[shut] = true;
            }
        }
        if (comma && StarModifierResync.isName(spoken.get(first + 1))) {
            reading.definition(first + 1);
        } else {
            reading.restart(first + 1, comma);
        }
        return reading.lines;
    }

    /** A comma in the pattern's own bracket, whose ')' stands at {@code end}. */
    private void ownBracket(final int comma, final int end) {
        final Token after = spoken.get(comma + 1);
        if (spoken.get(comma - 1).getType() == FrostlakeLexer.LPAREN) {
            if (StarModifierResync.isName(after) && comma + 2 == end) {
                definition(comma + 1);
            }
            return;
        }
        if (reserved(after)) {
            if (comma + 2 == end) {
                relationTail(next(end));
            }
            return;
        }
        if (after.getType() == FrostlakeLexer.LPAREN) {
            final int shut = closing(spoken, comma + 1);
            if (shut > 0 && commaIn(comma + 1, shut) > 0) {
                add(shut);
            }
            return;
        }
        for (int i = comma + 1; i < end && spoken.get(i).getType() != FrostlakeLexer.LPAREN; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.COMMA) {
                add(reserved(spoken.get(i + 1)) ? i + 1 : end);
                return;
            }
        }
    }

    /** A comma opening a bracket inside the pattern's own: the bracket's ')' stands at {@code shut}. */
    private void openingComma(final int comma, final int shut, final int end) {
        for (int i = comma + 1; i < shut; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                return;
            }
            if (type == FrostlakeLexer.COMMA) {
                add(i);
                return;
            }
        }
        if (shut + 1 == end) {
            add(end);
        } else if (StarModifierResync.isName(spoken.get(shut + 1))) {
            add(shut + 1);
            definition(shut + 1);
        }
    }

    /** The index of a comma the bracket at {@code open} holds at its own depth, or -1. */
    private int commaIn(final int open, final int shut) {
        int depth = 0;
        for (int i = open + 1; i < shut; i++) {
            final int type = spoken.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            if (depth == 0 && type == FrostlakeLexer.COMMA) {
                return i;
            }
        }
        return -1;
    }

    /** Past a token that is no name: the first name, comma or DEFINE after it decides the reading. */
    private void restart(final int from, final boolean afterComma) {
        for (int i = next(from - 1); i < spoken.size() && spoken.get(i).getType() != Token.EOF; i = next(i)) {
            final Token token = spoken.get(i);
            if ("DEFINE".equalsIgnoreCase(token.getText())) {
                return;
            }
            if (StarModifierResync.isName(token)) {
                definition(i);
                return;
            }
            if (afterComma && token.getType() == FrostlakeLexer.COMMA) {
                relationTail(i);
                return;
            }
        }
    }

    /** The definition whose symbol stands at {@code symbol}: its AS is due after it. */
    private void definition(final int symbol) {
        final int at = next(symbol);
        final Token token = spoken.get(at);
        final int type = token.getType();
        if (type == FrostlakeLexer.AS) {
            value(next(at));
        } else if (StarModifierResync.isName(token) || literal(token)) {
            add(at);
            value(at);
        } else if (type == FrostlakeLexer.RPAREN) {
            add(at);
            relationTail(next(at));
        } else if (type == FrostlakeLexer.COMMA) {
            add(at);
            symbol(next(at));
        } else if (type != Token.EOF) {
            add(at);
        }
    }

    /** A definition's symbol is due at {@code at}, after a comma. */
    private void symbol(final int at) {
        if (StarModifierResync.isName(spoken.get(at))) {
            definition(at);
            return;
        }
        if (spoken.get(at).getType() == Token.EOF) {
            return;
        }
        add(at);
        for (int i = next(at); i < spoken.size() && spoken.get(i).getType() != Token.EOF; i = next(i)) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.COMMA) {
                symbol(next(i));
                return;
            }
            if (type == FrostlakeLexer.RPAREN) {
                relationTail(next(i));
                return;
            }
        }
    }

    /** A definition's value read from {@code from}: the first token that cannot go on with it decides. */
    private void value(final int from) {
        int at = from;
        int depth = 0;
        boolean operand = false;
        while (at < spoken.size()) {
            final Token token = spoken.get(at);
            final int type = token.getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
                operand = false;
            } else if (type == FrostlakeLexer.RPAREN && depth > 0) {
                depth--;
                operand = true;
            } else if ((StarModifierResync.isName(token) || literal(token)) && !operand) {
                operand = true;
            } else if (operand && operator(type)) {
                operand = false;
            } else {
                break;
            }
            at = next(at);
        }
        final int type = at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
        if (type == FrostlakeLexer.COMMA) {
            symbol(next(at));
        } else if (type == FrostlakeLexer.RPAREN) {
            relationTail(next(at));
        } else if (type != Token.EOF) {
            add(at);
        }
    }

    /** Past the clause's closing parenthesis: an alias, or a comma and another relation, then the first fault. */
    private void relationTail(final int from) {
        int at = from;
        if (spoken.get(at).getType() == FrostlakeLexer.AS) {
            at = next(next(at));
        } else if (StarModifierResync.isName(spoken.get(at))) {
            at = next(at);
        }
        while (spoken.get(at).getType() == FrostlakeLexer.COMMA && StarModifierResync.isName(spoken.get(next(at)))) {
            at = next(next(at));
        }
        if (spoken.get(at).getType() != Token.EOF && spoken.get(at).getType() != FrostlakeLexer.SEMI) {
            add(at);
        }
    }

    /** The index of the next token after {@code at} the reading reads, past the ')'s closed with the pattern. */
    private int next(final int at) {
        int i = at + 1;
        while (i < spoken.size() - 1 && skipped[i]) {
            i++;
        }
        return Math.min(i, spoken.size() - 1);
    }

    /** Whether the bracket at {@code open} is a PERMUTE's list whose every item is a pattern. */
    private static boolean permuteList(final List<Token> spoken, final int open) {
        if (open < 1 || !"PERMUTE".equalsIgnoreCase(spoken.get(open - 1).getText())) {
            return false;
        }
        final int shut = closing(spoken, open);
        for (int i = open + 1; i < shut; i++) {
            if (reserved(spoken.get(i))) {
                return false;
            }
        }
        return shut > 0;
    }

    private void add(final int at) {
        lines.add(SyntaxErrorListener.sentence(spoken.get(at)));
    }

    /** Whether a token is a word that cannot be a name. */
    private static boolean reserved(final Token token) {
        final String text = token.getText();
        return token.getType() != Token.EOF && text != null && !text.isEmpty()
            && Character.isLetter(text.charAt(0)) && !StarModifierResync.isName(token);
    }

    private static boolean literal(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL;
    }

    /** Whether a token joins two operands of a value. */
    private static boolean operator(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.LT || type == FrostlakeLexer.GT
            || type == FrostlakeLexer.EQ || type == FrostlakeLexer.LTE || type == FrostlakeLexer.GTE
            || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.AND || type == FrostlakeLexer.OR;
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
}
