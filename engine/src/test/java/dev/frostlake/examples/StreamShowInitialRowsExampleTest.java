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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamShowInitialRowsExampleTest {
    private static final Logger logger = LoggerFactory.getLogger(StreamShowInitialRowsExampleTest.class);

    @Test
    public void demonstrateShowInitialRows() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== STREAM with SHOW_INITIAL_ROWS Option ===");

            // Example 1: Basic SHOW_INITIAL_ROWS usage
            logger.info("\n1. CREATE STREAM with SHOW_INITIAL_ROWS = TRUE:");
            engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR, email VARCHAR)");
            engine.execute("INSERT INTO customers VALUES (1, 'Alice', 'alice@example.com')");
            engine.execute("INSERT INTO customers VALUES (2, 'Bob', 'bob@example.com')");

            engine.execute("CREATE STREAM customers_stream ON TABLE customers SHOW_INITIAL_ROWS = TRUE");
            logger.info("Created stream: customers_stream with SHOW_INITIAL_ROWS = TRUE");
            logger.info("When SHOW_INITIAL_ROWS = TRUE:");
            logger.info("  - Stream includes existing rows from the source table");
            logger.info("  - Useful for initial data population in CDC pipelines");
            logger.info("  - Existing rows appear as INSERT operations");

            // Example 2: SHOW_INITIAL_ROWS = FALSE (default behavior)
            logger.info("\n2. CREATE STREAM with SHOW_INITIAL_ROWS = FALSE:");
            engine.execute("CREATE TABLE orders (id INTEGER, customer_id INTEGER, amount INTEGER)");
            engine.execute("INSERT INTO orders VALUES (1, 1, 500)");
            engine.execute("INSERT INTO orders VALUES (2, 2, 750)");

            engine.execute("CREATE STREAM orders_stream ON TABLE orders SHOW_INITIAL_ROWS = FALSE");
            logger.info("Created stream: orders_stream with SHOW_INITIAL_ROWS = FALSE");
            logger.info("When SHOW_INITIAL_ROWS = FALSE:");
            logger.info("  - Stream only tracks changes AFTER stream creation");
            logger.info("  - Existing rows are NOT included");
            logger.info("  - Default Snowflake behavior");

            // Example 3: Combining with APPEND_ONLY
            logger.info("\n3. Combining SHOW_INITIAL_ROWS with APPEND_ONLY:");
            engine.execute("CREATE TABLE events (id INTEGER, event_type VARCHAR, timestamp VARCHAR)");
            engine.execute("INSERT INTO events VALUES (1, 'LOGIN', '2024-01-01')");
            engine.execute("INSERT INTO events VALUES (2, 'LOGOUT', '2024-01-02')");

            engine.execute("CREATE STREAM events_stream ON TABLE events APPEND_ONLY = TRUE SHOW_INITIAL_ROWS = TRUE");
            logger.info("Created stream: events_stream with both options");
            logger.info("Combined options:");
            logger.info("  APPEND_ONLY = TRUE: Only captures INSERT operations");
            logger.info("  SHOW_INITIAL_ROWS = TRUE: Includes existing rows as INSERTs");
            logger.info("  Use case: Event logs, audit trails, append-only data");

            // Example 4: Standard stream with SHOW_INITIAL_ROWS
            logger.info("\n4. Standard stream (captures all DML) with initial rows:");
            engine.execute("CREATE TABLE inventory (id INTEGER, item VARCHAR, quantity INTEGER)");
            engine.execute("INSERT INTO inventory VALUES (1, 'Widget', 100)");
            engine.execute("INSERT INTO inventory VALUES (2, 'Gadget', 50)");

            engine.execute("CREATE STREAM inventory_stream ON TABLE inventory APPEND_ONLY = FALSE SHOW_INITIAL_ROWS = TRUE");
            logger.info("Created stream: inventory_stream (standard + initial rows)");
            logger.info("Standard stream with initial rows:");
            logger.info("  - Captures INSERT, UPDATE, DELETE operations");
            logger.info("  - Includes existing rows at creation time");
            logger.info("  - Full CDC with historical snapshot");

            // Example 5: Multiple streams on same table
            logger.info("\n5. Multiple streams on same table with different options:");
            engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
            engine.execute("INSERT INTO products VALUES (1, 'Laptop', 1200)");
            engine.execute("INSERT INTO products VALUES (2, 'Mouse', 25)");

            engine.execute("CREATE STREAM products_full ON TABLE products SHOW_INITIAL_ROWS = TRUE");
            logger.info("Created: products_full (includes initial rows)");

            engine.execute("CREATE STREAM products_changes ON TABLE products SHOW_INITIAL_ROWS = FALSE");
            logger.info("Created: products_changes (only future changes)");

            logger.info("Use case:");
            logger.info("  - products_full: For initial data load + CDC");
            logger.info("  - products_changes: For ongoing CDC only");

            // Example 6: Schema-qualified stream
            logger.info("\n6. Schema-qualified stream with SHOW_INITIAL_ROWS:");
            engine.execute("CREATE SCHEMA analytics");
            engine.execute("CREATE TABLE analytics.metrics (id INTEGER, metric_name VARCHAR, value INTEGER)");
            engine.execute("INSERT INTO analytics.metrics VALUES (1, 'cpu_usage', 75)");

            engine.execute("CREATE STREAM analytics.metrics_stream ON TABLE analytics.metrics SHOW_INITIAL_ROWS = TRUE");
            logger.info("Created: analytics.metrics_stream");
            logger.info("Schema-qualified streams work with SHOW_INITIAL_ROWS option");

            // Example 7: With COMMENT clause
            logger.info("\n7. Stream with SHOW_INITIAL_ROWS and COMMENT:");
            engine.execute("CREATE TABLE transactions (id INTEGER, amount INTEGER, status VARCHAR)");
            engine.execute("INSERT INTO transactions VALUES (1, 1000, 'COMPLETED')");

            engine.execute("CREATE STREAM transactions_stream ON TABLE transactions SHOW_INITIAL_ROWS = TRUE COMMENT = 'CDC stream with initial snapshot'");
            logger.info("Created: transactions_stream with comment");
            logger.info("All stream options can be combined with COMMENT clause");

            // Example 8: IF NOT EXISTS with SHOW_INITIAL_ROWS
            logger.info("\n8. IF NOT EXISTS with SHOW_INITIAL_ROWS:");
            engine.execute("CREATE TABLE logs (id INTEGER, message VARCHAR)");
            engine.execute("INSERT INTO logs VALUES (1, 'System started')");

            engine.execute("CREATE STREAM IF NOT EXISTS logs_stream ON TABLE logs SHOW_INITIAL_ROWS = TRUE");
            logger.info("Created: logs_stream with IF NOT EXISTS");

            // Try to create again - should not fail
            engine.execute("CREATE STREAM IF NOT EXISTS logs_stream ON TABLE logs SHOW_INITIAL_ROWS = TRUE");
            logger.info("Second CREATE with IF NOT EXISTS succeeded (no error)");

            logger.info("\n=== Summary ===");
            logger.info("SHOW_INITIAL_ROWS option controls whether existing table rows are included:");
            logger.info("");
            logger.info("SHOW_INITIAL_ROWS = TRUE:");
            logger.info("  - Includes existing rows at stream creation time");
            logger.info("  - Rows appear as INSERT operations in the stream");
            logger.info("  - Useful for: initial data loads, historical snapshots, backfills");
            logger.info("");
            logger.info("SHOW_INITIAL_ROWS = FALSE (default):");
            logger.info("  - Only tracks changes after stream creation");
            logger.info("  - Standard Snowflake stream behavior");
            logger.info("  - Useful for: ongoing CDC, real-time change tracking");
            logger.info("");
            logger.info("Can be combined with:");
            logger.info("  - APPEND_ONLY option (for insert-only tracking)");
            logger.info("  - IF NOT EXISTS clause");
            logger.info("  - COMMENT clause");
            logger.info("  - Schema-qualified names");

        } finally {
            engine.shutdown();
        }
    }
}
