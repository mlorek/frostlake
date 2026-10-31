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
 * USE SECONDARY ROLES and what the session shows of it. While secondary roles are in use a missing-object
 * refusal addresses the primary role or one of the secondary roles, except inside a procedure running with
 * its owner's rights. CURRENT_SECONDARY_ROLES() answers the roles named, each once and in alphabetical order,
 * and nothing after NONE; a role that is not there is refused and the session keeps what it had; the
 * statement without its roles is a syntax error at its end. Every answer is live-verified.
 */
public class SecondaryRolesSessionTest extends MeasuredCellsTestSupport {

    @Test
    public void aRefusalAddressesTheSecondaryRolesInUse() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_l3() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  SELECT * FROM "
                + "nosuch_t;\n  RETURN 'x';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_c3() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$\nBEGIN\n  "
                + "SELECT * FROM nosuch_t;\n  RETURN 'x';\nEND;\n$$");
        try {
            assertAnswer(
                "{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES ALL");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your "
                    + "primary role <ROLE> or one of your secondary roles must have at least one privilege granted "
                    + "on TABLE NOSUCH_T.",
                "SELECT * FROM nosuch_t");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                    + "primary role <ROLE> or one of your secondary roles must have USAGE or any other privilege "
                    + "granted on DATABASE NOSUCH_DB_W7.",
                "DROP DATABASE nosuch_db_w7");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized.",
                "DROP USER nosuch_user_w7");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                    + "role <ROLE> or one of your secondary roles must have at least one privilege granted on TABLE "
                    + "NOSUCH_T.",
                "DESCRIBE TABLE nosuch_t");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                    + "role <ROLE> or one of your secondary roles must have at least one privilege granted on TABLE "
                    + "NOSUCH_T.",
                "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_t', 'table'))");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | Object 'NOSUCH_T' does not exist or not authorized. Your primary role <ROLE> or one "
                    + "of your secondary roles must have at least one privilege granted on TABLE NOSUCH_T.",
                "BEGIN SELECT * FROM nosuch_t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation "
                    + "error: | Object 'NOSUCH_T' does not exist or not authorized. This executable runs with "
                    + "owner's rights. The owner role <ROLE> must have at least one privilege granted on TABLE "
                    + "NOSUCH_T.",
                "CALL p_l3()");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation "
                    + "error: | Object 'NOSUCH_T' does not exist or not authorized. Your primary role <ROLE> or one "
                    + "of your secondary roles must have at least one privilege granted on TABLE NOSUCH_T.",
                "CALL p_c3()");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. "
                    + "Your primary role <ROLE> or one of your secondary roles must have USAGE or any other "
                    + "privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
                "DROP SCHEMA nosuch_s");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. "
                    + "Your primary role <ROLE> or one of your secondary roles must have USAGE or any other "
                    + "privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
                "SELECT * FROM nosuch_s.nosuch_t");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
                "SELECT * FROM nosuch_t");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE SECONDARY ROLES nosuch_role_w7");
            assertAnswer(
                "{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','.",
                "USE SECONDARY ROLES ALL, NONE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
                "USE SECONDARY ROLES");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES \"ALL\"");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ''ALL''.",
                "USE SECONDARY ROLES 'ALL'");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
        } finally {
            engine.execute(
                "USE SECONDARY ROLES NONE");
        }
    }

    @Test
    public void theRolesNamedAreKeptSortedAndOnce() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES ORGADMIN, ACCOUNTADMIN");
            assertAnswer(
                "{\"roles\":\"ACCOUNTADMIN,ORGADMIN\",\"value\":\"ACCOUNTADMIN,ORGADMIN\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES ACCOUNTADMIN, ORGADMIN");
            assertAnswer(
                "{\"roles\":\"ACCOUNTADMIN,ORGADMIN\",\"value\":\"ACCOUNTADMIN,ORGADMIN\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES accountadmin, accountadmin");
            assertAnswer(
                "{\"roles\":\"ACCOUNTADMIN\",\"value\":\"ACCOUNTADMIN\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES \"ALL\"");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE, ORGADMIN");
            assertAnswer(
                "{\"roles\":\"ORGADMIN\",\"value\":\"ORGADMIN\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','.",
                "USE SECONDARY ROLES ALL, ORGADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES none");
            assertAnswer(
                "{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES \"none\"");
            assertAnswer(
                "{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'.",
                "USE SECONDARY ROLES;");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES ORGADMIN");
            assertAnswer(
                "{\"roles\":\"ORGADMIN\",\"value\":\"ORGADMIN\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your "
                    + "primary role <ROLE> or one of your secondary roles must have at least one privilege granted "
                    + "on TABLE NOSUCH_T.",
                "SELECT * FROM nosuch_t");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE SECONDARY ROLES ORGADMIN, nosuch_role_w7");
            assertAnswer(
                "{\"roles\":\"ORGADMIN\",\"value\":\"ORGADMIN\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(CURRENT_SECONDARY_ROLES())");
            assertShape(
                "CURRENT_SECONDARY_ROLES():VARCHAR(134217728)",
                "SELECT CURRENT_SECONDARY_ROLES()");
        } finally {
            engine.execute(
                "USE SECONDARY ROLES NONE");
        }
    }
}
