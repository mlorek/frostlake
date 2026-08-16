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
 * A date/time unit may be written with any of the dozens of abbreviations the account takes, as a
 * BAREWORD. Frostlake refused almost all of them, and in a way that hid what was wrong: the word never
 * reached the function at all, failing as {@code invalid identifier 'YY'}, because the list of
 * barewords the evaluator resolves to themselves was a hand-kept copy that had drifted from the alias
 * table beside it.
 *
 * <p>The vocabulary is NOT one list, which is the trap this covers:
 *
 * <pre>
 *   woy / weekofyear   DATE_PART reads them; DATEADD refuses them — a component, not a span
 *   ms / us            DATEADD adds them; DATE_PART refuses them, every spelling
 *   ns                 both take it
 * </pre>
 *
 * <p>And one ordering matters inside the canonicaliser: the alias is resolved BEFORE any plural is
 * stripped, because stripping is blind and would turn {@code MS} into {@code M} — milliseconds into
 * minutes.
 */
public class DatePartAliasTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE da (ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO da SELECT '2020-03-04 10:20:30'");
    }

    /** The answer, or the message of the refusal it raised. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery(
                "SELECT COALESCE(TO_VARCHAR(" + expr + "), '<NULL>') AS x FROM da");
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Every year and month spelling adds the same amount. */
    @Test
    public void theYearAndMonthAbbreviationsAdd() {
        final String year = "2021-03-04 10:20:30.000";
        assertEquals(year, outcome("DATEADD(y, 1, ts)"));
        assertEquals(year, outcome("DATEADD(yy, 1, ts)"));
        assertEquals(year, outcome("DATEADD(yyyy, 1, ts)"));
        assertEquals(year, outcome("DATEADD(yr, 1, ts)"));
        assertEquals(year, outcome("DATEADD(yrs, 1, ts)"));
        final String month = "2020-04-04 10:20:30.000";
        assertEquals(month, outcome("DATEADD(mm, 1, ts)"));
        assertEquals(month, outcome("DATEADD(mon, 1, ts)"));
        assertEquals(month, outcome("DATEADD(mons, 1, ts)"));
        assertEquals("2020-06-04 10:20:30.000", outcome("DATEADD(qtr, 1, ts)"));
        assertEquals("2020-03-11 10:20:30.000", outcome("DATEADD(wk, 1, ts)"));
    }

    /** And every clock-unit spelling, including the three that a blind plural strip would ruin. */
    @Test
    public void theClockAbbreviationsAdd() {
        assertEquals("2020-03-04 11:20:30.000", outcome("DATEADD(hh, 1, ts)"));
        assertEquals("2020-03-04 11:20:30.000", outcome("DATEADD(hr, 1, ts)"));
        assertEquals("2020-03-04 10:21:30.000", outcome("DATEADD(mi, 1, ts)"));
        assertEquals("2020-03-04 10:21:30.000", outcome("DATEADD(min, 1, ts)"));
        assertEquals("2020-03-04 10:20:31.000", outcome("DATEADD(sec, 1, ts)"));
        assertEquals("2020-03-04 10:20:30.001", outcome("DATEADD(ms, 1, ts)"),
            "MS is milliseconds — stripping its S first would make it minutes");
        assertEquals("2020-03-04 10:20:30.000", outcome("DATEADD(us, 1, ts)"));
        assertEquals("2020-03-04 10:20:30.000", outcome("DATEADD(ns, 1, ts)"));
    }

    /** The other spellings of the same operation take the same words. */
    @Test
    public void theOtherAddAndDiffSpellingsAgree() {
        assertEquals("2020-03-05 10:20:30.000", outcome("TIMEADD(dd, 1, ts)"));
        assertEquals("2020-03-05 10:20:30.000", outcome("TIMESTAMPADD(dd, 1, ts)"));
        assertEquals("0", outcome("TIMEDIFF(dd, ts, ts)"));
        assertEquals("0", outcome("TIMESTAMPDIFF(dd, ts, ts)"));
        assertEquals("0", outcome("DATEDIFF(ns, ts, ts)"));
        assertEquals("0", outcome("DATEDIFF(qtr, ts, ts)"));
    }

    /** DATE_TRUNC takes them too, down to the sub-second units it used to refuse. */
    @Test
    public void truncationTakesTheSameWords() {
        assertEquals("2020-01-01 00:00:00.000", outcome("DATE_TRUNC(yy, ts)"));
        assertEquals("2020-03-04 00:00:00.000", outcome("DATE_TRUNC(dd, ts)"));
        assertEquals("2020-03-04 10:00:00.000", outcome("DATE_TRUNC(hh, ts)"));
        assertEquals("2020-03-04 10:20:30.000", outcome("DATE_TRUNC(ms, ts)"));
        assertEquals("2020-03-04 10:20:30.000", outcome("DATE_TRUNC(us, ts)"));
        assertEquals("2020-03-04 10:20:30.000", outcome("DATE_TRUNC(ns, ts)"));
    }

    /** DATE_PART reads a component, and its vocabulary is its OWN. */
    @Test
    public void theComponentReaderHasItsOwnVocabulary() {
        assertEquals("2020", outcome("DATE_PART(yy, ts)"));
        assertEquals("3", outcome("DATE_PART(mm, ts)"));
        assertEquals("10", outcome("DATE_PART(hh, ts)"));
        assertEquals("20", outcome("DATE_PART(mi, ts)"));
        assertEquals("10", outcome("DATE_PART(woy, ts)"), "a component DATEADD refuses");
        assertEquals("0", outcome("DATE_PART(ns, ts)"));
        assertEquals("0", outcome("DATE_PART(nanosec, ts)"));
    }

    /** …and REFUSES the millisecond and microsecond family, which DATEADD accepts. */
    @Test
    public void theComponentReaderRefusesTheSubMilliUnits() {
        assertEquals("SQL compilation error: invalid value [MILLISECOND] for parameter"
            + " 'DATE_PART date/time part'", outcome("DATE_PART(millisecond, ts)"));
        assertEquals("SQL compilation error: invalid value [MS] for parameter"
            + " 'DATE_PART date/time part'", outcome("DATE_PART(ms, ts)"));
        assertEquals("SQL compilation error: invalid value [MSEC] for parameter"
            + " 'DATE_PART date/time part'", outcome("DATE_PART(msec, ts)"));
        assertEquals("SQL compilation error: invalid value [MICROSECOND] for parameter"
            + " 'DATE_PART date/time part'", outcome("DATE_PART(microsecond, ts)"));
        assertEquals("SQL compilation error: invalid value [USEC] for parameter"
            + " 'DATE_PART date/time part'", outcome("DATE_PART(usec, ts)"));
    }

    /**
     * And the one word the two vocabularies disagree on: an interval function refuses it, naming
     * itself. {@code woy} is still a unit for INTERVAL and still readable by DATE_PART.
     */
    @Test
    public void theIntervalFunctionsRefuseAComponentOnlyWord() {
        assertEquals("SQL compilation error: ['WOY'] is not a valid date/time component"
            + " for function DATEADD.", outcome("DATEADD(woy, 1, ts)"));
        assertEquals("SQL compilation error: ['WEEKOFYEAR'] is not a valid date/time component"
            + " for function DATEADD.", outcome("DATEADD(weekofyear, 1, ts)"));
        assertEquals("SQL compilation error: ['WOY'] is not a valid date/time component"
            + " for function DATEDIFF.", outcome("DATEDIFF(woy, ts, ts)"));
        assertEquals("SQL compilation error: ['WOY'] is not a valid date/time component"
            + " for function DATE_TRUNC.", outcome("DATE_TRUNC(woy, ts)"));
    }

    /** The quoted spelling still works, and the full words are untouched. */
    @Test
    public void theQuotedAndFullSpellingsStillWork() {
        assertEquals("2020-03-05 10:20:30.000", outcome("DATEADD('dd', 1, ts)"));
        assertEquals("2020-03-05 10:20:30.000", outcome("DATEADD(day, 1, ts)"));
        assertEquals("2021-03-04 10:20:30.000", outcome("DATEADD(year, 1, ts)"));
        assertEquals("2020-03-04 00:00:00.000", outcome("DATE_TRUNC(day, ts)"));
        assertEquals("2020", outcome("DATE_PART(year, ts)"));
    }
}
