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
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JDBC connection over HTTP whose server session goes away — released here out of band, as an idle
 * expiry or a restart would — carries on where it can and says so where it cannot. A session that held
 * nothing is replaced on the connection's own database and schema and the statement runs; one that held
 * a transaction reports the loss with SQLState 08003 and the statement never runs; in manual-commit mode
 * the replacement is put in manual-commit mode too. The server itself now refuses a request naming a
 * session it no longer holds unless the request asks for a fresh one.
 */
public class HttpSessionRecoveryTest {

    private static DatabaseHttpServer server;
    private static String baseUrl;
    private static String jdbcUrl;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    public static void startServer() throws IOException, SQLException {
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        baseUrl = "http://localhost:" + port;
        jdbcUrl = "jdbc:frostlake://localhost:" + port;
        try (final Connection admin = DriverManager.getConnection(jdbcUrl);
                final Statement statement = admin.createStatement()) {
            statement.execute("CREATE OR REPLACE DATABASE REC_DB");
            statement.execute("CREATE SCHEMA REC_DB.WORK");
            statement.execute("CREATE TABLE REC_DB.WORK.T (I INT)");
        }
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    /** Releases a connection's session behind its back, as an idle expiry or a restart would. */
    private void releaseBehindItsBack(final Connection connection) throws Exception {
        final String id = ((DatabaseConnection) connection).getSessionId();
        final HttpResponse<String> released = http.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/sessions/" + id)).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, released.statusCode(), released.body());
    }

    private static String text(final Statement statement, final String sql) throws SQLException {
        try (final ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getString(1);
        }
    }

    private static long rowsInT() throws SQLException {
        try (final Connection admin = DriverManager.getConnection(jdbcUrl);
                final Statement statement = admin.createStatement()) {
            return Long.parseLong(text(statement, "SELECT COUNT(*) FROM REC_DB.WORK.T"));
        }
    }

    private static FrostlakeSessionLostException lostOn(final Executable action) {
        final FrostlakeSessionLostException lost = assertThrows(FrostlakeSessionLostException.class, action);
        assertEquals("08003", lost.getSQLState());
        return lost;
    }

    @Test
    public void aSessionThatHeldNothingIsReplacedOnTheConnectionsScope() throws Exception {
        try (final Connection connection = DriverManager.getConnection(jdbcUrl + "/REC_DB?schema=WORK");
                final Statement statement = connection.createStatement()) {
            assertEquals("REC_DB.WORK", text(statement, "SELECT CURRENT_DATABASE() || '.' || CURRENT_SCHEMA()"));
            final String first = ((DatabaseConnection) connection).getSessionId();
            releaseBehindItsBack(connection);
            assertEquals("REC_DB.WORK", text(statement, "SELECT CURRENT_DATABASE() || '.' || CURRENT_SCHEMA()"));
            assertNotEquals(first, ((DatabaseConnection) connection).getSessionId());
        }
    }

    @Test
    public void aTransactionThatWentWithTheSessionIsReportedAndNothingRuns() throws Exception {
        final long before = rowsInT();
        try (final Connection connection = DriverManager.getConnection(jdbcUrl + "/REC_DB?schema=WORK");
                final Statement statement = connection.createStatement()) {
            statement.execute("BEGIN");
            statement.execute("INSERT INTO T VALUES (1)");
            releaseBehindItsBack(connection);
            final FrostlakeSessionLostException lost = lostOn(new Executable() {
                @Override
                public void execute() throws SQLException {
                    statement.execute("INSERT INTO T VALUES (2)");
                }
            });
            assertTrue(lost.isTransactionLost());
            // Neither insert landed: the first was rolled back with the session, the second never ran.
            assertEquals(before, rowsInT());
            // The connection goes on, on its own scope.
            assertEquals("REC_DB.WORK", text(statement, "SELECT CURRENT_DATABASE() || '.' || CURRENT_SCHEMA()"));
        }
    }

    @Test
    public void manualCommitSurvivesTheLossOfItsSession() throws Exception {
        final long before = rowsInT();
        try (final Connection connection = DriverManager.getConnection(jdbcUrl + "/REC_DB?schema=WORK");
                final Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("INSERT INTO T VALUES (3)");
            releaseBehindItsBack(connection);
            assertTrue(lostOn(new Executable() {
                @Override
                public void execute() throws SQLException {
                    connection.commit();
                }
            }).isTransactionLost());
            assertFalse(connection.getAutoCommit());

            // The replacement session is in manual-commit mode as well: an insert waits for commit().
            statement.execute("INSERT INTO T VALUES (4)");
            assertEquals(before, rowsInT());
            connection.commit();
            assertEquals(before + 1, rowsInT());
        }
    }

    @Test
    public void setSchemaMovesWhatGetSchemaReports() throws SQLException {
        try (final Connection connection = DriverManager.getConnection(jdbcUrl + "/REC_DB")) {
            connection.setSchema("WORK");
            assertEquals("WORK", connection.getSchema());
        }
    }

    @Test
    public void aSessionIdTheCallerChoseIsRefusedWhenTheServerHoldsNone() throws SQLException {
        final Properties properties = new Properties();
        properties.setProperty("sessionId", "no-such-session");
        try (final Connection connection = DriverManager.getConnection(jdbcUrl, properties);
                final Statement statement = connection.createStatement()) {
            lostOn(new Executable() {
                @Override
                public void execute() throws SQLException {
                    statement.execute("SELECT 1");
                }
            });
            // The next statement runs in a session of the connection's own.
            assertEquals("1", text(statement, "SELECT 1"));
        }
    }
}
