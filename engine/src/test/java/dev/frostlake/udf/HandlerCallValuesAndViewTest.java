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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A function a Java handler answers, where a statement calls it: in a VALUES row it is refused wherever it sits in
 * the item, the item echoed as the plan prints it, {@code Invalid expression [(EXTENSION_FUNC_V(1)) + 1] in VALUES
 * clause}, a whole number for a FLOAT parameter written as that FLOAT and a NULL as {@code null}, where a SQL
 * function's call is taken; a view read twice in one statement keeps its calls apart for each reference, as two arms
 * of a UNION ALL and two scalar subqueries do (live-verified).
 */
public class HandlerCallValuesAndViewTest extends MeasuredCellsTestSupport {

    @Test
    public void aHandlersCallInValuesAndInAView() {
        engine.execute("CREATE OR REPLACE FUNCTION jcnt(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "HANDLER = 'H.f' AS $$ class H { static int n = 0; public static int f(int x) { n = n + 1; "
            + "return n; } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION jdbl(x FLOAT) RETURNS FLOAT LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "HANDLER = 'H.f' AS $$ class H { public static double f(double x) { return x * 2; } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION jtxt(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
            + "'11' HANDLER = 'H.f' AS $$ class H { public static String f(String x) { return x + x; } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION jtwo(x INT, y VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' HANDLER = 'H.f' AS $$ class H { public static String f(int x, String "
            + "y) { return y + x; } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sdbl(x INT) RETURNS INT AS 'x * 2'");
        engine.execute("CREATE OR REPLACE TABLE ct (v INT)");
        engine.execute("INSERT INTO ct VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE ct2 (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE VIEW jv AS SELECT jcnt(1) AS x");
        assertCells(new String[][] {
            {"INSERT INTO ct2 (a) VALUES (jcnt(1))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(1)] in VALUES clause"},
            {"INSERT INTO ct2 (a) VALUES (jdbl(1))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(1.0)] in VALUES clause"},
            {"INSERT INTO ct2 (b) VALUES (jtxt('a'))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V('a')] in VALUES clause"},
            {"INSERT INTO ct2 (b) VALUES (jtwo(1, 'a'))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(1, 'a')] in VALUES clause"},
            {"INSERT INTO ct2 (a) VALUES (jcnt(1) + 1)",
                "REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1)) + 1] in VALUES clause"},
            {"INSERT INTO ct2 (b) VALUES (UPPER(jtxt('a')))",
                "REFUSED SQL compilation error: | Invalid expression [UPPER(EXTENSION_FUNC_V('a'))] in VALUES clause"},
            {"INSERT INTO ct2 (a) VALUES (sdbl(1))", "1"},
            {"INSERT INTO ct2 VALUES (jcnt(1), 'x'), (jcnt(1), 'y')",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(1)] in VALUES clause"},
            {"INSERT INTO ct2 (a) VALUES (jdbl(1.5))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(1.5)] in VALUES clause"},
            {"INSERT INTO ct2 (b) VALUES (jtxt(NULL))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(null)] in VALUES clause"},
            {"INSERT INTO ct2 (a) VALUES (test_schema.jcnt(1))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(1)] in VALUES clause"},
            {"SELECT COUNT(*) FROM ct2", "1"},
            {"SELECT a.x, b.x FROM jv a, jv b", "1|1"},
            {"SELECT a.x, b.x FROM jv a, jv b", "1|1"},
            {"SELECT x FROM jv UNION ALL SELECT x FROM jv", "1 ;; 1"},
            {"SELECT (SELECT x FROM jv), (SELECT x FROM jv)", "1|1"},
            {"SELECT v FROM ct WHERE EXISTS (SELECT jcnt(1)) ORDER BY 1", "1 ;; 2 ;; 3"},
        });
    }
}
