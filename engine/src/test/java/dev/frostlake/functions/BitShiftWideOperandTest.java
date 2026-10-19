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
 * BITSHIFTLEFT and BITSHIFTRIGHT shift the whole of their first operand in a signed 128-bit integer: a value of 2^63
 * or more keeps its high bits, so a left shift and a right shift undo each other, and a right shift keeps the sign of
 * a wide negative value (live-verified).
 */
public class BitShiftWideOperandTest extends MeasuredCellsTestSupport {

    @Test
    public void aWideOperandKeepsItsHighBits() {
        assertAnswer("2|2|2", "SELECT BITSHIFTRIGHT(BITSHIFTLEFT(1, 100), 99) AS a, "
            + "BITSHIFTRIGHT(1267650600228229401496703205376, 99) AS b, BITSHIFTRIGHT(BITSHIFTLEFT(1, 63), 62) AS c");
        assertShapedAnswer("2361183241434822606848|2361183241434822606848|<ID19>|3",
            "SELECT BITSHIFTLEFT(BITSHIFTLEFT(1, 70), 1) AS d, BITSHIFTLEFT(1, 71) AS e, "
                + "BITSHIFTRIGHT(9223372036854775808, 1) AS f, BITSHIFTRIGHT(BITSHIFTLEFT(3, 80), 80) AS g");
        assertAnswer("1267650600228229401496703205376|NUMBER(38,0)[SB16]|1267650600228229401496703205376|"
            + "-1267650600228229401496703205377", "SELECT BITSHIFTLEFT(1, 100) AS h, "
            + "SYSTEM$TYPEOF(BITSHIFTLEFT(1, 100)) AS t, BITAND(BITSHIFTLEFT(1, 100), BITSHIFTLEFT(1, 100)) AS i, "
            + "BITNOT(BITSHIFTLEFT(1, 100)) AS j");
        assertAnswer("-1267650600228229401496703205376|NUMBER(38,0)[SB16]|1", "SELECT BITSHIFTLEFT(-1, 100), "
            + "SYSTEM$TYPEOF(BITSHIFTRIGHT(BITSHIFTLEFT(1, 100), 99)), BITSHIFTRIGHT(BITSHIFTLEFT(1, 100), 100)");
    }

    @Test
    public void aRightShiftKeepsTheSignOfAWideValue() {
        assertAnswer("-170141183460469231731687303715884105728|85070591730234615865843651857942052864|-1|-4",
            "SELECT BITSHIFTLEFT(1, 127), BITSHIFTLEFT(1, 126), BITSHIFTRIGHT(-1, 1), "
                + "BITSHIFTRIGHT(-BITSHIFTLEFT(1, 100), 98)");
        assertShapedAnswer("-<ID19>|18446744073709551614",
            "SELECT BITSHIFTRIGHT(-9223372036854775809, 1), BITSHIFTLEFT(9223372036854775807, 1)");
        assertAnswer("true|true",
            "SELECT BITSHIFTRIGHT(9223372036854775808, 1) > 0, BITSHIFTRIGHT(-9223372036854775809, 1) < 0");
        assertAnswer("0|0|-1", "SELECT BITSHIFTLEFT(1, 300), BITSHIFTRIGHT(1, 300), BITSHIFTRIGHT(-1, 300)");
    }
}
