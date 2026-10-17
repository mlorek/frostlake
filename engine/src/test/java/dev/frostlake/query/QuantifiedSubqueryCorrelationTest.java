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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A quantified comparison's query is judged as an IN's is (live-verified): where the account refuses the IN — an
 * aggregate of the outer row, a LIMIT beside a correlation, a WHERE that reads the outer row alone, a set operation
 * over one — it refuses the quantified comparison too, under every operator, at its quantifier; where it answers
 * the IN, it answers the quantified comparison. A DELETE's WHERE is no query's: nothing is refused there.
 */
public class QuantifiedSubqueryCorrelationTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void createTheTables() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)", "Table FZ successfully created."},
            {"INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')", "2"},
            {"CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)", "Table G successfully created."},
            {"INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')", "2"},
            {"CREATE OR REPLACE TABLE pq2 (n INT)", "Table PQ2 successfully created."},
            {"INSERT INTO pq2 VALUES (1), (3)", "2"},
        });
    }

    @Test
    public void aQueryTheAccountCannotRunIsRefusedAtTheQuantifier() {
        assertCells(new String[][] {
            {"SELECT id, id = ANY (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 16"},
            {"SELECT id, id IN (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, id <> ALL (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, id NOT IN (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
            {"SELECT id, id > ANY (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 16"},
            {"SELECT id, id = ALL (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 16"},
            {"SELECT id, id <> ANY (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, id < ALL (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 16"},
            {"SELECT id FROM fz WHERE id = ANY (SELECT MAX(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.ID)] "
                + "can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE id = ANY (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM fz WHERE id > ANY (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM fz WHERE id = ANY (SELECT g.id FROM g WHERE fz.b) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE fz.b) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM fz WHERE id = ANY (SELECT g.id FROM g WHERE g.id = fz.id UNION SELECT 7) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.id = fz.id UNION SELECT 7) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT b FROM fz GROUP BY b HAVING 55 = ANY (SELECT MAX(fz.id + g.v) FROM g)",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b FROM fz GROUP BY b HAVING 55 IN (SELECT MAX(fz.id + g.v) FROM g)",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT id FROM fz WHERE id = ANY (SELECT AVG(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE id IN (SELECT AVG(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause"},
            {"SELECT id, id = ANY (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 16"},
            {"SELECT id, id IN (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, id <> ALL (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, id NOT IN (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
            {"SELECT id, id >= ALL (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
        });
    }

    @Test
    public void aQueryTheAccountRunsIsAnswered() {
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE id = ANY (SELECT v + fz.id FROM g LIMIT 1) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT v + fz.id FROM g LIMIT 1) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id = ANY (SELECT MAX(g.id) FROM g WHERE g.v > fz.id) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE g.v > fz.id) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id <> ALL (SELECT MAX(g.id) FROM g WHERE g.v > fz.id) ORDER BY id", "5 ;; 7"},
            {"SELECT id FROM fz WHERE id NOT IN (SELECT MAX(g.id) FROM g WHERE g.v > fz.id) ORDER BY id", "5 ;; 7"},
            {"SELECT id FROM fz WHERE id > ANY (SELECT MAX(g.id) FROM g WHERE g.v > fz.id) ORDER BY id", "7"},
            {"SELECT id FROM fz WHERE id = ANY (SELECT g.id FROM g WHERE g.s = fz.s) ORDER BY id", "5"},
            {"SELECT id FROM fz WHERE id > ANY (SELECT g.id FROM g WHERE g.s = fz.s) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id < ALL (SELECT g.id FROM g WHERE g.s = fz.s) ORDER BY id", "7"},
            {"SELECT id FROM fz WHERE id = ALL (SELECT g.id FROM g WHERE g.s = fz.s) ORDER BY id", "5 ;; 7"},
            {"SELECT id FROM fz WHERE id <> ANY (SELECT g.id FROM g WHERE g.s = fz.s) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id = ANY (SELECT g.id FROM g WHERE g.v > fz.id) ORDER BY id", "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.v > fz.id) ORDER BY id", "5"},
            {"SELECT id FROM fz WHERE id > ANY (SELECT g.id FROM g WHERE g.v > fz.id) ORDER BY id", "7"},
            {"SELECT id FROM fz WHERE id = ANY (SELECT fz.id) ORDER BY id", "5 ;; 7"},
            {"SELECT id FROM fz WHERE id IN (SELECT fz.id) ORDER BY id", "5 ;; 7"},
            {"SELECT id, id = ANY (SELECT g.id FROM g WHERE g.v > fz.id) FROM fz ORDER BY id", "5|true ;; 7|false"},
            {"SELECT id, id IN (SELECT g.id FROM g WHERE g.v > fz.id) FROM fz ORDER BY id", "5|true ;; 7|false"},
            {"SELECT id, id > ANY (SELECT g.id FROM g WHERE g.v > fz.id) FROM fz ORDER BY id", "5|false ;; 7|true"},
            {"SELECT id FROM fz WHERE id + 100 = ANY (SELECT g.id FROM g WHERE fz.id = fz.id) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id + 100 IN (SELECT g.id FROM g WHERE fz.id = fz.id) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE NOT (id = ANY (SELECT g.id FROM g WHERE g.v > fz.id)) ORDER BY id", "7"},
            {"SELECT id FROM fz WHERE id = ANY (SELECT g.id FROM g WHERE g.v > fz.id) OR b ORDER BY id", "5"},
        });
    }

    @Test
    public void aGroupedOrLimitedQueryIsJudgedAsInIs() {
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE id = ANY (SELECT g.id FROM g WHERE g.v > fz.id GROUP BY g.id) ORDER BY id", "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.v > fz.id GROUP BY g.id) ORDER BY id", "5"},
            {"SELECT id FROM fz WHERE id > ANY (SELECT g.id FROM g WHERE g.v > fz.id GROUP BY g.id) ORDER BY id", "7"},
            {"SELECT id FROM fz WHERE id <> ALL (SELECT g.id FROM g WHERE g.v > fz.id GROUP BY g.id) ORDER BY id", "7"},
            {"SELECT id FROM fz WHERE id NOT IN (SELECT g.id FROM g WHERE g.v > fz.id GROUP BY g.id) ORDER BY id", "7"},
            {"SELECT id, id = ANY (SELECT g.id FROM g WHERE g.v > fz.id GROUP BY g.id) FROM fz ORDER BY id",
                "5|true ;; 7|false"},
            {"SELECT id, id IN (SELECT g.id FROM g WHERE g.v > fz.id GROUP BY g.id) FROM fz ORDER BY id",
                "5|true ;; 7|false"},
            {"SELECT id FROM fz WHERE id = SOME (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM fz WHERE id != ALL (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT b FROM fz GROUP BY b HAVING MAX(id) = ANY (SELECT g.id FROM g WHERE g.v > MAX(fz.id) LIMIT 1)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM fz WHERE id = ANY (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) AND b ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM fz WHERE b AND id > ANY (SELECT g.id FROM g WHERE fz.b) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id FROM fz QUALIFY id = ANY (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: error line 1 at position 18 | found QUALIFY clause but no window "
                + "function."},
            {"SELECT id FROM fz ORDER BY id = ANY (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1), id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT (SELECT 1 WHERE 5 = ANY (SELECT g.id FROM g WHERE g.id = fz.id LIMIT 1)) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
        });
    }

    @Test
    public void aDeletesWhereRefusesNothing() {
        assertCells(new String[][] {
            {"DELETE FROM pq2 WHERE n = ANY (SELECT g.id FROM g WHERE g.id = pq2.n LIMIT 1)", "0"},
            {"DELETE FROM pq2 WHERE n IN (SELECT g.id FROM g WHERE g.id = pq2.n LIMIT 1)", "0"},
        });
    }
}
