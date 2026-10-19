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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A TIME plus or minus an interval of hours, minutes, seconds or their fractions moves around the clock: 23:30 plus
 * an hour is 00:30 and 25 hours are one. A day or longer is no part of a time, refused while the statement
 * compiles, naming the operator for the last part and DATEADD for an earlier one; an interval before a TIME is
 * refused by the operator's argument types (live-verified).
 */
public class TimeIntervalArithmeticTest extends MeasuredCellsTestSupport {

    @Test
    public void timeMovesAroundTheClock() {
        try {
            assertAnswer(
                "07:33:29",
                "SELECT TO_TIME('04:15:29') + INTERVAL '3 hours, 18 minutes'");
            assertAnswer(
                "03:15:29",
                "SELECT TO_TIME('04:15:29') - INTERVAL '1 hour'");
            assertAnswer(
                "00:30",
                "SELECT TO_TIME('23:30:00') + INTERVAL '1 hour'");
            assertAnswer(
                "23:30",
                "SELECT TO_TIME('00:30:00') - INTERVAL '1 hour'");
            assertAnswer(
                "REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function + and "
                    + "type TIME(9).",
                "SELECT TO_TIME('04:15:29') + INTERVAL '1 day'");
            assertAnswer(
                "REFUSED SQL compilation error: ['MONTH'] is not a valid date/time component for function + and "
                    + "type TIME(9).",
                "SELECT TO_TIME('04:15:29') + INTERVAL '1 month'");
            assertAnswer(
                "REFUSED SQL compilation error: ['YEAR'] is not a valid date/time component for function + and "
                    + "type TIME(9).",
                "SELECT TO_TIME('04:15:29') + INTERVAL '1 year'");
            assertAnswer(
                "05:15:29",
                "SELECT TO_TIME('04:15:29') + INTERVAL '25 hours'");
            assertAnswer(
                "04:15:31",
                "SELECT TO_TIME('04:15:29') + INTERVAL '1.5 seconds'");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid argument types for function "
                    + "'+': (INTERVAL, TIME(9))",
                "SELECT INTERVAL '1 hour' + TO_TIME('04:15:29')");
            assertAnswer(
                "TIME(9)[SB8]",
                "SELECT SYSTEM$TYPEOF(TO_TIME('04:15:29') + INTERVAL '1 hour')");
            assertAnswer(
                "04:15:30",
                "SELECT TO_TIME('04:15:29')::TIME(0) + INTERVAL '1 second'");
            assertAnswer(
                "TIME(9)[SB8]",
                "SELECT SYSTEM$TYPEOF(TO_TIME('04:15:29')::TIME(3) + INTERVAL '1 second')");
            assertAnswer(
                "REFUSED SQL compilation error: ['WEEK'] is not a valid date/time component for function + and "
                    + "type TIME(9).",
                "SELECT TO_TIME('04:15:29') + INTERVAL '1 week'");
            assertAnswer(
                "02:15:29",
                "SELECT TO_TIME('04:15:29') + INTERVAL '-2 hours'");
            assertAnswer(
                "REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD "
                    + "and type TIME(9).",
                "SELECT TO_TIME('04:15:29') + INTERVAL '1 day, 2 hours'");
            assertAnswer(
                "03:45:14",
                "SELECT TO_TIME('04:15:29') - INTERVAL '30 minutes, 15 seconds'");
            assertAnswer(
                "Table TT successfully created.",
                "CREATE OR REPLACE TABLE tt (t TIME)");
            assertAnswer(
                "2",
                "INSERT INTO tt VALUES ('10:00:00'), ('23:59:59')");
            assertAnswer(
                "10:00:01 ;; 00:00",
                "SELECT t + INTERVAL '1 second' FROM tt ORDER BY t");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for function "
                    + "'+': (TIME(9), NUMBER(1,0))",
                "SELECT TO_TIME('04:15:29') + 1");
            assertAnswer(
                "07:33:29",
                "SELECT TIMEADD(minute, 198, TO_TIME('04:15:29'))");
        } finally {
            answer(
                "DROP TABLE IF EXISTS tt");
        }
    }

    @Test
    public void longerPartsAreRefused() {
        try {
            assertAnswer(
                "REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function - and "
                    + "type TIME(9).",
                "SELECT TO_TIME('04:15:29') - INTERVAL '1 day'");
            assertAnswer(
                "REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD "
                    + "and type TIME(9).",
                "SELECT TO_TIME('04:15:29') - INTERVAL '1 day, 2 hours'");
            assertAnswer(
                "Table TE successfully created.",
                "CREATE OR REPLACE TABLE te (t TIME)");
            assertAnswer(
                "REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function + and "
                    + "type TIME(9).",
                "SELECT t + INTERVAL '1 day' FROM te");
            assertAnswer(
                "REFUSED SQL compilation error: ['QUARTER'] is not a valid date/time component for function + and "
                    + "type TIME(9).",
                "SELECT TO_TIME('04:15:29') + INTERVAL '1 quarter'");
            assertAnswer(
                "REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function + and "
                    + "type TIME(9).",
                "SELECT TO_TIME('04:15:29') + INTERVAL '2 hours, 1 day'");
            assertAnswer(
                "04:15:29.124",
                "SELECT TO_VARCHAR(TO_TIME('04:15:29.123') + INTERVAL '1 millisecond', 'HH24:MI:SS.FF3')");
            assertAnswer(
                "04:15:29.000000001",
                "SELECT TO_VARCHAR(TO_TIME('04:15:29') + INTERVAL '1 nanosecond', 'HH24:MI:SS.FF9')");
            assertAnswer(
                "00:00:00.001",
                "SELECT TO_VARCHAR(TO_TIME('23:59:59.999') + INTERVAL '2 milliseconds', 'HH24:MI:SS.FF3')");
            assertAnswer(
                "04:15:31.000",
                "SELECT TO_VARCHAR(TO_TIME('04:15:29') + INTERVAL '1.5 seconds', 'HH24:MI:SS.FF3')");
        } finally {
            answer(
                "DROP TABLE IF EXISTS te");
        }
    }
}
