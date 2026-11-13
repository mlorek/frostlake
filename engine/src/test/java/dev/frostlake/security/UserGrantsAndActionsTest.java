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
 * A user as the object of grants and of ALTER USER's two actions (live-verified). A user is granted on by its own
 * privileges — MONITOR, IMPERSONATE, MODIFY PROGRAMMATIC AUTHENTICATION METHODS and the four policy applications, ALL
 * standing for them — listed by SHOW GRANTS ON USER after its OWNERSHIP; any other privilege is refused for the kind,
 * and a REVOKE counts each privilege it takes back. A user takes account privileges too, but none that creates an
 * object, and a privilege of no account ON ACCOUNT is refused for the kind. ALTER USER … RESET PASSWORD answers the
 * account's login link in one status column, looking the user up even under IF EXISTS; ABORT ALL QUERIES answers
 * as any ALTER does.
 *
 * <p>The statements change the account's users, roles and grants, so on the account they run only where the
 * harness sends them (see {@link dev.frostlake.LiveAccountStatements}).
 */
public class UserGrantsAndActionsTest extends MeasuredCellsTestSupport {

    /** What a statement that changes something and answers nothing of its own reads as. */
    private static final String DONE = "Statement executed successfully.";

    /** The grants the last SHOW GRANTS listed, by privilege, the moment and the account's name aside. */
    private static final String GRANTS = """
        SELECT LISTAGG("privilege" || ':' || "granted_on" || ':' || "name" || ':' || "granted_to" || ':' || \
        "grantee_name" || ':' || "grant_option" || ':' || "granted_by", ',') WITHIN GROUP (ORDER BY "privilege") \
        FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))""";

    /** The grants SHOW GRANTS TO USER listed, by privilege, the object's name and its grantor aside. */
    private static final String USER_GRANTS = """
        SELECT LISTAGG("privilege" || ':' || "granted_on" || ':' || "granted_to" || ':' || "grantee_name" || ':' || \
        COALESCE("role", '-'), ',') WITHIN GROUP (ORDER BY "privilege") FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))""";

    /** The refusal of an account privilege that creates objects, granted to a user. */
    private static final String NO_CREATION = "REFUSED SQL execution error: Cannot grant object creation privileges to "
        + "users: users cannot own objects outside their personal database. Use the personal database for creating "
        + "user-owned objects.";

    /** Each of a user's own privileges, as ALL grants them to the role ug_r, with the given option on MONITOR. */
    private static String everyUserPrivilege(final String monitorOption) {
        return "APPLY AUTHENTICATION POLICY:USER:UG_U:ROLE:UG_R:false:ACCOUNTADMIN,"
            + "APPLY INGRESS POLICY:USER:UG_U:ROLE:UG_R:false:ACCOUNTADMIN,"
            + "APPLY PASSWORD POLICY:USER:UG_U:ROLE:UG_R:false:ACCOUNTADMIN,"
            + "APPLY SESSION POLICY:USER:UG_U:ROLE:UG_R:false:ACCOUNTADMIN,"
            + "IMPERSONATE:USER:UG_U:ROLE:UG_R:false:ACCOUNTADMIN,"
            + "MODIFY PROGRAMMATIC AUTHENTICATION METHODS:USER:UG_U:ROLE:UG_R:false:ACCOUNTADMIN,"
            + "MONITOR:USER:UG_U:ROLE:UG_R:" + monitorOption + ":ACCOUNTADMIN";
    }

    /** Makes the scratch role and user, as the account's administrator. */
    private void aUserAndARole() {
        answer("USE ROLE ACCOUNTADMIN");
        answer("DROP USER IF EXISTS ug_u");
        answer("DROP ROLE IF EXISTS ug_r");
        answer("CREATE ROLE ug_r");
        answer("CREATE USER ug_u PASSWORD = 'Abcdefghijklm0'");
    }

    /** Drops the scratch role and user. */
    private void dropUserAndRole() {
        answer("USE ROLE ACCOUNTADMIN");
        answer("DROP USER IF EXISTS ug_u");
        answer("DROP ROLE IF EXISTS ug_r");
    }

