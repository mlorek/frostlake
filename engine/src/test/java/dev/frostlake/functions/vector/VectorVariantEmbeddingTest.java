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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A VECTOR inside a VARIANT — an ARRAY of its elements, at a width that is neither of the other two.
 *
 * <p>★ THE SAME ELEMENT HAS THREE SPELLINGS, and each surface owns one:
 * <ul>
 *   <li>DISPLAY pads to six decimals — {@code 0.123457};</li>
 *   <li>the VARIANT embedding writes SIXTEEN significant digits — {@code 0.1234567016363144} — which
 *       is the float32 widened to float64 and printed one digit shorter than Java's shortest
 *       round-tripping form;</li>
 *   <li>TO_JSON writes the fifteen-decimal scientific form — {@code 1.234567016363144e-01}.</li>
 * </ul>
 * Sharing one renderer between any two of them makes one wrong, which is why the cells below pin all
 * three against each other rather than each on its own.
 *
 * <p>★ IT IS AN ARRAY THAT IS NOT AN ARRAY. The member prints with brackets, TYPEOF calls it VECTOR
 * rather than ARRAY, and every semi-structured accessor answers NULL over it — element access, a path
 * into it, ARRAY_SIZE. Frostlake used to embed the DISPLAY TEXT as a JSON string, so the member read
 * back as a VARCHAR and no consumer could tell it from text that merely looked like a list.
 */
public class VectorVariantEmbeddingTest extends BaseDatabaseTest {

    /** The element that separates the three spellings: 0.1234567f is not exact in either width. */
    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE vv (v VECTOR(FLOAT, 3), i VECTOR(INT, 2))");
        engine.execute("INSERT INTO vv SELECT [1.0, 0.1234567, 3.5]::VECTOR(FLOAT, 3),"
            + " [7, 8]::VECTOR(INT, 2)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ The embedding: an array of full-precision numbers, through all three constructors. */
    @Test
    public void theembeddingIsAnArrayOfFullPrecisionNumbers() {
        assertEquals("[1.0,0.1234567016363144,3.5]", answer("SELECT TO_VARIANT(v) FROM vv"),
            "sixteen significant digits — not the six-decimal display form, and not a quoted string");
        assertEquals("{\"k\":[1.0,0.1234567016363144,3.5]}",
            answer("SELECT OBJECT_CONSTRUCT('k', v) FROM vv"));
        assertEquals("[[1.0,0.1234567016363144,3.5]]", answer("SELECT ARRAY_CONSTRUCT(v) FROM vv"));
        assertEquals("[1.0,0.1234567016363144,3.5]", answer("SELECT v::VARIANT FROM vv"),
            "★ the cast spelling of the conversion must agree with the function spelling");
    }

    /** ★ An INT vector writes whole numbers — the family is not uniformly floating-point. */
    @Test
    public void anintVectorWritesIntegers() {
        assertEquals("[7,8]", answer("SELECT TO_VARIANT(i) FROM vv"),
            "never [7.0,8.0]: the element type decides how the member is spelled");
        assertEquals("{\"k\":[7,8]}", answer("SELECT OBJECT_CONSTRUCT('k', i) FROM vv"));
    }

    /** ★ The member knows it is a VECTOR, at the root and nested. */
    @Test
    public void themberReportsItsOwnKind() {
        assertEquals("VECTOR", answer("SELECT TYPEOF(TO_VARIANT(v)) FROM vv"),
            "★ not ARRAY, though it prints like one");
        assertEquals("VECTOR", answer("SELECT TYPEOF(OBJECT_CONSTRUCT('k', v):k) FROM vv"),
            "and the kind survives being fetched back out of an object");
    }

    /**
     * ★ The accessors do NOT reach into it — the cell that proves it is not merely an array.
     */
    @Test
    public void theaccessorsAnswerNull() {
        assertEquals("null", answer("SELECT TO_VARIANT(v)[0] FROM vv"),
            "the elements are visible in the text and unreachable through the accessors");
        assertEquals("null", answer("SELECT OBJECT_CONSTRUCT('k', v):k[1] FROM vv"));
        assertEquals("null", answer("SELECT ARRAY_SIZE(TO_VARIANT(v)) FROM vv"));
    }

    /** ★ TO_JSON's own spelling — the third one, and the reason a shared renderer breaks something. */
    @Test
    public void tojsonWritesTheScientificForm() {
        assertEquals("[1.000000000000000e+00,1.234567016363144e-01,3.500000000000000e+00]",
            answer("SELECT TO_JSON(TO_VARIANT(v)) FROM vv"),
            "the same elements again, at fifteen decimals in exponent form");
    }

    /**
     * ★ DISPLAY IS UNTOUCHED, and the round trip lands back on it — the guard on the other two rules.
     */
    @Test
    public void displayAndTheRoundTripKeepTheSixDecimalForm() {
        assertEquals("[1.000000,0.123457,3.500000]", answer("SELECT v FROM vv"),
            "the display rule is narrower than it looks: it stops at the boundary into a VARIANT");
        assertEquals("[1.000000,0.123457,3.500000]",
            answer("SELECT TO_VARIANT(v)::VECTOR(FLOAT, 3) FROM vv"),
            "★ and a variant read back as a vector displays as a vector again");
    }
}
