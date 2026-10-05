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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLIENT_SESSION_KEEP_ALIVE over the HTTP driver, as the account's drivers keep a session alive: with it on, the
 * connection sends a heartbeat every CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY seconds (kept within 900 to 3600),
 * whether a connection property, the URL or the session's own ALTER SESSION turns it on, and closing stops it.
 */
public class ClientSessionKeepAliveTest {

    private static DatabaseHttpServer server;
    private static String jdbcUrl;

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        jdbcUrl = "jdbc:frostlake://localhost:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private static Connection connect(final String url, final String... properties) throws SQLException {
        final Properties info = new Properties();
        for (int i = 0; i + 1 < properties.length; i += 2) {
            info.setProperty(properties[i], properties[i + 1]);
        }
        return DriverManager.getConnection(url, info);
    }

    private static HttpClient transport(final Connection connection) {
        return ((DatabaseConnection) connection).transport();
    }

    private static void run(final Connection connection, final String sql) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Test
    public void thePropertyStartsTheHeartbeatOnceThereIsASession() throws SQLException {
        try (Connection connection = connect(jdbcUrl, "CLIENT_SESSION_KEEP_ALIVE", "true")) {
            assertEquals(0, transport(connection).heartbeatPeriodSeconds(), "no session yet, so nothing to keep alive");
            run(connection, "SELECT 1");
            assertEquals(3600, transport(connection).heartbeatPeriodSeconds(), "the default frequency");
            connection.close();
            assertEquals(0, transport(connection).heartbeatPeriodSeconds(), "closing stops it");
        }
    }

    @Test
    public void theFrequencyIsKeptWithinNineHundredToThirtySixHundredSeconds() throws SQLException {
        try (Connection low = connect(jdbcUrl, "client_session_keep_alive", "TRUE",
                "CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY", "60");
             Connection high = connect(jdbcUrl, "CLIENT_SESSION_KEEP_ALIVE", "true",
                "CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY", "5000")) {
            run(low, "SELECT 1");
            run(high, "SELECT 1");
            assertEquals(900, transport(low).heartbeatPeriodSeconds());
            assertEquals(3600, transport(high).heartbeatPeriodSeconds());
        }
    }

    @Test
    public void theUrlCanTurnItOn() throws SQLException {
        try (Connection connection = connect(jdbcUrl
                + "?CLIENT_SESSION_KEEP_ALIVE=true&CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY=1200")) {
            run(connection, "SELECT 1");
            assertEquals(1200, transport(connection).heartbeatPeriodSeconds());
        }
    }

    @Test
    public void theSessionsOwnAlterSessionTurnsItOnAndOff() throws SQLException {
        try (Connection connection = connect(jdbcUrl)) {
            run(connection, "SELECT 1");
            assertEquals(0, transport(connection).heartbeatPeriodSeconds(), "off unless something turns it on");
            run(connection, "ALTER SESSION SET CLIENT_SESSION_KEEP_ALIVE = TRUE");
            assertEquals(3600, transport(connection).heartbeatPeriodSeconds());
            run(connection, "ALTER SESSION SET CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY = 1800");
            assertEquals(1800, transport(connection).heartbeatPeriodSeconds());
            run(connection, "ALTER SESSION SET CLIENT_SESSION_KEEP_ALIVE = FALSE");
            assertEquals(0, transport(connection).heartbeatPeriodSeconds());
        }
        try (Connection connection = connect(jdbcUrl, "CLIENT_SESSION_KEEP_ALIVE", "true")) {
            run(connection, "ALTER SESSION SET CLIENT_SESSION_KEEP_ALIVE = FALSE");
            assertEquals(0, transport(connection).heartbeatPeriodSeconds(), "the session's setting wins");
            run(connection, "ALTER SESSION UNSET CLIENT_SESSION_KEEP_ALIVE");
            assertEquals(3600, transport(connection).heartbeatPeriodSeconds(), "unset, the property is in force again");
        }
    }

    @Test
    public void aHeartbeatKeepsTheSessionPastTheIdleTimeout() throws Exception {
        try (Connection kept = connect(jdbcUrl, "CLIENT_SESSION_KEEP_ALIVE", "true");
             Connection idle = connect(jdbcUrl)) {
            run(kept, "SET marker = 'kept'");
            run(idle, "SET marker = 'idle'");
            final String keptSession = transport(kept).getSessionId();
            Thread.sleep(120);
            assertTrue(transport(kept).sendHeartbeat());
            server.getEngine().expireIdleSessions(100);
            try (Statement statement = kept.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT $marker")) {
                assertTrue(rows.next());
                assertEquals("kept", rows.getString(1), "the session and its variable survived");
            }
            assertEquals(keptSession, transport(kept).getSessionId());
            assertEquals(false, transport(idle).sendHeartbeat(), "the idle one is gone");
        }
    }
}
