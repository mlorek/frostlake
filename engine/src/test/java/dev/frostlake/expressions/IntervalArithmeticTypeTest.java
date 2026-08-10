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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shifting a temporal by an INTERVAL, where the line falls at the DAY rather than at the month.
 *
 * <p>A WHOLE-DAY unit — year, month, day — leaves a DATE a DATE. A SUB-DAY unit — hour, minute,
 * second — promotes it to TIMESTAMP_NTZ. Frostlake used to draw that line at the month, so
 * {@code DATE '2026-01-01' + INTERVAL '1 day'} came back as a timestamp: the right instant with the
 * wrong type, and a midnight the account never prints.
 *
 * <p>A timestamp keeps whatever flavour it already had, whichever unit is added.
 */
public class IntervalArithmeticTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE iv_base (d DATE, ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO iv_base VALUES ('2026-01-01', '2026-01-01 00:00:00')");
    }

    private String rendered(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The declared data_type of a one-column view over the expression. */
    private String declaredType(final String expression) {
        engine.execute("CREATE OR REPLACE VIEW iv_view AS SELECT " + expression + " AS c FROM iv_base");
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.iv_view");
        rs.next();
        return String.valueOf(rs.getValue("data_type"));
    }

    /** A whole-day unit leaves a DATE a DATE — no midnight appears. */
    @Test
    public void aWholeDayUnitLeavesADateADate() {
        assertEquals("2027-01-01", rendered("SELECT DATE '2026-01-01' + INTERVAL '1 year'"));
        assertEquals("2026-02-01", rendered("SELECT DATE '2026-01-01' + INTERVAL '1 month'"));
        assertEquals("2026-01-02", rendered("SELECT DATE '2026-01-01' + INTERVAL '1 day'"));
        assertEquals("2025-12-31", rendered("SELECT DATE '2026-01-01' - INTERVAL '1 day'"));
    }

    /** A sub-day unit promotes it, because the result no longer lands on a date boundary. */
    @Test
    public void aSubDayUnitPromotesADateToATimestamp() {
        assertEquals("2026-01-01T01:00", rendered("SELECT DATE '2026-01-01' + INTERVAL '1 hour'"));
        assertEquals("2026-01-01T00:01", rendered("SELECT DATE '2026-01-01' + INTERVAL '1 minute'"));
        assertEquals("2026-01-01T00:00:01", rendered("SELECT DATE '2026-01-01' + INTERVAL '1 second'"));
    }

    /** The same rule off a COLUMN, not just a literal. */
    @Test
    public void theRuleHoldsForAColumnToo() {
        assertEquals("2026-01-02", rendered("SELECT d + INTERVAL '1 day' FROM iv_base"));
        assertEquals("2026-01-01T01:00", rendered("SELECT d + INTERVAL '1 hour' FROM iv_base"));
    }

    /** A timestamp keeps its own flavour whichever unit is added. */
    @Test
    public void aTimestampStaysATimestamp() {
        assertEquals("2026-01-02T00:00", rendered("SELECT ts + INTERVAL '1 day' FROM iv_base"));
        assertEquals("2026-01-01T01:00", rendered("SELECT ts + INTERVAL '1 hour' FROM iv_base"));
    }

    /**
     * And the static channel agrees with the value, so a view over the expression declares the type
     * rather than the VARCHAR placeholder it used to.
     */
    @Test
    public void aViewDeclaresTheShiftedType() {
        assertEquals("""
            {"type":"DATE","nullable":true}""", declaredType("d + INTERVAL '1 day'"));
        assertEquals("""
            {"type":"TIMESTAMP_NTZ","precision":0,"scale":9,"nullable":true}""",
            declaredType("ts + INTERVAL '1 hour'"));
    }

    /** A bare interval is not a value on either side — it is only ever an operand. */
    @Test
    public void aBareIntervalLiteralIsRefused() {
        assertEquals("interval literal is not supported in this form.",
            refusalSentence("SELECT INTERVAL '1 day'"));
    }

    private String refusalSentence(final String sql) {
        try {
            engine.executeQuery(sql);
        } catch (final RuntimeException e) {
            final String flat = e.getMessage().replace('\n', ' ');
            final int at = flat.lastIndexOf("interval literal");
            return at < 0 ? flat : flat.substring(at).trim();
        }
        return "accepted";
    }
    /**
     * The unit VOCABULARY, every spelling of it measured on a live account. One unit has many
     * spellings, and which abbreviations exist follows no rule worth guessing at — {@code wk} is a
     * week but {@code wks} is not a unit at all, {@code sec} is a second but {@code ss} is not.
     */
    @Test
    public void everyMeasuredUnitSpellingIsAccepted() {
        assertEquals("2027-01-01", shifted("y"));
        assertEquals("2027-01-01", shifted("yyyy"));
        assertEquals("2026-04-01", shifted("quarter"));
        assertEquals("2026-04-01", shifted("qtr"));
        assertEquals("2026-02-01", shifted("mm"));
        assertEquals("2026-01-08", shifted("week"));
        assertEquals("2026-01-08", shifted("wk"));
        assertEquals("2026-01-08", shifted("woy"));
        assertEquals("2026-01-02", shifted("dd"));
        assertEquals("2026-01-01T01:00", shifted("hr"));
        assertEquals("2026-01-01T00:01", shifted("mi"));
        assertEquals("2026-01-01T00:00:01", shifted("sec"));
    }

    /** The SUB-SECOND family, which the engine had no units for at all. */
    @Test
    public void theSubSecondUnitsShiftByTheRightAmount() {
        assertEquals("2026-01-01T00:00:00.001", shifted("ms"));
        assertEquals("2026-01-01T00:00:00.001", shifted("millisecond"));
        assertEquals("2026-01-01T00:00:00.000001", shifted("us"));
        assertEquals("2026-01-01T00:00:00.000000001", shifted("ns"));
        assertEquals("2026-01-01T00:00:00.000000001", shifted("nanosec"));
    }

    /** WEEK and QUARTER are whole-day units, so a DATE stays a DATE. */
    @Test
    public void weekAndQuarterLeaveADateADate() {
        assertEquals("""
            {"type":"DATE","nullable":true}""", declaredType("d + INTERVAL '1 week'"));
        assertEquals("""
            {"type":"DATE","nullable":true}""", declaredType("d + INTERVAL '1 quarter'"));
    }

    /**
     * A spelling live does not know is refused with LIVE'S OWN SENTENCE, echoing the word as written.
     * These four are the near-misses of accepted ones, which is exactly why they are asserted.
     */
    @Test
    public void anUnknownUnitSpellingIsRefusedTheWayLiveRefusesIt() {
        for (final String unknown : new String[]{"wofy", "ss", "wks", "msecs"}) {
            final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT DATE '2026-01-01' + INTERVAL '1 " + unknown + "'");
                }
            });
            assertTrue(e.getMessage().contains(unknown + " is not recognized as a date type."),
                unknown + " gave: " + e.getMessage());
        }
    }

    /** The value a DATE shifts to, rendered — the surface that shows both amount and type. */
    private String shifted(final String unit) {
        return rendered("SELECT DATE '2026-01-01' + INTERVAL '1 " + unit + "'");
    }
}