    @Test
    public void aUserIsGrantedOnByItsOwnPrivileges() {
        aUserAndARole();
        try {
            assertAnswer(DONE, "GRANT MONITOR ON USER ug_u TO ROLE ug_r");
            assertAnswer(DONE, "GRANT MONITOR ON USER ug_u TO ROLE ug_r");
            engine.executeQuery("SHOW GRANTS ON USER ug_u");
            assertAnswer("MONITOR:USER:UG_U:ROLE:UG_R:false:ACCOUNTADMIN,"
                + "OWNERSHIP:USER:UG_U:ROLE:ACCOUNTADMIN:true:ACCOUNTADMIN", GRANTS);
            assertAnswer(DONE, "GRANT ALL ON USER ug_u TO ROLE ug_r");
            engine.executeQuery("SHOW GRANTS ON USER ug_u");
            assertAnswer(everyUserPrivilege("false") + ",OWNERSHIP:USER:UG_U:ROLE:ACCOUNTADMIN:true:ACCOUNTADMIN",
                GRANTS);
            assertAnswer("REFUSED SQL compilation error: | Invalid object type 'USER' for privilege 'SELECT'.",
                "GRANT SELECT ON USER ug_u TO ROLE ug_r");
            assertAnswer(DONE, "GRANT MONITOR ON USER ug_u TO ROLE ug_r WITH GRANT OPTION");
            engine.executeQuery("SHOW GRANTS TO ROLE ug_r");
            assertAnswer(everyUserPrivilege("true"), GRANTS);
            assertAnswer("Statement executed successfully. 7 objects affected.",
                "REVOKE ALL ON USER ug_u FROM ROLE ug_r");
            assertAnswer("Statement executed successfully. 0 objects affected.",
                "REVOKE MONITOR ON USER ug_u FROM ROLE ug_r");
            assertAnswer(DONE, "GRANT OWNERSHIP ON USER ug_u TO ROLE ug_r");
            engine.executeQuery("SHOW GRANTS ON USER ug_u");
            assertAnswer("OWNERSHIP:USER:UG_U:ROLE:UG_R:true:UG_R", GRANTS);
            assertAnswer(DONE, "GRANT OWNERSHIP ON USER ug_u TO ROLE ACCOUNTADMIN COPY CURRENT GRANTS");
            assertAnswer(DONE, "GRANT ALL PRIVILEGES ON USER ug_u TO ROLE ug_r");
            assertAnswer("Statement executed successfully. 1 objects affected.",
                "REVOKE IMPERSONATE ON USER ug_u FROM ROLE ug_r");
            assertAnswer(DONE, "GRANT MONITOR, IMPERSONATE ON USER ug_u TO ROLE ug_r");
            assertAnswer(DONE, "GRANT MONITOR ON USER ug_u TO USER ug_u");
        } finally {
            dropUserAndRole();
        }
    }

    @Test
    public void aUserTakesAccountPrivilegesThatCreateNothing() {
        aUserAndARole();
        try {
            assertAnswer(NO_CREATION, "GRANT CREATE DATABASE ON ACCOUNT TO USER ug_u");
            engine.executeQuery("SHOW GRANTS TO USER ug_u");
            assertAnswer("0", "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer("Statement executed successfully. 0 objects affected.",
                "REVOKE CREATE DATABASE ON ACCOUNT FROM USER ug_u");
            assertAnswer(DONE, "GRANT EXECUTE TASK ON ACCOUNT TO USER ug_u");
            engine.executeQuery("SHOW GRANTS TO USER ug_u");
            assertAnswer("EXECUTE TASK:ACCOUNT:USER:UG_U:-", USER_GRANTS);
            assertAnswer("Statement executed successfully. 1 objects affected.",
                "REVOKE EXECUTE TASK ON ACCOUNT FROM USER ug_u");
            assertAnswer(NO_CREATION, "GRANT CREATE ROLE ON ACCOUNT TO USER ug_u");
            assertAnswer(DONE, "GRANT MONITOR USAGE ON ACCOUNT TO USER ug_u");
            engine.executeQuery("SHOW GRANTS TO USER ug_u");
            assertAnswer("MONITOR USAGE:ACCOUNT:USER:UG_U:-", USER_GRANTS);
            assertAnswer("Statement executed successfully. 1 objects affected.",
                "REVOKE MONITOR USAGE ON ACCOUNT FROM USER ug_u");
            assertAnswer(DONE, "GRANT USAGE ON DATABASE test_db TO USER ug_u");
            engine.executeQuery("SHOW GRANTS TO USER ug_u");
            assertAnswer("USAGE:DATABASE:USER:UG_U:-", USER_GRANTS);
            assertAnswer("Statement executed successfully. 1 objects affected.",
                "REVOKE USAGE ON DATABASE test_db FROM USER ug_u");
            assertAnswer(DONE, "GRANT ROLE ug_r TO USER ug_u");
            engine.executeQuery("SHOW GRANTS TO USER ug_u");
            assertAnswer("USAGE:ROLE:USER:UG_U:UG_R", USER_GRANTS);
            assertAnswer("REFUSED SQL compilation error: | Invalid object type 'ACCOUNT' for privilege "
                + "'CREATE SCHEMA'.", "GRANT CREATE SCHEMA ON ACCOUNT TO USER ug_u");
            assertAnswer(NO_CREATION, "GRANT EXECUTE TASK, CREATE ROLE ON ACCOUNT TO USER ug_u");
            assertAnswer("Statement executed successfully. 0 objects affected.",
                "REVOKE ALL ON ACCOUNT FROM USER ug_u");
        } finally {
            dropUserAndRole();
        }
    }

