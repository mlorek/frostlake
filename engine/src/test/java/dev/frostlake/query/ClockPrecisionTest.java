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
 * CURRENT_TIMESTAMP(n), LOCALTIMESTAMP(n), CURRENT_TIME(n) and LOCALTIME(n) are typed with n fractional
 * digits, and a nanosecond read from one holds only those digits - whole milliseconds for 3, nothing for 0 -
 * through DATE_PART, EXTRACT, a derived column and a table created from it. The precision is a whole-number
 * constant between 0 and 9: a number outside is refused at the call as an invalid timestamp or time scale, a
 * value of a type that is no number as invalid argument types under the name written, and anything else -
 * NULL, a text, a number with a fraction, a cast, an expression - echoed as the plan converts it, under the
 * name of the function the call stands for. SYSDATE, SYSTIMESTAMP, GETDATE and CURRENT_DATE take none.
 * Every answer is live-verified.
 */
public class ClockPrecisionTest extends MeasuredCellsTestSupport {

    @Test
    public void aNanosecondAtTheTypesScale() {
        try {
            assertAnswer(
                "0|0|0",
                "SELECT MOD(DATE_PART(NANOSECOND, CURRENT_TIMESTAMP(3)), 1000000), DATE_PART(NANOSECOND, "
                    + "CURRENT_TIMESTAMP(0)), MOD(DATE_PART(NANOSECOND, CURRENT_TIMESTAMP(6)), 1000)");
            assertAnswer(
                "0|0",
                "SELECT MOD(DATE_PART(NANOSECOND, LOCALTIMESTAMP(3)), 1000000), DATE_PART(NANOSECOND, "
                    + "LOCALTIMESTAMP(0))");
            assertAnswer(
                "0|0",
                "SELECT MOD(DATE_PART(NANOSECOND, CURRENT_TIME(3)), 1000000), DATE_PART(NANOSECOND, "
                    + "CURRENT_TIME(0))");
            assertAnswer(
                "0|0",
                "SELECT MOD(DATE_PART(NANOSECOND, LOCALTIME(3)), 1000000), DATE_PART(NANOSECOND, LOCALTIME(0))");
            assertAnswer(
                "0|0",
                "SELECT MOD(DATE_PART(NANOSECOND, CURRENT_TIMESTAMP(1)), 100000000), MOD(DATE_PART(NANOSECOND, "
                    + "CURRENT_TIMESTAMP(2)), 10000000)");
            assertAnswer(
                "TIMESTAMP_LTZ(3)[SB8]",
                "SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP(3))");
            assertAnswer(
                "TIMESTAMP_LTZ(0)[SB8]",
                "SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP(0))");
            assertAnswer(
                "TIMESTAMP_LTZ(9)[SB16]",
                "SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP())");
            assertAnswer(
                "TIMESTAMP_LTZ(9)[SB16]",
                "SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP)");
            assertAnswer(
                "TIMESTAMP_LTZ(3)[SB8]",
                "SELECT SYSTEM$TYPEOF(LOCALTIMESTAMP(3))");
            assertAnswer(
                "TIME(3)[SB4]",
                "SELECT SYSTEM$TYPEOF(CURRENT_TIME(3))");
            assertAnswer(
                "TIME(9)[SB8]",
                "SELECT SYSTEM$TYPEOF(CURRENT_TIME)");
            assertAnswer(
                "TIME(2)[SB4]",
                "SELECT SYSTEM$TYPEOF(LOCALTIME(2))");
            assertAnswer(
                "TIMESTAMP_NTZ(9)[SB16]",
                "SELECT SYSTEM$TYPEOF(SYSDATE())");
            assertAnswer(
                "TIMESTAMP_LTZ(9)[SB16]",
                "SELECT SYSTEM$TYPEOF(SYSTIMESTAMP())");
            assertAnswer(
                "TIMESTAMP_LTZ(9)[SB16]",
                "SELECT SYSTEM$TYPEOF(GETDATE())");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid timestamp scale: 10. Must be "
                    + "between 0 and 9.",
                "SELECT CURRENT_TIMESTAMP(10)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid timestamp scale: -1. Must be "
                    + "between 0 and 9.",
                "SELECT CURRENT_TIMESTAMP(-1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [SYSTEM$NULL_TO_FIXED(null)] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TO_NUMBER('3', 18, 0)] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP('3')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(3.5 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(3.5)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[CURRENT_DATE(1)] expected 0, got 1",
                "SELECT CURRENT_DATE(1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid time scale: 10. Must be "
                    + "between 0 and 9.",
                "SELECT CURRENT_TIME(10)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid timestamp scale: 10. Must be "
                    + "between 0 and 9.",
                "SELECT LOCALTIMESTAMP(10)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[SYSDATE(3)] expected 0, got 1",
                "SELECT SYSDATE(3)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[SYSTIMESTAMP(3)] expected 0, got 1",
                "SELECT SYSTIMESTAMP(3) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[GETDATE(3)] expected 0, got 1",
                "SELECT GETDATE(3)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [1 + 2] for function 'CURRENT_TIMESTAMP', "
                    + "parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(1 + 2) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid time scale: 10. Must be "
                    + "between 0 and 9.",
                "SELECT LOCALTIME(10)");
            assertShape(
                "A:TIMESTAMP_LTZ, B:TIME, C:TIMESTAMP_LTZ, D:TIMESTAMP_LTZ, E:TIME",
                "SELECT CURRENT_TIMESTAMP(3) AS a, CURRENT_TIME(2) AS b, LOCALTIMESTAMP(0) AS c, "
                    + "CURRENT_TIMESTAMP AS d, LOCALTIME(4) AS e");
            assertAnswer(
                "Table TC successfully created.",
                "CREATE OR REPLACE TABLE tc AS SELECT CURRENT_TIMESTAMP(3) AS a, CURRENT_TIME(2) AS b");
            assertAnswer(
                "true",
                "SELECT DATE_PART(NANOSECOND, a) = MOD(DATE_PART(NANOSECOND, a), 1000000000) - "
                    + "MOD(DATE_PART(NANOSECOND, a), 1000000) FROM tc");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void whatReadsAClockValue() {
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [SYSTEM$NULL_TO_FIXED(null)] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT LOCALTIMESTAMP(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [SYSTEM$NULL_TO_FIXED(null)] for function "
                    + "'CURRENT_TIME', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIME(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TO_NUMBER('3', 18, 0)] for function "
                    + "'CURRENT_TIME', parameter 1: must be an integer constant",
                "SELECT LOCALTIME('3')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [1 + 2] for function 'CURRENT_TIME', parameter 1: "
                    + "must be an integer constant",
                "SELECT CURRENT_TIME(1 + 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(3.5 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT LOCALTIMESTAMP(3.5)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [1 + 2] for function 'CURRENT_TIME', parameter 1: "
                    + "must be an integer constant",
                "SELECT LOCALTIME(1 + 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(3.5 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIME', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIME(3.5)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [\"values\".A] for function 'CURRENT_TIMESTAMP', "
                    + "parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(a) FROM (SELECT 3 AS a)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[CURRENT_TIMESTAMP(3, 4)] expected 1, got 2",
                "SELECT CURRENT_TIMESTAMP(3, 4)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [UNARY PLUS(3)] for function 'CURRENT_TIMESTAMP', "
                    + "parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(+3) IS NOT NULL");
            assertAnswer(
                "true",
                "SELECT CURRENT_TIMESTAMP(-0) IS NOT NULL");
            assertAnswer(
                "true",
                "SELECT CURRENT_TIMESTAMP(03) IS NOT NULL");
            assertAnswer(
                "true",
                "SELECT CURRENT_TIMESTAMP(3.0) IS NOT NULL");
            assertAnswer(
                "true",
                "SELECT CURRENT_TIMESTAMP(9) IS NOT NULL");
            assertAnswer(
                "true",
                "SELECT CURRENT_TIME(9) IS NOT NULL");
            assertAnswer(
                "true",
                "SELECT CURRENT_TIMESTAMP(1e0) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'CURRENT_TIMESTAMP': (BOOLEAN)",
                "SELECT CURRENT_TIMESTAMP(TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid timestamp scale: 100. Must "
                    + "be between 0 and 9.",
                "SELECT CURRENT_TIMESTAMP(100)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid time scale: -1. Must be "
                    + "between 0 and 9.",
                "SELECT CURRENT_TIME(-1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid timestamp scale: -1. Must be "
                    + "between 0 and 9.",
                "SELECT LOCALTIMESTAMP(-1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 15 | Invalid timestamp scale: 10. Must "
                    + "be between 0 and 9.",
                "SELECT 1 WHERE CURRENT_TIMESTAMP(10) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 10 | Invalid time scale: 11. Must be "
                    + "between 0 and 9.",
                "SELECT 1, CURRENT_TIME(11)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(-3.5 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(-3.5)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TO_NUMBER('x', 18, 0)] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP('x')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [SYSTEM$NULL_TO_FIXED(null)] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(NULL::INT)");
            assertAnswer(
                "true",
                "SELECT CURRENT_TIMESTAMP((3)) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(3 AS NUMBER(38,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(3::INT) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [2 * 2] for function 'CURRENT_TIMESTAMP', "
                    + "parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(2 * 2) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [10 + 1] for function 'CURRENT_TIMESTAMP', "
                    + "parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(10 + 1) IS NOT NULL");
            assertAnswer(
                "0",
                "SELECT MOD(DATE_PART(NANOSECOND, CURRENT_TIMESTAMP(3)::TIMESTAMP_LTZ(1)), 100000000)");
            assertAnswer(
                "0",
                "SELECT MOD(DATE_PART(NANOSECOND, CURRENT_TIMESTAMP(3)::TIMESTAMP_NTZ(3)), 1000000)");
            assertAnswer(
                "0",
                "SELECT MOD(EXTRACT(NANOSECOND FROM CURRENT_TIMESTAMP(3)), 1000000)");
            assertAnswer(
                "0",
                "SELECT MOD(DATE_PART('ns', CURRENT_TIMESTAMP(3)), 1000000)");
            assertAnswer(
                "123000000",
                "SELECT DATE_PART(NANOSECOND, '2024-01-01 10:00:00.123456789'::TIMESTAMP_NTZ(3))");
            assertAnswer(
                "123000000",
                "SELECT DATE_PART(NANOSECOND, '10:00:00.123456789'::TIME(3))");
            assertAnswer(
                "0",
                "SELECT MOD(DATE_PART(NANOSECOND, a), 1000000) FROM (SELECT CURRENT_TIMESTAMP(3) AS a)");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
                "SELECT MOD(NANOSECOND(CURRENT_TIMESTAMP(3)), 1000000)");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [MICROSECOND] for parameter 'DATE_PART date/time "
                    + "part'",
                "SELECT MOD(DATE_PART(MICROSECOND, CURRENT_TIMESTAMP(3)), 1000), MOD(DATE_PART(MILLISECOND, "
                    + "CURRENT_TIMESTAMP(0)), 1000)");
            assertAnswer(
                "0|0",
                "SELECT MOD(DATE_PART(NANOSECOND, CURRENT_TIME(1)), 100000000), MOD(DATE_PART(NANOSECOND, "
                    + "LOCALTIME(5)), 10000)");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void thePrecisionArgument() {
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'LOCALTIMESTAMP': (BOOLEAN)",
                "SELECT LOCALTIMESTAMP(TRUE) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'CURRENT_TIME': (BOOLEAN)",
                "SELECT CURRENT_TIME(FALSE) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'LOCALTIME': (BOOLEAN)",
                "SELECT LOCALTIME(TRUE) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'CURRENT_TIMESTAMP': (DATE)",
                "SELECT CURRENT_TIMESTAMP(CURRENT_DATE) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'CURRENT_TIMESTAMP': (ARRAY)",
                "SELECT CURRENT_TIMESTAMP(ARRAY_CONSTRUCT()) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(CAST(3 AS FLOAT) AS NUMBER(18,0))] for "
                    + "function 'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(3::FLOAT) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TO_NUMBER('3', 18, 0)] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP('3'::VARCHAR) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TO_NUMBER('3')] for function 'CURRENT_TIMESTAMP',"
                    + " parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(TO_NUMBER('3')) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid timestamp scale: -3. Must be "
                    + "between 0 and 9.",
                "SELECT CURRENT_TIMESTAMP(-(3)) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid timestamp scale: -3. Must be "
                    + "between 0 and 9.",
                "SELECT CURRENT_TIMESTAMP(- 3) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(3.5 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(3.50) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(0.5 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(0.5) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(12.345 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(12.345) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TO_NUMBER(SYSTEM$NULL_TO_TEXT(null), 18, 0)] for "
                    + "function 'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(NULL::VARCHAR) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(CAST(3 AS NUMBER(10,2)) AS NUMBER(8,0))] "
                    + "for function 'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(3::NUMBER(10,2)) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(3 AS NUMBER(38,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(CAST(3 AS INT)) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TO_NUMBER(CAST(3 AS VARCHAR(134217728)), 18, 0)] "
                    + "for function 'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(3::VARCHAR) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(-0.5 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(-0.5) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TO_NUMBER('', 18, 0)] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP('') IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TO_NUMBER('abc', 18, 0)] for function "
                    + "'CURRENT_TIME', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIME('abc') IS NOT NULL");
            assertAnswer(
                "true",
                "SELECT CURRENT_TIMESTAMP(3e0) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(3.5 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(3.5e0) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid timestamp scale: 10. Must be "
                    + "between 0 and 9.",
                "SELECT CURRENT_TIMESTAMP(1e1) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [SYSTEM$NULL_TO_FIXED(null)] for function "
                    + "'CURRENT_TIME', parameter 1: must be an integer constant",
                "SELECT LOCALTIME(NULL) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(-3.5 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIME', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIME(-3.5) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'CURRENT_TIMESTAMP': (DATE)",
                "SELECT CURRENT_TIMESTAMP('2024-01-01'::DATE) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [3] for function 'CURRENT_TIMESTAMP', parameter "
                    + "1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(3::NUMBER(1,0)) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(SYSTEM$NULL_TO_FIXED(null) AS NUMBER(8,0))] "
                    + "for function 'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(NULL::NUMBER(10,2)) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(SYSTEM$NULL_TO_REAL(null) AS NUMBER(18,0))] "
                    + "for function 'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(NULL::FLOAT) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [SYSTEM$NULL_TO_FIXED(null)] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP((NULL)) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(3.5 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP((3.5)) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(UNARY PLUS(3.5) AS NUMBER(2,0))] for "
                    + "function 'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(+3.5) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST((CAST(3 AS NUMBER(3,1))) + 0.5 AS NUMBER(3,"
                    + "0))] for function 'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(3 + 0.5) IS NOT NULL");
            assertAnswer(
                "true",
                "SELECT CURRENT_TIMESTAMP(9.0) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid timestamp scale: 10. Must be "
                    + "between 0 and 9.",
                "SELECT CURRENT_TIMESTAMP(10.0) IS NOT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(9.4 AS NUMBER(2,0))] for function "
                    + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
                "SELECT CURRENT_TIMESTAMP(9.4) IS NOT NULL");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
