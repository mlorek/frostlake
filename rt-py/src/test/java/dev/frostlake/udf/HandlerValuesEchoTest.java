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
 * A handler function's call refused in a VALUES clause, as the plan prints it: EXTENSION_FUNC_P for a function
 * declared IMMUTABLE, an argument of constants folded, in FLOAT arithmetic every constant the FLOAT it becomes
 * and an exact call cast to FLOAT, and a whole call beside a decimal cast to the decimal's scale (all
 * live-verified).
 */
public class HandlerValuesEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void valuesEchoes() {
        engine.execute("CREATE OR REPLACE TABLE ct2 (a FLOAT)");
        engine.execute("CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'globalThis.n = "
            + "(globalThis.n || 0) + 1; return globalThis.n;'");
        engine.execute("CREATE OR REPLACE FUNCTION jcnt(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "HANDLER = 'C.f' AS 'class C { static int n = 0; public static int f(int x) { return ++n; } }'");
        engine.execute("CREATE OR REPLACE FUNCTION icnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT IMMUTABLE AS "
            + "'return X;'");
        engine.execute("CREATE OR REPLACE FUNCTION pcnt(x FLOAT) RETURNS FLOAT LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(x):\n    return x\n$$");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) + 1.0] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) + 1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1.0 + (EXTENSION_FUNC_V(1.0))] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (1 + cnt(1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) * 2.0] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) * 2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) + 1.5] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) + 1.5)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) - 2.0] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) - (1 + 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) + "
                + "(EXTENSION_FUNC_V(2.0))] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) + cnt(2))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(2.0)] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1 + 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(1.0)] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (cnt(x => 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(CAST(EXTENSION_FUNC_V(1) AS NUMBER(38,"
                + "1))) + 1.5] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (jcnt(1) + 1.5)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_P(1.0)] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (icnt(1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_P(1.0)) + 1.0] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (icnt(1) + 1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(1.0)] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (pcnt(1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) + 1.0] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (pcnt(1) + 1)");
    }

    @Test
    public void moreValuesEchoes() {
        engine.execute("CREATE OR REPLACE TABLE ct2 (a FLOAT)");
        engine.execute("CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'globalThis.n = "
            + "(globalThis.n || 0) + 1; return globalThis.n;'");
        engine.execute("CREATE OR REPLACE FUNCTION jcnt(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "HANDLER = 'C.f' AS 'class C { static int n = 0; public static int f(int x) { return ++n; } }'");
        engine.execute("CREATE OR REPLACE FUNCTION icnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT IMMUTABLE AS "
            + "'return X;'");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(CAST(EXTENSION_FUNC_V(1) AS NUMBER(38,"
                + "2))) + 1.25] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (jcnt(1) + 1.25)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1)) * 1.5] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (jcnt(1) * 1.5)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1.5 + (CAST(EXTENSION_FUNC_V(1) AS "
                + "NUMBER(38,1)))] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (1.5 + jcnt(1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) + 6.0] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) + (2 * 3))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) + -1.0] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) + -1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) + 100.0] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) + 1e2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) / 2.0] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) / 2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) + "
                + "(CAST(EXTENSION_FUNC_V(1) AS FLOAT))] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) + jcnt(1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(2.5)] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (cnt(2.5))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_P(1.0)) + "
                + "(EXTENSION_FUNC_P(2.0))] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (icnt(1) + icnt(2))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(2)] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (jcnt(1 + 1))");
        assertAnswer("REFUSED SQL compilation error: | Unknown function JIMM.", "INSERT INTO ct2 (a) VALUES (jimm(1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1)) + 1] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (jcnt(1) + 1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [((EXTENSION_FUNC_V(1.0)) + 1.0) + 2.0] in "
                + "VALUES clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) + 1 + 2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1.0)) + 1.0] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (cnt(1) + 0.5 * 2)");
    }
}
