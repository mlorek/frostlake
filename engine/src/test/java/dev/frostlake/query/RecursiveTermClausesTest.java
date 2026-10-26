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
 * What a recursive CTE's or a RECURSIVE view's body may hold. An ORDER BY, LIMIT or FETCH after the recursive term,
 * or after a parenthesized body, belongs to the whole body and changes nothing; one inside a parenthesized term is
 * refused, after a window call, an aggregate, DISTINCT or GROUP BY in the term, a LIMIT or FETCH before an ORDER BY;
 * a window call in the term's QUALIFY is a window call in the term (all live-verified).
 */
public class RecursiveTermClausesTest extends MeasuredCellsTestSupport {

    @Test
    public void clausesAfterTheTermChangeNothing() {
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 5 ORDER BY n DESC LIMIT 2) SELECT * FROM "
                + "r ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 FETCH FIRST 1 ROWS ONLY) SELECT * FROM "
                + "r ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 ORDER BY n) SELECT * FROM r ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 LIMIT 2) SELECT * FROM r ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "WITH RECURSIVE r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 5 ORDER BY n DESC LIMIT 2) "
                + "SELECT * FROM r ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "WITH r(n) AS ((SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 5) ORDER BY n DESC LIMIT 2) SELECT * "
                + "FROM r ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | LIMIT clause is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL (SELECT n + 1 FROM r WHERE n < 5 ORDER BY n LIMIT 1)) SELECT * FROM r");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 ORDER BY 1 DESC) SELECT * FROM r ORDER "
                + "BY 1");
        assertAnswer("View RV731 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv731(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv731 WHERE n < 3 "
                + "ORDER BY n DESC LIMIT 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rv731 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Window functions are not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 QUALIFY ROW_NUMBER() OVER (ORDER BY n) "
                + "= 1) SELECT * FROM r");
    }

    @Test
    public void severalRowsAStepAndWhatATermMayNotHold() {
        assertAnswer("Table T732 successfully created.", "CREATE OR REPLACE TABLE t732 (n INT)");
        assertAnswer("3", "INSERT INTO t732 VALUES (1), (2), (3)");
        assertAnswer("1 ;; 2 ;; 3 ;; 11 ;; 12 ;; 13 ;; 21 ;; 22 ;; 23 ;; 31 ;; 32 ;; 33",
            "WITH r(n) AS (SELECT n FROM t732 UNION ALL SELECT n + 10 FROM r WHERE n < 30 ORDER BY n DESC LIMIT 2) "
                + "SELECT * FROM r ORDER BY 1");
        assertAnswer("12",
            "WITH r(n) AS (SELECT n FROM t732 UNION ALL SELECT n + 10 FROM r WHERE n < 30 LIMIT 2) SELECT COUNT(*) "
                + "FROM r");
        assertAnswer("1 ;; 2 ;; 3 ;; 11 ;; 12 ;; 13 ;; 21 ;; 22 ;; 23 ;; 31 ;; 32 ;; 33",
            "WITH r(n) AS ((SELECT n FROM t732 UNION ALL SELECT n + 10 FROM r WHERE n < 30) ORDER BY n DESC LIMIT 2) "
                + "SELECT * FROM r ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3 ;; 11 ;; 12 ;; 13 ;; 21 ;; 22 ;; 23 ;; 31 ;; 32 ;; 33",
            "WITH r(n) AS (SELECT n FROM t732 UNION ALL SELECT n + 10 FROM r WHERE n < 30 FETCH FIRST 1 ROWS ONLY) "
                + "SELECT * FROM r ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3 ;; 11 ;; 12 ;; 13 ;; 21 ;; 22 ;; 23 ;; 31 ;; 32 ;; 33",
            "WITH r(n) AS (SELECT n FROM t732 UNION ALL SELECT n + 10 FROM r WHERE n < 30 LIMIT 2 OFFSET 1) SELECT * "
                + "FROM r ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3 ;; 11 ;; 12 ;; 13 ;; 21 ;; 22 ;; 23 ;; 31 ;; 32 ;; 33",
            "WITH r(n) AS (SELECT n FROM t732 UNION ALL SELECT n + 10 FROM r WHERE n < 30 ORDER BY n DESC) SELECT * "
                + "FROM r ORDER BY 1");
        assertAnswer("1 ;; 11 ;; 21 ;; 31",
            "WITH r(n) AS ((SELECT n FROM t732 ORDER BY n LIMIT 1) UNION ALL SELECT n + 10 FROM r WHERE n < 30) "
                + "SELECT * FROM r ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ORDER BY clause is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT n FROM t732 UNION ALL (SELECT n + 10 FROM r WHERE n < 30 ORDER BY n)) SELECT * "
                + "FROM r ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | LIMIT clause is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT n FROM t732 UNION ALL (SELECT n + 10 FROM r WHERE n < 30 LIMIT 5)) SELECT * FROM r "
                + "ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | LIMIT clause is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT n FROM t732 UNION ALL (SELECT n + 10 FROM r WHERE n < 30 FETCH FIRST 5 ROWS ONLY)) "
                + "SELECT * FROM r ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Aggregate functions are not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT MAX(n) + 1 FROM r WHERE n < 3) SELECT * FROM r ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | GROUP BY is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 GROUP BY n) SELECT * FROM r ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | DISTINCT is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT DISTINCT n + 1 FROM r WHERE n < 3) SELECT * FROM r ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Window functions are not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + ROW_NUMBER() OVER (ORDER BY n) FROM r WHERE n < 3) SELECT * "
                + "FROM r ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | [R.N] is not a valid group by expression",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 HAVING COUNT(*) > 0) SELECT * FROM r "
                + "ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 65 | found QUALIFY clause but no window "
                + "function.",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 QUALIFY n > 0) SELECT * FROM r ORDER "
                + "BY 1");
        assertAnswer("REFUSED SQL compilation error: | LIMIT clause is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT TOP 1 n + 1 FROM r WHERE n < 3) SELECT * FROM r ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | 'R' is an invalid Recursive CTE",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 UNION ALL SELECT n + 5 FROM r WHERE n "
                + "< 3 LIMIT 9) SELECT * FROM r ORDER BY 1");
        assertAnswer("View RV732 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv732(n) AS SELECT n FROM t732 UNION ALL SELECT n + 10 FROM rv732 "
                + "WHERE n < 30 ORDER BY n DESC LIMIT 2");
        assertAnswer("1 ;; 2 ;; 3 ;; 11 ;; 12 ;; 13 ;; 21 ;; 22 ;; 23 ;; 31 ;; 32 ;; 33",
            "SELECT * FROM rv732 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Window functions are not allowed in a CTEs recursive term.",
            "CREATE OR REPLACE RECURSIVE VIEW rv732b(n) AS SELECT 1 UNION ALL SELECT n + ROW_NUMBER() OVER (ORDER BY "
                + "n) FROM rv732b WHERE n < 3");
        assertAnswer("REFUSED SQL compilation error: | LIMIT clause is not allowed in a CTEs recursive term.",
            "CREATE OR REPLACE RECURSIVE VIEW rv732c(n) AS SELECT 1 UNION ALL (SELECT n + 1 FROM rv732c WHERE n < 3 "
                + "LIMIT 1)");
    }

    @Test
    public void parenthesizedBodiesRecurseAndTermsRefuseInOrder() {
        assertAnswer("1 ;; 2 ;; 3",
            "WITH r(n) AS ((SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3)) SELECT * FROM r ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH r(n) AS (((SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3))) SELECT * FROM r ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE r(n) AS ((SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) LIMIT 1) SELECT * FROM r "
                + "ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | LIMIT clause is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL ((SELECT n + 1 FROM r WHERE n < 3 LIMIT 1))) SELECT * FROM r");
        assertAnswer("REFUSED SQL compilation error: | DISTINCT is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL (SELECT DISTINCT n + 1 FROM r WHERE n < 3 LIMIT 1)) SELECT * FROM r");
        assertAnswer("REFUSED SQL compilation error: | Window functions are not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL (SELECT n + 1 FROM r WHERE n < 3 QUALIFY ROW_NUMBER() OVER (ORDER BY "
                + "n) = 1 LIMIT 1)) SELECT * FROM r");
        assertAnswer("REFUSED SQL compilation error: | ORDER BY clause is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL (SELECT n + 1 FROM r WHERE n < 3 ORDER BY ROW_NUMBER() OVER (ORDER BY "
                + "n))) SELECT * FROM r");
        assertAnswer("REFUSED SQL compilation error: | Aggregate functions are not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL (SELECT MAX(n) + 1 FROM r WHERE n < 3 ORDER BY 1)) SELECT * FROM r");
        assertAnswer("REFUSED SQL compilation error: | GROUP BY is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL (SELECT n + 1 FROM r WHERE n < 3 GROUP BY n ORDER BY 1)) SELECT * FROM "
                + "r");
        assertAnswer("REFUSED SQL compilation error: | LIMIT clause is not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL (SELECT n + 1 FROM r WHERE n < 3 ORDER BY n FETCH FIRST 1 ROWS ONLY)) "
                + "SELECT * FROM r");
        assertAnswer("REFUSED SQL compilation error: | Window functions are not allowed in a CTEs recursive term.",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 QUALIFY ROW_NUMBER() OVER (ORDER BY n) "
                + "= 1) SELECT * FROM r");
        assertAnswer("REFUSED SQL compilation error: | Window function [ROW_NUMBER() OVER (ORDER BY R.N ASC NULLS "
                + "LAST)] appears outside of SELECT, QUALIFY, and ORDER BY clauses.",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 AND ROW_NUMBER() OVER (ORDER BY n) = "
                + "1) SELECT * FROM r");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 65 | found QUALIFY clause but no window "
                + "function.",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 QUALIFY n > 0) SELECT * FROM r");
        assertAnswer("View RV734 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv734(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM rv734 WHERE n < 3)");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rv734 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ORDER BY clause is not allowed in a CTEs recursive term.",
            "CREATE OR REPLACE RECURSIVE VIEW rv734b(n) AS SELECT 1 UNION ALL (SELECT n + 1 FROM rv734b WHERE n < 3 "
                + "ORDER BY n)");
        assertAnswer("REFUSED SQL compilation error: | Window functions are not allowed in a CTEs recursive term.",
            "CREATE OR REPLACE RECURSIVE VIEW rv734c(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv734c WHERE n < 3 "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY n) = 1");
    }
}
