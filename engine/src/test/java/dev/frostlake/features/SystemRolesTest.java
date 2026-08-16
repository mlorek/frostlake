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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The six Snowflake system roles, asserted through the SQL surface — {@code SHOW ROLES} cells
 * (name, comment), {@code SHOW GRANTS TO ROLE} for the hierarchy edges, and the CREATE/DROP
 * refusals that guard them — so every check runs against whichever engine executed the
 * statements, embedded or live.
 */
public class SystemRolesTest extends BaseDatabaseTest {

    @Override
    protected void teardownTest() {
        engine.execute("DROP ROLE IF EXISTS my_custom_role");
        engine.execute("DROP ROLE IF EXISTS test_role");
        engine.execute("DROP ROLE IF EXISTS admin");
        engine.execute("DROP ROLE IF EXISTS my_role");
    }

    /** One SHOW ROLES cell for the given role, matched by its name column. */
    private String roleCell(final String role, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW ROLES LIKE '" + role + "'");
        return cell(rs, soleRowWhere(rs, "name", role.toUpperCase()), column);
    }

    private int roleCount(final String name) {
        return engine.executeQuery("SHOW ROLES LIKE '" + name + "'").getRowCount();
    }

    /** Whether SHOW GRANTS TO ROLE lists {@code granted} as a role granted to {@code grantee}. */
    private boolean roleGranted(final String grantee, final String granted) {
        final ResultSet rs = engine.executeQuery("SHOW GRANTS TO ROLE " + grantee);
        for (final Row row : rs.getRows()) {
            if ("ROLE".equalsIgnoreCase(cell(rs, row, "granted_on"))
                    && granted.equalsIgnoreCase(cell(rs, row, "name"))) {
                return true;
            }
        }
        return false;
    }

