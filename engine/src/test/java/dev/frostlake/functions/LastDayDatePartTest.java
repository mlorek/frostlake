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
 * LAST_DAY honours the optional date_part argument (year / quarter / month / week), not just the default
 * last-day-of-month. Previously the second argument was silently dropped.
 */
public class LastDayDatePartTest extends BaseDatabaseTest {

    private String lastDay(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void yearReturnsLastDayOfYear() {
        assertEquals("2015-12-31", lastDay("SELECT LAST_DAY('2015-05-08'::DATE, 'year')"));
    }

    @Test
    public void quarterReturnsLastDayOfQuarter() {
        assertEquals("2015-06-30", lastDay("SELECT LAST_DAY('2015-05-08'::DATE, 'quarter')"));
        assertEquals("2015-03-31", lastDay("SELECT LAST_DAY('2015-02-14'::DATE, 'quarter')"));
        assertEquals("2015-12-31", lastDay("SELECT LAST_DAY('2015-11-30'::DATE, 'quarter')"));
    }

    @Test
    public void weekReturnsSundayWithDefaultWeekStart() {
        // 2015-05-08 is a Friday; with weeks starting Monday, the week's last day is Sunday 2015-05-10.
        assertEquals("2015-05-10", lastDay("SELECT LAST_DAY('2015-05-08'::DATE, 'week')"));
    }

    @Test
    public void monthIsTheDefault() {
        assertEquals("2015-05-31", lastDay("SELECT LAST_DAY('2015-05-08'::DATE)"));
        assertEquals("2015-05-31", lastDay("SELECT LAST_DAY('2015-05-08'::DATE, 'month')"));
    }
}
