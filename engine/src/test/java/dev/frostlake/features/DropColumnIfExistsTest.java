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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for IF EXISTS clause in DROP COLUMN statement
 * Format: ALTER TABLE tbl DROP COLUMN IF EXISTS col
 */
public class DropColumnIfExistsTest {
    private static final Logger logger = LoggerFactory.getLogger(DropColumnIfExistsTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for DROP COLUMN IF EXISTS tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testDropColumnIfExistsWhenColumnExists() {
        logger.info("Testing DROP COLUMN IF EXISTS when column exists");

        engine.execute("CREATE TABLE test1 (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("ALTER TABLE test1 DROP COLUMN IF EXISTS email");

        Table table = engine.getCatalog().resolveTable("test1");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertTrue(table.hasColumn("id"));
        assertTrue(table.hasColumn("name"));
        assertFalse(table.hasColumn("email"));

        logger.info("DROP COLUMN IF EXISTS when column exists works correctly");
    }

    @Test
    public void testDropColumnIfExistsWhenColumnDoesNotExist() {
        logger.info("Testing DROP COLUMN IF EXISTS when column does not exist");

        engine.execute("CREATE TABLE test2 (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE test2 DROP COLUMN IF EXISTS nonexistent");

        Table table = engine.getCatalog().resolveTable("test2");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertTrue(table.hasColumn("id"));
        assertTrue(table.hasColumn("name"));

        logger.info("DROP COLUMN IF EXISTS when column does not exist works correctly (no error)");
    }

    @Test
    public void testDropColumnWithoutIfExistsThrowsError() {
        logger.info("Testing DROP COLUMN without IF EXISTS throws error");

        engine.execute("CREATE TABLE test3 (id INTEGER, name VARCHAR)");

        assertThrows(RuntimeException.class, () -> {
            engine.execute("ALTER TABLE test3 DROP COLUMN nonexistent");
        });

        logger.info("DROP COLUMN without IF EXISTS throws error correctly");
    }

    @Test
    public void testDropColumnIfExistsIsIdempotent() {
        logger.info("Testing DROP COLUMN IF EXISTS is idempotent");

        engine.execute("CREATE TABLE test4 (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("ALTER TABLE test4 DROP COLUMN IF EXISTS email");

        Table table = engine.getCatalog().resolveTable("test4");
        assertEquals(2, table.getColumns().size());

        // Dropping the same column again should not error
        engine.execute("ALTER TABLE test4 DROP COLUMN IF EXISTS email");

        table = engine.getCatalog().resolveTable("test4");
        assertEquals(2, table.getColumns().size());

        logger.info("DROP COLUMN IF EXISTS is idempotent");
    }

    @Test
    public void testDropColumnIfExistsWithQualifiedName() {
        logger.info("Testing DROP COLUMN IF EXISTS with qualified table name");

        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("CREATE TABLE test_schema.test5 (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("ALTER TABLE test_schema.test5 DROP COLUMN IF EXISTS email");

        Table table = engine.getCatalog().resolveTable("test_schema.test5");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertFalse(table.hasColumn("email"));

        logger.info("DROP COLUMN IF EXISTS with qualified name works correctly");
    }

    @Test
    public void testDropColumnIfExistsCaseInsensitive() {
        logger.info("Testing DROP COLUMN IF EXISTS is case-insensitive");

        engine.execute("CREATE TABLE test6 (id INTEGER, Name VARCHAR, EMAIL VARCHAR)");
        engine.execute("ALTER TABLE test6 DROP COLUMN IF EXISTS email");

        Table table = engine.getCatalog().resolveTable("test6");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertFalse(table.hasColumn("email"));

        logger.info("DROP COLUMN IF EXISTS case-insensitive works correctly");
    }

    @Test
    public void testDropMultipleColumnsWithIfExists() {
        logger.info("Testing DROP multiple columns with IF EXISTS");

        engine.execute("CREATE TABLE test7 (id INTEGER, col1 VARCHAR, col2 VARCHAR, col3 VARCHAR)");
        engine.execute("ALTER TABLE test7 DROP COLUMN IF EXISTS col1");
        engine.execute("ALTER TABLE test7 DROP COLUMN IF EXISTS col2");
        engine.execute("ALTER TABLE test7 DROP COLUMN IF EXISTS nonexistent");

        Table table = engine.getCatalog().resolveTable("test7");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertTrue(table.hasColumn("id"));
        assertTrue(table.hasColumn("col3"));

        logger.info("DROP multiple columns with IF EXISTS works correctly");
    }

    @Test
    public void testDropColumnIfExistsWithTableIfExists() {
        logger.info("Testing DROP COLUMN IF EXISTS with ALTER TABLE IF EXISTS");

        engine.execute("CREATE TABLE test8 (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("ALTER TABLE IF EXISTS test8 DROP COLUMN IF EXISTS email");

        Table table = engine.getCatalog().resolveTable("test8");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());

        // Both IF EXISTS should work together
        engine.execute("ALTER TABLE IF EXISTS nonexistent_table DROP COLUMN IF EXISTS email");

        logger.info("DROP COLUMN IF EXISTS with ALTER TABLE IF EXISTS works correctly");
    }

    @Test
    public void testDropColumnIfExistsWithConstraints() {
        logger.info("Testing DROP COLUMN IF EXISTS on column with constraints");

        engine.execute("CREATE TABLE test9 (id INTEGER PRIMARY KEY, name VARCHAR NOT NULL, email VARCHAR UNIQUE)");
        engine.execute("ALTER TABLE test9 DROP COLUMN IF EXISTS email");

        Table table = engine.getCatalog().resolveTable("test9");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertFalse(table.hasColumn("email"));

        logger.info("DROP COLUMN IF EXISTS with constraints works correctly");
    }

    @Test
    public void testDropColumnIfExistsWithDefault() {
        logger.info("Testing DROP COLUMN IF EXISTS on column with DEFAULT");

        engine.execute("CREATE TABLE test10 (id INTEGER, status VARCHAR DEFAULT 'active', created_at TIMESTAMP)");
        engine.execute("ALTER TABLE test10 DROP COLUMN IF EXISTS status");

        Table table = engine.getCatalog().resolveTable("test10");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertFalse(table.hasColumn("status"));

        logger.info("DROP COLUMN IF EXISTS with DEFAULT works correctly");
    }

    @Test
    public void testDropColumnIfExistsWithCollation() {
        logger.info("Testing DROP COLUMN IF EXISTS on column with COLLATE");

        engine.execute("CREATE TABLE test11 (id INTEGER, name VARCHAR COLLATE 'utf8', description VARCHAR)");
        engine.execute("ALTER TABLE test11 DROP COLUMN IF EXISTS name");

        Table table = engine.getCatalog().resolveTable("test11");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertFalse(table.hasColumn("name"));

        logger.info("DROP COLUMN IF EXISTS with COLLATE works correctly");
    }

    @Test
    public void testDropColumnIfExistsMultipleTimes() {
        logger.info("Testing DROP COLUMN IF EXISTS multiple times on same column");

        engine.execute("CREATE TABLE test12 (id INTEGER, col1 VARCHAR, col2 VARCHAR)");

        // First drop should succeed
        engine.execute("ALTER TABLE test12 DROP COLUMN IF EXISTS col1");
        Table table = engine.getCatalog().resolveTable("test12");
        assertEquals(2, table.getColumns().size());

        // Second drop should not error
        engine.execute("ALTER TABLE test12 DROP COLUMN IF EXISTS col1");
        table = engine.getCatalog().resolveTable("test12");
        assertEquals(2, table.getColumns().size());

        // Third drop should still not error
        engine.execute("ALTER TABLE test12 DROP COLUMN IF EXISTS col1");
        table = engine.getCatalog().resolveTable("test12");
        assertEquals(2, table.getColumns().size());

        logger.info("DROP COLUMN IF EXISTS multiple times works correctly");
    }

    @Test
    public void testDropColumnWithAndWithoutIfExists() {
        logger.info("Testing DROP COLUMN with and without IF EXISTS");

        engine.execute("CREATE TABLE test13 (id INTEGER, col1 VARCHAR, col2 VARCHAR, col3 VARCHAR)");

        // Drop existing column without IF EXISTS
        engine.execute("ALTER TABLE test13 DROP COLUMN col1");
        Table table = engine.getCatalog().resolveTable("test13");
        assertEquals(3, table.getColumns().size());

        // Drop existing column with IF EXISTS
        engine.execute("ALTER TABLE test13 DROP COLUMN IF EXISTS col2");
        table = engine.getCatalog().resolveTable("test13");
        assertEquals(2, table.getColumns().size());

        // Try to drop non-existing without IF EXISTS - should error
        assertThrows(RuntimeException.class, () -> {
            engine.execute("ALTER TABLE test13 DROP COLUMN col1");
        });

        // Drop non-existing with IF EXISTS - should not error
        engine.execute("ALTER TABLE test13 DROP COLUMN IF EXISTS col1");

        logger.info("DROP COLUMN with and without IF EXISTS works correctly");
    }

    @Test
    public void testDropColumnIfExistsInTransientTable() {
        logger.info("Testing DROP COLUMN IF EXISTS in TRANSIENT table");

        engine.execute("CREATE TRANSIENT TABLE test14 (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("ALTER TABLE test14 DROP COLUMN IF EXISTS email");

        Table table = engine.getCatalog().resolveTable("test14");
        assertNotNull(table);
        assertTrue(table.isTransient());
        assertEquals(2, table.getColumns().size());
        assertFalse(table.hasColumn("email"));

        logger.info("DROP COLUMN IF EXISTS in TRANSIENT table works correctly");
    }

    @Test
    public void testDropColumnIfExistsWithClusteredTable() {
        logger.info("Testing DROP COLUMN IF EXISTS in table with CLUSTER BY");

        engine.execute("CREATE TABLE test15 (id INTEGER, name VARCHAR, date DATE) CLUSTER BY (date)");
        engine.execute("ALTER TABLE test15 DROP COLUMN IF EXISTS name");

        Table table = engine.getCatalog().resolveTable("test15");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertFalse(table.hasColumn("name"));
        assertEquals(1, table.getClusterKeys().size());

        logger.info("DROP COLUMN IF EXISTS with CLUSTER BY works correctly");
    }

    @Test
    public void testDropColumnIfExistsPreservesTableComment() {
        logger.info("Testing DROP COLUMN IF EXISTS preserves table comment");

        engine.execute("CREATE TABLE test16 (id INTEGER, name VARCHAR, email VARCHAR) COMMENT = 'Test table'");
        engine.execute("ALTER TABLE test16 DROP COLUMN IF EXISTS email");

        Table table = engine.getCatalog().resolveTable("test16");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertEquals("Test table", table.getComment());

        logger.info("DROP COLUMN IF EXISTS preserves table comment correctly");
    }

    @Test
    public void testDropColumnIfExistsWithMixedOperations() {
        logger.info("Testing DROP COLUMN IF EXISTS with other ALTER TABLE operations");

        engine.execute("CREATE TABLE test17 (id INTEGER, col1 VARCHAR, col2 VARCHAR, col3 VARCHAR)");

        engine.execute("ALTER TABLE test17 DROP COLUMN IF EXISTS col1");
        engine.execute("ALTER TABLE test17 ADD COLUMN col4 INTEGER");
        engine.execute("ALTER TABLE test17 DROP COLUMN IF EXISTS col2");
        engine.execute("ALTER TABLE test17 RENAME COLUMN col3 TO col3_renamed");
        engine.execute("ALTER TABLE test17 DROP COLUMN IF EXISTS nonexistent");

        Table table = engine.getCatalog().resolveTable("test17");
        assertNotNull(table);
        assertEquals(3, table.getColumns().size());
        assertTrue(table.hasColumn("id"));
        assertTrue(table.hasColumn("col3_renamed"));
        assertTrue(table.hasColumn("col4"));
        assertFalse(table.hasColumn("col1"));
        assertFalse(table.hasColumn("col2"));

        logger.info("DROP COLUMN IF EXISTS with mixed operations works correctly");
    }
}
