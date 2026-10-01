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
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A TIME or TIMESTAMP result column reports its fractional-second precision as its scale, as Snowflake's
 * driver does — whether a table declares it, an expression or a cast carries it, a set operation folds it
 * from its arms, or a SHOW command or an INFORMATION_SCHEMA view answers it — and the HTTP transport, which
 * reads the scale off the wire, answers exactly what the in-process transport answers. The precision stays
 * the driver's display width: 8 for a TIME, 23 for a TIMESTAMP_NTZ, 29 for an LTZ or TZ; a DATE is 10 and
 * scale 0.
 */
public class TemporalColumnScaleTest extends BaseJdbcTest {

    private static final String TABLE = """
        CREATE OR REPLACE TABLE scale_t (t TIME, t0 TIME(0), t3 TIME(3), ntz TIMESTAMP_NTZ, ntz0 TIMESTAMP_NTZ(0),
            ntz6 TIMESTAMP_NTZ(6), ltz0 TIMESTAMP_LTZ(0), ltz3 TIMESTAMP_LTZ(3), tz TIMESTAMP_TZ,
            tz5 TIMESTAMP_TZ(5), dt4 DATETIME(4), d DATE)""";
    private static final String COLUMNS = "SELECT * FROM scale_t";
    private static final String DECLARED = "T TIME 8 9 | T0 TIME 8 0 | T3 TIME 8 3 | NTZ TIMESTAMPNTZ 23 9"
        + " | NTZ0 TIMESTAMPNTZ 23 0 | NTZ6 TIMESTAMPNTZ 23 6 | LTZ0 TIMESTAMPLTZ 29 0 | LTZ3 TIMESTAMPLTZ 29 3"
        + " | TZ TIMESTAMPTZ 29 9 | TZ5 TIMESTAMPTZ 29 5 | DT4 TIMESTAMPNTZ 23 4 | D DATE 10 0";

    private static final String EXPRESSIONS = """
        SELECT CURRENT_TIMESTAMP AS a, CURRENT_TIMESTAMP(3) AS b, CURRENT_TIMESTAMP(0) AS c, LOCALTIMESTAMP(2) AS d,
               SYSDATE() AS e, CURRENT_TIME(3) AS f, LOCALTIME(1) AS g, TO_TIMESTAMP(1700000000) AS h,
               TO_TIMESTAMP(1700000000123, 3) AS i, CAST(NULL AS TIME(4)) AS j, NULL::TIMESTAMP_TZ(2) AS k,
               '2024-01-01'::TIMESTAMP_NTZ(7) AS l, TO_DATE('2024-01-01') AS m""";
    private static final String CARRIED = "A TIMESTAMPLTZ 29 9 | B TIMESTAMPLTZ 29 3 | C TIMESTAMPLTZ 29 0"
        + " | D TIMESTAMPLTZ 29 2 | E TIMESTAMPNTZ 23 9 | F TIME 8 3 | G TIME 8 1 | H TIMESTAMPNTZ 23 0"
        + " | I TIMESTAMPNTZ 23 3 | J TIME 8 4 | K TIMESTAMPTZ 29 2 | L TIMESTAMPNTZ 23 7 | M DATE 10 0";

    private static final String AGGREGATES =
        "SELECT MIN(t3) AS a, MAX(ntz0) AS b, ANY_VALUE(ltz3) AS c, MIN(tz5) AS e FROM scale_t";
    private static final String AGGREGATED = "A TIME 8 3 | B TIMESTAMPNTZ 23 0 | C TIMESTAMPLTZ 29 3"
        + " | E TIMESTAMPTZ 29 5";
    private static final String CONDITIONALS = "SELECT GREATEST(ntz0, ntz6) AS g, COALESCE(ntz0, ntz6) AS h,"
        + " IFF(TRUE, ntz0, ntz6) AS i, COALESCE(ntz0, ntz6, '2024-01-01') AS j FROM scale_t";
    private static final String WIDER = "G TIMESTAMPNTZ 23 6 | H TIMESTAMPNTZ 23 6 | I TIMESTAMPNTZ 23 6"
        + " | J TIMESTAMPNTZ 23 6";

