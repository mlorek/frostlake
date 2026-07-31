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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conditional functions defined in terms of CASE short-circuit: only the selected branch is
 * evaluated. This is what makes the standard guard idiom work — {@code IFF(x IS NOT NULL, f(x), NULL)}
 * must not call {@code f} on the NULL rows, and {@code NVL(a, g(b))} must not compute the fallback
 * when {@code a} is non-NULL. Evaluating every argument first made such guards fail on exactly the
 * rows they were written to exclude.
 */
public class ConditionalShortCircuitTest extends BaseDatabaseTest {

    private Object one(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void testIffSkipsUntakenBranch() {
        assertEquals(42L, ((Number) one("SELECT IFF(FALSE, 1/0, 42)")).longValue());
        assertEquals(7L, ((Number) one("SELECT IFF(TRUE, 7, 1/0)")).longValue());
    }

    @Test
    public void testCoalesceStopsAtFirstNonNull() {
        assertEquals(7L, ((Number) one("SELECT COALESCE(NULL, 7, 1/0)")).longValue());
    }

    @Test
    public void testNvlAndIfnullSkipFallbackWhenValuePresent() {
        assertEquals(3L, ((Number) one("SELECT NVL(3, 1/0)")).longValue());
        assertEquals(2L, ((Number) one("SELECT IFNULL(2, 1/0)")).longValue());
        assertEquals(5L, ((Number) one("SELECT NVL(NULL, 5)")).longValue());
    }

    @Test
    public void testNvl2SelectsOneBranchOnly() {
        assertEquals(9L, ((Number) one("SELECT NVL2(NULL, 1/0, 9)")).longValue());
        assertEquals(4L, ((Number) one("SELECT NVL2('x', 4, 1/0)")).longValue());
    }

    @Test
    public void testGuardedUdfIsNotCalledOnExcludedRows() {
        // The vendor idiom: a UDF that rejects NULL, guarded by IFF. Before short-circuiting, the UDF
        // ran for every row and raised on the NULL one.
        engine.execute("""
            CREATE FUNCTION strict_len(s VARCHAR)
            RETURNS NUMBER
            LANGUAGE JAVA
            HANDLER = 'StrictLen.go'
            AS
            $$
            class StrictLen {
              public static int go(String s) {
                return s.length();
              }
            }
            $$
            """);
        engine.execute("CREATE TABLE guard_src (s VARCHAR)");
        engine.execute("INSERT INTO guard_src VALUES ('abc'), (NULL)");
        final ResultSet rs = engine.executeQuery(
            "SELECT IFF(s IS NOT NULL, strict_len(s), NULL) AS n FROM guard_src ORDER BY n");
        assertEquals(2, rs.getRows().size());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertNull(rs.getRows().get(1).getValue(0));
    }

    @Test
    public void testValuesStillCorrectWhenBothBranchesAreSafe() {
        // Short-circuiting must not change ordinary results.
        final ResultSet rs = engine.executeQuery("""
            SELECT IFF(x > 2, 'big', 'small') AS label, COALESCE(NULL, x, 99) AS c, NVL(NULL, x * 2) AS d
            FROM (SELECT 1 AS x UNION ALL SELECT 5) ORDER BY x
            """);
        assertEquals("small", rs.getRows().get(0).getValue(0));
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
        assertEquals("big", rs.getRows().get(1).getValue(0));
        assertEquals(5L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
        assertEquals(10L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
    }

    @Test
    public void testWrongArityStillReportsFunctionError() {
        // Arity mismatches fall through to the eager path so the function's own validation reports it.
        // COALESCE needs at least TWO arguments — live-verified on a real account,
        // SELECT COALESCE(1) fails "not enough arguments for function [COALESCE(1)], expected 2, got 1"
        // (GREATEST(1) and LEAST(1), by contrast, are fine there).
        final RuntimeException tooFew = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                one("SELECT COALESCE(1)");
            }
        });
        assertTrue(tooFew.getMessage().contains("not enough arguments"), tooFew.getMessage());
        assertEquals(1L, ((Number) one("SELECT COALESCE(NULL, 1)")).longValue());
    }
}
