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
 * Which surfaces read a zoned timestamp's INSTANT and which read its WALL CLOCK.
 *
 * <p>★ IT IS NOT ONE SWITCH. Both readings are right somewhere, and the boundary runs through the
 * middle of several functions:
 *
 * <ul>
 *   <li>EXTRACT / DATE_PART: the EPOCH components read the INSTANT; every other component reads the
 *       wall clock as written. HOUR of a TIMESTAMP_TZ spelled {@code 10:00 +0530} is 10, not its UTC 4.</li>
 *   <li>DATEDIFF: hour-and-smaller count the INSTANT; day-and-larger count wall-clock BOUNDARIES.</li>
 *   <li>CONVERT_TIMEZONE: the two-argument form takes an instant, the three-argument form a wall clock.</li>
 * </ul>
 *
 * <p>★ THE DAYLIGHT-SAVING CELL IS THE ONE THAT PROVES IT. January to June in America/Los_Angeles is
 * 3648 hours by the clock and 3647 by the world, because the clocks moved once in between — and both
 * engines agree on the two endpoints' text, so nothing but the counting rule can explain the
 * difference. A naive pair over the same digits is 3648 on both, which is the control.
 *
 * <p>★ CONVERT_TIMEZONE'S TWO-ARGUMENT FORM CONVERTED NOTHING. It read the value's wall clock and
 * re-labelled it UTC, handing the input straight back. Its answer carries the TARGET zone's offset —
 * an Asia/Tokyo conversion reads {@code +0900} and not the session's, which is what makes it a
 * TIMESTAMP_TZ rather than an LTZ.
 *
 * <p>★ AND A NAIVE INPUT IS A LOCAL TIME THERE, not a UTC one: live converts a TIMESTAMP_NTZ of 10:00
 * under America/Los_Angeles to 18:00 Z, reading it in the session's zone first.
 *
 * <p>NOT FIXED HERE: SYSTEM$TYPEOF's width and storage tag — it answers the right NAME now (it knew
 * neither zoned class and read both as VARCHAR) but still {@code TIMESTAMP_LTZ[LOB]} where live gives
 * {@code TIMESTAMP_LTZ(9)[SB16]}, which is wrong for the naive type too and is tracked with it.
 */
public class LtzInstantSurfacesTest extends BaseDatabaseTest {