    /** Each set operation's one column U, in either written order and across flavours. */
    private static final String[] SET_OPERATIONS = {
        "SELECT ntz0 AS u FROM scale_t UNION ALL SELECT ntz FROM scale_t",
        "SELECT ntz6 AS u FROM scale_t UNION ALL SELECT ntz0 FROM scale_t",
        "SELECT t0 AS u FROM scale_t UNION ALL SELECT t FROM scale_t",
        "SELECT ltz3 AS u FROM scale_t UNION SELECT CURRENT_TIMESTAMP",
        "SELECT ntz AS u FROM scale_t UNION ALL SELECT ltz3 FROM scale_t",
        "SELECT tz5 AS u FROM scale_t UNION ALL SELECT ntz FROM scale_t",
        "SELECT ltz0 AS u FROM scale_t UNION ALL SELECT ntz6 FROM scale_t",
        "SELECT ntz0 AS u FROM scale_t EXCEPT SELECT ntz6 FROM scale_t",
        "SELECT t0 AS u FROM scale_t INTERSECT SELECT t3 FROM scale_t",
        "SELECT d AS u FROM scale_t UNION ALL SELECT ntz6 FROM scale_t",
        "SELECT NULL AS u UNION ALL SELECT ntz6 FROM scale_t",
        "SELECT ntz0 AS u FROM scale_t UNION ALL SELECT '2024-01-01 00:00:00'",
        "SELECT ntz0 AS u FROM scale_t UNION ALL BY NAME SELECT ntz AS u FROM scale_t",
        "SELECT MAX(u) AS u FROM (SELECT ntz0 AS u FROM scale_t UNION ALL SELECT ntz6 FROM scale_t)",
        "WITH RECURSIVE c (u, i) AS (SELECT ntz0, 1 FROM scale_t UNION ALL SELECT DATEADD(day, 1, u), i + 1 FROM c"
            + " WHERE i < 2) SELECT u FROM c"};
    private static final String WIDEST_ARMS = "TIMESTAMPNTZ 23 9 | TIMESTAMPNTZ 23 6 | TIME 8 9 | TIMESTAMPLTZ 29 9"
        + " | TIMESTAMPLTZ 29 9 | TIMESTAMPTZ 29 9 | TIMESTAMPLTZ 29 6 | TIMESTAMPNTZ 23 6 | TIME 8 3"
        + " | TIMESTAMPNTZ 23 6 | TIMESTAMPNTZ 23 6 | TIMESTAMPNTZ 23 0 | TIMESTAMPNTZ 23 9 | TIMESTAMPNTZ 23 6"
        + " | TIMESTAMPNTZ 23 9";

    private static final String MILLISECONDS = "TIMESTAMPLTZ 29 3";

    @Override
    protected void setupTest() throws SQLException {
        statement.execute(TABLE);
    }

