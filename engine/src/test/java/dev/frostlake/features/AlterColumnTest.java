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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for ALTER COLUMN clause in ALTER TABLE statement
 */
public class AlterColumnTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterColumnTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for ALTER COLUMN tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testAlterColumnFromIntegerToVarchar() {
        logger.info("Testing ALTER COLUMN from INTEGER to VARCHAR");

        engine.execute("CREATE TABLE users (id INTEGER, age INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 25, 'Alice')");

        engine.execute("ALTER TABLE users ALTER COLUMN age SET DATA TYPE VARCHAR");

        Table table = engine.getCatalog().resolveTable("users");
        assertNotNull(table);

        TableColumn ageColumn = table.getColumn("age");
        assertEquals("VARCHAR", ageColumn.getDataType().getName());

        logger.info("Column type altered from INTEGER to VARCHAR successfully");
    }

    @Test
    public void testAlterColumnFromVarcharToInteger() {
        logger.info("Testing ALTER COLUMN from VARCHAR to INTEGER");

        engine.execute("CREATE TABLE products (id INTEGER, price VARCHAR, name VARCHAR)");

        engine.execute("ALTER TABLE products ALTER COLUMN price SET DATA TYPE INTEGER");

        Table table = engine.getCatalog().resolveTable("products");
        assertNotNull(table);

        TableColumn priceColumn = table.getColumn("price");
        assertEquals("INTEGER", priceColumn.getDataType().getName());

        logger.info("Column type altered from VARCHAR to INTEGER successfully");
    }

    @Test
    public void testAlterColumnWithTypeParameters() {
        logger.info("Testing ALTER COLUMN with type parameters (VARCHAR length)");

        engine.execute("CREATE TABLE documents (id INTEGER, content VARCHAR)");

        engine.execute("ALTER TABLE documents ALTER COLUMN content SET DATA TYPE VARCHAR(1000)");

        Table table = engine.getCatalog().resolveTable("documents");
        assertNotNull(table);

        TableColumn contentColumn = table.getColumn("content");
        assertEquals("VARCHAR", contentColumn.getDataType().getName());

        logger.info("Column type altered with type parameters successfully");
    }

    @Test
    public void testAlterColumnToDecimal() {
        logger.info("Testing ALTER COLUMN to DECIMAL with precision and scale");

        engine.execute("CREATE TABLE finances (id INTEGER, amount INTEGER)");

        engine.execute("ALTER TABLE finances ALTER COLUMN amount SET DATA TYPE DECIMAL(10, 2)");

        Table table = engine.getCatalog().resolveTable("finances");
        assertNotNull(table);

        TableColumn amountColumn = table.getColumn("amount");
        assertEquals("NUMBER", amountColumn.getDataType().getName());

        logger.info("Column type altered to DECIMAL successfully");
    }

    @Test
    public void testAlterColumnToTimestamp() {
        logger.info("Testing ALTER COLUMN to TIMESTAMP");

        engine.execute("CREATE TABLE events (id INTEGER, event_time VARCHAR)");

        engine.execute("ALTER TABLE events ALTER COLUMN event_time SET DATA TYPE TIMESTAMP");

        Table table = engine.getCatalog().resolveTable("events");
        assertNotNull(table);

        TableColumn eventTimeColumn = table.getColumn("event_time");
        assertEquals("TIMESTAMP", eventTimeColumn.getDataType().getName());

        logger.info("Column type altered to TIMESTAMP successfully");
    }

    @Test
    public void testAlterColumnToDate() {
        logger.info("Testing ALTER COLUMN to DATE");

        engine.execute("CREATE TABLE orders (id INTEGER, order_date VARCHAR)");

        engine.execute("ALTER TABLE orders ALTER COLUMN order_date SET DATA TYPE DATE");

        Table table = engine.getCatalog().resolveTable("orders");
        assertNotNull(table);

        TableColumn dateColumn = table.getColumn("order_date");
        assertEquals("DATE", dateColumn.getDataType().getName());

        logger.info("Column type altered to DATE successfully");
    }

    @Test
    public void testAlterColumnNonExistentColumn() {
        logger.info("Testing ALTER COLUMN on non-existent column");

        engine.execute("CREATE TABLE test_table (id INTEGER)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("ALTER TABLE test_table ALTER COLUMN non_existent SET DATA TYPE VARCHAR");
            }
        });

        logger.info("ALTER COLUMN on non-existent column correctly throws exception");
    }

    @Test
    public void testAlterColumnNonExistentTable() {
        logger.info("Testing ALTER COLUMN on non-existent table");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("ALTER TABLE non_existent ALTER COLUMN col SET DATA TYPE VARCHAR");
            }
        });

        logger.info("ALTER COLUMN on non-existent table correctly throws exception");
    }

    @Test
    public void testAlterColumnWithIfExists() {
        logger.info("Testing ALTER COLUMN with IF EXISTS on non-existent table");

        engine.execute("ALTER TABLE IF EXISTS non_existent ALTER COLUMN col SET DATA TYPE VARCHAR");

        logger.info("ALTER COLUMN with IF EXISTS succeeds without error");
    }

    @Test
    public void testAlterColumnPreservesComment() {
        logger.info("Testing that ALTER COLUMN preserves column comment");

        engine.execute("CREATE TABLE test_table (id INTEGER, value INTEGER COMMENT = 'Important value')");

        Table table = engine.getCatalog().resolveTable("test_table");
        TableColumn valueBefore = table.getColumn("value");
        String commentBefore = valueBefore.getComment();

        engine.execute("ALTER TABLE test_table ALTER COLUMN value SET DATA TYPE VARCHAR");

        table = engine.getCatalog().resolveTable("test_table");
        TableColumn valueAfter = table.getColumn("value");
        String commentAfter = valueAfter.getComment();

        assertEquals(commentBefore, commentAfter);
        assertEquals("Important value", commentAfter);

        logger.info("ALTER COLUMN preserved column comment");
    }

    @Test
    public void testAlterColumnPreservesPrimaryKey() {
        logger.info("Testing that ALTER COLUMN preserves primary key constraint");

        engine.execute("CREATE TABLE test_table (id INTEGER PRIMARY KEY, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("test_table");
        TableColumn idBefore = table.getColumn("id");
        boolean isPrimaryKeyBefore = idBefore.isPrimaryKey();

        engine.execute("ALTER TABLE test_table ALTER COLUMN id SET DATA TYPE VARCHAR");

        table = engine.getCatalog().resolveTable("test_table");
        TableColumn idAfter = table.getColumn("id");
        boolean isPrimaryKeyAfter = idAfter.isPrimaryKey();

        assertEquals(isPrimaryKeyBefore, isPrimaryKeyAfter);
        assertEquals(true, isPrimaryKeyAfter);

        logger.info("ALTER COLUMN preserved primary key constraint");
    }

    @Test
    public void testAlterMultipleColumns() {
        logger.info("Testing ALTER COLUMN on multiple columns");

        engine.execute("CREATE TABLE test_table (col1 INTEGER, col2 INTEGER, col3 VARCHAR)");

        engine.execute("ALTER TABLE test_table ALTER COLUMN col1 SET DATA TYPE VARCHAR");
        engine.execute("ALTER TABLE test_table ALTER COLUMN col2 SET DATA TYPE DECIMAL");
        engine.execute("ALTER TABLE test_table ALTER COLUMN col3 SET DATA TYPE INTEGER");

        Table table = engine.getCatalog().resolveTable("test_table");

        assertEquals("VARCHAR", table.getColumn("col1").getDataType().getName());
        assertEquals("NUMBER", table.getColumn("col2").getDataType().getName());
        assertEquals("INTEGER", table.getColumn("col3").getDataType().getName());

        logger.info("Multiple columns altered successfully");
    }

    @Test
    public void testAlterColumnWithQualifiedName() {
        logger.info("Testing ALTER COLUMN with schema-qualified table name");

        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("CREATE TABLE test_schema.products (id INTEGER, price INTEGER)");

        engine.execute("ALTER TABLE test_schema.products ALTER COLUMN price SET DATA TYPE DECIMAL");

        Table table = engine.getCatalog().resolveTable("test_schema.products");
        assertNotNull(table);

        TableColumn priceColumn = table.getColumn("price");
        assertEquals("NUMBER", priceColumn.getDataType().getName());

        logger.info("ALTER COLUMN with qualified name works correctly");
    }

    @Test
    public void testAlterColumnWithData() {
        logger.info("Testing ALTER COLUMN on table with existing data");

        engine.execute("CREATE TABLE customers (id INTEGER, age INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO customers VALUES (1, 25, 'Alice')");
        engine.execute("INSERT INTO customers VALUES (2, 30, 'Bob')");

        engine.execute("ALTER TABLE customers ALTER COLUMN age SET DATA TYPE VARCHAR");

        ResultSet rs = engine.executeQuery("SELECT * FROM customers");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        Table table = engine.getCatalog().resolveTable("customers");
        assertEquals("VARCHAR", table.getColumn("age").getDataType().getName());

        logger.info("ALTER COLUMN on table with data works correctly");
    }

    @Test
    public void testAlterColumnCombinedWithOtherAlterOperations() {
        logger.info("Testing ALTER COLUMN combined with other ALTER TABLE operations");

        engine.execute("CREATE TABLE test_table (id INTEGER, col1 INTEGER, col2 VARCHAR)");

        engine.execute("ALTER TABLE test_table ADD COLUMN col3 INTEGER");
        engine.execute("ALTER TABLE test_table ALTER COLUMN col1 SET DATA TYPE VARCHAR");
        engine.execute("ALTER TABLE test_table RENAME COLUMN col2 col2_renamed");
        engine.execute("ALTER TABLE test_table ALTER COLUMN col3 SET DATA TYPE DECIMAL");

        Table table = engine.getCatalog().resolveTable("test_table");

        assertEquals(4, table.getColumns().size());
        assertEquals("VARCHAR", table.getColumn("col1").getDataType().getName());
        assertEquals("NUMBER", table.getColumn("col3").getDataType().getName());

        logger.info("ALTER COLUMN combined with other operations works correctly");
    }

    @Test
    public void testAlterColumnToBoolean() {
        logger.info("Testing ALTER COLUMN to BOOLEAN");

        engine.execute("CREATE TABLE flags (id INTEGER, is_active INTEGER)");

        engine.execute("ALTER TABLE flags ALTER COLUMN is_active SET DATA TYPE BOOLEAN");

        Table table = engine.getCatalog().resolveTable("flags");
        assertNotNull(table);

        TableColumn activeColumn = table.getColumn("is_active");
        assertEquals("BOOLEAN", activeColumn.getDataType().getName());

        logger.info("Column type altered to BOOLEAN successfully");
    }
}
