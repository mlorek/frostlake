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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TO_CHAR / TO_VARCHAR date/time format model: Snowflake elements (YYYY, MM, MON/MONTH/MMMM,
 * DD, DY/DAY, HH24/HH12, MI, SS, AM/PM) are translated to a java.time formatter; separators pass
 * through and a non-date string is returned unchanged. 2020-01-15 is a Wednesday. Month/day names are
 * rendered in title case (the format element's case is not propagated — a documented approximation).
 */
public class ToCharDateTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE dt (d DATE, ts TIMESTAMP)");
        engine.execute("INSERT INTO dt VALUES ('2020-01-15', '2020-01-15 13:45:30')");
    }

    private String toChar(final String expr) {
        return engine.executeQuery("SELECT " + expr + " AS r FROM dt").getRows().get(0).getValue(0).toString();
    }

    @Test
    public void isoDate() {
        assertEquals("2020-01-15", toChar("TO_CHAR(d, 'YYYY-MM-DD')"));
    }

    @Test
    public void usDateWithSlashes() {
        assertEquals("01/15/2020", toChar("TO_CHAR(d, 'MM/DD/YYYY')"));
    }

    @Test
    public void abbreviatedMonthName() {
        assertEquals("Jan 15, 2020", toChar("TO_CHAR(d, 'MON DD, YYYY')"));
    }

    @Test
    public void fullMonthName() {
        assertEquals("January", toChar("TO_CHAR(d, 'MONTH')"));
        assertEquals("January", toChar("TO_CHAR(d, 'MMMM')"));
    }

    @Test
    public void dayNames() {
        assertEquals("Wed", toChar("TO_CHAR(d, 'DY')"));
        assertEquals("Wednesday", toChar("TO_CHAR(d, 'DAY')"));
    }

    @Test
    public void timestampTwentyFourHour() {
        assertEquals("2020-01-15 13:45:30", toChar("TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SS')"));
    }

    @Test
    public void timestampTwelveHourWithMeridiem() {
        assertEquals("01:45 PM", toChar("TO_CHAR(ts, 'HH12:MI AM')"));
    }

    @Test
    public void toVarcharSynonymFormatsYear() {
        assertEquals("2020", toChar("TO_VARCHAR(d, 'YYYY')"));
    }

    @Test
    public void nonDateStringPassesThrough() {
        assertEquals("hello", toChar("TO_CHAR('hello', 'YYYY-MM-DD')"));
    }
}
