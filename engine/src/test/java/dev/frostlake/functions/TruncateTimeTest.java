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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A TIME truncates on the CLOCK, and refuses anything larger than itself. Frostlake parsed the value
 * as a date/time before looking at it, so every unit failed the same way — "Cannot parse date/time:
 * 10:31:45.123456789" — whether or not the unit made sense for a TIME.
 *
 * <p>The two halves are spelled differently by the account, and the difference is the point:
 *
 * <pre>
 *   DATE_TRUNC(hour, tm)   10:00:00 — a sub-day unit truncates
 *   DATE_TRUNC(day, tm)    [DAY] is not a valid date/time component for function DATE_TRUNC and type TIME.
 * </pre>
 *
 * <p>Note the refusal's shape against its neighbour for an unknown unit: the word is bracketed
 * WITHOUT quotes, upper-cased however the call wrote it, and the sentence names the TYPE as well as
 * the function — where the unknown-unit sentence quotes the word and names only the function.
 *
 * <p>TRUNC is the same operation with its arguments reversed, so it answers alike and reports under
 * its OWN name.
 */
public class TruncateTimeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tt (tm TIME, ts TIMESTAMP_NTZ, d DATE)");
        engine.execute("INSERT INTO tt SELECT '10:31:45.123456789',"
            + " '2020-03-04 10:31:45', '2020-03-04'");
    }

    /** The answer, or the message of the refusal it raised. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery(
                "SELECT COALESCE(TO_VARCHAR(" + expr + "), '<NULL>') AS x FROM tt");
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** A sub-day unit truncates the clock and answers a TIME. */
    @Test
    public void aSubDayUnitTruncatesTheClock() {
        assertEquals("10:00:00", outcome("DATE_TRUNC(hour, tm)"));
        assertEquals("10:31:00", outcome("DATE_TRUNC(minute, tm)"));
        assertEquals("10:31:45", outcome("DATE_TRUNC(second, tm)"));
    }

    /** The sub-second units truncate too, below what the rendering shows. */
    @Test
    public void theSubSecondUnitsTruncateToo() {
        assertEquals("10:31:45", outcome("DATE_TRUNC(millisecond, tm)"));
        assertEquals("10:31:45", outcome("DATE_TRUNC(microsecond, tm)"));
        assertEquals("10:31:45", outcome("DATE_TRUNC(nanosecond, tm)"));
    }

    /** A day or larger has no date to truncate, and says so naming the type. */
    @Test
    public void aDayOrLargerUnitIsRefusedNamingTheType() {
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component"
            + " for function DATE_TRUNC and type TIME.", outcome("DATE_TRUNC(day, tm)"));
        assertEquals("SQL compilation error: [WEEK] is not a valid date/time component"
            + " for function DATE_TRUNC and type TIME.", outcome("DATE_TRUNC(week, tm)"));
        assertEquals("SQL compilation error: [MONTH] is not a valid date/time component"
            + " for function DATE_TRUNC and type TIME.", outcome("DATE_TRUNC(month, tm)"));
        assertEquals("SQL compilation error: [YEAR] is not a valid date/time component"
            + " for function DATE_TRUNC and type TIME.", outcome("DATE_TRUNC(year, tm)"));
    }

    /** TRUNC is the same operation reversed, and reports under its own name. */
    @Test
    public void truncAnswersAlikeAndNamesItself() {
        assertEquals("10:00:00", outcome("TRUNC(tm, 'hour')"));
        assertEquals("10:31:00", outcome("TRUNC(tm, 'minute')"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component"
            + " for function TRUNC and type TIME.", outcome("TRUNC(tm, 'day')"));
    }

    /** And the timestamp and date paths are untouched. */
    @Test
    public void theTimestampAndDatePathsAreUnchanged() {
        assertEquals("2020-03-04 10:00:00.000", outcome("DATE_TRUNC(hour, ts)"));
        assertEquals("2020-03-01", outcome("DATE_TRUNC(month, d)"));
        assertEquals("2020-03-04 10:00:00.000", outcome("TRUNC(ts, 'hour')"));
    }
}
