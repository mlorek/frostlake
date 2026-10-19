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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Grants passed on through a grant option, and the REVOKE that reaches them (documented): a grant a role makes
 * through its option is recorded in its own name, GRANTED_BY, and depends on the grant that gave the option. A
 * REVOKE taking the option away - with the privilege, or alone through GRANT OPTION FOR - is refused under RESTRICT,
 * the default, while such a grant stands, and takes it back under CASCADE, the grants made through it after it. The
 * grantor takes back what it granted; a role that holds the privilege from another grantor keeps the grant it held.
 * The live account allows no CREATE ROLE, CREATE USER or GRANT, so the rule is held by the embedded engine.
 */
public class DependentGrantRevokeTest {

    private static final String SHOW = "SHOW GRANTS ON TABLE p.public.t";

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        for (final String sql : new String[] {
            "CREATE DATABASE p", "CREATE ROLE a", "CREATE ROLE b", "CREATE ROLE c", "CREATE USER ua", "CREATE USER ub",
            "CREATE USER uc", "GRANT ROLE a TO USER ua", "GRANT ROLE b TO USER ub", "GRANT ROLE c TO USER uc",
            "GRANT USAGE ON DATABASE p TO ROLE PUBLIC", "GRANT USAGE ON SCHEMA p.public TO ROLE PUBLIC",
            "CREATE TABLE p.public.t (a INT)", "INSERT INTO p.public.t VALUES (1)",
            "GRANT SELECT ON TABLE p.public.t TO ROLE a WITH GRANT OPTION",
        }) {
            engine.execute(sql);
        }
        as("UA", "a");
        engine.execute("GRANT SELECT ON TABLE p.public.t TO ROLE b WITH GRANT OPTION");
        as("UB", "b");
        engine.execute("GRANT SELECT ON TABLE p.public.t TO ROLE c");
        asSystem();
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
        engine.setCurrentRole(systemRole);
        engine.execute("USE ROLE " + role);
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

    /** The SELECT grants on the table, each as grantee, grant option and grantor, in grantee order. */
    private String selectGrants() {
        final ResultSet rs = engine.executeQuery(SHOW + " ->> SELECT \"grantee_name\" || ':' || \"grant_option\" || ':'"
            + " || \"granted_by\" AS g FROM $1 WHERE \"privilege\" = 'SELECT' ORDER BY \"grantee_name\"");
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            out.append(out.length() > 0 ? " " : "").append(rs.getValue(0));
        }
        return out.toString();
    }

    private String reads(final String user, final String role) {
        as(user, role);
        try {
            return String.valueOf(engine.executeQuery("SELECT COUNT(*) FROM p.public.t").getRows().get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return "refused";
        } finally {
            asSystem();
        }
    }

    private static String dependent(final String grantee) {
        return "SQL execution error: Dependent grant of privilege 'SELECT' on securable 'P.PUBLIC.T' to role '"
            + grantee + "' exists. It must be revoked first. More than one dependent grant may exist: use CASCADE"
            + " option to revoke them all.";
    }

    @Test
    public void aGrantPassedOnIsRecordedInItsGrantorsName() {
        assertEquals("A:true:SYSADMIN B:true:A C:false:B", selectGrants());
        assertEquals("1", reads("UC", "c"));
    }

    @Test
    public void restrictRefusesWhileADependentGrantStandsAndChangesNothing() {
        assertEquals(dependent("C"), refusal("REVOKE SELECT ON TABLE p.public.t FROM ROLE b"));
        assertEquals(dependent("C"), refusal("REVOKE SELECT ON TABLE p.public.t FROM ROLE b RESTRICT"));
        assertEquals(dependent("B"), refusal("REVOKE SELECT ON TABLE p.public.t FROM ROLE a"));
        assertEquals("A:true:SYSADMIN B:true:A C:false:B", selectGrants());
        assertEquals("1", reads("UC", "c"));
    }

    @Test
    public void cascadeTakesBackEveryGrantMadeThroughTheOption() {
        as("UA", "a");
        engine.execute("REVOKE SELECT ON TABLE p.public.t FROM ROLE b CASCADE");
        asSystem();
        assertEquals("A:true:SYSADMIN", selectGrants());
        assertEquals("refused", reads("UB", "b"));
        assertEquals("refused", reads("UC", "c"));
        engine.execute("GRANT SELECT ON TABLE p.public.t TO ROLE b WITH GRANT OPTION");
        as("UB", "b");
        engine.execute("GRANT SELECT ON TABLE p.public.t TO ROLE c");
        asSystem();
        engine.execute("REVOKE SELECT ON TABLE p.public.t FROM ROLE a CASCADE");
        assertEquals("B:true:SYSADMIN C:false:B", selectGrants());
    }

    @Test
    public void theGrantOptionAloneIsTakenBackTheSameWay() {
        assertEquals(dependent("C"), refusal("REVOKE GRANT OPTION FOR SELECT ON TABLE p.public.t FROM ROLE b"));
        engine.execute("REVOKE GRANT OPTION FOR SELECT ON TABLE p.public.t FROM ROLE b CASCADE");
        assertEquals("A:true:SYSADMIN B:false:A", selectGrants());
        assertEquals("1", reads("UB", "b"));
        assertEquals("refused", reads("UC", "c"));
    }

    @Test
    public void theGrantorTakesBackWhatItGrantedAndNothingElse() {
        as("UC", "c");
        engine.execute("REVOKE SELECT ON TABLE p.public.t FROM ROLE b CASCADE");
        asSystem();
        assertEquals("A:true:SYSADMIN B:true:A C:false:B", selectGrants());
        as("UB", "b");
        engine.execute("REVOKE SELECT ON TABLE p.public.t FROM ROLE c");
        asSystem();
        assertEquals("A:true:SYSADMIN B:true:A", selectGrants());
    }

    @Test
    public void aGranteeHoldingThePrivilegeFromAnotherGrantorKeepsIt() {
        engine.execute("CREATE ROLE d");
        engine.execute("GRANT SELECT ON TABLE p.public.t TO ROLE d");
        as("UB", "b");
        engine.execute("GRANT SELECT ON TABLE p.public.t TO ROLE d");
        asSystem();
        assertEquals("A:true:SYSADMIN B:true:A C:false:B D:false:SYSADMIN", selectGrants());
        engine.execute("REVOKE SELECT ON TABLE p.public.t FROM ROLE b CASCADE");
        assertEquals("A:true:SYSADMIN D:false:SYSADMIN", selectGrants());
    }
}
