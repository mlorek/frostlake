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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;

/**
 * HTTP client for communicating with Frostlake SQL Engine
 */
class HttpClient {
    private final String baseUrl;
    private final java.net.http.HttpClient client;
    // Preserve full decimal precision over the wire: untyped JSON floats deserialize as BigDecimal (not
    // double), so a high-precision NUMBER survives the round-trip and getBigDecimal stays exact.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .build();
    private String sessionId;

    public HttpClient(final String baseUrl, final String sessionId) {
        this.baseUrl = baseUrl;
        this.sessionId = sessionId;
        this.client = java.net.http.HttpClient.newHttpClient();
    }

    /**
     * Execute SQL and return response
     */
    public SqlResponse execute(final String sql) throws SQLException {
        final SqlRequest request = new SqlRequest();
        request.setSql(sql);
        request.setSessionId(sessionId);

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

            if (httpResponse.statusCode() != 200) {
                throw new SQLException("HTTP error: " + httpResponse.statusCode() + " - " + httpResponse.body());
            }

            final SqlResponse response = MAPPER.readValue(httpResponse.body(), SqlResponse.class);

            // Update session ID
            if (response.getSessionId() != null) {
                this.sessionId = response.getSessionId();
            }

            if (!response.isSuccess()) {
                throw new SQLException(response.getErrorMessage());
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
     * Get current session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Check server health
     */
    public boolean isHealthy() {
        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/health"))
            .GET()
            .build();

        try {
            final HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            return response.statusCode() == 200;
        } catch (final Exception e) {
            return false;
        }
    }
}
