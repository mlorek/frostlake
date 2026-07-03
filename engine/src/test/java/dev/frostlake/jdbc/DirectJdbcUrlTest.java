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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The in-process JDBC URL scheme {@code jdbc:frostlake:direct:<name>} — a {@link DriverManager} connection to an
 * embedded {@code DatabaseEngine} with NO HTTP server (contrast with {@code jdbc:frostlake://host:port} which
 * needs a running {@code DatabaseHttpServer}). Connections sharing a name share data; different names isolate.
 * Note: these tests deliberately start no server.
 */
public class DirectJdbcUrlTest {

    @BeforeAll
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("dev.frostlake.jdbc.DatabaseDriver");
    }

    @Test
    public void inProcessConnectionWorksWithoutServer() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:frostlake:direct:t1")) {
            assertTrue(conn instanceof DirectConnection, "direct URL should yield an in-process connection");
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE DATABASE IF NOT EXISTS d");
                st.execute("USE DATABASE d");
                st.execute("USE SCHEMA PUBLIC");
                st.execute("CREATE TABLE t (id INTEGER)");
                st.execute("INSERT INTO t VALUES (42)");
                try (ResultSet rs = st.executeQuery("SELECT id FROM t")) {
                    assertTrue(rs.next());
                    assertEquals(42, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void executeUpdateReportsAffectedRows() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:frostlake:direct:counts")) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE DATABASE IF NOT EXISTS cd");
                st.execute("USE DATABASE cd");
                st.execute("USE SCHEMA PUBLIC");
                st.execute("CREATE TABLE cnt (id INTEGER)");
                assertEquals(2, st.executeUpdate("INSERT INTO cnt VALUES (1), (2)"));
                assertEquals(1, st.executeUpdate("UPDATE cnt SET id = 9 WHERE id = 1"));
                assertEquals(2, st.executeUpdate("DELETE FROM cnt"));
            }
        }
    }

    @Test
    public void sameNameSharesEngine() throws SQLException {
        try (Connection c1 = DriverManager.getConnection("jdbc:frostlake:direct:shared")) {
            try (Statement s = c1.createStatement()) {
                s.execute("CREATE DATABASE IF NOT EXISTS sd");
                s.execute("USE DATABASE sd");
                s.execute("USE SCHEMA PUBLIC");
                s.execute("CREATE TABLE shared_t (v INTEGER)");
                s.execute("INSERT INTO shared_t VALUES (7)");
            }
        }
        // A separate connection with the SAME name sees the first connection's data (one shared engine).
        try (Connection c2 = DriverManager.getConnection("jdbc:frostlake:direct:shared")) {
            try (Statement s = c2.createStatement()) {
                s.execute("USE DATABASE sd");
                s.execute("USE SCHEMA PUBLIC");
                try (ResultSet rs = s.executeQuery("SELECT v FROM shared_t")) {
                    assertTrue(rs.next());
                    assertEquals(7, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void tableSurvivesConnectionCloseAndReopen() throws SQLException {
        // Open a connection, create a table, and populate it.
        final Connection first = DriverManager.getConnection("jdbc:frostlake:direct:reopen");
        final Statement s1 = first.createStatement();
        s1.execute("CREATE DATABASE IF NOT EXISTS rdb");
        s1.execute("USE DATABASE rdb");
        s1.execute("USE SCHEMA PUBLIC");
        s1.execute("CREATE TABLE survivor (id INTEGER, name VARCHAR)");
        s1.execute("INSERT INTO survivor VALUES (1, 'alice'), (2, 'bob')");
        s1.close();

        // Close the connection — the shared per-name engine (and its catalog/data) outlives it.
        first.close();
        assertTrue(first.isClosed(), "the first connection should report closed");

        // Re-open a NEW connection to the SAME direct name and read the table back.
        final Connection second = DriverManager.getConnection("jdbc:frostlake:direct:reopen");
        assertFalse(second.isClosed(), "the reopened connection should be open");
        final Statement s2 = second.createStatement();
        s2.execute("USE DATABASE rdb");
        s2.execute("USE SCHEMA PUBLIC");
        try (ResultSet rs = s2.executeQuery("SELECT id, name FROM survivor ORDER BY id")) {
            assertTrue(rs.next(), "row 1 should survive the close/reopen");
            assertEquals(1, rs.getInt(1));
            assertEquals("alice", rs.getString(2));
            assertTrue(rs.next(), "row 2 should survive the close/reopen");
            assertEquals(2, rs.getInt(1));
            assertEquals("bob", rs.getString(2));
            assertFalse(rs.next(), "exactly the two inserted rows should remain");
        }
        s2.close();
        second.close();
    }

    @Test
    public void differentNamesAreIsolated() throws SQLException {
        try (Connection c1 = DriverManager.getConnection("jdbc:frostlake:direct:iso_a")) {
            try (Statement s = c1.createStatement()) {
                s.execute("CREATE DATABASE IF NOT EXISTS adb");
                s.execute("USE DATABASE adb");
            }
        }
        try (Connection c2 = DriverManager.getConnection("jdbc:frostlake:direct:iso_b")) {
            try (Statement s = c2.createStatement()) {
                boolean threw = false;
                try {
                    s.execute("USE DATABASE adb");   // adb exists only in the iso_a engine
                } catch (final SQLException expected) {
                    threw = true;
                }
                assertTrue(threw, "a database from a different direct engine must not be visible");
            }
        }
    }
}
