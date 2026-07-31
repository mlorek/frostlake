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
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example demonstrating SHOW and DESCRIBE commands
 */
public class ShowDescribeExample {
    private static final Logger logger = LoggerFactory.getLogger(ShowDescribeExample.class);

    public static void main(final String[] args) {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            logger.info("=== SHOW and DESCRIBE Commands Example ===\n");

            // Setup: Create some objects
            engine.execute("CREATE DATABASE analytics_db");
            engine.execute("USE DATABASE analytics_db");
            engine.execute("CREATE SCHEMA sales_schema");
            engine.execute("USE SCHEMA sales_schema");

            engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR, email VARCHAR, active BOOLEAN)");
            engine.execute("CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, amount INTEGER)");
            engine.execute("CREATE VIEW active_customers AS SELECT * FROM customers WHERE active = true");
            engine.execute("CREATE STREAM customer_stream ON TABLE customers");

            // 1. SHOW DATABASES
            logger.info("1. SHOW DATABASES:");
            logger.info("   {}", "-".repeat(60));
            ResultSet databases = engine.executeQuery("SHOW DATABASES");
            printResultSet(databases);

            // 2. SHOW SCHEMAS
            logger.info("\n2. SHOW SCHEMAS:");
            logger.info("   {}", "-".repeat(60));
            ResultSet schemas = engine.executeQuery("SHOW SCHEMAS");
            printResultSet(schemas);

            // 3. SHOW TABLES
            logger.info("\n3. SHOW TABLES:");
            logger.info("   {}", "-".repeat(60));
            ResultSet tables = engine.executeQuery("SHOW TABLES");
            printResultSet(tables);

            // 4. SHOW VIEWS
            logger.info("\n4. SHOW VIEWS:");
            logger.info("   {}", "-".repeat(60));
            ResultSet views = engine.executeQuery("SHOW VIEWS");
            printResultSet(views);

            // 5. SHOW COLUMNS
            logger.info("\n5. SHOW COLUMNS IN customers:");
            logger.info("   {}", "-".repeat(60));
            ResultSet columns = engine.executeQuery("SHOW COLUMNS IN customers");
            printResultSet(columns);

            // 6. SHOW STREAMS
            logger.info("\n6. SHOW STREAMS:");
            logger.info("   {}", "-".repeat(60));
            ResultSet streams = engine.executeQuery("SHOW STREAMS");
            printResultSet(streams);

            // 7. SHOW WAREHOUSES
            logger.info("\n7. SHOW WAREHOUSES:");
            logger.info("   {}", "-".repeat(60));
            ResultSet warehouses = engine.executeQuery("SHOW WAREHOUSES");
            printResultSet(warehouses);

            // 8. DESCRIBE TABLE
            logger.info("\n8. DESCRIBE TABLE customers:");
            logger.info("   {}", "-".repeat(60));
            ResultSet descTable = engine.executeQuery("DESCRIBE TABLE customers");
            printResultSet(descTable);

            // 9. DESCRIBE VIEW
            logger.info("\n9. DESCRIBE VIEW active_customers:");
            logger.info("   {}", "-".repeat(60));
            ResultSet descView = engine.executeQuery("DESCRIBE VIEW active_customers");
            printResultSet(descView);

            // 10. DESCRIBE STREAM
            logger.info("\n10. DESCRIBE STREAM customer_stream:");
            logger.info("    {}", "-".repeat(60));
            ResultSet descStream = engine.executeQuery("DESCRIBE STREAM customer_stream");
            printResultSet(descStream);

            logger.info("\n=== Example Complete ===");

        } finally {
            engine.shutdown();
        }
    }

    private static void printResultSet(final ResultSet rs) {
        if (rs.getRowCount() == 0) {
            logger.info("   (No results)");
            return;
        }

        // Print column headers
        StringBuilder header = new StringBuilder("   ");
        for (int i = 0; i < rs.getColumnCount(); i++) {
            header.append(String.format("%-25s", rs.getColumns().get(i).getName()));
        }
        logger.info(header.toString());

        // Print rows
        for (final Row row : rs.getRows()) {
            StringBuilder line = new StringBuilder("   ");
            for (int i = 0; i < row.getValues().size(); i++) {
                Object value = row.getValue(i);
                String strValue = value != null ? value.toString() : "NULL";
                // Truncate long values
                if (strValue.length() > 22) {
                    strValue = strValue.substring(0, 19) + "...";
                }
                line.append(String.format("%-25s", strValue));
            }
            logger.info(line.toString());
        }
    }
}
