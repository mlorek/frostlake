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
 * BITAND, BITOR, BITXOR, BITNOT, GETBIT, BITSHIFTLEFT, BITSHIFTRIGHT and the BITAND_AGG family compute in a signed
 * 128-bit integer: 2^63 and past it keep their bits, a left shift past bit 127 leaves 0, and a value OR-ed past
 * eight bytes is tagged by sixteen (live-verified).
 */
public class BitwiseWideTest extends MeasuredCellsTestSupport {

    @Test
    public void allOneHundredTwentyEightBits() {
        assertShapedAnswer(
            "<ID19>|18446744073709551616|1267650600228229401496703205376",
            "SELECT BITSHIFTLEFT(1, 63), BITSHIFTLEFT(1, 64), BITSHIFTLEFT(1, 100)");
        assertAnswer(
            "85070591730234615865843651857942052864|-170141183460469231731687303715884105728|0|0",
            "SELECT BITSHIFTLEFT(1, 126), BITSHIFTLEFT(1, 127), BITSHIFTLEFT(1, 128), BITSHIFTLEFT(1, 200)");
        assertAnswer(
            "0|-4|-1|-1",
            "SELECT BITSHIFTRIGHT(1, 64), BITSHIFTRIGHT(-8, 1), BITSHIFTRIGHT(-1, 127), BITSHIFTRIGHT(-1, 200)");
        assertAnswer(
            "-2|-170141183460469231731687303715884105728|-85070591730234615865843651857942052864",
            "SELECT BITSHIFTLEFT(-1, 1), BITSHIFTLEFT(-1, 127), BITSHIFTLEFT(3, 126)");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: shift parameter must be greater or equal to zero",
            "SELECT BITSHIFTLEFT(1, -1), BITSHIFTRIGHT(8, -1)");
        assertShapedAnswer(
            "<ID19>|18446744073709551616|-<ID19>",
            "SELECT BITOR(9223372036854775808, 1), BITAND(18446744073709551616, 18446744073709551616), "
                + "BITNOT(9223372036854775808)");
        assertAnswer(
            "18446744073709551614|0|-1",
            "SELECT BITXOR(18446744073709551615, 1), BITNOT(-1), BITNOT(0)");
        assertAnswer(
            "99999999999999999999999999999999999999",
            "SELECT BITOR(99999999999999999999999999999999999999, 1)");
        assertAnswer(
            "NUMBER(38,0)[SB16]|NUMBER(2,0)[SB1]|NUMBER(2,0)[SB1]|NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(BITSHIFTLEFT(1, 1)), SYSTEM$TYPEOF(BITSHIFTRIGHT(1, 1)), SYSTEM$TYPEOF(BITOR(1, "
                + "2)), SYSTEM$TYPEOF(BITNOT(1))");
        assertAnswer(
            "NUMBER(20,0)[SB16]|NUMBER(38,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(BITOR(9223372036854775808, 1)), SYSTEM$TYPEOF(BITAND(1::NUMBER(38,0), 1))");
        assertAnswer(
            "REFUSED SQL compilation error: Error line 1 at position 20 | Integer literal is out of representable "
                + "range: 170141183460469231731687303715884105727",
            "SELECT BITSHIFTLEFT(170141183460469231731687303715884105727, 1)");
        assertAnswer(
            "4|12",
            "SELECT BITSHIFTLEFT(1.5, 1), BITSHIFTLEFT('3', 2)");
        assertAnswer(
            "248",
            "SELECT BIT_LENGTH(BITSHIFTLEFT(1, 100)::VARCHAR)");
        assertAnswer(
            "18446744073709551616|18446744073709551617|1",
            "SELECT BITAND_AGG(v), BITOR_AGG(v), BITXOR_AGG(v) FROM (SELECT 18446744073709551616 AS v UNION ALL "
                + "SELECT 18446744073709551617)");
        assertAnswer(
            "1|0",
            "SELECT GETBIT(BITSHIFTLEFT(1, 100), 100), GETBIT(1, 127)");
    }
}
