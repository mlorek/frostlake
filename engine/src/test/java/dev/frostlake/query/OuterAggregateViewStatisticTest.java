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
 * An aggregate over the outer row in a derived table or a scalar subquery: a view's whole MIN and MAX read from
 * its table's statistics, a COUNT planned as the derived table's rows less the column's NULLs, the nested-aggregate
 * echo of a view's column, a COUNT and an AVG, and an outer aggregate beside the subquery's own column refused as a
 * subquery live cannot evaluate (all live-verified).
 */
public class OuterAggregateViewStatisticTest extends MeasuredCellsTestSupport {

    @Test
    public void outerAggregates() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT)");
        engine.execute("INSERT INTO fz VALUES (5), (7)");
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT)");
        engine.execute("INSERT INTO fz3 VALUES (5), (7), (9)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 1), (6, 2)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 10), (6, 20)");
        engine.execute("CREATE OR REPLACE VIEW vz3 AS SELECT id FROM fz3");
        assertAnswer("5|18 ;; 7|18 ;; 9|18",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(vz3.id) AS x FROM g3)) FROM vz3 ORDER BY id");
        assertAnswer("5|18 ;; 7|18 ;; 9|18",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SCALED_ROUND_INT_DIVIDE(SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0))), 2) AS NUMBER(38,6))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id");
        assertAnswer("5|10 ;; 7|10 ;; 9|10",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MIN(vz3.id) AS x FROM g3)) FROM vz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MAX(fz.id) + g.v FROM g WHERE g.id = 5) FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MAX(fz3.id) + g3.v FROM g3) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT g3.v + MAX(fz3.id) FROM g3) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MAX(fz3.id) FROM g3 WHERE g3.id = 5) FROM fz3 ORDER BY id");
    }

    @Test
    public void viewsAndAverages() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT)");
        engine.execute("INSERT INTO fz VALUES (5), (7)");
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT)");
        engine.execute("INSERT INTO fz3 VALUES (5), (7), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzn (id INT)");
        engine.execute("INSERT INTO fzn VALUES (5), (NULL), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzd (id NUMBER(10,2))");
        engine.execute("INSERT INTO fzd VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE fzf (id FLOAT)");
        engine.execute("INSERT INTO fzf VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 1), (6, 2)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 10), (6, 20)");
        engine.execute("CREATE OR REPLACE TABLE g4 (id INT, v INT)");
        engine.execute("INSERT INTO g4 VALUES (1, 1), (2, 2), (3, 3)");
        engine.execute("CREATE OR REPLACE VIEW vz3 AS SELECT id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3f AS SELECT id FROM fz3 WHERE id < 9");
        engine.execute("CREATE OR REPLACE VIEW vz3e AS SELECT id + 1 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3v AS SELECT id FROM vz3");
        engine.execute("CREATE OR REPLACE VIEW vz3g AS SELECT id FROM fz3 GROUP BY id");
        engine.execute("CREATE OR REPLACE VIEW vz3n AS SELECT id AS n FROM fz3");
        assertAnswer("5|18 ;; 7|18",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(vz3f.id) AS x FROM g3)) FROM vz3f ORDER BY id");
        assertAnswer("5|18 ;; 7|18 ;; 9|18",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(vz3v.id) AS x FROM g3)) FROM vz3v ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(VZ3G.ID)] nested "
                + "in [SUM(CORRELATION(CAST(MAX(VZ3G.ID) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(vz3g.id) AS x FROM g3)) FROM vz3g ORDER BY id");
        assertAnswer("5|4 ;; 7|4 ;; 9|4",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(vz3.id) AS x FROM g3)) FROM vz3 ORDER BY id");
        assertAnswer("5|10 ;; 7|10 ;; 9|10",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MIN(v.id) AS x FROM g3)) FROM vz3 v ORDER BY id");
        assertAnswer("5|18 ;; 7|18 ;; 9|18",
            "SELECT n, (SELECT SUM(x) FROM (SELECT MAX(vz3n.n) AS x FROM g3)) FROM vz3n ORDER BY n");
        assertAnswer("7|18",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(vz3f.id) AS x FROM g3)) FROM vz3f WHERE id > 5 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SCALED_ROUND_INT_DIVIDE(SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0))), 3) AS NUMBER(38,6))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fz3.id) AS x FROM g4)) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SCALED_ROUND_INT_DIVIDE(SUM(CAST(FZ.ID AS "
                + "NUMBER(38,0))), 2) AS NUMBER(38,6))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fz.id) AS x FROM g)) FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZN.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SCALED_ROUND_INT_DIVIDE(SUM(CAST(FZN.ID AS "
                + "NUMBER(38,0))), 1) AS NUMBER(38,6))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fzn.id) AS x FROM g3)) FROM fzn ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZD.ID AS "
                + "NUMBER(10,2)))] nested in [SUM(CORRELATION((CAST(SUM(CAST(FZD.ID AS NUMBER(10,2))) AS NUMBER(28,"
                + "8))) / 2))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fzd.id) AS x FROM g3)) FROM fzd ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(FZF.ID)] nested in "
                + "[SUM(CORRELATION((SUM(FZF.ID)) / (CAST(2 AS FLOAT))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fzf.id) AS x FROM g3)) FROM fzf ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fz3.id) AS x FROM g3 WHERE g3.id = 5)) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [MAX(CORRELATION(SCALED_ROUND_INT_DIVIDE(SUM(CAST(FZ3.ID AS NUMBER(38,"
                + "0))), 2)))]",
            "SELECT id, (SELECT MAX(x) FROM (SELECT AVG(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SCALED_ROUND_INT_DIVIDE(SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0))), 2) AS NUMBER(38,6))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3.id) AS x FROM g3)) FROM vz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8",
            "SELECT (SELECT MAX(fz.id) + g.v FROM g WHERE g.id = 5) FROM fz");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17",
            "SELECT MAX(id), (SELECT MAX(fz.id) + g.v FROM g WHERE g.id = 5) FROM fz");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MAX(fz.id) + g.v FROM g WHERE g.id = 5) FROM fz GROUP BY id ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 38",
            "SELECT id FROM fz GROUP BY id HAVING (SELECT MAX(fz.id) + g.v FROM g WHERE g.id = 5) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MAX(fz.id) + g.v + MAX(g.v) FROM g WHERE g.id = 5) FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT g.v FROM g WHERE g.id = 5 AND MAX(fz.id) > 0) FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT COUNT(fz.id) + g.v FROM g WHERE g.id = 5) FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MAX(fz.id) + g.v FROM g WHERE g.id = fz.id) FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MAX(fz.id) + MAX(g.v) FROM g WHERE g.id = 5) FROM fz ORDER BY id");
        assertAnswer("5|true ;; 7|true",
            "SELECT id, EXISTS (SELECT MAX(fz.id) + g.v FROM g WHERE g.id = 5) FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.ID)] "
                + "can only appear in having or select clause",
            "SELECT id FROM fz WHERE id IN (SELECT MAX(fz.id) + g.v FROM g WHERE g.id = 5) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17",
            "SELECT MAX(id), (SELECT MAX(fz.id) + g.v FROM g) FROM fz");
        assertAnswer("REFUSED SQL compilation error: | [G.V] is not a valid group by expression",
            "SELECT id, (SELECT g.v + COUNT(*) FROM g WHERE g.id = 5) FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MAX(fz.id) + g.v FROM g WHERE g.id = 5 GROUP BY g.v) FROM fz ORDER BY id");
    }

    @Test
    public void derivedCounts() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT)");
        engine.execute("INSERT INTO fz VALUES (5), (7)");
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT)");
        engine.execute("INSERT INTO fz3 VALUES (5), (7), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzn (id INT)");
        engine.execute("INSERT INTO fzn VALUES (5), (NULL), (9)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 1), (6, 2)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 10), (6, 20)");
        engine.execute("CREATE OR REPLACE TABLE g4 (id INT, v INT)");
        engine.execute("INSERT INTO g4 VALUES (1, 1), (2, 2), (3, 3)");
        engine.execute("CREATE OR REPLACE VIEW vz3 AS SELECT id FROM fz3");
        assertAnswer("5|4 ;; 7|4 ;; 9|4",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id");
        assertAnswer("5|9 ;; 7|9 ;; 9|9",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fz3.id) AS x FROM g4)) FROM fz3 ORDER BY id");
        assertAnswer("5|4 ;; 7|4",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fz.id) AS x FROM g)) FROM fz ORDER BY id");
        assertAnswer("5|2 ;; 9|2 ;; null|2",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fzn.id) AS x FROM g3)) FROM fzn ORDER BY id");
        assertAnswer("5|9 ;; 7|9 ;; 9|9",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(vz3.id) AS x FROM g4)) FROM vz3 ORDER BY id");
        assertAnswer("5|4 ;; 7|4 ;; 9|4",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(vz3.id) AS x FROM g3)) FROM vz3 ORDER BY id");
        assertAnswer("5|18 ;; 7|18",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(v.id) AS x FROM g3)) FROM vz3 v WHERE v.id < 9 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [MAX(CORRELATION(SCALED_ROUND_INT_DIVIDE(SUM(CAST(FZ3.ID AS NUMBER(38,"
                + "0))), 3)))]",
            "SELECT id, (SELECT MAX(x) FROM (SELECT AVG(vz3.id) AS x FROM g4)) FROM vz3 ORDER BY id");
    }

    @Test
    public void countsAndAverages() {
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT)");
        engine.execute("INSERT INTO fz3 VALUES (5), (7), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzn (id INT)");
        engine.execute("INSERT INTO fzn VALUES (5), (NULL), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzq (id INT)");
        engine.execute("INSERT INTO fzq VALUES (5), (NULL), (NULL), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzd2 (id NUMBER(5,1))");
        engine.execute("INSERT INTO fzd2 VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE fzd3 (id NUMBER(20,4))");
        engine.execute("INSERT INTO fzd3 VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE fzf (id FLOAT)");
        engine.execute("INSERT INTO fzf VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 10), (6, 20)");
        engine.execute("CREATE OR REPLACE TABLE g5 (id INT, v INT)");
        engine.execute("INSERT INTO g5 VALUES (1, 1), (2, 2), (3, 3), (4, 4)");
        engine.execute("CREATE OR REPLACE TABLE g7 (id INT)");
        engine.execute("INSERT INTO g7 VALUES (1), (2), (3), (4), (5), (6), (7)");
        assertAnswer("5|12 ;; 9|12 ;; null|12",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fzn.id) AS x FROM g5)) FROM fzn ORDER BY id");
        assertAnswer("5|35 ;; 9|35 ;; null|35 ;; null|35",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fzq.id) AS x FROM g7)) FROM fzq ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ3.ID)] nested "
                + "in [SUM(CORRELATION(CAST(COUNT(FZ3.ID) AS NUMBER(18,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fz3.id) AS x FROM g3 WHERE g3.id = 5)) FROM fz3 ORDER BY id");
        assertAnswer("5|4 ;; 7|4 ;; 9|4",
            "SELECT id, (SELECT MAX(x) FROM (SELECT COUNT(fz3.id) AS x FROM g5)) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZN.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SCALED_ROUND_INT_DIVIDE(SUM(CAST(FZN.ID AS "
                + "NUMBER(38,0))), 3) AS NUMBER(38,6))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fzn.id) AS x FROM g5)) FROM fzn ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZD2.ID AS "
                + "NUMBER(5,1)))] nested in [SUM(CORRELATION((CAST(SUM(CAST(FZD2.ID AS NUMBER(5,1))) AS NUMBER(23,"
                + "7))) / 2))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fzd2.id) AS x FROM g3)) FROM fzd2 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZD3.ID AS "
                + "NUMBER(20,4)))] nested in [SUM(CORRELATION((CAST(SUM(CAST(FZD3.ID AS NUMBER(20,4))) AS NUMBER(38,"
                + "10))) / 2))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fzd3.id) AS x FROM g3)) FROM fzd3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZD2.ID AS "
                + "NUMBER(5,1)))] nested in [MAX(CORRELATION((CAST(SUM(CAST(FZD2.ID AS NUMBER(5,1))) AS NUMBER(23,"
                + "7))) / 2))]",
            "SELECT id, (SELECT MAX(x) FROM (SELECT AVG(fzd2.id) AS x FROM g3)) FROM fzd2 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(FZF.ID)] nested in "
                + "[MAX(CORRELATION((SUM(FZF.ID)) / (CAST(2 AS FLOAT))))]",
            "SELECT id, (SELECT MAX(x) FROM (SELECT AVG(fzf.id) AS x FROM g3)) FROM fzf ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(FZF.ID)] nested in "
                + "[SUM(CORRELATION((SUM(FZF.ID)) / (CAST(COUNT(FZF.ID) AS FLOAT))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fzf.id) AS x FROM g3 WHERE g3.id = 5)) FROM fzf ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [MAX(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT MAX(x) FROM (SELECT AVG(fz3.id) AS x FROM g3 WHERE g3.id = 5)) FROM fz3 ORDER BY id");
    }
}
