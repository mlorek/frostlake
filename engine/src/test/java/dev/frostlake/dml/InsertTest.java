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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for INSERT command
 */
public class InsertTest extends BaseDatabaseTest {

    @Test
    public void testBasicInsert() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());
    }

    @Test
    public void testBulkInsert() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testInsertWithColumnList() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER, city VARCHAR)");
        engine.execute("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());

        Row row = result.getRows().get(0);
        assertEquals(1L, row.getValue(0));
        assertEquals("Alice", row.getValue(1));
        assertEquals(30L, row.getValue(2));
        assertNull(row.getValue(3)); // city should be null
    }

    @Test
    public void testInsertWithDifferentTypes() {
        engine.execute("""
            CREATE TABLE test_types (
            int_col INTEGER,
            str_col VARCHAR,
            bool_col BOOLEAN,
            float_col FLOAT
            )
            """);

        engine.execute("INSERT INTO test_types VALUES (42, 'hello', true, 3.14)");

        ResultSet result = engine.executeQuery("SELECT * FROM test_types");
        assertEquals(1, result.getRowCount());

        Row row = result.getRows().get(0);
        assertEquals(42L, row.getValue(0));
        assertEquals("hello", row.getValue(1));
        assertEquals(true, row.getValue(2));
        assertEquals(3.14, ((Number) row.getValue(3)).doubleValue(), 0.01);
    }

    @Test
    public void testInsertWithNulls() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', null)");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());

        Row row = result.getRows().get(0);
        assertNull(row.getValue(2));
    }

    @Test
    public void testInsertMultipleRows() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100)");
        engine.execute("INSERT INTO products VALUES (2, 'Gadget', 200)");
        engine.execute("INSERT INTO products VALUES (3, 'Doohickey', 150)");

        ResultSet result = engine.executeQuery("SELECT * FROM products");
        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testInsertAndQuery() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
        engine.execute("INSERT INTO users VALUES (2, 'Bob', 25)");

        ResultSet result = engine.executeQuery("SELECT * FROM users WHERE age > 28");
        assertEquals(1, result.getRowCount());
        assertEquals("Alice", result.getRows().get(0).getValue(1));
    }
}
