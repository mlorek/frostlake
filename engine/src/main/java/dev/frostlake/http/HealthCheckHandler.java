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

import java.io.IOException;

/** {@code GET /api/health} — liveness, with the active session count. */
final class HealthCheckHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckHandler.class);

    private final ConcurrentDatabaseEngine engine;

    HealthCheckHandler(final ConcurrentDatabaseEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            HttpResponses.methodNotAllowed(exchange);
            return;
        }

        try {
            final int activeSessions = engine.getActiveSessionCount();
            HttpResponses.send(exchange, 200, String.format(
                "{\"status\":\"healthy\",\"activeSessions\":%d}", activeSessions));
        } catch (final Exception e) {
            logger.error("Error handling health check", e);
            HttpResponses.send(exchange, 500, "{\"status\":\"error\"}");
        }
    }
}
