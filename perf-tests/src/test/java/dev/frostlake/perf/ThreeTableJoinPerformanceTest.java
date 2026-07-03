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

package dev.frostlake.perf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("perf")
public class ThreeTableJoinPerformanceTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(ThreeTableJoinPerformanceTest.class);

    private static final int ROWS_PER_TABLE = 10_000;
    private static final int BATCH_SIZE = 1000;

    @Override
    protected void setupTest() {
        logger.info("Setting up performance test with " + ROWS_PER_TABLE + " rows per table...");
        setupTables();
        logger.info("Setup complete.");
    }

    private void setupTables() {
        long startTime = System.currentTimeMillis();

        engine.execute("""
            CREATE TABLE customers (
                customer_id INTEGER,
                name VARCHAR,
                email VARCHAR,
                phone VARCHAR,
                address VARCHAR,
                city VARCHAR,
                state VARCHAR,
                zipcode VARCHAR,
                country VARCHAR,
                registration_date VARCHAR
            )
            """);

        engine.execute("""
            CREATE TABLE orders (
                order_id INTEGER,
                customer_id INTEGER,
                order_date VARCHAR,
                total_amount NUMBER,
                status VARCHAR,
                payment_method VARCHAR,
                shipping_address VARCHAR,
                tracking_number VARCHAR,
                notes VARCHAR,
                processed_by VARCHAR
            )
            """);

        engine.execute("""
            CREATE TABLE order_details (
                detail_id INTEGER,
                order_id INTEGER,
                product_name VARCHAR,
                quantity INTEGER,
                unit_price NUMBER,
                discount NUMBER,
                tax_amount NUMBER,
                subtotal NUMBER,
                category VARCHAR,
                supplier VARCHAR
            )
            """);

        long createTime = System.currentTimeMillis() - startTime;
        logger.info("Tables created in " + createTime + " ms");

        insertCustomers();
        insertOrders();
        insertOrderDetails();

        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("Data insertion completed in " + totalTime + " ms");
    }

    private void insertCustomers() {
        long startTime = System.currentTimeMillis();
        logger.info("Inserting " + ROWS_PER_TABLE + " customers...");

        for (int batch = 0; batch < ROWS_PER_TABLE / BATCH_SIZE; batch++) {
            StringBuilder sql = new StringBuilder("INSERT INTO customers VALUES ");
            for (int i = 0; i < BATCH_SIZE; i++) {
                int id = batch * BATCH_SIZE + i + 1;
                if (i > 0) sql.append(", ");
                sql.append(String.format(
                    "(%d, 'Customer_%d', 'customer%d@example.com', '555-%04d', " +
                    "'%d Main St', 'City_%d', 'ST', '%05d', 'USA', '2024-01-%02d')",
                    id, id, id, id % 10000, id,
                    id % 100, id % 100000, (id % 28) + 1
                ));
            }
            engine.execute(sql.toString());

            if ((batch + 1) % 10 == 0) {
                logger.info("  Inserted " + ((batch + 1) * BATCH_SIZE) + " customers...");
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Customers inserted in " + duration + " ms");
    }

    private void insertOrders() {
        long startTime = System.currentTimeMillis();
        logger.info("Inserting " + ROWS_PER_TABLE + " orders...");

        for (int batch = 0; batch < ROWS_PER_TABLE / BATCH_SIZE; batch++) {
            StringBuilder sql = new StringBuilder("INSERT INTO orders VALUES ");
            for (int i = 0; i < BATCH_SIZE; i++) {
                int id = batch * BATCH_SIZE + i + 1;
                if (i > 0) sql.append(", ");
                sql.append(String.format(
                    "(%d, %d, '2024-%02d-%02d', %d.%02d, '%s', '%s', " +
                    "'%d Main St', 'TRK%d', 'Note_%d', 'Agent_%d')",
                    id, id, (id % 12) + 1, (id % 28) + 1,
                    (id % 1000) + 100, id % 100,
                    id % 2 == 0 ? "COMPLETED" : "PENDING",
                    id % 3 == 0 ? "CREDIT_CARD" : "PAYPAL",
                    id, id, id, id % 50
                ));
            }
            engine.execute(sql.toString());

            if ((batch + 1) % 10 == 0) {
                logger.info("  Inserted " + ((batch + 1) * BATCH_SIZE) + " orders...");
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Orders inserted in " + duration + " ms");
    }

    private void insertOrderDetails() {
        long startTime = System.currentTimeMillis();
        logger.info("Inserting " + ROWS_PER_TABLE + " order details...");

        for (int batch = 0; batch < ROWS_PER_TABLE / BATCH_SIZE; batch++) {
            StringBuilder sql = new StringBuilder("INSERT INTO order_details VALUES ");
            for (int i = 0; i < BATCH_SIZE; i++) {
                int id = batch * BATCH_SIZE + i + 1;
                if (i > 0) sql.append(", ");
                sql.append(String.format(
                    "(%d, %d, 'Product_%d', %d, %d.%02d, %d.%02d, %d.%02d, %d.%02d, '%s', 'Supplier_%d')",
                    id, id, id % 500, (id % 10) + 1,
                    (id % 100) + 10, id % 100,
                    id % 20, id % 100,
                    (id % 15) + 5, id % 100,
                    ((id % 100) + 10) * ((id % 10) + 1), id % 100,
                    id % 3 == 0 ? "Electronics" : id % 3 == 1 ? "Clothing" : "Home",
                    id % 100
                ));
            }
            engine.execute(sql.toString());

            if ((batch + 1) % 10 == 0) {
                logger.info("  Inserted " + ((batch + 1) * BATCH_SIZE) + " order details...");
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Order details inserted in " + duration + " ms");
    }

    @Test
    public void testThreeTableInnerJoinPerformance() {
        logger.info("\n=== Testing 3-table INNER JOIN performance ===");

        String query = """
            SELECT
                c.customer_id,
                c.name,
                c.email,
                c.city,
                o.order_id,
                o.order_date,
                o.total_amount,
                o.status,
                od.detail_id,
                od.product_name,
                od.quantity,
                od.unit_price
            FROM customers c
            INNER JOIN orders o ON c.customer_id = o.customer_id
            INNER JOIN order_details od ON o.order_id = od.order_id
            """;

        long startTime = System.currentTimeMillis();
        ResultSet result = engine.executeQuery(query);
        long duration = System.currentTimeMillis() - startTime;

        logger.info("Query executed in " + duration + " ms");
        logger.info("Rows returned: " + result.getRowCount());
        logger.info("Columns: " + result.getColumnCount());

        assertEquals(ROWS_PER_TABLE, result.getRowCount(),
            "Should return " + ROWS_PER_TABLE + " rows (1-to-1-to-1 relationship)");
        assertEquals(12, result.getColumnCount(), "Should have 12 columns");

        assertTrue(duration < 30000, "Query should complete in under 30 seconds");

        logger.info("Performance: " + String.format("%.2f", ROWS_PER_TABLE / (duration / 1000.0)) + " rows/sec");
    }

    @Test
    public void testThreeTableJoinWithFilter() {
        logger.info("\n=== Testing 3-table JOIN with WHERE clause ===");

        String query = """
            SELECT
                c.customer_id,
                c.name,
                o.order_id,
                o.total_amount,
                od.product_name,
                od.quantity
            FROM customers c
            INNER JOIN orders o ON c.customer_id = o.customer_id
            INNER JOIN order_details od ON o.order_id = od.order_id
            WHERE o.status = 'COMPLETED'
            AND od.quantity > 5
            """;

        long startTime = System.currentTimeMillis();
        ResultSet result = engine.executeQuery(query);
        long duration = System.currentTimeMillis() - startTime;

        logger.info("Query executed in " + duration + " ms");
        logger.info("Rows returned: " + result.getRowCount());

        assertTrue(result.getRowCount() > 0, "Should return some rows");
        assertTrue(result.getRowCount() < ROWS_PER_TABLE, "Should return fewer rows than total");
        assertTrue(duration < 30000, "Query with filter should complete in under 30 seconds");

        logger.info("Performance: " + String.format("%.2f", result.getRowCount() / (duration / 1000.0)) + " rows/sec");
    }

    @Test
    public void testThreeTableJoinWithAggregation() {
        logger.info("\n=== Testing 3-table JOIN with aggregation ===");

        String query = """
            SELECT
                c.city,
                COUNT(*) as order_count,
                SUM(od.quantity) as total_quantity,
                AVG(o.total_amount) as avg_order_amount
            FROM customers c
            INNER JOIN orders o ON c.customer_id = o.customer_id
            INNER JOIN order_details od ON o.order_id = od.order_id
            GROUP BY c.city
            """;

        long startTime = System.currentTimeMillis();
        ResultSet result = engine.executeQuery(query);
        long duration = System.currentTimeMillis() - startTime;

        logger.info("Query executed in " + duration + " ms");
        logger.info("Groups returned: " + result.getRowCount());

        assertTrue(result.getRowCount() > 0, "Should return some groups");
        assertTrue(result.getRowCount() <= 100, "Should have at most 100 cities");
        assertTrue(duration < 30000, "Aggregation query should complete in under 30 seconds");

        logger.info("Performance: Processed " + ROWS_PER_TABLE + " rows in " + duration + " ms");
    }

    @Test
    public void testThreeTableLeftJoinPerformance() {
        logger.info("\n=== Testing 3-table LEFT JOIN performance ===");

        String query = """
            SELECT
                c.customer_id,
                c.name,
                o.order_id,
                od.product_name
            FROM customers c
            LEFT JOIN orders o ON c.customer_id = o.customer_id
            LEFT JOIN order_details od ON o.order_id = od.order_id
            """;

        long startTime = System.currentTimeMillis();
        ResultSet result = engine.executeQuery(query);
        long duration = System.currentTimeMillis() - startTime;

        logger.info("Query executed in " + duration + " ms");
        logger.info("Rows returned: " + result.getRowCount());

        assertEquals(ROWS_PER_TABLE, result.getRowCount(),
            "Should return " + ROWS_PER_TABLE + " rows (all customers with their orders)");
        assertTrue(duration < 30000, "LEFT JOIN query should complete in under 30 seconds");

        logger.info("Performance: " + String.format("%.2f", ROWS_PER_TABLE / (duration / 1000.0)) + " rows/sec");
    }

    @Test
    public void testThreeTableJoinSelectivity() {
        logger.info("\n=== Testing 3-table JOIN with high selectivity ===");

        String query = """
            SELECT
                c.customer_id,
                c.name,
                o.order_id,
                od.product_name
            FROM customers c
            INNER JOIN orders o ON c.customer_id = o.customer_id
            INNER JOIN order_details od ON o.order_id = od.order_id
            WHERE c.customer_id < 100
            """;

        long startTime = System.currentTimeMillis();
        ResultSet result = engine.executeQuery(query);
        long duration = System.currentTimeMillis() - startTime;

        logger.info("Query executed in " + duration + " ms");
        logger.info("Rows returned: " + result.getRowCount());

        assertEquals(99, result.getRowCount(), "Should return 99 rows (customer_id 1-99)");
        assertTrue(duration < 30000, "Highly selective query should complete in under 30 seconds");

        logger.info("Performance: Processed in " + duration + " ms");
    }
}
