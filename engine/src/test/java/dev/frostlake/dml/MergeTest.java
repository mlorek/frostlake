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

/**
 * Tests for MERGE command
 */
public class MergeTest extends BaseDatabaseTest {

    @Test
    public void testMergeInsertOnly() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100)");

        engine.execute("""
            MERGE INTO products USING (VALUES (2, 'Gadget', 200))
            ON id = 2
            WHEN NOT MATCHED THEN INSERT VALUES (2, 'Gadget', 200)
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM products ORDER BY id");
        assertEquals(2, result.getRowCount());

        List<Row> rows = result.getRows();
        assertEquals(2L, rows.get(1).getValue(0));
        assertEquals("Gadget", rows.get(1).getValue(1));
    }

    @Test
    public void testMergeUpdateOnly() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100), (2, 'Gadget', 200)");

        engine.execute("""
            MERGE INTO products USING (VALUES (1, 'Widget', 150))
            ON id = 1
            WHEN MATCHED THEN UPDATE SET price = 150
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM products WHERE id = 1");
        assertEquals(1, result.getRowCount());

        Row row = result.getRows().get(0);
        assertEquals(150L, row.getValue(2));
    }

    @Test
    public void testMergeInsertAndUpdate() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100)");

        engine.execute("""
            MERGE INTO products USING (VALUES (1, 'Widget', 120))
            ON id = 1
            WHEN MATCHED THEN UPDATE SET price = 120
            WHEN NOT MATCHED THEN INSERT VALUES (1, 'Widget', 120)
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM products WHERE id = 1");
        Row row = result.getRows().get(0);
        assertEquals(120L, row.getValue(2));
    }

    @Test
    public void testMergeMultipleRows() {
        engine.execute("CREATE TABLE inventory (id INTEGER, product VARCHAR, quantity INTEGER)");
        engine.execute("INSERT INTO inventory VALUES (1, 'Widget', 50), (2, 'Gadget', 30)");

        engine.execute("CREATE TABLE updates (id INTEGER, product VARCHAR, quantity INTEGER)");
        engine.execute("INSERT INTO updates VALUES (1, 'Widget', 75), (3, 'Doohickey', 20)");

        engine.execute("""
            MERGE INTO inventory USING updates
            ON inventory.id = updates.id
            WHEN MATCHED THEN UPDATE SET quantity = 75
            WHEN NOT MATCHED THEN INSERT VALUES (3, 'Doohickey', 20)
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM inventory ORDER BY id");
        assertEquals(3, result.getRowCount());

        result = engine.executeQuery("SELECT * FROM inventory WHERE id = 1");
        assertEquals(75L, result.getRows().get(0).getValue(2));

        result = engine.executeQuery("SELECT * FROM inventory WHERE id = 3");
        assertEquals(1, result.getRowCount());
        assertEquals("Doohickey", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testMergeWithComplexUpdate() {
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary INTEGER, bonus INTEGER)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 90000, 5000), (2, 'Bob', 80000, 4000)");

        engine.execute("""
            MERGE INTO employees USING (VALUES (1, 'Alice', 95000, 6000))
            ON id = 1
            WHEN MATCHED THEN UPDATE SET salary = 95000, bonus = 6000
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM employees WHERE id = 1");
        Row row = result.getRows().get(0);
        assertEquals(95000L, row.getValue(2));
        assertEquals(6000L, row.getValue(3));
    }

    @Test
    public void testMergeWithColumnList() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER, stock INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100, 50)");

        engine.execute("""
            MERGE INTO products USING (VALUES (2, 'Gadget', 200))
            ON id = 2
            WHEN NOT MATCHED THEN INSERT (id, name, price) VALUES (2, 'Gadget', 200)
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM products WHERE id = 2");
        assertEquals(1, result.getRowCount());

        Row row = result.getRows().get(0);
        assertEquals(2L, row.getValue(0));
        assertEquals("Gadget", row.getValue(1));
        assertEquals(200L, row.getValue(2));
        assertNull(row.getValue(3));
    }

    @Test
    public void testMergeBulkUpsert() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER, last_updated VARCHAR)");
        engine.execute("""
            INSERT INTO products VALUES (1, 'Widget', 100, '2024-01-01'), (2, 'Gadget', 200, '2024-01-01')
            """);

        engine.execute("CREATE TABLE staging (id INTEGER, name VARCHAR, price INTEGER, last_updated VARCHAR)");
        engine.execute("""
            INSERT INTO staging VALUES
            (1, 'Widget', 120, '2024-02-01'),
            (2, 'Gadget', 220, '2024-02-01'),
            (3, 'Doohickey', 150, '2024-02-01')
            """);

        engine.execute("""
            MERGE INTO products USING staging
            ON products.id = staging.id
            WHEN MATCHED THEN UPDATE SET price = 120, last_updated = '2024-02-01'
            WHEN NOT MATCHED THEN INSERT VALUES (3, 'Doohickey', 150, '2024-02-01')
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM products ORDER BY id");
        assertEquals(3, result.getRowCount());

        result = engine.executeQuery("SELECT * FROM products WHERE id = 1");
        assertEquals(120L, result.getRows().get(0).getValue(2));

        result = engine.executeQuery("SELECT * FROM products WHERE id = 3");
        assertEquals("Doohickey", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testMergeNoMatch() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100)");

        engine.execute("""
            MERGE INTO products USING (VALUES (2, 'Gadget', 200))
            ON id = 2
            WHEN MATCHED THEN UPDATE SET price = 250
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM products");
        assertEquals(1, result.getRowCount());
        assertEquals(100L, result.getRows().get(0).getValue(2));
    }

    @Test
    public void testMergeAllMatch() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100), (2, 'Gadget', 200)");

        engine.execute("""
            MERGE INTO products USING (VALUES (1, 'Widget', 120))
            ON id = 1
            WHEN NOT MATCHED THEN INSERT VALUES (1, 'Widget', 120)
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM products");
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testMergeEmptyTarget() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");

        engine.execute("""
            MERGE INTO products USING (VALUES (1, 'Widget', 100))
            ON id = 1
            WHEN MATCHED THEN UPDATE SET price = 150
            WHEN NOT MATCHED THEN INSERT VALUES (1, 'Widget', 100)
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM products");
        assertEquals(1, result.getRowCount());
        assertEquals(100L, result.getRows().get(0).getValue(2));
    }

    @Test
    public void testMergeWithDifferentTypes() {
        engine.execute("""
            CREATE TABLE test_types (
            id INTEGER,
            str_val VARCHAR,
            bool_val BOOLEAN,
            float_val FLOAT
            )
            """);
        engine.execute("INSERT INTO test_types VALUES (1, 'original', true, 1.5)");

        engine.execute("""
            MERGE INTO test_types USING (VALUES (1, 'updated', false, 2.5))
            ON id = 1
            WHEN MATCHED THEN UPDATE SET str_val = 'updated', bool_val = false, float_val = 2.5
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM test_types");
        Row row = result.getRows().get(0);
        assertEquals("updated", row.getValue(1));
        assertEquals(false, row.getValue(2));
        assertEquals(2.5, ((Number) row.getValue(3)).doubleValue(), 0.01);
    }

    @Test
    public void testMergeIncrementalLoad() {
        engine.execute("CREATE TABLE sales (date VARCHAR, product VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO sales VALUES ('2024-01-01', 'Widget', 1000)");

        engine.execute("CREATE TABLE daily_sales (date VARCHAR, product VARCHAR, amount INTEGER)");
        engine.execute("""
            INSERT INTO daily_sales VALUES
            ('2024-01-01', 'Widget', 1500),
            ('2024-01-02', 'Gadget', 2000)
            """);

        engine.execute("""
            MERGE INTO sales USING daily_sales
            ON sales.date = daily_sales.date AND sales.product = daily_sales.product
            WHEN MATCHED THEN UPDATE SET amount = 1500
            WHEN NOT MATCHED THEN INSERT VALUES ('2024-01-02', 'Gadget', 2000)
            """);

        ResultSet result = engine.executeQuery("SELECT * FROM sales ORDER BY date");
        assertEquals(2, result.getRowCount());

        result = engine.executeQuery("SELECT * FROM sales WHERE date = '2024-01-01'");
        assertEquals(1500L, result.getRows().get(0).getValue(2));

        result = engine.executeQuery("SELECT * FROM sales WHERE date = '2024-01-02'");
        assertEquals(1, result.getRowCount());
    }
}
