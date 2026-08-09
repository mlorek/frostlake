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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code getString} prints for each temporal type, measured against a real account's driver:
 *
 * <pre>
 *   DATE            2026-08-07
 *   TIME            12:34:56                        no fractional part, ever
 *   TIMESTAMP_NTZ   2026-08-07 12:34:56.789
 *   TIMESTAMP_LTZ   2026-08-07 12:34:56.789 -0700
 * </pre>
 *
 * <p>Frostlake used to print {@code value.toString()} for all four — the ISO form with a {@code T} and
 * up to nine fractional digits — because both transports' {@code getString} was exactly that call.
 *
 * <p>The offset is the SESSION's, so this asserts its shape rather than a fixed zone: a test that
 * hardcoded {@code -0700} would only pass in one timezone.
 */
public class TemporalTextTest {

    private Connection connection;

    @AfterEach
    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    private ResultSet temporalRow(final String url) throws Exception {
        connection = DriverManager.getConnection(url);
        final Statement st = connection.createStatement();
        st.execute("CREATE DATABASE IF NOT EXISTS tt_db");
        st.execute("USE DATABASE tt_db");
        st.execute("CREATE SCHEMA IF NOT EXISTS tt_s");
        st.execute("USE SCHEMA tt_s");
        st.execute("CREATE OR REPLACE TABLE tt (d DATE, tm TIME, ntz TIMESTAMP_NTZ, ltz TIMESTAMP_LTZ)");
        st.execute("INSERT INTO tt VALUES ('2026-08-07', '12:34:56.789',"
            + " '2026-08-07 12:34:56.789', '2026-08-07 12:34:56.789')");
        final ResultSet rs = st.executeQuery("SELECT d, tm, ntz, ltz FROM tt");
        rs.next();
        return rs;
    }

    private void assertTheMeasuredShapes(final ResultSet rs) throws Exception {
        assertEquals("2026-08-07", rs.getString("d"));
        // TIME drops its fraction entirely — .789 is not reported.
        assertEquals("12:34:56", rs.getString("tm"));
        // Exactly three fractional digits, space-separated, no 'T'.
        assertEquals("2026-08-07 12:34:56.789", rs.getString("ntz"));
        // The zoned one adds the session's numeric offset.
        final String ltz = rs.getString("ltz");
        assertTrue(ltz.startsWith("2026-08-07 12:34:56.789 "), ltz);
        assertTrue(ltz.matches(".* [+-]\\d{4}$"), ltz);
    }

    @Test
    public void theInProcessTransportPrintsTheMeasuredShapes() throws Exception {
        assertTheMeasuredShapes(temporalRow("jdbc:frostlake:direct:temporal_text"));
    }

    /** A timestamp still re-types, which is the reason the wire may carry text at all. */
    @Test
    public void aRenderedTimestampStillReadsBackAsATimestamp() throws Exception {
        final ResultSet rs = temporalRow("jdbc:frostlake:direct:temporal_roundtrip");
        assertEquals("2026-08-07 12:34:56.789", rs.getTimestamp("ltz").toString());
        assertEquals("2026-08-07", rs.getDate("d").toString());
    }

    /** Three fractional digits: PADDED for a shorter value, TRUNCATED for a longer one. */
    @Test
    public void theFractionIsAlwaysThreeDigits() throws Exception {
        connection = DriverManager.getConnection("jdbc:frostlake:direct:temporal_fraction");
        final Statement st = connection.createStatement();
        st.execute("CREATE DATABASE IF NOT EXISTS tf_db");
        st.execute("USE DATABASE tf_db");
        st.execute("CREATE SCHEMA IF NOT EXISTS tf_s");
        st.execute("USE SCHEMA tf_s");
        st.execute("CREATE OR REPLACE TABLE tf (a TIMESTAMP_NTZ, b TIMESTAMP_NTZ, c TIMESTAMP_NTZ)");
        st.execute("INSERT INTO tf VALUES ('2026-08-07 12:34:56.1', '2026-08-07 12:34:56',"
            + " '2026-08-07 12:34:56.123456789')");
        try (ResultSet rs = st.executeQuery("SELECT a, b, c FROM tf")) {
            rs.next();
            assertEquals("2026-08-07 12:34:56.100", rs.getString("a"));
            assertEquals("2026-08-07 12:34:56.000", rs.getString("b"));
            assertEquals("2026-08-07 12:34:56.123", rs.getString("c"));
        }
    }

    /** A non-temporal column is untouched — the wire keeps a number a number. */
    @Test
    public void aNumberIsNotRenderedAsText() throws Exception {
        connection = DriverManager.getConnection("jdbc:frostlake:direct:temporal_number");
        final Statement st = connection.createStatement();
        st.execute("CREATE DATABASE IF NOT EXISTS tn_db");
        st.execute("USE DATABASE tn_db");
        st.execute("CREATE SCHEMA IF NOT EXISTS tn_s");
        st.execute("USE SCHEMA tn_s");
        st.execute("CREATE OR REPLACE TABLE tn (n INTEGER)");
        st.execute("INSERT INTO tn VALUES (42)");
        try (ResultSet rs = st.executeQuery("SELECT n FROM tn")) {
            rs.next();
            assertEquals("42", rs.getString("n"));
            assertEquals(42, rs.getInt("n"));
        }
    }
}
