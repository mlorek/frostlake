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
 * A CALL whose answer is assigned to a variable, x := (CALL p()), is the statement that runs: a failure of it
 * is placed at the CALL inside the parentheses, at the block's top level and inside a construct, for a
 * RESULTSET as for any other variable, and its own places count from the CALL (live-verified).
 */
public class AssignedCallPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void theCallInsideTheParentheses() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN n + 1; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN d; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pa(a ARRAY) RETURNS ARRAY LANGUAGE SQL AS $$ BEGIN RETURN a; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pi(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN n + 1; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po3(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po4(s VARCHAR(16777216)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF "
                    + "(TRUE) THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po5(s VARCHAR(10)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF "
                    + "(TRUE) THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE add_dml(so OBJECT, qid VARCHAR) RETURNS OBJECT LANGUAGE SQL AS $$ "
                    + "BEGIN RETURN so; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE abs(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE upper(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "'proc'; END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 39 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; x VARCHAR; BEGIN x := (CALL pn(:d)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 59 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'ADD_DML': "
                    + "(VARCHAR(7), VARCHAR(1))",
                "DECLARE s VARCHAR DEFAULT '{\"a\":1}'; r OBJECT; BEGIN r := (CALL add_dml(:s, 'z')); RETURN r; "
                    + "END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 54 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; x VARCHAR; BEGIN IF (TRUE) THEN x := (CALL pn(:d)); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 58 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n NUMBER; r RESULTSET; BEGIN IF (TRUE) THEN r := (CALL pd(:n)); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 74 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'ADD_DML': "
                    + "(VARCHAR(7), VARCHAR(1))",
                "DECLARE s VARCHAR DEFAULT '{\"a\":1}'; r OBJECT; BEGIN IF (TRUE) THEN r := (CALL add_dml(:s, "
                    + "'z')); END IF; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 86 : SQL compilation "
                    + "error: error line 1 at position 13 | invalid identifier 'STATS'",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN IF (TRUE) THEN stats := (CALL "
                    + "add_dml(stats, 'z')); END IF; RETURN stats; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 86 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function "
                    + "[ADD_DML(CAST(PARSE_JSON(:stats) AS OBJECT), 'z', 1)] expected 2, got 3",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN IF (TRUE) THEN stats := (CALL "
                    + "add_dml(:stats, 'z', 1)); END IF; RETURN stats; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 46 : SQL compilation "
                    + "error: | Unknown function NOSUCH_PROC.",
                "DECLARE x VARCHAR; BEGIN IF (TRUE) THEN x := (CALL nosuch_proc(1)); END IF; RETURN x; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: | Unknown function NOSUCH_PROC.",
                "DECLARE x VARCHAR; BEGIN x := (CALL nosuch_proc(1)); RETURN x; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; BEGIN ASYNC (CALL pn(:d)); AWAIT ALL; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 41 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; r RESULTSET; BEGIN r := (CALL pn(:d)); RETURN 1; END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pn(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pd(DATE)");
            answer(
                "DROP PROCEDURE IF EXISTS pa(ARRAY)");
            answer(
                "DROP PROCEDURE IF EXISTS pi(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS po3(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po4(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po5(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS add_dml(OBJECT, VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS abs(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS upper(VARCHAR)");
        }
    }

    @Test
    public void acrossLinesAndInsideConstructs() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN n + 1; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN d; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pa(a ARRAY) RETURNS ARRAY LANGUAGE SQL AS $$ BEGIN RETURN a; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pt(t TIME) RETURNS TIME LANGUAGE SQL AS $$ BEGIN RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pts(t TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE ptl(t TIMESTAMP_LTZ) RETURNS TIMESTAMP_LTZ LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pb(b BOOLEAN) RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN b; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE pf(f FLOAT) RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN f; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pv(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN s; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE add_dml(so OBJECT, qid VARCHAR) RETURNS OBJECT LANGUAGE SQL AS $$ "
                    + "BEGIN RETURN so; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE abs(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE sum(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE lower(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "'proc'; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE greatest(a NUMBER, b NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN 7; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE nvl(a NUMBER, b NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN 7; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE zq_plain(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po3(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po6(s STRING) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po7(s TEXT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) THEN "
                    + "CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pob(b BINARY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:b); END IF; RETURN 1; END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 71 : SQL compilation "
                    + "error: error line 1 at position 13 | invalid identifier 'STATS'",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN stats := (CALL add_dml(stats, "
                    + "'z')); RETURN stats; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 8 : SQL compilation "
                    + "error: | Unknown function NOSUCH_PROC.",
                "DECLARE x VARCHAR;\nBEGIN\n  x := (CALL nosuch_proc(1));\n  RETURN x;\nEND;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 8 : SQL compilation "
                    + "error: error line 2 at position 12 | invalid identifier 'ZZ'",
                "DECLARE x VARCHAR;\nBEGIN\n  x := (CALL\n    add_dml(zz, 'z'));\n  RETURN x;\nEND;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 58 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; x VARCHAR; BEGIN FOR i IN 1 TO 1 DO x := (CALL pn(:d)); END FOR; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 59 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; x VARCHAR; BEGIN CASE WHEN TRUE THEN x := (CALL pn(:d)); END CASE; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 45 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; x VARCHAR; BEGIN BEGIN x := (CALL pn(:d)); END; RETURN 1; END;");
            assertAnswer(
                "2",
                "DECLARE x NUMBER; BEGIN x := (CALL pn(1)); RETURN x; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 30 : Division by zero",
                "DECLARE x NUMBER; BEGIN x := (CALL pn(1/0)); RETURN x; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 45 : Division by zero",
                "DECLARE x NUMBER; BEGIN IF (TRUE) THEN x := (CALL pn(1/0)); END IF; RETURN x; END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pn(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pd(DATE)");
            answer(
                "DROP PROCEDURE IF EXISTS pa(ARRAY)");
            answer(
                "DROP PROCEDURE IF EXISTS pt(TIME)");
            answer(
                "DROP PROCEDURE IF EXISTS pts(TIMESTAMP_NTZ)");
            answer(
                "DROP PROCEDURE IF EXISTS ptl(TIMESTAMP_LTZ)");
            answer(
                "DROP PROCEDURE IF EXISTS pb(BOOLEAN)");
            answer(
                "DROP PROCEDURE IF EXISTS pf(FLOAT)");
            answer(
                "DROP PROCEDURE IF EXISTS pv(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS add_dml(OBJECT, VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS abs(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS sum(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS lower(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS greatest(NUMBER, NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS nvl(NUMBER, NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS zq_plain(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS po3(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po6(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po7(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS pob(BINARY)");
        }
    }
}
