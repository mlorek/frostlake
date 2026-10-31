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
 * The lines after the SELECT of a query used as a call's argument or compared in a WHERE, whose FROM list reads a
 * statement in its parentheses after a comma, or a JOIN's bracket does (all live-verified).
 */
public class StatementWordAfterCommaLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void listedWords() {
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected 'TABLE'. | syntax error line 1 at position 45 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'DESCRIBE'.",
            "SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 32 unexpected 'DESCRIBE'.",
            "SELECT (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 66 unexpected 'TABLE'.",
            "SELECT 1 FROM rt_ctas WHERE 1 = (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas))");
        assertAnswer("REFUSED SQL compilation error: | Object 'SHOW' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE SHOW.",
            "SELECT 1 FROM rt_ctas, (SHOW TABLES)");
        assertAnswer("REFUSED SQL compilation error: | Object 'SHOW' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE SHOW.",
            "SELECT ABS((SELECT 1 FROM rt_ctas, (SHOW TABLES)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'EXPLAIN'.",
            "SELECT 1 FROM rt_ctas, (EXPLAIN SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'DESCRIBE'.",
            "SELECT 1 FROM rt_ctas, rt_ctas t2, (DESCRIBE TABLE rt_ctas)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 57 unexpected 'TABLE'. | syntax error line 1 at position 57 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM rt_ctas, rt_ctas t2, (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 34 unexpected 'JOIN'.",
            "SELECT ABS((SELECT 1 FROM rt_ctas JOIN (DESCRIBE TABLE rt_ctas) ON TRUE))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected 'DESCRIBE'. | syntax error line 1 at position 36 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM (DESCRIBE TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 36 unexpected 'DESCRIBE'.",
            "SELECT 1 + (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 50 unexpected 'TABLE'. | syntax error line 1 at position 50 "
                + "unexpected 'TABLE'.",
            "SELECT COALESCE((SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas)), 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 41 unexpected 'TABLE'. | syntax error line 1 at position 41 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM rt_ctas, (DESC TABLE rt_ctas)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 36 unexpected 'UPDATE'. | syntax error line 1 at position 51 "
                + "unexpected 'SET'.",
            "SELECT ABS((SELECT 1 FROM rt_ctas, (UPDATE rt_ctas SET a = 1)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected 'TABLE'. | syntax error line 1 at position 45 "
                + "unexpected 'TABLE'.",
            "SELECT ABS((SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas) x))");
    }
}
