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
 * The plan settles a number converted to BOOLEAN by its table's statistics: over a column holding no zero and no
 * NULL it is TRUE, over one holding zeros alone FALSE, whatever a WHERE keeps. A subquery reading the outer row
 * only so converted reads nothing of it, and is answered in a HAVING, a select list and a WHERE; one that reads
 * the outer row besides, or converts its own column, prints the settled BOOLEAN in the nesting it is refused
 * with, as it does a comparison the statistics decide (all live-verified).
 */
public class HavingSubquerySettledBooleanTest extends MeasuredCellsTestSupport {

    @Test
    public void settledConversions() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE), (0, NULL), (NULL, TRUE)");
        engine.execute("CREATE OR REPLACE TABLE fz2 (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz2 VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE fzneg (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fzneg VALUES (-5, TRUE), (-7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE fzz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fzz VALUES (5, TRUE), (0, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE fznull (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fznull VALUES (5, TRUE), (NULL, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE gz (id INT, v INT)");
        engine.execute("INSERT INTO gz VALUES (1, 0), (2, 0)");
        engine.execute("CREATE OR REPLACE TABLE gpos (id INT, v INT)");
        engine.execute("INSERT INTO gpos VALUES (1, 1), (2, 1)");
        engine.execute("CREATE OR REPLACE TABLE gnull (id INT, v INT)");
        engine.execute("INSERT INTO gnull VALUES (1, NULL), (2, 5)");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 61 ORDER "
                + "BY id");
        assertAnswer("",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 60 ORDER "
                + "BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) > 0 ORDER "
                + "BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT * g.v) FROM g)) = 60 ORDER "
                + "BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT SUM(fz.id::BOOLEAN::INT + g.v) FROM g)) = 112 "
                + "ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT AVG(fz.id::BOOLEAN::INT + g.v) FROM g)) = 56 ORDER "
                + "BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MIN((SELECT MIN(fz.id::BOOLEAN::INT + g.v) FROM g)) = 51 ORDER "
                + "BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT MAX(IFF(fz.id::BOOLEAN, g.v, 0)) FROM g)) = 60 "
                + "ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT MAX(TO_BOOLEAN(fz.id)::INT + g.v) FROM g)) = 61 "
                + "ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT MAX(CAST(fz.id AS BOOLEAN)::INT + g.v) FROM g)) = "
                + "61 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(((CAST(CAST(TRUE "
                + "AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))) + CORRELATION(CAST(FZ.ID AS "
                + "NUMBER(38,0))))] nested in [MAX(MAX(((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V "
                + "AS NUMBER(38,0)))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v + fz.id) FROM g)) = "
                + "66 ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING SUM((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 61 ORDER "
                + "BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING COUNT((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 1 "
                + "ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) + 1 FROM g)) = 62 "
                + "ORDER BY id");
        assertAnswer("5|true ;; 7|true",
            "SELECT id, MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 61 FROM fz2 fz GROUP BY id ORDER BY id");
        assertAnswer("5|61 ;; 7|61",
            "SELECT id, (SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g) FROM fz2 fz ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz2 fz WHERE (SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g) = 61 ORDER BY id");
        assertAnswer("-7 ;; -5",
            "SELECT id FROM fzneg fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 61 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fzz fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 61 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fznull fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 61 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(FALSE "
                + "AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(FALSE AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT + fz.id) FROM gz g)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT + fz.id) FROM gpos g)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::NUMBER(5,2) + fz.id) FROM g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(G.V > 55 AS FLOAT)] for parameter "
                + "'TO_DOUBLE'",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::FLOAT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 10)::INT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(FALSE "
                + "AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(FALSE AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v < 10)::INT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v >= 50)::INT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v <> 70)::INT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v IS NOT NULL)::INT + fz.id) FROM g)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V IS NULL AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V IS NULL AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v IS NULL)::INT + fz.id) FROM gnull g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v BETWEEN 1 AND 100)::INT + fz.id) FROM g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V >= 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V >= 55 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v BETWEEN 55 AND 100)::INT + fz.id) FROM g)) > "
                + "0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST((CAST(G.V > 55 AS BOOLEAN)) OR (CAST(G.ID > 5 AS BOOLEAN)) AS NUMBER(2,0)) AS "
                + "NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST((CAST(G.V "
                + "> 55 AS BOOLEAN)) OR (CAST(G.ID > 5 AS BOOLEAN)) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55 OR g.id > 5)::INT + fz.id) FROM g)) > 0 "
                + "ORDER BY id");
        assertAnswer("0 ;; 5 ;; 7 ;; null",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::INT) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CAST(CAST(G.V > "
                + "CORRELATION(FZ.ID) AS BOOLEAN) AS NUMBER(2,0)))] nested in [MAX(MAX(CAST(CAST(G.V > "
                + "CORRELATION(FZ.ID) AS BOOLEAN) AS NUMBER(2,0))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > fz.id)::INT) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))) + 1)] nested in [MAX(MAX(((CAST(CAST(CAST(G.V > 55 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))) + 1))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::INT + fz.id + 1) FROM g)) > 0 ORDER BY "
                + "id");
        assertAnswer("0 ;; 5 ;; 7 ;; null",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(1 + (g.v > 55)::INT) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[SUM((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(SUM((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM((g.v > 55)::INT + fz.id) FROM g)) > 0 ORDER BY id");
    }

    @Test
    public void answeredOverSettledColumns() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE gf (id INT, f FLOAT)");
        engine.execute("INSERT INTO gf VALUES (5, 1.5), (6, 2.5)");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v + fz.id::BOOLEAN::INT) FROM g)) > 0 ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT * g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MIN((SELECT MIN(fz.id::BOOLEAN::INT + g.v) FROM g)) > 0 ORDER BY id");
    }

    @Test
    public void answeredConversionShapes() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(IFF(fz.id::BOOLEAN, g.v, 0)) FROM g)) > 0 ORDER BY "
                + "id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v::BOOLEAN::INT) FROM g)) "
                + "> 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(fz.id::BOOLEAN::INT + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::NUMBER(5,0) + g.v) FROM g)) > 0 "
                + "ORDER BY id");
    }
}
