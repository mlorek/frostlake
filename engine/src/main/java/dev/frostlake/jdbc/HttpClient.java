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

import dev.frostlake.http.SqlRequest;
import dev.frostlake.http.SqlResponse;

import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The JDBC driver's HTTP transport: runs every statement of one connection in one server session, and
 * keeps that session honest when the server no longer holds it.
 *
 * <p>An answer that carries {@code newSession} says the server understands {@code requireSession} and
 * releases a session on {@code DELETE /api/sessions/{id}}; from then on every request naming the session
 * sends {@code requireSession: true}, so a session the server lost is refused (404) rather than quietly
 * replaced by a fresh one at the server's default scope. A server that predates the field is sent
 * neither.
 *
 * <p>On that refusal the session is dropped. When it held nothing a fresh session lacks, the connection's
 * scope — the last USE DATABASE and USE SCHEMA that succeeded, and manual-commit mode — goes onto a new
 * session and the statement is sent once more. When it held an open transaction, or context set up with
 * a USE, a SET, an ALTER SESSION or a temporary object, the statement is not re-run: a
 * {@link FrostlakeSessionLostException} says what was lost, and the next statement starts afresh on the
 * connection's scope. A session the caller named is never replaced, since whatever its other holders set
 * up on it went with it.
 *
 * <p>Closing releases a session this transport started: one DELETE, bounded, and never an error.
 */
class HttpClient {

    private static final Logger logger = LoggerFactory.getLogger(HttpClient.class);

    /** How long connecting to the server may take before a request fails. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** How long closing waits for the server to release the session. */
    private static final Duration RELEASE_TIMEOUT = Duration.ofSeconds(5);

    /** What puts a replacement session in manual-commit mode, as the connection's own was. */
    private static final String MANUAL_COMMIT = "ALTER SESSION SET AUTOCOMMIT = FALSE";

    // Preserve full decimal precision over the wire: untyped JSON floats deserialize as BigDecimal (not
    // double), so a high-precision NUMBER survives the round-trip and getBigDecimal stays exact.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .build();

    private final String baseUrl;
    private final java.net.http.HttpClient client;
    private volatile String sessionId;
    // A session the caller named may be shared, so closing this client never releases it, and losing it
    // is never papered over with a fresh one.
    private volatile boolean callerNamedSession;
    // Whether the server answers newSession; null until an answer naming a session has said.
    private volatile Boolean tracksSessions;
    // The connection's scope, replayed onto a replacement session: the last USE DATABASE and USE SCHEMA
    // that succeeded, as they were sent, and manual-commit mode.
    private String scopeDatabase;
    private String scopeSchema;
    private boolean manualCommit;
    // The scope has to go onto a new session before the next statement.
    private boolean scopePending;
    // What the session holds that a fresh one would not: context set up on it, an open transaction.
    private boolean holdsContext;
    private boolean inTransaction;

