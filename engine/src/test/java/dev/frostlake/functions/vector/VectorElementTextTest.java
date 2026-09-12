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
 * How a VECTOR's elements are SPELLED.
 *
 * <p>★ A FLOAT ELEMENT IS SIX DECIMAL PLACES — {@code %.6f}, rounded, never scientific and never
 * trimmed. An INT element prints plainly. Frostlake printed the shortest text that round-trips at the
 * element's width, and its class comment claimed that as live-verified while citing three values; all
 * three were right about the VALUE and wrong about the TEXT.
 *
 * <p>★ IT IS A FIXED WIDTH, NOT SIGNIFICANT DIGITS, which the extreme magnitudes are here to prove:
 * {@code 1e-20} reads {@code 0.000000} — the value is gone entirely — and {@code 1e20} reads
 * {@code 100000002004087734272.000000}, the float32's exact value written out in full rather than in
 * an exponent. No rule about significant digits produces either.
 *
 * <p>★ AND IT IS A THIRD FLOAT SPELLING in this engine, sharing nothing with the two beside it: a
 * FLOAT cast to VARCHAR is ten SIGNIFICANT digits, and a DOUBLE inside a VARIANT keeps its own text.
 * A vector's SCALAR results follow the float rule and not this one — {@code VECTOR_INNER_PRODUCT} is
 * the control below.
 *
 * <p>The float32 narrowing shows THROUGH the rendering: {@code 123456789.5} reads
 * {@code 123456792.000000}, which is the nearest float32 and not the input.
 *
 * <p>NOT FIXED HERE, each tracked on its own: a vector embedded in a VARIANT (live writes a JSON array
 * of full-precision doubles, Frostlake writes the display text as a quoted string); {@code TO_VARCHAR},
 * {@code ::VARCHAR} and {@code ||} over a vector, which live REFUSES and Frostlake accepts; and
 * {@code SYSTEM$TYPEOF} of one, which answers ARRAY.
 */
public class VectorElementTextTest extends BaseDatabaseTest {

    /** One scalar, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** ★ Six decimal places, whatever the value needs. */
    @Test
    public void afloatElementIsSixDecimalPlaces() {
        assertEquals("[1.000000,2.000000,3.000000]", answer("SELECT [1,2,3]::VECTOR(FLOAT,3)"));
        assertEquals("[1.000000,2.000000,3.000000]",
            answer("SELECT [1.0,2.0,3.0]::VECTOR(FLOAT,3)"), "however the literal was written");
        assertEquals("[1.500000,2.250000]", answer("SELECT [1.5,2.25]::VECTOR(FLOAT,2)"));
        assertEquals("[-1.500000,-0.250000]", answer("SELECT [-1.5,-0.25]::VECTOR(FLOAT,2)"));
        assertEquals("[0.000000,0.000000]", answer("SELECT [0.0,-0.0]::VECTOR(FLOAT,2)"),
            "a negative zero loses its sign");
    }

    /** ★ More decimals than six are ROUNDED away, not truncated. */
    @Test
    public void extradecimalsAreRounded() {
        assertEquals("[0.123457,0.765432]",
            answer("SELECT [0.1234567,0.7654321]::VECTOR(FLOAT,2)"));
        assertEquals("[0.123457]", answer("SELECT [0.1234567890]::VECTOR(FLOAT,1)"));
    }

    /** ★ A FIXED width, not significant digits — the two magnitudes that settle it. */
    @Test
    public void thewidthIsFixedNotSignificant() {
        assertEquals("[0.000000]", answer("SELECT [1e-20]::VECTOR(FLOAT,1)"),
            "a tiny value renders as nothing at all");
        assertEquals("[100000002004087734272.000000]", answer("SELECT [1e20]::VECTOR(FLOAT,1)"),
            "and a huge one is written out in full, never in an exponent");
    }

    /** The float32 narrowing shows through the text. */
    @Test
    public void thefloat32NarrowingShowsThrough() {
        assertEquals("[123456792.000000]", answer("SELECT [123456789.5]::VECTOR(FLOAT,1)"),
            "the nearest float32, not the input");
    }

    /** An INT element prints plainly — the padding is the FLOAT family's alone. */
    @Test
    public void anintElementPrintsPlainly() {
        assertEquals("[1,2,3]", answer("SELECT [1,2,3]::VECTOR(INT,3)"));
        assertEquals("[-1,0,7]", answer("SELECT [-1,0,7]::VECTOR(INT,3)"));
    }

    /** ★ A COMPUTED vector follows the same rule — there is no second spelling for results. */
    @Test
    public void acomputedVectorFollowsTheSameRule() {
        assertEquals("[0.267261,0.534522,0.801784]",
            answer("SELECT VECTOR_NORMALIZE([1,2,3]::VECTOR(FLOAT,3))"));
        assertEquals("[0.333333,0.666667,0.666667]",
            answer("SELECT VECTOR_NORMALIZE([1e-30,2e-30,2e-30]::VECTOR(FLOAT,3))"),
            "including one whose float32 arithmetic could not have produced it");
    }

    /** ★ A SCALAR result does NOT follow it — the control that keeps the rule to vectors. */
    @Test
    public void ascalarResultDoesNotFollowIt() {
        assertEquals("14.0", answer("SELECT VECTOR_INNER_PRODUCT([1,2,3]::VECTOR(FLOAT,3),"
            + " [1,2,3]::VECTOR(FLOAT,3))"));
    }

    /** A STORED vector reads back the same way, and so does one a function trims. */
    @Test
    public void astoredVectorReadsBackTheSameWay() {
        engine.execute("CREATE OR REPLACE TABLE vet (v VECTOR(FLOAT,3), i VECTOR(INT,3))");
        engine.execute("INSERT INTO vet SELECT [1.0,0.1234567,3.5]::VECTOR(FLOAT,3),"
            + " [1,2,3]::VECTOR(INT,3)");
        assertEquals("[1.000000,0.123457,3.500000]", answer("SELECT v FROM vet"));
        assertEquals("[1,2,3]", answer("SELECT i FROM vet"));
        assertEquals("[1.000000,0.123457]", answer("SELECT VECTOR_TRUNC(v, 2) FROM vet"));
    }
}
