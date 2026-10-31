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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The embedded API's user switch keeps no role the new user may not use: CURRENT_USER() names the user, the former
 * session's primary role gives way to PUBLIC until setCurrentRole names the user's, and the secondary roles are the
 * ones a written DEFAULT_SECONDARY_ROLES names. A user switched to from a SYSADMIN session no longer reads what only
 * SYSADMIN may. Engine only: the live account allows no CREATE USER or GRANT.
 */
public class UserSwitchRolesTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE us");
        engine.execute("USE SCHEMA us.public");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE ROLE r2");
        engine.execute("CREATE USER u1");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT ROLE r2 TO USER u1");
        engine.execute("CREATE USER u2 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u2");
        engine.execute("GRANT ROLE r2 TO USER u2");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("GRANT USAGE ON DATABASE us TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA us.public TO ROLE r1");
        engine.execute("GRANT USAGE ON DATABASE us TO ROLE r2");
        engine.execute("GRANT USAGE ON SCHEMA us.public TO ROLE r2");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE r2");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private String cell(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aSwitchedUserKeepsNoneOfTheFormerSessionsRoles() {
        engine.setCurrentRole("SYSADMIN");
        engine.setCurrentUser("U1");
        assertEquals("PUBLIC", cell("SELECT CURRENT_ROLE()"));
        engine.setCurrentRole("R1");
        assertEquals("U1", cell("SELECT CURRENT_USER()"));
        assertEquals("R1", cell("SELECT CURRENT_ROLE()"));
        // SYSADMIN, the former session's role, is neither available nor in use.
        assertEquals("[\"PUBLIC\",\"R1\",\"R2\"]", cell("SELECT CURRENT_AVAILABLE_ROLES()"));
        engine.execute("USE SECONDARY ROLES NONE");
        assertTrue(refusal("SELECT COUNT(*) FROM t").contains("SELECT"), refusal("SELECT COUNT(*) FROM t"));
        engine.execute("USE SECONDARY ROLES ALL");
        assertEquals("0", cell("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void noSecondaryRolesLeaveThePrimaryRoleAlone() {
        engine.setCurrentRole("SYSADMIN");
        engine.setCurrentUser("U2");
        engine.setCurrentRole("R1");
        // R2 is available to U2, granted to it, but not in use: () puts no secondary role in use.
        assertEquals("[\"PUBLIC\",\"R1\",\"R2\"]", cell("SELECT CURRENT_AVAILABLE_ROLES()"));
        assertEquals("{\"roles\":\"\",\"value\":\"\"}", cell("SELECT CURRENT_SECONDARY_ROLES()"));
        assertTrue(refusal("SELECT COUNT(*) FROM t").contains("SELECT"), refusal("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void switchingBackToTheEngineNamesTheConfiguredUser() {
        final String configured = cell("SELECT CURRENT_USER()");
        engine.setCurrentUser("U1");
        assertEquals("U1", cell("SELECT CURRENT_USER()"));
        engine.setCurrentUser("SYSTEM");
        engine.setCurrentRole(systemRole);
        assertEquals(configured, cell("SELECT CURRENT_USER()"));
        assertNotEquals("U1", configured);
    }
}
