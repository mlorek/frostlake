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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JDBC driver over HTTP reads what the wire carries: getTimestamp keeps a timestamp's whole fraction
 * of a second while getString and getObject keep the display text they always answered; executeUpdate
 * takes the affected-row count from each result's own mark, so a query naming its column like a count
 * grid counts nothing; and closing a connection releases its server session instead of leaving it to the
 * idle sweep.
 */
public class HttpWireJdbcTest {

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
    public void connect() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl);
        statement = connection.createStatement();
        statement.execute("CREATE OR REPLACE DATABASE wire_jdbc_db");
        statement.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void disconnect() throws SQLException {
        statement.execute("DROP DATABASE IF EXISTS wire_jdbc_db");
        connection.close();
    }

    @Test
    public void aTimestampKeepsItsNanosecondsWhileItsTextStaysTheDisplayText() throws SQLException {
        try (final ResultSet rs = statement.executeQuery("SELECT '2024-01-01 00:00:00.123456789'::TIMESTAMP_NTZ, "
            + "'12:34:56.5'::TIME, '2024-01-01 00:00:00.123456789 +0200'::TIMESTAMP_TZ")) {
            assertTrue(rs.next());
            assertEquals("2024-01-01 00:00:00.123", rs.getString(1));
            assertEquals("2024-01-01 00:00:00.123", rs.getObject(1));
            assertEquals(123456789, rs.getTimestamp(1).getNanos());
            assertEquals("12:34:56", rs.getString(2));
            assertEquals("2024-01-01 00:00:00.123 +0200", rs.getString(3));
            assertEquals(123456789, rs.getTimestamp(3).getNanos());
        }
    }

    @Test
    public void executeUpdateCountsOnlyWhatTheServerMarked() throws SQLException {
        statement.execute("CREATE TABLE t (i INT)");
        assertEquals(2, statement.executeUpdate("INSERT INTO t VALUES (1), (2)"));
        assertEquals(1, statement.executeUpdate("UPDATE t SET i = 3 WHERE i = 1"));
        assertEquals(0, statement.executeUpdate("SELECT 9 AS \"number of rows inserted\""));
    }

    @Test
    public void closingAConnectionReleasesItsSession() throws Exception {
        final int before = activeSessions();
        final Connection extra = DriverManager.getConnection(jdbcUrl);
        extra.createStatement().execute("SELECT 1");
        assertEquals(before + 1, activeSessions());
        extra.close();
        assertEquals(before, activeSessions());
    }

    /** The live session count, read off the endpoint rather than through the driver being tested. */
    private int activeSessions() throws Exception {
        // java.net.http's client, spelled out: this package has an HttpClient of its own.
        final HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
            HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/sessions")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        return Integer.parseInt(response.body().replaceAll("[^0-9]", ""));
    }
}
