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
 * A recursive common table expression runs until a step adds no row, however many steps that takes: a date
 * series of 1096 days and counters to 1500, 2000 and 5000 answer every row. Its recursion is a UNION ALL - a
 * distinct UNION is refused, in a view's body too - while a RECURSIVE view's body takes one (live-verified).
 */
public class RecursiveCteDepthTest extends MeasuredCellsTestSupport {

    @Test
    public void theRecursionRunsToItsFixpoint() {
        assertAnswer(
            "1096|2022-01-01|2024-12-31",
            "WITH RECURSIVE dates (d) AS (SELECT '2022-01-01'::DATE UNION ALL SELECT DATEADD(day, 1, d) FROM "
                + "dates WHERE d < '2024-12-31'::DATE) SELECT COUNT(*), MIN(d), MAX(d) FROM dates");
        assertAnswer(
            "1500|1500",
            "WITH RECURSIVE r (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM r WHERE k < 1500) SELECT COUNT(*), "
                + "MAX(k) FROM r");
        assertAnswer(
            "1001|1001",
            "WITH RECURSIVE r (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM r WHERE k < 1001) SELECT COUNT(*), "
                + "MAX(k) FROM r");
        assertAnswer(
            "1002|1002",
            "WITH RECURSIVE r (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM r WHERE k < 1002) SELECT COUNT(*), "
                + "MAX(k) FROM r");
        assertAnswer(
            "5000|5000|12502500",
            "WITH RECURSIVE r (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM r WHERE k < 5000) SELECT COUNT(*), "
                + "MAX(k), SUM(k) FROM r");
        assertAnswer(
            "2000|2000",
            "WITH r (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM r WHERE k < 2000) SELECT COUNT(*), MAX(k) FROM r");
    }

    @Test
    public void aDistinctUnionRecursesOnlyInARecursiveView() {
        try {
            answer(
                "ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 20");
            assertAnswer(
                "REFUSED SQL compilation error: | UNION not yet supported within a Recursive CTE",
                "WITH RECURSIVE r (n) AS (SELECT 1 UNION SELECT n + 1 FROM r WHERE n < 4) SELECT COUNT(*) FROM r");
            assertAnswer(
                "REFUSED SQL compilation error: | UNION not yet supported within a Recursive CTE",
                "WITH RECURSIVE r (n) AS (SELECT 1 UNION SELECT (n % 3) + 1 FROM r) SELECT COUNT(*) FROM r");
            assertAnswer(
                "1",
                "WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n FROM r WHERE n < 0) SELECT COUNT(*) FROM r");
            assertAnswer(
                "View UV7 successfully created.",
                "CREATE OR REPLACE RECURSIVE VIEW uv7 (n) AS SELECT 1 UNION SELECT n + 1 FROM uv7 WHERE n < 3");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM uv7 ORDER BY n");
            assertAnswer(
                "REFUSED SQL compilation error: | UNION not yet supported within a Recursive CTE",
                "CREATE OR REPLACE VIEW uv9 (n) AS WITH RECURSIVE r (n) AS (SELECT 1 UNION SELECT n + 1 FROM r "
                    + "WHERE n < 3) SELECT n FROM r");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'UV9' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE UV9.",
                "SELECT * FROM uv9 ORDER BY n");
        } finally {
            answer(
                "DROP VIEW IF EXISTS uv7");
            answer(
                "DROP VIEW IF EXISTS uv9");
            answer(
                "ALTER SESSION UNSET STATEMENT_TIMEOUT_IN_SECONDS");
        }
    }
}
