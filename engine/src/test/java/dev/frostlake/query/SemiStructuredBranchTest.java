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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.types.DataType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A conditional over the semi-structured family: which pairings live REFUSES, and what type the ones it
 * accepts come out as. The two halves belong together — separating them is what made the refusal look
 * unlandable for so long.
 *
 * <p>An OBJECT or an ARRAY does not meet a scalar, and does not meet each other, at COMPILE time:
 *
 * <pre>
 *   COALESCE(ob, ar)   Can not convert parameter 'VF.AR' of type [ARRAY] into expected type [OBJECT]
 *   COALESCE(ob, i)    … of type [NUMBER(38,0)] into expected type [OBJECT]
 *   COALESCE(s, ob)    … of type [OBJECT] into expected type [VARCHAR(5)]
 * </pre>
 *
 * <p>But a container DOES meet a VARIANT, and the result takes the FIRST branch's type rather than
 * widening to a supertype — {@code COALESCE(ob, va)} is OBJECT while {@code COALESCE(va, ob)} is
 * VARIANT. That is the same first-branch rule the refusal sentence names as the "expected type".
 *
 * <p><b>Why the two halves are one change.</b> Adding the refusal alone turned the vendor gate red with
 * no SQL error anywhere — a loader's rows silently came out different. The cause was the missing FOLD:
 * a conditional over containers fell through to the VARCHAR(16777216) placeholder, a
 * {@code CREATE TABLE … AS SELECT CASE … END AS drivers} therefore built a VARCHAR column, and the next
 * statement to read that column met a string where the query had written a container — which the new
 * refusal then, correctly, rejected. Typing the fold removes the wrong VARCHAR at its source and the
 * refusal has nothing left to trip over.
 */
public class SemiStructuredBranchTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE vf (id INT, va VARIANT, ob OBJECT, ar ARRAY,"
            + " s VARCHAR(5), i INT)");
        engine.execute("INSERT INTO vf (id) VALUES (1)");
    }

    /** The declared type name of the expression's result column. */
    private String typeOf(final String expr) {
        final DataType type = engine.executeQuery("SELECT " + expr + " AS c FROM vf")
            .getColumns().get(0).getDataType();
        return type == null ? "null" : type.getName();
    }

    /** The message of the refusal the expression raises, or its type when there is none. */
    private String outcome(final String expr) {
        try {
            return typeOf(expr);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** The type the column takes when a CTAS stores it — the surface a later statement reads. */
    private String storedType(final String expr) {
        engine.execute("CREATE OR REPLACE TABLE vfc AS SELECT " + expr + " AS c FROM vf");
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE vfc");
        return rs.next() ? String.valueOf(rs.getValue(1)) : "<none>";
    }

    /** A container beside a scalar is refused, and the sentence names the offending operand. */
    @Test
    public void aContainerBesideAScalarIsRefused() {
        assertEquals("SQL compilation error: Can not convert parameter 'VF.I' of type"
            + " [NUMBER(38,0)] into expected type [OBJECT]", outcome("COALESCE(ob, i)"));
        assertEquals("SQL compilation error: Can not convert parameter 'VF.I' of type"
            + " [NUMBER(38,0)] into expected type [ARRAY]", outcome("COALESCE(ar, i)"));
        assertEquals("SQL compilation error: Can not convert parameter 'VF.S' of type"
            + " [VARCHAR(5)] into expected type [OBJECT]", outcome("COALESCE(ob, s)"));
        assertEquals("SQL compilation error: Can not convert parameter 'VF.OB' of type"
            + " [OBJECT] into expected type [VARCHAR(5)]", outcome("COALESCE(s, ob)"),
            "and the other way round, where the SCALAR is what the first branch expects");
    }

    /** An OBJECT does not meet an ARRAY either, in either order. */
    @Test
    public void theTwoContainersDoNotMeetEachOther() {
        assertEquals("SQL compilation error: Can not convert parameter 'VF.AR' of type"
            + " [ARRAY] into expected type [OBJECT]", outcome("COALESCE(ob, ar)"));
        assertEquals("SQL compilation error: Can not convert parameter 'VF.OB' of type"
            + " [OBJECT] into expected type [ARRAY]", outcome("COALESCE(ar, ob)"));
    }

    /** CASE and IFF carry the same rule as COALESCE — it is the conditional, not the spelling. */
    @Test
    public void everyConditionalSpellingAgrees() {
        assertEquals("SQL compilation error: Can not convert parameter 'VF.I' of type"
            + " [NUMBER(38,0)] into expected type [OBJECT]",
            outcome("CASE WHEN id = 1 THEN ob ELSE i END"));
        assertEquals("SQL compilation error: Can not convert parameter 'VF.S' of type"
            + " [VARCHAR(5)] into expected type [ARRAY]", outcome("IFF(id = 1, ar, s)"));
    }

    /** A container DOES meet a VARIANT, and the FIRST branch decides the result's type. */
    @Test
    public void aContainerBesideAVariantTakesTheFirstBranchType() {
        assertEquals("OBJECT", typeOf("COALESCE(ob, va)"));
        assertEquals("VARIANT", typeOf("COALESCE(va, ob)"), "the same pair, written the other way");
        assertEquals("ARRAY", typeOf("COALESCE(ar, va)"));
        assertEquals("VARIANT", typeOf("COALESCE(va, ar)"));
    }

    /** The loader shape that made this matter, in all three conditional spellings. */
    @Test
    public void theConstructedContainerShapeIsTyped() {
        assertEquals("OBJECT",
            typeOf("CASE WHEN va IS NOT NULL THEN OBJECT_CONSTRUCT('k', TRUE) ELSE va END"));
        assertEquals("VARIANT",
            typeOf("CASE WHEN va IS NOT NULL THEN va ELSE OBJECT_CONSTRUCT('k', TRUE) END"));
        assertEquals("OBJECT", typeOf("COALESCE(OBJECT_CONSTRUCT('k', TRUE), va)"));
        assertEquals("VARIANT", typeOf("COALESCE(va, OBJECT_CONSTRUCT('k', TRUE))"));
        assertEquals("OBJECT", typeOf("IFF(id = 1, OBJECT_CONSTRUCT('k', TRUE), va)"));
    }

    /** The bare types the fold is built from, which must not have moved. */
    @Test
    public void theBareTypesAreUnchanged() {
        assertEquals("VARIANT", typeOf("va"));
        assertEquals("OBJECT", typeOf("ob"));
        assertEquals("ARRAY", typeOf("ar"));
        assertEquals("OBJECT", typeOf("OBJECT_CONSTRUCT('k', TRUE)"));
        assertEquals("ARRAY", typeOf("ARRAY_CONSTRUCT(1, 2)"));
        assertEquals("VARCHAR", typeOf("COALESCE(va, s)"),
            "a VARIANT beside a VARCHAR is the one pairing that does NOT take the first branch");
    }

    /**
     * What a CTAS STORES, which is the surface the whole thing turns on: a column built from one of
     * these conditionals must arrive as a container, because the next statement to read it will pair it
     * with one and be refused if it arrived as a string.
     */
    @Test
    public void aCtasStoresTheContainerAndNotAPlaceholder() {
        assertEquals("OBJECT",
            storedType("CASE WHEN va IS NOT NULL THEN OBJECT_CONSTRUCT('k', TRUE) ELSE va END"));
        assertEquals("OBJECT", storedType("COALESCE(ob, va)"));
        assertEquals("VARIANT", storedType("va"));
        assertEquals("OBJECT", storedType("ob"));
    }
}
