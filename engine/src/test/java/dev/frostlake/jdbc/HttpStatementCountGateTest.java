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

import dev.frostlake.http.DatabaseHttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The one-statement gate over the HTTP transport. The server counts what it parses, so a request of one
 * statement cannot carry a second behind a name that holds two dollars, a comment a carriage return ends or a
 * block's word used as a name; the remote driver counts the lexer's tokens before it sends — a block's word followed by
 * a ',' is a name to it too — and refuses such a request itself, with state 0A000.
 */
public class HttpStatementCountGateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TWO_FOR_ONE = "Actual statement count 2 did not match the desired statement count 1.";
    private static DatabaseHttpServer server;
    private static String baseUrl;
    private static String jdbcUrl;

    private Connection connection;
    private Statement statement;

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

    @BeforeEach
    public void threeRows() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl);
        statement = connection.createStatement();
        statement.execute("CREATE OR REPLACE DATABASE count_gate_db");
        statement.execute("USE DATABASE count_gate_db");
        statement.execute("USE SCHEMA PUBLIC");
        statement.execute("CREATE OR REPLACE TABLE sc (a INT)");
        statement.execute("INSERT INTO sc VALUES (1), (2), (3)");
    }

    @AfterEach
    public void dropDatabase() throws SQLException {
        try {
            statement.execute("DROP DATABASE IF EXISTS count_gate_db");
        } finally {
            statement.close();
            connection.close();
        }
    }

    private int rowsLeft() throws SQLException {
        try (final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM count_gate_db.public.sc")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** The error a request of one statement earns over the wire, sent as a client that counts nothing sends it. */
    private String refusalOverTheWire(final String sql) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        body.put("multiStatementCount", Integer.valueOf(1));
        final HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
        final JsonNode error = MAPPER.readTree(response.body()).get("errorMessage");
        return error == null || error.isNull() ? null : error.asText();
    }

    @Test
    public void theServerCountsWhatItParses() throws Exception {
        for (final String text : new String[] {
            "SELECT 1; DELETE FROM count_gate_db.public.sc",
            "SELECT 1 AS a$$b; DELETE FROM count_gate_db.public.sc",
            "SELECT 1 -- x\r; DELETE FROM count_gate_db.public.sc",
            "SELECT 1 // c\r; DELETE FROM count_gate_db.public.sc",
            "SELECT 1 AS a$$b /* $$ */; DELETE FROM count_gate_db.public.sc",
            "SELECT 1 AS begin, 2 AS b; DELETE FROM count_gate_db.public.sc"}) {
            assertEquals(TWO_FOR_ONE, refusalOverTheWire(text), text);
            assertEquals(3, rowsLeft(), text);
        }
    }

    @Test
    public void oneStatementIsOne() throws Exception {
        for (final String text : new String[] {"SELECT 1 AS a$$b", "SELECT 1 -- x\rAS c", "SELECT 'a;b'",
            "SELECT $$a;b$$", "SELECT 1 AS \"a;b\"", "SELECT 1 -- c; DELETE FROM count_gate_db.public.sc",
            "SELECT 1; ;", "BEGIN LET x := 1; RETURN x; END;", "SELECT 1 ->> SELECT 2"}) {
            assertEquals(null, refusalOverTheWire(text), text);
        }
        assertEquals(3, rowsLeft());
    }

    @Test
    public void theRemoteDriverRefusesBeforeItSends() throws SQLException {
        for (final String text : new String[] {"SELECT 1 AS a$$b; DELETE FROM sc", "SELECT 1 -- x\r; DELETE FROM sc",
            "SELECT 1 AS begin, 2 AS b; DELETE FROM sc"}) {
            final SQLException refused = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    statement.execute(text);
                }
            }, text);
            assertEquals(TWO_FOR_ONE, refused.getMessage(), text);
            assertEquals("0A000", refused.getSQLState(), text);
            assertEquals(3, rowsLeft(), text);
        }
    }
}
