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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MONTHS_BETWEEN uses Snowflake's formula (y1-y2)*12 + (m1-m2) + (day1-day2)/31, with a whole-number
 * result when the day numbers match or both dates are their month's last day. The previous impl
 * double-counted the day difference (adding a day fraction on top of a Period that already accounted
 * for it), so a partial-month case returned the wrong magnitude. Arguments must be temporal:
 * VARCHAR inputs — including literals — are rejected at compile time, so every date is cast ::DATE.
 */
public class MonthsBetweenTest extends BaseDatabaseTest {

    private double months(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    @Test
    public void partialMonthUses31DayDayFraction() {
        // The result carries Snowflake's NUMBER(27,6) scale, so the 31-day fraction is rounded to
        // six decimals (live-verified: 1.580645 for 2021-04-15 / 2021-02-28, never a raw double).
        assertEquals(1.838710,
            months("SELECT MONTHS_BETWEEN('2021-03-10'::DATE, '2021-01-15'::DATE)"), 1e-9);
    }

    @Test
    public void bothMonthEndIsWhole() {
        assertEquals(1.0, months("SELECT MONTHS_BETWEEN('2021-03-31'::DATE, '2021-02-28'::DATE)"), 1e-9);
    }

    @Test
    public void sameDayOfMonthIsWhole() {
        assertEquals(2.0, months("SELECT MONTHS_BETWEEN('2024-03-15'::DATE, '2024-01-15'::DATE)"), 1e-9);
    }

    @Test
    public void reversedArgumentsNegateTheResult() {
        assertEquals(-1.838710,
            months("SELECT MONTHS_BETWEEN('2021-01-15'::DATE, '2021-03-10'::DATE)"), 1e-9);
    }

    @Test
    public void wholeMonthsSpanningYears() {
        assertEquals(14.0, months("SELECT MONTHS_BETWEEN('2025-03-15'::DATE, '2024-01-15'::DATE)"), 1e-9);
    }

    @Test
    public void varcharArgumentsAreRejected() {
        final RuntimeException rejected = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                months("SELECT MONTHS_BETWEEN('2021-03-10', '2021-01-15')");
            }
        });
        assertTrue(rejected.getMessage().contains("Function EXTRACT does not support VARCHAR(10) argument type"),
            "unexpected: " + rejected.getMessage());
    }
}
