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
import dev.frostlake.metastore.model.TableColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for COLLATE clause in column definitions
 * Format: column_name datatype COLLATE 'collation_name'
 */
public class ColumnCollationTest {
    private static final Logger logger = LoggerFactory.getLogger(ColumnCollationTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for COLLATE tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testSingleColumnWithCollation() {
        logger.info("Testing single column with COLLATE");

        engine.execute("CREATE TABLE test1 (id INTEGER, name VARCHAR COLLATE 'utf8')");

        Table table = engine.getCatalog().resolveTable("test1");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertNotNull(nameColumn);
        assertEquals("utf8", nameColumn.getCollation());

        logger.info("Single column with COLLATE works correctly");
    }

    @Test
    public void testMultipleColumnsWithCollation() {
        logger.info("Testing multiple columns with COLLATE");

        engine.execute("""
            CREATE TABLE test2 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8',
                description VARCHAR COLLATE 'utf8_unicode_ci',
                code VARCHAR
            )
            """);

        Table table = engine.getCatalog().resolveTable("test2");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());

        TableColumn descColumn = table.getColumn("description");
        assertEquals("utf8_unicode_ci", descColumn.getCollation());

        TableColumn codeColumn = table.getColumn("code");
        assertNull(codeColumn.getCollation());

        logger.info("Multiple columns with COLLATE work correctly");
    }

    @Test
    public void testCollationWithTypeParameters() {
        logger.info("Testing COLLATE with type parameters");

        engine.execute("CREATE TABLE test3 (id INTEGER, name VARCHAR(100) COLLATE 'utf8_bin')");

        Table table = engine.getCatalog().resolveTable("test3");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8_bin", nameColumn.getCollation());

        logger.info("COLLATE with type parameters works correctly");
    }

    @Test
    public void testCollationWithConstraints() {
        logger.info("Testing COLLATE with column constraints");

        engine.execute("""
            CREATE TABLE test4 (
                id INTEGER PRIMARY KEY,
                name VARCHAR NOT NULL COLLATE 'utf8',
                email VARCHAR UNIQUE COLLATE 'utf8_general_ci'
            )
            """);

        Table table = engine.getCatalog().resolveTable("test4");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertEquals(false, nameColumn.isNullable());

        TableColumn emailColumn = table.getColumn("email");
        assertEquals("utf8_general_ci", emailColumn.getCollation());
        assertEquals(true, emailColumn.isUnique());

        logger.info("COLLATE with constraints works correctly");
    }

    @Test
    public void testCollationWithDefault() {
        logger.info("Testing COLLATE with DEFAULT constraint");

        engine.execute("""
            CREATE TABLE test5 (
                id INTEGER,
                status VARCHAR DEFAULT 'active' COLLATE 'utf8'
            )
            """);

        Table table = engine.getCatalog().resolveTable("test5");
        assertNotNull(table);

        TableColumn statusColumn = table.getColumn("status");
        assertEquals("utf8", statusColumn.getCollation());
        assertNotNull(statusColumn.getDefaultValue());

        logger.info("COLLATE with DEFAULT works correctly");
    }

    @Test
    public void testCollationWithComment() {
        logger.info("Testing COLLATE with COMMENT");

        engine.execute("""
            CREATE TABLE test6 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8' COMMENT = 'User name'
            )
            """);

        Table table = engine.getCatalog().resolveTable("test6");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertEquals("User name", nameColumn.getComment());

        logger.info("COLLATE with COMMENT works correctly");
    }

    @Test
    public void testCollationCaseInsensitive() {
        logger.info("Testing COLLATE case-insensitive names");

        engine.execute("CREATE TABLE test7 (id INTEGER, name VARCHAR COLLATE 'UTF8_UNICODE_CI')");

        Table table = engine.getCatalog().resolveTable("test7");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("UTF8_UNICODE_CI", nameColumn.getCollation());

        logger.info("COLLATE case-insensitive names work correctly");
    }

    @Test
    public void testCollationWithQualifiedTableName() {
        logger.info("Testing COLLATE with qualified table name");

        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("""
            CREATE TABLE test_schema.test8 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8'
            )
            """);

        Table table = engine.getCatalog().resolveTable("test_schema.test8");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());

