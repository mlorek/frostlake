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

package dev.frostlake.executor;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a multi-statement SQL SCRIPT into its top-level statements for clients that execute and
 * report per statement (the HTTP server's init script, the console's script runner).
 *
 * <p>The GRAMMAR does the splitting: each parsed flowChain (a statement, or a {@code ->>} chain
 * that must stay together) is one piece, so a semicolon inside a string literal, a $$-quoted body
 * or a BEGIN...END block never splits — the character-level splitters this replaces mis-grouped
 * everything after {@code SELECT '$$';} and cut a multi-line literal at a line-trailing ';'. When
 * the script does not parse whole, falls back to a LEXER-token split at SEMI (literals are single
 * tokens even then), so per-statement execution can still surface the offending statement's own
 * error.
 */
public final class SqlScriptSplitter {

    private SqlScriptSplitter() {
    }

    public static List<String> split(final String script) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(script));
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        final boolean[] failed = new boolean[1];
        final BaseErrorListener silent = new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol,
                    final int line, final int charPositionInLine, final String msg,
                    final RecognitionException e) {
                failed[0] = true;
            }
        };
        lexer.removeErrorListeners();
        lexer.addErrorListener(silent);
        parser.removeErrorListeners();
        parser.addErrorListener(silent);
        final FrostlakeParser.SqlScriptContext tree = parser.sqlScript();
        if (!failed[0] && !tree.flowChain().isEmpty()) {
            final List<String> statements = new ArrayList<>();
            for (final FrostlakeParser.FlowChainContext chain : tree.flowChain()) {
                String piece = script.substring(chain.getStart().getStartIndex(),
                    chain.getStop().getStopIndex() + 1).trim();
                // A statement's optional trailing ';' belongs to its parse span; the pieces are
                // re-executed individually, so drop it (as both replaced splitters did).
                if (piece.endsWith(";")) {
                    piece = piece.substring(0, piece.length() - 1).trim();
                }
                statements.add(piece);
            }
            return statements;
        }
        // Unparseable script: lexer-token split at SEMI, so each piece can report its own error.
        final FrostlakeLexer relexer = new FrostlakeLexer(CharStreams.fromString(script));
        relexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(relexer);
        stream.fill();
        final List<String> statements = new ArrayList<>();
        int startIndex = 0;
        for (final Token token : stream.getTokens()) {
            if (token.getType() == FrostlakeLexer.SEMI) {
                final String piece = script.substring(startIndex, token.getStartIndex()).trim();
                if (!piece.isEmpty()) {
                    statements.add(piece);
                }
                startIndex = token.getStopIndex() + 1;
            }
        }
        final String tail = script.substring(Math.min(startIndex, script.length())).trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }
}
