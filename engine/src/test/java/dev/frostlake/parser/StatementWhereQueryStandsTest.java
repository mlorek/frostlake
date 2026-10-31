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
 * A DESCRIBE or a SHOW written in the brackets a query stands in: in a predicate's or a CTAS's brackets the one line,
 * in a select item's brackets the word after it, in a common table expression the word after it alone, and in a
 * FROM - after FROM or a JOIN, in one pair of brackets or more - the word itself (all live-verified).
 */
public class StatementWhereQueryStandsTest extends MeasuredCellsTestSupport {

    @Test
    public void statementsInBrackets() {
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 17 unexpected 'TABLE'.",
            "SELECT (DESCRIBE TABLE rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'DESCRIBE'.",
            "SELECT 1 WHERE EXISTS (DESCRIBE TABLE rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'TABLE'.",
            "SELECT * FROM rt_ctas WHERE a IN (DESCRIBE TABLE rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'TABLE'.",
            "WITH w AS (DESCRIBE TABLE rt_ctas) SELECT * FROM w");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'DESCRIBE'.",
            "CREATE OR REPLACE TABLE rt_x AS (DESCRIBE TABLE rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'DESCRIBE'.",
            "SELECT * FROM ((DESCRIBE TABLE rt_ctas))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DESCRIBE'.",
            "SELECT * FROM rt_ctas JOIN (DESCRIBE TABLE rt_ctas) d ON TRUE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'TABLES'.",
            "SELECT (SHOW TABLES)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SHOW'.",
            "SELECT 1 WHERE EXISTS (SHOW TABLES)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'TABLES'.",
            "WITH w AS (SHOW TABLES) SELECT * FROM w");
        assertAnswer("REFUSED SQL compilation error: | Object 'SHOW' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE SHOW.",
            "SELECT * FROM ((SHOW TABLES))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DELETE'.",
            "SELECT * FROM (DELETE FROM rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "SELECT * FROM (SHOW TABLES) x y");
        assertAnswer("REFUSED SQL compilation error: | Object 'SHOW' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE SHOW.",
            "SELECT * FROM (SHOW TABLES)");
        assertAnswer("REFUSED SQL compilation error: | Object 'CALL' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE CALL.",
            "SELECT * FROM (CALL p)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'd'.",
            "SELECT * FROM (CALL p) c d");
        assertAnswer("REFUSED SQL compilation error: | Object 'SHOW' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE SHOW.",
            "SELECT * FROM (SHOW) x");
        assertAnswer("REFUSED SQL compilation error: | Object 'DESCRIBE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE DESCRIBE.",
            "SELECT * FROM (DESCRIBE) x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 17 unexpected 'TABLE'.",
            "SELECT (DESCRIBE TABLE rt_ctas) x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'TABLE'.",
            "SELECT 1 WHERE (DESCRIBE TABLE rt_ctas) = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'DESCRIBE'.",
            "SELECT * FROM rt_ctas WHERE EXISTS (DESCRIBE TABLE rt_ctas) AND a = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'TABLES'.",
            "WITH w AS (SHOW TABLES) SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'TABLE'.",
            "WITH w AS (DESCRIBE TABLE rt_ctas), v AS (SELECT 1) SELECT * FROM v");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'EXPLAIN'.",
            "SELECT * FROM (EXPLAIN SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'USE'.",
            "SELECT * FROM (USE DATABASE test_db)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'ALTER'.",
            "SELECT * FROM (ALTER TABLE rt_ctas ADD COLUMN c INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SET'.",
            "SELECT * FROM (SET x = 1)");
        assertAnswer("REFUSED SQL compilation error: | Object 'BEGIN' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE BEGIN.",
            "SELECT * FROM (BEGIN)");
    }
}
