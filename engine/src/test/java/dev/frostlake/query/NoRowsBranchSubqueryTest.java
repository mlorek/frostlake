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
 * A scalar subquery of several rows in a CASE branch or a choosing call of the select list: raised when no row
 * reaches the projection, over a table with no rows and under a WHERE that keeps none, and left uncomputed where
 * rows reach it and none takes the branch (all live-verified).
 */
public class NoRowsBranchSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void branchesOverNoRows() {
        engine.execute("CREATE OR REPLACE TABLE g2087 AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 10))");
        engine.execute("CREATE OR REPLACE TABLE ge2087 (i INT)");
        engine.execute("CREATE OR REPLACE TABLE gs2087 (i INT)");
        engine.execute("INSERT INTO gs2087 VALUES (1), (2)");
        assertAnswer("REFUSED Single-row subquery returns more than one row.",
            "SELECT i, CASE WHEN i > 0 THEN (SELECT i FROM g2087) END FROM ge2087");
        assertAnswer("REFUSED Single-row subquery returns more than one row.",
            "SELECT i, COALESCE((SELECT i FROM g2087), 1) FROM ge2087");
        assertAnswer("REFUSED Single-row subquery returns more than one row.",
            "SELECT i, (SELECT i FROM g2087) FROM ge2087");
        assertAnswer("REFUSED Single-row subquery returns more than one row.",
            "SELECT i, CASE WHEN i > 0 THEN (SELECT i FROM g2087) END FROM gs2087 WHERE i > 5");
        assertAnswer("REFUSED Single-row subquery returns more than one row.",
            "SELECT i, COALESCE((SELECT i FROM g2087), 1) FROM gs2087 WHERE i > 5");
        assertAnswer("1|null ;; 2|null",
            "SELECT i, CASE WHEN i > 5 THEN (SELECT i FROM g2087) END FROM gs2087 ORDER BY i");
        assertAnswer("1|1 ;; 2|2", "SELECT i, COALESCE(i, (SELECT i FROM g2087)) FROM gs2087 ORDER BY i");
        assertAnswer("REFUSED Single-row subquery returns more than one row.",
            "SELECT i, IFF(i > 5, (SELECT i FROM g2087), 0) FROM ge2087");
        assertAnswer("REFUSED Single-row subquery returns more than one row.",
            "SELECT i, NVL((SELECT i FROM g2087), 1) FROM ge2087");
        assertAnswer("1|0 ;; 2|0", "SELECT i, IFF(i > 5, (SELECT i FROM g2087), 0) FROM gs2087 ORDER BY i");
        assertAnswer("REFUSED Single-row subquery returns more than one row.",
            "SELECT i, CASE WHEN i > 0 THEN (SELECT i FROM g2087) END FROM gs2087 WHERE FALSE");
        assertAnswer("0|null", "SELECT COUNT(*), CASE WHEN COUNT(*) > 0 THEN (SELECT i FROM g2087) END FROM ge2087");
    }
}
