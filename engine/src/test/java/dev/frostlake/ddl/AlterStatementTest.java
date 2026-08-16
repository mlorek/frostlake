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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test ALTER statements for various Snowflake objects
 */
public class AlterStatementTest extends BaseDatabaseTest {

    /**
     * Why several tests below stop at the live switch: they assert on the CATALOG MODEL (or set the
     * session role through the security manager), and that model is the EMBEDDED metastore even during
     * a live run — the object they look for was created on the account. Such a test cannot compare the
     * two sides; running it live would only ever report the embedded engine back to itself.
     */

    @Test
    public void testAlterDatabaseRename() {
        engine.execute("CREATE DATABASE old_db");
        engine.execute("ALTER DATABASE old_db RENAME TO new_db");

        final ResultSet rs = engine.executeQuery("SHOW DATABASES");
        assertTrue(containsValue(rs, "new_db"), "Database should be renamed to new_db");
        assertFalse(containsValue(rs, "old_db"), "Old database name should not exist");
    }

    @Test
    public void testAlterDatabaseSetComment() {
        engine.execute("ALTER DATABASE test_db SET COMMENT = 'Test database comment'");

        final ResultSet rs = engine.executeQuery("SHOW DATABASES");
        // Note: Comment validation would require DESCRIBE DATABASE
        assertTrue(rs.getRowCount() > 0, "Database should exist");
    }

    @Test
    public void testAlterSchemaRename() {
        engine.execute("CREATE SCHEMA old_schema");
        engine.execute("ALTER SCHEMA old_schema RENAME TO new_schema");

        final ResultSet rs = engine.executeQuery("SHOW SCHEMAS");
        assertTrue(containsValue(rs, "new_schema"), "Schema should be renamed to new_schema");
        assertFalse(containsValue(rs, "old_schema"), "Old schema name should not exist");
    }

    @Test
    public void testAlterSchemaSetComment() {
        engine.execute("ALTER SCHEMA test_schema SET COMMENT = 'Test schema comment'");

        final ResultSet rs = engine.executeQuery("SHOW SCHEMAS");
        assertTrue(rs.getRowCount() > 0, "Schema should exist");
    }

    @Test
    public void testAlterTableRename() {
        engine.execute("CREATE TABLE old_table (id INT, name VARCHAR)");
        engine.execute("ALTER TABLE old_table RENAME TO new_table");

        final ResultSet rs = engine.executeQuery("SHOW TABLES");
        assertTrue(containsValue(rs, "new_table"), "Table should be renamed to new_table");
        assertFalse(containsValue(rs, "old_table"), "Old table name should not exist");
    }

    @Test
    public void testAlterTableAddColumn() {
        engine.execute("CREATE TABLE users (id INT, name VARCHAR)");
        engine.execute("ALTER TABLE users ADD COLUMN email VARCHAR");

        engine.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')");
        final ResultSet rs = engine.executeQuery("SELECT * FROM users");

        assertEquals(3, rs.getColumnCount(), "Table should have 3 columns after ADD COLUMN");
        assertEquals("EMAIL", rs.getColumns().get(2).getName().toUpperCase(),
                     "Third column should be email");
    }

    @Test
    public void testAlterTableDropColumn() {
        engine.execute("CREATE TABLE users (id INT, name VARCHAR, email VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')");
        engine.execute("ALTER TABLE users DROP COLUMN email");

        final ResultSet rs = engine.executeQuery("SELECT * FROM users");
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

        final ResultSet rs = engine.executeQuery("SELECT * FROM users");
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

        final ResultSet rs = engine.executeQuery("SHOW TABLES");
        assertTrue(rs.getRowCount() > 0, "Table should exist");
    }

    @Test
    public void testAlterViewRename() {
        engine.execute("CREATE TABLE users (id INT, name VARCHAR)");
        engine.execute("CREATE VIEW old_view AS SELECT * FROM users");
        engine.execute("ALTER VIEW old_view RENAME TO new_view");

        final ResultSet rs = engine.executeQuery("SHOW VIEWS");
        assertTrue(containsValue(rs, "new_view"), "View should be renamed to new_view");
        assertFalse(containsValue(rs, "old_view"), "Old view name should not exist");
    }

    @Test
    public void testAlterViewSetComment() {
        engine.execute("CREATE TABLE users (id INT, name VARCHAR)");
        engine.execute("CREATE VIEW user_view AS SELECT * FROM users");
        engine.execute("ALTER VIEW user_view SET COMMENT = 'User view'");

        final ResultSet rs = engine.executeQuery("SHOW VIEWS");
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
        engine.execute("CREATE USER test_user PASSWORD = 'Fl0stlake-Old-Passw0rd!'");
        engine.execute("ALTER USER test_user SET PASSWORD = 'Fr0stlake-New-Passw0rd!'");

        final ResultSet rs = engine.executeQuery("SHOW USERS");
        assertTrue(containsValue(rs, "test_user"), "User should exist");
    }