        logger.info("COLLATE with qualified table name works correctly");
    }

    @Test
    public void testCollationCloneTable() {
        logger.info("Testing COLLATE preserved during CLONE");

        engine.execute("""
            CREATE TABLE source (
                id INTEGER,
                name VARCHAR COLLATE 'utf8',
                description VARCHAR COLLATE 'utf8_bin'
            )
            """);
        engine.execute("CREATE TABLE test9 CLONE source");

        Table sourceTable = engine.getCatalog().resolveTable("source");
        Table clonedTable = engine.getCatalog().resolveTable("test9");

        assertNotNull(sourceTable);
        assertNotNull(clonedTable);

        assertEquals(sourceTable.getColumn("name").getCollation(),
                     clonedTable.getColumn("name").getCollation());
        assertEquals(sourceTable.getColumn("description").getCollation(),
                     clonedTable.getColumn("description").getCollation());

        logger.info("COLLATE preserved during CLONE correctly");
    }

    @Test
    public void testCollationWithIfNotExists() {
        logger.info("Testing COLLATE with IF NOT EXISTS");

        engine.execute("""
            CREATE TABLE IF NOT EXISTS test10 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8'
            )
            """);

        Table table = engine.getCatalog().resolveTable("test10");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());

        logger.info("COLLATE with IF NOT EXISTS works correctly");
    }

    @Test
    public void testCollationWithAllConstraints() {
        logger.info("Testing COLLATE with all constraint types");

        engine.execute("""
            CREATE TABLE test11 (
                id INTEGER PRIMARY KEY,
                name VARCHAR(200) NOT NULL UNIQUE DEFAULT 'unknown' COLLATE 'utf8' COMMENT = 'User name'
            )
            """);

        Table table = engine.getCatalog().resolveTable("test11");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertEquals(false, nameColumn.isNullable());
        assertEquals(true, nameColumn.isUnique());
        assertNotNull(nameColumn.getDefaultValue());
        assertEquals("User name", nameColumn.getComment());

        logger.info("COLLATE with all constraints works correctly");
    }

    @Test
    public void testNoCollationSpecified() {
        logger.info("Testing column without COLLATE");

        engine.execute("CREATE TABLE test12 (id INTEGER, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("test12");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertNull(nameColumn.getCollation());

        logger.info("Column without COLLATE has null collation");
    }

    @Test
    public void testCollationWithMultipleTypes() {
        logger.info("Testing COLLATE with different data types");

        engine.execute("""
            CREATE TABLE test13 (
                id INTEGER,
                varchar_col VARCHAR COLLATE 'utf8',
                text_col VARCHAR(1000) COLLATE 'utf8_bin',
                number_col NUMBER
            )
            """);

        Table table = engine.getCatalog().resolveTable("test13");
        assertNotNull(table);

        assertEquals("utf8", table.getColumn("varchar_col").getCollation());
        assertEquals("utf8_bin", table.getColumn("text_col").getCollation());
        assertNull(table.getColumn("number_col").getCollation());

        logger.info("COLLATE with different data types works correctly");
    }

    @Test
    public void testCollationPreservedDuringAlterColumn() {
        logger.info("Testing COLLATE preserved during ALTER COLUMN");

        engine.execute("""
            CREATE TABLE test14 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8'
            )
            """);

        Table table = engine.getCatalog().resolveTable("test14");
        assertEquals("utf8", table.getColumn("name").getCollation());

        engine.execute("ALTER TABLE test14 ALTER COLUMN name VARCHAR(200)");

        table = engine.getCatalog().resolveTable("test14");
        assertEquals("utf8", table.getColumn("name").getCollation());

        logger.info("COLLATE preserved during ALTER COLUMN correctly");
    }

    @Test
    public void testCollationWithClusterBy() {
        logger.info("Testing COLLATE with CLUSTER BY");

        engine.execute("""
            CREATE TABLE test15 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8',
                date DATE
            ) CLUSTER BY (date)
            """);

        Table table = engine.getCatalog().resolveTable("test15");
        assertNotNull(table);

        assertEquals("utf8", table.getColumn("name").getCollation());
        assertEquals(1, table.getClusterKeys().size());

        logger.info("COLLATE with CLUSTER BY works correctly");
    }

    @Test
    public void testCollationWithTableComment() {
        logger.info("Testing COLLATE with table COMMENT");

        engine.execute("""
            CREATE TABLE test16 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8'
            ) COMMENT = 'Test table'
            """);

        Table table = engine.getCatalog().resolveTable("test16");
        assertNotNull(table);

        assertEquals("utf8", table.getColumn("name").getCollation());
        assertEquals("Test table", table.getComment());

        logger.info("COLLATE with table COMMENT works correctly");
    }
}
