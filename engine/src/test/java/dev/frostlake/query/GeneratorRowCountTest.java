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
 * GENERATOR reads a bound that is NULL as a bound not given: a NULL ROWCOUNT, whatever folds to it, generates
 * no rows, and a NULL TIMELIMIT leaves the ROWCOUNT to bound them. A ROWCOUNT that is a text or a BOOLEAN is
 * refused by its type while the statement compiles, and a negative one in the account's sentence. Every
 * answer is live-verified.
 */
public class GeneratorRowCountTest extends MeasuredCellsTestSupport {

    @Test
    public void theRowsABoundGenerates() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE d (k INT)");
        engine.execute(
            "INSERT INTO d VALUES (1), (2)");
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        engine.execute(
            "CREATE OR REPLACE VIEW v2 (x) AS SELECT n + 1 FROM t");
        engine.execute(
            "SET one = 1");
        engine.execute(
            "SET half = 0.5");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL + 1))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL::INT))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NVL(NULL, NULL) + 1))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => NULL))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [-1] for function 'GENERATOR', parameter ROWCOUNT:  "
                + "must be non-negative.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => -1))");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => '3'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 'a'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TRUE))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR())");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2 + 1))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULLIF(1, 1)))");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => COALESCE(NULL, 2)))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => IFF(TRUE, NULL, 3)))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TRY_TO_NUMBER('x')))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ABS(NULL)))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL, TIMELIMIT => NULL))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3, TIMELIMIT => NULL))");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2.5))");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2.4))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(3)] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => '2.5'))");
        assertAnswer(
            "10",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1e1))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3::FLOAT))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => -NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(134217728)] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ''))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => FALSE))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL::BOOLEAN))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2, ROWCOUNT => 3))");
        assertAnswer(
            "",
            "SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => NVL(NULL, NULL) + 1))");
        assertShape(
            "SEQ4():NUMBER(10,0)",
            "SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => NVL(NULL, NULL) + 1))");
        assertAnswer(
            "",
            "SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => NULL))");
        assertShape(
            "SEQ4():NUMBER(10,0)",
            "SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => NULL))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL)) g, d");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM d, TABLE(GENERATOR(ROWCOUNT => NULL + d.k))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT NULL)))");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 2)))");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => $one))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(NULL))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(2))");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET one");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET half");
    }
}
