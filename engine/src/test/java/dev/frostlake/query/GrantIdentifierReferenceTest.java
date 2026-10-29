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
 * GRANT and REVOKE take IDENTIFIER() wherever they take a name: the object, with or without its kind, a bulk
 * grant's schema or database, the role or the user that is granted or holds the grant, a database role. The
 * object is found before the grantee, a grantee's reference and its unset variable are read before the object's,
 * and of two roles the one that is to hold the grant is found first. A reference that is not whole is refused where
 * the object's reading stops, and at the parenthesis where the word IDENTIFIER stands as a role's, a user's, a
 * database role's or a bulk scope's own name; one spelling a path names no role, user, database or warehouse.
 * Grants to a role that does not exist change nothing; the grants that take effect are made to a role and a
 * database role the test creates and drops. Every answer is live-verified.
 */
public class GrantIdentifierReferenceTest extends MeasuredCellsTestSupport {

    @Test
    public void aReferenceNamesTheObjectTheScopeAndTheGrantee() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "Statement executed successfully.",
            "SET db = CURRENT_DATABASE()");
        assertAnswer(
            "Statement executed successfully.",
            "SET sch = 'TEST_SCHEMA'");
        assertAnswer(
            "Statement executed successfully.",
            "SET tn = 't'");
        assertAnswer(
            "Statement executed successfully.",
            "SET rv = 'nosuch_role_w7'");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT USAGE ON DATABASE IDENTIFIER($db) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "REVOKE USAGE ON DATABASE IDENTIFIER($db) FROM ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT USAGE ON SCHEMA IDENTIFIER($sch) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON TABLE IDENTIFIER($tn) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON TABLE IDENTIFIER('t') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "REVOKE SELECT ON TABLE IDENTIFIER($tn) FROM ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "GRANT SELECT ON TABLE IDENTIFIER('nosuch_t') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | Session variable '$NOSUCH_VAR' does not "
                + "exist",
            "GRANT SELECT ON TABLE IDENTIFIER($nosuch_var) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON TABLE IDENTIFIER('t') TO ROLE IDENTIFIER('nosuch_role_w7')");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON TABLE IDENTIFIER('t') TO ROLE IDENTIFIER($rv)");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON TABLE t TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "GRANT SELECT ON TABLE nosuch_t TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier ''a b''",
            "GRANT SELECT ON TABLE IDENTIFIER('a b') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier '1'",
            "GRANT SELECT ON TABLE IDENTIFIER(1) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON ALL TABLES IN SCHEMA IDENTIFIER($sch) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON FUTURE TABLES IN SCHEMA IDENTIFIER($sch) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT OWNERSHIP ON TABLE IDENTIFIER($tn) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE2_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE2_W7.",
            "GRANT ROLE IDENTIFIER('nosuch_role_w7') TO ROLE nosuch_role2_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Warehouse 'NOSUCH_WH_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on WAREHOUSE NOSUCH_WH_W7.",
            "GRANT USAGE ON WAREHOUSE IDENTIFIER('nosuch_wh_w7') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | View 'NOSUCH_V' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE NOSUCH_V.",
            "GRANT SELECT ON VIEW IDENTIFIER('nosuch_v') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON TABLE IDENTIFIER('test_schema.t') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON TABLE IDENTIFIER($tn) TO ROLE nosuch_role_w7 WITH GRANT OPTION");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "REVOKE GRANT OPTION FOR SELECT ON TABLE IDENTIFIER($tn) FROM ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "GRANT USAGE ON SCHEMA IDENTIFIER('nosuch_s') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "GRANT USAGE ON DATABASE IDENTIFIER('nosuch_db_w7') TO ROLE nosuch_role_w7");
    }

    @Test
    public void everyNameOfAGrantMayBeAReference() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "Statement executed successfully.",
            "SET db = CURRENT_DATABASE()");
        assertAnswer(
            "Statement executed successfully.",
            "SET sch = 'TEST_SCHEMA'");
        assertAnswer(
            "Statement executed successfully.",
            "SET tn = 't'");
        assertAnswer(
            "Statement executed successfully.",
            "SET rv = 'nosuch_role_w7'");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE2_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE2_W7.",
            "GRANT ROLE nosuch_role_w7 TO ROLE nosuch_role2_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | View 'NOSUCH_V' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE NOSUCH_V.",
            "GRANT SELECT ON VIEW nosuch_v TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Warehouse 'NOSUCH_WH_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on WAREHOUSE NOSUCH_WH_W7.",
            "GRANT USAGE ON WAREHOUSE nosuch_wh_w7 TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "GRANT USAGE ON SCHEMA nosuch_s TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "GRANT USAGE ON DATABASE nosuch_db_w7 TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE2_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE2_W7.",
            "REVOKE ROLE IDENTIFIER('nosuch_role_w7') FROM ROLE IDENTIFIER('nosuch_role2_w7')");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE2_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE2_W7.",
            "REVOKE ROLE nosuch_role_w7 FROM ROLE nosuch_role2_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "GRANT ROLE IDENTIFIER($rv) TO USER IDENTIFIER('nosuch_user_w7')");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "GRANT ROLE nosuch_role_w7 TO USER nosuch_user_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "GRANT SELECT ON TABLE t TO USER IDENTIFIER('nosuch_user_w7')");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "GRANT SELECT ON TABLE t TO USER nosuch_user_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT CREATE DATABASE ON ACCOUNT TO ROLE IDENTIFIER('nosuch_role_w7')");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "REVOKE CREATE DATABASE ON ACCOUNT FROM ROLE IDENTIFIER($rv)");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT MONITOR USAGE ON ACCOUNT TO ROLE IDENTIFIER($rv)");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON ALL TABLES IN DATABASE IDENTIFIER($db) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON FUTURE TABLES IN DATABASE IDENTIFIER($db) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "REVOKE SELECT ON ALL TABLES IN SCHEMA IDENTIFIER($sch) FROM ROLE IDENTIFIER($rv)");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "REVOKE SELECT ON FUTURE TABLES IN SCHEMA IDENTIFIER('nosuch_s') FROM ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "GRANT SELECT ON ALL TABLES IN SCHEMA IDENTIFIER('nosuch_s') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "GRANT SELECT ON ALL TABLES IN DATABASE IDENTIFIER('nosuch_db_w7') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON IDENTIFIER($tn) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "GRANT SELECT ON IDENTIFIER('nosuch_t') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON TABLE IDENTIFIER('test_db.test_schema.t') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\"t\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \"t\".",
            "GRANT SELECT ON TABLE IDENTIFIER('\"t\"') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier ''''",
            "GRANT SELECT ON TABLE IDENTIFIER('') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'NULL'.",
            "GRANT SELECT ON TABLE IDENTIFIER(NULL) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '||'.",
            "GRANT SELECT ON TABLE IDENTIFIER('t' || '1') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier ''a b''",
            "GRANT SELECT ON TABLE t TO ROLE IDENTIFIER('a b')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 43 | Session variable '$NOSUCH_VAR' does not "
                + "exist",
            "GRANT SELECT ON TABLE t TO ROLE IDENTIFIER($nosuch_var)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "GRANT SELECT ON TABLE t TO ROLE IDENTIFIER('x.y')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier '1'",
            "GRANT SELECT ON TABLE t TO ROLE IDENTIFIER(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '('.",
            "GRANT SELECT ON TABLE t TO ROLE IDENTIFIER('r' || '1')");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NOSUCH_F' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_F.",
            "GRANT USAGE ON FUNCTION IDENTIFIER('nosuch_f')(INT) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.NOSUCH_P' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_P.",
            "GRANT USAGE ON PROCEDURE IDENTIFIER('nosuch_p')() TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Sequence 'TEST_DB.TEST_SCHEMA.NOSUCH_SEQ' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SEQUENCE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SEQ.",
            "GRANT USAGE ON SEQUENCE IDENTIFIER('nosuch_seq') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_STG' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_STG.",
            "GRANT READ ON STAGE IDENTIFIER('nosuch_stg') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "GRANT OWNERSHIP ON TABLE IDENTIFIER('nosuch_t') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT OWNERSHIP ON TABLE IDENTIFIER($tn) TO ROLE IDENTIFIER($rv) COPY CURRENT GRANTS");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT DATABASE ROLE IDENTIFIER('nosuch_dr') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT DATABASE ROLE nosuch_dr TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ROLE TEST_DB.NOSUCH_DR.",
            "GRANT SELECT ON TABLE t TO DATABASE ROLE IDENTIFIER('nosuch_dr')");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ROLE TEST_DB.NOSUCH_DR.",
            "GRANT SELECT ON TABLE t TO DATABASE ROLE nosuch_dr");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT DATABASE ROLE nosuch_dr TO ROLE IDENTIFIER($rv)");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "REVOKE DATABASE ROLE IDENTIFIER('nosuch_dr') FROM ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SHOW GRANTS ON TABLE nosuch_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "SHOW GRANTS TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "SHOW GRANTS OF ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "SHOW GRANTS TO USER nosuch_user_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW FUTURE GRANTS IN SCHEMA nosuch_s");
    }

    @Test
    public void aBrokenReferenceIsRefusedWhereItsReadingStops() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "Statement executed successfully.",
            "SET db = CURRENT_DATABASE()");
        assertAnswer(
            "Statement executed successfully.",
            "SET sch = 'TEST_SCHEMA'");
        assertAnswer(
            "Statement executed successfully.",
            "SET tn = 't'");
        assertAnswer(
            "Statement executed successfully.",
            "SET rv = 'nosuch_role_w7'");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT SELECT ON TABLE t TO IDENTIFIER('nosuch_role_w7')");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "REVOKE SELECT ON TABLE t FROM IDENTIFIER($rv)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 50 | Session variable '$NOSUCH_VAR' does not "
                + "exist",
            "GRANT SELECT ON TABLE nosuch_t TO ROLE IDENTIFIER($nosuch_var)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 64 | invalid identifier ''a b''",
            "GRANT SELECT ON TABLE IDENTIFIER('nosuch_t') TO ROLE IDENTIFIER('a b')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 59 | invalid identifier ''c d''",
            "GRANT SELECT ON TABLE IDENTIFIER('a b') TO ROLE IDENTIFIER('c d')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 65 | Session variable '$NOSUCH_VAR2' does not "
                + "exist",
            "GRANT SELECT ON TABLE IDENTIFIER($nosuch_var) TO ROLE IDENTIFIER($nosuch_var2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'IDENTIFIER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE IDENTIFIER.",
            "GRANT SELECT ON TABLE IDENTIFIER() TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ','.",
            "GRANT SELECT ON TABLE IDENTIFIER('t', 2) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | Bind variable for object \"x\" AS x not "
                + "set",
            "GRANT SELECT ON TABLE IDENTIFIER(:x) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'NULL'.",
            "GRANT SELECT ON IDENTIFIER(NULL) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('.",
            "GRANT SELECT ON ALL TABLES IN SCHEMA IDENTIFIER(NULL) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('.",
            "GRANT SELECT ON ALL TABLES IN SCHEMA IDENTIFIER('a' || 'b') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '('.",
            "GRANT SELECT ON FUTURE TABLES IN DATABASE IDENTIFIER(t) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '||'.",
            "REVOKE SELECT ON TABLE IDENTIFIER('t' || '1') FROM ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '('.",
            "REVOKE SELECT ON TABLE t FROM ROLE IDENTIFIER(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '('.",
            "GRANT ROLE IDENTIFIER('r' || '1') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '('.",
            "GRANT ROLE IDENTIFIER(NULL) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 45 | invalid identifier ''a b''",
            "GRANT ROLE nosuch_role_w7 TO USER IDENTIFIER('a b')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "GRANT SELECT ON TABLE t TO USER IDENTIFIER('x.y')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "GRANT USAGE ON DATABASE IDENTIFIER('a.b') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "GRANT USAGE ON SCHEMA IDENTIFIER('a.b.c') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "GRANT SELECT ON TABLE IDENTIFIER('a.b.c.d') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "GRANT USAGE ON WAREHOUSE IDENTIFIER('a.b') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier ''a b''",
            "GRANT CREATE DATABASE ON ACCOUNT TO ROLE IDENTIFIER('a b')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('.",
            "GRANT CREATE DATABASE ON ACCOUNT TO ROLE IDENTIFIER('r' || '1')");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "TEST_DB.NOSUCH_DR2.",
            "GRANT DATABASE ROLE nosuch_dr TO DATABASE ROLE IDENTIFIER('nosuch_dr2')");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "TEST_DB.NOSUCH_DR2.",
            "GRANT DATABASE ROLE nosuch_dr TO DATABASE ROLE nosuch_dr2");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "GRANT DATABASE ROLE nosuch_dr TO USER nosuch_user_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "GRANT DATABASE ROLE IDENTIFIER('nosuch_dr') TO USER IDENTIFIER('nosuch_user_w7')");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "TEST_DB.NOSUCH_DR2.",
            "REVOKE DATABASE ROLE nosuch_dr FROM DATABASE ROLE nosuch_dr2");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "REVOKE DATABASE ROLE nosuch_dr FROM USER nosuch_user_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "GRANT DATABASE ROLE IDENTIFIER('a.b.c') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('.",
            "GRANT DATABASE ROLE IDENTIFIER(NULL) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('.",
            "GRANT DATABASE ROLE IDENTIFIER('a' || 'b') TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('.",
            "GRANT SELECT ON TABLE t TO DATABASE ROLE IDENTIFIER('t' || '1')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('.",
            "GRANT SELECT ON TABLE t TO DATABASE ROLE IDENTIFIER(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "SHOW GRANTS ON ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "SHOW FUTURE GRANTS TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ROLE TEST_DB.NOSUCH_DR.",
            "SHOW GRANTS TO DATABASE ROLE nosuch_dr");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ROLE TEST_DB.NOSUCH_DR.",
            "SHOW GRANTS OF DATABASE ROLE nosuch_dr");
    }

    @Test
    public void grantsThroughReferencesTakeEffect() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "SET db = CURRENT_DATABASE()");
        engine.execute(
            "SET sch = 'TEST_SCHEMA'");
        engine.execute(
            "SET tn = 't'");
        engine.execute(
            "SET rv = 'nosuch_role_w7'");
        engine.execute(
            "DROP ROLE IF EXISTS fl_grant_ref_role");
        try {
            assertAnswer(
                "Role FL_GRANT_REF_ROLE successfully created.",
                "CREATE ROLE fl_grant_ref_role");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON DATABASE IDENTIFIER($db) TO ROLE fl_grant_ref_role");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON SCHEMA IDENTIFIER($sch) TO ROLE IDENTIFIER('fl_grant_ref_role')");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE IDENTIFIER($tn) TO ROLE fl_grant_ref_role");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT INSERT ON TABLE IDENTIFIER('test_schema.t') TO ROLE fl_grant_ref_role WITH GRANT OPTION");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT UPDATE ON IDENTIFIER($tn) TO ROLE fl_grant_ref_role");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "GRANT SELECT ON ALL TABLES IN SCHEMA IDENTIFIER($sch) TO ROLE fl_grant_ref_role");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON FUTURE TABLES IN SCHEMA IDENTIFIER($sch) TO ROLE fl_grant_ref_role");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE SELECT ON TABLE IDENTIFIER($tn) FROM ROLE fl_grant_ref_role");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE GRANT OPTION FOR INSERT ON TABLE IDENTIFIER($tn) FROM ROLE IDENTIFIER('fl_grant_ref_role')");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE UPDATE ON IDENTIFIER($tn) FROM ROLE fl_grant_ref_role");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE USAGE ON SCHEMA IDENTIFIER($sch) FROM ROLE fl_grant_ref_role");
            assertAnswer(
                "Statement executed successfully.",
                "REVOKE SELECT ON FUTURE TABLES IN SCHEMA IDENTIFIER($sch) FROM ROLE fl_grant_ref_role");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE USAGE ON DATABASE IDENTIFIER($db) FROM ROLE fl_grant_ref_role");
            assertAnswer(
                "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE2_W7' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE2_W7.",
                "GRANT ROLE IDENTIFIER('fl_grant_ref_role') TO ROLE IDENTIFIER('nosuch_role2_w7')");
            assertAnswer(
                "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE2_W7' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE2_W7.",
                "REVOKE ROLE IDENTIFIER('fl_grant_ref_role') FROM ROLE IDENTIFIER('nosuch_role2_w7')");
            assertAnswer(
                "FL_GRANT_REF_ROLE successfully dropped.",
                "DROP ROLE IF EXISTS fl_grant_ref_role");
            assertAnswer(
                "",
                "SHOW ROLES LIKE 'FL_GRANT_REF%'");
        } finally {
            engine.execute(
                "DROP ROLE IF EXISTS fl_grant_ref_role");
        }
    }

    @Test
    public void aDatabaseRoleIsGrantedThroughReferences() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "SET db = CURRENT_DATABASE()");
        engine.execute(
            "SET sch = 'TEST_SCHEMA'");
        engine.execute(
            "SET tn = 't'");
        engine.execute(
            "SET rv = 'nosuch_role_w7'");
        engine.execute(
            "DROP ROLE IF EXISTS fl_grant_ref_role");
        try {
            assertAnswer(
                "Role FL_GRANT_REF_ROLE successfully created.",
                "CREATE ROLE fl_grant_ref_role");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "GRANT ROLE IDENTIFIER('x.y') TO ROLE fl_grant_ref_role");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier ''a b''",
                "GRANT ROLE IDENTIFIER('a b') TO ROLE fl_grant_ref_role");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | Session variable '$NOSUCH_VAR' does "
                    + "not exist",
                "GRANT ROLE IDENTIFIER($nosuch_var) TO ROLE fl_grant_ref_role");
            assertAnswer(
                "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
                "GRANT ROLE IDENTIFIER('nosuch_role_w7') TO ROLE fl_grant_ref_role");
            assertAnswer(
                "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
                "REVOKE ROLE IDENTIFIER('nosuch_role_w7') FROM ROLE IDENTIFIER('fl_grant_ref_role')");
            assertAnswer(
                "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                    + "TEST_DB.NOSUCH_DR.",
                "GRANT DATABASE ROLE IDENTIFIER('nosuch_dr') TO ROLE fl_grant_ref_role");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
                "GRANT DATABASE ROLE nosuch_db_w7.dr TO ROLE fl_grant_ref_role");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "GRANT DATABASE ROLE IDENTIFIER('a.b.c') TO ROLE fl_grant_ref_role");
            assertAnswer(
                "Role TEST_DB.DR_GRANT_REF successfully created.",
                "CREATE DATABASE ROLE dr_grant_ref");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT DATABASE ROLE IDENTIFIER('dr_grant_ref') TO ROLE IDENTIFIER('fl_grant_ref_role')");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE IDENTIFIER($tn) TO DATABASE ROLE IDENTIFIER('dr_grant_ref')");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE SELECT ON TABLE IDENTIFIER($tn) FROM DATABASE ROLE IDENTIFIER('dr_grant_ref')");
            assertAnswer(
                "Statement executed successfully.",
                "REVOKE DATABASE ROLE IDENTIFIER('dr_grant_ref') FROM ROLE IDENTIFIER('fl_grant_ref_role')");
            assertAnswer(
                "TEST_DB.DR_GRANT_REF successfully dropped.",
                "DROP DATABASE ROLE dr_grant_ref");
            assertAnswer(
                "FL_GRANT_REF_ROLE successfully dropped.",
                "DROP ROLE IF EXISTS fl_grant_ref_role");
            assertAnswer(
                "",
                "SHOW ROLES LIKE 'FL_GRANT_REF%'");
        } finally {
            engine.execute(
                "DROP ROLE IF EXISTS fl_grant_ref_role");
        }
    }
}
