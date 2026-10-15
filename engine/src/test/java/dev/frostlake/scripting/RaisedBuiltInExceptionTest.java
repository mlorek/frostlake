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
 * RAISE STATEMENT_ERROR and RAISE EXPRESSION_ERROR: a script raises the two exceptions it has without
 * declaring them. Either carries the code -20000, the state P0001 and no message, is caught by its own name
 * and by OTHER, and reads "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6" when
 * nothing catches it; a procedure's caller catches it as the CALL's STATEMENT_ERROR, and a function's block
 * body raises and catches it as a block does. OTHER names no exception to raise (live-verified).
 */
public class RaisedBuiltInExceptionTest extends MeasuredCellsTestSupport {

    @Test
    public void raisedCaughtAndUncaught() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "statement -20000 P0001",
            "BEGIN RAISE STATEMENT_ERROR; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' "
                + "|| SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' "
                + "|| SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "expression -20000 P0001",
            "BEGIN RAISE EXPRESSION_ERROR; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' "
                + "|| SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' "
                + "|| SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | invalid identifier 'OTHER'",
            "BEGIN RAISE OTHER; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE "
                + "|| ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | invalid identifier 'NOSUCH_EXC'",
            "BEGIN RAISE nosuch_exc; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6",
            "BEGIN RAISE STATEMENT_ERROR; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 6",
            "BEGIN RAISE EXPRESSION_ERROR; END");
        assertAnswer(
            "null",
            "BEGIN RAISE STATEMENT_ERROR; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "null",
            "BEGIN RAISE EXPRESSION_ERROR; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6",
            "BEGIN RAISE STATEMENT_ERROR; EXCEPTION WHEN STATEMENT_ERROR THEN RAISE; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6",
            "BEGIN RAISE STATEMENT_ERROR; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'e'; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 |  Variable with name 'STATEMENT_ERROR' "
                + "declared twice.",
            "DECLARE STATEMENT_ERROR EXCEPTION (-20005, 'mine'); BEGIN RAISE STATEMENT_ERROR; RETURN 'none'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "e",
            "DECLARE e EXCEPTION (-20005, 'mine'); BEGIN RAISE e; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 's'; "
                + "WHEN e THEN RETURN 'e'; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 6 | RAISE without specifying exception only "
                + "allowed inside a handler.",
            "BEGIN RAISE; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' "
                + "' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; "
                + "WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6",
            "BEGIN RAISE STATEMENT_ERROR; RETURN 1; END");
        assertAnswer(
            "null",
            "BEGIN LET x := 1; RAISE EXPRESSION_ERROR; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || "
                + "' ' || SQLSTATE || ' ' || SQLERRM; END");
    }

    @Test
    public void codesHandlersAndNames() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "-20000",
            "BEGIN RAISE STATEMENT_ERROR; EXCEPTION WHEN OTHER THEN RETURN SQLCODE; END");
        assertAnswer(
            "P0001",
            "BEGIN RAISE STATEMENT_ERROR; EXCEPTION WHEN OTHER THEN RETURN SQLSTATE; END");
        assertAnswer(
            "null message",
            "BEGIN RAISE STATEMENT_ERROR; EXCEPTION WHEN OTHER THEN RETURN NVL(SQLERRM, 'null message'); END");
        assertAnswer(
            "null message",
            "BEGIN RAISE EXPRESSION_ERROR; EXCEPTION WHEN OTHER THEN RETURN NVL(SQLERRM, 'null message'); END");
        assertAnswer(
            "outer",
            "BEGIN BEGIN RAISE STATEMENT_ERROR; END; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'outer'; END");
        assertAnswer(
            "outer",
            "BEGIN BEGIN RAISE STATEMENT_ERROR; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'inner'; END; RETURN "
                + "'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'outer'; END");
        assertAnswer(
            "either",
            "BEGIN RAISE STATEMENT_ERROR; EXCEPTION WHEN STATEMENT_ERROR OR EXPRESSION_ERROR THEN RETURN "
                + "'either'; END");
        assertAnswer(
            "o",
            "BEGIN RAISE EXPRESSION_ERROR; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 's'; WHEN OTHER THEN RETURN "
                + "'o'; END");
        assertAnswer(
            "-20000 P0001 null message",
            "DECLARE e EXCEPTION; BEGIN RAISE e; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' "
                + "' || NVL(SQLERRM, 'null message'); END");
        assertAnswer(
            "o",
            "DECLARE e EXCEPTION; BEGIN RAISE e; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 's'; WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'x'; WHEN OTHER THEN RETURN 'o'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6",
            "BEGIN RAISE statement_error; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6",
            "BEGIN RAISE \"STATEMENT_ERROR\"; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | invalid identifier 'statement_error'",
            "BEGIN RAISE \"statement_error\"; END");
        assertAnswer(
            "1",
            "BEGIN LET STATEMENT_ERROR := 1; RETURN STATEMENT_ERROR; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'STATEMENT_ERROR'",
            "BEGIN RETURN STATEMENT_ERROR; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 2",
            "BEGIN LET x := 1;\n  RAISE EXPRESSION_ERROR; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21",
            "BEGIN IF (TRUE) THEN RAISE STATEMENT_ERROR; END IF; RETURN 'none'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 65",
            "BEGIN RAISE STATEMENT_ERROR; EXCEPTION WHEN STATEMENT_ERROR THEN RAISE EXPRESSION_ERROR; END");
        assertAnswer(
            "continued",
            "BEGIN RAISE STATEMENT_ERROR; EXCEPTION WHEN STATEMENT_ERROR CONTINUE THEN RETURN 'continued'; END");
    }

    @Test
    public void inAProcedure() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "Function PQ successfully created.",
            "CREATE OR REPLACE PROCEDURE pq() RETURNS VARCHAR LANGUAGE SQL AS $$BEGIN RAISE STATEMENT_ERROR; END$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6",
            "CALL pq()");
        assertAnswer(
            "Function PQ successfully created.",
            "CREATE OR REPLACE PROCEDURE pq() RETURNS VARCHAR LANGUAGE SQL AS $$BEGIN RAISE EXPRESSION_ERROR; "
                + "EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'caught ' || SQLCODE || ' ' || SQLSTATE; END$$");
        assertAnswer(
            "caught -20000 P0001",
            "CALL pq()");
        assertAnswer(
            "none",
            "BEGIN CALL pq(); RETURN 'none'; END");
        assertAnswer(
            "Function PQ successfully created.",
            "CREATE OR REPLACE PROCEDURE pq() RETURNS VARCHAR LANGUAGE SQL AS $$BEGIN RAISE EXPRESSION_ERROR; "
                + "END$$");
        assertAnswer(
            "statement -20000 P0001",
            "BEGIN CALL pq(); RETURN 'none'; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression'; WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE; END");
    }

    @Test
    public void inAFunctionsBody() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "Drop statement executed successfully (F already dropped).",
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RAISE EXPRESSION_ERROR; RETURN 1; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 6",
            "SELECT f()");
        assertAnswer(
            "F successfully dropped.",
            "DROP FUNCTION IF EXISTS f()");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RAISE STATEMENT_ERROR; RETURN 1; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 7; END");
        assertAnswer(
            "7",
            "SELECT f()");
    }
}
