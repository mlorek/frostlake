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

public class StreamOnViewExampleTest {
    private static final Logger logger = LoggerFactory.getLogger(StreamOnViewExampleTest.class);

    @Test
    public void demonstrateStreamOnView() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== STREAM on VIEW ===");

            // Create a base table
            logger.info("\n1. Create base table:");
            engine.execute("CREATE TABLE sales (id INTEGER, product STRING, amount DECIMAL, region STRING)");
            logger.info("Created table: sales(id, product, amount, region)");

            // Insert some initial data
            engine.execute("INSERT INTO sales VALUES (1, 'Widget', 100.00, 'North')");
            engine.execute("INSERT INTO sales VALUES (2, 'Gadget', 250.00, 'South')");
            engine.execute("INSERT INTO sales VALUES (3, 'Doohickey', 50.00, 'East')");
            logger.info("Inserted 3 initial sales records");

            // Create a view filtering high-value sales
            logger.info("\n2. Create view on base table:");
            engine.execute("CREATE VIEW high_value_sales AS SELECT * FROM sales WHERE amount > 100");
            logger.info("Created view: high_value_sales (filters sales > 100)");
            logger.info("View currently shows: 1 record (Gadget, 250.00)");

            // Create a stream on the view
            logger.info("\n3. Create stream on the view:");
            engine.execute("CREATE STREAM sales_stream ON VIEW high_value_sales");
            logger.info("Created stream: sales_stream ON VIEW high_value_sales");
            logger.info("Stream will track changes that match the view criteria");

            // Insert new data into base table
            logger.info("\n4. Insert data into base table:");
            engine.execute("INSERT INTO sales VALUES (4, 'Thingamajig', 300.00, 'West')");
            engine.execute("INSERT INTO sales VALUES (5, 'Whatsit', 75.00, 'North')");
            logger.info("Inserted 2 new sales records");
            logger.info("  - Thingamajig (300.00) - MATCHES view criteria");
            logger.info("  - Whatsit (75.00) - DOES NOT match view criteria");

            // Query the stream
            logger.info("\n5. Query the stream:");
            logger.info("SELECT * FROM sales_stream");
            logger.info("Stream shows changes that match view criteria:");
            logger.info("  - Only INSERT of Thingamajig appears (300.00 > 100)");
            logger.info("  - INSERT of Whatsit does NOT appear (75.00 < 100)");

            logger.info("\n=== STREAM on VIEW with SHOW_INITIAL_ROWS ===");

            // Create another example with initial rows
            logger.info("\n6. Create stream with SHOW_INITIAL_ROWS:");
            engine.execute("CREATE TABLE inventory (id INTEGER, item STRING, stock INTEGER)");
            engine.execute("INSERT INTO inventory VALUES (1, 'Item A', 5)");
            engine.execute("INSERT INTO inventory VALUES (2, 'Item B', 50)");
            engine.execute("INSERT INTO inventory VALUES (3, 'Item C', 3)");
            logger.info("Created table: inventory with 3 items");

            engine.execute("CREATE VIEW low_stock AS SELECT * FROM inventory WHERE stock < 10");
            logger.info("Created view: low_stock (filters stock < 10)");

            engine.execute("CREATE STREAM inventory_stream ON VIEW low_stock SHOW_INITIAL_ROWS = TRUE");
            logger.info("Created stream: inventory_stream with SHOW_INITIAL_ROWS = TRUE");
            logger.info("Stream immediately shows existing rows from view:");
            logger.info("  - Item A (stock: 5)");
            logger.info("  - Item C (stock: 3)");

            logger.info("\n=== STREAM on VIEW with APPEND_ONLY ===");

            // Create append-only stream example
            logger.info("\n7. Create APPEND_ONLY stream on view:");
            engine.execute("CREATE TABLE audit_log (id INTEGER, action STRING, timestamp STRING)");
            engine.execute("CREATE VIEW critical_actions AS SELECT * FROM audit_log WHERE action = 'DELETE'");
            engine.execute("CREATE STREAM audit_stream ON VIEW critical_actions APPEND_ONLY = TRUE");
            logger.info("Created APPEND_ONLY stream: audit_stream");
            logger.info("This stream only captures INSERT operations");
            logger.info("UPDATEs and DELETEs on the base table are not tracked");

            logger.info("\n=== Use Cases ===");
            logger.info("Streams on views are useful for:");
            logger.info("  1. Change Data Capture on filtered data");
            logger.info("     - Track only high-value transactions");
            logger.info("     - Monitor only critical inventory changes");
            logger.info("     - Capture specific types of audit events");
            logger.info("");
            logger.info("  2. Simplified ETL pipelines");
            logger.info("     - View defines business logic (filtering, transformations)");
            logger.info("     - Stream captures incremental changes");
            logger.info("     - Downstream systems consume only relevant changes");
            logger.info("");
            logger.info("  3. Multi-stage processing");
            logger.info("     - Raw data → Table");
            logger.info("     - Business rules → View");
            logger.info("     - Change tracking → Stream on View");
            logger.info("     - Target system ← Stream consumer");
            logger.info("");
            logger.info("  4. Performance optimization");
            logger.info("     - Avoid reprocessing entire tables");
            logger.info("     - Process only changes that matter");
            logger.info("     - Reduce downstream processing load");

            logger.info("\n=== Syntax Summary ===");
            logger.info("CREATE STREAM <stream_name> ON VIEW <view_name>");
            logger.info("  [APPEND_ONLY = TRUE|FALSE]");
            logger.info("  [SHOW_INITIAL_ROWS = TRUE|FALSE]");
            logger.info("  [COMMENT = '<comment>']");
            logger.info("");
            logger.info("Key differences from streams on tables:");
            logger.info("  - Stream captures changes that match view criteria");
            logger.info("  - View can filter, transform, or join data");
            logger.info("  - Changes to underlying table(s) flow through view logic");
            logger.info("  - Stream sees only the 'after-filter' changes");

        } finally {
            engine.shutdown();
        }
    }
}