    private void assertDropRoleRefused(final String spelling) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP ROLE " + spelling);
            }
        }, "DROP ROLE " + spelling + " must be refused");
    }

    private void assertCreateRoleRefused(final String name) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE ROLE " + name);
            }
        }, "CREATE ROLE " + name + " must be refused");
    }

    @Test
    public void testAllSystemRolesExist() {
        final String[] systemRoles = {
            "ORGADMIN",
            "ACCOUNTADMIN",
            "SECURITYADMIN",
            "USERADMIN",
            "SYSADMIN",
            "PUBLIC"
        };

        final ResultSet rs = engine.executeQuery("SHOW ROLES");
        for (final String roleName : systemRoles) {
            final Row row = soleRowWhere(rs, "name", roleName);
            assertEquals(roleName, cell(rs, row, "name"));
        }
    }

    @Test
    public void testOrgAdminRoleExists() {
        assertEquals("ORGADMIN", roleCell("orgadmin", "name"));
        assertTrue(roleCell("orgadmin", "comment").contains("Organization administrator"));
    }

    @Test
    public void testAccountAdminRoleExists() {
        assertEquals("ACCOUNTADMIN", roleCell("accountadmin", "name"));
        assertTrue(roleCell("accountadmin", "comment").contains("Account administrator"));
    }

    @Test
    public void testSecurityAdminRoleExists() {
        assertEquals("SECURITYADMIN", roleCell("securityadmin", "name"));
        assertTrue(roleCell("securityadmin", "comment").contains("Security administrator"));
    }

    @Test
    public void testUserAdminRoleExists() {
        assertEquals("USERADMIN", roleCell("useradmin", "name"));
        assertTrue(roleCell("useradmin", "comment").contains("User administrator"));
    }

    @Test
    public void testSysAdminRoleExists() {
        assertEquals("SYSADMIN", roleCell("sysadmin", "name"));
        assertTrue(roleCell("sysadmin", "comment").contains("System administrator"));
    }

    @Test
    public void testPublicRoleExists() {
        assertEquals("PUBLIC", roleCell("public", "name"));
        assertTrue(roleCell("public", "comment").contains("Public role"));
    }

    @Test
    public void testRoleHierarchy() {
        // SHOW GRANTS TO ROLE ACCOUNTADMIN enumerates every ownership grant the account has ever
        // accumulated, and on the shared harness account that no longer completes within its
        // 300-second statement timeout — so the hierarchy edges are asserted embedded only.
        // ORGADMIN's empty listing (the next test) returns instantly and stays live.
        Assumptions.assumeFalse(isLiveSnowflake(),
            "SHOW GRANTS TO ROLE ACCOUNTADMIN exceeds the shared account's statement timeout");
        assertTrue(roleGranted("ACCOUNTADMIN", "SECURITYADMIN"),
            "ACCOUNTADMIN should have SECURITYADMIN role granted");
        assertTrue(roleGranted("ACCOUNTADMIN", "SYSADMIN"),
            "ACCOUNTADMIN should have SYSADMIN role granted");
        assertTrue(roleGranted("SECURITYADMIN", "USERADMIN"),
            "SECURITYADMIN should have USERADMIN role granted");
    }

    @Test
    public void testOrgAdminStandsOutsideTheHierarchy() {
        // ORGADMIN is granted no ROLE at all — it is not ACCOUNTADMIN's parent (live-verified).
        // Its own object privileges are a separate matter and are not asserted here.
        assertFalse(roleGranted("ORGADMIN", "ACCOUNTADMIN"),
            "ORGADMIN must not hold ACCOUNTADMIN");

        final ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE ORGADMIN");
        assertEquals(0, rowsWhere(grants, "granted_on", "ROLE").size(),
            "ORGADMIN should hold no role grants");
    }

    @Test
    public void testCannotDropOrgAdmin() {
        assertDropRoleRefused("ORGADMIN");
    }

    @Test
    public void testCannotDropAccountAdmin() {
        assertDropRoleRefused("ACCOUNTADMIN");
    }

    @Test
    public void testCannotDropSecurityAdmin() {
        assertDropRoleRefused("SECURITYADMIN");
    }

    @Test
    public void testCannotDropUserAdmin() {
        assertDropRoleRefused("USERADMIN");
    }

    @Test
    public void testCannotDropSysAdmin() {
        assertDropRoleRefused("SYSADMIN");
    }

    @Test
    public void testCannotDropPublicRole() {
        assertDropRoleRefused("PUBLIC");
    }

    @Test
    public void testCannotCreateSystemRole() {
        assertCreateRoleRefused("SYSADMIN");
    }

    @Test
    public void testCannotCreateOrgAdmin() {
        assertCreateRoleRefused("ORGADMIN");
    }

    @Test
    public void testCannotCreateAccountAdmin() {
        assertCreateRoleRefused("ACCOUNTADMIN");
    }

    @Test
    public void testShowRolesIncludesSystemRoles() {
        final ResultSet rs = engine.executeQuery("SHOW ROLES");

        soleRowWhere(rs, "name", "ORGADMIN");
        soleRowWhere(rs, "name", "ACCOUNTADMIN");
        soleRowWhere(rs, "name", "SECURITYADMIN");
        soleRowWhere(rs, "name", "USERADMIN");
        soleRowWhere(rs, "name", "SYSADMIN");
        soleRowWhere(rs, "name", "PUBLIC");
    }

    @Test
    public void testCanCreateCustomRoles() {
        engine.execute("CREATE ROLE my_custom_role");

        assertEquals("MY_CUSTOM_ROLE", roleCell("my_custom_role", "name"));
    }

    @Test
    public void testCanDropCustomRoles() {
        engine.execute("CREATE ROLE test_role");
        engine.execute("DROP ROLE test_role");

        assertEquals(0, roleCount("test_role"));
    }

    @Test
    public void testSystemRoleCaseInsensitive() {
        // The guard recognizes system roles in any spelling: every cased DROP is refused.
        assertDropRoleRefused("orgadmin");
        assertDropRoleRefused("OrgAdmin");
        assertDropRoleRefused("accountadmin");
        assertDropRoleRefused("AccountAdmin");
        assertDropRoleRefused("sysadmin");
        assertDropRoleRefused("public");
    }

    @Test
    public void testNonSystemRoleNotRecognized() {
        // Non-system names pass the guard: they can be created and dropped freely.
        engine.execute("CREATE ROLE admin");
        assertEquals(1, roleCount("admin"));
        engine.execute("DROP ROLE admin");
        assertEquals(0, roleCount("admin"));

        engine.execute("CREATE ROLE my_role");
        engine.execute("DROP ROLE my_role");
        assertEquals(0, roleCount("my_role"));
    }
}
