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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code OBJECT_AGG(key, value)} — aggregates key/value pairs into a single OBJECT. Pairs with a NULL
 * key or NULL value are omitted; scalar value TYPES survive (a boolean stays {@code true}, not the
 * string {@code "true"}); the result is canonical JSON (keys sorted).
 */
public class ObjectAggTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE kv (grp INTEGER, k VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO kv SELECT 1, 'beta', TO_VARIANT(2)");
        engine.execute("INSERT INTO kv SELECT 1, 'alpha', TO_VARIANT('s')");
        engine.execute("INSERT INTO kv SELECT 1, NULL, TO_VARIANT('dropped')");
        engine.execute("INSERT INTO kv SELECT 1, 'nul', NULL");
        engine.execute("INSERT INTO kv SELECT 2, 'only', TO_VARIANT(TRUE)");
    }

    private String str(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aggregatesPairsIntoASortedObject() {
        assertEquals("{\"alpha\":\"s\",\"beta\":2}",
            str("SELECT OBJECT_AGG(k, v) FROM kv WHERE grp = 1"));
    }

    @Test
    public void nullKeyOrValueDropsThePair() {
        // The NULL-key and NULL-value rows of group 1 contribute nothing (covered above); a group
        // whose only pair is typed keeps the type.
        assertEquals("{\"only\":true}", str("SELECT OBJECT_AGG(k, v) FROM kv WHERE grp = 2"));
    }

    @Test
    public void groupByProducesOneObjectPerGroup() {
        assertEquals(2, engine.executeQuery(
            "SELECT grp, OBJECT_AGG(k, v) FROM kv GROUP BY grp").getRowCount());
    }

    @Test
    public void flattenedObjectEntriesReaggregateWithTypesIntact() {
        // The loader round-trip: FLATTEN an OBJECT into key/value rows, then OBJECT_AGG them back —
        // boolean and number values must not come back as quoted strings.
        assertEquals("{\"b\":true,\"n\":5,\"s\":\"x\"}", str("""
            SELECT OBJECT_AGG(f.key::VARCHAR, f.value)
            FROM (SELECT 1 AS i), LATERAL FLATTEN(input => OBJECT_CONSTRUCT('b', TRUE, 'n', 5, 's', 'x')) f"""));
    }

    @Test
    public void worksAsAWindowFunctionOverAPartition() {
        // Two-argument aggregates over a window frame: the pair must reach the accumulator (the
        // single-argument feed silently returned an empty object).
        assertEquals("{\"alpha\":\"s\",\"beta\":2}",
            str("SELECT OBJECT_AGG(k, v) OVER (PARTITION BY grp) FROM kv WHERE grp = 1 LIMIT 1"));
        assertEquals("x-y", str(
            "SELECT LISTAGG(k, '-') OVER (PARTITION BY grp2) FROM (SELECT 1 AS grp2, 'x' AS k UNION ALL SELECT 1, 'y') LIMIT 1"));
        assertEquals("s", str(
            "SELECT MAX_BY(k, srt) OVER (PARTITION BY g) FROM (SELECT 1 AS g, 's' AS k, 9 AS srt UNION ALL SELECT 1, 't', 1) LIMIT 1"));
    }
}
