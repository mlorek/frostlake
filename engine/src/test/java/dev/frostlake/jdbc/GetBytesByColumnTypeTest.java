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
import dev.frostlake.http.DatabaseHttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * getBytes reads a cell by its column's type, as the account's driver reads it: a whole number as its
 * two's-complement bytes, a FLOAT as its eight IEEE 754 bytes, a BOOLEAN as the one byte 1 or 0, a BINARY as its own
 * bytes, and a text, a VARIANT, an OBJECT, an ARRAY and a VECTOR as the UTF-8 bytes of what getString reads. A NUMBER
 * with digits after its point, a DECFLOAT, a DATE, a TIME and every timestamp are refused, quoting the cell's wire
 * form: a timestamp's seconds past the epoch to its column's scale, a TIMESTAMP_TZ's with its offset in minutes plus
 * 1440, and the type of a TIMESTAMP_TZ grouped by thousands, {@code type:2,014}. The numeric getters' refusals over a
 * timestamp quote the same form, and a TIMESTAMP_NTZ(0) reads by getInt and getLong as its seconds. The HTTP
 * transport answers exactly what the in-process one does (all live-verified).
 */
public class GetBytesByColumnTypeTest extends BaseJdbcTest {

    /** The wording every conversion refusal opens with; a line drops it so that the rest fits. */
    private static final String REFUSAL = "Cannot convert value in the driver from ";

    private static final String FAMILIES = """
        SELECT 7::NUMBER(38,0) AS i1, 300::NUMBER(38,0) AS i2, -1::NUMBER(38,0) AS i3, 0::NUMBER(38,0) AS i4,
               128::INT AS i5, -300::NUMBER(38,0) AS i6, 12345678901234567890::NUMBER(38,0) AS i7,
               99999999999999999999999999999999999999::NUMBER(38,0) AS i8, 1.5::NUMBER(38,0) AS i9,
               1.5::FLOAT AS f1, -0.25::DOUBLE AS f2, 'NaN'::FLOAT AS f3, 'inf'::FLOAT AS f4, '-inf'::FLOAT AS f5,
               -0.0::FLOAT AS f6, 1e308::FLOAT AS f7, 'abc'::VARCHAR AS s1, 'TRUE'::VARCHAR AS s2,
               'ff'::VARCHAR AS s3, 'é'::VARCHAR AS s4, ''::VARCHAR AS s5, FALSE AS b1, TRUE AS b2,
               NULL::BOOLEAN AS b3, TO_BINARY('0aff', 'HEX') AS x1, TO_BINARY('', 'HEX') AS x2,
               PARSE_JSON('{"a":1,"b":[1,2]}') AS v1, 'q'::VARIANT AS v2, PARSE_JSON('null') AS v3,
               5::NUMBER(38,0)::VARIANT AS v4, OBJECT_CONSTRUCT('a', 'x') AS o1, ARRAY_CONSTRUCT('x', NULL) AS a1,
               [1.5,2]::VECTOR(FLOAT,2) AS vc""";
    private static final String FAMILY_BYTES = """
        I1 [7]
        I2 [1, 44]
        I3 [-1]
        I4 [0]
        I5 [0, -128]
        I6 [-2, -44]
        I7 [0, -85, 84, -87, -116, -21, 31, 10, -46]
        I8 [75, 59, 76, -88, 90, -122, -60, 122, 9, -118, 34, 63, -1, -1, -1, -1]
        I9 [2]
        F1 [63, -8, 0, 0, 0, 0, 0, 0]
        F2 [-65, -48, 0, 0, 0, 0, 0, 0]
        F3 [127, -8, 0, 0, 0, 0, 0, 0]
        F4 [127, -16, 0, 0, 0, 0, 0, 0]
        F5 [-1, -16, 0, 0, 0, 0, 0, 0]
        F6 [-128, 0, 0, 0, 0, 0, 0, 0]
        F7 [127, -31, -52, -13, -123, -21, -56, -96]
        S1 [97, 98, 99]
        S2 [84, 82, 85, 69]
        S3 [102, 102]
        S4 [-61, -87]
        S5 []
        B1 [0]
        B2 [1]
        B3 null
        X1 [10, -1]
        X2 []
        V1 [123, 34, 97, 34, 58, 49, 44, 34, 98, 34, 58, 91, 49, 44, 50, 93, 125]
        V2 [34, 113, 34]
        V3 [110, 117, 108, 108]
        V4 [53]
        O1 [123, 34, 97, 34, 58, 34, 120, 34, 125]
        A1 [91, 34, 120, 34, 44, 117, 110, 100, 101, 102, 105, 110, 101, 100, 93]
        VC [91, 49, 46, 53, 48, 48, 48, 48, 48, 44, 50, 46, 48, 48, 48, 48, 48, 48, 93]
        """;

