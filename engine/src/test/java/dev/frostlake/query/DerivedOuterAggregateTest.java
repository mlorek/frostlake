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
 * An aggregate over the outer row alone written in a derived table's select list is the outer query's own value,
 * not an aggregate of the derived table, whose rows are its own FROM's (live-verified). Where nothing above reads the
 * item the value is ignored; where the select over the derived table reads it inside its own aggregates alone, a MIN
 * or a MAX of a number or a date column is the outer table's extreme over the whole table, the outer query's WHERE,
 * grouping and joins ignored; a read anywhere else is refused as an unsupported subquery, and one beside a filter, a
 * grouping or another relation, or of a call the statistics do not answer, as an aggregate nested in the one reading
 * it.
 */
public class DerivedOuterAggregateTest extends MeasuredCellsTestSupport {

    @Test
    public void anOuterAggregateNobodyReadsLeavesTheDerivedRowsItsFromsOwn() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertCells(new String[][] {
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g)) FROM fz ORDER BY id", "5|2 ;; 7|2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g WHERE g.id = fz.id)) FROM fz ORDER BY id",
                "5|1 ;; 7|0"},
            {"SELECT id, EXISTS (SELECT 1 FROM (SELECT MAX(fz.id) AS x FROM g)) FROM fz ORDER BY id",
                "5|true ;; 7|true"},
            {"SELECT id, EXISTS (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g) HAVING COUNT(*) = 2) FROM "
                + "fz ORDER BY id",
                "5|true ;; 7|true"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g) d) FROM fz ORDER BY id", "5|2 ;; 7|2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g GROUP BY g.s)) FROM fz ORDER BY id",
                "5|2 ;; 7|2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g LIMIT 1)) FROM fz ORDER BY id",
                "5|1 ;; 7|1"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g), g) FROM fz ORDER BY id", "5|4 ;; 7|4"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g)) FROM fz WHERE id < 6 ORDER BY id",
                "5|2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT AVG(fz.id) AS x FROM g)) FROM fz ORDER BY id", "5|2 ;; 7|2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g UNION ALL SELECT 1)) FROM fz ORDER BY id",
                "5|3 ;; 7|3"},
            {"SELECT id FROM fz WHERE (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g)) = 2 ORDER BY id",
                "5 ;; 7"},
            {"SELECT MAX(id), (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g)) FROM fz", "7|2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g) WHERE 1 = 1) FROM fz ORDER BY id",
                "5|2 ;; 7|2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT y FROM (SELECT MAX(fz.id) AS y FROM g))) FROM fz ORDER BY id",
                "5|2 ;; 7|2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x, MIN(fz.id) AS y FROM g)) FROM fz ORDER BY id",
                "5|2 ;; 7|2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) FROM g)) FROM fz ORDER BY id", "5|2 ;; 7|2"},
        });
    }

    @Test
    public void aReadMinOrMaxIsTheWholeOuterTablesExtreme() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertCells(new String[][] {
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz.id) AS x FROM g)) FROM fz ORDER BY id", "5|14 ;; 7|14"},
            {"SELECT id, (SELECT ANY_VALUE(x) FROM (SELECT MAX(fz.id) AS x FROM g)) FROM fz ORDER BY id", "5|7 ;; 7|7"},
            {"SELECT id, (SELECT COUNT(*) FROM g ORDER BY (SELECT MAX(fz.id))) FROM fz ORDER BY id", "5|2 ;; 7|2"},
            {"SELECT id, (SELECT MIN(x) FROM (SELECT MIN(fz.id) AS x FROM g)) FROM fz ORDER BY id", "5|5 ;; 7|5"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fz.s) AS x FROM g)) FROM fz ORDER BY id", "5|4 ;; 7|4"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz.id) + 1 AS x FROM g)) FROM fz ORDER BY id", "5|16 ;; 7|16"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz.id) AS x, g.v FROM g)) FROM fz ORDER BY id",
                "5|14 ;; 7|14"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz.id) AS x FROM g)) FROM fz WHERE id < 6 ORDER BY id",
                "5|14"},
            {"SELECT s, (SELECT SUM(x) FROM (SELECT MAX(fz.id) AS x FROM g)) FROM fz GROUP BY s ORDER BY s",
                "a|14 ;; b|14"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz.id) AS x FROM g) WHERE TRUE) FROM fz ORDER BY id",
                "5|14 ;; 7|14"},
            {"SELECT id, (SELECT COUNT(x) FROM (SELECT MAX(fz.id) AS x FROM g)) FROM fz ORDER BY id", "5|2 ;; 7|2"},
            {"SELECT id, (SELECT MAX(x) FROM (SELECT MAX(fz.id) AS x FROM g)) FROM fz ORDER BY id", "5|7 ;; 7|7"},
            {"SELECT id, (SELECT SUM(x + y) FROM (SELECT MAX(fz.id) AS x, MIN(fz.id) AS y FROM g)) FROM fz ORDER BY id",
                "5|24 ;; 7|24"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(*) AS x FROM g)) FROM fz ORDER BY id", "5|2 ;; 7|2"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz.id) AS x FROM g WHERE g.v > fz.id)) FROM fz ORDER BY id",
                "5|14 ;; 7|14"},
        });
    }

    @Test
    public void aReadElsewhereOrBesideAClauseIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertCells(new String[][] {
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g) WHERE x = 7) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, EXISTS (SELECT 1 FROM (SELECT MAX(fz.id) AS x FROM g) WHERE x = 7) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT SUM(fz.id) AS x FROM g)) FROM fz ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SUM(CAST(FZ.ID AS NUMBER(38,0))) AS "
                + "NUMBER(38,0))))]"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz.id) AS x FROM g WHERE g.v > 55)) FROM fz ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ.ID) AS NUMBER(38,0))))]"},
            {"SELECT id, (SELECT x FROM (SELECT MAX(fz.id) AS x FROM g) LIMIT 1) FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g) WHERE x IS NOT NULL) FROM fz "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz.id) AS x FROM g WHERE g.id = fz.id)) FROM fz ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ.ID) AS NUMBER(38,0))))]"},
        });
    }

    @Test
    public void overThreeRowsAViewAJoinAndADate() {
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT, s VARCHAR)");
        engine.execute("INSERT INTO fz3 VALUES (5, 'a'), (7, 'b'), (9, NULL)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE VIEW vz3 AS SELECT * FROM fz3");
        assertCells(new String[][] {
            {"SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(*) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|2 ;; 7|2 ;; 9|2"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|18 ;; 7|18 ;; 9|18"},
            {"SELECT id, (SELECT ANY_VALUE(x) FROM (SELECT MIN(fz3.s) AS x FROM g3)) FROM fz3 ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MIN(FZ3.S)] nested in "
                + "[ANY_VALUE(CORRELATION(MIN(FZ3.S)))]"},
            {"SELECT id, (SELECT LISTAGG(x, ',') FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|9,9 ;; 7|9,9 ;; 9|9,9"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 WHERE id > 100", ""},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(vz3.id) AS x FROM g3)) FROM vz3 ORDER BY id",
                "5|2 ;; 7|2 ;; 9|2"},
            {"SELECT fz3.id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 JOIN g3 ON g3.id = fz3.id",
                "5|18"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3 WHERE g3.v > 55)) FROM fz3 ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ3.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ3.ID) AS NUMBER(38,0))))]"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT SUM(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|2 ;; 7|2 ;; 9|2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz3.id) AS x FROM g3 WHERE g3.id = fz3.id)) FROM fz3 "
                + "ORDER BY id",
                "5|1 ;; 7|0 ;; 9|0"},
            {"SELECT id, EXISTS (SELECT 1 FROM (SELECT MAX(fz3.id) AS x FROM g3 WHERE g3.id = fz3.id)) FROM fz3 "
                + "ORDER BY id",
                "5|true ;; 7|false ;; 9|false"},
            {"SELECT id, (SELECT MAX(fz3.id) FROM g3) FROM fz3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MIN(fz3.id) + MAX(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|28 ;; 7|28 ;; 9|28"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x, MAX(fz3.id) AS y FROM g3)) FROM fz3 ORDER BY id",
                "5|18 ;; 7|18 ;; 9|18"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(id) AS x FROM g3)) FROM fz3 ORDER BY id", "5|6 ;; 7|6 ;; 9|6"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3) WHERE TRUE) FROM fz3 ORDER BY id",
                "5|18 ;; 7|18 ;; 9|18"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3) d WHERE d.x > 0) FROM fz3 ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ3.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ3.ID) AS NUMBER(38,0))))]"},
            {"SELECT id, (SELECT MAX(x) + MIN(x) FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|18 ;; 7|18 ;; 9|18"},
            {"SELECT id, (SELECT COUNT(DISTINCT x) FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|1 ;; 7|1 ;; 9|1"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3) GROUP BY x) FROM fz3 ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ3.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ3.ID) AS NUMBER(38,0))))]"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3) HAVING SUM(x) > 0) FROM fz3 ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ3.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ3.ID) AS NUMBER(38,0))))]"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3) ORDER BY 1) FROM fz3 ORDER BY id",
                "5|18 ;; 7|18 ;; 9|18"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3) d, g3) FROM fz3 ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ3.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ3.ID) AS NUMBER(38,0))))]"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3)) + 1 FROM fz3 ORDER BY id",
                "5|19 ;; 7|19 ;; 9|19"},
            {"SELECT id, (SELECT x FROM (SELECT MAX(fz3.id) AS x FROM g3) WHERE x = 9 LIMIT 1) FROM fz3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3) d JOIN g3 ON g3.id = 5) FROM fz3 "
                + "ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ3.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ3.ID) AS NUMBER(38,0))))]"},
        });
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT, s VARCHAR, d DATE)");
        engine.execute("INSERT INTO fz3 VALUES (5, 'a', '2020-01-05'), (7, 'b', '2020-01-07'), (9, NULL, NULL)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 50), (6, 60)");
        assertCells(new String[][] {
            {"SELECT id, (SELECT ANY_VALUE(x) FROM (SELECT MIN(fz3.d) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|2020-01-05 ;; 7|2020-01-05 ;; 9|2020-01-05"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3) d) FROM fz3 ORDER BY id",
                "5|18 ;; 7|18 ;; 9|18"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 WHERE id > 5 ORDER BY id",
                "7|18 ;; 9|18"},
            {"SELECT id, EXISTS (SELECT 1 FROM (SELECT MAX(fz3.id) AS x FROM g3) WHERE TRUE) FROM fz3 ORDER BY id",
                "5|true ;; 7|true ;; 9|true"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MAX(fz3.id) AS x FROM g3 WHERE g3.id = fz3.id) d) FROM "
                + "fz3 ORDER BY id",
                "5|1 ;; 7|0 ;; 9|0"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x, g3.v FROM g3) WHERE v > 55) FROM fz3 "
                + "ORDER BY id",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ3.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ3.ID) AS NUMBER(38,0))))]"},
            {"SELECT id, (SELECT SUM(v) FROM (SELECT MAX(fz3.id) AS x, g3.v FROM g3)) FROM fz3 ORDER BY id",
                "5|110 ;; 7|110 ;; 9|110"},
            {"SELECT id, (SELECT MAX(x) FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 GROUP BY id ORDER BY id",
                "5|9 ;; 7|9 ;; 9|9"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT MIN(fz3.s) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|2 ;; 7|2 ;; 9|2"},
            {"SELECT id, (SELECT x FROM (SELECT MAX(fz3.id) AS x FROM g3) LIMIT 1) FROM fz3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) AS x FROM g3), (SELECT 1)) FROM fz3 ORDER BY id",
                "REFUSED SQL compilation error: | duplicate alias 'values'"},
            {"SELECT id, (SELECT SUM(x) * 2 FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|36 ;; 7|36 ;; 9|36"},
            {"SELECT id, (SELECT AVG(x) FROM (SELECT MAX(fz3.id) AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|9.000000 ;; 7|9.000000 ;; 9|9.000000"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT MAX(fz3.id) + g3.v AS x FROM g3)) FROM fz3 ORDER BY id",
                "5|128 ;; 7|128 ;; 9|128"},
        });
    }
}
