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
 * A fault inside a query used as a value, past the query's own SELECT: a query that opens with a call names the
 * call's first token wherever it is read - a call's argument, a QUALIFY, an ORDER BY, a GROUP BY, an UPDATE's SET,
 * a WHERE compared or not - and a fault inside a later item's call names the query's own parenthesis in a select
 * list, the FROM past the call elsewhere; a null treatment inside a call nested in FIRST_VALUE or LAST_VALUE names
 * their parenthesis, and FIRST_VALUE takes the treatment after a call that ends its arguments (all live-verified).
 */
public class ValueQueryCallTailLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void valueQueryCalls() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT)");
        engine.execute("INSERT INTO rt VALUES (1), (2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 24 unexpected '2'. | syntax error line 1 at position 34 unexpected "
                + "')'.",
            "SELECT (SELECT n, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 24 unexpected '2'. | syntax error line 1 at position 26 unexpected "
                + "')'.",
            "SELECT (SELECT n, ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected '2'. | syntax error line 1 at position 37 unexpected "
                + "')'.",
            "SELECT (SELECT n, n, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 28 unexpected ''b''. | syntax error line 1 at position 40 "
                + "unexpected ')'.",
            "SELECT (SELECT n, UPPER('a' 'b') FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 24 unexpected '2'. | syntax error line 1 at position 46 unexpected "
                + "')'.",
            "SELECT (SELECT n, ABS(1 2) FROM rt WHERE n = 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'. | syntax error line 1 at position 47 unexpected "
                + "'FROM'.",
            "SELECT * FROM rt WHERE n = (SELECT n, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 25 unexpected '1'.",
            "SELECT UPPER((SELECT ABS(1 2)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 37 unexpected '1'.",
            "SELECT n FROM rt QUALIFY (SELECT ABS(1 2)) = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 38 unexpected '1'.",
            "SELECT n FROM rt ORDER BY (SELECT ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 38 unexpected '1'.",
            "SELECT n FROM rt GROUP BY (SELECT ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 30 unexpected '1'.",
            "UPDATE rt SET n = (SELECT ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'.",
            "SELECT * FROM VALUES ((SELECT ABS(1 2)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'DISTINCT'.",
            "SELECT COUNT(x => DISTINCT n FROM rt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT LAG(n IGNORE x) OVER (ORDER BY n) FROM rt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'IGNORE'. | "
                + "syntax error line 1 at position 38 unexpected ')'.",
            "SELECT FIRST_VALUE(ABS(n IGNORE NULLS)) OVER (ORDER BY n) FROM rt");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void valueQueryCallPlaces() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT)");
        engine.execute("INSERT INTO rt VALUES (1), (2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 24 unexpected '2'. | syntax error line 1 at position 34 unexpected "
                + "')'.",
            "SELECT (SELECT n, ABS(1 2) FROM rt) FROM rt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected '2'. | syntax error line 1 at position 37 unexpected "
                + "')'.",
            "SELECT 1, (SELECT n, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 24 unexpected '2'. | syntax error line 1 at position 34 unexpected "
                + "')'.",
            "SELECT (SELECT n, ABS(1 2) FROM rt) AS q");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'.",
            "SELECT * FROM rt WHERE n = (SELECT n, ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'. | syntax error line 1 at position 47 unexpected "
                + "'FROM'. | syntax error line 1 at position 66 unexpected ')'.",
            "SELECT * FROM rt WHERE n = (SELECT n, ABS(1 2) FROM rt WHERE n = 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 43 unexpected '2'. | syntax error line 1 at position 46 unexpected "
                + "'FROM'.",
            "SELECT n FROM rt ORDER BY (SELECT n, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 30 unexpected '2'. | syntax error line 1 at position 33 unexpected "
                + "'FROM'. | syntax error line 1 at position 40 unexpected ')'.",
            "SELECT UPPER((SELECT n, ABS(1 2) FROM rt))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 37 unexpected '1'.",
            "SELECT n FROM rt QUALIFY (SELECT ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected '1'.",
            "SELECT * FROM rt WHERE (SELECT ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 38 unexpected '1'.",
            "SELECT n FROM rt ORDER BY (SELECT ABS(1 2)) DESC");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 41 unexpected '1'.",
            "SELECT n FROM rt GROUP BY n, (SELECT ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 30 unexpected '1'.",
            "UPDATE rt SET n = (SELECT ABS(1 2)) WHERE n = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 28 unexpected '1'. | syntax error line 1 at position 36 unexpected "
                + "')'.",
            "SELECT COALESCE((SELECT ABS(1 2)), 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 38 unexpected 'n'.",
            "SELECT n FROM rt ORDER BY (SELECT SUM(n IGNORE NULLS) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT LAG(n IGNORE) OVER (ORDER BY n) FROM rt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'RESPECT'.",
            "SELECT LAG(n RESPECT x) OVER (ORDER BY n) FROM rt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'IGNORE'. | "
                + "syntax error line 1 at position 39 unexpected ')'.",
            "SELECT LAST_VALUE(UPPER(n IGNORE NULLS)) OVER (ORDER BY n) FROM rt");
        assertAnswer("1 ;; 1", "SELECT FIRST_VALUE(ABS(n) IGNORE NULLS) OVER (ORDER BY n) FROM rt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'IGNORE'. | "
                + "syntax error line 1 at position 42 unexpected ')'.",
            "SELECT FIRST_VALUE(ABS(n IGNORE NULLS) + 1) OVER (ORDER BY n) FROM rt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'IGNORE'. | "
                + "syntax error line 1 at position 41 unexpected ')'.",
            "SELECT FIRST_VALUE(ABS(n IGNORE NULLS), 1) OVER (ORDER BY n) FROM rt");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
