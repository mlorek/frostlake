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
 * USE ROLE activates a role the session's user may activate, and no other. A role that merely exists - one
 * the session itself just created - is refused, {@code Requested role 'R' is not assigned to the executing
 * user.  Specify another role to activate.}, and the session keeps the role it has; a role that is not there
 * is refused as an object USE cannot reach. A role granted to a role the user holds is activated, and so is
 * one granted to that role in turn, until the grant is revoked; the system roles under ACCOUNTADMIN and PUBLIC
 * are activated by a user who holds ACCOUNTADMIN. USE SECONDARY ROLES holds each role it names to the same
 * rule, after it has found every one of them. Every answer is live-verified.
 */
public class UseRoleAssignmentTest extends MeasuredCellsTestSupport {

    @Test
    public void aRoleIsActivatedOnlyByAUserItReaches() {
        try {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "DROP ROLE IF EXISTS flq_ur_child");
            answer(
                "DROP ROLE IF EXISTS flq_ur_r");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "Role FLQ_UR_R successfully created.",
                "CREATE ROLE flq_ur_r");
            assertAnswer(
                "",
                "SHOW GRANTS OF ROLE flq_ur_r");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "ACCOUNTADMIN",
                "SELECT CURRENT_ROLE()");
            assertAnswer(
                "REFUSED SQL access control error: | Requested role 'FLQ_UR_R' is not assigned to the executing "
                    + "user.  Specify another role to activate.",
                "USE ROLE flq_ur_r");
            assertAnswer(
                "ACCOUNTADMIN",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "REFUSED SQL access control error: | Requested role 'FLQ_UR_R' is not assigned to the executing "
                    + "user.  Specify another role to activate.",
                "USE ROLE FLQ_UR_R");
            assertAnswer(
                "ACCOUNTADMIN",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "REFUSED SQL access control error: | Requested role 'FLQ_UR_R' is not assigned to the executing "
                    + "user.  Specify another role to activate.",
                "USE ROLE \"FLQ_UR_R\"");
            assertAnswer(
                "ACCOUNTADMIN",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "USE ROLE \"flq_ur_r\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE ROLE nosuch_role_flq");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE ROLE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ''flq_ur_r''.",
                "USE ROLE 'flq_ur_r'");
            assertAnswer(
                "REFUSED SQL access control error: | Requested role 'FLQ_UR_R' is not assigned to the executing "
                    + "user.  Specify another role to activate.",
                "USE ROLE IDENTIFIER('flq_ur_r')");
            assertAnswer(
                "ACCOUNTADMIN",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "false|true|false|true",
                "SELECT ARRAY_CONTAINS('FLQ_UR_R'::VARIANT, PARSE_JSON(CURRENT_AVAILABLE_ROLES())), "
                    + "ARRAY_CONTAINS('ACCOUNTADMIN'::VARIANT, PARSE_JSON(CURRENT_AVAILABLE_ROLES())), "
                    + "IS_ROLE_IN_SESSION('FLQ_UR_R'), IS_ROLE_IN_SESSION('ACCOUNTADMIN')");
            assertAnswer(
                "REFUSED SQL access control error: | Requested role 'FLQ_UR_R' is not assigned to the executing "
                    + "user.  Specify another role to activate.",
                "USE SECONDARY ROLES flq_ur_r");
            assertAnswer(
                "{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE SECONDARY ROLES flq_ur_r, nosuch_role_flq");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE SECONDARY ROLES nosuch_role_flq");
            assertAnswer(
                "{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            answer(
                "GRANT ROLE flq_ur_r TO ROLE ACCOUNTADMIN");
            answer(
                "SHOW GRANTS OF ROLE flq_ur_r");
            assertAnswer(
                "FLQ_UR_R|ROLE|ACCOUNTADMIN",
                "SELECT \"role\", \"granted_to\", \"grantee_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Statement executed successfully.",
                "USE ROLE flq_ur_r");
            assertAnswer(
                "FLQ_UR_R",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "ACCOUNTADMIN",
                "SELECT CURRENT_ROLE()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES flq_ur_r");
            assertAnswer(
                "{\"roles\":\"FLQ_UR_R\",\"value\":\"FLQ_UR_R\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "Role FLQ_UR_CHILD successfully created.",
                "CREATE ROLE flq_ur_child");
            assertAnswer(
                "REFUSED SQL access control error: | Requested role 'FLQ_UR_CHILD' is not assigned to the "
                    + "executing user.  Specify another role to activate.",
                "USE ROLE flq_ur_child");
            assertAnswer(
                "ACCOUNTADMIN",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ROLE flq_ur_child TO ROLE flq_ur_r");
            assertAnswer(
                "Statement executed successfully.",
                "USE ROLE flq_ur_child");
            assertAnswer(
                "FLQ_UR_CHILD",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES flq_ur_child");
            assertAnswer(
                "{\"roles\":\"FLQ_UR_CHILD\",\"value\":\"FLQ_UR_CHILD\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "Statement executed successfully.",
                "USE ROLE SYSADMIN");
            assertAnswer(
                "SYSADMIN",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE ROLE SECURITYADMIN");
            assertAnswer(
                "SECURITYADMIN",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE ROLE USERADMIN");
            assertAnswer(
                "USERADMIN",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE ROLE PUBLIC");
            assertAnswer(
                "PUBLIC",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES SYSADMIN, PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "Statement executed successfully.",
                "REVOKE ROLE flq_ur_r FROM ROLE ACCOUNTADMIN");
            assertAnswer(
                "REFUSED SQL access control error: | Requested role 'FLQ_UR_R' is not assigned to the executing "
                    + "user.  Specify another role to activate.",
                "USE ROLE flq_ur_r");
            assertAnswer(
                "ACCOUNTADMIN",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "REFUSED SQL access control error: | Requested role 'FLQ_UR_CHILD' is not assigned to the "
                    + "executing user.  Specify another role to activate.",
                "USE ROLE flq_ur_child");
            assertAnswer(
                "ACCOUNTADMIN",
                "SELECT CURRENT_ROLE()");
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "GRANT ROLE flq_ur_r TO ROLE ACCOUNTADMIN");
            answer(
                "DROP ROLE IF EXISTS flq_ur_child");
            answer(
                "DROP ROLE IF EXISTS flq_ur_r");
            answer(
                "USE SECONDARY ROLES ALL");
            assertAnswer(
                "ACCOUNTADMIN",
                "SELECT CURRENT_ROLE()");
            assertAnswer(
                "",
                "SHOW ROLES LIKE 'FLQ_UR%'");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "GRANT ROLE flq_ur_r TO ROLE ACCOUNTADMIN");
            answer(
                "DROP ROLE IF EXISTS flq_ur_child");
            answer(
                "DROP ROLE IF EXISTS flq_ur_r");
            answer(
                "USE SECONDARY ROLES ALL");
        }
    }
}
