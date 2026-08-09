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

package dev.frostlake.ddl;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test ALTER statements for various Snowflake objects
 */
public class AlterStatementTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testAlterDatabaseRename() {
        engine.execute("CREATE DATABASE old_db");
        engine.execute("ALTER DATABASE old_db RENAME TO new_db");

        ResultSet rs = engine.executeQuery("SHOW DATABASES");
        assertTrue(containsValue(rs, "new_db"), "Database should be renamed to new_db");
        assertFalse(containsValue(rs, "old_db"), "Old database name should not exist");
    }

    @Test
    public void testAlterDatabaseSetComment() {
        engine.execute("ALTER DATABASE test_db SET COMMENT = 'Test database comment'");

        ResultSet rs = engine.executeQuery("SHOW DATABASES");
        // Note: Comment validation would require DESCRIBE DATABASE
        assertTrue(rs.getRowCount() > 0, "Database should exist");
    }

    @Test
    public void testAlterSchemaRename() {
        engine.execute("CREATE SCHEMA old_schema");
        engine.execute("ALTER SCHEMA old_schema RENAME TO new_schema");

        ResultSet rs = engine.executeQuery("SHOW SCHEMAS");
        assertTrue(containsValue(rs, "new_schema"), "Schema should be renamed to new_schema");
        assertFalse(containsValue(rs, "old_schema"), "Old schema name should not exist");
    }

    @Test
    public void testAlterSchemaSetComment() {
        engine.execute("ALTER SCHEMA test_schema SET COMMENT = 'Test schema comment'");

        ResultSet rs = engine.executeQuery("SHOW SCHEMAS");
        assertTrue(rs.getRowCount() > 0, "Schema should exist");
    }

    @Test
    public void testAlterTableRename() {
        engine.execute("CREATE TABLE old_table (id INT, name VARCHAR)");
        engine.execute("ALTER TABLE old_table RENAME TO new_table");

        ResultSet rs = engine.executeQuery("SHOW TABLES");
        assertTrue(containsValue(rs, "new_table"), "Table should be renamed to new_table");
        assertFalse(containsValue(rs, "old_table"), "Old table name should not exist");
    }

    @Test
    public void testAlterTableAddColumn() {
        engine.execute("CREATE TABLE users (id INT, name VARCHAR)");
        engine.execute("ALTER TABLE users ADD COLUMN email VARCHAR");

        engine.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')");
        ResultSet rs = engine.executeQuery("SELECT * FROM users");

        assertEquals(3, rs.getColumnCount(), "Table should have 3 columns after ADD COLUMN");
        assertEquals("EMAIL", rs.getColumns().get(2).getName().toUpperCase(),
                     "Third column should be email");
    }

    @Test
    public void testAlterTableDropColumn() {
        engine.execute("CREATE TABLE users (id INT, name VARCHAR, email VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')");
        engine.execute("ALTER TABLE users DROP COLUMN email");

        ResultSet rs = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, rs.getColumnCount(), "Table should have 2 columns after DROP COLUMN");

        // Verify the remaining columns are correct
        assertEquals("ID", rs.getColumns().get(0).getName().toUpperCase());
        assertEquals("NAME", rs.getColumns().get(1).getName().toUpperCase());
    }

    @Test
    public void testAlterTableRenameColumn() {
        engine.execute("CREATE TABLE users (id INT, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("ALTER TABLE users RENAME COLUMN name TO full_name");

        ResultSet rs = engine.executeQuery("SELECT * FROM users");
        assertEquals("FULL_NAME", rs.getColumns().get(1).getName().toUpperCase(),
                     "Column should be renamed to full_name");

        // Verify data is preserved
        rs.next();
        assertEquals("Alice", rs.getValue("full_name"));
    }

    @Test
    public void testAlterTableSetComment() {
        engine.execute("CREATE TABLE users (id INT, name VARCHAR)");
        engine.execute("ALTER TABLE users SET COMMENT = 'User data table'");

        ResultSet rs = engine.executeQuery("SHOW TABLES");
        assertTrue(rs.getRowCount() > 0, "Table should exist");
    }

    @Test
    public void testAlterViewRename() {
        engine.execute("CREATE TABLE users (id INT, name VARCHAR)");
        engine.execute("CREATE VIEW old_view AS SELECT * FROM users");
        engine.execute("ALTER VIEW old_view RENAME TO new_view");

        ResultSet rs = engine.executeQuery("SHOW VIEWS");
        assertTrue(containsValue(rs, "new_view"), "View should be renamed to new_view");
        assertFalse(containsValue(rs, "old_view"), "Old view name should not exist");
    }

    @Test
    public void testAlterViewSetComment() {
        engine.execute("CREATE TABLE users (id INT, name VARCHAR)");
        engine.execute("CREATE VIEW user_view AS SELECT * FROM users");
        engine.execute("ALTER VIEW user_view SET COMMENT = 'User view'");

        ResultSet rs = engine.executeQuery("SHOW VIEWS");
        assertTrue(rs.getRowCount() > 0, "View should exist");
    }

    /**
     * RENAME moves the NAME and nothing else: the login and display names a user already had stay
     * as they were, defaults included, so a renamed user still reports the name they were created
     * with in those two columns.
     */
    @Test
    public void testAlterUserRename() {
        engine.execute("CREATE USER old_user PASSWORD = 'pass123'");
        engine.execute("ALTER USER old_user RENAME TO new_user");

        final ResultSet rs = engine.executeQuery("SHOW USERS");
        final int nameIdx = rs.getColumnIndex("name");
        final int loginIdx = rs.getColumnIndex("login_name");
        final int displayIdx = rs.getColumnIndex("display_name");
        Row renamed = null;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("NEW_USER".equals(rs.getRows().get(i).getValue(nameIdx))) {
                renamed = rs.getRows().get(i);
            }
            assertNotEquals("OLD_USER", rs.getRows().get(i).getValue(nameIdx),
                "the old name must not survive as a user name");
        }
        assertNotNull(renamed, "User should be renamed to NEW_USER");
        assertEquals("OLD_USER", renamed.getValue(loginIdx), "the login name is left as it was");
        assertEquals("OLD_USER", renamed.getValue(displayIdx), "the display name is left as it was");
    }

    @Test
    public void testAlterUserSetPassword() {
        engine.execute("CREATE USER test_user PASSWORD = 'oldpass'");
        engine.execute("ALTER USER test_user SET PASSWORD = 'newpass'");

        ResultSet rs = engine.executeQuery("SHOW USERS");
        assertTrue(containsValue(rs, "test_user"), "User should exist");
    }

    @Test
    public void testAlterUserSetDefaultRole() {
        engine.execute("CREATE ROLE test_role");
        engine.execute("CREATE USER test_user PASSWORD = 'pass123'");
        engine.execute("ALTER USER test_user SET DEFAULT_ROLE = test_role");

        ResultSet rs = engine.executeQuery("SHOW USERS");
        assertTrue(containsValue(rs, "test_user"), "User should exist");
    }

    @Test
    public void testAlterUserSetComment() {
        engine.execute("CREATE USER test_user PASSWORD = 'pass123'");
        engine.execute("ALTER USER test_user SET COMMENT = 'Test user account'");

        ResultSet rs = engine.executeQuery("SHOW USERS");
        assertTrue(containsValue(rs, "test_user"), "User should exist");
    }

    @Test
    public void testAlterRoleRename() {
        engine.execute("CREATE ROLE old_role");
        engine.execute("ALTER ROLE old_role RENAME TO new_role");

        ResultSet rs = engine.executeQuery("SHOW ROLES");
        assertTrue(containsValue(rs, "new_role"), "Role should be renamed to new_role");
        assertFalse(containsValue(rs, "old_role"), "Old role name should not exist");
    }

    @Test
    public void testAlterRoleSetComment() {
        engine.execute("CREATE ROLE test_role");
        engine.execute("ALTER ROLE test_role SET COMMENT = 'Test role'");

        ResultSet rs = engine.executeQuery("SHOW ROLES");
        assertTrue(containsValue(rs, "test_role"), "Role should exist");
    }

    @Test
    public void testAlterWarehouseRename() {
        engine.execute("CREATE WAREHOUSE old_wh");
        engine.execute("ALTER WAREHOUSE old_wh RENAME TO new_wh");

        ResultSet rs = engine.executeQuery("SHOW WAREHOUSES");
        assertTrue(containsValue(rs, "new_wh"), "Warehouse should be renamed to new_wh");
        assertFalse(containsValue(rs, "old_wh"), "Old warehouse name should not exist");
    }

    @Test
    public void testAlterWarehouseSetComment() {
        engine.execute("CREATE WAREHOUSE test_wh");
        engine.execute("ALTER WAREHOUSE test_wh SET COMMENT = 'Test warehouse'");

        ResultSet rs = engine.executeQuery("SHOW WAREHOUSES");
        assertTrue(containsValue(rs, "test_wh"), "Warehouse should exist");
    }

    @Test
    public void testAlterWarehouseSetSize() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("ALTER WAREHOUSE test_wh SET WAREHOUSE_SIZE = 'LARGE'");

        ResultSet rs = engine.executeQuery("SHOW WAREHOUSES");
        assertTrue(containsValue(rs, "test_wh"), "Warehouse should exist");
    }

    @Test
    public void testAlterWarehouseSetAutoSuspend() {
        engine.execute("CREATE WAREHOUSE test_wh");
        engine.execute("ALTER WAREHOUSE test_wh SET AUTO_SUSPEND = 300");

        ResultSet rs = engine.executeQuery("SHOW WAREHOUSES");
        assertTrue(containsValue(rs, "test_wh"), "Warehouse should exist");
    }

    /**
     * Helper method to check if a ResultSet contains a specific value in the first column
     */
    // Renamed objects must keep the same name-casing as freshly-created ones (Snowflake
    // upper-cases unquoted identifiers). Lookups are case-insensitive, so the existing
    // SHOW-based rename tests above (which use equalsIgnoreCase) never caught the casing.

    @Test
    public void testAlterDatabaseRenameUppercasesName() {
        engine.execute("CREATE DATABASE old_db2");
        engine.execute("ALTER DATABASE old_db2 RENAME TO new_db2");
        assertEquals("NEW_DB2", engine.getCatalog().getDatabase("new_db2").getName());
    }

    @Test
    public void testAlterUserRenameUppercasesName() {
        engine.execute("CREATE USER old_user2 PASSWORD = 'p'");
        engine.execute("ALTER USER old_user2 RENAME TO new_user2");
        assertEquals("NEW_USER2", engine.getCatalog().getUser("new_user2").getName());
    }

    @Test
    public void testAlterRoleRenameUppercasesName() {
        engine.execute("CREATE ROLE old_role2");
        engine.execute("ALTER ROLE old_role2 RENAME TO new_role2");
        assertEquals("NEW_ROLE2", engine.getCatalog().getRole("new_role2").getName());
    }

    @Test
    public void testDefaultRoleUppercasedOnCreateAndAlter() {
        engine.execute("CREATE ROLE analyst_role");
        // CREATE USER ... DEFAULT_ROLE upper-cases the referenced role (roles are stored upper-cased).
        engine.execute("CREATE USER dr_user PASSWORD = 'p' DEFAULT_ROLE = analyst_role");
        assertEquals("ANALYST_ROLE", engine.getCatalog().getUser("dr_user").getDefaultRole());
        // ALTER USER ... SET DEFAULT_ROLE upper-cases it too, symmetric with CREATE.
        engine.execute("CREATE ROLE other_role");
        engine.execute("ALTER USER dr_user SET DEFAULT_ROLE = other_role");
        assertEquals("OTHER_ROLE", engine.getCatalog().getUser("dr_user").getDefaultRole());
    }

    @Test
    public void testAlterMaterializedViewRenameRekeys() {
        engine.execute("CREATE TABLE mv_src (id INTEGER, val INTEGER)");
        engine.execute("CREATE MATERIALIZED VIEW mv_old AS SELECT id, val FROM mv_src");
        engine.execute("ALTER MATERIALIZED VIEW mv_old RENAME TO mv_new");

        final var schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA");
        // Re-keyed: findable by the new name (MV names are stored verbatim, so no upper-casing here)...
        assertEquals("MV_NEW", schema.getMaterializedView("mv_new").getName());
        // ...and no longer findable by the old name.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                schema.getMaterializedView("mv_old");
            }
        });
    }

    @Test
    public void testShowSchemasIsCurrentCaseInsensitive() {
        // test_schema (set current in setup) is stored verbatim/lower-case while the current schema is
        // tracked upper-cased; SHOW SCHEMAS must still flag it is_current = Y (case-insensitive match).
        ResultSet rs = engine.executeQuery("SHOW SCHEMAS");
        boolean foundCurrent = false;
        while (rs.next()) {
            if ("test_schema".equalsIgnoreCase((String) rs.getValue("name"))) {
                assertEquals("Y", rs.getValue("is_current"), "current schema should be flagged is_current=Y");
                foundCurrent = true;
            }
        }
        assertTrue(foundCurrent, "test_schema should appear in SHOW SCHEMAS");
    }

    @Test
    public void testGrantorTrackedFromSession() {
        engine.execute("CREATE ROLE granting_role");
        engine.execute("CREATE ROLE child_role");
        engine.execute("CREATE ROLE parent_role");
        engine.getSecurityManager().getSessionContext().setCurrentRole("granting_role");
        engine.execute("GRANT ROLE child_role TO ROLE parent_role");
        // granted_by reflects the session's current role at GRANT time, not a hard-coded SYSADMIN.
        assertEquals("GRANTING_ROLE",
            engine.getCatalog().getRole("PARENT_ROLE").getRoleGrantor("CHILD_ROLE"));
    }

    @Test
    public void testObjectOwnerStampedWithCreatingRole() {
        engine.execute("CREATE ROLE data_owner");
        engine.getSecurityManager().getSessionContext().setCurrentRole("data_owner");
        engine.execute("CREATE TABLE owned_tbl (id INTEGER)");
        // The model object carries the creating role as owner...
        assertEquals("DATA_OWNER",
            engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA").getTable("OWNED_TBL").getOwner());
        // ...and SHOW TABLES surfaces it (no longer a hard-coded SYSADMIN).
        ResultSet rs = engine.executeQuery("SHOW TABLES");
        boolean found = false;
        while (rs.next()) {
            if ("owned_tbl".equalsIgnoreCase((String) rs.getValue("name"))) {
                assertEquals("DATA_OWNER", rs.getValue("owner"), "table owner should be the creating role");
                found = true;
            }
        }
        assertTrue(found, "owned_tbl should appear in SHOW TABLES");
    }

    @Test
    public void testAlterColumnSetDefaultIsRestrictedToSequences() {
        // Live-Snowflake verified: only a SEQUENCE default may be set after creation; a literal
        // default raises "Unsupported feature 'Alter Column Set Default'". DROP DEFAULT still works
        // against a CREATE-time default.
        engine.execute("CREATE TABLE def_tbl (id INT, region VARCHAR(50) NOT NULL DEFAULT 'EU')");
        engine.execute("INSERT INTO def_tbl(id) VALUES (1)");
        ResultSet rs = engine.executeQuery("SELECT region FROM def_tbl");
        assertEquals("EU", rs.getRows().get(0).getValue(0));

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE def_tbl ALTER COLUMN region SET DEFAULT 'US'");
            }
        });

        engine.execute("CREATE SEQUENCE def_seq");
        engine.execute("ALTER TABLE def_tbl ALTER COLUMN id SET DEFAULT def_seq.NEXTVAL");

        engine.execute("ALTER TABLE def_tbl ALTER COLUMN region DROP DEFAULT");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO def_tbl(id) VALUES (2)");
            }
        });
    }

    @Test
    public void testShowRolesIsCurrentReflectsSession() {
        // is_current must track the session's current role, not a hard-coded SYSADMIN.
        engine.execute("CREATE ROLE my_show_role");
        engine.getSecurityManager().getSessionContext().setCurrentRole("my_show_role");
        ResultSet rs = engine.executeQuery("SHOW ROLES");
        boolean checkedCurrent = false;
        boolean checkedSysadmin = false;
        while (rs.next()) {
            String name = (String) rs.getValue("name");
            if ("MY_SHOW_ROLE".equals(name)) {
                assertEquals("Y", rs.getValue("is_current"), "switched-to role should be is_current=Y");
                checkedCurrent = true;
            } else if ("SYSADMIN".equals(name)) {
                assertEquals("N", rs.getValue("is_current"), "SYSADMIN is not current after switching role");
                checkedSysadmin = true;
            }
        }
        assertTrue(checkedCurrent, "MY_SHOW_ROLE should appear in SHOW ROLES");
        assertTrue(checkedSysadmin, "SYSADMIN should appear in SHOW ROLES");
    }

    private boolean containsValue(final ResultSet rs, final String value) {
        rs.reset();
        int nameIdx = -1;
        try { nameIdx = rs.getColumnIndex("name"); } catch (final Exception ignored) {}
        while (rs.next()) {
            for (int i = 0; i < rs.getColumns().size(); i++) {
                Object colValue = rs.getValue(i);
                if (colValue != null && colValue.toString().equalsIgnoreCase(value)) {
                    return true;
                }
            }
        }
        return false;
    }
}
