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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Snowflake stores OBJECT / VARIANT values in a canonical form: object keys are sorted alphabetically
 * (objects are unordered) and a whole-valued decimal drops its scale (406.0 -> 406). Frostlake keeps
 * OBJECT/ARRAY values as JSON text and compares them by that text, so two objects that differ only in
 * key order or number scale must serialize identically — otherwise EXCEPT / DISTINCT / = would wrongly
 * treat them as distinct.
 */
public class ObjectCanonicalizationTest extends BaseDatabaseTest {

    private String str(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void objectConstructSortsKeys() {
        assertEquals("{\"a\":1,\"b\":2,\"c\":3}",
            str("SELECT OBJECT_CONSTRUCT('c', 3, 'a', 1, 'b', 2)"));
    }

    @Test
    public void wholeValuedDecimalDropsScale() {
        assertEquals("{\"s\":406}", str("SELECT OBJECT_CONSTRUCT('s', ROUND(405.958))"));
    }

    @Test
    public void numberBeyondDoublePrecisionEmbedsExactly() {
        // NUMBER(38,0) values exceed double precision; doubleValue() turned 21000000006420544706
        // into 21000000006420546000 inside VARIANT payloads.
        assertEquals("{\"r\":21000000006420544706}",
            str("SELECT OBJECT_CONSTRUCT('r', 21000000006420544706)"));
        assertEquals("[21000000006420544706]",
            str("SELECT ARRAY_CONSTRUCT(21000000006420544706)"));
    }

    @Test
    public void parseJsonSortsKeysAndNormalizesNumbers() {
        assertEquals("{\"a\":406,\"b\":2}", str("SELECT PARSE_JSON('{\"b\":2,\"a\":406.0}')"));
    }

    @Test
    public void nestedObjectsAreCanonicalizedRecursively() {
        assertEquals("{\"z\":{\"x\":1,\"y\":2}}",
            str("SELECT PARSE_JSON('{\"z\":{\"y\":2,\"x\":1}}')"));
    }

    @Test
    public void arraysKeepOrderButCanonicalizeElements() {
        // Array order is preserved (arrays are ordered); each object element is still key-sorted.
        assertEquals("[{\"a\":1,\"b\":2},{\"a\":3}]",
            str("SELECT PARSE_JSON('[{\"b\":2,\"a\":1},{\"a\":3.0}]')"));
    }

    @Test
    public void differentlyOrderedObjectsCompareEqualUnderExcept() {
        // {a,b} minus {b,a} must be empty: same object, different textual key order.
        final ResultSet rs = engine.executeQuery(
            "SELECT OBJECT_CONSTRUCT('a', 1, 'b', 2) EXCEPT SELECT OBJECT_CONSTRUCT('b', 2, 'a', 1)");
        assertEquals(0, rs.getRowCount());
    }

    @Test
    public void scaleNormalizationMakesObjectsCompareEqual() {
        // {"s":406} from an integer must equal {"s":406} from a rounded decimal (406.0 -> 406).
        final ResultSet rs = engine.executeQuery(
            "SELECT OBJECT_CONSTRUCT('s', 406) EXCEPT SELECT OBJECT_CONSTRUCT('s', ROUND(405.958))");
        assertEquals(0, rs.getRowCount());
    }

    @Test
    public void parseJsonNullStaysDistinctFromSqlNull() {
        // Canonicalization must not disturb the JSON-null-as-text "null" sentinel.
        assertEquals("null", str("SELECT PARSE_JSON('null')"));
        assertNull(engine.executeQuery("SELECT PARSE_JSON(NULL)").getRows().get(0).getValue(0));
    }

    @Test
    public void objectInsertAndDeleteKeepCanonicalKeyOrder() {
        assertEquals("{\"a\":1,\"b\":2}", str("SELECT OBJECT_INSERT(OBJECT_CONSTRUCT('b', 2), 'a', 1)"));
        assertEquals("{\"apple\":1,\"schema_version\":\"1.3.0\"}",
            str("SELECT OBJECT_INSERT(OBJECT_INSERT(OBJECT_CONSTRUCT(), 'schema_version', '1.3.0'::VARIANT, TRUE), 'apple', 1::VARIANT, TRUE)"));
        assertEquals("{\"a\":1,\"c\":3}",
            str("SELECT OBJECT_DELETE(OBJECT_CONSTRUCT('c', 3, 'b', 2, 'a', 1), 'b')"));
    }
}
