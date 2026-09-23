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

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import java.util.Arrays;
import java.util.List;

/**
 * A comma inside a parenthesized FROM group, which holds one source and the joins after it and never a list. Live
 * refuses the group's first token and then its closing parenthesis, where this parser refused the comma itself (all
 * live-verified):
 *
 * <pre>
 *   SELECT * FROM (t a, t b)                                  unexpected 't' at 15, then ')' at 23
 *   SELECT COUNT(*) FROM (t a JOIN t b ON a.a = b.a, t c)     unexpected 't' at 22, then ')' at 52
 * </pre>
 */
final class FromGroupComma {

    private FromGroupComma() {
    }

    /**
     * The two tokens live refuses for a comma in a FROM group, the group's first token and its closing parenthesis,
     * or null when the comma stands anywhere else.
     *
     * @param recognizer the parser that refused the comma
     * @param comma      the refused token
     * @return the first token and the closing parenthesis, or null
     */
    static List<Token> refusal(final Recognizer<?, ?> recognizer, final Token comma) {
        if (!(recognizer instanceof Parser) || comma.getType() != FrostlakeLexer.COMMA) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        final TokenStream stream = parser.getInputStream();
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        for (ParserRuleContext up = parser.getContext(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.TableSourceContext) {
                final FrostlakeParser.TableSourceContext source = (FrostlakeParser.TableSourceContext) up;
                if (source.LPAREN() == null || source.tableReference() == null) {
                    continue;
                }
                final int open = source.LPAREN().getSymbol().getTokenIndex();
                final Token close = closingOf(stream, open, comma.getTokenIndex());
                if (close == null) {
                    return null;
                }
                return Arrays.asList(spokenAfter(stream, open), close);
            }
        }
        return null;
    }

    /**
     * The parenthesis closing the one at {@code open}, provided {@code comma} stands directly inside the pair and not
     * in a bracket of its own; null otherwise.
     */
    private static Token closingOf(final TokenStream stream, final int open, final int comma) {
        int depth = 0;
        for (int i = open; i < stream.size(); i++) {
            final Token token = stream.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == Token.EOF) {
                return null;
            }
            if (token.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return i > comma ? token : null;
                }
            } else if (i == comma && depth != 1) {
                return null;
            }
        }
        return null;
    }

    /** The first token on the default channel after the one at {@code index}. */
    private static Token spokenAfter(final TokenStream stream, final int index) {
        for (int i = index + 1; i < stream.size(); i++) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
