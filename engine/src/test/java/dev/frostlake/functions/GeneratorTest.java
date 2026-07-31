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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GENERATOR(ROWCOUNT =&gt; n | TIMELIMIT =&gt; s) — matching Snowflake\u2019s live-verified shape: the
 * produced rows carry ZERO columns (SELECT * over a generator fails with "SELECT with no
 * columns"); consumers project literals/expressions or aggregate over the row count.
 */
public class GeneratorTest extends BaseDatabaseTest {

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void rowcountControlsTheNumberOfRows() {
        assertEquals(10L, count("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 10))"));
        assertEquals(0L, count("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 0))"));
        assertEquals(1L, count("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1))"));
        assertEquals(1000L, count("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1000))"));
    }

    @Test
    public void projectionsEvaluatePerGeneratedRow() {
        final ResultSet rs = engine.executeQuery("SELECT 7 AS n FROM TABLE(GENERATOR(ROWCOUNT => 5))");
        assertEquals(5, rs.getRowCount());
        assertEquals(7L, ((Number) rs.getRows().get(4).getValue(0)).longValue());
    }

    @Test
    public void starOverAGeneratorFailsLikeSnowflake() {
        // Live-verified: GENERATOR emits zero columns, so SELECT * has nothing to expand.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 10))");
            }
        });
        assertTrue(e.getMessage().contains("SELECT with no columns"), "unexpected: " + e.getMessage());
    }

    @Test
    public void timelimitZeroProducesNoRows() {
        assertEquals(0L, count("SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => 0))"));
    }
}
