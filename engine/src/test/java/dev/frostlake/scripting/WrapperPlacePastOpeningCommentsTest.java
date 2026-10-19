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
 * Where the wrapper of an uncaught failure places the statement: a text the session sends is counted past the
 * comments it opens with — the first token after them is line 1, position 0 — for a statement that failed and
 * for an exception that was raised alike, while leading blanks count; a procedure's body keeps its comments
 * and is counted from where its text begins (live-verified).
 */
public class WrapperPlacePastOpeningCommentsTest extends MeasuredCellsTestSupport {

    @Test
    public void aStatementThatFailed() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ BEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "BEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 9 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "   BEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */\nBEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "-- c\nBEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ /* d */ BEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ BEGIN\n  EXECUTE IMMEDIATE 'SELECT 1 FROM';\nEND");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ BEGIN LET x := 1; EXECUTE IMMEDIATE 'SELECT 1 FROM'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ DECLARE q INT; BEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END");
    }

    @Test
    public void inAScriptOfSeveralStatements() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"EXECUTE IMMEDIATE 'SELECT 1 FR...\" (at line 1, position 18). | SQL compilation error: | syntax "
                + "error line 1 at position 13 unexpected '<EOF>'. in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "/* cc */ SELECT 1; /* d */ EXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"EXECUTE IMMEDIATE 'SELECT 1 FR...\" (at line 1, position 10). | SQL compilation error: | syntax "
                + "error line 1 at position 13 unexpected '<EOF>'. in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; EXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"EXECUTE IMMEDIATE 'SELECT 1 FR...\" (at line 1, position 18). | SQL compilation error: | syntax "
                + "error line 1 at position 13 unexpected '<EOF>'. in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; /* d */ EXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"EXECUTE IMMEDIATE 'SELECT 1 FR...\" (at line 2, position 8). | SQL compilation error: | syntax "
                + "error line 1 at position 13 unexpected '<EOF>'. in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "/* cc */ SELECT 1;\n/* d */ EXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"EXECUTE IMMEDIATE 'SELECT 1 FR...\" (at line 1, position 20). | SQL compilation error: | syntax "
                + "error line 1 at position 13 unexpected '<EOF>'. in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "/* cc */ SELECT 1; SELECT 2; EXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"EXECUTE IMMEDIATE 'SELECT 1 FR...\" (at line 1, position 12). | SQL compilation error: | syntax "
                + "error line 1 at position 13 unexpected '<EOF>'. in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "  /* c */   SELECT 1;   EXECUTE IMMEDIATE 'SELECT 1 FROM'");
    }

    @Test
    public void anExceptionThatWasRaised() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 44 : boom",
            "DECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 44 : boom",
            "/* c */ DECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 3 at position 2 : boom",
            "/* c */\nDECLARE e EXCEPTION (-20001, 'boom');\nBEGIN\n  RAISE e;\nEND");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 44 : boom",
            "-- c\nDECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 47 : boom",
            "   DECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 44 : boom",
            "/* c */ /* d */ DECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 59 : boom",
            "/* c */ DECLARE e EXCEPTION (-20001, 'boom'); BEGIN IF (TRUE) THEN RAISE e; END IF; END");
    }

    @Test
    public void otherFailures() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero",
            "BEGIN SELECT 1 / 0; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero",
            "/* c */ BEGIN SELECT 1 / 0; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric value 'a' "
                + "is not recognized",
            "BEGIN LET q INT := 'a'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric value 'a' "
                + "is not recognized",
            "/* c */ BEGIN LET q INT := 'a'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 27 : Numeric value 'a' "
                + "is not recognized",
            "/* c */ BEGIN LET q INT := 1; q := 'a'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 12 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ BEGIN BEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ BEGIN IF (TRUE) THEN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END IF; END");
    }

    @Test
    public void aProceduresBodyKeepsItsComments() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "Function PW1 successfully created.",
            "CREATE OR REPLACE PROCEDURE pw1() RETURNS INT LANGUAGE SQL AS\n$$\n/* c */ BEGIN EXECUTE IMMEDIATE "
                + "'SELECT 1 FROM'; END\n$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 14 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "CALL pw1()");
        assertAnswer(
            "Function PW2 successfully created.",
            "CREATE OR REPLACE PROCEDURE pw2() RETURNS INT LANGUAGE SQL AS\n$$\nBEGIN EXECUTE IMMEDIATE 'SELECT 1 "
                + "FROM'; END\n$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 6 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "CALL pw2()");
        assertAnswer(
            "Function PW3 successfully created.",
            "CREATE OR REPLACE PROCEDURE pw3() RETURNS INT LANGUAGE SQL AS\n$$\n/* c */\nDECLARE e EXCEPTION "
                + "(-20001, 'boom');\nBEGIN\n  RAISE e;\nEND\n$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 5 at position 2 : boom",
            "CALL pw3()");
        assertAnswer(
            "Function PW4 successfully created.",
            "CREATE OR REPLACE PROCEDURE pw4() RETURNS INT LANGUAGE SQL AS\n$$\nDECLARE e EXCEPTION (-20001, "
                + "'boom');\nBEGIN\n  RAISE e;\nEND\n$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 4 at position 2 : boom",
            "CALL pw4()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 14 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ CALL pw1()");
        assertAnswer(
            "PW1 successfully dropped.",
            "DROP PROCEDURE IF EXISTS pw1()");
        assertAnswer(
            "PW2 successfully dropped.",
            "DROP PROCEDURE IF EXISTS pw2()");
        assertAnswer(
            "PW3 successfully dropped.",
            "DROP PROCEDURE IF EXISTS pw3()");
        assertAnswer(
            "PW4 successfully dropped.",
            "DROP PROCEDURE IF EXISTS pw4()");
    }
}
