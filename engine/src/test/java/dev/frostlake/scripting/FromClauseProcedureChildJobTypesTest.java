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
 * The statements a procedure called in a FROM clause may not run as child jobs of the query, named as the account names
 * their types (live-verified): BEGIN_TRANSACTION for every BEGIN and START, COMMIT and ROLLBACK, ALTER_SESSION,
 * LIST_FILES and REMOVE_FILES and USE in a caller's-rights procedure, an owner's-rights one refusing those in its own
 * words; a PUT and a GET are no statements a procedure runs at all, and SHOW and DESCRIBE read.
 */
public class FromClauseProcedureChildJobTypesTest extends MeasuredCellsTestSupport {

    /** Each statement in a caller's-rights and an owner's-rights procedure, read alone and joined. */
    @Test
    public void childJobsAreRefusedByTheirTypes() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE cj_tx1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN BEGIN TRANSACTION; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_TX1_C successfully created."},
            {"SELECT * FROM TABLE(cj_tx1_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_tx1_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_tx1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "BEGIN TRANSACTION; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_TX1_O successfully created."},
            {"SELECT * FROM TABLE(cj_tx1_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_tx1_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_tx2_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN START TRANSACTION; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_TX2_C successfully created."},
            {"SELECT * FROM TABLE(cj_tx2_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_tx2_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_tx2_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "START TRANSACTION; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_TX2_O successfully created."},
            {"SELECT * FROM TABLE(cj_tx2_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_tx2_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_tx3_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN BEGIN WORK; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'WORK'."},
            {"SELECT * FROM TABLE(cj_tx3_c())",
                "REFUSED SQL compilation error: | Unknown table function CJ_TX3_C"},
            {"SELECT COUNT(*) FROM TABLE(cj_tx3_c()) x JOIN t ON x.a = t.a",
                "REFUSED SQL compilation error: | Unknown table function CJ_TX3_C"},
            {"CREATE OR REPLACE PROCEDURE cj_tx3_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "BEGIN WORK; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'WORK'."},
            {"SELECT * FROM TABLE(cj_tx3_o())",
                "REFUSED SQL compilation error: | Unknown table function CJ_TX3_O"},
            {"SELECT COUNT(*) FROM TABLE(cj_tx3_o()) x JOIN t ON x.a = t.a",
                "REFUSED SQL compilation error: | Unknown table function CJ_TX3_O"},
            {"CREATE OR REPLACE PROCEDURE cj_tx4_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN COMMIT; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_TX4_C successfully created."},
            {"SELECT * FROM TABLE(cj_tx4_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'COMMIT' "
                + "for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_tx4_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'COMMIT' "
                + "for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_tx4_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "COMMIT; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_TX4_O successfully created."},
            {"SELECT * FROM TABLE(cj_tx4_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'COMMIT' "
                + "for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_tx4_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'COMMIT' "
                + "for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_tx5_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN ROLLBACK; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_TX5_C successfully created."},
            {"SELECT * FROM TABLE(cj_tx5_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'ROLLBACK' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_tx5_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'ROLLBACK' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_tx5_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "ROLLBACK; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_TX5_O successfully created."},
            {"SELECT * FROM TABLE(cj_tx5_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'ROLLBACK' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_tx5_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'ROLLBACK' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_tx6_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN COMMIT WORK; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_TX6_C successfully created."},
            {"SELECT * FROM TABLE(cj_tx6_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'COMMIT' "
                + "for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_tx6_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'COMMIT' "
                + "for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_tx6_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "COMMIT WORK; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_TX6_O successfully created."},
            {"SELECT * FROM TABLE(cj_tx6_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'COMMIT' "
                + "for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_tx6_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'COMMIT' "
                + "for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_us1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN USE SCHEMA test_db.test_schema; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_US1_C successfully created."},
            {"SELECT * FROM TABLE(cj_us1_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'USE' for "
                + "child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_us1_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'USE' for "
                + "child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_us1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "USE SCHEMA test_db.test_schema; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_US1_O successfully created."},
            {"SELECT * FROM TABLE(cj_us1_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'USE'."},
            {"SELECT COUNT(*) FROM TABLE(cj_us1_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'USE'."},
            {"CREATE OR REPLACE PROCEDURE cj_us2_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN USE DATABASE test_db; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_US2_C successfully created."},
            {"SELECT * FROM TABLE(cj_us2_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'USE' for "
                + "child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_us2_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'USE' for "
                + "child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_us2_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "USE DATABASE test_db; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_US2_O successfully created."},
            {"SELECT * FROM TABLE(cj_us2_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'USE'."},
            {"SELECT COUNT(*) FROM TABLE(cj_us2_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'USE'."},
            {"CREATE OR REPLACE PROCEDURE cj_us3_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN USE WAREHOUSE IDENTIFIER(CURRENT_WAREHOUSE()); LET rs RESULTSET := (SELECT 1 AS a); RETURN "
                + "TABLE(rs); END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '('. | syntax error "
                + "line 1 at position 49 unexpected '('."},
            {"SELECT * FROM TABLE(cj_us3_c())",
                "REFUSED SQL compilation error: | Unknown table function CJ_US3_C"},
            {"SELECT COUNT(*) FROM TABLE(cj_us3_c()) x JOIN t ON x.a = t.a",
                "REFUSED SQL compilation error: | Unknown table function CJ_US3_C"},
            {"CREATE OR REPLACE PROCEDURE cj_us3_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "USE WAREHOUSE IDENTIFIER(CURRENT_WAREHOUSE()); LET rs RESULTSET := (SELECT 1 AS a); RETURN "
                + "TABLE(rs); END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '('. | syntax error "
                + "line 1 at position 49 unexpected '('."},
            {"SELECT * FROM TABLE(cj_us3_o())",
                "REFUSED SQL compilation error: | Unknown table function CJ_US3_O"},
            {"SELECT COUNT(*) FROM TABLE(cj_us3_o()) x JOIN t ON x.a = t.a",
                "REFUSED SQL compilation error: | Unknown table function CJ_US3_O"},
            {"CREATE OR REPLACE PROCEDURE cj_as1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN ALTER SESSION SET QUERY_TAG = 'tagged'; LET rs RESULTSET := (SELECT 1 AS a); "
                + "RETURN TABLE(rs); "
                + "END $$",
                "Function CJ_AS1_C successfully created."},
            {"SELECT * FROM TABLE(cj_as1_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'ALTER_SESSION' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_as1_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'ALTER_SESSION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_as1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "ALTER SESSION SET QUERY_TAG = 'tagged'; LET rs RESULTSET := (SELECT 1 AS a); "
                + "RETURN TABLE(rs); END $$",
                "Function CJ_AS1_O successfully created."},
            {"SELECT * FROM TABLE(cj_as1_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'ALTER_SESSION'."},
            {"SELECT COUNT(*) FROM TABLE(cj_as1_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'ALTER_SESSION'."},
            {"CREATE OR REPLACE PROCEDURE cj_as2_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN ALTER SESSION UNSET QUERY_TAG; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_AS2_C successfully created."},
            {"SELECT * FROM TABLE(cj_as2_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'ALTER_SESSION' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_as2_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'ALTER_SESSION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_as2_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "ALTER SESSION UNSET QUERY_TAG; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_AS2_O successfully created."},
            {"SELECT * FROM TABLE(cj_as2_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'ALTER_SESSION'."},
            {"SELECT COUNT(*) FROM TABLE(cj_as2_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'ALTER_SESSION'."},
            {"CREATE OR REPLACE PROCEDURE cj_pt1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN PUT 'file:///tmp/cj_missing.csv' @~; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function CJ_PT1_C successfully created."},
            {"SELECT * FROM TABLE(cj_pt1_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'PUT_FILES'."},
            {"SELECT COUNT(*) FROM TABLE(cj_pt1_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'PUT_FILES'."},
            {"CREATE OR REPLACE PROCEDURE cj_pt1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "PUT 'file:///tmp/cj_missing.csv' @~; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END "
                + "$$",
                "Function CJ_PT1_O successfully created."},
            {"SELECT * FROM TABLE(cj_pt1_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'PUT_FILES'."},
            {"SELECT COUNT(*) FROM TABLE(cj_pt1_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'PUT_FILES'."},
            {"CREATE OR REPLACE PROCEDURE cj_gt1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN GET @~/cj_missing.csv 'file:///tmp/'; LET rs RESULTSET := (SELECT 1 AS a); RETURN "
                + "TABLE(rs); END $$",
                "Function CJ_GT1_C successfully created."},
            {"SELECT * FROM TABLE(cj_gt1_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'GET_FILES'."},
            {"SELECT COUNT(*) FROM TABLE(cj_gt1_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'GET_FILES'."},
            {"CREATE OR REPLACE PROCEDURE cj_gt1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "GET @~/cj_missing.csv 'file:///tmp/'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END "
                + "$$",
                "Function CJ_GT1_O successfully created."},
            {"SELECT * FROM TABLE(cj_gt1_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'GET_FILES'."},
            {"SELECT COUNT(*) FROM TABLE(cj_gt1_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'GET_FILES'."},
            {"CREATE OR REPLACE PROCEDURE cj_ls1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN LIST @~ PATTERN = 'cj_nothing.*'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function CJ_LS1_C successfully created."},
            {"SELECT * FROM TABLE(cj_ls1_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'LIST_FILES' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_ls1_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'LIST_FILES' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_ls1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "LIST @~ PATTERN = 'cj_nothing.*'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_LS1_O successfully created."},
            {"SELECT * FROM TABLE(cj_ls1_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'LIST_FILES'."},
            {"SELECT COUNT(*) FROM TABLE(cj_ls1_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'LIST_FILES'."},
            {"CREATE OR REPLACE PROCEDURE cj_rm1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN REMOVE @~ PATTERN = 'cj_nothing.*'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function CJ_RM1_C successfully created."},
            {"SELECT * FROM TABLE(cj_rm1_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'REMOVE_FILES' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_rm1_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'REMOVE_FILES' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_rm1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "REMOVE @~ PATTERN = 'cj_nothing.*'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_RM1_O successfully created."},
            {"SELECT * FROM TABLE(cj_rm1_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'REMOVE_FILES'."},
            {"SELECT COUNT(*) FROM TABLE(cj_rm1_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'REMOVE_FILES'."},
            {"CREATE OR REPLACE PROCEDURE cj_ei1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'BEGIN TRANSACTION'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function CJ_EI1_C successfully created."},
            {"SELECT * FROM TABLE(cj_ei1_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_ei1_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_ei1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "EXECUTE IMMEDIATE 'BEGIN TRANSACTION'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END "
                + "$$",
                "Function CJ_EI1_O successfully created."},
            {"SELECT * FROM TABLE(cj_ei1_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_ei1_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_ei2_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'USE SCHEMA test_db.test_schema'; LET rs RESULTSET := (SELECT 1 AS a); "
                + "RETURN TABLE(rs); END $$",
                "Function CJ_EI2_C successfully created."},
            {"SELECT * FROM TABLE(cj_ei2_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'USE' for "
                + "child job from stored procedure in FROM clause."},
            {"SELECT COUNT(*) FROM TABLE(cj_ei2_c()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'USE' for "
                + "child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE cj_ei2_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "EXECUTE IMMEDIATE 'USE SCHEMA test_db.test_schema'; LET rs RESULTSET := (SELECT 1 AS a); RETURN "
                + "TABLE(rs); END $$",
                "Function CJ_EI2_O successfully created."},
            {"SELECT * FROM TABLE(cj_ei2_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'USE'."},
            {"SELECT COUNT(*) FROM TABLE(cj_ei2_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'USE'."},
            {"CREATE OR REPLACE PROCEDURE cj_sh1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN SHOW TABLES LIKE 'T'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_SH1_C successfully created."},
            {"SELECT * FROM TABLE(cj_sh1_c())",
                "1"},
            {"SELECT COUNT(*) FROM TABLE(cj_sh1_c()) x JOIN t ON x.a = t.a",
                "1"},
            {"CREATE OR REPLACE PROCEDURE cj_sh1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "SHOW TABLES LIKE 'T'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_SH1_O successfully created."},
            {"SELECT * FROM TABLE(cj_sh1_o())",
                "1"},
            {"SELECT COUNT(*) FROM TABLE(cj_sh1_o()) x JOIN t ON x.a = t.a",
                "1"},
            {"CREATE OR REPLACE PROCEDURE cj_de1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN DESCRIBE TABLE t; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_DE1_C successfully created."},
            {"SELECT * FROM TABLE(cj_de1_c())",
                "1"},
            {"SELECT COUNT(*) FROM TABLE(cj_de1_c()) x JOIN t ON x.a = t.a",
                "1"},
            {"CREATE OR REPLACE PROCEDURE cj_de1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "DESCRIBE TABLE t; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_DE1_O successfully created."},
            {"SELECT * FROM TABLE(cj_de1_o())",
                "1"},
            {"SELECT COUNT(*) FROM TABLE(cj_de1_o()) x JOIN t ON x.a = t.a",
                "1"},
            {"CREATE OR REPLACE PROCEDURE cj_ab1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN SELECT SYSTEM$ABORT_TRANSACTION(0); LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function CJ_AB1_C successfully created."},
            {"SELECT * FROM TABLE(cj_ab1_c())",
                "1"},
            {"SELECT COUNT(*) FROM TABLE(cj_ab1_c()) x JOIN t ON x.a = t.a",
                "1"},
            {"CREATE OR REPLACE PROCEDURE cj_ab1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "SELECT SYSTEM$ABORT_TRANSACTION(0); LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function CJ_AB1_O successfully created."},
            {"SELECT * FROM TABLE(cj_ab1_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error:  | Query called from a stored "
                + "procedure contains a function with side effects [SYSTEM$ABORT_TRANSACTION]."},
            {"SELECT COUNT(*) FROM TABLE(cj_ab1_o()) x JOIN t ON x.a = t.a",
                "REFUSED Stored procedure execution error on line 1 position 27: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error:  | Query called from a stored "
                + "procedure contains a function with side effects [SYSTEM$ABORT_TRANSACTION]."},
            {"CALL cj_tx1_c()",
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                + "incomplete and it was rolled back."},
            {"SELECT CURRENT_TRANSACTION() IS NULL",
                "true"},
        });
    }
}
