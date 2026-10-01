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

/**
 * Example demonstrating DELETE operation in MERGE statements.
 *
 * MERGE statements can now include DELETE operations for matched rows:
 * - WHEN MATCHED THEN DELETE
 * - WHEN MATCHED AND condition THEN DELETE
 *
 * This allows for complex data synchronization patterns where some rows
 * need to be updated, some deleted, and some inserted.
 */
public class MergeDeleteExampleTest {

    private static final Logger logger = LoggerFactory.getLogger(MergeDeleteExampleTest.class);

    @Test
    public void demonstrateMergeDeleteOperations() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("Example 1: Simple MERGE with DELETE");
            logger.info("=".repeat(50));

            engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR, status VARCHAR)");
            engine.execute("INSERT INTO customers VALUES (1, 'Alice', 'active')");
            engine.execute("INSERT INTO customers VALUES (2, 'Bob', 'active')");
            engine.execute("INSERT INTO customers VALUES (3, 'Charlie', 'active')");

            logger.info("Initial customers:");
            ResultSet rs = engine.executeQuery("SELECT * FROM customers ORDER BY id");
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Customer: id={}, name={}, status={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1),
                    rs.getRows().get(i).getValue(2));
            }

            engine.execute("CREATE TABLE customer_updates (id INTEGER, action VARCHAR)");
            engine.execute("INSERT INTO customer_updates VALUES (1, 'remove')");
            engine.execute("INSERT INTO customer_updates VALUES (2, 'keep')");

            // Delete customers marked for removal
            engine.execute("""
                MERGE INTO customers c
                USING customer_updates u
                ON c.id = u.id
                WHEN MATCHED AND u.action = 'remove' THEN DELETE
                """);

            logger.info("After MERGE with DELETE:");
            rs = engine.executeQuery("SELECT * FROM customers ORDER BY id");
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Customer: id={}, name={}, status={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1),
                    rs.getRows().get(i).getValue(2));
            }

            logger.info("");
            logger.info("Example 2: MERGE with DELETE, UPDATE, and INSERT");
            logger.info("=".repeat(50));

            engine.execute("TRUNCATE TABLE customers");
            engine.execute("INSERT INTO customers VALUES (1, 'Alice', 'active')");
            engine.execute("INSERT INTO customers VALUES (2, 'Bob', 'active')");
            engine.execute("INSERT INTO customers VALUES (3, 'Charlie', 'inactive')");

            engine.execute("TRUNCATE TABLE customer_updates");
            engine.execute("CREATE TABLE customer_changes (id INTEGER, name VARCHAR, action VARCHAR, status VARCHAR)");
            engine.execute("INSERT INTO customer_changes VALUES (1, 'Alice', 'delete', NULL)");
            engine.execute("INSERT INTO customer_changes VALUES (2, 'Bob', 'update', 'premium')");
            engine.execute("INSERT INTO customer_changes VALUES (4, 'Diana', 'insert', 'active')");

            logger.info("Before comprehensive MERGE:");
            rs = engine.executeQuery("SELECT * FROM customers ORDER BY id");
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Customer: id={}, name={}, status={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1),
                    rs.getRows().get(i).getValue(2));
            }

            // Comprehensive MERGE with all operations
            engine.execute("""
                MERGE INTO customers c
                USING customer_changes ch
                ON c.id = ch.id
                WHEN MATCHED AND ch.action = 'delete' THEN DELETE
                WHEN MATCHED AND ch.action = 'update' THEN UPDATE SET c.status = ch.status
                WHEN NOT MATCHED AND ch.action = 'insert' THEN INSERT VALUES (ch.id, ch.name, ch.status)
                """);

            logger.info("After comprehensive MERGE:");
            rs = engine.executeQuery("SELECT * FROM customers ORDER BY id");
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Customer: id={}, name={}, status={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1),
                    rs.getRows().get(i).getValue(2));
            }

            logger.info("");
            logger.info("Example 3: Conditional DELETE based on target row values");
            logger.info("=".repeat(50));

            engine.execute("CREATE TABLE inventory (id INTEGER, product VARCHAR, quantity INTEGER)");
            engine.execute("INSERT INTO inventory VALUES (1, 'Widget A', 5)");
            engine.execute("INSERT INTO inventory VALUES (2, 'Widget B', 100)");
            engine.execute("INSERT INTO inventory VALUES (3, 'Widget C', 0)");
            engine.execute("INSERT INTO inventory VALUES (4, 'Widget D', 50)");

            engine.execute("CREATE TABLE inventory_check (id INTEGER)");
            engine.execute("INSERT INTO inventory_check VALUES (1)");
            engine.execute("INSERT INTO inventory_check VALUES (2)");
            engine.execute("INSERT INTO inventory_check VALUES (3)");
            engine.execute("INSERT INTO inventory_check VALUES (4)");

            logger.info("Before cleanup:");
            rs = engine.executeQuery("SELECT * FROM inventory ORDER BY id");
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Product: id={}, product={}, quantity={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1),
                    rs.getRows().get(i).getValue(2));
            }

            // Delete items with low or zero quantity, update others
            engine.execute("""
                MERGE INTO inventory i
                USING inventory_check ic
                ON i.id = ic.id
                WHEN MATCHED AND i.quantity <= 10 THEN DELETE
                WHEN MATCHED THEN UPDATE SET i.product = i.product
                """);

            logger.info("After cleanup (removed low/zero quantity items):");
            rs = engine.executeQuery("SELECT * FROM inventory ORDER BY id");
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Product: id={}, product={}, quantity={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1),
                    rs.getRows().get(i).getValue(2));
            }

            logger.info("");
            logger.info("All examples completed successfully!");

        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void demonstrateMergeDeleteUseCase() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("Use Case: Data synchronization with source system");
            logger.info("=".repeat(50));

            // Target table (our database)
            engine.execute("CREATE TABLE user_accounts (user_id INTEGER, username VARCHAR, status VARCHAR, last_login VARCHAR)");
            engine.execute("INSERT INTO user_accounts VALUES (1, 'alice', 'active', '2026-01-01')");
            engine.execute("INSERT INTO user_accounts VALUES (2, 'bob', 'active', '2026-02-01')");
            engine.execute("INSERT INTO user_accounts VALUES (3, 'charlie', 'inactive', '2025-12-01')");
            engine.execute("INSERT INTO user_accounts VALUES (4, 'diana', 'active', '2026-03-01')");

            logger.info("Current user records:");
            ResultSet rs = engine.executeQuery("SELECT * FROM user_accounts ORDER BY user_id");
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  User: id={}, username={}, status={}, last_login={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1),
                    rs.getRows().get(i).getValue(2),
                    rs.getRows().get(i).getValue(3));
            }

            // Source table (from external system)
            engine.execute("CREATE TABLE user_sync (user_id INTEGER, username VARCHAR, action VARCHAR, status VARCHAR, last_login VARCHAR)");
            engine.execute("INSERT INTO user_sync VALUES (1, 'alice', 'update', 'active', '2026-04-01')");  // Update last_login
            engine.execute("INSERT INTO user_sync VALUES (2, 'bob', 'delete', NULL, NULL)");                 // User deleted in source
            engine.execute("INSERT INTO user_sync VALUES (4, 'diana', 'update', 'inactive', '2026-03-15')"); // Deactivated
            engine.execute("INSERT INTO user_sync VALUES (5, 'eve', 'insert', 'active', '2026-04-10')");     // New user

            logger.info("");
            logger.info("Synchronizing with source system...");

            // Comprehensive sync: delete removed users, update existing, insert new
            engine.execute("""
                MERGE INTO user_accounts ua
                USING user_sync us
                ON ua.user_id = us.user_id
                WHEN MATCHED AND us.action = 'delete' THEN DELETE
                WHEN MATCHED AND us.action = 'update' THEN UPDATE SET
                    ua.status = us.status,
                    ua.last_login = us.last_login
                WHEN NOT MATCHED AND us.action = 'insert' THEN INSERT
                    VALUES (us.user_id, us.username, us.status, us.last_login)
                """);

            logger.info("");
            logger.info("After synchronization:");
            rs = engine.executeQuery("SELECT * FROM user_accounts ORDER BY user_id");
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  User: id={}, username={}, status={}, last_login={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1),
                    rs.getRows().get(i).getValue(2),
                    rs.getRows().get(i).getValue(3));
            }

            logger.info("");
            logger.info("Summary:");
            logger.info("  - User 1 (alice): Updated last_login");
            logger.info("  - User 2 (bob): Deleted");
            logger.info("  - User 3 (charlie): Unchanged (not in sync)");
            logger.info("  - User 4 (diana): Updated status and last_login");
            logger.info("  - User 5 (eve): Inserted");

        } finally {
            engine.shutdown();
        }
    }
}
