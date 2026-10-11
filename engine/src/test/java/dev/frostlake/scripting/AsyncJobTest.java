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
 * ASYNC and AWAIT in a block. {@code ASYNC (<statement>)} starts a job of any one statement - a query, DML, a
 * CALL, DDL, a SHOW, an EXECUTE IMMEDIATE, a block - binding the block's variables as they stand then; a
 * RESULTSET is filled by one through DEFAULT, {@code :=} in a LET, or an assignment, and to any other target the
 * word is {@code Unknown function ASYNC.} Until its job is awaited the RESULTSET is {@code uninitialized
 * resultSet 'R'}, to a RETURN TABLE and to a cursor declared over it alike. {@code AWAIT r} waits for one
 * RESULTSET - a failure of its job is the AWAIT's, the counts are the job's - and {@code AWAIT ALL} for every
 * job not awaited yet, in the order they were started: it stops at the first that fails, reported where that
 * job's statement stands, leaves the later jobs to be awaited again, and leaves the counts NULL. AWAIT of a
 * RESULTSET a statement filled does nothing, of one never given anything is {@code Can not wait on an empty
 * resultset}, of one whose job failed already {@code Statement is terminated.}, and of any other name is
 * refused while the block compiles. Both words are syntax errors outside a block. What a job the block never
 * waits for leaves behind is asked and not asserted: the account ends such a job with its block, however far it
 * came. Every asserted answer is live-verified.
 */
public class AsyncJobTest extends MeasuredCellsTestSupport {

