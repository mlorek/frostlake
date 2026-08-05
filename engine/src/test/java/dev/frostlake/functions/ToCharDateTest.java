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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TO_CHAR / TO_VARCHAR date/time format model: Snowflake elements (YYYY/YY/Y, MMMM/MON/MM, DD/DY/D,
 * HH24/HH12/HH/H, MI, SS, AM/PM) are translated to a java.time formatter with greedy longest-match
 * tokenization; unmatched characters pass through as literals and a non-date string is returned
 * unchanged. Snowflake has NO full-name 'DAY'/'MONTH' elements: 'DAY' tokenizes D+A+Y and 'MONTH'
 * tokenizes MON+T+H (both live-verified on DATE '2020-01-15', a Wednesday). Month/day names are
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
        assertEquals("January", toChar("TO_CHAR(d, 'MMMM')"));
        // 'MONTH' is NOT an element: it tokenizes MON+T+H — "Jan", literal T, unpadded hour 0
        // (live-verified).
        assertEquals("JanT0", toChar("TO_CHAR(d, 'MONTH')"));
    }

    @Test
    public void dayNames() {
        assertEquals("Wed", toChar("TO_CHAR(d, 'DY')"));
        // 'DAY' is NOT an element: it tokenizes D+A+Y — unpadded day 15, literal A, 2-digit year 20
        // (live-verified).
        assertEquals("15A20", toChar("TO_CHAR(d, 'DAY')"));
    }

    @Test
    public void singleLetterElements() {
        // Live-verified: Y = 2-digit year, D = unpadded day-of-month, HH = 24-hour.
        assertEquals("20", toChar("TO_CHAR(d, 'Y')"));
        assertEquals("15", toChar("TO_CHAR(d, 'D')"));
        assertEquals("13", toChar("TO_CHAR(ts, 'HH')"));
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
    public void nonDateStringPassesThroughWithoutAFormat() {
        // Over a VARCHAR input TO_CHAR is single-argument; supplying a format is an arity error
        // ("too many arguments for function [TO_CHAR('hello', 'YYYY-MM-DD')] expected 1, got 2",
        // live-verified), while the bare form passes the text through.
        assertEquals("hello", toChar("TO_CHAR('hello')"));
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                toChar("TO_CHAR('hello', 'YYYY-MM-DD')");
            }
        });
    }
}
