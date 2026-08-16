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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeLexer;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A definition may not carry an unnamed bind. A {@code ?} written into a view's query or a UDF's body is
 * refused when the object is CREATED — "Bind variables not allowed in view and UDF definitions." — where
 * the same {@code ?} in an ordinary statement is the unset-bind refusal instead. A stored PROCEDURE's
 * body is not held to this: the account creates one whose block writes {@code ?} (live-verified).
 *
 * <p>The refusal points at the {@code ?}: in a view, at its place in the whole statement; in a routine
 * body, at its place within the BODY, counted from one — the frame every body-compilation refusal uses.
 */
public final class BindsInDefinition {

    private static final String REFUSAL = "Bind variables not allowed in view and UDF definitions.";

    private BindsInDefinition() {
    }

    /**
     * Refuse a {@code ?} anywhere in a definition's parse tree.
     *
     * @param definition the view's query, or any part of a statement that defines an object
     */
    public static void reject(final ParseTree definition) {
        if (definition == null) {
            return;
        }
        if (definition instanceof TerminalNode) {
            final Token token = ((TerminalNode) definition).getSymbol();
            if (token.getType() == FrostlakeLexer.QUESTION) {
                throw new RuntimeException(SqlCompilationError.at(token.getLine(),
                    token.getCharPositionInLine(), REFUSAL));
            }
            return;
        }
        for (int i = 0; i < definition.getChildCount(); i++) {
            reject(definition.getChild(i));
        }
    }

    /**
     * Refuse a {@code ?} anywhere in a routine's body text. The body is LEXED rather than scanned, so a
     * question mark inside a string literal or a comment is left alone.
     *
     * @param body the body as written, already stripped of its delimiters
     */
    public static void rejectInBody(final String body) {
        if (body == null || body.indexOf('?') < 0) {
            return;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> tokens = stream.getTokens();
        for (final Token token : tokens) {
            if (token.getType() == FrostlakeLexer.QUESTION) {
                // One-based within the body, which is where a body's own refusals are counted from.
                throw new RuntimeException(SqlCompilationError.at(token.getLine(),
                    token.getCharPositionInLine() + 1, REFUSAL));
            }
        }
    }
}
