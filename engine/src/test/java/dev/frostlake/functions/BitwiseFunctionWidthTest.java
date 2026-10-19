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
 * The bitwise functions shift within 128 bits, a right shift keeping the sign. BITOR, which declares one digit
 * more than its widest argument, is tagged by the value it answers over whole numbers or texts written out —
 * BITOR(12, 10) is [SB1] — where the others, and a call over a fraction, keep the width they declare
 * (live-verified).
 */
public class BitwiseFunctionWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void shiftsWithin128Bits() {
        assertAnswer(
            "85070591730234615865843651857942052864|NUMBER(38,0)[SB16]",
            "SELECT BITSHIFTLEFT(1, 126), SYSTEM$TYPEOF(BITSHIFTLEFT(1, 126))");
        assertAnswer(
            "-2|NUMBER(38,0)[SB16]",
            "SELECT BITSHIFTLEFT(-1, 1), SYSTEM$TYPEOF(BITSHIFTLEFT(-1, 1))");
        assertAnswer(
            "-170141183460469231731687303715884105728|NUMBER(38,0)[SB16]",
            "SELECT BITSHIFTLEFT(3, 127), SYSTEM$TYPEOF(BITSHIFTLEFT(3, 127))");
        assertAnswer(
            "0|NUMBER(38,0)[SB16]",
            "SELECT BITSHIFTLEFT(1, 200), SYSTEM$TYPEOF(BITSHIFTLEFT(1, 200))");
        assertAnswer(
            "4|NUMBER(38,0)[SB16]",
            "SELECT BITSHIFTLEFT(1.5, 1), SYSTEM$TYPEOF(BITSHIFTLEFT(1.5, 1))");
        assertAnswer(
            "8|NUMBER(38,0)[SB16]",
            "SELECT BITSHIFTLEFT('1', 3), SYSTEM$TYPEOF(BITSHIFTLEFT('1', 3))");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: shift parameter must be greater or equal to zero",
            "SELECT BITSHIFTRIGHT(16, -1), SYSTEM$TYPEOF(BITSHIFTRIGHT(16, -1))");
        assertAnswer(
            "-9|NUMBER(2,0)[SB1]",
            "SELECT BITSHIFTRIGHT(-17, 1), SYSTEM$TYPEOF(BITSHIFTRIGHT(-17, 1))");
        assertAnswer(
            "18446744073709551616|NUMBER(38,0)[SB16]",
            "SELECT BITSHIFTLEFT(1, 64), SYSTEM$TYPEOF(BITSHIFTLEFT(1, 64))");
        assertAnswer(
            "128|NUMBER(4,0)[SB2]",
            "SELECT BITSHIFTRIGHT(1024, 3), SYSTEM$TYPEOF(BITSHIFTRIGHT(1024, 3))");
        assertAnswer(
            "null|NUMBER(38,0)[SB1]",
            "SELECT BITSHIFTLEFT(1, NULL), SYSTEM$TYPEOF(BITSHIFTLEFT(1, NULL))");
        assertAnswer(
            "2|NUMBER(2,0)[SB1]",
            "SELECT BITSHIFTRIGHT(16, 2.6), SYSTEM$TYPEOF(BITSHIFTRIGHT(16, 2.6))");
    }

    @Test
    public void theValueOfACallOverLiterals() {
        assertAnswer(
            "127|NUMBER(4,0)[SB1]",
            "SELECT BITOR(100, 27), SYSTEM$TYPEOF(BITOR(100, 27))");
        assertAnswer(
            "-256|NUMBER(3,0)[SB2]",
            "SELECT BITXOR(-1, 255), SYSTEM$TYPEOF(BITXOR(-1, 255))");
        assertAnswer(
            "301|NUMBER(19,0)[SB2]",
            "SELECT BITOR('300', 1), SYSTEM$TYPEOF(BITOR('300', 1))");
        assertAnswer(
            "296|NUMBER(4,0)[SB2]",
            "SELECT BITAND(300, 1000), SYSTEM$TYPEOF(BITAND(300, 1000))");
        assertAnswer(
            "0|NUMBER(2,0)[SB1]",
            "SELECT BITNOT(-1), SYSTEM$TYPEOF(BITNOT(-1))");
        assertAnswer(
            "-128|NUMBER(3,0)[SB2]",
            "SELECT BITNOT(127), SYSTEM$TYPEOF(BITNOT(127))");
        assertAnswer(
            "-129|NUMBER(3,0)[SB2]",
            "SELECT BITNOT(128), SYSTEM$TYPEOF(BITNOT(128))");
        assertAnswer(
            "null|NUMBER(2,0)[SB1]",
            "SELECT BITXOR(12, NULL), SYSTEM$TYPEOF(BITXOR(12, NULL))");
        assertAnswer(
            "70001|NUMBER(6,0)[SB4]",
            "SELECT BITOR(70000, 1), SYSTEM$TYPEOF(BITOR(70000, 1))");
        assertAnswer(
            "40001|NUMBER(5,0)[SB4]",
            "SELECT BITXOR(40000, 1), SYSTEM$TYPEOF(BITXOR(40000, 1))");
        assertAnswer(
            "14|NUMBER(3,0)[SB1]",
            "SELECT BIT_OR(12, 10), SYSTEM$TYPEOF(BIT_OR(12, 10))");
        assertAnswer(
            "301|NUMBER(3,0)[SB2]",
            "SELECT BIT_XOR(300, 1), SYSTEM$TYPEOF(BIT_XOR(300, 1))");
        assertAnswer(
            "-301|NUMBER(3,0)[SB2]",
            "SELECT BIT_NOT(300), SYSTEM$TYPEOF(BIT_NOT(300))");
        assertAnswer(
            "-129|NUMBER(3,0)[SB2]",
            "SELECT BITAND(-129, -1), SYSTEM$TYPEOF(BITAND(-129, -1))");
        assertAnswer(
            "-299|NUMBER(4,0)[SB2]",
            "SELECT BITOR(-300, 1), SYSTEM$TYPEOF(BITOR(-300, 1))");
    }
}