    @Test
    public void testAlterUserSetDefaultRole() {
        engine.execute("CREATE ROLE test_role");
        engine.execute("CREATE USER test_user PASSWORD = 'pass123'");
        engine.execute("ALTER USER test_user SET DEFAULT_ROLE = test_role");

        final ResultSet rs = engine.executeQuery("SHOW USERS");
        assertTrue(containsValue(rs, "test_user"), "User should exist");
    }

    @Test
    public void testAlterUserSetComment() {
        engine.execute("CREATE USER test_user PASSWORD = 'pass123'");
        engine.execute("ALTER USER test_user SET COMMENT = 'Test user account'");

        final ResultSet rs = engine.executeQuery("SHOW USERS");
        assertTrue(containsValue(rs, "test_user"), "User should exist");
    }

    @Test
    public void testAlterRoleRename() {
        engine.execute("CREATE ROLE old_role");
        engine.execute("ALTER ROLE old_role RENAME TO new_role");

        final ResultSet rs = engine.executeQuery("SHOW ROLES");
        assertTrue(containsValue(rs, "new_role"), "Role should be renamed to new_role");
        assertFalse(containsValue(rs, "old_role"), "Old role name should not exist");
    }

    @Test
    public void testAlterRoleSetComment() {
        engine.execute("CREATE ROLE test_role");
        engine.execute("ALTER ROLE test_role SET COMMENT = 'Test role'");

        final ResultSet rs = engine.executeQuery("SHOW ROLES");
        assertTrue(containsValue(rs, "test_role"), "Role should exist");
    }

    @Test
    public void testAlterWarehouseRename() {
        engine.execute("CREATE WAREHOUSE old_wh");
        engine.execute("ALTER WAREHOUSE old_wh RENAME TO new_wh");

        final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES");
        assertTrue(containsValue(rs, "new_wh"), "Warehouse should be renamed to new_wh");
        assertFalse(containsValue(rs, "old_wh"), "Old warehouse name should not exist");
    }

    @Test
    public void testAlterWarehouseSetComment() {
        engine.execute("CREATE WAREHOUSE test_wh");
        engine.execute("ALTER WAREHOUSE test_wh SET COMMENT = 'Test warehouse'");

        final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES");
        assertTrue(containsValue(rs, "test_wh"), "Warehouse should exist");
    }

    @Test
    public void testAlterWarehouseSetSize() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("ALTER WAREHOUSE test_wh SET WAREHOUSE_SIZE = 'LARGE'");

