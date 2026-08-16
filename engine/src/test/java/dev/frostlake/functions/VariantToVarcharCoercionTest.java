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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Turning a VARIANT into a VARCHAR unwraps a variant STRING to its content; turning a VARCHAR into a
 * VARCHAR does nothing at all. The two look identical in Frostlake, because a variant string is
 * carried as a Java string with its JSON quotes — so the SOURCE's declared type decides, and the two
 * conversions used to be wrong in OPPOSITE directions:
 *
 * <pre>
 * TO_VARCHAR(v:k)          over {"k":"[3,4]"}   [3,4]      5 chars — the content
 * '"[1,2,3]"'::VARCHAR                          "[1,2,3]"  9 chars — a VARCHAR is its own text
 * </pre>
 *
 * <p>{@code TO_VARCHAR} never unwrapped and the cast always unwrapped, so each was right for exactly
 * the case the other got wrong. Lengths are asserted here rather than just the text, because the
 * quotes are the whole question.
 */
public class VariantToVarcharCoercionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE vt (js VARCHAR(50), v VARIANT)");
        engine.execute("INSERT INTO vt SELECT '[1,2]', PARSE_JSON('{\"k\":\"[3,4]\"}')");
    }

    private Object one(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private long length(final String expression) {
        return ((Number) one("SELECT LENGTH(" + expression + ")")).longValue();
    }

    /** A VARCHAR is its own text, quotes included — neither conversion may strip them. */
    @Test
    public void aVarcharKeepsItsOwnQuotes() {
        assertEquals("\"[1,2,3]\"", one("SELECT TO_VARCHAR('\"[1,2,3]\"')"));
        assertEquals(9L, length("TO_VARCHAR('\"[1,2,3]\"')"));
        assertEquals("\"[1,2,3]\"", one("SELECT '\"[1,2,3]\"'::VARCHAR"));
        assertEquals(9L, length("'\"[1,2,3]\"'::VARCHAR"));
    }

    /** A variant STRING contributes its CONTENT, by either spelling. */
    @Test
    public void aVariantStringContributesItsContent() {
        assertEquals("[3,4]", one("SELECT TO_VARCHAR(v:k) FROM vt"));
        assertEquals(5L, ((Number) one("SELECT LENGTH(TO_VARCHAR(v:k)) FROM vt")).longValue());
        assertEquals("[3,4]", one("SELECT v:k::VARCHAR FROM vt"));
        assertEquals(5L, ((Number) one("SELECT LENGTH(v:k::VARCHAR) FROM vt")).longValue());

        assertEquals("abc", one("SELECT TO_VARCHAR(PARSE_JSON('\"abc\"'))"));
        assertEquals(3L, length("TO_VARCHAR(PARSE_JSON('\"abc\"'))"));
        assertEquals("abc", one("SELECT TO_CHAR(PARSE_JSON('\"abc\"'))"));
    }

    /**
     * The case that ties the two halves together: a variant string whose CONTENT reads as JSON is
     * still a string, so it contributes seven characters and not nine.
     */
    @Test
    public void aVariantStringThatReadsAsJsonIsStillJustText() {
        assertEquals("[1,2,3]", one("SELECT TO_VARCHAR(PARSE_JSON('\"[1,2,3]\"'))"));
        assertEquals(7L, length("TO_VARCHAR(PARSE_JSON('\"[1,2,3]\"'))"));
        // and re-reading that text as JSON does give the array, because by then it IS a VARCHAR
        assertEquals("ARRAY", one("SELECT TYPEOF(PARSE_JSON(TO_VARCHAR(PARSE_JSON('\"[1,2,3]\"'))))"));
    }

    /** A VARIANT that is not a string renders its own JSON text. */
    @Test
    public void aNonStringVariantRendersItsJson() {
        assertEquals("[1,2]", one("SELECT TO_VARCHAR(PARSE_JSON('[1,2]'))"));
        assertEquals(5L, length("TO_VARCHAR(PARSE_JSON('[1,2]'))"));
        assertEquals(3L, length("TO_VARCHAR(PARSE_JSON('7.5'))"));
    }

    /** The JSON null flattens to SQL NULL on the way to VARCHAR — it does not render as the text. */
    @Test
    public void theJsonNullBecomesSqlNull() {
        assertNull(one("SELECT TO_VARCHAR(PARSE_JSON('null'))"));
        assertNull(one("SELECT PARSE_JSON('null')::VARCHAR"));
    }

    /** A VARCHAR column holding JSON text is text, and stays text. */
    @Test
    public void aVarcharColumnIsNotUnwrapped() {
        assertEquals("[1,2]", one("SELECT TO_VARCHAR(js) FROM vt"));
        assertEquals(5L, ((Number) one("SELECT LENGTH(js::VARCHAR) FROM vt")).longValue());
    }

    /**
     * The write direction of the same rule: {@code ::VARIANT} is TO_VARIANT, so a VARCHAR becomes a
     * variant STRING and is never parsed. The cast used to hand the text straight through, which made
     * it read back as an ARRAY — and made FLATTEN expand a value that holds no elements at all.
     */
    @Test
    public void castingAVarcharToVariantMakesAVariantString() {
        assertEquals("VARCHAR", one("SELECT TYPEOF('[1,2,3]'::VARIANT)"));
        assertEquals("VARCHAR", one("SELECT TYPEOF(js::VARIANT) FROM vt"));
        assertEquals("[1,2,3]", one("SELECT TO_VARCHAR('[1,2,3]'::VARIANT)"));
        assertEquals(7L, length("TO_VARCHAR('[1,2,3]'::VARIANT)"));
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '[1,2,3]'::VARIANT))").getRowCount());
        // The cast and the function are the same operation, so they agree.
        assertEquals(Boolean.TRUE, one("SELECT '[1,2,3]'::VARIANT = TO_VARIANT('[1,2,3]')"));
    }

    /** Only a VARCHAR source wraps: a value that is already semi-structured passes through as itself. */
    @Test
    public void castingASemiStructuredValueToVariantKeepsIt() {
        assertEquals("ARRAY", one("SELECT TYPEOF(PARSE_JSON('[1,2]')::VARIANT)"));
        assertEquals("VARCHAR", one("SELECT TYPEOF(PARSE_JSON('\"abc\"')::VARIANT)"));
        assertEquals("INTEGER", one("SELECT TYPEOF(123::VARIANT)"));
    }
}
