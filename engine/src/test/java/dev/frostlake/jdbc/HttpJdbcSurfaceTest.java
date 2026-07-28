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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the HTTP-transport JDBC surface end to end against a live {@link DatabaseHttpServer}:
 * {@link DatabaseCallableStatement} result getters, the {@link DatabaseResultSet} typed-getter and
 * navigation matrix, the {@link DatabasePreparedStatement} setter matrix, {@link DatabaseMetaData}
 * queries and {@link DatabaseConnection} transaction/savepoint behavior.
 */
public class HttpJdbcSurfaceTest {

    private static final Logger logger = LoggerFactory.getLogger(HttpJdbcSurfaceTest.class);

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
        logger.info("HTTP JDBC surface server on {}", jdbcUrl);
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
        statement.execute("CREATE OR REPLACE DATABASE http_surface_db");
        statement.execute("USE DATABASE http_surface_db");
        statement.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void teardown() throws SQLException {
        try {
            statement.execute("DROP DATABASE IF EXISTS http_surface_db");
        } catch (final SQLException e) {
            // best-effort cleanup
        }
        statement.close();
        connection.close();
    }

    @Test
    public void callableNumericReturnCoversEveryNumericGetter() throws SQLException {
        statement.execute("""
            CREATE OR REPLACE PROCEDURE ADD_ONE(N FLOAT) RETURNS FLOAT LANGUAGE SQL AS
            $$ BEGIN RETURN N + 1; END $$
            """);
        try (final CallableStatement cs = connection.prepareCall("CALL ADD_ONE(?)")) {
            cs.registerOutParameter(1, Types.DOUBLE);
            cs.setDouble(1, 41);
            cs.execute();
            assertEquals(42.0, cs.getDouble(1));
            assertEquals(42.0f, cs.getFloat(1));
            assertEquals(42, cs.getInt(1));
            assertEquals(42L, cs.getLong(1));
            assertEquals((short) 42, cs.getShort(1));
            assertEquals((byte) 42, cs.getByte(1));
            assertEquals(0, cs.getBigDecimal(1).compareTo(new BigDecimal("42")));
            assertNotNull(cs.getObject(1));
            assertFalse(cs.wasNull());
        }
    }

