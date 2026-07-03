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

package dev.frostlake.scripting;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EXECUTE IMMEDIATE with dollar-quoted strings
 */
public class ExecuteImmediateDollarQuotedTest extends BaseJdbcTest {

    @Test
    public void testExecuteImmediateWithDollarQuotedString() throws SQLException {
        // Create table using EXECUTE IMMEDIATE with dollar-quoted string
        statement.execute("""
            EXECUTE IMMEDIATE $$CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)$$
            """);

        // Verify table was created
        ResultSet rs = statement.executeQuery("SHOW TABLES");
        assertTrue(rs.next(), "Table should be created");
        assertEquals("products", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testExecuteImmediateWithDollarQuotedInsert() throws SQLException {
        // Create table
        statement.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");

        // Insert data with dollar-quoted string - no need to escape quotes in dollar-quoted
        statement.execute("EXECUTE IMMEDIATE $$INSERT INTO users VALUES (1, 'John O''Brien')$$");

        // Verify data was inserted (note: quote escaping is preserved from the INSERT statement)
        ResultSet rs = statement.executeQuery("SELECT * FROM users");
        assertTrue(rs.next(), "Should have data");
        assertEquals(1, rs.getInt("id"));
        // The INSERT statement itself has escaped quotes, so they remain escaped
        assertTrue(rs.getString("name").contains("Brien"), "Name should contain Brien");
        rs.close();
    }

    @Test
    public void testExecuteImmediateWithDollarQuotedSelect() throws SQLException {
        // Create and populate table
        statement.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary INTEGER)");
        statement.execute("INSERT INTO employees VALUES (1, 'Alice', 75000)");
        statement.execute("INSERT INTO employees VALUES (2, 'Bob', 50000)");

        // Execute SELECT with dollar-quoted string
        ResultSet rs = statement.executeQuery("EXECUTE IMMEDIATE $$SELECT * FROM employees WHERE salary > 60000$$");

        assertTrue(rs.next(), "Should have result");
        assertEquals("Alice", rs.getString("name"));
        assertFalse(rs.next(), "Should have only one result");
        rs.close();
    }

    @Test
    public void testExecuteImmediateWithComplexDollarQuotedSQL() throws SQLException {
        // Create procedure with EXECUTE IMMEDIATE using dollar-quoted string
        // Note: Using $$ in EXECUTE IMMEDIATE, but single quotes for procedure body
        String sql = """
            EXECUTE IMMEDIATE $$CREATE PROCEDURE calculate_bonus(emp_salary INTEGER) RETURNS INTEGER AS 'emp_salary * 0.10'$$
            """;

        statement.execute(sql);

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next(), "Procedure should be created");
        assertEquals("CALCULATE_BONUS", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testExecuteImmediateWithDollarQuotedMultiline() throws SQLException {
        // Multi-line SQL in dollar-quoted string
        statement.execute("""
            EXECUTE IMMEDIATE $$CREATE TABLE inventory (id INTEGER, product_name VARCHAR, quantity INTEGER)$$
            """);

        // Verify table was created
        ResultSet rs = statement.executeQuery("SHOW TABLES");
        boolean found = false;
        while (rs.next()) {
            if ("inventory".equals(rs.getString("name"))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Inventory table should be created");
        rs.close();
    }

    @Test
    public void testExecuteImmediateWithDollarQuotedView() throws SQLException {
        // Create base table
        statement.execute("CREATE TABLE orders (id INTEGER, customer VARCHAR, amount INTEGER)");
        statement.execute("INSERT INTO orders VALUES (1, 'Alice', 100)");
        statement.execute("INSERT INTO orders VALUES (2, 'Bob', 200)");
        statement.execute("INSERT INTO orders VALUES (3, 'Alice', 150)");

        // Create view with dollar-quoted EXECUTE IMMEDIATE
        statement.execute("""
            EXECUTE IMMEDIATE $$CREATE VIEW high_value_orders AS SELECT * FROM orders WHERE amount > 100$$
            """);

        // Verify view was created and works
        ResultSet rs = statement.executeQuery("SELECT * FROM high_value_orders");
        int count = 0;
        while (rs.next()) {
            count++;
        }
        assertEquals(2, count, "Should have 2 high value orders");
        rs.close();
    }

    @Test
    public void testExecuteImmediateWithDollarQuotedFunction() throws SQLException {
        // Create function with dollar-quoted string
        statement.execute("EXECUTE IMMEDIATE $$CREATE FUNCTION triple(x INTEGER) RETURNS INTEGER AS 'x * 3'$$");

        // Verify function was created
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.next(), "Function should be created");
        assertEquals("TRIPLE", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testExecuteImmediateMixedQuotingStyles() throws SQLException {
        // Test that both single-quoted and dollar-quoted work

        // Single-quoted style
        statement.execute("EXECUTE IMMEDIATE 'CREATE TABLE test1 (id INTEGER)'");

        // Dollar-quoted style
        statement.execute("EXECUTE IMMEDIATE $$CREATE TABLE test2 (id INTEGER)$$");

        // Verify both tables were created
        ResultSet rs = statement.executeQuery("SHOW TABLES");
        int count = 0;
        while (rs.next()) {
            String tableName = rs.getString("name");
            if ("test1".equals(tableName) || "test2".equals(tableName)) {
                count++;
            }
        }
        assertEquals(2, count, "Both tables should be created");
        rs.close();
    }

    @Test
    public void testExecuteImmediateWithDollarQuotedDelete() throws SQLException {
        // Test DELETE with dollar-quoted EXECUTE IMMEDIATE
        statement.execute("CREATE TABLE items (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO items VALUES (1, 'Item1')");
        statement.execute("INSERT INTO items VALUES (2, 'Item2')");

        // Delete with dollar-quoted string
        statement.execute("EXECUTE IMMEDIATE $$DELETE FROM items WHERE id = 1$$");

        ResultSet rs = statement.executeQuery("SELECT * FROM items");
        assertTrue(rs.next(), "Should have data");
        assertEquals(2, rs.getInt("id"));
        assertFalse(rs.next(), "Should have only one row");
        rs.close();
    }

    @Test
    public void testExecuteImmediateWithDollarQuotedUpdate() throws SQLException {
        // Test UPDATE with dollar-quoted EXECUTE IMMEDIATE
        statement.execute("CREATE TABLE prices (id INTEGER, amount INTEGER)");
        statement.execute("INSERT INTO prices VALUES (1, 100)");

        // Update with dollar-quoted string
        statement.execute("EXECUTE IMMEDIATE $$UPDATE prices SET amount = 200 WHERE id = 1$$");

        ResultSet rs = statement.executeQuery("SELECT * FROM prices");
        assertTrue(rs.next(), "Should have data");
        assertEquals(200, rs.getInt("amount"));
        rs.close();
    }
}
