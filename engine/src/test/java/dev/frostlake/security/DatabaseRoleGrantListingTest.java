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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sentences and listings of database roles and of the grants that name roles: the database role's name
 * qualified in every CREATE and DROP sentence, a database role's own USAGE on its database, the holders listed
 * under SHOW GRANTS ON a role, the order of SHOW GRANTS TO and SHOW FUTURE GRANTS, and SHOW GRANTS TO USER with
 * its {@code role} column.
 */
public class DatabaseRoleGrantListingTest extends BaseDatabaseTest {

    private static final String ROLE_A = "DRL_ROLE_A";
    private static final String ROLE_B = "DRL_ROLE_B";
    private static final String USER = "DRL_USER";

    @Override
    protected void setupTest() {
        engine.executeQuery("CREATE ROLE " + ROLE_A);
        engine.executeQuery("CREATE ROLE " + ROLE_B);
        engine.executeQuery("CREATE USER " + USER);
    }

    @Override
    protected void teardownTest() {
        for (final String drop : new String[] {"DROP USER IF EXISTS " + USER, "DROP ROLE IF EXISTS " + ROLE_A,
            "DROP ROLE IF EXISTS " + ROLE_B}) {
            try {
                engine.executeQuery(drop);
            } catch (final RuntimeException ignored) {
                // cleanup only
            }
        }
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    /** Each row of a listing as its chosen cells joined with {@code |}. */
    private List<String> rows(final String sql, final String... columns) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (final String column : columns) {
                if (line.length() > 0) {
                    line.append('|');
                }
                line.append(row.getValue(rs.getColumnIndex(column)));
            }
            out.add(line.toString());
        }
        return out;
    }

    private String currentRole() {
        return status("SELECT CURRENT_ROLE()");
    }

    @Test
    public void aDatabaseRoleIsNamedWithItsDatabaseInEverySentence() {
        assertEquals("Role TEST_DB.DRL_ONE successfully created.", status("CREATE DATABASE ROLE drl_one"));
        assertEquals("TEST_DB.DRL_ONE already exists, statement succeeded.",
            status("CREATE DATABASE ROLE IF NOT EXISTS drl_one"));
        assertTrue(refusal("CREATE DATABASE ROLE drl_one").contains("Object 'TEST_DB.DRL_ONE' already exists."));
        assertEquals("Role TEST_DB.DRL_TWO successfully created.", status("CREATE DATABASE ROLE test_db.drl_two"));
        assertTrue(refusal("ALTER DATABASE ROLE drl_two RENAME TO drl_one")
            .contains("Object 'TEST_DB.DRL_ONE' already exists."));
        assertEquals("TEST_DB.DRL_TWO successfully dropped.", status("DROP DATABASE ROLE drl_two"));
        assertEquals("Drop statement executed successfully (DRL_TWO already dropped).",
            status("DROP DATABASE ROLE IF EXISTS test_db.drl_two"));
        assertTrue(refusal("CREATE OR REPLACE DATABASE ROLE IF NOT EXISTS drl_three")
            .contains("options IF NOT EXISTS and OR REPLACE are incompatible."));
    }

    @Test
    public void aDatabaseRoleHoldsUsageOnItsDatabase() {
        engine.executeQuery("CREATE DATABASE ROLE drl_usage");
        engine.executeQuery("CREATE DATABASE ROLE drl_inner");
        engine.executeQuery("CREATE TABLE drl_t (a INT)");
        engine.executeQuery("GRANT DATABASE ROLE drl_inner TO DATABASE ROLE drl_usage");
        engine.executeQuery("GRANT SELECT ON TABLE test_db.test_schema.drl_t TO DATABASE ROLE drl_usage");
        final String role = currentRole();
        // By kind granted on: DATABASE, DATABASE_ROLE, TABLE; the database's USAGE is granted by no one.
        assertEquals(List.of(
                "USAGE|DATABASE|TEST_DB|DATABASE_ROLE|DRL_USAGE|false|",
                "USAGE|DATABASE_ROLE|TEST_DB.DRL_INNER|DATABASE_ROLE|DRL_USAGE|false|" + role,
                "SELECT|TABLE|TEST_DB.TEST_SCHEMA.DRL_T|DATABASE_ROLE|DRL_USAGE|false|" + role),
            rows("SHOW GRANTS TO DATABASE ROLE drl_usage", "privilege", "granted_on", "name", "granted_to",
                "grantee_name", "grant_option", "granted_by"));
        // A USAGE granted to it explicitly is listed beside the one it holds by itself, however often it is granted.
        engine.executeQuery("GRANT USAGE ON DATABASE test_db TO DATABASE ROLE drl_inner");
        engine.executeQuery("GRANT USAGE ON DATABASE test_db TO DATABASE ROLE drl_inner");
        assertEquals(List.of("USAGE|DATABASE|TEST_DB|", "USAGE|DATABASE|TEST_DB|" + role),
            rows("SHOW GRANTS TO DATABASE ROLE drl_inner", "privilege", "granted_on", "name", "granted_by"));
    }

    @Test
    public void theGrantsOnARoleListItsHolders() {
        final String role = currentRole();
        engine.executeQuery("GRANT ROLE " + ROLE_B + " TO ROLE " + ROLE_A);
        engine.executeQuery("GRANT ROLE " + ROLE_B + " TO USER " + USER);
        // The account roles holding it are listed, by grantee name beside the owner's OWNERSHIP; the user holding
        // it is not.
        final String usage = "USAGE|ROLE|" + ROLE_B + "|ROLE|" + ROLE_A + "|false|" + role + "|ROLE";
        final String ownership = "OWNERSHIP|ROLE|" + ROLE_B + "|ROLE|" + role + "|true|" + role + "|ROLE";
        assertEquals(role.compareTo(ROLE_A) < 0 ? List.of(ownership, usage) : List.of(usage, ownership),
            rows("SHOW GRANTS ON ROLE " + ROLE_B, "privilege", "granted_on", "name", "granted_to", "grantee_name",
                "grant_option", "granted_by", "granted_by_role_type"));

        engine.executeQuery("CREATE DATABASE ROLE drl_held");
        engine.executeQuery("CREATE DATABASE ROLE drl_holder");
        engine.executeQuery("GRANT DATABASE ROLE drl_held TO ROLE " + ROLE_A);
        engine.executeQuery("GRANT DATABASE ROLE drl_held TO DATABASE ROLE drl_holder");
        engine.executeQuery("GRANT DATABASE ROLE drl_held TO USER " + USER);
        // A database role's holders are all listed: roles, database roles (named bare) and users.
        final List<String> on = rows("SHOW GRANTS ON DATABASE ROLE drl_held", "privilege", "name", "granted_to",
            "grantee_name");
        assertEquals(4, on.size(), on.toString());
        assertTrue(on.contains("OWNERSHIP|TEST_DB.DRL_HELD|ROLE|" + role), on.toString());
        assertTrue(on.contains("USAGE|TEST_DB.DRL_HELD|ROLE|" + ROLE_A), on.toString());
        assertTrue(on.contains("USAGE|TEST_DB.DRL_HELD|DATABASE_ROLE|DRL_HOLDER"), on.toString());
        assertTrue(on.contains("USAGE|TEST_DB.DRL_HELD|USER|" + USER), on.toString());
    }

    @Test
    public void grantsToARoleAreOrderedByKindThenName() {
        engine.executeQuery("CREATE DATABASE ROLE drl_order");
        engine.executeQuery("GRANT ROLE " + ROLE_B + " TO ROLE " + ROLE_A);
        engine.executeQuery("GRANT DATABASE ROLE drl_order TO ROLE " + ROLE_A);
        assertEquals(List.of("USAGE|DATABASE_ROLE|TEST_DB.DRL_ORDER", "USAGE|ROLE|" + ROLE_B),
            rows("SHOW GRANTS TO ROLE " + ROLE_A, "privilege", "granted_on", "name"));
    }

    @Test
    public void grantsToAUserCarryTheRoleColumnAndThePrivileges() {
        engine.executeQuery("CREATE DATABASE ROLE drl_user");
        engine.executeQuery("CREATE TABLE drl_ut (a INT)");
        engine.executeQuery("GRANT ROLE " + ROLE_A + " TO USER " + USER);
        engine.executeQuery("GRANT DATABASE ROLE drl_user TO USER " + USER);
        engine.executeQuery("GRANT SELECT ON TABLE test_db.test_schema.drl_ut TO USER " + USER);
        final ResultSet rs = engine.executeQuery("SHOW GRANTS TO USER " + USER);
        final List<String> columns = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            columns.add(column.getName());
        }
        assertEquals(List.of("created_on", "privilege", "granted_on", "name", "role", "granted_to", "grantee_name",
            "grant_option", "granted_by"), columns);
        assertEquals(List.of("USAGE|DATABASE_ROLE|TEST_DB.DRL_USER|null|USER",
                "USAGE|ROLE|" + ROLE_A + "|" + ROLE_A + "|USER",
                "SELECT|TABLE|TEST_DB.TEST_SCHEMA.DRL_UT|null|USER"),
            rows("SHOW GRANTS TO USER " + USER, "privilege", "granted_on", "name", "role", "granted_to"));

        // A database role is revoked from a user as it is from a role.
        assertEquals("Statement executed successfully.", status("REVOKE DATABASE ROLE drl_user FROM USER " + USER));
        assertEquals(List.of("ROLE", "TABLE"), rows("SHOW GRANTS TO USER " + USER, "granted_on"));
    }

    @Test
    public void grantingPublicToARoleOrAUserChangesNothing() {
        final String noEffect =
            "Granting role PUBLIC has no effect.  Every user and role has role PUBLIC implicitly granted.";
        assertEquals(noEffect, status("GRANT ROLE PUBLIC TO ROLE " + ROLE_A));
        assertEquals(List.of(), rows("SHOW GRANTS TO ROLE " + ROLE_A, "name"));
        assertEquals(noEffect, status("GRANT ROLE PUBLIC TO USER " + USER));
        assertEquals(List.of(), rows("SHOW GRANTS TO USER " + USER, "name"));
        assertTrue(refusal("GRANT ROLE PUBLIC TO USER drl_no_such_user")
            .contains("User 'DRL_NO_SUCH_USER' does not exist or not authorized."));
    }

    @Test
    public void aRevokeOfARoleTakesRestrictOrCascade() {
        engine.executeQuery("CREATE DATABASE ROLE drl_rc");
        engine.executeQuery("CREATE DATABASE ROLE drl_rc_holder");
        final String executed = "Statement executed successfully.";
        engine.executeQuery("GRANT DATABASE ROLE drl_rc TO ROLE " + ROLE_A);
        assertEquals(executed, status("REVOKE DATABASE ROLE drl_rc FROM ROLE " + ROLE_A + " RESTRICT"));
        engine.executeQuery("GRANT DATABASE ROLE drl_rc TO USER " + USER);
        assertEquals(executed, status("REVOKE DATABASE ROLE drl_rc FROM USER " + USER + " CASCADE"));
        engine.executeQuery("GRANT DATABASE ROLE drl_rc TO DATABASE ROLE drl_rc_holder");
        assertEquals(executed, status("REVOKE DATABASE ROLE drl_rc FROM DATABASE ROLE drl_rc_holder CASCADE"));
        assertEquals(executed, status("REVOKE DATABASE ROLE drl_rc FROM DATABASE ROLE drl_rc_holder RESTRICT"));
        assertEquals(List.of(), rows("SHOW GRANTS OF DATABASE ROLE drl_rc", "grantee_name"));
        engine.executeQuery("GRANT ROLE " + ROLE_B + " TO ROLE " + ROLE_A);
        assertEquals(executed, status("REVOKE ROLE " + ROLE_B + " FROM ROLE " + ROLE_A + " RESTRICT"));
        engine.executeQuery("GRANT ROLE " + ROLE_B + " TO USER " + USER);
        assertEquals(executed, status("REVOKE ROLE " + ROLE_B + " FROM USER " + USER + " CASCADE"));
        assertEquals(List.of(), rows("SHOW GRANTS TO ROLE " + ROLE_A, "name"));
        // One mode at most, and none on a GRANT.
        assertTrue(refusal("REVOKE DATABASE ROLE drl_rc FROM USER " + USER + " CASCADE RESTRICT")
            .contains("syntax error"));
        assertTrue(refusal("GRANT DATABASE ROLE drl_rc TO ROLE " + ROLE_A + " CASCADE").contains("syntax error"));
    }

    @Test
    public void futureGrantsAreOrderedByGranteeKindScopeAndPrivilege() {
        engine.executeQuery("CREATE SCHEMA drl_fs");
        engine.executeQuery("USE SCHEMA test_db.test_schema");
        engine.executeQuery("GRANT SELECT ON FUTURE TABLES IN SCHEMA drl_fs TO ROLE " + ROLE_B);
        engine.executeQuery("GRANT INSERT ON FUTURE TABLES IN SCHEMA drl_fs TO ROLE " + ROLE_A);
        engine.executeQuery("GRANT SELECT ON FUTURE VIEWS IN SCHEMA drl_fs TO ROLE " + ROLE_A);
        engine.executeQuery("GRANT DELETE ON FUTURE TABLES IN SCHEMA drl_fs TO ROLE " + ROLE_B);
        engine.executeQuery("GRANT USAGE ON FUTURE SEQUENCES IN SCHEMA drl_fs TO ROLE " + ROLE_A);
        engine.executeQuery("GRANT SELECT ON FUTURE TABLES IN DATABASE test_db TO ROLE " + ROLE_A);
        assertEquals(List.of(
                "USAGE|SEQUENCE|TEST_DB.DRL_FS.<SEQUENCE>|" + ROLE_A,
                "INSERT|TABLE|TEST_DB.DRL_FS.<TABLE>|" + ROLE_A,
                "SELECT|VIEW|TEST_DB.DRL_FS.<VIEW>|" + ROLE_A,
                "DELETE|TABLE|TEST_DB.DRL_FS.<TABLE>|" + ROLE_B,
                "SELECT|TABLE|TEST_DB.DRL_FS.<TABLE>|" + ROLE_B),
            rows("SHOW FUTURE GRANTS IN SCHEMA drl_fs", "privilege", "grant_on", "name", "grantee_name"));
        assertEquals(List.of(
                "USAGE|SEQUENCE|TEST_DB.DRL_FS.<SEQUENCE>",
                "SELECT|TABLE|TEST_DB.<TABLE>",
                "INSERT|TABLE|TEST_DB.DRL_FS.<TABLE>",
                "SELECT|VIEW|TEST_DB.DRL_FS.<VIEW>"),
            rows("SHOW FUTURE GRANTS TO ROLE " + ROLE_A, "privilege", "grant_on", "name"));
        // A future grant is not a grant the role holds.
        assertEquals(List.of(), rows("SHOW GRANTS TO ROLE " + ROLE_A, "name"));
    }
}
