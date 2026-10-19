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
 * TO_NUMBER and its kin convert to NUMBER(38,0) when the call names no precision, so a text past 38 digits is
 * out of range and the TRY_ forms answer NULL; a text past a width with a scale is not recognized, a VARIANT past
 * the width fails its cast to FIXED, and a FLOAT converts by its binary value. A text past a declared width is
 * out of range while its digits at the target's scale fit the 128-bit carrier, and not recognized once they do
 * not (all live-verified).
 */
public class ToNumberDefaultWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void aCallNamingNoWidthConvertsToNumber38() {
        assertAnswer("REFUSED Numeric value '1e38' is out of range", "SELECT TO_NUMBER('1e38')");
        assertAnswer("REFUSED Numeric value '170141183460469231731687303715884105727' is out of range",
            "SELECT TO_DECIMAL('170141183460469231731687303715884105727')");
        assertAnswer("null", "SELECT TRY_TO_NUMBER('1e38')");
        assertAnswer("REFUSED Numeric value '-1.5e38' is out of range", "SELECT TO_NUMERIC('-1.5e38')");
        assertAnswer("REFUSED Numeric value '99999999999999999999999999999999999999.5' is out of range",
            "SELECT TO_NUMBER('99999999999999999999999999999999999999.5')");
        assertAnswer("REFUSED Numeric value '99999999999999999999999999999999999999.5' is out of range",
            "SELECT '99999999999999999999999999999999999999.5'::NUMBER");
        assertAnswer("REFUSED Numeric value '1e38' is out of range", "SELECT TO_NUMBER('1e38', 38, 0)");
        assertAnswer("99999999999999999999999999999999999999",
            "SELECT TO_NUMBER('99999999999999999999999999999999999999')");
        assertAnswer("99000000000000000000000000000000000000", "SELECT TO_NUMBER('9.9e37')");
        assertAnswer("null", "SELECT TRY_TO_DECIMAL('1e38')");
        assertAnswer("null", "SELECT TRY_TO_NUMBER('99999999999999999999999999999999999999.5')");
        assertAnswer("99999999999999997748809823456034029568", "SELECT TO_NUMBER(1e38::FLOAT)");
        assertAnswer("REFUSED Failed to cast variant value \"1e38\" to FIXED", "SELECT TO_NUMBER('1e38'::VARIANT)");
        assertAnswer("REFUSED Numeric value '1e37' is not recognized", "SELECT TO_NUMBER('1e37', 10, 2)");
        assertAnswer("12.35", "SELECT TO_NUMBER('12.345', 38, 2)");
        assertAnswer("REFUSED Numeric value '1e38' is not recognized", "SELECT TO_NUMBER('1e38', 38, 2)");
        assertAnswer("NUMBER(38,0)[SB1]", "SELECT SYSTEM$TYPEOF(TO_NUMBER('5'))");
        assertAnswer("REFUSED Numeric value '100000000000000000000000000000000000000' is out of range",
            "SELECT TO_NUMBER('100000000000000000000000000000000000000')");
        assertAnswer("null", "SELECT TRY_CAST('1e38' AS NUMBER)");
    }

    @Test
    public void aTextPastTheWidthIsOutOfRangeUntilNoNumberOfItsScaleHoldsIt() {
        assertAnswer("REFUSED Numeric value '1234.56' is out of range", "SELECT TO_NUMBER('1234.56', 3, 1)");
        assertAnswer("REFUSED Numeric value '1234' is out of range", "SELECT TO_NUMBER('1234', 3, 1)");
        assertAnswer("REFUSED Numeric value '1e3' is out of range", "SELECT TO_NUMBER('1e3', 3, 1)");
        assertAnswer("REFUSED Numeric value '1e3' is out of range", "SELECT TO_NUMBER('1e3', 3, 0)");
        assertAnswer("REFUSED Numeric value '1,234.56' is out of range",
            "SELECT TO_NUMBER('1,234.56', '9,999.99', 3, 1)");
        assertAnswer("REFUSED Numeric value '1234.56' is not recognized", "SELECT TO_NUMBER('1234.56', 38, 37)");
        assertAnswer("REFUSED Numeric value '1.5e37' is not recognized", "SELECT TO_NUMBER('1.5e37', 38, 2)");
        assertAnswer("REFUSED Numeric value '15000000000000000000000000000000000000' is not recognized",
            "SELECT TO_NUMBER('15000000000000000000000000000000000000', 38, 2)");
        assertAnswer("REFUSED Numeric value '1e2' is out of range", "SELECT TO_NUMBER('1e2', 3, 1)");
        assertAnswer("10.0", "SELECT TO_NUMBER('1.0e1', 3, 1)");
        assertAnswer("123.4", "SELECT TO_NUMBER('12.34e1', 4, 1)");
        assertAnswer("REFUSED Numeric value '1234' is out of range", "SELECT TO_NUMBER('1234', 3)");
        assertAnswer("REFUSED Numeric value '1e4' is out of range", "SELECT TO_NUMBER('1e4', 3)");
        assertAnswer("REFUSED Numeric value '12.5e0' is out of range", "SELECT TO_NUMBER('12.5e0', 2, 1)");
        assertAnswer("REFUSED Numeric value '-1e3' is out of range", "SELECT TO_NUMBER('-1e3', 3, 1)");
        assertAnswer("REFUSED Numeric value '1E3' is out of range", "SELECT TO_NUMBER('1E3', 3, 1)");
        assertAnswer("REFUSED Numeric value '100.5' is out of range", "SELECT TO_NUMBER('100.5', 3, 1)");
        assertAnswer("REFUSED Numeric value '99.95' is out of range", "SELECT TO_NUMBER('99.95', 3, 1)");
        assertAnswer("REFUSED Numeric value '99.95e0' is out of range", "SELECT TO_NUMBER('99.95e0', 3, 1)");
        assertAnswer("REFUSED Numeric value '1e3' is out of range", "SELECT TO_DECIMAL('1e3', 3, 1)");
        assertAnswer("null", "SELECT TRY_TO_NUMBER('1e3', 3, 1)");
    }
}
