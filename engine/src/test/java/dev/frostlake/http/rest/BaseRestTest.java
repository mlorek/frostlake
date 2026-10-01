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

package dev.frostlake.http.rest;

import dev.frostlake.ExecutionResult;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.http.DatabaseHttpServer;
import dev.frostlake.http.SessionContext;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real HTTP server on a free port for the {@code /api/v2} tests, with request helpers. Each test class gets a
 * server of its own; objects a test creates live in that server's engine for the class's lifetime, so tests
 * name their objects uniquely.
 */
public abstract class BaseRestTest {

    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected static DatabaseHttpServer server;
    protected static String baseUrl;
    protected final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
        server = new DatabaseHttpServer(config);
        server.start();
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    /** Sends a request; a non-null body is sent as JSON. */
    protected HttpResponse<String> request(final String method, final String path, final String body)
            throws Exception {
        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path));
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        builder.header("Accept", "application/json");
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> get(final String path) throws Exception {
        return request("GET", path, null);
    }

    protected HttpResponse<String> post(final String path, final String body) throws Exception {
        return request("POST", path, body);
    }

    protected HttpResponse<String> put(final String path, final String body) throws Exception {
        return request("PUT", path, body);
    }

    protected HttpResponse<String> delete(final String path) throws Exception {
        return request("DELETE", path, null);
    }

    /** The body parsed as JSON. */
    protected static JsonNode json(final HttpResponse<String> response) {
        return MAPPER.readTree(response.body());
    }

    /** Asserts the status and answers the body parsed as JSON. */
    protected static JsonNode expect(final int status, final HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.request().method() + " " + response.request().uri()
            + " -> " + response.body());
        return response.body().isEmpty() ? null : json(response);
    }

    /** Asserts {@code 200} and answers the body. */
    protected static JsonNode ok(final HttpResponse<String> response) {
        return expect(200, response);
    }

    /** Asserts an error in the ErrorResponse shape with the given status, and answers its message. */
    protected static String error(final int status, final HttpResponse<String> response) {
        final JsonNode body = expect(status, response);
        assertTrue(body.has("message"), response.body());
        assertEquals(response.headers().firstValue("X-Snowflake-Request-ID").orElse(null),
            body.path("request_id").asString(null), "the error names the request it answers");
        return body.get("message").asString();
    }

    /** Runs SQL on the server's engine in a session of its own, failing the test when it is refused. */
    protected static void sql(final String statement) {
        final SessionContext session = server.getEngine().createSession();
        try {
            final ExecutionResult result = server.getEngine().execute(statement, session);
            assertTrue(result.isSuccess(), statement + " -> " + result.getErrorMessage());
        } finally {
            server.getEngine().removeSession(session.getSessionId());
        }
    }

    /** A JSON array's elements' {@code name} values, joined with commas. */
    protected static String names(final JsonNode array) {
        final StringBuilder out = new StringBuilder();
        for (final JsonNode item : array.values()) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(item.path("name").asString());
        }
        return out.toString();
    }
}
