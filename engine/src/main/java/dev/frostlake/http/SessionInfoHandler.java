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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * The session collection: {@code GET /api/sessions} counts the live sessions, {@code POST /api/sessions}
 * starts one (optionally in a given database and schema) and {@code DELETE /api/sessions/{id}} releases
 * one, rolling back a transaction it left open. A session otherwise lives until its idle expiry.
 */
final class SessionInfoHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(SessionInfoHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PATH = "/api/sessions";

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
            } else {
                HttpResponses.methodNotAllowed(exchange);
            }
        } catch (final Exception e) {
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
        if (session.isInTransaction()) {
            try {
                engine.execute("ROLLBACK", session);
            } catch (final RuntimeException e) {
                // The session goes regardless; its transaction's writes were never committed.
                logger.warn("Rolling back released session {} failed: {}", sessionId, e.getMessage());
            }
        }
        engine.removeSession(sessionId);
        HttpResponses.send(exchange, 200, MAPPER.writeValueAsString(new SqlResponse(true, null)));
    }

    /** {@code IDENTIFIER('<name>')}: the name read as SQL reads an identifier reference, never spliced as text. */
    private static String identifierCall(final String name) {
        return "IDENTIFIER('" + name.replace("\\", "\\\\").replace("'", "''") + "')";
    }
}