        final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES");
        assertTrue(containsValue(rs, "test_wh"), "Warehouse should exist");
    }

    @Test
    public void testAlterWarehouseSetAutoSuspend() {
        engine.execute("CREATE WAREHOUSE test_wh");
        engine.execute("ALTER WAREHOUSE test_wh SET AUTO_SUSPEND = 300");

        final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES");
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
        // Exact-case cell: the lookup is case-insensitive, the STORED spelling must be upper.
        final ResultSet dbs = engine.executeQuery("SHOW DATABASES LIKE 'new_db2'");
        assertEquals("NEW_DB2", cell(dbs, soleRowWhere(dbs, "name", "new_db2"), "name"));
    }

    @Test
    public void testAlterUserRenameUppercasesName() {
        engine.execute("CREATE USER old_user2 PASSWORD = 'p'");
        engine.execute("ALTER USER old_user2 RENAME TO new_user2");
        final ResultSet users = engine.executeQuery("SHOW USERS LIKE 'new_user2'");
        assertEquals("NEW_USER2", cell(users, soleRowWhere(users, "name", "new_user2"), "name"));
    }

    @Test
    public void testAlterRoleRenameUppercasesName() {
        engine.execute("CREATE ROLE old_role2");
        engine.execute("ALTER ROLE old_role2 RENAME TO new_role2");
        final ResultSet roles = engine.executeQuery("SHOW ROLES LIKE 'new_role2'");
        assertEquals("NEW_ROLE2", cell(roles, soleRowWhere(roles, "name", "new_role2"), "name"));
    }

    @Test
    public void testDefaultRoleUppercasedOnCreateAndAlter() {
        engine.execute("CREATE ROLE analyst_role");
        // CREATE USER ... DEFAULT_ROLE upper-cases the referenced role (roles are stored upper-cased).
        engine.execute("CREATE USER dr_user PASSWORD = 'p' DEFAULT_ROLE = analyst_role");
        ResultSet users = engine.executeQuery("SHOW USERS LIKE 'dr_user'");
        assertEquals("ANALYST_ROLE", cell(users, soleRowWhere(users, "name", "dr_user"), "default_role"));
        // ALTER USER ... SET DEFAULT_ROLE upper-cases it too, symmetric with CREATE.
        engine.execute("CREATE ROLE other_role");
        engine.execute("ALTER USER dr_user SET DEFAULT_ROLE = other_role");
        users = engine.executeQuery("SHOW USERS LIKE 'dr_user'");
        assertEquals("OTHER_ROLE", cell(users, soleRowWhere(users, "name", "dr_user"), "default_role"));
    }

    @Test
    public void testAlterMaterializedViewRenameRekeys() {
        engine.execute("CREATE TABLE mv_src (id INTEGER, val INTEGER)");
        engine.execute("CREATE MATERIALIZED VIEW mv_old AS SELECT id, val FROM mv_src");
        engine.execute("ALTER MATERIALIZED VIEW mv_old RENAME TO mv_new");

        // Re-keyed: listed under the new name, gone under the old.
        final ResultSet renamed = engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE 'mv_new'");
        assertEquals("MV_NEW", cell(renamed, soleRowWhere(renamed, "name", "mv_new"), "name"));
        assertEquals(0, engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE 'mv_old'").getRowCount());
    }

    @Test
    public void testShowSchemasIsCurrentCaseInsensitive() {
        // test_schema (set current in setup) is stored verbatim/lower-case while the current schema is
        // tracked upper-cased; SHOW SCHEMAS must still flag it is_current = Y (case-insensitive match).
        final ResultSet rs = engine.executeQuery("SHOW SCHEMAS");
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
        // A freshly created role is not granted to the shared live session's user, so USE ROLE of
        // it refuses there; the grantor rule is asserted embedded.
        Assumptions.assumeFalse(isLiveSnowflake(),
            "USE ROLE of a just-created role needs a grant to the session user on a live account");
        engine.execute("CREATE ROLE granting_role");
        engine.execute("CREATE ROLE child_role");
        engine.execute("CREATE ROLE parent_role");
        engine.execute("USE ROLE granting_role");
        engine.execute("GRANT ROLE child_role TO ROLE parent_role");
        // granted_by reflects the session's current role at GRANT time, not a hard-coded SYSADMIN.
        final ResultSet grants = engine.executeQuery("SHOW GRANTS OF ROLE child_role");
        assertEquals("GRANTING_ROLE",
            cell(grants, soleRowWhere(grants, "grantee_name", "PARENT_ROLE"), "granted_by"));
    }

    @Test
    public void testObjectOwnerStampedWithCreatingRole() {
        // Same live precondition as the grantor test: the fresh role is not granted to the shared
        // session's user, so USE ROLE refuses there.
        Assumptions.assumeFalse(isLiveSnowflake(),
            "USE ROLE of a just-created role needs a grant to the session user on a live account");
        engine.execute("CREATE ROLE data_owner");
        engine.execute("USE ROLE data_owner");
        engine.execute("CREATE TABLE owned_tbl (id INTEGER)");
        // SHOW TABLES surfaces the creating role as owner (no longer a hard-coded SYSADMIN).
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE 'owned_tbl'");
        assertEquals("DATA_OWNER",
            cell(rs, soleRowWhere(rs, "name", "owned_tbl"), "owner"),
            "table owner should be the creating role");
    }

    @Test
    public void testAlterColumnSetDefaultIsRestrictedToSequences() {
        // ALTER COLUMN SET DEFAULT is refused in almost every shape. The only accepted one is
        // re-pointing a column that ALREADY has a sequence default at a sequence; adding a sequence
        // default where there was none, and any literal default at all, both raise
        // "Unsupported feature 'Alter Column Set Default'." DROP DEFAULT is unrestricted.
        engine.execute("CREATE TABLE def_tbl (id INT, region VARCHAR(50) NOT NULL DEFAULT 'EU')");
        engine.execute("INSERT INTO def_tbl(id) VALUES (1)");
        final ResultSet rs = engine.executeQuery("SELECT region FROM def_tbl");
        assertEquals("EU", rs.getRows().get(0).getValue(0));

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE def_tbl ALTER COLUMN region SET DEFAULT 'US'");
            }
        });

        // The ONE accepted shape: a column that ALREADY carries a sequence default may be re-pointed
        // at a sequence — even a different one. Adding a sequence default to a column that has none
        // is refused just like a literal, which is what this test used to do on `id`.
        engine.execute("CREATE SEQUENCE def_seq");
        engine.execute("CREATE SEQUENCE def_seq2");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE def_tbl ALTER COLUMN id SET DEFAULT def_seq.NEXTVAL");
            }
        });
        engine.execute("CREATE TABLE seq_tbl (id NUMBER DEFAULT def_seq.NEXTVAL, txt VARCHAR)");
        engine.execute("ALTER TABLE seq_tbl ALTER COLUMN id SET DEFAULT def_seq2.NEXTVAL");

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
        // Same live precondition as the grantor test: the fresh role is not granted to the shared
        // session's user, so USE ROLE refuses there.
        Assumptions.assumeFalse(isLiveSnowflake(),
            "USE ROLE of a just-created role needs a grant to the session user on a live account");
        // is_current must track the session's current role, not a hard-coded SYSADMIN.
        engine.execute("CREATE ROLE my_show_role");
        engine.execute("USE ROLE my_show_role");
        final ResultSet rs = engine.executeQuery("SHOW ROLES");
        boolean checkedCurrent = false;
        boolean checkedSysadmin = false;
        while (rs.next()) {
            final String name = (String) rs.getValue("name");
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
                final Object colValue = rs.getValue(i);
                if (colValue != null && colValue.toString().equalsIgnoreCase(value)) {
                    return true;
                }
            }
        }
        return false;
    }
}