    private static final String REFUSED = """
        SELECT '2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ AS t1, '2024-01-02 03:04:05.123'::TIMESTAMP_NTZ(3) AS t2,
               '2024-01-02 03:04:05'::TIMESTAMP_NTZ(0) AS t3, '1960-01-01 00:00:00.5'::TIMESTAMP_NTZ AS t4,
               '2024-01-02 03:04:05'::TIMESTAMP_LTZ AS t5, '1960-01-01 00:00:00.5'::TIMESTAMP_LTZ(1) AS t6,
               '2024-01-02 03:04:05.5 +02:00'::TIMESTAMP_TZ AS t7, '2024-01-02 03:04:05 -05:00'::TIMESTAMP_TZ(0) AS t8,
               '2024-01-02 03:04:05.5 +00:00'::TIMESTAMP_TZ AS t9, '2024-01-02'::DATE AS d1, '1969-12-31'::DATE AS d2,
               '03:04:05.5'::TIME AS m1, '03:04:05.123'::TIME(3) AS m2, '00:00:00'::TIME(0) AS m3,
               12.5::NUMBER(4,2) AS n1, -12.5::NUMBER(10,1) AS n2, 2.0::NUMBER(38,1) AS n3, 1.5::DECFLOAT AS n4,
               NULL::TIMESTAMP_NTZ AS n5""";
    private static final String REFUSALS = """
        T1 0A000 200038 type:93 to type:byte array, value=1704164645.123456789.
        T2 0A000 200038 type:93 to type:byte array, value=1704164645.123.
        T3 0A000 200038 type:93 to type:byte array, value=1704164645.
        T4 0A000 200038 type:93 to type:byte array, value=-315619199.500000000.
        T5 0A000 200038 type:93 to type:byte array, value=1704164645.000000000.
        T6 0A000 200038 type:93 to type:byte array, value=-315619199.5.
        T7 0A000 200038 type:2,014 to type:byte array, value=1704157445.500000000 1560.
        T8 0A000 200038 type:2,014 to type:byte array, value=1704182645 1140.
        T9 0A000 200038 type:2,014 to type:byte array, value=1704164645.500000000 1440.
        D1 0A000 200038 type:91 to type:byte array, value=19724.
        D2 0A000 200038 type:91 to type:byte array, value=-1.
        M1 0A000 200038 type:92 to type:byte array, value=11045.500000000.
        M2 0A000 200038 type:92 to type:byte array, value=11045.123.
        M3 0A000 200038 type:92 to type:byte array, value=0.
        N1 0A000 200038 type:3 to type:byte array, value=12.50.
        N2 0A000 200038 type:3 to type:byte array, value=-12.5.
        N3 0A000 200038 type:3 to type:byte array, value=2.0.
        N4 0A000 200038 type:3 to type:byte array, value=1.5.
        N5 null
        """;

