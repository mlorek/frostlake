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
 * How many statements the text of an EXECUTE IMMEDIATE may hold: as many as the count in force asks — one by default,
 * one inside a script of several, the session's inside a block (ExecuteImmediateSessionCountTest) — a block being one
 * statement; a text of none — blank, comments or semicolons alone — is the empty statement to a request and a
 * statement that may not run to a block (live-verified).
 */
public class ExecuteImmediateTextCountTest extends MeasuredCellsTestSupport {

    @Test
    public void aTextOfSeveralStatements() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE 'SELECT 1; SELECT nosuch FROM t'");
        assertAnswer(
            "REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE 'SELECT 1;'");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE 'SELECT 1; '");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE 'SELECT 1;;'");
        assertAnswer(
            "REFUSED Actual statement count 3 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE 'SELECT 1; SELECT 2; SELECT 3'");
        assertAnswer(
            "REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE $$SELECT 1; SELECT 2$$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE 'BEGIN SELECT 1; SELECT 2; END'");
        assertAnswer(
            "REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE 'BEGIN SELECT 1; END; SELECT 2'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
            "EXECUTE IMMEDIATE 'SELECT 1 x y; SELECT 2'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'.",
            "EXECUTE IMMEDIATE 'SELECT 1; SELECT 1 x y'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Actual statement "
                + "count 2 did not match the desired statement count 1.",
            "BEGIN EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Actual statement "
                + "count 2 did not match the desired statement count 1.",
            "BEGIN EXECUTE IMMEDIATE 'SELECT 1; SELECT nosuch FROM t'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Actual statement "
                + "count 2 did not match the desired statement count 1.",
            "BEGIN LET s := 'SELECT 1; SELECT 2'; EXECUTE IMMEDIATE :s; END");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"EXECUTE IMMEDIATE 'SELECT 1; S...\" (at line 1, position 10). | Actual statement count 2 did "
                + "not match the desired statement count 1. in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'");
        assertAnswer(
            "1",
            "SELECT 1; EXECUTE IMMEDIATE 'SELECT 1'");
        assertAnswer(
            "REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE 'INSERT INTO t VALUES (7, 7); INSERT INTO t VALUES (6, 6)'");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM t WHERE a IN (6, 7)");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE '/* c */ SELECT 1; -- d'");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE 'SELECT 1; /* c */'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'.",
            "EXECUTE IMMEDIATE 'SELECT 1; SELECT 2' USING (1)");
    }

    @Test
    public void aTextFromAVariable() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "Statement executed successfully.",
            "SET s1 = 'SELECT 1; SELECT 2'");
        assertAnswer(
            "REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE $s1");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET s1");
    }

    @Test
    public void aTextOfNoStatement() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "EXECUTE IMMEDIATE ''");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "EXECUTE IMMEDIATE ' '");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "EXECUTE IMMEDIATE ';'");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "EXECUTE IMMEDIATE ';;'");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "EXECUTE IMMEDIATE '/* c */'");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "EXECUTE IMMEDIATE '-- c'");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "EXECUTE IMMEDIATE $$$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Executing NULL or "
                + "empty statement is not permitted.",
            "BEGIN EXECUTE IMMEDIATE ''; END");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"EXECUTE IMMEDIATE ''\" (at line 1, position 10). | SQL compilation error: | Empty SQL "
                + "statement. in SYSTEM$MULTISTMT at '    throw `Execution of multiple statements failed on "
                + "statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  "
                + "| SYSTEM$MULTISTMT line: 10",
            "SELECT 1; EXECUTE IMMEDIATE ''");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 2 unexpected 'SELECT'.",
            "EXECUTE IMMEDIATE '; SELECT 1'");
        assertAnswer(
            "REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE 'SELECT 1;; SELECT 2'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 19 : Executing NULL or "
                + "empty statement is not permitted.",
            "BEGIN LET s := ''; EXECUTE IMMEDIATE :s; END");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "EXECUTE IMMEDIATE ' ; '");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'NULL'.",
            "EXECUTE IMMEDIATE NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "EXECUTE IMMEDIATE ''");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "EXECUTE IMMEDIATE ';'");
    }
}
