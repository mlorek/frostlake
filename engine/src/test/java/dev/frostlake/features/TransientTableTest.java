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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TRANSIENT table type in CREATE TABLE statement
 * TRANSIENT tables have limited Time Travel and no Fail-safe
 */
public class TransientTableTest {
    private static final Logger logger = LoggerFactory.getLogger(TransientTableTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for TRANSIENT table tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCreateTransientTable() {
        logger.info("Testing CREATE TRANSIENT TABLE");

        engine.execute("CREATE TRANSIENT TABLE test1 (id INTEGER, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("TEST1");
        assertNotNull(table);
        assertTrue(table.isTransient());
        assertFalse(table.isTemporary());

        logger.info("CREATE TRANSIENT TABLE works correctly");
    }

    @Test
    public void testCreateRegularTable() {
        logger.info("Testing CREATE TABLE without TRANSIENT");

        engine.execute("CREATE TABLE test2 (id INTEGER, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("TEST2");
        assertNotNull(table);
        assertFalse(table.isTransient());
        assertFalse(table.isTemporary());

        logger.info("Regular table is not transient");
    }

    @Test
    public void testTransientTableWithIfNotExists() {
        logger.info("Testing CREATE TRANSIENT TABLE IF NOT EXISTS");

        engine.execute("CREATE TRANSIENT TABLE IF NOT EXISTS test3 (id INTEGER, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("TEST3");
        assertNotNull(table);
        assertTrue(table.isTransient());

        // Executing again should not throw error
        engine.execute("CREATE TRANSIENT TABLE IF NOT EXISTS test3 (id INTEGER, name VARCHAR)");

        logger.info("CREATE TRANSIENT TABLE IF NOT EXISTS works correctly");
    }

    @Test
    public void testTransientTableWithQualifiedName() {
        logger.info("Testing CREATE TRANSIENT TABLE with qualified name");

        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("CREATE TRANSIENT TABLE test_schema.test4 (id INTEGER, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("TEST_SCHEMA.TEST4");
        assertNotNull(table);
        assertTrue(table.isTransient());

        logger.info("TRANSIENT table with qualified name works correctly");
    }

    @Test
    public void testTransientTableWithComment() {
        logger.info("Testing CREATE TRANSIENT TABLE with COMMENT");

        engine.execute("CREATE TRANSIENT TABLE test5 (id INTEGER, name VARCHAR) COMMENT = 'Test transient table'");

        Table table = engine.getCatalog().resolveTable("TEST5");
        assertNotNull(table);
        assertTrue(table.isTransient());
        assertEquals("Test transient table", table.getComment());

        logger.info("TRANSIENT table with COMMENT works correctly");
    }

    @Test
    public void testTransientTableWithCommentAfterTableName() {
        logger.info("Testing CREATE TRANSIENT TABLE with COMMENT after table name");

        engine.execute("CREATE TRANSIENT TABLE test6 COMMENT = 'Comment position test' (id INTEGER)");

        Table table = engine.getCatalog().resolveTable("TEST6");
        assertNotNull(table);
        assertTrue(table.isTransient());
        assertEquals("Comment position test", table.getComment());

        logger.info("TRANSIENT table with COMMENT after table name works correctly");
    }

    @Test
    public void testTransientTableWithClusterBy() {
        logger.info("Testing CREATE TRANSIENT TABLE with CLUSTER BY");

        engine.execute("""
            CREATE TRANSIENT TABLE test7 (
                id INTEGER,
                date DATE,
                value NUMBER
            ) CLUSTER BY (date)
            """);

        Table table = engine.getCatalog().resolveTable("TEST7");
        assertNotNull(table);
        assertTrue(table.isTransient());
        assertEquals(1, table.getClusterKeys().size());
        assertEquals("date", table.getClusterKeys().get(0));

        logger.info("TRANSIENT table with CLUSTER BY works correctly");
    }

    @Test
    public void testTransientTableWithConstraints() {
        logger.info("Testing CREATE TRANSIENT TABLE with constraints");

        engine.execute("""
            CREATE TRANSIENT TABLE test8 (
                id INTEGER PRIMARY KEY,
                email VARCHAR UNIQUE,
                name VARCHAR NOT NULL,
                status VARCHAR DEFAULT 'active'
            )
            """);

        Table table = engine.getCatalog().resolveTable("TEST8");
        assertNotNull(table);
        assertTrue(table.isTransient());
        assertEquals(1, table.getPrimaryKeys().size());
        assertEquals("ID", table.getPrimaryKeys().get(0));

        logger.info("TRANSIENT table with constraints works correctly");
    }

    @Test
    public void testCloneTransientTable() {
        logger.info("Testing CLONE preserves TRANSIENT flag");

        engine.execute("CREATE TRANSIENT TABLE source (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE test9 CLONE source");

        Table sourceTable = engine.getCatalog().resolveTable("SOURCE");
        Table clonedTable = engine.getCatalog().resolveTable("TEST9");

        assertNotNull(sourceTable);
        assertNotNull(clonedTable);
        assertTrue(sourceTable.isTransient());
        assertTrue(clonedTable.isTransient());

        logger.info("CLONE preserves TRANSIENT flag correctly");
    }

    @Test
    public void testCloneRegularTableAsTransient() {
        logger.info("Testing CLONE regular table as TRANSIENT");

        engine.execute("CREATE TABLE source2 (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TRANSIENT TABLE test10 CLONE source2");

        Table sourceTable = engine.getCatalog().resolveTable("SOURCE2");
        Table clonedTable = engine.getCatalog().resolveTable("TEST10");

        assertNotNull(sourceTable);
        assertNotNull(clonedTable);
        assertFalse(sourceTable.isTransient());
        assertTrue(clonedTable.isTransient());

        logger.info("CLONE regular table as TRANSIENT works correctly");
    }

    @Test
    public void testCloneTransientTableAsRegular() {
        logger.info("Testing CLONE transient table without TRANSIENT keyword");

        engine.execute("CREATE TRANSIENT TABLE source3 (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE test11 CLONE source3");

        Table sourceTable = engine.getCatalog().resolveTable("SOURCE3");
        Table clonedTable = engine.getCatalog().resolveTable("TEST11");

        assertNotNull(sourceTable);
        assertNotNull(clonedTable);
        assertTrue(sourceTable.isTransient());
        assertTrue(clonedTable.isTransient());

        logger.info("CLONE without explicit TRANSIENT inherits from source");
    }

    @Test
    public void testTransientTableWithMultipleColumns() {
        logger.info("Testing CREATE TRANSIENT TABLE with multiple columns");

        engine.execute("""
            CREATE TRANSIENT TABLE test12 (
                id INTEGER,
                first_name VARCHAR,
                last_name VARCHAR,
                email VARCHAR,
                created_at TIMESTAMP,
                updated_at TIMESTAMP
            )
            """);

        Table table = engine.getCatalog().resolveTable("TEST12");
        assertNotNull(table);
        assertTrue(table.isTransient());
        assertEquals(6, table.getColumns().size());

        logger.info("TRANSIENT table with multiple columns works correctly");
    }

    @Test
    public void testTransientTableWithCollation() {
        logger.info("Testing CREATE TRANSIENT TABLE with COLLATE");

        engine.execute("""
            CREATE TRANSIENT TABLE test13 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8'
            )
            """);

        Table table = engine.getCatalog().resolveTable("TEST13");
        assertNotNull(table);
        assertTrue(table.isTransient());
        assertEquals("utf8", table.getColumn("name").getCollation());

        logger.info("TRANSIENT table with COLLATE works correctly");
    }

    @Test
    public void testTransientTableAllFeaturesCombined() {
        logger.info("Testing CREATE TRANSIENT TABLE with all features combined");

        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("""
            CREATE TRANSIENT TABLE IF NOT EXISTS test_schema.test14
            COMMENT = 'Full featured transient table'
            (
                id INTEGER PRIMARY KEY,
                name VARCHAR(200) NOT NULL UNIQUE COLLATE 'utf8',
                status VARCHAR DEFAULT 'active',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                date DATE
            )
            CLUSTER BY (date)
            COMMENT = 'End comment'
            """);

        Table table = engine.getCatalog().resolveTable("TEST_SCHEMA.TEST14");
        assertNotNull(table);
        assertTrue(table.isTransient());
        assertEquals("Full featured transient table", table.getComment());
        assertEquals(1, table.getClusterKeys().size());
        assertEquals("date", table.getClusterKeys().get(0));

        logger.info("TRANSIENT table with all features works correctly");
    }

    @Test
    public void testMultipleTransientTables() {
        logger.info("Testing multiple TRANSIENT tables");

        engine.execute("CREATE TRANSIENT TABLE transient1 (id INTEGER)");
        engine.execute("CREATE TABLE regular1 (id INTEGER)");
        engine.execute("CREATE TRANSIENT TABLE transient2 (id INTEGER)");
        engine.execute("CREATE TABLE regular2 (id INTEGER)");

        assertTrue(engine.getCatalog().resolveTable("TRANSIENT1").isTransient());
        assertFalse(engine.getCatalog().resolveTable("REGULAR1").isTransient());
        assertTrue(engine.getCatalog().resolveTable("TRANSIENT2").isTransient());
        assertFalse(engine.getCatalog().resolveTable("REGULAR2").isTransient());

        logger.info("Multiple TRANSIENT tables work correctly");
    }

    @Test
    public void testTransientTableWithForeignKey() {
        logger.info("Testing CREATE TRANSIENT TABLE with FOREIGN KEY");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TRANSIENT TABLE test15 (
                id INTEGER PRIMARY KEY,
                parent_id INTEGER REFERENCES parent(id)
            )
            """);

        Table table = engine.getCatalog().resolveTable("TEST15");
        assertNotNull(table);
        assertTrue(table.isTransient());
        assertTrue(table.getColumn("parent_id").hasForeignKey());

        logger.info("TRANSIENT table with FOREIGN KEY works correctly");
    }
}
