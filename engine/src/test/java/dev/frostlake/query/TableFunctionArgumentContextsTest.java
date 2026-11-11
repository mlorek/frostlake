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
 * A table function's bracketed-query argument followed by an operator is refused at the operator and then at the
 * second closing bracket after it, one more for each bracket around the query holding the call - a derived table, a
 * CTE, an EXISTS - and alike under INSERT ... SELECT, CTAS, a view, a later named argument and a call that is no
 * GENERATOR. In a select item, as an operand of it, the query is refused at its SELECT and then after the first name
 * of its list; as the item's whole value with a list that opens with no name, at its SELECT and then as the
 * statement's own FROM reads it (live-verified).
 */
public class TableFunctionArgumentContextsTest extends MeasuredCellsTestSupport {

    @Test
    public void bracketedQueryArgumentsInMoreContexts() {
        engine.execute("CREATE OR REPLACE TABLE g1 (n INT)");
        engine.execute("INSERT INTO g1 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g2 (n INT)");
        engine.execute("INSERT INTO g2 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE FUNCTION tfn915(x NUMBER) RETURNS TABLE (y NUMBER) AS 'SELECT x'");
        assertCells(new String[][] {
            {"SELECT * FROM (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 88 unexpected '+'. | syntax "
                + "error line 1 at position 93 unexpected ')'."},
            {"WITH c AS (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1))) SELECT "
                + "* FROM c",
                "REFUSED SQL compilation error: | syntax error line 1 at position 84 unexpected '+'. | syntax "
                + "error line 1 at position 89 unexpected ')'."},
            {"INSERT INTO g1 SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 88 unexpected '+'. | syntax "
                + "error line 1 at position 92 unexpected ')'."},
            {"CREATE OR REPLACE TABLE g9 AS SELECT COUNT(*) AS c FROM TABLE(GENERATOR(ROWCOUNT => (SELECT "
                + "MAX(n) FROM g2) + 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 108 unexpected '+'. | syntax "
                + "error line 1 at position 112 unexpected ')'."},
            {"CREATE OR REPLACE VIEW gv915 AS SELECT COUNT(*) AS c FROM TABLE(GENERATOR(ROWCOUNT => (SELECT "
                + "MAX(n) FROM g2) + 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 110 unexpected '+'. | syntax "
                + "error line 1 at position 114 unexpected ')'."},
            {"SELECT * FROM g1 WHERE EXISTS (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) "
                + "FROM g2) + 1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 104 unexpected '+'. | syntax "
                + "error line 1 at position 109 unexpected ')'."},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => 1, ROWCOUNT => (SELECT MAX(n) FROM g2) + 1))", "4"},
            {"SELECT * FROM TABLE(tfn915((SELECT MAX(n) FROM g2) + ABS(1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '+'."},
            {"SELECT 1 + (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 24 unexpected '('."},
            {"SELECT (SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 74 unexpected '+'. | syntax error line 1 at position 78 unexpected ')'."},
        });
    }
}
