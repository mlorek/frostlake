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
 * Switching the session to a user whose DEFAULT_SECONDARY_ROLES a statement wrote puts the user's defaults in use, and
 * the primary role the previous user's session had stays behind: a user granted nothing but R1 holds R1 and PUBLIC,
 * with ('ALL') the roles granted to it too, and never the SYSADMIN the switch started from (FL-428). Engine only: the
 * live account allows no CREATE USER or CREATE ROLE.
 */
public class DefaultSecondaryRolesUserSwitchTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("CREATE USER u2 DEFAULT_SECONDARY_ROLES = ('ALL')");
        engine.execute("CREATE USER u3");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT ROLE r1 TO USER u2");
        engine.execute("GRANT ROLE r1 TO USER u3");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void as(final String user, final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(user);
        engine.setCurrentRole(role);
    }

    private void asSystem() {
        as("SYSTEM", systemRole);
    }

    private String answer(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private void holdsOnlyItsOwnRoles(final String user) {
        as(user, "R1");
        assertEquals("[\"PUBLIC\",\"R1\"]", answer("SELECT CURRENT_AVAILABLE_ROLES()").replaceAll("\\s", ""));
        assertTrue(refusal("SELECT * FROM t").contains("must have SELECT granted on TABLE P.PUBLIC.T"),
            refusal("SELECT * FROM t"));
        assertTrue(refusal("GRANT ROLE SYSADMIN TO ROLE r1").contains("SYSADMIN"));
        asSystem();
        assertEquals("1", answer("SELECT * FROM t"));
    }

    @Test
    public void aUserWithNoDefaultSecondaryRolesDoesNotKeepThePreviousPrimaryRole() {
        holdsOnlyItsOwnRoles("U1");
    }

    @Test
    public void aUserWithAllDefaultSecondaryRolesDoesNotKeepThePreviousPrimaryRole() {
        holdsOnlyItsOwnRoles("U2");
        as("U2", "R1");
        assertTrue(answer("SELECT CURRENT_SECONDARY_ROLES()").contains("\"value\":\"ALL\""));
    }

    @Test
    public void defaultsWrittenByAlterUserDoNotKeepThePreviousPrimaryRole() {
        engine.execute("ALTER USER u3 SET DEFAULT_SECONDARY_ROLES = ()");
        holdsOnlyItsOwnRoles("U3");
    }
}
