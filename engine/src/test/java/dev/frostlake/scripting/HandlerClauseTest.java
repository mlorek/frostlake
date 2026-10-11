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
 * Which built-in exception a fault is, and what a handler clause may name. A fault raised while a scripting
 * statement's own expression is read - an assignment's or a LET's value, a condition, a loop's bound, a CASE's
 * operand, a RETURN's value, a query such an expression reads included - is an EXPRESSION_ERROR; a statement
 * the block runs and the engine refuses - a query, a SELECT INTO, DML, a CALL, an EXECUTE IMMEDIATE, a cursor's
 * OPEN, a RESULTSET's query - is a STATEMENT_ERROR; an exception the script raises is neither. A clause is
 * {@code WHEN name [OR name ...] [CONTINUE | EXIT] THEN}: OTHER stands alone and last - {@code The OTHER handler
 * cannot be used in a list}, {@code The OTHER handler must be the last handler} -, a name that stands for no
 * exception in sight is {@code invalid identifier} while the block compiles, and a body's top block may not
 * declare STATEMENT_ERROR or EXPRESSION_ERROR anew. An untyped LET over an expression that holds a query with a
 * FROM has no type to infer. Every answer is live-verified.
 */
public class HandlerClauseTest extends MeasuredCellsTestSupport {

