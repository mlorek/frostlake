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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bracket subscripts on a VARIANT: a numeric subscript indexes an array; a string subscript {@code x['key']}
 * is object member access (Snowflake bracket notation, equivalent to {@code x:key}).
 */
public class BracketPathAccessTest extends BaseDatabaseTest {

    private long asLong(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void stringKeySubscriptReadsObjectMember() {
        assertEquals(1L, asLong("SELECT PARSE_JSON('{\"a\":1}')['a']"));
    }

    @Test
    public void nestedStringKeySubscripts() {
        assertEquals(2L, asLong("SELECT PARSE_JSON('{\"a\":{\"b\":2}}')['a']['b']"));
    }

    @Test
    public void numericSubscriptStillIndexesArray() {
        assertEquals(20L, asLong("SELECT PARSE_JSON('[10,20,30]')[1]"));
    }

    @Test
    public void colonPathStillWorks() {
        assertEquals(1L, asLong("SELECT PARSE_JSON('{\"a\":1}'):a"));
    }

    @Test
    public void dotFieldAfterArraySubscript() {
        // c[0].b — dot field access on the object stored at array element 0.
        assertEquals(1L, asLong("SELECT PARSE_JSON('[{\"b\":1},{\"b\":2}]')[0].b"));
        assertEquals(2L, asLong("SELECT PARSE_JSON('[{\"b\":1},{\"b\":2}]')[1].b"));
    }

    @Test
    public void dotFieldChainAfterSubscript() {
        // c[0].a.b — chained dot access after a subscript.
        assertEquals(7L, asLong("SELECT PARSE_JSON('[{\"a\":{\"b\":7}}]')[0].a.b"));
    }

    @Test
    public void dotFieldAfterSubscriptOnArrayColumn() {
        // The reported scenario: an ARRAY column of objects, accessed as c[0].b.
        engine.execute("CREATE OR REPLACE TABLE t1 (c ARRAY)");
        engine.execute("INSERT INTO t1 SELECT [{'b' : 1}, {'b' : 2}] AS c");
        assertEquals(1L, asLong("SELECT c[0].b FROM t1"));
        assertEquals(2L, asLong("SELECT c[1].b FROM t1"));
    }

    @Test
    public void bareDottedNameStaysColumnReference() {
        // Regression guard: t1.n must remain a table-qualified column reference, not variant field access.
        engine.execute("CREATE OR REPLACE TABLE t1 (n INTEGER)");
        engine.execute("INSERT INTO t1 VALUES (42)");
        assertEquals(42L, asLong("SELECT t1.n FROM t1"));
    }

    @Test
    public void deepNestedDotAndSubscriptChain() {
        // Objects nested inside objects inside an array: c[0] = {'b':{'c':[1]}}, .b = {'c':[1]},
        // .c = [1], [0] = 1 — exercises recursive nested-literal serialization plus a long access chain.
        engine.execute("CREATE OR REPLACE TABLE t1 (c ARRAY)");
        engine.execute("INSERT INTO t1 SELECT [{'b' : {'c' : [1]}}, {'b' : 2}] AS c");
        assertEquals(1L, asLong("SELECT c[0].b.c[0] FROM t1"));

        // The user's exact form: SELECT *, <expr> — the expression is the second output column.
        final Object v = engine.executeQuery("SELECT *, c[0].b.c[0] FROM t1")
            .getRows().get(0).getValue(1);
        assertEquals(1L, ((Number) v).longValue());
    }
}
