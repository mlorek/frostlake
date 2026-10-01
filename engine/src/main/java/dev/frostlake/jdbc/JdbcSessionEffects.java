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

package dev.frostlake.jdbc;

import dev.frostlake.executor.SqlTokens;
import dev.frostlake.parser.FrostlakeLexer;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a statement sent over the HTTP transport leaves behind in its server session, read from the
 * lexer's tokens: whether it set up context a fresh session would not have, whether it opened or ended a
 * transaction, and whether it is one of the plain USE shapes a connection follows as its own scope.
 *
 * <p>A request is split on its top-level semicolons. One inside a string, a quoted identifier or a
 * {@code $$} body does not split, because the lexer hands those over whole, and comments never reach the
 * token stream. A scripting block is split along with everything else, which only makes the checks more
 * willing to flag a request — the safe direction to be wrong in.
 */
final class JdbcSessionEffects {

    /** How many leading words decide whether a statement touches its session. */
    private static final int WORD_WINDOW = 16;

    /** The words that may stand between CREATE, DROP or ALTER and the kind of object it names. */
    private static final Set<String> MODIFIERS = Set.of("OR", "REPLACE", "TRANSIENT", "TEMPORARY", "TEMP",
        "VOLATILE", "LOCAL", "GLOBAL", "SECURE", "IF", "NOT", "EXISTS", "PUBLIC", "PRIVATE", "ICEBERG", "DYNAMIC",
        "HYBRID", "EVENT", "RECURSIVE", "MATERIALIZED", "EXTERNAL");

    /** The modifiers that make a created object live only as long as its session. */
    private static final Set<String> TEMPORARY = Set.of("TEMPORARY", "TEMP", "VOLATILE");

    private JdbcSessionEffects() {
    }

    /**
     * The statements of a request, each as its default-channel tokens without the semicolon that ends
     * it. A statement holding no token is left out.
     *
     * @param sql the request's text
     * @return the statements, or null when the text does not lex
     */
    static List<List<Token>> statements(final String sql) {
        final List<List<Token>> statements = new ArrayList<>();
        if (sql == null) {
            return statements;
        }
        List<Token> current = new ArrayList<>();
        try {
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
            lexer.removeErrorListeners();
            for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
                if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                    continue;
                }
                if (token.getType() == FrostlakeLexer.SEMI) {
                    if (!current.isEmpty()) {
                        statements.add(current);
                        current = new ArrayList<>();
                    }
                } else {
                    current.add(token);
                }
            }
        } catch (final RuntimeException notLexable) {
            return null;
        }
        if (!current.isEmpty()) {
            statements.add(current);
        }
        return statements;
    }

    /**
     * Up to {@code limit} leading words of a statement, upper-cased, stopping at the first token that is
     * not a bare word.
     */
    static List<String> leadingWords(final List<Token> statement, final int limit) {
        final List<String> words = new ArrayList<>();
        for (final Token token : statement) {
            if (words.size() >= limit || !SqlTokens.isWord(token)) {
                break;
            }
            words.add(token.getText().toUpperCase(Locale.ROOT));
        }
        return words;
    }

    /**
     * Whether a statement leaves behind state a fresh session would not have: a moved scope (USE, or a
     * CREATE or DROP of a DATABASE or SCHEMA), a session variable or setting (SET, UNSET, ALTER SESSION),
     * or a temporary object. CREATE TABLE and its kind leave the session as it was.
     */
    static boolean touchesSession(final List<Token> statement) {
        final List<String> words = leadingWords(statement, WORD_WINDOW);
        if (words.isEmpty()) {
            return false;
        }
        final String verb = words.get(0);
        if ("USE".equals(verb) || "SET".equals(verb) || "UNSET".equals(verb)) {
            return true;
        }
        if (!"ALTER".equals(verb) && !"CREATE".equals(verb) && !"DROP".equals(verb)) {
            return false;
        }
        int kind = 1;
        boolean temporary = false;
        while (kind < words.size() && MODIFIERS.contains(words.get(kind))) {
            temporary = temporary || TEMPORARY.contains(words.get(kind));
            kind++;
        }
        final String object = kind < words.size() ? words.get(kind) : "";
        if ("ALTER".equals(verb)) {
            return "SESSION".equals(object);
        }
        return "DATABASE".equals(object) || "SCHEMA".equals(object) || ("CREATE".equals(verb) && temporary);
    }

    /**
     * Whether a statement opens or ends a transaction. BEGIN on its own, or with TRANSACTION, WORK or
     * NAME, opens one, as START TRANSACTION does; BEGIN followed by a statement opens a scripting block
     * instead. COMMIT and ROLLBACK end one.
     */
    static JdbcTransactionEffect transactionEffect(final List<Token> statement) {
        final List<String> words = leadingWords(statement, 2);
        if (words.isEmpty()) {
            return JdbcTransactionEffect.NONE;
        }
        final String first = words.get(0);
        final String second = words.size() > 1 ? words.get(1) : null;
        if ("COMMIT".equals(first) || "ROLLBACK".equals(first)) {
            return JdbcTransactionEffect.ENDS;
        }
        if ("BEGIN".equals(first) && (second == null || "TRANSACTION".equals(second) || "WORK".equals(second)
                || "NAME".equals(second))) {
            return JdbcTransactionEffect.BEGINS;
        }
        if ("START".equals(first) && "TRANSACTION".equals(second)) {
            return JdbcTransactionEffect.BEGINS;
        }
        return JdbcTransactionEffect.NONE;
    }

    /**
     * Which plain USE a statement is: {@code USE DATABASE name}, {@code USE SCHEMA name} or
     * {@code USE SCHEMA database.name}. Every other statement, and every other form of USE, is NONE.
     */
    static JdbcScopeUse scopeUse(final List<Token> statement) {
        if (statement.size() < 3 || statement.get(0).getType() != FrostlakeLexer.USE) {
            return JdbcScopeUse.NONE;
        }
        final int kind = statement.get(1).getType();
        if (kind == FrostlakeLexer.DATABASE && statement.size() == 3 && isNamePart(statement.get(2))) {
            return JdbcScopeUse.DATABASE;
        }
        if (kind == FrostlakeLexer.SCHEMA && statement.size() == 3 && isNamePart(statement.get(2))) {
            return JdbcScopeUse.SCHEMA;
        }
        if (kind == FrostlakeLexer.SCHEMA && statement.size() == 5 && isNamePart(statement.get(2))
                && statement.get(3).getType() == FrostlakeLexer.DOT && isNamePart(statement.get(4))) {
            return JdbcScopeUse.SCHEMA;
        }
        return JdbcScopeUse.NONE;
    }

    /** A token that can serve as a name in USE DATABASE or USE SCHEMA — anything but punctuation. */
    private static boolean isNamePart(final Token token) {
        final int type = token.getType();
        return type != FrostlakeLexer.SEMI && type != FrostlakeLexer.DOT;
    }
}
