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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests USE ROLE and USE SECONDARY ROLES — switching the session's primary role (reflected by
 * {@code CURRENT_ROLE()}) and adjusting secondary roles, with existence validation.
 */
public class UseRoleTest extends BaseDatabaseTest {

    private String currentRole() {
        final ResultSet rs = engine.executeQuery("SELECT CURRENT_ROLE()");
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private String currentUser() {
        final ResultSet rs = engine.executeQuery("SELECT CURRENT_USER()");
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void useRoleSwitchesCurrentRole() {
        // A role can only be ACTIVATED by a user it has been granted to. Live-verified: without the
        // GRANT below the statement fails "Requested role 'ANALYST' is not assigned to the executing
        // user. Specify another role to activate." The working spelling is
        // GRANT ROLE <role> TO USER <user>; live Snowflake REJECTS
        // GRANT ROLE r TO USER IDENTIFIER(CURRENT_USER()) with a syntax error at the '(', so the
        // executing user's name is read first and spliced into the statement.
        final String originalRole = currentRole();
        final String user = currentUser();
        // The account is stateful across runs (an earlier round failed with "Object 'ANALYST' already
        // exists"), so create tolerantly and hand everything back at the end.
        engine.execute("CREATE ROLE IF NOT EXISTS analyst");
        // Live: the executing user is already on the account, so this is a no-op there. Embedded: the
        // session user (CURRENT_USER()) is not in the catalog until something creates it, and
        // GRANT ROLE ... TO USER needs it to exist.
        engine.execute("CREATE USER IF NOT EXISTS \"" + user + "\"");
        engine.execute("GRANT ROLE analyst TO USER \"" + user + "\"");
        engine.execute("USE ROLE analyst");
        assertEquals("ANALYST", currentRole());

        // Step off the role before dropping it — a session left on a dropped role has none.
        engine.execute("USE ROLE \"" + originalRole + "\"");
        engine.execute("REVOKE ROLE analyst FROM USER \"" + user + "\"");
        engine.execute("DROP ROLE IF EXISTS analyst");
    }

    @Test
    public void useSystemRoleSwitches() {
        engine.execute("USE ROLE SYSADMIN");
        assertEquals("SYSADMIN", currentRole());
        engine.execute("USE ROLE ACCOUNTADMIN");
        assertEquals("ACCOUNTADMIN", currentRole());
    }

    @Test
    public void useNonexistentRoleFails() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("USE ROLE no_such_role");
            }
        });
    }

    @Test
    public void useSecondaryRolesAllThenNone() {
        engine.execute("CREATE ROLE r1");
        // ALL then NONE adjust active roles without error.
        engine.execute("USE SECONDARY ROLES ALL");
        engine.execute("USE SECONDARY ROLES NONE");
    }
}
