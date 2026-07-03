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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RANDOM([seed]) returns a signed 64-bit integer (a constant seed is deterministic across rows).
 * UNIFORM(min, max, gen) returns an integer inclusive of both bounds when the bounds are integers, or
 * a double in [min, max) with a float bound; a per-row RANDOM() varies it, a constant RANDOM(seed)
 * repeats it. Assertions check these invariants, not specific random draws.
 */
public class RandomUniformTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (id INTEGER)");
        final StringBuilder insert = new StringBuilder("INSERT INTO t VALUES ");
        for (int i = 0; i < 50; i++) {
            if (i > 0) {
                insert.append(", ");
            }
            insert.append('(').append(i).append(')');
        }
        engine.execute(insert.toString());
    }

    private Object one(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void randomReturnsSignedLong() {
        assertTrue(one("SELECT RANDOM() AS r") instanceof Long, "RANDOM() is a 64-bit integer");
    }

    @Test
    public void randomWithSeedIsDeterministic() {
        assertEquals(one("SELECT RANDOM(123) AS r"), one("SELECT RANDOM(123) AS r"));
        assertNotEquals(one("SELECT RANDOM(1) AS r"), one("SELECT RANDOM(2) AS r"));
    }

    @Test
    public void uniformIntegerBoundsAreInclusiveIntegers() {
        final ResultSet rs = engine.executeQuery("SELECT UNIFORM(1, 3, RANDOM()) AS r FROM t");
        assertTrue(rs.getRows().get(0).getValue(0) instanceof Long, "integer bounds → integer result");
        for (final Row row : rs.getRows()) {
            final long v = ((Number) row.getValue(0)).longValue();
            assertTrue(v >= 1 && v <= 3, "UNIFORM(1,3) out of inclusive range: " + v);
        }
    }

    @Test
    public void uniformFloatBoundsReturnDoubleInHalfOpenRange() {
        final Object v = one("SELECT UNIFORM(0.0, 1.0, RANDOM()) AS r");
        assertTrue(v instanceof Double, "float bounds → double result");
        final double d = (Double) v;
        assertTrue(d >= 0.0 && d < 1.0, "UNIFORM(0.0,1.0) out of [0,1): " + d);
    }

    @Test
    public void uniformWithConstantSeedGeneratorRepeatsAcrossRows() {
        final ResultSet rs = engine.executeQuery("SELECT UNIFORM(1, 100, RANDOM(7)) AS r FROM t ORDER BY id");
        final Object first = rs.getRows().get(0).getValue(0);
        for (final Row row : rs.getRows()) {
            assertEquals(first, row.getValue(0), "a constant-seed generator should give the same value each row");
        }
    }

    @Test
    public void uniformWithoutGeneratorStaysInRange() {
        final ResultSet rs = engine.executeQuery("SELECT UNIFORM(5, 10) AS r FROM t");
        for (final Row row : rs.getRows()) {
            final long v = ((Number) row.getValue(0)).longValue();
            assertTrue(v >= 5 && v <= 10, "UNIFORM(5,10) out of inclusive range: " + v);
        }
    }
}
