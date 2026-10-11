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
 * SYSTEM$TYPEOF names a small literal by the digits it has written out: 1e-7 and 0.0000001 are NUMBER(8,7),
 * 1e-20 NUMBER(21,20) and 1.5e-7 NUMBER(9,8), where the value's own exponent form was once counted as its
 * digits. A literal past 37 decimals is a FLOAT. Every answer is live-verified.
 */
public class SmallLiteralTypeOfTest extends MeasuredCellsTestSupport {

    @Test
    public void theTypeASmallLiteralHas() {
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        assertAnswer(
            "1E-7|NUMBER(8,7)[SB1]|0.0000001",
            "SELECT 1e-7 AS a, SYSTEM$TYPEOF(1e-7) AS b, TO_VARCHAR(1e-7) AS c");
        assertShape(
            "A:NUMBER(8,7), B:VARCHAR(134217728), C:VARCHAR(134217728)",
            "SELECT 1e-7 AS a, SYSTEM$TYPEOF(1e-7) AS b, TO_VARCHAR(1e-7) AS c");
        assertAnswer(
            "NUMBER(21,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(1e20)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(1e-300)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-7)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(1.5e1)");
        assertAnswer(
            "NUMBER(21,20)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-20)");
        assertAnswer(
            "NUMBER(3,2)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-2)");
        assertAnswer(
            "NUMBER(4,3)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-3)");
        assertAnswer(
            "NUMBER(11,10)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-10)");
        assertAnswer(
            "NUMBER(38,37)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-37)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(1e-38)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(1e-39)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(2e-7)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(12e-7)");
        assertAnswer(
            "NUMBER(9,8)[SB1]",
            "SELECT SYSTEM$TYPEOF(1.5e-7)");
        assertAnswer(
            "NUMBER(10,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-7 + 0)");
        assertAnswer(
            "NUMBER(6,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(123e-5)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(-1e-7)");
        assertAnswer(
            "NUMBER(9,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(+1e-7)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(1E-7)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-07)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(0e-7)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e0)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e1)");
        assertAnswer(
            "NUMBER(38,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(1e37)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(1e38)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(1e39)");
        assertAnswer(
            "NUMBER(2,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(10e-2)");
        assertAnswer(
            "NUMBER(2,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(100e-3)");
        assertAnswer(
            "NUMBER(2,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-1)");
        assertAnswer(
            "NUMBER(5,4)[SB1]",
            "SELECT SYSTEM$TYPEOF(2.5e-3)");
        assertAnswer(
            "NUMBER(10,8)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-7::NUMBER(10,8))");
        assertAnswer(
            "NUMBER(9,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-7 * 2)");
        assertAnswer(
            "NUMBER(9,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-7 + 1e-7)");
        assertAnswer(
            "NUMBER(3,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e+2)");
        assertAnswer(
            "NUMBER(5,4)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-4)");
        assertAnswer(
            "NUMBER(6,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-5)");
        assertAnswer(
            "NUMBER(7,6)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-6)");
        assertAnswer(
            "NUMBER(9,8)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-8)");
        assertAnswer(
            "NUMBER(10,9)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-9)");
        assertAnswer(
            "NUMBER(7,6)[SB1]",
            "SELECT SYSTEM$TYPEOF(10e-7)");
        assertAnswer(
            "NUMBER(6,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(100e-7)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(5e-7)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(9e-7)");
        assertAnswer(
            "NUMBER(10,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e-7 - 0)");
        assertAnswer(
            "NUMBER(9,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(ABS(1e-7))");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF((1e-7))");
        assertAnswer(
            "NUMBER(13,12)[SB4]",
            "SELECT SYSTEM$TYPEOF(1e-7 / 1)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(0.0000001)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(1.0e-7)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(1.00e-7)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "BEGIN RETURN SYSTEM$TYPEOF(1e-7); END;");
        assertAnswer(
            "NUMBER(21,0)[SB16]|FLOAT[DOUBLE]|NUMBER(8,7)[SB1]|NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(1e20), SYSTEM$TYPEOF(1e-300), SYSTEM$TYPEOF(1e-7), SYSTEM$TYPEOF(1.5e1)");
        assertAnswer(
            "1E-20|0.01|2E-7|1E-37|1.0E-38",
            "SELECT 1e-20 AS a, 1e-2 AS b, 2e-7 AS c, 1e-37 AS d, 1e-38 AS e");
    }
}
