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

import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the previously-unsupported PreparedStatement methods: getParameterMetaData, getMetaData (SELECT vs
 * DML), and the stream/bytes binders.
 */
public class PreparedStatementGapsTest extends BaseJdbcTest {

    @Test
    public void getParameterMetaDataCountsPlaceholders() throws SQLException {
        statement.execute("CREATE TABLE pm (a INTEGER, b VARCHAR)");
        try (final PreparedStatement ps = connection.prepareStatement("SELECT * FROM pm WHERE a = ? AND b = ?")) {
            assertEquals(2, ps.getParameterMetaData().getParameterCount());
        }
    }

    @Test
    public void getMetaDataDescribesSelectColumns() throws SQLException {
        statement.execute("CREATE TABLE md (id INTEGER, name VARCHAR)");
        try (final PreparedStatement ps = connection.prepareStatement("SELECT id, name FROM md WHERE id = ?")) {
            final ResultSetMetaData meta = ps.getMetaData();
            assertNotNull(meta);
            assertEquals(2, meta.getColumnCount());
        }
    }

    @Test
    public void getMetaDataNullForDml() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "asserts the FROSTLAKE driver's own contract — Snowflake's JDBC driver returns a non-null "
            + "SnowflakeResultSetMetaDataV1 from PreparedStatement.getMetaData() even for an INSERT");
        statement.execute("CREATE TABLE dm (id INTEGER)");
        try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO dm VALUES (?)")) {
            assertNull(ps.getMetaData());
        }
    }

    @Test
    public void setCharacterStreamBindsText() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "binds through PreparedStatement.setCharacterStream(int, Reader); Snowflake's own driver "
            + "throws SnowflakeLoggedFeatureNotSupportedException for that overload — it is a "
            + "Frostlake driver capability, not an account feature");
        statement.execute("CREATE TABLE cs (txt VARCHAR)");
        try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO cs VALUES (?)")) {
            ps.setCharacterStream(1, new StringReader("hello"));
            ps.executeUpdate();
        }
        final ResultSet rs = statement.executeQuery("SELECT txt FROM cs");
        rs.next();
        assertEquals("hello", rs.getString(1));
    }

    @Test
    public void setBytesInsertsRow() throws SQLException {
        statement.execute("CREATE TABLE bt (data BINARY)");
        try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO bt VALUES (?)")) {
            ps.setBytes(1, new byte[]{1, 2, 3});
            ps.executeUpdate();
        }
        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM bt");
        rs.next();
        assertEquals(1, rs.getInt(1));
    }

    /**
     * A bound negative number stays a number beside a minus: spliced bare, {@code 3-?} with -5 read as
     * {@code 3--5} — a comment — and answered 3 under a column named 3. The account binds server-side, so
     * these answers are the account's own.
     */
    @Test
    public void aNegativeBindBesideAMinusStaysANumber() throws SQLException {
        try (final PreparedStatement ps = connection.prepareStatement("SELECT 3-? AS r")) {
            ps.setInt(1, -5);
            try (final ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals("R", rs.getMetaData().getColumnLabel(1).toUpperCase());
                assertEquals("8", rs.getString(1));
            }
            ps.setBigDecimal(1, new BigDecimal("-1.5"));
            try (final ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals("4.5", rs.getString(1));
            }
            ps.setLong(1, Long.MIN_VALUE);
            try (final ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals("9223372036854775811", rs.getString(1));
            }
        }
        try (final PreparedStatement ps = connection.prepareStatement("SELECT 10-? AS r")) {
            ps.setDouble(1, -2.5);
            try (final ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(12.5, rs.getDouble(1), 0.0);
            }
        }
    }
}
