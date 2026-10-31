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
 * A procedure's body runs no PUT and no GET, with its caller's rights or its owner's, and an owner's-rights one lists
 * no stage, removes no staged file and sets no session parameter (live-verified); each is the body's failure as it
 * runs, the procedure created as written. A GET's target closes its statement in a block.
 */
public class ProcedureFileStatementsTest extends MeasuredCellsTestSupport {

    /**
     * PUT, GET, LIST, REMOVE and ALTER SESSION in procedures of both rights, called and called in a FROM clause, and
     * transactions an EXECUTE IMMEDIATE opens there.
     */
    @Test
    public void fileStatementsAreRefusedAsTheBodyRuns() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE pf_pt1_c() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN PUT "
                + "'file:///tmp/pf_missing.csv' @~; RETURN 'ok'; END $$",
                "Function PF_PT1_C successfully created."},
            {"CALL pf_pt1_c()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'PUT_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_pt1_o() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN PUT "
                + "'file:///tmp/pf_missing.csv' @~; RETURN 'ok'; END $$",
                "Function PF_PT1_O successfully created."},
            {"CALL pf_pt1_o()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'PUT_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_pt2_c() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN PUT "
                + "file:///tmp/pf_missing.csv @~; RETURN 'ok'; END $$",
                "Function PF_PT2_C successfully created."},
            {"CALL pf_pt2_c()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'PUT_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_pt2_o() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN PUT "
                + "file:///tmp/pf_missing.csv @~; RETURN 'ok'; END $$",
                "Function PF_PT2_O successfully created."},
            {"CALL pf_pt2_o()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'PUT_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_gt1_c() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN GET "
                + "@~/pf_missing.csv 'file:///tmp/'; RETURN 'ok'; END $$",
                "Function PF_GT1_C successfully created."},
            {"CALL pf_gt1_c()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'GET_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_gt1_o() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN GET "
                + "@~/pf_missing.csv 'file:///tmp/'; RETURN 'ok'; END $$",
                "Function PF_GT1_O successfully created."},
            {"CALL pf_gt1_o()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'GET_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_gt2_c() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN GET "
                + "@~/pf_missing.csv file:///tmp/; RETURN 'ok'; END $$",
                "Function PF_GT2_C successfully created."},
            {"CALL pf_gt2_c()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'GET_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_gt2_o() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN GET "
                + "@~/pf_missing.csv file:///tmp/; RETURN 'ok'; END $$",
                "Function PF_GT2_O successfully created."},
            {"CALL pf_gt2_o()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'GET_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_ls1_c() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN LIST "
                + "@~ PATTERN = 'pf_nothing.*'; RETURN 'ok'; END $$",
                "Function PF_LS1_C successfully created."},
            {"CALL pf_ls1_c()",
                "ok"},
            {"CREATE OR REPLACE PROCEDURE pf_ls1_o() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN LIST "
                + "@~ PATTERN = 'pf_nothing.*'; RETURN 'ok'; END $$",
                "Function PF_LS1_O successfully created."},
            {"CALL pf_ls1_o()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'LIST_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_rm1_c() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN "
                + "REMOVE @~ PATTERN = 'pf_nothing.*'; RETURN 'ok'; END $$",
                "Function PF_RM1_C successfully created."},
            {"CREATE OR REPLACE PROCEDURE pf_rm1_o() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "REMOVE @~ PATTERN = 'pf_nothing.*'; RETURN 'ok'; END $$",
                "Function PF_RM1_O successfully created."},
            {"CALL pf_rm1_o()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'REMOVE_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_as1_c() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN "
                + "ALTER SESSION SET QUERY_TAG = 'tagged'; RETURN 'ok'; END $$",
                "Function PF_AS1_C successfully created."},
            {"CALL pf_as1_c()",
                "ok"},
            {"CREATE OR REPLACE PROCEDURE pf_as1_o() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN ALTER "
                + "SESSION SET QUERY_TAG = 'tagged'; RETURN 'ok'; END $$",
                "Function PF_AS1_O successfully created."},
            {"CALL pf_as1_o()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'ALTER_SESSION'."},
            {"CREATE OR REPLACE PROCEDURE pf_as2_c() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN "
                + "ALTER SESSION UNSET QUERY_TAG; RETURN 'ok'; END $$",
                "Function PF_AS2_C successfully created."},
            {"CALL pf_as2_c()",
                "ok"},
            {"CREATE OR REPLACE PROCEDURE pf_as2_o() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN ALTER "
                + "SESSION UNSET QUERY_TAG; RETURN 'ok'; END $$",
                "Function PF_AS2_O successfully created."},
            {"CALL pf_as2_o()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'ALTER_SESSION'."},
            {"SELECT CURRENT_TRANSACTION() IS NULL",
                "true"},
            {"CREATE OR REPLACE PROCEDURE pf_fgt2_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN GET @~/pf_missing.csv file:///tmp/; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function PF_FGT2_C successfully created."},
            {"SELECT * FROM TABLE(pf_fgt2_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'GET_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_fgt2_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ "
                + "BEGIN GET @~/pf_missing.csv file:///tmp/; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function PF_FGT2_O successfully created."},
            {"SELECT * FROM TABLE(pf_fgt2_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'GET_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_fpt2_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN PUT file:///tmp/pf_missing.csv @~; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function PF_FPT2_C successfully created."},
            {"SELECT * FROM TABLE(pf_fpt2_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'PUT_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_fpt2_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ "
                + "BEGIN PUT file:///tmp/pf_missing.csv @~; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function PF_FPT2_O successfully created."},
            {"SELECT * FROM TABLE(pf_fpt2_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported statement "
                + "type 'PUT_FILES'."},
            {"CREATE OR REPLACE PROCEDURE pf_fei1_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'BEGIN WORK'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function PF_FEI1_C successfully created."},
            {"SELECT * FROM TABLE(pf_fei1_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE pf_fei1_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'BEGIN WORK'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function PF_FEI1_O successfully created."},
            {"SELECT * FROM TABLE(pf_fei1_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE pf_fei2_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'START TRANSACTION'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function PF_FEI2_C successfully created."},
            {"SELECT * FROM TABLE(pf_fei2_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE pf_fei2_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'START TRANSACTION'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); "
                + "END $$",
                "Function PF_FEI2_O successfully created."},
            {"SELECT * FROM TABLE(pf_fei2_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE pf_fei3_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'BEGIN'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function PF_FEI3_C successfully created."},
            {"SELECT * FROM TABLE(pf_fei3_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE pf_fei3_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'BEGIN'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function PF_FEI3_O successfully created."},
            {"SELECT * FROM TABLE(pf_fei3_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type "
                + "'BEGIN_TRANSACTION' for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE pf_fei4_c() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'COMMIT'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function PF_FEI4_C successfully created."},
            {"SELECT * FROM TABLE(pf_fei4_c())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'COMMIT' "
                + "for child job from stored procedure in FROM clause."},
            {"CREATE OR REPLACE PROCEDURE pf_fei4_o() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS OWNER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'COMMIT'; LET rs RESULTSET := (SELECT 1 AS a); RETURN TABLE(rs); END $$",
                "Function PF_FEI4_O successfully created."},
            {"SELECT * FROM TABLE(pf_fei4_o())",
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: Invalid statement type 'COMMIT' "
                + "for child job from stored procedure in FROM clause."},
        });
        engine.execute("ALTER SESSION UNSET QUERY_TAG");
    }
}
