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
 * Example demonstrating EXECUTE IMMEDIATE with dollar-quoted strings
 */
public final class ExecuteImmediateDollarQuotedExample {

    /** Static helpers only — never instantiated. */
    private ExecuteImmediateDollarQuotedExample() {
    }
    private static final Logger logger = LoggerFactory.getLogger(ExecuteImmediateDollarQuotedExample.class);

    public static void main(final String[] args) {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            logger.info("=== EXECUTE IMMEDIATE with Dollar-Quoted Strings ===\n");

            // Setup
            engine.execute("DROP DATABASE IF EXISTS demo_db");
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");
            engine.execute("USE SCHEMA PUBLIC");

            // 1. Simple CREATE TABLE with dollar-quoted string
            logger.info("1. CREATE TABLE with Dollar-Quoted String:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("EXECUTE IMMEDIATE $$CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)$$");
            logger.info("   ✓ Table created using $$...$$\n");

            // 2. INSERT with dollar-quoted string
            logger.info("2. INSERT with Dollar-Quoted String:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("EXECUTE IMMEDIATE $$INSERT INTO products VALUES (1, 'Laptop', 1200)$$");
            engine.execute("EXECUTE IMMEDIATE $$INSERT INTO products VALUES (2, 'Mouse', 25)$$");
            logger.info("   ✓ Data inserted using $$...$$\n");

            // 3. SELECT with dollar-quoted string
            logger.info("3. SELECT with Dollar-Quoted String:");
            logger.info("   {}", "-".repeat(60));
            final var result1 = engine.execute("EXECUTE IMMEDIATE $$SELECT * FROM products WHERE price > 100$$");
            if (!result1.getResultSets().isEmpty()) {
                final ResultSet rs = result1.getResultSets().get(0);
                logger.info("   Found {} expensive products:", rs.getRowCount());
                for (final Row row : rs.getRows()) {
                    logger.info("   - {}: ${}", row.getValue(1), row.getValue(2));
                }
            }
            logger.info("");

            // 4. CREATE FUNCTION with dollar-quoted string (no quote escaping needed)
            logger.info("4. CREATE FUNCTION with Dollar-Quoted String:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("EXECUTE IMMEDIATE $$CREATE FUNCTION calc_tax(amount INTEGER) RETURNS INTEGER AS 'amount * 0.08'$$");
            logger.info("   ✓ Function created - no quote escaping needed!\n");

            // 5. CREATE PROCEDURE with dollar-quoted string
            logger.info("5. CREATE PROCEDURE with Dollar-Quoted String:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("EXECUTE IMMEDIATE $$CREATE PROCEDURE apply_discount(item_id INTEGER) RETURNS INTEGER AS 'BEGIN RETURN item_id; END'$$");
            logger.info("   ✓ Procedure created using $$...$$\n");

            // 6. UPDATE with dollar-quoted string
            logger.info("6. UPDATE with Dollar-Quoted String:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("EXECUTE IMMEDIATE $$UPDATE products SET price = price * 1.1 WHERE id = 1$$");
            logger.info("   ✓ Updated laptop price (10% increase)\n");

            // 7. CREATE VIEW with dollar-quoted string
            logger.info("7. CREATE VIEW with Dollar-Quoted String:");
            logger.info("   {}", "-".repeat(60));
            engine.execute("EXECUTE IMMEDIATE $$CREATE VIEW expensive_products AS SELECT * FROM products WHERE price > 500$$");
            logger.info("   ✓ View created using $$...$$\n");

            // 8. Mixed quoting styles work together
            logger.info("8. Mixed Quoting Styles:");
            logger.info("   {}", "-".repeat(60));
            logger.info("   Single-quoted style: EXECUTE IMMEDIATE 'CREATE TABLE test1 (id INTEGER)'");
            logger.info("   Dollar-quoted style: EXECUTE IMMEDIATE $$CREATE TABLE test2 (id INTEGER)$$");
            engine.execute("EXECUTE IMMEDIATE 'CREATE TABLE test1 (id INTEGER)'");
            engine.execute("EXECUTE IMMEDIATE $$CREATE TABLE test2 (id INTEGER)$$");
            logger.info("   ✓ Both styles work!\n");

            // 9. Benefits of dollar-quoted strings
            logger.info("9. Benefits of Dollar-Quoted Strings:");
            logger.info("   {}", "-".repeat(60));
            logger.info("   ✓ No need to escape single quotes inside the SQL");
            logger.info("   ✓ Cleaner syntax for complex SQL");
            logger.info("   ✓ Better readability for multi-line statements");
            logger.info("   ✓ Compatible with all SQL commands");
            logger.info("   ✓ Works with CREATE FUNCTION/PROCEDURE body definitions\n");

            // 10. Example comparison
            logger.info("10. Comparison: Traditional vs Dollar-Quoted:");
            logger.info("    {}", "-".repeat(60));
            logger.info("    Traditional: EXECUTE IMMEDIATE 'SELECT * FROM users WHERE name = ''O''Brien'''");
            logger.info("    Dollar-Quoted: EXECUTE IMMEDIATE $$SELECT * FROM users WHERE name = 'O'Brien'$$");
            logger.info("    ✓ Much cleaner with dollar-quoted!\n");

            engine.execute("DROP DATABASE demo_db");
            engine.shutdown();

            logger.info("=== Example Complete ===");

        } catch (final Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
        }
    }
}