    /** Each column's label, driver type name, precision and scale, a bar between columns. */
    private static String scales(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            final StringBuilder out = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                out.append(i == 1 ? "" : " | ").append(md.getColumnLabel(i)).append(' ')
                    .append(md.getColumnTypeName(i)).append(' ').append(md.getPrecision(i)).append(' ')
                    .append(md.getScale(i));
            }
            return out.toString();
        }
    }

    /** One named column's driver type name, precision and scale. */
    private static String scaleOf(final Statement on, final String sql, final String label) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (md.getColumnLabel(i).equalsIgnoreCase(label)) {
                    return md.getColumnTypeName(i) + ' ' + md.getPrecision(i) + ' ' + md.getScale(i);
                }
            }
            return "no column " + label;
        }
    }

    /** Every set operation's column U, a bar between statements. */
    private static String setOperationScales(final Statement on) throws SQLException {
        final StringBuilder out = new StringBuilder();
        for (final String sql : SET_OPERATIONS) {
            out.append(out.length() == 0 ? "" : " | ").append(scaleOf(on, sql, "U"));
        }
        return out.toString();
    }

    /** The type DESCRIBE TABLE reports for a table's first column. */
    private static String describedType(final Statement on, final String table) throws SQLException {
        try (ResultSet rs = on.executeQuery("DESCRIBE TABLE " + table)) {
            return rs.next() ? rs.getString("type") : "no column";
        }
    }

    /** A table's column U to the nanosecond, in order, a bar between rows. */
    private static String nanoseconds(final Statement on, final String table) throws SQLException {
        try (ResultSet rs = on.executeQuery("SELECT TO_VARCHAR(u, 'YYYY-MM-DD HH24:MI:SS.FF9') FROM " + table
                + " ORDER BY 1")) {
            final StringBuilder out = new StringBuilder();
            while (rs.next()) {
                out.append(out.length() == 0 ? "" : " | ").append(rs.getString(1));
            }
            return out.toString();
        }
    }

    @Test
    public void aDeclaredColumnReportsItsFractionalDigitsAsTheScale() throws SQLException {
        assertEquals(DECLARED, scales(statement, COLUMNS));
    }

    @Test
    public void anExpressionReportsTheDigitsItsTypeCarries() throws SQLException {
        assertEquals(CARRIED, scales(statement, EXPRESSIONS));
    }

    /** An aggregate keeps its argument's digits, and a conditional takes the wider of its branches'. */
    @Test
    public void anAggregateOrAConditionalKeepsItsArgumentsDigits() throws SQLException {
        assertEquals(AGGREGATED, scales(statement, AGGREGATES));
        assertEquals(WIDER, scales(statement, CONDITIONALS));
    }

    /**
     * A set operation declares the WIDEST fractional-second precision among its arms, whichever arm is written
     * first and whichever flavour wins: UNION [ALL] [BY NAME], EXCEPT, INTERSECT and a recursive CTE alike. A
     * DATE, an untyped NULL and a string arm add no digits of their own.
     */
    @Test
    public void aSetOperationDeclaresItsWidestArmsDigits() throws SQLException {
        assertEquals(WIDEST_ARMS, setOperationScales(statement));
    }

    /**
     * The widest arm's digits hold for the values too: a table created from a set operation declares them and
     * keeps every digit either arm carried, and a VALUES list mixing precisions converts its rows to the widest.
     * The session runs in UTC, where converting an unzoned row to a local timestamp keeps its wall clock.
     */
    @Test
    public void theWidestDigitsSurviveIntoATableAndAValuesList() throws SQLException {
        statement.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        statement.execute("INSERT INTO scale_t (ntz0, ntz6, ntz, ltz3) VALUES ('2024-01-02 03:04:05',"
            + " '2024-01-02 03:04:05.123456', '2024-01-02 03:04:05.123456789', '2024-01-02 03:04:05.678')");
        statement.execute("CREATE TABLE scale_u AS SELECT ntz0 AS u FROM scale_t UNION ALL SELECT ntz6 FROM scale_t");
        assertEquals("TIMESTAMP_NTZ(6)", describedType(statement, "scale_u"));
        assertEquals("2024-01-02 03:04:05.000000000 | 2024-01-02 03:04:05.123456000", nanoseconds(statement, "scale_u"));
        statement.execute("CREATE TABLE scale_v AS SELECT ntz AS u FROM scale_t UNION ALL SELECT ltz3 FROM scale_t");
        assertEquals("TIMESTAMP_LTZ(9)", describedType(statement, "scale_v"));
        assertEquals("2024-01-02 03:04:05.123456789 | 2024-01-02 03:04:05.678000000", nanoseconds(statement, "scale_v"));
        statement.execute("CREATE TABLE scale_w (u TIMESTAMP_LTZ(9))");
        statement.execute("INSERT INTO scale_w VALUES ('2024-01-01 00:00:00.123456789'::TIMESTAMP_LTZ(3)),"
            + " ('2024-01-01 00:00:00.123456789'::TIMESTAMP_NTZ(9))");
        assertEquals("2024-01-01 00:00:00.123000000 | 2024-01-01 00:00:00.123456789", nanoseconds(statement, "scale_w"));
        statement.execute("CREATE TABLE scale_n (u NUMBER)");
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("INSERT INTO scale_n VALUES ('2024-01-01'::TIMESTAMP_NTZ(0)),"
                    + " ('2024-01-01'::TIMESTAMP_NTZ(6))");
            }
        });
        assertTrue(refused.getMessage().contains(
            "Expression type does not match column data type, expecting NUMBER(38,0) but got TIMESTAMP_NTZ(6)"),
            refused.getMessage());
        statement.execute("ALTER SESSION UNSET TIMEZONE");
    }

    /** SHOW's created_on and INFORMATION_SCHEMA's timestamps are TIMESTAMP_LTZ(3). */
    @Test
    public void showAndInformationSchemaTimestampsCarryMilliseconds() throws SQLException {
        assertEquals(MILLISECONDS, scaleOf(statement, "SHOW TABLES LIKE 'SCALE_T'", "created_on"));
        assertEquals(MILLISECONDS, scaleOf(statement, "SHOW SCHEMAS LIKE 'PUBLIC'", "created_on"));
        final String view = "SELECT CREATED, LAST_ALTERED, LAST_DDL FROM INFORMATION_SCHEMA.TABLES"
            + " WHERE TABLE_NAME = 'SCALE_T'";
        assertEquals(MILLISECONDS, scaleOf(statement, view, "CREATED"));
        assertEquals(MILLISECONDS, scaleOf(statement, view, "LAST_ALTERED"));
        assertEquals(MILLISECONDS, scaleOf(statement, view, "LAST_DDL"));
    }

    /** Over HTTP the digits travel as the wire's scale, so that transport answers every cell alike. */
    @Test
    public void theHttpTransportReportsTheSameDigits() throws SQLException, IOException {
        assumeFalse(isLiveSnowflake(), "the engine's own HTTP transport has no live counterpart");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection http = DriverManager.getConnection("jdbc:frostlake://localhost:" + port);
             Statement over = http.createStatement()) {
            over.execute("CREATE OR REPLACE DATABASE scale_http");
            over.execute("USE DATABASE scale_http");
            over.execute("USE SCHEMA PUBLIC");
            over.execute(TABLE);
            assertEquals(DECLARED, scales(over, COLUMNS));
            assertEquals(CARRIED, scales(over, EXPRESSIONS));
            assertEquals(AGGREGATED, scales(over, AGGREGATES));
            assertEquals(WIDER, scales(over, CONDITIONALS));
            assertEquals(WIDEST_ARMS, setOperationScales(over));
            assertEquals(MILLISECONDS, scaleOf(over, "SHOW TABLES LIKE 'SCALE_T'", "created_on"));
            assertEquals(MILLISECONDS, scaleOf(over, "SELECT CREATED FROM INFORMATION_SCHEMA.TABLES"
                + " WHERE TABLE_NAME = 'SCALE_T'", "CREATED"));
            over.execute("DROP DATABASE scale_http");
        } finally {
            server.stop();
        }
    }
}
