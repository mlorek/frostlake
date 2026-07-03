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
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for PRIMARY KEY functionality
 */
public class PrimaryKeyTest extends BaseDatabaseTest {

    @Test
    public void testSingleColumnPrimaryKey() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            name VARCHAR,
            email VARCHAR
            )
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        assertNotNull(table);
        assertEquals(3, table.getColumns().size());

        // Check that id column is marked as primary key
        TableColumn idColumn = table.getColumn("id");
        assertTrue(idColumn.isPrimaryKey(), "id should be primary key");

        // Check that other columns are not primary keys
        assertFalse(table.getColumn("name").isPrimaryKey());
        assertFalse(table.getColumn("email").isPrimaryKey());

        // Check primary keys list
        List<String> primaryKeys = table.getPrimaryKeys();
        assertEquals(1, primaryKeys.size());
        assertTrue(primaryKeys.contains("id") || primaryKeys.contains("ID"));
    }

    @Test
    public void testPrimaryKeyWithNotNull() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY NOT NULL,
            name VARCHAR
            )
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        TableColumn idColumn = table.getColumn("id");
        assertTrue(idColumn.isPrimaryKey());
        assertFalse(idColumn.isNullable(), "Primary key should be NOT NULL");
    }

    @Test
    public void testMultipleSingleColumnPrimaryKeys() {
        // This should work - each table has one primary key
        engine.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE products (product_id INTEGER PRIMARY KEY, name VARCHAR)");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");

        Table users = schema.getTable("users");
        assertEquals(1, users.getPrimaryKeys().size());

        Table products = schema.getTable("products");
        assertEquals(1, products.getPrimaryKeys().size());
    }

    @Test
    public void testPrimaryKeyWithAutoIncrement() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name VARCHAR
            )
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        TableColumn idColumn = table.getColumn("id");
        assertTrue(idColumn.isPrimaryKey());
        assertTrue(idColumn.isAutoIncrement());
    }

    @Test
    public void testPrimaryKeyWithDefault() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY DEFAULT 1,
            name VARCHAR
            )
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        TableColumn idColumn = table.getColumn("id");
        assertTrue(idColumn.isPrimaryKey());
        assertEquals(1L, idColumn.getDefaultValue());
    }

    @Test
    public void testInsertWithPrimaryKey() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            name VARCHAR
            )
            """);

        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("INSERT INTO users VALUES (2, 'Bob')");

        ResultSet result = engine.executeQuery("SELECT * FROM users ORDER BY id");
        assertEquals(2, result.getRowCount());

        result.next();
        assertEquals(1L, result.getValue("id"));
        assertEquals("Alice", result.getValue("name"));

        result.next();
        assertEquals(2L, result.getValue("id"));
        assertEquals("Bob", result.getValue("name"));
    }

    @Test
    public void testPrimaryKeyInDifferentDataTypes() {
        // INTEGER primary key
        engine.execute("CREATE TABLE t1 (id INTEGER PRIMARY KEY, name VARCHAR)");

        // BIGINT primary key
        engine.execute("CREATE TABLE t2 (id BIGINT PRIMARY KEY, name VARCHAR)");

        // VARCHAR primary key
        engine.execute("CREATE TABLE t3 (id VARCHAR PRIMARY KEY, name VARCHAR)");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");

        assertTrue(schema.getTable("t1").getColumn("id").isPrimaryKey());
        assertTrue(schema.getTable("t2").getColumn("id").isPrimaryKey());
        assertTrue(schema.getTable("t3").getColumn("id").isPrimaryKey());
    }

    @Test
    public void testGetPrimaryKeysList() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            username VARCHAR,
            email VARCHAR
            )
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        List<String> primaryKeys = table.getPrimaryKeys();
        assertNotNull(primaryKeys);
        assertEquals(1, primaryKeys.size());

        String pkName = primaryKeys.get(0);
        assertTrue(pkName.equalsIgnoreCase("id"));
    }

    @Test
    public void testTableWithNoPrimaryKey() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, email VARCHAR)");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        List<String> primaryKeys = table.getPrimaryKeys();
        assertNotNull(primaryKeys);
        assertEquals(0, primaryKeys.size(), "Table should have no primary keys");

        // Verify none of the columns are marked as primary key
        for (final TableColumn col : table.getColumns()) {
            assertFalse(col.isPrimaryKey(), col.getName() + " should not be primary key");
        }
    }

    @Test
    public void testPrimaryKeyWithComplexTable() {
        engine.execute("""
            CREATE TABLE orders (
            order_id INTEGER PRIMARY KEY,
            customer_id INTEGER NOT NULL,
            order_date DATE,
            total_amount DECIMAL,
            status VARCHAR DEFAULT 'pending'
            )
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("orders");

        assertEquals(5, table.getColumns().size());

        TableColumn orderIdCol = table.getColumn("order_id");
        assertTrue(orderIdCol.isPrimaryKey());

        TableColumn customerIdCol = table.getColumn("customer_id");
        assertFalse(customerIdCol.isPrimaryKey());
        assertFalse(customerIdCol.isNullable());
    }

    @Test
    public void testShowPrimaryKeyInSystemViews() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            name VARCHAR
            )
            """);

        ResultSet columns = engine.showColumns("users");

        boolean foundPrimaryKey = false;
        while (columns.next()) {
            String columnName = (String) columns.getValue("COLUMN_NAME");
            if ("id".equalsIgnoreCase(columnName)) {
                foundPrimaryKey = true;
                break;
            }
        }

        assertTrue(foundPrimaryKey, "Primary key column should be visible in SHOW COLUMNS");
    }
}
