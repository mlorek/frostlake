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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * DATE_FROM_PARTS, TIME_FROM_PARTS and TIMESTAMP_FROM_PARTS and its flavours carry a part out of its range into the
 * next one - day 100 is the 100th day, month 0 the December before, -3600 seconds an hour back - round a fraction
 * half away from zero and read a text part as its number; TIMESTAMP_FROM_PARTS also joins a date and a time, and
 * a BOOLEAN part is refused at the call (live-verified).
 */
public class FromPartsNormalisationTest extends MeasuredCellsTestSupport {

    @Test
    public void partsRollOver() {
        assertAnswer(
            "2010-04-10",
            "SELECT DATE_FROM_PARTS(2010, 1, 100)");
        assertAnswer(
            "2012-01-01",
            "SELECT DATE_FROM_PARTS(2010, 1 + 24, 1)");
        assertAnswer(
            "2003-12-01",
            "SELECT DATE_FROM_PARTS(2004, 0, 1)");
        assertAnswer(
            "2004-01-31",
            "SELECT DATE_FROM_PARTS(2004, 2, 0)");
        assertAnswer(
            "2003-10-30",
            "SELECT DATE_FROM_PARTS(2004, -1, -1)");
        assertAnswer(
            "2005-01-31",
            "SELECT DATE_FROM_PARTS(2004, 13, 31)");
        assertAnswer(
            "2005-12-30",
            "SELECT DATE_FROM_PARTS(2004, 1, 365 * 2)");
        assertAnswer(
            "2004-02-01",
            "SELECT DATE_FROM_PARTS(2004, 1.6, 1.4)");
        assertAnswer(
            "+10000-01-01",
            "SELECT DATE_FROM_PARTS(10000, 1, 1)");
        assertAnswer(
            "+85337-04-01",
            "SELECT DATE_FROM_PARTS(2004, 1000000, 1)");
        assertAnswer(
            "2004-02-03",
            "SELECT DATE_FROM_PARTS('2004', '2', '3')");
        assertAnswer(
            "13:00|01:00|23:59:59",
            "SELECT TIME_FROM_PARTS(12, 60, 0), TIME_FROM_PARTS(25, 0, 0), TIME_FROM_PARTS(0, 0, -1)");
        assertAnswer(
            "2024-01-02T00:00",
            "SELECT TIMESTAMP_FROM_PARTS(2024, 1, 1, 24, 0, 0)");
        assertAnswer(
            "2013-04-05T11:00",
            "SELECT TIMESTAMP_FROM_PARTS(2013, 4, 5, 12, 0, -3600)");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS(2013, 4, 5, 12, 0, 0, -1)");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS(2013, 4, 5, 12, 0, 0, 1000000000)");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS(2013, 13, 32, 25, 61, 61)");
        assertAnswer(
            "2012-11-30T00:00",
            "SELECT TIMESTAMP_NTZ_FROM_PARTS(2013, 0, 0, 0, 0, 0)");
        answer(
            "SELECT TIMESTAMP_TZ_FROM_PARTS(2013, 4, 5, 24, 0, 0, 0, 'America/Los_Angeles')::VARCHAR");
        assertAnswer(
            "2013-04-05T11:00",
            "SELECT TIMESTAMP_LTZ_FROM_PARTS(2013, 4, 5, 12, 0, -3600)::TIMESTAMP_NTZ");
        assertAnswer(
            "2024-01-01T10:00",
            "SELECT TIMESTAMP_FROM_PARTS('2024-01-01'::DATE, '10:00:00'::TIME)");
        assertAnswer(
            "2013-04-05T12:00",
            "SELECT TIMESTAMP_NTZ_FROM_PARTS(TO_DATE('2013-04-05'), TO_TIME('12:00:00'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_LTZ_FROM_PARTS(CAST('2013-04-05' AS DATE), CAST('12:00:00' AS TIME(9)))], expected 6, "
                + "got 2",
            "SELECT TIMESTAMP_LTZ_FROM_PARTS(TO_DATE('2013-04-05'), TO_TIME('12:00:00'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_TZ_FROM_PARTS(CAST('2013-04-05' AS DATE), CAST('12:00:00' AS TIME(9)))], expected 6, "
                + "got 2",
            "SELECT TIMESTAMP_TZ_FROM_PARTS(TO_DATE('2013-04-05'), TO_TIME('12:00:00'))");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS(TO_DATE('2013-04-05'), TO_TIME('12:00:00.123456789'))");
        assertAnswer(
            "null|null",
            "SELECT TIMESTAMP_FROM_PARTS(TO_DATE('2013-04-05'), NULL::TIME), TIMESTAMP_FROM_PARTS(NULL::DATE, "
                + "TO_TIME('12:00:00'))");
        assertAnswer(
            "2013-04-05T12:00",
            "SELECT TIMESTAMP_FROM_PARTS('2013-04-05', '12:00:00')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TIMESTAMP_NTZ_FROM_DATE_TIME': (NUMBER(4,0), NUMBER(1,0))",
            "SELECT TIMESTAMP_FROM_PARTS(2013, 4)");
        assertAnswer(
            "TIMESTAMP_NTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(TO_DATE('2013-04-05'), TO_TIME('12:00:00')))");
        assertAnswer(
            "2004-03-01",
            "SELECT DATE_FROM_PARTS(2004, 2, 29.5)");
    }

    @Test
    public void partsAreReadAsNumbers() {
        assertAnswer(
            "2004-02-01T02:00",
            "SELECT TIMESTAMP_FROM_PARTS(2004, 1.6, 1.4, 1.5, 0, 0)");
        assertAnswer(
            "02:00",
            "SELECT TIME_FROM_PARTS(1.5, 0.4, 0)");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS('2004', '2', '3', '4', '5', '6')");
        assertAnswer(
            "01:02:03",
            "SELECT TIME_FROM_PARTS('1', '2', '3')");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "SELECT DATE_FROM_PARTS('2004', 'x', '3')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_FROM_PARTS': (BOOLEAN, NUMBER(1,0), NUMBER(1,0))",
            "SELECT DATE_FROM_PARTS(TRUE, 1, 1)");
    }
}
