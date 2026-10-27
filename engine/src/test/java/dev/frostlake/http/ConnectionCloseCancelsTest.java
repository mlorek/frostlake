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

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Over the HTTP driver {@code Connection.close()} releases the connection's resources at once (java.sql.Connection
 * #close), a statement that another thread runs on it included: the statement is canceled, "SQL execution canceled",
 * and what it had yet to do is not done.
 */
public class ConnectionCloseCancelsTest {

    private static DatabaseHttpServer server;
    private static String address;

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        address = "localhost:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private static String one(final Statement st, final String sql) throws SQLException {
        final ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    private static void pause(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void closingTheConnectionCancelsTheStatementAnotherThreadRuns() throws Exception {
        try (Connection setup = DriverManager.getConnection("jdbc:frostlake://" + address)) {
            final Statement st = setup.createStatement();
            st.execute("CREATE DATABASE IF NOT EXISTS ccl_db");
            st.execute("CREATE OR REPLACE TABLE ccl_db.public.t (a INT)");
        }
        final Connection connection = DriverManager.getConnection("jdbc:frostlake://" + address);
        final Statement running = connection.createStatement();
        // The session the cancel names is the connection's once a request has answered.
        assertEquals("1", one(running, "SELECT 1"));
        final AtomicReference<SQLException> refused = new AtomicReference<>();
        final Thread runner = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    running.execute("BEGIN SELECT SYSTEM$WAIT(30); INSERT INTO ccl_db.public.t VALUES (1); END");
                } catch (final SQLException e) {
                    refused.set(e);
                }
            }
        });
        runner.start();
        // The block takes the connection before it closes.
        pause(300);
        connection.close();
        runner.join();
        assertNotNull(refused.get());
        assertEquals("SQL execution canceled", refused.get().getMessage());
        try (Connection check = DriverManager.getConnection("jdbc:frostlake://" + address)) {
            assertEquals("0", one(check.createStatement(), "SELECT COUNT(*) FROM ccl_db.public.t"));
        }
    }
}