    private static final String LTZ = "'2020-01-01 10:00:00'::TIMESTAMP_LTZ";
    private static final String JUN = "'2020-06-01 10:00:00'::TIMESTAMP_LTZ";
    private static final String TZ = "'2020-01-01 10:00:00 +0530'::TIMESTAMP_TZ";
    private static final String NTZ = "'2020-01-01 10:00:00'::TIMESTAMP_NTZ";

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
    }

    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** ★ The EPOCH components read the INSTANT. */
    @Test
    public void theepochComponentsReadTheInstant() {
        assertEquals("1577901600", answer("SELECT DATE_PART('EPOCH_SECOND', " + LTZ + ")"),
            "ten o'clock at -0800 is eighteen hundred UTC, and the epoch counts from there");
        assertEquals("1577901600000",
            answer("SELECT DATE_PART('EPOCH_MILLISECOND', " + LTZ + ")"));
        assertEquals("1577853000", answer("SELECT DATE_PART('EPOCH_SECOND', " + TZ + ")"),
            "and a TZ's epoch comes off the offset it was written with");
        assertEquals("1577872800", answer("SELECT DATE_PART('EPOCH_SECOND', " + NTZ + ")"),
            "a naive timestamp has no offset to apply, so its digits ARE the UTC ones");
    }

    /** ★ Every OTHER component reads the wall clock as written — the control half. */
    @Test
    public void theothercomponentsReadTheWallClock() {
        assertEquals("10", answer("SELECT DATE_PART('HOUR', " + LTZ + ")"));
        assertEquals("10", answer("SELECT DATE_PART('HOUR', " + TZ + ")"),
            "★ a TZ written 10:00 +0530 is hour 10, not the 4 its UTC digits would give");
        assertEquals("0", answer("SELECT DATE_PART('MINUTE', " + TZ + ")"));
        assertEquals("2020", answer("SELECT DATE_PART('YEAR', " + LTZ + ")"));
        assertEquals("1", answer("SELECT DATE_PART('DAY', " + LTZ + ")"));
    }

    /** ★ DATEDIFF's sub-day units count the INSTANT, so a daylight-saving change costs an hour. */
    @Test
    public void thesubDayUnitsCountTheInstant() {
        assertEquals("3647", answer("SELECT DATEDIFF(hour, " + LTZ + ", " + JUN + ")"));
        assertEquals("218820", answer("SELECT DATEDIFF(minute, " + LTZ + ", " + JUN + ")"));
        assertEquals("13129200", answer("SELECT DATEDIFF(second, " + LTZ + ", " + JUN + ")"));
        assertEquals("3648",
            answer("SELECT DATEDIFF(hour, " + NTZ + ", '2020-06-01 10:00:00'::TIMESTAMP_NTZ)"),
            "the control: the same digits with no zone count 3648, by the clock");
    }

    /** And the day-and-larger units count wall-clock boundaries, which the change must not move. */
    @Test
    public void thedayAndLargerUnitsCountBoundaries() {
        assertEquals("152", answer("SELECT DATEDIFF(day, " + LTZ + ", " + JUN + ")"));
        assertEquals("5", answer("SELECT DATEDIFF(month, " + LTZ + ", " + JUN + ")"));
        assertEquals("0", answer("SELECT DATEDIFF(year, " + LTZ + ", " + JUN + ")"));
    }

    /** ★ CONVERT_TIMEZONE's two-argument form re-expresses the INSTANT in the named zone. */
    @Test
    public void thetwoArgumentConversionMovesTheInstant() {
        assertEquals("2020-01-01 18:00:00.000 Z",
            answer("SELECT TO_VARCHAR(CONVERT_TIMEZONE('UTC', " + LTZ + "))"));
        assertEquals("2020-01-01 04:30:00.000 Z",
            answer("SELECT TO_VARCHAR(CONVERT_TIMEZONE('UTC', " + TZ + "))"),
            "a TZ brings its own offset to the conversion");
        assertEquals("2020-01-02 03:00:00.000 +0900",
            answer("SELECT TO_VARCHAR(CONVERT_TIMEZONE('Asia/Tokyo', " + LTZ + "))"),
            "★ the answer carries the TARGET zone's offset, not the session's");
    }

    /** ★ A naive input is read in the SESSION's zone before it is converted. */
    @Test
    public void anaiveInputIsALocalTimeThere() {
        assertEquals("2020-01-01 18:00:00.000 Z",
            answer("SELECT TO_VARCHAR(CONVERT_TIMEZONE('UTC', " + NTZ + "))"));
    }

    /** The THREE-argument form names its own source zone and stays a wall-clock conversion. */
    @Test
    public void thethreeArgumentFormIsUnchanged() {
        assertEquals("2020-01-01 18:00:00.000",
            answer("SELECT TO_VARCHAR(CONVERT_TIMEZONE('America/Los_Angeles', 'UTC', " + NTZ + "))"));
    }

    /** ★ SYSTEM$TYPEOF knows the zoned classes — the NAME, the width being a separate matter. */
    @Test
    public void systemTypeofKnowsTheZonedClasses() {
        assertEquals("true", answer("SELECT SYSTEM$TYPEOF(" + LTZ + ") LIKE 'TIMESTAMP_LTZ%'"));
        assertEquals("true", answer("SELECT SYSTEM$TYPEOF(" + TZ + ") LIKE 'TIMESTAMP_TZ%'"));
        assertEquals("true", answer("SELECT SYSTEM$TYPEOF(" + NTZ + ") LIKE 'TIMESTAMP_NTZ%'"));
    }

    /** The neighbours that already agreed and must not move. */
    @Test
    public void theneighboursAreUntouched() {
        assertEquals("2020-01-01 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(DATE_TRUNC('hour', " + LTZ + "))"));
        assertEquals("2020-01-02 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(DATEADD(day, 1, " + LTZ + "))"));
        assertEquals("true", answer("SELECT " + LTZ + " = " + NTZ),
            "the same wall-clock digits in the session's zone are the same instant");
        assertEquals("true", answer("SELECT " + LTZ + " < " + JUN));
    }
}
