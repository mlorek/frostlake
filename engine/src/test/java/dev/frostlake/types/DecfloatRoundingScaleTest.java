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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * ROUND, TRUNC, CEIL and FLOOR round an exact number to a scale read through a FIXED[SB2](4,0) slot (all
 * live-verified): a whole number outside -32768..32767 is out of range, one past 64 bits not recognized, and a FLOAT
 * scale that is NaN, infinite or rounds to ten thousand is refused in the slot's words. A DECFLOAT has no scale span
 * limit within it, so ROUND(DECFLOAT '1e-100', 50) is 0; a FLOAT input takes any scale, rounding to NaN past the
 * double's range.
 */
public class DecfloatRoundingScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void aDecfloatRoundsToAnyScaleTheSlotHolds() {
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-100', 38)::VARCHAR");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-100', 39)::VARCHAR");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-100', 50)::VARCHAR");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-100', 99)::VARCHAR");
        assertAnswer("1e-100", "SELECT ROUND(DECFLOAT '1e-100', 100)::VARCHAR");
        assertAnswer("1e-100", "SELECT ROUND(DECFLOAT '1e-100', 101)::VARCHAR");
        assertAnswer("1e-100", "SELECT ROUND(DECFLOAT '1e-100', 1000)::VARCHAR");
        assertAnswer("1e-100", "SELECT ROUND(DECFLOAT '1e-100', 6176)::VARCHAR");
        assertAnswer("1e-100", "SELECT ROUND(DECFLOAT '1e-100', 16383)::VARCHAR");
        assertAnswer("1e-100", "SELECT ROUND(DECFLOAT '1e-100', 16384)::VARCHAR");
        assertAnswer("REFUSED Numeric value '100000' is out of range",
            "SELECT ROUND(DECFLOAT '1e-100', 100000)::VARCHAR");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-50', -38)::VARCHAR");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-50', -39)::VARCHAR");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-50', -100)::VARCHAR");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-50', -6144)::VARCHAR");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-50', -16383)::VARCHAR");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-50', -16384)::VARCHAR");
        assertAnswer("0|0|0",
            "SELECT TRUNC(DECFLOAT '1e-100', 50)::VARCHAR, FLOOR(DECFLOAT '1e-100', 50)::VARCHAR, CEIL(DECFLOAT "
                + "'-1e-100', 50)::VARCHAR");
        assertAnswer("2e-100|1.5e-100",
            "SELECT ROUND(DECFLOAT '1.5e-100', 100)::VARCHAR, ROUND(DECFLOAT '1.5e-100', 101)::VARCHAR");
        assertAnswer("1e-6000|1e6000",
            "SELECT ROUND(DECFLOAT '1e-6000', 6001)::VARCHAR, ROUND(DECFLOAT '1e6000', -5999)::VARCHAR");
        assertAnswer("123.456|DECFLOAT(38)[DECFLOAT18]",
            "SELECT ROUND(DECFLOAT '123.456', 50)::VARCHAR, SYSTEM$TYPEOF(ROUND(DECFLOAT '123.456', 50))");
        assertAnswer("0", "SELECT ROUND(1e-50::NUMBER(38,37))");
        assertAnswer("REFUSED Invalid parameter value: -37. Reason: Scale too large",
            "SELECT CEIL(1.5::NUMBER(10,2), -37)");
        assertAnswer("REFUSED Invalid parameter value: -38. Reason: Scale too large",
            "SELECT CEIL(1.5::NUMBER(10,2), -38)");
        assertAnswer("1.50", "SELECT ROUND(1.5::NUMBER(10,2), 50)");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1e-100', n)::VARCHAR FROM (SELECT 50 n)");
    }

    @Test
    public void theScaleIsReadThroughASixteenBitSlot() {
        assertAnswer("1.25", "SELECT ROUND(DECFLOAT '1.25', 32767)::VARCHAR");
        assertAnswer("REFUSED Numeric value '32768' is out of range", "SELECT ROUND(DECFLOAT '1.25', 32768)::VARCHAR");
        assertAnswer("0", "SELECT ROUND(DECFLOAT '1.25', -32768)::VARCHAR");
        assertAnswer("REFUSED Numeric value '-32769' is out of range",
            "SELECT ROUND(DECFLOAT '1.25', -32769)::VARCHAR");
        assertAnswer("REFUSED Numeric value '65535' is out of range", "SELECT ROUND(DECFLOAT '1.25', 65535)::VARCHAR");
        assertAnswer("REFUSED Numeric value '65536' is out of range", "SELECT ROUND(DECFLOAT '1.25', 65536)::VARCHAR");
        assertAnswer("REFUSED Numeric value '2147483647' is out of range",
            "SELECT ROUND(DECFLOAT '1.25', 2147483647)::VARCHAR");
        assertAnswer("REFUSED Numeric value '2147483648' is out of range",
            "SELECT ROUND(DECFLOAT '1.25', 2147483648)::VARCHAR");
        assertAnswer("REFUSED Numeric value '-2147483649' is out of range",
            "SELECT ROUND(DECFLOAT '1.25', -2147483649)::VARCHAR");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT ROUND(DECFLOAT '1.25', 10000000000000000000000000)::VARCHAR");
        assertAnswer("1.25", "SELECT ROUND(DECFLOAT '1.25', 1.5)::VARCHAR");
        assertAnswer("1.25", "SELECT ROUND(DECFLOAT '1.25', '2')::VARCHAR");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](4,0){not null}, value inf",
            "SELECT ROUND(DECFLOAT '1.25', 'inf'::FLOAT)::VARCHAR");
        assertAnswer("1.25", "SELECT ROUND(DECFLOAT '1.25', 2.5::FLOAT)::VARCHAR");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](4,0){not null}, value 1e+30",
            "SELECT ROUND(DECFLOAT '1.25', 1e30::FLOAT)::VARCHAR");
        assertAnswer("REFUSED Numeric value '32768' is out of range", "SELECT TRUNC(DECFLOAT '1.25', 32768)::VARCHAR");
        assertAnswer("REFUSED Numeric value '-32769' is out of range", "SELECT CEIL(DECFLOAT '1.25', -32769)::VARCHAR");
        assertAnswer("1.25", "SELECT FLOOR(DECFLOAT '1.25', 32767)::VARCHAR");
        assertAnswer("REFUSED Numeric value '40000' is out of range",
            "SELECT ROUND(DECFLOAT '1.25', n)::VARCHAR FROM (SELECT 40000 n)");
        assertAnswer("REFUSED Numeric value '32768' is out of range", "SELECT ROUND(1.25, 32768)");
        assertAnswer("NaN", "SELECT ROUND(1.25::FLOAT, 32768)");
    }
}
