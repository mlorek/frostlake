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
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JDBC Driver
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class JdbcDriverTest {

    private static DatabaseHttpServer server;
    private static int TEST_PORT;
    private static String JDBC_URL;

    @BeforeAll
    public static void startServer() throws Exception {
        // Find a free port dynamically to avoid conflicts with running servers
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            TEST_PORT = s.getLocalPort();
        }
        JDBC_URL = "jdbc:frostlake://localhost:" + TEST_PORT;
        server = new DatabaseHttpServer(TEST_PORT);
        server.start();
        Thread.sleep(500);
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testDriverRegistration() throws Exception {
        // Driver should be automatically registered
        final Driver driver = DriverManager.getDriver(JDBC_URL);
        assertNotNull(driver);
        assertTrue(driver instanceof DatabaseDriver);
    }

    @Test
    public void testBasicConnection() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
            assertTrue(conn.isValid(5));
        }
    }

    @Test
    public void testCreateTableAndInsert() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = conn.createStatement()) {
                // Create database and table
                stmt.execute("CREATE DATABASE jdbc_test_db");
                stmt.execute("USE DATABASE jdbc_test_db");
                stmt.execute("CREATE TABLE users (id INT, name VARCHAR, age INT)");

                // Insert data — the affected-row count is reported over the wire
                final int rows = stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice', 30)");
                assertEquals(1, rows);

                // Query data
                final ResultSet rs = stmt.executeQuery("SELECT * FROM users");
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("id"));
                assertEquals("Alice", rs.getString("name"));
                assertEquals(30, rs.getInt("age"));
                assertFalse(rs.next());
            }
        }
    }

    @Test
    public void testPreparedStatement() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE prep_test_db");
                stmt.execute("USE DATABASE prep_test_db");
                stmt.execute("CREATE TABLE products (id INT, name VARCHAR, price DECIMAL)");
            }

            // Use PreparedStatement
            final String sql = "INSERT INTO products VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, 1);
                pstmt.setString(2, "Laptop");
                pstmt.setDouble(3, 999.99);
                pstmt.executeUpdate();

                pstmt.setInt(1, 2);
                pstmt.setString(2, "Mouse");
                pstmt.setDouble(3, 29.99);
                pstmt.executeUpdate();
            }

            // Query with PreparedStatement
            final String querySql = "SELECT * FROM products WHERE price > ?";
            try (PreparedStatement pstmt = conn.prepareStatement(querySql)) {
                pstmt.setDouble(1, 50.0);
                final ResultSet rs = pstmt.executeQuery();

                assertTrue(rs.next());
                assertEquals("Laptop", rs.getString("name"));
                assertFalse(rs.next());
            }
        }
    }

    @Test
    public void testResultSetMetaData() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE meta_test_db");
                stmt.execute("USE DATABASE meta_test_db");
                stmt.execute("CREATE TABLE test (id INT, name VARCHAR, active BOOLEAN)");
                stmt.execute("INSERT INTO test VALUES (1, 'Test', true)");

                final ResultSet rs = stmt.executeQuery("SELECT * FROM test");
                final ResultSetMetaData meta = rs.getMetaData();

                assertEquals(3, meta.getColumnCount());
                assertEquals("ID", meta.getColumnName(1).toUpperCase());
                assertEquals("NAME", meta.getColumnName(2).toUpperCase());
                assertEquals("ACTIVE", meta.getColumnName(3).toUpperCase());

                // An INT column is NUMBER(38,0) in the catalog, as on a real account, and a
                // scale-less NUMBER is a BIGINT to JDBC — which is exactly what live reports for it.
                assertEquals(Types.BIGINT, meta.getColumnType(1));
                assertEquals(Types.VARCHAR, meta.getColumnType(2));
            }
        }
    }

    @Test
    public void testJoinQuery() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE join_test_db");
                stmt.execute("USE DATABASE join_test_db");

                // Create tables
                stmt.execute("CREATE TABLE employees (id INT, name VARCHAR, dept_id INT)");
                stmt.execute("CREATE TABLE departments (dept_id INT, dept_name VARCHAR)");

                // Insert data
                stmt.execute("INSERT INTO employees VALUES (1, 'Alice', 10)");
                stmt.execute("INSERT INTO employees VALUES (2, 'Bob', 20)");
                stmt.execute("INSERT INTO departments VALUES (10, 'Engineering')");
                stmt.execute("INSERT INTO departments VALUES (20, 'Sales')");

                // Join query
                final String sql = """
                    SELECT e.name as emp_name, d.dept_name FROM employees e
                    JOIN departments d ON e.dept_id = d.dept_id
                    """;
                final ResultSet rs = stmt.executeQuery(sql);

                assertTrue(rs.next());
                assertEquals("Alice", rs.getString("emp_name"));
                assertEquals("Engineering", rs.getString("dept_name"));

                assertTrue(rs.next());
                assertEquals("Bob", rs.getString("emp_name"));
                assertEquals("Sales", rs.getString("dept_name"));

                assertFalse(rs.next());
            }
        }
    }

    @Test
    public void testTransactions() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE trans_test_db");
                stmt.execute("USE DATABASE trans_test_db");
                stmt.execute("CREATE TABLE wallets (id INT, balance INT)");
                stmt.execute("INSERT INTO wallets VALUES (1, 1000)");
            }

            // Disable auto-commit
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE wallets SET balance = 500 WHERE id = 1");
                conn.commit();

                final ResultSet rs = stmt.executeQuery("SELECT balance FROM wallets WHERE id = 1");
                assertTrue(rs.next());
                assertEquals(500, rs.getInt(1));
            }
        }
    }

    @Test
    public void testDatabaseMetaData() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            final java.sql.DatabaseMetaData meta = conn.getMetaData();

            assertNotNull(meta);
            assertEquals("Frostlake SQL Engine", meta.getDatabaseProductName());
            assertEquals("1.0.0", meta.getDatabaseProductVersion());
            assertEquals("Frostlake JDBC Driver", meta.getDriverName());

            assertTrue(meta.supportsOuterJoins());
            assertTrue(meta.supportsFullOuterJoins());
            assertTrue(meta.supportsGroupBy());
            assertTrue(meta.supportsTransactions());
        }
    }

    @Test
    public void testDatabaseMetaDataObjectQueries() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE meta_obj_db");
                stmt.execute("USE DATABASE meta_obj_db");
                stmt.execute("CREATE SCHEMA meta_s");
                stmt.execute("USE SCHEMA meta_s");
                stmt.execute("CREATE TABLE employees (id INTEGER PRIMARY KEY, name VARCHAR, salary NUMBER(10,2))");
            }
            final java.sql.DatabaseMetaData meta = conn.getMetaData();

            // getTables — the created table must appear (previously threw over the HTTP path)
            boolean foundTable = false;
            try (ResultSet rs = meta.getTables(null, "META_S", "EMPLOYEES", null)) {
                while (rs.next()) {
                    if ("EMPLOYEES".equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        foundTable = true;
                    }
                }
            }
            assertTrue(foundTable, "getTables should list the created table over HTTP");

            // getColumns — all three columns
            int colCount = 0;
            try (ResultSet rs = meta.getColumns(null, "META_S", "EMPLOYEES", null)) {
                while (rs.next()) {
                    colCount++;
                }
            }
            assertEquals(3, colCount, "getColumns should list all three columns over HTTP");

            // getPrimaryKeys — the id column
            boolean foundPk = false;
            try (ResultSet rs = meta.getPrimaryKeys(null, "META_S", "EMPLOYEES")) {
                while (rs.next()) {
                    if ("ID".equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        foundPk = true;
                    }
                }
            }
            assertTrue(foundPk, "getPrimaryKeys should report the id column over HTTP");

            // getIndexInfo — must not throw (indexes are not implemented, so it may be empty)
            try (ResultSet rs = meta.getIndexInfo(null, "META_S", "EMPLOYEES", false, false)) {
                assertNotNull(rs);
                while (rs.next()) {
                    // drain
                }
            }
        }
    }

    @Test
    public void testResultSetNavigation() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE nav_test_db");
                stmt.execute("USE DATABASE nav_test_db");
                stmt.execute("CREATE TABLE numbers (value INT)");

                for (int i = 1; i <= 5; i++) {
                    stmt.execute("INSERT INTO numbers VALUES (" + i + ")");
                }

                final ResultSet rs = stmt.executeQuery("SELECT * FROM numbers");

                // Forward navigation
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));

                // Absolute positioning
                assertTrue(rs.absolute(5));
                assertEquals(5, rs.getInt(1));

                assertTrue(rs.absolute(1));
                assertEquals(1, rs.getInt(1));

                // First and last
                assertTrue(rs.last());
                assertEquals(5, rs.getInt(1));

                assertTrue(rs.first());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    public void testSessionPersistence() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            // Create database
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE session_db");
                stmt.execute("USE DATABASE session_db");
            }

            // In same connection, database should still be set
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
                stmt.execute("INSERT INTO test VALUES (1)");

                final ResultSet rs = stmt.executeQuery("SELECT * FROM test");
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    public void testConcurrentConnections() throws Exception {
        // Create two separate connections
        try (Connection conn1 = DriverManager.getConnection(JDBC_URL);
             Connection conn2 = DriverManager.getConnection(JDBC_URL)) {

            // Each connection should have its own session
            try (Statement stmt1 = conn1.createStatement()) {
                stmt1.execute("CREATE DATABASE conn1_db");
                stmt1.execute("USE DATABASE conn1_db");
                stmt1.execute("CREATE TABLE data (value VARCHAR)");
                stmt1.execute("INSERT INTO data VALUES ('connection1')");
            }

            try (Statement stmt2 = conn2.createStatement()) {
                stmt2.execute("CREATE DATABASE conn2_db");
                stmt2.execute("USE DATABASE conn2_db");
                stmt2.execute("CREATE TABLE data (value VARCHAR)");
                stmt2.execute("INSERT INTO data VALUES ('connection2')");
            }

            // Verify each connection sees its own data
            try (Statement stmt1 = conn1.createStatement()) {
                final ResultSet rs = stmt1.executeQuery("SELECT * FROM data");
                assertTrue(rs.next());
                assertEquals("connection1", rs.getString(1));
            }

            try (Statement stmt2 = conn2.createStatement()) {
                final ResultSet rs = stmt2.executeQuery("SELECT * FROM data");
                assertTrue(rs.next());
                assertEquals("connection2", rs.getString(1));
            }
        }
    }

    @Test
    public void testErrorHandling() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = conn.createStatement()) {
                // Try to select from non-existent table
                assertThrows(SQLException.class, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        stmt.executeQuery("SELECT * FROM nonexistent_table");
                        
                    }
                });

                // Try to use non-existent database
                assertThrows(SQLException.class, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        stmt.execute("USE DATABASE nonexistent_db");
                        
                    }
                });
            }
        }
    }

    @Test
    public void testConnectionProperties() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL + "/test_db")) {
            assertNotNull(conn);
            // Database should be set from URL
            assertEquals("TEST_DB", conn.getCatalog());
        }
    }

    @Test
    public void testWireArray() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE DATABASE arr_db");
                st.execute("USE DATABASE arr_db");
                st.execute("USE SCHEMA PUBLIC");
                st.execute("CREATE TABLE arr (a ARRAY)");
                st.execute("INSERT INTO arr SELECT ARRAY_CONSTRUCT(10, 20)");
                try (ResultSet rs = st.executeQuery("SELECT a FROM arr")) {
                    assertTrue(rs.next());
                    assertEquals(Types.ARRAY, rs.getMetaData().getColumnType(1));
                    final Object[] elements = (Object[]) rs.getArray(1).getArray();   // previously threw
                    assertEquals(2, elements.length);
                    assertEquals(10, ((Number) elements[0]).intValue());
                    assertEquals(20, ((Number) elements[1]).intValue());
                }
            }
        }
    }

    @Test
    public void testWireDecimalPrecisionAndMetadata() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE DATABASE wire_db");
                st.execute("USE DATABASE wire_db");
                st.execute("USE SCHEMA PUBLIC");
                st.execute("CREATE TABLE w (n NUMBER(30,12), ts TIMESTAMP)");
                // More significant digits than a double can hold — exercises the wire fidelity fix.
                st.execute("INSERT INTO w VALUES (123456789.123456789012, '2025-06-17T14:30:00')");
                try (ResultSet rs = st.executeQuery("SELECT n, ts FROM w")) {
                    assertTrue(rs.next());
                    // Decimal precision survives the JSON wire (without USE_BIG_DECIMAL_FOR_FLOATS it would
                    // collapse through double).
                    assertEquals(0, new BigDecimal("123456789.123456789012").compareTo(rs.getBigDecimal(1)));
                    // Temporal getter over the wire (an ISO 'T' string) — old code threw on Timestamp.valueOf.
                    assertEquals(Timestamp.valueOf("2025-06-17 14:30:00"), rs.getTimestamp(2));
                    final ResultSetMetaData md = rs.getMetaData();
                    assertEquals(Types.DECIMAL, md.getColumnType(1));
                    assertEquals(Types.TIMESTAMP, md.getColumnType(2));
                    assertEquals(30, md.getPrecision(1));   // precision/scale now carried on the wire (was 0)
                    assertEquals(12, md.getScale(1));
                }
            }
        }
    }

    @Test
    public void testConnectionWithoutDatabase() throws Exception {
        // Connect without specifying database in URL
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
            assertTrue(conn.isValid(5));

            // Catalog should be null initially
            assertNull(conn.getCatalog());

            try (Statement stmt = conn.createStatement()) {
                // Should be able to create and use database
                stmt.execute("CREATE DATABASE optional_db_test");
                stmt.execute("USE DATABASE optional_db_test");
                stmt.execute("CREATE TABLE test_table (id INT, value VARCHAR)");
                stmt.execute("INSERT INTO test_table VALUES (1, 'test')");

                // Verify data
                final ResultSet rs = stmt.executeQuery("SELECT * FROM test_table");
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("id"));
                assertEquals("test", rs.getString("value"));
                assertFalse(rs.next());
            }

            // After using database via setCatalog, catalog should be set
            conn.setCatalog("optional_db_test");
            // Note: getCatalog() returns the value as it was set
            assertNotNull(conn.getCatalog());
        }
    }

    @Test
    public void testConnectionWithDatabaseParameter() throws Exception {
        // Test database as query parameter
        final Properties props = new Properties();
        props.setProperty("database", "param_db_test");

        try (Connection conn = DriverManager.getConnection(JDBC_URL, props)) {
            assertNotNull(conn);
            // Note: Database from properties is not currently auto-used,
            // but connection should still work
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE param_db_test");
                stmt.execute("USE DATABASE param_db_test");
                stmt.execute("CREATE TABLE test (id INT)");
                stmt.execute("INSERT INTO test VALUES (42)");

                final ResultSet rs = stmt.executeQuery("SELECT * FROM test");
                assertTrue(rs.next());
                assertEquals(42, rs.getInt(1));
            }
        }
    }
}
