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

package dev.frostlake.jdbc;

import dev.frostlake.types.DateTimeType;
import dev.frostlake.values.TemporalText;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A DATE or TIMESTAMP before year 1 crosses the HTTP wire with its SIGNED proleptic year, and the driver reads
 * that back as the same year. The year of the era — what SQL text shows — would read year -1 as year 2.
 */
public class SignedYearWireTest {

    /** Year -1 crosses as -1, the year 0 as 0000, and an ordinary year as itself. */
    @Test
    public void aYearBeforeTheFirstIsSignedOnTheWire() {
        assertEquals("-1-01-15", TemporalText.wireText(LocalDate.of(-1, 1, 15), DateTimeType.DATE));
        assertEquals("0000-01-15", TemporalText.wireText(LocalDate.of(0, 1, 15), DateTimeType.DATE));
        assertEquals("2020-01-15", TemporalText.wireText(LocalDate.of(2020, 1, 15), DateTimeType.DATE));
        assertEquals("-1-01-15 10:00:00.000",
            TemporalText.wireText(LocalDateTime.of(-1, 1, 15, 10, 0, 0), DateTimeType.TIMESTAMP_NTZ));
    }

    /** The driver reads each back as the year it is. */
    @Test
    public void theDriverReadsTheSignedYearBack() {
        assertEquals(LocalDate.of(-1, 1, 15), JdbcMarshaling.parseDate("-1-01-15"));
        assertEquals(LocalDate.of(0, 1, 15), JdbcMarshaling.parseDate("0000-01-15"));
        assertEquals(LocalDate.of(2020, 1, 15), JdbcMarshaling.parseDate("2020-01-15"));
        assertEquals(LocalDate.of(-1, 1, 15), JdbcMarshaling.parseDate("-1-01-15 10:00:00.000"));
    }
}
