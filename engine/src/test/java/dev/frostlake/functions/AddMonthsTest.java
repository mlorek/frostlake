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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ADD_MONTHS preserves end-of-month (Snowflake): when the input is the last day of its month, the
 * result is the last day of the target month — so ADD_MONTHS('2016-02-29', 1) is 2016-03-31, not the
 * plain 2016-03-29. A non-month-end date is shifted normally.
 */
public class AddMonthsTest extends BaseDatabaseTest {

    private Object q(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void nonMonthEndShiftsNormally() {
        assertEquals(LocalDate.of(2016, 2, 15), q("SELECT ADD_MONTHS('2016-01-15', 1)"));
    }

    @Test
    public void endOfMonthGoesToEndOfLongerMonth() {
        assertEquals(LocalDate.of(2016, 3, 31), q("SELECT ADD_MONTHS('2016-02-29', 1)"));
    }

    @Test
    public void endOfMonthGoesToEndOfShorterMonth() {
        assertEquals(LocalDate.of(2016, 4, 30), q("SELECT ADD_MONTHS('2016-03-31', 1)"));
    }

    @Test
    public void jan31ToLeapFebruary() {
        assertEquals(LocalDate.of(2016, 2, 29), q("SELECT ADD_MONTHS('2016-01-31', 1)"));
    }

    @Test
    public void negativeMonthsPreserveEndOfMonth() {
        assertEquals(LocalDate.of(2016, 1, 31), q("SELECT ADD_MONTHS('2016-02-29', -1)"));
    }
}
