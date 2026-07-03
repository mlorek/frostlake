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
 * A bare boolean {@code AND}/{@code OR} may appear as a function argument (e.g. {@code COUNT_IF(a OR b)},
 * {@code IFF(x AND y, …)}) without requiring extra parentheses. Previously only comparisons parsed as
 * function args; a top-level AND/OR failed. BETWEEN / predicate AND-OR precedence is preserved.
 */
public class FunctionArgBooleanTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE vals (v NUMBER)");
        engine.execute("INSERT INTO vals VALUES (1), (2), (3), (4), (5)");
    }

    private double scalar(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    @Test
    public void countIfWithOrArgument() {
        assertEquals(2.0, scalar("SELECT COUNT_IF(v = 2 OR v = 4) FROM vals"), 1e-9);
    }

    @Test
    public void countIfWithAndArgument() {
        assertEquals(2.0, scalar("SELECT COUNT_IF(v > 1 AND v < 4) FROM vals"), 1e-9);
    }

    @Test
    public void iffWithBooleanArgument() {
        assertEquals(1.0, scalar("SELECT IFF(1 = 1 OR 2 = 3, 1, 0)"), 1e-9);
        assertEquals(0.0, scalar("SELECT IFF(1 = 1 AND 2 = 3, 1, 0)"), 1e-9);
    }

    // ── regression: AND still binds to BETWEEN, and predicate AND/OR still work ──

    @Test
    public void betweenStillBindsAndToItsRange() {
        // v BETWEEN 2 AND 4 → {2,3,4} = 3, not "(v BETWEEN 2) AND 4".
        assertEquals(3.0, scalar("SELECT COUNT(*) FROM vals WHERE v BETWEEN 2 AND 4"), 1e-9);
    }

    @Test
    public void whereClauseAndOrStillWork() {
        assertEquals(2.0, scalar("SELECT COUNT(*) FROM vals WHERE v = 1 OR v = 5"), 1e-9);
        assertEquals(1.0, scalar("SELECT COUNT(*) FROM vals WHERE v > 2 AND v < 4"), 1e-9);
        assertEquals(3.0, scalar("SELECT COUNT(*) FROM vals WHERE v = 1 OR v = 3 OR v = 5"), 1e-9);
    }
}
