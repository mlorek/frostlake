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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EXECUTE IMMEDIATE command
 */
public class ExecuteImmediateTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // Create a test table
        engine.execute("""
            CREATE TABLE products (
            id INTEGER,
            name VARCHAR,
            price INTEGER
            )
            """);
    }

    @Test
    public void testExecuteImmediateSimpleInsert() {
        // First test with a simpler case - create and insert using regular SQL
        engine.execute("INSERT INTO products VALUES (1, 'Laptop', 1200)");

        // Now test EXECUTE IMMEDIATE with INSERT
        engine.execute("EXECUTE IMMEDIATE 'INSERT INTO products VALUES (2, ''Mouse'', 25)'");

        ResultSet result = engine.executeQuery("SELECT * FROM products WHERE id = 2");
        assertEquals(1, result.getRowCount());
        assertEquals(2L, result.getRows().get(0).getValue(0));
        assertEquals("Mouse", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testExecuteImmediateCreateTable() {
        // Create a table dynamically using EXECUTE IMMEDIATE
        engine.execute("EXECUTE IMMEDIATE 'CREATE TABLE orders (order_id INTEGER, customer_name VARCHAR)'");

        // Verify the table was created by inserting into it
        engine.execute("INSERT INTO orders VALUES (1, 'Alice')");

        ResultSet result = engine.executeQuery("SELECT * FROM orders");
        assertEquals(1, result.getRowCount());
        assertEquals("Alice", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testExecuteImmediateSelect() {
        // Insert test data
        engine.execute("INSERT INTO products VALUES (1, 'Mouse', 25)");
        engine.execute("INSERT INTO products VALUES (2, 'Keyboard', 75)");

        // Execute SELECT using EXECUTE IMMEDIATE
        var execResult = engine.execute("EXECUTE IMMEDIATE 'SELECT * FROM products WHERE price > 50'");

        assertTrue(execResult.isSuccess());
        assertFalse(execResult.getResultSets().isEmpty());

        ResultSet result = execResult.getResultSets().get(0);
        assertEquals(1, result.getRowCount());
        assertEquals("Keyboard", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testExecuteImmediateUpdate() {
        // Insert test data
        engine.execute("INSERT INTO products VALUES (1, 'Monitor', 300)");

        // First verify the initial price
        ResultSet beforeUpdate = engine.executeQuery("SELECT * FROM products WHERE id = 1");
        assertEquals(300L, beforeUpdate.getRows().get(0).getValue(2)); // price is at index 2

        // Update using EXECUTE IMMEDIATE
        engine.execute("EXECUTE IMMEDIATE 'UPDATE products SET price = 350 WHERE id = 1'");

        // Check that price was updated
        ResultSet afterUpdate = engine.executeQuery("SELECT * FROM products WHERE id = 1");
        assertEquals(350L, afterUpdate.getRows().get(0).getValue(2)); // price is at index 2
    }

    @Test
    public void testExecuteImmediateDelete() {
        // Insert test data
        engine.execute("INSERT INTO products VALUES (1, 'Tablet', 400)");
        engine.execute("INSERT INTO products VALUES (2, 'Phone', 800)");

        // Delete using EXECUTE IMMEDIATE
        engine.execute("EXECUTE IMMEDIATE 'DELETE FROM products WHERE price < 500'");

        ResultSet result = engine.executeQuery("SELECT * FROM products");
        assertEquals(1, result.getRowCount());
        assertEquals("Phone", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testExecuteImmediateMultipleStatements() {
        // Execute IMMEDIATE with CREATE and INSERT in separate calls
        engine.execute("EXECUTE IMMEDIATE 'CREATE TABLE temp_data (id INTEGER, value VARCHAR)'");
        engine.execute("EXECUTE IMMEDIATE 'INSERT INTO temp_data VALUES (1, ''test'')'");

        ResultSet result = engine.executeQuery("SELECT * FROM temp_data");
        assertEquals(1, result.getRowCount());
        assertEquals("test", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testExecuteImmediateWithVariable() {
        // Set a variable containing SQL
        engine.execute("SET my_sql = 'INSERT INTO products VALUES (100, ''Variable Insert'', 999)'");

        // A session variable source needs the $ prefix (live-verified)
        engine.execute("EXECUTE IMMEDIATE $my_sql");

        ResultSet result = engine.executeQuery("SELECT * FROM products WHERE id = 100");
        assertEquals(1, result.getRowCount());
        assertEquals("Variable Insert", result.getRows().get(0).getValue(1));

        // The bare-identifier source is a syntax error at session level (live-verified)
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("EXECUTE IMMEDIATE my_sql");
            }
        });
    }

    @Test
    public void testExecuteImmediateComplexQuery() {
        // Insert test data
        engine.execute("INSERT INTO products VALUES (1, 'Item A', 100)");
        engine.execute("INSERT INTO products VALUES (2, 'Item B', 200)");
        engine.execute("INSERT INTO products VALUES (3, 'Item C', 150)");

        // Execute complex query with ORDER BY and LIMIT
        var execResult = engine.execute(
            "EXECUTE IMMEDIATE 'SELECT * FROM products WHERE price >= 100 ORDER BY price DESC LIMIT 2'"
        );

        assertTrue(execResult.isSuccess());
        assertFalse(execResult.getResultSets().isEmpty());

        ResultSet result = execResult.getResultSets().get(0);
        assertEquals(2, result.getRowCount());
        assertEquals(200L, result.getRows().get(0).getValue(2)); // Highest price first
    }
}
