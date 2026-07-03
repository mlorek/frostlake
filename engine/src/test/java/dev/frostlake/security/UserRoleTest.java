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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for user and role management commands
 */
public class UserRoleTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // No specific setup needed
    }

    @Test
    public void testCreateUser() {
        engine.execute("CREATE USER alice");

        ResultSet users = engine.executeQuery("SHOW USERS");
        assertTrue(users.getRowCount() >= 1);

        boolean found = false;
        for (int i = 0; i < users.getRowCount(); i++) {
            if ("ALICE".equals(users.getRows().get(i).getValue(0))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "User ALICE should be in SHOW USERS");
    }

    @Test
    public void testCreateUserWithPassword() {
        engine.execute("CREATE USER bob PASSWORD = 'secret123'");

        ResultSet users = engine.executeQuery("SHOW USERS");
        boolean found = false;
        for (int i = 0; i < users.getRowCount(); i++) {
            if ("BOB".equals(users.getRows().get(i).getValue(0))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "User BOB should be in SHOW USERS");
    }

    @Test
    public void testDropUser() {
        engine.execute("CREATE USER temp_user");
        engine.execute("DROP USER temp_user");

        ResultSet users = engine.executeQuery("SHOW USERS");
        for (int i = 0; i < users.getRowCount(); i++) {
            assertNotEquals("TEMP_USER", users.getRows().get(i).getValue(0),
                "User TEMP_USER should not exist after DROP");
        }
    }

    @Test
    public void testCreateRole() {
        engine.execute("CREATE ROLE analyst");

        ResultSet roles = engine.executeQuery("SHOW ROLES");
        assertTrue(roles.getRowCount() >= 1);

        boolean found = false;
        for (int i = 0; i < roles.getRowCount(); i++) {
            if ("ANALYST".equals(roles.getRows().get(i).getValue(roles.getColumnIndex("name")))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Role ANALYST should be in SHOW ROLES");
    }

    @Test
    public void testDropRole() {
        engine.execute("CREATE ROLE temp_role");
        engine.execute("DROP ROLE temp_role");

        ResultSet roles = engine.executeQuery("SHOW ROLES");
        for (int i = 0; i < roles.getRowCount(); i++) {
            assertNotEquals("TEMP_ROLE", roles.getRows().get(i).getValue(roles.getColumnIndex("name")),
                "Role TEMP_ROLE should not exist after DROP");
        }
    }

    @Test
    public void testGrantRoleToUser() {
        engine.execute("CREATE USER charlie");
        engine.execute("CREATE ROLE developer");
        engine.execute("GRANT ROLE developer TO USER charlie");

        ResultSet grants = engine.executeQuery("SHOW GRANTS TO USER charlie");
        assertTrue(grants.getRowCount() >= 1);

        boolean found = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            if ("DEVELOPER".equals(grants.getRows().get(i).getValue(3))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "DEVELOPER role should be granted to charlie");
    }

    @Test
    public void testRevokeRoleFromUser() {
        engine.execute("CREATE USER dave");
        engine.execute("CREATE ROLE tester");
        engine.execute("GRANT ROLE tester TO USER dave");
        engine.execute("REVOKE ROLE tester FROM USER dave");

        ResultSet grants = engine.executeQuery("SHOW GRANTS TO USER dave");
        for (int i = 0; i < grants.getRowCount(); i++) {
            assertNotEquals("TESTER", grants.getRows().get(i).getValue(3),
                "TESTER role should not be granted to dave after REVOKE");
        }
    }

    @Test
    public void testGrantPrivilegeToRole() {
        engine.execute("CREATE DATABASE priv_test_db");
        engine.execute("CREATE ROLE data_reader");
        engine.execute("GRANT SELECT ON DATABASE priv_test_db TO ROLE data_reader");

        ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE data_reader");
        assertTrue(grants.getRowCount() >= 1);

        boolean found = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            if ("SELECT".equals(grants.getRows().get(i).getValue(1)) &&
                "PRIV_TEST_DB".equals(grants.getRows().get(i).getValue(3))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "SELECT privilege on priv_test_db should be granted to data_reader");
    }

    @Test
    public void testRevokePrivilegeFromRole() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR)");
        engine.execute("CREATE ROLE writer");
        engine.execute("GRANT INSERT ON TABLE products TO ROLE writer");
        engine.execute("REVOKE INSERT ON TABLE products FROM ROLE writer");

        ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE writer");
        for (int i = 0; i < grants.getRowCount(); i++) {
            if ("INSERT".equals(grants.getRows().get(i).getValue(1))) {
                assertNotEquals("PRODUCTS", grants.getRows().get(i).getValue(3),
                    "INSERT privilege on products should not be granted after REVOKE");
            }
        }
    }

    @Test
    public void testShowSystemRoles() {
        ResultSet roles = engine.executeQuery("SHOW ROLES");
        assertTrue(roles.getRowCount() >= 4, "Should have at least 4 system roles");

        // Check for system roles
        boolean hasSysadmin = false;
        boolean hasUseradmin = false;
        boolean hasSecurityadmin = false;
        boolean hasPublic = false;

        for (int i = 0; i < roles.getRowCount(); i++) {
            String roleName = (String) roles.getRows().get(i).getValue(roles.getColumnIndex("name"));
            if ("SYSADMIN".equals(roleName)) hasSysadmin = true;
            if ("USERADMIN".equals(roleName)) hasUseradmin = true;
            if ("SECURITYADMIN".equals(roleName)) hasSecurityadmin = true;
            if ("PUBLIC".equals(roleName)) hasPublic = true;
        }

        assertTrue(hasSysadmin, "Should have SYSADMIN role");
        assertTrue(hasUseradmin, "Should have USERADMIN role");
        assertTrue(hasSecurityadmin, "Should have SECURITYADMIN role");
        assertTrue(hasPublic, "Should have PUBLIC role");
    }

    @Test
    public void testShowGrantsOnObject() {
        engine.execute("CREATE WAREHOUSE grant_test_wh");
        engine.execute("CREATE ROLE wh_user");
        engine.execute("GRANT USAGE ON WAREHOUSE grant_test_wh TO ROLE wh_user");

        ResultSet grants = engine.executeQuery("SHOW GRANTS ON WAREHOUSE grant_test_wh");
        assertTrue(grants.getRowCount() >= 1);

        boolean found = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            if ("USAGE".equals(grants.getRows().get(i).getValue(1)) &&
                "WH_USER".equals(grants.getRows().get(i).getValue(5))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "USAGE privilege should be shown for grant_test_wh");
    }

    @Test
    public void testGrantAllPrivileges() {
        engine.execute("CREATE TABLE all_priv_test (id INTEGER)");
        engine.execute("CREATE ROLE admin_role");
        engine.execute("GRANT ALL ON TABLE all_priv_test TO ROLE admin_role");

        ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE admin_role");
        assertTrue(grants.getRowCount() >= 1);

        boolean found = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            if ("ALL".equals(grants.getRows().get(i).getValue(1)) &&
                "ALL_PRIV_TEST".equals(grants.getRows().get(i).getValue(3))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "ALL privileges should be granted");
    }

    @Test
    public void testPublicRoleAutomaticallyGrantedToNewUsers() {
        // Create a new user
        engine.execute("CREATE USER test_public_user");

        // Verify PUBLIC role is automatically granted
        ResultSet grants = engine.executeQuery("SHOW GRANTS TO USER test_public_user");
        assertTrue(grants.getRowCount() >= 1, "User should have at least PUBLIC role");

        boolean hasPublicRole = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            if ("PUBLIC".equals(grants.getRows().get(i).getValue(3))) {
                hasPublicRole = true;
                break;
            }
        }
        assertTrue(hasPublicRole, "PUBLIC role should be automatically granted to all new users");
    }

    @Test
    public void testPublicRoleGrantedToUserWithPassword() {
        // Create a new user with password and default role
        engine.execute("CREATE USER test_public_user2 PASSWORD = 'pass123' DEFAULT_ROLE = 'PUBLIC'");

        // Verify PUBLIC role is automatically granted
        ResultSet grants = engine.executeQuery("SHOW GRANTS TO USER test_public_user2");
        assertTrue(grants.getRowCount() >= 1, "User should have at least PUBLIC role");

        boolean hasPublicRole = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            if ("PUBLIC".equals(grants.getRows().get(i).getValue(3))) {
                hasPublicRole = true;
                break;
            }
        }
        assertTrue(hasPublicRole, "PUBLIC role should be automatically granted to user created with password");
    }
}
