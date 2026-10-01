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
 * Database roles: CREATE, ALTER and DROP DATABASE ROLE, SHOW DATABASE ROLES, GRANT and REVOKE DATABASE ROLE,
 * privileges granted to a database role, and the grants listings that name one.
 */
public class DatabaseRoleTest extends BaseDatabaseTest {

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

    @Test
    public void aDatabaseRoleIsCreatedListedAlteredAndDropped() {
        assertEquals("Role TEST_DB.DR_A successfully created.",
            status("CREATE DATABASE ROLE test_db.dr_a COMMENT = 'first'"));
        assertEquals("TEST_DB.DR_A already exists, statement succeeded.",
            status("CREATE DATABASE ROLE IF NOT EXISTS test_db.dr_a"));
        assertTrue(refusal("CREATE DATABASE ROLE test_db.dr_a").contains("already exists"));
        engine.executeQuery("CREATE DATABASE ROLE dr_b");
        assertEquals(List.of("DR_A|first|0|0|0|ROLE", "DR_B||0|0|0|ROLE"),
            rows("SHOW DATABASE ROLES IN DATABASE test_db", "name", "comment", "granted_to_roles",
                "granted_to_database_roles", "granted_database_roles", "owner_role_type"));
        // FROM is a cursor: the page starts after the name.
        assertEquals(List.of("DR_B"), rows("SHOW DATABASE ROLES IN DATABASE test_db LIMIT 1 FROM 'DR_A'", "name"));
        assertEquals(List.of(), rows("SHOW DATABASE ROLES IN DATABASE test_db LIMIT 1 FROM 'DR_B'", "name"));
        assertEquals(List.of("DR_A", "DR_B"), rows("SHOW ROLES IN DATABASE test_db", "name"));

        engine.executeQuery("ALTER DATABASE ROLE test_db.dr_a SET COMMENT = 'second'");
        assertEquals(List.of("DR_A|second"), rows("SHOW DATABASE ROLES IN DATABASE test_db LIMIT 1", "name", "comment"));
        engine.executeQuery("ALTER DATABASE ROLE test_db.dr_a UNSET COMMENT");
        engine.executeQuery("ALTER DATABASE ROLE test_db.dr_a RENAME TO dr_c");
        assertEquals(List.of("DR_B|", "DR_C|"), rows("SHOW DATABASE ROLES IN DATABASE test_db", "name", "comment"));
        engine.executeQuery("ALTER DATABASE ROLE IF EXISTS test_db.no_such RENAME TO other");
        assertTrue(refusal("ALTER DATABASE ROLE test_db.no_such SET COMMENT = 'x'").contains("does not exist"));

        assertEquals("TEST_DB.DR_C successfully dropped.", status("DROP DATABASE ROLE test_db.dr_c"));
        assertEquals("Drop statement executed successfully (DR_C already dropped).",
            status("DROP DATABASE ROLE IF EXISTS test_db.dr_c"));
        assertEquals(hinted("SQL compilation error:\nDatabase role 'TEST_DB.DR_C' does not exist or not authorized."),
            refusal("DROP DATABASE ROLE test_db.dr_c"));
        assertTrue(refusal("CREATE DATABASE ROLE no_such_db.r").contains("does not exist"));
    }

    @Test
    public void aDatabaseRoleIsGrantedToRolesDatabaseRolesAndUsers() {
        engine.executeQuery("CREATE DATABASE ROLE test_db.dr_held");
        engine.executeQuery("CREATE DATABASE ROLE test_db.dr_holder");
        engine.executeQuery("CREATE ROLE dr_account_role");
        engine.executeQuery("CREATE USER dr_user");
        engine.executeQuery("GRANT DATABASE ROLE test_db.dr_held TO ROLE dr_account_role");
        engine.executeQuery("GRANT DATABASE ROLE test_db.dr_held TO DATABASE ROLE test_db.dr_holder");
        engine.executeQuery("GRANT DATABASE ROLE test_db.dr_held TO USER dr_user");
        assertEquals(List.of("TEST_DB.DR_HELD|ROLE|DR_ACCOUNT_ROLE", "TEST_DB.DR_HELD|DATABASE_ROLE|DR_HOLDER",
                "TEST_DB.DR_HELD|USER|DR_USER"),
            rows("SHOW GRANTS OF DATABASE ROLE test_db.dr_held", "role", "granted_to", "grantee_name"));
        assertEquals(List.of("USAGE|DATABASE_ROLE|TEST_DB.DR_HELD|ROLE|DR_ACCOUNT_ROLE"),
            rows("SHOW GRANTS TO ROLE dr_account_role", "privilege", "granted_on", "name", "granted_to",
                "grantee_name"));
        assertEquals(List.of("USAGE|DATABASE_ROLE|TEST_DB.DR_HELD|USER"),
            rows("SHOW GRANTS TO USER dr_user", "privilege", "granted_on", "name", "granted_to"));
        assertEquals(List.of("DR_HELD|1|1|0", "DR_HOLDER|0|0|1"),
            rows("SHOW DATABASE ROLES IN DATABASE test_db", "name", "granted_to_roles", "granted_to_database_roles",
                "granted_database_roles"));

        engine.executeQuery("REVOKE DATABASE ROLE test_db.dr_held FROM ROLE dr_account_role");
        engine.executeQuery("REVOKE DATABASE ROLE test_db.dr_held FROM DATABASE ROLE test_db.dr_holder");
        assertEquals(List.of("USER|DR_USER"),
            rows("SHOW GRANTS OF DATABASE ROLE test_db.dr_held", "granted_to", "grantee_name"));
        engine.executeQuery("DROP DATABASE ROLE test_db.dr_held");
        assertEquals(List.of(), rows("SHOW GRANTS TO USER dr_user", "name"));
        assertTrue(refusal("GRANT DATABASE ROLE test_db.no_such TO ROLE dr_account_role").contains("does not exist"));
    }

