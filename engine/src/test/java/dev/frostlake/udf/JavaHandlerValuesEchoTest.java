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
 * A Java function's call refused in a VALUES clause, as the plan prints it: a whole call added to or taken from
 * a decimal is cast to the decimal's scale, multiplied it is not, and an argument of constants is folded (all
 * live-verified).
 */
public class JavaHandlerValuesEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void javaValuesEchoes() {
        engine.execute("CREATE OR REPLACE TABLE ct2 (a FLOAT)");
        engine.execute("CREATE OR REPLACE FUNCTION jcnt(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "HANDLER = 'C.f' AS 'class C { static int n = 0; public static int f(int x) { return ++n; } }'");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(CAST(EXTENSION_FUNC_V(1) AS NUMBER(38,"
                + "2))) + 1.25] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (jcnt(1) + 1.25)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1)) * 1.5] in VALUES "
                + "clause",
            "INSERT INTO ct2 (a) VALUES (jcnt(1) * 1.5)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1.5 + (CAST(EXTENSION_FUNC_V(1) AS "
                + "NUMBER(38,1)))] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (1.5 + jcnt(1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(2)] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (jcnt(1 + 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(EXTENSION_FUNC_V(1)) + 1] in VALUES clause",
            "INSERT INTO ct2 (a) VALUES (jcnt(1) + 1)");
    }
}
