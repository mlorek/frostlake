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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GRANT … WITH GRANT OPTION, REVOKE GRANT OPTION FOR, the ALL and FUTURE forms of GRANT and REVOKE, RESTRICT and
 * CASCADE, SHOW FUTURE GRANTS and SHOW GRANTS ON ROLE.
 */
public class GrantOptionAndFutureGrantTest extends BaseDatabaseTest {

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

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }
    private static List<String> sorted(final List<String> rows) {
        final List<String> out = new ArrayList<>(rows);
        Collections.sort(out);
        return out;
    }

    @Test
    public void aGrantOptionIsListedAndRevokedAlone() {
        engine.executeQuery("CREATE ROLE go_role");
        engine.executeQuery("GRANT USAGE ON DATABASE test_db TO ROLE go_role WITH GRANT OPTION");
        engine.executeQuery("GRANT MONITOR ON DATABASE test_db TO ROLE go_role");
        assertEquals(List.of("MONITOR|false", "USAGE|true"),
            sorted(rows("SHOW GRANTS TO ROLE go_role", "privilege", "grant_option")));
        engine.executeQuery("REVOKE GRANT OPTION FOR USAGE ON DATABASE test_db FROM ROLE go_role CASCADE");
        assertEquals(List.of("MONITOR|false", "USAGE|false"),
            sorted(rows("SHOW GRANTS TO ROLE go_role", "privilege", "grant_option")));
        engine.executeQuery("REVOKE USAGE ON DATABASE test_db FROM ROLE go_role RESTRICT");
        assertEquals(List.of("MONITOR"), rows("SHOW GRANTS TO ROLE go_role", "privilege"));
    }

    @Test
    public void onAllGrantsAndRevokesEachExistingObject() {
        engine.executeQuery("CREATE ROLE all_role");
        engine.executeQuery("CREATE TABLE all_t1 (a INT)");
        engine.executeQuery("CREATE TABLE all_t2 (a INT)");
        engine.executeQuery("GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA test_db.test_schema TO ROLE all_role");
        assertEquals(List.of("INSERT|TABLE|TEST_DB.TEST_SCHEMA.ALL_T1", "INSERT|TABLE|TEST_DB.TEST_SCHEMA.ALL_T2",
                "SELECT|TABLE|TEST_DB.TEST_SCHEMA.ALL_T1", "SELECT|TABLE|TEST_DB.TEST_SCHEMA.ALL_T2"),
            sorted(rows("SHOW GRANTS TO ROLE all_role", "privilege", "granted_on", "name")));
        engine.executeQuery("REVOKE INSERT ON ALL TABLES IN SCHEMA test_db.test_schema FROM ROLE all_role");
        assertEquals(List.of("SELECT|TEST_DB.TEST_SCHEMA.ALL_T1", "SELECT|TEST_DB.TEST_SCHEMA.ALL_T2"),
            sorted(rows("SHOW GRANTS TO ROLE all_role", "privilege", "name")));
        engine.executeQuery("GRANT USAGE ON ALL SCHEMAS IN DATABASE test_db TO ROLE all_role");
        assertTrue(rows("SHOW GRANTS TO ROLE all_role", "privilege", "name").contains("USAGE|TEST_DB.TEST_SCHEMA"));
        assertTrue(refusal("GRANT SELECT ON ALL TABLES IN SCHEMA test_db.no_such TO ROLE all_role")
            .contains("does not exist"));
    }

    @Test
    public void futureGrantsAreListedByScopeAndGrantee() {
        engine.executeQuery("CREATE ROLE fut_role");
        engine.executeQuery("GRANT SELECT, INSERT ON FUTURE TABLES IN SCHEMA test_db.test_schema TO ROLE fut_role "
            + "WITH GRANT OPTION");
        engine.executeQuery("GRANT USAGE ON FUTURE SCHEMAS IN DATABASE test_db TO ROLE fut_role");
        assertEquals(List.of("INSERT|TABLE|TEST_DB.TEST_SCHEMA.<TABLE>|ROLE|FUT_ROLE|true",
                "SELECT|TABLE|TEST_DB.TEST_SCHEMA.<TABLE>|ROLE|FUT_ROLE|true"),
            sorted(rows("SHOW FUTURE GRANTS IN SCHEMA test_db.test_schema", "privilege", "grant_on", "name",
                "grant_to", "grantee_name", "grant_option")));
        assertEquals(List.of("USAGE|SCHEMA|TEST_DB.<SCHEMA>"),
            rows("SHOW FUTURE GRANTS IN DATABASE test_db", "privilege", "grant_on", "name"));
        assertEquals(3, rows("SHOW FUTURE GRANTS TO ROLE fut_role", "privilege").size());
        assertEquals(List.of(), rows("SHOW GRANTS TO ROLE fut_role", "privilege"),
            "a future grant is no grant on an existing object");

        engine.executeQuery("REVOKE GRANT OPTION FOR SELECT ON FUTURE TABLES IN SCHEMA test_db.test_schema "
            + "FROM ROLE fut_role");
        engine.executeQuery("REVOKE INSERT ON FUTURE TABLES IN SCHEMA test_db.test_schema FROM ROLE fut_role");
        assertEquals(List.of("SELECT|false"),
            rows("SHOW FUTURE GRANTS IN SCHEMA test_db.test_schema", "privilege", "grant_option"));
        assertTrue(refusal("SHOW FUTURE GRANTS IN SCHEMA test_db.no_such").contains("does not exist"));
        assertTrue(refusal("SHOW FUTURE GRANTS TO ROLE no_such_role").contains("does not exist"));
    }

    @Test
    public void showGrantsOnRoleListsItsOwnership() {
        engine.executeQuery("CREATE ROLE owned_role");
        assertEquals(List.of("OWNERSHIP|ROLE|OWNED_ROLE|ROLE|true"),
            rows("SHOW GRANTS ON ROLE owned_role", "privilege", "granted_on", "name", "granted_to", "grant_option"));
        assertTrue(refusal("SHOW GRANTS ON ROLE no_such_role").contains("does not exist"));
    }

    @Test
    public void showRolesCountsItsHolders() {
        engine.executeQuery("CREATE ROLE counted_role");
        engine.executeQuery("CREATE ROLE counted_parent");
        engine.executeQuery("CREATE USER counted_user");
        engine.executeQuery("GRANT ROLE counted_role TO USER counted_user");
        engine.executeQuery("GRANT ROLE counted_role TO ROLE counted_parent");
        assertEquals(List.of("COUNTED_ROLE|1|1|0"),
            rows("SHOW ROLES LIKE 'COUNTED_ROLE'", "name", "assigned_to_users", "granted_to_roles", "granted_roles"));
    }
}
