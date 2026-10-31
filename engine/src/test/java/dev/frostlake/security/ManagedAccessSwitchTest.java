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
 * ALTER SCHEMA … ENABLE MANAGED ACCESS asks the schema's owner to hold MANAGE GRANTS too, and every future grant in
 * the schema to be revoked first; DISABLE asks MANAGE GRANTS only while a future grant in the schema is open (the
 * ALTER SCHEMA page). The live account allows no CREATE ROLE, CREATE USER or GRANT, so the rule is held by the
 * embedded engine.
 */
public class ManagedAccessSwitchTest {

    private static final String DONE = "Statement executed successfully.";

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE mas");
        engine.execute("CREATE ROLE a");
        engine.execute("CREATE ROLE x");
        engine.execute("CREATE USER ua");
        engine.execute("GRANT ROLE a TO USER ua");
        engine.execute("GRANT USAGE, CREATE SCHEMA ON DATABASE mas TO ROLE a");
        as("ua", "a", "CREATE SCHEMA mas.sa");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** One user's answer - rows as "a, b | c, d", or the refusal - then back to the administrator. */
    private String as(final String user, final String role, final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(user);
        engine.setCurrentRole(role);
        try {
            return rows(sql);
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser(adminUser);
            engine.setCurrentRole(adminRole);
        }
    }

    /** The answer - rows as "a, b | c, d", or the refusal. */
    private String rows(final String sql) {
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
        }
    }

    /** The schema's options as its owner lists them: the administrator sees no schema of A's. */
    private String options(final String schema) {
        return as("ua", "a", "SHOW SCHEMAS LIKE '" + schema + "' IN DATABASE mas ->> SELECT \"name\", \"options\" "
            + "FROM $1");
    }

    private static String unauthorized(final String verb, final String schema, final String when) {
        return "Cannot " + verb + " managed access on schema 'MAS." + schema + "'" + when + ": requires OWNERSHIP of "
            + "the schema and MANAGE GRANTS or an administrative role (ACCOUNTADMIN / SECURITYADMIN / SYSADMIN)";
    }

    @Test
    public void theSchemaOwnerEnablesManagedAccessOnlyWithManageGrants() {
        assertEquals(unauthorized("enable", "SA", ""), as("ua", "a", "ALTER SCHEMA mas.sa ENABLE MANAGED ACCESS"));
        assertEquals("SA, ", options("SA"));
        engine.execute("GRANT MANAGE GRANTS ON ACCOUNT TO ROLE a");
        assertEquals(DONE, as("ua", "a", "ALTER SCHEMA mas.sa ENABLE MANAGED ACCESS"));
        assertEquals("SA, MANAGED ACCESS", options("SA"));
    }

    @Test
    public void anOpenFutureGrantInTheSchemaIsRevokedBeforeManagedAccessIsEnabled() {
        engine.execute("CREATE SCHEMA mas.fg");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA mas.fg TO ROLE x");
        assertEquals("Cannot enable managed access on schema 'MAS.FG': the future grants in it must be revoked first",
            rows("ALTER SCHEMA mas.fg ENABLE MANAGED ACCESS"));
        engine.execute("REVOKE SELECT ON FUTURE TABLES IN SCHEMA mas.fg FROM ROLE x");
        assertEquals(DONE, rows("ALTER SCHEMA mas.fg ENABLE MANAGED ACCESS"));
        // A database's future grant is no grant in the schema.
        engine.execute("CREATE SCHEMA mas.fd");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN DATABASE mas TO ROLE x");
        assertEquals(DONE, rows("ALTER SCHEMA mas.fd ENABLE MANAGED ACCESS"));
    }

    @Test
    public void disablingAsksManageGrantsOnlyWhileAFutureGrantIsOpen() {
        engine.execute("GRANT MANAGE GRANTS ON ACCOUNT TO ROLE a");
        as("ua", "a", "ALTER SCHEMA mas.sa ENABLE MANAGED ACCESS");
        engine.execute("REVOKE MANAGE GRANTS ON ACCOUNT FROM ROLE a");
        assertEquals(DONE, as("ua", "a", "ALTER SCHEMA mas.sa DISABLE MANAGED ACCESS"));
        engine.execute("GRANT MANAGE GRANTS ON ACCOUNT TO ROLE a");
        as("ua", "a", "ALTER SCHEMA mas.sa ENABLE MANAGED ACCESS");
        engine.execute("REVOKE MANAGE GRANTS ON ACCOUNT FROM ROLE a");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA mas.sa TO ROLE x");
        assertEquals(unauthorized("disable", "SA", " while future grants are defined in it"),
            as("ua", "a", "ALTER SCHEMA mas.sa DISABLE MANAGED ACCESS"));
        assertEquals("SA, MANAGED ACCESS", options("SA"));
        engine.execute("GRANT MANAGE GRANTS ON ACCOUNT TO ROLE a");
        assertEquals(DONE, as("ua", "a", "ALTER SCHEMA mas.sa DISABLE MANAGED ACCESS"));
        assertEquals("SA, ", options("SA"));
    }
}
