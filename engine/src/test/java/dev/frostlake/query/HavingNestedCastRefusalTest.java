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
 * The nested-aggregate refusal of a HAVING subquery prints the casts the decorrelated plan holds: a BOOLEAN to a
 * number through NUMBER(2,0), the conversions arithmetic makes of its operands, and an aggregate of the outer row
 * alone as the grouped query's own, CORRELATION(MAX(...)) (all live-verified).
 */
public class HavingNestedCastRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void castsInTheNesting() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CORRELATION(FZ.B) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,"
                + "0))))] nested in [MAX(MAX((CAST(CAST(CORRELATION(FZ.B) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.b::INT + g.v) FROM g)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CORRELATION(FZ.ID) "
                + "+ G.V)] nested in [MAX(MAX(CORRELATION(FZ.ID) + G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CAST(CAST(FZ.B AS "
                + "NUMBER(2,0)) AS NUMBER(38,0)))] nested in [MAX(CORRELATION(MAX(CAST(CAST(FZ.B AS NUMBER(2,0)) AS "
                + "NUMBER(38,0)))))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.b::INT) FROM g)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CAST(FZ.ID AS "
                + "VARCHAR(134217728)))] nested in [MAX(CORRELATION(MAX(CAST(FZ.ID AS VARCHAR(134217728)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::VARCHAR) FROM g)) > '0' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CORRELATION(FZ.ID) AS FLOAT)) + (CAST(G.V AS FLOAT)))] nested in "
                + "[MAX(MAX((CAST(CORRELATION(FZ.ID) AS FLOAT)) + (CAST(G.V AS FLOAT))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(CAST(fz.id AS DOUBLE) + g.v) FROM g)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CORRELATION(FZ.ID) AS NUMBER(10,2))) + (CAST(G.V AS NUMBER(38,2))))] nested in "
                + "[MAX(MAX((CAST(CORRELATION(FZ.ID) AS NUMBER(10,2))) + (CAST(G.V AS NUMBER(38,2)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::NUMBER(10,2) + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CORRELATION(FZ.ID) AS FLOAT)) + (CAST(G.V AS FLOAT)))] nested in "
                + "[MAX(MAX((CAST(CORRELATION(FZ.ID) AS FLOAT)) + (CAST(G.V AS FLOAT))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id + g.v::FLOAT) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CORRELATION(CAST(FZ.ID AS NUMBER(38,0))) * (CAST(G.V AS NUMBER(38,0))))] nested in "
                + "[MAX(MAX(CORRELATION(CAST(FZ.ID AS NUMBER(38,0))) * (CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id * g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CORRELATION(FZ.ID) AS NUMBER(5,0))) + G.V)] nested in [MAX(MAX((CAST(CORRELATION(FZ.ID) "
                + "AS NUMBER(5,0))) + G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::NUMBER(5,0) + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(CAST(FZ.B AS "
                + "NUMBER(2,0)) AS NUMBER(38,0)))] nested in [MAX(CORRELATION(SUM(CAST(CAST(FZ.B AS NUMBER(2,0)) AS "
                + "NUMBER(38,0)))))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT SUM(fz.b::INT) FROM g)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CORRELATION(FZ.ID)) + G.V)] nested in [MAX(MAX((CORRELATION(FZ.ID)) + G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(TO_NUMBER(fz.id) + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CORRELATION(FZ.ID)) + (G.V))] nested in [MAX(MAX((CORRELATION(FZ.ID)) + (G.V)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::INT + g.v::INT) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(CAST(FZ.B AS "
                + "NUMBER(2,0)) AS NUMBER(38,0))) * 2)] nested in [MAX(CORRELATION(MAX((CAST(CAST(FZ.B AS NUMBER(2,"
                + "0)) AS NUMBER(38,0))) * 2)))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.b::INT * 2) FROM g)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(G.V AS "
                + "VARCHAR(134217728))) || (CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728))))] nested in "
                + "[MAX(MAX((CAST(G.V AS VARCHAR(134217728))) || (CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::VARCHAR || fz.id) FROM g)) > '0' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CAST(FZ.ID AS "
                + "NUMBER(10,2)))] nested in [MAX(CORRELATION(MAX(CAST(FZ.ID AS NUMBER(10,2)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::NUMBER(10,2)) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CAST(FZ.ID AS "
                + "FLOAT))] nested in [MAX(CORRELATION(MAX(CAST(FZ.ID AS FLOAT))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::DOUBLE) FROM g)) > 0 ORDER BY id");
    }

    @Test
    public void moreCastsInTheNesting() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(G.V AS "
                + "NUMBER(38,0))) * CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] nested in [MAX(MAX((CAST(G.V AS "
                + "NUMBER(38,0))) * CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v * fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CORRELATION(FZ.ID) AS NUMBER(5,0)) AS NUMBER(5,0))) * (CAST(G.V AS NUMBER(38,"
                + "0))))] nested in [MAX(MAX((CAST(CAST(CORRELATION(FZ.ID) AS NUMBER(5,0)) AS NUMBER(5,0))) * "
                + "(CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::NUMBER(5,0) * g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CORRELATION(CAST(FZ.ID AS NUMBER(38,0))) * (CAST(CAST(G.V AS NUMBER(5,0)) AS NUMBER(5,0))))] "
                + "nested in [MAX(MAX(CORRELATION(CAST(FZ.ID AS NUMBER(38,0))) * (CAST(CAST(G.V AS NUMBER(5,0)) AS "
                + "NUMBER(5,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id * g.v::NUMBER(5,0)) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CORRELATION(FZ.ID) "
                + "- G.V)] nested in [MAX(MAX(CORRELATION(FZ.ID) - G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id - g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(SCALED_ROUND_INT_DIVIDE(CORRELATION(CAST(FZ.ID AS NUMBER(38,0))), CAST(G.V AS NUMBER(38,"
                + "0))))] nested in [MAX(MAX(SCALED_ROUND_INT_DIVIDE(CORRELATION(CAST(FZ.ID AS NUMBER(38,0))), "
                + "CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id / g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CORRELATION(FZ.ID) + G.V) + 1)] nested in [MAX(MAX((CORRELATION(FZ.ID) + G.V) + 1))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id + g.v + 1) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CORRELATION(FZ.B) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(CAST(G.V AS NUMBER(5,"
                + "0)) AS NUMBER(5,0))))] nested in [MAX(MAX((CAST(CAST(CORRELATION(FZ.B) AS NUMBER(2,0)) AS "
                + "NUMBER(38,0))) + (CAST(CAST(G.V AS NUMBER(5,0)) AS NUMBER(5,0)))))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.b::INT + g.v::NUMBER(5,0)) FROM g)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CORRELATION(FZ.ID)) + G.V)] nested in [MAX(MAX((CORRELATION(FZ.ID)) + G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::NUMBER(38,0) + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728))) || (CAST(G.V AS VARCHAR(134217728))))] "
                + "nested in [MAX(MAX((CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728))) || (CAST(G.V AS "
                + "VARCHAR(134217728)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::VARCHAR || g.v) FROM g)) > '0' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MIN((CAST(CAST(CORRELATION(FZ.B) AS NUMBER(2,0)) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,"
                + "0))))] nested in [MIN(MIN((CAST(CAST(CORRELATION(FZ.B) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "(CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT b FROM fz GROUP BY b HAVING MIN((SELECT MIN(fz.b::INT + g.v) FROM g)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CAST(FZ.ID AS "
                + "VARCHAR(134217728)))] nested in [MAX(CORRELATION(MAX(CAST(FZ.ID AS VARCHAR(134217728)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::VARCHAR) FROM g WHERE g.id = 5)) > '0' "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID + 1)] nested "
                + "in [MAX(CORRELATION(MAX(FZ.ID + 1)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id + 1) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID * 2)] nested "
                + "in [MAX(CORRELATION(MAX(FZ.ID * 2)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id * 2) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(NEGATE(FZ.ID))] "
                + "nested in [MAX(CORRELATION(MAX(NEGATE(FZ.ID))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(-fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(CAST(FZ.ID AS "
                + "NUMBER(10,2)) AS NUMBER(10,2)))] nested in [MAX(CORRELATION(SUM(CAST(CAST(FZ.ID AS NUMBER(10,2)) "
                + "AS NUMBER(10,2)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(fz.id::NUMBER(10,2)) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V + "
                + "CORRELATION(FZ.ID))] nested in [MAX(MAX(G.V + CORRELATION(FZ.ID)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(G.V AS "
                + "NUMBER(10,2))) + (CAST(CORRELATION(FZ.ID) AS NUMBER(38,2))))] nested in [MAX(MAX((CAST(G.V AS "
                + "NUMBER(10,2))) + (CAST(CORRELATION(FZ.ID) AS NUMBER(38,2)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::NUMBER(10,2) + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CORRELATION(FZ.ID) AS FLOAT)) * (CAST(G.V AS FLOAT)))] nested in "
                + "[MAX(MAX((CAST(CORRELATION(FZ.ID) AS FLOAT)) * (CAST(G.V AS FLOAT))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::FLOAT * g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CORRELATION(FZ.ID) AS NUMBER(10,2)) AS NUMBER(10,2))) * (CAST(G.V AS NUMBER(38,"
                + "0))))] nested in [MAX(MAX((CAST(CAST(CORRELATION(FZ.ID) AS NUMBER(10,2)) AS NUMBER(10,2))) * "
                + "(CAST(G.V AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::NUMBER(10,2) * g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((G.V * 2) + "
                + "CORRELATION(FZ.ID))] nested in [MAX(MAX((G.V * 2) + CORRELATION(FZ.ID)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v * 2 + fz.id) FROM g)) > 0 ORDER BY id");
    }
}
