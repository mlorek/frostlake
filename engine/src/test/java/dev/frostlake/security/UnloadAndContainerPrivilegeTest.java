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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two reaches of a role's privileges (documented): an unload of a table reads it, so COPY INTO a stage FROM a table
 * needs SELECT on the table as its query form does; and a privilege on an object of a schema reaches it only with
 * USAGE on the database and the schema that contain it, the database asked first. The account is never asked for
 * these: its sessions run no role or grant statement here.
 */
public class UnloadAndContainerPrivilegeTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.PUBLIC");
        engine.execute("CREATE ROLE reader_role");
        engine.execute("CREATE USER reader");
        engine.execute("GRANT ROLE reader_role TO USER reader");
        engine.execute("CREATE TABLE secret (x INT, s VARCHAR)");
        engine.execute("INSERT INTO secret VALUES (1, 'top'), (2, 'secret')");
        engine.execute("CREATE TABLE pub (x INT)");
        engine.execute("INSERT INTO pub VALUES (3)");
        engine.execute("GRANT SELECT ON TABLE pub TO ROLE reader_role");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** The reader's answer — rows as "a, b | c, d", or the refusal — then back to the administrator. */
    private String asReader(final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("reader");
        engine.setCurrentRole("reader_role");
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser(adminUser);
            engine.setCurrentRole(adminRole);
        }
    }

    @Test
    public void anUnloadOfATableNeedsSelectOnIt() {
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE reader_role");
        engine.execute("GRANT USAGE ON SCHEMA p.PUBLIC TO ROLE reader_role");
        final String noSelect = "SQL access control error:|Insufficient privileges to operate on table 'SECRET'. Your "
            + "primary role READER_ROLE must have SELECT granted on TABLE P.PUBLIC.SECRET.";
        assertEquals(noSelect, asReader("SELECT * FROM secret"));
        assertEquals(noSelect, asReader("COPY INTO @~/upt_leak1/ FROM secret"));
        assertEquals(noSelect, asReader("COPY INTO @~/upt_leak2/ FROM (SELECT * FROM secret)"));
        assertEquals("1", asReader("COPY INTO @~/upt_pub/ FROM pub ->> SELECT \"rows_unloaded\" FROM $1"));
    }

    @Test
    public void aPrivilegeReachesAnObjectOnlyThroughItsContainersUsage() {
        assertEquals("SQL compilation error:|Database 'P' does not exist or not authorized. Your primary role "
            + "READER_ROLE must have USAGE or any other privilege granted on DATABASE P.", asReader("SELECT * FROM pub"));
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE reader_role");
        assertEquals("SQL compilation error:|Schema 'P.PUBLIC' does not exist or not authorized. Your primary role "
            + "READER_ROLE must have USAGE or any other privilege granted on SCHEMA P.PUBLIC.",
            asReader("SELECT * FROM pub"));
        engine.execute("GRANT USAGE ON SCHEMA p.PUBLIC TO ROLE reader_role");
        assertEquals("3", asReader("SELECT * FROM pub"));
        engine.execute("REVOKE USAGE ON SCHEMA p.PUBLIC FROM ROLE reader_role");
        assertEquals("SQL compilation error:|Schema 'P.PUBLIC' does not exist or not authorized. Your primary role "
            + "READER_ROLE must have USAGE or any other privilege granted on SCHEMA P.PUBLIC.",
            asReader("SELECT x FROM p.PUBLIC.pub"));
    }
}
