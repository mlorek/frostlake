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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DATE_PART and EXTRACT of {@code epoch_second} declare NUMBER(18,0) over a DATE and every TIMESTAMP flavour, while
 * the epoch in milli-, micro- and nanoseconds declares NUMBER(38,0) (live-verified). The values were already right;
 * a CTAS, a view or DESCRIBE RESULT over the part declared the wider type.
 */
public class EpochSecondWidthTest extends BaseDatabaseTest {

    private String typeOf(final String expression) {
        final String typed = String.valueOf(engine.executeQuery("SELECT SYSTEM$TYPEOF(" + expression + ")")
            .getRows().get(0).getValue(0));
        return typed.substring(0, typed.indexOf('['));
    }

    @Test
    public void theEpochInSecondsIsEighteenDigits() {
        assertEquals("NUMBER(18,0)", typeOf("EXTRACT(epoch_second FROM '2024-01-01 10:00:00'::TIMESTAMP)"));
        assertEquals("NUMBER(18,0)", typeOf("DATE_PART(epoch_second, '2024-01-01 10:00:00'::TIMESTAMP)"));
        assertEquals("NUMBER(18,0)", typeOf("DATE_PART(epoch_second, '2024-01-01'::DATE)"));
        assertEquals("NUMBER(18,0)", typeOf("DATE_PART(epoch_second, '2024-01-01 10:00:00 +02:00'::TIMESTAMP_TZ)"));
        assertEquals("NUMBER(18,0)", typeOf("DATE_PART(epoch_second, '2024-01-01 10:00:00'::TIMESTAMP_LTZ)"));
    }

    @Test
    public void everySpellingOfThePartIsEighteenDigits() {
        assertEquals("NUMBER(18,0)", typeOf("DATE_PART(epoch, '2024-01-01 10:00:00'::TIMESTAMP)"));
        assertEquals("NUMBER(18,0)", typeOf("DATE_PART('EPOCH_SECOND', '2024-01-01'::DATE)"));
        assertEquals("NUMBER(18,0)", typeOf("DATE_PART(epoch_seconds, '2024-01-01'::DATE)"));
    }

    @Test
    public void theFinerEpochPartsStayTheWidestNumber() {
        assertEquals("NUMBER(38,0)", typeOf("DATE_PART(epoch_millisecond, '2024-01-01 10:00:00'::TIMESTAMP)"));
        assertEquals("NUMBER(38,0)", typeOf("DATE_PART(epoch_microsecond, '2024-01-01 10:00:00'::TIMESTAMP)"));
        assertEquals("NUMBER(38,0)", typeOf("DATE_PART(epoch_nanosecond, '2024-01-01 10:00:00'::TIMESTAMP)"));
    }

    @Test
    public void theValuesAreUnchanged() {
        assertEquals("1704103200", String.valueOf(engine.executeQuery(
            "SELECT EXTRACT(epoch_second FROM '2024-01-01 10:00:00'::TIMESTAMP)").getRows().get(0).getValue(0)));
        assertEquals("1704103200123456789", String.valueOf(engine.executeQuery(
            "SELECT DATE_PART(epoch_nanosecond, '2024-01-01 10:00:00.123456789'::TIMESTAMP)").getRows().get(0)
            .getValue(0)));
    }

    @Test
    public void aTableBuiltOverThePartDeclaresItsWidth() {
        engine.execute("CREATE TABLE ep AS SELECT DATE_PART(epoch_second, '2024-01-01'::DATE) s,"
            + " EXTRACT(epoch FROM '2024-01-01 10:00:00'::TIMESTAMP_NTZ) t,"
            + " DATE_PART(epoch_millisecond, '2024-01-01'::TIMESTAMP_NTZ) ms");
        assertEquals("NUMBER(18,0)", describeCell("ep", "S", "type"));
        assertEquals("NUMBER(18,0)", describeCell("ep", "T", "type"));
        assertEquals("NUMBER(38,0)", describeCell("ep", "MS", "type"));
    }
}
