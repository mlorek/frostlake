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
 * A date or a timestamp before the first year, held in a VARIANT, keeps its proleptic year in the
 * VARIANT's text — {@code -1} for the second year BC, {@code 0000} for the year 0 — where the ::VARCHAR
 * of the same value stepped there by whole years prints the year of the era (live-verified).
 */
public class VariantPreYearOneTextTest extends BaseDatabaseTest {

    private static final String TS = "'2024-01-15 10:00:00'::TIMESTAMP_NTZ";

    private String text(final String expression) {
        return String.valueOf(engine.executeQuery("SELECT " + expression).getRows().get(0).getValue(0));
    }

    @Test
    public void aTimestampKeepsItsProlepticYear() {
        assertEquals("\"-1-01-15 10:00:00.000\"", text("TO_JSON(TO_VARIANT(DATEADD(year, -2025, " + TS + ")))"));
        assertEquals("\"0000-01-15 10:00:00.000\"", text("TO_JSON(TO_VARIANT(DATEADD(year, -2024, " + TS + ")))"));
        assertEquals("\"-10001-01-15 10:00:00.000\"", text("TO_JSON(TO_VARIANT(DATEADD(year, -12025, " + TS + ")))"));
        assertEquals("\"-1-01-15 10:00:00.000 +0200\"",
            text("TO_JSON(TO_VARIANT(DATEADD(year, -2025, '2024-01-15 10:00:00 +0200'::TIMESTAMP_TZ)))"));
    }

    @Test
    public void aDateInTheYearZeroIsPadded() {
        assertEquals("\"0000-01-15\"", text("TO_JSON(TO_VARIANT(DATEADD(year, -2024, '2024-01-15'::DATE)))"));
        assertEquals("\"-1-01-15\"", text("TO_JSON(TO_VARIANT(DATEADD(year, -2025, '2024-01-15'::DATE)))"));
    }

    @Test
    public void containersHoldTheSameText() {
        assertEquals("{\"t\":\"0000-01-15 10:00:00.000\"}",
            text("OBJECT_CONSTRUCT('t', DATEADD(year, -2024, " + TS + "))::VARCHAR"));
        assertEquals("[\"-1-01-15 10:00:00.000\",\"0000-01-15\"]",
            text("ARRAY_CONSTRUCT(DATEADD(year, -2025, " + TS + "), DATEADD(year, -2024, '2024-01-15'::DATE))::VARCHAR"));
    }

    @Test
    public void theTextOfTheValueItselfKeepsTheYearOfTheEra() {
        assertEquals("0002-01-15 10:00:00.000", text("DATEADD(year, -2025, " + TS + ")::VARCHAR"));
        assertEquals("0001-01-15", text("DATEADD(year, -2024, '2024-01-15'::DATE)::VARCHAR"));
    }
}
