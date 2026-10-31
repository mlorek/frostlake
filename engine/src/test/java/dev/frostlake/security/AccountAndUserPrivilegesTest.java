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

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The privileges statements on the account and on users ask: ALTER ACCOUNT SET and UNSET need MODIFY on the account,
 * which ACCOUNTADMIN holds; CREATE USER needs CREATE USER on the account, which USERADMIN and the roles above it
 * hold or a grant gives; ALTER USER needs MODIFY on the user, which its owner holds - and users change their own
 * defaults and session parameters without it (documented). Each is asked once the statement's names and values are
 * judged (live-verified). The live account allows no CREATE ROLE, CREATE USER or GRANT, so the rule is held by the
 * embedded engine.
 */
public class AccountAndUserPrivilegesTest {

    private DatabaseEngine engine;
    private String systemRole;
    private String account;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        account = engine.getCatalog().getAccountLocator();
        engine.execute("CREATE ROLE r1");
        // The user puts no secondary role in use, so a refusal names the primary role alone.
        engine.execute("CREATE USER u DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("CREATE USER other");
        engine.execute("GRANT ROLE r1 TO USER u");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void asUser(final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("u");
        engine.setCurrentRole(role);
    }

    private void asSystem() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("SYSTEM");
        engine.setCurrentRole(systemRole);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String onAccount(final String role, final String privilege) {
        return "SQL access control error:\nInsufficient privileges to operate on account '" + account
            + "'. Your primary role " + role + " must have " + privilege + " granted on ACCOUNT " + account + ".";
    }

    @Test
    public void alterAccountAsksModifyOnTheAccountAfterTheList() {
        asUser("PUBLIC");
        assertEquals(onAccount("PUBLIC", "MODIFY"), refusal("ALTER ACCOUNT SET JSON_INDENT = 2"));
        assertEquals(onAccount("PUBLIC", "MODIFY"), refusal("ALTER ACCOUNT UNSET JSON_INDENT"));
        assertTrue(refusal("ALTER ACCOUNT SET JSON_INDENT = 'x'").contains("invalid value ['x'] for parameter"));
        assertTrue(refusal("ALTER ACCOUNT SET NOSUCH = 2").contains("invalid property 'NOSUCH'"));
        asUser("r1");
        assertEquals(onAccount("R1", "MODIFY"), refusal("ALTER ACCOUNT SET JSON_INDENT = 2"));
        asSystem();
        engine.execute("GRANT ROLE ACCOUNTADMIN TO USER u");
        asUser("ACCOUNTADMIN");
        engine.execute("ALTER ACCOUNT SET JSON_INDENT = 2");
        engine.execute("ALTER ACCOUNT UNSET JSON_INDENT");
    }

    @Test
    public void createUserAsksCreateUserOnTheAccount() {
        asUser("r1");
        assertEquals(onAccount("R1", "CREATE USER"), refusal("CREATE USER zz"));
        asSystem();
        engine.execute("GRANT CREATE USER ON ACCOUNT TO ROLE r1");
        asUser("r1");
        engine.execute("CREATE USER zz");
        asSystem();
        engine.execute("GRANT ROLE USERADMIN TO USER u");
        asUser("USERADMIN");
        engine.execute("CREATE USER yy");
        assertEquals(onAccount("USERADMIN", "MODIFY"), refusal("ALTER ACCOUNT SET JSON_INDENT = 2"));
    }

    @Test
    public void alterUserAsksModifyOnTheUserAfterNamesAndValues() {
        asUser("r1");
        assertEquals("""
            SQL access control error:
            Insufficient privileges to operate on user 'OTHER'. Your primary role R1 must have MODIFY granted on \
            USER OTHER.""", refusal("ALTER USER other SET QUERY_TAG = 'p'"));
        assertTrue(refusal("ALTER USER other SET COMMENT = 'c'").contains("user 'OTHER'"));
        assertTrue(refusal("ALTER USER other SET QUERY_TAG = 5").contains("invalid value [5] for parameter"));
        assertTrue(refusal("ALTER USER other SET NOSUCH = 5").contains("invalid property 'NOSUCH'"));
        assertTrue(refusal("ALTER USER nosuch SET QUERY_TAG = 'p'").contains("does not exist"));
    }

    @Test
    public void usersChangeTheirOwnDefaultsAndSessionParameters() {
        asUser("r1");
        engine.execute("ALTER USER u SET QUERY_TAG = 'mine' DEFAULT_ROLE = r1 DEFAULT_WAREHOUSE = wh");
        engine.execute("ALTER USER u UNSET QUERY_TAG");
        engine.execute("ALTER USER u UNSET DEFAULT_WAREHOUSE");
        assertTrue(refusal("ALTER USER u SET DISPLAY_NAME = 'me'").contains("user 'U'"));
        assertTrue(refusal("ALTER USER u SET COMMENT = 'me'").contains("user 'U'"));
        assertTrue(refusal("ALTER USER u UNSET COMMENT").contains("user 'U'"));
    }

    @Test
    public void theRoleThatOwnsAUserAltersIt() {
        asSystem();
        engine.execute("GRANT ROLE USERADMIN TO USER u");
        asUser("USERADMIN");
        engine.execute("CREATE USER owned");
        engine.execute("ALTER USER owned SET COMMENT = 'c' DISPLAY_NAME = 'd'");
        assertTrue(refusal("ALTER USER other SET COMMENT = 'c'").contains("user 'OTHER'"));
        asUser("r1");
        assertTrue(refusal("ALTER USER owned SET COMMENT = 'c'").contains("user 'OWNED'"));
    }

    @Test
    public void theEnginesOwnSessionIsNotHeldToThem() {
        engine.execute("ALTER ACCOUNT SET JSON_INDENT = 2");
        engine.execute("ALTER ACCOUNT UNSET JSON_INDENT");
        engine.execute("CREATE USER mine");
        engine.execute("ALTER USER other SET COMMENT = 'c'");
    }
}
