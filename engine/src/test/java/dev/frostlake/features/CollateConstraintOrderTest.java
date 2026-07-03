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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for COLLATE clause position flexibility among column constraints
 * COLLATE can be placed before, after, or between other constraints
 */
public class CollateConstraintOrderTest {
    private static final Logger logger = LoggerFactory.getLogger(CollateConstraintOrderTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for COLLATE constraint order tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCollateBeforeNotNull() {
        logger.info("Testing COLLATE before NOT NULL");

        engine.execute("CREATE TABLE test1 (id INTEGER, name VARCHAR COLLATE 'utf8' NOT NULL)");

        Table table = engine.getCatalog().resolveTable("test1");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertFalse(nameColumn.isNullable());

        logger.info("COLLATE before NOT NULL works correctly");
    }

    @Test
    public void testCollateAfterNotNull() {
        logger.info("Testing COLLATE after NOT NULL");

        engine.execute("CREATE TABLE test2 (id INTEGER, name VARCHAR NOT NULL COLLATE 'utf8')");

        Table table = engine.getCatalog().resolveTable("test2");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertFalse(nameColumn.isNullable());

        logger.info("COLLATE after NOT NULL works correctly");
    }

    @Test
    public void testCollateBeforeUnique() {
        logger.info("Testing COLLATE before UNIQUE");

        engine.execute("CREATE TABLE test3 (id INTEGER, email VARCHAR COLLATE 'utf8_bin' UNIQUE)");

        Table table = engine.getCatalog().resolveTable("test3");
        assertNotNull(table);

        TableColumn emailColumn = table.getColumn("email");
        assertEquals("utf8_bin", emailColumn.getCollation());
        assertTrue(emailColumn.isUnique());

        logger.info("COLLATE before UNIQUE works correctly");
    }

    @Test
    public void testCollateAfterUnique() {
        logger.info("Testing COLLATE after UNIQUE");

        engine.execute("CREATE TABLE test4 (id INTEGER, email VARCHAR UNIQUE COLLATE 'utf8_bin')");

        Table table = engine.getCatalog().resolveTable("test4");
        assertNotNull(table);

        TableColumn emailColumn = table.getColumn("email");
        assertEquals("utf8_bin", emailColumn.getCollation());
        assertTrue(emailColumn.isUnique());

        logger.info("COLLATE after UNIQUE works correctly");
    }

    @Test
    public void testCollateBeforeDefault() {
        logger.info("Testing COLLATE before DEFAULT");

        engine.execute("CREATE TABLE test5 (id INTEGER, status VARCHAR COLLATE 'utf8' DEFAULT 'active')");

        Table table = engine.getCatalog().resolveTable("test5");
        assertNotNull(table);

        TableColumn statusColumn = table.getColumn("status");
        assertEquals("utf8", statusColumn.getCollation());
        assertNotNull(statusColumn.getDefaultValue());

        logger.info("COLLATE before DEFAULT works correctly");
    }

    @Test
    public void testCollateAfterDefault() {
        logger.info("Testing COLLATE after DEFAULT");

        engine.execute("CREATE TABLE test6 (id INTEGER, status VARCHAR DEFAULT 'active' COLLATE 'utf8')");

        Table table = engine.getCatalog().resolveTable("test6");
        assertNotNull(table);

        TableColumn statusColumn = table.getColumn("status");
        assertEquals("utf8", statusColumn.getCollation());
        assertNotNull(statusColumn.getDefaultValue());

        logger.info("COLLATE after DEFAULT works correctly");
    }

    @Test
    public void testCollateBetweenConstraints() {
        logger.info("Testing COLLATE between NOT NULL and UNIQUE");

        engine.execute("CREATE TABLE test7 (id INTEGER, name VARCHAR NOT NULL COLLATE 'utf8' UNIQUE)");

        Table table = engine.getCatalog().resolveTable("test7");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertFalse(nameColumn.isNullable());
        assertTrue(nameColumn.isUnique());

        logger.info("COLLATE between constraints works correctly");
    }

    @Test
    public void testCollateFirstAmongMultiple() {
        logger.info("Testing COLLATE first among multiple constraints");

        engine.execute("CREATE TABLE test8 (id INTEGER, name VARCHAR COLLATE 'utf8' NOT NULL UNIQUE DEFAULT 'unknown')");

        Table table = engine.getCatalog().resolveTable("test8");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertFalse(nameColumn.isNullable());
        assertTrue(nameColumn.isUnique());
        assertNotNull(nameColumn.getDefaultValue());

        logger.info("COLLATE first among multiple constraints works correctly");
    }

    @Test
    public void testCollateLastAmongMultiple() {
        logger.info("Testing COLLATE last among multiple constraints");

        engine.execute("CREATE TABLE test9 (id INTEGER, name VARCHAR NOT NULL UNIQUE DEFAULT 'unknown' COLLATE 'utf8')");

        Table table = engine.getCatalog().resolveTable("test9");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertFalse(nameColumn.isNullable());
        assertTrue(nameColumn.isUnique());
        assertNotNull(nameColumn.getDefaultValue());

        logger.info("COLLATE last among multiple constraints works correctly");
    }

    @Test
    public void testCollateMiddleAmongMultiple() {
        logger.info("Testing COLLATE in middle among multiple constraints");

        engine.execute("CREATE TABLE test10 (id INTEGER, name VARCHAR NOT NULL COLLATE 'utf8' DEFAULT 'test' UNIQUE)");

        Table table = engine.getCatalog().resolveTable("test10");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertFalse(nameColumn.isNullable());
        assertTrue(nameColumn.isUnique());
        assertNotNull(nameColumn.getDefaultValue());

        logger.info("COLLATE in middle among multiple constraints works correctly");
    }

    @Test
    public void testCollateWithPrimaryKey() {
        logger.info("Testing COLLATE with PRIMARY KEY");

        engine.execute("CREATE TABLE test11 (id INTEGER, name VARCHAR PRIMARY KEY COLLATE 'utf8')");

        Table table = engine.getCatalog().resolveTable("test11");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertTrue(nameColumn.isPrimaryKey());

        logger.info("COLLATE with PRIMARY KEY works correctly");
    }

    @Test
    public void testCollateBeforePrimaryKey() {
        logger.info("Testing COLLATE before PRIMARY KEY");

        engine.execute("CREATE TABLE test12 (id INTEGER, name VARCHAR COLLATE 'utf8' PRIMARY KEY)");

        Table table = engine.getCatalog().resolveTable("test12");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertTrue(nameColumn.isPrimaryKey());

        logger.info("COLLATE before PRIMARY KEY works correctly");
    }

    @Test
    public void testMultipleColumnsWithCollateInDifferentPositions() {
        logger.info("Testing multiple columns with COLLATE in different positions");

        engine.execute("""
            CREATE TABLE test13 (
                id INTEGER,
                col1 VARCHAR COLLATE 'utf8' NOT NULL,
                col2 VARCHAR NOT NULL COLLATE 'utf8_bin',
                col3 VARCHAR UNIQUE COLLATE 'utf8_unicode_ci',
                col4 VARCHAR DEFAULT 'test' COLLATE 'utf8'
            )
            """);

        Table table = engine.getCatalog().resolveTable("test13");
        assertNotNull(table);

        assertEquals("utf8", table.getColumn("col1").getCollation());
        assertEquals("utf8_bin", table.getColumn("col2").getCollation());
        assertEquals("utf8_unicode_ci", table.getColumn("col3").getCollation());
        assertEquals("utf8", table.getColumn("col4").getCollation());

        logger.info("Multiple columns with COLLATE in different positions work correctly");
    }

    @Test
    public void testCollateWithComment() {
        logger.info("Testing COLLATE before COMMENT");

        engine.execute("CREATE TABLE test14 (id INTEGER, name VARCHAR COLLATE 'utf8' COMMENT = 'User name')");

        Table table = engine.getCatalog().resolveTable("test14");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertEquals("User name", nameColumn.getComment());

        logger.info("COLLATE with COMMENT works correctly");
    }

    @Test
    public void testCollateAfterAllConstraintsBeforeComment() {
        logger.info("Testing COLLATE after all constraints before COMMENT");

        engine.execute("""
            CREATE TABLE test15 (
                id INTEGER,
                name VARCHAR NOT NULL UNIQUE DEFAULT 'test' COLLATE 'utf8' COMMENT = 'Test column'
            )
            """);

        Table table = engine.getCatalog().resolveTable("test15");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("name");
        assertEquals("utf8", nameColumn.getCollation());
        assertEquals("Test column", nameColumn.getComment());
        assertFalse(nameColumn.isNullable());
        assertTrue(nameColumn.isUnique());

        logger.info("COLLATE after all constraints before COMMENT works correctly");
    }

    @Test
    public void testCollateFlexibilityAllPositions() {
        logger.info("Testing COLLATE flexibility with all constraint positions");

        engine.execute("""
            CREATE TABLE test16 (
                col1 VARCHAR COLLATE 'utf8',
                col2 VARCHAR NOT NULL COLLATE 'utf8',
                col3 VARCHAR COLLATE 'utf8' NOT NULL,
                col4 VARCHAR UNIQUE COLLATE 'utf8',
                col5 VARCHAR COLLATE 'utf8' UNIQUE,
                col6 VARCHAR DEFAULT 'x' COLLATE 'utf8',
                col7 VARCHAR COLLATE 'utf8' DEFAULT 'x'
            )
            """);

        Table table = engine.getCatalog().resolveTable("test16");
        assertNotNull(table);

        for (int i = 1; i <= 7; i++) {
            String colName = "col" + i;
            assertEquals("utf8", table.getColumn(colName).getCollation(),
                "Column " + colName + " should have utf8 collation");
        }

        logger.info("COLLATE flexibility with all constraint positions works correctly");
    }
}
