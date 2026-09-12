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
import dev.frostlake.types.DateTimeType;
import dev.frostlake.values.ClientValueText;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The YEAR in every text of a date or a timestamp is the year of the era, four digits at least and
 * never signed: a year past 9999 prints its digits — never java.time's {@code +20201} — and the second
 * year before the first prints {@code 0002}. Every cell is live-verified.
 */
public class DateYearTextTest extends BaseDatabaseTest {

    private String text(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aYearPast9999PrintsItsDigits() {
        final String date = "TO_DATE('20201-01-15', 'YYYY-MM-DD')";
        assertEquals("20201-01-15", text("SELECT " + date + "::VARCHAR"));
        assertEquals("20201-01-15", text("SELECT TO_VARCHAR(" + date + ")"));
        assertEquals("20201-01-15", text("SELECT TO_CHAR(" + date + ", 'YYYY-MM-DD')"));
        assertEquals("20201", text("SELECT TO_CHAR(" + date + ", 'YYYY')"));
        assertEquals("20201-01-15", text("SELECT " + date + " || ''"));
        assertEquals("\"20201-01-15\"", text("SELECT TO_JSON(TO_VARIANT(" + date + "))"));
        assertEquals("20201", text("SELECT YEAR(" + date + ")"));
        assertEquals("12020-01-01", text("SELECT DATEADD(year, 10000, TO_DATE('2020-01-01'))::VARCHAR"));
        engine.execute("CREATE OR REPLACE TABLE dt (d DATE)");
        engine.execute("INSERT INTO dt SELECT " + date);
        assertEquals("20201-01-15", text("SELECT d::VARCHAR FROM dt"));
        assertEquals("20201-01-15", text("SELECT TO_VARCHAR(d) FROM dt"));
    }

    @Test
    public void aTimestampPast9999PrintsItsDigits() {
        assertEquals("20201-01-15 10:00:00.000",
            text("SELECT TO_TIMESTAMP('20201-01-15 10:00:00', 'YYYY-MM-DD HH24:MI:SS')::VARCHAR"));
        assertEquals("20201-01-15 10:00:00",
            text("SELECT TO_CHAR(TO_TIMESTAMP_NTZ('20201-01-15 10:00:00', 'YYYY-MM-DD HH24:MI:SS'), "
                + "'YYYY-MM-DD HH24:MI:SS')"));
        assertEquals("20201-01-15 10:00:00.000 +0100",
            text("SELECT TO_VARCHAR(TO_TIMESTAMP_TZ('20201-01-15 10:00:00 +01:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'))"));
    }

    @Test
    public void aYearBeforeTheFirstPrintsTheEraYear() {
        assertEquals("0002-01-01", text("SELECT DATEADD(year, -2021, TO_DATE('2020-01-01'))::VARCHAR"));
        assertEquals("0001-01-01", text("SELECT DATEADD(year, -2020, TO_DATE('2020-01-01'))::VARCHAR"));
        assertEquals("0002-01-01", text("SELECT DATEADD(year, -2021, TO_DATE('2020-01-01')) || ''"));
        assertEquals("0002-01-01", text("SELECT TO_CHAR(DATEADD(year, -2021, TO_DATE('2020-01-01')), 'YYYY-MM-DD')"));
        assertEquals("0002-01-01 10:00:00.000",
            text("SELECT DATEADD(year, -2021, TO_TIMESTAMP_NTZ('2020-01-01 10:00:00'))::VARCHAR"));
        assertEquals("0999-01-01", text("SELECT TO_DATE('0999-01-01')::VARCHAR"));
    }

    /** The text a driver hands back for a cell, which the engine's client renderer spells. */
    @Test
    public void theClientTextFollowsTheSameYear() {
        assertEquals("20201-01-15", ClientValueText.render(LocalDate.of(20201, 1, 15), DateTimeType.DATE));
        assertEquals("20201-01-15 10:00:00.000",
            ClientValueText.render(LocalDateTime.of(20201, 1, 15, 10, 0), DateTimeType.TIMESTAMP_NTZ));
        assertEquals("0002-01-01", ClientValueText.render(LocalDate.of(-1, 1, 1), DateTimeType.DATE));
        assertEquals("0999-01-01", ClientValueText.render(LocalDate.of(999, 1, 1), DateTimeType.DATE));
    }
}
