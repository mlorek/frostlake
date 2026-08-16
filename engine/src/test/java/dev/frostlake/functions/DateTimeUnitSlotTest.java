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
 * WHERE a date/time unit word is looked up. Snowflake reads the first argument of the interval
 * functions as a KEYWORD SLOT — never as an expression — so the unit wins against everything, and
 * Frostlake had the precedence backwards: it resolved the unit word only AFTER a column lookup, so a
 * table with an INT column {@code dd} turned {@code DATEADD(dd, 1, ts)} into "unit 7".
 *
 * <pre>
 *   DATEADD(dd, 1, ts)      adds one DAY, over a table whose dd column holds 7
 *   DATEADD(sh.dd, 1, ts)   the same — the QUALIFIER is read and thrown away
 *   DATEADD("DAY", 1, ts)   and a quoted identifier is a unit too
 *   SELECT dd, dd + 1       still 7 and 8: only the SLOT is special
 * </pre>
 *
 * <p>The mirror of it is TRUNC, whose unit is NOT a slot: its second argument is an ordinary value,
 * so live refuses a bareword there outright where Frostlake truncated to the hour.
 *
 * <p>Three refusal families come out of the slot, and they are all spelled differently:
 *
 * <pre>
 *   a word that is no unit      ['ZZ'] is not a valid date/time component for function DATEADD.
 *   … but for DATE_PART         invalid value [ZZ] for parameter 'DATE_PART date/time part'
 *   a slot that is not a name   Date/time component [UPPER('day') ]for function DATEADD needs to be
 *                               an identifier or a string literal.
 * </pre>
 *
 * <p>A BAREWORD is quoted upper-cased and a string literal as written, in the first two. The third
 * carries live's own spacing — a space before the bracket closes and none after it.
 *
 * <p>Compared by DETAIL rather than by whole message: what follows the "SQL compilation error:" prefix,
 * a space or a line break, is a property of each sentence and is pinned where that sentence is.
 */
