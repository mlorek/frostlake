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

/**
 * PERCENTILE_CONT / PERCENTILE_DISC via {@code WITHIN GROUP (ORDER BY <expr>)}: the argument is the
 * fraction p and the ORDER BY expression supplies the data. Previously the WITHIN GROUP clause was a
 * parse error and the accumulator hardcoded p = 0.5 over the (constant) function argument.
 */
public class PercentileTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE p (grp INTEGER, x DOUBLE)");
        // grp 1: 10,20,30,40,100 ; grp 2: 5,15
        engine.execute("INSERT INTO p VALUES (1,10),(1,20),(1,30),(1,40),(1,100),(2,5),(2,15)");
    }

    private double scalar(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    @Test
    public void continuousMedian() {
        assertEquals(30.0, scalar(
            "SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x) AS r FROM p WHERE grp = 1"), 1e-9);
    }

    @Test
    public void continuousInterpolatesBetweenValues() {
        // p=0.9 over [10,20,30,40,100]: idx 3.6 → 40*0.4 + 100*0.6 = 76.
        assertEquals(76.0, scalar(
            "SELECT PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY x) AS r FROM p WHERE grp = 1"), 1e-9);
    }

    @Test
    public void discreteReturnsAnActualValue() {
        // PERCENTILE_DISC returns a value present in the set (no interpolation).
        assertEquals(100.0, scalar(
            "SELECT PERCENTILE_DISC(0.9) WITHIN GROUP (ORDER BY x) AS r FROM p WHERE grp = 1"), 1e-9);
        assertEquals(30.0, scalar(
            "SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY x) AS r FROM p WHERE grp = 1"), 1e-9);
    }

    @Test
    public void percentileIsComputedPerGroup() {
        final ResultSet r = engine.executeQuery(
            "SELECT grp, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x) AS r FROM p GROUP BY grp ORDER BY grp");
        assertEquals(2, r.getRowCount());
        assertEquals(30.0, ((Number) r.getRows().get(0).getValue(r.getColumnIndex("r"))).doubleValue(), 1e-9);
        assertEquals(10.0, ((Number) r.getRows().get(1).getValue(r.getColumnIndex("r"))).doubleValue(), 1e-9);
    }
}
