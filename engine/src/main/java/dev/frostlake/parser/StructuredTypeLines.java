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
 * The lines live reports for a fault inside the parentheses of a structured type — ARRAY, OBJECT, MAP, VECTOR —
 * written as a cast's target ({@code ::}, a CAST, a TRY_CAST) or as a table column's type, where the parse gives the
 * type up before any list of it is judged (all live-verified):
 *
 * <pre>
 *   NULL::ARRAY(5 NOT NULL), CAST(… AS ARRAY('x'))         the first token twice: it begins no element type
 *   (NULL::ARRAY(5)), COALESCE(NULL::ARRAY(5), 1)          … then the ')' closing the parenthesis around the cast
 *   NULL::ARRAY(5) AS a, NULL::OBJECT(5) AS b              … and a later cast's own lines
 *   NULL::OBJECT(a), CAST(… AS OBJECT(a)), … (a OBJECT(a))  the ')' once: a field wants its type
 *   NULL::OBJECT(a 5)                                      the '5'
 *   NULL::ARRAY(NUMBER, 5), NULL::MAP(VARCHAR, NUMBER, 5)  the ',' and then the structured type's ')'
 *   … (a ARRAY(NUMBER, 5)), … (a MAP(VARCHAR, NUMBER, 5))  the ',', the '5' no name may be, then the ')' after the
 *                                                          column list the type's own ')' ends
 *   NULL::ARRAY(NUMBER NUMBER)                             the second 'NUMBER'
 *   NULL::MAP(VARCHAR), CAST(… AS MAP()), NULL::VECTOR(INT)  the ')' alone
 *   CAST(… AS MAP(VARCHAR)), … (a MAP(VARCHAR)), … (a VECTOR(INT))   the ')' and then the one after it
 *   … (a MAP()), … (a VECTOR())                            the ')' alone
 * </pre>
 *
 * <p>A list of numbers after an ARRAY or an OBJECT in a column is judged by {@link TypeParameterLines}.
 */
final class StructuredTypeLines {

