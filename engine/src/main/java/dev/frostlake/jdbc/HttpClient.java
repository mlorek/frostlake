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
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    // A session the caller named may be shared, so closing this client never releases it.
    private final boolean callerNamedSession;

    public HttpClient(final String baseUrl, final String sessionId) {
        this.baseUrl = baseUrl;
        this.sessionId = sessionId;
        this.callerNamedSession = sessionId != null && !sessionId.isEmpty();
        this.client = java.net.http.HttpClient.newHttpClient();
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
     * @throws SQLException when the request fails or the server refuses it
     */
    public SqlResponse execute(final String sql, final Integer multiStatementCount) throws SQLException {
        final SqlRequest request = new SqlRequest();
        request.setSql(sql);
        request.setSessionId(sessionId);
        request.setMultiStatementCount(multiStatementCount);

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
     * Release the server session this client was handed, so it does not linger until the idle sweep. A
     * session the caller named is left alone, and a failure is ignored: the connection is closing
     * either way.
     */
    public void releaseSession() {
        if (callerNamedSession || sessionId == null) {
            return;
        }
        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/sessions/"
                + URLEncoder.encode(sessionId, StandardCharsets.UTF_8).replace("+", "%20")))
            .DELETE()
            .build();
        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (final IOException e) {
            // The server is gone, and the session went with it.
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
