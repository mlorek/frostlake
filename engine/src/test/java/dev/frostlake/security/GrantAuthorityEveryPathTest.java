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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may grant and revoke, on every form of GRANT and REVOKE, as Snowflake's GRANT and REVOKE pages put it: a
 * privilege on an object is granted by its owner, by a role holding it WITH GRANT OPTION, or by MANAGE GRANTS; a bulk
 * grant asks that of every object it reaches; a future grant and a privilege on the account ask MANAGE GRANTS; a role
 * or a database role is granted by its owner or by MANAGE GRANTS; a REVOKE takes back only the grants its role made
 * unless it holds MANAGE GRANTS. The administrative roles decide every grant. The live account allows no CREATE ROLE,
 * CREATE USER or GRANT, so the rule is the documented one, held by the embedded engine.
 */
public class GrantAuthorityEveryPathTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE ROLE own");
        engine.execute("CREATE ROLE rv");
        engine.execute("CREATE USER uo");
        engine.execute("CREATE USER victim");
        engine.execute("GRANT ROLE own TO USER uo");
        engine.execute("GRANT ROLE rv TO USER victim");
        engine.execute("GRANT USAGE ON DATABASE db TO ROLE own");
        engine.execute("GRANT USAGE ON SCHEMA db.public TO ROLE own");
        engine.execute("GRANT USAGE ON DATABASE db TO ROLE rv");
        engine.execute("GRANT USAGE ON SCHEMA db.public TO ROLE rv");
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("INSERT INTO t VALUES (42)");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE rv");
        engine.execute("CREATE DATABASE ROLE dr");
        engine.execute("GRANT SELECT ON TABLE t TO DATABASE ROLE dr");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** The session as a user with only one role active. */
    private void as(final String user, final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(user);
        engine.setCurrentRole(role);
    }

    /** The session as the engine's own user and role again, which may grant and revoke anything. */
    private void asSystem() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("SYSTEM");
        engine.setCurrentRole(systemRole);
    }

    private void refused(final String sql) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
    }

    private String status(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        return String.valueOf(result.getRows().get(0).getValue(0));
    }

    @Test
    public void aBulkGrantAsksForEachObjectItReaches() {
        as("uo", "own");
        refused("SELECT * FROM t");
        refused("GRANT SELECT ON TABLE t TO ROLE own");
        refused("GRANT ALL ON ALL TABLES IN SCHEMA db.public TO ROLE own");
        refused("GRANT SELECT ON ALL TABLES IN DATABASE db TO ROLE own");
        refused("GRANT OWNERSHIP ON ALL TABLES IN SCHEMA db.public TO ROLE own COPY CURRENT GRANTS");
        refused("SELECT * FROM t");
    }

    @Test
    public void aBulkGrantOverOnlyItsOwnObjectsPasses() {
        asSystem();
        engine.execute("GRANT CREATE TABLE ON SCHEMA db.public TO ROLE own");
        engine.execute("CREATE SCHEMA mine");
        engine.execute("GRANT OWNERSHIP ON SCHEMA db.mine TO ROLE own");
        as("uo", "own");
        engine.execute("CREATE TABLE db.mine.m (id INT)");
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("GRANT SELECT ON ALL TABLES IN SCHEMA db.mine TO ROLE rv"));
        engine.execute("GRANT SELECT ON TABLE db.mine.m TO ROLE rv");
        refused("GRANT SELECT ON ALL TABLES IN DATABASE db TO ROLE rv");
    }

    @Test
    public void aRoleIsGrantedByItsOwnerOrManageGrants() {
        as("uo", "own");
        refused("GRANT ROLE ACCOUNTADMIN TO ROLE own");
        refused("GRANT ROLE SYSADMIN TO USER uo");
        refused("GRANT ROLE rv TO USER uo");
        refused("USE ROLE SYSADMIN");
        asSystem();
        engine.execute("GRANT CREATE ROLE ON ACCOUNT TO ROLE own");
        as("uo", "own");
        engine.execute("CREATE ROLE sub");
        engine.execute("GRANT ROLE sub TO USER victim");
        engine.execute("REVOKE ROLE sub FROM USER victim");
        refused("GRANT ROLE ACCOUNTADMIN TO ROLE sub");
    }

    @Test
    public void aPrivilegeOnTheAccountAsksManageGrants() {
        as("uo", "own");
        refused("GRANT MANAGE GRANTS ON ACCOUNT TO ROLE own");
        refused("GRANT CREATE DATABASE ON ACCOUNT TO ROLE own");
        refused("REVOKE CREATE DATABASE ON ACCOUNT FROM ROLE rv");
    }

    @Test
    public void aFutureGrantAsksManageGrants() {
        as("uo", "own");
        refused("GRANT SELECT ON FUTURE TABLES IN DATABASE db TO ROLE own");
        refused("GRANT SELECT ON FUTURE TABLES IN SCHEMA db.public TO ROLE own");
        refused("REVOKE SELECT ON FUTURE TABLES IN SCHEMA db.public FROM ROLE rv");
        asSystem();
        engine.execute("CREATE TABLE later (id INT)");
        as("uo", "own");
        refused("SELECT * FROM later");
    }

    @Test
    public void aDatabaseRoleIsGrantedByItsOwnerOrManageGrants() {
        as("uo", "own");
        refused("GRANT DATABASE ROLE db.dr TO ROLE own");
        refused("REVOKE DATABASE ROLE db.dr FROM ROLE own");
        refused("SELECT * FROM t");
    }

    @Test
    public void aRevokeTakesBackOnlyTheGrantsItsRoleMade() {
        as("uo", "own");
        assertEquals("Statement executed successfully. 0 objects affected.",
            status("REVOKE SELECT ON TABLE t FROM ROLE rv"));
        assertEquals("Statement executed successfully. 0 objects affected.",
            status("REVOKE SELECT ON ALL TABLES IN SCHEMA db.public FROM ROLE rv"));
        refused("REVOKE ROLE rv FROM USER victim");
        as("victim", "rv");
        assertEquals(1, engine.executeQuery("SELECT * FROM t").getRowCount());
    }

    @Test
    public void manageGrantsDecidesEveryGrant() {
        asSystem();
        engine.execute("GRANT MANAGE GRANTS ON ACCOUNT TO ROLE own");
        as("uo", "own");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE own");
        assertEquals(1, engine.executeQuery("SELECT * FROM t").getRowCount());
        engine.execute("GRANT ROLE rv TO USER uo");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA db.public TO ROLE rv");
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE SELECT ON TABLE t FROM ROLE rv"));
    }

    @Test
    public void aGrantOptionPassesOnThatPrivilegeAlone() {
        asSystem();
        engine.execute("GRANT SELECT ON TABLE t TO ROLE own WITH GRANT OPTION");
        engine.execute("CREATE ROLE rv2");
        as("uo", "own");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE rv2");
        refused("GRANT INSERT ON TABLE t TO ROLE rv2");
        refused("GRANT SELECT, INSERT ON TABLE t TO ROLE rv2");
        asSystem();
        final ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE rv2");
        boolean select = false;
        boolean insert = false;
        for (int i = 0; i < grants.getRowCount(); i++) {
            final String privilege = String.valueOf(grants.getRows().get(i).getValue(1));
            select = select || "SELECT".equals(privilege);
            insert = insert || "INSERT".equals(privilege);
        }
        assertTrue(select);
        assertFalse(insert);
    }
}
