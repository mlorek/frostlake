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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A database role keeps the case of a quoted name: it is a role of its own beside the one an unquoted name folds
 * to, and every sentence and listing names it as it is. SHOW DATABASE ROLES reads its modifiers.
 */
public class DatabaseRoleNamesTest extends BaseDatabaseTest {

    private String status(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private List<String> column(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(rs.getColumnIndex(column))));
        }
        return out;
    }

    private void refused(final String sql, final String fragment) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refusal.getMessage().contains(fragment), sql + " => " + refusal.getMessage());
    }

    @Test
    public void aQuotedNameIsARoleOfItsOwn() {
        assertEquals("Role TEST_DB.\"dr_low\" successfully created.", status("CREATE DATABASE ROLE \"dr_low\""));
        assertEquals("Role TEST_DB.DR_UP successfully created.", status("CREATE DATABASE ROLE dr_up"));
        refused("CREATE DATABASE ROLE \"dr_low\"", "Object 'TEST_DB.\"dr_low\"' already exists.");
        assertEquals("TEST_DB.\"dr_low\" already exists, statement succeeded.",
            status("CREATE DATABASE ROLE IF NOT EXISTS \"dr_low\""));
        assertEquals("Role TEST_DB.DR_LOW successfully created.", status("CREATE DATABASE ROLE dr_low"));
        assertEquals("Role TEST_DB.\"Dr Mixed\" successfully created.", status("CREATE DATABASE ROLE \"Dr Mixed\""));
        assertEquals("[DR_LOW, DR_UP, Dr Mixed, dr_low]",
            column("SHOW DATABASE ROLES IN DATABASE test_db", "name").toString());
        refused("ALTER DATABASE ROLE \"nosuch\" RENAME TO x",
            "Database role 'TEST_DB.\"nosuch\"' does not exist or not authorized.");
        refused("DROP DATABASE ROLE \"dr_up\"", "Database role 'TEST_DB.\"dr_up\"' does not exist or not authorized.");
    }

    @Test
    public void theGrantListingsNameItAsItIs() {
        engine.executeQuery("CREATE DATABASE ROLE \"dr_low\"");
        engine.executeQuery("CREATE DATABASE ROLE dr_low");
        engine.executeQuery("CREATE DATABASE ROLE dr_up");
        engine.executeQuery("CREATE DATABASE ROLE \"Dr Mixed\"");
        engine.executeQuery("GRANT USAGE ON SCHEMA test_schema TO DATABASE ROLE \"dr_low\"");
        engine.executeQuery("GRANT USAGE ON SCHEMA test_schema TO DATABASE ROLE dr_low");
        engine.executeQuery("GRANT DATABASE ROLE \"dr_low\" TO DATABASE ROLE dr_up");
        engine.executeQuery("GRANT DATABASE ROLE \"Dr Mixed\" TO DATABASE ROLE \"dr_low\"");
        // A grantee is named bare and exact; a database role that is granted or listed keeps its database and its
        // quotes.
        assertEquals("[dr_low, dr_low, dr_low]",
            column("SHOW GRANTS TO DATABASE ROLE \"dr_low\"", "grantee_name").toString());
        assertEquals("[TEST_DB, TEST_DB.\"Dr Mixed\", TEST_DB.TEST_SCHEMA]",
            column("SHOW GRANTS TO DATABASE ROLE test_db.\"dr_low\"", "name").toString());
        assertEquals("[DR_LOW, DR_LOW]", column("SHOW GRANTS TO DATABASE ROLE dr_low", "grantee_name").toString());
        assertEquals("[TEST_DB.\"dr_low\"]", column("SHOW GRANTS OF DATABASE ROLE \"dr_low\"", "role").toString());
        assertEquals("[TEST_DB.\"dr_low\", TEST_DB.\"dr_low\"]",
            column("SHOW GRANTS ON DATABASE ROLE \"dr_low\"", "name").toString());
        assertEquals("[TEST_DB, TEST_DB.\"dr_low\"]", column("SHOW GRANTS TO DATABASE ROLE dr_up", "name").toString());
        final List<String> onSchema = column("SHOW GRANTS ON SCHEMA test_schema", "grantee_name");
        assertTrue(onSchema.contains("dr_low") && onSchema.contains("DR_LOW"), onSchema.toString());
        // A rename keeps the case it is given and carries the grants.
        engine.executeQuery("ALTER DATABASE ROLE \"dr_low\" SET COMMENT = 'c'");
        engine.executeQuery("ALTER DATABASE ROLE \"dr_low\" RENAME TO \"dr_low2\"");
        refused("ALTER DATABASE ROLE \"dr_low2\" RENAME TO dr_up", "Object 'TEST_DB.DR_UP' already exists.");
        assertEquals("[DR_LOW, DR_UP, Dr Mixed, dr_low2]",
            column("SHOW DATABASE ROLES IN DATABASE test_db", "name").toString());
        assertEquals("[TEST_DB, TEST_DB.\"dr_low2\"]", column("SHOW GRANTS TO DATABASE ROLE dr_up", "name").toString());
        engine.executeQuery("REVOKE DATABASE ROLE \"dr_low2\" FROM DATABASE ROLE dr_up");
        engine.executeQuery("REVOKE USAGE ON SCHEMA test_schema FROM DATABASE ROLE \"dr_low2\"");
        assertEquals("TEST_DB.\"dr_low2\" successfully dropped.", status("DROP DATABASE ROLE \"dr_low2\""));
        refused("DROP DATABASE ROLE \"dr_low2\"",
            "Database role 'TEST_DB.\"dr_low2\"' does not exist or not authorized.");
        assertEquals("TEST_DB.\"Dr Mixed\" successfully dropped.", status("DROP DATABASE ROLE \"Dr Mixed\""));
        assertEquals("[DR_LOW, DR_UP]", column("SHOW DATABASE ROLES IN DATABASE test_db", "name").toString());
    }

    @Test
    public void theListingReadsItsModifiers() {
        for (final String name : new String[] {"dr_a", "dr_b", "\"dr_c\"", "xr_d"}) {
            engine.executeQuery("CREATE DATABASE ROLE " + name);
        }
        final String all = "[DR_A, DR_B, XR_D, dr_c]";
        assertEquals(all, column("SHOW DATABASE ROLES IN DATABASE test_db", "name").toString());
        assertEquals(all, column("SHOW DATABASE ROLES IN test_db", "name").toString());
        assertEquals(all, column("SHOW DATABASE ROLES LIKE '%' IN DATABASE test_db", "name").toString());
        // The pattern matches whatever the case; the prefix and the name a page starts after are read as they are.
        assertEquals("[DR_A, DR_B, dr_c]", column("SHOW DATABASE ROLES LIKE 'dr%' IN DATABASE test_db", "name").toString());
        assertEquals("[dr_c]", column("SHOW DATABASE ROLES LIKE 'dr\\\\_c' IN DATABASE test_db", "name").toString());
        assertEquals("[]", column("SHOW DATABASE ROLES LIKE '' IN DATABASE test_db", "name").toString());
        assertEquals("[DR_A, DR_B]",
            column("SHOW DATABASE ROLES IN DATABASE test_db STARTS WITH 'DR'", "name").toString());
        assertEquals("[DR_A]", column("SHOW DATABASE ROLES LIKE 'DR%' IN DATABASE test_db LIMIT 1", "name").toString());
        assertEquals("[DR_B, dr_c]",
            column("SHOW DATABASE ROLES LIKE 'dr%' IN DATABASE test_db LIMIT 2 FROM 'DR_A'", "name").toString());
        assertEquals("[dr_c]",
            column("SHOW DATABASE ROLES LIKE 'dr%' IN DATABASE test_db LIMIT 2 FROM 'dr'", "name").toString());
        assertEquals("[DR_A, DR_B]", column("SHOW DATABASE ROLES IN DATABASE test_db LIMIT 2", "name").toString());
        refused("SHOW DATABASE ROLES", "Unsupported feature 'SHOW DATABASE ROLES'; use 'SHOW DATABASE ROLES IN DATABASE"
            + " <database_name>' instead.");
        refused("SHOW DATABASE ROLES LIKE 'DR%'", "Unsupported feature 'SHOW DATABASE ROLES LIKE 'DR%''; use 'SHOW"
            + " DATABASE ROLES IN DATABASE <database_name>' instead.");
        refused("SHOW TERSE DATABASE ROLES IN DATABASE test_db", "Unsupported feature 'SHOW TERSE DATABASE ROLES'.");
        refused("SHOW DATABASE ROLES IN DATABASE test_db LIKE 'DR%'",
            "syntax error line 1 at position 40 unexpected 'LIKE'.");
        refused("SHOW DATABASE ROLES LIKE 'x' LIKE 'y' IN DATABASE test_db",
            "syntax error line 1 at position 29 unexpected 'LIKE'.");
        refused("SHOW DATABASE ROLES LIKE 'DR%' IN DATABASE nosuch_db",
            "Database 'NOSUCH_DB' does not exist or not authorized.");
    }
}
