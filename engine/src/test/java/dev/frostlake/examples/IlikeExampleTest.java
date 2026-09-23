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

public class IlikeExampleTest {
    private static final Logger logger = LoggerFactory.getLogger(IlikeExampleTest.class);

    @Test
    public void demonstrateIlike() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== ILIKE (Case-Insensitive Pattern Matching) Examples ===");

            // Example 1: Basic ILIKE vs LIKE
            logger.info("\n1. ILIKE vs LIKE comparison:");
            engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR)");
            engine.execute("INSERT INTO products VALUES (1, 'Apple iPhone')");
            engine.execute("INSERT INTO products VALUES (2, 'SAMSUNG Galaxy')");
            engine.execute("INSERT INTO products VALUES (3, 'google Pixel')");

            final ResultSet rs1 = engine.executeQuery("SELECT name FROM products WHERE name LIKE 'apple%'");
            logger.info("LIKE 'apple%' (case-sensitive): {} results", rs1.getRowCount());

            final ResultSet rs2 = engine.executeQuery("SELECT name FROM products WHERE name ILIKE 'apple%'");
            logger.info("ILIKE 'apple%' (case-insensitive): {} results", rs2.getRowCount());
            if (rs2.getRowCount() > 0) {
                logger.info("  Found: {}", rs2.getRows().get(0).getValue(0));
            }

            // Example 2: Search with wildcards
            logger.info("\n2. Search with % wildcard:");
            engine.execute("CREATE TABLE emails (id INTEGER, address VARCHAR)");
            engine.execute("INSERT INTO emails VALUES (1, 'user@EXAMPLE.COM')");
            engine.execute("INSERT INTO emails VALUES (2, 'admin@test.org')");
            engine.execute("INSERT INTO emails VALUES (3, 'info@Example.com')");

            final ResultSet rs3 = engine.executeQuery(
                "SELECT address FROM emails WHERE address ILIKE '%example.com%' ORDER BY id"
            );
            logger.info("Emails matching '%example.com%':");
            for (int i = 0; i < rs3.getRowCount(); i++) {
                logger.info("  {}", rs3.getRows().get(i).getValue(0));
            }

            // Example 3: Single character wildcard
            logger.info("\n3. Single character wildcard (_):");
            engine.execute("CREATE TABLE codes (id INTEGER, code VARCHAR)");
            engine.execute("INSERT INTO codes VALUES (1, 'A123')");
            engine.execute("INSERT INTO codes VALUES (2, 'a456')");
            engine.execute("INSERT INTO codes VALUES (3, 'B789')");
            engine.execute("INSERT INTO codes VALUES (4, 'A999')");

            final ResultSet rs4 = engine.executeQuery(
                "SELECT code FROM codes WHERE code ILIKE 'a___' ORDER BY id"
            );
            logger.info("Codes matching 'a___' (a followed by 3 chars):");
            for (int i = 0; i < rs4.getRowCount(); i++) {
                logger.info("  {}", rs4.getRows().get(i).getValue(0));
            }

            // Example 4: NOT ILIKE
            logger.info("\n4. NOT ILIKE:");
            engine.execute("CREATE TABLE status_records (id INTEGER, status VARCHAR)");
            engine.execute("INSERT INTO status_records VALUES (1, 'ACTIVE')");
            engine.execute("INSERT INTO status_records VALUES (2, 'inactive')");
            engine.execute("INSERT INTO status_records VALUES (3, 'Pending')");
            engine.execute("INSERT INTO status_records VALUES (4, 'Active')");

            final ResultSet rs5 = engine.executeQuery(
                "SELECT status FROM status_records WHERE status NOT ILIKE 'active' ORDER BY id"
            );
            logger.info("Statuses NOT matching 'active':");
            for (int i = 0; i < rs5.getRowCount(); i++) {
                logger.info("  {}", rs5.getRows().get(i).getValue(0));
            }

            // Example 5: Complex filtering
            logger.info("\n5. Complex filtering with ILIKE:");
            engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR, email VARCHAR)");
            engine.execute("INSERT INTO customers VALUES (1, 'John Smith', 'john@TECH.com')");
            engine.execute("INSERT INTO customers VALUES (2, 'Jane DOE', 'jane@sales.com')");
            engine.execute("INSERT INTO customers VALUES (3, 'Bob WILSON', 'bob@TECH.com')");

            final ResultSet rs6 = engine.executeQuery(
                "SELECT name, email FROM customers " +
                "WHERE email ILIKE '%tech.com%' OR name ILIKE '%smith%' " +
                "ORDER BY id"
            );
            logger.info("Customers with tech.com email or Smith in name:");
            for (int i = 0; i < rs6.getRowCount(); i++) {
                logger.info("  {}: {}",
                    rs6.getRows().get(i).getValue(0),
                    rs6.getRows().get(i).getValue(1)
                );
            }

            // Example 6: ILIKE in aggregate query
            logger.info("\n6. ILIKE in aggregate query:");
            final ResultSet rs7 = engine.executeQuery(
                "SELECT COUNT(*) as tech_customers FROM customers WHERE email ILIKE '%tech.com%'"
            );
            logger.info("Number of tech.com customers: {}", rs7.getRows().get(0).getValue(0));

        } finally {
            engine.shutdown();
        }
    }
}
