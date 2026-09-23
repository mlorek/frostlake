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

public class SysdateExampleTest {
    private static final Logger logger = LoggerFactory.getLogger(SysdateExampleTest.class);

    @Test
    public void demonstrateSysdate() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== SYSDATE() Examples ===");

            // Example 1: Get current timestamp
            logger.info("\n1. Get current timestamp using SYSDATE():");
            final ResultSet rs1 = engine.executeQuery("SELECT SYSDATE() AS current_time");
            logger.info("Current time: {}", rs1.getRows().get(0).getValue(0));

            // Example 2: Use SYSDATE in INSERT
            logger.info("\n2. Insert records with current timestamp:");
            engine.execute("CREATE TABLE audit_log (id INTEGER, action VARCHAR, created_at TIMESTAMP_NTZ)");
            engine.execute("INSERT INTO audit_log VALUES (1, 'User login', SYSDATE())");
            engine.execute("INSERT INTO audit_log VALUES (2, 'File upload', SYSDATE())");
            engine.execute("INSERT INTO audit_log VALUES (3, 'Report generated', SYSDATE())");

            final ResultSet rs2 = engine.executeQuery("SELECT * FROM audit_log ORDER BY id");
            logger.info("Audit log entries:");
            for (int i = 0; i < rs2.getRowCount(); i++) {
                logger.info("  ID {}: {} at {}",
                    rs2.getRows().get(i).getValue(0),
                    rs2.getRows().get(i).getValue(1),
                    rs2.getRows().get(i).getValue(2)
                );
            }

            // Example 3: Compare timestamps
            logger.info("\n3. Compare SYSDATE with past timestamp:");
            final ResultSet rs3 = engine.executeQuery(
                "SELECT SYSDATE() > TO_TIMESTAMP_NTZ('2020-01-01 00:00:00') AS is_after_2020"
            );
            logger.info("Is current time after 2020-01-01? {}", rs3.getRows().get(0).getValue(0));

            // Example 4: Filter records by timestamp
            logger.info("\n4. Create table with historical data:");
            engine.execute("CREATE TABLE orders (id INTEGER, order_date TIMESTAMP_NTZ, status VARCHAR)");
            engine.execute("INSERT INTO orders VALUES (1, TO_TIMESTAMP_NTZ('2024-01-15 10:00:00'), 'completed')");
            engine.execute("INSERT INTO orders VALUES (2, TO_TIMESTAMP_NTZ('2024-06-15 14:30:00'), 'completed')");
            engine.execute("INSERT INTO orders VALUES (3, SYSDATE(), 'pending')");

            final ResultSet rs4 = engine.executeQuery(
                "SELECT id, status FROM orders WHERE order_date <= SYSDATE() ORDER BY id"
            );
            logger.info("Orders up to now:");
            for (int i = 0; i < rs4.getRowCount(); i++) {
                logger.info("  Order {}: {}",
                    rs4.getRows().get(i).getValue(0),
                    rs4.getRows().get(i).getValue(1)
                );
            }

            // Example 5: Use SYSDATE in WHERE clause
            logger.info("\n5. Find recent orders (current timestamp):");
            final ResultSet rs5 = engine.executeQuery(
                "SELECT COUNT(*) AS pending_count FROM orders WHERE order_date = SYSDATE() AND status = 'pending'"
            );
            logger.info("Pending orders created now: {}", rs5.getRows().get(0).getValue(0));

            // Example 6: Multiple SYSDATE calls
            logger.info("\n6. Multiple SYSDATE() calls in same query:");
            final ResultSet rs6 = engine.executeQuery(
                "SELECT SYSDATE() AS ts1, SYSDATE() AS ts2"
            );
            logger.info("First call: {}", rs6.getRows().get(0).getValue(0));
            logger.info("Second call: {}", rs6.getRows().get(0).getValue(1));

            // Example 7: SYSDATE in subquery
            logger.info("\n7. Use SYSDATE() in subquery:");
            final ResultSet rs7 = engine.executeQuery(
                "SELECT id, status FROM orders WHERE order_date < (SELECT SYSDATE())"
            );
            logger.info("Orders before now: {} records", rs7.getRowCount());

            // Example 8: CREATE TABLE AS SELECT with SYSDATE
            logger.info("\n8. Create snapshot table with current timestamp:");
            engine.execute("CREATE TABLE order_snapshot AS SELECT *, SYSDATE() AS snapshot_time FROM orders");
            final ResultSet rs8 = engine.executeQuery("SELECT id, snapshot_time FROM order_snapshot LIMIT 1");
            logger.info("Snapshot created at: {}", rs8.getRows().get(0).getValue(1));

        } finally {
            engine.shutdown();
        }
    }
}
