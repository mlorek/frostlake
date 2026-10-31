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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A RECURSIVE view whose body is a distinct UNION recurses as UNION ALL does: no row is dropped as a duplicate,
 * the anchor's included, so an anchor row met twice recurses twice, a join that reaches a row twice keeps both,
 * and a step that meets its rows again runs on until the statement times out (all live-verified).
 */
public class RecursiveViewUnionTest extends MeasuredCellsTestSupport {

    @Test
    public void aDistinctUnionKeepsEveryRow() {
        assertAnswer("View RV735 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv735(n) AS SELECT 1 FROM (VALUES (1), (1)) UNION SELECT n + 1 FROM "
                + "rv735 WHERE n < 2");
        assertAnswer("1 ;; 1 ;; 2 ;; 2", "SELECT * FROM rv735 ORDER BY 1");
        assertAnswer("4", "SELECT COUNT(*) FROM rv735");
        assertAnswer("View RV735B successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv735b(n) AS SELECT n FROM (VALUES (1), (1), (2)) t(n) UNION SELECT n "
                + "+ 1 FROM rv735b WHERE n < 3");
        assertAnswer("1 ;; 1 ;; 2 ;; 2 ;; 2 ;; 3 ;; 3 ;; 3", "SELECT * FROM rv735b ORDER BY 1");
        assertAnswer("View RV735C successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv735c(n) AS SELECT n FROM (VALUES (1), (2)) t(n) UNION SELECT 5 FROM "
                + "rv735c WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 5 ;; 5", "SELECT * FROM rv735c ORDER BY 1");
        assertAnswer("View RV735D successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv735d(n) AS SELECT 1 FROM (VALUES (1), (1)) UNION ALL SELECT n + 1 "
                + "FROM rv735d WHERE n < 2");
        assertAnswer("1 ;; 1 ;; 2 ;; 2", "SELECT * FROM rv735d ORDER BY 1");
        assertAnswer("View RV735E successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv735e(n, m) AS SELECT n, 0 FROM (VALUES (1), (1), (NULL), (NULL)) "
                + "t(n) UNION SELECT n + 1, m + 1 FROM rv735e WHERE n < 2");
        assertAnswer("1|0 ;; 1|0 ;; 2|1 ;; 2|1 ;; null|0 ;; null|0", "SELECT * FROM rv735e ORDER BY 1, 2");
        assertAnswer("REFUSED SQL compilation error: | UNION not yet supported within a Recursive CTE",
            "WITH RECURSIVE r(n) AS (SELECT 1 FROM (VALUES (1), (1)) UNION SELECT n + 1 FROM r WHERE n < 2) SELECT * "
                + "FROM r ORDER BY 1");
    }

    @Test
    public void aJoinReachingARowTwiceKeepsBothAndARunawayTimesOut() {
        assertAnswer("Table E736 successfully created.", "CREATE OR REPLACE TABLE e736 (a INT, b INT)");
        assertAnswer("4", "INSERT INTO e736 VALUES (1, 2), (1, 3), (2, 4), (3, 4)");
        assertAnswer("View RV736B successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv736b(n) AS SELECT 1 UNION SELECT e.b FROM rv736b JOIN e736 e ON e.a "
                + "= rv736b.n");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 4", "SELECT * FROM rv736b ORDER BY 1");
        assertAnswer("View RV736C successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv736c(n) AS SELECT 1 UNION ALL SELECT e.b FROM rv736c JOIN e736 e ON "
                + "e.a = rv736c.n");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 4", "SELECT * FROM rv736c ORDER BY 1");
        assertAnswer("5|4", "SELECT COUNT(*), COUNT(DISTINCT n) FROM rv736b");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 2");
        assertAnswer("View RV736 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv736(n) AS SELECT 1 FROM (VALUES (1), (1)) UNION SELECT 1 FROM rv736 "
                + "WHERE n < 5");
        assertAnswer("REFUSED Statement reached its statement or warehouse timeout of 2 second(s) and was canceled.",
            "SELECT COUNT(*) FROM rv736");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET STATEMENT_TIMEOUT_IN_SECONDS");
    }
}
