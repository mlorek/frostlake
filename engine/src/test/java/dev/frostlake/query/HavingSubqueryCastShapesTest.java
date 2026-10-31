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
 * The nested-aggregate refusal of a HAVING subquery prints further shapes as the plan does: a text cast to a number
 * as TO_NUMBER, a FLOAT cast to one through a cast of its own, the other side of an addition beside such a
 * conversion cast to its own type, an outer number converted to a BOOLEAN as its comparison with zero, a remainder
 * and MOD, a decimal literal aligning the scale, a text literal and chained concatenations as written, and a SUM
 * over arithmetic cast to the arithmetic's type (all live-verified).
 */
public class HavingSubqueryCastShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void furtherShapesPrintAsThePlanDoes() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE gf (id INT, f FLOAT)");
        engine.execute("INSERT INTO gf VALUES (5, 1.5), (6, 2.5)");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CAST(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0)))] nested in "
                + "[MAX(CORRELATION(MAX(CAST(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((TO_NUMBER(CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728)))) + (CAST(G.V AS NUMBER(38,0))))] "
                + "nested in [MAX(MAX((TO_NUMBER(CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728)))) + (CAST(G.V AS "
                + "NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::VARCHAR::INT + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CORRELATION(FZ.ID) AS FLOAT) AS NUMBER(38,0))) + (CAST(G.V AS NUMBER(38,0))))] "
                + "nested in [MAX(MAX((CAST(CAST(CORRELATION(FZ.ID) AS FLOAT) AS NUMBER(38,0))) + (CAST(G.V AS "
                + "NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::FLOAT::INT + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CORRELATION(FZ.ID) AS NUMBER(10,2)) AS NUMBER(38,0))) + G.V)] nested in "
                + "[MAX(MAX((CAST(CAST(CORRELATION(FZ.ID) AS NUMBER(10,2)) AS NUMBER(38,0))) + G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::NUMBER(10,2)::INT + g.v) FROM g)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[SUM(CAST(CORRELATION(FZ.ID) + G.V AS NUMBER(38,0)))] nested in [MAX(SUM(CAST(CORRELATION(FZ.ID) + "
                + "G.V AS NUMBER(38,0))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(fz.id + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ.ID * 2 AS "
                + "NUMBER(38,0)))] nested in [MAX(CORRELATION(SUM(CAST(FZ.ID * 2 AS NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(fz.id * 2) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST((CAST(G.V AS "
                + "NUMBER(38,0))) * CORRELATION(CAST(FZ.ID AS NUMBER(38,0))) AS NUMBER(38,0)))] nested in "
                + "[MAX(SUM(CAST((CAST(G.V AS NUMBER(38,0))) * CORRELATION(CAST(FZ.ID AS NUMBER(38,0))) AS NUMBER(38,"
                + "0))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(g.v * fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CORRELATION(FZ.ID) % 2) + G.V)] nested in [MAX(MAX((CORRELATION(FZ.ID) % 2) + G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id % 2 + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((MOD(CORRELATION(FZ.ID), 2)) + G.V)] nested in [MAX(MAX((MOD(CORRELATION(FZ.ID), 2)) + G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(MOD(fz.id, 2) + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(FZ.ID AS "
                + "NUMBER(38,1))) + 1.5)] nested in [MAX(CORRELATION(MAX((CAST(FZ.ID AS NUMBER(38,1))) + 1.5)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id + 1.5) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CORRELATION(FZ.ID) + G.V AS NUMBER(38,1))) + 1.5)] nested in "
                + "[MAX(MAX((CAST(CORRELATION(FZ.ID) + G.V AS NUMBER(38,1))) + 1.5))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id + g.v + 1.5) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(FZ.ID AS "
                + "VARCHAR(134217728))) || 'x')] nested in [MAX(CORRELATION(MAX((CAST(FZ.ID AS VARCHAR(134217728))) ||"
                + " 'x')))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id || 'x') FROM g)) > '0' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((CAST(FZ.ID AS "
                + "VARCHAR(134217728))) || 'x')] nested in [MAX(CORRELATION(MAX((CAST(FZ.ID AS VARCHAR(134217728))) ||"
                + " 'x')))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::VARCHAR || 'x') FROM g)) > '0' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CORRELATION(FZ.ID) AS FLOAT)) + GF.F)] nested in [MAX(MAX((CAST(CORRELATION(FZ.ID) AS "
                + "FLOAT)) + GF.F))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id + gf.f) FROM gf)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CORRELATION(FZ.ID) "
                + "+ G.V)] nested in [MAX(MAX(CORRELATION(FZ.ID) + G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id + g.v) FROM g)) > 0 ORDER BY id");
    }

    @Test
    public void moreCastsAndArithmetic() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(TO_NUMBER(CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728)), 10, 2) AS NUMBER(10,2))) + "
                + "(CAST(CAST(G.V AS NUMBER(38,2)) AS NUMBER(38,2))))] nested in "
                + "[MAX(MAX((CAST(TO_NUMBER(CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728)), 10, 2) AS NUMBER(10,2))) "
                + "+ (CAST(CAST(G.V AS NUMBER(38,2)) AS NUMBER(38,2)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::VARCHAR::NUMBER(10,2) + g.v) FROM g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) AS FLOAT) AS NUMBER(10,2)) AS NUMBER(10,2))) + "
                + "(CAST(CAST(G.V AS NUMBER(38,2)) AS NUMBER(38,2))))] nested in "
                + "[MAX(MAX((CAST(CAST(CAST(CORRELATION(FZ.ID) AS FLOAT) AS NUMBER(10,2)) AS NUMBER(10,2))) + "
                + "(CAST(CAST(G.V AS NUMBER(38,2)) AS NUMBER(38,2)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::FLOAT::NUMBER(10,2) + g.v) FROM g)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((TO_NUMBER(CAST(G.V AS VARCHAR(134217728)))) + CORRELATION(CAST(FZ.ID AS NUMBER(38,0))))] "
                + "nested in [MAX(MAX((TO_NUMBER(CAST(G.V AS VARCHAR(134217728)))) + CORRELATION(CAST(FZ.ID AS "
                + "NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v::VARCHAR::INT + fz.id) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((TO_NUMBER(CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728)))) * (CAST(G.V AS NUMBER(38,0))))] "
                + "nested in [MAX(MAX((TO_NUMBER(CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728)))) * (CAST(G.V AS "
                + "NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::VARCHAR::INT * g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[SUM(CAST((CAST(CORRELATION(FZ.ID) + G.V AS NUMBER(38,1))) + 1.5 AS NUMBER(38,1)))] nested in "
                + "[MAX(SUM(CAST((CAST(CORRELATION(FZ.ID) + G.V AS NUMBER(38,1))) + 1.5 AS NUMBER(38,1))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(fz.id + g.v + 1.5) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[SUM(CAST((CORRELATION(FZ.ID) % 2) + G.V AS NUMBER(38,0)))] nested in "
                + "[MAX(SUM(CAST((CORRELATION(FZ.ID) % 2) + G.V AS NUMBER(38,0))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(fz.id % 2 + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CORRELATION(FZ.ID) % 2) * G.V)] nested in [MAX(MAX((CORRELATION(FZ.ID) % 2) * G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id % 2 * g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + 1)] nested in "
                + "[MAX(CORRELATION(MAX((CAST(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))) + "
                + "1)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::BOOLEAN::INT + 1) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CAST(CORRELATION(FZ.ID) AS FLOAT) AS NUMBER(38,0))) * (CAST(G.V AS NUMBER(38,0))))] "
                + "nested in [MAX(MAX((CAST(CAST(CORRELATION(FZ.ID) AS FLOAT) AS NUMBER(38,0))) * (CAST(G.V AS "
                + "NUMBER(38,0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::FLOAT::INT * g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID % 2)] nested "
                + "in [MAX(CORRELATION(MAX(FZ.ID % 2)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id % 2) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(MOD(FZ.ID, 2))] "
                + "nested in [MAX(CORRELATION(MAX(MOD(FZ.ID, 2))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(MOD(fz.id, 2)) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX((CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728))) || (CAST(G.V AS VARCHAR(134217728))) || "
                + "'x')] nested in [MAX(MAX((CAST(CORRELATION(FZ.ID) AS VARCHAR(134217728))) || (CAST(G.V AS "
                + "VARCHAR(134217728))) || 'x'))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id || g.v || 'x') FROM g)) > '0' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX('x' || (CAST(FZ.ID "
                + "AS VARCHAR(134217728))))] nested in [MAX(CORRELATION(MAX('x' || (CAST(FZ.ID AS "
                + "VARCHAR(134217728))))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX('x' || fz.id) FROM g)) > '0' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(TO_NUMBER(CAST(FZ.ID AS VARCHAR(134217728))))] nested in "
                + "[MAX(CORRELATION(MAX(TO_NUMBER(CAST(FZ.ID AS VARCHAR(134217728))))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::VARCHAR::INT) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CAST(CAST(FZ.ID AS "
                + "FLOAT) AS NUMBER(38,0)))] nested in [MAX(CORRELATION(MAX(CAST(CAST(FZ.ID AS FLOAT) AS NUMBER(38,"
                + "0)))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id::FLOAT::INT) FROM g)) > 0 ORDER BY id");
    }
}