    @Test
    public void whichExceptionAFaultIs() {
        assertAnswer(
            "Table WT successfully created.",
            "CREATE OR REPLACE TABLE wt (n NUMBER)");
        assertAnswer(
            "Table ERROR_LOG_TABLE successfully created.",
            "CREATE OR REPLACE TABLE error_log_table (handler_type VARCHAR, error_message VARCHAR)");
        assertAnswer(
            "Function WP successfully created.",
            "CREATE OR REPLACE PROCEDURE wp() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1/0; END; $$");
        assertAnswer(
            "Function WQ successfully created.",
            "CREATE OR REPLACE PROCEDURE wq() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE e EXCEPTION (-20005, "
                + "'callee'); BEGIN RAISE e; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN "
                + "'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100038 22018 Numeric value 'abc' is not recognized",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 'abc'::NUMBER; EXCEPTION WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR "
                + "THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 1/0; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' "
                + "|| SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN LET y NUMBER := 1/0; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN "
                + "'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN IF (1/0 > 0) THEN RETURN 'a'; END IF; EXCEPTION WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR "
                + "THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN WHILE (1/0 > 0) DO x := 1; END WHILE; EXCEPTION WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN FOR i IN 1 TO 1/0 DO x := 1; END FOR; EXCEPTION WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN 1/0; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := (SELECT 1/0); EXCEPTION WHEN EXPRESSION_ERROR THEN "
                + "RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN "
                + "RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN SELECT 1/0 INTO :x; EXCEPTION WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR "
                + "THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement 2014 22000 SQL compilation error: | Invalid expression [1 / 0] in VALUES clause",
            "EXECUTE IMMEDIATE $$ BEGIN INSERT INTO wt VALUES (1/0); EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN "
                + "'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement 100038 22018 DML operation to table WT failed on column N with error: Numeric value 'abc' "
                + "is not recognized",
            "EXECUTE IMMEDIATE $$ BEGIN INSERT INTO wt SELECT 'abc'::NUMBER; EXCEPTION WHEN EXPRESSION_ERROR THEN "
                + "RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN "
                + "RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement 100051 22012 Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : "
                + "Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN CALL wp(); EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN EXECUTE IMMEDIATE 'SELECT 1/0'; EXCEPTION WHEN EXPRESSION_ERROR THEN "
                + "RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN "
                + "RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement 1003 42000 SQL compilation error: | syntax error line 1 at position 0 unexpected 'SELEC'.",
            "EXECUTE IMMEDIATE $$ BEGIN EXECUTE IMMEDIATE 'SELEC 1'; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN "
                + "'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN CASE (1/0) WHEN 1 THEN x := 1; ELSE x := 2; END CASE; "
                + "EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN LET c CURSOR FOR SELECT 1/0; OPEN c; FETCH c INTO x; "
                + "EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER := 1; BEGIN x := :x / 0; EXCEPTION WHEN EXPRESSION_ERROR THEN "
                + "RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN "
                + "RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  variable 'Z' cannot have its type "
                + "inferred from initializer",
            "EXECUTE IMMEDIATE $$ BEGIN LET z := 10 / (SELECT COUNT(*) FROM wt WHERE n < 0); EXCEPTION WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "other -20001 P0001 one",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); BEGIN RAISE e1; EXCEPTION WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement -20005 P0001 Uncaught exception of type 'E' on line 1 at position 47 : callee",
            "EXECUTE IMMEDIATE $$ BEGIN CALL wq(); EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100038 22018 Numeric value 'abc' is not recognized",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := (SELECT 'abc'::NUMBER); EXCEPTION WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN LET rs RESULTSET := (SELECT 1/0); RETURN TABLE(rs); EXCEPTION WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN DELETE FROM wt WHERE n = 1/0; EXCEPTION WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT 1/0); EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN "
                + "'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100038 22018 Numeric value 'abc' is not recognized",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := TO_NUMBER('abc'); EXCEPTION WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR "
                + "THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100069 22P02 Error parsing JSON: incomplete object value, pos 5",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := PARSE_JSON('{bad'):a; EXCEPTION WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER DEFAULT 1/0; BEGIN RETURN x; EXCEPTION WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR "
                + "THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN BEGIN x := 1/0; END; EXCEPTION WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR "
                + "THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "expression 100051 22012 Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN BEGIN x := 1/0; EXCEPTION WHEN STATEMENT_ERROR THEN "
                + "RETURN 'inner statement'; END; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN SELECT 1/0 INTO :x; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Uncaught exception of "
                + "type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN CALL wp(); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E1' on line 1 at position 45 : one",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); BEGIN RAISE e1; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Uncaught exception of "
                + "type 'E' on line 1 at position 47 : callee",
            "EXECUTE IMMEDIATE $$ BEGIN CALL wq(); END; $$");
    }

    @Test
    public void whatAHandlerClauseNames() {
        assertAnswer(
            "Table WT successfully created.",
            "CREATE OR REPLACE TABLE wt (n NUMBER)");
        assertAnswer(
            "Table ERROR_LOG_TABLE successfully created.",
            "CREATE OR REPLACE TABLE error_log_table (handler_type VARCHAR, error_message VARCHAR)");
        assertAnswer(
            "Function WP successfully created.",
            "CREATE OR REPLACE PROCEDURE wp() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1/0; END; $$");
        assertAnswer(
            "Function WQ successfully created.",
            "CREATE OR REPLACE PROCEDURE wq() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE e EXCEPTION (-20005, "
                + "'callee'); BEGIN RAISE e; END; $$");
        assertAnswer(
            "2",
            "EXECUTE IMMEDIATE $$ DECLARE x INT := 0; BEGIN FOR i IN 0 TO 5 DO x := 10/i; END FOR; RETURN x; "
                + "EXCEPTION WHEN EXPRESSION_ERROR CONTINUE THEN INSERT INTO error_log_table SELECT 'continue_type', "
                + ":SQLERRM; END; $$");
        assertAnswer(
            "1 / continue_type Division by zero",
            "SELECT COUNT(*) || ' / ' || NVL(MAX(handler_type || ' ' || error_message), 'none') FROM "
                + "error_log_table");
        assertAnswer(
            "exit 0",
            "EXECUTE IMMEDIATE $$ DECLARE x INT := 0; BEGIN FOR i IN 0 TO 5 DO x := 10/i; END FOR; RETURN x; "
                + "EXCEPTION WHEN EXPRESSION_ERROR EXIT THEN RETURN 'exit ' || x; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN INSERT INTO wt VALUES (1/0); INSERT INTO wt VALUES (7); RETURN (SELECT "
                + "COUNT(*) FROM wt); EXCEPTION WHEN STATEMENT_ERROR CONTINUE THEN INSERT INTO error_log_table "
                + "SELECT 'statement', :SQLERRM; END; $$");
        assertAnswer(
            "2 / statement",
            "SELECT COUNT(*) || ' / ' || NVL(MAX(handler_type), 'none') FROM error_log_table");
        assertAnswer(
            "went on 5",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; RETURN 'went on ' || NVL(x, -1); EXCEPTION "
                + "WHEN OTHER CONTINUE THEN x := 5; END; $$");
        assertAnswer(
            "caught as EXPRESSION_ERROR",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'caught as STATEMENT_ERROR'; WHEN EXPRESSION_ERROR THEN RETURN 'caught as EXPRESSION_ERROR'; END; "
                + "$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'caught as STATEMENT_ERROR'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'caught as "
                + "EXPRESSION_ERROR'; END; $$");
        assertAnswer(
            "other -20001",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); BEGIN RAISE e1; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'caught as STATEMENT_ERROR'; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE; END; $$");
        assertAnswer(
            "other -20001",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'Cannot proceed'); BEGIN RAISE e1; EXCEPTION WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'caught as EXPRESSION_ERROR'; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE; END; $$");
        assertAnswer(
            "caught as STATEMENT_ERROR",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 'Cannot'::NUMBER; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN "
                + "'caught as EXPRESSION_ERROR'; WHEN STATEMENT_ERROR THEN RETURN 'caught as STATEMENT_ERROR'; END; "
                + "$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'RUNTIMEEXCEPTION'",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN RuntimeException THEN RETURN 'caught by a Java "
                + "class name'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier 'NO_SUCH_EXCEPTION'",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1; EXCEPTION WHEN no_such_exception THEN RETURN 'never'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 45 | The OTHER handler must be the last "
                + "handler",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; EXCEPTION WHEN OTHER THEN RETURN 'other "
                + "first'; WHEN EXPRESSION_ERROR THEN RETURN 'expression second'; END; $$");
        assertAnswer(
            "either 100051",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; EXCEPTION WHEN STATEMENT_ERROR OR "
                + "EXPRESSION_ERROR THEN RETURN 'either ' || SQLCODE; END; $$");
        assertAnswer(
            "first",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN "
                + "'first'; WHEN EXPRESSION_ERROR THEN RETURN 'second'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 |  Variable with name 'STATEMENT_ERROR' "
                + "declared twice.",
            "EXECUTE IMMEDIATE $$ DECLARE statement_error EXCEPTION (-20001, 'mine'); BEGIN RAISE "
                + "statement_error; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' "
                + "|| SQLERRM; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 45 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); BEGIN SELECT 1/0; EXCEPTION WHEN e1 THEN "
                + "RETURN 'named'; END; $$");
    }

    @Test
    public void exitListsAndOther() {
        assertAnswer(
            "exit",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; EXCEPTION WHEN EXPRESSION_ERROR EXIT THEN "
                + "RETURN 'exit'; END; $$");
        assertAnswer(
            "exit 100051",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN SELECT 1/0 INTO :x; EXCEPTION WHEN STATEMENT_ERROR EXIT "
                + "THEN RETURN 'exit ' || SQLCODE; END; $$");
        assertAnswer(
            "exit other",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; EXCEPTION WHEN OTHER EXIT THEN RETURN 'exit "
                + "other'; END; $$");
        assertAnswer(
            "returned from the handler",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; RETURN 'went on'; EXCEPTION WHEN OTHER "
                + "CONTINUE THEN RETURN 'returned from the handler'; END; $$");
        assertAnswer(
            "either -20002",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); e2 EXCEPTION (-20002, 'two'); BEGIN RAISE "
                + "e2; EXCEPTION WHEN e1 OR e2 THEN RETURN 'either ' || SQLCODE; END; $$");
        assertAnswer(
            "other",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); e2 EXCEPTION (-20002, 'two'); BEGIN RAISE "
                + "e2; EXCEPTION WHEN e1 OR STATEMENT_ERROR THEN RETURN 'mixed ' || SQLCODE; WHEN OTHER THEN RETURN "
                + "'other'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 95 | The OTHER handler cannot be used in a "
                + "list",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); e2 EXCEPTION (-20002, 'two'); BEGIN RAISE "
                + "e2; EXCEPTION WHEN e1 OR OTHER THEN RETURN 'with other'; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); e2 EXCEPTION (-20002, 'two'); BEGIN RAISE "
                + "e2; EXCEPTION WHEN e1 OR e2 OR EXPRESSION_ERROR CONTINUE THEN LET n := 1; END; $$");
        assertAnswer(
            "first",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); e2 EXCEPTION (-20002, 'two'); BEGIN RAISE "
                + "e1; EXCEPTION WHEN e1 THEN RETURN 'first'; WHEN e1 THEN RETURN 'second'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 48 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1; EXCEPTION WHEN x THEN RETURN 'a variable'; END; "
                + "$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 |  Variable with name 'EXPRESSION_ERROR' "
                + "declared twice.",
            "EXECUTE IMMEDIATE $$ DECLARE expression_error EXCEPTION (-20001, 'mine'); BEGIN RAISE "
                + "expression_error; EXCEPTION WHEN OTHER THEN RETURN SQLCODE; END; $$");
        assertAnswer(
            "mine",
            "EXECUTE IMMEDIATE $$ DECLARE sqlcode EXCEPTION (-20001, 'mine'); BEGIN RAISE sqlcode; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLERRM; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 47 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); BEGIN x := 1; EXCEPTION WHEN e1 THEN "
                + "RETURN 'never'; END; $$");
        assertAnswer(
            "-20001",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); BEGIN LET e1 := 5; RAISE e1; EXCEPTION "
                + "WHEN OTHER THEN RETURN SQLCODE; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NO_SUCH'",
            "EXECUTE IMMEDIATE $$ BEGIN RAISE no_such; EXCEPTION WHEN OTHER THEN RETURN 'other'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 83 | The OTHER handler must be the last "
                + "handler",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN x := 1/0; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'s'; WHEN OTHER THEN RETURN 'o'; WHEN OTHER THEN RETURN 'p'; END; $$");
        assertAnswer(
            "2",
            "EXECUTE IMMEDIATE $$ BEGIN LET z := 1 + (SELECT 1); RETURN z; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET z := COALESCE((SELECT 1), 0); RETURN z; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET z := (SELECT 1)::NUMBER; RETURN z; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET z := IFF(TRUE, 1, (SELECT 2)); RETURN z; END; $$");
        assertAnswer(
            "2",
            "EXECUTE IMMEDIATE $$ BEGIN LET z NUMBER := 1 + (SELECT 1); RETURN z; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET z := (SELECT 1); RETURN z; END; $$");
        assertAnswer(
            "2",
            "EXECUTE IMMEDIATE $$ DECLARE z DEFAULT 1 + (SELECT 1); BEGIN RETURN z; END; $$");
        assertAnswer(
            "2003 42S02",
            "EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t VALUES (1/0); EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' "
                + "' || SQLSTATE; END; $$");
        assertAnswer(
            "100069 22P02",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := PARSE_JSON('{bad'); EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END; $$");
        assertAnswer(
            "100069 22P02",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT PARSE_JSON('{bad'); EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' "
                + "' || SQLSTATE; END; $$");
    }
}
