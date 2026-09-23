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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A VARIANT holding a bare DOUBLE has THREE texts, and which one a conversion prints depends on where
 * the double came from (live-verified cell by cell):
 *
 * <ul>
 *   <li>a FLOAT constant the compiler folds, through TO_VARIANT, converts to VARCHAR in the shortest
 *       round-trip spelling held to ten significant digits plus one per decade — {@code 1.5}, {@code 3.0},
 *       {@code 1.0E18}, {@code -0.0}, {@code 1.2345678901234568E16} in full;</li>
 *   <li>a computed double through TO_VARIANT (SQRT(2) is {@code 1.414213562}; see
 *       FoldedDoubleVariantTextTest), a double read from JSON text, or one extracted from a container,
 *       converts in the FLOAT text —
 *       {@code 100} for {@code 1e2}, {@code 1e+18}, {@code 1e-07}, {@code -0};</li>
 *   <li>TO_JSON, and a double inside a container under any conversion, keep the fifteen-decimal
 *       scientific form ({@code 1.500000000000000e+00}).</li>
 * </ul>
 *
 * <p>The DECIMAL and INTEGER families convert as they display, whatever their provenance.
 */
public class VariantDoubleTextTest extends BaseDatabaseTest {

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aFloatThroughToVariantConvertsInTheShortestForm() {
        assertEquals("1.5", cell("SELECT TO_VARIANT(1.5::FLOAT)::VARCHAR"));
        assertEquals("3.0", cell("SELECT TO_VARIANT(3::FLOAT)::VARCHAR"));
        assertEquals("1.0E18", cell("SELECT TO_VARIANT(1e18::FLOAT)::VARCHAR"));
        assertEquals("-0.0", cell("SELECT TO_VARIANT(-0.0::FLOAT)::VARCHAR"));
        assertEquals("0.1", cell("SELECT TO_VARIANT(0.1::FLOAT)::VARCHAR"));
        assertEquals("1.0E7", cell("SELECT TO_VARIANT(1e7::FLOAT)::VARCHAR"));
        assertEquals("1000000.0", cell("SELECT TO_VARIANT(1e6::FLOAT)::VARCHAR"));
        assertEquals("0.001", cell("SELECT TO_VARIANT(1e-3::FLOAT)::VARCHAR"));
        assertEquals("1.0E-4", cell("SELECT TO_VARIANT(1e-4::FLOAT)::VARCHAR"));
        assertEquals("1.0E-7", cell("SELECT TO_VARIANT(1e-7::FLOAT)::VARCHAR"));
        assertEquals("1.0E15", cell("SELECT TO_VARIANT(1e15::FLOAT)::VARCHAR"));
        assertEquals("1.0E21", cell("SELECT TO_VARIANT(1e21::FLOAT)::VARCHAR"));
        assertEquals("1.23456789123E8", cell("SELECT TO_VARIANT(123456789.123::FLOAT)::VARCHAR"));
        assertEquals("1.2345678901234568E16", cell("SELECT TO_VARIANT(12345678901234567::FLOAT)::VARCHAR"));
        assertEquals("0.333333", cell("SELECT TO_VARIANT((1.0/3)::FLOAT)::VARCHAR"));
        assertEquals("100.0", cell("SELECT TO_VARIANT(100::FLOAT)::VARCHAR"));
        assertEquals("0.5", cell("SELECT TO_VARIANT(0.5::FLOAT)::VARCHAR"));
        assertEquals("-1.5", cell("SELECT TO_VARIANT(-1.5::FLOAT)::VARCHAR"));
        assertEquals("9999999.9", cell("SELECT TO_VARIANT(9999999.9::FLOAT)::VARCHAR"));
        assertEquals("1.23456785E7", cell("SELECT TO_VARIANT(12345678.5::FLOAT)::VARCHAR"));
        assertEquals("NaN", cell("SELECT TO_VARIANT('NaN'::FLOAT)::VARCHAR"));
        assertEquals("Infinity", cell("SELECT TO_VARIANT('inf'::FLOAT)::VARCHAR"));
        assertEquals("-Infinity", cell("SELECT TO_VARIANT('-inf'::FLOAT)::VARCHAR"));
        // Ten significant digits, one more per decade: SQRT(2) is cut where the sixteen-digit value is not.
        assertEquals("1.414213562", cell("SELECT TO_VARIANT(SQRT(2))::VARCHAR"));
        assertEquals("3.0", cell("SELECT TO_VARIANT(1.5::FLOAT * 2)::VARCHAR"));
        assertEquals("1.5", cell("SELECT TO_VARIANT(SUM(f))::VARCHAR FROM (SELECT 1.5::FLOAT AS f)"));
        // The same spelling through every string conversion, and it is what LENGTH and || see.
        assertEquals("1.5", cell("SELECT TO_VARCHAR(TO_VARIANT(1.5::FLOAT))"));
        assertEquals("3.0", cell("SELECT TO_CHAR(TO_VARIANT(3::FLOAT))"));
        assertEquals("3", cell("SELECT LENGTH(TO_VARIANT(1.5::FLOAT)::VARCHAR)"));
        assertEquals("100.0", cell("SELECT TO_VARIANT(100::FLOAT)::VARCHAR || ''"));
        assertEquals("1.5", cell("SELECT TO_VARIANT(1.5::FLOAT)::FLOAT::VARCHAR"));
        assertEquals("1.50", cell("SELECT TO_VARIANT(1.5::FLOAT)::NUMBER(5,2)"));
    }

