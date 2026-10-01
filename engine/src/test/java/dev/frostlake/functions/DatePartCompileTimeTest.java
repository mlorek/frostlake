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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * DATE_PART's and EXTRACT's part words, and LAST_DAY's subject, judged while the statement compiles
 * (live-verified): an unknown part is refused over an empty table too, in each function's own sentence with
 * the word as written; LAST_DAY refuses a TIME before any unit; and the spellings live accepts that Frostlake
 * refused — WEEKDAY, the _ISO forms, YEARDAY / DY, NSECOND(S) — read their parts.
 */
public class DatePartCompileTimeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("""
            CREATE TABLE wt (d DATE, tm TIME, tm3 TIME(3), ts TIMESTAMP_NTZ, ltz TIMESTAMP_LTZ, tz TIMESTAMP_TZ)""");
        engine.execute("""
            INSERT INTO wt VALUES ('2024-01-15', '10:30:00', '10:30:00', '2024-01-15 10:30:00', '2024-01-15 10:30:00',
                '2024-01-15 10:30:00 +02:00')""");
        engine.execute("CREATE TABLE wt0 LIKE wt");
    }

    @Override
    protected void teardownTest() {
        engine.execute("ALTER SESSION UNSET TIMEZONE");
    }

    /** The one row's cells, as text. */
    private List<String> row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> cells = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            cells.add(String.valueOf(rs.getRows().get(0).getValue(i)));
        }
        return cells;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String invalid(final String word, final String function) {
        return "SQL compilation error:\ninvalid value [" + word + "] for parameter '" + function + " date/time part'";
    }

    @Test
    public void anUnknownPartIsRefusedOverNoRows() {
        assertEquals(invalid("WKS", "DATE_PART"), refusal("SELECT DATE_PART(wks, d) FROM wt0"));
        assertEquals(invalid("WKS", "DATE_PART"), refusal("SELECT DATE_PART(Wks, d) FROM wt0"));
        assertEquals(invalid("xyz", "DATE_PART"), refusal("SELECT DATE_PART('xyz', d) FROM wt0"));
        assertEquals(invalid("wks", "DATE_PART"), refusal("SELECT DATE_PART(\"wks\", d) FROM wt0"));
        assertEquals(invalid("EPOCH_XYZ", "DATE_PART"), refusal("SELECT DATE_PART(epoch_xyz, ts) FROM wt0"));
        assertEquals(invalid("WKS", "DATE_PART"), refusal("SELECT DATE_PART(wks, tm) FROM wt0"));
        assertEquals(invalid("wks", "EXTRACT"), refusal("SELECT EXTRACT(wks FROM d) FROM wt0"));
        assertEquals(invalid("Wks", "EXTRACT"), refusal("SELECT EXTRACT(Wks FROM d) FROM wt0"));
        assertEquals(invalid("nanosecondz", "EXTRACT"), refusal("SELECT EXTRACT(nanosecondz FROM ts) FROM wt0"));
        assertEquals(0, engine.executeQuery("SELECT DATE_PART(day, d), DATE_PART(dayofweek_iso, d) FROM wt0").getRowCount());
    }

    @Test
    public void lastDayRefusesATimeFirst() {
        assertEquals("SQL compilation error:\nFunction LAST_DAY does not support TIME(9) argument type",
            refusal("SELECT LAST_DAY(tm, w) FROM wt"));
        assertEquals("SQL compilation error:\nFunction LAST_DAY does not support TIME(9) argument type",
            refusal("SELECT LAST_DAY(tm) FROM wt0"));
        assertEquals("SQL compilation error:\nFunction LAST_DAY does not support TIME(3) argument type",
            refusal("SELECT LAST_DAY(tm3) FROM wt0"));
        assertEquals("SQL compilation error:\nFunction LAST_DAY does not support TIME(9) argument type",
            refusal("SELECT LAST_DAY(tm, wks) FROM wt0"));
        assertEquals("SQL compilation error:\nFunction LAST_DAY does not support TIME(9) argument type",
            refusal("SELECT LAST_DAY('10:30:00'::TIME) FROM wt0"));
        assertEquals(List.of("2024-01-31", "2024-01-31", "2024-01-31", "DATE[SB4]", "DATE[SB4]"),
            row("""
                SELECT LAST_DAY(ltz)::VARCHAR, LAST_DAY(tz)::VARCHAR, LAST_DAY(ts)::VARCHAR, SYSTEM$TYPEOF(LAST_DAY(ltz)),
                    SYSTEM$TYPEOF(LAST_DAY(tz))
                FROM wt"""));
    }

    @Test
    public void theSpellingsLiveAcceptsReadTheirParts() {
        assertEquals(List.of("1", "1", "1", "1", "1", "15", "15", "3", "2024", "0", "0"),
            row("""
                SELECT DATE_PART(weekday, ltz), DATE_PART(weekday_iso, ltz), DATE_PART(dow_iso, ltz),
                    DATE_PART(dw_iso, ltz), DATE_PART(dayofweek_iso, ltz), DATE_PART(yearday, ltz), DATE_PART(dy, ltz),
                    DATE_PART(week_iso, ltz), DATE_PART(yearofweek_iso, ltz), DATE_PART(nsecond, ltz),
                    DATE_PART(nseconds, ltz)
                FROM wt"""));
        assertEquals(List.of("1", "1", "15", "3", "2024", "0"),
            row("""
                SELECT EXTRACT(weekday FROM ltz), EXTRACT(dow_iso FROM ltz), EXTRACT(yearday FROM ltz),
                    EXTRACT(week_iso FROM ltz), EXTRACT(yearofweek_iso FROM ltz), EXTRACT(nseconds FROM ltz)
                FROM wt"""));
    }
}
