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
 * APPROX_PERCENTILE(expr, percentile): the second argument is a constant fraction in [0, 1]. This emulator
 * computes the exact interpolated percentile (the best possible approximation), so results match
 * PERCENTILE_CONT.
 */
public class ApproxPercentileTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE p (grp INTEGER, v DOUBLE)");
        engine.execute("INSERT INTO p VALUES "
            + "(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(2,100),(2,200)");
    }

    private double scalar(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    @Test
    public void median() {
        assertEquals(5.5, scalar("SELECT APPROX_PERCENTILE(v, 0.5) FROM p WHERE grp = 1"), 1e-9);
    }

    @Test
    public void interpolatesHighPercentile() {
        // idx = 0.9 * 9 = 8.1 → 9*0.9 + 10*0.1 = 9.1
        assertEquals(9.1, scalar("SELECT APPROX_PERCENTILE(v, 0.9) FROM p WHERE grp = 1"), 1e-9);
    }

    @Test
    public void extremesReturnMinAndMax() {
        assertEquals(1.0, scalar("SELECT APPROX_PERCENTILE(v, 0.0) FROM p WHERE grp = 1"), 1e-9);
        assertEquals(10.0, scalar("SELECT APPROX_PERCENTILE(v, 1.0) FROM p WHERE grp = 1"), 1e-9);
    }

    @Test
    public void grouped() {
        assertEquals(150.0, scalar("SELECT APPROX_PERCENTILE(v, 0.5) FROM p WHERE grp = 2"), 1e-9);
    }

    @Test
    public void emptyInputIsNull() {
        engine.execute("CREATE TABLE e (v DOUBLE)");
        assertNull(engine.executeQuery("SELECT APPROX_PERCENTILE(v, 0.5) FROM e").getRows().get(0).getValue(0));
    }
}
