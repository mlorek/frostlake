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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BOOLEAN through the HTTP transport prints as it does in process: {@code getString} is the
 * account driver's upper-case {@code TRUE} / {@code FALSE} for a BOOLEAN column, while a VARCHAR or
 * VARIANT column holding a converted boolean keeps SQL's lower-case text. The wire itself carries
 * the JSON {@code true} / {@code false}, which every other client reads as its own boolean.
 */
public class BooleanTextHttpTransportTest {

    private static DatabaseHttpServer server;
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
        statement.execute("CREATE OR REPLACE DATABASE test_db");
        statement.execute("USE DATABASE test_db");
        statement.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void disconnect() throws SQLException {
        statement.execute("DROP DATABASE IF EXISTS test_db");
        statement.close();
        connection.close();
    }

    @Test
    public void aBooleanColumnPrintsUpperCaseOverHttp() throws SQLException {
        statement.execute("CREATE TABLE bt (b BOOLEAN, v VARIANT, s VARCHAR)");
        statement.execute("INSERT INTO bt SELECT TRUE, TO_VARIANT(FALSE), TRUE");
        final ResultSet rs = statement.executeQuery(
            "SELECT b, v, s, 1 = 2, NULL::BOOLEAN, TO_VARCHAR(b) FROM bt");
        assertTrue(rs.next());
        assertEquals("TRUE", rs.getString(1));
        assertEquals("false", rs.getString(2));
        assertEquals("true", rs.getString(3));
        assertEquals("FALSE", rs.getString(4));
        assertNull(rs.getString(5));
        assertTrue(rs.wasNull());
        assertEquals("true", rs.getString(6));
        assertEquals(Boolean.TRUE, rs.getObject(1));
        assertTrue(rs.getBoolean(1));
        assertFalse(rs.getBoolean(4));
        rs.close();
    }
}
