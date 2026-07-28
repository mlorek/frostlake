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
 * TO_JSON serializes the VARIANT VALUE. The engine carries VARIANT/ARRAY/OBJECT values as JSON text,
 * so TO_JSON of a semi-structured value returns that JSON compacted — it must NOT re-encode the text
 * into a JSON string ({@code ["a"]}, never {@code "[\"a\"]"} — that double encoding broke vendor
 * assertions of the form {@code TO_JSON(array_col) = '["X"]'}). A non-JSON string is a variant
 * STRING and serializes quoted.
 */
public class ToJsonTest extends BaseDatabaseTest {

    private Object q(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void arrayColumnSerializesAsJsonArray() {
        engine.execute("CREATE TABLE tj_arr (a ARRAY)");
        engine.execute("INSERT INTO tj_arr SELECT ARRAY_CONSTRUCT('LAPTOP_DESKTOP')");
        assertEquals("[\"LAPTOP_DESKTOP\"]", q("SELECT TO_JSON(a) FROM tj_arr"));
        assertEquals(1L, ((Number) q(
            "SELECT COUNT(*) FROM tj_arr WHERE TO_JSON(a) = '[\"LAPTOP_DESKTOP\"]'")).longValue());
    }

    @Test
    public void parsedObjectRoundTripsCompactly() {
        assertEquals("{\"a\":1,\"b\":[true,null]}",
            q("SELECT TO_JSON(PARSE_JSON('{ \"a\": 1, \"b\": [true, null] }'))"));
    }

    @Test
    public void variantNullSerializesAsNullText() {
        assertEquals("null", q("SELECT TO_JSON(PARSE_JSON('null'))"));
    }

    @Test
    public void plainStringSerializesQuoted() {
        assertEquals("\"hello\"", q("SELECT TO_JSON('hello')"));
    }

    @Test
    public void stringWithJsonLikePrefixStaysAString() {
        // "2026-04-20 12:00:00" starts with a valid JSON number token; a lenient parser that ignored
        // trailing tokens would truncate it to 2026. It must serialize as the full quoted string.
        assertEquals("\"2026-04-20 12:00:00\"", q("SELECT TO_JSON('2026-04-20 12:00:00')"));
    }

    @Test
    public void numberSerializesBare() {
        assertEquals("42", String.valueOf(q("SELECT TO_JSON(42)")));
    }

    @Test
    public void nullReturnsNull() {
        assertNull(q("SELECT TO_JSON(NULL)"));
    }

    @Test
    public void nestedConstructedObjectSerializes() {
        assertEquals("{\"k\":[\"v1\",\"v2\"]}",
            q("SELECT TO_JSON(OBJECT_CONSTRUCT('k', ARRAY_CONSTRUCT('v1', 'v2')))"));
    }
}