    private static final String CLOCKS = """
        SELECT '2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ AS ntz,
               '2024-01-02 03:04:05.5 +02:00'::TIMESTAMP_TZ AS tz, '2024-01-02 03:04:05'::TIMESTAMP_LTZ AS ltz,
               '2024-01-02 03:04:05'::TIMESTAMP_NTZ(0) AS ntz0""";
    private static final String[] NUMERIC_GETTERS =
        {"int", "long", "short", "double", "float", "big decimal", "boolean"};
    private static final String CLOCK_READS = """
        NTZ int 0A000 200038 type:93 to type:int, value=1704164645.123456789.
        NTZ long 0A000 200038 type:93 to type:long, value=1704164645.123456789.
        NTZ short 0A000 200038 type:93 to type:short, value=1704164645.123456789.
        NTZ double 0A000 200038 type:93 to type:double, value=1704164645.123456789.
        NTZ float 0A000 200038 type:93 to type:float, value=1704164645.123456789.
        NTZ big decimal 0A000 200038 type:93 to type:big decimal, value=1704164645.123456789.
        NTZ boolean 0A000 200038 type:93 to type:boolean, value=1704164645.123456789.
        TZ int 0A000 200038 type:2,014 to type:int, value=1704157445.500000000 1560.
        TZ long 0A000 200038 type:2,014 to type:long, value=1704157445.500000000 1560.
        TZ short 0A000 200038 type:2,014 to type:short, value=1704157445.500000000 1560.
        TZ double 0A000 200038 type:2,014 to type:double, value=1704157445.500000000 1560.
        TZ float 0A000 200038 type:2,014 to type:float, value=1704157445.500000000 1560.
        TZ big decimal 0A000 200038 type:2,014 to type:big decimal, value=1704157445.500000000 1560.
        TZ boolean 0A000 200038 type:2,014 to type:boolean, value=1704157445.500000000 1560.
        LTZ int 0A000 200038 type:93 to type:int, value=1704164645.000000000.
        LTZ long 0A000 200038 type:93 to type:long, value=1704164645.000000000.
        LTZ short 0A000 200038 type:93 to type:short, value=1704164645.000000000.
        LTZ double 0A000 200038 type:93 to type:double, value=1704164645.000000000.
        LTZ float 0A000 200038 type:93 to type:float, value=1704164645.000000000.
        LTZ big decimal 0A000 200038 type:93 to type:big decimal, value=1704164645.000000000.
        LTZ boolean 0A000 200038 type:93 to type:boolean, value=1704164645.000000000.
        NTZ0 int 1704164645
        NTZ0 long 1704164645
        NTZ0 short 0A000 200038 type:93 to type:short, value=1704164645.
        NTZ0 double 0A000 200038 type:93 to type:double, value=1704164645.
        NTZ0 float 0A000 200038 type:93 to type:float, value=1704164645.
        NTZ0 big decimal 0A000 200038 type:93 to type:big decimal, value=1704164645.
        NTZ0 boolean 0A000 200038 type:93 to type:boolean, value=1704164645.
        """;

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
    }

    @Override
    protected void teardownTest() throws SQLException {
        statement.execute("ALTER SESSION UNSET TIMEZONE");
    }

    /** A refusal as one line: its SQLSTATE, its vendor code and its message, the common opening dropped. */
    private static String refusal(final SQLException refused) {
        final String message = refused.getMessage();
        return refused.getSQLState() + " " + refused.getErrorCode() + " "
            + (message.startsWith(REFUSAL) ? message.substring(REFUSAL.length()) : message);
    }

    /** Every column's getBytes, a line each: its label and its bytes, or the refusal. */
    private static String bytesOf(final Statement on, final String sql) throws SQLException {
        final StringBuilder out = new StringBuilder();
        try (ResultSet rs = on.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            rs.next();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String read;
                try {
                    final byte[] bytes = rs.getBytes(i);
                    read = bytes == null ? "null" : Arrays.toString(bytes);
                } catch (final SQLException refused) {
                    read = refusal(refused);
                }
                out.append(md.getColumnLabel(i).toUpperCase(Locale.ROOT)).append(' ').append(read).append('\n');
            }
        }
        return out.toString();
    }

    /** Every column read by each numeric getter, a line each: its label, the getter, and the value or the refusal. */
    private static String numericReadsOf(final Statement on, final String sql) throws SQLException {
        final StringBuilder out = new StringBuilder();
        try (ResultSet rs = on.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            rs.next();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                for (final String getter : NUMERIC_GETTERS) {
                    String read;
                    try {
                        read = numericRead(rs, i, getter);
                    } catch (final SQLException refused) {
                        read = refusal(refused);
                    }
                    out.append(md.getColumnLabel(i).toUpperCase(Locale.ROOT)).append(' ').append(getter).append(' ')
                        .append(read).append('\n');
                }
            }
        }
        return out.toString();
    }

    private static String numericRead(final ResultSet rs, final int column, final String getter) throws SQLException {
        switch (getter) {
            case "int":
                return String.valueOf(rs.getInt(column));
            case "long":
                return String.valueOf(rs.getLong(column));
            case "short":
                return String.valueOf(rs.getShort(column));
            case "double":
                return String.valueOf(rs.getDouble(column));
            case "float":
                return String.valueOf(rs.getFloat(column));
            case "big decimal":
                return String.valueOf(rs.getBigDecimal(column));
            default:
                return String.valueOf(rs.getBoolean(column));
        }
    }

    @Test
    public void eachFamilyReadsAsItsOwnBytes() throws SQLException {
        assertEquals(FAMILY_BYTES, bytesOf(statement, FAMILIES));
    }

    @Test
    public void numbersWithAPointAndTemporalsAreRefusedQuotingTheirWireForm() throws SQLException {
        assertEquals(REFUSALS, bytesOf(statement, REFUSED));
    }

    @Test
    public void numericGettersOverATimestampReadItsWireForm() throws SQLException {
        assertEquals(CLOCK_READS, numericReadsOf(statement, CLOCKS));
    }

    /** The HTTP transport's cells are the wire's text, and it answers every read alike. */
    @Test
    public void theHttpTransportReadsTheSameBytes() throws SQLException, IOException {
        assumeFalse(isLiveSnowflake(), "the engine's own HTTP transport has no live counterpart");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection http = DriverManager.getConnection("jdbc:frostlake://localhost:" + port);
             Statement over = http.createStatement()) {
            over.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
            assertEquals(FAMILY_BYTES, bytesOf(over, FAMILIES));
            assertEquals(REFUSALS, bytesOf(over, REFUSED));
            assertEquals(CLOCK_READS, numericReadsOf(over, CLOCKS));
        } finally {
            server.stop();
        }
    }
}
