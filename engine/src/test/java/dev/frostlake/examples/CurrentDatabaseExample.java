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

public class CurrentDatabaseExample {
    private static final Logger logger = LoggerFactory.getLogger(CurrentDatabaseExample.class);

    @Test
    public void demonstrateCurrentDatabase() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            logger.info("=== CURRENT_DATABASE() Examples ===");

            // Example 1: Get current database
            logger.info("\n1. Get current database:");
            final ResultSet rs1 = engine.executeQuery("SELECT CURRENT_DATABASE() AS db_name");
            logger.info("Current database: {}", rs1.getRows().get(0).getValue(0));

            // Example 2: Create and switch databases
            logger.info("\n2. Create and switch databases:");
            engine.execute("CREATE DATABASE production_db");
            engine.execute("CREATE DATABASE staging_db");

            engine.execute("USE DATABASE production_db");
            final ResultSet rs2 = engine.executeQuery("SELECT CURRENT_DATABASE()");
            logger.info("After switching to production: {}", rs2.getRows().get(0).getValue(0));

            engine.execute("USE DATABASE staging_db");
            final ResultSet rs3 = engine.executeQuery("SELECT CURRENT_DATABASE()");
            logger.info("After switching to staging: {}", rs3.getRows().get(0).getValue(0));

            // Example 3: Track database context in audit table
            logger.info("\n3. Track database context in audit table:");
            engine.execute("CREATE TABLE audit_log (id INTEGER, db_context VARCHAR, action VARCHAR)");
            engine.execute("INSERT INTO audit_log VALUES (1, CURRENT_DATABASE(), 'Data loaded')");
            engine.execute("INSERT INTO audit_log VALUES (2, CURRENT_DATABASE(), 'Report generated')");

            final ResultSet rs4 = engine.executeQuery("SELECT * FROM audit_log ORDER BY id");
            logger.info("Audit log entries:");
            for (int i = 0; i < rs4.getRowCount(); i++) {
                logger.info("  ID {}: {} - {} ",
                    rs4.getRows().get(i).getValue(0),
                    rs4.getRows().get(i).getValue(1),
                    rs4.getRows().get(i).getValue(2)
                );
            }

            // Example 4: Filter by current database
            logger.info("\n4. Filter by current database:");
            engine.execute("USE DATABASE production_db");
            engine.execute("CREATE TABLE database_configs (db_name VARCHAR, setting VARCHAR, value VARCHAR)");
            engine.execute("INSERT INTO database_configs VALUES ('PRODUCTION_DB', 'max_connections', '100')");
            engine.execute("INSERT INTO database_configs VALUES ('STAGING_DB', 'max_connections', '50')");
            engine.execute("INSERT INTO database_configs VALUES ('PRODUCTION_DB', 'timeout', '30')");

            final ResultSet rs5 = engine.executeQuery(
                "SELECT setting, value FROM database_configs WHERE db_name = CURRENT_DATABASE()"
            );
            logger.info("Settings for current database:");
            for (int i = 0; i < rs5.getRowCount(); i++) {
                logger.info("  {}: {}",
                    rs5.getRows().get(i).getValue(0),
                    rs5.getRows().get(i).getValue(1)
                );
            }

            // Example 5: String concatenation with CURRENT_DATABASE
            logger.info("\n5. String concatenation:");
            final ResultSet rs6 = engine.executeQuery(
                "SELECT 'Connected to: ' || CURRENT_DATABASE() AS message"
            );
            logger.info("{}", rs6.getRows().get(0).getValue(0));

        } finally {
            engine.shutdown();
        }
    }
}
