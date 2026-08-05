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
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for enhanced GRANT and REVOKE commands
 */
public class GrantRevokeTest extends BaseDatabaseTest {

    private static final String CATALOG_PRINCIPAL_ASSERTIONS =
        "asserts the grant through engine.getCatalog().getUser()/getRole(), which under SF_LIVE "
        + "still reads the embedded engine — the CREATE USER and the GRANT went to Snowflake, so "
        + "the embedded catalog has no such principal; the statements are still submitted to the "
        + "account";

    @Override
    protected void setupTest() {
        // No specific setup needed
    }

    @Test
    public void testGrantRoleToRole() {
        engine.execute("CREATE ROLE parent_role");
        engine.execute("CREATE ROLE child_role");
        engine.execute("GRANT ROLE child_role TO ROLE parent_role");

        // Verify the grant was successful
        ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE parent_role");
        assertTrue(grants.getRowCount() >= 1);

        boolean found = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            // Column 3 is the name (0=created_on, 1=privilege, 2=granted_on, 3=name, 4=granted_to, 5=grantee_name)
            // For role grants, name=granted_role_name
            if ("CHILD_ROLE".equals(grants.getRows().get(i).getValue(3).toString())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Child role should be granted to parent role");
    }

    @Test
    public void testRevokeRoleFromRole() {
        engine.execute("CREATE ROLE parent_role");
        engine.execute("CREATE ROLE child_role");
        engine.execute("GRANT ROLE child_role TO ROLE parent_role");
        engine.execute("REVOKE ROLE child_role FROM ROLE parent_role");

        ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE parent_role");
        for (int i = 0; i < grants.getRowCount(); i++) {
            assertNotEquals("CHILD_ROLE", grants.getRows().get(i).getValue(1));
        }
    }

    @Test
    public void testMultiLevelRoleHierarchy() {
        // Test creating a multi-level role hierarchy
        engine.execute("CREATE ROLE role_a");
        engine.execute("CREATE ROLE role_b");
        engine.execute("CREATE ROLE role_c");

        // Grant roles in a hierarchy: role_a <- role_b <- role_c
        assertDoesNotThrow(() -> engine.execute("GRANT ROLE role_b TO ROLE role_a"));
        assertDoesNotThrow(() -> engine.execute("GRANT ROLE role_c TO ROLE role_b"));

        // Verify role hierarchy works
        ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE role_a");
        assertTrue(grants.getRowCount() >= 1, "Should have role grants");
    }

    @Test
    public void testGrantNewPrivileges() {
        engine.execute("CREATE ROLE test_role");
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");

        // Test TRUNCATE privilege
        engine.execute("GRANT TRUNCATE ON TABLE test_table TO ROLE test_role");

        ResultSet grants = engine.executeQuery("SHOW GRANTS ON TABLE test_table");
        assertTrue(grants.getRowCount() >= 1, "Should have at least one grant");

        boolean foundTruncate = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            // Column 1 is the privilege (0=created_on, 1=privilege, 2=granted_on, etc.)
            if ("TRUNCATE".equals(grants.getRows().get(i).getValue(1).toString())) {
                foundTruncate = true;
                break;
            }
        }
        assertTrue(foundTruncate, "TRUNCATE privilege should be granted");
    }

    @Test
    public void testGrantMultiplePrivileges() {
        engine.execute("CREATE ROLE data_role");
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR)");

        // Grant multiple privileges
        engine.execute("GRANT SELECT, INSERT, UPDATE ON TABLE employees TO ROLE data_role");

        ResultSet grants = engine.executeQuery("SHOW GRANTS ON TABLE employees");
        assertTrue(grants.getRowCount() >= 3);

        boolean hasSelect = false;
        boolean hasInsert = false;
        boolean hasUpdate = false;

        for (int i = 0; i < grants.getRowCount(); i++) {
            String privilege = grants.getRows().get(i).getValue(1).toString();
            if ("SELECT".equals(privilege)) hasSelect = true;
            if ("INSERT".equals(privilege)) hasInsert = true;
            if ("UPDATE".equals(privilege)) hasUpdate = true;
        }

