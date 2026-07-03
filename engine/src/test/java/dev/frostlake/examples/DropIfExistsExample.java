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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example demonstrating DROP IF EXISTS statements
 */
public class DropIfExistsExample {
    private static final Logger logger = LoggerFactory.getLogger(DropIfExistsExample.class);
    public static void main(final String[] args) {
        try {
            logger.info("=== DROP IF EXISTS Example ===\n");

            DatabaseEngine engine = new DatabaseEngine();

            // Setup - use IF EXISTS to make example idempotent
            engine.execute("DROP DATABASE IF EXISTS demo_db");
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");
            logger.info("Created and using database: demo_db\n");

            // 1. Safe DROP with IF EXISTS
            logger.info("1. Safe DROP - No error if object doesn't exist:");
            logger.info("   " + "-".repeat(60));

            // Drop non-existent table - no error
            engine.execute("DROP TABLE IF EXISTS nonexistent_table");
            logger.info("   DROP TABLE IF EXISTS nonexistent_table -> Success (no error)");

            // Create and drop table
            engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
            logger.info("   Created table: users");

            engine.execute("DROP TABLE IF EXISTS users");
            logger.info("   DROP TABLE IF EXISTS users -> Table dropped");

            // Drop again - still no error
            engine.execute("DROP TABLE IF EXISTS users");
            logger.info("   DROP TABLE IF EXISTS users -> Success (no error)\n");

            // 2. Multiple object types
            logger.info("2. IF EXISTS works with all object types:");
            logger.info("   " + "-".repeat(60));

            // Database
            engine.execute("DROP DATABASE IF EXISTS old_db");
            logger.info("   DROP DATABASE IF EXISTS old_db -> Success");

            // Schema
            engine.execute("DROP SCHEMA IF EXISTS old_schema");
            logger.info("   DROP SCHEMA IF EXISTS old_schema -> Success");

            // Table
            engine.execute("DROP TABLE IF EXISTS old_table");
            logger.info("   DROP TABLE IF EXISTS old_table -> Success");

            // View
            engine.execute("DROP VIEW IF EXISTS old_view");
            logger.info("   DROP VIEW IF EXISTS old_view -> Success");

            // Function
            engine.execute("DROP FUNCTION IF EXISTS old_func");
            logger.info("   DROP FUNCTION IF EXISTS old_func -> Success");

            // Procedure
            engine.execute("DROP PROCEDURE IF EXISTS old_proc");
            logger.info("   DROP PROCEDURE IF EXISTS old_proc -> Success");

            // Task
            engine.execute("DROP TASK IF EXISTS old_task");
            logger.info("   DROP TASK IF EXISTS old_task -> Success");

            // Stream
            engine.execute("DROP STREAM IF EXISTS old_stream");
            logger.info("   DROP STREAM IF EXISTS old_stream -> Success");

            // Warehouse
            engine.execute("DROP WAREHOUSE IF EXISTS old_wh");
            logger.info("   DROP WAREHOUSE IF EXISTS old_wh -> Success");

            // Stage
            engine.execute("DROP STAGE IF EXISTS old_stage");
            logger.info("   DROP STAGE IF EXISTS old_stage -> Success");

            // User
            engine.execute("DROP USER IF EXISTS old_user");
            logger.info("   DROP USER IF EXISTS old_user -> Success");

            // Role
            engine.execute("DROP ROLE IF EXISTS old_role");
            logger.info("   DROP ROLE IF EXISTS old_role -> Success\n");

            // 3. Idempotent cleanup scripts
            logger.info("3. Idempotent cleanup scripts (can run multiple times):");
            logger.info("   " + "-".repeat(60));

            // Create some objects
            engine.execute("CREATE TABLE t1 (id INTEGER)");
            engine.execute("CREATE TABLE t2 (id INTEGER)");
            engine.execute("CREATE VIEW v1 AS SELECT * FROM t1");
            logger.info("   Created: t1, t2, v1");

            // Cleanup script - can run multiple times without errors
            String cleanupScript = """
                DROP VIEW IF EXISTS v1;
                DROP TABLE IF EXISTS t1;
                DROP TABLE IF EXISTS t2;
                DROP TABLE IF EXISTS t3;
                """;

            logger.info("\n   Running cleanup script (first time):");
            for (String sql : cleanupScript.split(";")) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    engine.execute(sql);
                    logger.info("     " + sql + " -> Success");
                }
            }

            logger.info("\n   Running cleanup script (second time - idempotent):");
            for (String sql : cleanupScript.split(";")) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    engine.execute(sql);
                    logger.info("     " + sql + " -> Success (no error)");
                }
            }

            // 4. Comparison with regular DROP
            logger.info("\n4. Regular DROP vs DROP IF EXISTS:");
            logger.info("   " + "-".repeat(60));

            // Regular DROP fails
            try {
                engine.execute("DROP TABLE nonexistent_table");
                logger.info("   DROP TABLE nonexistent_table -> Unexpected success");
            } catch (final RuntimeException e) {
                logger.info("   DROP TABLE nonexistent_table -> Error: " + e.getMessage());
            }

            // DROP IF EXISTS succeeds
            engine.execute("DROP TABLE IF EXISTS nonexistent_table");
            logger.info("   DROP TABLE IF EXISTS nonexistent_table -> Success (no error)\n");

            // 5. Use case: Safe migrations
            logger.info("5. Use case: Safe database migrations:");
            logger.info("   " + "-".repeat(60));

            String migrationScript = """
                -- Safe to run multiple times
                DROP TABLE IF EXISTS old_users;
                DROP TABLE IF EXISTS legacy_data;
                DROP VIEW IF EXISTS deprecated_view;

                CREATE TABLE users_v2 (
                    id INTEGER PRIMARY KEY,
                    email VARCHAR NOT NULL,
                    created_at TIMESTAMP
                );
                """;

            logger.info("   Running migration script:");
            for (String sql : migrationScript.split(";")) {
                sql = sql.trim();
                if (!sql.isEmpty() && !sql.startsWith("--")) {
                    engine.execute(sql);
                    String shortSql = sql.length() > 50 ? sql.substring(0, 47) + "..." : sql;
                    logger.info("     " + shortSql);
                }
            }
            logger.info("   Migration completed successfully\n");

            // Cleanup
            engine.execute("DROP DATABASE IF EXISTS demo_db");
            engine.shutdown();

            logger.info("Example completed successfully!");

        } catch (final Exception e) {
            logger.error("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
