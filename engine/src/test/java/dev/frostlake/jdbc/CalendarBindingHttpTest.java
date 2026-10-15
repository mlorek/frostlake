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

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The JDBC Calendar overloads over the HTTP driver: the answers {@link CalendarBindingTest} pins for the in-process one
 * and the account's driver.
 */
public class CalendarBindingHttpTest {

    private static DatabaseHttpServer server;
    private static String jdbcUrl;
    private Connection connection;

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
        final Statement statement = connection.createStatement();
        statement.execute("CREATE DATABASE IF NOT EXISTS cal_db");
        statement.execute("USE SCHEMA cal_db.PUBLIC");
        statement.close();
    }

    @AfterEach
    public void disconnect() throws SQLException {
        final Statement statement = connection.createStatement();
        statement.execute("DROP DATABASE IF EXISTS cal_db");
        statement.close();
        connection.close();
    }

    @Test
    public void inAUtcSession() throws SQLException {
        assertEquals(Arrays.asList(
            "2024-01-01 00:00:00.000|2024-01-01|23:30:00|2024-01-01 00:00:00.000 Z|2024-01-01 00:00:00.000 Z",
            "2024-01-01 05:30:00.000|2024-01-02|23:30:00|2024-01-01 05:30:00.000 Z|2024-01-01 05:30:00.000 Z",
            "2024-01-01T00:00:00Z 2023-12-31T18:30:00Z 1970-01-01T14:30:00Z 2024-01-01T00:00:00Z 2024-01-01T00:00:00Z"
                + " 2024-01-01T00:00:00Z",
            "2024-01-01T05:30:00Z 2024-01-01T00:00:00Z 1970-01-01T14:30:00Z 2024-01-01T05:30:00Z 2024-01-01T05:30:00Z"
                + " 2024-01-02T00:00:00Z"),
            CalendarBindingSupport.observe(connection, "UTC"));
    }

    @Test
    public void bindingsIgnoreTheSessionInputFormats() throws SQLException {
        assertEquals("2024-03-05 10:00:00.000|2024-03-05|10:15:00",
            CalendarBindingSupport.underInputFormats(connection));
    }

    @Test
    public void inANewYorkSession() throws SQLException {
        assertEquals(Arrays.asList(
            "2023-12-31 19:00:00.000|2024-01-01|23:30:00|2023-12-31 19:00:00.000 -0500|2023-12-31 19:00:00.000 -0500",
            "2024-01-01 00:30:00.000|2024-01-02|23:30:00|2024-01-01 00:30:00.000 -0500|2024-01-01 00:30:00.000 -0500",
            "2023-12-31T19:00:00Z 2023-12-31T13:30:00Z 1970-01-01T14:30:00Z 2024-01-01T00:00:00Z 2024-01-01T00:00:00Z",
            "2024-01-01T00:30:00Z 2023-12-31T19:00:00Z 1970-01-01T14:30:00Z 2024-01-01T05:30:00Z 2024-01-01T05:30:00Z"),
            CalendarBindingSupport.withoutDateReads(CalendarBindingSupport.observe(connection, "America/New_York")));
    }
}
