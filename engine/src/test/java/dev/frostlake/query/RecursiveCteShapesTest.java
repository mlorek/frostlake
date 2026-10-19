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
 * A recursive CTE's anchor and recursive term (live-verified): the recursive term is the last operand and the
 * only one reading the CTE, after a UNION ALL; the anchor before it may combine operands with UNION, EXCEPT,
 * MINUS or INTERSECT; a parenthesized operand is the query it holds; and every other shape is refused as the
 * account refuses it - an invalid Recursive CTE, a distinct UNION, or a UNION ALL inside the anchor.
 */
public class RecursiveCteShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void operandShapesOfARecursiveCte() {
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 20");
        assertCells(new String[][] {
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT 10 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r "
                + "ORDER BY n",
                "REFUSED SQL compilation error: | Multiple set operators are not yet supported within a Recursive CTE"},
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 UNION ALL SELECT 7) SELECT * FROM r "
                + "ORDER BY n",
                "REFUSED SQL compilation error: | 'R' is an invalid Recursive CTE"},
            {"WITH r(n) AS (SELECT 5 EXCEPT SELECT 6 UNION ALL SELECT n + 1 FROM r WHERE n < 7) SELECT * FROM r ORDER "
                + "BY n",
                "5 ;; 6 ;; 7"},
            {"WITH r(n) AS (SELECT 1 MINUS SELECT 2 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r ORDER "
                + "BY n",
                "1 ;; 2 ;; 3"},
            {"WITH r(n) AS (SELECT 1 INTERSECT SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r "
                + "ORDER BY n",
                "1 ;; 2 ;; 3"},
            {"WITH r(n) AS (SELECT 1 UNION ALL (SELECT n + 1 FROM r WHERE n < 3)) SELECT * FROM r ORDER BY n",
                "1 ;; 2 ;; 3"},
            {"WITH r(n) AS ((SELECT 1) UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r ORDER BY n",
                "1 ;; 2 ;; 3"},
            {"CREATE OR REPLACE RECURSIVE VIEW rv4(n) AS SELECT 1 UNION ALL (SELECT n + 1 FROM rv4 WHERE n < 3)",
                "View RV4 successfully created."},
            {"SELECT * FROM rv4 ORDER BY 1", "1 ;; 2 ;; 3"},
            {"CREATE OR REPLACE RECURSIVE VIEW rv6(n) AS SELECT 1 UNION ALL SELECT 1 UNION SELECT n + 1 FROM rv6 "
                + "WHERE n < 3",
                "View RV6 successfully created."},
        });
    }

    @Test
    public void theShapesTheAccountRefuses() {
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 20");
        assertCells(new String[][] {
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT 1 UNION SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r ORDER "
                + "BY n",
                "REFUSED SQL compilation error: | UNION not yet supported within a Recursive CTE"},
            {"WITH r(n) AS (SELECT 1 UNION SELECT 10 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r ORDER "
                + "BY n",
                "1 ;; 2 ;; 3 ;; 10"},
            {"WITH r(n) AS (SELECT 1 UNION SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r ORDER BY n",
                "REFUSED SQL compilation error: | UNION not yet supported within a Recursive CTE"},
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 UNION ALL SELECT n + 2 FROM r WHERE n "
                + "< 2) SELECT * FROM r ORDER BY n",
                "REFUSED SQL compilation error: | 'R' is an invalid Recursive CTE"},
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT 2 INTERSECT SELECT 2 UNION ALL SELECT n + 1 FROM r WHERE n < 3) "
                + "SELECT * FROM r ORDER BY n",
                "REFUSED SQL compilation error: | Multiple set operators are not yet supported within a Recursive CTE"},
            {"WITH r(n) AS (SELECT 1 EXCEPT SELECT 2 EXCEPT SELECT 3 UNION ALL SELECT n + 1 FROM r WHERE n < 3) "
                + "SELECT * FROM r ORDER BY n",
                "1 ;; 2 ;; 3"},
            {"WITH r(n) AS (SELECT n + 1 FROM r WHERE n < 3 UNION ALL SELECT 1) SELECT * FROM r ORDER BY n",
                "REFUSED SQL compilation error: | 'R' is an invalid Recursive CTE"},
            {"CREATE OR REPLACE RECURSIVE VIEW rv8(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv8 WHERE n < 3 UNION "
                + "ALL SELECT 7",
                "View RV8 successfully created."},
            {"SELECT * FROM rv8 ORDER BY 1", "1 ;; 2 ;; 3"},
            {"CREATE OR REPLACE RECURSIVE VIEW rv9(n) AS SELECT 5 EXCEPT SELECT 6 UNION ALL SELECT n + 1 FROM rv9 "
                + "WHERE n < 7",
                "View RV9 successfully created."},
            {"SELECT * FROM rv9 ORDER BY 1", "5 ;; 6 ;; 7"},
        });
    }
}
