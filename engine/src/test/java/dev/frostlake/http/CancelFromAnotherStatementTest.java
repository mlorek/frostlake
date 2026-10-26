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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Over the HTTP driver a statement object's {@code cancel()} cancels that object's own statement alone (a JDBC
 * Statement's cancel cancels "this Statement object"): one whose statement waits behind another object's on the same
 * connection cancels nothing of it, however often it asks.
 */
public class CancelFromAnotherStatementTest {

    private static final Logger logger = LoggerFactory.getLogger(CancelFromAnotherStatementTest.class);

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
    public void aStatementWaitingBehindAnotherCancelsNothingOfIt() throws Exception {
        final Connection connection = DriverManager.getConnection("jdbc:frostlake://" + address);
        try {
            final Statement first = connection.createStatement();
            final Statement second = connection.createStatement();
            // The session the cancels name is the connection's once a request has answered.
            assertEquals("1", one(first, "SELECT 1"));
            final AtomicReference<String> answered = new AtomicReference<>();
            final AtomicReference<SQLException> refused = new AtomicReference<>();
            final AtomicBoolean done = new AtomicBoolean();
            final Thread firstRunner = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        answered.set(one(first, "SELECT SYSTEM$WAIT(2)"));
                    } catch (final SQLException e) {
                        refused.set(e);
                    } finally {
                        done.set(true);
                    }
                }
            });
            firstRunner.start();
            // The first statement takes the connection; the second waits behind it, and asks to cancel meanwhile.
            pause(300);
            final Thread secondRunner = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        one(second, "SELECT 'second'");
                    } catch (final SQLException e) {
                        // Its own statement, should a cancel reach it once the first is through.
                        logger.debug("The second statement was canceled: {}", e.getMessage());
                    }
                }
            });
            secondRunner.start();
            while (!done.get()) {
                second.cancel();
                pause(20);
            }
            firstRunner.join();
            secondRunner.join();
            assertNull(refused.get());
            assertEquals("waited 2 seconds", answered.get());
            assertEquals("1", one(second, "SELECT 1"));
        } finally {
            connection.close();
        }
    }
}
