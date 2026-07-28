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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A JavaScript UDF's ARRAY/OBJECT/VARIANT parameters must arrive as NATIVE JS values regardless of
 * how the engine carries the argument — JSON text or a Java container. A host List has no JS Array
 * protocol, so a body's {@code SRC.forEach(...)} threw
 * "TypeError: (intermediate value).forEach is not a function" whenever a variant path handed the
 * argument over as a List (the vendor {@code child_array(SRC ARRAY, PROPERTY VARCHAR)} shape).
 */
public class JavaScriptSemiStructuredArgTest extends BaseDatabaseTest {

    @BeforeEach
    public void createUdf() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION js_child_array(SRC ARRAY, PROPERTY VARCHAR)
            RETURNS ARRAY
            LANGUAGE JAVASCRIPT
            AS $$
                if (!SRC) {
                    return undefined;
                }
                let values = [];
                let props = PROPERTY.split('.')
                SRC.forEach(e => {
                    let child = e;
                    for (let i = 0 ; i < props.length ; ++i) {
                        child = child[props[i]];
                        if (child == null) {
                            break;
                        }
                    }
                    if (child) {
                        if (Array.isArray(child)) {
                            child.forEach(e2 => values.push(e2));
                        } else {
                            values.push(child);
                        }
                    }
                });
                return values;
            $$""");
    }

    private Object q(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    private String normalized(final Object value) {
        return String.valueOf(value).replace(" ", "");
    }

    @Test
    public void arrayArgFromVariantPath() {
        assertEquals("[\"reader_one\",\"reader_two\"]", normalized(q("""
            SELECT js_child_array(
                PARSE_JSON('{"overview": {"details": [
                    {"first_seen": 1, "source_name": "reader_one"},
                    {"first_seen": 2, "source_name": "reader_two"}]}}'):overview.details,
                'source_name')""")));
    }

    @Test
    public void arrayArgFromArrayAgg() {
        engine.execute("CREATE TABLE js_src (n VARCHAR)");
        engine.execute("INSERT INTO js_src VALUES ('a'), ('b')");
        assertEquals("[\"a\",\"b\"]", normalized(q(
            "SELECT js_child_array(ARRAY_AGG(OBJECT_CONSTRUCT('n', n)), 'n') FROM (SELECT n FROM js_src ORDER BY n)")));
    }

    @Test
    public void arrayArgFromConstructor() {
        assertEquals("[\"x\"]", normalized(q(
            "SELECT js_child_array(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('k', OBJECT_CONSTRUCT('v', 'x'))), 'k.v')")));
    }

    @Test
    public void nullArrayReturnsNull() {
        assertNull(q("SELECT js_child_array(PARSE_JSON('{\"a\": 1}'):missing, 'x')"));
    }

    @Test
    public void objectArgToArrayParamWrapsLikeToArray() {
        // Snowflake implicit VARIANT->ARRAY conversion wraps a non-array in a one-element array;
        // vendor code passes a nested OBJECT to `SRC ARRAY` and indexes the result with [0].
        assertEquals("[\"test.email@xyz.com\"]", normalized(q("""
            SELECT js_child_array(
                PARSE_JSON('{"contact": {"email_address": "test.email@xyz.com"}}'):contact,
                'email_address')""")));
    }

    @Test
    public void scalarArgToArrayParamWrapsLikeToArray() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION js_arr_len(SRC ARRAY)
            RETURNS DOUBLE
            LANGUAGE JAVASCRIPT
            AS $$ return SRC ? SRC.length : -1; $$""");
        assertEquals(1L, ((Number) q("SELECT js_arr_len(PARSE_JSON('{\"v\": \"solo\"}'):v)")).longValue());
        assertEquals(2L, ((Number) q("SELECT js_arr_len(PARSE_JSON('[1, 2]'))")).longValue());
    }
}
