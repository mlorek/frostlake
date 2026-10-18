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
import dev.frostlake.executor.StatementCancellation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * The session collection: {@code GET /api/sessions} counts the live sessions, {@code POST /api/sessions}
 * starts one (optionally in a given database and schema) and {@code DELETE /api/sessions/{id}} releases
 * one, rolling back a transaction it left open. A session otherwise lives until its idle expiry, which
 * {@code POST /api/sessions/{id}/heartbeat} puts off: the heartbeat a client that sets CLIENT_SESSION_KEEP_ALIVE
 * sends, as the account's drivers do. {@code POST /api/sessions/{id}/cancel} cancels the statement running in the
 * session, which ends with "SQL execution canceled", as a driver's {@code Statement.cancel()} ends it.
 */
final class SessionInfoHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(SessionInfoHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PATH = "/api/sessions";
    private static final String HEARTBEAT = "/heartbeat";
    private static final String CANCEL = "/cancel";

    private final ConcurrentDatabaseEngine engine;

    SessionInfoHandler(final ConcurrentDatabaseEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        final String method = exchange.getRequestMethod();
        final String path = exchange.getRequestURI().getPath();
        String rest = path.length() > PATH.length() ? path.substring(PATH.length()) : "";
        if ("/".equals(rest)) {
            rest = "";
        }
        try {
            if ("GET".equals(method) && rest.isEmpty()) {
                HttpResponses.send(exchange, 200, String.format(
                    "{\"activeSessions\":%d}", engine.getActiveSessionCount()));
            } else if ("POST".equals(method) && rest.isEmpty()) {
                create(exchange);
            } else if ("DELETE".equals(method) && rest.length() > 1 && rest.indexOf('/', 1) < 0) {
                release(exchange, rest.substring(1));
            } else if ("POST".equals(method) && isAction(rest, HEARTBEAT)) {
                heartbeat(exchange, rest.substring(1, rest.length() - HEARTBEAT.length()));
            } else if ("POST".equals(method) && isAction(rest, CANCEL)) {
                cancel(exchange, rest.substring(1, rest.length() - CANCEL.length()));
            } else {
                HttpResponses.methodNotAllowed(exchange);
            }
        } catch (final Exception | Error e) {
            logger.error("Error handling session request", e);
            HttpResponses.send(exchange, 500, "{\"error\":\"Internal server error\"}");
        }
    }

    /**
     * Start a session, applying the body's database and schema with USE — so a name that selects nothing
     * fails here, up front, and leaves no session behind.
     */
    private void create(final HttpExchange exchange) throws IOException {
        final String body = HttpResponses.readBody(exchange);
        final SessionRequest request = body.isBlank() ? new SessionRequest()
            : MAPPER.readValue(body, SessionRequest.class);
        final SessionContext session = engine.createSession();
        try {
            if (request.getDatabase() != null) {
                engine.execute("USE DATABASE " + identifierCall(request.getDatabase()), session);
            }
            if (request.getSchema() != null) {
                engine.execute("USE SCHEMA " + identifierCall(request.getSchema()), session);
            }
        } catch (final RuntimeException refused) {
            engine.removeSession(session.getSessionId());
            HttpResponses.send(exchange, 200, MAPPER.writeValueAsString(
                SqlResponse.error(null, String.valueOf(refused.getMessage()))));
            return;
        }
        // Read-only from here on: the USEs above placed the session, and nothing after them may change anything.
        session.setReadOnly(Boolean.TRUE.equals(request.getReadOnly()));
        final SqlResponse created = new SqlResponse(true, session.getSessionId());
        created.setNewSession(true);
        HttpResponses.send(exchange, 200, MAPPER.writeValueAsString(created));
    }

    private void release(final HttpExchange exchange, final String sessionId) throws IOException {
        final SessionContext session = engine.getSession(sessionId);
        if (session == null) {
            HttpResponses.send(exchange, 404, MAPPER.writeValueAsString(
                SqlResponse.error(null, HttpResponses.unknownSession(sessionId))));
            return;
        }
        // The one ending rule an expired session follows too: roll back, then drop the engine's state.
        engine.endSession(session);
        HttpResponses.send(exchange, 200, MAPPER.writeValueAsString(new SqlResponse(true, null)));
    }

    /** Whether the path below {@code /api/sessions} is {@code /{id}} followed by an action such as {@code /cancel}. */
    private static boolean isAction(final String rest, final String action) {
        return rest.endsWith(action) && rest.length() > action.length() + 1
            && rest.indexOf('/', 1) == rest.length() - action.length();
    }

    /**
     * A client's heartbeat: the session counts as in use now, which is all CLIENT_SESSION_KEEP_ALIVE asks of the
     * server. Nothing runs. A session the server no longer holds is a 404, as for any request that names it.
     */
    private void heartbeat(final HttpExchange exchange, final String sessionId) throws IOException {
        // Looking the session up marks it as used, which is the heartbeat.
        final SessionContext session = engine.getSession(sessionId);
        if (session == null) {
            HttpResponses.send(exchange, 404, MAPPER.writeValueAsString(
                SqlResponse.error(null, HttpResponses.unknownSession(sessionId))));
            return;
        }
        HttpResponses.send(exchange, 200, MAPPER.writeValueAsString(new SqlResponse(true, sessionId)));
    }

    /**
     * Cancel the statement running in the session: it ends at its next check with "SQL execution canceled", and the
     * session runs its next statement as usual. A session running nothing has nothing to cancel, and answers the same.
     * Nothing here waits for the engine, so the cancel never queues behind the statement it cancels; a session the
     * server no longer holds is a 404.
     */
    private void cancel(final HttpExchange exchange, final String sessionId) throws IOException {
        final SessionContext session = engine.findSession(sessionId);
        if (session == null) {
            HttpResponses.send(exchange, 404, MAPPER.writeValueAsString(
                SqlResponse.error(null, HttpResponses.unknownSession(sessionId))));
            return;
        }
        final StatementCancellation running = session.getRunning();
        if (running != null) {
            running.cancel();
        }
        HttpResponses.send(exchange, 200, MAPPER.writeValueAsString(new SqlResponse(true, sessionId)));
    }

    /** {@code IDENTIFIER('<name>')}: the name read as SQL reads an identifier reference, never spliced as text. */
    private static String identifierCall(final String name) {
        return "IDENTIFIER('" + name.replace("\\", "\\\\").replace("'", "''") + "')";
    }
}
