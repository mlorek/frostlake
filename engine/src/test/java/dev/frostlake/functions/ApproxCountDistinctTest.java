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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * APPROX_COUNT_DISTINCT is now a HyperLogLog sketch. It must not undercount distinct values that
 * collide on 32-bit String.hashCode() (the previous impl's bug), NULLs are ignored, small
 * cardinalities are exact, and a moderate cardinality is estimated within HLL's error bound.
 */
public class ApproxCountDistinctTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (v VARCHAR)");
    }

    private long approxCount() {
        return ((Number) engine.executeQuery("SELECT APPROX_COUNT_DISTINCT(v) AS r FROM t")
            .getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void distinctHashCodeCollisionsAreNotMerged() {
        // 'Aa' and 'BB' share String.hashCode() (2112); the old 32-bit-hashCode set counted them as one.
        engine.execute("INSERT INTO t VALUES ('Aa'), ('BB'), ('Aa')");
        assertEquals(2L, approxCount());
    }

    @Test
    public void smallCardinalityIsExactAndDeduplicates() {
        engine.execute("INSERT INTO t VALUES ('a'), ('b'), ('c'), ('a'), ('c')");
        assertEquals(3L, approxCount());
    }

    @Test
    public void nullsAreNotCounted() {
        engine.execute("INSERT INTO t VALUES ('a'), (NULL), ('b'), (NULL)");
        assertEquals(2L, approxCount());
    }

    @Test
    public void allNullIsZero() {
        engine.execute("INSERT INTO t VALUES (NULL), (NULL)");
        assertEquals(0L, approxCount());
    }

    @Test
    public void moderateCardinalityIsWithinErrorBound() {
        final StringBuilder insert = new StringBuilder("INSERT INTO t VALUES ");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) {
                insert.append(", ");
            }
            insert.append("('k").append(i).append("')");
        }
        engine.execute(insert.toString());
        final long estimate = approxCount();
        assertTrue(Math.abs(estimate - 1000) <= 20, "estimate " + estimate + " not within 2% of 1000");
    }
}
