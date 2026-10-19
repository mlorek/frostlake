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

import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The driver surface every transport must answer as the account's driver answers it, written once and run over the
 * in-process driver and the account's own (DriverResultSetSurfaceTest) and over the HTTP driver
 * (DriverResultSetSurfaceHttpTest). Each check takes the connection to test; a check of a call the account's driver
 * answers with an escaping parser exception of its own takes {@code live} to leave that call out there.
 */
final class DriverSurfaceChecks {

    private static final String SCALED = "SELECT 3.50::NUMBER(10,2) AS X";
    private static final String TWO_ROWS = "SELECT column1 AS X FROM VALUES (1), (2) ORDER BY 1";

    private DriverSurfaceChecks() {
    }

    /** The first row of a query, the result left open for the check to read. */
    private static ResultSet firstRow(final Connection c, final String sql) throws SQLException {
        final ResultSet rs = c.createStatement().executeQuery(sql);
        assertTrue(rs.next(), sql + " has a row");
        return rs;
    }

    /** A getter's refusal: its message, SQLSTATE and vendor code. */
    private static void refused(final String message, final String state, final int code, final Executable read) {
        final SQLException refusal = assertThrows(SQLException.class, read);
        assertEquals(message, refusal.getMessage());
        assertEquals(state, refusal.getSQLState());
        assertEquals(code, refusal.getErrorCode());
    }

    /** The driver's conversion refusal of a cell. */
    private static void unconvertible(final int type, final String target, final String value, final Executable read) {
        refused("Cannot convert value in the driver from type:" + type + " to type:" + target + ", value=" + value
            + ".", "0A000", 200038, read);
    }

