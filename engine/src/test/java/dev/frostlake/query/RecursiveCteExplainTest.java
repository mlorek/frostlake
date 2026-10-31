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
 * EXPLAIN of a recursive CTE: a RecursiveWithClause named by the CTE over its anchor - a ValuesClause where the
 * anchor has no FROM - and its recursive term, the term's operator feeding the clause back as one of its parents,
 * the CTE's columns read as R.N; no table of the CTE's name is scanned, whether one exists or not (all
 * live-verified).
 */
public class RecursiveCteExplainTest extends MeasuredCellsTestSupport {

    @Test
    public void recursivePlan() {
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|R.N|null|null|null "
                + ";; 1|1|[0, 3]|RecursiveWithClause|null|null|R|null|null|null ;; 1|2|[1]|ValuesClause|null|null|()|"
                + "null|null|null ;; 1|3|[1]|Filter|null|null|R.N < 3|null|null|null",
            "EXPLAIN WITH RECURSIVE r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|R.N|null|null|null "
                + ";; 1|1|[0, 3]|RecursiveWithClause|null|null|R|null|null|null ;; 1|2|[1]|ValuesClause|null|null|()|"
                + "null|null|null ;; 1|3|[1]|Filter|null|null|R.N < 3|null|null|null",
            "EXPLAIN WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|R.N * 2|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|R.N > 1|null|null|null ;; 1|2|[1, 4]|RecursiveWithClause|null|"
                + "null|R|null|null|null ;; 1|3|[2]|ValuesClause|null|null|()|null|null|null ;; 1|4|[2]|Filter|null|"
                + "null|R.N < 3|null|null|null",
            "EXPLAIN WITH RECURSIVE r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT n * 2 FROM r "
                + "WHERE n > 1");
    }

    @Test
    public void besideATableOfItsName() {
        engine.execute("CREATE OR REPLACE TABLE r (n INT)");
        engine.execute("INSERT INTO r VALUES (100), (200)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|R.N|null|null|null "
                + ";; 1|1|[0, 3]|RecursiveWithClause|null|null|R|null|null|null ;; 1|2|[1]|ValuesClause|null|null|()|"
                + "null|null|null ;; 1|3|[1]|Filter|null|null|R.N < 3|null|null|null",
            "EXPLAIN WITH RECURSIVE r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r");
    }
}
