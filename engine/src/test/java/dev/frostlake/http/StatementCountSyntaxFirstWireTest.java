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
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Over the HTTP wire, as through the in-process driver, a text that will not parse is refused for its
 * syntax before its statements are counted: the server's own count check asks the parser first, and so
 * does the JDBC driver's {@code jdbc:frostlake://} transport, which gates a pack before sending it.
 */
public class StatementCountSyntaxFirstWireTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static DatabaseHttpServer server;
    private static String baseUrl;
    private static String jdbcUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        baseUrl = "http://localhost:" + port;
        jdbcUrl = "jdbc:frostlake://localhost:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    /** The error one request earns; a null count declares none, leaving the session's to answer. */
    private String errorOf(final String sql, final Integer multiStatementCount) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        if (multiStatementCount != null) {
            body.put("multiStatementCount", multiStatementCount);
        }
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), sql);
        final JsonNode answer = MAPPER.readTree(response.body());
        assertFalse(answer.get("success").asBoolean(), sql + " -> " + response.body());
        assertEquals(0, answer.get("resultSets").size(), sql);
        return answer.get("errorMessage").asText();
    }

    @Test
    public void theServerRefusesATextThatWillNotParseForItsSyntax() throws Exception {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 11 unexpected 'y'.",
            errorOf("SELECT 1 x y; SELECT 2", null));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected 'y'.",
            errorOf("SELECT 1; SELECT 2 x y", null));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 11 unexpected 'y'.",
            errorOf("SELECT 1 x y", Integer.valueOf(2)));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 31 unexpected 'y'.",
            errorOf("SELECT 1; SELECT 2; SELECT 3 x y", Integer.valueOf(2)));
    }

    @Test
    public void theServerCountsATextThatParses() throws Exception {
        assertEquals("Actual statement count 2 did not match the desired statement count 1.",
            errorOf("SELECT 1; SELECT 2", null));
        assertEquals("Actual statement count 2 did not match the desired statement count 1.",
            errorOf("SELECT nosuch FROM nowhere; SELECT 2", null));
        assertEquals("Actual statement count 1 did not match the desired statement count 2.",
            errorOf("SELECT 1", Integer.valueOf(2)));
    }

    @Test
    public void theHttpJdbcTransportRefusesForTheSyntaxFirstToo() throws SQLException {
        try (final Connection connection = DriverManager.getConnection(jdbcUrl);
             final Statement statement = connection.createStatement()) {
            final SQLException syntax = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    statement.execute("SELECT 1; SELECT 2 x y");
                }
            });
            assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected 'y'.",
                syntax.getMessage());
            final SQLException count = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    statement.execute("SELECT 1; SELECT 2");
                }
            });
            assertEquals("Actual statement count 2 did not match the desired statement count 1.",
                count.getMessage());
            assertEquals("0A000", count.getSQLState());
            assertEquals(8, count.getErrorCode());
        }
    }
}
