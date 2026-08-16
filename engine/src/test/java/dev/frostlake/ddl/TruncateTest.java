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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TRUNCATE TABLE functionality
 */
public class TruncateTest extends BaseDatabaseTest {

    /**
     * Snowflake does not ENFORCE primary keys at all — a PK is metadata there, so nothing is refused
     * and nothing throws. Frostlake's enforcement is an EngineConfig toggle reached through the
     * storage engine, which makes this a test of a Frostlake-only feature rather than of agreement.
     */
    private static final String PK_ENFORCEMENT_IS_FROSTLAKE_ONLY =
        "primary-key enforcement is a Frostlake toggle; Snowflake does not enforce keys";

    @Test
    public void testTruncateTable() {
        // Create table and insert data
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Charlie')");

        // Verify table has data
        final ResultSet beforeTruncate = engine.executeQuery("SELECT * FROM users");
        assertEquals(3, beforeTruncate.getRowCount(), "Table should have 3 rows before truncate");

        // Truncate the table
        engine.execute("TRUNCATE TABLE users");

        // Verify table is empty
        final ResultSet afterTruncate = engine.executeQuery("SELECT * FROM users");
        assertEquals(0, afterTruncate.getRowCount(), "Table should be empty after truncate");

        // Verify table structure still exists
        final ResultSet tables = engine.executeQuery("SHOW TABLES");
        boolean foundUsers = false;
        while (tables.next()) {
            final String tableName = (String) tables.getValue("name");
            if ("USERS".equalsIgnoreCase(tableName)) {
                foundUsers = true;
                break;
            }
        }
        assertTrue(foundUsers, "Table should still exist after truncate");
    }

    @Test
    public void testTruncateTableWithoutTableKeyword() {
        // Test TRUNCATE without TABLE keyword (optional in Snowflake)
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget'), (2, 'Gadget')");

        // Truncate using just TRUNCATE (without TABLE keyword)
        engine.execute("TRUNCATE products");

        // Verify table is empty
        final ResultSet result = engine.executeQuery("SELECT * FROM products");
        assertEquals(0, result.getRowCount(), "Table should be empty after truncate");
    }

