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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code w} is a WEEK wherever a date function reads a unit — DATEADD, DATEDIFF and their TIME and
 * TIMESTAMP spellings, DATE_TRUNC, TRUNC, DATE_PART, EXTRACT, LAST_DAY, TIME_SLICE and INTERVAL — while
 * the week components {@code wy}, {@code woy} and {@code weekofyear} are read only where a component is.
 * A word outside a function's vocabulary is refused while the statement compiles, under the name the
 * call was written with. Live-verified.
 */
public class WeekUnitAliasTest extends BaseDatabaseTest {

    private void createTable() {
        engine.execute("CREATE OR REPLACE TABLE wt (d DATE, ts TIMESTAMP_NTZ, tm TIME)");
        engine.execute("CREATE OR REPLACE TABLE wt0 (d DATE, ts TIMESTAMP_NTZ, tm TIME)");
        engine.execute("INSERT INTO wt VALUES ('2024-01-17', '2024-01-17 10:30:00', '10:30:00')");
    }

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

    private static String notAComponent(final String word, final String function) {
        return "SQL compilation error: ['" + word + "'] is not a valid date/time component for function "
            + function + ".";
    }

    /** {@code w}, in any case and quoted or not, is a week in every function that reads a unit. */
    @Test
    public void wIsAWeekEverywhere() {
        createTable();
        assertEquals("2024-01-24", scalar("SELECT DATEADD(w, 1, d) FROM wt"));
        assertEquals("2024-01-24", scalar("SELECT DATEADD('w', 1, d) FROM wt"));
        assertEquals("2024-01-24 10:30:00.000", scalar("SELECT TO_VARCHAR(TIMEADD(W, 1, ts)) FROM wt"));
        assertEquals("2024-01-24 10:30:00.000", scalar("SELECT TO_VARCHAR(TIMESTAMPADD(w, 1, ts)) FROM wt"));
        assertEquals("2", scalar("SELECT DATEDIFF(w, d, d + 14) FROM wt"));
        assertEquals("2", scalar("SELECT TIMESTAMPDIFF(w, d, d + 14) FROM wt"));
        assertEquals("2", scalar("SELECT TIMEDIFF(w, d, d + 14) FROM wt"));
        assertEquals("2024-01-15", scalar("SELECT DATE_TRUNC(w, d) FROM wt"));
        assertEquals("2024-01-15", scalar("SELECT DATE_TRUNC('W', d) FROM wt"));
        assertEquals("2024-01-15", scalar("SELECT TRUNC(d, 'w') FROM wt"));
        assertEquals("3", scalar("SELECT DATE_PART(w, d) FROM wt"));
        assertEquals("3", scalar("SELECT EXTRACT(w FROM d) FROM wt"));
        assertEquals("2024-01-21", scalar("SELECT LAST_DAY(d, w) FROM wt"));
        assertEquals("2024-01-31", scalar("SELECT d + INTERVAL '2 w' FROM wt"));
    }

    /** TIME_SLICE reads the unit aliases, from a second up to a year, and no fraction of a second. */
    @Test
    public void timeSliceReadsTheAliases() {
        createTable();
        assertEquals("2024-01-15", scalar("SELECT TIME_SLICE(d, 1, 'W') FROM wt"));
        assertEquals("2024-01-15", scalar("SELECT TIME_SLICE(d, 1, 'WK') FROM wt"));
        assertEquals("2024-01-01 00:00:00.000", scalar("SELECT TO_VARCHAR(TIME_SLICE(ts, 1, 'YY')) FROM wt"));
        assertEquals("2024-01-01 00:00:00.000", scalar("SELECT TO_VARCHAR(TIME_SLICE(ts, 1, 'MM')) FROM wt"));
        assertEquals("2024-01-17 10:00:00.000", scalar("SELECT TO_VARCHAR(TIME_SLICE(ts, 1, 'HH')) FROM wt"));
        assertEquals("2024-01-17 10:30:00.000", scalar("SELECT TO_VARCHAR(TIME_SLICE(ts, 1, 'MI')) FROM wt"));
        assertEquals(notAComponent("MS", "TIME_SLICE"), refusal("SELECT TIME_SLICE(ts, 1, 'MS') FROM wt"));
        assertEquals(notAComponent("WEEKLY", "TIME_SLICE"), refusal("SELECT TIME_SLICE(ts, 1, 'WEEKLY') FROM wt"));
        assertEquals(notAComponent("WKS", "TIME_SLICE"), refusal("SELECT TIME_SLICE(ts, 1, 'WKS') FROM wt0"));
    }

