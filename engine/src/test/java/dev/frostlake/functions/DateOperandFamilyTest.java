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
 * The operands of the date functions, judged while the statement compiles (live-verified): TRUNC with a date part
 * refuses a value that is no number, date or timestamp by its type; TIME_SLICE refuses a slice length that is a text
 * and a clock unit over a DATE; DATEDIFF of a TIME beside a DATE is refused by the argument types before the unit; a
 * VARIANT holding an OBJECT converted to a date is refused in the VARIANT's words; TO_TIME reads a zoned timestamp's
 * own wall clock.
 */
public class DateOperandFamilyTest extends MeasuredCellsTestSupport {

    @Test
    public void eachDateFunctionJudgesItsOperandAsLiveDoes() {
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, dc NUMBER(10,2), f FLOAT, bo BOOLEAN, s VARCHAR(5), o "
            + "OBJECT, ar ARRAY, bi BINARY, v VARIANT, tm TIME, d DATE, ts TIMESTAMP_NTZ, tz TIMESTAMP_TZ, "
            + "lz TIMESTAMP_LTZ, sv VARCHAR, b8 BINARY(8), t9 TIME(3), dbl DOUBLE, n1 NUMBER(5,0))");
        engine.execute("INSERT INTO tt SELECT 1, 1.50, 1.5, TRUE, 'abc', OBJECT_CONSTRUCT('x', '2024-01-02'), "
            + "ARRAY_CONSTRUCT(1), TO_BINARY('ab', 'HEX'), PARSE_JSON('{\"x\":\"2024-01-02\"}'), "
            + "'03:04:05', '2024-01-02', '2024-01-02 03:04:05', '2024-01-02 03:04:05 +01:00', '2024-01-02 "
            + "03:04:05', '2024-01-02', TO_BINARY('ab', 'HEX'), '03:04:05.123', 1.5, 1");
        assertCells(new String[][] {
            {"SELECT TRUNC(bo, 'month') FROM tt",
                "REFUSED SQL compilation error: | Function TRUNC does not support BOOLEAN argument type"},
            {"SELECT TRUNC(s, 'month') FROM tt",
                "REFUSED SQL compilation error: | Function TRUNC does not support VARCHAR(5) argument type"},
            {"SELECT TRUNC(o, 'month') FROM tt",
                "REFUSED SQL compilation error: | Function TRUNC does not support OBJECT argument type"},
            {"SELECT TRUNC(ar, 'month') FROM tt",
                "REFUSED SQL compilation error: | Function TRUNC does not support ARRAY argument type"},
            {"SELECT TRUNC(bi, 'month') FROM tt",
                "REFUSED SQL compilation error: | Function TRUNC does not support BINARY(8388608) argument type"},
            {"SELECT TRUNC(v, 'month') FROM tt",
                "REFUSED SQL compilation error: | Function TRUNC does not support VARIANT argument type"},
            {"SELECT TRUNC(sv, 'month') FROM tt",
                "REFUSED SQL compilation error: | Function TRUNC does not support VARCHAR(16777216) argument type"},
            {"SELECT TRUNC(a, 'month') FROM tt", "REFUSED Numeric value 'month' is not recognized"},
            {"SELECT TRUNC(bo, 'day') FROM tt",
                "REFUSED SQL compilation error: | Function TRUNC does not support BOOLEAN argument type"},
            {"SELECT DATE_TRUNC(hour, d) FROM tt", "2024-01-02"},
            {"SELECT DATE_TRUNC('hour', d) FROM tt", "2024-01-02"},
            {"SELECT DATE_TRUNC(minute, d) FROM tt", "2024-01-02"},
            {"SELECT DATE_TRUNC(second, d) FROM tt", "2024-01-02"},
            {"SELECT SYSTEM$TYPEOF(DATE_TRUNC(hour, d)) FROM tt", "DATE[SB4]"},
            {"SELECT TIME_SLICE(d, 1, 'HOUR') FROM tt",
                "REFUSED SQL compilation error: ['HOUR'] is not a valid date/time component for function "
                + "TIME_SLICE and type DATE."},
            {"SELECT TIME_SLICE(d, 1, 'MINUTE') FROM tt",
                "REFUSED SQL compilation error: ['MINUTE'] is not a valid date/time component for function "
                + "TIME_SLICE and type DATE."},
            {"SELECT TIME_SLICE(d, 'x', 'HOUR') FROM tt",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE(d, 1, 'DAY') FROM tt", "2024-01-02"},
            {"SELECT DATEDIFF(day, tm, d) FROM tt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATEDIFF': (VARCHAR(3), TIME(9), DATE)"},
            {"SELECT DATEDIFF(day, t9, d) FROM tt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATEDIFF': (VARCHAR(3), TIME(3), DATE)"},
            {"SELECT DATEDIFF(hour, tm, d) FROM tt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATEDIFF': (VARCHAR(4), TIME(9), DATE)"},
            {"SELECT DAYNAME(v) FROM tt", "REFUSED Failed to cast variant value {\"x\":\"2024-01-02\"} to DATE"},
            {"SELECT NEXT_DAY(v, 'mo') FROM tt", "REFUSED Failed to cast variant value {\"x\":\"2024-01-02\"} to DATE"},
            {"SELECT ADD_MONTHS(v, 1) FROM tt",
                "REFUSED Failed to cast variant value {\"x\":\"2024-01-02\"} to TIMESTAMP_NTZ"},
            {"SELECT DATEADD(day, 1, sv) FROM tt", "2024-01-03T00:00"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(day, 1, sv)) FROM tt", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT TO_TIME(tz) FROM tt", "03:04:05"},
            {"SELECT TO_TIME(lz) FROM tt", "03:04:05"},
            {"SELECT TO_TIME(ts) FROM tt", "03:04:05"},
            {"SELECT TO_TIME(t9)::VARCHAR FROM tt", "03:04:05"},
        });
    }
}
