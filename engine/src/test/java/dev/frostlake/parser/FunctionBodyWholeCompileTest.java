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
 * A SQL function's block body, compiled whole with the statement that calls it, its names written out as binds;
 * TABLE, a pipe and a manual RECLUSTER as a block's statements; and a quoted body that reads as no block, read on
 * by its frame (all live-verified).
 */
public class FunctionBodyWholeCompileTest extends MeasuredCellsTestSupport {

    @Test
    public void functionBodies() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 'a' + "
                + "TRUE; END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN IF (FALSE) THEN RETURN 'a' + TRUE; END IF; RETURN "
                + "1; END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1; RETURN 'a' + TRUE; END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET y := 1; RETURN IFF(y = 1, 'a' + TRUE, 2); END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for "
                + "function '+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET y := 1; LET z := 2; RETURN IFF(y = z, 'a' + "
                + "TRUE, 2); END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 49 | Invalid argument types for "
                + "function '+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET y := 1; RETURN IFF(TRUE, 'a' + TRUE, y); END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for "
                + "function '+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN IFF(TRUE, 'a' + TRUE, 2); END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for "
                + "function '+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer("Function FX successfully created.",
            "CREATE OR REPLACE FUNCTION fx(x INT) RETURNS INT AS BEGIN RETURN x + 'a' + TRUE; END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 25 | Invalid argument types for "
                + "function '+': (NUMBER(38,5), BOOLEAN)",
            "SELECT fx(1)");
        assertAnswer("Function FX successfully created.",
            "CREATE OR REPLACE FUNCTION fx(x INT) RETURNS INT AS BEGIN RETURN 'a' + TRUE + x; END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT fx(1)");
        assertAnswer("REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN TABLE t; RETURN 1; END");
        assertAnswer("REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT a FROM t ->> SELECT 1; RETURN 1; END");
        assertAnswer("REFUSED Unsupported statement type for Snowscript UDF: SHOW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SHOW TABLES ->> SELECT 1; RETURN 1; END");
        assertAnswer("REFUSED Unsupported statement type for Snowscript UDF: RECLUSTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t RECLUSTER; RETURN 1; END");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "7 unexpected 'RETURN'. | syntax error line 1 at position 16 unexpected ','.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN [1, ; END$$");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "7 unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 + ) ; END$$");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "7 unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected '1'. | syntax error line 1 "
                + "at position 21 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN (1)) + ; END$$");
        assertAnswer("1", "BEGIN TABLE t; RETURN 1; END;");
        assertAnswer("", "TABLE t");
        assertAnswer("1", "BEGIN SELECT 1 ->> SELECT 2; RETURN 1; END;");
        assertAnswer("Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN LET y := 1; "
                + "RETURN y + 'a' + TRUE; END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 25 | Invalid argument types for "
                + "function '+': (NUMBER(38,5), BOOLEAN)",
            "SELECT f()");
        assertAnswer("REFUSED Unsupported feature 'RECLUSTER'.", "ALTER TABLE t RECLUSTER");
        assertAnswer("REFUSED Unsupported feature 'RECLUSTER'.", "ALTER TABLE t RECLUSTER WHERE a > 1");
        assertAnswer("REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN IF (TRUE) THEN TABLE t; END IF; RETURN 1; END");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