    /** The week components are read where a component is, and refused where an interval is. */
    @Test
    public void theWeekComponentsAreReadOnlyAsComponents() {
        createTable();
        assertEquals("3", scalar("SELECT DATE_PART(wy, d) FROM wt"));
        assertEquals("3", scalar("SELECT EXTRACT(woy FROM d) FROM wt"));
        assertEquals("2024-01-25", scalar("SELECT d + INTERVAL '1 wy, 1 day' FROM wt"));
        assertEquals(notAComponent("WY", "DATEADD"), refusal("SELECT DATEADD(wy, 1, d) FROM wt"));
        assertEquals(notAComponent("WY", "DATEDIFF"), refusal("SELECT DATEDIFF(wy, d, d) FROM wt"));
        assertEquals(notAComponent("WY", "DATE_TRUNC"), refusal("SELECT DATE_TRUNC(wy, d) FROM wt"));
        assertEquals(notAComponent("WY", "LAST_DAY"), refusal("SELECT LAST_DAY(d, wy) FROM wt"));
        assertEquals(notAComponent("WOY", "LAST_DAY"), refusal("SELECT LAST_DAY(d, woy) FROM wt"));
        assertEquals(notAComponent("WEEKOFYEAR", "LAST_DAY"), refusal("SELECT LAST_DAY(d, weekofyear) FROM wt"));
        assertEquals(notAComponent("wy", "TRUNC"), refusal("SELECT TRUNC(d, 'wy') FROM wt"));
    }

    /** A word outside the vocabulary is refused as the statement compiles, under the written name. */
    @Test
    public void anUnknownUnitIsRefusedAsTheStatementCompiles() {
        createTable();
        assertEquals(notAComponent("WY", "DATEADD"), refusal("SELECT DATEADD(wy, 1, d) FROM wt0"));
        assertEquals(notAComponent("WY", "TIMEADD"), refusal("SELECT TIMEADD(wy, 1, ts) FROM wt0"));
        assertEquals(notAComponent("WO", "TIMESTAMPADD"), refusal("SELECT TIMESTAMPADD(wo, 1, d) FROM wt0"));
        assertEquals(notAComponent("WKS", "TIMESTAMPDIFF"), refusal("SELECT TIMESTAMPDIFF(wks, d, d) FROM wt0"));
        assertEquals(notAComponent("WW", "TIMEDIFF"), refusal("SELECT TIMEDIFF(ww, d, d) FROM wt0"));
        assertEquals(notAComponent("wks", "DATEDIFF"), refusal("SELECT DATEDIFF('wks', d, d) FROM wt0"));
        assertEquals(notAComponent("ZZ", "DATE_TRUNC"), refusal("SELECT DATE_TRUNC(zz, d) FROM wt0"));
        assertEquals(notAComponent("zz", "TRUNC"), refusal("SELECT TRUNC(d, 'zz') FROM wt0"));
        assertEquals(notAComponent("DAY", "LAST_DAY"), refusal("SELECT LAST_DAY(d, day) FROM wt0"));
        assertEquals(notAComponent("WY", "TIMEADD"), refusal("SELECT TIMEADD(wy, 1, '2024-01-17'::DATE)"));
    }

    /** Over a TIME, a week is no component: the shift quotes the unit as written, the others resolve it. */
    @Test
    public void aWeekOfATimeIsRefused() {
        createTable();
        assertEquals("SQL compilation error: ['W'] is not a valid date/time component for function DATEADD"
            + " and type TIME(9).", refusal("SELECT DATEADD(w, 1, tm) FROM wt"));
        assertEquals("SQL compilation error: ['WK'] is not a valid date/time component for function TIMEADD"
            + " and type TIME(9).", refusal("SELECT TIMEADD(wk, 1, tm) FROM wt"));
        assertEquals("SQL compilation error: [WEEK] is not a valid date/time component for function DATEDIFF"
            + " and type TIME.", refusal("SELECT DATEDIFF(w, tm, tm) FROM wt"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function DATEDIFF"
            + " and type TIME.", refusal("SELECT DATEDIFF(dd, tm, tm) FROM wt"));
        assertEquals("SQL compilation error: [WEEK] is not a valid date/time component for function TIMEDIFF"
            + " and type TIME.", refusal("SELECT TIMEDIFF(w, tm, tm) FROM wt"));
        assertEquals("SQL compilation error: [WEEK] is not a valid date/time component for function DATE_TRUNC"
            + " and type TIME.", refusal("SELECT DATE_TRUNC(w, tm) FROM wt"));
        assertEquals("SQL compilation error: [WEEK] is not a valid date/time component for function TRUNC"
            + " and type TIME.", refusal("SELECT TRUNC(tm, 'w') FROM wt"));
        assertEquals("SQL compilation error:\ninvalid value [W] for parameter 'DATE_PART date/time part'",
            refusal("SELECT DATE_PART(w, tm) FROM wt"));
    }
}
