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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A privilege held through a database role authorizes what it would if the role it is granted to held it: the
 * database roles granted to a session's roles are asked with them, and the database roles granted to those,
 * however far down. A database role that is not granted to the session authorizes nothing, and neither does one
 * whose database is gone.
 */
public class DatabaseRolePrivilegesTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE g");
        engine.execute("USE SCHEMA g.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE ROLE reader_role");
        engine.execute("CREATE USER reader");
        engine.execute("GRANT ROLE reader_role TO USER reader");
        // A role reaches an object of a schema only with USAGE on its database and its schema.
        engine.execute("GRANT USAGE ON DATABASE g TO ROLE reader_role");
        engine.execute("GRANT USAGE ON SCHEMA g.PUBLIC TO ROLE reader_role");
        engine.execute("CREATE DATABASE ROLE g.dr");
        engine.execute("CREATE DATABASE ROLE g.dr_low");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** Whether the reader may run the statement: run as the reader, then back to the administrator. */
    private boolean readerMay(final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("reader");
        engine.setCurrentRole("reader_role");
        try {
            engine.execute(sql);
            return true;
        } catch (final SecurityException denied) {
            return false;
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser(adminUser);
            engine.setCurrentRole(adminRole);
        }
    }

    @Test
    public void aPrivilegeOfADatabaseRoleGrantedToTheSessionsRoleAuthorizes() {
        engine.execute("GRANT SELECT ON TABLE g.PUBLIC.t TO DATABASE ROLE g.dr");
        assertFalse(readerMay("SELECT * FROM g.PUBLIC.t"), "the database role is granted to no one");
        engine.execute("GRANT DATABASE ROLE g.dr TO ROLE reader_role");
        assertTrue(readerMay("SELECT * FROM g.PUBLIC.t"), "held through the database role");
        assertFalse(readerMay("INSERT INTO g.PUBLIC.t VALUES (2)"), "only what the database role holds");
        engine.execute("REVOKE DATABASE ROLE g.dr FROM ROLE reader_role");
        assertFalse(readerMay("SELECT * FROM g.PUBLIC.t"), "taken back with the database role");
    }

    @Test
    public void aDatabaseRoleGrantedToADatabaseRoleCarriesItsPrivilegesUp() {
        engine.execute("GRANT INSERT ON TABLE g.PUBLIC.t TO DATABASE ROLE g.dr_low");
        engine.execute("GRANT DATABASE ROLE g.dr TO ROLE reader_role");
        assertFalse(readerMay("INSERT INTO g.PUBLIC.t VALUES (2)"));
        engine.execute("GRANT DATABASE ROLE g.dr_low TO DATABASE ROLE g.dr");
        assertTrue(readerMay("INSERT INTO g.PUBLIC.t VALUES (2)"), "two database roles down");
    }

    @Test
    public void aCreateIsAuthorizedThroughADatabaseRoleToo() {
        engine.execute("GRANT CREATE TABLE ON SCHEMA g.PUBLIC TO DATABASE ROLE g.dr");
        assertFalse(readerMay("CREATE TABLE g.PUBLIC.made (a INT)"));
        engine.execute("GRANT DATABASE ROLE g.dr TO ROLE reader_role");
        assertTrue(readerMay("CREATE TABLE g.PUBLIC.made (a INT)"));
    }
}
