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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Snowflake system roles
 */
public class SystemRolesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testAllSystemRolesExist() {
        // Verify all 6 system roles exist
        String[] systemRoles = {
            "ORGADMIN",
            "ACCOUNTADMIN",
            "SECURITYADMIN",
            "USERADMIN",
            "SYSADMIN",
            "PUBLIC"
        };

        for (final String roleName : systemRoles) {
            Role role = engine.getCatalog().getRole(roleName);
            assertNotNull(role, roleName + " should exist");
            assertEquals(roleName, role.getName());
        }
    }

    @Test
    public void testOrgAdminRoleExists() {
        Role role = engine.getCatalog().getRole("ORGADMIN");
        assertNotNull(role);
        assertEquals("ORGADMIN", role.getName());
        assertNotNull(role.getComment());
        assertTrue(role.getComment().contains("Organization administrator"));
    }

    @Test
    public void testAccountAdminRoleExists() {
        Role role = engine.getCatalog().getRole("ACCOUNTADMIN");
        assertNotNull(role);
        assertEquals("ACCOUNTADMIN", role.getName());
        assertNotNull(role.getComment());
        assertTrue(role.getComment().contains("Account administrator"));
    }

    @Test
    public void testSecurityAdminRoleExists() {
        Role role = engine.getCatalog().getRole("SECURITYADMIN");
        assertNotNull(role);
        assertEquals("SECURITYADMIN", role.getName());
        assertNotNull(role.getComment());
        assertTrue(role.getComment().contains("Security administrator"));
    }

    @Test
    public void testUserAdminRoleExists() {
        Role role = engine.getCatalog().getRole("USERADMIN");
        assertNotNull(role);
        assertEquals("USERADMIN", role.getName());
        assertNotNull(role.getComment());
        assertTrue(role.getComment().contains("User administrator"));
    }

    @Test
    public void testSysAdminRoleExists() {
        Role role = engine.getCatalog().getRole("SYSADMIN");
        assertNotNull(role);
        assertEquals("SYSADMIN", role.getName());
        assertNotNull(role.getComment());
        assertTrue(role.getComment().contains("System administrator"));
    }

    @Test
    public void testPublicRoleExists() {
        Role role = engine.getCatalog().getRole("PUBLIC");
        assertNotNull(role);
        assertEquals("PUBLIC", role.getName());
        assertNotNull(role.getComment());
        assertTrue(role.getComment().contains("Public role"));
    }

    @Test
    public void testRoleHierarchy() {
        // Test role hierarchy relationships
        Role orgAdmin = engine.getCatalog().getRole("ORGADMIN");
        Role accountAdmin = engine.getCatalog().getRole("ACCOUNTADMIN");
        Role securityAdmin = engine.getCatalog().getRole("SECURITYADMIN");
        Role userAdmin = engine.getCatalog().getRole("USERADMIN");

        // ORGADMIN should have ACCOUNTADMIN
        assertTrue(orgAdmin.hasRole("ACCOUNTADMIN"),
            "ORGADMIN should have ACCOUNTADMIN role granted");

        // ACCOUNTADMIN should have SECURITYADMIN and SYSADMIN
        assertTrue(accountAdmin.hasRole("SECURITYADMIN"),
            "ACCOUNTADMIN should have SECURITYADMIN role granted");
        assertTrue(accountAdmin.hasRole("SYSADMIN"),
            "ACCOUNTADMIN should have SYSADMIN role granted");

        // SECURITYADMIN should have USERADMIN
        assertTrue(securityAdmin.hasRole("USERADMIN"),
            "SECURITYADMIN should have USERADMIN role granted");
    }

    @Test
    public void testCannotDropOrgAdmin() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("DROP ROLE ORGADMIN");
        });
        assertTrue(exception.getMessage().contains("Cannot drop system role"));
    }

    @Test
    public void testCannotDropAccountAdmin() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("DROP ROLE ACCOUNTADMIN");
        });
        assertTrue(exception.getMessage().contains("Cannot drop system role"));
    }

    @Test
    public void testCannotDropSecurityAdmin() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("DROP ROLE SECURITYADMIN");
        });
        assertTrue(exception.getMessage().contains("Cannot drop system role"));
    }

    @Test
    public void testCannotDropUserAdmin() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("DROP ROLE USERADMIN");
        });
        assertTrue(exception.getMessage().contains("Cannot drop system role"));
    }

    @Test
    public void testCannotDropSysAdmin() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("DROP ROLE SYSADMIN");
        });
        assertTrue(exception.getMessage().contains("Cannot drop system role"));
    }

    @Test
    public void testCannotDropPublicRole() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("DROP ROLE PUBLIC");
        });
        assertTrue(exception.getMessage().contains("Cannot drop system role"));
    }

    @Test
    public void testCannotCreateSystemRole() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("CREATE ROLE SYSADMIN");
        });
        assertTrue(exception.getMessage().contains("Cannot create system role") ||
                   exception.getMessage().contains("Role already exists"));
    }

    @Test
    public void testCannotCreateOrgAdmin() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("CREATE ROLE ORGADMIN");
        });
        assertTrue(exception.getMessage().contains("Cannot create system role") ||
                   exception.getMessage().contains("Role already exists"));
    }

    @Test
    public void testCannotCreateAccountAdmin() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("CREATE ROLE ACCOUNTADMIN");
        });
        assertTrue(exception.getMessage().contains("Cannot create system role") ||
                   exception.getMessage().contains("Role already exists"));
    }

    @Test
    public void testShowRolesIncludesSystemRoles() {
        ResultSet result = engine.executeQuery("SHOW ROLES");

        boolean foundOrgAdmin = false;
        boolean foundAccountAdmin = false;
        boolean foundSecurityAdmin = false;
        boolean foundUserAdmin = false;
        boolean foundSysAdmin = false;
        boolean foundPublic = false;

        while (result.next()) {
            String roleName = (String) result.getValue("name");
            if ("ORGADMIN".equals(roleName)) foundOrgAdmin = true;
            if ("ACCOUNTADMIN".equals(roleName)) foundAccountAdmin = true;
            if ("SECURITYADMIN".equals(roleName)) foundSecurityAdmin = true;
            if ("USERADMIN".equals(roleName)) foundUserAdmin = true;
            if ("SYSADMIN".equals(roleName)) foundSysAdmin = true;
            if ("PUBLIC".equals(roleName)) foundPublic = true;
        }

        assertTrue(foundOrgAdmin, "SHOW ROLES should include ORGADMIN");
        assertTrue(foundAccountAdmin, "SHOW ROLES should include ACCOUNTADMIN");
        assertTrue(foundSecurityAdmin, "SHOW ROLES should include SECURITYADMIN");
        assertTrue(foundUserAdmin, "SHOW ROLES should include USERADMIN");
        assertTrue(foundSysAdmin, "SHOW ROLES should include SYSADMIN");
        assertTrue(foundPublic, "SHOW ROLES should include PUBLIC");
    }

    @Test
    public void testCanCreateCustomRoles() {
        // Creating custom roles should still work
        engine.execute("CREATE ROLE my_custom_role");

        Role role = engine.getCatalog().getRole("MY_CUSTOM_ROLE");
        assertNotNull(role);
        assertEquals("MY_CUSTOM_ROLE", role.getName());
    }

    @Test
    public void testCanDropCustomRoles() {
        // Creating and dropping custom roles should work
        engine.execute("CREATE ROLE test_role");
        engine.execute("DROP ROLE test_role");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.getCatalog().getRole("TEST_ROLE");
        });
        assertTrue(exception.getMessage().contains("Role 'TEST_ROLE' does not exist or not authorized."));
    }

    @Test
    public void testSystemRoleCaseInsensitive() {
        // System roles should be recognized in any case
        assertTrue(engine.getCatalog().isSystemRole("ORGADMIN"));
        assertTrue(engine.getCatalog().isSystemRole("orgadmin"));
        assertTrue(engine.getCatalog().isSystemRole("OrgAdmin"));
        assertTrue(engine.getCatalog().isSystemRole("ACCOUNTADMIN"));
        assertTrue(engine.getCatalog().isSystemRole("accountadmin"));
        assertTrue(engine.getCatalog().isSystemRole("SYSADMIN"));
        assertTrue(engine.getCatalog().isSystemRole("sysadmin"));
        assertTrue(engine.getCatalog().isSystemRole("PUBLIC"));
        assertTrue(engine.getCatalog().isSystemRole("public"));
    }

    @Test
    public void testNonSystemRoleNotRecognized() {
        assertFalse(engine.getCatalog().isSystemRole("CUSTOM_ROLE"));
        assertFalse(engine.getCatalog().isSystemRole("MY_ROLE"));
        assertFalse(engine.getCatalog().isSystemRole("ADMIN"));
    }
}