    @Test
    public void grantListingsOfAUserAndARoleHaveTheirOwnColumns() {
        aUserAndARole();
        try {
            assertShape("created_on:TIMESTAMP_LTZ, privilege:VARCHAR(16777216), granted_on:VARCHAR(16777216), "
                + "name:VARCHAR(16777216), role:VARCHAR(16777216), granted_to:VARCHAR(16777216), "
                + "grantee_name:VARCHAR(16777216), grant_option:VARCHAR(16777216), granted_by:VARCHAR(16777216), "
                + "expires_on:TIMESTAMP_LTZ", "SHOW GRANTS TO USER ug_u");
            assertShape("created_on:TIMESTAMP_LTZ, privilege:VARCHAR(16777216), granted_on:VARCHAR(16777216), "
                + "name:VARCHAR(16777216), granted_to:VARCHAR(16777216), grantee_name:VARCHAR(16777216), "
                + "grant_option:VARCHAR(16777216), granted_by:VARCHAR(16777216), "
                + "granted_by_role_type:VARCHAR(16777216)", "SHOW GRANTS ON USER ug_u");
            assertShape("created_on:TIMESTAMP_LTZ, privilege:VARCHAR(16777216), granted_on:VARCHAR(16777216), "
                + "name:VARCHAR(16777216), granted_to:VARCHAR(16777216), grantee_name:VARCHAR(16777216), "
                + "grant_option:VARCHAR(16777216), granted_by:VARCHAR(16777216)", "SHOW GRANTS TO ROLE ug_r");
            assertShape("created_on:TIMESTAMP_LTZ, role:VARCHAR(16777216), granted_to:VARCHAR(16777216), "
                + "grantee_name:VARCHAR(16777216), granted_by:VARCHAR(16777216), expires_on:TIMESTAMP_LTZ",
                "SHOW GRANTS OF ROLE ug_r");
        } finally {
            dropUserAndRole();
        }
    }

    @Test
    public void alterUserResetsAPasswordAndAbortsItsQueries() {
        aUserAndARole();
        try {
            assertAnswer(DONE, "ALTER USER ug_u ABORT ALL QUERIES");
            assertShape("status:VARCHAR(16777216)", "ALTER USER ug_u RESET PASSWORD");
            engine.executeQuery("ALTER USER ug_u RESET PASSWORD");
            assertAnswer("true", """
                SELECT REGEXP_LIKE("status", '^https://' || LOWER(CURRENT_ORGANIZATION_NAME()) || '-' || \
                LOWER(CURRENT_ACCOUNT_NAME()) || '\\\\.snowflakecomputing\\\\.com/console/login\\\\?\
                resetPasswordToken=ver%3A3-hint%3A.*') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))""");
            assertAnswer("REFUSED SQL compilation error: | User 'NO_SUCH_UG' does not exist or not authorized.",
                "ALTER USER IF EXISTS no_such_ug RESET PASSWORD");
            assertAnswer(DONE, "ALTER USER IF EXISTS no_such_ug ABORT ALL QUERIES");
        } finally {
            dropUserAndRole();
        }
    }
}