    @Test
    public void callableVarcharBooleanAndNullReturns() throws SQLException {
        statement.execute("""
            CREATE OR REPLACE PROCEDURE GIVE_TAG() RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN RETURN 'frost'; END $$
            """);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE IS_ON() RETURNS BOOLEAN LANGUAGE SQL AS
            $$ BEGIN RETURN TRUE; END $$
            """);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE GIVE_NULL() RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN RETURN NULL; END $$
            """);
        try (final CallableStatement cs = connection.prepareCall("CALL GIVE_TAG()")) {
            cs.execute();
            assertEquals("frost", cs.getString(1));
            assertFalse(cs.wasNull());
        }
        try (final CallableStatement cs = connection.prepareCall("CALL IS_ON()")) {
            cs.execute();
            assertTrue(cs.getBoolean(1));
        }
        try (final CallableStatement cs = connection.prepareCall("CALL GIVE_NULL()")) {
            cs.execute();
            assertNull(cs.getString(1));
            assertTrue(cs.wasNull());
        }
    }

    @Test
    public void callableTemporalReturns() throws SQLException {
        statement.execute("""
            CREATE OR REPLACE PROCEDURE GIVE_TS() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS
            $$ BEGIN RETURN TO_TIMESTAMP('2026-01-15 10:30:00'); END $$
            """);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE GIVE_DATE() RETURNS DATE LANGUAGE SQL AS
            $$ BEGIN RETURN TO_DATE('2026-01-15'); END $$
            """);
        try (final CallableStatement cs = connection.prepareCall("CALL GIVE_TS()")) {
            cs.execute();
            final Timestamp ts = cs.getTimestamp(1);
            assertNotNull(ts);
            assertTrue(ts.toString().startsWith("2026-01-15 10:30:00"));
        }
        try (final CallableStatement cs = connection.prepareCall("CALL GIVE_DATE()")) {
            cs.execute();
            assertEquals(Date.valueOf("2026-01-15").toString(), cs.getDate(1).toString());
        }
    }

    @Test
    public void resultSetTypedGetterMatrixByIndexAndLabel() throws SQLException {
        statement.execute("""
            CREATE TABLE rs_types (i INTEGER, l BIGINT, d DOUBLE, n NUMBER(10,2), s VARCHAR,
                                   b BOOLEAN, dt DATE, tm TIME, ts TIMESTAMP_NTZ, nul VARCHAR)
            """);
        statement.execute("""
            INSERT INTO rs_types VALUES (42, 9876543210, 3.5, 12.34, 'frost', TRUE,
                                         '2026-01-15', '10:30:00', '2026-01-15 10:30:00', NULL)
            """);
        try (final ResultSet rs = statement.executeQuery("SELECT * FROM rs_types")) {
            assertTrue(rs.isBeforeFirst());
            assertTrue(rs.next());
            assertEquals(1, rs.getRow());

            assertEquals(42, rs.getInt(1));
            assertEquals(42, rs.getInt("I"));
            assertEquals((short) 42, rs.getShort(1));
            assertEquals((byte) 42, rs.getByte(1));
            assertEquals(9876543210L, rs.getLong(2));
            assertEquals(9876543210L, rs.getLong("L"));
            assertEquals(3.5, rs.getDouble(3));
            assertEquals(3.5f, rs.getFloat(3));
            assertEquals(0, rs.getBigDecimal(4).compareTo(new BigDecimal("12.34")));
            assertEquals(0, rs.getBigDecimal("N").compareTo(new BigDecimal("12.34")));
            assertEquals("frost", rs.getString(5));
            assertEquals("frost", rs.getString("S"));
            assertTrue(rs.getBoolean(6));
            assertTrue(rs.getBoolean("B"));
            assertEquals("2026-01-15", rs.getDate(7).toString());
            assertEquals("2026-01-15", rs.getDate("DT").toString());
            assertEquals("10:30:00", rs.getTime(8).toString());
            assertEquals("10:30:00", rs.getTime("TM").toString());
            assertTrue(rs.getTimestamp(9).toString().startsWith("2026-01-15 10:30:00"));
            assertTrue(rs.getTimestamp("TS").toString().startsWith("2026-01-15 10:30:00"));
            assertNotNull(rs.getObject(1));

            assertNull(rs.getString(10));
            assertTrue(rs.wasNull());
            assertEquals(5, rs.findColumn("S"));

            final ResultSetMetaData md = rs.getMetaData();
            assertEquals(10, md.getColumnCount());
            assertEquals("I", md.getColumnName(1));
            assertEquals(Types.VARCHAR, md.getColumnType(5));

            assertFalse(rs.next());
            assertTrue(rs.isAfterLast());
        }
    }

    @Test
    public void resultSetFindColumnUnknownThrows() throws SQLException {
        statement.execute("CREATE TABLE fc_t (a INTEGER)");
        statement.execute("INSERT INTO fc_t VALUES (1)");
        try (final ResultSet rs = statement.executeQuery("SELECT * FROM fc_t")) {
            assertTrue(rs.next());
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    rs.findColumn("NO_SUCH_COLUMN");
                }
            });
        }
    }

    @Test
    public void preparedStatementSetterMatrix() throws SQLException {
        statement.execute("""
            CREATE TABLE ps_types (b BOOLEAN, bt NUMBER(3,0), sh NUMBER(5,0), i INTEGER, l BIGINT,
                                   f DOUBLE, d DOUBLE, dec NUMBER(10,2), s VARCHAR, dt DATE,
                                   tm TIME, ts TIMESTAMP_NTZ, o VARCHAR, nul INTEGER)
            """);
        final String insert = "INSERT INTO ps_types VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (final PreparedStatement ps = connection.prepareStatement(insert)) {
            ps.setBoolean(1, true);
            ps.setByte(2, (byte) 7);
            ps.setShort(3, (short) 300);
            ps.setInt(4, 42);
            ps.setLong(5, 9876543210L);
            ps.setFloat(6, 1.5f);
            ps.setDouble(7, 2.5);
            ps.setBigDecimal(8, new BigDecimal("12.34"));
            ps.setString(9, "frost");
            ps.setDate(10, Date.valueOf("2026-01-15"));
            ps.setTime(11, Time.valueOf("10:30:00"));
            ps.setTimestamp(12, Timestamp.valueOf("2026-01-15 10:30:00"));
            ps.setObject(13, "as-object");
            ps.setNull(14, Types.INTEGER);
            assertEquals(1, ps.executeUpdate());
        }
        try (final ResultSet rs = statement.executeQuery("SELECT * FROM ps_types")) {
            assertTrue(rs.next());
            assertTrue(rs.getBoolean(1));
            assertEquals(7, rs.getInt(2));
            assertEquals(300, rs.getInt(3));
            assertEquals(42, rs.getInt(4));
            assertEquals("frost", rs.getString(9));
            assertEquals("as-object", rs.getString(13));
            rs.getObject(14);
            assertTrue(rs.wasNull());
        }
    }

    @Test
    public void preparedStatementReuseAndClearParameters() throws SQLException {
        statement.execute("CREATE TABLE ps_reuse (i INTEGER, s VARCHAR)");
        try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO ps_reuse VALUES (?, ?)")) {
            ps.setInt(1, 1);
            ps.setString(2, "one");
            assertEquals(1, ps.executeUpdate());
            ps.clearParameters();
            ps.setInt(1, 2);
            ps.setString(2, "two");
            assertEquals(1, ps.executeUpdate());
        }
        try (final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM ps_reuse")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test
    public void preparedStatementQueryAndExecuteForms() throws SQLException {
        statement.execute("CREATE TABLE ps_q (i INTEGER)");
        statement.execute("INSERT INTO ps_q VALUES (1), (2), (3)");
        try (final PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM ps_q WHERE i >= ?")) {
            ps.setInt(1, 2);
            try (final ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
            ps.setInt(1, 1);
            assertTrue(ps.execute());
            try (final ResultSet rs = ps.getResultSet()) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
            }
        }
    }

    @Test
    public void databaseMetaDataSampling() throws SQLException {
        statement.execute("CREATE TABLE md_probe (id INTEGER, name VARCHAR)");
        final java.sql.DatabaseMetaData md = connection.getMetaData();
        assertNotNull(md.getDatabaseProductName());
        assertNotNull(md.getDatabaseProductVersion());
        assertNotNull(md.getDriverName());
        assertNotNull(md.getDriverVersion());
        assertNotNull(md.getURL());
        assertNotNull(md.getIdentifierQuoteString());

        boolean sawProbe = false;
        try (final ResultSet tables = md.getTables(null, null, "%", null)) {
            while (tables.next()) {
                if ("MD_PROBE".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    sawProbe = true;
                }
            }
        }
        assertTrue(sawProbe, "getTables must list MD_PROBE");

        boolean sawName = false;
        try (final ResultSet cols = md.getColumns(null, null, "MD_PROBE", "%")) {
            while (cols.next()) {
                if ("NAME".equalsIgnoreCase(cols.getString("COLUMN_NAME"))) {
                    sawName = true;
                }
            }
        }
        assertTrue(sawName, "getColumns must list NAME");

        try (final ResultSet schemas = md.getSchemas()) {
            assertNotNull(schemas);
        }
        try (final ResultSet catalogs = md.getCatalogs()) {
            assertNotNull(catalogs);
        }
    }

    @Test
    public void connectionTransactionsAndSavepointsUnsupported() throws SQLException {
        statement.execute("CREATE TABLE tx_t (i INTEGER)");
        connection.setAutoCommit(false);
        assertFalse(connection.getAutoCommit());
        statement.execute("INSERT INTO tx_t VALUES (1)");
        connection.rollback();
        try (final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM tx_t")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
        statement.execute("INSERT INTO tx_t VALUES (2)");
        connection.commit();
        try (final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM tx_t")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
        connection.setAutoCommit(true);

        assertTrue(connection.isValid(2));
        assertFalse(connection.isClosed());

        // Savepoints do not exist in Snowflake; the driver must refuse them.
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                connection.setSavepoint();
            }
        });
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                connection.setSavepoint("sp1");
            }
        });
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                connection.rollback(null);
            }
        });
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                connection.releaseSavepoint(null);
            }
        });
    }
}
