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
 * A SQL procedure's body written without a block is one statement, and runs as the statement of a block of its
 * own: BEGIN on a line above it, END; on a line below. So its place is line 2 — line 3 when the body opens with
 * a line break — and what it fails with is that block's statement's failure, a name the block cannot bind its
 * refusal as it compiles. As the procedure is created, a body that opens with a name is refused at that name,
 * a $name that is no session variable at its place, and a CALL left without its semicolon at the END the frame
 * closes with (live-verified).
 */
public class BlocklessProcedureBodyTest extends MeasuredCellsTestSupport {

    @Test
    public void aStatementRunsAsABlocksOwn() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            answer(
                "SET nb_v = 7");
            assertAnswer(
                "Function NB_A successfully created.",
                "CREATE OR REPLACE PROCEDURE nb_a() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT :x;$$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 2 at position 7 | invalid identifier 'X'",
                "CALL nb_a()");
            assertAnswer(
                "Function NB_B successfully created.",
                "CREATE OR REPLACE PROCEDURE nb_b() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT x;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 0 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'X'",
                "CALL nb_b()");
            assertAnswer(
                "Function NB_C successfully created.",
                "CREATE OR REPLACE PROCEDURE nb_c() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT 1;$$");
            assertAnswer(
                "null",
                "CALL nb_c()");
            assertAnswer(
                "Function NB_D successfully created.",
                "CREATE OR REPLACE PROCEDURE nb_d() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT 1/0;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 0 : Division by zero",
                "CALL nb_d()");
            assertAnswer(
                "Function NB_E successfully created.",
                "CREATE OR REPLACE PROCEDURE nb_e() RETURNS VARCHAR LANGUAGE SQL AS $$INSERT INTO nb_t VALUES "
                    + "(1);$$");
            assertAnswer(
                "null",
                "CALL nb_e()");
            assertAnswer(
                "Function NB_F successfully created.",
                "CREATE OR REPLACE PROCEDURE nb_f() RETURNS VARCHAR LANGUAGE SQL AS $$SET w2 = 2;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 0 : Stored procedure "
                    + "execution error: Unsupported statement type 'SET'.",
                "CALL nb_f()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Session variable '$V' does not exist",
                "CREATE OR REPLACE PROCEDURE nb_g() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT $v;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NB_G.",
                "CALL nb_g()");
            assertAnswer(
                "Function NB_H successfully created.",
                "CREATE OR REPLACE PROCEDURE nb_h() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$SET w2 = "
                    + "2;$$");
            assertAnswer(
                "null",
                "CALL nb_h()");
            assertAnswer(
                "Function NB_I successfully created.",
                "CREATE OR REPLACE PROCEDURE nb_i() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$SELECT "
                    + "$nb_v;$$");
            assertAnswer(
                "null",
                "CALL nb_i()");
            assertAnswer(
                "Function NB_K successfully created.",
                "CREATE OR REPLACE PROCEDURE nb_k() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT nosuch_col_x9 FROM "
                    + "nb_t;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 0 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'NOSUCH_COL_X9'",
                "CALL nb_k()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'SELEC'.",
                "CREATE OR REPLACE PROCEDURE nb_l() RETURNS VARCHAR LANGUAGE SQL AS $$SELEC 1;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NB_L.",
                "CALL nb_l()");
            assertAnswer(
                "Function NB_M successfully created.",
                "CREATE OR REPLACE PROCEDURE nb_m() RETURNS VARCHAR LANGUAGE SQL AS $$\nSELECT x;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 0 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'X'",
                "CALL nb_m()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 3 at position 4 unexpected '<EOF>'.",
                "CREATE OR REPLACE PROCEDURE nb_n() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT 1$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NB_N.",
                "CALL nb_n()");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                    + "exception of type 'STATEMENT_ERROR' on line 2 at position 0 : SQL compilation error: error "
                    + "line 1 at position 7 | invalid identifier 'X'",
                "BEGIN CALL nb_b(); RETURN 1; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM nb_t");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS nb_a()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_b()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_c()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_d()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_e()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_f()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_g()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_h()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_i()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_j()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_k()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_l()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_m()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_n()");
            answer(
                "UNSET w2");
            answer(
                "UNSET nb_v");
        }
    }

    @Test
    public void namesPlacesAndTerminators() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            answer(
                "INSERT INTO nb_t VALUES (1)");
            answer(
                "CREATE OR REPLACE SEQUENCE nb_seq");
            answer(
                "CREATE OR REPLACE PROCEDURE nb_p1(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN NVL(a, "
                    + "nosuch_var_x9); END $$");
            assertAnswer(
                "Function NC_B successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_b() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT 1;;$$");
            assertAnswer(
                "null",
                "CALL nc_b()");
            assertAnswer(
                "Function NC_C successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_c() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT 1; -- c$$");
            assertAnswer(
                "null",
                "CALL nc_c()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 3 at position 0 unexpected 'END'.",
                "CREATE OR REPLACE PROCEDURE nc_g() RETURNS VARCHAR LANGUAGE SQL AS $$CALL nc_c()$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NC_G.",
                "CALL nc_g()");
            assertAnswer(
                "Function NC_H successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_h() RETURNS VARCHAR LANGUAGE SQL AS $$CALL nc_c();$$");
            assertAnswer(
                "null",
                "CALL nc_h()");
            assertAnswer(
                "Function NC_I successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_i() RETURNS VARCHAR LANGUAGE SQL AS $$EXECUTE IMMEDIATE 'SELECT "
                    + "1/0';$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 0 : Division by zero",
                "CALL nc_i()");
            assertAnswer(
                "Function NC_J successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_j() RETURNS VARCHAR LANGUAGE SQL AS $$  SELECT 1/0;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : Division by zero",
                "CALL nc_j()");
            assertAnswer(
                "Function NC_K successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_k() RETURNS VARCHAR LANGUAGE SQL AS $$/* c */ SELECT 1/0;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 8 : Division by zero",
                "CALL nc_k()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'x'.",
                "CREATE OR REPLACE PROCEDURE nc_l() RETURNS VARCHAR LANGUAGE SQL AS $$x := 1;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NC_L.",
                "CALL nc_l()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'foo'.",
                "CREATE OR REPLACE PROCEDURE nc_m() RETURNS VARCHAR LANGUAGE SQL AS $$foo;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NC_M.",
                "CALL nc_m()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Session variable '$NOSUCH_V9' does "
                    + "not exist",
                "CREATE OR REPLACE PROCEDURE nc_q() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$SELECT "
                    + "$nosuch_v9;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NC_Q.",
                "CALL nc_q()");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS nc_a()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_b()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_c()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_d()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_e()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_f()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_g()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_h()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_i()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_j()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_k()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_l()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_m()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_n()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_o()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_p()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_q()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_r()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_s()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_t()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_u()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_v(INT)");
            answer(
                "DROP PROCEDURE IF EXISTS nc_w(INT)");
            answer(
                "DROP PROCEDURE IF EXISTS nc_x()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_y()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_z()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_aa()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_ab()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_ac()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_ad()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_ae()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_af()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_tab()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_p1(INT)");
            answer(
                "DROP FUNCTION IF EXISTS nb_f1(INT)");
            answer(
                "DROP SEQUENCE IF EXISTS nb_seq");
        }
    }

    @Test
    public void moreStatementsAndFrames() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            answer(
                "INSERT INTO nb_t VALUES (1)");
            answer(
                "CREATE OR REPLACE SEQUENCE nb_seq");
            answer(
                "CREATE OR REPLACE PROCEDURE nb_p1(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN NVL(a, "
                    + "nosuch_var_x9); END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE nc_j() RETURNS VARCHAR LANGUAGE SQL AS $$  SELECT 1/0;$$");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "CREATE OR REPLACE PROCEDURE nc_s() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT 2 ^ 2;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NC_S.",
                "CALL nc_s()");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "CREATE OR REPLACE PROCEDURE nc_t() RETURNS VARCHAR LANGUAGE SQL AS $$BEGIN RETURN 2 ^ 2; END$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NC_T.",
                "CALL nc_t()");
            assertAnswer(
                "Function NC_U successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_u() RETURNS VARCHAR LANGUAGE SQL AS $$DELETE FROM nb_t;$$");
            assertAnswer(
                "null",
                "CALL nc_u()");
            assertAnswer(
                "Function NC_V successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_v(x INT) RETURNS VARCHAR LANGUAGE SQL AS $$SELECT :x;$$");
            assertAnswer(
                "null",
                "CALL nc_v(1)");
            assertAnswer(
                "Function NC_W successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_w(x INT) RETURNS VARCHAR LANGUAGE SQL AS $$SELECT x;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 0 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'X'",
                "CALL nc_w(1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'RETURN'.",
                "CREATE OR REPLACE PROCEDURE nc_x() RETURNS VARCHAR LANGUAGE SQL AS $$RETURN 1;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NC_X.",
                "CALL nc_x()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'SELEC'.",
                "CREATE OR REPLACE PROCEDURE nc_y() RETURNS VARCHAR LANGUAGE SQL AS $$SELEC 1; SELECT 2;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NC_Y.",
                "CALL nc_y()");
            assertAnswer(
                "Function NC_Z successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_z() RETURNS VARCHAR LANGUAGE SQL AS $$BEGIN SELECT 1/0; END;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero",
                "CALL nc_z()");
            assertAnswer(
                "Function NC_AA successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_aa() RETURNS VARCHAR LANGUAGE SQL AS $$\n\n  SELECT 1/0;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 4 at position 2 : Division by zero",
                "CALL nc_aa()");
            assertAnswer(
                "Function NC_AB successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_ab() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT 1/0 ;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 0 : Division by zero",
                "CALL nc_ab()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected '\"SELECT\"'.",
                "CREATE OR REPLACE PROCEDURE nc_ac() RETURNS VARCHAR LANGUAGE SQL AS $$\"SELECT\" 1;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NC_AC.",
                "CALL nc_ac()");
            assertAnswer(
                "Function NC_AD successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_ad() RETURNS VARCHAR LANGUAGE SQL AS $$SHOW TABLES LIKE "
                    + "'nosuch_zq7';$$");
            assertAnswer(
                "null",
                "CALL nc_ad()");
            assertAnswer(
                "Function NC_AE successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_ae() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT 1 FROM "
                    + "nosuch_table_zq7;$$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 0 : SQL compilation "
                    + "error: | Object 'NOSUCH_TABLE_ZQ7' does not exist or not authorized. This executable runs "
                    + "with owner's rights. The owner role <ROLE> must have at least one privilege granted on TABLE "
                    + "NOSUCH_TABLE_ZQ7.",
                "CALL nc_ae()");
            assertAnswer(
                "Function NC_AF successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_af() RETURNS VARCHAR LANGUAGE SQL AS $$UPDATE nb_t SET a = 1/0;$$");
            assertAnswer(
                "null",
                "CALL nc_af()");
            assertAnswer(
                "Function NC_TAB successfully created.",
                "CREATE OR REPLACE PROCEDURE nc_tab() RETURNS TABLE() LANGUAGE SQL AS $$SELECT 1;$$");
            assertAnswer(
                "REFUSED SQL compilation error: stored procedure is missing a return statement",
                "CALL nc_tab()");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                    + "exception of type 'STATEMENT_ERROR' on line 2 at position 2 : Division by zero",
                "BEGIN CALL nc_j(); RETURN 1; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM nb_t");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS nc_a()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_b()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_c()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_d()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_e()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_f()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_g()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_h()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_i()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_j()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_k()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_l()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_m()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_n()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_o()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_p()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_q()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_r()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_s()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_t()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_u()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_v(INT)");
            answer(
                "DROP PROCEDURE IF EXISTS nc_w(INT)");
            answer(
                "DROP PROCEDURE IF EXISTS nc_x()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_y()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_z()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_aa()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_ab()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_ac()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_ad()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_ae()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_af()");
            answer(
                "DROP PROCEDURE IF EXISTS nc_tab()");
            answer(
                "DROP PROCEDURE IF EXISTS nb_p1(INT)");
            answer(
                "DROP FUNCTION IF EXISTS nb_f1(INT)");
            answer(
                "DROP SEQUENCE IF EXISTS nb_seq");
        }
    }
}