    @Test
    public void testTruncateEmptyTable() {
        // Truncate an already empty table
        engine.execute("CREATE TABLE empty_table (id INTEGER, value VARCHAR)");

        // Truncate the empty table (should not error)
        engine.execute("TRUNCATE TABLE empty_table");

        // Verify still empty
        final ResultSet result = engine.executeQuery("SELECT * FROM empty_table");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testTruncateQualifiedName() {
        // Test TRUNCATE with schema-qualified table name
        engine.execute("CREATE SCHEMA schema1");
        engine.execute("CREATE TABLE schema1.test_table (id INTEGER, data VARCHAR)");
        engine.execute("INSERT INTO schema1.test_table VALUES (1, 'Data1'), (2, 'Data2')");

        // Truncate using qualified name
        engine.execute("TRUNCATE TABLE schema1.test_table");

        // Verify empty
        final ResultSet result = engine.executeQuery("SELECT * FROM schema1.test_table");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testTruncateFullyQualifiedName() {
        // Test TRUNCATE with fully qualified table name (database.schema.table)
        engine.execute("CREATE DATABASE db1");
        engine.execute("USE DATABASE db1");
        engine.execute("CREATE SCHEMA schema1");
        engine.execute("CREATE TABLE schema1.test_table (id INTEGER)");
        engine.execute("INSERT INTO schema1.test_table VALUES (1), (2), (3)");

        // Use different database context
        engine.execute("CREATE DATABASE db2");
        engine.execute("USE DATABASE db2");

        // Truncate using fully qualified name
        engine.execute("TRUNCATE TABLE db1.schema1.test_table");

        // Verify empty
        final ResultSet result = engine.executeQuery("SELECT * FROM db1.schema1.test_table");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testTruncateIfExists() {
        // Test TRUNCATE TABLE IF EXISTS on existing table
        engine.execute("CREATE TABLE existing_table (id INTEGER)");
        engine.execute("INSERT INTO existing_table VALUES (1)");

        // This should succeed
        engine.execute("TRUNCATE TABLE IF EXISTS existing_table");

        final ResultSet result = engine.executeQuery("SELECT * FROM existing_table");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testTruncateIfExistsNonExistent() {
        // Test TRUNCATE TABLE IF EXISTS on non-existent table (should not error)

        // This should not throw an exception
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("TRUNCATE TABLE IF EXISTS non_existent_table");
                
            }
        });
    }

    @Test
    public void testTruncateNonExistentTable() {
        // Test TRUNCATE on non-existent table (should error)

        final RuntimeException exception = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("TRUNCATE TABLE non_existent_table");
                
            }
        });
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testTruncateTableWithPrimaryKey() {
        Assumptions.assumeFalse(isLiveSnowflake(), PK_ENFORCEMENT_IS_FROSTLAKE_ONLY);
        // Ensure primary key constraint is maintained after truncate
        engine.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        // Truncate
        engine.execute("TRUNCATE TABLE users");

        // Verify we can insert data again with same IDs
        engine.execute("INSERT INTO users VALUES (1, 'NewAlice')");
        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());

        // Verify primary key constraint works when enforcement is enabled
        engine.getStorageEngine().setEnforcePrimaryKey(true);
        try {
            final RuntimeException exception = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    engine.execute("INSERT INTO users VALUES (1, 'Duplicate')");
                    
                }
            });
            assertTrue(exception.getMessage().toLowerCase().contains("duplicate") ||
                       exception.getMessage().toLowerCase().contains("primary key"));
        } finally {
            engine.getStorageEngine().setEnforcePrimaryKey(false);
        }
    }

    @Test
    public void testTruncateAndReinsert() {
        // Verify we can insert data after truncating
        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO data VALUES (1, 'Old')");

        // Truncate
        engine.execute("TRUNCATE TABLE data");

        // Insert new data
        engine.execute("INSERT INTO data VALUES (2, 'New')");

        // Verify new data exists
        final ResultSet result = engine.executeQuery("SELECT * FROM data");
        assertEquals(1, result.getRowCount());
        assertTrue(result.next());
        assertEquals(2L, result.getValue("id"));
        assertEquals("New", result.getValue("value"));
    }

    @Test
    public void testTruncateMultipleTimes() {
        // Truncate the same table multiple times
        engine.execute("CREATE TABLE test_table (id INTEGER)");

        // Insert and truncate multiple times
        for (int i = 0; i < 3; i++) {
            engine.execute("INSERT INTO test_table VALUES (" + i + ")");
            engine.execute("TRUNCATE TABLE test_table");

            final ResultSet result = engine.executeQuery("SELECT * FROM test_table");
            assertEquals(0, result.getRowCount(), "Table should be empty after truncate #" + (i + 1));
        }
    }

    @Test
    public void testTruncateTableWithComment() {
        // Ensure table comment is preserved after truncate
        engine.execute("CREATE TABLE users (id INTEGER) COMMENT = 'User table'");
        engine.execute("INSERT INTO users VALUES (1)");

        // Truncate
        engine.execute("TRUNCATE TABLE users");

        // Table should still exist with structure intact
        final ResultSet tables = engine.executeQuery("SHOW TABLES");
        boolean found = false;
        while (tables.next()) {
            final String tableName = (String) tables.getValue("name");
            if ("USERS".equalsIgnoreCase(tableName)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Table should still exist with comment after truncate");
    }

    @Test
    public void testTruncateLargeTable() {
        // Test truncating a table with many rows
        engine.execute("CREATE TABLE large_table (id INTEGER, value VARCHAR)");

        // Insert many rows
        for (int i = 0; i < 100; i++) {
            engine.execute("INSERT INTO large_table VALUES (" + i + ", 'Value" + i + "')");
        }

        // Verify row count before truncate
        final ResultSet beforeTruncate = engine.executeQuery("SELECT * FROM large_table");
        assertEquals(100, beforeTruncate.getRowCount());

        // Truncate
        engine.execute("TRUNCATE TABLE large_table");

        // Verify empty
        final ResultSet afterTruncate = engine.executeQuery("SELECT * FROM large_table");
        assertEquals(0, afterTruncate.getRowCount());
    }
}