    @Test
    public void aDoubleReadFromJsonTextConvertsInTheFloatText() {
        assertEquals("100", cell("SELECT PARSE_JSON('1e2')::VARCHAR"));
        assertEquals("100", cell("SELECT PARSE_JSON('1.0e2')::VARCHAR"));
        assertEquals("100", cell("SELECT PARSE_JSON('1E2')::VARCHAR"));
        assertEquals("2.5", cell("SELECT PARSE_JSON('2.5e0')::VARCHAR"));
        assertEquals("3", cell("SELECT PARSE_JSON('3e0')::VARCHAR"));
        assertEquals("3", cell("SELECT PARSE_JSON('3.0e0')::VARCHAR"));
        assertEquals("35", cell("SELECT PARSE_JSON('3.5e1')::VARCHAR"));
        assertEquals("1e+18", cell("SELECT PARSE_JSON('1e18')::VARCHAR"));
        assertEquals("1e+21", cell("SELECT PARSE_JSON('1e21')::VARCHAR"));
        assertEquals("1e-07", cell("SELECT PARSE_JSON('1e-7')::VARCHAR"));
        assertEquals("10000000", cell("SELECT PARSE_JSON('1e7')::VARCHAR"));
        assertEquals("1000000", cell("SELECT PARSE_JSON('1e6')::VARCHAR"));
        assertEquals("123456789.123", cell("SELECT PARSE_JSON('1.23456789123e8')::VARCHAR"));
        assertEquals("-0", cell("SELECT PARSE_JSON('-0e0')::VARCHAR"));
        assertEquals("100", cell("SELECT PARSE_JSON('1e2')::FLOAT::VARCHAR"));
        assertEquals("DOUBLE", cell("SELECT TYPEOF(PARSE_JSON('1e2'))"));
        // A member read out of a container is a plain double again, whatever put it there.
        assertEquals("1.5", cell("SELECT PARSE_JSON('[1.5e0]')[0]::VARCHAR"));
        assertEquals("100", cell("SELECT PARSE_JSON('[1e2, 2.5e0, 1.5]')[0]::VARCHAR"));
        assertEquals("100", cell("SELECT PARSE_JSON('{\"a\":1e2}'):a::VARCHAR"));
        assertEquals("1.5", cell("SELECT GET(OBJECT_CONSTRUCT('a', 1.5::FLOAT), 'a')::VARCHAR"));
        assertEquals("100", cell("SELECT OBJECT_CONSTRUCT('a', 1e2::FLOAT):a::VARCHAR"));
    }

    @Test
    public void theOtherFamiliesAndTheContainersAreUntouched() {
        assertEquals("1.5", cell("SELECT PARSE_JSON('1.5')::VARCHAR"));
        assertEquals("1.5", cell("SELECT PARSE_JSON('1.50')::VARCHAR"));
        assertEquals("100", cell("SELECT PARSE_JSON('100.0')::VARCHAR"));
        assertEquals("12345678901234567890", cell("SELECT PARSE_JSON('12345678901234567890')::VARCHAR"));
        assertEquals("12345678901234567890.5", cell("SELECT PARSE_JSON('12345678901234567890.5')::VARCHAR"));
        assertEquals("DECIMAL", cell("SELECT TYPEOF(PARSE_JSON('1.5'))"));
        assertEquals("1.5", cell("SELECT TO_VARIANT(1.5::NUMBER(3,1))::VARCHAR"));
        assertEquals("1.5", cell("SELECT TO_VARIANT(1.50::NUMBER(4,2))::VARCHAR"));
        assertEquals("100", cell("SELECT TO_VARIANT(100.0)::VARCHAR"));
        assertEquals("INTEGER", cell("SELECT TYPEOF(TO_VARIANT(100.0))"));
        // A double INSIDE a container converts in the fifteen-decimal form, and so does TO_JSON of a bare one.
        assertEquals("{\"a\":1.500000000000000e+00}", cell("SELECT OBJECT_CONSTRUCT('a', 1.5::FLOAT)::VARCHAR"));
        assertEquals("[1.500000000000000e+00]", cell("SELECT ARRAY_CONSTRUCT(1.5::FLOAT)::VARCHAR"));
        assertEquals("{\"a\":1.500000000000000e+00}", cell("SELECT TO_VARCHAR(OBJECT_CONSTRUCT('a', 1.5::FLOAT))"));
        assertEquals("1.500000000000000e+00", cell("SELECT TO_JSON(TO_VARIANT(1.5::FLOAT))"));
    }
}
