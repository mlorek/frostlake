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

/**
 * A VARIANT holding a STRING shows its CONTENT when read as a WHOLE VALUE, and keeps its JSON quotes
 * only as a CONTAINER MEMBER (live-verified cell by cell). It is not cosmetic: LENGTH counts the
 * content and concatenation joins it bare.
 *
 * <p>★ THE DISPLAY IS GENUINELY AMBIGUOUS AND LIVE ACCEPTS THAT: a variant string holding
 * {@code [1,2]} displays exactly like the array. TYPEOF and variant comparison separate them — never
 * the rendering — so equality, ordering, hashing and every JSON-consuming subsystem read the quoted
 * canonical text instead.
 */
public class VariantStringDisplayTest extends BaseDatabaseTest {

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aWholeVariantStringDisplaysItsContent() {
        assertEquals("cdefg", cell("SELECT TO_VARIANT('cdefg')"));
        assertEquals("cdefg", cell("SELECT 'cdefg'::VARIANT"));
        assertEquals("cdefg", cell("SELECT PARSE_JSON('\"cdefg\"')"));
        assertEquals("", cell("SELECT TO_VARIANT('')"));
        assertEquals("a\"b", cell("SELECT TO_VARIANT('a\"b')"));
    }

    @Test
    public void aStoredVariantStringReadsBackBare() {
        engine.execute("CREATE OR REPLACE TABLE vsd (v VARIANT)");
        engine.execute("INSERT INTO vsd SELECT TO_VARIANT('cdefg')");
        assertEquals("cdefg", cell("SELECT v FROM vsd"));
    }

    @Test
    public void containerMembersKeepTheirQuotes() {
        assertEquals("[\"a\",\"b\"]",
            cell("SELECT TO_JSON(ARRAY_CONSTRUCT('a','b'))"));
        assertEquals("{\"k\":\"v\"}",
            cell("SELECT TO_JSON(OBJECT_CONSTRUCT('k','v'))"));
        assertEquals("v", cell("SELECT OBJECT_CONSTRUCT('k','v'):k"));
        assertEquals("a", cell("SELECT GET(ARRAY_CONSTRUCT('a','b'), 0)"));
    }

    @Test
    public void theConversionsAreUnchanged() {
        assertEquals("\"cdefg\"", cell("SELECT TO_JSON(TO_VARIANT('cdefg'))"));
        assertEquals("cdefg", cell("SELECT TO_VARCHAR(TO_VARIANT('cdefg'))"));
    }

    @Test
    public void itIsNotCosmetic() {
        assertEquals("5", cell("SELECT LENGTH(TO_VARIANT('cdefg'))"));
        assertEquals("cdefgx", cell("SELECT TO_VARIANT('cdefg') || 'x'"));
    }

    @Test
    public void theAmbiguityWithAStructuralLookalikeIsAccepted() {
        assertEquals("[1,2]", cell("SELECT TO_VARIANT('[1,2]')"));
        assertEquals("VARCHAR", cell("SELECT TYPEOF(TO_VARIANT('[1,2]'))"));
    }
}