    @Test
    public void privilegesAreGrantedToAndRevokedFromADatabaseRole() {
        engine.executeQuery("CREATE TABLE dr_t (a INT)");
        engine.executeQuery("CREATE DATABASE ROLE test_db.dr_priv");
        engine.executeQuery("GRANT SELECT ON TABLE test_db.test_schema.dr_t TO DATABASE ROLE test_db.dr_priv "
            + "WITH GRANT OPTION");
        engine.executeQuery("GRANT USAGE ON SCHEMA test_db.test_schema TO DATABASE ROLE dr_priv");
        assertEquals(List.of("SELECT|TABLE|TEST_DB.TEST_SCHEMA.DR_T|DATABASE_ROLE|DR_PRIV|true",
                "USAGE|DATABASE|TEST_DB|DATABASE_ROLE|DR_PRIV|false",
                "USAGE|SCHEMA|TEST_DB.TEST_SCHEMA|DATABASE_ROLE|DR_PRIV|false"),
            sorted(rows("SHOW GRANTS TO DATABASE ROLE test_db.dr_priv", "privilege", "granted_on", "name",
                "granted_to", "grantee_name", "grant_option")));
        assertTrue(rows("SHOW GRANTS ON TABLE test_db.test_schema.dr_t", "privilege", "granted_to", "grantee_name",
            "grant_option").contains("SELECT|DATABASE_ROLE|DR_PRIV|true"));
        engine.executeQuery("REVOKE SELECT ON TABLE test_db.test_schema.dr_t FROM DATABASE ROLE test_db.dr_priv");
        assertEquals(List.of("USAGE|DATABASE", "USAGE|SCHEMA"),
            rows("SHOW GRANTS TO DATABASE ROLE test_db.dr_priv", "privilege", "granted_on"));
        assertEquals(List.of("OWNERSHIP|DATABASE_ROLE|TEST_DB.DR_PRIV|ROLE|true"),
            rows("SHOW GRANTS ON DATABASE ROLE test_db.dr_priv", "privilege", "granted_on", "name", "granted_to",
                "grant_option"));
    }

    @Test
    public void aBareNameNeedsACurrentDatabase() {
        engine.executeQuery("CREATE DATABASE dr_other_db");
        engine.executeQuery("USE DATABASE dr_other_db");
        engine.executeQuery("DROP DATABASE dr_other_db");
        assertEquals("Cannot perform CREATE DATABASE ROLE. This session does not have a current database. "
            + "Call 'USE DATABASE', or use a qualified name.", refusal("CREATE DATABASE ROLE dr_bare"));
        engine.executeQuery("CREATE DATABASE ROLE test_db.dr_qualified");
        assertEquals(List.of("DR_QUALIFIED"), rows("SHOW DATABASE ROLES IN DATABASE test_db", "name"));
    }

    @Test
    public void aDatabaseRoleCarriesTags() {
        engine.executeQuery("CREATE TAG dr_tag");
        engine.executeQuery("CREATE DATABASE ROLE test_db.dr_tagged");
        engine.executeQuery("ALTER DATABASE ROLE test_db.dr_tagged SET TAG dr_tag = 'gold'");
        assertEquals(List.of("DR_TAG|gold|DATABASE_ROLE"), rows("""
            SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES('test_db.dr_tagged', 'DATABASE ROLE'))""",
            "TAG_NAME", "TAG_VALUE", "LEVEL"));
        engine.executeQuery("ALTER DATABASE ROLE test_db.dr_tagged UNSET TAG dr_tag");
        assertEquals(List.of(), rows("""
            SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES('test_db.dr_tagged', 'DATABASE ROLE'))""",
            "TAG_NAME"));
    }

    private static List<String> sorted(final List<String> rows) {
        final List<String> out = new ArrayList<>(rows);
        Collections.sort(out);
        return out;
    }
}
