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
 * What a recursive CTE's recursive term must be, in the account's words (live-verified): as many columns as the
 * anchor - Anchor and recursive term in Recursive CTE 'R' must select the same number of columns. - and no
 * window function, aggregate, DISTINCT, GROUP BY or TOP: Aggregate functions are not allowed in a CTEs recursive
 * term.
 */
public class RecursiveTermRulesTest extends MeasuredCellsTestSupport {

    @Test
    public void aTermAsWideAsItsAnchor() {
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 20");
        assertCells(new String[][] {
            {"WITH r(n, m) AS (SELECT 1, 2 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r",
                "REFUSED SQL compilation error: | Anchor and recursive term in Recursive CTE 'R' must select the "
                + "same number of columns."},
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1, 5 FROM r WHERE n < 3) SELECT * FROM r",
                "REFUSED SQL compilation error: | Anchor and recursive term in Recursive CTE 'R' must select the "
                + "same number of columns."},
            {"WITH r AS (SELECT 1 a, 2 b UNION ALL SELECT a + 1 FROM r WHERE a < 3) SELECT * FROM r",
                "REFUSED SQL compilation error: | Anchor and recursive term in Recursive CTE 'R' must select the "
                + "same number of columns."},
        });
    }

    @Test
    public void whatATermMayNotHold() {
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 20");
        assertCells(new String[][] {
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT MAX(n) + 1 FROM r WHERE n < 3) SELECT * FROM r",
                "REFUSED SQL compilation error: | Aggregate functions are not allowed in a CTEs recursive term."},
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT ROW_NUMBER() OVER (ORDER BY n) + n FROM r WHERE n < 3) SELECT * "
                + "FROM r",
                "REFUSED SQL compilation error: | Window functions are not allowed in a CTEs recursive term."},
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT DISTINCT n + 1 FROM r WHERE n < 3) SELECT * FROM r",
                "REFUSED SQL compilation error: | DISTINCT is not allowed in a CTEs recursive term."},
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 GROUP BY n) SELECT * FROM r",
                "REFUSED SQL compilation error: | GROUP BY is not allowed in a CTEs recursive term."},
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 HAVING TRUE) SELECT * FROM r",
                "1 ;; 2 ;; 3"},
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT TOP 1 n + 1 FROM r WHERE n < 3) SELECT * FROM r",
                "REFUSED SQL compilation error: | LIMIT clause is not allowed in a CTEs recursive term."},
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 QUALIFY TRUE) SELECT * FROM r",
                "REFUSED SQL compilation error: error line 1 at position 65 | found QUALIFY clause but no window "
                + "function."},
        });
    }
}
