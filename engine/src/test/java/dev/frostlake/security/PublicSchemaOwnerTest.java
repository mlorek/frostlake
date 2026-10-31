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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The role that creates a database owns its PUBLIC schema as it owns the database (live-verified for the account's
 * own role, which owns both), so a role with CREATE DATABASE alone works in its new database's default schema. The
 * live account allows no CREATE DATABASE, CREATE ROLE or GRANT, so the rule is held by the embedded engine.
 */
public class PublicSchemaOwnerTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE ROLE a");
        engine.execute("CREATE USER ua");
        engine.execute("GRANT ROLE a TO USER ua");
        engine.execute("GRANT CREATE DATABASE ON ACCOUNT TO ROLE a");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** The reader's answer - rows as "a, b | c, d", or the refusal - then back to the administrator. */
    private String asA(final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("ua");
        engine.setCurrentRole("a");
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
    public void theCreatingRoleOwnsThePublicSchemaAndWorksInIt() {
        asA("CREATE DATABASE ad");
        assertEquals("INFORMATION_SCHEMA,  | PUBLIC, A",
            asA("SHOW SCHEMAS IN DATABASE ad ->> SELECT \"name\", \"owner\" FROM $1 ORDER BY 1"));
        assertEquals("OWNERSHIP, A, A", asA("SHOW GRANTS ON SCHEMA ad.public ->> SELECT \"privilege\", "
            + "\"grantee_name\", \"granted_by\" FROM $1"));
        assertEquals("Statement executed successfully.", asA("USE SCHEMA ad.public"));
        assertEquals("Table T successfully created.", asA("CREATE TABLE t (a INT)"));
        assertEquals("A", asA("SHOW TABLES LIKE 'T' IN SCHEMA ad.public ->> SELECT \"owner\" FROM $1"));
    }

    @Test
    public void aReplacedDatabasesPublicSchemaIsTheReplacingRolesToo() {
        asA("CREATE DATABASE ad");
        asA("CREATE OR REPLACE DATABASE ad");
        assertEquals("A", asA("SHOW SCHEMAS LIKE 'PUBLIC' IN DATABASE ad ->> SELECT \"owner\" FROM $1"));
    }
}
