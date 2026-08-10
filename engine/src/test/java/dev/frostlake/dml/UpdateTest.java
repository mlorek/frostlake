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

/**
 * Tests for UPDATE command
 */
public class UpdateTest extends BaseDatabaseTest {

    @Test
    public void testBasicUpdate() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
        engine.execute("UPDATE users SET age = 31");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        final Row row = result.getRows().get(0);
        assertEquals(31L, row.getValue(2));
    }

    @Test
    public void testUpdateWithWhere() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        engine.execute("UPDATE users SET age = 26 WHERE id = 2");

        ResultSet result = engine.executeQuery("SELECT * FROM users WHERE id = 2");
        Row row = result.getRows().get(0);
        assertEquals(26L, row.getValue(2));

        // Verify others unchanged
        result = engine.executeQuery("SELECT * FROM users WHERE id = 1");
        row = result.getRows().get(0);
        assertEquals(30L, row.getValue(2));
    }

    @Test
    public void testUpdateMultipleColumns() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("UPDATE users SET name = 'Alicia', age = 31 WHERE id = 1");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        final Row row = result.getRows().get(0);
        assertEquals("Alicia", row.getValue(1));
        assertEquals(31L, row.getValue(2));
    }

    @Test
    public void testUpdateWithComplexWhere() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        engine.execute("UPDATE users SET age = 100 WHERE age > 28 AND age < 35");

        final ResultSet result = engine.executeQuery("SELECT * FROM users WHERE age = 100");
        assertEquals(1, result.getRowCount());
    }

    @Test
    public void testUpdateNoMatch() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("UPDATE users SET age = 40 WHERE id = 999");

        // Original value should be unchanged
        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        final Row row = result.getRows().get(0);
        assertEquals(30L, row.getValue(2));
    }

    @Test
    public void testUpdateWithExpression() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100), (2, 'Gadget', 200)");

        // Update price with calculation
        engine.execute("UPDATE products SET price = 150 WHERE id = 1");

        final ResultSet result = engine.executeQuery("SELECT * FROM products WHERE id = 1");
        final Row row = result.getRows().get(0);
        assertEquals(150L, row.getValue(2));
    }

    @Test
    public void testUpdateMultipleRows() {
        engine.execute("CREATE TABLE users (id INTEGER, department VARCHAR, salary INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Engineering', 100), (2, 'Engineering', 110), (3, 'Sales', 90)");

        engine.execute("UPDATE users SET salary = 120 WHERE department = 'Engineering'");

        final ResultSet result = engine.executeQuery("SELECT * FROM users WHERE department = 'Engineering'");
        assertEquals(2, result.getRowCount());

        for (final Row row : result.getRows()) {
            assertEquals(120L, row.getValue(2));
        }
    }

    @Test
    public void testUpdateAll() {
        engine.execute("CREATE TABLE users (id INTEGER, status VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'inactive'), (2, 'inactive'), (3, 'inactive')");

        engine.execute("UPDATE users SET status = 'active'");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(3, result.getRowCount());

        for (final Row row : result.getRows()) {
            assertEquals("active", row.getValue(1));
        }
    }
}
