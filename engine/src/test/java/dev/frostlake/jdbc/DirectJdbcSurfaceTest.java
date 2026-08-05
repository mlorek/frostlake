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

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the in-process (direct) JDBC surface: the {@link DirectResultSet} typed-getter and
 * navigation matrix, {@link DirectCallableStatement} result getters, {@link DirectDatabaseMetaData}
 * catalog queries, and {@link DirectConnection}'s Snowflake-aligned savepoint refusal.
 */
public class DirectJdbcSurfaceTest extends BaseJdbcTest {

    private static final Logger logger = LoggerFactory.getLogger(DirectJdbcSurfaceTest.class);

    private static final String CALLABLE_GETTERS =
        "reads a procedure's result through DirectCallableStatement's OUT-parameter getters, a Frostlake "
        + "driver surface: the Snowflake driver returns the procedure's value as a result set instead";

    @Test
    public void directResultSetTypedGetterMatrix() throws SQLException {
        statement.execute("""
            CREATE TABLE drs_types (i INTEGER, l BIGINT, d DOUBLE, n NUMBER(10,2), s VARCHAR,
                                    b BOOLEAN, dt DATE, ts TIMESTAMP_NTZ, nul VARCHAR)
            """);
        statement.execute("""
            INSERT INTO drs_types VALUES (42, 9876543210, 3.5, 12.34, 'frost', TRUE,
                                          '2026-01-15', '2026-01-15 10:30:00', NULL)
            """);
        try (final ResultSet rs = statement.executeQuery("SELECT * FROM drs_types")) {
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
            assertTrue(rs.getTimestamp(8).toString().startsWith("2026-01-15 10:30:00"));
            assertTrue(rs.getTimestamp("TS").toString().startsWith("2026-01-15 10:30:00"));
            assertNotNull(rs.getObject(1));
            assertNotNull(rs.getObject("S"));

            assertNull(rs.getString(9));
            assertTrue(rs.wasNull());
            assertEquals(5, rs.findColumn("S"));

            final ResultSetMetaData md = rs.getMetaData();
            assertEquals(9, md.getColumnCount());
            assertEquals("I", md.getColumnName(1));

            assertFalse(rs.next());
            assertTrue(rs.isAfterLast());
        }
    }

    @Test
    public void directCallableNumericGetterChain() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), CALLABLE_GETTERS);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE D_ADD_ONE(N FLOAT) RETURNS FLOAT LANGUAGE SQL AS
            $$ BEGIN RETURN N + 1; END $$
            """);
        try (final CallableStatement cs = connection.prepareCall("CALL D_ADD_ONE(?)")) {
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
    public void directCallableVarcharBooleanNullAndTemporal() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), CALLABLE_GETTERS);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE D_TAG() RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN RETURN 'frost'; END $$
            """);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE D_ON() RETURNS BOOLEAN LANGUAGE SQL AS
            $$ BEGIN RETURN TRUE; END $$
            """);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE D_NULL() RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN RETURN NULL; END $$
            """);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE D_TS() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS
            $$ BEGIN RETURN TO_TIMESTAMP('2026-01-15 10:30:00'); END $$
            """);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE D_DATE() RETURNS DATE LANGUAGE SQL AS
            $$ BEGIN RETURN TO_DATE('2026-01-15'); END $$
            """);
        try (final CallableStatement cs = connection.prepareCall("CALL D_TAG()")) {
            cs.execute();
            assertEquals("frost", cs.getString(1));
        }
        try (final CallableStatement cs = connection.prepareCall("CALL D_ON()")) {
            cs.execute();
            assertTrue(cs.getBoolean(1));
        }
        try (final CallableStatement cs = connection.prepareCall("CALL D_NULL()")) {
            cs.execute();
            assertNull(cs.getString(1));
            assertTrue(cs.wasNull());
        }
        try (final CallableStatement cs = connection.prepareCall("CALL D_TS()")) {
            cs.execute();
            final Timestamp ts = cs.getTimestamp(1);
            assertNotNull(ts);
            assertTrue(ts.toString().startsWith("2026-01-15 10:30:00"));
        }
        try (final CallableStatement cs = connection.prepareCall("CALL D_DATE()")) {
            cs.execute();
            assertEquals(Date.valueOf("2026-01-15").toString(), cs.getDate(1).toString());
        }
    }

    @Test
    public void directDatabaseMetaDataCatalogQueries() throws SQLException {
        statement.execute("CREATE TABLE dmd_probe (id INTEGER NOT NULL, name VARCHAR, PRIMARY KEY (id))");
        final java.sql.DatabaseMetaData md = connection.getMetaData();

        assertNotNull(md.getDatabaseProductName());
        assertNotNull(md.getDatabaseProductVersion());
        assertNotNull(md.getDriverName());
        assertNotNull(md.getURL());
        assertNotNull(md.getIdentifierQuoteString());
        assertTrue(md.getJDBCMajorVersion() >= 4);

        boolean sawProbe = false;
        try (final ResultSet tables = md.getTables(null, null, "%", null)) {
            while (tables.next()) {
                if ("DMD_PROBE".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    sawProbe = true;
                }
            }
        }
        assertTrue(sawProbe, "getTables must list DMD_PROBE");

        boolean sawId = false;
        try (final ResultSet cols = md.getColumns(null, null, "DMD_PROBE", "%")) {
            while (cols.next()) {
                if ("ID".equalsIgnoreCase(cols.getString("COLUMN_NAME"))) {
                    sawId = true;
                }
            }
        }
        assertTrue(sawId, "getColumns must list ID");

        try (final ResultSet pks = md.getPrimaryKeys(null, null, "DMD_PROBE")) {
            assertNotNull(pks);
        }
        try (final ResultSet types = md.getTableTypes()) {
            assertNotNull(types);
        }
        try (final ResultSet schemas = md.getSchemas()) {
            assertNotNull(schemas);
        }
        try (final ResultSet catalogs = md.getCatalogs()) {
            assertNotNull(catalogs);
        }
    }

    @Test
    public void directSavepointsRefusedLikeSnowflake() {
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
        logger.info("Savepoint surface refused on direct connection, as in Snowflake");
    }
}
