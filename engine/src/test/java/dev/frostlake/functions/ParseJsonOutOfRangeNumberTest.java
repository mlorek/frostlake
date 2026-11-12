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
 * PARSE_JSON keeps a plainly written number exact, but numbers that "cannot be stored as fixed-point decimals due to
 * range or scale limitations, are stored as DOUBLE values" (documented): an integer past the signed 128-bit range,
 * within which a 39-digit integer stays exact (live-verified, see JsonPlainDecimalWidthTest), and a fraction of a
 * scale past 37. A 42-digit integer was an INTEGER of all its digits.
 */
public class ParseJsonOutOfRangeNumberTest extends MeasuredCellsTestSupport {

    private static final String DIGITS_42 = "123456789012345678901234567890123456789012";

    /** 2^127 - 1, the largest integer held exactly. */
    private static final String LARGEST = "170141183460469231731687303715884105727";

    @Test
    public void aNumberPastTheRangeOrTheScaleIsADouble() {
        assertAnswer("DOUBLE", "SELECT TYPEOF(PARSE_JSON('" + DIGITS_42 + "'))");
        assertAnswer("DOUBLE", "SELECT TYPEOF(PARSE_JSON('" + "9".repeat(39) + "'))");
        assertAnswer("DOUBLE", "SELECT TYPEOF(PARSE_JSON('170141183460469231731687303715884105728'))");
        assertAnswer("DOUBLE", "SELECT TYPEOF(PARSE_JSON('0.1234567890123456789012345678901234567890'))");
        assertAnswer("DOUBLE", "SELECT TYPEOF(PARSE_JSON('{\"n\":" + DIGITS_42 + "}'):n)");
        assertAnswer("DOUBLE", "SELECT TYPEOF(TRY_PARSE_JSON('[" + DIGITS_42 + "]')[0])");
        assertAnswer("[1.234567890123457e+41]", "SELECT PARSE_JSON('[" + DIGITS_42 + "]')");
    }

    @Test
    public void aNumberWithinThemStaysExact() {
        assertAnswer("INTEGER", "SELECT TYPEOF(PARSE_JSON('" + LARGEST + "'))");
        assertAnswer(LARGEST, "SELECT PARSE_JSON('" + LARGEST + "')::VARCHAR");
        assertAnswer("INTEGER", "SELECT TYPEOF(PARSE_JSON('-170141183460469231731687303715884105728'))");
        assertAnswer("DECIMAL", "SELECT TYPEOF(PARSE_JSON('0.1234567890123456789012345678901234567'))");
    }
}
