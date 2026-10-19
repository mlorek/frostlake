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
 * A positional bind is a LIMIT's, an OFFSET's and a FETCH's count to the account, and one nothing sets is refused while
 * the statement compiles: {@code Bind variable ? not set.} at the mark (live-verified).
 */
public class RowLimitBindTest extends MeasuredCellsTestSupport {

    @Test
    public void aPositionalBindIsACount() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 2), (3, 4)");
        assertCells(new String[][] {
            {"TABLE t LIMIT -1", "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '-'."},
            {"SELECT a FROM t LIMIT ?",
                "REFUSED SQL compilation error: error line 1 at position 22 | Bind variable ? not set."},
            {"SELECT a FROM t LIMIT ? OFFSET 1",
                "REFUSED SQL compilation error: error line 1 at position 22 | Bind variable ? not set."},
            {"SELECT a FROM t LIMIT 1 OFFSET ?",
                "REFUSED SQL compilation error: error line 1 at position 31 | Bind variable ? not set."},
            {"SELECT a FROM t FETCH FIRST ? ROWS ONLY",
                "REFUSED SQL compilation error: error line 1 at position 28 | Bind variable ? not set."},
            {"SELECT TOP ? a FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '?'."},
            {"SELECT a FROM t LIMIT (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax "
                + "error line 1 at position 22 unexpected '('."},
            {"SELECT a FROM t ORDER BY a LIMIT ?",
                "REFUSED SQL compilation error: error line 1 at position 33 | Bind variable ? not set."},
        });
    }
}
