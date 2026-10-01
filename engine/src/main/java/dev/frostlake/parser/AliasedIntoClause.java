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

import dev.frostlake.executor.LeadingCommentOffset;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import java.util.ArrayList;
import java.util.List;

/**
 * A query of a Snowflake Scripting block that runs into the next statement's first word, where that word is followed
 * by INTO: the account takes the word as the query's alias and the INTO clause after it as the block's own SELECT …
 * INTO, so the fault lands wherever that SELECT … INTO fails — {@code SELECT 'foo'} then {@code MERGE INTO t USING u
 * ON …;} is 'USING', then 'u', where a word followed by anything else is refused at the token after it
 * (live-verified). This parser reads a complete statement after the word instead, so the reading is rebuilt on the
 * text with the word written as a plain name of its own length.
 */
final class AliasedIntoClause {

    private AliasedIntoClause() {
    }

    /**
     * The tokens refused when {@code word} is read as the alias before an INTO clause: the first, and the one stacked
     * after it when there is one.
     *
     * @param stream         the parse's token stream
     * @param sql            the text the stream was read from
     * @param word           the word the query runs into
     * @param statementParse whether the text is parsed as statements
     * @return the refused tokens of {@code stream}, or null when the rebuilt reading refuses nothing after the word
     */
    static List<Token> faults(final TokenStream stream, final String sql, final Token word,
                              final boolean statementParse) {
        final int start = word.getStartIndex();
        final int stop = word.getStopIndex();
        if (sql == null || start < 0 || stop >= sql.length() || !sql.substring(start, stop + 1).equals(word.getText())
                || isPlainName(word.getText())) {
            // A word already written as the plain name the rebuilt reading uses reads nothing new.
            return null;
        }
        final StringBuilder text = new StringBuilder(sql);
        for (int i = start; i <= stop; i++) {
            text.setCharAt(i, 'x');
        }
        final List<String> lines = SyntaxErrorListener.linesReportedFor(text.toString(), statementParse);
        final List<Token> refused = new ArrayList<Token>();
        for (int i = 0; i < lines.size() && refused.size() < 2; i++) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(lines.get(i));
            final Token token = at == null ? null : tokenAt(stream, word, at);
            if (token == null) {
                break;
            }
            refused.add(token);
        }
        return refused.isEmpty() ? null : refused;
    }

    /** Whether {@code text} is the plain name the rebuilt reading writes: x's alone. */
    private static boolean isPlainName(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != 'x') {
                return false;
            }
        }
        return true;
    }

    /** The token after {@code word} that stands at the reported line and position, or null. */
    private static Token tokenAt(final TokenStream stream, final Token word, final int[] at) {
        for (int i = word.getTokenIndex() + 1; i < stream.size(); i++) {
            final Token token = stream.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
            if (shown[0] == at[0] && shown[1] == at[1]) {
                return token;
            }
            if (token.getType() == Token.EOF) {
                return null;
            }
        }
        return null;
    }
}
