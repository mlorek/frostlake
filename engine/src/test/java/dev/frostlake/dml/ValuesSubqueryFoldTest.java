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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A VALUES item's constants fold before it is judged: a NULL operand folds its operation away, a whole quotient
 * folds, and a subquery its constants or the statistics answer is echoed as its value (all live-verified).
 */
public class ValuesSubqueryFoldTest extends MeasuredCellsTestSupport {

    @Test
    public void subqueriesFoldIntoTheEcho() {
        engine.execute("CREATE OR REPLACE TABLE sink (x NUMBER(38,6))");
        engine.execute("CREATE OR REPLACE TABLE dz (id INT)");
        engine.execute("INSERT INTO dz VALUES (0), (1), (2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 0] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 2] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MAX(id) FROM dz))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 2.5] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT 2.5))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / -3] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT -3))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(1 / 0) + 1] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz) + 1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [SCALED_ROUND_INT_DIVIDE(0, 2)] in VALUES "
                + "clause",
            "INSERT INTO sink VALUES ((SELECT MIN(id) FROM dz)/2)");
        answer("INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz WHERE id > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (TO_NUMBER('a', 18, 5))] in VALUES "
                + "clause",
            "INSERT INTO sink VALUES (1/(SELECT 'a'))");
        assertAnswer("1", "INSERT INTO sink VALUES (1/(SELECT NULL))");
        assertAnswer("1|0", "SELECT COUNT(*), COUNT(x) FROM sink");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 3] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT COUNT(*) FROM dz))");
        answer("INSERT INTO sink VALUES (1/(SELECT SUM(id) FROM dz))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 2] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT 2))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 5] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT 2 + 3))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [2.5 / 2] in VALUES clause",
            "INSERT INTO sink VALUES ((SELECT 2.5)/2)");
        assertAnswer("1", "INSERT INTO sink VALUES (1/(SELECT NULL) + 1)");
        assertAnswer("1", "INSERT INTO sink VALUES ((SELECT NULL)/2)");
        assertAnswer("3|0", "SELECT COUNT(*), COUNT(x) FROM sink");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 2.5] in VALUES clause",
            "SELECT $1 FROM VALUES (1/(SELECT 2.5))");
        assertAnswer("null", "SELECT $1 FROM VALUES (1/(SELECT NULL))");
    }

    @Test
    public void foldedSubqueryShapes() {
        engine.execute("CREATE OR REPLACE TABLE sink (x NUMBER(38,6))");
        engine.execute("CREATE OR REPLACE TABLE dz (id INT)");
        engine.execute("INSERT INTO dz VALUES (0), (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE dze (id INT)");
        engine.execute("CREATE OR REPLACE TABLE dzn (n NUMBER(10,2))");
        engine.execute("INSERT INTO dzn VALUES (1.50), (2.25)");
        engine.execute("CREATE OR REPLACE VIEW dzv AS SELECT id FROM dz");
        assertAnswer("1", "INSERT INTO sink VALUES (1/NULL)");
        assertAnswer("1", "INSERT INTO sink VALUES (NULL/2)");
        assertAnswer("1", "INSERT INTO sink VALUES (SQRT(NULL))");
        assertAnswer("1", "INSERT INTO sink VALUES (1/NULL + 1)");
        assertAnswer("4|0", "SELECT COUNT(*), COUNT(x) FROM sink");
        assertAnswer("1", "INSERT INTO sink VALUES (1/(SELECT NULL::NUMBER))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 2] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT '2'))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 2] in VALUES clause",
            "INSERT INTO sink VALUES (1/'2')");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (TO_NUMBER('a', 18, 5))] in VALUES "
                + "clause",
            "INSERT INTO sink VALUES (1/'a')");
        assertAnswer("1", "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dze))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 3] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT COUNT(id) FROM dz))");
        answer("INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dzv))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 1.5] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(n) FROM dzn))");
        assertAnswer("1", "INSERT INTO sink VALUES (2/(SELECT MAX(id) FROM dz))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [7 / 2] in VALUES clause",
            "INSERT INTO sink VALUES ((SELECT 7)/2)");
        assertAnswer("1", "INSERT INTO sink VALUES ((SELECT MAX(id) FROM dz)/(SELECT 2))");
        answer("INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz LIMIT 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(1 / 0) * 2] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz) * 2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [SQRT(4.0)] in VALUES clause",
            "INSERT INTO sink VALUES (SQRT((SELECT 4)))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 3] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MAX(id) + 1 FROM dz))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / -2.5] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT -2.5))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1.0 / 2.0] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT 2::FLOAT))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 0] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT COUNT(*) FROM dze))");
        assertAnswer("8|2", "SELECT COUNT(*), COUNT(x) FROM sink");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 0] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM test_db.test_schema.dz))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [7 / 2] in VALUES clause",
            "SELECT $1 FROM VALUES ((SELECT 7)/2)");
        answer("INSERT INTO sink VALUES (1/(SELECT 2 FROM dz LIMIT 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / (CAST(1 / 3 AS NUMBER(7,6)))] in "
                + "VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT 1/3))");
        answer("INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz GROUP BY id LIMIT 1))");
        answer("INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz) / 2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 0] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MIN(id) FROM dz)), (1/(SELECT 2.5))");
        assertAnswer("2", "INSERT INTO sink VALUES (1/(SELECT NULL)), (2)");
        assertAnswer("10|3", "SELECT COUNT(*), COUNT(x) FROM sink");
    }

    @Test
    public void nullsAndWholeQuotientsFold() {
        engine.execute("CREATE OR REPLACE TABLE sink (x NUMBER(38,6))");
        engine.execute("CREATE OR REPLACE TABLE dz (id INT)");
        engine.execute("INSERT INTO dz VALUES (0), (1), (2)");
        assertAnswer("1", "INSERT INTO sink VALUES (2/2)");
        assertAnswer("1", "INSERT INTO sink VALUES (4/2)");
        assertAnswer("1", "INSERT INTO sink VALUES (6/3)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [0 / 2] in VALUES clause",
            "INSERT INTO sink VALUES (0/2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [0 / 0] in VALUES clause",
            "INSERT INTO sink VALUES (0/0)");
        assertAnswer("1", "INSERT INTO sink VALUES (2.5/2.5)");
        assertAnswer("1", "INSERT INTO sink VALUES (-3/-3)");
        assertAnswer("1", "INSERT INTO sink VALUES (3/-3)");
        assertAnswer("1", "INSERT INTO sink VALUES (2/2.0)");
        assertAnswer("1", "INSERT INTO sink VALUES ((1+1)/2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SQRT(4.0)) / (SQRT(4.0))] in VALUES clause",
            "INSERT INTO sink VALUES (SQRT(4)/SQRT(4))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [2.0 / 2.0] in VALUES clause",
            "INSERT INTO sink VALUES (2::FLOAT/2::FLOAT)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [2.0 / 2.0] in VALUES clause",
            "INSERT INTO sink VALUES ('2'/'2')");
        assertAnswer("1.000000", "SELECT $1 FROM VALUES (2/2)");
        assertAnswer("2.000000", "SELECT $1 FROM VALUES (4/2)");
        assertAnswer("1", "INSERT INTO sink VALUES (1/(SELECT 1))");
        assertAnswer("1", "INSERT INTO sink VALUES ((SELECT 3)/(SELECT 3))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 + (1 / 2)] in VALUES clause",
            "INSERT INTO sink VALUES (2/2 + 1/2)");
        assertAnswer("10|10", "SELECT COUNT(*), COUNT(x) FROM sink");
        assertAnswer("1", "INSERT INTO sink VALUES (ROUND(NULL))");
        assertAnswer("1", "INSERT INTO sink VALUES (POWER(2, NULL))");
        assertAnswer("1", "INSERT INTO sink VALUES (SQRT(4) + NULL)");
        assertAnswer("1", "INSERT INTO sink VALUES (SQRT(4) / NULL)");
        assertAnswer("1", "INSERT INTO sink VALUES (1/NULL::NUMBER)");
        assertAnswer("1", "INSERT INTO sink VALUES (1/CAST(NULL AS INT))");
        assertAnswer("1", "INSERT INTO sink VALUES (SQRT(NULL::FLOAT))");
        assertAnswer("17|10", "SELECT COUNT(*), COUNT(x) FROM sink");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 2.5] in VALUES clause",
            "INSERT INTO sink VALUES (1/'2.50')");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / -3] in VALUES clause",
            "INSERT INTO sink VALUES (1/'-3')");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 2.5] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT '2.5'))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(1 / 2) + (1 / 2)] in VALUES clause",
            "INSERT INTO sink VALUES (1/(SELECT MAX(id) FROM dz) + 1/(SELECT MAX(id) FROM dz))");
        assertAnswer("1", "INSERT INTO sink VALUES (3/(SELECT COUNT(*) FROM dz))");
        assertAnswer("1", "INSERT INTO sink VALUES ((SELECT COUNT(*) FROM dz)/3)");
        assertAnswer("1", "INSERT INTO sink VALUES ((SELECT MAX(id) FROM dz)/2)");
        assertAnswer("20|13", "SELECT COUNT(*), COUNT(x) FROM sink");
    }
}
