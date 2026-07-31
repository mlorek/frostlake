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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for DROP commands (TABLE, VIEW, SCHEMA, DATABASE).
 *
 * <p>The listings are read through the SHOW commands and probed for the objects this test itself
 * created, never for a total row count: a real account carries objects of its own, so only the
 * presence or absence of a named object is a portable assertion.
 */
public class DropCommandsTest extends BaseDatabaseTest {

    /** Whether the SHOW listing carries a row whose {@code name} column is this object. */
    private boolean listed(final String showSql, final String name) {
        final ResultSet rs = engine.executeQuery(showSql);
        final int nameColumn = rs.getColumnIndex("name");
        for (final Row row : rs.getRows()) {
            if (name.equalsIgnoreCase(String.valueOf(row.getValue(nameColumn)))) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testDropTable() {
        engine.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");
        assertTrue(listed("SHOW TABLES", "USERS"), "the created table should be listed");

        engine.execute("DROP TABLE users");

        assertFalse(listed("SHOW TABLES", "USERS"), "the dropped table should be gone");
    }

    @Test
    public void testDropView() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE VIEW user_view AS SELECT * FROM users");
        assertTrue(listed("SHOW VIEWS", "USER_VIEW"), "the created view should be listed");

        engine.execute("DROP VIEW user_view");

        assertFalse(listed("SHOW VIEWS", "USER_VIEW"), "the dropped view should be gone");
    }

    @Test
    public void testDropSchema() {
        engine.execute("CREATE SCHEMA drop_test_schema");
        assertTrue(listed("SHOW SCHEMAS", "DROP_TEST_SCHEMA"), "the created schema should be listed");

        engine.execute("DROP SCHEMA drop_test_schema");

        assertFalse(listed("SHOW SCHEMAS", "DROP_TEST_SCHEMA"), "the dropped schema should be gone");
    }

    @Test
    public void testDropDatabase() {
        engine.execute("CREATE DATABASE drop_test_db");
        assertTrue(listed("SHOW DATABASES", "DROP_TEST_DB"), "the created database should be listed");

        engine.execute("DROP DATABASE drop_test_db");

        assertFalse(listed("SHOW DATABASES", "DROP_TEST_DB"), "the dropped database should be gone");
    }

    @Test
    public void testDropTableDoesNotAffectOtherTables() {
        engine.execute("CREATE TABLE users (id INTEGER)");
        engine.execute("CREATE TABLE products (id INTEGER)");

        engine.execute("DROP TABLE users");

        assertFalse(listed("SHOW TABLES", "USERS"), "the dropped table should be gone");
        assertTrue(listed("SHOW TABLES", "PRODUCTS"), "the other table should survive");
    }
}
