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
 * The ORDER BY of a query that aggregates with no GROUP BY. The query answers one row, so once the keys' names,
 * argument types and ordinals have been judged the clause is dropped: no key is computed, no subquery of a key is
 * run or judged as a correlation, and DISTINCT asks nothing of the keys. A GROUP BY query keeps its ORDER BY,
 * one group or not. Every answer is live-verified.
 */
public class AggregateQueryOrderByTest extends MeasuredCellsTestSupport {

    @Test
    public void theKeysAreJudgedAndNeverComputed() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id / 0)");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz HAVING MAX(id) > 0 ORDER BY MAX(id / 0)");
        assertAnswer(
            "7|5",
            "SELECT MAX(id), MIN(id) FROM fz ORDER BY 2 / 0");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX((SELECT g.v FROM g))");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY (SELECT g.v FROM g)");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX((SELECT g.v FROM g WHERE g.id = fz.id))");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX((SELECT MAX(fz.id + g.v) FROM g))");
        assertAnswer(
            "7",
            "SELECT DISTINCT MAX(id) FROM fz ORDER BY MAX(id + 1)");
        assertAnswer(
            "7",
            "SELECT DISTINCT MAX(id) FROM fz ORDER BY MAX((SELECT MAX(g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier 'NOSUCH'",
            "SELECT MAX(id) FROM fz ORDER BY nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT MAX(id) FROM fz ORDER BY MAX('x' + TRUE)");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT MAX(id) FROM fz GROUP BY 'k' ORDER BY MAX(id / 0)");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY id");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY b");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY 1 / 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [5] is not a valid order by expression",
            "SELECT MAX(id) FROM fz ORDER BY 5");
        assertAnswer(
            "REFUSED SQL compilation error: | [0] is not a valid order by expression",
            "SELECT MAX(id) FROM fz ORDER BY 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [-1] is not a valid order by expression",
            "SELECT MAX(id) FROM fz ORDER BY -1");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY 'a'");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY NULL");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY SUM(MAX(id / 0)) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY MAX(id)) = 1 ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | found QUALIFY clause but no window "
                + "function.",
            "SELECT MAX(id) FROM fz QUALIFY 1 / 0 = 1");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT MAX(id) FROM fz HAVING MAX(id / 0) > 0");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id / 0) LIMIT 1");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id / 0) DESC NULLS LAST");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM fz ORDER BY id / 0");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM fz ORDER BY (SELECT 1 / 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCH_FN.",
            "SELECT COUNT(*) FROM fz ORDER BY nosuch_fn(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | too many arguments for function "
                + "[UPPER(1, 2)] expected 1, got 2",
            "SELECT COUNT(*) FROM fz ORDER BY UPPER(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 41 | invalid identifier 'NOSUCH'",
            "SELECT COUNT(*) FROM fz ORDER BY (SELECT nosuch FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 45 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT COUNT(*) FROM fz ORDER BY (SELECT 'x' + TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'G.ID'",
            "SELECT COUNT(*) FROM fz ORDER BY g.id");
        assertAnswer(
            "2",
            "SELECT COUNT(*) c FROM fz ORDER BY c / 0");
        assertAnswer(
            "2",
            "SELECT COUNT(*) c FROM fz ORDER BY c");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY TO_NUMBER('x')");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX(TO_NUMBER('x'))");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT MAX(id) FROM fz UNION ALL SELECT 1 ORDER BY 1 / 0");
        assertAnswer(
            "7",
            "SELECT * FROM (SELECT MAX(id) FROM fz ORDER BY MAX(id / 0))");
        assertAnswer(
            "7",
            "SELECT (SELECT MAX(id) FROM fz ORDER BY MAX(id / 0))");
        assertAnswer(
            "null",
            "SELECT MAX(id) FROM fz WHERE 1 = 0 ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT MAX(id), b FROM fz GROUP BY b ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ')'.",
            "SELECT MAX(id) FROM fz GROUP BY () ORDER BY MAX(id / 0)");
        assertAnswer(
            "7",
            "SELECT DISTINCT MAX(id) FROM fz ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 41 | invalid identifier 'NOSUCH'",
            "SELECT DISTINCT MAX(id) FROM fz ORDER BY nosuch");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id) / 0");
        assertAnswer(
            "1",
            "SELECT 1 FROM fz HAVING COUNT(*) > 0 ORDER BY 1 / 0");
        assertAnswer(
            "5",
            "SELECT ANY_VALUE(id) FROM fz ORDER BY SUM(id) / 0");
    }
}
