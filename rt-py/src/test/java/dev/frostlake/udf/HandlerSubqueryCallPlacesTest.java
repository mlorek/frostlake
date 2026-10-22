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
 * A handler function's call in an uncorrelated subquery: a JavaScript function not declared IMMUTABLE is called
 * again for each outer row, its state kept, in an IN list, a scalar subquery, a select list's subquery, a
 * subquery over FROM and a UNION; an IMMUTABLE one and a Python one are called once (all live-verified).
 */
public class HandlerSubqueryCallPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void javaScriptCallsPerRow() {
        engine.execute("CREATE OR REPLACE TABLE ct (v INT)");
        engine.execute("INSERT INTO ct VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'globalThis.n = "
            + "(globalThis.n || 0) + 1; return globalThis.n;'");
        engine.execute("CREATE OR REPLACE FUNCTION jcnt(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "HANDLER = 'C.f' AS 'class C { static int n = 0; public static int f(int x) { return ++n; } }'");
        assertAnswer("1|1.0 ;; 2|2.0 ;; 3|3.0", "SELECT v, cnt(1) FROM ct WHERE v IN (SELECT cnt(1)) ORDER BY 1");
        assertAnswer("1|1.0 ;; 2|2.0 ;; 3|3.0", "SELECT v, cnt(1) FROM ct WHERE v IN (SELECT cnt(1)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT v FROM ct WHERE v IN (SELECT cnt(1)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT v FROM ct WHERE v IN (SELECT cnt(1)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT v FROM ct WHERE v = (SELECT cnt(1)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT v FROM ct WHERE v NOT IN (SELECT cnt(1) + 1) ORDER BY 1");
    }

    @Test
    public void callPlacesAcrossLanguages() {
        engine.execute("CREATE OR REPLACE TABLE ct (v INT)");
        engine.execute("INSERT INTO ct VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'globalThis.n = "
            + "(globalThis.n || 0) + 1; return globalThis.n;'");
        engine.execute("CREATE OR REPLACE FUNCTION icnt2(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT IMMUTABLE AS "
            + "'globalThis.n = (globalThis.n || 0) + 1; return globalThis.n;'");
        engine.execute("CREATE OR REPLACE FUNCTION pcnt2(x FLOAT) RETURNS FLOAT LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\nn = 0\ndef f(x):\n    global n\n    n += 1\n    return n\n$$");
        assertAnswer("1|1.0 ;; 2|2.0 ;; 3|3.0", "SELECT v, (SELECT cnt(1)) FROM ct ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT v FROM ct WHERE v IN (SELECT cnt(1) FROM (SELECT 1)) ORDER BY 1");
        assertAnswer("1", "SELECT v FROM ct WHERE v IN (SELECT icnt2(1)) ORDER BY 1");
        assertAnswer("1", "SELECT v FROM ct WHERE v IN (SELECT pcnt2(1)) ORDER BY 1");
        assertAnswer("1", "SELECT v FROM ct WHERE v IN (SELECT pcnt2(1)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT v FROM ct WHERE v > (SELECT cnt(1)) - 1 ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT v FROM ct WHERE v IN (SELECT cnt(1) UNION ALL SELECT 10) ORDER BY 1");
        assertAnswer("3", "SELECT COUNT(*) FROM ct WHERE v IN (SELECT cnt(1))");
    }
}
