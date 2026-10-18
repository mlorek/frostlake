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

import java.util.ArrayList;
import java.util.List;

/**
 * The lines of a SQL procedure's body whose first fault is a comma inside a CAST's or a TRY_CAST's parentheses, which
 * the account refuses as the procedure is created, in the body's own frame (all live-verified):
 *
 * <pre>
 *   BEGIN SELECT UPPER(CAST(1, 2)); …        ',', then the ')' after the cast's: the statement's own lines, read
 *                                            as it would be read alone, placed where it stands in the body
 *   BEGIN RETURN CAST(1, 2); …               ',' alone, and so in a LET's or an assignment's value
 * </pre>
 */
public final class BodyCastCommaLines {

    private BodyCastCommaLines() {
    }

    /**
     * The body's lines, or null when the statement its first fault stands in holds no cast's comma.
     *
     * @param body       the body's text
     * @param spoken     its default-channel tokens, end of input included
     * @param faultStart where the token the parse's first fault names begins, a character index of the body
     * @return the lines, or null
     */
    public static List<String> lines(final String body, final List<Token> spoken, final int faultStart) {
        int named = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getStartIndex() == faultStart && spoken.get(i).getType() != Token.EOF) {
                named = i;
                break;
            }
        }
        if (named < 1) {
            return null;
        }
        // The statement the first fault stands in, and a cast's comma in it.
        final int start = statementStart(spoken, named);
        final int end = statementEnd(spoken, named - 1);
        final int fault = start < 0 || end < 0 ? -1 : castComma(spoken, start, end);
        if (fault < 0) {
            return null;
        }
        final Token first = spoken.get(start);
        final int firstType = first.getType();
        final boolean value = firstType == FrostlakeLexer.RETURN || firstType == FrostlakeLexer.LET
            || start + 1 < spoken.size() && spoken.get(start + 1).getType() == FrostlakeLexer.COLON_EQ;
        final List<String> lines = new ArrayList<>();
        if (value) {
            // A block's own value names the comma alone.
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
            return lines;
        }
        final String statement = body.substring(first.getStartIndex(), spoken.get(end).getStartIndex());
        final List<String> alone = SyntaxErrorListener.linesReportedFor(statement, true);
        if (alone == null || alone.isEmpty()) {
            return null;
        }
        for (final String line : alone) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
            if (at == null) {
                return null;
            }
            final int placedLine = first.getLine() + at[0] - 1;
            final int placedColumn = at[0] == 1 ? first.getCharPositionInLine() + at[1] : at[1];
            lines.add(line.replaceFirst("line \\d+ at position \\d+",
                "line " + placedLine + " at position " + placedColumn));
        }
        return lines;
    }

    /** The first comma written inside a CAST's or a TRY_CAST's own parentheses between two tokens, or -1. */
    private static int castComma(final List<Token> spoken, final int from, final int to) {
        for (int i = from; i + 1 < to; i++) {
            final int type = spoken.get(i).getType();
            if ((type == FrostlakeLexer.CAST || type == FrostlakeLexer.TRY_CAST)
                    && spoken.get(i + 1).getType() == FrostlakeLexer.LPAREN) {
                int depth = 0;
                for (int j = i + 2; j < to; j++) {
                    final int inner = spoken.get(j).getType();
                    if (inner == FrostlakeLexer.LPAREN) {
                        depth++;
                    } else if (inner == FrostlakeLexer.RPAREN) {
                        if (depth == 0) {
                            break;
                        }
                        depth--;
                    } else if (inner == FrostlakeLexer.COMMA && depth == 0) {
                        return j;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * The first token of the statement a token stands in: the one after a ';' or a BEGIN before it, or after a THEN,
     * an ELSE, a DO, a LOOP or a REPEAT that a statement follows — a CASE's THEN, which a value follows, is no
     * boundary.
     */
    private static int statementStart(final List<Token> spoken, final int at) {
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.BEGIN) {
                return i + 1;
            }
            if ((type == FrostlakeLexer.THEN || type == FrostlakeLexer.ELSE || type == FrostlakeLexer.DO
                    || type == FrostlakeLexer.LOOP || type == FrostlakeLexer.REPEAT) && i + 1 < spoken.size()
                    && opensAStatement(spoken.get(i + 1).getType())) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Whether a token is a word a statement of a block begins with. */
    private static boolean opensAStatement(final int type) {
        return type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WITH || type == FrostlakeLexer.INSERT
            || type == FrostlakeLexer.UPDATE || type == FrostlakeLexer.DELETE || type == FrostlakeLexer.MERGE
            || type == FrostlakeLexer.CREATE || type == FrostlakeLexer.ALTER || type == FrostlakeLexer.DROP
            || type == FrostlakeLexer.CALL || type == FrostlakeLexer.RETURN || type == FrostlakeLexer.LET
            || type == FrostlakeLexer.IF || type == FrostlakeLexer.FOR || type == FrostlakeLexer.WHILE
            || type == FrostlakeLexer.RAISE || type == FrostlakeLexer.EXECUTE || type == FrostlakeLexer.OPEN
            || type == FrostlakeLexer.FETCH || type == FrostlakeLexer.CLOSE || type == FrostlakeLexer.TRUNCATE
            || type == FrostlakeLexer.BEGIN;
    }

    /** The ';' that ends the statement a token stands in, or -1. */
    private static int statementEnd(final List<Token> spoken, final int at) {
        for (int i = at + 1; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                return i;
            }
            if (spoken.get(i).getType() == Token.EOF) {
                return -1;
            }
        }
        return -1;
    }
}
