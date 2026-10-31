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
 * A value query whose FROM reads a statement in its brackets, as a call's argument or in a clause: the SELECT,
 * the statement's word, then the token after it in a select list's call, or in a clause the ')' before the
 * outermost call's own (all live-verified).
 */
public class StatementWordArgumentLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void callArgument() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 29 unexpected 'DESCRIBE'. | syntax error line 1 at position 38 "
                + "unexpected 'TABLE'.",
            "SELECT UPPER((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 32 unexpected 'DESCRIBE'. | syntax error line 1 at position 41 "
                + "unexpected 'TABLE'.",
            "SELECT COALESCE((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)), 1)");
        assertAnswer("REFUSED SQL compilation error: | Object 'SHOW' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE SHOW.",
            "SELECT UPPER((SELECT 1 FROM (SHOW TABLES)))");
        assertAnswer("REFUSED SQL compilation error: | Object 'DESCRIBE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE DESCRIBE.",
            "SELECT UPPER((SELECT 1 FROM (DESCRIBE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'LIST'. | syntax error line 1 at position 32 "
                + "unexpected '@st'.",
            "SELECT ABS((SELECT 1 FROM (LIST @st)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 42 unexpected 'DESCRIBE'. | syntax error line 1 at position 65 "
                + "unexpected ')'.",
            "SELECT 1 FROM t WHERE ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas))) = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 29 unexpected 'DESCRIBE'. | syntax error line 1 at position 38 "
                + "unexpected 'TABLE'.",
            "SELECT UPPER((SELECT 1 FROM (DESCRIBE TABLE rt_ctas) x))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected 'DESCRIBE'.",
            "SELECT UPPER(UPPER((SELECT 1 FROM (DESCRIBE TABLE rt_ctas))))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 29 unexpected 'SHOW'. | syntax error line 1 at position 34 "
                + "unexpected 'TABLES'.",
            "SELECT UPPER((SELECT 1 FROM (SHOW TABLES IN SCHEMA s)))");
    }

    @Test
    public void morePlaces() {
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 38 unexpected 'DESCRIBE'.",
            "SELECT 1 FROM t WHERE (SELECT 1 FROM (DESCRIBE TABLE rt_ctas)) = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 46 unexpected 'DESCRIBE'. | syntax error line 1 at position 70 "
                + "unexpected ')'.",
            "SELECT 1 FROM t WHERE ABS(ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)))) = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected 'DESCRIBE'. | syntax error line 1 at position 68 "
                + "unexpected ')'.",
            "SELECT 1 FROM t ORDER BY ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 31 unexpected 'DESCRIBE'. | syntax error line 1 at position 40 "
                + "unexpected 'TABLE'.",
            "SELECT 1 + ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'DESC'. | syntax error line 1 at position 32 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM (DESC TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'EXPLAIN'. | syntax error line 1 at position 35 "
                + "unexpected 'USING'.",
            "SELECT ABS((SELECT 1 FROM (EXPLAIN USING TEXT SELECT 1)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'UPDATE'. | syntax error line 1 at position 42 "
                + "unexpected 'SET'.",
            "SELECT ABS((SELECT 1 FROM (UPDATE rt_ctas SET a = 1)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'CALL'. | syntax error line 1 at position 32 "
                + "unexpected 'p'.",
            "SELECT ABS((SELECT 1 FROM (CALL p())))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'DESCRIBE'. | syntax error line 1 at position 36 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas) JOIN t ON TRUE))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'DESCRIBE'. | syntax error line 1 at position 36 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas))) FROM t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'DESCRIBE'. | syntax error line 1 at position 36 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas))), 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 42 unexpected 'DESCRIBE'. | syntax error line 1 at position 65 "
                + "unexpected ')'.",
            "SELECT 1 FROM t WHERE ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas))) = 1 AND 1 = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'VALUES'.",
            "SELECT ABS((SELECT 1 FROM (VALUES)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected 'DESCRIBE'. | syntax error line 1 at position 68 "
                + "unexpected ')'.",
            "SELECT 1 FROM t GROUP BY ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 32 unexpected 'DESCRIBE'. | syntax error line 1 at position 41 "
                + "unexpected 'TABLE'.",
            "SELECT (1 + ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas))))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'DESCRIBE'. | syntax error line 1 at position 36 "
                + "unexpected 'VIEW'.",
            "SELECT ABS((SELECT 1 FROM (DESCRIBE VIEW rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'SHOW'. | syntax error line 1 at position 32 "
                + "unexpected 'TERSE'.",
            "SELECT ABS((SELECT 1 FROM (SHOW TERSE TABLES)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'INSERT'. | syntax error line 1 at position 47 "
                + "unexpected 'VALUES'.",
            "SELECT ABS((SELECT 1 FROM (INSERT INTO rt_ctas VALUES (1, 'a'))))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'USE'. | syntax error line 1 at position 31 "
                + "unexpected 'SCHEMA'.",
            "SELECT ABS((SELECT 1 FROM (USE SCHEMA s)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected '('. | syntax error line 1 at position 37 unexpected "
                + "'TABLE'.",
            "SELECT ABS((SELECT 1 FROM ((DESCRIBE TABLE rt_ctas))))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 46 unexpected 'DESCRIBE'. | syntax error line 1 at position 69 "
                + "unexpected ')'.",
            "SELECT 1 FROM t WHERE 1 = ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 43 unexpected 'DESCRIBE'. | syntax error line 1 at position 66 "
                + "unexpected ')'.",
            "SELECT 1 FROM t HAVING ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas))) = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'DESCRIBE'.",
            "SELECT ABS((SELECT a FROM (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'DESCRIBE'. | syntax error line 1 at position 36 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas) WHERE TRUE))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'DESCRIBE'. | syntax error line 1 at position 36 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas))) x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 29 unexpected 'DESCRIBE'. | syntax error line 1 at position 38 "
                + "unexpected 'TABLE'.",
            "SELECT UPPER((SELECT 1 FROM (DESCRIBE TABLE rt_ctas))), ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 30 unexpected 'DESCRIBE'. | syntax error line 1 at position 39 "
                + "unexpected 'TABLE'.",
            "SELECT 1, ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 31 unexpected 'DESCRIBE'. | syntax error line 1 at position 40 "
                + "unexpected 'TABLE'.",
            "SELECT TO_CHAR((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)), '9')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'DESCRIBE'. | syntax error line 1 at position 36 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas.b)))");
    }
}
