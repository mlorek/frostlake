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

public class CreateViewCommentPositionTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(CreateViewCommentPositionTest.class);

    @Test
    public void testViewCommentAfterSelectIsRejected() throws SQLException {
        logger.info("Testing CREATE VIEW with COMMENT after SELECT is a syntax error");

        statement.execute("CREATE TABLE products (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO products VALUES (1, 'Apple')");

        // Live-verified: the COMMENT property belongs BEFORE AS; after the query it is a syntax error.
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute("CREATE VIEW product_view AS SELECT id, name FROM products COMMENT = 'Products view'");
            }
        });

        // The before-AS position works: the view is created and listed.
        statement.execute("CREATE VIEW product_view COMMENT = 'Products view' AS SELECT id, name FROM products");
        final ResultSet shown = statement.executeQuery("SHOW VIEWS");
        assertTrue(shown.next());
        assertEquals("PRODUCT_VIEW", shown.getString("name"));
        shown.close();
    }

    @Test
    public void testViewCommentBeforeAs() throws SQLException {
        logger.info("Testing CREATE VIEW with COMMENT before AS");

        statement.execute("CREATE TABLE employees (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO employees VALUES (1, 'John')");

        statement.execute("CREATE VIEW emp_view COMMENT = 'Employee view' AS SELECT id, name FROM employees");

        // Verify view works
        final ResultSet rs = statement.executeQuery("SELECT id, name FROM emp_view");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("John", rs.getString("name"));
        assertFalse(rs.next());
    }

    @Test
    public void testViewCommentBeforeAsWithColumns() throws SQLException {
        logger.info("Testing CREATE VIEW with column names and COMMENT before AS");

        statement.execute("CREATE TABLE orders (order_id INTEGER, amount INTEGER)");
        statement.execute("INSERT INTO orders VALUES (100, 500)");

        statement.execute("CREATE VIEW order_view (id, total) COMMENT = 'Order summary' AS SELECT order_id, amount FROM orders");

        // Verify view works with renamed columns
        final ResultSet rs = statement.executeQuery("SELECT id, total FROM order_view");
        assertTrue(rs.next());
        assertEquals(100, rs.getInt("id"));
        assertEquals(500, rs.getInt("total"));
        assertFalse(rs.next());
    }

    @Test
    public void testViewCommentInBothPositionsIsRejected() throws SQLException {
        logger.info("Testing CREATE VIEW with COMMENT in both positions is a syntax error");

        statement.execute("CREATE TABLE items (id INTEGER, description VARCHAR)");
        statement.execute("INSERT INTO items VALUES (1, 'Widget')");

        // The trailing after-SELECT comment makes the whole statement a syntax error.
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute("CREATE VIEW item_view COMMENT = 'Comment before AS' AS SELECT id, description FROM items COMMENT = 'Comment after SELECT'");
            }
        });
    }

    @Test
    public void testViewNoComment() throws SQLException {
        logger.info("Testing CREATE VIEW without COMMENT");

        statement.execute("CREATE TABLE customers (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO customers VALUES (1, 'Alice')");

        statement.execute("CREATE VIEW cust_view AS SELECT id, name FROM customers");

        // Verify view works
        final ResultSet rs = statement.executeQuery("SELECT id, name FROM cust_view");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("Alice", rs.getString("name"));
        assertFalse(rs.next());
    }

    @Test
    public void testOrReplaceViewCommentBeforeAs() throws SQLException {
        logger.info("Testing CREATE OR REPLACE VIEW with COMMENT before AS");

        statement.execute("CREATE TABLE data (x INTEGER, y INTEGER)");
        statement.execute("INSERT INTO data VALUES (1, 2)");

        statement.execute("CREATE VIEW data_view COMMENT = 'First version' AS SELECT x, y FROM data");

        ResultSet rs = statement.executeQuery("SELECT x, y FROM data_view");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("x"));

        // Replace with new comment
        statement.execute("CREATE OR REPLACE VIEW data_view COMMENT = 'Second version' AS SELECT x, y FROM data");

        rs = statement.executeQuery("SELECT x, y FROM data_view");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("x"));
        assertFalse(rs.next());
    }

    @Test
    public void testMaterializedViewCommentBeforeAsCreation() throws SQLException {
        logger.info("Testing CREATE MATERIALIZED VIEW with COMMENT before AS - creation only");

        statement.execute("CREATE TABLE inventory (item VARCHAR, qty INTEGER)");
        statement.execute("INSERT INTO inventory VALUES ('Widget', 100)");

        // Just verify creation works with COMMENT before AS
        statement.execute("CREATE MATERIALIZED VIEW inv_mv COMMENT = 'Inventory snapshot' AS SELECT item, qty FROM inventory");
    }

    @Test
    public void testMaterializedViewCommentAfterSelectIsRejected() throws SQLException {
        logger.info("Testing CREATE MATERIALIZED VIEW with COMMENT after SELECT is a syntax error");

        statement.execute("CREATE TABLE sales (product VARCHAR, amount INTEGER)");
        statement.execute("INSERT INTO sales VALUES ('Gadget', 500)");

        // Live-verified: the COMMENT property belongs BEFORE AS on materialized views too.
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute("CREATE MATERIALIZED VIEW sales_mv AS SELECT product, amount FROM sales COMMENT = 'Sales snapshot'");
            }
        });
        statement.execute("CREATE MATERIALIZED VIEW sales_mv COMMENT = 'Sales snapshot' AS SELECT product, amount FROM sales");
    }

    @Test
    public void testMaterializedViewCommentBeforeAsWithColumnsCreation() throws SQLException {
        logger.info("Testing CREATE MATERIALIZED VIEW with column names and COMMENT before AS - creation only");

        statement.execute("CREATE TABLE metrics (metric_name VARCHAR, metric_value INTEGER)");
        statement.execute("INSERT INTO metrics VALUES ('cpu_usage', 75)");

        // Just verify creation works
        statement.execute("CREATE MATERIALIZED VIEW metrics_mv (name, value) COMMENT = 'Metrics view' AS SELECT metric_name, metric_value FROM metrics");
    }

    @Test
    public void testComplexViewCommentBeforeAs() throws SQLException {
        logger.info("Testing complex VIEW with joins and COMMENT before AS");

        statement.execute("CREATE TABLE users (user_id INTEGER, user_name VARCHAR)");
        statement.execute("CREATE TABLE orders_table (order_id INTEGER, user_ref INTEGER, amount INTEGER)");

        statement.execute("INSERT INTO users VALUES (1, 'Alice')");
        statement.execute("INSERT INTO orders_table VALUES (100, 1, 500)");

        statement.execute("CREATE VIEW user_orders (id, name, order_num, total) COMMENT = 'User orders summary' AS SELECT u.user_id, u.user_name, o.order_id, o.amount FROM users u JOIN orders_table o ON u.user_id = o.user_ref");

        // Verify complex view works
        final ResultSet rs = statement.executeQuery("SELECT id, name, order_num, total FROM user_orders");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("Alice", rs.getString("name"));
        assertEquals(100, rs.getInt("order_num"));
        assertEquals(500, rs.getInt("total"));
        assertFalse(rs.next());
    }
}
