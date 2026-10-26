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
 * A fault inside a query used as a value: a literal written as a select item's alias, with the query's ')' after it
 * and the AS before it after an unaliased item, and a call in the ANSI FROM form read as a query's first-item call
 * (all live-verified).
 */
public class ValueQueryLiteralLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void valueQueryFaults() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected '1'. | syntax error line 1 at position 24 unexpected "
                + "')'.",
            "SELECT (SELECT 1 AS 1, 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected 'AS'. | syntax error line 1 at position 23 "
                + "unexpected '2'.",
            "SELECT (SELECT 1, 2 AS 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 16 unexpected '('. | syntax error line 1 at position 24 unexpected "
                + "'('.",
            "SELECT (SELECT ((EXTRACT('wks' FROM d))) FROM t) FROM t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 16 unexpected '('. | syntax error line 1 at position 24 unexpected "
                + "'('.",
            "SELECT (SELECT ((EXTRACT('wks' FROM d))) FROM t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 22 unexpected '('.",
            "SELECT (SELECT EXTRACT('wks' FROM d) FROM t) FROM t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 16 unexpected 'EXTRACT'. | syntax error line 1 at position 23 "
                + "unexpected '('.",
            "SELECT (SELECT (EXTRACT('wks' FROM d)) FROM t) FROM t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FROM'. | syntax "
                + "error line 1 at position 27 unexpected ')'.",
            "SELECT EXTRACT('wks' FROM d) FROM t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FROM'. | syntax "
                + "error line 1 at position 27 unexpected ')'.",
            "SELECT EXTRACT('wks' FROM d)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 22 unexpected '('.",
            "SELECT (SELECT EXTRACT('year' FROM d) FROM t) FROM t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 57 unexpected 'FROM'.",
            "SELECT * FROM t WHERE a IN (SELECT EXTRACT('wks' FROM d) FROM t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 18 unexpected '('.",
            "SELECT (SELECT ABS(EXTRACT('wks' FROM d)) FROM t) FROM t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected '1'.",
            "SELECT (SELECT 1 AS 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected '1'. | syntax error line 1 at position 27 unexpected "
                + "')'.",
            "SELECT (SELECT 1 AS 1, 2, 3)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected 'AS'. | syntax error line 1 at position 23 "
                + "unexpected '2'. | syntax error line 1 at position 27 unexpected ')'.",
            "SELECT (SELECT 1, 2 AS 2, 3)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected '1'. | syntax error line 1 at position 28 unexpected "
                + "')'.",
            "SELECT (SELECT 1 AS 1 FROM t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected 'AS'. | syntax error line 1 at position 23 "
                + "unexpected '2'. | syntax error line 1 at position 31 unexpected ')'.",
            "SELECT (SELECT 1, 2 AS 2 FROM t)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected ''x''. | syntax error line 1 at position 26 "
                + "unexpected ')'.",
            "SELECT (SELECT 1 AS 'x', 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected '1.5'. | syntax error line 1 at position 26 "
                + "unexpected ')'.",
            "SELECT (SELECT 1 AS 1.5, 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'.",
            "SELECT 1 AS 1, 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '2'.",
            "SELECT 1, 2 AS 2");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
