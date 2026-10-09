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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * A routine's required argument written after an optional one, judged ahead of a word the parse could not read as
 * a data type: {@code CREATE FUNCTION f(x INT DEFAULT 1, y NOSUCHTYPE) …} is refused for the order of its
 * arguments, at the required one's name, and not for the type — whichever argument the unknown type is, and when
 * it is the result's too (live-verified). A statement that parses is judged for the same order where it runs.
 */
public final class RoutineArgumentOrder {

    private RoutineArgumentOrder() {
    }

    /**
     * Refuse a routine statement whose only syntax fault is an unknown type word and whose arguments are out of
     * order.
     *
     * @param tokens   the token stream the script was parsed from
     * @param listener the parse's error listener
     */
    public static void requireBefore(final TokenStream tokens, final SyntaxErrorListener listener) {
        if (tokens == null || !listener.hasErrors() || !listener.reportsUnsupportedType()) {
            return;
        }
        final List<Token> spoken = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                spoken.add(tokens.get(i));
            }
        }
        final int open = argumentListStart(spoken);
        if (open < 0) {
            return;
        }
        Token optional = null;
        Token name = null;
        boolean defaulted = false;
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            final int type = token.getType();
            if (type == Token.EOF) {
                return;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
                continue;
            }
            final boolean ends = type == FrostlakeLexer.RPAREN && depth == 1;
            if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
            if (ends || depth == 1 && type == FrostlakeLexer.COMMA) {
                if (name != null && defaulted) {
                    optional = name;
                } else if (name != null && optional != null) {
                    throw new RuntimeException(SqlCompilationError.at(name.getLine(), name.getCharPositionInLine(),
                        "required argument " + SqlIdentifiers.canonicalText(name.getText())
                            + " cannot follow optional argument " + SqlIdentifiers.canonicalText(optional.getText())));
                }
                name = null;
                defaulted = false;
                if (ends) {
                    return;
                }
                continue;
            }
            if (depth != 1) {
                continue;
            }
            if (name == null) {
                name = token;
            } else if (type == FrostlakeLexer.DEFAULT || type == FrostlakeLexer.COLON_EQ) {
                defaulted = true;
            }
        }
    }

    /** The index of the parenthesis opening a CREATE FUNCTION's or CREATE PROCEDURE's argument list, or -1. */
    private static int argumentListStart(final List<Token> spoken) {
        if (spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.CREATE) {
            return -1;
        }
        boolean routine = false;
        for (int i = 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                return routine ? i : -1;
            }
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return -1;
            }
            routine = routine || type == FrostlakeLexer.FUNCTION || type == FrostlakeLexer.PROCEDURE;
        }
        return -1;
    }
}
