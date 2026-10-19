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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The token after a LIMIT or an OFFSET that may be a table's alias (live-verified): a bracket that holds no name
 * is refused at the token inside it and then at the bracket, whatever is written in it, and after the two
 * readings the clause's reading reads on — a later clause's value and a later statement are refused too, the
 * rest of the value passed over.
 */
public class RowLimitReadOnTest extends MeasuredCellsTestSupport {

    @Test
    public void theClauseReadsOnAfterItsValue() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertCells(new String[][] {
            {"SELECT a FROM t LIMIT (SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 22 unexpected '('."},
            {"SELECT a FROM t LIMIT -1; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'. | syntax error line 1 at position 37 unexpected 'y'."},
            {"SELECT a FROM t LIMIT -1 OFFSET -1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'. | syntax error line 1 at position 32 unexpected '-'."},
            {"SELECT a FROM t LIMIT (SELECT 1) OFFSET 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 22 unexpected '('."},
            {"SELECT a FROM t LIMIT (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax error "
                + "line 1 at position 22 unexpected '('."},
            {"SELECT a FROM t LIMIT ((SELECT 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '('. | syntax error "
                + "line 1 at position 22 unexpected '('."},
            {"SELECT a FROM t OFFSET (SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 23 unexpected '('."},
            {"SELECT a FROM t LIMIT (SELECT 1); SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 22 unexpected '('. | syntax error line 1 at position 45 unexpected 'y'."},
            {"SELECT a FROM t LIMIT -1 OFFSET 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'."},
            {"SELECT a FROM t LIMIT -1 x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'."},
            {"SELECT a FROM t LIMIT -1; SELECT 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'."},
            {"SELECT a FROM t OFFSET -1; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '-'. | syntax error "
                + "line 1 at position 23 unexpected '-'. | syntax error line 1 at position 38 unexpected 'y'."},
            {"SELECT a FROM t LIMIT 1 OFFSET -1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '-'."},
            {"SELECT a FROM t LIMIT -1 OFFSET -1; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'. | syntax error line 1 at position 32 unexpected '-'. | "
                + "syntax error line 1 at position 47 unexpected 'y'."},
            {"SELECT a FROM t FETCH FIRST -1 ROWS ONLY; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '-'. | syntax error "
                + "line 1 at position 53 unexpected 'y'."},
            {"SELECT a FROM t LIMIT (SELECT 1 FROM t)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 22 unexpected '('."},
            {"SELECT a FROM t LIMIT (SELECT a FROM t)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 22 unexpected '('."},
            {"SELECT a FROM t LIMIT -1 OFFSET 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'."},
            {"SELECT a FROM t LIMIT -1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'."},
            {"SELECT a FROM t ORDER BY a LIMIT -1; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '-'. | syntax error "
                + "line 1 at position 48 unexpected 'y'."},
            {"SELECT a FROM t LIMIT (SELECT)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 22 unexpected '('."},
            {"SELECT a FROM t LIMIT (SELECT 1) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 22 unexpected '('."},
        });
    }
}
