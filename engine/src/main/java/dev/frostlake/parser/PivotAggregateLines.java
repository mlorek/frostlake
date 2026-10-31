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

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines live reports for a PIVOT that lists more than one aggregate. The first fault is the comma after the first
 * aggregate, and live's recovery reads on through the rest of the clause (live-verified):
 *
 * <ul>
 *   <li>a word after a comma is read as the alias it could be, so a bracket that opens right after it is refused,
 *       {@code COUNT(amt)} at its {@code (}; a token after a comma that is no word is refused where it stands;</li>
 *   <li>the rest of the item is read past, a comma inside it included, up to the clause's FOR, where the column's
 *       first token is refused, or up to the bracket that closes the clause, which is refused where no FOR came;</li>
 *   <li>nothing after the FOR column is reported, and a FOR right after a comma is read on as written.</li>
 * </ul>
 *
 * <p>{@code PIVOT (SUM(amt) AS total, COUNT(amt) AS n FOR mon IN ('JAN'))} is refused at the comma, at COUNT's
 * bracket and at {@code mon}; {@code PIVOT (SUM(amt), amt FOR mon IN ('JAN'))} at the comma and at {@code mon}.
 */
final class PivotAggregateLines {

    /** After a comma: a word is read as an alias, anything else refused. */
    private static final int AFTER_COMMA = 0;

    /** After the word a comma led to: a bracket is refused, FOR opens the column, a comma starts over. */
    private static final int AFTER_WORD = 1;

    /** Reading past the rest of an item. */
    private static final int READING_PAST = 2;

    private PivotAggregateLines() {
    }

    /**
     * The lines for a statement whose first fault is the comma after a PIVOT's first aggregate, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0 && faultIndex >= 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 0 || type(spoken, fault) != FrostlakeLexer.COMMA || !followsFirstAggregate(spoken, fault)) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        int state = AFTER_COMMA;
        int depth = 0;
        for (int i = fault + 1; !ends(spoken, i); i++) {
            final int type = type(spoken, i);
            if (state == AFTER_COMMA) {
                if (type == FrostlakeLexer.FOR && depth == 0) {
                    return lines;
                }
                if (isName(type)) {
                    state = AFTER_WORD;
                    continue;
                }
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                state = READING_PAST;
                if (type == FrostlakeLexer.RPAREN && depth == 0) {
                    return lines;
                }
                depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
                continue;
            }
            if (state == AFTER_WORD && type == FrostlakeLexer.LPAREN) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                depth++;
                state = READING_PAST;
                continue;
            }
            state = READING_PAST;
            if (type == FrostlakeLexer.FOR && depth == 0) {
                return ends(spoken, i + 1) ? null : withLine(lines, spoken.get(i + 1));
            }
            if (type == FrostlakeLexer.COMMA) {
                state = AFTER_COMMA;
            } else if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return withLine(lines, spoken.get(i));
                }
                depth--;
            }
        }
        return null;
    }

    private static List<String> withLine(final List<String> lines, final Token token) {
        lines.add(SyntaxErrorListener.sentence(token));
        return lines;
    }

    /**
     * Whether the comma at {@code comma} follows a PIVOT's first aggregate inside the clause's bracket: a call, then an
     * alias with or without AS, or none.
     */
    private static boolean followsFirstAggregate(final List<Token> spoken, final int comma) {
        int depth = 0;
        int open = -1;
        for (int i = comma - 1; i >= 0 && open < 0; i--) {
            final int type = type(spoken, i);
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    open = i;
                } else {
                    depth--;
                }
            } else if (type == FrostlakeLexer.SEMI) {
                return false;
            }
        }
        if (open < 1 || type(spoken, open - 1) != FrostlakeLexer.PIVOT || !isName(type(spoken, open + 1))
                || type(spoken, open + 2) != FrostlakeLexer.LPAREN) {
            return false;
        }
        int close = -1;
        depth = 0;
        for (int i = open + 2; i < comma && close < 0; i++) {
            if (type(spoken, i) == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type(spoken, i) == FrostlakeLexer.RPAREN && --depth == 0) {
                close = i;
            }
        }
        if (close < 0) {
            return false;
        }
        final int alias = close + 1 < comma && type(spoken, close + 1) == FrostlakeLexer.AS ? close + 2 : close + 1;
        return alias == comma || alias + 1 == comma && isName(type(spoken, alias));
    }

    /** Whether a token of this type can be a name. */
    private static boolean isName(final int type) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(type);
    }

    /** Whether the statement ends at {@code at}: the end of input, a semicolon, or no token at all. */
    private static boolean ends(final List<Token> spoken, final int at) {
        final int type = type(spoken, at);
        return type == Token.EOF || type == FrostlakeLexer.SEMI;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
