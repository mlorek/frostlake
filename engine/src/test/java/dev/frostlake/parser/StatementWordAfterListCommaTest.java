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
 * A statement in the parentheses of a FROM list's item after a comma is refused at its first word (live-verified), and
 * the lines after it are those of the list read on. An UPDATE adds the SET after its name, an EXPLAIN its USING, and a
 * CALL or an INSERT the token past its item's first comma: a number there, or the ')' after a text or a name. CALL p(x,
 * y) reads as a table named CALL with an alias and its column list. In a query used as a value the query's SELECT comes
 * first, and the lines that follow it depend on where the query stands.
 */
public class StatementWordAfterListCommaTest extends MeasuredCellsTestSupport {

    /** Each word after a comma at the top level and in queries used as values. */
    @Test
    public void theWordAndWhatTheListReadsOn() {
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertCells(new String[][] {
            {"SELECT 1 FROM rt_ctas, (CALL p())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'."},
            {"SELECT 1 FROM rt_ctas, (CALL p()) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'."},
            {"SELECT 1 FROM rt_ctas, (CALL p(1, 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'. | syntax error "
                + "line 1 at position 34 unexpected '2'."},
            {"SELECT 1 FROM rt_ctas, (CALL p)",
                "REFUSED SQL compilation error: | Object 'CALL' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE CALL."},
            {"SELECT 1 FROM rt_ctas, (CALL db.s.p())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'."},
            {"SELECT (SELECT 1 FROM rt_ctas, (CALL p()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 32 unexpected 'CALL'."},
            {"SELECT 1 + (SELECT 1 FROM rt_ctas, (CALL p()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 36 unexpected 'CALL'."},
            {"SELECT ABS((SELECT 1 FROM rt_ctas, (CALL p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected 'p'. | syntax error line 1 at position 42 unexpected '('."},
            {"SELECT 1 FROM rt_ctas WHERE 1 = (SELECT 1 FROM rt_ctas, (CALL p()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 62 unexpected 'p'. | syntax error line 1 at position 64 unexpected ')'. | "
                + "syntax error line 1 at position 66 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas, rt_ctas t2, (CALL p())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CALL'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (CALL p()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'CALL'. | syntax error "
                + "line 1 at position 68 unexpected ')'."},
            {"SELECT 1 FROM (CALL p())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'CALL'."},
            {"SELECT 1 FROM rt_ctas JOIN (CALL p()) ON TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'CALL'."},
            {"SELECT ABS((SELECT 1 FROM rt_ctas, (CALL p()) x))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected 'p'. | syntax error line 1 at position 42 unexpected '('."},
            {"SELECT ABS((SELECT 1 FROM rt_ctas, (CALL p(1))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected 'p'. | syntax error line 1 at position 42 unexpected '('."},
            {"SELECT COALESCE((SELECT 1 FROM rt_ctas, (CALL p())), 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 46 unexpected 'p'. | syntax error line 1 at position 47 unexpected '('."},
        });
    }

    /** A comma inside a CALL's or an INSERT's item, and items after the statement's. */
    @Test
    public void commasInsideTheItem() {
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertCells(new String[][] {
            {"SELECT 1 FROM rt_ctas, (CALL p(x, y))",
                "REFUSED SQL compilation error: | Object 'CALL' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE CALL."},
            {"SELECT 1 FROM rt_ctas, (CALL p(1, 'a'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'. | syntax error "
                + "line 1 at position 37 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'DESCRIBE'."},
            {"SELECT 1 FROM rt_ctas, (UPDATE rt_ctas SET a = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'UPDATE'. | syntax "
                + "error line 1 at position 39 unexpected 'SET'."},
            {"SELECT 1 FROM rt_ctas, (INSERT INTO rt_ctas VALUES (1, 'a'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'INSERT'. | syntax "
                + "error line 1 at position 58 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas, (EXPLAIN USING TEXT SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'EXPLAIN'. | syntax "
                + "error line 1 at position 32 unexpected 'USING'."},
            {"SELECT 1 FROM rt_ctas, (CALL p(1, 2)) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'. | syntax error "
                + "line 1 at position 34 unexpected '2'."},
            {"SELECT 1 FROM rt_ctas, (CALL p(1), 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'. | syntax error "
                + "line 1 at position 35 unexpected '2'."},
            {"SELECT 1 FROM rt_ctas, (TRUNCATE TABLE rt_ctas)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'TRUNCATE'."},
            {"SELECT 1 FROM rt_ctas, (CALL p()), rt_ctas t2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'."},
            {"SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas), rt_ctas t2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'DESCRIBE'."},
            {"SELECT 1 FROM rt_ctas, (CALL p(1, 2, 3))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'. | syntax error "
                + "line 1 at position 34 unexpected '2'."},
        });
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertCells(new String[][] {
            {"SELECT 1 FROM rt_ctas, (CALL p(1, x))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'. | syntax error "
                + "line 1 at position 35 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas, (CALL p(1, 2.5))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'. | syntax error "
                + "line 1 at position 34 unexpected '2.5'."},
            {"SELECT 1 FROM rt_ctas, (CALL p('a', 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CALL'. | syntax error "
                + "line 1 at position 36 unexpected '1'."},
            {"SELECT 1 FROM rt_ctas, (INSERT INTO rt_ctas VALUES (1, 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'INSERT'. | syntax "
                + "error line 1 at position 55 unexpected '2'."},
        });
    }
}
