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

import dev.frostlake.config.EngineConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the server holds a request to before any handler runs: the host it names, the origin it comes from and,
 * for the SQL endpoints, the kind of body it carries — and what it answers a page whose origin is allowed.
 */
public class RequestGuardWireTest {

    private static final String ALLOWED = "https://localhost:18443";
    private static final String SELECT_ONE = "{\"sql\":\"SELECT 1\"}";

    private static DatabaseHttpServer server;
    private static int port;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @BeforeAll
    public static void startServer() throws IOException {
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
        config.setProperty(EngineConfig.PROP_HTTP_CORS_ALLOWED_ORIGINS, ALLOWED + "/, https://sheets.example.com");
        server = new DatabaseHttpServer(config);
        server.start();
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private static HttpResponse<String> send(final String method, final String path, final String body,
                                             final String... headers) throws Exception {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** A request written by hand, since the JDK's client names the host itself. */
    private static String raw(final String request) throws IOException {
        try (final Socket socket = new Socket("127.0.0.1", port)) {
            final OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            final InputStream in = socket.getInputStream();
            final ByteArrayOutputStream answer = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int read = in.read(buffer);
            while (read > 0) {
                answer.write(buffer, 0, read);
                read = in.read(buffer);
            }
            return new String(answer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String rawGet(final String host) throws IOException {
        return raw("GET /api/health HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n");
    }

    @Test
    public void aDriversRequestIsServed() throws Exception {
        assertEquals(200, send("POST", "/api/execute", SELECT_ONE, "Content-Type", "application/json").statusCode());
        assertEquals(200, send("POST", "/api/execute", SELECT_ONE, "Content-Type",
            "application/json; charset=utf-8").statusCode());
        assertEquals(200, send("GET", "/api/health", null).statusCode());
        // A session is started with no body at all, or with a JSON one.
        assertEquals(200, send("POST", "/api/sessions", null).statusCode());
        assertEquals(200, send("POST", "/api/sessions", "{}", "Content-Type", "application/json").statusCode());
    }

    @Test
    public void aBodyThatIsNotDeclaredAsJsonIsRefused() throws Exception {
        final HttpResponse<String> plain = send("POST", "/api/execute", "{\"sql\":\"CREATE DATABASE guard_probe\"}",
            "Content-Type", "text/plain");
        assertEquals(415, plain.statusCode());
        assertTrue(plain.body().contains("application/json"), plain.body());
        assertEquals(415, send("POST", "/api/execute", "sql=SELECT+1", "Content-Type",
            "application/x-www-form-urlencoded").statusCode());
        assertEquals(415, send("POST", "/api/sessions", "{}", "Content-Type", "text/plain").statusCode());
        // Nothing of the refused statement ran.
        final HttpResponse<String> listed = send("POST", "/api/execute",
            "{\"sql\":\"SHOW DATABASES LIKE 'GUARD_PROBE'\"}", "Content-Type", "application/json");
        assertFalse(listed.body().contains("GUARD_PROBE"), listed.body());
    }

    @Test
    public void aPageOfAnotherOriginIsRefused() throws Exception {
        final HttpResponse<String> post = send("POST", "/api/execute", SELECT_ONE, "Content-Type",
            "application/json", "Origin", "https://evil.example");
        assertEquals(403, post.statusCode());
        assertTrue(post.body().contains("https://evil.example"), post.body());
        assertFalse(post.headers().firstValue("Access-Control-Allow-Origin").isPresent());
        assertEquals(403, send("GET", "/api/health", null, "Origin", "null").statusCode());
        assertEquals(403, send("GET", "/api/v2/databases", null, "Origin", "https://evil.example").statusCode());
        assertEquals(403, send("OPTIONS", "/api/execute", null, "Origin", "https://evil.example",
            "Access-Control-Request-Method", "POST").statusCode());
    }

    @Test
    public void aPageOfAnAllowedOriginIsAnswered() throws Exception {
        final HttpResponse<String> post = send("POST", "/api/execute", SELECT_ONE, "Content-Type",
            "application/json", "Origin", ALLOWED);
        assertEquals(200, post.statusCode());
        assertEquals(ALLOWED, post.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertEquals("Origin", post.headers().firstValue("Vary").orElse(null));
        final HttpResponse<String> preflight = send("OPTIONS", "/api/execute", null, "Origin", ALLOWED,
            "Access-Control-Request-Method", "POST", "Access-Control-Request-Headers", "content-type");
        assertEquals(204, preflight.statusCode());
        assertEquals(ALLOWED, preflight.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertEquals("GET, POST, PUT, DELETE",
            preflight.headers().firstValue("Access-Control-Allow-Methods").orElse(null));
        assertEquals("content-type", preflight.headers().firstValue("Access-Control-Allow-Headers").orElse(null));
        assertEquals("600", preflight.headers().firstValue("Access-Control-Max-Age").orElse(null));
        assertEquals(200, send("GET", "/api/health", null, "Origin", "https://SHEETS.example.com").statusCode());
        // Without an origin a preflight's verb is one the endpoint does not serve.
        assertEquals(405, send("OPTIONS", "/api/execute", null).statusCode());
    }

    @Test
    public void aLoopbackServerAnswersToItsOwnNamesOnly() throws Exception {
        assertTrue(rawGet("localhost:" + port).startsWith("HTTP/1.1 200"));
        assertTrue(rawGet("127.0.0.1:" + port).startsWith("HTTP/1.1 200"));
        assertTrue(rawGet("LOCALHOST").startsWith("HTTP/1.1 200"));
        assertTrue(rawGet("[::1]:" + port).startsWith("HTTP/1.1 200"));
        final String rebound = rawGet("evil.example:" + port);
        assertTrue(rebound.startsWith("HTTP/1.1 421"), rebound);
        assertTrue(rebound.contains("evil.example"), rebound);
    }
}
