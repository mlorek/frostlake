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
 * A statement that is no query written where a query or a name is read: in a FROM clause's brackets, as a select
 * item's bracketed value, inside a query used as a value or an EXISTS, in an INSERT's column list, and a VALUES
 * list with an empty row; and a bracketed source given a second alias (all live-verified).
 */
public class StatementWordPlacementLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void statementWords() {
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 43 unexpected ')'.",
            "INSERT INTO rt_ctas (DESCRIBE TABLE rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'.",
            "INSERT INTO rt_ctas (SHOW TABLES)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 26 unexpected 'DESCRIBE'.",
            "SELECT 1, (SELECT 1 FROM (DESCRIBE TABLE rt_ctas))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'INSERT'. | "
                + "syntax error line 1 at position 49 unexpected ')'.",
            "SELECT * FROM (INSERT INTO rt_ctas VALUES (1, 'a'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'INTO'. | syntax "
                + "error line 1 at position 28 unexpected 'VALUES'.",
            "SELECT (INSERT INTO rt_ctas VALUES (1, 'a'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 30 unexpected 'SET'.",
            "SELECT * FROM (UPDATE rt_ctas SET a = 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 30 unexpected 'SET'.",
            "SELECT * FROM (UPDATE rt_ctas SET a = 1) u");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'EXPLAIN'. | "
                + "syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT * FROM (EXPLAIN USING TEXT SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'. | "
                + "syntax error line 1 at position 24 unexpected ')'.",
            "SELECT * FROM (VALUES ())");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'. | "
                + "syntax error line 1 at position 24 unexpected ')'.",
            "SELECT * FROM (VALUES ()) v");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'. | "
                + "syntax error line 1 at position 28 unexpected ')'. | syntax error line 1 at position 29 unexpected "
                + "')'.",
            "SELECT * FROM (VALUES (1), ())");
        assertAnswer("REFUSED SQL compilation error: | duplicate alias 'x'", "SELECT * FROM (SHOW TABLES)x");
        assertAnswer("REFUSED SQL compilation error: | duplicate alias 'c'", "SELECT * FROM (CALL p) c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'VIEWS'.",
            "INSERT INTO rt_ctas (SHOW VIEWS)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'VIEW'.",
            "INSERT INTO rt_ctas (DESCRIBE VIEW rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DELETE'.",
            "SELECT * FROM (DELETE FROM rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'EXPLAIN'.",
            "SELECT * FROM (EXPLAIN SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "SELECT * FROM (SHOW TABLES) x y");
        assertAnswer("REFUSED SQL compilation error: | Object 'CALL' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE CALL.",
            "SELECT * FROM (CALL p)");
        assertAnswer("REFUSED SQL compilation error: | duplicate alias 'x'", "SELECT * FROM (SHOW VIEWS)x");
        assertAnswer("REFUSED SQL compilation error: | Object 'SHOW' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE SHOW.",
            "SELECT 1, (SELECT 1 FROM (SHOW TABLES))");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void moreStatementWords() {
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 30 unexpected 'SET'.",
            "SELECT * FROM (UPDATE rt_ctas SET a = 1 WHERE b = 'x')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'UPDATE'.",
            "SELECT * FROM (UPDATE rt_ctas r SET a = 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'INSERT'. | "
                + "syntax error line 1 at position 48 unexpected ')'.",
            "SELECT * FROM (INSERT INTO rt_ctas SELECT 1, 'a')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'INSERT'.",
            "SELECT * FROM (INSERT INTO rt_ctas (a) VALUES (1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'INSERT'. | "
                + "syntax error line 1 at position 49 unexpected ')'.",
            "SELECT * FROM (INSERT INTO rt_ctas VALUES (1, 'a'), (2, 'b'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'INSERT'.",
            "SELECT * FROM (INSERT INTO rt_ctas VALUES (1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'EXPLAIN'. | "
                + "syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT * FROM (EXPLAIN USING JSON SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 23 unexpected 'SET'.",
            "SELECT (UPDATE rt_ctas SET a = 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'DELETE'. | "
                + "syntax error line 1 at position 27 unexpected ')'.",
            "SELECT (DELETE FROM rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'INTO'. | syntax "
                + "error line 1 at position 28 unexpected 'SELECT'.",
            "SELECT (INSERT INTO rt_ctas SELECT 1, 'a')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'INTO'. | syntax "
                + "error line 1 at position 28 unexpected '('.",
            "SELECT (INSERT INTO rt_ctas (a) VALUES (1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 39 unexpected ')'.",
            "INSERT INTO rt_ctas (DESC TABLE rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'b'.",
            "INSERT INTO rt_ctas (a b)");
        assertAnswer("0", "INSERT INTO rt_ctas TABLE rt_ctas");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'TABLE'. | "
                + "syntax error line 1 at position 43 unexpected ')'.",
            "INSERT INTO rt_ctas (DESCRIBE TABLE rt_ctas) SELECT 1, 'a'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'.",
            "INSERT INTO rt_ctas (SHOW TABLES) SELECT 1, 'a'");
        assertAnswer("REFUSED SQL compilation error: | duplicate alias 'b'", "SELECT * FROM (rt_ctas a) b");
        assertAnswer("REFUSED SQL compilation error: | duplicate alias 'b'", "SELECT * FROM (rt_ctas AS a) AS b");
        assertAnswer("", "SELECT * FROM (rt_ctas) b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'.",
            "SELECT * FROM (VALUES (1, ))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'. | "
                + "syntax error line 1 at position 27 unexpected '1'. | syntax error line 1 at position 29 unexpected "
                + "')'.",
            "SELECT * FROM (VALUES (), (1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'. | "
                + "syntax error line 1 at position 24 unexpected ')'.",
            "SELECT * FROM (VALUES ()) JOIN rt_ctas ON TRUE");
        assertAnswer("REFUSED SQL compilation error: | duplicate alias 'x'", "SELECT * FROM (SHOW TABLES) AS x");
        assertAnswer("REFUSED SQL compilation error: | duplicate alias 'u'", "SELECT * FROM (nosuch t) u");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 72 unexpected ')'.",
            "SELECT * FROM rt_ctas WHERE a IN (SELECT 1 FROM (DESCRIBE TABLE rt_ctas))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'DESCRIBE'. | "
                + "syntax error line 1 at position 61 unexpected ')'.",
            "SELECT 1 WHERE EXISTS (SELECT 1 FROM (DESCRIBE TABLE rt_ctas))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'MERGE'.",
            "SELECT * FROM (MERGE INTO rt_ctas USING rt_ctas ON TRUE WHEN MATCHED THEN DELETE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'TRUNCATE'.",
            "SELECT * FROM (TRUNCATE TABLE rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'USING'.",
            "SELECT (EXPLAIN USING TEXT SELECT 1)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
