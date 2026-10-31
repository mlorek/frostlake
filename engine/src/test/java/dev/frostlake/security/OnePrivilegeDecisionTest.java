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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One decision serves every statement that asks a privilege: one of the roles the privilege is asked of owns the
 * object or was granted the privilege, and no administrative role stands in for that (live-verified for reads and
 * writes: ACCOUNTADMIN is refused a table another role owns). A DROP decides the same way, a TRUNCATE asks TRUNCATE
 * alone, and a multi-table INSERT and a stream's source table are refused in the account's own sentence. Engine
 * only: the live account allows no GRANT or USE ROLE.
 */
public class OnePrivilegeDecisionTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE od");
        engine.execute("USE SCHEMA od.PUBLIC");
        engine.execute("CREATE ROLE app_role");
        engine.execute("CREATE ROLE owner_role");
        engine.execute("CREATE USER app_user");
        engine.execute("GRANT ROLE app_role TO USER app_user");
        engine.execute("GRANT USAGE ON DATABASE od TO ROLE app_role");
        engine.execute("GRANT USAGE ON SCHEMA od.PUBLIC TO ROLE app_role");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** The session as app_user with the one role active. */
    private void asRole(final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("app_user");
        engine.setCurrentRole(role);
    }

    /** The engine's own session again, which is asked nothing. */
    private void asSystem() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("SYSTEM");
        engine.setCurrentRole("SYSADMIN");
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private String count(final String table) {
        return String.valueOf(engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0));
    }

    @Test
    public void anAdministrativeRoleDropsOnlyWhatItOwns() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("GRANT OWNERSHIP ON TABLE t TO ROLE owner_role");
        asRole("SYSADMIN");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP TABLE t");
            }
        });
        assertEquals("SQL access control error:\nInsufficient privileges to operate on table 'T'. "
            + "Your primary role SYSADMIN must have OWNERSHIP granted on TABLE OD.PUBLIC.T.", refused.getMessage());
        // The owner's role granted to the administrative role is what lets it drop the table.
        asSystem();
        engine.execute("GRANT ROLE owner_role TO ROLE SYSADMIN");
        asRole("SYSADMIN");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP TABLE t");
            }
        });
    }

    @Test
    public void aStreamsSourceIsAskedInTheAccountsSentence() {
        engine.execute("CREATE TABLE src (a INT)");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1)");
        engine.execute("GRANT SELECT ON STREAM st TO ROLE app_role");
        asRole("app_role");
        final String refused = refusal("SELECT * FROM st");
        assertTrue(refused.contains("must have SELECT granted on TABLE OD.PUBLIC.SRC"), refused);
        asSystem();
        engine.execute("GRANT SELECT ON TABLE src TO ROLE app_role");
        asRole("app_role");
        assertEquals("1", count("st"));
    }

    @Test
    public void aMultiTableInsertIsAskedInTheAccountsSentence() {
        engine.execute("CREATE TABLE s1 (a INT)");
        engine.execute("INSERT INTO s1 VALUES (1)");
        engine.execute("CREATE TABLE d1 (a INT)");
        engine.execute("GRANT SELECT ON TABLE s1 TO ROLE app_role");
        asRole("app_role");
        final String refused = refusal("INSERT ALL INTO d1 SELECT a FROM s1");
        assertTrue(refused.contains("must have INSERT granted on TABLE OD.PUBLIC.D1"), refused);
        asSystem();
        engine.execute("GRANT INSERT ON TABLE d1 TO ROLE app_role");
        asRole("app_role");
        engine.execute("INSERT ALL INTO d1 SELECT a FROM s1");
        asSystem();
        assertEquals("1", count("d1"));
    }

    @Test
    public void aTruncateAsksTruncateAlone() {
        engine.execute("CREATE TABLE tt (a INT)");
        engine.execute("INSERT INTO tt VALUES (1)");
        engine.execute("GRANT TRUNCATE ON TABLE tt TO ROLE app_role");
        asRole("app_role");
        engine.execute("TRUNCATE TABLE tt");
        asSystem();
        assertEquals("0", count("tt"));
        engine.execute("INSERT INTO tt VALUES (1)");
        engine.execute("REVOKE TRUNCATE ON TABLE tt FROM ROLE app_role");
        engine.execute("GRANT DELETE ON TABLE tt TO ROLE app_role");
        asRole("app_role");
        final String refused = refusal("TRUNCATE TABLE tt");
        assertTrue(refused.contains("must have TRUNCATE granted on TABLE OD.PUBLIC.TT"), refused);
    }
}
