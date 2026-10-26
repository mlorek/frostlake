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
 * A merged view's SUM and AVG as the nesting refusal prints them - a FLOAT column beside a number written out as a
 * FLOAT literal, a filter the statistics prove true of every row dropped for the derived count - and a VALUES
 * item's subquery echoed without the conjuncts the statistics prove (all live-verified).
 */
public class MergedViewSumEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void statisticsEchoes() {
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT)");
        engine.execute("INSERT INTO fz3 VALUES (5), (7), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzf (f FLOAT)");
        engine.execute("INSERT INTO fzf VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 10), (6, 20)");
        engine.execute("CREATE OR REPLACE VIEW vz3e AS SELECT id + 1 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz4u AS SELECT id + 123456789012345678 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz4f AS SELECT f + 1 AS id FROM fzf");
        engine.execute("CREATE OR REPLACE VIEW vz4g AS SELECT f + 2.5 AS id FROM fzf");
        engine.execute("CREATE OR REPLACE TABLE dz (id INT)");
        engine.execute("INSERT INTO dz VALUES (0), (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE sink (n NUMBER(10,3))");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(FZF.F + 1.0)] "
                + "nested in [SUM(CORRELATION(SUM(FZF.F + 1.0)))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz4f.id) AS x FROM g3)) FROM vz4f ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(FZF.F + 1.0)] "
                + "nested in [SUM(CORRELATION((SUM(FZF.F + 1.0)) / (CAST(COUNT(FZF.F + 1.0) AS FLOAT))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz4f.id) AS x FROM g3)) FROM vz4f ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(FZF.F + 2.5)] "
                + "nested in [SUM(CORRELATION(SUM(FZF.F + 2.5)))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz4g.id) AS x FROM g3)) FROM vz4g ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 2 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3e.id) AS x FROM g3 WHERE g3.v > 0)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST((SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))) + (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + 1) AS "
                + "NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3e.id) AS x FROM g3 WHERE g3.v > 0)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION((CAST(SUM(CAST(FZ3.ID AS NUMBER(38,0))) AS NUMBER(38,"
                + "0))) + (1 * (CAST(COUNT(FZ3.ID) AS NUMBER(18,0))))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3e.id) AS x FROM g3 WHERE g3.v > 15)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE((CAST(SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0))) AS NUMBER(38,0))) + (1 * (CAST(COUNT(FZ3.ID) AS NUMBER(18,0)))), CAST(COUNT(FZ3.ID "
                + "+ 1) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(vz3e.id) AS x FROM g3 WHERE g3.v > 15)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION((CAST(SUM(CAST(FZ3.ID AS NUMBER(38,0))) AS NUMBER(38,"
                + "0))) + (1 * (CAST(COUNT(FZ3.ID) AS NUMBER(18,0))))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3e.id) AS x FROM g3 WHERE g3.v > 0 AND g3.v < 15)) FROM "
                + "vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 2 "
                + "AS NUMBER(19,0))) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT SUM(vz3e.id) AS x FROM g3 WHERE g3.v < 100)) FROM vz3e ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz WHERE id > 0 AND id < 5))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz WHERE id < 5 AND id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ WHERE (DZ.ID > 0) AND (DZ.ID < 2)) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz WHERE id > 0 AND id < 2))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz WHERE id >= 0 AND id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz WHERE id > 0 AND id <= 2))");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "EXPLAIN SELECT SUM(id) FROM vz3e, g3 WHERE g3.v > 0");
    }
}
