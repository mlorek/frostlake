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

package dev.frostlake.examples;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewCommentPositionExample {
    private static final Logger logger = LoggerFactory.getLogger(ViewCommentPositionExample.class);

    @Test
    public void demonstrateViewCommentPositions() {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== CREATE VIEW with COMMENT in Different Positions ===");

            // Example 1: COMMENT after SELECT (traditional position)
            logger.info("\n1. COMMENT after SELECT statement:");
            engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
            engine.execute("INSERT INTO products VALUES (1, 'Laptop', 1200)");
            engine.execute("INSERT INTO products VALUES (2, 'Mouse', 25)");

            engine.execute("CREATE VIEW products_view AS SELECT id, name, price FROM products COMMENT = 'All products catalog'");

            ResultSet rs1 = engine.executeQuery("SELECT id, name, price FROM products_view ORDER BY id");
            logger.info("Products view (COMMENT after SELECT):");
            for (int i = 0; i < rs1.getRowCount(); i++) {
                logger.info("  {}: {} - ${}",
                    rs1.getRows().get(i).getValue(0),
                    rs1.getRows().get(i).getValue(1),
                    rs1.getRows().get(i).getValue(2)
                );
            }

            // Example 2: COMMENT before AS (new position)
            logger.info("\n2. COMMENT before AS keyword:");
            engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, dept VARCHAR)");
            engine.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering')");
            engine.execute("INSERT INTO employees VALUES (2, 'Bob', 'Sales')");

            engine.execute("CREATE VIEW employees_view COMMENT = 'Active employees list' AS SELECT id, name, dept FROM employees");

            ResultSet rs2 = engine.executeQuery("SELECT id, name, dept FROM employees_view ORDER BY id");
            logger.info("Employees view (COMMENT before AS):");
            for (int i = 0; i < rs2.getRowCount(); i++) {
                logger.info("  {}: {} - {}",
                    rs2.getRows().get(i).getValue(0),
                    rs2.getRows().get(i).getValue(1),
                    rs2.getRows().get(i).getValue(2)
                );
            }

            // Example 3: COMMENT before AS with explicit column names
            logger.info("\n3. COMMENT before AS with column names:");
            engine.execute("CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, total INTEGER)");
            engine.execute("INSERT INTO orders VALUES (100, 1, 500)");
            engine.execute("INSERT INTO orders VALUES (101, 2, 750)");

            engine.execute("CREATE VIEW order_summary (id, customer, amount) COMMENT = 'Order details summary' AS SELECT order_id, customer_id, total FROM orders");

            ResultSet rs3 = engine.executeQuery("SELECT id, customer, amount FROM order_summary WHERE amount > 600");
            logger.info("Order summary (COMMENT before AS with columns):");
            for (int i = 0; i < rs3.getRowCount(); i++) {
                logger.info("  Order {}: Customer {} - ${}",
                    rs3.getRows().get(i).getValue(0),
                    rs3.getRows().get(i).getValue(1),
                    rs3.getRows().get(i).getValue(2)
                );
            }

            // Example 4: No comment
            logger.info("\n4. View without COMMENT:");
            engine.execute("CREATE TABLE todo_items (id INTEGER, description VARCHAR, completed INTEGER)");
            engine.execute("INSERT INTO todo_items VALUES (1, 'Write report', 1)");
            engine.execute("INSERT INTO todo_items VALUES (2, 'Review code', 0)");

            engine.execute("CREATE VIEW pending_items AS SELECT id, description FROM todo_items WHERE completed = 0");

            ResultSet rs4 = engine.executeQuery("SELECT id, description FROM pending_items");
            logger.info("Pending tasks (no COMMENT):");
            for (int i = 0; i < rs4.getRowCount(); i++) {
                logger.info("  Task {}: {}",
                    rs4.getRows().get(i).getValue(0),
                    rs4.getRows().get(i).getValue(1)
                );
            }

            // Example 5: CREATE OR REPLACE with COMMENT before AS
            logger.info("\n5. CREATE OR REPLACE VIEW with COMMENT:");
            engine.execute("CREATE TABLE inventory (item VARCHAR, quantity INTEGER)");
            engine.execute("INSERT INTO inventory VALUES ('Widget', 100)");
            engine.execute("INSERT INTO inventory VALUES ('Gadget', 50)");

            engine.execute("CREATE VIEW inventory_view COMMENT = 'Current inventory levels' AS SELECT item, quantity FROM inventory");

            ResultSet rs5a = engine.executeQuery("SELECT item, quantity FROM inventory_view");
            logger.info("Original inventory view:");
            for (int i = 0; i < rs5a.getRowCount(); i++) {
                logger.info("  {}: {}",
                    rs5a.getRows().get(i).getValue(0),
                    rs5a.getRows().get(i).getValue(1)
                );
            }

            // Replace with different comment
            engine.execute("CREATE OR REPLACE VIEW inventory_view COMMENT = 'Updated inventory snapshot' AS SELECT item, quantity FROM inventory WHERE quantity > 60");

            ResultSet rs5b = engine.executeQuery("SELECT item, quantity FROM inventory_view");
            logger.info("Replaced inventory view (filtered):");
            for (int i = 0; i < rs5b.getRowCount(); i++) {
                logger.info("  {}: {}",
                    rs5b.getRows().get(i).getValue(0),
                    rs5b.getRows().get(i).getValue(1)
                );
            }

            // Example 6: Complex view with aggregation and COMMENT before AS
            logger.info("\n6. Complex aggregation view with COMMENT:");
            engine.execute("CREATE TABLE sales (region VARCHAR, product VARCHAR, revenue INTEGER)");
            engine.execute("INSERT INTO sales VALUES ('North', 'Widget', 1000)");
            engine.execute("INSERT INTO sales VALUES ('North', 'Gadget', 1500)");
            engine.execute("INSERT INTO sales VALUES ('South', 'Widget', 800)");
            engine.execute("INSERT INTO sales VALUES ('South', 'Gadget', 1200)");

            engine.execute("CREATE VIEW regional_revenue (region, total) COMMENT = 'Revenue by region summary' AS SELECT region, SUM(revenue) FROM sales GROUP BY region");

            ResultSet rs6 = engine.executeQuery("SELECT region, total FROM regional_revenue ORDER BY region");
            logger.info("Regional revenue (COMMENT before AS):");
            for (int i = 0; i < rs6.getRowCount(); i++) {
                logger.info("  {}: ${}",
                    rs6.getRows().get(i).getValue(0),
                    rs6.getRows().get(i).getValue(1)
                );
            }

            logger.info("\n=== Summary ===");
            logger.info("COMMENT clause can be placed:");
            logger.info("  1. After SELECT statement: ... AS SELECT ... COMMENT = 'description'");
            logger.info("  2. Before AS keyword: ... COMMENT = 'description' AS SELECT ...");
            logger.info("  3. Both positions work with column names and OR REPLACE");

        } finally {
            engine.shutdown();
        }
    }
}