    @Test
    public void jobsOfAnyStatementAndTheirResultSets() {
        assertAnswer(
            "Table ORDERS_Q1 successfully created.",
            "CREATE OR REPLACE TABLE orders_q1 (order_amount NUMBER)");
        assertAnswer(
            "2",
            "INSERT INTO orders_q1 VALUES (10), (20)");
        assertAnswer(
            "30",
            "EXECUTE IMMEDIATE $$ DECLARE res1 RESULTSET DEFAULT ASYNC (SELECT order_amount FROM orders_q1); "
                + "total NUMBER DEFAULT 0; BEGIN AWAIT res1; LET c1 CURSOR FOR res1; FOR r IN c1 DO total := total + "
                + "r.order_amount; END FOR; RETURN total; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); AWAIT r; RETURN TABLE(r); END; "
                + "$$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET ins RESULTSET := ASYNC (INSERT INTO orders_q1 VALUES (1)); AWAIT ins; "
                + "RETURN TABLE(ins); END; $$");
        assertAnswer(
            "31",
            "SELECT SUM(order_amount) FROM orders_q1");
        assertAnswer(
            "ok",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1); AWAIT ALL; RETURN 'ok'; END; $$");
        assertAnswer(
            "36",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (INSERT INTO orders_q1 VALUES (5)); AWAIT ALL; RETURN (SELECT "
                + "SUM(order_amount) FROM orders_q1); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 62 : Stored procedure "
                + "execution error: uninitialized resultSet 'R'",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); AWAIT r; AWAIT r; RETURN "
                + "TABLE(r); END; $$");
        assertAnswer(
            "2",
            "EXECUTE IMMEDIATE $$ DECLARE r RESULTSET; BEGIN r := ASYNC (SELECT 2 AS a); AWAIT r; RETURN "
                + "TABLE(r); END; $$");
        assertAnswer(
            "statement 100051 Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1/0 AS a); AWAIT r; RETURN 'no failure'; "
                + "EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLERRM; WHEN OTHER "
                + "THEN RETURN 'other ' || SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "statement 100051 Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1/0); AWAIT ALL; RETURN 'no failure'; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "never awaited",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1/0); RETURN 'never awaited'; END; $$");
        assertAnswer(
            "nothing to await",
            "EXECUTE IMMEDIATE $$ BEGIN AWAIT ALL; RETURN 'nothing to await'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 |  Invalid target for AWAIT statement. "
                + "Expected a resultset.",
            "EXECUTE IMMEDIATE $$ DECLARE x NUMBER; BEGIN AWAIT x; RETURN 'awaited a number'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Invalid target for AWAIT statement. "
                + "Expected a resultset.",
            "EXECUTE IMMEDIATE $$ BEGIN AWAIT nosuch; RETURN 'no'; END; $$");
        assertAnswer(
            "Function AP successfully created.",
            "CREATE OR REPLACE PROCEDURE ap() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO orders_q1 "
                + "VALUES (100); RETURN 'called'; END; $$");
        assertAnswer(
            "136",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (CALL ap()); AWAIT ALL; RETURN (SELECT SUM(order_amount) FROM "
                + "orders_q1); END; $$");
        assertAnswer(
            "called",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (CALL ap()); AWAIT r; RETURN TABLE(r); END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ BEGIN LET n := ASYNC (SELECT 1); RETURN n; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | Unknown function ASYNC.",
            "EXECUTE IMMEDIATE $$ DECLARE n NUMBER; BEGIN n := ASYNC (SELECT 1); RETURN n; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1); ASYNC (SELECT 2); AWAIT ALL; RETURN SQLROWCOUNT; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT order_amount FROM orders_q1 ORDER BY "
                + "order_amount); AWAIT r; LET c CURSOR FOR r; LET v NUMBER := 0; OPEN c; FETCH c INTO v; RETURN v; "
                + "END; $$");
        assertAnswer(
            "0",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (CREATE OR REPLACE TABLE async_made (n NUMBER)); AWAIT ALL; RETURN "
                + "(SELECT COUNT(*) FROM async_made); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'ASYNC'.",
            "ASYNC (SELECT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'AWAIT'.",
            "AWAIT ALL");
        answer(
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC SELECT 1; AWAIT ALL; RETURN 'no parentheses'; END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1; SELECT 2); AWAIT ALL; RETURN 'two'; END; $$");
        assertAnswer(
            "2",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); LET s RESULTSET := ASYNC "
                + "(SELECT 2 AS a); AWAIT ALL; RETURN TABLE(s); END; $$");
    }

    @Test
    public void awaitingAndFailures() {
        assertAnswer(
            "Table JOBS successfully created.",
            "CREATE OR REPLACE TABLE jobs (n NUMBER)");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ DECLARE r RESULTSET DEFAULT (SELECT 1 AS a); BEGIN AWAIT r; RETURN TABLE(r); "
                + "END; $$");
        assertAnswer(
            "2",
            "EXECUTE IMMEDIATE $$ BEGIN LET ins RESULTSET := ASYNC (INSERT INTO jobs VALUES (1), (2)); AWAIT ins; "
                + "RETURN SQLROWCOUNT; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (INSERT INTO jobs VALUES (3)); AWAIT ALL; RETURN SQLROWCOUNT; END; "
                + "$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 14 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1/0); AWAIT ALL; RETURN 'no failure'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1/0 AS a); AWAIT r; RETURN 'no failure'; "
                + "END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1); ASYNC (SELECT 1/0); ASYNC (INSERT INTO jobs VALUES "
                + "(4)); AWAIT ALL; RETURN 'no failure'; EXCEPTION WHEN OTHER THEN RETURN SQLERRM || ' / ' || "
                + "(SELECT COUNT(*) FROM jobs WHERE n = 4); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 122 : Stored procedure "
                + "execution error: uninitialized resultSet 'R'",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1/0 AS a); BEGIN AWAIT r; EXCEPTION WHEN "
                + "OTHER THEN LET z := 1; END; RETURN TABLE(r); END; $$");
        assertAnswer(
            "Function SP successfully created.",
            "CREATE OR REPLACE PROCEDURE sp() RETURNS TABLE (a NUMBER) LANGUAGE SQL AS $$ BEGIN LET r RESULTSET "
                + ":= ASYNC (SELECT n AS a FROM jobs ORDER BY n); AWAIT r; RETURN TABLE(r); END; $$");
        assertAnswer(
            "1 ;; 2 ;; 3",
            "CALL sp()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 66 : Stored procedure "
                + "execution error: uninitialized resultSet 'R'",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); LET c CURSOR FOR r; OPEN c; "
                + "RETURN 'opened before await'; END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); FOR x IN r DO RETURN x.a; END "
                + "FOR; RETURN 'looped'; END; $$");
        assertAnswer(
            "a block",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1) ; ASYNC (BEGIN RETURN 1; END); AWAIT ALL; RETURN 'a "
                + "block'; END; $$");
        assertAnswer(
            "dynamic",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (EXECUTE IMMEDIATE 'SELECT 1'); AWAIT ALL; RETURN 'dynamic'; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SHOW TABLES LIKE 'JOBS' IN SCHEMA "
                + "test_db.test_schema); AWAIT r; LET c CURSOR FOR r; LET n NUMBER := 0; FOR x IN c DO n := n + 1; "
                + "END FOR; RETURN n; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 28 : Can not wait on an "
                + "empty resultset",
            "EXECUTE IMMEDIATE $$ DECLARE r RESULTSET; BEGIN AWAIT r; RETURN 'declared and never filled'; END; $$");
        answer(
            "SELECT COUNT(*) FROM jobs");
    }

    @Test
    public void whenAJobTakesEffect() {
        assertAnswer(
            "Table JOBS2 successfully created.",
            "CREATE OR REPLACE TABLE jobs2 (n NUMBER)");
        answer(
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (INSERT INTO jobs2 VALUES (9)); RETURN 'not awaited'; END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (INSERT INTO jobs2 VALUES (10)); ASYNC (SELECT 1/0); AWAIT ALL; "
                + "RETURN 'no failure'; EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END; $$");
        answer(
            "SELECT LISTAGG(n, ',') WITHIN GROUP (ORDER BY n) FROM jobs2");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 113 : Stored procedure "
                + "execution error: uninitialized resultSet 'R'",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); LET s RESULTSET := ASYNC "
                + "(SELECT 2 AS a); AWAIT s; RETURN TABLE(r); END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); ASYNC (SELECT 1/0); AWAIT r; "
                + "RETURN TABLE(r); END; $$");
        assertAnswer(
            "5",
            "EXECUTE IMMEDIATE $$ BEGIN LET x := 5; LET r RESULTSET := ASYNC (SELECT :x AS a); x := 6; AWAIT r; "
                + "RETURN TABLE(r); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 46 |  variable 'N' cannot have its type "
                + "inferred from initializer",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (INSERT INTO jobs2 VALUES (20)); LET n := (SELECT COUNT(*) FROM "
                + "jobs2 WHERE n = 20); AWAIT ALL; RETURN n; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); AWAIT ALL; AWAIT r; AWAIT ALL; "
                + "RETURN TABLE(r); END; $$");
        assertAnswer(
            "null null",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a UNION ALL SELECT 2); AWAIT r; "
                + "RETURN NVL(SQLROWCOUNT::VARCHAR, 'null') || ' ' || NVL(SQLFOUND::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN BEGIN ASYNC (INSERT INTO jobs2 VALUES (30)); END; AWAIT ALL; RETURN "
                + "(SELECT COUNT(*) FROM jobs2 WHERE n = 30); END; $$");
        assertAnswer(
            "second await: Statement is terminated.",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1/0 AS a); BEGIN AWAIT r; EXCEPTION WHEN "
                + "OTHER THEN LET z := 1; END; BEGIN AWAIT r; EXCEPTION WHEN OTHER THEN RETURN 'second await: ' || "
                + "SQLERRM; END; RETURN 'second await passed'; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1/0); ASYNC (INSERT INTO jobs2 VALUES (40)); BEGIN AWAIT "
                + "ALL; EXCEPTION WHEN OTHER THEN LET z := 1; END; AWAIT ALL; RETURN (SELECT COUNT(*) FROM jobs2 "
                + "WHERE n = 40); END; $$");
        assertAnswer(
            "not awaited",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT * FROM no_such_table_zz); RETURN 'not awaited'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 14 : SQL compilation "
                + "error: | Object 'NO_SUCH_TABLE_ZZ' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZZ.",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT * FROM no_such_table_zz); AWAIT ALL; RETURN 'awaited'; END; "
                + "$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation "
                + "error: | Object 'NO_SUCH_TABLE_ZZ' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZZ.",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT * FROM no_such_table_zz); AWAIT r; "
                + "RETURN 'awaited'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE r RESULTSET DEFAULT ASYNC (SELECT 1/0 AS a); BEGIN AWAIT r; RETURN 'no "
                + "failure'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1/0 AS a); AWAIT ALL; RETURN 'no "
                + "failure'; END; $$");
        assertAnswer(
            "3",
            "EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 3 DO ASYNC (INSERT INTO jobs2 VALUES (:i + 50)); END FOR; "
                + "AWAIT ALL; RETURN (SELECT COUNT(*) FROM jobs2 WHERE n > 50 AND n < 60); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 99 : Stored procedure "
                + "execution error: uninitialized resultSet 'R'",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); AWAIT r; r := ASYNC (SELECT 2 "
                + "AS a); RETURN TABLE(r); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'ASYNC'.",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (ASYNC (SELECT 1)); AWAIT ALL; RETURN 'nested'; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); AWAIT r; LET n NUMBER := 0; FOR "
                + "x IN r DO n := n + x.a; END FOR; RETURN n; END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ DECLARE r RESULTSET; BEGIN r := ASYNC (INSERT INTO jobs2 VALUES (70)); RETURN "
                + "(SELECT COUNT(*) FROM jobs2 WHERE n = 70) || ' before'; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ BEGIN LET x NUMBER := 0; ASYNC (SELECT 7); AWAIT ALL; x := SQLROWCOUNT; RETURN "
                + "NVL(x::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "null null null",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (INSERT INTO jobs2 VALUES (80)); AWAIT ALL; RETURN "
                + "NVL(SQLROWCOUNT::VARCHAR, 'null') || ' ' || NVL(SQLFOUND::VARCHAR, 'null') || ' ' || "
                + "NVL(SQLNOTFOUND::VARCHAR, 'null'); END; $$");
        answer(
            "SELECT LISTAGG(n, ',') WITHIN GROUP (ORDER BY n) FROM jobs2");
    }

    @Test
    public void cursorsCountsAndBlocks() {
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 65 : Stored procedure "
                + "execution error: uninitialized resultSet 'R'",
            "EXECUTE IMMEDIATE $$ DECLARE r RESULTSET DEFAULT ASYNC (SELECT 1 AS a); c CURSOR FOR r; BEGIN AWAIT "
                + "r; OPEN c; LET v NUMBER := 0; FETCH c INTO v; RETURN v; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 75 : Stored procedure "
                + "execution error: uninitialized resultSet 'R'",
            "EXECUTE IMMEDIATE $$ DECLARE r RESULTSET DEFAULT ASYNC (SELECT 1 AS a); BEGIN LET c CURSOR FOR r; "
                + "AWAIT r; OPEN c; LET v NUMBER := 0; FETCH c INTO v; RETURN v; END; $$");
        assertAnswer(
            "Table JOBS3 successfully created.",
            "CREATE OR REPLACE TABLE jobs3 (n NUMBER)");
        assertAnswer(
            "null null",
            "EXECUTE IMMEDIATE $$ BEGIN INSERT INTO jobs3 VALUES (1), (2); ASYNC (SELECT 1); AWAIT ALL; RETURN "
                + "NVL(SQLROWCOUNT::VARCHAR, 'null') || ' ' || NVL(SQLFOUND::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "null null",
            "EXECUTE IMMEDIATE $$ BEGIN INSERT INTO jobs3 VALUES (3), (4); LET r RESULTSET := ASYNC (SELECT 1 AS "
                + "a); AWAIT r; RETURN NVL(SQLROWCOUNT::VARCHAR, 'null') || ' ' || NVL(SQLFOUND::VARCHAR, 'null'); "
                + "END; $$");
        assertAnswer(
            "7",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (BEGIN RETURN 7; END); AWAIT r; RETURN TABLE(r); "
                + "END; $$");
        assertAnswer(
            "8",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (EXECUTE IMMEDIATE 'SELECT 8 AS a'); AWAIT r; "
                + "RETURN TABLE(r); END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (INSERT INTO jobs3 VALUES (9)); AWAIT r; LET c "
                + "CURSOR FOR r; LET v NUMBER := 0; OPEN c; FETCH c INTO v; RETURN v; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); AWAIT r; AWAIT r; RETURN "
                + "NVL(SQLROWCOUNT::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "5",
            "SELECT COUNT(*) FROM jobs3");
    }
}
