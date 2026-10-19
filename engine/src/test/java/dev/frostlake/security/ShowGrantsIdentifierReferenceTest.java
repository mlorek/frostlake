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
 * SHOW GRANTS and SHOW FUTURE GRANTS name what they list by an IDENTIFIER() reference as by a name: a table, a
 * schema, a database or a bare relation after ON, a role or a user after TO, OF and ON ROLE, a schema or a database
 * after IN, a database role — a session variable, a string or a bind spelling it; a reference to nothing is refused
 * as the name is, a path where one part is read names nothing, and IDENTIFIER(NULL) is refused at the NULL (all
 * live-verified).
 */
public class ShowGrantsIdentifierReferenceTest extends MeasuredCellsTestSupport {

    @Test
    public void aGrantListingNamesItsObjectByAReference() {
        engine.execute("CREATE OR REPLACE TABLE gt (a INT)");
        engine.execute("SET tn = 'gt'");
        engine.execute("SET sch = 'test_schema'");
        engine.execute("SET db = 'test_db'");
        engine.execute("SET r = CURRENT_ROLE()");
        engine.execute("SET rv = 'nosuch_role_w7'");
        answer("SHOW GRANTS ON TABLE IDENTIFIER($tn)");
        assertAnswer("true|OWNERSHIP|TABLE|ROLE|true",
            "SELECT COUNT(*) > 0, MIN(\"privilege\"), MAX(\"granted_on\"), MAX(\"granted_to\"), "
                + "BOOLOR_AGG(\"grantee_name\" = CURRENT_ROLE()) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("SHOW GRANTS ON IDENTIFIER($tn)");
        assertAnswer("true|OWNERSHIP|TABLE|ROLE|true",
            "SELECT COUNT(*) > 0, MIN(\"privilege\"), MAX(\"granted_on\"), MAX(\"granted_to\"), "
                + "BOOLOR_AGG(\"grantee_name\" = CURRENT_ROLE()) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("SHOW GRANTS ON TABLE IDENTIFIER('gt')");
        assertAnswer("true|OWNERSHIP|TABLE|ROLE|true",
            "SELECT COUNT(*) > 0, MIN(\"privilege\"), MAX(\"granted_on\"), MAX(\"granted_to\"), "
                + "BOOLOR_AGG(\"grantee_name\" = CURRENT_ROLE()) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("SHOW GRANTS ON SCHEMA IDENTIFIER($sch)");
        assertAnswer("true|OWNERSHIP|SCHEMA|ROLE|true",
            "SELECT COUNT(*) > 0, MIN(\"privilege\"), MAX(\"granted_on\"), MAX(\"granted_to\"), "
                + "BOOLOR_AGG(\"grantee_name\" = CURRENT_ROLE()) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("SHOW GRANTS ON DATABASE IDENTIFIER($db)");
        assertAnswer("true|OWNERSHIP|DATABASE|ROLE|true",
            "SELECT COUNT(*) > 0, MIN(\"privilege\"), MAX(\"granted_on\"), MAX(\"granted_to\"), "
                + "BOOLOR_AGG(\"grantee_name\" = CURRENT_ROLE()) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("SHOW GRANTS TO ROLE IDENTIFIER($r)");
        assertAnswer("true", "SELECT COUNT(*) > 0 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("SHOW GRANTS ON ROLE IDENTIFIER($r)");
        answer("SELECT COUNT(*) > 0 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("SHOW GRANTS OF ROLE IDENTIFIER($r)");
        assertAnswer("true", "SELECT COUNT(*) > 0 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("", "SHOW FUTURE GRANTS IN SCHEMA IDENTIFIER($sch)");
        assertAnswer("", "SHOW FUTURE GRANTS IN DATABASE IDENTIFIER($db)");
        assertAnswer("REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SHOW GRANTS ON TABLE IDENTIFIER('nosuch_t')");
        assertAnswer("REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "SHOW GRANTS TO ROLE IDENTIFIER('nosuch_role_w7')");
        assertAnswer("REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "SHOW GRANTS TO ROLE IDENTIFIER($rv)");
        assertAnswer("REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "SHOW GRANTS OF ROLE IDENTIFIER($rv)");
        assertAnswer("REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "SHOW GRANTS ON ROLE IDENTIFIER($rv)");
        assertAnswer("REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "SHOW FUTURE GRANTS TO ROLE IDENTIFIER($rv)");
        answer("SHOW GRANTS TO USER IDENTIFIER('nosuch_user_w7')");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW FUTURE GRANTS IN SCHEMA IDENTIFIER('nosuch_s')");
        assertAnswer("REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SHOW FUTURE GRANTS IN DATABASE IDENTIFIER('nosuch_db_w7')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'NULL'.",
            "SHOW GRANTS ON TABLE IDENTIFIER(NULL)");
        assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW GRANTS TO ROLE IDENTIFIER('x.y')");
        assertAnswer("REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "TEST_DB.NOSUCH_DR.",
            "SHOW GRANTS TO DATABASE ROLE IDENTIFIER('nosuch_dr')");
        assertAnswer("REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "TEST_DB.NOSUCH_DR.",
            "SHOW GRANTS OF DATABASE ROLE IDENTIFIER('nosuch_dr')");
        assertAnswer("REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "TEST_DB.NOSUCH_DR.",
            "SHOW GRANTS ON DATABASE ROLE IDENTIFIER('nosuch_dr')");
        assertAnswer("REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "TEST_DB.NOSUCH_DR.",
            "SHOW FUTURE GRANTS TO DATABASE ROLE IDENTIFIER('nosuch_dr')");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW GRANTS ON SCHEMA IDENTIFIER('nosuch_s')");
        assertAnswer("REFUSED SQL compilation error: | View 'NOSUCH_V' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_V.",
            "SHOW GRANTS ON VIEW IDENTIFIER('nosuch_v')");
        assertAnswer("REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SHOW GRANTS ON TABLE IDENTIFIER('test_db.test_schema.nosuch_t')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 32 | Session variable '$NOSUCH_VAR' "
                + "does not exist",
            "SHOW GRANTS ON TABLE IDENTIFIER($nosuch_var)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 32 | Bind variable for object \"tn\" AS "
                + "tn not set",
            "SHOW GRANTS ON TABLE IDENTIFIER(:tn)");
    }
}
