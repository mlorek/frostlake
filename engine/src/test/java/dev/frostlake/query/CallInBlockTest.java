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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A CALL a block runs is a statement of its own. CALL ... INTO assigns the value the procedure returns - the
 * one cell of a table procedure's one row, NULL for none, refused for several - with or without the colon,
 * and only inside a block. Each :name is judged by the type its variable is declared with, holding NULL or a
 * value, against the procedure's parameters, as an argument written at the top level is: a text or a binary
 * that holds a value at the value's own width, a LET variable by the type inferred for it, a loop's counter as
 * a NUMBER(9,0). The sentence lists every argument; too many, none and a name no parameter has are refused
 * in a CALL's own words. Date arithmetic over a variable that holds NULL folds to an untyped NULL, which any
 * parameter takes. A bare name among the arguments is the statement's identifier, never the block's variable:
 * it is refused when the CALL runs, placed in the CALL's own text, and not at all on a branch that does not
 * run, where a :name no variable declares is refused while the block compiles. A CALL assigned to a variable,
 * variable := (CALL ...), binds the same way, inside a construct too: a variable declared OBJECT is an OBJECT to
 * the procedure whatever carries its value. Every answer is live-verified.
 */
public class CallInBlockTest extends MeasuredCellsTestSupport {

    @Test
    public void callIntoAVariable() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:n); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pt() RETURNS TABLE (a INT) LANGUAGE SQL AS 'DECLARE r RESULTSET DEFAULT "
                + "(SELECT 1 AS a); BEGIN RETURN TABLE(r); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pw() RETURNS TABLE (a INT, b VARCHAR) LANGUAGE SQL AS 'DECLARE r "
                + "RESULTSET DEFAULT (SELECT 1 AS a, ''x'' AS b UNION ALL SELECT 2, ''y'' ORDER BY 1); BEGIN RETURN "
                + "TABLE(r); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pv() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN NULL; END;'");
        try {
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE r VARCHAR; BEGIN CALL pn(1) INTO :r; RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE r VARCHAR; BEGIN CALL pn(1) INTO r; RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE r VARCHAR; BEGIN IF (TRUE) THEN CALL pn(1) INTO :r; END IF; RETURN r; END;");
            assertAnswer(
                "1",
                "DECLARE r VARCHAR; BEGIN CALL pt() INTO :r; RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE s VARCHAR(10); r VARCHAR; BEGIN IF (TRUE) THEN CALL pn(:s) INTO :r; END IF; RETURN r; "
                    + "END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 48 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE s DATE; r VARCHAR; BEGIN IF (TRUE) THEN CALL pn(:s) INTO :r; END IF; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE s DATE; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE s DATE DEFAULT '2024-01-01'; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (BOOLEAN)",
                "DECLARE s BOOLEAN; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 39 : Numeric value "
                    + "'abc' is not recognized",
                "DECLARE s VARCHAR DEFAULT 'abc'; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (DATE)",
                "CALL pn('2024-01-01'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (BOOLEAN)",
                "CALL pn(TRUE)");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "CALL pn('abc')");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "CALL pn('12')");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "CALL pn(NULL)");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "CALL pn(1.5)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 11 INTO clause is not allowed in this "
                    + "context",
                "CALL pn(1) INTO :r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 11 INTO clause is not allowed in this "
                    + "context",
                "CALL pn(1) INTO r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCH'",
                "BEGIN CALL pn(1) INTO :nosuch; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'NOSUCH'",
                "BEGIN CALL pn(1) INTO nosuch; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : A SELECT INTO "
                    + "statement expects exactly 1 returned row, but got 2.",
                "DECLARE r VARCHAR; BEGIN CALL pw() INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 |  Number of SELECT values (1) does "
                    + "not match the number of INTO variables (2).",
                "DECLARE r VARCHAR; q VARCHAR; BEGIN CALL pw() INTO :r, :q; RETURN r || q; END;");
            assertAnswer(
                "was null",
                "DECLARE r VARCHAR DEFAULT 'old'; BEGIN CALL pv() INTO :r; RETURN NVL(r, 'was null'); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 21 : Numeric value "
                    + "'NUMBER(38,0)[SB16]' is not recognized",
                "DECLARE r INT; BEGIN CALL pn(1) INTO :r; RETURN r; END;");
            assertAnswer(
                "2",
                "DECLARE r NUMBER; BEGIN CALL pt() INTO :r; RETURN r + 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 44 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'I'",
                "DECLARE r VARCHAR; BEGIN FOR i IN 1 TO 2 DO CALL pn(i) INTO :r; END FOR; RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE r VARCHAR; BEGIN BEGIN CALL pn(1) INTO :r; END; RETURN r; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'x'. | syntax "
                    + "error line 1 at position 47 unexpected 'RETURN'.",
                "DECLARE r VARCHAR; BEGIN CALL pn(1) INTO :r x; RETURN r; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ';'.",
                "DECLARE r VARCHAR; BEGIN CALL pn(1) INTO; RETURN r; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 25 |  Number of SELECT values (1) does "
                    + "not match the number of INTO variables (2).",
                "DECLARE r VARCHAR; BEGIN CALL pn(1) INTO :r, :r; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : SQL compilation "
                    + "error: | Unknown function NOSUCH_PROC.",
                "DECLARE r VARCHAR; BEGIN CALL nosuch_proc(1) INTO :r; RETURN r; END;");
            assertAnswer(
                "waited 0 seconds",
                "DECLARE r VARCHAR; BEGIN CALL SYSTEM$WAIT(0) INTO :r; RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE r VARCHAR; BEGIN LET c := 1; CALL pn(:c) INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 43 |  Invalid use of resultset 'R'.",
                "DECLARE r RESULTSET; BEGIN CALL pt() INTO :r; RETURN TABLE(r); END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE r VARCHAR; BEGIN r := (CALL pn(1)); RETURN r; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'pn'.",
                "DECLARE r VARCHAR; BEGIN LET x VARCHAR := (CALL pn(1)); RETURN x; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void aBindIsJudgedByItsDeclaredType() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:n); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p2(a NUMBER, b VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:a) || SYSTEM$TYPEOF(:b); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:d); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pb(b BOOLEAN) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:b); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pe() RETURNS TABLE (a INT) LANGUAGE SQL AS 'DECLARE r RESULTSET DEFAULT "
                + "(SELECT 1 AS a WHERE FALSE); BEGIN RETURN TABLE(r); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pc() RETURNS TABLE (a INT, b VARCHAR) LANGUAGE SQL AS 'DECLARE r "
                + "RESULTSET DEFAULT (SELECT 7 AS a, ''x'' AS b); BEGIN RETURN TABLE(r); END;'");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (DATE)",
                "CALL pn(NULL::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (BOOLEAN)",
                "CALL pn(NULL::BOOLEAN)");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "CALL pn(NULL::VARCHAR)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (TIME(9))",
                "CALL pn(NULL::TIME)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PD': (NUMBER(38,0))",
                "CALL pd(NULL::NUMBER)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PD': (NUMBER(1,0))",
                "CALL pd(1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PB': (DATE)",
                "CALL pb(NULL::DATE)");
            assertAnswer(
                "DATE[SB4]",
                "CALL pd('2024-01-01')");
            assertAnswer(
                "BOOLEAN[SB1]",
                "CALL pb(1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (TIME(9))",
                "DECLARE s TIME; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': "
                    + "(TIMESTAMP_NTZ(9))",
                "DECLARE s TIMESTAMP_NTZ; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': "
                    + "(TIMESTAMP_LTZ(9))",
                "DECLARE s TIMESTAMP_LTZ; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE s NUMBER; BEGIN CALL pd(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE s NUMBER DEFAULT 1; BEGIN CALL pd(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 23 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (FLOAT)",
                "DECLARE s FLOAT; BEGIN CALL pd(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (BOOLEAN)",
                "DECLARE s BOOLEAN; BEGIN CALL pd(:s); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE s VARCHAR; BEGIN CALL pd(:s); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE s VARCHAR DEFAULT '2024-01-01'; BEGIN CALL pd(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PB': (DATE)",
                "DECLARE s DATE; BEGIN CALL pb(:s); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE s NUMBER; BEGIN CALL pb(:s); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE s NUMBER DEFAULT 1; BEGIN CALL pb(:s); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE s VARIANT; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 23 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (ARRAY)",
                "DECLARE s ARRAY; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (OBJECT)",
                "DECLARE s OBJECT; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': "
                    + "(BINARY(67108864))",
                "DECLARE s BINARY; BEGIN CALL pn(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (DATE, "
                    + "VARCHAR(10))",
                "DECLARE d DATE; s VARCHAR(10); BEGIN CALL p2(:d, :s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (DATE, "
                    + "VARCHAR(3))",
                "DECLARE d DATE; s VARCHAR(10) DEFAULT 'abc'; BEGIN CALL p2(:d, :s); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE n NUMBER(10,2) DEFAULT 1.5; d DATE; BEGIN CALL p2(:n, :d); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 41 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (BOOLEAN, "
                    + "NUMBER(10,2))",
                "DECLARE n NUMBER(10,2); b BOOLEAN; BEGIN CALL p2(:b, :n); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (DATE, NULL)",
                "DECLARE d DATE; BEGIN CALL p2(:d, NULL); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (DATE, "
                    + "VARCHAR(1))",
                "DECLARE d DATE; BEGIN CALL p2(:d, 'x'); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL p2(1, :d); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (DATE)",
                "DECLARE d DATE; BEGIN CALL p2(:d); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(:d, :d)] expected 1, "
                    + "got 2",
                "DECLARE d DATE; BEGIN CALL pn(:d, :d); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | named arguments [N] do not match any signature for "
                    + "function PN",
                "DECLARE d DATE; BEGIN CALL pn(n => :d); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; BEGIN IF (TRUE) THEN CALL pn(:d); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 41 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; BEGIN FOR i IN 1 TO 1 DO CALL pn(:d); END FOR; RETURN 1; END;");
            assertAnswer(
                "caught",
                "DECLARE d DATE; BEGIN CALL pn(:d); RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
            assertAnswer(
                "Function PO successfully created.",
                "CREATE OR REPLACE PROCEDURE po(d DATE) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN CALL pn(:d); "
                    + "RETURN 1; END;'");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "CALL po(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "CALL po('2024-01-01'::DATE)");
            assertAnswer(
                "was null",
                "DECLARE r VARCHAR; BEGIN CALL pe() INTO :r; RETURN NVL(r, 'was null'); END;");
            assertAnswer(
                "7",
                "DECLARE r VARCHAR; BEGIN CALL pc() INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 |  Number of SELECT values (1) does "
                    + "not match the number of INTO variables (2).",
                "DECLARE r VARCHAR; q VARCHAR; BEGIN CALL pc() INTO :r, :q; RETURN r; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 25 |  Number of SELECT values (1) does "
                    + "not match the number of INTO variables (2).",
                "DECLARE r VARCHAR; BEGIN CALL SYSTEM$WAIT(0) INTO :r, :r; RETURN r; END;");
            assertAnswer(
                "8",
                "DECLARE r INT; BEGIN CALL pc() INTO :r; RETURN r + 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : Date 'NUMBER(38,"
                    + "0)[SB16]' is not recognized",
                "DECLARE r DATE; BEGIN CALL pn(1) INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'X'",
                "DECLARE r VARCHAR; BEGIN CALL pn(x) INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'X'",
                "DECLARE r VARCHAR; x INT DEFAULT 1; BEGIN CALL pn(x) INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                    + "error: error line 1 at position 12 | invalid identifier 'X'",
                "DECLARE r VARCHAR; x INT DEFAULT 1; BEGIN CALL pn(1 + x) INTO :r; RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE r VARCHAR; x INT DEFAULT 1; BEGIN CALL pn(:x + 1) INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : SQL compilation "
                    + "error: error line 2 at position 4 | invalid identifier 'X'",
                "DECLARE r VARCHAR; x INT DEFAULT 1; BEGIN\n  CALL pn(\n    x) INTO :r;\n  RETURN r;\nEND;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 11 INTO clause is not allowed in this "
                    + "context",
                "CALL pn(1) INTO :r, :q");
            assertAnswer(
                "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on "
                    + "statement \"CALL pn(1) INTO :r\" (at line 1, position 10). | SQL compilation error: error "
                    + "line 1 at position 11 INTO clause is not allowed in this context in SYSTEM$MULTISTMT at '    "
                    + "throw `Execution of multiple statements failed on statement {0} (at line {1}, position "
                    + "{2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "SELECT 1; CALL pn(1) INTO :r");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void everyDeclaredTypeExpressionAndConstruct() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:n); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p2(a NUMBER, b VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:a) || SYSTEM$TYPEOF(:b); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:d); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pa(a ARRAY) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:a); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pv(v VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:v); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pf(f FLOAT) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:f); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pt(t TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:t); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE po2(n NUMBER) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN CALL pd(:n); RETURN "
                + "1; END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE po3(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN IF (TRUE) THEN "
                + "CALL pa(:s); END IF; RETURN 1; END;'");
        try {
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "BEGIN LET x := 1; CALL pd(:x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': (VARCHAR(3))",
                "BEGIN LET x := 'abc'; CALL pa(:x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 20 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (FLOAT)",
                "BEGIN LET x := 1.5; CALL pd(:x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (BOOLEAN)",
                "BEGIN LET x := TRUE; CALL pd(:x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "BEGIN LET x := CURRENT_DATE(); CALL pn(:x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "BEGIN LET x := '2024-01-01'::DATE; CALL pn(:x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 28 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "BEGIN LET x := 12345678901; CALL pd(:x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 27 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (FLOAT)",
                "BEGIN LET x := 1.5::FLOAT; CALL pd(:x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(134217728))",
                "DECLARE s VARCHAR; BEGIN CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 39 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': (VARCHAR(3))",
                "DECLARE s VARCHAR DEFAULT 'abc'; BEGIN CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': (VARCHAR(3))",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(134217728))",
                "DECLARE s STRING; BEGIN CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(134217728))",
                "DECLARE s TEXT; BEGIN CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': (VARCHAR(1))",
                "DECLARE s CHAR; BEGIN CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(10,2))",
                "DECLARE n NUMBER(10,2) DEFAULT 1.5; BEGIN CALL pd(:n); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n INT DEFAULT 5; BEGIN CALL pd(:n); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n INT; BEGIN CALL pd(:n); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (FLOAT)",
                "DECLARE f FLOAT DEFAULT 1.5; BEGIN CALL pd(:f); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (FLOAT)",
                "DECLARE f DOUBLE; BEGIN CALL pd(:f); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 57 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (BINARY(2))",
                "DECLARE b BINARY DEFAULT TO_BINARY('ab', 'UTF-8'); BEGIN CALL pn(:b); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 50 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (ARRAY)",
                "DECLARE a ARRAY DEFAULT ARRAY_CONSTRUCT(1); BEGIN CALL pn(:a); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 50 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PV': (ARRAY)",
                "DECLARE a ARRAY DEFAULT ARRAY_CONSTRUCT(1); BEGIN CALL pv(:a); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (OBJECT)",
                "DECLARE o OBJECT DEFAULT OBJECT_CONSTRUCT(); BEGIN CALL pn(:o); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE v VARIANT DEFAULT 1; BEGIN CALL pn(:v); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE v VARIANT; BEGIN CALL pv(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 30 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PT': "
                    + "(TIMESTAMP_TZ(9))",
                "DECLARE t TIMESTAMP_TZ; BEGIN CALL pt(:t); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE t TIMESTAMP_NTZ; BEGIN CALL pd(:t); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pt(:d); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PF': (DATE)",
                "DECLARE d DATE; BEGIN CALL pf(:d); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PF': (BOOLEAN)",
                "DECLARE b BOOLEAN; BEGIN CALL pf(:b); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE b BOOLEAN; BEGIN CALL pv(:b); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(:d + 1); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(YEAR(:d)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n NUMBER; BEGIN CALL pd(:n + 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; BEGIN CALL pn(NVL(:d, CURRENT_DATE())); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE s VARCHAR; BEGIN CALL pd(TO_DATE(:s)); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE n NUMBER; BEGIN CALL pd(:n || 'x'); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE n NUMBER; BEGIN CALL pd(:n::VARCHAR); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(:d::VARCHAR); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n NUMBER; BEGIN CALL pd(-:n); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n NUMBER; BEGIN CALL pd((:n)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n NUMBER; BEGIN CALL pd(IFF(TRUE, :n, 2)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; BEGIN CALL pn((SELECT :d)); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(1 + 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; BEGIN CALL pn(CURRENT_DATE()); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(2,0))",
                "DECLARE d DATE; BEGIN CALL pd(1 + 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (BOOLEAN)",
                "DECLARE d DATE; BEGIN CALL pd(TRUE); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (BOOLEAN)",
                "DECLARE d DATE; BEGIN CALL pn(TRUE); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(:d, 1)] expected 1, "
                    + "got 2",
                "DECLARE d DATE; BEGIN CALL pn(:d, 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(1, 2)] expected 1, "
                    + "got 2",
                "DECLARE d DATE; BEGIN CALL pn(1, 2); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': ()",
                "DECLARE d DATE; BEGIN CALL pn(); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | named arguments [A, B] do not match any signature for "
                    + "function P2",
                "DECLARE d DATE; BEGIN CALL p2(b => :d, a => :d); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | named arguments [X] do not match any signature for "
                    + "function PN",
                "DECLARE d DATE; BEGIN CALL pn(x => 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 39 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n NUMBER; BEGIN IF (TRUE) THEN CALL pd(:n); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 41 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (DATE, "
                    + "VARCHAR(1))",
                "DECLARE d DATE; BEGIN FOR i IN 1 TO 1 DO CALL p2(:d, 'x'); END FOR; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(:d, :d)] expected 1, "
                    + "got 2",
                "DECLARE d DATE; BEGIN IF (TRUE) THEN CALL pn(:d, :d); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : SQL compilation "
                    + "error: error line 0 at position -1 | named arguments [N] do not match any signature for "
                    + "function PN",
                "DECLARE d DATE; BEGIN IF (TRUE) THEN CALL pn(n => :d); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 41 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(9,0))",
                "DECLARE d DATE; BEGIN FOR i IN 1 TO 1 DO CALL pd(:i); END FOR; RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN FOR i IN 1 TO 1 DO CALL pn(:i); END FOR; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 58 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': (VARCHAR(3))",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN IF (TRUE) THEN CALL pa(:s); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 44 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': (VARCHAR(10))",
                "DECLARE s VARCHAR(10); BEGIN IF (TRUE) THEN CALL pa(:s); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 49 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(10,2))",
                "DECLARE n NUMBER(10,2) DEFAULT 1.5; BEGIN REPEAT CALL pd(:n); UNTIL (TRUE) END REPEAT; RETURN 1; "
                    + "END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 38 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (ARRAY)",
                "DECLARE a ARRAY; BEGIN IF (TRUE) THEN CALL pn(:a); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 39 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n NUMBER; BEGIN IF (TRUE) THEN CALL pd(:n + 1); END IF; RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN IF (TRUE) THEN CALL pn(YEAR(:d)); END IF; RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN IF (TRUE) THEN CALL pn(:d + 1); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 28 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; BEGIN BEGIN CALL pn(:d); END; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 46 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; BEGIN BEGIN LET e DATE := :d; CALL pn(:e); END; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "CALL po2(1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "CALL po2(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': (VARCHAR(3))",
                "CALL po3('abc')");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'X'",
                "BEGIN CALL pn(x); RETURN 1; END;");
            assertAnswer(
                "1",
                "BEGIN IF (FALSE) THEN CALL pn(x); END IF; RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE r VARCHAR; BEGIN IF (FALSE) THEN CALL pn(x) INTO :r; END IF; RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE x INT DEFAULT 1; BEGIN IF (FALSE) THEN CALL pn(x); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'X'",
                "DECLARE x INT DEFAULT 1; BEGIN CALL pn(x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 12 | invalid identifier 'X'",
                "DECLARE x INT DEFAULT 1; BEGIN CALL pn(1 + x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 13 | invalid identifier 'X'",
                "DECLARE x INT DEFAULT 1; BEGIN CALL pn(n => x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 11 | invalid identifier 'X'",
                "DECLARE x INT DEFAULT 1; BEGIN CALL p2(1, x); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'X'",
                "DECLARE x INT DEFAULT 1; r VARCHAR; BEGIN CALL p2(x, x) INTO :r; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'NOSUCH'",
                "BEGIN CALL pn(:nosuch); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'NOSUCH'",
                "DECLARE r VARCHAR; BEGIN CALL pn(:nosuch) INTO :r; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'NOSUCH'",
                "BEGIN IF (FALSE) THEN CALL pn(:nosuch); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 15 | invalid identifier 'X'",
                "BEGIN CALL pn(LENGTH(x)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : SQL compilation "
                    + "error: error line 2 at position 4 | invalid identifier 'X'",
                "DECLARE x INT DEFAULT 1; BEGIN\n  CALL pn(\n    x);\n  RETURN 1;\nEND;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 46 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'X'",
                "DECLARE x INT DEFAULT 1; BEGIN IF (TRUE) THEN CALL pn(x); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 57 : SQL compilation "
                    + "error: error line 1 at position 12 | invalid identifier 'X'",
                "DECLARE x INT DEFAULT 1; r VARCHAR; BEGIN IF (TRUE) THEN CALL pn(1 + x) INTO :r; END IF; RETURN "
                    + "1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 41 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE; r RESULTSET; BEGIN r := (CALL pn(:d)); RETURN TABLE(r); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 44 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n NUMBER; BEGIN LET r RESULTSET := (CALL pd(:n)); RETURN TABLE(r); END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE n NUMBER DEFAULT 1; r RESULTSET; BEGIN r := (CALL pn(:n)); RETURN TABLE(r); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 50 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'X'",
                "DECLARE x INT DEFAULT 1; r RESULTSET; BEGIN r := (CALL pn(x)); RETURN TABLE(r); END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void foldsNamesAndLoops() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:n); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p2(a NUMBER, b VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:a) || SYSTEM$TYPEOF(:b); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:d); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pv(v VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:v); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pf(f FLOAT) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:f); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pi(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN :n + 1; END;'");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (ARRAY)",
                "CALL pn(ARRAY_CONSTRUCT(1))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (ARRAY)",
                "CALL pn(NULL::ARRAY)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (OBJECT)",
                "CALL pn(OBJECT_CONSTRUCT())");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (DATE)",
                "CALL pn(NULL::DATE + 1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (DATE)",
                "CALL pn('2024-01-01'::DATE + 1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE DEFAULT '2024-01-01'; BEGIN CALL pn(:d + 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (NULL, ARRAY)",
                "DECLARE d DATE; BEGIN CALL p2(:d + 1, ARRAY_CONSTRUCT(1)); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PV': (ARRAY)",
                "CALL pv(ARRAY_CONSTRUCT(1))");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(DATEADD(day, 1, :d)); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(:d - 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 11 | Invalid argument types for function '+': "
                    + "(TIMESTAMP_NTZ(9), NUMBER(1,0))",
                "DECLARE t TIMESTAMP_NTZ; BEGIN CALL pn(:t + 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                    + "'PN': (DATE)",
                "CALL pn(CURRENT_DATE() + 1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (DATE, ARRAY)",
                "DECLARE d DATE; BEGIN CALL p2(NVL(:d, CURRENT_DATE()) + 1, ARRAY_CONSTRUCT(1)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 58 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (DATE)",
                "DECLARE d DATE DEFAULT '2024-01-01'; BEGIN IF (TRUE) THEN CALL pn(:d); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 65 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (ARRAY)",
                "DECLARE a ARRAY DEFAULT ARRAY_CONSTRUCT(1); BEGIN IF (TRUE) THEN CALL pn(:a); END IF; RETURN 1; "
                    + "END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN IF (TRUE) THEN CALL pn(:d - 1); END IF; RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(1 + :d); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(DATEDIFF(day, :d, :d)); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pd(:d + 1); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(DATE_TRUNC(month, :d)); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(LAST_DAY(:d)); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE d DATE; BEGIN CALL pn(ADD_MONTHS(:d, 1)); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE t TIME; BEGIN CALL pn(TIMEADD(hour, 1, :t)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PD': (NUMBER(38,0))",
                "DECLARE n NUMBER; BEGIN CALL pd(:n * 2); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE s VARCHAR; BEGIN CALL pn(:s || 'x'); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (NULL, ARRAY)",
                "DECLARE d DATE; BEGIN CALL p2(:d - 1, ARRAY_CONSTRUCT(1)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (NULL, ARRAY)",
                "DECLARE d DATE; BEGIN CALL p2(DATEADD(day, 1, :d), ARRAY_CONSTRUCT(1)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (NULL, ARRAY)",
                "DECLARE d DATE; BEGIN CALL p2(LAST_DAY(:d), ARRAY_CONSTRUCT(1)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (NULL, ARRAY)",
                "DECLARE d DATE; BEGIN CALL p2(YEAR(:d), ARRAY_CONSTRUCT(1)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (NUMBER(38,0), "
                    + "ARRAY)",
                "DECLARE n NUMBER; BEGIN CALL p2(:n + 1, ARRAY_CONSTRUCT(1)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'P2': (NULL, ARRAY)",
                "DECLARE t TIMESTAMP_NTZ; BEGIN CALL p2(DATEADD(day, 1, :t), ARRAY_CONSTRUCT(1)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 69 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'REC.A'",
                "DECLARE c CURSOR FOR SELECT 1 AS a; r VARCHAR; BEGIN FOR rec IN c DO CALL pn(rec.a) INTO :r; END "
                    + "FOR; RETURN r; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 69 |  variable 'V' cannot have its type "
                    + "inferred from initializer",
                "DECLARE c CURSOR FOR SELECT 1 AS a; r VARCHAR; BEGIN FOR rec IN c DO LET v := rec.a; CALL pn(:v) "
                    + "INTO :r; END FOR; RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE n NUMBER DEFAULT 5; r VARCHAR; BEGIN CALL pn(:n) INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : SQL compilation "
                    + "error: | Invalid bind value (inf) for type (REAL).",
                "DECLARE f FLOAT DEFAULT 'inf'; BEGIN CALL pf(:f); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : SQL compilation "
                    + "error: | Invalid bind value (inf) for type (REAL).",
                "DECLARE f FLOAT DEFAULT 'inf'; BEGIN IF (TRUE) THEN CALL pf(:f); END IF; RETURN 1; END;");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "DECLARE f FLOAT DEFAULT 'nan'; r VARCHAR; BEGIN IF (TRUE) THEN CALL pf(:f) INTO :r; END IF; "
                    + "RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE n NUMBER DEFAULT 5; r VARCHAR; BEGIN IF (TRUE) THEN CALL pn(n => :n) INTO :r; END IF; "
                    + "RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE n NUMBER DEFAULT 5; r VARCHAR; BEGIN IF (TRUE) THEN CALL test_schema.pn(:n) INTO :r; END "
                    + "IF; RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE n NUMBER DEFAULT 5; r VARCHAR; BEGIN IF (TRUE) THEN CALL \"PN\"(:n) INTO :r; END IF; "
                    + "RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE n NUMBER DEFAULT 5; r VARCHAR; BEGIN IF (TRUE) THEN CALL pn(:n + :n) INTO :r; END IF; "
                    + "RETURN r; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE s VARCHAR DEFAULT '7'; r VARCHAR; BEGIN IF (TRUE) THEN CALL pn(:s) INTO :r; END IF; "
                    + "RETURN r; END;");
            assertAnswer(
                "VARCHAR[LOB]",
                "DECLARE s VARCHAR DEFAULT 'x''y'; r VARCHAR; BEGIN IF (TRUE) THEN CALL pv(:s) INTO :r; END IF; "
                    + "RETURN r; END;");
            assertAnswer(
                "VARCHAR[LOB]",
                "DECLARE r VARCHAR; BEGIN IF (TRUE) THEN CALL pv(':notabind') INTO :r; END IF; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 44 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'I'",
                "DECLARE r VARCHAR; BEGIN FOR i IN 1 TO 1 DO CALL pn(i + 1) INTO :r; END FOR; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 57 : SQL compilation "
                    + "error: error line 1 at position 13 | invalid identifier 'X'",
                "DECLARE r VARCHAR; x INT DEFAULT 2; BEGIN IF (TRUE) THEN CALL pn(n => x) INTO :r; END IF; RETURN "
                    + "r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : SQL compilation "
                    + "error: error line 2 at position 4 | invalid identifier 'X'",
                "DECLARE r VARCHAR; x INT DEFAULT 2; BEGIN IF (TRUE) THEN\n  CALL pn(\n    x) INTO :r;\nEND IF; "
                    + "RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 40 : SQL compilation "
                    + "error: | Unknown function NOSUCH_PROC.",
                "DECLARE r VARCHAR; BEGIN IF (TRUE) THEN CALL nosuch_proc(1) INTO :r; END IF; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 40 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(1, 2)] expected 1, "
                    + "got 2",
                "DECLARE r VARCHAR; BEGIN IF (TRUE) THEN CALL pn(1, 2) INTO :r; END IF; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 40 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': ()",
                "DECLARE r VARCHAR; BEGIN IF (TRUE) THEN CALL pn() INTO :r; END IF; RETURN r; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void aCallAssignedToAVariable() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE add_dml(so OBJECT, qid VARCHAR) RETURNS OBJECT LANGUAGE SQL AS 'BEGIN "
                + "RETURN OBJECT_INSERT(:so, ''q'', NVL(:qid, ''none''), TRUE); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:n); END;'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(:d); END;'");
        try {
            assertAnswer(
                "{\"a\":1,\"q\":\"z\"}",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN IF (TRUE) THEN stats := (CALL "
                    + "add_dml(:stats, 'z')); END IF; RETURN stats; END;");
            assertAnswer(
                "{\"a\":1,\"q\":\"y\"}",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN stats := (CALL add_dml(:stats, "
                    + "'x')); stats := (CALL add_dml(:stats, 'y')); RETURN stats; END;");
            assertAnswer(
                "{\"a\":1,\"q\":\"x\"}",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN FOR i IN 1 TO 2 DO stats := (CALL "
                    + "add_dml(:stats, 'x')); END FOR; RETURN stats; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE n NUMBER DEFAULT 3; x VARCHAR; BEGIN IF (TRUE) THEN x := (CALL pn(:n)); END IF; RETURN "
                    + "x; END;");
            assertAnswer(
                "null",
                "DECLARE stats OBJECT; BEGIN IF (TRUE) THEN stats := (CALL add_dml(:stats, 'z')); END IF; RETURN "
                    + "stats; END;");
            assertAnswer(
                "{\"a\":1,\"q\":\"w\"}",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); r VARCHAR; BEGIN WHILE (r IS NULL) DO "
                    + "stats := (CALL add_dml(:stats, 'w')); r := 'done'; END WHILE; RETURN stats; END;");
            assertAnswer(
                "true",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN IF (TRUE) THEN stats := (CALL "
                    + "add_dml(:stats, LAST_QUERY_ID())); END IF; RETURN GET(stats, 'q') IS NOT NULL; END;");
            assertAnswer(
                "{\"a\":1,\"q\":\"none\"}",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN IF (TRUE) THEN stats := (CALL "
                    + "add_dml(:stats, NULL)); END IF; RETURN stats; END;");
            assertAnswer(
                "{\"a\":1,\"q\":\"v\"}",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); x VARCHAR DEFAULT 'v'; BEGIN IF (TRUE) "
                    + "THEN stats := (CALL add_dml(:stats, :x)); END IF; RETURN stats; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE n NUMBER DEFAULT 3; x VARCHAR; BEGIN IF (TRUE) THEN x := (CALL pn(n => :n)); END IF; "
                    + "RETURN x; END;");
            assertAnswer(
                "NUMBER(38,0)[SB16]",
                "DECLARE n NUMBER DEFAULT 3; x VARCHAR; BEGIN FOR i IN 1 TO 2 DO x := (CALL pn(:i)); END FOR; "
                    + "RETURN x; END;");
            assertAnswer(
                "1",
                "BEGIN ASYNC (CALL pn(1)); AWAIT ALL; RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE n NUMBER DEFAULT 1; BEGIN IF (TRUE) THEN ASYNC (CALL pn(:n)); AWAIT ALL; END IF; RETURN "
                    + "1; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
