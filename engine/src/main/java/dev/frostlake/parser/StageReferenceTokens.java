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
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;

/**
 * A stage reference as the account's lexer reads it: the '@' and every character after it up to a delimiter make ONE
 * token, where this lexer reads the '@' and the tokens after it apart. {@code @s}, {@code @~}, {@code @%t1},
 * {@code @s.t/x.gz}, {@code @s=1}, {@code @s:x}, {@code @s[1]}, {@code @TABLE} and {@code @s@t} are each one token,
 * named whole in a syntax error's line, and the characters of a comment written straight after the name belong to it
 * too ({@code @s--x} is one token). The delimiters are white space, '(', ')', ',', a single quote, '&lt;', '&gt;', '|'
 * and a '~' past the first character — {@code @~s} is one token, {@code @s~x} is {@code @s} and more — and a lone '@'
 * is a token of its own (all live-verified).
 */
final class StageReferenceTokens {

    private StageReferenceTokens() {
    }

    /**
     * The tokens with each stage reference joined into one '@' token that spans and spells it whole, or null when
     * where a reference ends cannot be told from these tokens: a comment straight after it, a ';', or a token that may
     * hold a delimiter or runs across one (a quoted word, a dollar-quoted string, '=&gt;', '-&gt;', '&lt;&gt;').
     *
     * @param tokens   the tokens, in order, ending with the end of the input
     * @param comments where each comment this lexer skipped starts, as character indexes
     * @return the joined tokens, or null
     */
    static List<Token> joined(final List<Token> tokens, final List<Integer> comments) {
        final List<Token> joined = new ArrayList<>();
        int index = 0;
        while (index < tokens.size()) {
            final Token at = tokens.get(index);
            index++;
            if (at.getType() != FrostlakeLexer.AT) {
                joined.add(at);
                continue;
            }
            final StringBuilder spelling = new StringBuilder(at.getText());
            Token last = at;
            while (index < tokens.size()) {
                final Token next = tokens.get(index);
                if (next.getStartIndex() != last.getStopIndex() + 1) {
                    if (comments.contains(last.getStopIndex() + 1)) {
                        return null;
                    }
                    break;
                }
                final int type = next.getType();
                if (delimits(type) || type == FrostlakeLexer.TILDE && last != at) {
                    break;
                }
                if (unsettled(type)) {
                    return null;
                }
                spelling.append(next.getText());
                last = next;
                index++;
            }
            final CommonToken reference = new CommonToken(at);
            reference.setText(spelling.toString());
            reference.setStopIndex(last.getStopIndex());
            joined.add(reference);
        }
        return joined;
    }

    /** Whether a token that stands straight after a reference's characters begins with a delimiter. */
    private static boolean delimits(final int type) {
        return type == Token.EOF || type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.RPAREN
            || type == FrostlakeLexer.COMMA || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.LT
            || type == FrostlakeLexer.LTE || type == FrostlakeLexer.GT || type == FrostlakeLexer.GTE
            || type == FrostlakeLexer.PIPE_PIPE;
    }

    /** Whether a token may hold a delimiter or run across one, so that where the reference ends is not known. */
    private static boolean unsettled(final int type) {
        return type == FrostlakeLexer.SEMI || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.ARROW
            || type == FrostlakeLexer.THIN_ARROW || type == FrostlakeLexer.FLOW_ARROW
            || type == FrostlakeLexer.QUOTED_IDENTIFIER || type == FrostlakeLexer.DOLLAR_QUOTED_STRING
            || type == FrostlakeLexer.HEX_LITERAL || type == FrostlakeLexer.FILE_URL
            || type == FrostlakeLexer.REVOKE_CURRENT_GRANTS || type == FrostlakeLexer.RESUME_IF_SUSPENDED
            || type == FrostlakeLexer.ABORT_ALL_QUERIES;
    }
}
