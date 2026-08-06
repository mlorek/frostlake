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

/**
 * Tests for different ALTER COLUMN syntax variations
 * Format: ALTER COLUMN col [ [ SET DATA ] TYPE ] datatype
 */
public class AlterColumnSyntaxTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterColumnSyntaxTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for ALTER COLUMN syntax tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testAlterColumnWithSetDataType() {
        logger.info("Testing ALTER COLUMN with SET DATA TYPE");

        engine.execute("CREATE TABLE test1 (id INTEGER, col1 VARCHAR(10))");
        engine.execute("ALTER TABLE test1 ALTER COLUMN col1 SET DATA TYPE VARCHAR");

        Table table = engine.getCatalog().resolveTable("TEST1");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("VARCHAR", column.getDataType().getName());

        logger.info("ALTER COLUMN with SET DATA TYPE works correctly");
    }

    @Test
    public void testAlterColumnWithTypeOnly() {
        logger.info("Testing ALTER COLUMN with TYPE keyword");

        engine.execute("CREATE TABLE test2 (id INTEGER, col1 VARCHAR(10))");
        engine.execute("ALTER TABLE test2 ALTER COLUMN col1 TYPE VARCHAR");

        Table table = engine.getCatalog().resolveTable("TEST2");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("VARCHAR", column.getDataType().getName());

        logger.info("ALTER COLUMN with TYPE works correctly");
    }

    @Test
    public void testAlterColumnWithoutTypeKeyword() {
        logger.info("Testing ALTER COLUMN without TYPE keyword (minimal form)");

        engine.execute("CREATE TABLE test3 (id INTEGER, col1 VARCHAR(10))");
        engine.execute("ALTER TABLE test3 ALTER COLUMN col1 VARCHAR");

        Table table = engine.getCatalog().resolveTable("TEST3");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("VARCHAR", column.getDataType().getName());

        logger.info("ALTER COLUMN without TYPE keyword works correctly");
    }

    @Test
    public void testAlterColumnWithSetDataTypeAndParameters() {
        logger.info("Testing ALTER COLUMN with SET DATA TYPE and type parameters");

        engine.execute("CREATE TABLE test4 (id INTEGER, col1 VARCHAR(10))");
        engine.execute("ALTER TABLE test4 ALTER COLUMN col1 SET DATA TYPE VARCHAR(100)");

        Table table = engine.getCatalog().resolveTable("TEST4");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("VARCHAR", column.getDataType().getName());

        logger.info("ALTER COLUMN with SET DATA TYPE and parameters works correctly");
    }

    @Test
    public void testAlterColumnWithTypeAndParameters() {
        logger.info("Testing ALTER COLUMN with TYPE and type parameters");

        engine.execute("CREATE TABLE test5 (id INTEGER, col1 INTEGER)");
        engine.execute("ALTER TABLE test5 ALTER COLUMN col1 TYPE DECIMAL(10, 2)");

        Table table = engine.getCatalog().resolveTable("TEST5");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("NUMBER", column.getDataType().getName());

        logger.info("ALTER COLUMN with TYPE and parameters works correctly");
    }

    @Test
    public void testAlterColumnWithoutTypeKeywordAndParameters() {
        logger.info("Testing ALTER COLUMN without TYPE keyword and type parameters");

        engine.execute("CREATE TABLE test6 (id INTEGER, col1 VARCHAR(100))");
        engine.execute("ALTER TABLE test6 ALTER COLUMN col1 VARCHAR(500)");

        Table table = engine.getCatalog().resolveTable("TEST6");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("VARCHAR", column.getDataType().getName());

        logger.info("ALTER COLUMN without TYPE keyword and parameters works correctly");
    }

    @Test
    public void testMultipleColumnsWithDifferentSyntax() {
        logger.info("Testing multiple columns altered with different syntax variations");

        engine.execute("CREATE TABLE test7 (id INTEGER, col1 VARCHAR(10), col2 DATE, col3 TIMESTAMP)");

        engine.execute("ALTER TABLE test7 ALTER COLUMN col1 SET DATA TYPE VARCHAR");
        engine.execute("ALTER TABLE test7 ALTER COLUMN col2 TYPE DATE");
        engine.execute("ALTER TABLE test7 ALTER COLUMN col3 TIMESTAMP");

        Table table = engine.getCatalog().resolveTable("TEST7");
        assertNotNull(table);

        assertEquals("VARCHAR", table.getColumn("col1").getDataType().getName());
        assertEquals("DATE", table.getColumn("col2").getDataType().getName());
        assertEquals("TIMESTAMP", table.getColumn("col3").getDataType().getName());

        logger.info("Multiple columns with different syntax variations work correctly");
    }

    @Test
    public void testAlterColumnWithIfExists() {
        logger.info("Testing ALTER COLUMN with IF EXISTS");

        engine.execute("CREATE TABLE test8 (id INTEGER, col1 VARCHAR(10))");
        engine.execute("ALTER TABLE IF EXISTS test8 ALTER COLUMN col1 TYPE VARCHAR");

        Table table = engine.getCatalog().resolveTable("TEST8");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("VARCHAR", column.getDataType().getName());

        logger.info("ALTER COLUMN with IF EXISTS works correctly");
    }

    @Test
    public void testAlterColumnWithQualifiedTableName() {
        logger.info("Testing ALTER COLUMN with schema-qualified table name");

        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("CREATE TABLE test_schema.test9 (id INTEGER, col1 VARCHAR(10))");

        engine.execute("ALTER TABLE test_schema.test9 ALTER COLUMN col1 TYPE VARCHAR");

        Table table = engine.getCatalog().resolveTable("TEST_SCHEMA.TEST9");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("VARCHAR", column.getDataType().getName());

        logger.info("ALTER COLUMN with qualified name works correctly");
    }

    @Test
    public void testAlterColumnWithComplexDataType() {
        logger.info("Testing ALTER COLUMN with complex data type");

        engine.execute("CREATE TABLE test10 (id INTEGER, col1 TIMESTAMP)");
        engine.execute("ALTER TABLE test10 ALTER COLUMN col1 TYPE TIMESTAMP_LTZ");

        Table table = engine.getCatalog().resolveTable("TEST10");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("TIMESTAMP_LTZ", column.getDataType().getName());

        logger.info("ALTER COLUMN with complex data type works correctly");
    }

    @Test
    public void testAllSyntaxVariationsProduceSameResult() {
        logger.info("Testing that all syntax variations produce the same result");

        engine.execute("CREATE TABLE test11 (id INTEGER, col1 VARCHAR(10), col2 VARCHAR(10), col3 VARCHAR(10))");

        engine.execute("ALTER TABLE test11 ALTER COLUMN col1 SET DATA TYPE VARCHAR");
        engine.execute("ALTER TABLE test11 ALTER COLUMN col2 TYPE VARCHAR");
        engine.execute("ALTER TABLE test11 ALTER COLUMN col3 VARCHAR");

        Table table = engine.getCatalog().resolveTable("TEST11");
        assertNotNull(table);

        assertEquals("VARCHAR", table.getColumn("col1").getDataType().getName());
        assertEquals("VARCHAR", table.getColumn("col2").getDataType().getName());
        assertEquals("VARCHAR", table.getColumn("col3").getDataType().getName());

        logger.info("All syntax variations produce the same result");
    }

    @Test
    public void testAlterColumnWithoutTypeKeywordWithDecimal() {
        logger.info("Testing ALTER COLUMN without TYPE keyword with DECIMAL");

        engine.execute("CREATE TABLE test12 (id INTEGER, col1 INTEGER)");
        engine.execute("ALTER TABLE test12 ALTER COLUMN col1 DECIMAL(15, 3)");

        Table table = engine.getCatalog().resolveTable("TEST12");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("NUMBER", column.getDataType().getName());

        logger.info("ALTER COLUMN without TYPE keyword with DECIMAL works correctly");
    }

    @Test
    public void testAlterColumnSetDataTypeWithDecimal() {
        logger.info("Testing ALTER COLUMN SET DATA TYPE with DECIMAL");

        engine.execute("CREATE TABLE test13 (id INTEGER, col1 INTEGER)");
        engine.execute("ALTER TABLE test13 ALTER COLUMN col1 SET DATA TYPE DECIMAL(20, 5)");

        Table table = engine.getCatalog().resolveTable("TEST13");
        assertNotNull(table);

        TableColumn column = table.getColumn("col1");
        assertEquals("NUMBER", column.getDataType().getName());

        logger.info("ALTER COLUMN SET DATA TYPE with DECIMAL works correctly");
    }
}
