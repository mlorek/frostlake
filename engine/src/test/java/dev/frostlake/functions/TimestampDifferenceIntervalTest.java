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

package dev.frostlake.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A TIMESTAMP minus a TIMESTAMP is an {@code INTERVAL DAY(9) TO SECOND(9)}, whatever the two flavours, where
 * Frostlake answered a whole-day count and refused a mix of flavours outright (live-verified). The interval
 * converts to an exact number of seconds and to its text, moves a timestamp, and scales by an exact number;
 * every other conversion and pairing is refused while the statement compiles.
 *
 * <p>Nothing here projects a bare interval: every cell reads one through a conversion instead. That is a
 * property of how these cells are FETCHED, not of the type — the account's JDBC driver refuses an interval
 * column ("Feature unsupported: data type: 50006") with JSON results, which the live comparison forces, and
 * reads one with its default ARROW results.
 */
public class TimestampDifferenceIntervalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("""
            CREATE TABLE fam (ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ, ltz TIMESTAMP_LTZ, tz TIMESTAMP_TZ, d DATE,
                t TIME, f FLOAT, n NUMBER(10,2))""");
        engine.execute("""
            INSERT INTO fam VALUES ('2024-01-15 10:00:00', '2024-01-14 09:00:00', '2024-01-15 10:00:00',
                '2024-01-15 10:00:00 +02:00', '2024-01-10', '10:00:00', 1.5, 2.25)""");
        engine.execute("CREATE TABLE empty_fam LIKE fam");
        engine.execute("CREATE TABLE frac (ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO frac VALUES ('2024-01-15 10:00:00.6', '2024-01-15 10:00:00')");
    }

    @Override
    protected void teardownTest() {
        engine.execute("ALTER SESSION UNSET TIMEZONE");
    }

    /** The one row's cells, as text. */
    private List<String> row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> cells = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            cells.add(String.valueOf(rs.getRows().get(0).getValue(i)));
        }
        return cells;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void aTimestampDifferenceIsADayTimeInterval() {
        assertEquals(List.of("INTERVAL DAY(9) TO SECOND(9)[SB16]", "INTERVAL DAY(9) TO SECOND(9)[SB16]",
                "INTERVAL DAY(9) TO SECOND(9)[SB16]"),
            row("SELECT SYSTEM$TYPEOF(ts - ts2), SYSTEM$TYPEOF(ltz - ts), SYSTEM$TYPEOF(tz - tz) FROM fam"));
        assertEquals(List.of("90000", "NUMBER(38,0)[SB16]", "+1 01:00:00.000000000", "-1 01:00:00.000000000", "-90000"),
            row("""
                SELECT (ts - ts2)::NUMBER, SYSTEM$TYPEOF((ts - ts2)::NUMBER), CAST(ts - ts2 AS VARCHAR),
                    (ts2 - ts)::VARCHAR, (ts2 - ts)::NUMBER
                FROM fam"""));
        assertEquals(List.of("+1 01:00:00.000000000", "90000", "+1 01:00:00.000000000"),
            row("SELECT TO_CHAR(ts - ts2), TO_NUMBER(ts - ts2), TO_VARCHAR(ts - ts2) FROM fam"));
    }

    @Test
    public void flavoursMeetAtTheSessionZone() {
        assertEquals(List.of("+0 00:00:00.000000000", "-0 02:00:00.000000000", "+0 02:00:00.000000000",
                "-0 02:00:00.000000000"),
            row("SELECT (ltz - ts)::VARCHAR, (tz - ts)::VARCHAR, (ts - tz)::VARCHAR, (tz - ltz)::VARCHAR FROM fam"));
    }

    @Test
    public void numbersRoundHalfAwayFromZero() {
        assertEquals(List.of("+0 00:00:00.600000000", "1", "0.600000000", "0.6", "1"),
            row("""
                SELECT (ts - ts2)::VARCHAR, (ts - ts2)::NUMBER, (ts - ts2)::NUMBER(38,9), (ts - ts2)::NUMBER(10,1),
                    (ts - ts2)::INTEGER
                FROM frac"""));
        assertEquals(List.of("-0 00:00:00.600000000", "-1", "-0.600000000"),
            row("SELECT (ts2 - ts)::VARCHAR, (ts2 - ts)::NUMBER, (ts2 - ts)::NUMBER(38,9) FROM frac"));
        assertEquals("Interval out of representable range, type: FIXED[SB2](3,0){not null} value: +1 01:00:00.000000000",
            refusal("SELECT (TIMESTAMP '2024-01-15 10:00:00' - TIMESTAMP '2024-01-14 09:00:00')::NUMBER(3,0)"));
    }

    @Test
    public void intervalArithmetic() {
        assertEquals(List.of("+2 02:00:00.000000000", "+2 02:00:00.000000000", "+2 02:00:00.000000000",
                "+0 12:30:00.000000000", "-1 01:00:00.000000000", "INTERVAL DAY(9) TO SECOND(9)[SB16]"),
            row("""
                SELECT ((ts - ts2) + (ts - ts2))::VARCHAR, ((ts - ts2) * 2)::VARCHAR, (2 * (ts - ts2))::VARCHAR,
                    ((ts - ts2) / 2)::VARCHAR, (-(ts - ts2))::VARCHAR, SYSTEM$TYPEOF((ts - ts2) * n)
                FROM fam"""));
        assertEquals(List.of("TIMESTAMP_NTZ(9)[SB16]", "true", "TIMESTAMP_NTZ(9)[SB16]", "TIMESTAMP_LTZ(9)[SB16]",
                "TIMESTAMP_TZ(9)[SB16]", "TIMESTAMP_NTZ(9)[SB16]"),
            row("""
                SELECT SYSTEM$TYPEOF(ts2 + (ts - ts2)), ts2 + (ts - ts2) = ts, SYSTEM$TYPEOF(ts - (ts - ts2)),
                    SYSTEM$TYPEOF(ltz + (ts - ts2)), SYSTEM$TYPEOF(tz + (ts - ts2)), SYSTEM$TYPEOF(d + (ts - ts2))
                FROM fam"""));
        assertEquals("Interval division by zero", refusal("SELECT ((ts - ts2) / 0)::VARCHAR FROM fam"));
    }

    @Test
    public void intervalsCompareGroupAndAggregate() {
        assertEquals(List.of("true", "+1 01:00:00.000000000", "+1 01:00:00.000000000"),
            row("""
                SELECT (ts - ts2) = (ts - ts2), IFF(TRUE, ts - ts2, ts - ts2)::VARCHAR,
                    COALESCE(ts - ts2, ts - ts2)::VARCHAR
                FROM fam"""));
        assertEquals(List.of("+1 01:00:00.000000000", "90000", "1"),
            row("SELECT MAX(ts - ts2)::VARCHAR, MIN(ts - ts2)::NUMBER, COUNT(DISTINCT ts - ts2) FROM fam"));
        assertEquals(List.of("+1 01:00:00.000000000", "1"),
            row("SELECT (ts - ts2)::VARCHAR, COUNT(*) FROM fam GROUP BY ts - ts2"));
    }

    @Test
    public void pairingsOutsideTheIntervalAlgebraAreRefusedAtTheOperator() {
        final String interval = "INTERVAL DAY(9) TO SECOND(9)";
        assertEquals("SQL compilation error: error line 1 at position 18\nInvalid argument types for function '+': ("
            + interval + ", NUMBER(1,0))", refusal("SELECT (ts - ts2) + 1 FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 18\nInvalid argument types for function '/': ("
            + interval + ", " + interval + ")", refusal("SELECT (ts - ts2) / (ts - ts2) FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 18\nInvalid argument types for function '*': ("
            + interval + ", " + interval + ")", refusal("SELECT (ts - ts2) * (ts - ts2) FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 9\nInvalid argument types for function '/': "
            + "(NUMBER(1,0), " + interval + ")", refusal("SELECT 2 / (ts - ts2) FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 18\nInvalid argument types for function '%': ("
            + interval + ", NUMBER(1,0))", refusal("SELECT (ts - ts2) % 2 FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 18\nInvalid argument types for function '-': ("
            + interval + ", TIMESTAMP_NTZ(9))", refusal("SELECT (ts - ts2) - ts FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 18\nInvalid argument types for function '*': ("
            + interval + ", FLOAT)", refusal("SELECT (ts - ts2) * f FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 9\nInvalid argument types for function '+': "
            + "(TIME(9), " + interval + ")", refusal("SELECT t + (ts - ts2) FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 18\nInvalid argument types for function '*': ("
            + interval + ", NULL)", refusal("SELECT (ts - ts2) * NULL FROM fam"));
    }

    @Test
    public void conversionsOutsideNumbersAndTextAreRefusedWhileCompiling() {
        final String difference = "DATE_DIFFTIMESTAMPTOINTERVAL(EMPTY_FAM.TS2, EMPTY_FAM.TS)";
        assertEquals("SQL compilation error:\ninvalid type [CAST(" + difference + " AS DATE)] for parameter 'TO_DATE'",
            refusal("SELECT CAST(ts - ts2 AS DATE) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(" + difference
            + " AS TIMESTAMP_NTZ(9))] for parameter 'TO_TIMESTAMP_NTZ'",
            refusal("SELECT CAST(ts - ts2 AS TIMESTAMP) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(" + difference + " AS FLOAT)] for parameter 'TO_DOUBLE'",
            refusal("SELECT (ts - ts2)::DOUBLE FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(" + difference
            + " AS TIMESTAMP_TZ(9))] for parameter 'TO_TIMESTAMP_TZ'",
            refusal("SELECT CAST(ts - ts2 AS TIMESTAMP_TZ) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(" + difference + " AS OBJECT)] for parameter 'TO_OBJECT'",
            refusal("SELECT CAST(ts - ts2 AS OBJECT) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(" + difference + " AS ARRAY)] for parameter 'TO_ARRAY'",
            refusal("SELECT CAST(ts - ts2 AS ARRAY) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(NEGATE(" + difference + ") AS DATE)] for parameter 'TO_DATE'",
            refusal("SELECT CAST(-(ts - ts2) AS DATE) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST((" + difference
            + ") INTERVAL DAY TIME MULTIPLY 2 AS DATE)] for parameter 'TO_DATE'",
            refusal("SELECT CAST(2 * (ts - ts2) AS DATE) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST((" + difference
            + ") INTERVAL DAY TIME DIVIDE 2 AS DATE)] for parameter 'TO_DATE'",
            refusal("SELECT CAST((ts - ts2) / 2 AS DATE) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST((" + difference + ") INTERVAL DAY TIME MINUS ("
            + difference + ") AS DATE)] for parameter 'TO_DATE'",
            refusal("SELECT CAST((ts - ts2) - (ts - ts2) AS DATE) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST((" + difference
            + ") INTERVAL DAY TIME MULTIPLY EMPTY_FAM.N AS DATE)] for parameter 'TO_DATE'",
            refusal("SELECT CAST((ts - ts2) * n AS DATE) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(DATE_DIFFTIMESTAMPTOINTERVAL(TO_TIMESTAMP_LTZ(EMPTY_FAM.TS), "
            + "EMPTY_FAM.LTZ) AS DATE)] for parameter 'TO_DATE'",
            refusal("SELECT CAST(ltz - ts AS DATE) FROM empty_fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(DATE_DIFFTIMESTAMPTOINTERVAL("
            + "TO_TIMESTAMP_NTZ('2024-01-14 09:00:00'), TO_TIMESTAMP_NTZ('2024-01-15 10:00:00')) AS DATE)] for parameter 'TO_DATE'",
            refusal("SELECT CAST(TIMESTAMP '2024-01-15 10:00:00' - TIMESTAMP '2024-01-14 09:00:00' AS DATE)"));
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types "
            + "INTERVAL DAY(9) TO SECOND(9) and VARCHAR(134217728)",
            refusal("SELECT TRY_CAST(ts - ts2 AS VARCHAR) FROM empty_fam"));
    }
}
