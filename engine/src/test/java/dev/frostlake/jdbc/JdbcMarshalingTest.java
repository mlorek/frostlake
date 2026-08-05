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

import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the JDBC marshaling fixes for the in-process (Direct) transport plus the shared
 * {@link JdbcMarshaling} helper: temporal/binary getters that used to throw
 * {@code SQLFeatureNotSupportedException}, {@code ResultSetMetaData} that used to report every column as
 * VARCHAR, and the type-name → {@link java.sql.Types} mapping (including the old BIGINT→INTEGER bug and the
 * TIMESTAMP/TIME substring collision). Runs over a Direct JDBC connection (see {@link BaseJdbcTest}).
 */
public class JdbcMarshalingTest extends BaseJdbcTest {

    @Test
    public void directTemporalGettersReturnValues() throws SQLException {
        statement.execute("CREATE TABLE m (d DATE, ts TIMESTAMP)");
        statement.execute("INSERT INTO m VALUES ('2025-06-17', '2025-06-17T14:30:00')");
        try (ResultSet rs = statement.executeQuery("SELECT d, ts FROM m")) {
            assertTrue(rs.next());
            assertEquals(Date.valueOf("2025-06-17"), rs.getDate(1));                      // previously threw
            assertEquals(Timestamp.valueOf("2025-06-17 14:30:00"), rs.getTimestamp(2));   // previously threw
        }
    }

    @Test
    public void directMetadataReportsRealTypes() throws SQLException {
        statement.execute("CREATE TABLE mt (d DATE, ts TIMESTAMP, n NUMBER(10,2), s VARCHAR)");
        statement.execute("INSERT INTO mt VALUES ('2025-01-01', '2025-01-01T00:00:00', 123.45, 'x')");
        try (ResultSet rs = statement.executeQuery("SELECT d, ts, n, s FROM mt")) {
            final ResultSetMetaData md = rs.getMetaData();
            assertEquals(Types.DATE, md.getColumnType(1));        // previously Types.VARCHAR (hardcoded)
            assertEquals(Types.TIMESTAMP, md.getColumnType(2));   // previously Types.VARCHAR
            assertEquals(Types.DECIMAL, md.getColumnType(3));     // previously Types.VARCHAR
            assertEquals(Types.VARCHAR, md.getColumnType(4));
            assertEquals(10, md.getPrecision(3));                 // previously 0
            assertEquals(2, md.getScale(3));                      // previously 0
        }
    }

    @Test
    public void typeNameMappingHandlesSubstringCollisions() {
        assertEquals(Types.BIGINT, JdbcMarshaling.toSqlType("BIGINT"));        // was INTEGER (BIGINT contains "INT")
        assertEquals(Types.INTEGER, JdbcMarshaling.toSqlType("INTEGER"));
        assertEquals(Types.TIMESTAMP, JdbcMarshaling.toSqlType("TIMESTAMP_NTZ"));   // contains "TIME"
        assertEquals(Types.DATE, JdbcMarshaling.toSqlType("DATE"));
        assertEquals(Types.TIME, JdbcMarshaling.toSqlType("TIME"));
        assertEquals(Types.DECIMAL, JdbcMarshaling.toSqlType("NUMBER"));
        assertEquals(Types.BINARY, JdbcMarshaling.toSqlType("BINARY"));
        assertEquals(Types.VARCHAR, JdbcMarshaling.toSqlType("VARCHAR"));
        assertEquals(Types.OTHER, JdbcMarshaling.toSqlType("VARIANT"));
    }

    @Test
    public void arrayColumnExposesJavaSqlArray() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "asserts the FROSTLAKE driver's own marshaling — Snowflake's JDBC driver reports an ARRAY "
            + "column as Types.VARCHAR (12) and hands back its JSON text, not a java.sql.Array");
        statement.execute("CREATE TABLE arr (a ARRAY)");
        statement.execute("INSERT INTO arr SELECT ARRAY_CONSTRUCT(1, 2, 3)");
        try (ResultSet rs = statement.executeQuery("SELECT a FROM arr")) {
            assertTrue(rs.next());
            assertEquals(Types.ARRAY, rs.getMetaData().getColumnType(1));
            final Array array = rs.getArray(1);                 // previously threw SQLFeatureNotSupportedException
            final Object[] elements = (Object[]) array.getArray();
            assertEquals(3, elements.length);
            assertEquals(1, ((Number) elements[0]).intValue());
            assertEquals(2, ((Number) elements[1]).intValue());
            assertEquals(3, ((Number) elements[2]).intValue());
        }
    }

    @Test
    public void placeholderSubstitutionIsLiteralAwareAndEscapes() {
        final Map<Integer, Object> params = new HashMap<>();
        params.put(1, "O'Brien");
        // The '?' inside the 'a?b' string literal must NOT consume the parameter slot.
        assertEquals("SELECT * FROM t WHERE note = 'a?b' AND name = 'O''Brien'",
            JdbcMarshaling.substitutePlaceholders("SELECT * FROM t WHERE note = 'a?b' AND name = ?", params));
        // Backslash AND single-quote are both escaped (the lexer honors \-escapes, so doubling only the
        // quote would let a trailing backslash break out of the literal).
        assertEquals("'a\\\\b'", JdbcMarshaling.formatLiteral("a\\b"));
        assertEquals("'x''y'", JdbcMarshaling.formatLiteral("x'y"));
        assertEquals("NULL", JdbcMarshaling.formatLiteral(null));
        assertEquals("42", JdbcMarshaling.formatLiteral(42));
    }

    @Test
    public void columnClassNamesMatchEngineStorage() {
        assertEquals("java.lang.Long", JdbcMarshaling.columnClassName(Types.INTEGER));   // engine stores INT as Long
        assertEquals("java.lang.Long", JdbcMarshaling.columnClassName(Types.BIGINT));
        assertEquals("java.math.BigDecimal", JdbcMarshaling.columnClassName(Types.DECIMAL));
        assertEquals("java.sql.Timestamp", JdbcMarshaling.columnClassName(Types.TIMESTAMP));
        assertEquals("java.lang.String", JdbcMarshaling.columnClassName(Types.VARCHAR));
    }

    @Test
    public void bytesAndTimestampConversionsAreRobust() {
        final byte[] hello = "HELLO".getBytes(StandardCharsets.UTF_8);
        // byte[] passthrough (Direct), engine "0x"+hex rendering, and the JSON-wire Base64 form
        assertArrayEquals(hello, JdbcMarshaling.toBytes(hello));
        assertArrayEquals(hello, JdbcMarshaling.toBytes("0x48454C4C4F"));
        assertArrayEquals(hello, JdbcMarshaling.toBytes(Base64.getEncoder().encodeToString(hello)));
        // ISO 'T' (the wire form) and SQL-space both parse to the same Timestamp; the old HTTP code did
        // Timestamp.valueOf on the 'T' string and threw.
        final Timestamp expected = Timestamp.valueOf("2025-06-17 14:30:00");
        assertEquals(expected, JdbcMarshaling.toTimestamp("2025-06-17T14:30:00"));
        assertEquals(expected, JdbcMarshaling.toTimestamp("2025-06-17 14:30:00"));
        // TIME has no column type in the grammar, so cover the time/date converters directly.
        assertEquals(Time.valueOf("14:30:00"), JdbcMarshaling.toTime(java.time.LocalTime.of(14, 30, 0)));
        assertEquals(Date.valueOf("2025-06-17"), JdbcMarshaling.toDate(java.time.LocalDate.of(2025, 6, 17)));
    }
}
