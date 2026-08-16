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
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch failure surface over the HTTP transport, matching the direct transport and live:
 * statement batches run every entry and throw {@link BatchUpdateException} with the first
 * failure's message and the complete update-count array; prepared batches surface the failing
 * statement's own {@link SQLException}, stop at the failure, and report real update counts on
 * success; mixed bind types are refused at {@code addBatch}.
 */
public class HttpBatchFailureTest {

    private static DatabaseHttpServer server;
    private static String jdbcUrl;

    private Connection connection;
    private Statement statement;

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
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
    public void setup() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl);
        statement = connection.createStatement();
        statement.execute("CREATE OR REPLACE DATABASE http_batch_db");
        statement.execute("USE DATABASE http_batch_db");
        statement.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void teardown() throws SQLException {
        try {
            statement.execute("DROP DATABASE IF EXISTS http_batch_db");
        } catch (final SQLException e) {
            // best-effort cleanup
        }
        statement.close();
        connection.close();
    }

    @Test
    public void testStatementBatchFailureContinuesAndThrowsBatchUpdateException() throws SQLException {
        statement.execute("CREATE TABLE hb1 (id INTEGER)");

        statement.addBatch("INSERT INTO hb1 VALUES (20)");
        statement.addBatch("INSERT INTO missing_t VALUES (1)");
        statement.addBatch("INSERT INTO hb1 VALUES (21)");

        final BatchUpdateException e = assertThrows(BatchUpdateException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.executeBatch();
            }
        });
        assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
        assertEquals(3, e.getUpdateCounts().length);
        assertEquals(1, e.getUpdateCounts()[0]);
        assertEquals(Statement.EXECUTE_FAILED, e.getUpdateCounts()[1]);
        assertEquals(1, e.getUpdateCounts()[2]);

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM hb1");
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1));
        rs.close();
    }

    @Test
    public void testPreparedBatchRealCountsOnSuccess() throws SQLException {
        statement.execute("CREATE TABLE hb2 (id INTEGER)");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO hb2 VALUES (?)");
        pstmt.setInt(1, 1);
        pstmt.addBatch();
        pstmt.setInt(1, 2);
        pstmt.addBatch();

        final int[] counts = pstmt.executeBatch();
        assertEquals(2, counts.length);
        assertEquals(1, counts[0]);
        assertEquals(1, counts[1]);
        pstmt.close();
    }

    @Test
    public void testPreparedBatchFailureIsThePlainStatementException() throws SQLException {
        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO missing_p VALUES (?)");
        pstmt.setInt(1, 1);
        pstmt.addBatch();

        final SQLException e = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                pstmt.executeBatch();
            }
        });
        assertFalse(e instanceof BatchUpdateException, e.getClass().getName());
        assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
        pstmt.close();
    }

    @Test
    public void testMixedBindTypesRefusedAtAddBatch() throws SQLException {
        statement.execute("CREATE TABLE hb3 (n NUMBER(10,0))");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO hb3 VALUES (?)");
        pstmt.setInt(1, 10);
        pstmt.addBatch();
        pstmt.setString(1, "abc");

        final SQLException e = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                pstmt.addBatch();
            }
        });
        assertEquals("Array bind for values with mixed types not supported. "
            + "Previous type: JAVA_BIGDECIMAL, Current type: JAVA_STRING at Column: 1, Row: 2.",
            e.getMessage());
        assertEquals("0A000", e.getSQLState());
        assertEquals(200023, e.getErrorCode());
        pstmt.close();
    }

    @Test
    public void testClearBatchDropsPreparedRows() throws SQLException {
        statement.execute("CREATE TABLE hb4 (id INTEGER)");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO hb4 VALUES (?)");
        pstmt.setInt(1, 1);
        pstmt.addBatch();
        pstmt.clearBatch();

        final int[] counts = pstmt.executeBatch();
        assertEquals(0, counts.length);

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM hb4");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        rs.close();
        pstmt.close();
    }
}
