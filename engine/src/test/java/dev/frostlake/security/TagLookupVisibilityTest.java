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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SYSTEM$GET_TAG needs "the privileges to run a DESCRIBE <object> operation on the specified object name", and
 * TAG_REFERENCES returns results "only for a role that has access to the specified object" (documented): a table, a
 * column of it, a schema or a database the role cannot see is refused as DESCRIBE refuses it, where the lookups
 * answered its tags and their values. Engine only: the live account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class TagLookupVisibilityTest {

    private static final String UNSEEN = "SQL compilation error:\nTable 'D1.PRIV.SALARIES' does not exist or not "
        + "authorized. Your primary role R1 must have at least one privilege granted on TABLE D1.PRIV.SALARIES.";

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.priv");
        engine.execute("CREATE TABLE d1.priv.salaries (name VARCHAR, salary NUMBER)");
        engine.execute("CREATE TAG d1.priv.cls");
        engine.execute("ALTER TABLE d1.priv.salaries SET TAG d1.priv.cls = 'restricted'");
        engine.execute("ALTER TABLE d1.priv.salaries MODIFY COLUMN salary SET TAG d1.priv.cls = 'pii'");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
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
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aTableTheRoleCannotSeeIsRefusedByEveryLookup() {
        as("U1", "R1");
        assertEquals(UNSEEN, refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries', 'table')"));
        assertEquals(UNSEEN, refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries.salary', 'column')"));
        assertEquals(UNSEEN, refusal("SELECT * FROM TABLE(d1.information_schema.tag_references("
            + "'d1.priv.salaries', 'table'))"));
        assertEquals(UNSEEN, refusal("SELECT * FROM TABLE(d1.information_schema.tag_references_all_columns("
            + "'d1.priv.salaries', 'table'))"));
    }

    @Test
    public void aRoleThatSeesTheTableReadsItsTags() {
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.priv TO ROLE r1");
        engine.execute("GRANT SELECT ON TABLE d1.priv.salaries TO ROLE r1");
        as("U1", "R1");
        assertEquals("restricted", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries', 'table')"));
        assertEquals("pii", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries.salary', 'column')"));
        as("SYSTEM", systemRole);
        assertEquals("restricted", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries', 'table')"));
    }
}
