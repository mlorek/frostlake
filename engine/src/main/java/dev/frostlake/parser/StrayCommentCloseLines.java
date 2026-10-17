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

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@code *}{@code /} that closes no comment is a star and a slash: the slash is refused where it stands, and when
 * the text ends after it the account names its end too — {@code SELECT 1 *}{@code /} is {@code unexpected '/'} at 10,
 * then {@code unexpected '<EOF>'} at 11 (live-verified). With a token after the slash the slash's line stands alone.
 */
public final class StrayCommentCloseLines {

    private StrayCommentCloseLines() {
    }

    /**
     * Refuse a text that ends with a star and a slash written together, its first fault the slash, with the slash's
     * line and then the end's.
     *
     * @param sql      the script's source text
     * @param parsed   the parse's tokens
     * @param listener the parse's error listener
     */
    public static void requireBefore(final String sql, final CommonTokenStream parsed,
                                     final SyntaxErrorListener listener) {
        if (sql == null || !listener.hasErrors()) {
            return;
        }
        parsed.fill();
        final Token end = parsed.get(parsed.size() - 1);
        final Token slash = previousSpoken(parsed, end.getTokenIndex());
        final Token named = slash == null ? null : stackedAfter(parsed, slash);
        if (named == null || firstFault(sql) != slash.getStartIndex()) {
            return;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(slash));
        lines.add(SyntaxErrorListener.sentence(named));
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }

    /** Where a fresh parse of the text meets its first fault, or -1. */
    private static int firstFault(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        ColdPrediction.arm(parser);
        parser.removeErrorListeners();
        final int[] at = {-1};
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (at[0] < 0 && offendingSymbol instanceof Token) {
                    at[0] = ((Token) offendingSymbol).getStartIndex();
                }
            }
        });
        parser.sqlScript();
        return at[0];
    }

    /**
     * The end of the text, named after a refused slash that a star stands straight before, or null.
     *
     * @param tokens  the parse's tokens
     * @param refused the refused token
     * @return the end-of-text token, or null where nothing is stacked
     */
    static Token stackedAfter(final TokenStream tokens, final Token refused) {
        if (refused == null || refused.getType() != FrostlakeLexer.SLASH) {
            return null;
        }
        final Token star = previousSpoken(tokens, refused.getTokenIndex());
        if (star == null || star.getType() != FrostlakeLexer.STAR
                || star.getStopIndex() + 1 != refused.getStartIndex()) {
            return null;
        }
        final Token after = nextSpoken(tokens, refused.getTokenIndex());
        return after != null && after.getType() == Token.EOF ? after : null;
    }

    private static Token previousSpoken(final TokenStream stream, final int before) {
        for (int i = before - 1; i >= 0; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }

    private static Token nextSpoken(final TokenStream stream, final int after) {
        for (int i = after + 1; i < stream.size(); i++) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
