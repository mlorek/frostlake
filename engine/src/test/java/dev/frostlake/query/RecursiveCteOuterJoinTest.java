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

import org.junit.jupiter.api.Test;

/**
 * A recursive term may read its CTE on the preserved side of an outer join, the left of a LEFT JOIN or the right
 * of a RIGHT JOIN; on the side that supplies NULLs, or either side of a FULL JOIN, it is refused, OUTER JOINs
 * with a self reference are not allowed in a recursive CTE (all live-verified).
 */
public class RecursiveCteOuterJoinTest extends MeasuredCellsTestSupport {

    @Test
    public void outerJoinedSelfReference() {
        engine.execute("CREATE OR REPLACE TABLE e (id INT, mgr INT, name VARCHAR)");
        engine.execute("INSERT INTO e VALUES (1, NULL, 'a'), (2, 1, 'b'), (3, 1, 'c'), (4, 2, 'd'), (5, 4, 'e')");
        assertAnswer("REFUSED SQL compilation error: | OUTER JOINs with a self reference are not allowed in a "
                + "recursive CTE.",
            "WITH r(id, lvl) AS (SELECT id, 1 FROM e WHERE mgr IS NULL UNION ALL SELECT e.id, r.lvl + 1 FROM e LEFT "
                + "JOIN r ON e.mgr = r.id WHERE r.lvl < 3) SELECT * FROM r ORDER BY id");
        assertAnswer("1|1 ;; 2|2 ;; 3|2 ;; 4|3 ;; null|3",
            "WITH r(id, lvl) AS (SELECT id, 1 FROM e WHERE mgr IS NULL UNION ALL SELECT e.id, r.lvl + 1 FROM r LEFT "
                + "JOIN e ON e.mgr = r.id WHERE r.lvl < 3) SELECT * FROM r ORDER BY id");
        assertAnswer("1|1 ;; 2|2 ;; 3|2 ;; 4|3 ;; null|3",
            "WITH r(id, lvl) AS (SELECT id, 1 FROM e WHERE mgr IS NULL UNION ALL SELECT e.id, r.lvl + 1 FROM e RIGHT "
                + "JOIN r ON e.mgr = r.id WHERE r.lvl < 3) SELECT * FROM r ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | OUTER JOINs with a self reference are not allowed in a "
                + "recursive CTE.",
            "WITH r(id, lvl) AS (SELECT id, 1 FROM e WHERE mgr IS NULL UNION ALL SELECT e.id, r.lvl + 1 FROM r RIGHT "
                + "JOIN e ON e.mgr = r.id WHERE r.lvl < 3) SELECT * FROM r ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | OUTER JOINs with a self reference are not allowed in a "
                + "recursive CTE.",
            "WITH r(id, lvl) AS (SELECT id, 1 FROM e WHERE mgr IS NULL UNION ALL SELECT e.id, r.lvl + 1 FROM e FULL "
                + "JOIN r ON e.mgr = r.id WHERE r.lvl < 3) SELECT * FROM r ORDER BY id");
        assertAnswer("1|1 ;; 2|2 ;; 3|2 ;; 4|3",
            "WITH r(id, lvl) AS (SELECT id, 1 FROM e WHERE mgr IS NULL UNION ALL SELECT e.id, r.lvl + 1 FROM e JOIN "
                + "r ON e.mgr = r.id WHERE r.lvl < 3) SELECT * FROM r ORDER BY id");
    }
}
