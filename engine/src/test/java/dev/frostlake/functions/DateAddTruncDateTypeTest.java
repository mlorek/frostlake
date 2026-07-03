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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * DATEADD / DATE_TRUNC on a DATE-only input keep the DATE type for a day-or-larger unit (returning a
 * {@link LocalDate} that prints as {@code 2023-02-28}, matching Snowflake) rather than widening to a
 * timestamp. A sub-day unit, or a timestamp input, still yields a timestamp.
 */
public class DateAddTruncDateTypeTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void dateAddDayOrLargerKeepsDate() {
        final Object r = scalar("SELECT DATEADD(month, 1, '2023-01-31'::DATE)");
        assertInstanceOf(LocalDate.class, r);
        assertEquals("2023-02-28", r.toString());
    }

    @Test
    public void dateTruncDayOrLargerKeepsDate() {
        final Object r = scalar("SELECT DATE_TRUNC('month', '2024-05-09'::DATE)");
        assertInstanceOf(LocalDate.class, r);
        assertEquals("2024-05-01", r.toString());
    }

    @Test
    public void dateColumnInputKeepsDate() {
        engine.execute("CREATE TABLE d (dt DATE)");
        engine.execute("INSERT INTO d VALUES ('2023-01-31')");
        final Object r = scalar("SELECT DATEADD(day, 1, dt) FROM d");
        assertInstanceOf(LocalDate.class, r);
        assertEquals("2023-02-01", r.toString());
    }

    @Test
    public void dateAddSubDayWidensToTimestamp() {
        final Object r = scalar("SELECT DATEADD(hour, 2, '2023-01-31'::DATE)");
        assertInstanceOf(LocalDateTime.class, r);
        assertEquals("2023-01-31T02:00", r.toString());
    }

    @Test
    public void timestampInputStaysTimestamp() {
        final Object r = scalar("SELECT DATEADD(month, 1, '2023-01-31 10:00:00'::TIMESTAMP)");
        assertInstanceOf(LocalDateTime.class, r);
        assertEquals("2023-02-28T10:00", r.toString());
    }
}
