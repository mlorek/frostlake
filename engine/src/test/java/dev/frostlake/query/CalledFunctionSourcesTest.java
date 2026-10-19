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
 * A SQL function's body is expanded into the statement that calls it while the statement compiles, so a table
 * the body reads that is gone refuses the statement even where no row would call the function: over a table with
 * no row, in a branch never taken, under a condition that keeps nothing, with LIMIT 0, in a DML statement, and in
 * a view, a table or a function created over the call. A table function joined to an empty table and a function
 * reached through another are refused alike, a view the body reads is expanded under its own name, and a call
 * reached through a view is told bare. Everything else of the statement is judged first, and a block compiles
 * no call this way (live-verified).
 */
public class CalledFunctionSourcesTest extends MeasuredCellsTestSupport {

    @Test
    public void whereNoRowCallsTheFunction() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "INSERT INTO t1 VALUES (1)");
        answer(
            "CREATE OR REPLACE TABLE ue (x INT)");
        answer(
            "CREATE OR REPLACE TABLE ub (x INT)");
        answer(
            "CREATE OR REPLACE TABLE vb (x INT)");
        answer(
            "CREATE OR REPLACE VIEW vv AS SELECT x FROM vb");
        answer(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t1");
        answer(
            "CREATE OR REPLACE FUNCTION f_ue() RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue'");
        answer(
            "CREATE OR REPLACE FUNCTION f_ub() RETURNS TABLE (x INT) AS 'SELECT x FROM ub'");
        answer(
            "CREATE OR REPLACE FUNCTION f_outer() RETURNS NUMBER AS 'f_ue() + 1'");
        answer(
            "CREATE OR REPLACE FUNCTION f_ok() RETURNS NUMBER AS 'SELECT COUNT(*) FROM t1'");
        answer(
            "DROP TABLE ue");
        answer(
            "DROP TABLE ub");
        answer(
            "DROP TABLE vb");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_ue() + 1 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT * FROM t WHERE a = f_ue()");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UB.",
            "SELECT * FROM t, TABLE(f_ub())");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_ue()");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_ue() FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UB.",
            "SELECT * FROM TABLE(f_ub())");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT a FROM t ORDER BY f_ue()");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT a FROM t GROUP BY a HAVING a = f_ue()");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT COUNT(*) FROM t WHERE FALSE AND a = f_ue()");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT IFF(FALSE, f_ue(), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT CASE WHEN FALSE THEN f_ue() ELSE 1 END FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT 1 WHERE 1 = 0 AND f_ue() = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT (SELECT f_ue())");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT a FROM t WHERE a IN (SELECT f_ue())");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT * FROM t, LATERAL (SELECT f_ue() AS c)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_outer() FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_outer()");
        assertAnswer(
            "",
            "SELECT f_ok() FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UB.",
            "SELECT * FROM t JOIN TABLE(f_ub()) u ON u.x = t.a");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UB.",
            "SELECT * FROM t LEFT JOIN TABLE(f_ub()) u ON u.x = t.a");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UB.",
            "SELECT COUNT(*) FROM t, TABLE(f_ub())");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "INSERT INTO t SELECT f_ue()");
    }

    @Test
    public void inDmlAndInWhatIsCreatedOverTheCall() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT)");
            answer(
                "CREATE OR REPLACE TABLE t1 (a INT)");
            answer(
                "INSERT INTO t1 VALUES (1)");
            answer(
                "CREATE OR REPLACE TABLE ue (x INT)");
            answer(
                "CREATE OR REPLACE TABLE ub (x INT)");
            answer(
                "CREATE OR REPLACE TABLE vb (x INT)");
            answer(
                "CREATE OR REPLACE VIEW vv AS SELECT x FROM vb");
            answer(
                "CREATE OR REPLACE VIEW vw AS SELECT a FROM t1");
            answer(
                "CREATE OR REPLACE FUNCTION f_ue() RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue'");
            answer(
                "CREATE OR REPLACE FUNCTION f_ub() RETURNS TABLE (x INT) AS 'SELECT x FROM ub'");
            answer(
                "CREATE OR REPLACE FUNCTION f_outer() RETURNS NUMBER AS 'f_ue() + 1'");
            answer(
                "CREATE OR REPLACE FUNCTION f_ok() RETURNS NUMBER AS 'SELECT COUNT(*) FROM t1'");
            answer(
                "DROP TABLE ue");
            answer(
                "DROP TABLE ub");
            answer(
                "DROP TABLE vb");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.UE.",
                "INSERT INTO t SELECT a FROM t WHERE a = f_ue()");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.UE.",
                "UPDATE t SET a = f_ue()");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.UE.",
                "UPDATE t1 SET a = f_ue()");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.UE.",
                "DELETE FROM t WHERE a = f_ue()");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.UE.",
                "CREATE OR REPLACE VIEW vf AS SELECT f_ue() AS c FROM t");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'VF' does not exist or not authorized. Your primary role "
                    + "<ROLE> must have at least one privilege granted on TABLE VF.",
                "SELECT * FROM vf");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.UE.",
                "CREATE OR REPLACE TABLE tc AS SELECT f_ue() AS c FROM t");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. Your primary role "
                    + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
                "BEGIN RETURN f_ue(); END;");
            assertAnswer(
                "1",
                "BEGIN IF (FALSE) THEN RETURN f_ue(); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'N' cannot have its type "
                    + "inferred from initializer",
                "BEGIN LET n := (SELECT COUNT(*) FROM t WHERE a = f_ue()); RETURN n; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.UE.",
                "CREATE OR REPLACE FUNCTION f_two() RETURNS NUMBER AS 'f_outer() + 1'");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function F_TWO.",
                "SELECT f_two() FROM t");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 41 | invalid identifier 'NOSUCH_COL'",
                "SELECT * FROM t WHERE a = f_ue() AND a = nosuch_col");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH_COL'",
                "SELECT nosuch_col FROM t WHERE a = f_ue()");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.UE.",
                "SELECT * FROM t WHERE a = f_ue() LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.UE.",
                "EXPLAIN USING TEXT SELECT f_ue() FROM t");
        } finally {
            answer(
                "DROP VIEW IF EXISTS vf");
            answer(
                "DROP TABLE IF EXISTS tc");
            answer(
                "DROP FUNCTION IF EXISTS f_two()");
        }
    }

    @Test
    public void throughQueriesParametersAndOverloads() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "INSERT INTO t1 VALUES (1)");
        answer(
            "CREATE OR REPLACE TABLE ue (x INT)");
        answer(
            "CREATE OR REPLACE TABLE ub (x INT)");
        answer(
            "CREATE OR REPLACE TABLE vb (x INT)");
        answer(
            "CREATE OR REPLACE VIEW vv AS SELECT x FROM vb");
        answer(
            "CREATE OR REPLACE VIEW vv2 AS SELECT x FROM vv");
        answer(
            "CREATE OR REPLACE FUNCTION f_ue() RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue'");
        answer(
            "CREATE OR REPLACE FUNCTION f_ub(p INT) RETURNS TABLE (x INT) AS 'SELECT x FROM ub WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_p(p INT) RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_o(p INT) RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_o(p VARCHAR) RETURNS NUMBER AS 'LENGTH(p)'");
        answer(
            "CREATE OR REPLACE FUNCTION f_v() RETURNS NUMBER AS 'SELECT COUNT(*) FROM vv'");
        answer(
            "CREATE OR REPLACE FUNCTION f_sub() RETURNS NUMBER AS '(SELECT COUNT(*) FROM ue) + 1'");
        answer(
            "CREATE OR REPLACE VIEW vfn AS SELECT f_ue() AS c FROM t");
        answer(
            "DROP TABLE ue");
        answer(
            "DROP TABLE ub");
        answer(
            "DROP TABLE vb");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT a FROM t UNION ALL SELECT f_ue() FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "WITH c AS (SELECT f_ue() AS x FROM t) SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT * FROM (SELECT f_ue() AS x FROM t) d");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT * FROM t WHERE EXISTS (SELECT 1 FROM t WHERE a = f_ue())");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_p(a) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_p(1) FROM t");
        assertAnswer(
            "",
            "SELECT f_o('a') FROM t");
        answer(
            "SELECT f_o(1) FROM t");
        assertAnswer(
            "1",
            "SELECT f_o('a') FROM t1");
        answer(
            "SELECT f_o(a) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT f_v() FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_sub() FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UB.",
            "SELECT * FROM t, TABLE(f_ub(t.a))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UB.",
            "SELECT * FROM t, TABLE(f_ub(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT * FROM vfn");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT COUNT(*) FROM vfn");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT test_schema.f_ue() FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT test_db.test_schema.f_ue() FROM t");
    }

    @Test
    public void afterTheStatementsOwnRefusals() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "INSERT INTO t1 VALUES (1)");
        answer(
            "CREATE OR REPLACE TABLE ue (x INT)");
        answer(
            "CREATE OR REPLACE TABLE ub (x INT)");
        answer(
            "CREATE OR REPLACE TABLE vb (x INT)");
        answer(
            "CREATE OR REPLACE VIEW vv AS SELECT x FROM vb");
        answer(
            "CREATE OR REPLACE VIEW vv2 AS SELECT x FROM vv");
        answer(
            "CREATE OR REPLACE FUNCTION f_ue() RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue'");
        answer(
            "CREATE OR REPLACE FUNCTION f_ub(p INT) RETURNS TABLE (x INT) AS 'SELECT x FROM ub WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_p(p INT) RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_o(p INT) RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_o(p VARCHAR) RETURNS NUMBER AS 'LENGTH(p)'");
        answer(
            "CREATE OR REPLACE FUNCTION f_v() RETURNS NUMBER AS 'SELECT COUNT(*) FROM vv'");
        answer(
            "CREATE OR REPLACE FUNCTION f_sub() RETURNS NUMBER AS '(SELECT COUNT(*) FROM ue) + 1'");
        answer(
            "CREATE OR REPLACE VIEW vfn AS SELECT f_ue() AS c FROM t");
        answer(
            "DROP TABLE ue");
        answer(
            "DROP TABLE ub");
        answer(
            "DROP TABLE vb");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCH_FN.",
            "SELECT f_ue(), nosuch_fn() FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F_UE(1)] expected 0, got 1",
            "SELECT f_ue(1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS('x',"
                + " 2)] expected 1, got 2",
            "SELECT ABS('x', 2), f_ue() FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT * FROM t WHERE a = f_ue() AND a");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH_T_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T_ZQ7.",
            "SELECT f_ue() FROM nosuch_t_zq7");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate alias 'X'",
            "SELECT f_ue() FROM t x, t x");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_ue() FROM t GROUP BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT a, f_ue() FROM t GROUP BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT SUM(f_ue()) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [F_UE] for window function.",
            "SELECT f_ue() OVER () FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT a FROM t QUALIFY ROW_NUMBER() OVER (ORDER BY f_ue()) = 1");
    }

    @Test
    public void inMergeAndTheOtherDml() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "INSERT INTO t1 VALUES (1)");
        answer(
            "CREATE OR REPLACE TABLE ue (x INT)");
        answer(
            "CREATE OR REPLACE TABLE ub (x INT)");
        answer(
            "CREATE OR REPLACE TABLE vb (x INT)");
        answer(
            "CREATE OR REPLACE VIEW vv AS SELECT x FROM vb");
        answer(
            "CREATE OR REPLACE VIEW vv2 AS SELECT x FROM vv");
        answer(
            "CREATE OR REPLACE FUNCTION f_ue() RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue'");
        answer(
            "CREATE OR REPLACE FUNCTION f_ub(p INT) RETURNS TABLE (x INT) AS 'SELECT x FROM ub WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_p(p INT) RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_o(p INT) RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_o(p VARCHAR) RETURNS NUMBER AS 'LENGTH(p)'");
        answer(
            "CREATE OR REPLACE FUNCTION f_v() RETURNS NUMBER AS 'SELECT COUNT(*) FROM vv'");
        answer(
            "CREATE OR REPLACE FUNCTION f_sub() RETURNS NUMBER AS '(SELECT COUNT(*) FROM ue) + 1'");
        answer(
            "CREATE OR REPLACE VIEW vfn AS SELECT f_ue() AS c FROM t");
        answer(
            "DROP TABLE ue");
        answer(
            "DROP TABLE ub");
        answer(
            "DROP TABLE vb");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "MERGE INTO t USING t1 ON t.a = t1.a WHEN MATCHED THEN UPDATE SET a = f_ue()");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "MERGE INTO t USING t1 ON t.a = f_ue() WHEN MATCHED THEN DELETE");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "MERGE INTO t USING (SELECT f_ue() AS a FROM t) s ON t.a = s.a WHEN MATCHED THEN DELETE");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "INSERT INTO t VALUES (f_ue())");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "INSERT INTO t SELECT f_ue() FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "UPDATE t SET a = 1 WHERE a = f_ue()");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "UPDATE t SET a = f_ue() FROM t1 WHERE t.a = t1.a");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "DELETE FROM t USING t1 WHERE t.a = f_ue()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | invalid identifier 'NOSUCH'",
            "UPDATE t SET a = f_ue(), nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'NOSUCH'",
            "DELETE FROM t WHERE a = f_ue() AND nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT a FROM t WHERE a = (SELECT f_ue())");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT IFF(a = 1, f_ue(), 0) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT IFF(a = 2, f_ue(), 0) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_ue() AS c FROM t1 WHERE a = 2");
    }
}