public class DateTimeUnitSlotTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sh (ts TIMESTAMP_NTZ, tm TIME, dd INT,"
            + " hour INT, mm INT, u VARCHAR)");
        engine.execute("INSERT INTO sh SELECT '2020-03-04 10:20:30'::TIMESTAMP_NTZ,"
            + " '10:20:30'::TIME, 7, 9, 11, 'day'");
    }

    /** The answer, or the refusal's DETAIL with the compilation prefix and its line break removed. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM sh");
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage())
                .replace("SQL compilation error:\n", "").replace("SQL compilation error: ", "")
                .replace("\n", " ").trim();
        }
    }

    /** The slot beats a real column of the same name, qualified or not. */
    @Test
    public void theSlotBeatsAColumnOfTheSameName() {
        assertEquals("2020-03-05T10:20:30", outcome("DATEADD(dd, 1, ts)"));
        assertEquals("2020-03-04T11:20:30", outcome("DATEADD(hour, 1, ts)"));
        assertEquals("2020-04-04T10:20:30", outcome("DATEADD(mm, 1, ts)"));
        assertEquals("2020-03-05T10:20:30", outcome("DATEADD(sh.dd, 1, ts)"),
            "the qualifier is read and thrown away — no relation is looked up");
        assertEquals("2020-03-05T10:20:30", outcome("DATEADD(\"DAY\", 1, ts)"));
    }

    /** Every function that takes a slot takes it in the same way. */
    @Test
    public void everySlotTakingFunctionAgrees() {
        assertEquals("0", outcome("DATEDIFF(dd, ts, ts)"));
        assertEquals("0", outcome("DATEDIFF(sh.dd, ts, ts)"));
        assertEquals("2020-03-04T00:00", outcome("DATE_TRUNC(dd, ts)"));
        assertEquals("4", outcome("DATE_PART(dd, ts)"));
        assertEquals("2020-03-05T10:20:30", outcome("TIMEADD(dd, 1, ts)"));
        assertEquals("2020-03-05T10:20:30", outcome("TIMESTAMPADD(dd, 1, ts)"));
        assertEquals("0", outcome("TIMEDIFF(dd, ts, ts)"));
        assertEquals("0", outcome("TIMESTAMPDIFF(dd, ts, ts)"));
        assertEquals("2020-03-31", outcome("LAST_DAY(ts, mm)"), "LAST_DAY's slot is its SECOND");
    }

    /** And the same columns, read anywhere else, are still columns. */
    @Test
    public void outsideASlotTheWordIsStillAColumn() {
        assertEquals("7", outcome("dd"));
        assertEquals("8", outcome("dd + 1"));
        assertEquals("7", outcome("ABS(dd)"));
        assertEquals("9", outcome("hour"));
        assertEquals("20", outcome("hour + mm"));
    }

    /** A word that names no unit is a bad COMPONENT, not an unknown column. */
    @Test
    public void anUnknownWordIsABadComponent() {
        assertEquals("['ZZ'] is not a valid date/time component for function DATEADD.",
            outcome("DATEADD(zz, 1, ts)"), "a bareword is quoted UPPER-cased");
        assertEquals("['zz'] is not a valid date/time component for function DATEADD.",
            outcome("DATEADD('zz', 1, ts)"), "a string literal keeps its own case");
        assertEquals("['ZZ'] is not a valid date/time component for function DATEADD.",
            outcome("DATEADD(sh.zz, 1, ts)"), "and a qualified one is still just the word");
        assertEquals("['U'] is not a valid date/time component for function DATEADD.",
            outcome("DATEADD(u, 1, ts)"), "a VARCHAR column holding 'day' is the word U, not its value");
        assertEquals("['ZZ'] is not a valid date/time component for function DATE_TRUNC.",
            outcome("DATE_TRUNC(zz, ts)"));
        assertEquals("['ZZ'] is not a valid date/time component for function LAST_DAY.",
            outcome("LAST_DAY(ts, zz)"));
        assertEquals("['zz'] is not a valid date/time component for function LAST_DAY.",
            outcome("LAST_DAY(ts, 'zz')"), "LAST_DAY used to read any unknown word as MONTH");
    }

    /** DATE_PART spells the same complaint its own way. */
    @Test
    public void datePartHasASentenceOfItsOwn() {
        assertEquals("invalid value [ZZ] for parameter 'DATE_PART date/time part'",
            outcome("DATE_PART(zz, ts)"));
        assertEquals("invalid value [zz] for parameter 'DATE_PART date/time part'",
            outcome("DATE_PART('zz', ts)"));
        assertEquals("invalid value [U] for parameter 'DATE_PART date/time part'",
            outcome("DATE_PART(u, ts)"));
        assertEquals("invalid value [null] for parameter 'DATE_PART date/time part'",
            outcome("DATE_PART(1, ts)"), "and it reports a non-name slot as the WORD null");
    }

    /** A slot that holds no name at all is refused before the unit is looked up. */
    @Test
    public void aSlotMustHoldANameOrAString() {
        assertEquals("Date/time component [UPPER('day') ]for function DATEADD"
            + " needs to be an identifier or a string literal.",
            outcome("DATEADD(UPPER('day'), 1, ts)"));
        assertEquals("Date/time component [1 ]for function DATEADD"
            + " needs to be an identifier or a string literal.", outcome("DATEADD(1, 1, ts)"));
        assertEquals("Date/time component [1 ]for function DATEDIFF"
            + " needs to be an identifier or a string literal.", outcome("DATEDIFF(1, ts, ts)"));
        assertEquals("Date/time component [1 ]for function DATE_TRUNC"
            + " needs to be an identifier or a string literal.", outcome("DATE_TRUNC(1, ts)"));
    }

    /** NULL in a slot is not a fault — the whole call is NULL. */
    @Test
    public void aNullSlotMakesTheCallNull() {
        assertEquals("null", outcome("DATEADD(NULL, 1, ts)"));
        assertEquals("null", outcome("DATEDIFF(NULL, ts, ts)"));
        assertEquals("null", outcome("DATE_TRUNC(NULL, ts)"));
        assertEquals("null", outcome("DATE_PART(NULL, ts)"));
    }

    /** TRUNC has NO slot: its unit is a value, and only a string literal will do. */
    @Test
    public void truncTakesNoSlot() {
        assertEquals("10:00", outcome("TRUNC(tm, 'hour')"));
        assertEquals("2020-03-04T10:00", outcome("TRUNC(ts, 'hour')"));
        assertEquals("Date/time component [SH.U ]for function TRUNC"
            + " needs to be an identifier or a string literal.", outcome("TRUNC(tm, u)"));
        assertEquals("Date/time component [SH.U ]for function TRUNC"
            + " needs to be an identifier or a string literal.", outcome("TRUNC(tm, sh.u)"));
        assertEquals("Date/time component [UPPER('hour') ]for function TRUNC"
            + " needs to be an identifier or a string literal.", outcome("TRUNC(tm, UPPER('hour'))"));
        assertEquals("null", outcome("TRUNC(tm, NULL)"));
    }

    /** A non-text argument is CONVERTED on the way in, and the echo shows the conversion. */
    @Test
    public void truncEchoesTheConversionOfANonTextUnit() {
        assertEquals("Date/time component [TO_CHAR(SH.HOUR) ]for function TRUNC"
            + " needs to be an identifier or a string literal.", outcome("TRUNC(tm, hour)"),
            "Frostlake used to truncate to the hour here, reading the column name as a unit");
        assertEquals("Date/time component [TO_CHAR(SH.HOUR) ]for function TRUNC"
            + " needs to be an identifier or a string literal.", outcome("TRUNC(ts, hour)"));
        assertEquals("Date/time component [TO_CHAR(1) ]for function TRUNC"
            + " needs to be an identifier or a string literal.", outcome("TRUNC(tm, 1)"));
    }

    /** The NUMERIC TRUNC keeps its scale argument, which is what the check must not disturb. */
    @Test
    public void theNumericTruncIsUntouched() {
        assertEquals("123.4", outcome("TRUNC(123.45, 1)"));
        assertEquals("123", outcome("TRUNC(123.45)"));
    }
}
