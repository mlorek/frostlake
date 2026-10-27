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
 * In a schema WITH MANAGED ACCESS the ownership of an object passes only to a subordinate role of the schema owner,
 * and a future grant of OWNERSHIP there applies only to one, while a database's future OWNERSHIP grant does not reach
 * the schema's new objects (all documented on the GRANT page): a grant naming the object, one ON ALL objects in the
 * schema or its database, and one ON FUTURE objects in the schema each refuse any other grantee, whichever role
 * grants it. The schema itself passes to a different role only once every future OWNERSHIP grant in it is revoked.
 * The live account allows no CREATE ROLE, CREATE USER or GRANT, so the rule is held by the embedded engine.
 */
public class ManagedOwnershipGranteeTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE mog");
        engine.execute("CREATE ROLE so");
        engine.execute("CREATE ROLE oo");
        engine.execute("CREATE ROLE x");
        engine.execute("CREATE USER uso");
        engine.execute("CREATE USER uoo");
        engine.execute("GRANT ROLE so TO USER uso");
        engine.execute("GRANT ROLE oo TO USER uoo");
        engine.execute("GRANT ROLE oo TO ROLE so");
        engine.execute("GRANT USAGE ON DATABASE mog TO ROLE PUBLIC");
        engine.execute("CREATE SCHEMA mog.ms WITH MANAGED ACCESS");
        engine.execute("GRANT OWNERSHIP ON SCHEMA mog.ms TO ROLE so");
        engine.execute("GRANT USAGE ON SCHEMA mog.ms TO ROLE oo");
        engine.execute("GRANT CREATE TABLE, CREATE SEQUENCE ON SCHEMA mog.ms TO ROLE oo");
        as("uoo", "oo", "CREATE TABLE mog.ms.t1 (a INT)");
        as("uoo", "oo", "CREATE TABLE mog.ms.t2 (a INT)");
        as("uoo", "oo", "CREATE SEQUENCE mog.ms.sq1");
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

    /** The role that owns an object. */
    private String ownerOf(final String object) {
        return rows("SHOW GRANTS ON " + object + " ->> SELECT \"grantee_name\" FROM $1 WHERE \"privilege\" = "
            + "'OWNERSHIP'");
    }

    private static String refusal(final String what, final String grantee) {
        return "Cannot grant OWNERSHIP on " + what + " to " + grantee + ": schema 'MOG.MS' has managed access, where "
            + "ownership passes only to the schema's owner or a role granted to it";
    }

    @Test
    public void anObjectPassesOnlyToTheSchemaOwnerOrARoleBeneathIt() {
        assertEquals(refusal("SEQUENCE 'MOG.MS.SQ1'", "role 'X'"),
            as("uso", "so", "GRANT OWNERSHIP ON SEQUENCE mog.ms.sq1 TO ROLE x"));
        // The roles that decide every grant are bound as well.
        assertEquals(refusal("SEQUENCE 'MOG.MS.SQ1'", "role 'X'"),
            rows("GRANT OWNERSHIP ON SEQUENCE mog.ms.sq1 TO ROLE x"));
        assertEquals("OO", ownerOf("SEQUENCE mog.ms.sq1"));
        assertEquals("Statement executed successfully.",
            as("uso", "so", "GRANT OWNERSHIP ON SEQUENCE mog.ms.sq1 TO ROLE so"));
        assertEquals("SO", ownerOf("SEQUENCE mog.ms.sq1"));
        assertEquals("Statement executed successfully.",
            as("uso", "so", "GRANT OWNERSHIP ON SEQUENCE mog.ms.sq1 TO ROLE oo"));
        assertEquals("OO", ownerOf("SEQUENCE mog.ms.sq1"));
    }

    @Test
    public void aDatabaseRolePassesOnceItIsGrantedToTheSchemaOwner() {
        engine.execute("CREATE DATABASE ROLE mog.dr");
        assertEquals(refusal("TABLE 'MOG.MS.T1'", "database role 'MOG.DR'"),
            as("uso", "so", "GRANT OWNERSHIP ON TABLE mog.ms.t1 TO DATABASE ROLE mog.dr"));
        engine.execute("GRANT DATABASE ROLE mog.dr TO ROLE so");
        assertEquals("Statement executed successfully.",
            as("uso", "so", "GRANT OWNERSHIP ON TABLE mog.ms.t1 TO DATABASE ROLE mog.dr"));
        // The owning database role is named bare, as every listing names a database role as grantee.
        assertEquals("DR", ownerOf("TABLE mog.ms.t1"));
    }

    @Test
    public void aGrantOnAllObjectsAsksEveryObjectBeforeAnyChangesHands() {
        assertEquals(refusal("TABLE 'MOG.MS.T1'", "role 'X'"),
            as("uso", "so", "GRANT OWNERSHIP ON ALL TABLES IN SCHEMA mog.ms TO ROLE x"));
        engine.execute("CREATE TABLE mog.public.tp (a INT)");
        assertEquals(refusal("TABLE 'MOG.MS.T1'", "role 'X'"),
            rows("GRANT OWNERSHIP ON ALL TABLES IN DATABASE mog TO ROLE x"));
        assertEquals("OO", ownerOf("TABLE mog.ms.t1"));
        assertEquals("OO", ownerOf("TABLE mog.ms.t2"));
        assertEquals("SYSADMIN", ownerOf("TABLE mog.public.tp"));
        assertEquals("Statement executed successfully. 2 objects affected.",
            as("uso", "so", "GRANT OWNERSHIP ON ALL TABLES IN SCHEMA mog.ms TO ROLE so"));
        assertEquals("SO", ownerOf("TABLE mog.ms.t2"));
    }

    @Test
    public void aFutureOwnershipInTheSchemaGoesOnlyToARoleBeneathItsOwner() {
        assertEquals(refusal("future TABLES in schema 'MOG.MS'", "role 'X'"),
            as("uso", "so", "GRANT OWNERSHIP ON FUTURE TABLES IN SCHEMA mog.ms TO ROLE x"));
        assertEquals("Statement executed successfully.",
            as("uso", "so", "GRANT OWNERSHIP ON FUTURE TABLES IN SCHEMA mog.ms TO ROLE oo"));
        assertEquals("OWNERSHIP, TABLE, OO", rows("SHOW FUTURE GRANTS IN SCHEMA mog.ms ->> SELECT \"privilege\", "
            + "\"grant_on\", \"grantee_name\" FROM $1"));
    }

    @Test
    public void aDatabasesFutureOwnershipDoesNotReachAManagedSchema() {
        engine.execute("CREATE ROLE dbo");
        engine.execute("GRANT OWNERSHIP ON FUTURE TABLES IN DATABASE mog TO ROLE dbo");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN DATABASE mog TO ROLE dbo");
        as("uoo", "oo", "CREATE TABLE mog.ms.t3 (a INT)");
        engine.execute("CREATE TABLE mog.public.t4 (a INT)");
        assertEquals("OWNERSHIP, OO | SELECT, DBO", rows("SHOW GRANTS ON TABLE mog.ms.t3 ->> SELECT \"privilege\", "
            + "\"grantee_name\" FROM $1 ORDER BY 1"));
        assertEquals("DBO", ownerOf("TABLE mog.public.t4"));
    }

    @Test
    public void aManagedSchemaMovesOnlyOnceItsFutureOwnershipIsRevoked() {
        final String held = "Cannot transfer the ownership of managed access schema 'MOG.MS': the future grants of "
            + "OWNERSHIP in it must be revoked first";
        engine.execute("GRANT OWNERSHIP ON FUTURE TABLES IN SCHEMA mog.ms TO ROLE oo");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA mog.ms TO ROLE oo");
        assertEquals(held, rows("GRANT OWNERSHIP ON SCHEMA mog.ms TO ROLE x"));
        assertEquals("SO", ownerOf("SCHEMA mog.ms"));
        // A grant to the role that owns it already moves nothing.
        assertEquals("Statement executed successfully.", rows("GRANT OWNERSHIP ON SCHEMA mog.ms TO ROLE so"));
        // Every schema an ON ALL reaches is asked before any of them moves.
        engine.execute("CREATE SCHEMA mog.free");
        assertEquals(held, rows("GRANT OWNERSHIP ON ALL SCHEMAS IN DATABASE mog TO ROLE x"));
        assertEquals("SYSADMIN", ownerOf("SCHEMA mog.free"));
        // A future grant of another privilege does not hold the move.
        engine.execute("REVOKE OWNERSHIP ON FUTURE TABLES IN SCHEMA mog.ms FROM ROLE oo");
        assertEquals("Statement executed successfully.",
            rows("GRANT OWNERSHIP ON SCHEMA mog.ms TO ROLE x COPY CURRENT GRANTS"));
        assertEquals("X", ownerOf("SCHEMA mog.ms"));
    }

    @Test
    public void aStandardSchemaPassesOwnershipToAnyRole() {
        engine.execute("CREATE TABLE mog.public.ts (a INT)");
        assertEquals("Statement executed successfully.", rows("GRANT OWNERSHIP ON TABLE mog.public.ts TO ROLE x"));
        assertEquals("X", ownerOf("TABLE mog.public.ts"));
    }
}
