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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * {@code POST /api/execute} — runs SQL in a session.
 *
 * <p>Body: {@code { "sql": "SELECT * FROM users", "sessionId": "optional-session-id" }}
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

            if (request.getSql() == null || request.getSql().trim().isEmpty()) {
                HttpResponses.send(exchange, 400, "{\"error\":\"SQL is required\"}");
                return;
            }

            final SessionContext session = engine.getOrCreateSession(request.getSessionId());

            final long startTime = System.currentTimeMillis();
            final ExecutionResult result = engine.execute(request.getSql(), session);
            final long executionTime = System.currentTimeMillis() - startTime;

            final SqlResponse response;
            if (result.isSuccess()) {
                response = SqlResponse.success(
                    session.getSessionId(),
                    result.getResultSets(),
                    executionTime
                );
            } else {
                response = SqlResponse.error(
                    session.getSessionId(),
                    result.getErrorMessage()
                );
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
}
