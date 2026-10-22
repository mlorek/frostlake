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
 * A SQL function's block body: a template query that reads no table function is refused for that before the
 * statement's kind, and a VALUES written as a statement of the body is refused alone (all live-verified).
 */
public class FunctionBodyStatementsTest extends MeasuredCellsTestSupport {

    @Test
    public void blockBodies() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 'a' + "
                + "TRUE; END");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET y := 1; RETURN IFF(y = 1, 'a' + TRUE, 2); END");
        assertAnswer("Function FX successfully created.",
            "CREATE OR REPLACE FUNCTION fx(x INT) RETURNS INT AS BEGIN RETURN x + 'a' + TRUE; END");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 'a' + TRUE; END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("REFUSED Unsupported feature 'Table function must be used in the TEMPLATE sub-query'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TABLE z USING TEMPLATE (SELECT 1); RETURN 1; "
                + "END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'VALUES'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN VALUES (1); RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'VALUES'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN VALUES (1), (2); RETURN 1; END");
    }
}
