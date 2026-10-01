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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A cast to text out of a VARIANT that holds a date or time before the first year reads the VARIANT's own
 * spelling, the signed proleptic year, on every cast spelling — while the same value cast directly prints its
 * year of the era (live-verified).
 */
public class VariantTemporalTextCastTest extends BaseDatabaseTest {

    private static final String TS = "'2024-01-15 10:00:00'::TIMESTAMP_NTZ";

    private String value(final String expression) {
        for (final Row row : engine.executeQuery("SELECT " + expression).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    @Test
    public void everyCastSpellingReadsTheSignedYear() {
        assertEquals("-1-01-15 10:00:00.000", value("TO_VARIANT(DATEADD(year, -2025, " + TS + "))::VARCHAR"));
        assertEquals("-1-01-15 10:00:00.000", value("TO_VARIANT(DATEADD(year, -2025, " + TS + "))::STRING"));
        assertEquals("-1-01-15 10:00:00.000",
            value("CAST(TO_VARIANT(DATEADD(year, -2025, " + TS + ")) AS VARCHAR(40))"));
        assertEquals("-1-01-15 10:00:00.000",
            value("ARRAY_CONSTRUCT(DATEADD(year, -2025, " + TS + "))[0]::VARCHAR"));
    }

    @Test
    public void theYearZeroAndADateReadTheirVariantText() {
        assertEquals("0000-01-15 10:00:00.000", value("TO_VARIANT(DATEADD(year, -2024, " + TS + "))::VARCHAR"));
        assertEquals("-10001-01-15 10:00:00.000", value("TO_VARIANT(DATEADD(year, -12025, " + TS + "))::VARCHAR"));
        assertEquals("-1-01-15", value("TO_VARIANT(DATEADD(year, -2025, '2024-01-15'::DATE))::VARCHAR"));
    }

    @Test
    public void theValueCastDirectlyKeepsItsEraYear() {
        assertEquals("0002-01-15 10:00:00.000", value("DATEADD(year, -2025, " + TS + ")::VARCHAR"));
        assertEquals("2024-01-15 10:00:00.000", value("TO_VARIANT(" + TS + ")::VARCHAR"));
    }
}
