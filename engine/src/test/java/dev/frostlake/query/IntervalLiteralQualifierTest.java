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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A unit-suffixed interval literal takes a full qualifier: a range of fields ({@code DAY TO HOUR} through
 * {@code YEAR TO MONTH}), a fractional SECOND, a plural field, and a leading precision ({@code DAY(2)}, a
 * SECOND's fractional one too). The qualifier is judged while the statement compiles, its text when a row
 * reaches the literal. Every cell is the account's answer.
 */
public class IntervalLiteralQualifierTest extends BaseDatabaseTest {

    private static final String DAY_TIME_FORMATS = "is invalid, expected format is '<sign>D(p) HH24:MM:SS.F(fsp)'"
        + " for subtype DAY TO SECOND";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ilq_empty (a INT)");
        engine.execute("CREATE OR REPLACE TABLE ilq_one (a INT)");
        engine.execute("INSERT INTO ilq_one VALUES (1)");
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
                out.append(String.valueOf(row.getValue(i)));
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

    /** SYSTEM$TYPEOF and TO_VARCHAR of one literal, joined by {@code |}. */
    private String typedText(final String literal) {
        return rows("SELECT SYSTEM$TYPEOF(" + literal + "), TO_VARCHAR(" + literal + ")");
    }

    /** A range of fields reads its text by them, and prints them back the same way. */
    @Test
    public void aRangeOfFieldsReadsAndPrintsItsText() {
        assertEquals("INTERVAL DAY(9) TO HOUR[SB16]|+1 02", typedText("INTERVAL '1 02' DAY TO HOUR"));
        assertEquals("INTERVAL DAY(9) TO MINUTE[SB16]|+1 02:03", typedText("INTERVAL '1 02:03' DAY TO MINUTE"));
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]|+1 02:03:04.500000000",
            typedText("INTERVAL '1 02:03:04.5' DAY TO SECOND"));
        assertEquals("INTERVAL HOUR(9) TO MINUTE[SB16]|+2:03", typedText("INTERVAL '02:03' HOUR TO MINUTE"));
        assertEquals("INTERVAL HOUR(9) TO SECOND(9)[SB16]|+2:03:04.000000000",
            typedText("INTERVAL '02:03:04' HOUR TO SECOND"));
        assertEquals("INTERVAL MINUTE(9) TO SECOND(9)[SB16]|+3:04.000000000",
            typedText("INTERVAL '03:04' MINUTE TO SECOND"));
        assertEquals("INTERVAL YEAR(9) TO MONTH[SB8]|-1-02", typedText("INTERVAL '-1-2' YEAR TO MONTH"));
        assertEquals("+1 02|+1 02:03:04.000000000|+25:00|+100:30:15.250000000|-0 02|-3:04.500000000|+0-00",
            rows("SELECT TO_VARCHAR(INTERVAL '1 2' DAY TO HOUR), TO_VARCHAR(INTERVAL '1 2:3:4' DAY TO SECOND),"
                + " TO_VARCHAR(INTERVAL '25:00' HOUR TO MINUTE), TO_VARCHAR(INTERVAL '100:30:15.25' HOUR TO SECOND),"
                + " TO_VARCHAR(INTERVAL '-0 02' DAY TO HOUR), TO_VARCHAR(INTERVAL '-03:04.5' MINUTE TO SECOND),"
                + " TO_VARCHAR(interval '0-0' year to month)"));
    }

    /** A SECOND takes a fraction, and every field takes its plural. */
    @Test
    public void aSecondTakesAFractionAndEveryFieldItsPlural() {
        assertEquals("INTERVAL SECOND(9,9)[SB8]|+1.500000000", typedText("INTERVAL '1.5' SECOND"));
        assertEquals("+1.123456789|-0.500000000", rows("SELECT TO_VARCHAR(INTERVAL '1.123456789' SECOND),"
            + " TO_VARCHAR(INTERVAL '-0.5' SECOND)"));
        assertEquals("INTERVAL DAY(9)[SB16]|+1", typedText("INTERVAL '1' DAYS"));
        assertEquals("INTERVAL HOUR(9)[SB16]|+2", typedText("INTERVAL '2' HOURS"));
        assertEquals("INTERVAL MINUTE(9)[SB16]|+2", typedText("INTERVAL '2' MINUTES"));
        assertEquals("INTERVAL SECOND(9,9)[SB8]|+2.000000000", typedText("INTERVAL '2' SECONDS"));
        assertEquals("INTERVAL YEAR(9)[SB8]|+2", typedText("INTERVAL '2' YEARS"));
        assertEquals("INTERVAL MONTH(9)[SB4]|+2", typedText("INTERVAL '2' Months"));
    }

    /** The plural is a unit wherever it stands, and an alias may follow it. */
    @Test
    public void thePluralIsAUnitWhereverItStands() {
        assertEquals("2024-01-11 00:00:00.000|2024-03-01",
            rows("SELECT TO_VARCHAR(DATE '2024-01-01' + INTERVAL '10' DAYS), TO_VARCHAR(DATE '2024-01-01'"
                + " + INTERVAL '2' MONTHS)"));
        assertEquals("+10", rows("SELECT TO_VARCHAR(x) FROM (SELECT INTERVAL '10' DAYS AS x)"));
        assertEquals("2024-01-11 00:00:00.000", rows("SELECT TO_VARCHAR(x) FROM (SELECT DATE '2024-01-01'"
            + " + INTERVAL '10' DAYS AS x)"));
    }

    /** WEEK and QUARTER are no fields: after the string they are an alias, the literal a count of seconds. */
    @Test
    public void weekAndQuarterStayAliases() {
        assertEquals("2026-01-01 00:00:01.000|2026-01-01 00:00:01.000", rows("SELECT TO_VARCHAR(week),"
            + " TO_VARCHAR(quarter) FROM (SELECT DATE '2026-01-01' + INTERVAL '1' WEEK, DATE '2026-01-01'"
            + " + INTERVAL '1' QUARTER)"));
    }

    /** A leading precision types the literal, and the storage it needs follows the digits it holds. */
    @Test
    public void aLeadingPrecisionTypesTheLiteral() {
        assertEquals("INTERVAL DAY(2)[SB8]|+99", typedText("INTERVAL '99' DAY(2)"));
        assertEquals("INTERVAL DAY(2)[SB8]|+1", typedText("INTERVAL '1' DAY ( 2 )"));
        assertEquals("INTERVAL DAY(2)[SB8]|+1", typedText("INTERVAL '1' DAYS(2)"));
        assertEquals("INTERVAL DAY(5)[SB8]|INTERVAL DAY(6)[SB16]|INTERVAL HOUR(6)[SB8]|INTERVAL HOUR(7)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(INTERVAL '1' DAY(5)), SYSTEM$TYPEOF(INTERVAL '1' DAY(6)),"
                + " SYSTEM$TYPEOF(INTERVAL '1' HOUR(6)), SYSTEM$TYPEOF(INTERVAL '1' HOUR(7))"));
        assertEquals("INTERVAL YEAR(1)[SB2]|INTERVAL YEAR(4)[SB4]|INTERVAL MONTH(4)[SB2]|INTERVAL MONTH(5)[SB4]",
            rows("SELECT SYSTEM$TYPEOF(INTERVAL '1' YEAR(1)), SYSTEM$TYPEOF(INTERVAL '1' YEAR(4)),"
                + " SYSTEM$TYPEOF(INTERVAL '1' MONTH(4)), SYSTEM$TYPEOF(INTERVAL '1' MONTH(5))"));
        assertEquals("INTERVAL SECOND(2,3)[SB8]|+1.250", typedText("INTERVAL '1.25' SECOND(2,3)"));
        assertEquals("INTERVAL SECOND(2,0)[SB8]|+1", typedText("INTERVAL '1' SECOND(2,0)"));
        assertEquals("INTERVAL SECOND(2,9)[SB8]|+1.000000000", typedText("INTERVAL '1' SECOND(2)"));
        assertEquals("INTERVAL DAY(3) TO SECOND(3)[SB8]|+1 02:03:04.500",
            typedText("INTERVAL '1 02:03:04.5' DAY(3) TO SECOND(3)"));
        assertEquals("INTERVAL DAY(9) TO SECOND(0)[SB16]|+1 02:03:04", typedText("INTERVAL '1 02:03:04' DAY TO SECOND(0)"));
        assertEquals("INTERVAL MINUTE(2) TO SECOND(1)[SB8]|+3:04.5", typedText("INTERVAL '03:04.5' MINUTE(2) TO SECOND(1)"));
        assertEquals("INTERVAL YEAR(2) TO MONTH[SB2]|+1-02", typedText("INTERVAL '1-2' YEAR(2) TO MONTH"));
    }

    /** A qualifier that names no type is refused while the statement compiles, over no row at all. */
    @Test
    public void aQualifierNamingNoTypeIsRefusedWhileCompiling() {
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY(0)) FROM ilq_empty"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY(10)) FROM ilq_empty"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY(2,3)) FROM ilq_empty"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY",
            refusal("SELECT TO_VARCHAR(INTERVAL '1 02' DAY(10) TO HOUR) FROM ilq_empty"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL SECOND",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' SECOND(2,10)) FROM ilq_empty"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL SECOND",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' SECOND(0)) FROM ilq_empty"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL HOUR TO DAY",
            refusal("SELECT TO_VARCHAR(INTERVAL 'x' HOUR TO DAY) FROM ilq_empty"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY TO DAY",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY TO DAY) FROM ilq_empty"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL YEAR TO SECOND",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' YEAR TO SECOND) FROM ilq_empty"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY TO HOUR",
            refusal("SELECT TO_VARCHAR(INTERVAL '1 02' DAY TO HOUR(2)) FROM ilq_empty"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY TO SECOND",
            refusal("SELECT TO_VARCHAR(INTERVAL '1 02:03:04' DAY TO SECOND(10)) FROM ilq_empty"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 39 unexpected 'TO'.",
            refusal("SELECT TO_VARCHAR(INTERVAL '1 02' DAYS TO HOURS) AS v"));
    }

    /**
     * A qualifier that names no type, and a quoted text that does not read, refuse the statement before any name it
     * holds, wherever each stands; the first in the text wins, a cast's declared width among them. A unit word the
     * account does not know waits for the names.
     */
    @Test
    public void aLiteralIsJudgedAheadOfEveryName() {
        final String day = "Invalid specification for type INTERVAL: INTERVAL DAY";
        assertEquals(day, refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY(0)), nosuch FROM ilq_one"));
        assertEquals(day, refusal("SELECT nosuch, TO_VARCHAR(INTERVAL '1' DAY(0)) FROM ilq_one"));
        assertEquals(day, refusal("SELECT nosuch FROM ilq_one WHERE TO_VARCHAR(INTERVAL '1' DAY(0)) IS NULL"));
        assertEquals(day, refusal("SELECT a FROM ilq_one WHERE nosuch = 1 AND TO_VARCHAR(INTERVAL '1' DAY(0)) IS NULL"));
        assertEquals(day, refusal("SELECT a FROM ilq_nosuch WHERE TO_VARCHAR(INTERVAL '1' DAY(0)) IS NULL"));
        assertEquals(day, refusal("SELECT ilq_nosuchfn(1), TO_VARCHAR(INTERVAL '1' DAY(0)) FROM ilq_one"));
        assertEquals(day, refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY(0)) FROM ilq_one ORDER BY 5"));
        assertEquals(day, refusal("SELECT a FROM ilq_one WHERE a = (SELECT TO_VARCHAR(INTERVAL '1' DAY(0)))"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY TO HOUR",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY TO HOUR(2)), nosuch FROM ilq_one"));
        assertEquals(day, refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY(0)), TO_VARCHAR(INTERVAL '1' HOUR TO DAY)"));
        assertEquals(day, refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY(0)) AS v, CAST(1 AS VARCHAR(0)) AS a"));
        assertEquals("SQL compilation error: error line 1 at position 25\nInvalid character length: 0. Must be between"
            + " 1 and 134,217,728.", refusal("SELECT CAST(1 AS VARCHAR(0)) AS a, TO_VARCHAR(INTERVAL '1' DAY(0)) AS v"));
        assertEquals("SQL compilation error: error line 1 at position 18\nInvalid character length: 0. Must be between"
            + " 1 and 134,217,728.", refusal("SELECT 1::VARCHAR(0) AS a, TO_VARCHAR(INTERVAL '1' DAY(0)) AS v"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 6 unexpected '2'.",
            refusal("SELECT nosuch, TO_VARCHAR('2024-03-09'::DATE + INTERVAL '1 day 2 hours') FROM ilq_one"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 6 unexpected '2'.",
            refusal("SELECT TO_VARCHAR('2024-03-09'::DATE + INTERVAL '1 day 2 hours') AS w,"
                + " TO_VARCHAR(INTERVAL '1' DAY(0)) AS v"));
        assertEquals(day, refusal("SELECT '2024-01-01'::DATE + INTERVAL '1 dayx', TO_VARCHAR(INTERVAL '1' DAY(0))"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'NOSUCH'",
            refusal("SELECT nosuch, TO_VARCHAR('2024-03-09'::DATE + INTERVAL '1 dayx') FROM ilq_one"));
        assertEquals(day, refusal("CREATE OR REPLACE VIEW ilq_view AS SELECT TO_VARCHAR(INTERVAL '1' DAY(0)) AS v"));
    }

    /** A text that does not fit its qualifier is refused only when a row reaches the literal. */
    @Test
    public void aTextIsReadOnlyWhenARowReachesIt() {
        assertEquals("", rows("SELECT TO_VARCHAR(INTERVAL 'x' DAY TO HOUR) FROM ilq_empty"));
        assertEquals("null", rows("SELECT CASE WHEN a = 2 THEN TO_VARCHAR(INTERVAL '100' DAY(2)) END FROM ilq_one"));
        assertEquals("Day-Time Interval '1 25' is invalid, required that 0 <= HOUR <= 23, 0 <= MINUTE <= 59,"
            + " 0 <= SECOND <= 59", refusal("SELECT TO_VARCHAR(INTERVAL '1 25' DAY TO HOUR) FROM ilq_one"));
        assertEquals("Day-Time Interval '1 02:60' is invalid, required that 0 <= HOUR <= 23, 0 <= MINUTE <= 59,"
            + " 0 <= SECOND <= 59", refusal("SELECT TO_VARCHAR(INTERVAL '1 02:60' DAY TO MINUTE)"));
        assertEquals("Year-Month Interval '1-13' is invalid, required that 0 <= MONTH <= 11",
            refusal("SELECT TO_VARCHAR(INTERVAL '1-13' YEAR TO MONTH)"));
        assertEquals("Day-Time Interval is '099' invalid, value of leading or fractional second field is greater"
            + " than specified precision/fsp", refusal("SELECT TO_VARCHAR(INTERVAL '099' DAY(2))"));
        assertEquals("Day-Time Interval is '1.2345' invalid, value of leading or fractional second field is"
            + " greater than specified precision/fsp", refusal("SELECT TO_VARCHAR(INTERVAL '1.2345' SECOND(2,3))"));
        assertEquals("Day-Time Interval is '1 002' invalid, value of leading or fractional second field is"
            + " greater than specified precision/fsp", refusal("SELECT TO_VARCHAR(INTERVAL '1 002' DAY TO HOUR)"));
        assertEquals("Year-Month Interval '100' is invalid, value of leading field is greater than specified"
            + " precision", refusal("SELECT TO_VARCHAR(INTERVAL '100' YEAR(2))"));
        assertEquals("Year-Month Interval '1' is invalid, expected format is '<sign>Y(p)-MM' for subtype YEAR TO"
            + " MONTH, '<sign>Y(p)' for subtype YEAR, or '<sign>M(p)' for subtype MONTH",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' YEAR TO MONTH)"));
        final String fractionAtScaleZero = refusal("SELECT TO_VARCHAR(INTERVAL '1.25' SECOND(2,0))");
        assertTrue(fractionAtScaleZero.startsWith("Day-Time Interval '1.25' " + DAY_TIME_FORMATS), fractionAtScaleZero);
        final String doubleSpace = refusal("SELECT TO_VARCHAR(INTERVAL '1  02' DAY TO HOUR)");
        assertTrue(doubleSpace.startsWith("Day-Time Interval '1  02' " + DAY_TIME_FORMATS), doubleSpace);
        final String spacedSign = refusal("SELECT TO_VARCHAR(INTERVAL '- 1' DAY)");
        assertTrue(spacedSign.startsWith("Day-Time Interval '- 1' " + DAY_TIME_FORMATS), spacedSign);
        assertEquals("+1|+1", rows("SELECT TO_VARCHAR(INTERVAL ' 1 ' DAY), TO_VARCHAR(INTERVAL '+1' DAY)"));
    }

    /** The literal casts to its trailing field's count, and a refusal naming it spells the plan's conversion. */
    @Test
    public void aRefusalSpellsTheLiteralAsItsConversion() {
        assertEquals("26|14|1.5", rows("SELECT (INTERVAL '1 02' DAY TO HOUR)::NUMBER,"
            + " (INTERVAL '1-2' YEAR TO MONTH)::NUMBER, (INTERVAL '1.5' SECOND)::NUMBER(10,1)"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'CAST('1 02' AS INTERVAL DAY(9) TO HOUR)'"
            + " of type [INTERVAL DAY(9) TO HOUR] into expected type [INTERVAL MONTH(9)]",
            refusal("SELECT INTERVAL '1' MONTH = INTERVAL '1 02' DAY TO HOUR"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
            + " [TO_VARCHAR(TO_INTERVAL_YEAR_MONTH('1-2'), 'x')] expected 1, got 2",
            refusal("SELECT TO_VARCHAR(INTERVAL '1-2' YEAR TO MONTH, 'x')"));
    }
}
