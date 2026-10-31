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
 * The syntax-error lines of a TABLE query written with an alias inside a bracket that may hold a value as well as
 * a query - a scalar subquery, an IN list, an ANY or ALL comparison: the TABLE is refused first, then the token
 * after the first part of the name. Under EXISTS, as a FROM item and as a statement the alias is refused alone
 * (all live-verified).
 */
public class TableQueryLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void inAQuantifiedComparison() {
        engine.execute("CREATE OR REPLACE TABLE pq3 (n INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 26 unexpected 't'.",
            "SELECT 1 = ANY (TABLE pq3 t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 23 unexpected 't'.",
            "SELECT 1 IN (TABLE pq3 t)");
    }

    @Test
    public void inEveryBracket() {
        engine.execute("CREATE OR REPLACE TABLE pq3 (n INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'TABLE'. | syntax "
                + "error line 1 at position 18 unexpected 't'.",
            "SELECT (TABLE pq3 t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 't'.",
            "SELECT 1 FROM pq3 WHERE EXISTS (TABLE pq3 t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 23 unexpected 'AS'.",
            "SELECT 1 IN (TABLE pq3 AS t)");
        assertAnswer("false", "SELECT 1 IN (TABLE pq3)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 't'.",
            "SELECT * FROM (TABLE pq3 t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 26 unexpected 't'.",
            "SELECT 1 = ALL (TABLE pq3 t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 23 unexpected 't'.",
            "SELECT 1 IN (TABLE pq3 t) FROM pq3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 27 unexpected 't'.",
            "SELECT 1 NOT IN (TABLE pq3 t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 30 unexpected '.'.",
            "SELECT 1 IN (TABLE test_schema.pq3 t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 't'.",
            "TABLE pq3 t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 24 unexpected 't'.",
            "SELECT 1 IN ((TABLE pq3 t))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 23 unexpected 't'.",
            "SELECT 1 IN (TABLE pq3 t) AS k");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 26 unexpected 't'.",
            "SELECT 1 = ANY (TABLE pq3 t), 2");
    }
}
