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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.executor.expressions.IntervalCells;
import dev.frostlake.http.ColumnData;
import dev.frostlake.http.DatabaseHttpServer;
import dev.frostlake.http.ResultSetData;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An interval column read through Frostlake's own driver, on BOTH transports, cell by cell as Snowflake's JDBC
 * driver reads one with its default ARROW results (every expected value below is that driver's answer on the
 * account). The metadata names the family with codes 50006 / 50005, precision 0, a scale that says which
 * fields the interval spans, and refuses a column class. The cells follow the column's storage width: a
 * sixteen-byte day-time interval (DAY, HOUR, MINUTE, a TIMESTAMP difference) reads as a BigDecimal count of
 * nanoseconds, an eight-byte one (SECOND) as a Duration, and a year-month interval as a Period.
 */
public class IntervalColumnDriverTest {

    private static DatabaseHttpServer server;
    private static String httpUrl;

    private final List<Connection> connections = new ArrayList<Connection>();
    private final List<String> transports = new ArrayList<String>();

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        httpUrl = "jdbc:frostlake://localhost:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    public void connect() throws SQLException {
        connections.add(DriverManager.getConnection("jdbc:frostlake:direct:interval-driver-" + System.nanoTime()));
        transports.add("direct");
        connections.add(DriverManager.getConnection(httpUrl));
        transports.add("http");
        for (final Connection connection : connections) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE OR REPLACE DATABASE interval_driver_db");
                statement.execute("USE SCHEMA interval_driver_db.public");
                statement.execute("CREATE OR REPLACE TABLE fam (ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ)");
                statement.execute("INSERT INTO fam VALUES ('2020-01-02 01:00:00', '2020-01-01 00:00:00')");
            }
        }
    }

    @AfterEach
    public void disconnect() throws SQLException {
        for (final Connection connection : connections) {
            connection.close();
        }
    }

    /** One row of one statement, positioned on its first row. */
    private static ResultSet first(final Connection connection, final String sql) throws SQLException {
        final ResultSet rs = connection.createStatement().executeQuery(sql);
        assertTrue(rs.next(), sql);
        return rs;
    }

    private static SQLException refusal(final String label, final Executable read) {
        return assertThrows(SQLException.class, read, label);
    }

    /** A sixteen-byte day-time cell: metadata, a BigDecimal of nanoseconds, and the getters around it. */
    @Test
    public void aWideDayTimeCellReadsAsItsNanoseconds() throws SQLException {
        for (int t = 0; t < connections.size(); t++) {
            final String on = transports.get(t);
            final ResultSet rs = first(connections.get(t), "SELECT INTERVAL '1' DAY");
            final ResultSetMetaData meta = rs.getMetaData();
            assertEquals("INTERVAL_DAY_TIME", meta.getColumnTypeName(1), on);
            assertEquals(50006, meta.getColumnType(1), on);
            assertEquals(0, meta.getPrecision(1), on);
            assertEquals(6, meta.getScale(1), on);
            assertEquals(25, meta.getColumnDisplaySize(1), on);
            assertFalse(meta.isSigned(1), on);
            final SQLFeatureNotSupportedException noClass = assertThrows(SQLFeatureNotSupportedException.class,
                new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        meta.getColumnClassName(1);
                    }
                }, on);
            assertEquals("No corresponding Java type is found for java.sql.Type: 50006", noClass.getMessage(), on);

            assertEquals("86400000000000", rs.getString(1), on);
            assertEquals(new BigDecimal("86400000000000"), rs.getObject(1), on);
            assertEquals(86_400_000_000_000L, rs.getLong(1), on);
            assertEquals(new BigDecimal("86400000000000"), rs.getBigDecimal(1), on);
            assertEquals(8.64e13, rs.getDouble(1), 0.0, on);
            assertArrayEquals(new byte[] {0x4E, (byte) 0x94, (byte) 0x91, 0x4F, 0x00, 0x00}, rs.getBytes(1), on);
            assertEquals(Duration.ofHours(24), rs.getObject(1, Duration.class), on);
            assertEquals("86400000000000", rs.getObject(1, String.class), on);
            assertEquals(Long.valueOf(86_400_000_000_000L), rs.getObject(1, Long.class), on);

            final SQLException noInt = refusal(on, new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getInt(1);
                }
            });
            assertEquals("Cannot convert value in the driver from type:FIXED(null,null) to type:Int, "
                + "value=86400000000000.", noInt.getMessage(), on);
            assertEquals("0A000", noInt.getSQLState(), on);
            assertEquals(200038, noInt.getErrorCode(), on);
            assertEquals("Cannot convert value in the driver from type:FIXED(null,null) to type:Boolean, "
                + "value=86400000000000.", refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getBoolean(1);
                    }
                }).getMessage(), on);
            assertEquals("Cannot convert value in the driver from type:FIXED(null,null) to type:timestamp, value=.",
                refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getTimestamp(1);
                    }
                }).getMessage(), on);
            final SQLException noPeriod = refusal(on, new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getObject(1, Period.class);
                }
            });
            assertEquals("Type passed to 'getObject(int columnIndex,Class<T> type)' is unsupported. "
                + "Type: java.time.Period", noPeriod.getMessage(), on);
            assertNull(noPeriod.getSQLState(), on);
        }
    }

    /** A TIMESTAMP difference is the widest day-time type, scale 3, its value in nanoseconds either sign. */
    @Test
    public void aTimestampDifferenceReadsAsItsNanoseconds() throws SQLException {
        for (int t = 0; t < connections.size(); t++) {
            final String on = transports.get(t);
            final ResultSet rs = first(connections.get(t), "SELECT ts - ts2 AS x, ts2 - ts AS y, 1 AS z FROM fam");
            assertEquals("INTERVAL_DAY_TIME", rs.getMetaData().getColumnTypeName(1), on);
            assertEquals(3, rs.getMetaData().getScale(1), on);
            assertEquals("90000000000000", rs.getString(1), on);
            assertEquals(new BigDecimal("-90000000000000"), rs.getObject("y"), on);
            assertEquals(Duration.ofHours(-25), rs.getObject(2, Duration.class), on);
            assertEquals(1L, ((Number) rs.getObject(3)).longValue(), on);
        }
    }

    /** A narrow getter reads a wide count while it fits, and a count of 0 or 1 reads as a boolean. */
    @Test
    public void aWideCountReadsNarrowWhileItFits() throws SQLException {
        for (int t = 0; t < connections.size(); t++) {
            final String on = transports.get(t);
            final ResultSet rs = first(connections.get(t),
                "SELECT TO_TIMESTAMP_NTZ('2020-01-01 00:00:00.000000128') - TO_TIMESTAMP_NTZ('2020-01-01'),"
                + " TO_TIMESTAMP_NTZ('2020-01-01 00:00:00.000000001') - TO_TIMESTAMP_NTZ('2020-01-01')");
            assertEquals(128, rs.getInt(1), on);
            assertEquals((short) 128, rs.getShort(1), on);
            assertEquals("Cannot convert value in the driver from type:FIXED(null,null) to type:Byte, value=128.",
                refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getByte(1);
                    }
                }).getMessage(), on);
            assertArrayEquals(new byte[] {0x00, (byte) 0x80}, rs.getBytes(1), on);
            assertTrue(rs.getBoolean(2), on);
            assertEquals(Integer.valueOf(1), rs.getObject(2, Integer.class), on);
        }
    }

    /** An eight-byte day-time cell (SECOND) is a Duration, and nothing numeric reads it. */
    @Test
    public void aNarrowDayTimeCellIsADuration() throws SQLException {
        for (int t = 0; t < connections.size(); t++) {
            final String on = transports.get(t);
            final ResultSet rs = first(connections.get(t), "SELECT INTERVAL '1' SECOND, INTERVAL '-1' SECOND");
            assertEquals(12, rs.getMetaData().getScale(1), on);
            assertEquals("PT1S", rs.getString(1), on);
            assertEquals(Duration.ofSeconds(1), rs.getObject(1), on);
            assertEquals(Duration.ofSeconds(-1), rs.getObject(2, Duration.class), on);
            assertEquals("PT-1S", rs.getObject(2, String.class), on);
            assertEquals("Cannot convert value in the driver from type:INTERVAL_DAY_TIME to type:long, value=.",
                refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getLong(1);
                    }
                }).getMessage(), on);
            assertEquals("Cannot convert value in the driver from type:INTERVAL_DAY_TIME to type:int, value={2}.",
                refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getInt(1);
                    }
                }).getMessage(), on);
            assertEquals("Cannot convert value in the driver from type:INTERVAL_DAY_TIME to type:big decimal, value=.",
                refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getBigDecimal(1);
                    }
                }).getMessage(), on);
        }
    }

    /** A second-sized column carries a wider value as a Duration too — the column's width decides. */
    @Test
    public void aSecondColumnCarriesADayAsADuration() throws SQLException {
        for (int t = 0; t < connections.size(); t++) {
            final String on = transports.get(t);
            final ResultSet rs = connections.get(t).createStatement().executeQuery(
                "SELECT INTERVAL '1' SECOND AS s UNION ALL SELECT INTERVAL '1' DAY");
            final List<String> texts = new ArrayList<String>();
            while (rs.next()) {
                texts.add(rs.getString(1));
            }
            assertTrue(texts.contains("PT1S") && texts.contains("PT24H") && texts.size() == 2, on + " " + texts);
        }
    }

    /** A year-month cell is a normalized Period, its text ISO, and nothing numeric reads it. */
    @Test
    public void aYearMonthCellIsAPeriod() throws SQLException {
        for (int t = 0; t < connections.size(); t++) {
            final String on = transports.get(t);
            final ResultSet rs = first(connections.get(t),
                "SELECT INTERVAL '1' YEAR, INTERVAL '14' MONTH, INTERVAL '-14' MONTH, INTERVAL '0' MONTH");
            final ResultSetMetaData meta = rs.getMetaData();
            assertEquals("INTERVAL_YEAR_MONTH", meta.getColumnTypeName(1), on);
            assertEquals(50005, meta.getColumnType(1), on);
            assertEquals(1, meta.getScale(1), on);
            assertEquals(2, meta.getScale(2), on);
            assertEquals("P1Y", rs.getString(1), on);
            assertEquals(Period.of(1, 2, 0), rs.getObject(2), on);
            assertEquals("P-1Y-2M", rs.getString(3), on);
            assertEquals("P0D", rs.getString(4), on);
            assertEquals(Period.ofYears(1), rs.getObject(1, Period.class), on);
            assertEquals("Cannot convert value in the driver from type:INTERVAL_YEAR_MONTH to type:int, value={2}.",
                refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getInt(2);
                    }
                }).getMessage(), on);
            assertEquals("Type passed to 'getObject(int columnIndex,Class<T> type)' is unsupported. "
                + "Type: java.time.Duration", refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getObject(2, Duration.class);
                    }
                }).getMessage(), on);
        }
    }

    /**
     * A NULL interval is NULL or zero to most getters, but a temporal getter refuses it whatever the width, and a
     * narrow or year-month column refuses getByte and getBoolean over it as well.
     */
    @Test
    public void aNullIntervalFollowsTheColumnsWidth() throws SQLException {
        for (int t = 0; t < connections.size(); t++) {
            final String on = transports.get(t);
            final ResultSet rs = first(connections.get(t), "SELECT CASE WHEN FALSE THEN INTERVAL '1' DAY END,"
                + " CASE WHEN FALSE THEN INTERVAL '1' SECOND END, CASE WHEN FALSE THEN INTERVAL '1' YEAR END");
            for (int column = 1; column <= 3; column++) {
                assertNull(rs.getString(column), on);
                assertTrue(rs.wasNull(), on);
                assertNull(rs.getObject(column), on);
                assertEquals(0L, rs.getLong(column), on);
                assertEquals(Integer.valueOf(0), rs.getObject(column, Integer.class), on);
                assertNull(rs.getObject(column, Duration.class), on);
            }
            assertFalse(rs.getBoolean(1), on);
            assertEquals((byte) 0, rs.getByte(1), on);
            assertEquals("Cannot convert value in the driver from type:FIXED(null,null) to type:date, value=.",
                refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getDate(1);
                    }
                }).getMessage(), on);
            assertEquals("Cannot convert value in the driver from type:INTERVAL_DAY_TIME to type:boolean, value=.",
                refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getBoolean(2);
                    }
                }).getMessage(), on);
            assertEquals("Cannot convert value in the driver from type:INTERVAL_YEAR_MONTH to type:byte, value=.",
                refusal(on, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        rs.getByte(3);
                    }
                }).getMessage(), on);
        }
    }

    /** A query that merely USES an interval is untouched. */
    @Test
    public void aQueryUsingAnIntervalIsUntouched() throws SQLException {
        for (int t = 0; t < connections.size(); t++) {
            final String on = transports.get(t);
            final ResultSet rs = first(connections.get(t), "SELECT ts + INTERVAL '1' DAY FROM fam");
            assertEquals("TIMESTAMPNTZ", rs.getMetaData().getColumnTypeName(1), on);
            assertEquals("2020-01-03 01:00:00.000", rs.getString(1), on);
        }
    }

    /**
     * The wire itself never carries an engine object: the column crosses under its family name and the cell as
     * its digits, while the embedded API keeps handing back the engine's own interval.
     */
    @Test
    public void theWireCarriesDigitsAndTheEngineKeepsItsValue() {
        final DatabaseEngine engine = new DatabaseEngine();
        final dev.frostlake.storage.ResultSet result = engine.executeQuery(
            "SELECT INTERVAL '1' DAY AS d, INTERVAL '1' YEAR AS y, INTERVAL '1' SECOND AS s");
        assertTrue(IntervalCells.isInterval(result.getRows().get(0).getValue(0)));
        final ResultSetData wire = ResultSetData.from(result);
        final ColumnData day = wire.getColumns().get(0);
        assertEquals("INTERVAL_DAY_TIME", day.getDataType());
        assertEquals(0, day.getPrecision());
        assertEquals(6, day.getScale());
        assertEquals("INTERVAL_YEAR_MONTH", wire.getColumns().get(1).getDataType());
        assertEquals(12, wire.getColumns().get(2).getScale());
        assertArrayEquals(new Object[] {"86400000000000", "12", "1000000000"}, wire.getRows().get(0).toArray());
    }
}
