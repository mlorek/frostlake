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
 * A set operation's LIMIT / OFFSET or FETCH pages the set operation's result alone ("You can apply the ORDER BY and
 * LIMIT / FETCH clauses to the result of the set operator", documented). A FROM-less arm applied it to its own one row
 * as well, so with an OFFSET it was emptied before the union was formed: a constant row was lost, and a union of
 * literal rows answered none.
 */
public class FromlessArmPagingTest extends MeasuredCellsTestSupport {

    @Test
    public void theSetOperationsResultIsPagedOnce() {
        engine.execute("CREATE OR REPLACE TABLE fap (x INT)");
        engine.execute("INSERT INTO fap VALUES (1), (2), (3), (4)");
        assertAnswer("2 ;; 3 ;; 4 ;; 9", "SELECT x FROM fap UNION ALL SELECT 9 ORDER BY x LIMIT 10 OFFSET 1");
        assertAnswer("9", "SELECT 9 AS x UNION ALL SELECT x FROM fap ORDER BY x LIMIT 10 OFFSET 4");
        assertAnswer("2 ;; 3", "SELECT 1 AS x UNION ALL SELECT 2 UNION ALL SELECT 3 ORDER BY x LIMIT 2 OFFSET 1");
        assertAnswer("2", "SELECT 1 AS x UNION ALL SELECT 2 ORDER BY x OFFSET 1 ROWS FETCH NEXT 1 ROW ONLY");
        assertAnswer("1", "SELECT COUNT(*) FROM (SELECT 1 AS x UNION ALL SELECT 2 LIMIT 1 OFFSET 1)");
        // A FROM-less select alone still pages its own row.
        assertAnswer("", "SELECT 1 AS x LIMIT 1 OFFSET 1");
        assertAnswer("1", "SELECT 1 AS x LIMIT 1");
    }
}
