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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HTTP transport's session protocol, against a server that answers from a script and records what it
 * was sent. {@code requireSession} goes out only once the server has answered {@code newSession}, and never
 * to a server that predates it. A session the server refuses (404) is replaced on the connection's scope and
 * the statement sent once more when it held nothing; when it held a transaction or context the statement is
 * not re-run and the loss is reported. Closing releases the session once, bounded, and never fails.
 */
public class HttpSessionProtocolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String url;
    // Guarded by this: the scripted answers to POST /api/execute, and what the server saw.
    private final Deque<String[]> script = new ArrayDeque<>();
    private final List<JsonNode> executes = new ArrayList<>();
    private final List<String> released = new ArrayList<>();
    private int releaseStatus = 200;
    // Holds a DELETE unanswered until released, standing in for a server that never answers.
    private CountDownLatch releaseGate;

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(final HttpExchange exchange) throws IOException {
                answer(exchange);
            }
        });
        server.start();
        url = "jdbc:frostlake://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    public void stopServer() {
        final CountDownLatch gate = releaseGate;
        if (gate != null) {
            gate.countDown();
        }
        server.stop(0);
    }

    private void answer(final HttpExchange exchange) throws IOException {
        final String method = exchange.getRequestMethod();
        final String path = exchange.getRequestURI().getPath();
        final byte[] body = exchange.getRequestBody().readAllBytes();
        int status = 200;
        String reply = "{\"status\":\"ok\"}";
        CountDownLatch hold = null;
        synchronized (this) {
            if ("DELETE".equals(method)) {
                released.add(path);
                status = releaseStatus;
                reply = "{\"success\":" + (status == 200) + "}";
                hold = releaseGate;
            } else if ("POST".equals(method) && "/api/execute".equals(path)) {
                executes.add(MAPPER.readTree(body));
                final String[] next = script.poll();
                if (next == null) {
                    status = 500;
                    reply = "{\"success\":false,\"errorMessage\":\"unscripted\",\"sessionId\":null}";
                } else {
                    status = Integer.parseInt(next[0]);
                    reply = next[1];
                }
            }
        }
        if (hold != null) {
            try {
                hold.await(60, TimeUnit.SECONDS);
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        final byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (final OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** The statement ran, answered by a server that reports newSession. */
    private synchronized void ran(final String sessionId, final boolean newSession) {
        script.add(new String[] {"200", "{\"success\":true,\"sessionId\":\"" + sessionId + "\",\"newSession\":"
            + newSession + ",\"errorMessage\":null,\"executionTimeMs\":1,\"resultSets\":[]}"});
    }

    /** The statement ran, answered by a server that predates newSession. */
    private synchronized void ranOnOlderServer(final String sessionId) {
        script.add(new String[] {"200", "{\"success\":true,\"sessionId\":\"" + sessionId
            + "\",\"errorMessage\":null,\"executionTimeMs\":1,\"resultSets\":[]}"});
    }

    /** The server no longer holds the session named, and refuses: nothing ran. */
    private synchronized void gone(final String sessionId) {
        script.add(new String[] {"404", "{\"success\":false,\"sessionId\":null,\"errorMessage\":\"Session '"
            + sessionId + "' does not exist\"}"});
    }

    private synchronized JsonNode sent(final int index) {
        return executes.get(index);
    }

    private synchronized int sentCount() {
        return executes.size();
    }

    private synchronized List<String> releases() {
        return new ArrayList<>(released);
    }

    private String sqlOf(final int index) {
        return sent(index).get("sql").asText();
    }

    private String sessionOf(final int index) {
        final JsonNode id = sent(index).get("sessionId");
        return id == null || id.isNull() ? null : id.asText();
    }

    private boolean requiresSession(final int index) {
        final JsonNode require = sent(index).get("requireSession");
        return require != null && require.asBoolean();
    }

    private Connection connect(final String path) throws SQLException {
        return DriverManager.getConnection(url + path);
    }

    private static FrostlakeSessionLostException lostOn(final Statement statement, final String sql) {
        return assertThrows(FrostlakeSessionLostException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        });
    }

    @Test
    public void requireSessionGoesOutOnceTheServerHasAnsweredNewSession() throws SQLException {
        ran("s1", true);
        ran("s1", false);
        try (final Connection connection = connect("/DB"); final Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
        }
        assertEquals("USE DATABASE DB", sqlOf(0));
        assertNull(sessionOf(0));
        assertFalse(sent(0).has("requireSession"), sent(0).toString());
        assertEquals("s1", sessionOf(1));
        assertTrue(requiresSession(1), sent(1).toString());
        assertEquals(List.of("/api/sessions/s1"), releases());
    }

    @Test
    public void anOlderServerIsSentNeitherRequireSessionNorARelease() throws SQLException {
        ranOnOlderServer("s1");
        ranOnOlderServer("s1");
        try (final Connection connection = connect("/DB"); final Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
        }
        assertEquals("s1", sessionOf(1));
        assertFalse(sent(1).has("requireSession"), sent(1).toString());
        assertEquals(List.of(), releases());
    }

    @Test
    public void aLostSessionThatHeldNothingIsReplacedOnTheScopeAndTheStatementSentOnce() throws SQLException {
        ran("s1", true);
        gone("s1");
        ran("s2", true);
        ran("s2", false);
        try (final Connection connection = connect("/DB"); final Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            assertEquals("s2", ((DatabaseConnection) connection).getSessionId());
        }
        assertEquals(4, sentCount());
        assertEquals("SELECT 1", sqlOf(1));
        assertEquals("s1", sessionOf(1));
        assertEquals("USE DATABASE DB", sqlOf(2));
        assertNull(sessionOf(2));
        assertEquals("SELECT 1", sqlOf(3));
        assertEquals("s2", sessionOf(3));
        assertTrue(requiresSession(3));
    }

    @Test
    public void aReplacementTheServerAlsoLosesIsReportedRatherThanReplacedAgain() throws SQLException {
        ran("s1", true);
        gone("s1");
        ran("s2", true);
        gone("s2");
        try (final Connection connection = connect("/DB"); final Statement statement = connection.createStatement()) {
            final FrostlakeSessionLostException lost = lostOn(statement, "SELECT 1");
            assertEquals("08003", lost.getSQLState());
            assertFalse(lost.isTransactionLost());
        }
        assertEquals(4, sentCount());
    }

    @Test
    public void aLostTransactionIsReportedAndTheStatementIsNotRerun() throws SQLException {
        ran("s1", true);
        ran("s1", false);
        ran("s1", false);
        gone("s1");
        ran("s2", true);
        ran("s2", false);
        try (final Connection connection = connect("/DB"); final Statement statement = connection.createStatement()) {
            statement.execute("BEGIN");
            statement.execute("INSERT INTO t VALUES (1)");
            final FrostlakeSessionLostException lost = lostOn(statement, "INSERT INTO t VALUES (2)");
            assertTrue(lost.isTransactionLost());
            assertTrue(lost.getMessage().contains("s1"), lost.getMessage());
            assertEquals(4, sentCount());
            // The connection goes on: the next statement starts a new session on the connection's scope.
            statement.execute("SELECT 1");
        }
        assertEquals("USE DATABASE DB", sqlOf(4));
        assertNull(sessionOf(4));
        assertEquals("SELECT 1", sqlOf(5));
        assertEquals("s2", sessionOf(5));
    }

    @Test
    public void contextSetUpOnTheSessionIsReportedLostRatherThanRebuiltElsewhere() throws SQLException {
        ran("s1", true);
        ran("s1", false);
        gone("s1");
        try (final Connection connection = connect("/DB"); final Statement statement = connection.createStatement()) {
            statement.execute("USE ROLE ANALYST");
            assertFalse(lostOn(statement, "SELECT 1").isTransactionLost());
        }
        assertEquals(3, sentCount());
    }

    @Test
    public void aPlainUseBecomesTheScopeTheReplacementGets() throws SQLException {
        ran("s1", true);
        ran("s1", false);
        gone("s1");
        ran("s2", true);
        ran("s2", false);
        ran("s2", false);
        try (final Connection connection = connect("/DB"); final Statement statement = connection.createStatement()) {
            statement.execute("USE SCHEMA S2");
            statement.execute("SELECT 1");
        }
        assertEquals("USE DATABASE DB", sqlOf(3));
        assertEquals("USE SCHEMA S2", sqlOf(4));
        assertEquals("SELECT 1", sqlOf(5));
        assertEquals("s2", sessionOf(5));
    }

    @Test
    public void manualCommitGoesOntoTheReplacementAndRollingBackALostTransactionSucceeds() throws SQLException {
        ran("s1", true);
        ran("s1", false);
        ran("s1", false);
        gone("s1");
        ran("s2", true);
        ran("s2", false);
        ran("s2", false);
        ran("s2", false);
        gone("s2");
        try (final Connection connection = connect("/DB"); final Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            connection.commit();
            statement.execute("SELECT 1");
            statement.execute("INSERT INTO t VALUES (1)");
            // The transaction went with the session, and the server rolled it back when it dropped it.
            connection.rollback();
            assertFalse(connection.getAutoCommit());
        }
        final String[] expected = {"USE DATABASE DB", "ALTER SESSION SET AUTOCOMMIT = FALSE", "COMMIT", "SELECT 1",
            "USE DATABASE DB", "ALTER SESSION SET AUTOCOMMIT = FALSE", "SELECT 1", "INSERT INTO t VALUES (1)",
            "ROLLBACK"};
        assertEquals(expected.length, sentCount());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], sqlOf(i), "request " + i);
        }
    }

    @Test
    public void aSessionTheCallerNamedIsNeitherReplacedNorReleased() throws SQLException {
        gone("shared");
        ran("own", true);
        final Properties properties = new Properties();
        properties.setProperty("sessionId", "shared");
        try (final Connection connection = DriverManager.getConnection(url, properties);
                final Statement statement = connection.createStatement()) {
            assertFalse(lostOn(statement, "SELECT 1").isTransactionLost());
            statement.execute("SELECT 2");
        }
        assertEquals("shared", sessionOf(0));
        assertNull(sessionOf(1));
        // Only the session this connection started is released.
        assertEquals(List.of("/api/sessions/own"), releases());
    }

    @Test
    public void closingReleasesOnceAndNeverFails() throws SQLException {
        ran("s1", true);
        releaseStatus = 404;
        final Connection connection = connect("/DB");
        connection.close();
        connection.close();
        assertEquals(List.of("/api/sessions/s1"), releases());
    }

    @Test
    @Timeout(60)
    public void closingDoesNotWaitOnAServerThatNeverAnswers() throws SQLException {
        ran("s1", true);
        releaseGate = new CountDownLatch(1);
        final Connection connection = connect("/DB");
        connection.close();
        assertTrue(connection.isClosed());
        assertEquals(List.of("/api/sessions/s1"), releases());
    }
}
