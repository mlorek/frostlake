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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A string of digits read as a TIME is an epoch in the unit its magnitude picks — seconds, then
 * milliseconds, microseconds and nanoseconds, as a TIMESTAMP's digits are — placed in its day, whichever
 * spelling converts it: TO_TIME, TIME, a cast, a TRY_ form, a comparison or a write. Live-verified.
 */
public class IntegerStringTimeTest extends BaseDatabaseTest {

    /** The one cell of a single-row query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** The message a refused statement carries. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    /** TO_TIME over the text, printed to the nanosecond. */
    private String time(final String text) {
        return scalar("SELECT TO_VARCHAR(TO_TIME('" + text + "'), 'HH24:MI:SS.FF9')");
    }

    /** Seconds are counted plainly and wrap at midnight, in either direction. */
    @Test
    public void digitsAreSecondsInTheirDay() {
        assertEquals("00:00:00.000000000", time("0"));
        assertEquals("00:00:12.000000000", time("12"));
        assertEquals("00:01:30.000000000", time("90"));
        assertEquals("10:00:00.000000000", time("36000"));
        assertEquals("23:59:59.000000000", time("86399"));
        assertEquals("00:00:00.000000000", time("86400"));
        assertEquals("03:46:40.000000000", time("100000"));
        assertEquals("22:13:20.000000000", time("1700000000"));
        assertEquals("23:59:59.000000000", time("-1"));
        assertEquals("00:00:00.000000000", time("-86400"));
        assertEquals("00:00:12.000000000", time("+12"));
        assertEquals("00:00:12.000000000", time(" 12 "));
        assertEquals("00:00:12.000000000", time("0012"));
    }

    /** The magnitude picks the unit, and a value past a long still reads. */
    @Test
    public void theMagnitudePicksTheUnit() {
        assertEquals("23:59:59.000000000", time("31535999999"));
        assertEquals("00:00:00.123000000", time("31536000123"));
        assertEquals("22:13:20.123000000", time("1700000000123"));
        assertEquals("22:13:20.123456000", time("1700000000123456"));
        assertEquals("00:00:00.000000000", time("31536000000000000"));
        assertEquals("22:13:20.123456789", time("1700000000123456789"));
        assertEquals("23:47:16.854775807", time("9223372036854775807"));
        assertEquals("09:46:39.999999999", time("99999999999999999999"));
    }

    /** A point or an exponent makes no integer, and a format other than AUTO reads the text itself. */
    @Test
    public void onlyAnIntegerIsAnEpoch() {
        assertEquals("Time '12.5' is not recognized", refusal("SELECT TO_TIME('12.5')"));
        assertEquals("Time '1e3' is not recognized", refusal("SELECT TO_TIME('1e3')"));
        assertEquals("12:00:00.000000000", scalar("SELECT TO_VARCHAR(TO_TIME('12', 'HH24'), 'HH24:MI:SS.FF9')"));
        assertEquals("10:00:00.000000000", scalar("SELECT TO_VARCHAR(TO_TIME('36000', 'AUTO'), 'HH24:MI:SS.FF9')"));
    }

    /** Every spelling of the conversion reads the digits the same way. */
    @Test
    public void everySpellingReadsTheDigits() {
        engine.execute("CREATE OR REPLACE TABLE tt (tm TIME)");
        engine.execute("INSERT INTO tt VALUES ('10:00:00')");
        assertEquals("10:00:00", scalar("SELECT TO_VARCHAR(TIME('36000'), 'HH24:MI:SS')"));
        assertEquals("00:00:12", scalar("SELECT TO_VARCHAR(CAST('12' AS TIME), 'HH24:MI:SS')"));
        assertEquals("00:01:30", scalar("SELECT TO_VARCHAR('90'::TIME, 'HH24:MI:SS')"));
        assertEquals("00:20:30", scalar("SELECT TO_VARCHAR('1230'::TIME, 'HH24:MI:SS')"));
        assertEquals("00:00:12", scalar("SELECT TO_VARCHAR(TRY_TO_TIME('12'), 'HH24:MI:SS')"));
        assertEquals("23:59:59", scalar("SELECT TO_VARCHAR(TRY_TO_TIME('-1'), 'HH24:MI:SS')"));
        assertEquals("00:00:12", scalar("SELECT TO_VARCHAR(TRY_CAST('12' AS TIME), 'HH24:MI:SS')"));
        assertEquals("10:00:00", scalar("SELECT TO_VARCHAR(TO_TIME(PARSE_JSON('\"36000\"')), 'HH24:MI:SS')"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM tt WHERE tm = '36000'"));
        assertEquals("00:00:12,10:00:00", scalar("SELECT LISTAGG(TO_VARCHAR(x, 'HH24:MI:SS'), ',') WITHIN GROUP"
            + " (ORDER BY x) FROM (SELECT tm AS x FROM tt UNION SELECT '12')"));
        engine.execute("INSERT INTO tt VALUES ('3600')");
        assertEquals("01:00:00,10:00:00",
            scalar("SELECT LISTAGG(TO_VARCHAR(tm, 'HH24:MI:SS'), ',') WITHIN GROUP (ORDER BY tm) FROM tt"));
    }
}
