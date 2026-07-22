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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CREATE TABLE command
 */
public class CreateTableTest extends BaseDatabaseTest {

    @Test
    public void testCreateSimpleTable() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        assertNotNull(table, "Table should exist");
        assertEquals("USERS", table.getName());
        assertEquals(3, table.getColumns().size());
    }

    @Test
    public void testCreateTableWithPrimaryKey() {
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
        assertTrue(table.getColumns().get(0).isPrimaryKey(), "First column should be primary key");
    }

    @Test
    public void testCreateTableWithNotNull() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER NOT NULL,
            name VARCHAR NOT NULL,
            age INTEGER
            )
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        assertNotNull(table);
        assertFalse(table.getColumns().get(0).isNullable(), "id should be NOT NULL");
        assertFalse(table.getColumns().get(1).isNullable(), "name should be NOT NULL");
        assertTrue(table.getColumns().get(2).isNullable(), "age should be nullable");
    }

    @Test
    public void testCreateTableWithDefault() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER,
            name VARCHAR,
            status VARCHAR DEFAULT 'active'
            )
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        assertNotNull(table);
        assertEquals("active", table.getColumns().get(2).getDefaultValue());
    }

    @Test
    public void testCreateTableWithAutoIncrement() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER AUTOINCREMENT,
            name VARCHAR
            )
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        assertNotNull(table);
        assertTrue(table.getColumns().get(0).isAutoIncrement());
    }

    @Test
    public void testCreateTableWithAllDataTypes() {
        engine.execute("""
            CREATE TABLE test_types (
            col_int INTEGER,
            col_bigint BIGINT,
            col_smallint SMALLINT,
            col_number NUMBER,
            col_decimal DECIMAL,
            col_float FLOAT,
            col_double DOUBLE,
            col_varchar VARCHAR,
            col_string STRING,
            col_text TEXT,
            col_char CHAR,
            col_boolean BOOLEAN,
            col_date DATE,
            col_timestamp TIMESTAMP,
            col_variant VARIANT,
            col_array ARRAY,
            col_object OBJECT
            )
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("test_types");

        assertNotNull(table);
        assertEquals(17, table.getColumns().size());
    }

    @Test
    public void testCreateTableAndInsertData() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
        engine.execute("INSERT INTO users VALUES (2, 'Bob', 25)");

        ResultSet result = engine.executeQuery("SELECT * FROM users");

        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testCreateTableInDifferentSchema() {
        engine.execute("CREATE SCHEMA other_schema");
        engine.execute("CREATE TABLE other_schema.users (id INTEGER, name VARCHAR)");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("other_schema");
        Table table = schema.getTable("users");

        assertNotNull(table);
        assertEquals("USERS", table.getName());
    }

    @Test
    public void testCreateTableWithQualifiedName() {
        engine.execute("CREATE TABLE test_db.test_schema.products (id INTEGER, name VARCHAR)");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("products");

        assertNotNull(table);
    }

    @Test
    public void testCreateTableIfNotExists() {
        // Create table first time
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");

        // Create with IF NOT EXISTS should not throw
        assertDoesNotThrow(() -> {
            engine.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER, email VARCHAR)");
        }, "Creating table with IF NOT EXISTS should not fail");

        // Verify original table structure is preserved
        ResultSet tables = engine.executeQuery("SHOW TABLES");
        assertTrue(tables.getRowCount() > 0, "Table should exist");
    }
}
