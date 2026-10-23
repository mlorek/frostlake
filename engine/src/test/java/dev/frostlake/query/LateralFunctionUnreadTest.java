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
 * A query that reads no row of its FROM - a WHERE whose constant conjunct is FALSE or NULL, a LIMIT 0, an inner
 * join on such a constant - calls none of its lateral table functions, so an argument that would fault or be
 * refused for a row answers no row, though the names it reads are still judged; a WHERE that a row could still
 * pass calls the function for each row. A function whose arguments cannot be read with NULL inputs keeps its
 * columns, and its argument's correlated subquery is refused at the first row (all live-verified).
 */
public class LateralFunctionUnreadTest extends MeasuredCellsTestSupport {

    @Test
    public void lateralCallsOfAQueryThatReadsNoRow() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(fz.id, (SELECT v FROM g WHERE "
                + "g.id = fz.id)))) f WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 78",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(fz.id, (SELECT v FROM g WHERE "
                + "g.id = fz.id)))) f");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 95",
            "SELECT d.id, f.value FROM (SELECT id FROM fz) d, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(d.id, (SELECT v "
                + "FROM g WHERE g.id = d.id)))) f");
        assertAnswer("",
            "SELECT d.id, f.value FROM (SELECT id FROM fz) d, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(d.id, (SELECT v "
                + "FROM g WHERE g.id = d.id)))) f LIMIT 0");
        assertAnswer("",
            "SELECT d.id, f.value FROM (SELECT id FROM fz) d, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(d.id, (SELECT v "
                + "FROM g WHERE g.id = d.id)))) f WHERE FALSE");
        assertAnswer("",
            "SELECT d.id, f.value FROM (SELECT id FROM fz GROUP BY id) d, TABLE(FLATTEN(INPUT => "
                + "ARRAY_CONSTRUCT(d.id, (SELECT v FROM g WHERE g.id = d.id)))) f WHERE 1 = 0");
        assertAnswer("",
            "SELECT fz.id, f.value FROM fz, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(fz.id, (SELECT v FROM g WHERE "
                + "g.id = fz.id))) f WHERE 1 = 0");
        assertAnswer("",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => PARSE_JSON('[' || fz.id))) f WHERE 1 = 0");
        assertAnswer("REFUSED Error parsing JSON: incomplete array value, pos 3",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => PARSE_JSON('[' || fz.id))) f");
        assertAnswer("",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => PARSE_JSON('[' || fz.id))) f WHERE fz.id = 5 AND "
                + "1 = 0");
        assertAnswer("",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => PARSE_JSON('[' || fz.id))) f WHERE 1 = 0 OR fz.id "
                + "= 99");
        assertAnswer("",
            "SELECT fz.id, f.value FROM fz JOIN g ON 1 = 0, TABLE(FLATTEN(INPUT => PARSE_JSON('[' || fz.id))) f");
        assertAnswer("",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1 / (fz.id - 5)))) f WHERE 1 = 0");
        assertAnswer("REFUSED Division by zero",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1 / (fz.id - 5)))) f");
        assertAnswer("",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1 / (fz.id - 5)))) f LIMIT 0");
        assertAnswer("",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1 / (fz.id - 5)))) f WHERE NULL");
        assertAnswer("0", "SELECT COUNT(*) FROM fz, TABLE(FLATTEN(INPUT => PARSE_JSON('[' || fz.id))) f WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 70 | invalid identifier 'NOSUCH_COL'",
            "SELECT fz.id, f.value FROM fz, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(nosuch_col))) f WHERE 1 = 0");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
