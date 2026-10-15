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
 * DATEADD, DATEDIFF, DATE_PART and the one-part functions, DATE_TRUNC, LAST_DAY and the functions that build a
 * date, a time or a timestamp from parts. A DATE stays a DATE under a day or a longer part and under any
 * truncation, a text moved by DATEADD is a timestamp, a part out of its range rolls over, and the NTZ names
 * also take a date and a time (live-verified).
 */
public class DateFunctionCatalogTest extends MeasuredCellsTestSupport {

    @Test
    public void movingAndMeasuring() {
        assertAnswer(
            "2024-03-16|DATE[SB4]",
            "SELECT DATEADD(day, 1, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATEADD(day, 1, '2024-03-15'::DATE))");
        assertAnswer(
            "2024-02-29|DATE[SB4]",
            "SELECT DATEADD(month, 1, '2024-01-31'::DATE), SYSTEM$TYPEOF(DATEADD(month, 1, '2024-01-31'::DATE))");
        assertAnswer(
            "2024-03-15T01:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT DATEADD(hour, 1, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATEADD(hour, 1, '2024-03-15'::DATE))");
        assertAnswer(
            "2023-02-28|DATE[SB4]",
            "SELECT DATEADD(year, -1, '2024-02-29'::DATE), SYSTEM$TYPEOF(DATEADD(year, -1, '2024-02-29'::DATE))");
        assertAnswer(
            "2024-03-17|DATE[SB4]",
            "SELECT DATEADD(day, 1.6, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATEADD(day, 1.6, '2024-03-15'::DATE))");
        assertAnswer(
            "null|NULL[LOB]",
            "SELECT DATEADD(day, NULL, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATEADD(day, NULL, '2024-03-15'::DATE))");
        assertAnswer(
            "10:30|TIME(9)[SB8]",
            "SELECT TIMEADD(minute, 30, '10:00'::TIME), SYSTEM$TYPEOF(TIMEADD(minute, 30, '10:00'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and "
                + "type TIME(9).",
            "SELECT DATEADD(day, 1, '10:00'::TIME), SYSTEM$TYPEOF(DATEADD(day, 1, '10:00'::TIME))");
        assertAnswer(
            "11:00|TIME(9)[SB8]",
            "SELECT DATEADD(hour, 25, '10:00'::TIME), SYSTEM$TYPEOF(DATEADD(hour, 25, '10:00'::TIME))");
        assertAnswer(
            "2024-03-16|DATE[SB4]",
            "SELECT DATEADD('day', 1, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATEADD('day', 1, '2024-03-15'::DATE))");
        assertAnswer(
            "2024-03-16T00:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT DATEADD(day, 1, '2024-03-15'), SYSTEM$TYPEOF(DATEADD(day, 1, '2024-03-15'))");
        assertAnswer(
            "60|NUMBER(9,0)[SB4]",
            "SELECT DATEDIFF(day, '2024-01-01'::DATE, '2024-03-01'::DATE), SYSTEM$TYPEOF(DATEDIFF(day, "
                + "'2024-01-01'::DATE, '2024-03-01'::DATE))");
        assertAnswer(
            "1|NUMBER(9,0)[SB4]",
            "SELECT DATEDIFF(month, '2024-01-31'::DATE, '2024-02-01'::DATE), SYSTEM$TYPEOF(DATEDIFF(month, "
                + "'2024-01-31'::DATE, '2024-02-01'::DATE))");
        assertAnswer(
            "1|NUMBER(9,0)[SB4]",
            "SELECT DATEDIFF(year, '2023-12-31'::DATE, '2024-01-01'::DATE), SYSTEM$TYPEOF(DATEDIFF(year, "
                + "'2023-12-31'::DATE, '2024-01-01'::DATE))");
        assertAnswer(
            "1|NUMBER(9,0)[SB4]",
            "SELECT DATEDIFF(hour, '2024-01-01 23:59'::TIMESTAMP_NTZ, '2024-01-02 00:01'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(DATEDIFF(hour, '2024-01-01 23:59'::TIMESTAMP_NTZ, '2024-01-02 "
                + "00:01'::TIMESTAMP_NTZ))");
        assertAnswer(
            "-60|NUMBER(9,0)[SB4]",
            "SELECT DATEDIFF(day, '2024-03-01'::DATE, '2024-01-01'::DATE), SYSTEM$TYPEOF(DATEDIFF(day, "
                + "'2024-03-01'::DATE, '2024-01-01'::DATE))");
        assertAnswer(
            "null|NULL[LOB]",
            "SELECT DATEDIFF(day, NULL, '2024-01-01'::DATE), SYSTEM$TYPEOF(DATEDIFF(day, NULL, "
                + "'2024-01-01'::DATE))");
        assertAnswer(
            "90|NUMBER(9,0)[SB4]",
            "SELECT TIMEDIFF(minute, '10:00'::TIME, '11:30'::TIME), SYSTEM$TYPEOF(TIMEDIFF(minute, '10:00'::TIME, "
                + "'11:30'::TIME))");
        assertAnswer(
            "1|NUMBER(9,0)[SB4]",
            "SELECT DATEDIFF(week, '2024-01-06'::DATE, '2024-01-08'::DATE), SYSTEM$TYPEOF(DATEDIFF(week, "
                + "'2024-01-06'::DATE, '2024-01-08'::DATE))");
        assertAnswer(
            "2|NUMBER(9,0)[SB4]",
            "SELECT DATEDIFF(day, '2024-01-01', '2024-01-03'), SYSTEM$TYPEOF(DATEDIFF(day, '2024-01-01', "
                + "'2024-01-03'))");
    }

