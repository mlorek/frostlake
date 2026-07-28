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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for COMMENT clause position in CREATE TABLE statement
 * COMMENT can appear immediately after table name or at the end
 */
public class CreateTableCommentPositionTest {
    private static final Logger logger = LoggerFactory.getLogger(CreateTableCommentPositionTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for CREATE TABLE COMMENT position tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCommentAfterTableName() {
        logger.info("Testing COMMENT immediately after table name");

        engine.execute("CREATE TABLE test1 COMMENT = 'Table comment' (id INTEGER, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("test1");
        assertNotNull(table);
        assertEquals("Table comment", table.getComment());

        logger.info("COMMENT after table name works correctly");
    }

    @Test
    public void testCommentAtEnd() {
        logger.info("Testing COMMENT at end of CREATE TABLE (traditional position)");

        engine.execute("CREATE TABLE test2 (id INTEGER, name VARCHAR) COMMENT = 'End comment'");

        Table table = engine.getCatalog().resolveTable("test2");
        assertNotNull(table);
        assertEquals("End comment", table.getComment());

        logger.info("COMMENT at end works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithClusterBy() {
        logger.info("Testing COMMENT after table name with CLUSTER BY");

        engine.execute("CREATE TABLE test3 COMMENT = 'Clustered table' (id INTEGER, date DATE) CLUSTER BY (date)");

        Table table = engine.getCatalog().resolveTable("test3");
        assertNotNull(table);
        assertEquals("Clustered table", table.getComment());
        assertEquals(1, table.getClusterKeys().size());
        assertEquals("date", table.getClusterKeys().get(0));

        logger.info("COMMENT after table name with CLUSTER BY works correctly");
    }

    @Test
    public void testCommentAtEndWithClusterBy() {
        logger.info("Testing COMMENT at end with CLUSTER BY");

        engine.execute("CREATE TABLE test4 (id INTEGER, date DATE) CLUSTER BY (date) COMMENT = 'Clustered at end'");

        Table table = engine.getCatalog().resolveTable("test4");
        assertNotNull(table);
        assertEquals("Clustered at end", table.getComment());
        assertEquals(1, table.getClusterKeys().size());

        logger.info("COMMENT at end with CLUSTER BY works correctly");
    }

    @Test
    public void testCommentAfterTableNameBeforeClusterBy() {
        logger.info("Testing COMMENT after table name, before CLUSTER BY");

        engine.execute("CREATE TABLE test5 COMMENT = 'Comment first' (id INTEGER, date DATE) CLUSTER BY (date)");

        Table table = engine.getCatalog().resolveTable("test5");
        assertNotNull(table);
        assertEquals("Comment first", table.getComment());

        logger.info("COMMENT before CLUSTER BY works correctly");
    }

    @Test
    public void testBothCommentPositionsPrioritizeFirst() {
        logger.info("Testing both COMMENT positions - should prioritize first one");

        engine.execute("CREATE TABLE test6 COMMENT = 'First position' (id INTEGER) COMMENT = 'Second position'");

        Table table = engine.getCatalog().resolveTable("test6");
        assertNotNull(table);
        assertEquals("First position", table.getComment());

        logger.info("First COMMENT position takes precedence");
    }

    @Test
    public void testCommentAfterTableNameWithIfNotExists() {
        logger.info("Testing COMMENT after table name with IF NOT EXISTS");

        engine.execute("CREATE TABLE IF NOT EXISTS test7 COMMENT = 'With IF NOT EXISTS' (id INTEGER, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("test7");
        assertNotNull(table);
        assertEquals("With IF NOT EXISTS", table.getComment());

        logger.info("COMMENT with IF NOT EXISTS works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithQualifiedName() {
        logger.info("Testing COMMENT after qualified table name");

        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("CREATE TABLE test_schema.test8 COMMENT = 'Qualified name' (id INTEGER)");

        Table table = engine.getCatalog().resolveTable("test_schema.test8");
        assertNotNull(table);
        assertEquals("Qualified name", table.getComment());

        logger.info("COMMENT with qualified name works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithMultipleColumns() {
        logger.info("Testing COMMENT after table name with multiple columns");

        engine.execute("""
            CREATE TABLE test9 COMMENT = 'Multi column table'
            (
                id INTEGER,
                name VARCHAR,
                email VARCHAR,
                created_at TIMESTAMP
            )
            """);

        Table table = engine.getCatalog().resolveTable("test9");
        assertNotNull(table);
        assertEquals("Multi column table", table.getComment());
        assertEquals(4, table.getColumns().size());

        logger.info("COMMENT with multiple columns works correctly");
    }

    @Test
    public void testNoCommentSpecified() {
        logger.info("Testing CREATE TABLE without COMMENT");

        engine.execute("CREATE TABLE test10 (id INTEGER, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("test10");
        assertNotNull(table);
        assertNull(table.getComment());

        logger.info("CREATE TABLE without COMMENT works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithClone() {
        logger.info("Testing COMMENT after table name with CLONE");

        engine.execute("CREATE TABLE source (id INTEGER, name VARCHAR) COMMENT = 'Source table'");
        engine.execute("CREATE TABLE test11 COMMENT = 'Cloned with comment' CLONE source");

        Table sourceTable = engine.getCatalog().resolveTable("source");
        Table clonedTable = engine.getCatalog().resolveTable("test11");

        assertNotNull(sourceTable);
        assertNotNull(clonedTable);
        assertEquals("Source table", sourceTable.getComment());
        assertEquals("Cloned with comment", clonedTable.getComment());

        logger.info("COMMENT with CLONE overrides source comment");
    }

    @Test
    public void testCloneWithoutCommentInheritsSourceComment() {
        logger.info("Testing CLONE without COMMENT inherits source comment");

        engine.execute("CREATE TABLE source2 (id INTEGER) COMMENT = 'Original comment'");
        engine.execute("CREATE TABLE test12 CLONE source2");

        Table clonedTable = engine.getCatalog().resolveTable("test12");
        assertNotNull(clonedTable);
        assertEquals("Original comment", clonedTable.getComment());

        logger.info("CLONE without COMMENT inherits correctly");
    }

    @Test
    public void testCommentAtEndWithClone() {
        logger.info("Testing COMMENT at end with CLONE");

        engine.execute("CREATE TABLE source3 (id INTEGER) COMMENT = 'Source'");
        engine.execute("CREATE TABLE test13 CLONE source3 COMMENT = 'New comment at end'");

        Table clonedTable = engine.getCatalog().resolveTable("test13");
        assertNotNull(clonedTable);
        assertEquals("New comment at end", clonedTable.getComment());

        logger.info("COMMENT at end with CLONE works correctly");
    }

    @Test
    public void testCommentWithSpecialCharacters() {
        logger.info("Testing COMMENT with special characters");

        engine.execute("CREATE TABLE test14 COMMENT = 'Comment with \"quotes\" and \\'apostrophes\\'' (id INTEGER)");

        Table table = engine.getCatalog().resolveTable("test14");
        assertNotNull(table);
        assertNotNull(table.getComment());

        logger.info("COMMENT with special characters works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithColumnConstraints() {
        logger.info("Testing COMMENT after table name with column constraints");

        engine.execute("""
            CREATE TABLE test15 COMMENT = 'Table with constraints'
            (
                id INTEGER PRIMARY KEY,
                email VARCHAR UNIQUE,
                name VARCHAR NOT NULL
            )
            """);

        Table table = engine.getCatalog().resolveTable("test15");
        assertNotNull(table);
        assertEquals("Table with constraints", table.getComment());

        logger.info("COMMENT with column constraints works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithDefaultValues() {
        logger.info("Testing COMMENT after table name with DEFAULT values");

        engine.execute("""
            CREATE TABLE test16 COMMENT = 'Table with defaults'
            (
                id INTEGER DEFAULT 0,
                status VARCHAR DEFAULT 'active',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);

        Table table = engine.getCatalog().resolveTable("test16");
        assertNotNull(table);
        assertEquals("Table with defaults", table.getComment());

        logger.info("COMMENT with DEFAULT values works correctly");
    }

    @Test
    public void testClusterByThenCommentBeforeColumns() {
        logger.info("Testing CLUSTER BY then COMMENT, both before the column list");

        // Snowflake accepts the table-level property clauses in either order before the column list;
        // this CLUSTER-BY-then-COMMENT order is emitted by some production DDL.
        engine.execute("CREATE TABLE test17 CLUSTER BY (date) COMMENT = 'Cluster then comment' (id INTEGER, date DATE)");

        Table table = engine.getCatalog().resolveTable("test17");
        assertNotNull(table);
        assertEquals("Cluster then comment", table.getComment());
        assertEquals(1, table.getClusterKeys().size());
        assertEquals("date", table.getClusterKeys().get(0));

        logger.info("CLUSTER BY then COMMENT before columns works correctly");
    }

    @Test
    public void testClusterByThenJsonCommentBeforeColumnsMultiKey() {
        logger.info("Testing multi-key CLUSTER BY then JSON COMMENT before columns");

        engine.execute("""
            CREATE TABLE IF NOT EXISTS test18
                CLUSTER BY (EFFECTIVE_DATE, GROUP_TYPE, RECORD_ID)
                COMMENT = '{"ver": "1.00.00", "doc": "derived KPI metric benchmarks."}'
            (
                METRIC_NAME    VARCHAR NOT NULL,
                EFFECTIVE_DATE DATE    NOT NULL,
                GROUP_TYPE     VARCHAR NOT NULL,
                RECORD_ID      VARCHAR NOT NULL
            )
            """);

        Table table = engine.getCatalog().resolveTable("test18");
        assertNotNull(table);
        assertEquals("{\"ver\": \"1.00.00\", \"doc\": \"derived KPI metric benchmarks.\"}", table.getComment());
        assertEquals(3, table.getClusterKeys().size());
        assertEquals(4, table.getColumns().size());

        logger.info("Multi-key CLUSTER BY then JSON COMMENT before columns works correctly");
    }
}
