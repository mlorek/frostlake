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
 * The ORDER BY of an aggregate query with no GROUP BY. The one row is never sorted and the clause is dropped,
 * its keys uncomputed — but a window call among them is held to the grouping first, as the select list's
 * are: a column it reads outside an aggregate is no valid group by expression, in a key of its own, under
 * another function, beside DISTINCT, HAVING, QUALIFY and LIMIT alike (live-verified).
 */
public class DroppedOrderByWindowKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void aWindowKeyReadingAColumn() {
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
            "SELECT MAX(id) FROM fz ORDER BY MAX(id) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY ROW_NUMBER() OVER (ORDER BY id)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY ROW_NUMBER() OVER (PARTITION BY id ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY ROW_NUMBER() OVER (PARTITION BY b ORDER BY MAX(id))");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY ROW_NUMBER() OVER (PARTITION BY MAX(b) ORDER BY id)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY COUNT(id) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY RANK() OVER (ORDER BY b)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id) OVER (), 1");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY 1, MAX(b) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY ABS(MAX(id) OVER ())");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY LAG(id) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY FIRST_VALUE(b) OVER (ORDER BY MAX(id))");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id) OVER (PARTITION BY MAX(b))");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(FZ.B AS NUMBER(2,0)))] is not a valid group by "
                + "expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id + b::INT) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id) OVER (ORDER BY id)");
    }

    @Test
    public void aWindowKeyOverAggregatesAndConstants() {
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
            "SELECT MAX(id) FROM fz ORDER BY SUM(MAX(id / 0)) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY SUM(MAX(id)) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY ROW_NUMBER() OVER (ORDER BY MAX(id))");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY COUNT(*) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX(1) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY id");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id / 0)");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY (SELECT MAX(id / 0) FROM fz)");
    }

    @Test
    public void besideOtherClauses() {
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
            "SELECT DISTINCT MAX(id) FROM fz ORDER BY MAX(id) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT DISTINCT MAX(id) FROM fz ORDER BY ROW_NUMBER() OVER (ORDER BY id)");
        assertAnswer(
            "7",
            "SELECT DISTINCT MAX(id) FROM fz ORDER BY SUM(MAX(id)) OVER ()");
        assertAnswer(
            "7",
            "SELECT DISTINCT MAX(id) FROM fz ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz HAVING TRUE ORDER BY MAX(id) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz HAVING TRUE ORDER BY MAX(id / 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY MAX(id)) = 1 ORDER BY MAX(id) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz LIMIT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id) OVER () LIMIT 1");
    }

    @Test
    public void whichColumnIsNamed() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + 1] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id + 1) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + 1] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY ROW_NUMBER() OVER (ORDER BY id + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | [ABS(FZ.ID)] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY ROW_NUMBER() OVER (PARTITION BY ABS(id) ORDER BY b)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id) OVER (ORDER BY b, id)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(b) OVER (ORDER BY id)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id) OVER (PARTITION BY b)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY ROW_NUMBER() OVER (PARTITION BY b ORDER BY id)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(b) OVER (), MAX(id) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier 'NOSUCH'",
            "SELECT MAX(id) FROM fz ORDER BY nosuch, MAX(id) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 49 | invalid identifier 'NOSUCH'",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id) OVER (), nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: | [2] is not a valid order by expression",
            "SELECT MAX(id) FROM fz ORDER BY 2, MAX(id) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [2] is not a valid order by expression",
            "SELECT MAX(id) FROM fz ORDER BY MAX(id) OVER (), 2");
        assertAnswer(
            "7",
            "SELECT MAX(id) x FROM fz ORDER BY MAX(x) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) x FROM fz ORDER BY SUM(x) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT COUNT(*) FROM fz ORDER BY MAX(id) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT 1 FROM fz HAVING COUNT(*) > 0 ORDER BY MAX(id) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT 1 FROM fz ORDER BY MAX(id), MAX(id) OVER ()");
        assertAnswer(
            "7",
            "SELECT MAX(id) FROM fz ORDER BY MAX(MAX(id)) OVER (ORDER BY COUNT(*))");
    }
}
