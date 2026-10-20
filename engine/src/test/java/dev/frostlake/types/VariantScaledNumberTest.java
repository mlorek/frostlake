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
 * A whole number of a scaled NUMBER cast to VARIANT keeps no trailing zeros, as TO_VARIANT gives it: 100.00 reads
 * 100 in its JSON text and its VARCHAR, where a fraction keeps its digits (all live-verified).
 */
public class VariantScaledNumberTest extends MeasuredCellsTestSupport {

    @Test
    public void aScaledWholeNumberCastToVariantKeepsNoTrailingZeros() {
        assertAnswer("100", "SELECT TO_JSON(100::NUMBER(10,2)::VARIANT)");
        assertAnswer("100", "SELECT (100::NUMBER(10,2)::VARIANT)::VARCHAR");
        assertAnswer("1.5|0.0000001|100",
            "SELECT TO_JSON(1.50::VARIANT), TO_JSON(0.0000001::VARIANT), TO_JSON(100.00::VARIANT)");
        assertAnswer("100|2.5",
            "SELECT TO_VARIANT(100::NUMBER(10,2))::VARCHAR, TO_JSON(TO_VARIANT(2.50::NUMBER(5,2)))");
        assertAnswer("[100]|{\"a\":1.5}",
            "SELECT ARRAY_CONSTRUCT(100::NUMBER(10,2))::VARCHAR, OBJECT_CONSTRUCT('a', 1.50::NUMBER(5,2))::VARCHAR");
        assertAnswer("100|1.5", "SELECT TO_JSON(PARSE_JSON('100.00')), TO_JSON(PARSE_JSON('1.50'))");
        assertAnswer("-100|0", "SELECT (-100::NUMBER(10,2)::VARIANT)::VARCHAR, (0::NUMBER(10,2)::VARIANT)::VARCHAR");
    }
}
