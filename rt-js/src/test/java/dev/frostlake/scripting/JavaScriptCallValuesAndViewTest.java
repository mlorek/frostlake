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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A JavaScript function's call in a VALUES row is refused, the item echoed as the plan prints it, {@code Invalid
 * expression [EXTENSION_FUNC_V(1.0)] in VALUES clause}, and nothing is written; a view that counts its calls, read
 * twice in one statement, counts apart for each reference (live-verified).
 */
public class JavaScriptCallValuesAndViewTest extends MeasuredCellsTestSupport {

    @Test
    public void aCallInValuesAndInAView() {
        engine.execute("CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'globalThis.n "
            + "= (globalThis.n || 0) + 1; return globalThis.n;'");
        engine.execute("CREATE OR REPLACE FUNCTION jsdbl(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return X * "
            + "2;'");
        engine.execute("CREATE OR REPLACE FUNCTION jstxt(s VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return "
            + "S + S;'");
        engine.execute("CREATE OR REPLACE TABLE ct (v INT)");
        engine.execute("INSERT INTO ct VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE ct2 (a FLOAT, b FLOAT, s VARCHAR)");
        engine.execute("CREATE OR REPLACE VIEW jsv AS SELECT cnt(1) AS x");
        assertCells(new String[][] {
            {"SELECT a.x, b.x FROM jsv a, jsv b", "1.0|1.0"},
            {"INSERT INTO ct2 (a, b) VALUES (cnt(1), cnt(1)), (cnt(1), cnt(1))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(1.0)] in VALUES clause"},
            {"INSERT INTO ct2 (a) VALUES (cnt(1))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(1.0)] in VALUES clause"},
            {"INSERT INTO ct2 (a) VALUES (jsdbl(2.5))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(2.5)] in VALUES clause"},
            {"INSERT INTO ct2 (s) VALUES (jstxt('a'))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V('a')] in VALUES clause"},
            {"INSERT INTO ct2 (a) VALUES (jsdbl(NULL))",
                "REFUSED SQL compilation error: | Invalid expression [EXTENSION_FUNC_V(null)] in VALUES clause"},
            {"SELECT COUNT(*) FROM ct2", "0"},
        });
    }
}