    private static final IntervalSet NAMES =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private static final IntervalSet TYPE_WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_dataTypeName]);

    /** Where a structured type stands: after {@code ::}, as a CAST's or a TRY_CAST's target, as a column's type. */
    private static final int COLONS = 0;
    private static final int CAST = 1;
    private static final int COLUMN = 2;

    private StructuredTypeLines() {
    }

    /**
     * The lines of a statement whose first fault stands in a structured type's parentheses, at or after the parse's
     * own first fault — or in the column whose definition the parse refuses at its name — or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names, or -1 for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = spoken.size() - 1;
        for (int i = 0; i < spoken.size() && faultIndex >= 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
        }
        for (int i = 1; i + 2 < spoken.size() && i - 1 <= fault; i++) {
            if (!isStructured(spoken.get(i)) || spoken.get(i + 1).getType() != FrostlakeLexer.LPAREN) {
                continue;
            }
            // A column's definition fails as a whole, and the parse names its first token: the column's name.
            final int context = contextOf(spoken, i);
            if (context < 0 || i + 1 > fault && context != COLUMN) {
                continue;
            }
            final List<Token> lines = judged(spoken, i, context);
            if (lines != null) {
                final List<String> sentences = new ArrayList<>();
                for (final Token line : lines) {
                    sentences.add(SyntaxErrorListener.sentence(line));
                }
                return sentences;
            }
        }
        return null;
    }

    /** The lines of the structured type at {@code at}, or null when its parentheses hold no fault read here. */
    private static List<Token> judged(final List<Token> spoken, final int at, final int context) {
        final int word = spoken.get(at).getType();
        final int open = at + 1;
        final Token head = tokenAt(spoken, open + 1);
        if (head.getType() == Token.EOF) {
            return null;
        }
        if ((word == FrostlakeLexer.ARRAY || word == FrostlakeLexer.OBJECT) && isLiteral(head)) {
            return context == COLUMN ? null : firstTokenLines(spoken, at, context);
        }
        if (word == FrostlakeLexer.OBJECT) {
            final Token after = tokenAt(spoken, open + 2);
            return isName(head) && (after.getType() == FrostlakeLexer.RPAREN || isLiteral(after)) ? only(after) : null;
        }
        if (word == FrostlakeLexer.MAP || word == FrostlakeLexer.VECTOR) {
            if (head.getType() == FrostlakeLexer.RPAREN) {
                return only(head);
            }
        }
        if (!isTypeWord(head)) {
            return null;
        }
        final int past = pastType(spoken, open + 1, word != FrostlakeLexer.MAP);
        if (past < 0) {
            return null;
        }
        final Token next = tokenAt(spoken, past);
        if (word == FrostlakeLexer.ARRAY) {
            if (next.getType() == FrostlakeLexer.COMMA) {
                return overflow(spoken, past, open, context);
            }
            return isTypeWord(next) && next.getType() != FrostlakeLexer.NOT ? only(next) : null;
        }
        if (next.getType() == FrostlakeLexer.RPAREN) {
            // A MAP's value, a VECTOR's dimension is missing: the ')', and the CAST's or the column list's after it.
            final List<Token> lines = only(next);
            if (context != COLONS && tokenAt(spoken, past + 1).getType() == FrostlakeLexer.RPAREN) {
                lines.add(tokenAt(spoken, past + 1));
            }
            return lines;
        }
        if (word != FrostlakeLexer.MAP || next.getType() != FrostlakeLexer.COMMA
                || !isTypeWord(tokenAt(spoken, past + 1))) {
            return null;
        }
        final int pastValue = pastType(spoken, past + 1, true);
        return pastValue >= 0 && tokenAt(spoken, pastValue).getType() == FrostlakeLexer.COMMA
            ? overflow(spoken, pastValue, open, context) : null;
    }

    /**
     * An ARRAY's or an OBJECT's first token that begins no element type, named twice — and a list of numbers' own
     * ')' when more of it stood before — then read on: the ')' closing a parenthesis around the cast, or else the
     * lines of a later such cast.
     */
    private static List<Token> firstTokenLines(final List<Token> spoken, final int at, final int context) {
        final int open = at + 1;
        final Token head = spoken.get(open + 1);
        final int close = closing(spoken, open);
        final List<Token> lines = new ArrayList<>();
        lines.add(head);
        lines.add(head);
        if (close > open + 2 && numbersUpTo(spoken, open + 1, close)) {
            lines.add(spoken.get(close));
        }
        if (close < 0) {
            return lines;
        }
        int depth = context == CAST ? 1 : 0;
        for (int i = close + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                break;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth < 0) {
                lines.add(spoken.get(i));
                return lines;
            } else if (depth == 0 && isStructured(spoken.get(i)) && i + 2 < spoken.size()
                    && spoken.get(i + 1).getType() == FrostlakeLexer.LPAREN && isLiteral(spoken.get(i + 2))) {
                final int later = contextOf(spoken, i);
                if (later == COLONS || later == CAST) {
                    lines.addAll(firstTokenLines(spoken, i, later));
                    return lines;
                }
            }
        }
        return lines;
    }

    /**
     * A comma where the structured type takes no more: in a cast the comma and then the type's own ')'; in a column
     * the comma read as the one between two columns, the token after it when it is no name, and then what follows
     * the ')' that ends the column list.
     */
    private static List<Token> overflow(final List<Token> spoken, final int comma, final int open, final int context) {
        final List<Token> lines = only(spoken.get(comma));
        if (context != COLUMN) {
            final int close = closing(spoken, open);
            if (close > comma) {
                lines.add(spoken.get(close));
            }
            return lines;
        }
        int at = comma + 1;
        if (isLiteral(tokenAt(spoken, at))) {
            lines.add(tokenAt(spoken, at));
        }
        while (at < spoken.size() && spoken.get(at).getType() != FrostlakeLexer.RPAREN
                && spoken.get(at).getType() != Token.EOF) {
            at++;
        }
        if (at + 1 < spoken.size()) {
            final int after = spoken.get(at + 1).getType();
            if (after == FrostlakeLexer.RPAREN || after == FrostlakeLexer.COMMA || after == FrostlakeLexer.NOT) {
                lines.add(spoken.get(at + 1));
            }
        }
        return lines;
    }

    /**
     * Where the structured type at {@code at} stands, or -1: after {@code ::}, after the AS of a CAST or a TRY_CAST
     * still open, or after a column's name in a table's column list.
     */
    private static int contextOf(final List<Token> spoken, final int at) {
        final int before = spoken.get(at - 1).getType();
        if (before == FrostlakeLexer.DOUBLE_COLON) {
            return COLONS;
        }
        if (before == FrostlakeLexer.AS) {
            return insideOpenCast(spoken, at - 1) ? CAST : -1;
        }
        if (at < 3 || !isName(spoken.get(at - 1)) || spoken.get(0).getType() != FrostlakeLexer.CREATE) {
            return -1;
        }
        final int lead = spoken.get(at - 2).getType();
        if (lead != FrostlakeLexer.LPAREN && lead != FrostlakeLexer.COMMA) {
            return -1;
        }
        int opens = 0;
        int table = -1;
        for (int i = 0; i < at - 1; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                opens++;
            } else if (type == FrostlakeLexer.RPAREN) {
                opens--;
            } else if (type == FrostlakeLexer.TABLE && table < 0) {
                table = i;
            } else if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
        }
        if (opens != 1 || table < 1) {
            return -1;
        }
        for (int i = 1; i < table; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.EXTERNAL || type == FrostlakeLexer.DYNAMIC) {
                return -1;
            }
        }
        return COLUMN;
    }

    /** Whether an AS stands directly inside the parentheses of a CAST or a TRY_CAST nothing has closed. */
    private static boolean insideOpenCast(final List<Token> spoken, final int as) {
        int depth = 0;
        for (int i = as - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    final int word = spoken.get(i - 1).getType();
                    return word == FrostlakeLexer.CAST || word == FrostlakeLexer.TRY_CAST;
                }
                depth--;
            }
        }
        return false;
    }

    /** The index past a type written from {@code at} — its words, its parentheses, a NOT NULL after it — or -1. */
    private static int pastType(final List<Token> spoken, final int at, final boolean notNull) {
        int past = at + 1;
        final int word = tokenAt(spoken, at).getType();
        if (word == FrostlakeLexer.DOUBLE && tokenAt(spoken, past).getType() == FrostlakeLexer.PRECISION
                || tokenAt(spoken, past).getType() == FrostlakeLexer.VARYING) {
            past++;
        }
        if (tokenAt(spoken, past).getType() == FrostlakeLexer.LPAREN) {
            final int close = closing(spoken, past);
            if (close < 0) {
                return -1;
            }
            past = close + 1;
        }
        if (notNull && tokenAt(spoken, past).getType() == FrostlakeLexer.NOT
                && tokenAt(spoken, past + 1).getType() == FrostlakeLexer.NULL) {
            past += 2;
        }
        return past < spoken.size() ? past : -1;
    }

    /** The index of the ')' closing the '(' at {@code open}, or -1 when the statement ends first. */
    private static int closing(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            } else if (type == FrostlakeLexer.SEMI || type == Token.EOF) {
                return -1;
            }
        }
        return -1;
    }

    /** Whether the tokens from {@code from} up to {@code close} are numbers, signs and commas alone. */
    private static boolean numbersUpTo(final List<Token> spoken, final int from, final int close) {
        for (int i = from; i < close; i++) {
            final int type = spoken.get(i).getType();
            if (type != FrostlakeLexer.INTEGER_LITERAL && type != FrostlakeLexer.MINUS
                    && type != FrostlakeLexer.COMMA) {
                return false;
            }
        }
        return true;
    }

    /** The token at an index, or the end of input past the last. */
    private static Token tokenAt(final List<Token> spoken, final int index) {
        return spoken.get(Math.min(index, spoken.size() - 1));
    }

    private static List<Token> only(final Token token) {
        final List<Token> lines = new ArrayList<>();
        lines.add(token);
        return lines;
    }

    private static boolean isStructured(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.ARRAY || type == FrostlakeLexer.OBJECT || type == FrostlakeLexer.MAP
            || type == FrostlakeLexer.VECTOR;
    }

    private static boolean isLiteral(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.MINUS;
    }

    private static boolean isName(final Token token) {
        return NAMES.contains(token.getType()) || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    private static boolean isTypeWord(final Token token) {
        return TYPE_WORDS.contains(token.getType());
    }
}
