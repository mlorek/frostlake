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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cloning asks for the source's privilege (documented, CREATE ... CLONE: USAGE on a database or a schema,
 * OWNERSHIP of a stream): a source the role does not see is refused as one that is not there, and one it sees without
 * the privilege as an insufficient privilege, as a table clone already asked SELECT. A role holding only the CREATE
 * privilege for the target cloned databases, schemas and streams it could not even see. Engine only: the live account
 * allows no CREATE ROLE or GRANT.
 */
public class ClonePrivilegesTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.priv");
        engine.execute("CREATE TABLE d1.priv.t (a INT)");
        engine.execute("CREATE STREAM d1.priv.s ON TABLE d1.priv.t");
        engine.execute("CREATE DATABASE hidden");
        engine.execute("CREATE ROLE low");
        engine.execute("CREATE USER ulow");
        engine.execute("GRANT ROLE low TO USER ulow");
        engine.execute("GRANT CREATE DATABASE ON ACCOUNT TO ROLE low");
        engine.execute("GRANT USAGE, CREATE SCHEMA ON DATABASE d1 TO ROLE low");
        engine.execute("CREATE SCHEMA d1.own");
        engine.execute("GRANT OWNERSHIP ON SCHEMA d1.own TO ROLE low");
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

    @Test
    public void anUnseenSourceIsRefusedAsMissing() {
        as("ULOW", "LOW");
        final String database = refusal("CREATE DATABASE copy_db CLONE hidden");
        assertTrue(database.startsWith(
            "SQL compilation error:\nDatabase 'HIDDEN' does not exist or not authorized."), database);
        final String schema = refusal("CREATE SCHEMA d1.copy_s CLONE d1.priv");
        assertTrue(schema.startsWith(
            "SQL compilation error:\nSchema 'D1.PRIV' does not exist or not authorized."), schema);
        final String stream = refusal("CREATE STREAM d1.own.s2 CLONE d1.priv.s");
        assertTrue(stream.startsWith(
            "SQL compilation error:\nStream 'D1.PRIV.S' does not exist or not authorized."), stream);
        as("SYSTEM", systemRole);
        assertTrue(engine.executeQuery("SHOW DATABASES LIKE 'COPY_DB'").getRows().isEmpty());
    }

    @Test
    public void aSeenSourceStillNeedsItsPrivilege() {
        engine.execute("GRANT MONITOR ON DATABASE hidden TO ROLE low");
        engine.execute("GRANT SELECT ON TABLE d1.priv.t TO ROLE low");
        engine.execute("GRANT USAGE ON SCHEMA d1.priv TO ROLE low");
        engine.execute("GRANT SELECT ON STREAM d1.priv.s TO ROLE low");
        as("ULOW", "LOW");
        final String database = refusal("CREATE DATABASE copy_db CLONE hidden");
        assertTrue(database.contains("Insufficient privileges to operate on database 'HIDDEN'"), database);
        final String stream = refusal("CREATE STREAM d1.own.s2 CLONE d1.priv.s");
        assertTrue(stream.contains("Insufficient privileges to operate on stream 'S'"), stream);
    }

    @Test
    public void theSourcesOwnerClonesIt() {
        engine.execute("GRANT OWNERSHIP ON DATABASE hidden TO ROLE low");
        as("ULOW", "LOW");
        engine.execute("CREATE DATABASE copy_db CLONE hidden");
        engine.execute("CREATE SCHEMA d1.copy_own CLONE d1.own");
    }
}
