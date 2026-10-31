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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A missing user is refused in one sentence, and only a statement working under an account privilege goes on, to a
 * role without that privilege: MANAGE GRANTS over a user's grants and parameters, APPLY TAG over its tags. Everything
 * else asks nothing of any role, and it is the privilege that decides, not the role's name (live-verified).
 */
public class MissingUserHintTest extends MeasuredCellsTestSupport {

    private static final String MISSING =
        "REFUSED SQL compilation error: | User 'NOSUCH_HINT_USER' does not exist or not authorized.";

    private static final String WITHOUT_MANAGE_GRANTS =
        MISSING + " Your primary role <ROLE> must have MANAGE GRANTS granted on ACCOUNT <ACCOUNT>.";

    private static final String WITHOUT_APPLY_TAG =
        MISSING + " Your primary role <ROLE> must have APPLY TAG granted on ACCOUNT <ACCOUNT>.";

    private static final String[] ROLES = {"ACCOUNTADMIN", "SECURITYADMIN", "SYSADMIN", "USERADMIN", "PUBLIC"};

    private static final String[] OVER_GRANTS = {
        "GRANT ROLE PUBLIC TO USER nosuch_hint_user",
        "GRANT ROLE nosuch_hint_role TO USER nosuch_hint_user",
        "REVOKE ROLE PUBLIC FROM USER nosuch_hint_user",
        "GRANT DATABASE ROLE test_db.nosuch_hint_dr TO USER nosuch_hint_user",
        "REVOKE DATABASE ROLE test_db.nosuch_hint_dr FROM USER nosuch_hint_user",
        "GRANT OWNERSHIP ON USER nosuch_hint_user TO ROLE PUBLIC",
        "SHOW GRANTS TO USER nosuch_hint_user",
        "SHOW TERSE GRANTS TO USER nosuch_hint_user",
        "SHOW PARAMETERS IN USER nosuch_hint_user",
        "SHOW DELEGATED AUTHORIZATIONS BY USER nosuch_hint_user",
    };

    private static final String[] OVER_TAGS = {
        "ALTER USER nosuch_hint_user SET TAG nosuch_hint_tag = 'x'",
        "ALTER USER nosuch_hint_user UNSET TAG nosuch_hint_tag",
    };

    private static final String[] ASKING_NOTHING = {
        "DROP USER nosuch_hint_user",
        "DESCRIBE USER nosuch_hint_user",
        "COMMENT ON USER nosuch_hint_user IS 'x'",
        "ALTER USER nosuch_hint_user SET COMMENT = 'x'",
        "ALTER USER nosuch_hint_user SET QUERY_TAG = 'x'",
        "ALTER USER nosuch_hint_user UNSET COMMENT",
        "ALTER USER nosuch_hint_user RENAME TO nosuch_hint_user2",
        "ALTER USER nosuch_hint_user SET PASSWORD POLICY nosuch_hint_policy",
    };

    /** The engine's own SYSTEM session holds every privilege under any role, so the test runs as a person does. */
    @BeforeEach
    public void aPersonOfTheAccount() {
        if (!isLiveSnowflake()) {
            answer("CREATE USER hint_person");
            for (final String role : ROLES) {
                answer("GRANT ROLE " + role + " TO USER hint_person");
            }
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser("HINT_PERSON");
        }
        answer("USE ROLE ACCOUNTADMIN");
        answer("USE SECONDARY ROLES NONE");
    }

    @AfterEach
    public void backToTheAdministrator() {
        answer("USE ROLE ACCOUNTADMIN");
        if (!isLiveSnowflake()) {
            engine.setCurrentUser("SYSTEM");
        }
    }

    private void assertEvery(final String expected, final String role, final String... statements) {
        answer("USE ROLE " + role);
        for (final String statement : statements) {
            assertAnswer(expected, statement);
        }
    }

    @Test
    public void aRoleHoldingManageGrantsHearsOneSentence() {
        assertEvery(MISSING, "ACCOUNTADMIN", OVER_GRANTS);
        assertEvery(MISSING, "SECURITYADMIN", OVER_GRANTS);
    }

    @Test
    public void aRoleWithoutManageGrantsIsAskedForIt() {
        assertEvery(WITHOUT_MANAGE_GRANTS, "SYSADMIN", OVER_GRANTS);
        assertEvery(WITHOUT_MANAGE_GRANTS, "USERADMIN", OVER_GRANTS);
        assertEvery(WITHOUT_MANAGE_GRANTS, "PUBLIC", OVER_GRANTS);
    }

    @Test
    public void aRoleWithoutApplyTagIsAskedForIt() {
        assertEvery(MISSING, "ACCOUNTADMIN", OVER_TAGS);
        assertEvery(WITHOUT_APPLY_TAG, "SECURITYADMIN", OVER_TAGS);
        assertEvery(WITHOUT_APPLY_TAG, "SYSADMIN", OVER_TAGS);
        assertEvery(WITHOUT_APPLY_TAG, "USERADMIN", OVER_TAGS);
        assertEvery(WITHOUT_APPLY_TAG, "PUBLIC", OVER_TAGS);
    }

    @Test
    public void everyOtherStatementAsksNothingOfAnyRole() {
        for (final String role : ROLES) {
            assertEvery(MISSING, role, ASKING_NOTHING);
        }
    }

    /** Roles of the account's own hold exactly the privilege they were granted, as a system role does. */
    @Test
    public void thePrivilegeDecidesAndNotTheRolesName() {
        try {
            answer("CREATE OR REPLACE ROLE hint_grants");
            answer("GRANT MANAGE GRANTS ON ACCOUNT TO ROLE hint_grants");
            answer("GRANT ROLE hint_grants TO ROLE ACCOUNTADMIN");
            answer("CREATE OR REPLACE ROLE hint_tags");
            answer("GRANT APPLY TAG ON ACCOUNT TO ROLE hint_tags");
            answer("GRANT ROLE hint_tags TO ROLE ACCOUNTADMIN");
            assertEvery(MISSING, "hint_grants", OVER_GRANTS);
            assertEvery(WITHOUT_APPLY_TAG, "hint_grants", OVER_TAGS);
            assertEvery(WITHOUT_MANAGE_GRANTS, "hint_tags", OVER_GRANTS);
            assertEvery(MISSING, "hint_tags", OVER_TAGS);
        } finally {
            answer("USE ROLE ACCOUNTADMIN");
            answer("DROP ROLE IF EXISTS hint_grants");
            answer("DROP ROLE IF EXISTS hint_tags");
        }
    }
}
