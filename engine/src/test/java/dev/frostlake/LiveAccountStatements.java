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

package dev.frostlake;

import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeLexer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Assumptions;

/**
 * The statements the live harness does not send: the ones that change the account's roles, users and grants, which
 * the account the suite runs against keeps as they are — GRANT, REVOKE, USE ROLE, USE SECONDARY ROLES, and CREATE,
 * ALTER or DROP of a ROLE, a DATABASE ROLE or a USER. A test that sends one is skipped on the account before the
 * statement reaches it, the reason naming the statement's words, and a teardown's one is passed over, since nothing
 * it would undo was made. The embedded engine runs them all as ever. {@code SF_LIVE_ACCOUNT_STATEMENTS=1} sends them
 * all the same, for a run against an account that takes them.
 *
 * <p>A test that catches the skip as a refusal — an {@code assertThrows} around the statement — is skipped all the
 * same once it ends (see {@link LiveHeldBackFailures} and {@link BaseDatabaseTest}): what it went on to check was not
 * the account's answer. The words are read from the engine's own lexer (see {@link #heldBack}).
 */
public final class LiveAccountStatements {

    /** The switch that sends them all the same. */
    private static final String SEND_ALL = "SF_LIVE_ACCOUNT_STATEMENTS";

    /** The words of the statement the running test was refused, or null while it was refused none. */
    private static volatile String heldBackInTest;

    private LiveAccountStatements() {
    }

    /** Whether this run sends the account's role and grant statements all the same. */
    public static boolean sent() {
        return "1".equals(System.getenv(SEND_ALL));
    }

    /** Starts a test with no statement held back. */
    public static void beginTest() {
        heldBackInTest = null;
    }

    /**
     * Skips the running test over a statement the account is not sent, remembering it for the test's end.
     *
     * @param words the statement's leading words
     */
    public static void skipOver(final String words) {
        heldBackInTest = words;
        Assumptions.abort(reason(words));
    }

    /** The words of the statement the running test was refused, or null when it was refused none. */
    public static String heldBackInTest() {
        return heldBackInTest;
    }

    /**
     * Why a test is skipped on the account.
     *
     * @param words the leading words of the statement held back
     * @return the skip's reason
     */
    public static String reason(final String words) {
        return words + " is not sent to the live account, whose roles, users and grants stay as they are (set "
            + SEND_ALL + "=1 to send it)";
    }

    /**
     * The words of the first statement in {@code sql} the account is not sent, such as {@code GRANT} or
     * {@code CREATE ROLE}, or null when it holds none. A statement is found where one begins — the text's start,
     * after a semicolon, or after the word a block's statement follows (BEGIN, THEN, ELSE, DO, LOOP, REPEAT) — and in
     * the text an EXECUTE IMMEDIATE runs or a routine's body holds, a string or a {@code $$} text after IMMEDIATE or
     * AS.
     *
     * @param sql the text a test hands the engine
     * @return the statement's leading words, or null
     */
    public static String heldBack(final String sql) {
        final List<Token> tokens = new ArrayList<>();
        try {
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
            lexer.removeErrorListeners();
            for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
                if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                    tokens.add(token);
                }
            }
        } catch (final RuntimeException unreadable) {
            return null;
        }
        for (int i = 0; i < tokens.size(); i++) {
            final Token at = tokens.get(i);
            final int type = at.getType();
            if ((type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.DOLLAR_QUOTED_STRING)
                    && i > 0 && runsText(tokens.get(i - 1))) {
                final String inner = type == FrostlakeLexer.STRING_LITERAL ? SqlStringLiterals.decode(at.getText())
                    : at.getText().length() >= 4 ? at.getText().substring(2, at.getText().length() - 2) : "";
                final String held = heldBack(inner);
                if (held != null) {
                    return held;
                }
                continue;
            }
            if (i > 0 && !beginsStatement(tokens.get(i - 1))) {
                continue;
            }
            if (type == FrostlakeLexer.GRANT) {
                return "GRANT";
            }
            if (type == FrostlakeLexer.REVOKE) {
                return "REVOKE";
            }
            if (type == FrostlakeLexer.USE && i + 1 < tokens.size()) {
                final int next = tokens.get(i + 1).getType();
                if (next == FrostlakeLexer.ROLE) {
                    return "USE ROLE";
                }
                if (next == FrostlakeLexer.SECONDARY) {
                    return "USE SECONDARY ROLES";
                }
            }
            if (type == FrostlakeLexer.CREATE || type == FrostlakeLexer.ALTER || type == FrostlakeLexer.DROP) {
                final String principal = principalAfter(tokens, i + 1);
                if (principal != null) {
                    return at.getText().toUpperCase(Locale.ROOT) + " " + principal;
                }
            }
        }
        return null;
    }

    /** Whether a statement may begin after {@code before}: a semicolon, or a word a block's statement follows. */
    private static boolean beginsStatement(final Token before) {
        if (before.getType() == FrostlakeLexer.SEMI) {
            return true;
        }
        final String word = before.getText().toUpperCase(Locale.ROOT);
        return "BEGIN".equals(word) || "THEN".equals(word) || "ELSE".equals(word) || "DO".equals(word)
            || "LOOP".equals(word) || "REPEAT".equals(word);
    }

    /** Whether a string after {@code before} is a text of statements: what EXECUTE IMMEDIATE runs or a body holds. */
    private static boolean runsText(final Token before) {
        final String word = before.getText().toUpperCase(Locale.ROOT);
        return "IMMEDIATE".equals(word) || "AS".equals(word);
    }

    /**
     * The principal kind a CREATE, ALTER or DROP at {@code from} names, past an OR REPLACE: ROLE, DATABASE ROLE or
     * USER, or null for any other object.
     */
    private static String principalAfter(final List<Token> tokens, final int from) {
        int at = from;
        if (at + 1 < tokens.size() && tokens.get(at).getType() == FrostlakeLexer.OR
                && tokens.get(at + 1).getType() == FrostlakeLexer.REPLACE) {
            at += 2;
        }
        if (at >= tokens.size()) {
            return null;
        }
        final int kind = tokens.get(at).getType();
        if (kind == FrostlakeLexer.ROLE) {
            return "ROLE";
        }
        if (kind == FrostlakeLexer.USER) {
            return "USER";
        }
        if (kind == FrostlakeLexer.DATABASE && at + 1 < tokens.size()
                && tokens.get(at + 1).getType() == FrostlakeLexer.ROLE) {
            return "DATABASE ROLE";
        }
        return null;
    }
}
