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
 * A SQL function whose block body holds an expression that does not compile: the function is created, and the
 * statement that calls it is refused with the expression's own compilation error, bare — the body compiles
 * with the statement calling it — where an expression that fails as it is evaluated is the body's uncaught
 * EXPRESSION_ERROR. A block that calls the function words the refusal as its own statement's
 * (live-verified).
 */
public class FunctionBodyCompileRefusalAtCallTest extends MeasuredCellsTestSupport {

    @Test
    public void aReturnThatDoesNotCompile() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 'a' + TRUE; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 'a' + TRUE; END$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN UPPER(1, 2); END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 0 | too many arguments for function [UPPER(1,"
                + " 2)] expected 1, got 2",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN UPPER(1, 2); END$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 0 | too many arguments for function [UPPER(1,"
                + " 2)] expected 1, got 2",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN nosuch; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH'",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1 / 0; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Division by zero",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN TO_NUMBER('x'); END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Numeric value 'x' "
                + "is not recognized",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET y := 'a' + TRUE; RETURN y; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
    }

    @Test
    public void branchesLaterLinesAndCallers() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN IF (TRUE) THEN RETURN 'a' + TRUE; END IF; RETURN "
                + "1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET y := 1; RETURN y + 'a'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : Numeric value 'a' "
                + "is not recognized",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCHFN(1)",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN NOSUCHFN(1); END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function F.",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS \nBEGIN\n  RETURN 'a' + TRUE;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 'a' + TRUE; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT 1, f() FROM t");
        answer(
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 'a' + TRUE; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 4 | Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)",
            "BEGIN RETURN f(); END");
    }

    @Test
    public void otherStatementsAndCallers() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 'a' + TRUE; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 4 | Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)",
            "BEGIN LET z := 1; RETURN f(); END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT f() + 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT a FROM t WHERE f() = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "BEGIN LET z := f(); RETURN z; END");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN f(); END");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN IF ('a' + TRUE) THEN RETURN 1; END IF; RETURN 2; "
                + "END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET y := 1; y := 'a' + TRUE; RETURN y; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET y INT := 'a' + TRUE; RETURN y; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 'a' + TRUE; EXCEPTION WHEN OTHER THEN "
                + "RETURN 5; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1 / 0; EXCEPTION WHEN OTHER THEN RETURN "
                + "5; END");
        assertAnswer(
            "5",
            "SELECT f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN WHILE ('a' + TRUE) DO RETURN 1; END WHILE; "
                + "RETURN 2; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CASE WHEN 'a' + TRUE THEN RETURN 1; ELSE RETURN "
                + "2; END CASE; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "SELECT f()");
    }
}