        assertTrue(hasSelect && hasInsert && hasUpdate, "All three privileges should be granted");
    }

    @Test
    public void testGrantOnDifferentObjectTypes() {
        engine.execute("CREATE ROLE admin_role");
        engine.execute("CREATE DATABASE grant_test_db");
        engine.execute("USE DATABASE grant_test_db");
        engine.execute("CREATE SCHEMA grant_test_schema");
        engine.execute("USE SCHEMA grant_test_schema");
        engine.execute("CREATE TABLE grant_test_table (id INTEGER)");
        engine.execute("CREATE VIEW grant_test_view AS SELECT * FROM grant_test_table");

        // Grant on different object types
        engine.execute("GRANT USAGE ON DATABASE grant_test_db TO ROLE admin_role");
        engine.execute("GRANT USAGE ON SCHEMA grant_test_schema TO ROLE admin_role");
        engine.execute("GRANT SELECT ON TABLE grant_test_table TO ROLE admin_role");
        engine.execute("GRANT SELECT ON VIEW grant_test_view TO ROLE admin_role");

        // Verify database grant
        ResultSet dbGrants = engine.executeQuery("SHOW GRANTS ON DATABASE grant_test_db");
        assertTrue(dbGrants.getRowCount() >= 1);
    }

    @Test
    public void testGrantOwnership() {
        engine.execute("CREATE ROLE owner_role");
        engine.execute("CREATE TABLE ownership_test (id INTEGER)");

        engine.execute("GRANT OWNERSHIP ON TABLE ownership_test TO ROLE owner_role");

        ResultSet grants = engine.executeQuery("SHOW GRANTS ON TABLE ownership_test");
        boolean foundOwnership = false;

        for (int i = 0; i < grants.getRowCount(); i++) {
            if ("OWNERSHIP".equals(grants.getRows().get(i).getValue(1))) {
                foundOwnership = true;
                break;
            }
        }

        assertTrue(foundOwnership, "OWNERSHIP privilege should be granted");
    }

    @Test
    public void testRevokeSpecificPrivileges() {
        engine.execute("CREATE ROLE revoke_test_role");
        engine.execute("CREATE TABLE revoke_table (id INTEGER)");

        engine.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE revoke_table TO ROLE revoke_test_role");
        engine.execute("REVOKE INSERT, UPDATE ON TABLE revoke_table FROM ROLE revoke_test_role");

        ResultSet grants = engine.executeQuery("SHOW GRANTS ON TABLE revoke_table");

        boolean hasSelect = false;
        boolean hasDelete = false;
        boolean hasInsert = false;
        boolean hasUpdate = false;

        for (int i = 0; i < grants.getRowCount(); i++) {
            String privilege = grants.getRows().get(i).getValue(1).toString();
            if ("SELECT".equals(privilege)) hasSelect = true;
            if ("DELETE".equals(privilege)) hasDelete = true;
            if ("INSERT".equals(privilege)) hasInsert = true;
            if ("UPDATE".equals(privilege)) hasUpdate = true;
        }

        assertTrue(hasSelect && hasDelete, "SELECT and DELETE should remain");
        assertFalse(hasInsert || hasUpdate, "INSERT and UPDATE should be revoked");
    }

    @Test
    public void testGrantAllPrivileges() {
        engine.execute("CREATE ROLE all_priv_role");
        engine.execute("CREATE TABLE all_priv_table (id INTEGER)");

        engine.execute("GRANT ALL PRIVILEGES ON TABLE all_priv_table TO ROLE all_priv_role");

        ResultSet grants = engine.executeQuery("SHOW GRANTS ON TABLE all_priv_table");
        assertTrue(grants.getRowCount() >= 1);

        // Check that ALL privilege is granted
        boolean foundAll = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            if ("ALL".equals(grants.getRows().get(i).getValue(1))) {
                foundAll = true;
                break;
            }
        }

        assertTrue(foundAll, "ALL privilege should be granted");
    }

    @Test
    public void testRevokeAllPrivileges() {
        engine.execute("CREATE ROLE revoke_all_role");
        engine.execute("CREATE TABLE revoke_all_table (id INTEGER)");

        engine.execute("GRANT ALL PRIVILEGES ON TABLE revoke_all_table TO ROLE revoke_all_role");
        engine.execute("REVOKE ALL PRIVILEGES ON TABLE revoke_all_table FROM ROLE revoke_all_role");

        ResultSet grants = engine.executeQuery("SHOW GRANTS ON TABLE revoke_all_table");

        // Should have no grants for this role
        boolean hasAnyGrant = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            if ("REVOKE_ALL_ROLE".equals(grants.getRows().get(i).getValue(2))) {
                hasAnyGrant = true;
                break;
            }
        }

        assertFalse(hasAnyGrant, "All privileges should be revoked");
    }

    @Test
    public void testQualifiedObjectNames() {
        engine.execute("CREATE ROLE qualified_role");
        engine.execute("CREATE DATABASE qname_db");
        engine.execute("USE DATABASE qname_db");
        engine.execute("CREATE SCHEMA qname_schema");
        engine.execute("USE SCHEMA qname_schema");
        engine.execute("CREATE TABLE qname_table (id INTEGER)");

        // Grant using simple name (schema context is set)
        engine.execute("GRANT SELECT ON TABLE qname_table TO ROLE qualified_role");

        ResultSet grants = engine.executeQuery("SHOW GRANTS ON TABLE qname_table");
        assertTrue(grants.getRowCount() >= 1, "Should have at least one grant");
    }

    @Test
    public void testRoleHierarchy() {
        engine.execute("CREATE ROLE top_role");
        engine.execute("CREATE ROLE middle_role");
        engine.execute("CREATE ROLE bottom_role");

        // Create hierarchy: top_role -> middle_role -> bottom_role
        engine.execute("GRANT ROLE bottom_role TO ROLE middle_role");
        engine.execute("GRANT ROLE middle_role TO ROLE top_role");

        ResultSet topGrants = engine.executeQuery("SHOW GRANTS TO ROLE top_role");
        boolean foundMiddle = false;

        for (int i = 0; i < topGrants.getRowCount(); i++) {
            // Column 3 is the name for role grants
            if ("MIDDLE_ROLE".equals(topGrants.getRows().get(i).getValue(3).toString())) {
                foundMiddle = true;
                break;
            }
        }

        assertTrue(foundMiddle, "Middle role should be granted to top role");
    }

    @Test
    public void testGrantPrivilegeToUser() {
        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_PRINCIPAL_ASSERTIONS);
        engine.execute("CREATE USER u1");
        engine.execute("CREATE TABLE t1 (id INTEGER, name VARCHAR)");

        // Test the main use case: GRANT SELECT ON TABLE t1 TO USER u1
        engine.execute("GRANT SELECT ON TABLE t1 TO USER u1");

        // Verify the privilege was granted
        var user = engine.getCatalog().getUser("u1");
        assertTrue(user.hasPrivilege("TABLE", "T1",
            Privilege.SELECT),
            "User should have SELECT privilege on table");
    }

    @Test
    public void testRevokePrivilegeFromUser() {
        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_PRINCIPAL_ASSERTIONS);
        engine.execute("CREATE USER u2");
        engine.execute("CREATE TABLE t2 (id INTEGER)");

        engine.execute("GRANT SELECT ON TABLE t2 TO USER u2");
        engine.execute("REVOKE SELECT ON TABLE t2 FROM USER u2");

        var user = engine.getCatalog().getUser("u2");
        assertFalse(user.hasPrivilege("TABLE", "T2",
            Privilege.SELECT),
            "User should not have SELECT privilege after revoke");
    }

    @Test
    public void testGrantMultiplePrivilegesToUser() {
        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_PRINCIPAL_ASSERTIONS);
        engine.execute("CREATE USER u3");
        engine.execute("CREATE TABLE t3 (id INTEGER, value VARCHAR)");

        engine.execute("GRANT SELECT, INSERT, UPDATE ON TABLE t3 TO USER u3");

        var user = engine.getCatalog().getUser("u3");
        assertTrue(user.hasPrivilege("TABLE", "T3",
            Privilege.SELECT));
        assertTrue(user.hasPrivilege("TABLE", "T3",
            Privilege.INSERT));
        assertTrue(user.hasPrivilege("TABLE", "T3",
            Privilege.UPDATE));
    }

    @Test
    public void testGrantAllPrivilegesToUser() {
        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_PRINCIPAL_ASSERTIONS);
        engine.execute("CREATE USER u4");
        engine.execute("CREATE TABLE t4 (id INTEGER)");

        engine.execute("GRANT ALL PRIVILEGES ON TABLE t4 TO USER u4");

        var user = engine.getCatalog().getUser("u4");
        assertTrue(user.hasPrivilege("TABLE", "T4",
            Privilege.ALL));
    }

    @Test
    public void testGrantOnDatabaseToUser() {
        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_PRINCIPAL_ASSERTIONS);
        engine.execute("CREATE USER u5");
        engine.execute("CREATE DATABASE user_test_db");

        engine.execute("GRANT USAGE ON DATABASE user_test_db TO USER u5");

        var user = engine.getCatalog().getUser("u5");
        assertTrue(user.hasPrivilege("DATABASE", "USER_TEST_DB",
            Privilege.USAGE));
    }

    @Test
    public void testGrantOwnershipToUser() {
        engine.execute("CREATE USER IF NOT EXISTS u6");
        engine.execute("CREATE TABLE t6 (id INTEGER)");

        // Ownership belongs to ROLES only. Live-verified: GRANT OWNERSHIP ON TABLE t TO USER u fails
        // with "SQL execution error: Cannot grant OWNERSHIP to users." while the same statement
        // TO ROLE succeeds — so the user form is asserted as a rejection, not as a grant.
        final RuntimeException rejected = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("GRANT OWNERSHIP ON TABLE t6 TO USER u6");
            }
        });
        assertTrue(String.valueOf(rejected.getMessage()).contains("Cannot grant OWNERSHIP to users"),
            "unexpected message: " + rejected.getMessage());

        // The positive half of the pair, keeping the OWNERSHIP-transfer coverage the user form used
        // to provide.
        engine.execute("CREATE ROLE IF NOT EXISTS t6_owner_role");
        engine.execute("GRANT OWNERSHIP ON TABLE t6 TO ROLE t6_owner_role");

        ResultSet grants = engine.executeQuery("SHOW GRANTS ON TABLE t6");
        boolean foundOwnership = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            // Column 1 is the privilege (0=created_on, 1=privilege, 2=granted_on, ...).
            if ("OWNERSHIP".equals(String.valueOf(grants.getRows().get(i).getValue(1)))) {
                foundOwnership = true;
                break;
            }
        }
        assertTrue(foundOwnership, "OWNERSHIP privilege should be granted to the role");
    }

    @Test
    public void testMixedUserAndRoleGrants() {
        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_PRINCIPAL_ASSERTIONS);
        engine.execute("CREATE USER mixed_user");
        engine.execute("CREATE ROLE mixed_role");
        engine.execute("CREATE TABLE mixed_table (id INTEGER)");

        // Grant to both user and role
        engine.execute("GRANT SELECT ON TABLE mixed_table TO USER mixed_user");
        engine.execute("GRANT INSERT ON TABLE mixed_table TO ROLE mixed_role");

        var user = engine.getCatalog().getUser("mixed_user");
        var role = engine.getCatalog().getRole("mixed_role");

        assertTrue(user.hasPrivilege("TABLE", "MIXED_TABLE",
            Privilege.SELECT));
        assertTrue(role.hasPrivilege("TABLE", "MIXED_TABLE",
            Privilege.INSERT));
    }
}
