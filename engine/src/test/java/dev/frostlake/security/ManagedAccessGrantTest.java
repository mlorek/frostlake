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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A schema WITH MANAGED ACCESS takes grant decisions away from the owners of its objects: "only the schema owner or
 * a role with the MANAGE GRANTS privilege can grant privileges on objects in the schema, including future grants",
 * and revoke them, as Snowflake's managed access pages put it. The live account allows no CREATE ROLE, CREATE USER or
 * GRANT, so the rule is the documented one, held by the embedded engine.
 */
public class ManagedAccessGrantTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE ROLE own");
        engine.execute("CREATE ROLE rx");
        engine.execute("CREATE ROLE keeper");
        engine.execute("CREATE USER uo");
        engine.execute("CREATE USER ux");
        engine.execute("CREATE USER uk");
        engine.execute("GRANT ROLE own TO USER uo");
        engine.execute("GRANT ROLE rx TO USER ux");
        engine.execute("GRANT ROLE keeper TO USER uk");
        engine.execute("CREATE SCHEMA m WITH MANAGED ACCESS");
        engine.execute("CREATE SCHEMA s");
        for (final String role : new String[] {"own", "rx", "keeper"}) {
            engine.execute("GRANT USAGE ON DATABASE db TO ROLE " + role);
            engine.execute("GRANT USAGE ON SCHEMA db.m TO ROLE " + role);
            engine.execute("GRANT USAGE ON SCHEMA db.s TO ROLE " + role);
        }
        engine.execute("GRANT CREATE TABLE ON SCHEMA db.m TO ROLE own");
        engine.execute("GRANT CREATE TABLE ON SCHEMA db.s TO ROLE own");
        engine.execute("GRANT OWNERSHIP ON SCHEMA db.m TO ROLE keeper COPY CURRENT GRANTS");
        as("uo", "own");
        engine.execute("CREATE TABLE db.m.t (id INT)");
        engine.execute("INSERT INTO db.m.t VALUES (1)");
        engine.execute("CREATE TABLE db.s.t (id INT)");
        engine.execute("INSERT INTO db.s.t VALUES (1)");
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
    public void anObjectOwnerGrantsNothingInAManagedSchema() {
        as("uo", "own");
        refused("GRANT SELECT ON TABLE db.m.t TO ROLE rx");
        refused("GRANT ALL ON TABLE db.m.t TO ROLE rx");
        refused("GRANT SELECT ON ALL TABLES IN SCHEMA db.m TO ROLE rx");
        refused("GRANT SELECT ON FUTURE TABLES IN SCHEMA db.m TO ROLE rx");
        as("ux", "rx");
        refused("SELECT COUNT(*) FROM db.m.t");
    }

    @Test
    public void anObjectOwnerStillGrantsInAStandardSchema() {
        as("uo", "own");
        engine.execute("GRANT SELECT ON TABLE db.s.t TO ROLE rx");
        refused("GRANT SELECT ON FUTURE TABLES IN SCHEMA db.s TO ROLE rx");
        as("ux", "rx");
        assertEquals(1, engine.executeQuery("SELECT COUNT(*) FROM db.s.t").getRowCount());
    }

    @Test
    public void theSchemaOwnerGrantsAndRevokesOnItsObjects() {
        as("uk", "keeper");
        engine.execute("GRANT SELECT ON TABLE db.m.t TO ROLE rx");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA db.m TO ROLE rx");
        as("ux", "rx");
        assertEquals(1, engine.executeQuery("SELECT COUNT(*) FROM db.m.t").getRowCount());
        as("uo", "own");
        assertEquals("Statement executed successfully. 0 objects affected.",
            status("REVOKE SELECT ON TABLE db.m.t FROM ROLE rx"));
        as("uk", "keeper");
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE SELECT ON TABLE db.m.t FROM ROLE rx"));
        as("ux", "rx");
        refused("SELECT COUNT(*) FROM db.m.t");
    }
}
