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

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateViewWithColumnsTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(CreateViewWithColumnsTest.class);

    @Test
    public void testCreateViewWithColumnNames() throws SQLException {
        logger.info("Testing CREATE VIEW with explicit column names");

        statement.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        statement.execute("INSERT INTO products VALUES (1, 'Apple', 100)");
        statement.execute("INSERT INTO products VALUES (2, 'Banana', 50)");

        statement.execute("CREATE VIEW product_view (product_id, product_name, product_price) AS SELECT id, name, price FROM products");

        final ResultSet rs = statement.executeQuery("SELECT product_id, product_name, product_price FROM product_view ORDER BY product_id");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("product_id"));
        assertEquals("Apple", rs.getString("product_name"));
        assertEquals(100, rs.getInt("product_price"));

        assertTrue(rs.next());
        assertEquals(2, rs.getInt("product_id"));
        assertEquals("Banana", rs.getString("product_name"));
        assertEquals(50, rs.getInt("product_price"));

        assertFalse(rs.next());
    }

    @Test
    public void testCreateViewWithoutColumnNames() throws SQLException {
        logger.info("Testing CREATE VIEW without explicit column names (original behavior)");

        statement.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary INTEGER)");
        statement.execute("INSERT INTO employees VALUES (1, 'John', 50000)");

        statement.execute("CREATE VIEW emp_view AS SELECT id, name, salary FROM employees");

        final ResultSet rs = statement.executeQuery("SELECT id, name, salary FROM emp_view");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("John", rs.getString("name"));
        assertEquals(50000, rs.getInt("salary"));
        assertFalse(rs.next());
    }

    @Test
    public void testViewColumnCountMismatch() throws SQLException {
        logger.info("Testing CREATE VIEW with mismatched column count - error at create");

        statement.execute("CREATE TABLE items (a INTEGER, b INTEGER, c INTEGER)");
        statement.execute("INSERT INTO items VALUES (1, 2, 3)");

        // 2 column names but 3 columns in SELECT - live-verified: the CREATE itself fails
        final SQLException exception = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute("CREATE VIEW bad_view (col1, col2) AS SELECT a, b, c FROM items");
            }
        });
        assertTrue(exception.getMessage().contains("Invalid column definition list"),
            "unexpected message: " + exception.getMessage());
    }

    @Test
    public void testViewWithRenamedColumns() throws SQLException {
        logger.info("Testing VIEW renames columns properly");

        statement.execute("CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, amount INTEGER)");
        statement.execute("INSERT INTO orders VALUES (100, 1, 250)");
        statement.execute("INSERT INTO orders VALUES (101, 2, 300)");

        statement.execute("CREATE VIEW order_summary (id, cust, total) AS SELECT order_id, customer_id, amount FROM orders");

        final ResultSet rs = statement.executeQuery("SELECT id, cust, total FROM order_summary WHERE total > 200 ORDER BY id");
        assertTrue(rs.next());
        assertEquals(100, rs.getInt("id"));
        assertEquals(1, rs.getInt("cust"));
        assertEquals(250, rs.getInt("total"));

        assertTrue(rs.next());
        assertEquals(101, rs.getInt("id"));
        assertEquals(2, rs.getInt("cust"));
        assertEquals(300, rs.getInt("total"));

        assertFalse(rs.next());
    }

    @Test
    public void testViewWithComputedColumns() throws SQLException {
        logger.info("Testing VIEW with computed columns and explicit names");

        statement.execute("CREATE TABLE numbers (a INTEGER, b INTEGER)");
        statement.execute("INSERT INTO numbers VALUES (10, 5)");
        statement.execute("INSERT INTO numbers VALUES (20, 8)");

        statement.execute("CREATE VIEW calculations (sum_col, diff_col, prod_col) AS SELECT a + b, a - b, a * b FROM numbers");

        final ResultSet rs = statement.executeQuery("SELECT sum_col, diff_col, prod_col FROM calculations ORDER BY sum_col");
        assertTrue(rs.next());
        assertEquals(15, rs.getInt("sum_col"));
        assertEquals(5, rs.getInt("diff_col"));
        assertEquals(50, rs.getInt("prod_col"));

        assertTrue(rs.next());
        assertEquals(28, rs.getInt("sum_col"));
        assertEquals(12, rs.getInt("diff_col"));
        assertEquals(160, rs.getInt("prod_col"));

        assertFalse(rs.next());
    }

    @Test
    public void testViewWithAggregateAndColumnNames() throws SQLException {
        logger.info("Testing VIEW with aggregate functions and explicit column names");

        statement.execute("CREATE TABLE sales (product VARCHAR, quantity INTEGER, revenue INTEGER)");
        statement.execute("INSERT INTO sales VALUES ('Apple', 10, 100)");
        statement.execute("INSERT INTO sales VALUES ('Apple', 5, 50)");
        statement.execute("INSERT INTO sales VALUES ('Banana', 20, 80)");

        statement.execute("CREATE VIEW sales_summary (product_name, total_qty, total_rev) AS SELECT product, SUM(quantity), SUM(revenue) FROM sales GROUP BY product");

        final ResultSet rs = statement.executeQuery("SELECT product_name, total_qty, total_rev FROM sales_summary ORDER BY product_name");
        assertTrue(rs.next());
        assertEquals("Apple", rs.getString("product_name"));
        assertEquals(15.0, rs.getDouble("total_qty"), 0.001);
        assertEquals(150.0, rs.getDouble("total_rev"), 0.001);

        assertTrue(rs.next());
        assertEquals("Banana", rs.getString("product_name"));
        assertEquals(20.0, rs.getDouble("total_qty"), 0.001);
        assertEquals(80.0, rs.getDouble("total_rev"), 0.001);

        assertFalse(rs.next());
    }

    @Test
    public void testViewInJoin() throws SQLException {
        logger.info("Testing VIEW with explicit column names in JOIN");

        statement.execute("CREATE TABLE customers (id INTEGER, name VARCHAR)");
        statement.execute("CREATE TABLE orders_table (order_id INTEGER, cust_id INTEGER, amount INTEGER)");

        statement.execute("INSERT INTO customers VALUES (1, 'Alice')");
        statement.execute("INSERT INTO customers VALUES (2, 'Bob')");

        statement.execute("INSERT INTO orders_table VALUES (100, 1, 500)");
        statement.execute("INSERT INTO orders_table VALUES (101, 2, 300)");

        statement.execute("CREATE VIEW order_view (oid, customer, amt) AS SELECT order_id, cust_id, amount FROM orders_table");

        final ResultSet rs = statement.executeQuery(
            "SELECT c.name, ov.oid, ov.amt FROM customers c JOIN order_view ov ON c.id = ov.customer ORDER BY ov.oid"
        );

        assertTrue(rs.next());
        assertEquals("Alice", rs.getString("name"));
        assertEquals(100, rs.getInt("oid"));
        assertEquals(500, rs.getInt("amt"));

        assertTrue(rs.next());
        assertEquals("Bob", rs.getString("name"));
        assertEquals(101, rs.getInt("oid"));
        assertEquals(300, rs.getInt("amt"));

        assertFalse(rs.next());
    }

    @Test
    public void testOrReplaceViewWithColumnNames() throws SQLException {
        logger.info("Testing CREATE OR REPLACE VIEW with column names");

        statement.execute("CREATE TABLE data (x INTEGER, y INTEGER)");
        statement.execute("INSERT INTO data VALUES (1, 2)");

        statement.execute("CREATE VIEW data_view (col1, col2) AS SELECT x, y FROM data");

        ResultSet rs = statement.executeQuery("SELECT col1, col2 FROM data_view");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("col1"));
        assertEquals(2, rs.getInt("col2"));

        // Replace with different column names
        statement.execute("CREATE OR REPLACE VIEW data_view (alpha, beta) AS SELECT x, y FROM data");

        rs = statement.executeQuery("SELECT alpha, beta FROM data_view");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("alpha"));
        assertEquals(2, rs.getInt("beta"));
        assertFalse(rs.next());
    }

    @Test
    public void testViewWithWhereClause() throws SQLException {
        logger.info("Testing VIEW with WHERE clause and explicit column names");

        statement.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        statement.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
        statement.execute("INSERT INTO users VALUES (2, 'Bob', 25)");
        statement.execute("INSERT INTO users VALUES (3, 'Charlie', 35)");

        statement.execute("CREATE VIEW adult_users (user_id, user_name) AS SELECT id, name FROM users WHERE age >= 30");

        final ResultSet rs = statement.executeQuery("SELECT user_id, user_name FROM adult_users ORDER BY user_id");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("user_id"));
        assertEquals("Alice", rs.getString("user_name"));

        assertTrue(rs.next());
        assertEquals(3, rs.getInt("user_id"));
        assertEquals("Charlie", rs.getString("user_name"));

        assertFalse(rs.next());
    }

    @Test
    public void testViewWithStarAndColumnNames() throws SQLException {
        logger.info("Testing VIEW with SELECT * and explicit column names");

        statement.execute("CREATE TABLE simple (a INTEGER, b INTEGER)");
        statement.execute("INSERT INTO simple VALUES (1, 2)");

        statement.execute("CREATE VIEW simple_view (col_a, col_b) AS SELECT * FROM simple");

        final ResultSet rs = statement.executeQuery("SELECT col_a, col_b FROM simple_view");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("col_a"));
        assertEquals(2, rs.getInt("col_b"));
        assertFalse(rs.next());
    }
}