    public HttpClient(final String baseUrl, final String sessionId) {
        this.baseUrl = baseUrl;
        this.sessionId = sessionId != null && !sessionId.isEmpty() ? sessionId : null;
        this.callerNamedSession = this.sessionId != null;
        this.client = java.net.http.HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /**
     * Execute SQL and return response
     */
    public SqlResponse execute(final String sql) throws SQLException {
        return execute(sql, null);
    }

    /**
     * Execute SQL, declaring how many statements it holds so the server applies the gate this driver
     * applies. A null declares none, leaving the session's MULTI_STATEMENT_COUNT to answer.
     *
     * @param sql the statement, or a pack of them
     * @param multiStatementCount the count this statement declares, or null
     * @return the server's answer
     * @throws SQLException when the request fails or the server refuses it; a
     *     {@link FrostlakeSessionLostException} when the session went and took state with it
     */
    public synchronized SqlResponse execute(final String sql, final Integer multiStatementCount)
            throws SQLException {
        final SqlResponse response = run(sql, multiStatementCount);
        if (!response.isSuccess()) {
            throw new SQLException(response.getErrorMessage());
        }
        follow(sql);
        return response;
    }

    /**
     * Switches the session's autocommit mode with ALTER SESSION, which also ends a transaction open on it,
     * as the account does. A replacement session is put in the same mode.
     */
    synchronized void setAutoCommit(final boolean autoCommit) throws SQLException {
        final SqlResponse response = run("ALTER SESSION SET AUTOCOMMIT = " + (autoCommit ? "TRUE" : "FALSE"),
            null);
        if (!response.isSuccess()) {
            throw new SQLException(response.getErrorMessage());
        }
        manualCommit = !autoCommit;
        inTransaction = false;
    }

    /**
     * COMMIT or ROLLBACK. A ROLLBACK whose transaction went with a lost session has nothing left to
     * undo — the server rolled it back when it dropped the session — so it succeeds.
     */
    synchronized void endTransaction(final boolean commit) throws SQLException {
        try {
            execute(commit ? "COMMIT" : "ROLLBACK");
        } catch (final FrostlakeSessionLostException lost) {
            if (commit || !lost.isTransactionLost()) {
                throw lost;
            }
        }
    }

    /** One statement in this connection's session, a lost session replaced at most once. */
    private SqlResponse run(final String sql, final Integer multiStatementCount) throws SQLException {
        if (scopePending) {
            applyScope();
        }
        final SqlResponse answer = post(sql, multiStatementCount);
        return answer != null ? answer : recover(sql, multiStatementCount);
    }

    /**
     * The server no longer holds the session: drop it, then either say what went with it or put the
     * connection's scope onto a new session and send the statement once more.
     */
    private SqlResponse recover(final String sql, final Integer multiStatementCount) throws SQLException {
        final String lost = sessionId;
        final boolean hadTransaction = inTransaction;
        final boolean hadContext = holdsContext || callerNamedSession;
        dropSession();
        if (hadTransaction) {
            throw new FrostlakeSessionLostException("The server no longer holds session " + lost
                + ", and the transaction open on it went with it: the statement did not run. The next"
                + " statement starts a new session.", true);
        }
        if (hadContext) {
            throw new FrostlakeSessionLostException("The server no longer holds session " + lost
                + ", and the context set up on it (a USE, a SET, an ALTER SESSION or a temporary object)"
                + " went with it: the statement was not run again. The next statement starts a new"
                + " session.", false);
        }
        logger.debug("Session {} is gone; replacing it on the connection's scope", lost);
        applyScope();
        final SqlResponse again = post(sql, multiStatementCount);
        if (again == null) {
            final String replacement = sessionId;
            dropSession();
            throw new FrostlakeSessionLostException("The server no longer holds session " + replacement
                + ", started in place of the lost session " + lost + ": the statement did not run.", false);
        }
        return again;
    }

    /** Forgets the session and what it held; the scope goes onto the next one. */
    private void dropSession() {
        sessionId = null;
        callerNamedSession = false;
        holdsContext = false;
        inTransaction = false;
        scopePending = true;
    }

    /**
     * Puts the connection's scope onto a new session: its USE DATABASE and USE SCHEMA, and manual-commit
     * mode. A USE the server refuses leaves the session where connecting would have left it, as it did
     * when the connection was opened; a session that cannot be put in manual-commit mode is refused, since
     * its statements would commit one by one.
     */
    private void applyScope() throws SQLException {
        scopePending = false;
        final List<String> scope = new ArrayList<>();
        if (scopeDatabase != null) {
            scope.add(scopeDatabase);
        }
        if (scopeSchema != null) {
            scope.add(scopeSchema);
        }
        if (manualCommit) {
            scope.add(MANUAL_COMMIT);
        }
        for (final String statement : scope) {
            final SqlResponse answer = post(statement, null);
            if (answer == null) {
                final String gone = sessionId;
                dropSession();
                throw new FrostlakeSessionLostException("The server no longer holds session " + gone
                    + ", started to replace a lost one: the statement did not run.", false);
            }
            if (!answer.isSuccess()) {
                if (MANUAL_COMMIT.equals(statement)) {
                    scopePending = true;
                    throw new SQLException("A new session could not be put in manual-commit mode: "
                        + answer.getErrorMessage());
                }
                logger.debug("Scope statement refused on a new session: {} ({})", statement,
                    answer.getErrorMessage());
            }
        }
    }

    /**
     * Sends one statement in the session held, or in a new one when none is, and answers the server's
     * reply — or null when the server refused because it no longer holds the session named.
     */
    private SqlResponse post(final String sql, final Integer multiStatementCount) throws SQLException {
        final String sentId = sessionId;
        final SqlRequest request = new SqlRequest();
        request.setSql(sql);
        request.setSessionId(sentId);
        request.setMultiStatementCount(multiStatementCount);
        if (sentId != null && Boolean.TRUE.equals(tracksSessions)) {
            request.setRequireSession(Boolean.TRUE);
        }

        final String requestJson;
        try {
            requestJson = MAPPER.writeValueAsString(request);
        } catch (final JacksonException e) {
            throw new SQLException("Failed to serialize request: " + e.getMessage(), e);
        }

        final HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .build();

        try {
            final HttpResponse<String> httpResponse = client.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString()
            );

            // A server refuses a request naming a session it no longer holds; nothing ran.
            if (httpResponse.statusCode() == 404 && sentId != null) {
                return null;
            }
            if (httpResponse.statusCode() != 200) {
                throw new SQLException("HTTP error: " + httpResponse.statusCode() + " - " + httpResponse.body());
            }

            final SqlResponse response = MAPPER.readValue(httpResponse.body(), SqlResponse.class);
            if (response.getSessionId() != null) {
                tracksSessions = response.carriesNewSession();
                if (sentId != null && response.isNewSession()) {
                    // The server ran the statement in a fresh session in place of the one named, so what
                    // that one held is gone, and the scope goes onto this one before the next statement.
                    logger.debug("Session {} was replaced by the server with {}", sentId, response.getSessionId());
                    holdsContext = false;
                    inTransaction = false;
                    callerNamedSession = false;
                    scopePending = true;
                }
                this.sessionId = response.getSessionId();
            }
            return response;

        } catch (final IOException e) {
            throw new SQLException("Failed to send HTTP request: " + e.getMessage(), e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("HTTP request interrupted", e);
        }
    }

    /**
     * Follows what a statement that ran left behind in the session. A plain USE DATABASE or USE SCHEMA
     * becomes the connection's scope, replayed onto a replacement session; anything else that sets up
     * context, and a transaction left open, is what a lost session cannot give back.
     */
    private void follow(final String sql) {
        final List<List<Token>> statements = JdbcSessionEffects.statements(sql);
        if (statements == null) {
            holdsContext = true;
            return;
        }
        if (statements.size() == 1) {
            final JdbcScopeUse use = JdbcSessionEffects.scopeUse(statements.get(0));
            if (use == JdbcScopeUse.DATABASE) {
                // USE DATABASE also moves the schema to the database's own default, so an earlier USE
                // SCHEMA no longer describes where the session is.
                scopeDatabase = sql;
                scopeSchema = null;
                return;
            }
            if (use == JdbcScopeUse.SCHEMA) {
                scopeSchema = sql;
                return;
            }
        }
        for (final List<Token> statement : statements) {
            if (JdbcSessionEffects.touchesSession(statement)) {
                holdsContext = true;
            }
            final JdbcTransactionEffect effect = JdbcSessionEffects.transactionEffect(statement);
            if (effect == JdbcTransactionEffect.BEGINS) {
                inTransaction = true;
            } else if (effect == JdbcTransactionEffect.ENDS) {
                inTransaction = false;
            } else if (manualCommit) {
                // With autocommit off every statement runs in the transaction the session keeps open
                // until COMMIT or ROLLBACK.
                inTransaction = true;
            }
        }
    }

    /**
     * Get current session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Release the server session this client started, so it does not linger until the idle sweep, and
     * so a transaction left open on it is rolled back. A session the caller named is left alone, and so
     * is one on a server that predates the release. The request is bounded and a failure is ignored: the
     * connection is closing either way.
     */
    public void releaseSession() {
        final String held = sessionId;
        if (held == null || callerNamedSession || !Boolean.TRUE.equals(tracksSessions)) {
            return;
        }
        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/sessions/"
                + URLEncoder.encode(held, StandardCharsets.UTF_8).replace("+", "%20")))
            .timeout(RELEASE_TIMEOUT)
            .DELETE()
            .build();
        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (final IOException e) {
            // The server is gone or slow to answer; the idle sweep reclaims the session.
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check server health
     */
    public boolean isHealthy() {
        return isHealthy(0);
    }

    /**
     * Check server health, waiting at most {@code timeoutSeconds} for the answer; zero waits as long as
     * the request takes.
     */
    public boolean isHealthy(final int timeoutSeconds) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/health"))
            .GET();
        if (timeoutSeconds > 0) {
            builder.timeout(Duration.ofSeconds(timeoutSeconds));
        }

        try {
            final HttpResponse<String> response = client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
            );
            return response.statusCode() == 200;
        } catch (final Exception e) {
            return false;
        }
    }
}
