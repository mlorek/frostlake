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
 * The SUM and the AVG of a merged view's column standing for an expression, refused as nested, as the plan
 * rewrites them: a column moved by a constant as its sum and the constant times the derived count (all
 * live-verified).
 */
public class MergedViewSumRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void sumAndAverage() {
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT)");
        engine.execute("INSERT INTO fz3 VALUES (5), (7), (9)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 10), (6, 20)");
        engine.execute("CREATE OR REPLACE TABLE g4 (id INT, v INT)");
        engine.execute("INSERT INTO g4 VALUES (1, 1), (2, 2), (3, 3)");
        engine.execute("CREATE OR REPLACE VIEW vz3e AS SELECT id + 1 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3m AS SELECT id * 2 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3a AS SELECT ABS(id) AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3s AS SELECT id - 1 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3k AS SELECT 3 + id AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3x AS SELECT -id AS id FROM fz3");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 2 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3e.id) AS x FROM g3)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID * 2 AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SUM(CAST(FZ3.ID * 2 AS NUMBER(38,0))) AS NUMBER(38,"
                + "0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3m.id) AS x FROM g3)) FROM vz3m ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(ABS(FZ3.ID) "
                + "AS NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SUM(CAST(ABS(FZ3.ID) AS NUMBER(38,0))) AS "
                + "NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3a.id) AS x FROM g3)) FROM vz3a ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) - (CAST(1 * 2 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3s.id) AS x FROM g3)) FROM vz3s ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((CAST(3 * 2 AS NUMBER(19,0))) + (SUM(CAST(FZ3.ID "
                + "AS NUMBER(38,0)))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3k.id) AS x FROM g3)) FROM vz3k ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[SUM(CAST(NEGATE(FZ3.ID) AS NUMBER(38,0)))] nested in "
                + "[SUM(CORRELATION(CAST(SUM(CAST(NEGATE(FZ3.ID) AS NUMBER(38,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3x.id) AS x FROM g3)) FROM vz3x ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) + (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + 1) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3e.id) AS x FROM g3)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID * 2 AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(FZ3.ID * 2 AS "
                + "NUMBER(38,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID * 2) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3m.id) AS x FROM g3)) FROM vz3m ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(ABS(FZ3.ID) "
                + "AS NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(ABS(FZ3.ID) AS "
                + "NUMBER(38,0))) AS NUMBER(38,0)), CAST(COUNT(ABS(FZ3.ID)) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3a.id) AS x FROM g3)) FROM vz3a ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) - (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID - 1) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3s.id) AS x FROM g3)) FROM vz3s ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((CAST(3 * 2 AS NUMBER(19,"
                + "0))) + (SUM(CAST(FZ3.ID AS NUMBER(38,0)))) AS NUMBER(38,0)), CAST(COUNT(3 + FZ3.ID) AS NUMBER(18,"
                + "0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3k.id) AS x FROM g3)) FROM vz3k ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[SUM(CAST(NEGATE(FZ3.ID) AS NUMBER(38,0)))] nested in "
                + "[SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(NEGATE(FZ3.ID) AS NUMBER(38,0))) AS "
                + "NUMBER(38,0)), CAST(COUNT(NEGATE(FZ3.ID)) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3x.id) AS x FROM g3)) FROM vz3x ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 3 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3e.id) AS x FROM g4)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) + (CAST(1 * 3 AS NUMBER(19,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + 1) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3e.id) AS x FROM g4)) FROM vz3e ORDER BY id");
    }

    @Test
    public void moreExpressions() {
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT)");
        engine.execute("INSERT INTO fz3 VALUES (5), (7), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzn (id INT)");
        engine.execute("INSERT INTO fzn VALUES (5), (NULL), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzf (f FLOAT)");
        engine.execute("INSERT INTO fzf VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 10), (6, 20)");
        engine.execute("CREATE OR REPLACE TABLE g4 (id INT, v INT)");
        engine.execute("INSERT INTO g4 VALUES (1, 1), (2, 2), (3, 3)");
        engine.execute("CREATE OR REPLACE VIEW vz3e AS SELECT id + 1 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz4n AS SELECT 3 - id AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz4t AS SELECT id + 10 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz4p AS SELECT (id + 1) + 2 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz4q AS SELECT id + id AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz4d AS SELECT id + 1.5 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz4u AS SELECT id + 123456789012345678 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vzne AS SELECT id + 1 AS id FROM fzn");
        engine.execute("CREATE OR REPLACE VIEW vz4f AS SELECT f + 1 AS id FROM fzf");
        engine.execute("CREATE OR REPLACE VIEW vz4w AS SELECT id + 1 AS id FROM fz3 WHERE id > 0");
        engine.execute("CREATE OR REPLACE VIEW vz4b AS SELECT (id + 1) AS id FROM fz3");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((CAST(3 * 2 AS NUMBER(19,0))) - (SUM(CAST(FZ3.ID "
                + "AS NUMBER(38,0)))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz4n.id) AS x FROM g3)) FROM vz4n ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((CAST(3 * 2 AS NUMBER(19,"
                + "0))) - (SUM(CAST(FZ3.ID AS NUMBER(38,0)))) AS NUMBER(38,0)), CAST(COUNT(3 - FZ3.ID) AS NUMBER(18,"
                + "0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz4n.id) AS x FROM g3)) FROM vz4n ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(10 * 2 "
                + "AS NUMBER(20,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz4t.id) AS x FROM g3)) FROM vz4t ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) + (CAST(10 * 2 AS NUMBER(20,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + 10) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz4t.id) AS x FROM g3)) FROM vz4t ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST((FZ3.ID + 1) "
                + "+ 2 AS NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SUM(CAST((FZ3.ID + 1) + 2 AS NUMBER(38,0))) "
                + "AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz4p.id) AS x FROM g3)) FROM vz4p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST((FZ3.ID + 1) "
                + "+ 2 AS NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST((FZ3.ID + "
                + "1) + 2 AS NUMBER(38,0))) AS NUMBER(38,0)), CAST(COUNT((FZ3.ID + 1) + 2) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz4p.id) AS x FROM g3)) FROM vz4p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID + "
                + "FZ3.ID AS NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SUM(CAST(FZ3.ID + FZ3.ID AS NUMBER(38,"
                + "0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz4q.id) AS x FROM g3)) FROM vz4q ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID + "
                + "FZ3.ID AS NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(FZ3.ID "
                + "+ FZ3.ID AS NUMBER(38,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + FZ3.ID) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz4q.id) AS x FROM g3)) FROM vz4q ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST((CAST(FZ3.ID "
                + "AS NUMBER(38,1))) + 1.5 AS NUMBER(38,1)))] nested in [SUM(CORRELATION(CAST(SUM(CAST((CAST(FZ3.ID "
                + "AS NUMBER(38,1))) + 1.5 AS NUMBER(38,1))) AS NUMBER(38,1))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz4d.id) AS x FROM g3)) FROM vz4d ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST((CAST(FZ3.ID "
                + "AS NUMBER(38,1))) + 1.5 AS NUMBER(38,1)))] nested in "
                + "[SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST((CAST(FZ3.ID AS NUMBER(38,1))) + 1.5 AS "
                + "NUMBER(38,1))) AS NUMBER(38,1)), CAST(COUNT((CAST(FZ3.ID AS NUMBER(38,1))) + 1.5) AS NUMBER(18,"
                + "0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz4d.id) AS x FROM g3)) FROM vz4d ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + "
                + "(123456789012345678 * 2) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz4u.id) AS x FROM g3)) FROM vz4u ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 2 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz4w.id) AS x FROM g3)) FROM vz4w ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) + (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + 1) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz4w.id) AS x FROM g3)) FROM vz4w ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 2 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz4b.id) AS x FROM g3)) FROM vz4b ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) + (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + 1) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz4b.id) AS x FROM g3)) FROM vz4b ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZN.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZN.ID AS NUMBER(38,0)))) + (CAST(1 * 2 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vzne.id) AS x FROM g4)) FROM vzne ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [MAX(CORRELATION((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 2 AS "
                + "NUMBER(19,0)))))]",
            "SELECT id, (SELECT MAX(x) FROM (SELECT SUM(vz3e.id) AS x FROM g3)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION((CAST(SUM(CAST(FZ3.ID AS NUMBER(38,0))) AS NUMBER(38,"
                + "0))) + (1 * (CAST(COUNT(FZ3.ID) AS NUMBER(18,0))))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3e.id) AS x FROM g3 JOIN g4 ON TRUE)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 2 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3e.id) AS x FROM g3 AS gg)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 2 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3e.id) AS x FROM (SELECT * FROM g3))) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZN.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZN.ID AS "
                + "NUMBER(38,0)))) + (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0)), CAST(COUNT(FZN.ID + 1) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vzne.id) AS x FROM g4)) FROM vzne ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [MAX(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) + (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + 1) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT MAX(x) FROM (SELECT AVG(vz3e.id) AS x FROM g3)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE((CAST(SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0))) AS NUMBER(38,0))) + (1 * (CAST(COUNT(FZ3.ID) AS NUMBER(18,0)))), CAST(COUNT(FZ3.ID "
                + "+ 1) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3e.id) AS x FROM g3 JOIN g4 ON TRUE)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) + (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + 1) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3e.id) AS x FROM g3 AS gg)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) + (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + 1) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3e.id) AS x FROM (SELECT * FROM g3))) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 2 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(v.id) AS x FROM g3)) FROM vz3e v ORDER BY id");
    }

    /**
     * Past eighteen digits between a view's constant and the derived count, an AVG's count casts the column beside the
     * constant; a SUM(DISTINCT) of the view's column is the column's distinct sum moved by the constant times its
     * distinct count, DISTINCT printed in the cast too; and a plan prints a number beside a FLOAT column as a FLOAT
     * literal (live-verified).
     */
    @Test
    public void theWideCountTheDistinctSumAndAFloatLiteral() {
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT)");
        engine.execute("INSERT INTO fz3 VALUES (5), (7), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzf (f FLOAT)");
        engine.execute("INSERT INTO fzf VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 10), (6, 20)");
        engine.execute("CREATE OR REPLACE VIEW vz3e AS SELECT id + 1 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz4u AS SELECT id + 123456789012345678 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz4f AS SELECT f + 1 AS id FROM fzf");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) + (123456789012345678 * 2) AS NUMBER(38,0)), CAST(COUNT((CAST(FZ3.ID AS "
                + "NUMBER(38,0))) + 123456789012345678) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz4u.id) AS x FROM g3)) FROM vz4u ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(DISTINCT CAST("
                + "DISTINCT FZ3.ID AS NUMBER(38,0)))] nested in [SUM(CORRELATION((CAST(SUM(DISTINCT CAST(DISTINCT "
                + "FZ3.ID AS NUMBER(38,0))) AS NUMBER(38,0))) + (1 * (CAST(COUNT(DISTINCT FZ3.ID) AS "
                + "NUMBER(18,0))))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(DISTINCT vz3e.id) AS x FROM g3)) FROM vz3e ORDER BY id");
        answer("EXPLAIN SELECT SUM(id) FROM vz4f");
        assertAnswer("Result|SUM(FZF.F + 1.0) ;; Aggregate|aggExprs: [SUM(FZF.F + 1.0)]",
            "SELECT \"operation\", \"expressions\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"operation\" IN "
                + "('Result', 'Aggregate') ORDER BY \"id\"");
    }
}
