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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A TIMESTAMP_LTZ or TIMESTAMP_TZ cell read as it is carries its offset on both backends: an LTZ the session's offset
 * for that instant and a TZ its own, so a test may compare the cell itself rather than its text.
 */
public class ZonedTimestampCellTest extends MeasuredCellsTestSupport {

    @Test
    public void aLocalZoneCellCarriesTheSessionOffset() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertCells(new String[][] {
            {"SELECT '2024-01-01 00:00:00'::TIMESTAMP_LTZ", "2024-01-01T00:00-08:00"},
            {"SELECT '2024-07-01 12:30:15.123456789'::TIMESTAMP_LTZ", "2024-07-01T12:30:15.123456789-07:00"},
            {"SELECT '2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ::TIMESTAMP_LTZ", "2023-12-31T15:00-08:00"},
        });
    }

    @Test
    public void aWrittenOffsetCellKeepsItsOwnOffset() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertCells(new String[][] {
            {"SELECT '2024-01-01 00:00:00 +01:30'::TIMESTAMP_TZ", "2024-01-01T00:00+01:30"},
            {"SELECT '2024-01-01 00:00:00 +00:00'::TIMESTAMP_TZ", "2024-01-01T00:00Z"},
            {"SELECT '2024-01-01 00:00:00'::TIMESTAMP_TZ", "2024-01-01T00:00-08:00"},
        });
    }

    @Test
    public void aZonedCellOfASetOperation() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertCells(new String[][] {
            {"SELECT x FROM (SELECT '2024-01-01 00:00:00'::TIMESTAMP_LTZ AS x EXCEPT SELECT '2024-01-02'::DATE)",
                "2024-01-01T00:00Z"},
        });
    }
}
