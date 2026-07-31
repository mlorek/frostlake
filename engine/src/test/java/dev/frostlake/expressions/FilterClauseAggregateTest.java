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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FILTER (WHERE cond) on aggregate calls — conditional aggregation: the aggregate operates only
 * on the rows satisfying the condition. Covered for the fast-path aggregates (SUM/COUNT), grouped
 * queries, generic accumulator aggregates (MIN_BY), aggregates nested in larger expressions,
 * HAVING, and the explicit rejection of FILTER combined with OVER.
 */
public class FilterClauseAggregateTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE ft (a INTEGER, b INTEGER, c INTEGER, g INTEGER)");
        engine.execute("INSERT INTO ft VALUES (1, 1, 1, 1), (100, 100, -1, 1), (7, 7, 2, 2)");
    }

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void sumAndCountSeeOnlyMatchingRows() {
        assertEquals(8L, ((Number) scalar("SELECT SUM(a) FILTER(WHERE c > 0) FROM ft")).longValue());
        assertEquals(2L, ((Number) scalar("SELECT COUNT(a) FILTER(WHERE c > 0) FROM ft")).longValue());
        assertEquals(108L, ((Number) scalar("SELECT SUM(a) FROM ft")).longValue(),
            "the unfiltered aggregate still sees every row");
    }

    @Test
    public void filterAppliesPerGroup() {
        final ResultSet rs = engine.executeQuery(
            "SELECT g, SUM(a) FILTER(WHERE c > 0) FROM ft GROUP BY g ORDER BY g");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(7L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void genericAccumulatorAggregatesAreFiltered() {
        assertEquals(7L, ((Number) scalar("SELECT MIN_BY(a, b) FILTER(WHERE c = 2) FROM ft")).longValue());
    }

    @Test
    public void filteredAggregateInsideALargerExpression() {
        assertEquals(9L, ((Number) scalar("SELECT SUM(a) FILTER(WHERE c > 0) + 1 FROM ft")).longValue());
        assertEquals(1, engine.executeQuery(
            "SELECT g FROM ft GROUP BY g HAVING SUM(a) FILTER(WHERE c > 0) > 5").getRowCount(),
            "HAVING evaluates the filtered aggregate");
    }

    @Test
    public void filterWithOverIsRejected() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT SUM(a) FILTER(WHERE c > 0) OVER (PARTITION BY g) FROM ft");
            }
        });
        assertTrue(e.getMessage().contains("FILTER"), "unexpected message: " + e.getMessage());
    }

    @Test
    public void filterRemainsUsableAsAnIdentifier() {
        engine.execute("CREATE TABLE ftab (filter VARCHAR)");
        engine.execute("INSERT INTO ftab VALUES ('x')");
        assertEquals("x", scalar("SELECT filter FROM ftab"));
        assertEquals("FILTER", engine.executeQuery("SELECT 1 AS filter").getColumns().get(0).getName());
    }
}
