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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A WITH-clause CTE is visible inside the subqueries of the main query, not just its top-level FROM — so a
 * CTE can be referenced from a {@code WHERE NOT EXISTS (…)} / {@code IN (…)} / scalar subquery. Previously
 * this raised "Table does not exist" because the CTE context was only wired for the outer FROM.
 */
public class CteInSubqueryTest extends BaseDatabaseTest {

    private void seed() {
        engine.execute("CREATE TABLE ev (name VARCHAR, state VARCHAR, seq INT)");
        engine.execute("INSERT INTO ev VALUES ('a','FAILED',1), ('a','FAILED',2), "
            + "('b','FAILED',1), ('b','SUCCEEDED',3), ('c','FAILED',5)");
    }

    @Test
    public void cteVisibleInsideNotExistsCorrelatedSubquery() {
        seed();
        // Failing names that were never later followed by a SUCCEEDED row — mirrors the task-history shape.
        final ResultSet rs = engine.executeQuery("""
            WITH e AS (SELECT * FROM ev)
            SELECT DISTINCT bad.name
            FROM e bad
            WHERE bad.state = 'FAILED'
              AND NOT EXISTS (
                SELECT 1 FROM e good
                WHERE good.name = bad.name AND good.state = 'SUCCEEDED' AND good.seq > bad.seq)
            ORDER BY bad.name
            """);
        // 'a' (no success) and 'c' (no success) qualify; 'b' is excluded (a later SUCCEEDED row exists).
        assertEquals(2, rs.getRowCount());
        assertEquals("a", rs.getRows().get(0).getValue(0));
        assertEquals("c", rs.getRows().get(1).getValue(0));
    }

    @Test
    public void cteVisibleInsideInSubquery() {
        seed();
        final ResultSet rs = engine.executeQuery("""
            WITH e AS (SELECT * FROM ev)
            SELECT COUNT(*) FROM e
            WHERE name IN (SELECT name FROM e WHERE state = 'SUCCEEDED')
            """);
        // Only 'b' ever succeeded; it has 2 rows in ev.
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void cteVisibleInsideScalarSubquery() {
        seed();
        final ResultSet rs = engine.executeQuery("""
            WITH e AS (SELECT * FROM ev)
            SELECT (SELECT COUNT(*) FROM e WHERE state = 'FAILED') AS failed_count
            """);
        assertEquals(4L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
