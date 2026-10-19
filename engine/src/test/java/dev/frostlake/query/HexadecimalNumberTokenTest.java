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
 * A number written 0x and hexadecimal digits is one token no statement reads, refused where it stands; the
 * prefix alone is a number that stops, told by the character after it. The clock functions take an integer
 * constant as their precision and echo anything else as the plan converts it (live-verified).
 */
public class HexadecimalNumberTokenTest extends MeasuredCellsTestSupport {

    @Test
    public void theClocksPrecisionArgument() {
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(PI() AS NUMBER(18,0))] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(PI())");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [TO_NUMBER(CAST(3 AS VARIANT), 18, 0)] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(TO_VARIANT(3))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(SQRT(CAST(4 AS FLOAT)) AS NUMBER(18,0))] for "
                + "function 'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(SQRT(4))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(1.5 AS NUMBER(2,0))] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(1.5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [TO_NUMBER('3', 18, 0)] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP('3')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [TO_NUMBER(CAST(3 AS VARIANT), 18, 0)] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(3::VARIANT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [TO_NUMBER(PARSE_JSON('3'), 18, 0)] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(PARSE_JSON('3'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(PI() AS NUMBER(18,0))] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT LOCALTIMESTAMP(PI())");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(PI() AS NUMBER(18,0))] for function "
                + "'CURRENT_TIME', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIME(PI())");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(EXP(CAST(1 AS FLOAT)) AS NUMBER(18,0))] for "
                + "function 'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(EXP(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(CAST(1 AS FLOAT) AS NUMBER(18,0))] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(1::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [RANDOM()] for function 'CURRENT_TIMESTAMP', "
                + "parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(RANDOM())");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CURRENT_TIMESTAMP': (ARRAY)",
            "SELECT CURRENT_TIMESTAMP(ARRAY_CONSTRUCT(3))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [TO_NUMBER(CAST(3.5 AS VARIANT), 18, 0)] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(TO_VARIANT(3.5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [TO_NUMBER(CAST('3' AS VARIANT), 18, 0)] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT CURRENT_TIMESTAMP(TO_VARIANT('3'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CURRENT_TIMESTAMP': (OBJECT)",
            "SELECT CURRENT_TIMESTAMP(OBJECT_CONSTRUCT())");
    }

    @Test
    public void aHexadecimalNumberAndTypesOfPi() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0x3'.",
            "SELECT 0x3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0x3'.",
            "SELECT 0x3 + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0xFF'.",
            "SELECT 0xFF");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '0x3'.",
            "SELECT 1 + 0x3");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(PI())");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(PI() + 1)");
        assertAnswer(
            "3.0|FLOAT[DOUBLE]",
            "SELECT ROUND(PI()), SYSTEM$TYPEOF(ROUND(PI()))");
        assertAnswer(
            "FLOAT[DOUBLE]|FLOAT[DOUBLE]|FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(PI() * 2), SYSTEM$TYPEOF(-PI()), SYSTEM$TYPEOF(ABS(PI()))");
    }

    @Test
    public void prefixesAndOtherRadixes() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0x1F'.",
            "SELECT 0x1F");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0X1f'.",
            "SELECT 0X1f + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '0x10'.",
            "SELECT 1 + 0x10");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 9 near '<EOF>'. | syntax error line "
                + "1 at position 9 unexpected '<EOF>'.",
            "SELECT 0x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 9 near '71'.",
            "SELECT 0xG1");
        assertAnswer(
            "1F",
            "SELECT x'1F'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0x1F'.",
            "SELECT 0x1F AS c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0x1F'.",
            "SELECT 0x1F FROM ta");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '0x1F'.",
            "SELECT 1 AS 0x1F");
        assertAnswer(
            "0",
            "SELECT 0b11");
        assertAnswer(
            "0",
            "SELECT 0o17");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0x1F'.",
            "SELECT 0x1F.5");
        assertAnswer(
            "1",
            "SELECT 1e0x3");
    }

    @Test
    public void precisionsThatAreNoConstants() {
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [2 + 1] for function 'CURRENT_TIMESTAMP', parameter "
                + "1: must be an integer constant",
            "SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP(2 + 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(CAST(9 AS FLOAT) AS NUMBER(18,0))] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT SYSTEM$TYPEOF(LOCALTIMESTAMP(9.0::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(CAST(3 AS FLOAT) AS NUMBER(18,0))] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP(3::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(CAST(1.5 AS FLOAT) AS NUMBER(18,0))] for "
                + "function 'CURRENT_TIME', parameter 1: must be an integer constant",
            "SELECT SYSTEM$TYPEOF(CURRENT_TIME(1.5::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(SQRT(CAST(4 AS FLOAT)) AS NUMBER(18,0))] for "
                + "function 'CURRENT_TIME', parameter 1: must be an integer constant",
            "SELECT SYSTEM$TYPEOF(LOCALTIME(SQRT(4)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [ROUND(2.6)] for function 'CURRENT_TIMESTAMP', "
                + "parameter 1: must be an integer constant",
            "SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP(ROUND(2.6)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(CEIL(PI()) AS NUMBER(18,0))] for function "
                + "'CURRENT_TIMESTAMP', parameter 1: must be an integer constant",
            "SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP(CEIL(PI())))");
    }
}
