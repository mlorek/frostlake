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
package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Whose privileges a SQL function's body reads with. A role that may use a function reads through it what the
 * function's OWNER may read - a scalar function with a query body or with a subquery in its expression, a table
 * function, a function called from another function's body - as it reads through a view, though it may not read
 * the table itself. The caller's own privileges count beside the owner's, so a table the caller owns is read by
 * a function another role owns. What neither may read is refused as the statement's own, in the caller's name
 * and without a function-body preamble, and USAGE on the function is still the caller's to hold.
 * CURRENT_ROLE() inside the body is the caller's. The session stays ACCOUNTADMIN throughout: objects are made
 * another role's by giving their ownership to a scratch role, which the test drops whatever its assertions
 * make of the statements between. Every answer is live-verified.
 */
public class SqlFunctionOwnerRightsTest extends MeasuredCellsTestSupport {

    @Test
    public void theBodyReadsWithItsOwnersRights() {
        try {
            engine.execute("USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "Drop statement executed successfully (FLQ_M6J_O already dropped).",
                "DROP ROLE IF EXISTS flq_m6j_o");
            assertAnswer(
                "Role FLQ_M6J_O successfully created.",
                "CREATE ROLE flq_m6j_o");
            assertAnswer(
                "Table SECRET_T successfully created.",
                "CREATE OR REPLACE TABLE secret_t (v NUMBER)");
            assertAnswer(
                "3",
                "INSERT INTO secret_t VALUES (1), (2), (3)");
            assertAnswer(
                "Table OPEN_T successfully created.",
                "CREATE OR REPLACE TABLE open_t (v NUMBER)");
            assertAnswer(
                "1",
                "INSERT INTO open_t VALUES (7)");
            assertAnswer(
                "View CNT_V successfully created.",
                "CREATE OR REPLACE VIEW cnt_v AS SELECT COUNT(*) AS c FROM secret_t");
            assertAnswer(
                "Function CNT_SECRET successfully created.",
                "CREATE OR REPLACE FUNCTION cnt_secret() RETURNS NUMBER AS 'SELECT COUNT(*) FROM secret_t'");
            assertAnswer(
                "Function CNT_SECRET_E successfully created.",
                "CREATE OR REPLACE FUNCTION cnt_secret_e() RETURNS NUMBER AS '(SELECT COUNT(*) FROM secret_t)'");
            assertAnswer(
                "Function TF_SECRET successfully created.",
                "CREATE OR REPLACE FUNCTION tf_secret() RETURNS TABLE (v NUMBER) AS 'SELECT v FROM secret_t'");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: (SELECT \"COUNT(*)\"() AS \"COUNT(*)\" FROM "
                    + "SECRET_T AS SECRET_T)",
                "CREATE OR REPLACE FUNCTION cnt_block() RETURNS NUMBER AS $$ BEGIN RETURN (SELECT COUNT(*) FROM "
                    + "secret_t); END $$");
            assertAnswer(
                "Function CNT_NESTED successfully created.",
                "CREATE OR REPLACE FUNCTION cnt_nested() RETURNS NUMBER AS 'cnt_secret() + 100'");
            assertAnswer(
                "Function CNT_OPEN successfully created.",
                "CREATE OR REPLACE FUNCTION cnt_open() RETURNS NUMBER AS 'SELECT COUNT(*) FROM open_t'");
            assertAnswer(
                "Function CNT_NOUSAGE successfully created.",
                "CREATE OR REPLACE FUNCTION cnt_nousage() RETURNS NUMBER AS 'SELECT COUNT(*) FROM secret_t'");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON DATABASE test_db TO ROLE flq_m6j_o");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON SCHEMA test_db.test_schema TO ROLE flq_m6j_o");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE secret_t TO ROLE flq_m6j_o COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON VIEW cnt_v TO ROLE flq_m6j_o COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION cnt_secret() TO ROLE flq_m6j_o COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION cnt_secret_e() TO ROLE flq_m6j_o COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION tf_secret() TO ROLE flq_m6j_o COPY CURRENT GRANTS");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.CNT_BLOCK' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                    + "TEST_DB.TEST_SCHEMA.CNT_BLOCK.",
                "GRANT OWNERSHIP ON FUNCTION cnt_block() TO ROLE flq_m6j_o COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION cnt_nested() TO ROLE flq_m6j_o COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION cnt_open() TO ROLE flq_m6j_o COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION cnt_nousage() TO ROLE flq_m6j_o COPY CURRENT GRANTS");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on function 'CNT_SECRET'. "
                    + "Your primary role <ROLE> must have USAGE granted on FUNCTION TEST_DB.TEST_SCHEMA.CNT_SECRET().",
                "SELECT cnt_secret()");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON VIEW cnt_v TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION cnt_secret() TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION cnt_secret_e() TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION tf_secret() TO ROLE PUBLIC");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.CNT_BLOCK' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                    + "TEST_DB.TEST_SCHEMA.CNT_BLOCK.",
                "GRANT USAGE ON FUNCTION cnt_block() TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION cnt_nested() TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION cnt_open() TO ROLE PUBLIC");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'SECRET_T'. Your "
                    + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.SECRET_T.",
                "SELECT * FROM secret_t");
            assertAnswer(
                "3",
                "SELECT c FROM cnt_v");
            assertAnswer(
                "3",
                "SELECT cnt_secret()");
            assertAnswer(
                "3",
                "SELECT cnt_secret_e()");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT v FROM TABLE(tf_secret()) ORDER BY 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function CNT_BLOCK.",
                "SELECT cnt_block()");
            assertAnswer(
                "103",
                "SELECT cnt_nested()");
            assertAnswer(
                "1",
                "SELECT cnt_open()");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on function "
                    + "'CNT_NOUSAGE'. Your primary role <ROLE> must have USAGE granted on FUNCTION "
                    + "TEST_DB.TEST_SCHEMA.CNT_NOUSAGE().",
                "SELECT cnt_nousage()");
            assertAnswer(
                "7|3",
                "SELECT v, cnt_secret() FROM open_t");
        } finally {
            // The scratch roles go, and the session is as it was, whatever the assertions made of it.
            answer("GRANT ROLE flq_m6j_o TO ROLE ACCOUNTADMIN");
            answer("DROP ROLE IF EXISTS flq_m6j_o");
            answer("USE SECONDARY ROLES ALL");
        }
    }

    @Test
    public void callerAndOwnerTogetherAndNeither() {
        try {
            engine.execute("USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "Drop statement executed successfully (FLQ_M6J_A already dropped).",
                "DROP ROLE IF EXISTS flq_m6j_a");
            assertAnswer(
                "Drop statement executed successfully (FLQ_M6J_B already dropped).",
                "DROP ROLE IF EXISTS flq_m6j_b");
            assertAnswer(
                "Role FLQ_M6J_A successfully created.",
                "CREATE ROLE flq_m6j_a");
            assertAnswer(
                "Role FLQ_M6J_B successfully created.",
                "CREATE ROLE flq_m6j_b");
            assertAnswer(
                "Table THIRD_T successfully created.",
                "CREATE OR REPLACE TABLE third_t (v NUMBER)");
            assertAnswer(
                "2",
                "INSERT INTO third_t VALUES (1), (2)");
            assertAnswer(
                "Table OWN_T successfully created.",
                "CREATE OR REPLACE TABLE own_t (v NUMBER)");
            assertAnswer(
                "1",
                "INSERT INTO own_t VALUES (5)");
            assertAnswer(
                "Function CNT_THIRD successfully created.",
                "CREATE OR REPLACE FUNCTION cnt_third() RETURNS NUMBER AS 'SELECT COUNT(*) FROM third_t'");
            assertAnswer(
                "Function TF_THIRD successfully created.",
                "CREATE OR REPLACE FUNCTION tf_third() RETURNS TABLE (v NUMBER) AS 'SELECT v FROM third_t'");
            assertAnswer(
                "Function CNT_MINE successfully created.",
                "CREATE OR REPLACE FUNCTION cnt_mine() RETURNS NUMBER AS 'SELECT COUNT(*) FROM third_t'");
            assertAnswer(
                "Function WHO_RUNS successfully created.",
                "CREATE OR REPLACE FUNCTION who_runs() RETURNS VARCHAR AS 'IFF(CURRENT_ROLE() = ''FLQ_M6J_A'', "
                    + "''the owner'', ''the caller'')'");
            assertAnswer(
                "Function INNER_F successfully created.",
                "CREATE OR REPLACE FUNCTION inner_f() RETURNS NUMBER AS 'SELECT COUNT(*) FROM third_t'");
            assertAnswer(
                "Function OUTER_F successfully created.",
                "CREATE OR REPLACE FUNCTION outer_f() RETURNS NUMBER AS 'inner_f() + 10'");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON DATABASE test_db TO ROLE flq_m6j_a");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON SCHEMA test_db.test_schema TO ROLE flq_m6j_a");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON DATABASE test_db TO ROLE flq_m6j_b");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON SCHEMA test_db.test_schema TO ROLE flq_m6j_b");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE third_t TO ROLE flq_m6j_b COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION cnt_third() TO ROLE flq_m6j_a COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION tf_third() TO ROLE flq_m6j_a COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION who_runs() TO ROLE flq_m6j_a COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION inner_f() TO ROLE flq_m6j_a COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION outer_f() TO ROLE flq_m6j_a COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION cnt_third() TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION tf_third() TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION who_runs() TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION outer_f() TO ROLE PUBLIC");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'THIRD_T'. Your "
                    + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.THIRD_T.",
                "SELECT cnt_third()");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'THIRD_T'. Your "
                    + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.THIRD_T.",
                "SELECT v FROM TABLE(tf_third()) ORDER BY 1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'THIRD_T'. Your "
                    + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.THIRD_T.",
                "SELECT cnt_mine()");
            assertAnswer(
                "the caller",
                "SELECT who_runs()");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'THIRD_T'. Your "
                    + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.THIRD_T.",
                "SELECT outer_f()");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE third_t TO ROLE flq_m6j_a");
            assertAnswer(
                "2",
                "SELECT cnt_third()");
            assertAnswer(
                "1 ;; 2",
                "SELECT v FROM TABLE(tf_third()) ORDER BY 1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'THIRD_T'. Your "
                    + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.THIRD_T.",
                "SELECT cnt_mine()");
            assertAnswer(
                "12",
                "SELECT outer_f()");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on function 'INNER_F'. "
                    + "Your primary role <ROLE> must have USAGE granted on FUNCTION TEST_DB.TEST_SCHEMA.INNER_F().",
                "SELECT inner_f()");
        } finally {
            // The scratch roles go, and the session is as it was, whatever the assertions made of it.
            answer("GRANT ROLE flq_m6j_a TO ROLE ACCOUNTADMIN");
            answer("GRANT ROLE flq_m6j_b TO ROLE ACCOUNTADMIN");
            answer("DROP ROLE IF EXISTS flq_m6j_a");
            answer("DROP ROLE IF EXISTS flq_m6j_b");
            answer("USE SECONDARY ROLES ALL");
        }
    }
}
