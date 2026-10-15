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
 * GROUP BY ALL over a select list that holds no key — nothing but aggregates and window calls — is the one
 * group an aggregate query with no GROUP BY is: one row, over an empty input too, its ORDER BY dropped, and a
 * column a window call or a HAVING reads held to the grouping. A constant item is a key, and the query is
 * grouped by it (live-verified).
 */
public class GroupByAllWithoutKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void theOrderByIsDropped() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz GROUP BY ALL ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz GROUP BY ALL ORDER BY MAX(id) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz GROUP BY ALL ORDER BY SUM(MAX(id)) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz GROUP BY ALL ORDER BY id");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz GROUP BY ALL");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz GROUP BY ALL ORDER BY MAX(id / 0), 1");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT MAX(id) FROM fz GROUP BY ALL HAVING MAX(id / 0) > 0");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz GROUP BY ALL LIMIT 1");
        assertAnswer(
            "7|2",
            "SELECT MAX(id), COUNT(*) FROM fz GROUP BY ALL ORDER BY MAX(id / 0)");
        assertAnswer(
            "8",
            "SELECT MAX(id) + 1 FROM fz GROUP BY ALL ORDER BY MAX(id / 0)");
    }

    @Test
    public void aConstantItemIsAKey() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT 1, MAX(id) FROM fz GROUP BY ALL ORDER BY MAX(id / 0)");
        assertAnswer(
            "1|7",
            "SELECT 1, MAX(id) FROM fz GROUP BY ALL");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT 'a', MAX(id) FROM fz GROUP BY ALL ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT 1 + 1, MAX(id) FROM fz GROUP BY ALL ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT NULL, MAX(id) FROM fz GROUP BY ALL ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT CURRENT_DATE() IS NOT NULL, MAX(id) FROM fz GROUP BY ALL ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT b, MAX(id) FROM fz GROUP BY ALL ORDER BY MAX(id / 0)");
    }

    @Test
    public void oneRowOverAnEmptyInput() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "null",
            "SELECT MAX(id) FROM fz WHERE FALSE GROUP BY ALL");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM fz WHERE FALSE GROUP BY ALL");
        assertAnswer(
            "",
            "SELECT 1, COUNT(*) FROM fz WHERE FALSE GROUP BY ALL");
        assertAnswer(
            "",
            "SELECT 'a', COUNT(*) FROM fz WHERE FALSE GROUP BY ALL");
        assertAnswer(
            "",
            "SELECT b, COUNT(*) FROM fz WHERE FALSE GROUP BY ALL");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM fz WHERE FALSE");
        assertAnswer(
            "1|0",
            "SELECT 1, COUNT(*) FROM fz WHERE FALSE");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM fz WHERE FALSE GROUP BY ALL HAVING COUNT(*) = 0");
        assertAnswer(
            "null",
            "SELECT MAX(id) FROM fz WHERE FALSE GROUP BY ALL ORDER BY 1");
        assertAnswer(
            "null|0|null",
            "SELECT MAX(id), COUNT(*), SUM(id) FROM fz WHERE FALSE GROUP BY ALL");
        assertAnswer(
            "null",
            "SELECT MAX(id) + 1 FROM fz WHERE FALSE GROUP BY ALL");
        assertAnswer(
            "",
            "SELECT COUNT(*) FROM fz WHERE FALSE GROUP BY ALL LIMIT 0");
        assertAnswer(
            "0",
            "SELECT (SELECT COUNT(*) FROM fz WHERE FALSE GROUP BY ALL)");
        assertAnswer(
            "0",
            "SELECT * FROM (SELECT COUNT(*) c FROM fz WHERE FALSE GROUP BY ALL)");
        assertAnswer(
            "0 ;; 9",
            "SELECT COUNT(*) FROM fz WHERE FALSE GROUP BY ALL UNION ALL SELECT 9");
        assertAnswer(
            "1",
            "SELECT ROW_NUMBER() OVER (ORDER BY COUNT(*)) FROM fz WHERE FALSE GROUP BY ALL");
        assertAnswer(
            "0|1",
            "SELECT COUNT(*) c, c + 1 FROM fz WHERE FALSE GROUP BY ALL");
    }

    @Test
    public void windowCallsAndOtherForms() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) OVER () FROM fz GROUP BY ALL");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id), MAX(id) OVER () FROM fz GROUP BY ALL");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz GROUP BY ALL ORDER BY MAX(id) OVER (ORDER BY id)");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz GROUP BY ALL QUALIFY ROW_NUMBER() OVER (ORDER BY MAX(id)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ')'.",
            "SELECT MAX(id) FROM fz GROUP BY ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ')'.",
            "SELECT MAX(id) FROM fz GROUP BY () ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ')'.",
            "SELECT COUNT(*) FROM fz WHERE FALSE GROUP BY ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(ID)] is not a valid group by expression",
            "SELECT MAX(id) FROM fz GROUP BY 1");
        assertAnswer(
            "7",
            "SELECT MAX(id) x FROM fz GROUP BY ALL ORDER BY x");
        assertAnswer(
            "7",
            "SELECT MAX(id) x FROM fz GROUP BY ALL ORDER BY SUM(x) OVER ()");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT b FROM fz GROUP BY ALL ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) OVER () FROM fz GROUP BY ALL ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(FZ.B) OVER ()] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY ALL ORDER BY MAX(b) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz GROUP BY ALL HAVING id > 0");
        assertAnswer(
            "5|6 ;; 7|8",
            "SELECT MAX(id), id + 1 FROM fz GROUP BY ALL");
        assertAnswer(
            "1",
            "SELECT MAX(id) FROM (SELECT 1 id) GROUP BY ALL ORDER BY MAX(id / 0)");
    }
}
