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
 * A numeric argument written as a text that spells no number is "Numeric value 'b' is not recognized" for
 * ADD_MONTHS, HEX_ENCODE, BASE64_ENCODE, BITCOUNT, NEGATE and ARRAY_REPEAT as for ABS, and a FLOAT's NaN or
 * infinity given to a bitwise function is out of the fixed-point range it is read in (live-verified).
 */
public class NumericTextArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void textThatSpellsNoNumber() {
        assertAnswer(
            "REFUSED Numeric value 'b' is not recognized",
            "SELECT ADD_MONTHS('2024-01-31'::DATE, 'b')");
        assertAnswer(
            "REFUSED Numeric value 'b' is not recognized",
            "SELECT HEX_ENCODE('a', 'b')");
        assertAnswer(
            "REFUSED Numeric value 'b' is not recognized",
            "SELECT BASE64_ENCODE('a', 'b')");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT BITNOT('NaN'::FLOAT)");
        assertAnswer(
            "REFUSED Numeric value 'b' is not recognized",
            "SELECT ABS('b')");
        assertAnswer(
            "REFUSED Numeric value 'b' is not recognized",
            "SELECT BITCOUNT('b')");
        assertAnswer(
            "REFUSED Numeric value 'b' is not recognized",
            "SELECT NEGATE('b')");
        assertAnswer(
            "REFUSED Numeric value 'b' is not recognized",
            "SELECT ARRAY_REPEAT(1, 'b')");
        assertAnswer(
            "2024-03-31",
            "SELECT ADD_MONTHS('2024-01-31'::DATE, '2')");
        assertAnswer(
            "61",
            "SELECT HEX_ENCODE('a', '0')");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT BITNOT('inf'::FLOAT)");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT BITAND('NaN'::FLOAT, 1)");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT BITSHIFTLEFT('NaN'::FLOAT, 1)");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "SELECT BASE64_ENCODE('a', 'x', '+/=')");
        assertAnswer(
            "2024-03-31",
            "SELECT ADD_MONTHS('2024-01-31'::DATE, '1.5')");
    }
}
