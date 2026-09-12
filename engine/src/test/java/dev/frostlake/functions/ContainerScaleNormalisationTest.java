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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What happens to a number's SCALE when it enters an OBJECT or an ARRAY. An exact DECIMAL descales —
 * {@code ARRAY_CONSTRUCT(1.00)} is {@code [1]} and its element is an INTEGER — while the DOUBLE family
 * survives being whole, so {@code 1.0::FLOAT} stays a DOUBLE and displays as one,
 * {@code 1.000000000000000e+00}.
 *
 * <p>The two containers used to be wrong in OPPOSITE directions, which is why both halves are asserted
 * side by side here. The array path never descaled at all; the object path descaled so eagerly that it
 * demoted a whole double to an integer and lost the family. They agree now because the normalisation sits
 * at the one point where a SQL value becomes a node, rather than at each constructor.
 *
 * <p>Every builder is covered, not just ARRAY_CONSTRUCT: ARRAY_APPEND in particular hand-rolled its own
 * conversion and pushed every number through a double, which both kept the scale and would have rounded a
 * value past 38 digits.
 */
public class ContainerScaleNormalisationTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cnorm (n NUMBER(10,2))");
        engine.execute("INSERT INTO cnorm VALUES (2.50)");
        engine.execute("CREATE OR REPLACE TABLE cnorm2 (x NUMBER(10,2))");
        engine.execute("INSERT INTO cnorm2 VALUES (1.00), (2.50)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** An exact DECIMAL loses its trailing zeros on the way in. */
    @Test
    public void anExactDecimalDescales() {
        assertEquals("[1]", answer("SELECT ARRAY_CONSTRUCT(1.00)"));
        assertEquals("[1]", answer("SELECT ARRAY_CONSTRUCT(1.0)"));
        assertEquals("[0]", answer("SELECT ARRAY_CONSTRUCT(0.00)"));
        assertEquals("[-1]", answer("SELECT ARRAY_CONSTRUCT(-1.00)"));
        assertEquals("[10]", answer("SELECT ARRAY_CONSTRUCT(10.00)"));
        assertEquals("[1.5]", answer("SELECT ARRAY_CONSTRUCT(1.50)"), "a real fraction is kept");
        assertEquals("[1.1]", answer("SELECT ARRAY_CONSTRUCT(1.10)"));
        assertEquals("[1,2.5,3]", answer("SELECT ARRAY_CONSTRUCT(1.00, 2.50, 3)"));
        assertEquals("[2.5]", answer("SELECT ARRAY_CONSTRUCT(n) FROM cnorm"), "a stored column too");
        assertEquals("[1,undefined]", answer("SELECT ARRAY_CONSTRUCT(1.00, NULL)"),
            "and a SQL NULL is still the undefined element");
    }

    /** The value itself descales, not merely its rendering. */
    @Test
    public void theValueDescalesNotJustTheText() {
        assertEquals("1", answer("SELECT ARRAY_CONSTRUCT(1.00)[0]"));
        assertEquals("INTEGER", answer("SELECT TYPEOF(ARRAY_CONSTRUCT(1.00)[0])"));
        assertEquals("2.5", answer("SELECT ARRAY_CONSTRUCT(n)[0] FROM cnorm"));
        assertEquals("DECIMAL", answer("SELECT TYPEOF(ARRAY_CONSTRUCT(2.50)[0])"),
            "a surviving fraction stays a DECIMAL");
        assertEquals("INTEGER", answer("SELECT TYPEOF(OBJECT_CONSTRUCT('k', 1.00):k)"));
    }

    /** The DOUBLE family survives being whole — the half the object path used to lose. */
    @Test
    public void theDoubleFamilySurvivesBeingWhole() {
        assertEquals("{\"k\":1.000000000000000e+00}", answer("SELECT OBJECT_CONSTRUCT('k', 1.0::FLOAT)"));
        assertEquals("[1.000000000000000e+00]", answer("SELECT ARRAY_CONSTRUCT(1.0::FLOAT)"));
        assertEquals("{\"k\":1.000000000000000e+05}", answer("SELECT OBJECT_CONSTRUCT('k', 100000.0::FLOAT)"));
        assertEquals("[1.000000000000000e+05]", answer("SELECT ARRAY_CONSTRUCT(100000.0::FLOAT)"));
        assertEquals("DOUBLE", answer("SELECT TYPEOF(ARRAY_CONSTRUCT(1.0::FLOAT)[0])"));
        assertEquals("DOUBLE", answer("SELECT TYPEOF(OBJECT_CONSTRUCT('k', 1.0::FLOAT):k)"));
        assertEquals("{\"k\":1.500000000000000e+00}", answer("SELECT OBJECT_CONSTRUCT('k', 1.5::FLOAT)"));
        assertEquals("[1.500000000000000e+00]", answer("SELECT ARRAY_CONSTRUCT(1.5::FLOAT)"));
    }

    /** The two containers agree, nested either way round. */
    @Test
    public void bothContainersAgreeAndNest() {
        assertEquals("{\"k\":1}", answer("SELECT OBJECT_CONSTRUCT('k', 1.00)"));
        assertEquals("{\"k\":1}", answer("SELECT OBJECT_CONSTRUCT('k', 1.0)"));
        assertEquals("{\"k\":2.5}", answer("SELECT OBJECT_CONSTRUCT('k', n) FROM cnorm"));
        assertEquals("{\"k\":[1]}", answer("SELECT OBJECT_CONSTRUCT('k', ARRAY_CONSTRUCT(1.00))"));
        assertEquals("[{\"k\":1}]", answer("SELECT ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('k', 1.00))"));
        assertEquals("[[1]]", answer("SELECT ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1.00))"));
    }

    /** Every builder normalises, not only ARRAY_CONSTRUCT. */
    @Test
    public void everyBuilderNormalises() {
        assertEquals("[1]", answer("SELECT TO_ARRAY(1.00)"));
        assertEquals("[1]", answer("SELECT ARRAY_APPEND(ARRAY_CONSTRUCT(), 1.00)"));
        assertEquals("[1]", answer("SELECT ARRAY_PREPEND(ARRAY_CONSTRUCT(), 1.00)"));
        assertEquals("[1]", answer("SELECT ARRAY_INSERT(ARRAY_CONSTRUCT(), 0, 1.00)"));
        assertEquals("[1,2.5]",
            answer("SELECT ARRAY_CAT(ARRAY_CONSTRUCT(1.00), ARRAY_CONSTRUCT(2.50))"));
        assertEquals("[1,2.5]", answer("SELECT ARRAY_AGG(x) FROM cnorm2"));
        assertEquals("1", answer("SELECT TO_VARIANT(1.00)"));
    }

    /** A 38-digit value must descale WITHOUT being widened to a double. */
    @Test
    public void aThirtyEightDigitValueStaysExact() {
        assertEquals("[12345678901234567890123456789012345678]",
            answer("SELECT ARRAY_CONSTRUCT(12345678901234567890123456789012345678.00)"));
    }

    /** The reader and the renderers agree with the constructors. */
    @Test
    public void theReaderAndRenderersAgree() {
        assertEquals("[1]", answer("SELECT PARSE_JSON('[1.00]')"));
        assertEquals("{\"k\":1}", answer("SELECT PARSE_JSON('{\"k\":1.00}')"));
        assertEquals("1", answer("SELECT PARSE_JSON('1.00')"));
        assertEquals("[1]", answer("SELECT TO_JSON(ARRAY_CONSTRUCT(1.00))"));
        assertEquals("1", answer("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(1.00), ',')"));
    }
}
