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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for IF NOT EXISTS clause in ADD COLUMN statement
 */
public class AddColumnIfNotExistsTest {
    private static final Logger logger = LoggerFactory.getLogger(AddColumnIfNotExistsTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for ADD COLUMN IF NOT EXISTS tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testAddColumnWithoutIfNotExists() {
        logger.info("Testing ADD COLUMN without IF NOT EXISTS on new column");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE users ADD COLUMN age INTEGER");

        Table table = engine.getCatalog().resolveTable("USERS");
        assertNotNull(table);
        assertEquals(3, table.getColumns().size());
        assertTrue(table.hasColumn("age"));

        logger.info("ADD COLUMN without IF NOT EXISTS works correctly");
    }

    @Test
    public void testAddColumnWithoutIfNotExistsOnExistingColumn() {
        logger.info("Testing ADD COLUMN without IF NOT EXISTS on existing column");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("ALTER TABLE users ADD COLUMN name VARCHAR");
            }
        });

        logger.info("ADD COLUMN without IF NOT EXISTS correctly throws exception on duplicate");
    }

    @Test
    public void testAddColumnIfNotExistsOnNewColumn() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS on new column");

        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS price INTEGER");

        Table table = engine.getCatalog().resolveTable("PRODUCTS");
        assertNotNull(table);
        assertEquals(3, table.getColumns().size());
        assertTrue(table.hasColumn("price"));

        logger.info("ADD COLUMN IF NOT EXISTS on new column works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsOnExistingColumn() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS on existing column");

        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");

        engine.execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS price INTEGER");

        Table table = engine.getCatalog().resolveTable("PRODUCTS");
        assertNotNull(table);
        assertEquals(3, table.getColumns().size());

        logger.info("ADD COLUMN IF NOT EXISTS on existing column succeeds without error");
    }

    @Test
    public void testAddColumnIfNotExistsWithDifferentType() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS with different type on existing column");

        engine.execute("CREATE TABLE items (id INTEGER, name VARCHAR)");

        engine.execute("ALTER TABLE items ADD COLUMN IF NOT EXISTS name INTEGER");

        Table table = engine.getCatalog().resolveTable("ITEMS");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertEquals("VARCHAR", table.getColumn("name").getDataType().getName());

        logger.info("ADD COLUMN IF NOT EXISTS preserves existing column type");
    }

    @Test
    public void testAddMultipleColumnsIfNotExists() {
        logger.info("Testing multiple ADD COLUMN IF NOT EXISTS operations");

        engine.execute("CREATE TABLE orders (id INTEGER, customer_id INTEGER)");

        engine.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS status VARCHAR");
        engine.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS status VARCHAR");
        engine.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS total INTEGER");

        Table table = engine.getCatalog().resolveTable("ORDERS");
        assertNotNull(table);
        assertEquals(4, table.getColumns().size());
        assertTrue(table.hasColumn("status"));
        assertTrue(table.hasColumn("total"));

        logger.info("Multiple ADD COLUMN IF NOT EXISTS operations work correctly");
    }

    @Test
    public void testAddColumnIfNotExistsWithQualifiedName() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS with schema-qualified table name");

        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("CREATE TABLE test_schema.customers (id INTEGER, name VARCHAR)");

        engine.execute("ALTER TABLE test_schema.customers ADD COLUMN IF NOT EXISTS email VARCHAR");

        Table table = engine.getCatalog().resolveTable("TEST_SCHEMA.CUSTOMERS");
        assertNotNull(table);
        assertEquals(3, table.getColumns().size());
        assertTrue(table.hasColumn("email"));

        logger.info("ADD COLUMN IF NOT EXISTS with qualified name works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsWithAlterTableIfExists() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS with ALTER TABLE IF EXISTS");

        engine.execute("CREATE TABLE test_table (id INTEGER)");
        engine.execute("ALTER TABLE IF EXISTS test_table ADD COLUMN IF NOT EXISTS col1 VARCHAR");

        Table table = engine.getCatalog().resolveTable("TEST_TABLE");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertTrue(table.hasColumn("col1"));

        logger.info("ADD COLUMN IF NOT EXISTS with ALTER TABLE IF EXISTS works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsOnNonExistentTableWithTableIfExists() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS on non-existent table with ALTER TABLE IF EXISTS");

        engine.execute("ALTER TABLE IF EXISTS non_existent ADD COLUMN IF NOT EXISTS col1 VARCHAR");

        logger.info("ADD COLUMN IF NOT EXISTS with ALTER TABLE IF EXISTS on non-existent table succeeds");
    }

    @Test
    public void testAddColumnIfNotExistsWithData() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS on table with data");

        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice')");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob')");

        engine.execute("ALTER TABLE employees ADD COLUMN IF NOT EXISTS salary INTEGER");

        ResultSet rs = engine.executeQuery("SELECT * FROM employees");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        Table table = engine.getCatalog().resolveTable("EMPLOYEES");
        assertEquals(3, table.getColumns().size());

        logger.info("ADD COLUMN IF NOT EXISTS on table with data works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsIdempotent() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS is idempotent");

        engine.execute("CREATE TABLE config (id INTEGER)");

        for (int i = 0; i < 5; i++) {
            engine.execute("ALTER TABLE config ADD COLUMN IF NOT EXISTS setting VARCHAR");
        }

        Table table = engine.getCatalog().resolveTable("CONFIG");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());

        logger.info("ADD COLUMN IF NOT EXISTS is idempotent");
    }

    @Test
    public void testAddColumnIfNotExistsCaseInsensitive() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS is case-insensitive");

        engine.execute("CREATE TABLE test_table (id INTEGER, Name VARCHAR)");

        engine.execute("ALTER TABLE test_table ADD COLUMN IF NOT EXISTS name VARCHAR");

        Table table = engine.getCatalog().resolveTable("TEST_TABLE");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());

        logger.info("ADD COLUMN IF NOT EXISTS is case-insensitive");
    }

    @Test
    public void testAddColumnIfNotExistsWithComplexDataType() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS with complex data type");

        engine.execute("CREATE TABLE events (id INTEGER)");
        engine.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS event_time TIMESTAMP_LTZ");

        Table table = engine.getCatalog().resolveTable("EVENTS");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertTrue(table.hasColumn("event_time"));

        logger.info("ADD COLUMN IF NOT EXISTS with complex data type works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsWithTypeParameters() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS with type parameters");

        engine.execute("CREATE TABLE documents (id INTEGER)");
        engine.execute("ALTER TABLE documents ADD COLUMN IF NOT EXISTS content VARCHAR(16777216)");

        Table table = engine.getCatalog().resolveTable("DOCUMENTS");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertTrue(table.hasColumn("content"));
        assertEquals("VARCHAR", table.getColumn("content").getDataType().getName());

        logger.info("ADD COLUMN IF NOT EXISTS with type parameters works correctly");
    }

    @Test
    public void testMixedAddColumnWithAndWithoutIfNotExists() {
        logger.info("Testing mixed ADD COLUMN with and without IF NOT EXISTS");

        engine.execute("CREATE TABLE mixed (id INTEGER)");

        engine.execute("ALTER TABLE mixed ADD COLUMN col1 VARCHAR");
        engine.execute("ALTER TABLE mixed ADD COLUMN IF NOT EXISTS col2 INTEGER");
        engine.execute("ALTER TABLE mixed ADD COLUMN IF NOT EXISTS col1 VARCHAR");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("ALTER TABLE mixed ADD COLUMN col2 INTEGER");
            }
        });

        Table table = engine.getCatalog().resolveTable("MIXED");
        assertNotNull(table);
        assertEquals(3, table.getColumns().size());

        logger.info("Mixed ADD COLUMN with and without IF NOT EXISTS works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsRealWorldExample() {
        logger.info("Testing real-world example from user");

        engine.execute("CREATE SCHEMA BASE");
        engine.execute("CREATE TABLE BASE.DIM_PRODUCT (PRODUCT_ID INTEGER, PRODUCT_NAME VARCHAR)");

        engine.execute("ALTER TABLE IF EXISTS BASE.DIM_PRODUCT ADD COLUMN IF NOT EXISTS PRODUCT_URL VARCHAR(16777216)");

        Table table = engine.getCatalog().resolveTable("BASE.DIM_PRODUCT");
        assertNotNull(table);
        assertEquals(3, table.getColumns().size());
        assertTrue(table.hasColumn("PRODUCT_URL"));
        assertEquals("VARCHAR", table.getColumn("PRODUCT_URL").getDataType().getName());

        engine.execute("ALTER TABLE IF EXISTS BASE.DIM_PRODUCT ADD COLUMN IF NOT EXISTS PRODUCT_URL VARCHAR(16777216)");

        table = engine.getCatalog().resolveTable("BASE.DIM_PRODUCT");
        assertEquals(3, table.getColumns().size());

        logger.info("Real-world example works correctly");
    }
}
