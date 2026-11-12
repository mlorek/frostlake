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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A caller grant on one object belongs to the object, as the object's other grants do ("privileges are granted on
 * securable objects", documented): a rename moves it, a new object of the dropped one's name starts without it,
 * CREATE OR REPLACE drops it, and SHOW CALLER GRANTS lists none on an object that is gone, which an UNDROP brings
 * back with its grants. An inherited grant on a schema's objects moves with the schema's rename. The grant was kept
 * by the object's name, so a later object of the name picked it up and a renamed one lost it. Engine only: the live
 * account allows no CREATE ROLE or GRANT.
 */
public class CallerGrantLifetimeTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("CREATE TABLE d1.s1.staging (a INT)");
        engine.execute("CREATE ROLE rown");
        engine.execute("CREATE ROLE rcall");
        engine.execute("CREATE USER uown");
        engine.execute("CREATE USER ucall");
        engine.execute("GRANT ROLE rown TO USER uown");
        engine.execute("GRANT ROLE rcall TO USER ucall");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE rown");
        engine.execute("GRANT USAGE ON SCHEMA d1.s1 TO ROLE rown");
        engine.execute("GRANT CREATE PROCEDURE ON SCHEMA d1.s1 TO ROLE rown");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE rcall");
        engine.execute("GRANT USAGE ON SCHEMA d1.s1 TO ROLE rcall");
        engine.execute("GRANT CALLER USAGE ON DATABASE d1 TO ROLE rown");
        engine.execute("GRANT CALLER USAGE ON SCHEMA d1.s1 TO ROLE rown");
        engine.execute("GRANT CALLER SELECT ON TABLE d1.s1.staging TO ROLE rown");
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

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** The privilege, the kind and the name of each row of a SHOW CALLER GRANTS, joined by '|'. */
    private List<String> shown(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> rows = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            rows.add(row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3));
        }
        return rows;
    }

    @Test
    public void aNewTableOfADroppedOnesNameHasNoCallerGrant() {
        engine.execute("DROP TABLE d1.s1.staging");
        engine.execute("CREATE TABLE d1.s1.staging (ssn VARCHAR)");
        engine.execute("INSERT INTO d1.s1.staging VALUES ('123-45-6789')");
        engine.execute("GRANT SELECT ON TABLE d1.s1.staging TO ROLE rcall");
        assertEquals(List.of(), shown("SHOW CALLER GRANTS ON TABLE d1.s1.staging"));
        as("UOWN", "ROWN");
        engine.execute("""
            CREATE PROCEDURE d1.s1.peek() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS RESTRICTED CALLER
            AS 'DECLARE v VARCHAR; BEGIN SELECT MAX(ssn) INTO :v FROM d1.s1.staging; RETURN v; END'""");
        engine.execute("GRANT USAGE ON PROCEDURE d1.s1.peek() TO ROLE rcall");
        as("UCALL", "RCALL");
        final String refused = refusal("CALL d1.s1.peek()");
        assertTrue(refused.contains("must have SELECT granted on TABLE D1.S1.STAGING"), refused);
        as("SYSTEM", systemRole);
    }

    @Test
    public void aRenamedTableKeepsItsCallerGrant() {
        engine.execute("ALTER TABLE d1.s1.staging RENAME TO d1.s1.staged");
        assertEquals(List.of("USAGE|DATABASE|D1", "USAGE|SCHEMA|D1.S1", "SELECT|TABLE|D1.S1.STAGED"),
            shown("SHOW CALLER GRANTS TO ROLE rown"));
        engine.execute("CREATE TABLE d1.s1.staging (a INT)");
        assertEquals(List.of(), shown("SHOW CALLER GRANTS ON TABLE d1.s1.staging"));
        assertEquals(List.of("SELECT|TABLE|D1.S1.STAGED"), shown("SHOW CALLER GRANTS ON TABLE d1.s1.staged"));
    }

    @Test
    public void aReplacedTableLosesItsCallerGrant() {
        engine.execute("CREATE OR REPLACE TABLE d1.s1.staging (b INT)");
        assertEquals(List.of(), shown("SHOW CALLER GRANTS ON TABLE d1.s1.staging"));
    }

    @Test
    public void aDroppedObjectsCallerGrantIsNotListedUntilItsUndrop() {
        engine.execute("DROP TABLE d1.s1.staging");
        assertEquals(List.of("USAGE|DATABASE|D1", "USAGE|SCHEMA|D1.S1"), shown("SHOW CALLER GRANTS TO ROLE rown"));
        engine.execute("UNDROP TABLE d1.s1.staging");
        assertEquals(List.of("SELECT|TABLE|D1.S1.STAGING"), shown("SHOW CALLER GRANTS ON TABLE d1.s1.staging"));
        engine.execute("DROP SCHEMA d1.s1");
        assertEquals(List.of("USAGE|DATABASE|D1"), shown("SHOW CALLER GRANTS TO ROLE rown"));
    }

    @Test
    public void anInheritedGrantMovesWithItsSchema() {
        engine.execute("GRANT INHERITED CALLER INSERT ON ALL TABLES IN SCHEMA d1.s1 TO ROLE rcall");
        engine.execute("ALTER SCHEMA d1.s1 RENAME TO d1.s2");
        assertEquals(List.of("INSERT|TABLE|null"), shown("SHOW CALLER GRANTS TO ROLE rcall"));
        assertEquals(List.of("INSERT|TABLE|null", "SELECT|TABLE|D1.S2.STAGING"),
            shown("SHOW CALLER GRANTS ON TABLE d1.s2.staging"));
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("CREATE TABLE d1.s1.other (a INT)");
        assertEquals(List.of(), shown("SHOW CALLER GRANTS ON TABLE d1.s1.other"));
    }
}
