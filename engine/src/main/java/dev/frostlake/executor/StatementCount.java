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
import dev.frostlake.parser.SqlCharStream;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How many top-level statements a request holds, counted the way the account counts them
 * (live-verified). One rule decides the multi-statement gate for every client: the HTTP endpoint
 * applies it to a request, and the JDBC driver applies it before sending one.
 *
 * <p>The count is the parser's own: a text that parses holds as many statements as its script has —
 * a flow chain is one, a scripting block is one, a trailing {@code ;} adds nothing. Nothing is read a
 * second way, so a text cannot count as one statement and run as two: {@code SELECT 1 AS a$$b; DELETE
 * FROM t} is two, the {@code $$} being part of a name, and so is a statement after a comment that a
 * carriage return ends. A text that does not parse is refused for its syntax whatever it counts, and
 * is counted from the lexer's tokens so that the refusal can still be chosen.
 */
public final class StatementCount {

    private StatementCount() {
    }

    /**
     * The number of statements {@code sql} holds: its script's top-level statements when it parses,
     * and the count {@link #countTokens} reads when it does not.
     *
     * @param sql the request's text
     * @return the count; 0 for a text holding no statement
     */
    public static int countStatements(final String sql) {
        try {
            return QueryExecutor.parsedStatementCount(sql);
        } catch (final RuntimeException doesNotParse) {
            return countTokens(sql);
        }
    }

    /**
     * The number of statements {@code sql} holds, read off the lexer's tokens without parsing it: the
     * semicolons that stand outside a scripting region. Literals, quoted names, dollar-quoted bodies and
     * comments are each what the lexer makes of them, so none is read differently from the parser. A
     * scripting region counts as one statement: a block-opening {@code BEGIN} (one not followed by
     * {@code ;}, {@code )}, {@code TRANSACTION}, {@code WORK} or {@code NAME}, which start a transaction)
     * or a top-level {@code DECLARE} opens one; nested blocks pair their own BEGIN and END;
     * {@code END IF} / {@code WHILE} / {@code FOR} / {@code LOOP} / {@code REPEAT} close constructs the
     * count never opened; a CASE inside a region pairs with its bare {@code END} or {@code END CASE}; a
     * block in parentheses is a nested block, never a DECLARE section's own BEGIN.
     *
     * <p>This is the count of a client that does not parse what it sends — the remote driver — and of a
     * text that does not parse; where the words of a block are used as names it can differ from the
     * parser's, which is why the server counts with {@link #countStatements}.
     *
     * @param sql the text
     * @return the count; 0 for a text holding no statement
     */
    public static int countTokens(final String sql) {
        final List<Token> tokens = tokens(sql);
        int count = 0;
        boolean sawContent = false;
        int depth = 0;
        int parentheses = 0;
        boolean headerBeginPending = false;
        for (int i = 0; i < tokens.size(); i++) {
            final int type = tokens.get(i).getType();
            if (type == Token.EOF) {
                break;
            }
            if (type == FrostlakeLexer.SEMI) {
                if (depth == 0 && sawContent) {
                    count++;
                    sawContent = false;
                }
                continue;
            }
            sawContent = true;
            if (type == FrostlakeLexer.BEGIN) {
                if (!startsTransaction(tokens, i)) {
                    if (headerBeginPending && parentheses == 0) {
                        headerBeginPending = false;
                    } else {
                        depth++;
                    }
                }
            } else if (type == FrostlakeLexer.DECLARE && depth == 0) {
                depth++;
                headerBeginPending = true;
            } else if (type == FrostlakeLexer.CASE && depth > 0) {
                depth++;
            } else if (type == FrostlakeLexer.END && depth > 0) {
                final int follower = tokens.get(i + 1).getType();
                if (follower == FrostlakeLexer.IF || follower == FrostlakeLexer.WHILE
                        || follower == FrostlakeLexer.FOR || follower == FrostlakeLexer.LOOP
                        || follower == FrostlakeLexer.REPEAT) {
                    i++;
                } else {
                    if (follower == FrostlakeLexer.CASE) {
                        i++;
                    }
                    depth--;
                }
            } else if (type == FrostlakeLexer.LPAREN) {
                parentheses++;
            } else if (type == FrostlakeLexer.RPAREN && parentheses > 0) {
                parentheses--;
            }
        }
        if (sawContent) {
            count++;
        }
        return count;
    }

    /** Whether the BEGIN at {@code index} starts a transaction rather than a block. */
    private static boolean startsTransaction(final List<Token> tokens, final int index) {
        final int follower = tokens.get(index + 1).getType();
        return follower == Token.EOF || follower == FrostlakeLexer.SEMI || follower == FrostlakeLexer.RPAREN
            || follower == FrostlakeLexer.TRANSACTION || follower == FrostlakeLexer.WORK
            || follower == FrostlakeLexer.NAME;
    }

    /** The lexer's tokens of {@code sql}, comments and blanks dropped, the end of the text last. */
    private static List<Token> tokens(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        return stream.getTokens();
    }

    /**
     * The value an exact {@code ALTER SESSION SET MULTI_STATEMENT_COUNT = n} statement assigns, 1
     * for the {@code UNSET} form (the parameter's default), or null when {@code sql} is not that
     * statement — so a connection can follow the session parameter without a server round trip.
     */
    public static Integer assignedCount(final String sql) {
        final List<String> words = words(sql);
        if (words.size() == 4 && "ALTER".equals(words.get(0)) && "SESSION".equals(words.get(1))
                && "UNSET".equals(words.get(2)) && "MULTI_STATEMENT_COUNT".equals(words.get(3))) {
            return 1;
        }
        if (words.size() == 6 && "ALTER".equals(words.get(0)) && "SESSION".equals(words.get(1))
                && "SET".equals(words.get(2)) && "MULTI_STATEMENT_COUNT".equals(words.get(3))
                && "=".equals(words.get(4))) {
            try {
                return Integer.valueOf(words.get(5));
            } catch (final NumberFormatException notANumber) {
                return null;
            }
        }
        return null;
    }

    /** The upper-cased texts of the first tokens of {@code sql}, semicolons dropped, for the detector above. */
    private static List<String> words(final String sql) {
        final List<String> words = new ArrayList<String>();
        for (final Token token : tokens(sql)) {
            if (token.getType() == Token.EOF || words.size() > 7) {
                break; // longer than any form the detector accepts
            }
            if (token.getType() != FrostlakeLexer.SEMI) {
                words.add(token.getText().toUpperCase(Locale.ROOT));
            }
        }
        return words;
    }
}
