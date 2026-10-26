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
 * A HAVING subquery's own aggregate converting an outer number to BOOLEAN nests in the HAVING's when the
 * statistics leave the conversion open, printed CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN); a comparison converted
 * to a number goes through NUMBER(2,0) whatever the cast names, and is cast to its own type once more in
 * arithmetic, as an AND, an OR, a NOT, an IN and a BETWEEN of comparisons are (all live-verified).
 */
public class HavingSubqueryBooleanPrintsTest extends MeasuredCellsTestSupport {

    @Test
    public void outerConversions() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE), (0, NULL), (NULL, TRUE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE g0 (id INT, v INT)");
        engine.execute("INSERT INTO g0 VALUES (1, 0), (2, 5)");
        engine.execute("CREATE OR REPLACE TABLE gnull (id INT, v INT)");
        engine.execute("INSERT INTO gnull VALUES (1, NULL), (2, 5)");
        engine.execute("CREATE OR REPLACE TABLE gneg (id INT, v INT)");
        engine.execute("INSERT INTO gneg VALUES (1, -3), (2, -5)");
        engine.execute("CREATE OR REPLACE TABLE gempty (id INT, v INT)");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 61 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 60 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) IS NULL ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(CAST(fz.id AS BOOLEAN)::INT + g.v) FROM g)) = 61 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(TO_BOOLEAN(fz.id)::INT + g.v) FROM g)) = 61 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.b::INT + g.v) FROM g)) = 61 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(IFF(fz.b, g.v, 0)) FROM g)) = 60 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) > 5 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) > 5 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((fz.id > 5)::INT + g.v) FROM g)) = 61 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[SUM((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in "
                + "[MAX(SCALED_ROUND_INT_DIVIDE(SUM((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,"
                + "0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))), "
                + "CAST(COUNT((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0)))) AS NUMBER(18,0))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT AVG(fz.id::BOOLEAN::INT + g.v) FROM g)) = 56 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MIN((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MIN((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MIN(fz.id::BOOLEAN::INT + g.v) FROM g)) = 51 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [SUM(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING SUM((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 61 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [COUNT(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING COUNT((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 1 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX((MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0))))) + 1)]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) + 1 FROM g)) = 62 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g WHERE g.id = "
                + "fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id, MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 61 FROM fz GROUP BY id ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 15",
            "SELECT id, MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) FROM fz GROUP BY id ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g) FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25",
            "SELECT id FROM fz WHERE (SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g) = 61 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 38",
            "SELECT id FROM fz GROUP BY id HAVING (SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g) = 61 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)) = 61 AND "
                + "COUNT(*) = 1 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + 1)] nested in "
                + "[MAX(CORRELATION(MAX((CAST(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "1)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + 1) FROM g)) = 2 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0)))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX(((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0)))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v + fz.id) FROM g)) = 66 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + g.v::BOOLEAN::INT) FROM g)) "
                + "= 2 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) * "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) * (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT * g.v) FROM g)) = 60 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[SUM((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0))))] nested in [MAX(SUM((CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(fz.id::BOOLEAN::INT + g.v) FROM g)) = 112 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(5,0)) AS "
                + "NUMBER(5,0))) + (CAST(G.V AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(5,0)) "
                + "AS NUMBER(5,0))) + (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::NUMBER(5,0) + g.v) FROM g)) = 61 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(G.V AS "
                + "NUMBER(38,0))) + (CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,"
                + "0))))] nested in [MAX(MAX((CAST(G.V AS NUMBER(38,0))) + (CAST(CAST(CAST(CORRELATION(FZ.ID) <> 0 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v + fz.id::BOOLEAN::INT) FROM g)) = 61 ORDER BY "
                + "id");
    }

    @Test
    public void comparisonNumbers() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE), (0, NULL), (NULL, TRUE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE g0 (id INT, v INT)");
        engine.execute("INSERT INTO g0 VALUES (1, 0), (2, 5)");
        engine.execute("CREATE OR REPLACE TABLE gnull (id INT, v INT)");
        engine.execute("INSERT INTO gnull VALUES (1, NULL), (2, 5)");
        engine.execute("CREATE OR REPLACE TABLE gneg (id INT, v INT)");
        engine.execute("INSERT INTO gneg VALUES (1, -3), (2, -5)");
        engine.execute("CREATE OR REPLACE TABLE gempty (id INT, v INT)");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::INT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V = 50 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V = 50 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v = 50)::INT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::NUMBER(5,0) + fz.id) FROM g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) * "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(2,0))) * CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::INT * fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::BOOLEAN::INT + fz.id) FROM g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(CAST(g.v > 55 AS INT) + fz.id) FROM g)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V > CORRELATION(FZ.ID) AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + 1)] "
                + "nested in [MAX(MAX((CAST(CAST(CAST(G.V > CORRELATION(FZ.ID) AS BOOLEAN) AS NUMBER(2,0)) AS "
                + "NUMBER(2,0))) + 1))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > fz.id)::INT + 1) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CAST(CAST((G.V + "
                + "CORRELATION(FZ.ID)) > 55 AS BOOLEAN) AS NUMBER(2,0)))] nested in [MAX(MAX(CAST(CAST((G.V + "
                + "CORRELATION(FZ.ID)) > 55 AS BOOLEAN) AS NUMBER(2,0))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v + fz.id > 55)::INT) FROM g)) > 0 ORDER BY id");
        assertAnswer("0 ;; 5 ;; 7 ;; null",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::INT + 1) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V <> 0 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT + fz.id) FROM g0 g)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V <> 0 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT + fz.id) FROM gnull g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT + fz.id) FROM gneg g)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT + fz.id) FROM g WHERE g.v > 55)) "
                + "> 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V <> 0 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT + fz.id) FROM g0 g WHERE g.v > "
                + "0)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V <> 0 AS BOOLEAN) "
                + "AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT + fz.id) FROM gempty g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(FALSE "
                + "AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(FALSE AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::INT + fz.id) FROM g0 g)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(FALSE "
                + "AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(FALSE AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v IS NULL)::INT + fz.id) FROM g)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V <= 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V <= 55 AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v BETWEEN 1 AND 55)::INT + fz.id) FROM g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V IN (50, 60) AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(G.V IN (50, 60) AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v IN (50, 60))::INT + fz.id) FROM g)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(NOT(G.V > 55) AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST(CAST(NOT(G.V > 55) AS "
                + "BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((NOT (g.v > 55))::INT + fz.id) FROM g)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST((CAST(G.V > 55 AS BOOLEAN)) AND (CAST(G.ID > 5 AS BOOLEAN)) AS NUMBER(2,0)) AS "
                + "NUMBER(2,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(CAST((CAST(G.V "
                + "> 55 AS BOOLEAN)) AND (CAST(G.ID > 5 AS BOOLEAN)) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55 AND g.id > 5)::INT + fz.id) FROM g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "(CAST(CAST(CORRELATION(FZ.ID) AS NUMBER(5,0)) AS NUMBER(5,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(CAST(G.V > 55 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(2,0))) + "
                + "(CAST(CAST(CORRELATION(FZ.ID) AS NUMBER(5,0)) AS NUMBER(5,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX((g.v > 55)::INT + fz.id::NUMBER(5,0)) FROM g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.id::BOOLEAN::INT + fz.id) FROM g)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(38,0))) * CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))) * CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT * fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(SUM((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(g.v::BOOLEAN::INT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::BOOLEAN::INT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(TRUE AS "
                + "NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX((CAST(CAST(TRUE AS NUMBER(2,0)) AS NUMBER(38,0))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(CAST(g.v AS BOOLEAN)::INT + fz.id) FROM g)) > 0 "
                + "ORDER BY id");
    }
}
