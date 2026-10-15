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
 * FACTORIAL, CHR and the FROM_PARTS functions read a FLOAT's NaN or infinity, and a whole number too wide for them,
 * as the account does (live-verified): a NaN or an infinity is refused as the fixed-point number the function reads
 * it as, a date or time part past 32 bits is out of range, FACTORIAL's argument and a nanosecond part past 64 bits are
 * not recognized, a nanosecond part is read in 64 bits, and CHR reads the low 32 bits of its code.
 */
public class WholeNumberArgumentTest extends MeasuredCellsTestSupport {

    private static final String RANGE = "REFUSED Number out of representable range: type ";

    @Test
    public void aNanOrAnInfinityIsRefusedAsTheFixedPointNumberItIsReadAs() {
        assertCells(new String[][] {
            {"SELECT FACTORIAL('NaN'::FLOAT)", RANGE + "FIXED[SB1](2,0){not null}, value nan"},
            {"SELECT FACTORIAL('-inf'::FLOAT)", RANGE + "FIXED[SB1](2,0){not null}, value -inf"},
            {"SELECT CHR('inf'::FLOAT)", RANGE + "FIXED[SB8](18,0){not null}, value inf"},
            {"SELECT CHR('NaN'::FLOAT)", RANGE + "FIXED[SB8](18,0){not null}, value nan"},
            {"SELECT DATE_FROM_PARTS(2024, 'NaN'::FLOAT, 1)", RANGE + "FIXED[SB4](9,0){not null}, value nan"},
            {"SELECT DATE_FROM_PARTS('inf'::FLOAT, 1, 1)", RANGE + "FIXED[SB4](9,0){not null}, value inf"},
            {"SELECT TIMESTAMP_FROM_PARTS(2024, 1, 1, 0, 0, 'NaN'::FLOAT)",
                RANGE + "FIXED[SB4](9,0){not null}, value nan"},
            {"SELECT TIME_FROM_PARTS(0, 0, 'inf'::FLOAT)", RANGE + "FIXED[SB4](9,0){not null}, value inf"},
            {"SELECT TIMESTAMP_FROM_PARTS(2024, 1, 1, 0, 0, 0, 'NaN'::FLOAT)",
                RANGE + "FIXED[SB8](18,0){not null}, value nan"},
        });
    }

    @Test
    public void aWholeNumberTooWideIsRefusedRatherThanCut() {
        assertCells(new String[][] {
            {"SELECT FACTORIAL(18446744073709551621)",
                "REFUSED Numeric value '18446744073709551621' is not recognized"},
            {"SELECT DATE_FROM_PARTS(2024, 1, 4294967297) AS d1", "REFUSED Numeric value '4294967297' is out of range"},
            {"SELECT DATE_FROM_PARTS(4294969320, 1, 1) AS d2", "REFUSED Numeric value '4294969320' is out of range"},
            {"SELECT DATE_FROM_PARTS(2024, 4294967309, 1) AS d5", "REFUSED Numeric value '4294967309' is out of range"},
            {"SELECT DATE_FROM_PARTS(2024, 1, 2147483648) AS d3", "REFUSED Numeric value '2147483648' is out of range"},
            {"SELECT DATE_FROM_PARTS(2024, 1, -2147483649)", "REFUSED Numeric value '-2147483649' is out of range"},
            {"SELECT TIMESTAMP_FROM_PARTS(2024, 1, 1, 4294967296, 0, 0) AS t1",
                "REFUSED Numeric value '4294967296' is out of range"},
            {"SELECT TIME_FROM_PARTS(4294967297, 0, 0) AS t2", "REFUSED Numeric value '4294967297' is out of range"},
            {"SELECT FACTORIAL(33), FACTORIAL(2.5)", "8683317618811886495518194401280000000|6"},
            {"SELECT FACTORIAL(34)", "REFUSED Numeric value is out of range, error: 34 is too large for factorial "
                + "function."},
        });
        assertShapedAnswer("REFUSED Numeric value '<ID19>' is not recognized", "SELECT FACTORIAL(9223372036854775808)");
        assertShapedAnswer("REFUSED Numeric value '-<ID19>' is not recognized",
            "SELECT FACTORIAL(-9223372036854775809)");
    }

    @Test
    public void aNanosecondPartIsReadInSixtyFourBits() {
        assertCells(new String[][] {
            {"SELECT TO_VARCHAR(TIMESTAMP_FROM_PARTS(2024, 1, 1, 0, 0, 0, 4294967296), 'HH24:MI:SS.FF9')",
                "00:00:04.294967296"},
            {"SELECT TO_VARCHAR(TIME_FROM_PARTS(0, 0, 0, 4294967296), 'HH24:MI:SS.FF9')", "00:00:04.294967296"},
        });
        assertShapedAnswer("REFUSED Numeric value '<ID19>' is not recognized",
            "SELECT TIMESTAMP_FROM_PARTS(2024, 1, 1, 0, 0, 0, 9223372036854775808)");
    }

    @Test
    public void chrReadsTheLowThirtyTwoBitsOfItsCode() {
        assertCells(new String[][] {
            {"SELECT CHR(18446744073709551681)", "A"},
            {"SELECT CHR(4294967361)", "A"},
            {"SELECT CHR(-4294967231)", "A"},
            {"SELECT CHR(4294967296)", "\u0000"},
            {"SELECT CHR(9223372036854775808)", "\u0000"},
            {"SELECT CHR(2147483713)", "REFUSED Invalid character code 2147483713 in the CHR input"},
            {"SELECT CHR(-1)", "REFUSED Invalid character code -1 in the CHR input"},
            {"SELECT CHR(1114112)", "REFUSED Invalid character code 1114112 in the CHR input"},
        });
    }
}
