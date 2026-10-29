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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The HTTP driver ends a statement as the in-process driver does: by its query timeout, or by {@code cancel()} from
 * another thread, both "SQL execution canceled" with SQLSTATE 57014 and code 604, and by the session's
 * STATEMENT_TIMEOUT_IN_SECONDS with code 630; the statement object runs the next statement as usual. On the wire a
 * request carries {@code queryTimeout}, {@code POST /api/sessions/{id}/cancel} cancels what the session runs, and a
 * canceled statement's answer carries {@code sqlState} and {@code errorCode}.
 */
public class StatementCancelHttpTest {

    private static final String CANCELED = "SQL execution canceled";
    private static final String TIMED_OUT =
        "Statement reached its statement or warehouse timeout of 1 second(s) and was canceled.";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static DatabaseHttpServer server;
    private static String baseUrl;

    private final HttpClient client = HttpClient.newHttpClient();
    private Connection connection;

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    public void connect() throws SQLException {
        connection = DriverManager.getConnection("jdbc:frostlake://" + baseUrl.substring("http://".length()));
    }

    @AfterEach
    public void disconnect() throws SQLException {
        connection.close();
    }

    private static void refused(final Statement st, final String sql, final String message, final int code) {
        try {
            st.execute(sql);
            fail("ran: " + sql);
        } catch (final SQLException e) {
            assertEquals(message, e.getMessage());
            assertEquals("57014", e.getSQLState());
            assertEquals(code, e.getErrorCode());
        }
    }

    private static String one(final Statement st, final String sql) throws SQLException {
        final ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    private JsonNode post(final String path, final String body, final int status) throws Exception {
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), path + " " + body);
        return MAPPER.readTree(response.body());
    }

    private JsonNode execute(final String sql, final Integer queryTimeout) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        if (queryTimeout != null) {
            body.put("queryTimeout", queryTimeout);
        }
        return post("/api/execute", MAPPER.writeValueAsString(body), 200);
    }

    @Test
    public void aQueryTimeoutCancelsTheStatement() throws SQLException {
        final Statement timed = connection.createStatement();
        try {
            assertEquals(0, timed.getQueryTimeout());
            timed.setQueryTimeout(1);
            assertEquals(1, timed.getQueryTimeout());
            refused(timed, "SELECT SYSTEM$WAIT(3)", CANCELED, 604);
            timed.setQueryTimeout(0);
            assertEquals("2", one(timed, "SELECT 2"));
            // A negative timeout is kept as given and bounds nothing, as the account's driver keeps it.
            timed.setQueryTimeout(-1);
            assertEquals(-1, timed.getQueryTimeout());
            assertEquals("3", one(timed, "SELECT 3"));
        } finally {
            timed.close();
        }
    }

    @Test
    public void theSessionsTimeoutCarriesItsCode() throws SQLException {
        final Statement st = connection.createStatement();
        try {
            st.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 1");
            refused(st, "SELECT SYSTEM$WAIT(3)", TIMED_OUT, 630);
            st.execute("ALTER SESSION UNSET STATEMENT_TIMEOUT_IN_SECONDS");
            assertEquals("waited 1 seconds", one(st, "SELECT SYSTEM$WAIT(1)"));
        } finally {
            st.close();
        }
    }

    @Test
    public void cancelEndsTheRunningStatement() throws Exception {
        final Statement canceled = connection.createStatement();
        try {
            canceled.cancel();
            assertEquals("1", one(canceled, "SELECT 1"));
            final AtomicBoolean done = new AtomicBoolean();
            final Thread canceller = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!done.get()) {
                            canceled.cancel();
                            Thread.sleep(100);
                        }
                    } catch (final InterruptedException | SQLException ignored) {
                        // the assertion below reports a statement that ran to its end
                    }
                }
            });
            canceller.start();
            try {
                refused(canceled, "SELECT SYSTEM$WAIT(30)", CANCELED, 604);
            } finally {
                done.set(true);
                canceller.join(10000);
            }
            assertEquals("3", one(canceled, "SELECT 3"));
        } finally {
            canceled.close();
        }
    }

    @Test
    public void theWireCarriesTheCancel() throws Exception {
        final JsonNode timedOut = execute("SELECT SYSTEM$WAIT(3)", Integer.valueOf(1));
        assertFalse(timedOut.get("success").asBoolean(), timedOut.toString());
        assertEquals(CANCELED, timedOut.get("errorMessage").asText());
        assertEquals("57014", timedOut.get("sqlState").asText());
        assertEquals(604, timedOut.get("errorCode").asInt());

        final JsonNode failed = execute("SELECT * FROM nosuch_cancel_t", null);
        assertFalse(failed.get("success").asBoolean(), failed.toString());
        // Any other failure carries its family's pair: a missing table's 42S02 with 2003 (live-verified).
        assertEquals("42S02", failed.get("sqlState").asText(), failed.toString());
        assertEquals(2003, failed.get("errorCode").asInt(), failed.toString());

        final String idle = post("/api/sessions", "", 200).get("sessionId").asText();
        assertTrue(post("/api/sessions/" + idle + "/cancel", "", 200).get("success").asBoolean());
        assertFalse(post("/api/sessions/nosuch-session/cancel", "", 404).get("success").asBoolean());
    }
}
