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

public class ToTimestampNtzExample {
    private static final Logger logger = LoggerFactory.getLogger(ToTimestampNtzExample.class);

    @Test
    public void demonstrateToTimestampNtz() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== TO_TIMESTAMP_NTZ Examples ===");

            // Example 1: Convert string to timestamp
            logger.info("\n1. Convert ISO format string to timestamp:");
            final ResultSet rs1 = engine.executeQuery("SELECT TO_TIMESTAMP_NTZ('2024-01-15T10:30:45') AS timestamp_value");
            logger.info("Result: {}", rs1.getRows().get(0).getValue(0));

            // Example 2: Convert space-separated string
            logger.info("\n2. Convert space-separated string to timestamp:");
            final ResultSet rs2 = engine.executeQuery("SELECT TO_TIMESTAMP_NTZ('2024-01-15 10:30:45') AS timestamp_value");
            logger.info("Result: {}", rs2.getRows().get(0).getValue(0));

            // Example 3: Convert date-only string
            logger.info("\n3. Convert date string to timestamp (assumes midnight):");
            final ResultSet rs3 = engine.executeQuery("SELECT TO_TIMESTAMP_NTZ('2024-01-15') AS timestamp_value");
            logger.info("Result: {}", rs3.getRows().get(0).getValue(0));

            // Example 4: Convert epoch seconds
            logger.info("\n4. Convert Unix epoch seconds to timestamp:");
            final ResultSet rs4 = engine.executeQuery("SELECT TO_TIMESTAMP_NTZ(1705315845) AS timestamp_value");
            logger.info("Result: {}", rs4.getRows().get(0).getValue(0));

            // Example 5: Use in CREATE TABLE and INSERT
            logger.info("\n5. Create table with TIMESTAMP_NTZ column:");
            engine.execute("CREATE TABLE events (id INTEGER, event_name VARCHAR, event_time TIMESTAMP_NTZ)");
            engine.execute("INSERT INTO events VALUES (1, 'Meeting', TO_TIMESTAMP_NTZ('2024-01-15 10:30:00'))");
            engine.execute("INSERT INTO events VALUES (2, 'Lunch', TO_TIMESTAMP_NTZ('2024-01-15 12:00:00'))");
            engine.execute("INSERT INTO events VALUES (3, 'Review', TO_TIMESTAMP_NTZ('2024-01-15 14:30:00'))");

            final ResultSet rs5 = engine.executeQuery("SELECT * FROM events ORDER BY event_time");
            logger.info("Events table:");
            for (int i = 0; i < rs5.getRowCount(); i++) {
                logger.info("  Row {}: id={}, event_name={}, event_time={}",
                    i + 1,
                    rs5.getRows().get(i).getValue(0),
                    rs5.getRows().get(i).getValue(1),
                    rs5.getRows().get(i).getValue(2)
                );
            }

            // Example 6: Filter by timestamp
            logger.info("\n6. Filter events after a specific time:");
            final ResultSet rs6 = engine.executeQuery(
                "SELECT event_name, event_time FROM events WHERE event_time > TO_TIMESTAMP_NTZ('2024-01-15 11:00:00')"
            );
            logger.info("Events after 11:00:");
            for (int i = 0; i < rs6.getRowCount(); i++) {
                logger.info("  {}: {}",
                    rs6.getRows().get(i).getValue(0),
                    rs6.getRows().get(i).getValue(1)
                );
            }

            // Example 7: Compare timestamps
            logger.info("\n7. Compare two timestamps:");
            final ResultSet rs7 = engine.executeQuery(
                "SELECT TO_TIMESTAMP_NTZ('2024-01-15 10:30:00') < TO_TIMESTAMP_NTZ('2024-01-15 14:30:00') AS is_earlier"
            );
            logger.info("Is 10:30 earlier than 14:30? {}", rs7.getRows().get(0).getValue(0));

            // Example 8: Convert timestamp column from string
            logger.info("\n8. Convert string column to timestamp:");
            engine.execute("CREATE TABLE raw_logs (id INTEGER, log_message VARCHAR, log_time_str VARCHAR)");
            engine.execute("INSERT INTO raw_logs VALUES (1, 'System started', '2024-01-15 09:00:00')");
            engine.execute("INSERT INTO raw_logs VALUES (2, 'User login', '2024-01-15 09:15:30')");
            engine.execute("INSERT INTO raw_logs VALUES (3, 'File uploaded', '2024-01-15 09:45:00')");

            final ResultSet rs8 = engine.executeQuery(
                "SELECT id, log_message, TO_TIMESTAMP_NTZ(log_time_str) AS log_time FROM raw_logs ORDER BY id"
            );
            logger.info("Parsed log times:");
            for (int i = 0; i < rs8.getRowCount(); i++) {
                logger.info("  {}: {} at {}",
                    rs8.getRows().get(i).getValue(0),
                    rs8.getRows().get(i).getValue(1),
                    rs8.getRows().get(i).getValue(2)
                );
            }

        } finally {
            engine.shutdown();
        }
    }
}
