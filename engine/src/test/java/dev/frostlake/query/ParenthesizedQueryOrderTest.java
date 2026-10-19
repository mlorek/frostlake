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
 * An ORDER BY, a LIMIT or a FETCH written after a parenthesized query — (SELECT * FROM t) ORDER BY 1, (TABLE t)
 * ORDER BY b DESC FETCH FIRST 1 ROW ONLY — applies to the query's rows, by its output columns' names, positions and
 * expressions over them (live-verified).
 */
public class ParenthesizedQueryOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void theStatementsOrderAppliesToTheQuery() {
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, b INT)");
        engine.execute("INSERT INTO tt VALUES (3, 4), (1, 2), (2, 9)");
        assertAnswer("1|2 ;; 2|9 ;; 3|4", "(SELECT * FROM tt) ORDER BY 1");
        assertAnswer("3|4 ;; 2|9 ;; 1|2", "(SELECT * FROM tt) ORDER BY a DESC");
        answer("(SELECT * FROM tt ORDER BY a DESC) LIMIT 1");
        assertAnswer("1|2", "(SELECT * FROM tt) ORDER BY a LIMIT 1");
        assertAnswer("2|9", "(SELECT * FROM tt) ORDER BY a LIMIT 1 OFFSET 1");
        assertAnswer("1|2 ;; 2|9 ;; 3|4", "(TABLE tt) ORDER BY 1");
        assertAnswer("2|9", "(TABLE tt) ORDER BY b DESC FETCH FIRST 1 ROW ONLY");
        assertAnswer("1|2 ;; 2|9 ;; 3|4", "((SELECT * FROM tt)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH'",
            "(SELECT a FROM tt) ORDER BY nosuch");
        assertAnswer("3 ;; 2 ;; 1", "(SELECT a AS x FROM tt) ORDER BY x DESC");
        assertAnswer("REFUSED SQL compilation error: | [2] is not a valid order by expression",
            "(SELECT a FROM tt) ORDER BY 2");
        assertAnswer("3 ;; 2 ;; 1", "(SELECT a FROM tt) ORDER BY a + 0 DESC");
        answer("(SELECT a FROM tt) ORDER BY b");
        assertAnswer("1 ;; 2", "SELECT * FROM ((SELECT a FROM tt) ORDER BY 1 LIMIT 2) ORDER BY 1");
    }
}