    @Test
    public void partsAndTruncation() {
        assertAnswer(
            "2024|NUMBER(4,0)[SB2]",
            "SELECT DATE_PART(year, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_PART(year, '2024-03-15'::DATE))");
        assertAnswer(
            "3|NUMBER(2,0)[SB1]",
            "SELECT EXTRACT(month FROM '2024-03-15'::DATE), SYSTEM$TYPEOF(EXTRACT(month FROM '2024-03-15'::DATE))");
        assertAnswer(
            "15|NUMBER(2,0)[SB1]",
            "SELECT DATE_PART('day', '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_PART('day', '2024-03-15'::DATE))");
        assertAnswer(
            "10|NUMBER(2,0)[SB1]",
            "SELECT DATE_PART(hour, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(DATE_PART(hour, "
                + "'2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "123000000|NUMBER(9,0)[SB4]",
            "SELECT DATE_PART(nanosecond, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(DATE_PART(nanosecond, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [HOUR] for parameter 'DATE_PART date/time part'",
            "SELECT DATE_PART(hour, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_PART(hour, '2024-03-15'::DATE))");
        assertAnswer(
            "null|NUMBER(4,0)[SB1]",
            "SELECT DATE_PART(year, NULL::DATE), SYSTEM$TYPEOF(DATE_PART(year, NULL::DATE))");
        assertAnswer(
            "5|NUMBER(2,0)[SB1]",
            "SELECT DATE_PART(dow, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_PART(dow, '2024-03-15'::DATE))");
        assertAnswer(
            "1|NUMBER(2,0)[SB1]",
            "SELECT DATE_PART(quarter, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_PART(quarter, '2024-03-15'::DATE))");
        assertAnswer(
            "2024|NUMBER(4,0)[SB2]",
            "SELECT YEAR('2024-03-15'::DATE), SYSTEM$TYPEOF(YEAR('2024-03-15'::DATE))");
        assertAnswer(
            "3|NUMBER(2,0)[SB1]",
            "SELECT MONTH('2024-03-15'::DATE), SYSTEM$TYPEOF(MONTH('2024-03-15'::DATE))");
        assertAnswer(
            "15|NUMBER(2,0)[SB1]",
            "SELECT DAY('2024-03-15'::DATE), SYSTEM$TYPEOF(DAY('2024-03-15'::DATE))");
        assertAnswer(
            "10|NUMBER(2,0)[SB1]",
            "SELECT HOUR('2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(HOUR('2024-03-15 "
                + "10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "30|NUMBER(2,0)[SB1]",
            "SELECT SECOND('2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(SECOND('2024-03-15 "
                + "10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "20|NUMBER(2,0)[SB1]",
            "SELECT MINUTE('2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(MINUTE('2024-03-15 "
                + "10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "null|NUMBER(4,0)[SB1]",
            "SELECT YEAR(NULL::DATE), SYSTEM$TYPEOF(YEAR(NULL::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(10) argument type",
            "SELECT YEAR('2024-03-15'), SYSTEM$TYPEOF(YEAR('2024-03-15'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [HOUR] for parameter 'HOUR date/time part'",
            "SELECT HOUR('2024-03-15'::DATE), SYSTEM$TYPEOF(HOUR('2024-03-15'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [YEAR] for parameter 'YEAR date/time part'",
            "SELECT YEAR('10:00'::TIME), SYSTEM$TYPEOF(YEAR('10:00'::TIME))");
        assertAnswer(
            "2024-03-01|DATE[SB4]",
            "SELECT DATE_TRUNC(month, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_TRUNC(month, '2024-03-15'::DATE))");
        assertAnswer(
            "2024-01-01T00:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT DATE_TRUNC(year, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(DATE_TRUNC(year, "
                + "'2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "2024-03-15T10:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT DATE_TRUNC('hour', '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(DATE_TRUNC('hour',"
                + " '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "2024-03-11|DATE[SB4]",
            "SELECT DATE_TRUNC(week, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_TRUNC(week, '2024-03-15'::DATE))");
        assertAnswer(
            "2024-01-01|DATE[SB4]",
            "SELECT DATE_TRUNC(quarter, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_TRUNC(quarter, "
                + "'2024-03-15'::DATE))");
        assertAnswer(
            "null|DATE[SB4]",
            "SELECT DATE_TRUNC(day, NULL::DATE), SYSTEM$TYPEOF(DATE_TRUNC(day, NULL::DATE))");
        assertAnswer(
            "10:20|TIME(9)[SB8]",
            "SELECT DATE_TRUNC(minute, '10:20:30'::TIME), SYSTEM$TYPEOF(DATE_TRUNC(minute, '10:20:30'::TIME))");
        assertAnswer(
            "2024-03-15|DATE[SB4]",
            "SELECT DATE_TRUNC(hour, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_TRUNC(hour, '2024-03-15'::DATE))");
        assertAnswer(
            "2024-03-31|DATE[SB4]",
            "SELECT LAST_DAY('2024-03-15'::DATE), SYSTEM$TYPEOF(LAST_DAY('2024-03-15'::DATE))");
        assertAnswer(
            "2024-12-31|DATE[SB4]",
            "SELECT LAST_DAY('2024-03-15'::DATE, year), SYSTEM$TYPEOF(LAST_DAY('2024-03-15'::DATE, year))");
        assertAnswer(
            "2024-03-17|DATE[SB4]",
            "SELECT LAST_DAY('2024-03-15'::DATE, week), SYSTEM$TYPEOF(LAST_DAY('2024-03-15'::DATE, week))");
        assertAnswer(
            "2024-03-31|DATE[SB4]",
            "SELECT LAST_DAY('2024-03-15'::DATE, quarter), SYSTEM$TYPEOF(LAST_DAY('2024-03-15'::DATE, quarter))");
        assertAnswer(
            "null|DATE[SB4]",
            "SELECT LAST_DAY(NULL::DATE), SYSTEM$TYPEOF(LAST_DAY(NULL::DATE))");
        assertAnswer(
            "2024-03-31|DATE[SB4]",
            "SELECT LAST_DAY('2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(LAST_DAY('2024-03-15 "
                + "10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "2024-02-29|DATE[SB4]",
            "SELECT LAST_DAY('2024-02-10'::DATE), SYSTEM$TYPEOF(LAST_DAY('2024-02-10'::DATE))");
    }

    @Test
    public void builtFromParts() {
        assertAnswer(
            "2024-03-15|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 3, 15), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 3, 15))");
        assertAnswer(
            "2025-01-01|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 13, 1), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 13, 1))");
        assertAnswer(
            "2023-12-01|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 0, 1), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 0, 1))");
        assertAnswer(
            "2024-02-29|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 3, 0), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 3, 0))");
        assertAnswer(
            "2024-02-28|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 3, -1), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 3, -1))");
        assertAnswer(
            "null|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(NULL, 3, 15), SYSTEM$TYPEOF(DATE_FROM_PARTS(NULL, 3, 15))");
        assertAnswer(
            "2024-03-01|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 2, 30), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 2, 30))");
        assertAnswer(
            "2024-03-15|DATE[SB4]",
            "SELECT DATEFROMPARTS(2024, 3, 15), SYSTEM$TYPEOF(DATEFROMPARTS(2024, 3, 15))");
        assertAnswer(
            "10:20:30|TIME(9)[SB8]",
            "SELECT TIME_FROM_PARTS(10, 20, 30), SYSTEM$TYPEOF(TIME_FROM_PARTS(10, 20, 30))");
        assertAnswer(
            "01:00|TIME(9)[SB8]",
            "SELECT TIME_FROM_PARTS(25, 0, 0), SYSTEM$TYPEOF(TIME_FROM_PARTS(25, 0, 0))");
        assertAnswer(
            "01:30|TIME(9)[SB8]",
            "SELECT TIME_FROM_PARTS(0, 90, 0), SYSTEM$TYPEOF(TIME_FROM_PARTS(0, 90, 0))");
        assertAnswer(
            "null|TIME(9)[SB8]",
            "SELECT TIME_FROM_PARTS(NULL, 0, 0), SYSTEM$TYPEOF(TIME_FROM_PARTS(NULL, 0, 0))");
        assertAnswer(
            "10:20:30|TIME(9)[SB8]",
            "SELECT TIMEFROMPARTS(10, 20, 30), SYSTEM$TYPEOF(TIMEFROMPARTS(10, 20, 30))");
        assertAnswer(
            "00:00|TIME(9)[SB8]",
            "SELECT TIME_FROM_PARTS(0, 0, 86400), SYSTEM$TYPEOF(TIME_FROM_PARTS(0, 0, 86400))");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3, 15, 10, 20, 30), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3, 15,"
                + " 10, 20, 30))");
        answer(
            "SELECT TIMESTAMP_NTZ_FROM_PARTS(2024, 3, 15, 10, 20, 30, 500000000), "
                + "SYSTEM$TYPEOF(TIMESTAMP_NTZ_FROM_PARTS(2024, 3, 15, 10, 20, 30, 500000000))");
        answer(
            "SELECT TIMESTAMPNTZFROMPARTS(2024, 3, 15, 10, 20, 30), SYSTEM$TYPEOF(TIMESTAMPNTZFROMPARTS(2024, 3, "
                + "15, 10, 20, 30))");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS('2024-03-15'::DATE, '10:20:30'::TIME), "
                + "SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS('2024-03-15'::DATE, '10:20:30'::TIME))");
        assertAnswer(
            "2025-01-01T00:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT TIMESTAMP_FROM_PARTS(2024, 13, 1, 0, 0, 0), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 13, 1, 0,"
                + " 0, 0))");
        assertAnswer(
            "null|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT TIMESTAMP_FROM_PARTS(NULL, 3, 15, 10, 20, 30), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(NULL, 3, 15,"
                + " 10, 20, 30))");
        assertAnswer(
            "TIMESTAMP_LTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(TIMESTAMP_LTZ_FROM_PARTS(2024, 3, 15, 10, 20, 30))");
        assertAnswer(
            "TIMESTAMP_LTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(TIMESTAMPLTZFROMPARTS(2024, 3, 15, 10, 20, 30))");
        answer(
            "SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 3, 15, 10, 20, 30, 0, 'America/New_York'), "
                + "SYSTEM$TYPEOF(TIMESTAMP_TZ_FROM_PARTS(2024, 3, 15, 10, 20, 30, 0, 'America/New_York'))");
        assertAnswer(
            "TIMESTAMP_TZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(TIMESTAMP_TZ_FROM_PARTS(2024, 3, 15, 10, 20, 30))");
    }

    @Test
    public void partsThatRollOver() {
        assertAnswer(
            "2025-02-01|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 14, 1), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 14, 1))");
        assertAnswer(
            "2023-12-01|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 0, 1), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 0, 1))");
        assertAnswer(
            "2023-11-01|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, -1, 1), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, -1, 1))");
        assertAnswer(
            "2024-02-29|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 3, 0), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 3, 0))");
        assertAnswer(
            "2024-02-28|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 3, -1), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 3, -1))");
        assertAnswer(
            "2024-03-01|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 2, 30), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 2, 30))");
        assertAnswer(
            "2025-02-01|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 13, 32), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 13, 32))");
        assertAnswer(
            "2025-02-03|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, 1, 400), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, 1, 400))");
        assertAnswer(
            "2022-12-01|DATE[SB4]",
            "SELECT DATE_FROM_PARTS(2024, -11, -30), SYSTEM$TYPEOF(DATE_FROM_PARTS(2024, -11, -30))");
        assertAnswer(
            "2024-03-16T01:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3, 15, 25, 0, 0), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3, 15, "
                + "25, 0, 0))");
        assertAnswer(
            "2024-03-15T11:01|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3, 15, 10, 61, 0), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3, 15, "
                + "10, 61, 0))");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3, 15, 10, 20, 61), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3, 15,"
                + " 10, 20, 61))");
        assertAnswer(
            "2024-03-14T23:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3, 15, -1, 0, 0), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3, 15, "
                + "-1, 0, 0))");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS(2024, 14, 32, 25, 61, 61), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 14, "
                + "32, 25, 61, 61))");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3, 15, 10, 20, 30, 1500000000), "
                + "SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3, 15, 10, 20, 30, 1500000000))");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3, 15, 10, 20, 30, -1), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3,"
                + " 15, 10, 20, 30, -1))");
        assertAnswer(
            "01:00|TIME(9)[SB8]",
            "SELECT TIME_FROM_PARTS(25, 0, 0), SYSTEM$TYPEOF(TIME_FROM_PARTS(25, 0, 0))");
        assertAnswer(
            "11:01|TIME(9)[SB8]",
            "SELECT TIME_FROM_PARTS(10, 61, 0), SYSTEM$TYPEOF(TIME_FROM_PARTS(10, 61, 0))");
        assertAnswer(
            "23:00|TIME(9)[SB8]",
            "SELECT TIME_FROM_PARTS(-1, 0, 0), SYSTEM$TYPEOF(TIME_FROM_PARTS(-1, 0, 0))");
        assertAnswer(
            "10:21:01|TIME(9)[SB8]",
            "SELECT TIME_FROM_PARTS(10, 20, 61), SYSTEM$TYPEOF(TIME_FROM_PARTS(10, 20, 61))");
    }

    @Test
    public void aDateWithATimeAndShortCalls() {
        answer(
            "SELECT TIMESTAMP_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME), "
                + "SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME))");
        answer(
            "SELECT TIMESTAMP_NTZ_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME), "
                + "SYSTEM$TYPEOF(TIMESTAMP_NTZ_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME))");
        answer(
            "SELECT TIMESTAMPFROMPARTS('2024-03-15'::DATE, '10:20:30.5'::TIME), "
                + "SYSTEM$TYPEOF(TIMESTAMPFROMPARTS('2024-03-15'::DATE, '10:20:30.5'::TIME))");
        answer(
            "SELECT TIMESTAMPNTZFROMPARTS('2024-03-15'::DATE, '10:20:30.5'::TIME), "
                + "SYSTEM$TYPEOF(TIMESTAMPNTZFROMPARTS('2024-03-15'::DATE, '10:20:30.5'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_LTZ_FROM_PARTS(CAST('2024-03-15' AS DATE), CAST('10:20:30.5' AS TIME(9)))], expected 6,"
                + " got 2",
            "SELECT TIMESTAMP_LTZ_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME), "
                + "SYSTEM$TYPEOF(TIMESTAMP_LTZ_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_TZ_FROM_PARTS(CAST('2024-03-15' AS DATE), CAST('10:20:30.5' AS TIME(9)))], expected 6, "
                + "got 2",
            "SELECT TIMESTAMP_TZ_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME), "
                + "SYSTEM$TYPEOF(TIMESTAMP_TZ_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_NTZ_FROM_PARTS(2024, 3, 15)], expected 6, got 3",
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3, 15), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3, 15))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_FROM_PARTS(2024)], expected 2, got 1",
            "SELECT TIMESTAMP_FROM_PARTS(2024), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_FROM_PARTS(CAST('2024-03-15' AS DATE))], expected 2, got 1",
            "SELECT TIMESTAMP_FROM_PARTS('2024-03-15'::DATE), "
                + "SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS('2024-03-15'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_NTZ_FROM_PARTS(2024, 3, 15, 10)], expected 6, got 4",
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3, 15, 10), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3, 15, 10))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_NTZ_FROM_PARTS(2024, 3, 15, 10, 20)], expected 6, got 5",
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3, 15, 10, 20), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3, 15, 10,"
                + " 20))");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS('2024-03-15', '10:20:30'), "
                + "SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS('2024-03-15', '10:20:30'))");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS('2024-03-15 08:00:00'::TIMESTAMP_NTZ, '10:20:30.5'::TIME), "
                + "SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS('2024-03-15 08:00:00'::TIMESTAMP_NTZ, '10:20:30.5'::TIME))");
        assertAnswer(
            "null|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT TIMESTAMP_FROM_PARTS(NULL, '10:20:30.5'::TIME), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(NULL, "
                + "'10:20:30.5'::TIME))");
        assertAnswer(
            "null|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT TIMESTAMP_FROM_PARTS('2024-03-15'::DATE, NULL), "
                + "SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS('2024-03-15'::DATE, NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TIMESTAMP_NTZ_FROM_DATE_TIME': (TIME(9), DATE)",
            "SELECT TIMESTAMP_FROM_PARTS('10:20:30.5'::TIME, '2024-03-15'::DATE), "
                + "SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS('10:20:30.5'::TIME, '2024-03-15'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TIMESTAMP_NTZ_FROM_DATE_TIME': (NUMBER(4,0), NUMBER(1,0))",
            "SELECT TIMESTAMP_FROM_PARTS(2024, 3), SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS(2024, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_NTZ_FROM_PARTS(CAST('2024-03-15' AS DATE), CAST('10:20:30.5' AS TIME(9)), 1)], "
                + "expected 6, got 3",
            "SELECT TIMESTAMP_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME, 1), "
                + "SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME, 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_LTZ_FROM_PARTS(2024, 3, 15)], expected 6, got 3",
            "SELECT TIMESTAMP_LTZ_FROM_PARTS(2024, 3, 15), SYSTEM$TYPEOF(TIMESTAMP_LTZ_FROM_PARTS(2024, 3, 15))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMESTAMP_TZ_FROM_PARTS(2024, 3)], expected 6, got 2",
            "SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 3), SYSTEM$TYPEOF(TIMESTAMP_TZ_FROM_PARTS(2024, 3))");
    }

    @Test
    public void textsMovedAndDatesTruncated() {
        assertAnswer(
            "2024-03-16T10:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT DATEADD(day, 1, '2024-03-15 10:00:00'), SYSTEM$TYPEOF(DATEADD(day, 1, '2024-03-15 10:00:00'))");
        assertAnswer(
            "2024-02-29T00:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT DATEADD(month, 1, '2024-01-31'), SYSTEM$TYPEOF(DATEADD(month, 1, '2024-01-31'))");
        assertAnswer(
            "2024-03-15T01:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT DATEADD(hour, 1, '2024-03-15'), SYSTEM$TYPEOF(DATEADD(hour, 1, '2024-03-15'))");
        assertAnswer(
            "2024-03-16|DATE[SB4]",
            "SELECT DATEADD(day, 1, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATEADD(day, 1, '2024-03-15'::DATE))");
        assertAnswer(
            "2025-02-28T00:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT DATEADD(year, 1, '2024-02-29'), SYSTEM$TYPEOF(DATEADD(year, 1, '2024-02-29'))");
        assertAnswer(
            "2024-03-16T00:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT DATEADD(day, 1, '2024-03-15'::VARCHAR), SYSTEM$TYPEOF(DATEADD(day, 1, '2024-03-15'::VARCHAR))");
        assertAnswer(
            "2024-03-16T00:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT TIMESTAMPADD(day, 1, '2024-03-15'), SYSTEM$TYPEOF(TIMESTAMPADD(day, 1, '2024-03-15'))");
        assertAnswer(
            "2024-03-16T10:00|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT DATEADD(day, 1, '2024-03-15T10:00:00+02:00'), SYSTEM$TYPEOF(DATEADD(day, 1, "
                + "'2024-03-15T10:00:00+02:00'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support VARCHAR(10) argument type",
            "SELECT DATE_TRUNC(hour, '2024-03-15'), SYSTEM$TYPEOF(DATE_TRUNC(hour, '2024-03-15'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support VARCHAR(10) argument type",
            "SELECT DATE_TRUNC(month, '2024-03-15'), SYSTEM$TYPEOF(DATE_TRUNC(month, '2024-03-15'))");
        assertAnswer(
            "2024-03-15|DATE[SB4]",
            "SELECT DATE_TRUNC(minute, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_TRUNC(minute, '2024-03-15'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support VARCHAR(19) argument type",
            "SELECT DATE_TRUNC(day, '2024-03-15 10:20:30'), SYSTEM$TYPEOF(DATE_TRUNC(day, '2024-03-15 10:20:30'))");
        assertAnswer(
            "2024-03-15|DATE[SB4]",
            "SELECT DATE_TRUNC(second, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_TRUNC(second, '2024-03-15'::DATE))");
        assertAnswer(
            "2024-03-15|DATE[SB4]",
            "SELECT DATE_TRUNC(nanosecond, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_TRUNC(nanosecond, "
                + "'2024-03-15'::DATE))");
        assertAnswer(
            "2024-03-15|DATE[SB4]",
            "SELECT TRUNC('2024-03-15'::DATE, 'hour'), SYSTEM$TYPEOF(TRUNC('2024-03-15'::DATE, 'hour'))");
        assertAnswer(
            "2024-03-01|DATE[SB4]",
            "SELECT TRUNC('2024-03-15'::DATE, 'month'), SYSTEM$TYPEOF(TRUNC('2024-03-15'::DATE, 'month'))");
        assertAnswer(
            "10:20:30|VARCHAR[LOB]",
            "SELECT TIME_FROM_PARTS(10, 20, 30, 500000000)::VARCHAR, SYSTEM$TYPEOF(TIME_FROM_PARTS(10, 20, 30, "
                + "500000000)::VARCHAR)");
        answer(
            "SELECT TIMESTAMPTZFROMPARTS(2024, 3, 15, 10, 20, 30, 0, 'UTC')::VARCHAR, "
                + "SYSTEM$TYPEOF(TIMESTAMPTZFROMPARTS(2024, 3, 15, 10, 20, 30, 0, 'UTC')::VARCHAR)");
        answer(
            "SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 3, 15, 10, 20, 30, 0, 'America/Los_Angeles')::VARCHAR, "
                + "SYSTEM$TYPEOF(TIMESTAMP_TZ_FROM_PARTS(2024, 3, 15, 10, 20, 30, 0, 'America/Los_Angeles')::VARCHAR)");
        answer(
            "SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 3, 15, 10, 20, 30, 0, 'Europe/Warsaw')::VARCHAR, "
                + "SYSTEM$TYPEOF(TIMESTAMP_TZ_FROM_PARTS(2024, 3, 15, 10, 20, 30, 0, 'Europe/Warsaw')::VARCHAR)");
        answer(
            "SELECT TIMESTAMP_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME)::VARCHAR, "
                + "SYSTEM$TYPEOF(TIMESTAMP_FROM_PARTS('2024-03-15'::DATE, '10:20:30.5'::TIME)::VARCHAR)");
        answer(
            "SELECT DATEADD(day, 1, '2024-03-15')::VARCHAR, SYSTEM$TYPEOF(DATEADD(day, 1, '2024-03-15')::VARCHAR)");
        assertAnswer(
            "2024-03-15|VARCHAR[LOB]",
            "SELECT DATE_TRUNC(hour, '2024-03-15'::DATE)::VARCHAR, SYSTEM$TYPEOF(DATE_TRUNC(hour, "
                + "'2024-03-15'::DATE)::VARCHAR)");
    }
}
