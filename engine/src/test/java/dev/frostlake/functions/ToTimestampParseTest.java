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
 * TO_TIMESTAMP / TO_TIMESTAMP_NTZ parsing: an explicit Snowflake format is honored (a date-only
 * format defaults to midnight), and a numeric argument is a Unix epoch whose unit is chosen by
 * magnitude (seconds vs milliseconds vs …). Previously the format was ignored and only epoch seconds
 * were accepted.
 */
public class ToTimestampParseTest extends BaseDatabaseTest {

    private String ts(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0).toString();
    }

    @Test
    public void epochSeconds() {
        assertEquals("2021-09-15T13:19:59", ts("SELECT TO_TIMESTAMP(1631711999)"));
    }

    @Test
    public void epochMillisecondsByMagnitude() {
        // 1631711999000 (ms) resolves to the same instant as 1631711999 (s).
        assertEquals("2021-09-15T13:19:59", ts("SELECT TO_TIMESTAMP_NTZ(1631711999000)"));
    }

    @Test
    public void explicitFormatWithTime() {
        assertEquals("2021-01-15T13:45:30",
            ts("SELECT TO_TIMESTAMP('15/01/2021 13:45:30', 'DD/MM/YYYY HH24:MI:SS')"));
    }

    @Test
    public void dateOnlyFormatDefaultsToMidnight() {
        assertEquals("2021-01-15T00:00", ts("SELECT TO_TIMESTAMP('01/15/2021', 'MM/DD/YYYY')"));
    }

    @Test
    public void isoStringWithoutFormat() {
        assertEquals("2024-01-15T10:30:45", ts("SELECT TO_TIMESTAMP_NTZ('2024-01-15 10:30:45')"));
    }
}
