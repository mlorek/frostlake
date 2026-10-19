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
 * An uncorrelated scalar subquery of the select list that reads a relation is computed ahead of the rows, so what it
 * raises is the statement's error even when no row reaches the projection: the WHERE keeps none, the table holds
 * none, or the grouping makes no group — aliased or not, under a window or a DISTINCT (live-verified). Only the
 * subquery's own computation is ahead: the arithmetic around it, a subquery without a FROM and one that reads the
 * row wait for a row.
 */
public class ScalarSubqueryAheadOfNoRowsTest extends MeasuredCellsTestSupport {

    @Test
    public void aSubqueryOfTheSelectListRaisesWhenTheWhereKeepsNoRow() {
        engine.execute("CREATE OR REPLACE TABLE g AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
        engine.execute("CREATE OR REPLACE TABLE ge (i INT)");
        assertCells(new String[][] {
            {"SELECT i, (SELECT i FROM g) AS x FROM g WHERE i = 99999",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT i, (SELECT i FROM g) FROM g WHERE i = 99999",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT i, (SELECT i FROM g) + 1 FROM g WHERE i = 99999",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT i, 1 / (SELECT COUNT(*) - 1000 FROM g) FROM g WHERE i = 99999", ""},
            {"SELECT i, (SELECT i FROM g) FROM ge", "REFUSED Single-row subquery returns more than one row."},
            {"SELECT (SELECT i FROM g) FROM ge WHERE i > 0", "REFUSED Single-row subquery returns more than one row."},
            {"SELECT i, (SELECT i FROM g WHERE i < 2) FROM g WHERE i = 99999",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT i, (SELECT i FROM g LIMIT 1) FROM g WHERE i = 99999", ""},
            {"SELECT COUNT(*), (SELECT i FROM g) FROM g WHERE i = 99999",
                "REFUSED Single-row subquery returns more than one row."},
        });
    }

    @Test
    public void aSubqueryRaisesAheadWhateverStageReceivesNoRow() {
        engine.execute("CREATE OR REPLACE TABLE g AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
        engine.execute("CREATE OR REPLACE TABLE ge (i INT)");
        assertCells(new String[][] {
            {"SELECT i, (SELECT i FROM g) FROM g WHERE i = 99999 GROUP BY i",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT DISTINCT i, (SELECT i FROM g) FROM g WHERE i = 99999",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT i, (SELECT i FROM g) FROM g WHERE i = 99999 ORDER BY 1",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT i, (SELECT i FROM g) FROM g WHERE i = 99999 LIMIT 5",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT i, (SELECT i FROM g), ROW_NUMBER() OVER (ORDER BY i) FROM g WHERE i = 99999",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT 1, (SELECT i FROM g) FROM ge UNION ALL SELECT 1, 2",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT * FROM (SELECT i, (SELECT i FROM g) x FROM ge)",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT i, (SELECT i FROM g WHERE g.i = ge.i) FROM ge", ""},
            {"SELECT i, (SELECT i / 0 FROM g WHERE i = 1) FROM ge", "REFUSED Division by zero"},
            {"SELECT i, (SELECT 1 / 0) FROM ge", ""},
            {"SELECT ge.i, (SELECT i FROM g) FROM ge, g WHERE ge.i = g.i",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT i, EXISTS (SELECT i FROM g) FROM ge", ""},
            {"SELECT i, (SELECT i FROM g) + i FROM g WHERE i = 99999",
                "REFUSED Single-row subquery returns more than one row."},
        });
    }
}
