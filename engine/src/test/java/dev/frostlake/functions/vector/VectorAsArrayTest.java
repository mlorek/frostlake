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
package dev.frostlake.functions.vector;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TO_ARRAY reads a vector as the array of its elements. The engine carried the vector's own display text into the
 * array, six decimals a piece, so the array displayed {@code [1.500000,0.123457,3.000000]} and its subscript answered
 * {@code 0.123457}, a decimal.
 *
 * <p>On the account a FLOAT vector's elements are DOUBLEs, each the double its 32-bit element widens to: the array
 * displays them in the fifteen-decimal form every variant DOUBLE takes, a subscript is a DOUBLE whose text is a
 * FLOAT's ({@code 0.1234567016}), and ARRAY_TO_STRING joins that text. An INT vector's elements are whole numbers.
 * Every value here is read as VARCHAR, the server's own text. All live-verified.
 */
public class VectorAsArrayTest extends BaseDatabaseTest {

    private static final String V = "[1.5,0.1234567,3]::VECTOR(FLOAT,3)";

    private static final String ELEMENTS = "[1.500000000000000e+00,1.234567016363144e-01,3.000000000000000e+00]";

    private String text(final String expression) {
        return String.valueOf(engine.executeQuery("SELECT " + expression).getRows().get(0).getValue(0));
    }

    @Test
    public void aFloatVectorsElementsAreDoubles() {
        assertEquals(ELEMENTS, text("TO_ARRAY(" + V + ")::VARCHAR"));
        assertEquals(ELEMENTS, text("TO_JSON(TO_ARRAY(" + V + "))"));
        assertEquals(ELEMENTS, text("TO_VARIANT(TO_ARRAY(" + V + "))::VARCHAR"));
        assertEquals("ARRAY", text("TYPEOF(TO_ARRAY(" + V + "))"));
        assertEquals("DOUBLE", text("TYPEOF(TO_ARRAY(" + V + ")[1])"));
        assertEquals("3", text("ARRAY_SIZE(TO_ARRAY(" + V + "))::VARCHAR"));
        assertEquals("0.1234567016", text("TO_ARRAY(" + V + ")[1]::VARCHAR"));
        assertEquals("0.1234567016", text("TO_ARRAY(" + V + ")[1]::FLOAT::VARCHAR"));
        assertEquals("3", text("TO_ARRAY(" + V + ")[2]::VARCHAR"));
        assertEquals("2.5", text("(TO_ARRAY(" + V + ")[0] + 1)::VARCHAR"));
        assertEquals("[1.000000000000000e+10,1.000000013351432e-10,0.000000000000000e+00]",
            text("TO_ARRAY([1e10,1e-10,0]::VECTOR(FLOAT,3))::VARCHAR"), "each element is its 32-bit value, widened");
    }

    @Test
    public void theArrayBehavesAsAnyArrayOfDoubles() {
        assertEquals("1.5|0.1234567016|3", text("ARRAY_TO_STRING(TO_ARRAY(" + V + "), '|')"));
        assertEquals("[1.500000000000000e+00,1.234567016363144e-01,3.000000000000000e+00,1.5]",
            text("ARRAY_CAT(TO_ARRAY(" + V + "), ARRAY_CONSTRUCT(1.5))::VARCHAR"));
        assertEquals("{\"k\":" + ELEMENTS + "}", text("OBJECT_CONSTRUCT('k', TO_ARRAY(" + V + "))::VARCHAR"));
        assertEquals("[" + ELEMENTS + "]", text("ARRAY_CONSTRUCT(TO_ARRAY(" + V + "))::VARCHAR"));
        assertEquals(ELEMENTS, text("PARSE_JSON(TO_JSON(TO_ARRAY(" + V + ")))::VARCHAR"));
        engine.execute("CREATE TABLE v844 (v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO v844 SELECT " + V);
        final List<String> flattened = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT f.value::VARCHAR FROM v844, LATERAL FLATTEN(INPUT =>"
            + " TO_ARRAY(v)) f ORDER BY f.index").getRows()) {
            flattened.add(String.valueOf(row.getValue(0)));
        }
        assertEquals("[1.5, 0.1234567016, 3]", flattened.toString());
        assertEquals(ELEMENTS, text("TO_ARRAY(v)::VARCHAR FROM v844"));
    }

    @Test
    public void anIntVectorsElementsAreWholeNumbers() {
        assertEquals("[1,2,3]", text("TO_ARRAY([1,2,3]::VECTOR(INT,3))::VARCHAR"));
        assertEquals("1|2|3", text("ARRAY_TO_STRING(TO_ARRAY([1,2,3]::VECTOR(INT,3)), '|')"));
        assertEquals("[[1,2,3]]", text("ARRAY_CONSTRUCT([1,2,3]::VECTOR(INT,3))::VARCHAR"));
    }
}
