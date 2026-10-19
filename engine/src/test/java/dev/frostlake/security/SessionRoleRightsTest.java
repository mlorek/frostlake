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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The roles a session's statements are authorized by: the primary role, the secondary roles in use, and the roles
 * those inherit. A role granted to the user that the session never activated authorizes nothing, so with secondary
 * roles NONE the primary role decides alone; secondary roles ALL stand for the user's roles as each statement runs; and
 * the primary role a USE ROLE leaves stops acting, unless USE SECONDARY ROLES named it (documented). The live account
 * allows no CREATE ROLE, CREATE USER, GRANT or USE ROLE, so the rule is the documented one, held by the embedded
 * engine.
 */
public class SessionRoleRightsTest {

    private static final String READ = "SELECT x FROM t2";

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE q");
        engine.execute("USE SCHEMA q.public");
        engine.execute("CREATE TABLE t2 (x INT)");
        engine.execute("INSERT INTO t2 VALUES (42)");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE ROLE r2");
        engine.execute("CREATE ROLE r3");
        for (final String role : new String[] {"r1", "r2", "r3"}) {
            engine.execute("GRANT USAGE ON DATABASE q TO ROLE " + role);
            engine.execute("GRANT USAGE ON SCHEMA q.public TO ROLE " + role);
        }
        engine.execute("GRANT SELECT, INSERT ON TABLE t2 TO ROLE r2");
        engine.execute("GRANT SELECT ON TABLE t2 TO ROLE r3");
        engine.execute("CREATE USER u");
        engine.execute("GRANT ROLE r1 TO USER u");
        engine.execute("GRANT ROLE r2 TO USER u");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void asUser() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("u");
        engine.setCurrentRole("r1");
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

    private String answer(final String sql) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            rows.append(rows.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return rows.toString();
    }

    private boolean listsT2() {
        for (final Row row : engine.executeQuery("SHOW TABLES IN SCHEMA q.public").getRows()) {
            if ("T2".equals(row.getValue(1))) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void withNoSecondaryRolesTheUsersOtherRolesAuthorizeNothing() {
        asUser();
        engine.execute("USE SECONDARY ROLES NONE");
        assertEquals("R1", answer("SELECT CURRENT_ROLE()"));
        assertEquals("""
            SQL access control error:
            Insufficient privileges to operate on table 'T2'. Your primary role R1 must have SELECT granted on \
            TABLE Q.PUBLIC.T2.""", refusal(READ));
        assertTrue(refusal("INSERT INTO t2 VALUES (1)").contains("must have INSERT granted on TABLE Q.PUBLIC.T2"));
        assertFalse(listsT2());
        engine.execute("USE SECONDARY ROLES r2");
        assertEquals("42", answer(READ));
        assertTrue(listsT2());
        engine.execute("USE SECONDARY ROLES NONE");
        assertTrue(refusal(READ).contains("Your primary role R1 must have SELECT"), refusal(READ));
    }

    @Test
    public void secondaryRolesAllStandForTheUsersRolesAsEachStatementRuns() {
        asUser();
        engine.execute("USE SECONDARY ROLES ALL");
        assertEquals("42", answer(READ));
        asSystem();
        engine.execute("REVOKE ROLE r2 FROM USER u");
        asUser();
        assertTrue(refusal(READ).contains("or one of your secondary roles must have SELECT"), refusal(READ));
        asSystem();
        engine.execute("GRANT ROLE r3 TO USER u");
        asUser();
        assertEquals("42", answer(READ));
        assertTrue(refusal("INSERT INTO t2 VALUES (1)").contains("INSERT"));
    }

    @Test
    public void theRoleAUseRoleLeavesStopsActing() {
        asUser();
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("USE ROLE r2");
        assertEquals("42", answer(READ));
        engine.execute("USE ROLE r1");
        assertTrue(refusal(READ).contains("Your primary role R1 must have SELECT"), refusal(READ));
    }

    @Test
    public void aRoleUseSecondaryRolesNamedOutlivesItsTurnAsPrimary() {
        asUser();
        engine.execute("USE SECONDARY ROLES r2");
        engine.execute("USE ROLE r2");
        engine.execute("USE ROLE r1");
        assertEquals("42", answer(READ));
        assertEquals("R2", answer("SELECT PARSE_JSON(CURRENT_SECONDARY_ROLES()):value::VARCHAR"));
    }
}
