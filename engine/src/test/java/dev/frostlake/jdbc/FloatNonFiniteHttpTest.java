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
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A FLOAT's NaN and infinities over the HTTP driver: the wire spells them as strings, and the driver reads them back as
 * the doubles they name, as {@link FloatNonFiniteReadTest} pins for the in-process driver and the account's.
 */
public class FloatNonFiniteHttpTest {

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

    @Test
    public void nonFiniteFloatsAreDoubles() throws SQLException {
        final Connection connection = DriverManager.getConnection(jdbcUrl);
        try {
            final Statement statement = connection.createStatement();
            statement.execute("CREATE DATABASE IF NOT EXISTS nan_db");
            statement.execute("USE SCHEMA nan_db.PUBLIC");
            assertEquals(FloatNonFiniteSupport.EXPECTED, FloatNonFiniteSupport.read(connection));
            statement.execute("DROP DATABASE IF EXISTS nan_db");
            statement.close();
        } finally {
            connection.close();
        }
    }
}
