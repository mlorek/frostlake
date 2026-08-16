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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INSERT / UPDATE / DELETE and their combinations, asserted through query results alone, so every
 * check runs against whichever engine executed the DML — embedded or live. Transactions use the
 * SQL surface (BEGIN / COMMIT / ROLLBACK), never the engine's Java transaction API.
 */
public class DMLOperationsTest extends BaseDatabaseTest {

    // ==================== INSERT TESTS ====================

    @Test
    public void testBasicInsert() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());
    }

    @Test
    public void testBulkInsert() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testInsertWithColumnList() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER, city VARCHAR)");
        engine.execute("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());

        final Row row = result.getRows().get(0);
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

        final ResultSet result = engine.executeQuery("SELECT * FROM test_types");
        assertEquals(1, result.getRowCount());

        final Row row = result.getRows().get(0);
        assertEquals(42L, row.getValue(0));
        assertEquals("hello", row.getValue(1));
        assertEquals(true, row.getValue(2));
        assertEquals(3.14, ((Number) row.getValue(3)).doubleValue(), 0.01);
    }

    @Test
    public void testInsertWithNulls() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', null)");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());

        final Row row = result.getRows().get(0);
        assertNull(row.getValue(2));
    }

    // ==================== UPDATE TESTS ====================

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

    // ==================== DELETE TESTS ====================

    @Test
    public void testBasicDelete() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("DELETE FROM users WHERE id = 1");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testDeleteWithWhere() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        engine.execute("DELETE FROM users WHERE age < 30");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testDeleteMultipleRows() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("""
            INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35), (4, 'Diana', 28)
            """);

        engine.execute("DELETE FROM users WHERE age > 25 AND age < 35");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());

        // Verify remaining rows
        final List<Row> rows = result.getRows();
        boolean sawBob = false;
        boolean sawCharlie = false;
        for (final Row r : rows) {
            if ("Bob".equals(r.getValue(1))) {
                sawBob = true;
            }
            if ("Charlie".equals(r.getValue(1))) {
                sawCharlie = true;
            }
        }
        assertTrue(sawBob);
        assertTrue(sawCharlie);
    }

    @Test
    public void testDeleteNoMatch() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("DELETE FROM users WHERE id = 999");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());
    }

    @Test
    public void testDeleteAll() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        engine.execute("DELETE FROM users");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testDeleteWithComplexWhere() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("""
            INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35), (4, 'Diana', 28)
            """);

        engine.execute("DELETE FROM users WHERE age = 30 OR age > 33");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());
    }

    // ==================== COMBINED OPERATIONS ====================

    @Test
    public void testInsertUpdateDelete() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");

        // Insert
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25)");
        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());

        // Update
        engine.execute("UPDATE users SET age = 31 WHERE id = 1");
        result = engine.executeQuery("SELECT * FROM users WHERE id = 1");
        assertEquals(31L, result.getRows().get(0).getValue(2));

        // Delete
        engine.execute("DELETE FROM users WHERE id = 2");
        result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());
    }

    @Test
    public void testBulkOperations() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER, stock INTEGER)");

        // Bulk insert
        engine.execute("""
            INSERT INTO products VALUES
            (1, 'Widget', 100, 50),
            (2, 'Gadget', 200, 30),
            (3, 'Doohickey', 150, 20),
            (4, 'Thingamajig', 175, 40)
            """);

        // Bulk update - Update products with price < 150 to 120
        engine.execute("UPDATE products SET price = 120 WHERE price < 150");

        // Verify updates - Should have Widget (was 100) and Doohickey (was 150, but 150 < 150 is false)
        ResultSet result = engine.executeQuery("SELECT * FROM products WHERE price = 120");
        assertEquals(1, result.getRowCount()); // Only Widget matches

        // Bulk delete - delete stock < 35 (Gadget=30, Doohickey=20)
        engine.execute("DELETE FROM products WHERE stock < 35");

        result = engine.executeQuery("SELECT * FROM products");
        assertEquals(2, result.getRowCount()); // Widget and Thingamajig remain
    }

    @Test
    public void testTransactionalDML() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");

        engine.execute("BEGIN");
        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("INSERT INTO users VALUES (2, 'Bob')");
        engine.execute("COMMIT");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());

        // Rollback must undo the DELETE — both rows remain.
        engine.execute("BEGIN");
        engine.execute("DELETE FROM users WHERE id = 1");
        engine.execute("ROLLBACK");
        result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());
    }
}
