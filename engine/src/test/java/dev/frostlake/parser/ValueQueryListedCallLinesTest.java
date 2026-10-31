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
 * A query used as a value whose FROM list holds a statement after a comma (live-verified): after its SELECT, a call's
 * argument names the word after CALL and the token after it, DESCRIBE's, DESC's and EXPLAIN's next word twice; a
 * compared query names the name after CALL, the token inside its parentheses and the token after the first ')' past it,
 * an UPDATE and that token, DESCRIBE's or EXPLAIN's next word, and after a USING the token after the first ')'; a
 * select list opening with no literal names the word.
 */
public class ValueQueryListedCallLinesTest extends MeasuredCellsTestSupport {

    /** A CALL, an UPDATE and an EXPLAIN in a call's argument and in comparisons. */
    @Test
    public void callsAndUpdatesInValueQueries() {
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertCells(new String[][] {
            {"SELECT 1 FROM rt_ctas WHERE 1 = (SELECT 1 FROM rt_ctas, (CALL p(1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 62 unexpected 'p'. | syntax error line 1 at position 64 unexpected '1'. | "
                + "syntax error line 1 at position 66 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE 1 = (SELECT 1 FROM rt_ctas, (CALL p()) x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 62 unexpected 'p'. | syntax error line 1 at position 64 unexpected ')'. | "
                + "syntax error line 1 at position 67 unexpected 'x'."},
            {"SELECT 1 FROM rt_ctas WHERE (SELECT 1 FROM rt_ctas, (CALL p())) = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 58 unexpected 'p'. | syntax error line 1 at position 60 unexpected ')'. | "
                + "syntax error line 1 at position 62 unexpected ')'."},
            {"SELECT UPPER((SELECT 1 FROM rt_ctas, (CALL p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 43 unexpected 'p'. | syntax error line 1 at position 44 unexpected '('."},
            {"SELECT ABS((SELECT 'a' FROM rt_ctas, (CALL p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 43 unexpected 'p'. | syntax error line 1 at position 44 unexpected '('."},
            {"SELECT ABS((SELECT a FROM rt_ctas, (CALL p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 36 unexpected 'CALL'."},
            {"SELECT 1 FROM rt_ctas WHERE 1 = (SELECT 1 FROM rt_ctas, (UPDATE rt_ctas SET a = 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 57 unexpected 'UPDATE'. | syntax error line 1 at position 82 unexpected "
                + "')'."},
            {"SELECT 1 FROM rt_ctas WHERE 1 = (SELECT 1 FROM rt_ctas, (EXPLAIN SELECT 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 65 unexpected 'SELECT'."},
            {"SELECT 1 FROM rt_ctas WHERE 2 > (SELECT 1 FROM rt_ctas, (CALL p()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 62 unexpected 'p'. | syntax error line 1 at position 64 unexpected ')'. | "
                + "syntax error line 1 at position 66 unexpected ')'."},
            {"SELECT ABS((SELECT 1 FROM rt_ctas, (CALL db.s.p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected 'db'. | syntax error line 1 at position 43 unexpected '.'."},
            {"SELECT ABS((SELECT 1 FROM rt_ctas, (CALL p)))",
                "REFUSED SQL compilation error: | Object 'CALL' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE CALL."},
        });
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertCells(new String[][] {
            {"SELECT ABS((SELECT 1 FROM rt_ctas, (CALL p(1, 2))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected 'p'. | syntax error line 1 at position 42 unexpected '('."},
            {"SELECT 1 FROM rt_ctas WHERE 1 = (SELECT 1 FROM rt_ctas, (CALL p(1, 2)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 62 unexpected 'p'. | syntax error line 1 at position 64 unexpected '1'. | "
                + "syntax error line 1 at position 69 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE 1 = (SELECT 1 FROM rt_ctas, (DESC TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 62 unexpected 'TABLE'."},
            {"SELECT 1 FROM rt_ctas WHERE 1 = (SELECT 1 FROM rt_ctas, (EXPLAIN USING TEXT SELECT 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 65 unexpected 'USING'. | syntax error line 1 at position 85 unexpected "
                + "')'."},
            {"SELECT LOWER((SELECT 'x' FROM rt_ctas, (EXPLAIN SELECT 1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 48 unexpected 'SELECT'. | syntax error line 1 at position 48 unexpected "
                + "'SELECT'."},
        });
    }
}
