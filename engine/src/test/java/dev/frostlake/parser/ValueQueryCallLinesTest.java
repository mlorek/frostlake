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
 * The lines of a call given up in a query that is a value: in a select list the query's SELECT and then its first
 * item's call's parenthesis, wherever the fault stands; compared in a WHERE or a HAVING the first token in that
 * call; as an IN's list the token past it. LAG, LEAD and NTH_VALUE refuse a quantifier before anything after it, a
 * null treatment written twice is refused at the second, a relation's star given a name is named with its dot,
 * and APPROX_PERCENTILE echoes a subquery as the query its plan runs (all live-verified).
 */
public class ValueQueryCallLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void callLinesAndThePercentileEcho() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT)");
        engine.execute("INSERT INTO rt VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE rt2 (n INT)");
        engine.execute("INSERT INTO rt2 VALUES (1), (NULL), (3)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 18 unexpected '('.",
            "SELECT (SELECT SUM(n IGNORE NULLS) FROM rt2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 18 unexpected '('.",
            "SELECT (SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2 LIMIT 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected '('.",
            "SELECT (SELECT COUNT(x => *, 1) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 18 unexpected '('.",
            "SELECT (SELECT ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 18 unexpected '('.",
            "SELECT (SELECT ABS(1) 2 3)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 18 unexpected '('.",
            "SELECT (SELECT ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected '('.",
            "SELECT (SELECT UPPER('a' 'b') FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 22 unexpected '('.",
            "SELECT 1 + (SELECT SUM(n IGNORE NULLS) FROM rt2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 39 unexpected 'n'.",
            "SELECT * FROM rt WHERE n = (SELECT SUM(n IGNORE NULLS) FROM rt2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'IGNORE'.",
            "SELECT LAG(n) IGNORE NULLS IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'DISTINCT'. | "
                + "syntax error line 1 at position 10 unexpected '('.",
            "SELECT LAG(DISTINCT n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'DISTINCT'. | "
                + "syntax error line 1 at position 11 unexpected '('.",
            "SELECT LEAD(DISTINCT n) OVER (ORDER BY n) FROM rt2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'DISTINCT'. | "
                + "syntax error line 1 at position 16 unexpected '('.",
            "SELECT NTH_VALUE(DISTINCT n, 1) OVER (ORDER BY n) FROM rt2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'ALL'. | syntax "
                + "error line 1 at position 10 unexpected '('.",
            "SELECT LAG(ALL n) OVER (ORDER BY n) FROM rt2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '*'. | syntax "
                + "error line 1 at position 20 unexpected '.'.",
            "SELECT COUNT(x => rt.*) FROM rt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '*'. | syntax "
                + "error line 1 at position 18 unexpected '.'.",
            "SELECT SUM(x => rt.*) FROM rt");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [(SELECT 0.5 AS \"0.5\" FROM (VALUES (null)) "
                + "DUAL)] for function 'APPROX_PERCENTILE', parameter 2: Percentile must be a constant numberic value "
                + "between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, (SELECT 0.5)) FROM rt2");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [(SELECT 0.5 AS \"Q\" FROM (VALUES (null)) "
                + "DUAL)] for function 'APPROX_PERCENTILE', parameter 2: Percentile must be a constant numberic value "
                + "between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, (SELECT 0.5 AS q)) FROM rt2");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [(SELECT (CAST(1 AS NUMBER(3,1))) + 0.5 AS \"1 + "
                + "0.5\" FROM (VALUES (null)) DUAL)] for function 'APPROX_PERCENTILE', parameter 2: Percentile must "
                + "be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, (SELECT 1 + 0.5)) FROM rt2");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [(SELECT MAX(RT.N) AS \"MAX(N)\" FROM RT AS RT)] "
                + "for function 'APPROX_PERCENTILE', parameter 2: Percentile must be a constant numberic value "
                + "between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, (SELECT MAX(n) FROM rt)) FROM rt2");
    }

    @Test
    public void valueQueriesByPlace() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT)");
        engine.execute("INSERT INTO rt VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE rt2 (n INT)");
        engine.execute("INSERT INTO rt2 VALUES (1), (NULL), (3)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 39 unexpected '1'.",
            "SELECT * FROM rt WHERE n = (SELECT ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected 'FROM'.",
            "SELECT * FROM rt WHERE n IN (SELECT SUM(n IGNORE NULLS) FROM rt2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 41 unexpected 'x'. | syntax error line 1 at position 47 unexpected "
                + "','.",
            "SELECT * FROM rt WHERE n > (SELECT COUNT(x => *, 1) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'IGNORE'.",
            "SELECT * FROM rt WHERE EXISTS (SELECT SUM(n IGNORE NULLS) FROM rt2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'.",
            "SELECT (SELECT 1 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 18 unexpected '('.",
            "SELECT (SELECT SUM(n) FROM rt rt2 rt3)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 39 unexpected '1'.",
            "SELECT * FROM rt WHERE n = (SELECT ABS(1) 2 3)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 51 unexpected 'n'.",
            "SELECT n FROM rt GROUP BY n HAVING n = (SELECT SUM(n IGNORE NULLS) FROM rt2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected '('.",
            "SELECT (SELECT UPPER(LOWER('a' 'b')) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 18 unexpected '('.",
            "SELECT (SELECT ABS(1 2) AS q)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 27 unexpected '('.",
            "SELECT (SELECT DISTINCT ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 21 unexpected '('.",
            "SELECT n, (SELECT SUM(n IGNORE NULLS) FROM rt2) FROM rt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'.",
            "SELECT * FROM rt WHERE n = (SELECT 1 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected '1'.",
            "SELECT * FROM rt WHERE (SELECT ABS(1 2)) = n");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 49 unexpected '1'.",
            "SELECT * FROM rt WHERE n = 1 AND n = (SELECT ABS(1 2))");
    }
}
