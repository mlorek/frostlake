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
 * Example demonstrating EXECUTE IMMEDIATE command
 */
public class ExecuteImmediateExample {
    private static final Logger logger = LoggerFactory.getLogger(ExecuteImmediateExample.class);

    public static void main(final String[] args) {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            logger.info("=== EXECUTE IMMEDIATE Examples ===\n");

            // Setup
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");
            engine.execute("CREATE SCHEMA demo_schema");
            engine.execute("USE SCHEMA demo_schema");

            // Example 1: Dynamic CREATE TABLE
            logger.info("1. Dynamic Table Creation:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("EXECUTE IMMEDIATE 'CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)'");
            logger.info("   ✅ Table created via EXECUTE IMMEDIATE\n");

            // Example 2: Dynamic INSERT with escaped quotes
            logger.info("2. Dynamic INSERT:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("EXECUTE IMMEDIATE 'INSERT INTO products VALUES (1, ''Laptop'', 1200)'");
            engine.execute("EXECUTE IMMEDIATE 'INSERT INTO products VALUES (2, ''Mouse'', 25)'");
            engine.execute("EXECUTE IMMEDIATE 'INSERT INTO products VALUES (3, ''Keyboard'', 75)'");
            logger.info("   ✅ 3 rows inserted via EXECUTE IMMEDIATE\n");

            // Example 3: Dynamic SELECT
            logger.info("3. Dynamic SELECT:");
            logger.info("   {}", "-".repeat(60));
            var result1 = engine.execute("EXECUTE IMMEDIATE 'SELECT * FROM products WHERE price > 50'");
            if (!result1.getResultSets().isEmpty()) {
                ResultSet rs = result1.getResultSets().get(0);
                logger.info("   Found {} products with price > 50:", rs.getRowCount());
                for (final Row row : rs.getRows()) {
                    logger.info("   - {}: ${}", row.getValue(1), row.getValue(2));
                }
            }
            logger.info("");

            // Example 4: Using Variables
            logger.info("4. EXECUTE IMMEDIATE with Variables:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("SET my_query = 'SELECT name, price FROM products ORDER BY price DESC'");
            logger.info("   Variable set: my_query");

            var result2 = engine.execute("EXECUTE IMMEDIATE my_query");
            if (!result2.getResultSets().isEmpty()) {
                ResultSet rs = result2.getResultSets().get(0);
                logger.info("   Products sorted by price:");
                for (final Row row : rs.getRows()) {
                    logger.info("   - {}: ${}", row.getValue(0), row.getValue(1));
                }
            }
            logger.info("");

            // Example 5: Dynamic UPDATE
            logger.info("5. Dynamic UPDATE:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("EXECUTE IMMEDIATE 'UPDATE products SET price = price * 1.1 WHERE id = 1'");
            logger.info("   ✅ Updated laptop price (10% increase)\n");

            ResultSet updated = engine.executeQuery("SELECT * FROM products WHERE id = 1");
            logger.info("   New laptop price: ${}\n", updated.getRows().get(0).getValue(2));

            // Example 6: Dynamic DELETE
            logger.info("6. Dynamic DELETE:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("EXECUTE IMMEDIATE 'DELETE FROM products WHERE price < 50'");
            logger.info("   ✅ Deleted products with price < $50\n");

            ResultSet remaining = engine.executeQuery("SELECT COUNT(*) FROM products");
            logger.info("   Remaining products: {}\n", remaining.getRows().get(0).getValue(0));

            // Example 7: Complex Dynamic Query
            logger.info("7. Complex Dynamic Query:");
            logger.info("   {}", "-".repeat(60));

            // Insert more data for complex example
            engine.execute("INSERT INTO products VALUES (4, 'Monitor', 300)");
            engine.execute("INSERT INTO products VALUES (5, 'Tablet', 400)");
            engine.execute("INSERT INTO products VALUES (6, 'Phone', 800)");

            engine.execute("SET complex_sql = 'SELECT * FROM products WHERE price >= 100 ORDER BY price DESC LIMIT 3'");
            var result3 = engine.execute("EXECUTE IMMEDIATE complex_sql");

            if (!result3.getResultSets().isEmpty()) {
                ResultSet rs = result3.getResultSets().get(0);
                logger.info("   Top 3 most expensive products:");
                for (final Row row : rs.getRows()) {
                    logger.info("   - {}: ${}", row.getValue(1), row.getValue(2));
                }
            }
            logger.info("");

            // Example 8: Building SQL Dynamically
            logger.info("8. Building SQL with Variables:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("SET table_name = 'products'");
            engine.execute("SET min_price = 300");

            // Note: In real Snowflake, you'd concatenate. Here we demonstrate variable storage
            engine.execute("SET dynamic_query = 'SELECT COUNT(*) FROM products WHERE price > 300'");
            var result4 = engine.execute("EXECUTE IMMEDIATE dynamic_query");

            if (!result4.getResultSets().isEmpty()) {
                ResultSet rs = result4.getResultSets().get(0);
                logger.info("   Products with price > $300: {}", rs.getRows().get(0).getValue(0));
            }
            logger.info("");

            logger.info("=== All Examples Completed Successfully! ===");

        } finally {
            engine.shutdown();
        }
    }
}
