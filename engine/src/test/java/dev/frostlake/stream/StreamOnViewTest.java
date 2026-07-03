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

package dev.frostlake.stream;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StreamOnViewTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(StreamOnViewTest.class);

    @Test
    public void testCreateStreamOnView() throws SQLException {
        logger.info("Testing CREATE STREAM on VIEW");

        statement.execute("CREATE TABLE users (id INTEGER, name STRING, age INTEGER)");
        statement.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
        statement.execute("INSERT INTO users VALUES (2, 'Bob', 25)");

        statement.execute("CREATE VIEW active_users AS SELECT id, name FROM users WHERE age >= 25");
        statement.execute("CREATE STREAM user_stream ON VIEW active_users");

        ResultSet rs = statement.executeQuery("SHOW STREAMS");
        boolean found = false;
        while (rs.next()) {
            String name = rs.getString("name");
            if ("USER_STREAM".equals(name)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Stream should be created on view");
    }

    @Test
    public void testCreateStreamOnViewWithAppendOnly() throws SQLException {
        logger.info("Testing CREATE STREAM on VIEW with APPEND_ONLY");

        statement.execute("CREATE TABLE products (id INTEGER, name STRING, price DECIMAL)");
        statement.execute("CREATE VIEW expensive_products AS SELECT * FROM products WHERE price > 100");
        statement.execute("CREATE STREAM product_stream ON VIEW expensive_products APPEND_ONLY = TRUE");

        ResultSet rs = statement.executeQuery("SHOW STREAMS");
        boolean found = false;
        while (rs.next()) {
            String name = rs.getString("name");
            if ("PRODUCT_STREAM".equals(name)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Stream with APPEND_ONLY should be created on view");
    }

    @Test
    public void testCreateStreamOnViewWithShowInitialRows() throws SQLException {
        logger.info("Testing CREATE STREAM on VIEW with SHOW_INITIAL_ROWS");

        statement.execute("CREATE TABLE orders (id INTEGER, customer_id INTEGER, amount DECIMAL)");
        statement.execute("INSERT INTO orders VALUES (1, 100, 50.00)");
        statement.execute("INSERT INTO orders VALUES (2, 101, 75.00)");

        statement.execute("CREATE VIEW large_orders AS SELECT * FROM orders WHERE amount > 60");
        statement.execute("CREATE STREAM order_stream ON VIEW large_orders SHOW_INITIAL_ROWS = TRUE");

        ResultSet rs = statement.executeQuery("SHOW STREAMS");
        boolean found = false;
        while (rs.next()) {
            String name = rs.getString("name");
            if ("ORDER_STREAM".equals(name)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Stream with SHOW_INITIAL_ROWS should be created on view");
    }

    @Test
    public void testCreateStreamOnNonExistentView() {
        logger.info("Testing CREATE STREAM on non-existent VIEW");

        SQLException exception = assertThrows(SQLException.class, () -> {
            statement.execute("CREATE STREAM test_stream ON VIEW non_existent_view");
        });
        assertTrue(exception.getMessage().contains("View does not exist"),
                   "Should fail when view doesn't exist");
    }

    @Test
    public void testCreateStreamOnTableAndView() throws SQLException {
        logger.info("Testing CREATE STREAM on both TABLE and VIEW in same schema");

        statement.execute("CREATE TABLE employees (id INTEGER, name STRING, salary DECIMAL)");
        statement.execute("INSERT INTO employees VALUES (1, 'John', 50000)");
        statement.execute("INSERT INTO employees VALUES (2, 'Jane', 60000)");

        statement.execute("CREATE VIEW high_earners AS SELECT * FROM employees WHERE salary > 55000");

        statement.execute("CREATE STREAM table_stream ON TABLE employees");
        statement.execute("CREATE STREAM view_stream ON VIEW high_earners");

        ResultSet rs = statement.executeQuery("SHOW STREAMS");
        int count = 0;
        while (rs.next()) {
            String name = rs.getString("name");
            if ("TABLE_STREAM".equals(name) || "VIEW_STREAM".equals(name)) {
                count++;
            }
        }
        assertEquals(2, count, "Should have both table and view streams");
    }

    @Test
    public void testStreamOnViewWithComplexView() throws SQLException {
        logger.info("Testing CREATE STREAM on complex VIEW");

        statement.execute("CREATE TABLE inventory (id INTEGER, product_name STRING, quantity INTEGER)");
        statement.execute("INSERT INTO inventory VALUES (1, 'Widget', 100)");
        statement.execute("INSERT INTO inventory VALUES (2, 'Gadget', 5)");

        statement.execute("CREATE VIEW low_stock AS SELECT * FROM inventory WHERE quantity < 10");
        statement.execute("CREATE STREAM stock_stream ON VIEW low_stock");

        ResultSet rs = statement.executeQuery("SHOW STREAMS");
        boolean found = false;
        while (rs.next()) {
            String name = rs.getString("name");
            if ("STOCK_STREAM".equals(name)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Stream should be created on view with WHERE clause");
    }

    @Test
    public void testStreamOnViewIfNotExists() throws SQLException {
        logger.info("Testing CREATE STREAM IF NOT EXISTS on VIEW");

        statement.execute("CREATE TABLE customers (id INTEGER, name STRING)");
        statement.execute("CREATE VIEW customer_view AS SELECT * FROM customers");
        statement.execute("CREATE STREAM customer_stream ON VIEW customer_view");

        // Should not throw error with IF NOT EXISTS
        statement.execute("CREATE STREAM IF NOT EXISTS customer_stream ON VIEW customer_view");

        ResultSet rs = statement.executeQuery("SHOW STREAMS");
        int count = 0;
        while (rs.next()) {
            String name = rs.getString("name");
            if ("CUSTOMER_STREAM".equals(name)) {
                count++;
            }
        }
        assertEquals(1, count, "Should still have only one stream");
    }
}
