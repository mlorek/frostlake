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
import dev.frostlake.LiveSnowflake;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * {@code ResultSetMetaData} answers a column's precision, scale and display size per type, as Snowflake's
 * driver does: a NUMBER(p,s) is p, s and p + 1 (one more with a fraction), a DOUBLE 0, 0 and 24, a DATE 10,
 * a TIME 8, a TIMESTAMP_NTZ 23 and an LTZ or TZ 29, each with its fractional digits as the scale, a BOOLEAN
 * displays at 5, a VECTOR at 25, text and binary at their length, and semi-structured values at 0. Both
 * transports answer alike: the HTTP wire carries a time or timestamp's fractional digits as the column's
 * scale. Live-verified.
 */
public class DriverColumnMetricsTest extends BaseJdbcTest {

    private static final String FIRST = "SELECT 1.5::NUMBER(5,2) AS n, 1.5::FLOAT AS f, '2024-01-15'::DATE AS d,"
        + " '2024-01-01 00:00:00'::TIMESTAMP_NTZ AS ts, TRUE AS bo, PARSE_JSON('1') AS var, NULL AS nul";
    private static final String NUMBERS = "SELECT 1::NUMBER(38,0) AS n38, 1::NUMBER(10,4) AS n104,"
        + " 1::NUMBER(38,37) AS n3837, 123 AS lit3, 1.25 AS dec, 1::INT AS i, 1::FLOAT4 AS f4";
    private static final String OTHERS = "SELECT 'abc'::VARCHAR(10) AS v10, X'00'::BINARY(5) AS b5,"
        + " ARRAY_CONSTRUCT(1) AS arr, OBJECT_CONSTRUCT('a', 1) AS obj, [1,2]::VECTOR(INT, 2) AS vec";
    private static final String TEMPORALS = """
        SELECT '10:00:00'::TIME AS t, '10:00:00.123'::TIME(3) AS t3, '2024-01-01'::TIMESTAMP_NTZ(3) AS ntz3,
               '2024-01-01 00:00:00'::TIMESTAMP_LTZ AS ltz, '2024-01-01'::TIMESTAMP_LTZ(6) AS ltz6,
               '2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ(0) AS tz0""";
    private static final String TEMPORAL_METRICS = "T 8 9 8 | T3 8 3 8 | NTZ3 23 3 23 | LTZ 29 9 29 | LTZ6 29 6 29"
        + " | TZ0 29 0 29";

    /** Each column's label, precision, scale and display size, a bar between columns. */
    private static String metrics(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            final StringBuilder out = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (i > 1) {
                    out.append(" | ");
                }
                out.append(md.getColumnLabel(i)).append(' ').append(md.getPrecision(i)).append(' ')
                    .append(md.getScale(i)).append(' ').append(md.getColumnDisplaySize(i));
            }
            return out.toString();
        }
    }

    @Test
    public void eachTypeReportsItsOwnPrecisionScaleAndDisplaySize() throws SQLException {
        assertEquals("N 5 2 7 | F 0 0 24 | D 10 0 10 | TS 23 9 23 | BO 0 0 5 | VAR 0 0 0 | NUL 0 0 0",
            metrics(statement, FIRST));
        assertEquals("N38 38 0 39 | N104 10 4 12 | N3837 38 37 40 | LIT3 3 0 4 | DEC 3 2 5 | I 38 0 39 | F4 0 0 24",
            metrics(statement, NUMBERS));
        assertEquals("V10 10 0 10 | B5 5 0 5 | ARR 0 0 0 | OBJ 0 0 0 | VEC 0 0 25", metrics(statement, OTHERS));
    }

    @Test
    public void aTimeOrTimestampScaleIsItsFractionalDigits() throws SQLException {
        assertEquals(TEMPORAL_METRICS, metrics(statement, TEMPORALS));
    }

    @Test
    public void aTableColumnReportsItsDeclaredType() throws SQLException {
        statement.execute("""
            CREATE OR REPLACE TABLE metrics_t (n NUMBER(5,2), f FLOAT, d DATE, ts TIMESTAMP_NTZ, b BOOLEAN, t TIME,
                ltz TIMESTAMP_LTZ(3), tz TIMESTAMP_TZ, n38 NUMBER(38,0), v VARCHAR(20), bi BINARY(8), var VARIANT)""");
        assertEquals("N 5 2 7 | F 0 0 24 | D 10 0 10 | TS 23 9 23 | B 0 0 5 | T 8 9 8 | LTZ 29 3 29 | TZ 29 9 29"
            + " | N38 38 0 39 | V 20 0 20 | BI 8 0 8 | VAR 0 0 0", metrics(statement, "SELECT * FROM metrics_t"));
    }

    /** The HTTP transport answers the same from the wire's type name, precision, scale and length. */
    @Test
    public void theHttpTransportAnswersAlike() throws SQLException, IOException {
        assumeFalse(LiveSnowflake.enabled(), "the engine's own HTTP transport has no live counterpart");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection http = DriverManager.getConnection("jdbc:frostlake://localhost:" + port);
             Statement over = http.createStatement()) {
            assertEquals("N 5 2 7 | F 0 0 24 | D 10 0 10 | TS 23 9 23 | BO 0 0 5 | VAR 0 0 0 | NUL 0 0 0",
                metrics(over, FIRST));
            assertEquals("N38 38 0 39 | N104 10 4 12 | N3837 38 37 40 | LIT3 3 0 4 | DEC 3 2 5 | I 38 0 39 | F4 0 0 24",
                metrics(over, NUMBERS));
            assertEquals("V10 10 0 10 | B5 5 0 5 | ARR 0 0 0 | OBJ 0 0 0 | VEC 0 0 25", metrics(over, OTHERS));
            assertEquals(TEMPORAL_METRICS, metrics(over, TEMPORALS));
        } finally {
            server.stop();
        }
    }
}
