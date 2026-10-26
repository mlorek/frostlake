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
 * A VALUES item's subquery that does not fold is echoed as the plan holds it, cast to its type where its item
 * carries the type of what it reads; through a LIMIT or a view the statistics still fold it, and a quotient
 * divided again is cast first (all live-verified).
 */
public class ValuesSubqueryEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void plannedSubqueries() {
        engine.execute("CREATE OR REPLACE TABLE sink (x NUMBER(38,6))");
        engine.execute("CREATE OR REPLACE TABLE dz (id INT)");
        engine.execute("INSERT INTO dz VALUES (0), (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE dzn (n NUMBER(10,2))");
        engine.execute("INSERT INTO dzn VALUES (1.50), (2.25)");
        engine.execute("CREATE OR REPLACE TABLE dzs (s VARCHAR)");
        engine.execute("INSERT INTO dzs VALUES ('a'), ('bb')");
        engine.execute("CREATE OR REPLACE VIEW dzv AS SELECT id FROM dz");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz WHERE id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (SELECT SUM(CAST(DZ.ID AS NUMBER(38,"
                + "0))) AS \"SUM(ID)\" FROM DZ AS DZ)] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT SUM(id) FROM dz))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ GROUP BY DZ.ID LIMIT 1 OFFSET 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz GROUP BY id LIMIT 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) AS NUMBER(1,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT 2 FROM dz LIMIT 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 0] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz LIMIT 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 0] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dzv))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(CAST(1 / 0 AS NUMBER(13,12))) / 2] in "
                + "VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz) / 2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MAX(DZ.ID) AS "
                + "\"MAX(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MAX(id) FROM dz WHERE id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (SELECT COUNT(*) AS \"COUNT(*)\" FROM "
                + "DZ AS DZ WHERE DZ.ID > 0)] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT COUNT(*) FROM dz WHERE id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (SELECT COUNT(DZ.ID) AS \"COUNT(ID)\" "
                + "FROM DZ AS DZ WHERE DZ.ID > 0)] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT COUNT(id) FROM dz WHERE id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (SELECT "
                + "SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(DZ.ID AS NUMBER(38,0))) AS NUMBER(38,0)), CAST(COUNT(DZ.ID) "
                + "AS NUMBER(18,0))) AS \"AVG(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0)] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT AVG(id) FROM dz WHERE id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (SELECT SUM(CAST(DZ.ID AS NUMBER(38,"
                + "0))) AS \"SUM(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0)] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT SUM(id) FROM dz WHERE id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (SELECT SUM(CAST(DZN.N AS NUMBER(10,"
                + "2))) AS \"SUM(N)\" FROM DZN AS DZN)] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT SUM(n) FROM dzn))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 1.5] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(n) FROM dzn WHERE n > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT (MAX(DZ.ID)) + 1 AS "
                + "\"MAX(ID) + 1\" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MAX(id) + 1 FROM dz WHERE id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT DZ.ID AS \"ID\" FROM DZ "
                + "AS DZ WHERE DZ.ID = 1) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT id FROM dz WHERE id = 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS \"M\" FROM "
                + "DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) AS m FROM dz WHERE id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(D.ID) AS "
                + "\"MIN(D.ID)\" FROM DZ AS D WHERE D.ID > 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(d.id) FROM dz d WHERE d.id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0 LIMIT 1 OFFSET 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz WHERE id > 0 LIMIT 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT 3 AS \"3\" FROM DZ AS DZ "
                + "WHERE DZ.ID > 0 LIMIT 1 OFFSET 0) AS NUMBER(1,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT 3 FROM dz WHERE id > 0 LIMIT 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (SELECT MIN(LENGTH(DZS.S)) AS "
                + "\"MIN(LENGTH(S))\" FROM DZS AS DZS)] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(LENGTH(s)) FROM dzs))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))) + 1] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz WHERE id > 0) + 1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [SCALED_ROUND_INT_DIVIDE(CAST((SELECT "
                + "MIN(DZ.ID) AS \"MIN(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)), 2)] in VALUES clause",
            "INSERT INTO sink VALUES ((SELECT MIN(id) FROM dz WHERE id > 0)/2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 2] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MAX(id) FROM dzv))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 0] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz LIMIT 2))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(CAST(1 / 0 AS NUMBER(13,12))) / 4] in "
                + "VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz) / 4)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(CAST(2 / 0 AS NUMBER(13,12))) / 2] in "
                + "VALUES clause",
            "INSERT INTO sink VALUES (2/(SELECT MIN(id) FROM dz) / 2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(CAST(1.5 / 0 AS NUMBER(13,12))) / 2] in "
                + "VALUES clause",
            "INSERT INTO sink VALUES (1.5/(SELECT MIN(id) FROM dz) / 2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(1 / 0) * 2] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz) * 2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 3, rowCount => 3))) AS NUMBER(1,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT 2 FROM dz))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT 2 AS \"2\" FROM DZ AS DZ "
                + "WHERE DZ.ID > 0) AS NUMBER(1,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT 2 FROM dz WHERE id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0 GROUP BY DZ.ID) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz WHERE id > 0 GROUP BY id))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ  HAVING MIN(ID) > 0) AS NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz HAVING MIN(id) > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS "
                + "\"MIN(ID)\" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))] in VALUES clause",
            "SELECT $1 FROM VALUES (1/(SELECT MIN(id) FROM dz WHERE id > 0))");
        assertAnswer("0|0", "SELECT COUNT(*), COUNT(x) FROM sink");
    }
}