    /**
     * An integer getter refuses a fixed-point number with a fraction, by position and by label alike.
     *
     * @param c    the connection
     * @param live whether it is the account's driver, whose getByte lets its parser's exception escape
     */
    static void scaledNumberRefusals(final Connection c, final boolean live) throws SQLException {
        final ResultSet rs = firstRow(c, SCALED);
        unconvertible(3, "int", "3.50", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getInt(1);
            }
        });
        unconvertible(3, "int", "3.50", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getInt("X");
            }
        });
        unconvertible(3, "long", "3.50", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getLong("X");
            }
        });
        unconvertible(3, "short", "3.50", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getShort("x");
            }
        });
        if (!live) {
            unconvertible(3, "byte", "3.50", new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getByte(1);
                }
            });
        }
        assertEquals("3.50", rs.getString("X"));
        final ResultSet whole = firstRow(c, "SELECT 3.00::NUMBER(10,2) AS X");
        unconvertible(3, "int", "3.00", new Executable() {
            @Override
            public void execute() throws Throwable {
                whole.getInt(1);
            }
        });
    }

    /**
     * The integer getters parse a cell's text: a FLOAT's cut at its point, a text that spells an integer, a BOOLEAN's
     * 1; a number past the getter's type and a text that is no integer are refused.
     *
     * @param c the connection
     */
    static void integerReads(final Connection c) throws SQLException {
        final ResultSet rs = firstRow(c, """
            SELECT 2.5::FLOAT AS F, '12' AS T, TRUE AS B, 'x' AS W, 9999999999 AS BIG, 70000 AS MID,
                   1e10::FLOAT AS FBIG""");
        assertEquals(2, rs.getInt("F"));
        assertEquals(2L, rs.getLong("F"));
        assertEquals(12, rs.getInt("T"));
        assertEquals(1, rs.getInt("B"));
        assertEquals(9999999999L, rs.getLong("BIG"));
        unconvertible(12, "int", "x", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getInt("W");
            }
        });
        unconvertible(-5, "int", "9999999999", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getInt("BIG");
            }
        });
        unconvertible(-5, "short", "70000", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getShort("MID");
            }
        });
        unconvertible(8, "int", "10000000000", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getInt("FBIG");
            }
        });
        assertEquals(10000000000L, rs.getLong("FBIG"));
    }

    /**
     * getBoolean reads TRUE and FALSE and the texts 1, 0, true and false; any other number or text is refused.
     *
     * @param c the connection
     */
    static void booleanReads(final Connection c) throws SQLException {
        final ResultSet rs = firstRow(c, """
            SELECT 1 AS ONE, 0 AS ZERO, 'true' AS T, '1' AS TONE, 'yes' AS YES, 2 AS TWO, 1.5::FLOAT AS F,
                   NULL::BOOLEAN AS N""");
        assertTrue(rs.getBoolean("ONE"));
        assertFalse(rs.getBoolean("ZERO"));
        assertTrue(rs.getBoolean("T"));
        assertTrue(rs.getBoolean("TONE"));
        assertFalse(rs.getBoolean("N"));
        assertTrue(rs.wasNull());
        unconvertible(12, "boolean", "yes", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getBoolean("YES");
            }
        });
        unconvertible(-5, "boolean", "2", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getBoolean("TWO");
            }
        });
        unconvertible(8, "boolean", "1.5", new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getBoolean("F");
            }
        });
    }

    /**
     * A label names the first column whose name matches it ignoring case, for every getter and for findColumn; a
     * label no column has is refused with SQLSTATE 42703; a position no column has with 22000 and 200032.
     *
     * @param c the connection
     */
    static void labelsAndPositions(final Connection c) throws SQLException {
        final ResultSet twins = firstRow(c, "SELECT 1 AS \"a\", 2 AS \"A\", 3 AS \"x y\"");
        assertEquals(1, twins.getInt("A"));
        assertEquals(1, twins.findColumn("A"));
        assertEquals("1", twins.getString("A"));
        assertEquals(1L, twins.getObject("a"));
        assertEquals(3, twins.getInt("X Y"));
        refused("Column not found: NOPE", "42703", 0, new Executable() {
            @Override
            public void execute() throws Throwable {
                twins.getInt("NOPE");
            }
        });
        refused("Column not found: NOPE", "42703", 0, new Executable() {
            @Override
            public void execute() throws Throwable {
                twins.findColumn("NOPE");
            }
        });
        refused("Invalid column index: 0", "22000", 200032, new Executable() {
            @Override
            public void execute() throws Throwable {
                twins.getString(0);
            }
        });
        refused("Invalid column index: 4", "22000", 200032, new Executable() {
            @Override
            public void execute() throws Throwable {
                twins.getString(4);
            }
        });
    }

    /**
     * The cursor moves forward only and closes at commit; its position reads as the account's driver reads it,
     * including after the last row and over an empty result.
     *
     * @param c the connection
     */
    static void cursor(final Connection c) throws SQLException {
        final Statement statement = c.createStatement();
        final ResultSet rs = statement.executeQuery(TWO_ROWS);
        assertEquals(ResultSet.TYPE_FORWARD_ONLY, rs.getType());
        assertEquals(ResultSet.CONCUR_READ_ONLY, rs.getConcurrency());
        assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, rs.getHoldability());
        assertEquals("0 true false false false", position(rs));
        assertTrue(rs.next());
        assertEquals("1 false true false false", position(rs));
        assertTrue(rs.next());
        assertEquals("2 false false true false", position(rs));
        assertFalse(rs.next());
        assertEquals("2 false false false true", position(rs));
        refused("Invalid column index: 1", "22000", 200032, new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getString(1);
            }
        });
        final String[] moves = {"previous", "absolute", "first", "last", "beforeFirst", "afterLast", "relative"};
        for (final String move : moves) {
            assertThrows(SQLFeatureNotSupportedException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    move(rs, move);
                }
            }, move);
        }
        final ResultSet empty = statement.executeQuery("SELECT 1 AS X WHERE 1 = 0");
        assertEquals("0 true false true false", position(empty));
        assertFalse(empty.next());
        assertEquals("0 true false false true", position(empty));
        statement.close();
    }

    /** getRow, isBeforeFirst, isFirst, isLast and isAfterLast, in that order. */
    private static String position(final ResultSet rs) throws SQLException {
        return rs.getRow() + " " + rs.isBeforeFirst() + " " + rs.isFirst() + " " + rs.isLast() + " "
            + rs.isAfterLast();
    }

    private static void move(final ResultSet rs, final String move) throws SQLException {
        switch (move) {
            case "previous":
                rs.previous();
                break;
            case "absolute":
                rs.absolute(1);
                break;
            case "first":
                rs.first();
                break;
            case "last":
                rs.last();
                break;
            case "beforeFirst":
                rs.beforeFirst();
                break;
            case "afterLast":
                rs.afterLast();
                break;
            default:
                rs.relative(0);
                break;
        }
    }

    /**
     * A closed result set refuses every read with the account's sentence, while next answers false; a NULL read
     * through any getter says so through wasNull.
     *
     * @param c the connection
     */
    static void closedAndNull(final Connection c) throws SQLException {
        final ResultSet rs = firstRow(c, "SELECT NULL::BINARY AS B, 1 AS N");
        assertNull(rs.getBytes(1));
        assertTrue(rs.wasNull());
        assertEquals(1, rs.getInt(2));
        assertFalse(rs.wasNull());
        rs.close();
        assertTrue(rs.isClosed());
        assertFalse(rs.next());
        refused("Result set has been closed.", "0A000", 200037, new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getString(2);
            }
        });
        refused("Result set has been closed.", "0A000", 200037, new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getLong(2);
            }
        });
        refused("Result set has been closed.", "0A000", 200037, new Executable() {
            @Override
            public void execute() throws Throwable {
                rs.getMetaData();
            }
        });
    }

    /**
     * getCatalog and getSchema follow where the session stands: a CREATE SCHEMA moves it, as a USE SCHEMA and
     * setSchema do.
     *
     * @param c the connection, standing in TEST_DB.PUBLIC
     */
    static void namespace(final Connection c) throws SQLException {
        assertEquals("TEST_DB.PUBLIC", c.getCatalog() + "." + c.getSchema());
        final Statement statement = c.createStatement();
        statement.execute("CREATE SCHEMA test_db.dss");
        assertEquals("TEST_DB.DSS", c.getCatalog() + "." + c.getSchema());
        statement.execute("USE SCHEMA test_db.PUBLIC");
        assertEquals("TEST_DB.PUBLIC", c.getCatalog() + "." + c.getSchema());
        c.setSchema("DSS");
        assertEquals("TEST_DB.DSS", c.getCatalog() + "." + c.getSchema());
        statement.execute("USE SCHEMA test_db.PUBLIC");
        statement.close();
    }

    /**
     * A callable statement in the JDBC escape syntax runs, and a procedure's result reads through the result set
     * getters.
     *
     * @param c the connection
     */
    static void escapedCall(final Connection c) throws SQLException {
        final Statement statement = c.createStatement();
        statement.execute("""
            CREATE OR REPLACE PROCEDURE dss_ts() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS
            $$ BEGIN RETURN '2024-01-02 03:04'::TIMESTAMP_NTZ; END $$""");
        statement.close();
        final CallableStatement call = c.prepareCall("{call dss_ts()}");
        call.execute();
        final ResultSet rs = call.getResultSet();
        assertTrue(rs.next());
        assertEquals("2024-01-02 03:04:00.000", rs.getString(1));
        assertEquals(Timestamp.valueOf("2024-01-02 03:04:00"), rs.getObject(1));
        call.close();
    }

    /**
     * What the metadata says about the SQL the server speaks — the account's own answers.
     *
     * @param c the connection
     */
    static void metadataAnswers(final Connection c) throws SQLException {
        final DatabaseMetaData meta = c.getMetaData();
        final boolean[] answers = {meta.nullsAreSortedHigh(), meta.nullsAreSortedLow(),
            meta.dataDefinitionCausesTransactionCommit(), meta.doesMaxRowSizeIncludeBlobs(),
            meta.supportsConvert(), meta.supportsCoreSQLGrammar(), meta.supportsMinimumSQLGrammar(),
            meta.supportsGroupByUnrelated(), meta.supportsLikeEscapeClause(), meta.supportsMultipleTransactions(),
            meta.supportsOpenStatementsAcrossCommit(), meta.supportsOpenStatementsAcrossRollback(),
            meta.supportsCatalogsInIndexDefinitions(), meta.supportsCatalogsInPrivilegeDefinitions(),
            meta.supportsCatalogsInProcedureCalls(), meta.supportsSchemasInIndexDefinitions(),
            meta.supportsSchemasInPrivilegeDefinitions(), meta.supportsSchemasInProcedureCalls(),
            meta.supportsStoredFunctionsUsingCallSyntax(), meta.supportsUnion(), meta.supportsGetGeneratedKeys(),
            meta.locatorsUpdateCopy(), meta.supportsResultSetType(ResultSet.TYPE_FORWARD_ONLY),
            meta.supportsResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE),
            meta.supportsResultSetConcurrency(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY),
            meta.supportsResultSetHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT),
            meta.supportsResultSetHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT),
            meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_NONE),
            meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_READ_COMMITTED),
            meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE)};
        assertArrayEquals(new boolean[] {true, false, true, true, false, false, false, false, false, true, false,
            false, false, false, false, false, false, false, true, true, false, false, true, false, false, false,
            true, true, true, false}, answers);
        assertEquals("database schema procedure $", meta.getCatalogTerm() + " " + meta.getSchemaTerm() + " "
            + meta.getProcedureTerm() + " " + meta.getExtraNameCharacters());
        assertEquals("255 255 255 255 255 0 67108864 134217728 2", meta.getMaxCatalogNameLength() + " "
            + meta.getMaxSchemaNameLength() + " " + meta.getMaxTableNameLength() + " " + meta.getMaxColumnNameLength()
            + " " + meta.getMaxUserNameLength() + " " + meta.getMaxProcedureNameLength() + " "
            + meta.getMaxBinaryLiteralLength() + " " + meta.getMaxCharLiteralLength() + " "
            + meta.getResultSetHoldability());
        assertEquals("DATABASE,IFNULL,USER", meta.getSystemFunctions());
        assertTrue(meta.getSQLKeywords().startsWith("ACCOUNT,ASOF,BIT,BYTEINT,CONNECTION,"));
        assertTrue(meta.getNumericFunctions().startsWith("ABS,ACOS,ASIN,ATAN,ATAN2,CBRT,CEILING,"));
        assertTrue(meta.getTimeDateFunctions().startsWith("CURDATE,CURTIME,DAYNAME,"));
        assertTrue(meta.getStringFunctions().startsWith("ASCII,BIT_LENGTH,CHAR,CONCAT,INSERT,"));
        assertThrows(SQLFeatureNotSupportedException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                meta.getRowIdLifetime();
            }
        });
        assertThrows(SQLFeatureNotSupportedException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                meta.isWrapperFor(DatabaseMetaData.class);
            }
        });
    }

    /**
     * OUT parameters, a surface of this driver's own: each reads as its column of the CALL's result reads, through
     * the result set getters, and a named parameter binds at its position.
     *
     * @param c the connection
     */
    static void outParameters(final Connection c) throws SQLException {
        final Statement statement = c.createStatement();
        statement.execute("""
            CREATE OR REPLACE PROCEDURE dss_ts() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS
            $$ BEGIN RETURN '2024-01-02 03:04'::TIMESTAMP_NTZ; END $$""");
        statement.execute("""
            CREATE OR REPLACE PROCEDURE dss_v() RETURNS VARIANT LANGUAGE SQL AS
            $$ BEGIN RETURN PARSE_JSON('{"a":1}'); END $$""");
        statement.execute("""
            CREATE OR REPLACE PROCEDURE dss_n(x NUMBER(10,2)) RETURNS NUMBER(10,2) LANGUAGE SQL AS
            $$ BEGIN RETURN x; END $$""");
        statement.execute("""
            CREATE OR REPLACE PROCEDURE dss_null() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN NULL; END $$""");
        statement.close();

        final CallableStatement ts = c.prepareCall("CALL dss_ts()");
        ts.registerOutParameter(1, Types.TIMESTAMP);
        ts.execute();
        final ResultSet row = ts.getResultSet();
        assertTrue(row.next());
        assertEquals("2024-01-02 03:04:00.000", ts.getString(1));
        assertEquals(row.getString(1), ts.getString(1));
        assertEquals(Timestamp.valueOf("2024-01-02 03:04:00"), ts.getObject(1));
        assertEquals(Timestamp.valueOf("2024-01-02 03:04:00"), ts.getTimestamp(1));
        assertNull(ts.getString(2));
        assertTrue(ts.wasNull());
        ts.close();

        final CallableStatement variant = c.prepareCall("CALL dss_v()");
        variant.execute();
        assertEquals("{\"a\":1}", variant.getObject(1));
        assertEquals("{\"a\":1}", variant.getString(1));
        variant.close();

        final CallableStatement named = c.prepareCall("CALL dss_n(:x)");
        named.setInt("x", 7);
        named.execute();
        assertEquals(new BigDecimal("7.00"), named.getBigDecimal(1));
        assertEquals("7.00", named.getString("x"));
        named.setObject("x", new BigDecimal("2.5"), Types.DECIMAL, 2);
        named.execute();
        assertEquals("2.50", named.getString(1));
        named.close();

        final CallableStatement empty = c.prepareCall("{call dss_null()}");
        empty.execute();
        assertNull(empty.getString(1));
        assertTrue(empty.wasNull());
        empty.close();
    }
}
