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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CREATE that adds no object changes no grant. CREATE ... IF NOT EXISTS over an object that is there does nothing
 * (documented), so the privileges on it stay and no future grant applies to it: future grants are for objects created
 * afterwards. CREATE OR ALTER TASK alters the task in place and keeps its privileges, and a CREATE OR REPLACE
 * TASK that is refused leaves the task as it was, privileges included. The future grants were applied, and
 * the name's grants taken back, as the handler worked out the new object's owner, before it found the object there.
 * Engine only: the live account allows no CREATE ROLE or GRANT.
 */
public class NoOpCreateKeepsGrantsTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE ROLE r2");
        engine.execute("CREATE USER u1");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("CREATE USER u2");
        engine.execute("GRANT ROLE r2 TO USER u2");
        for (final String role : new String[] {"r1", "r2"}) {
            engine.execute("GRANT USAGE ON DATABASE p TO ROLE " + role);
            engine.execute("GRANT USAGE ON SCHEMA p.public TO ROLE " + role);
        }
        engine.execute("GRANT CREATE TABLE ON SCHEMA p.public TO ROLE r1");
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
        engine.setCurrentRole(role);
    }

    private void asSystem() {
        as("SYSTEM", systemRole);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** SHOW GRANTS ON an object, one "privilege role" per row. */
    private String grantsOn(final String object) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SHOW GRANTS ON " + object).getRows()) {
            out.append(out.length() > 0 ? ", " : "").append(row.getValue(1)).append(' ').append(row.getValue(5));
        }
        return out.toString();
    }

    @Test
    public void createTableIfNotExistsOverATableKeepsItsGrantsAndAppliesNoFutureGrant() {
        engine.execute("CREATE TABLE secret (a INT)");
        engine.execute("INSERT INTO secret VALUES (42)");
        engine.execute("CREATE TABLE shared (a INT)");
        engine.execute("GRANT SELECT ON TABLE shared TO ROLE r2");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA p.public TO ROLE r1");
        as("U1", "R1");
        assertTrue(refusal("SELECT * FROM secret").contains("must have SELECT granted on TABLE P.PUBLIC.SECRET"));
        engine.execute("CREATE TABLE IF NOT EXISTS secret (a INT)");
        assertTrue(refusal("SELECT * FROM secret").contains("must have SELECT granted on TABLE P.PUBLIC.SECRET"));
        engine.execute("CREATE TABLE IF NOT EXISTS shared (a INT)");
        as("U2", "R2");
        assertEquals(0, engine.executeQuery("SELECT * FROM shared").getRows().size());
        asSystem();
        assertEquals("OWNERSHIP SYSADMIN", grantsOn("TABLE secret"));
        // A table created afterwards takes the future grant.
        as("U1", "R1");
        engine.execute("CREATE TABLE fresh (a INT)");
        assertEquals(0, engine.executeQuery("SELECT * FROM fresh").getRows().size());
        asSystem();
        assertEquals("OWNERSHIP R1, SELECT R1", grantsOn("TABLE fresh"));
    }

    @Test
    public void createIfNotExistsOverAnObjectOfEachKindKeepsItsGrants() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE SEQUENCE sq");
        engine.execute("GRANT USAGE ON SEQUENCE sq TO ROLE r2");
        engine.execute("CREATE SEQUENCE IF NOT EXISTS sq");
        assertEquals("USAGE R2, OWNERSHIP SYSADMIN", grantsOn("SEQUENCE sq"));
        engine.execute("CREATE FUNCTION f() RETURNS INT AS '1'");
        engine.execute("GRANT USAGE ON FUNCTION f() TO ROLE r2");
        engine.execute("CREATE FUNCTION IF NOT EXISTS f() RETURNS INT AS '1'");
        assertEquals("USAGE R2, OWNERSHIP SYSADMIN", grantsOn("FUNCTION f()"));
        engine.execute("CREATE PROCEDURE pr() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("GRANT USAGE ON PROCEDURE pr() TO ROLE r2");
        engine.execute("CREATE PROCEDURE IF NOT EXISTS pr() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        assertEquals("USAGE R2, OWNERSHIP SYSADMIN", grantsOn("PROCEDURE pr()"));
        engine.execute("CREATE TASK tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("GRANT MONITOR ON TASK tk TO ROLE r2");
        engine.execute("CREATE TASK IF NOT EXISTS tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertEquals("MONITOR R2, OWNERSHIP SYSADMIN", grantsOn("TASK tk"));
        engine.execute("CREATE STREAM str ON TABLE t");
        engine.execute("GRANT SELECT ON STREAM str TO ROLE r2");
        engine.execute("CREATE STREAM IF NOT EXISTS str ON TABLE t");
        assertEquals("SELECT R2, OWNERSHIP SYSADMIN", grantsOn("STREAM str"));
        engine.execute("CREATE FILE FORMAT ff");
        engine.execute("GRANT USAGE ON FILE FORMAT ff TO ROLE r2");
        engine.execute("CREATE FILE FORMAT IF NOT EXISTS ff");
        assertEquals("USAGE R2, OWNERSHIP SYSADMIN", grantsOn("FILE FORMAT ff"));
        engine.execute("CREATE VIEW v AS SELECT 1 x");
        engine.execute("GRANT SELECT ON VIEW v TO ROLE r2");
        engine.execute("CREATE VIEW IF NOT EXISTS v AS SELECT 1 x");
        assertEquals("SELECT R2, OWNERSHIP SYSADMIN", grantsOn("VIEW v"));
    }

    @Test
    public void createOrAlterTaskKeepsTheTasksGrants() {
        engine.execute("CREATE TASK a SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("GRANT MONITOR, OPERATE ON TASK a TO ROLE r2");
        engine.execute("GRANT OPERATE ON TASK a TO ROLE r1");
        engine.execute("CREATE OR ALTER TASK a SCHEDULE = '30 MINUTE' AS SELECT 2");
        assertEquals("OPERATE R1, MONITOR R2, OPERATE R2, OWNERSHIP SYSADMIN", grantsOn("TASK a"));
    }

    @Test
    public void aRefusedCreateOrReplaceTaskKeepsTheTasksGrants() {
        engine.execute("CREATE TASK a SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("GRANT MONITOR ON TASK a TO ROLE r2");
        assertTrue(refusal("CREATE OR REPLACE TASK a SCHEDULE = '60 MINUTE' AFTER a AS SELECT 1")
            .contains("cannot have both a schedule and a predecessor"));
        assertEquals("MONITOR R2, OWNERSHIP SYSADMIN", grantsOn("TASK a"));
        assertTrue(refusal("CREATE OR REPLACE TASK a WAREHOUSE = nosuch SCHEDULE = '60 MINUTE' AS SELECT 1")
            .contains("NOSUCH"));
        assertEquals("MONITOR R2, OWNERSHIP SYSADMIN", grantsOn("TASK a"));
        // A replacement that is made is a new task: the old one's grants are gone.
        engine.execute("CREATE OR REPLACE TASK a SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertEquals("OWNERSHIP SYSADMIN", grantsOn("TASK a"));
    }
}
