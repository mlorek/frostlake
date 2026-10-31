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
 * REPEAT, LPAD, RPAD and SPACE read their length as the account does: REPEAT's count times its text's length goes
 * into a 32-bit length typed as that product (FIXED[SB4](29,0) for 1e10, the count's precision and eighteen), a
 * padded length past 32 bits is "Numeric value '...' is out of range", a FLOAT one goes through FIXED[SB4](9,0),
 * and a length that fits but builds past 128 MB of text or 64 MB of binary is "String '(padded string)' is too
 * long and would be truncated" (all live-verified).
 */
public class StringBuilderLengthTest extends MeasuredCellsTestSupport {

    @Test
    public void aLengthIsReadAndBoundedAsTheAccountDoes() {
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](29,0){not null}, value 10000000000",
            "SELECT REPEAT('a', 1e10)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](28,0){not null}, value 6000000000",
            "SELECT REPEAT('ab', 3e9)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](29,0){not null}, value 30000000000",
            "SELECT REPEAT('abc', 1e10)");
        assertAnswer("REFUSED String '(padded string)' is too long and would be truncated",
            "SELECT REPEAT('a', 2147483647)");
        assertAnswer("REFUSED String '(padded string)' is too long and would be truncated", "SELECT REPEAT('ab', 1e9)");
        assertAnswer("REFUSED String '(padded string)' is too long and would be truncated",
            "SELECT REPEAT('a', 134217729)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](29,0){not null}, value -10000000000",
            "SELECT REPEAT('a', -1e10)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](38,0){not null}, value 10000000000",
            "SELECT REPEAT('a', 1e10::INT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](28,0){not null}, value 3000000000",
            "SELECT REPEAT('a', 3000000000)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LPAD': (VARCHAR(1), NUMBER(28,0), BINARY(67108864))",
            "SELECT REPEAT(TO_BINARY('41', 'HEX'), 3e9)");
        assertAnswer("REFUSED String '(padded string)' is too long and would be truncated",
            "SELECT LPAD('a', 1e9, 'b')");
        assertAnswer("REFUSED String '(padded string)' is too long and would be truncated", "SELECT SPACE(1e9)");
        assertAnswer("REFUSED String '(padded string)' is too long and would be truncated", "SELECT RPAD('a', 1e9)");
        assertAnswer("REFUSED String '(padded string)' is too long and would be truncated",
            "SELECT LPAD('a', 134217729, 'b')");
        assertAnswer("REFUSED Numeric value '2147483648' is out of range", "SELECT LPAD('a', 2147483648, 'b')");
        assertAnswer("REFUSED String '(padded string)' is too long and would be truncated",
            "SELECT LPAD('a', 2147483647, 'b')");
        assertAnswer("REFUSED Numeric value '2147483648' is out of range", "SELECT SPACE(2147483648)");
        assertAnswer("REFUSED Numeric value '-2147483649' is out of range", "SELECT SPACE(-2147483649)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range",
            "SELECT RPAD(TO_BINARY('41', 'HEX'), 3e9, TO_BINARY('42', 'HEX'))");
        assertAnswer("REFUSED String '(padded string)' is too long and would be truncated",
            "SELECT LPAD(TO_BINARY('41', 'HEX'), 1e9, TO_BINARY('42', 'HEX'))");
        assertAnswer("REFUSED String '(padded string)' is too long and would be truncated",
            "SELECT LPAD(TO_BINARY('41', 'HEX'), 67108865, TO_BINARY('42', 'HEX'))");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 3e+09",
            "SELECT LPAD('a', 3e9::FLOAT, 'b')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 3e+09",
            "SELECT REPEAT('a', 3e9::FLOAT)");
        assertAnswer("Table RN successfully created.", "CREATE TABLE rn (n NUMBER(38,0))");
        assertAnswer("1", "INSERT INTO rn VALUES (10000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](38,0){nullable}, value 10000000000",
            "SELECT REPEAT('a', n) FROM rn");
        assertAnswer("REFUSED Numeric value '10000000000' is out of range", "SELECT LPAD('a', n, 'b') FROM rn");
    }
}
