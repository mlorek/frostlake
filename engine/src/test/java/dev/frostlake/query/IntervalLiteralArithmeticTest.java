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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Arithmetic on unit-suffixed interval literals, of either family: negation, scaling by an exact number, the
 * sum and difference of two intervals, and a DATE or a timestamp moved by one in either order — each typed as
 * the account types it, and every other pairing refused at the operator while the statement compiles. A
 * product or a quotient keeps no part finer than its type's trailing field, and a result its leading precision
 * cannot hold is refused naming the operation. Every cell is the account's answer.
 */
public class IntervalLiteralArithmeticTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ila (i INT, n NUMBER(10,2), ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO ila VALUES (3, 1.50, '2024-01-02 01:00:00', '2024-01-01 00:00:00')");
    }

    /** Every row of a query, cells joined by {@code |}, rows by {@code ;}. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(';');
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append('|');
                }
                out.append(String.valueOf(row.getValue(i)).toUpperCase());
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
        return "answered";
    }

    /** SYSTEM$TYPEOF and TO_VARCHAR of one expression, joined by {@code |}. */
    private String typedText(final String expression) {
        return rows("SELECT SYSTEM$TYPEOF(" + expression + "), TO_VARCHAR(" + expression + ")");
    }

    /** A minus keeps the interval's type; a plus is refused while compiling. */
    @Test
    public void aMinusKeepsTheTypeAndAPlusIsRefused() {
        assertEquals("INTERVAL DAY(9)[SB16]|-1", typedText("-INTERVAL '1' DAY"));
        assertEquals("INTERVAL YEAR(9) TO MONTH[SB8]|-1-02", typedText("-INTERVAL '1-2' YEAR TO MONTH"));
        assertEquals("INTERVAL MONTH(9)[SB4]|-14", typedText("-INTERVAL '14' MONTH"));
        assertEquals("INTERVAL DAY(2)[SB8]|-1", typedText("-INTERVAL '1' DAY(2)"));
        assertEquals("+1|+0", rows("SELECT TO_VARCHAR(-(-INTERVAL '1' DAY)), TO_VARCHAR(-INTERVAL '0' DAY)"));
        assertEquals("SQL compilation error: error line 1 at position 21\nInvalid argument types for function"
            + " 'UNARY PLUS': (INTERVAL DAY(9))", refusal("SELECT SYSTEM$TYPEOF(+INTERVAL '1' DAY)"));
    }

    /** Scaling keeps the fields and widens the leading precision by the number's digits. */
    @Test
    public void scalingKeepsTheFieldsAndWidensTheLeadingPrecision() {
        assertEquals("INTERVAL DAY(9)[SB16]|+2", typedText("INTERVAL '1' DAY * 2"));
        assertEquals("INTERVAL DAY(9)[SB16]|+2", typedText("2 * INTERVAL '1' DAY"));
        assertEquals("INTERVAL DAY(3)[SB8]|+2", typedText("INTERVAL '1' DAY(2) * 2"));
        assertEquals("INTERVAL DAY(5)[SB8]|+200", typedText("INTERVAL '1' DAY(2) * 200"));
        assertEquals("INTERVAL DAY(4)[SB8]|INTERVAL DAY(5)[SB8]|INTERVAL DAY(4)[SB8]|INTERVAL DAY(3)[SB8]",
            rows("SELECT SYSTEM$TYPEOF(INTERVAL '1' DAY(2) * 1.5), SYSTEM$TYPEOF(INTERVAL '1' DAY(2) * 1.25),"
                + " SYSTEM$TYPEOF(INTERVAL '1' DAY(2) * 0.5), SYSTEM$TYPEOF(INTERVAL '1' DAY(2) / 2)"));
        assertEquals("INTERVAL SECOND(3,3)[SB8]|+3.750", typedText("INTERVAL '1.25' SECOND(2,3) * 3"));
        assertEquals("INTERVAL DAY(9) TO HOUR[SB16]|+2 04", typedText("INTERVAL '1 02' DAY TO HOUR * 2"));
        assertEquals("INTERVAL YEAR(9) TO MONTH[SB8]|+2-04", typedText("2 * INTERVAL '1-2' YEAR TO MONTH"));
        assertEquals("INTERVAL YEAR(4)[SB4]|INTERVAL DAY(9)[SB16]|INTERVAL HOUR(9)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(INTERVAL '1' YEAR(2) * 12), SYSTEM$TYPEOF(INTERVAL '1' DAY(2) * n),"
                + " SYSTEM$TYPEOF(i * INTERVAL '1' HOUR(3)) FROM ila"));
        assertEquals("INTERVAL DAY(9)[SB16]|NULL", typedText("INTERVAL '1' DAY * NULL::INT"));
    }

    /**
     * A product is rounded half away from zero to the nanosecond or the month, and a quotient truncated toward
     * zero; either keeps no part finer than its type's trailing field.
     */
    @Test
    public void aProductOrQuotientKeepsNothingFinerThanTheTrailingField() {
        assertEquals("+1|+0|+0.333333333|+0.666666666|-0.666666666", rows("SELECT TO_VARCHAR(INTERVAL '1' DAY * 1.5),"
            + " TO_VARCHAR(INTERVAL '1' DAY / 2), TO_VARCHAR(INTERVAL '1' SECOND / 3), TO_VARCHAR(INTERVAL '2' SECOND / 3),"
            + " TO_VARCHAR(INTERVAL '-2' SECOND / 3)"));
        assertEquals("+0.000000002|+0.000000003|+0.000", rows("SELECT TO_VARCHAR(INTERVAL '1' SECOND * 0.0000000015),"
            + " TO_VARCHAR(INTERVAL '1' SECOND * 0.0000000025), TO_VARCHAR(INTERVAL '1' SECOND(2,3) * 0.0005)"));
        assertEquals("+2|+1|+2|+3|+1|-2|-1|+1", rows("SELECT TO_VARCHAR(INTERVAL '1' MONTH * 1.5),"
            + " TO_VARCHAR(INTERVAL '3' MONTH / 2), TO_VARCHAR(INTERVAL '5' MONTH / 2), TO_VARCHAR(INTERVAL '1' MONTH * 2.5),"
            + " TO_VARCHAR(INTERVAL '1' MONTH * 1.4), TO_VARCHAR(INTERVAL '-1' MONTH * 1.5),"
            + " TO_VARCHAR(INTERVAL '-3' MONTH / 2), TO_VARCHAR(INTERVAL '7' MONTH / 4)"));
        assertEquals("+1 13|+1-08|+1|-2|+0 12|+0 12:30:00.250000000", rows("SELECT TO_VARCHAR(INTERVAL '1 01' DAY TO HOUR * 1.5),"
            + " TO_VARCHAR(INTERVAL '1-01' YEAR TO MONTH * 1.5), TO_VARCHAR(INTERVAL '1' YEAR * 1.25),"
            + " TO_VARCHAR(INTERVAL '5' DAY / -2), TO_VARCHAR(INTERVAL '1 01' DAY TO HOUR / 2),"
            + " TO_VARCHAR(INTERVAL '1 01:00:00.5' DAY TO SECOND / 2)"));
        assertEquals("FALSE|TRUE|FALSE|TRUE|TRUE|TRUE", rows("SELECT INTERVAL '1' DAY * 1.5 = INTERVAL '36' HOUR,"
            + " INTERVAL '1' DAY / 2 = INTERVAL '0' DAY, INTERVAL '1' YEAR * 1.5 = INTERVAL '18' MONTH,"
            + " INTERVAL '1' YEAR / 2 = INTERVAL '0' MONTH, INTERVAL '3' HOUR / 2 = INTERVAL '1' HOUR,"
            + " INTERVAL '2' SECOND(2,3) / 3 = INTERVAL '0.666' SECOND"));
        assertEquals("+3|+1|+2|+2", rows("SELECT TO_VARCHAR(INTERVAL '1' DAY * i), TO_VARCHAR(INTERVAL '1' HOUR * n),"
            + " TO_VARCHAR(INTERVAL '7' DAY / i), TO_VARCHAR(INTERVAL '7' MONTH / i) FROM ila"));
        assertEquals("Interval division by zero", refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY / 0)"));
        assertEquals("Interval division by zero", refusal("SELECT TO_VARCHAR(INTERVAL '1' YEAR / 0)"));
    }

    /** Two intervals of one family meet in the span of both, one leading digit wider than the wider one. */
    @Test
    public void twoIntervalsMeetInTheSpanOfBoth() {
        assertEquals("INTERVAL DAY(9) TO HOUR[SB16]|+1 01", typedText("INTERVAL '1' DAY + INTERVAL '1' HOUR"));
        assertEquals("INTERVAL DAY(9) TO HOUR[SB16]|+1 01", typedText("INTERVAL '1' HOUR + INTERVAL '1' DAY"));
        assertEquals("INTERVAL DAY(9) TO HOUR[SB16]|-0 23", typedText("INTERVAL '1' HOUR - INTERVAL '1' DAY"));
        assertEquals("INTERVAL MINUTE(9) TO SECOND(9)[SB16]|+1:01.000000000",
            typedText("INTERVAL '1' SECOND + INTERVAL '1' MINUTE"));
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]|+1 00:00:01.500000000",
            typedText("INTERVAL '1' DAY + INTERVAL '1.5' SECOND"));
        assertEquals("INTERVAL DAY(9) TO MINUTE[SB16]|+1 02:03", typedText("INTERVAL '1 02' DAY TO HOUR + INTERVAL '3' MINUTE"));
        assertEquals("INTERVAL DAY(9)[SB16]|+2", typedText("INTERVAL '1' DAY + INTERVAL '1' DAY"));
        assertEquals("INTERVAL DAY(4)[SB8]|+2", typedText("INTERVAL '1' DAY(2) + INTERVAL '1' DAY(3)"));
        assertEquals("INTERVAL DAY(4)[SB8]|+0", typedText("INTERVAL '1' DAY(2) - INTERVAL '1' DAY(3)"));
        assertEquals("INTERVAL DAY(3) TO HOUR[SB8]|+1 01", typedText("INTERVAL '1' DAY(2) + INTERVAL '1' HOUR(2)"));
        assertEquals("INTERVAL DAY(5) TO HOUR[SB8]|INTERVAL HOUR(7) TO SECOND(9)[SB16]|INTERVAL MINUTE(9) TO SECOND(3)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(INTERVAL '1' DAY(2) + INTERVAL '1' HOUR(4)),"
                + " SYSTEM$TYPEOF(INTERVAL '1' HOUR(2) + INTERVAL '1' SECOND(6)),"
                + " SYSTEM$TYPEOF(INTERVAL '1' SECOND(2,3) + INTERVAL '1' MINUTE)"));
        assertEquals("INTERVAL SECOND(2,0)[SB8]|+11", typedText("INTERVAL '5' SECOND(1,0) + INTERVAL '6' SECOND(1,0)"));
        assertEquals("INTERVAL YEAR(9) TO MONTH[SB8]|+1-01", typedText("INTERVAL '1' YEAR + INTERVAL '1' MONTH"));
        assertEquals("INTERVAL YEAR(9) TO MONTH[SB8]|+0-11", typedText("INTERVAL '1' YEAR - INTERVAL '1' MONTH"));
        assertEquals("INTERVAL MONTH(9)[SB4]|+2", typedText("INTERVAL '1' MONTH + INTERVAL '1' MONTH"));
        assertEquals("INTERVAL YEAR(4)[SB4]|INTERVAL YEAR(3) TO MONTH[SB2]|INTERVAL YEAR(6) TO MONTH[SB4]",
            rows("SELECT SYSTEM$TYPEOF(INTERVAL '1' YEAR(2) + INTERVAL '1' YEAR(3)),"
                + " SYSTEM$TYPEOF(INTERVAL '1' YEAR(2) + INTERVAL '1' MONTH(2)),"
                + " SYSTEM$TYPEOF(INTERVAL '1' YEAR(2) + INTERVAL '1' MONTH(5))"));
        assertEquals("TRUE|TRUE|TRUE", rows("SELECT INTERVAL '1' DAY + INTERVAL '1' HOUR = INTERVAL '25' HOUR,"
            + " INTERVAL '1' YEAR + INTERVAL '1' MONTH = INTERVAL '13' MONTH, -INTERVAL '1' DAY = INTERVAL '-24' HOUR"));
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]|+1 02:00:00.000000000",
            rows("SELECT SYSTEM$TYPEOF(INTERVAL '1' HOUR + (ts - ts2)), TO_VARCHAR((ts - ts2) + INTERVAL '1' HOUR) FROM ila"));
    }

    /** A DATE or a timestamp moves by an interval in either order; a DATE stays a DATE for a year-month one. */
    @Test
    public void aTemporalMovesByAnIntervalInEitherOrder() {
        assertEquals("TIMESTAMP_NTZ(9)[SB16]|2024-01-02 00:00:00.000", typedText("INTERVAL '1' DAY + DATE '2024-01-01'"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]|2024-01-02 00:00:00.000", typedText("DATE '2024-01-01' + INTERVAL '1' DAY"));
        assertEquals("DATE[SB4]|2024-02-29", typedText("INTERVAL '1' MONTH + DATE '2024-01-31'"));
        assertEquals("DATE[SB4]|2025-02-28", typedText("INTERVAL '1' YEAR + DATE '2024-02-29'"));
        assertEquals("DATE[SB4]|2025-03-01", typedText("DATE '2024-01-01' + INTERVAL '1-2' YEAR TO MONTH"));
        assertEquals("DATE[SB4]|2022-12-31", typedText("DATE '2024-01-31' - INTERVAL '1-1' YEAR TO MONTH"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]|2024-01-02 02:00:00.000", typedText("INTERVAL '1 02' DAY TO HOUR + DATE '2024-01-01'"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]|2024-02-29 10:00:00.000",
            typedText("INTERVAL '1' MONTH + '2024-01-31 10:00:00'::TIMESTAMP_NTZ(3)"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]|2024-01-01 00:00:01.500", typedText("DATE '2024-01-01' + INTERVAL '1.5' SECOND"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]|2024-01-03 00:00:00.000", typedText("DATE '2024-01-01' + INTERVAL '1' DAY * 2"));
        assertEquals("DATE[SB4]|2024-03-31", typedText("DATE '2024-01-31' + INTERVAL '1' MONTH * 2"));
        assertEquals("DATE[SB4]|2023-12-31", typedText("DATE '2024-01-31' + -INTERVAL '1' MONTH"));
        assertEquals("DATE[SB4]|2025-04-30", typedText("DATE '2024-03-31' + (INTERVAL '1' YEAR + INTERVAL '1' MONTH)"));
        assertEquals("2024-01-01 00:00:00.000|2023-12-31 21:00:00.000|2023-10-31", rows("SELECT"
            + " TO_VARCHAR(TIMESTAMP '2024-01-01 00:00:00' + INTERVAL '1' DAY / 2),"
            + " TO_VARCHAR(TIMESTAMP '2024-01-01 00:00:00' - INTERVAL '1' HOUR * 3),"
            + " TO_VARCHAR(DATE '2024-01-31' - INTERVAL '1' MONTH * 3)"));
        assertEquals("2024-01-02 01:00:00.000", rows("SELECT TO_VARCHAR(DATE '2024-01-01' + INTERVAL '1' DAY"
            + " + INTERVAL '1 hour')"));
    }

    /**
     * A day-time interval moves a TIMESTAMP_LTZ by its exact duration across a daylight-saving change, where
     * the quoted-string DAY moves it by a calendar day; a year-month one moves the wall clock.
     */
    @Test
    public void aDayTimeIntervalMovesAnLtzByItsDuration() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        try {
            assertEquals("2024-03-10 13:00:00.000 -0700|2024-03-10 12:00:00.000 -0700|2024-03-09 11:00:00.000 -0800",
                rows("SELECT TO_VARCHAR('2024-03-09 12:00:00'::TIMESTAMP_LTZ + INTERVAL '1' DAY),"
                    + " TO_VARCHAR('2024-03-09 12:00:00'::TIMESTAMP_LTZ + INTERVAL '1 day'),"
                    + " TO_VARCHAR('2024-03-10 12:00:00'::TIMESTAMP_LTZ - INTERVAL '1' DAY * 1)"));
            assertEquals("2024-03-10 13:00:00.000 -0700|2024-03-10 03:30:00.000 -0700|2024-03-10 12:00:00.000 -0800",
                rows("SELECT TO_VARCHAR(INTERVAL '1' DAY + '2024-03-09 12:00:00'::TIMESTAMP_LTZ),"
                    + " TO_VARCHAR('2024-02-10 02:30:00'::TIMESTAMP_LTZ + INTERVAL '1' MONTH * 1),"
                    + " TO_VARCHAR('2024-03-09 12:00:00'::TIMESTAMP_TZ + INTERVAL '1' DAY)"));
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    /** Every other pairing with an interval is refused at the operator while the statement compiles. */
    @Test
    public void everyOtherPairingIsRefusedAtTheOperator() {
        assertEquals("SQL compilation error: error line 1 at position 35\nInvalid argument types for function '%':"
            + " (INTERVAL DAY(9), NUMBER(1,0))", refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY % 2)"));
        assertEquals("SQL compilation error: error line 1 at position 35\nInvalid argument types for function '*':"
            + " (INTERVAL DAY(9), INTERVAL DAY(9))", refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY * INTERVAL '1' DAY)"));
        assertEquals("SQL compilation error: error line 1 at position 20\nInvalid argument types for function '/':"
            + " (NUMBER(1,0), INTERVAL DAY(9))", refusal("SELECT TO_VARCHAR(2 / INTERVAL '1' DAY)"));
        assertEquals("SQL compilation error: error line 1 at position 36\nInvalid argument types for function '*':"
            + " (INTERVAL YEAR(9), FLOAT)", refusal("SELECT TO_VARCHAR(INTERVAL '1' YEAR * 2.5::FLOAT)"));
        assertEquals("SQL compilation error: error line 1 at position 35\nInvalid argument types for function '*':"
            + " (INTERVAL DAY(9), VARIANT)", refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY * TO_VARIANT(2))"));
        assertEquals("SQL compilation error: error line 1 at position 35\nInvalid argument types for function '*':"
            + " (INTERVAL DAY(9), BOOLEAN)", refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY * TRUE)"));
        assertEquals("SQL compilation error: error line 1 at position 36\nInvalid argument types for function '+':"
            + " (INTERVAL YEAR(9), NUMBER(1,0))", refusal("SELECT TO_VARCHAR(INTERVAL '1' YEAR + 1)"));
        assertEquals("SQL compilation error: error line 1 at position 36\nInvalid argument types for function '+':"
            + " (INTERVAL YEAR(9), VARCHAR(1))", refusal("SELECT TO_VARCHAR(INTERVAL '1' YEAR + '1')"));
        assertEquals("SQL compilation error: error line 1 at position 36\nInvalid argument types for function '+':"
            + " (INTERVAL YEAR(9), NULL)", refusal("SELECT TO_VARCHAR(INTERVAL '1' YEAR + NULL)"));
        assertEquals("SQL compilation error: error line 1 at position 23\nInvalid argument types for function '+':"
            + " (NULL, INTERVAL YEAR(9))", refusal("SELECT TO_VARCHAR(NULL + INTERVAL '1' YEAR)"));
        assertEquals("SQL compilation error: error line 1 at position 35\nInvalid argument types for function '+':"
            + " (INTERVAL DAY(9), INTERVAL MONTH(9))", refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY + INTERVAL '1' MONTH)"));
        assertEquals("SQL compilation error: error line 1 at position 36\nInvalid argument types for function '-':"
            + " (INTERVAL YEAR(9), DATE)", refusal("SELECT TO_VARCHAR(INTERVAL '1' YEAR - DATE '2024-01-01')"));
        assertEquals("SQL compilation error: error line 1 at position 39\nInvalid argument types for function '+':"
            + " (INTERVAL HOUR(9), TIME(9))", refusal("SELECT SYSTEM$TYPEOF(INTERVAL '1' HOUR + TIME '10:00:00')"));
        assertEquals("SQL compilation error: error line 1 at position 31\nInvalid argument types for function '+':"
            + " (VARCHAR(10), INTERVAL DAY(9))", refusal("SELECT TO_VARCHAR('2024-01-01' + INTERVAL '1' DAY)"));
        assertEquals("SQL compilation error: error line 1 at position 35\nInvalid argument types for function '+':"
            + " (INTERVAL DAY(9), INTERVAL)", refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY + INTERVAL '1 hour')"));
    }

    /** A result the leading precision cannot hold is refused, naming the operation and the storage type. */
    @Test
    public void aResultPastTheLeadingPrecisionIsRefused() {
        assertEquals("Interval out of representable range after multiply, type: INTERVAL_DAY_TIME[SB16](9,6){not null}",
            refusal("SELECT TO_VARCHAR(INTERVAL '999999999' DAY * 10)"));
        assertEquals("Interval out of representable range after multiply, type: INTERVAL_DAY_TIME[SB8](153,12){not null}",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' SECOND * 10000000000)"));
        assertEquals("Interval out of representable range after multiply, type: INTERVAL_YEAR_MONTH[SB8](9,0){not null}",
            refusal("SELECT TO_VARCHAR(INTERVAL '999999999-11' YEAR TO MONTH * 10)"));
        assertEquals("Interval out of representable range after plus, type: INTERVAL_YEAR_MONTH[SB8](9,1){not null}",
            refusal("SELECT TO_VARCHAR(INTERVAL '999999999' YEAR + INTERVAL '1' YEAR)"));
        assertEquals("Interval out of representable range after minus, type: INTERVAL_DAY_TIME[SB16](9,6){not null}",
            refusal("SELECT TO_VARCHAR(INTERVAL '-999999999' DAY - INTERVAL '1' DAY)"));
        assertEquals("Interval out of representable range after plus, type: INTERVAL_DAY_TIME[SB16](9,5){not null}",
            refusal("SELECT TO_VARCHAR(INTERVAL '999999999' DAY + INTERVAL '24' HOUR)"));
        assertEquals("Interval out of representable range after divide, type: INTERVAL_DAY_TIME[SB16](9,6){not null}",
            refusal("SELECT TO_VARCHAR(INTERVAL '999999999' DAY / 0.5)"));
        assertEquals("Interval out of representable range after multiply, type: INTERVAL_DAY_TIME[SB16](153,3){nullable}",
            refusal("SELECT TO_VARCHAR((ts - ts2) * 100000000000) FROM ila"));
        assertEquals("+999999999 23|+198", rows("SELECT TO_VARCHAR(INTERVAL '999999999' DAY + INTERVAL '23' HOUR),"
            + " TO_VARCHAR(INTERVAL '99' DAY(2) + INTERVAL '99' DAY(2))"));
    }
}
