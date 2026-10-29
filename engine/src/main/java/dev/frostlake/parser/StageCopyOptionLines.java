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
 * The lines the account reports for a fault inside a stage's {@code COPY_OPTIONS = (…)} list, and inside a table's
 * {@code STAGE_COPY_OPTIONS = (…)} (live-verified):
 *
 * <pre>
 *   COPY_OPTIONS = (PURGE, SIZE_LIMIT = 5)          '=', then the list's ')'
 *   COPY_OPTIONS = (PURGE NOPE)                     'NOPE' twice, then the list's ')'
 *   COPY_OPTIONS = (ON_ERROR = SKIP_FILE_10%)       '%' twice
 * </pre>
 *
 * <p>A list of bare keys takes no value, so its {@code =} is refused and the list's parenthesis after it; a word after
 * a bare key is named twice before that parenthesis; a {@code %} in a value is named twice.
 */
final class StageCopyOptionLines {

    private StageCopyOptionLines() {
    }

    /**
     * The lines for a text whose first fault stands in a {@code COPY_OPTIONS = (…)} list, or null.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 3 || fault >= spoken.size()) {
            return null;
        }
        int open = -1;
        int depth = 0;
        boolean comma = false;
        for (int i = fault - 1; i >= 2 && open < 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth > 0) {
                    depth--;
                } else if (spoken.get(i - 1).getType() == FrostlakeLexer.EQ
                        && (spoken.get(i - 2).getType() == FrostlakeLexer.COPY_OPTIONS
                            || ClauseWords.isWord(spoken.get(i - 2), "STAGE_COPY_OPTIONS"))) {
                    open = i;
                } else {
                    return null;
                }
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                comma = true;
            } else if (type == FrostlakeLexer.SEMI) {
                return null;
            }
        }
        if (open < 0) {
            return null;
        }
        final Token wrong = spoken.get(fault);
        final List<String> lines = new ArrayList<String>();
        if (wrong.getType() == FrostlakeLexer.PERCENT) {
            lines.add(SyntaxErrorListener.sentence(wrong));
            lines.add(SyntaxErrorListener.sentence(wrong));
            return lines;
        }
        final Token close = closing(spoken, fault);
        if (close == null) {
            return null;
        }
        if (wrong.getType() == FrostlakeLexer.EQ && comma) {
            lines.add(SyntaxErrorListener.sentence(wrong));
            lines.add(SyntaxErrorListener.sentence(close));
            return lines;
        }
        final int before = spoken.get(fault - 2).getType();
        if (SyntaxErrorListener.isNameLike(wrong) && fault - 1 > open
                && SyntaxErrorListener.isNameLike(spoken.get(fault - 1))
                && (before == FrostlakeLexer.LPAREN || before == FrostlakeLexer.COMMA)) {
            lines.add(SyntaxErrorListener.sentence(wrong));
            lines.add(SyntaxErrorListener.sentence(wrong));
            lines.add(SyntaxErrorListener.sentence(close));
            return lines;
        }
        return null;
    }

    /** The parenthesis that closes the list the token at {@code from} stands in, or null. */
    private static Token closing(final List<Token> spoken, final int from) {
        int depth = 0;
        for (int i = from + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return spoken.get(i);
                }
                depth--;
            } else if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return null;
            }
        }
        return null;
    }
}
