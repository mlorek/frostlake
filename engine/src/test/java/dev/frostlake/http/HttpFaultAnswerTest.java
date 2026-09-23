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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A fault while handling a request is answered, never left open. The client gets a 500 in the endpoint's
 * usual failure shape at once instead of waiting out its own timeout, and an Error counts: a class the
 * server cannot load or an exhausted stack would otherwise end the pool thread with the exchange open.
 */
public class HttpFaultAnswerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Far longer than any answer takes: reaching it means the request was left unanswered. */
    private static final Duration PATIENCE = Duration.ofSeconds(30);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(PATIENCE).build();

    @Test
    public void anErrorWhileAStatementRunsIsAnsweredWithTheUsualFailure() throws Exception {
        final HttpServer server = serve("/api/execute",
            new ErrorAnsweringHandler(new ExecuteSqlHandler(failingEngine(
                new NoClassDefFoundError("dev/frostlake/executor/MissingClass")))));
        try {
            final HttpResponse<String> answer = post(server, "/api/execute", "{\"sql\":\"SELECT 1\"}");
            assertEquals(500, answer.statusCode());
            final JsonNode body = MAPPER.readTree(answer.body());
            assertFalse(body.get("success").asBoolean());
            assertEquals("dev/frostlake/executor/MissingClass", body.get("errorMessage").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void anErrorWithoutAMessageIsNamedByItsClass() throws Exception {
        final HttpServer server = serve("/api/execute",
            new ErrorAnsweringHandler(new ExecuteSqlHandler(failingEngine(new StackOverflowError()))));
        try {
            final HttpResponse<String> answer = post(server, "/api/execute", "{\"sql\":\"SELECT 1\"}");
            assertEquals(500, answer.statusCode());
            assertEquals("java.lang.StackOverflowError",
                MAPPER.readTree(answer.body()).get("errorMessage").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void anErrorInTheHealthCheckIsAnswered() throws Exception {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine() {
            @Override
            public int getActiveSessionCount() {
                throw new NoClassDefFoundError("dev/frostlake/http/MissingClass");
            }
        };
        final HttpServer server = serve("/api/health", new ErrorAnsweringHandler(new HealthCheckHandler(engine)));
        try {
            final HttpResponse<String> answer = get(server, "/api/health");
            assertEquals(500, answer.statusCode());
            assertEquals("{\"status\":\"error\"}", answer.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void aFaultNoHandlerAnswersGetsAFiveHundred() throws Exception {
        final HttpServer server = serve("/api/boom", new ErrorAnsweringHandler(new HttpHandler() {
            @Override
            public void handle(final HttpExchange exchange) {
                throw new NoClassDefFoundError("dev/frostlake/http/MissingClass");
            }
        }));
        try {
            final HttpResponse<String> answer = get(server, "/api/boom");
            assertEquals(500, answer.statusCode());
            assertEquals(ErrorAnsweringHandler.FAULT_BODY, answer.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void aFaultAfterTheResponseBeganEndsTheExchange() throws Exception {
        final HttpServer server = serve("/api/half", new ErrorAnsweringHandler(new HttpHandler() {
            @Override
            public void handle(final HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(200, 10);
                final OutputStream body = exchange.getResponseBody();
                body.write("half".getBytes(StandardCharsets.UTF_8));
                body.flush();
                throw new NoClassDefFoundError("dev/frostlake/http/MissingClass");
            }
        }));
        try {
            get(server, "/api/half");
            fail("a body cut short read as complete");
        } catch (final HttpTimeoutException leftOpen) {
            fail("the exchange was left open: " + leftOpen.getMessage());
        } catch (final IOException cutShort) {
            // The connection ended before the promised ten bytes arrived, so the client knows at once.
        } finally {
            server.stop(0);
        }
    }

    /** An engine whose every statement throws the given Error. */
    private static ConcurrentDatabaseEngine failingEngine(final Error fault) {
        return new ConcurrentDatabaseEngine() {
            @Override
            public ExecutionResult execute(final String sql, final SessionContext session,
                                           final Boolean clientAutoCommit) {
                throw fault;
            }
        };
    }

    private static HttpServer serve(final String path, final HttpHandler handler) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler);
        server.start();
        return server;
    }

    private static URI uri(final HttpServer server, final String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private HttpResponse<String> get(final HttpServer server, final String path)
            throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder().uri(uri(server, path)).timeout(PATIENCE).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(final HttpServer server, final String path, final String body)
            throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder().uri(uri(server, path)).timeout(PATIENCE)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
