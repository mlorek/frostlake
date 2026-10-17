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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Where a membership over a query may stand, and what it answers (live-verified): a NOT IN over a query that reads
 * nothing of the outer row is refused outside the WHERE — in the select list, a CASE there, the HAVING and the
 * ORDER BY — where the query's one column may hold NULL; a correlated IN over an aggregate is refused in a select
 * list for a filter only a WHERE's semi-join takes — an OR, an inequality, an outer name alone — and for an item
 * turning the aggregate's NULL into a value; a probe that is NULL whatever row is read, a derived relation's NULL
 * constant among them, makes a membership UNKNOWN whatever the query answers.
 */
public class MembershipPlacementTest extends MeasuredCellsTestSupport {

    private static final String THREE_VALUES =
        "the account answers these memberships as a null-unaware anti-join and a count over no row would, which"
        + " contradicts its own answer for the same membership written otherwise; this engine keeps the"
        + " membership's three-valued answer";

    @BeforeEach
    public void createTheTables() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)", "Table FZ successfully created."},
            {"INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')", "2"},
            {"CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)", "Table G successfully created."},
            {"INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')", "2"},
            {"CREATE OR REPLACE TABLE fo (id INT, k VARCHAR)", "Table FO successfully created."},
            {"INSERT INTO fo VALUES (5, 'a'), (7, 'b'), (9, 'd'), (NULL, 'a'), (NULL, 'b')", "5"},
            {"CREATE OR REPLACE TABLE gn (id INT, k VARCHAR)", "Table GN successfully created."},
            {"INSERT INTO gn VALUES (NULL, 'a'), (5, 'a'), (6, 'c'), (NULL, 'd')", "4"},
            {"CREATE OR REPLACE TABLE fn (id INT, n INT)", "Table FN successfully created."},
            {"INSERT INTO fn VALUES (0, NULL), (2, 1), (5, NULL)", "3"},
            {"CREATE OR REPLACE TABLE n (k INT)", "Table N successfully created."},
            {"INSERT INTO n VALUES (2), (10)", "2"},
            {"CREATE OR REPLACE TABLE s (t VARCHAR)", "Table S successfully created."},
            {"INSERT INTO s VALUES ('02'), ('10.0'), ('x')", "3"},
            {"CREATE OR REPLACE TABLE gnn (id INT NOT NULL)", "Table GNN successfully created."},
            {"INSERT INTO gnn VALUES (5), (6)", "2"},
        });
    }

    @Test
    public void anUncorrelatedNotInOutsideTheWhereIsRefusedOverANullableColumn() {
        assertCells(new String[][] {
            {"SELECT id, k, id NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'a') FROM fo ORDER BY k, id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25"},
            {"SELECT id, 99 NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'a') FROM fo ORDER BY k, id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
            {"SELECT id, 99 NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'c') FROM fo ORDER BY k, id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
            {"SELECT id, k, id NOT IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
                "5|a|false ;; null|a|null ;; 7|b|true ;; null|b|true ;; 9|d|null"},
            {"SELECT id FROM fo WHERE id NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'c') ORDER BY id", "5 ;; 7 ;; 9"},
            {"SELECT id, id NOT IN (SELECT 5) FROM fo ORDER BY k, id",
                "5|false ;; null|null ;; 7|true ;; null|null ;; 9|true"},
            {"SELECT id, id NOT IN (SELECT 5 UNION ALL SELECT NULL) FROM fo ORDER BY k, id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
            {"SELECT id, id IN (SELECT gn.id FROM gn) FROM fo ORDER BY k, id",
                "5|true ;; null|null ;; 7|null ;; null|null ;; 9|null"},
            {"SELECT k NOT IN (SELECT t FROM s WHERE t <> 'x') FROM n ORDER BY k",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT COUNT(*) FROM n WHERE k NOT IN (SELECT t FROM s)", "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT id, NOT (id IN (SELECT gn.id FROM gn WHERE gn.k = 'a')) FROM fo ORDER BY k, id",
                "5|false ;; null|null ;; 7|null ;; null|null ;; 9|null"},
            {"SELECT k, COUNT(*) FROM fo GROUP BY k HAVING MAX(id) NOT IN (SELECT gn.id FROM gn WHERE gn.k = "
                + "'c') ORDER BY k",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 61"},
            {"SELECT id FROM fo QUALIFY id NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'c') ORDER BY id",
                "REFUSED SQL compilation error: error line 1 at position 18 | found QUALIFY clause but no window "
                + "function."},
            {"SELECT id FROM fo ORDER BY id NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'c'), id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 38"},
            {"SELECT id, CASE WHEN id NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'c') THEN 1 END FROM fo ORDER BY k, id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id, id NOT IN (SELECT id FROM gn) FROM fo WHERE id IS NOT NULL ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
        });
    }

    @Test
    public void aCorrelatedInOverAnAggregateIsRefusedForAFilterOnlyAWhereTakes() {
        assertCells(new String[][] {
            {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id OR g.s = fz.s) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.id > fn.id) FROM fn ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE fn.id = 5) FROM fn ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, 0 IN (SELECT COALESCE(SUM(g.v), 0) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, 0 IN (SELECT ZEROIFNULL(MAX(g.v)) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id OR g.s = fz.s) ORDER BY id",
                "5"},
            {"SELECT id FROM fn WHERE id IN (SELECT MAX(g.id) FROM g WHERE g.id > fn.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|true ;; 7|false"},
            {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.s = fz.s AND g.v > 1) FROM fz ORDER BY id",
                "5|true ;; 7|false"},
            {"SELECT id, id NOT IN (SELECT MAX(g.id) FROM g WHERE g.id > fz.id) FROM fz ORDER BY id",
                "5|true ;; 7|null"},
            {"SELECT id, 1 IN (SELECT SUM(g.v) / 50 FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
                "0|false ;; 2|false ;; 5|true"},
        });
    }

    @Test
    public void aProbeThatIsNullWhateverRowIsReadIsUnknown() {
        assertCells(new String[][] {
            {"SELECT id, k FROM fo WHERE id NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'a') ORDER BY k, id", ""},
            {"SELECT id, k FROM fo WHERE (id IN (SELECT gn.id FROM gn WHERE gn.k = 'a')) IS NULL ORDER BY k, id",
                "null|a ;; 7|b ;; null|b ;; 9|d"},
            {"SELECT id, k FROM fo WHERE NOT (id IN (5, NULL)) ORDER BY k, id", ""},
            {"SELECT id, k FROM fo WHERE NOT (id IN (SELECT 5)) ORDER BY k, id", "7|b ;; 9|d"},
            {"SELECT id, k FROM fo WHERE NOT (id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k)) ORDER BY k, id",
                "null|a ;; 7|b ;; null|b ;; 9|d"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT NULL AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT CAST(NULL AS INT) AS x)", "null"},
            {"SELECT x, x IN (SELECT 1 WHERE FALSE) FROM (VALUES (NULL), (1)) t(x) ORDER BY x",
                "1|false ;; null|false"},
            {"SELECT x NOT IN (SELECT 1 WHERE FALSE) FROM (SELECT NULL AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT 5 AS x)", "false"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT NULL AS x, 1 AS y)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT NULL AS x) WHERE TRUE", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT NULL AS x) d", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT NULL AS x FROM fz LIMIT 1)", "null"},
            {"SELECT x IN (SELECT 2) FROM (SELECT NULL AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE), x FROM (SELECT NULL AS x) JOIN fz ON TRUE ORDER BY 1",
                "null|null ;; null|null"},
        });
    }

    @Test
    public void aMembershipKeepsItsThreeValues() {
        Assumptions.assumeFalse(isLiveSnowflake(), THREE_VALUES);
        assertCells(new String[][] {
            {"SELECT id, id NOT IN (SELECT id FROM gnn) FROM fo ORDER BY k, id",
                "5|false ;; null|null ;; 7|true ;; null|null ;; 9|true"},
            {"SELECT id, 0 IN (SELECT COUNT(*) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
                "0|true ;; 2|true ;; 5|false"},
            {"SELECT id, k FROM fo WHERE NOT (id IN (SELECT gn.id FROM gn WHERE gn.k = 'a')) ORDER BY k, id", ""},
            {"SELECT id, k FROM fo WHERE NOT (id IN (SELECT gn.id FROM gn WHERE gn.k = 'c')) ORDER BY k, id",
                "5|a ;; 7|b ;; 9|d"},
        });
    }
}
