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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GET_DDL of a schema or a database recreates only what the session sees: a member it holds no privilege on, and a
 * schema of the database it does not see with all it holds, are left out, as SHOW leaves them out and as GET_DDL of
 * the member alone refuses it. GET_DDL "requires the same minimum permissions needed to view the object"
 * (documented). Engine only: the live account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class ContainerDdlVisibilityTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.priv");
        engine.execute("CREATE TABLE d1.priv.salaries (name VARCHAR, salary NUMBER) COMMENT = 'exec pay'");
        engine.execute("CREATE ROW ACCESS POLICY d1.priv.rp AS (n VARCHAR) RETURNS BOOLEAN -> n = 'ME'");
        engine.execute("CREATE TABLE d1.public.pub (a INT)");
        engine.execute("CREATE TABLE d1.public.hidden (card VARCHAR)");
        engine.execute("CREATE SEQUENCE d1.public.hidden_seq");
        engine.execute("CREATE VIEW d1.public.hidden_view AS SELECT 1 AS x");
        engine.execute("CREATE ROLE r1");
        // The user puts no secondary role in use, so the role's own privileges decide.
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.public TO ROLE r1");
        engine.execute("GRANT SELECT ON TABLE d1.public.pub TO ROLE r1");
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

    private String ddl(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aSchemasDdlListsOnlyTheMembersTheSessionSees() {
        as("U1", "R1");
        assertEquals("create or replace schema PUBLIC;\n\ncreate or replace TABLE PUB (\n\tA NUMBER(38,0)\n);",
            ddl("SELECT GET_DDL('SCHEMA', 'd1.public')"));
    }

    @Test
    public void aDatabasesDdlLeavesOutTheSchemasTheSessionDoesNotSee() {
        as("U1", "R1");
        assertEquals("create or replace database D1;\n\ncreate or replace schema PUBLIC;\n\n"
            + "create or replace TABLE PUB (\n\tA NUMBER(38,0)\n);", ddl("SELECT GET_DDL('DATABASE', 'd1')"));
    }

    @Test
    public void theOwnerStillSeesEveryMember() {
        as("SYSTEM", systemRole);
        final String text = ddl("SELECT GET_DDL('DATABASE', 'd1')");
        for (final String member : new String[] {"TABLE SALARIES", "row access policy RP", "TABLE HIDDEN",
            "sequence HIDDEN_SEQ", "view HIDDEN_VIEW", "TABLE PUB"}) {
            assertEquals(true, text.contains(member), member + " in " + text);
        }
    }
}
