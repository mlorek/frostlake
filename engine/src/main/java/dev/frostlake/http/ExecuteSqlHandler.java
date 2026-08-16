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

package dev.frostlake.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.executor.StatementCount;
import dev.frostlake.values.VariantJsonFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * {@code POST /api/execute} — runs SQL in a session.
 *
 * <p>Body: {@code { "sql": "SELECT * FROM users", "sessionId": "optional-session-id",
 * "requireSession": false, "autoCommit": true }}. The wire contract — statuses, fields, the session
 * rules — is in {@code docs/http-api.md}.
 */
final class ExecuteSqlHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteSqlHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConcurrentDatabaseEngine engine;

    ExecuteSqlHandler(final ConcurrentDatabaseEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            HttpResponses.methodNotAllowed(exchange);
            return;
        }

        try {
            final String requestBody = HttpResponses.readBody(exchange);
            final SqlRequest request = MAPPER.readValue(requestBody, SqlRequest.class);

            // A body with no statement at all is a request the endpoint cannot run: 400, nothing ran, and the
            // answer has the usual shape, so every client reads the reason where it reads any other. A statement
            // that is blank, or holds only a semicolon or comments, is not refused here: it runs like any other
            // and fails as the account fails it, "Empty SQL statement.".
            if (request.getSql() == null) {
                HttpResponses.send(exchange, 400, MAPPER.writeValueAsString(
                    SqlResponse.error(null, "SQL is required")));
                return;
            }

            final String requestedId = request.getSessionId();
            final boolean named = requestedId != null && !requestedId.isEmpty();
            final SessionContext existing = named ? engine.getSession(requestedId) : null;
            if (existing == null && named && request.isRequireSession()) {
                // The caller asked to RESUME a session, not to be handed a fresh one in its place: nothing
                // runs, so no statement lands in a context the caller never set up.
                HttpResponses.send(exchange, 404, MAPPER.writeValueAsString(
                    SqlResponse.error(null, HttpResponses.unknownSession(requestedId))));
                return;
            }
            final SessionContext session = existing != null ? existing : engine.getOrCreateSession(requestedId);

            final String countRefusal = multiStatementRefusal(request, session);
            if (countRefusal != null) {
                // A pack the caller did not ask for is refused before any of it runs, the way the account
                // refuses one: an answer like any other failed statement, in the session it was sent to.
                final SqlResponse refused = SqlResponse.error(session.getSessionId(), countRefusal);
                refused.setNewSession(existing == null);
                HttpResponses.send(exchange, 200, MAPPER.writeValueAsString(refused));
                return;
            }

            final long startTime = System.currentTimeMillis();
            SqlResponse response;
            try {
                final ExecutionResult result = engine.execute(request.getSql(), session, request.getAutoCommit());
                // The rows are rendered after the statement's own scope has closed, so the session's
                // JSON_INDENT is bound again for them: a container crosses laid out as the in-process
                // driver and live's REST answer lay it out.
                engine.getEngine().bindJsonIndent();
                try {
                    response = result.isSuccess()
                        ? SqlResponse.success(session.getSessionId(), result.getResultSets(),
                            System.currentTimeMillis() - startTime)
                        : SqlResponse.error(session.getSessionId(), result.getErrorMessage());
                } finally {
                    VariantJsonFormat.clearSessionScope();
                }
            } catch (final RuntimeException statementFailure) {
                // A statement that fails is an ANSWER, not a server fault: 200 with success:false, the error,
                // and the session it ran in — which survives its failed statement, context and all. Only a
                // request the server could not handle at all is a 5xx.
                logger.debug("Statement failed in session {}: {}", session.getSessionId(),
                    statementFailure.getMessage());
                response = SqlResponse.error(session.getSessionId(), messageOf(statementFailure));
            }
            response.setNewSession(existing == null);
            if (response.isSuccess()) {
                // An ALTER SESSION SET/UNSET MULTI_STATEMENT_COUNT that ran moves this session's gate, so
                // the next request declaring no count of its own is judged by what the client asked for.
                session.followStatementCount(request.getSql());
            }

            // Compact JSON: pretty-printing emitted per-cell indentation and a several-times larger
            // payload for the exact same parsed content.
            HttpResponses.send(exchange, 200, MAPPER.writeValueAsString(response));

        } catch (final Exception e) {
            logger.error("Error handling SQL execution", e);
            // Serialised, not hand-assembled: escaping only the quote left every other character that
            // JSON forbids raw in a string to corrupt the body. A backslash, a tab or a newline was
            // enough — and compilation errors always carry a newline, so this was one message away
            // from emitting a response no client could parse.
            HttpResponses.send(exchange, 500, MAPPER.writeValueAsString(
                SqlResponse.error(null, e.getMessage())));
        }
    }

    /**
     * The refusal for a request holding a number of statements the caller did not ask for, or null when
     * it may run. The count comes from the request when it declares one — the account's driver sends it
     * with the statement — and otherwise from the session's MULTI_STATEMENT_COUNT, which starts at 1.
     * Zero means any number. A script holding no statement is not counted here: it runs and fails as an
     * empty statement, which is what the account answers for it.
     */
    private String multiStatementRefusal(final SqlRequest request, final SessionContext session) {
        final int desired = request.getMultiStatementCount() != null
            ? request.getMultiStatementCount().intValue() : session.getMultiStatementCount();
        if (desired == 0) {
            return null;
        }
        final int actual = StatementCount.countStatements(request.getSql());
        if (actual == 0 || actual == desired) {
            return null;
        }
        return "Actual statement count " + actual + " did not match the desired statement count "
            + desired + ".";
    }

    /** A failure's message, or its class when it carries none — never an empty answer. */
    private static String messageOf(final RuntimeException failure) {
        return failure.getMessage() != null ? failure.getMessage() : failure.toString();
    }
}
