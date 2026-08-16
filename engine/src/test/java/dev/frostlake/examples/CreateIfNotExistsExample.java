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
 * Example demonstrating CREATE IF NOT EXISTS statements
 */
public final class CreateIfNotExistsExample {

    /** Static helpers only — never instantiated. */
    private CreateIfNotExistsExample() {
    }
    private static final Logger logger = LoggerFactory.getLogger(CreateIfNotExistsExample.class);

    public static void main(final String[] args) {
        try {
            logger.info("=== CREATE IF NOT EXISTS Example ===\n");

            final DatabaseEngine engine = new DatabaseEngine();

            // 1. Idempotent database setup
            logger.info("1. Idempotent Database Setup:");
            logger.info("   " + "-".repeat(60));

            // Can run this multiple times safely
            engine.execute("CREATE DATABASE IF NOT EXISTS app_db");
            logger.info("   CREATE DATABASE IF NOT EXISTS app_db -> Created");

            // Run again - no error
            engine.execute("CREATE DATABASE IF NOT EXISTS app_db");
            logger.info("   CREATE DATABASE IF NOT EXISTS app_db -> Already exists (no error)");

            // Use the database
            engine.execute("USE DATABASE app_db");
            logger.info("   Using database: app_db\n");

            // 2. Idempotent schema setup
            logger.info("2. Idempotent Schema Setup:");
            logger.info("   " + "-".repeat(60));

            engine.execute("CREATE SCHEMA IF NOT EXISTS app_schema");
            logger.info("   CREATE SCHEMA IF NOT EXISTS app_schema -> Created");

            engine.execute("CREATE SCHEMA IF NOT EXISTS app_schema");
            logger.info("   CREATE SCHEMA IF NOT EXISTS app_schema -> Already exists (no error)\n");

            // 3. Idempotent table setup
            logger.info("3. Idempotent Table Setup:");
            logger.info("   " + "-".repeat(60));

            final String createUsers = """
                    CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY,
                    email VARCHAR NOT NULL,
                    created_at TIMESTAMP
                    )""";

            engine.execute(createUsers);
            logger.info("   CREATE TABLE IF NOT EXISTS users -> Created");

            engine.execute(createUsers);
            logger.info("   CREATE TABLE IF NOT EXISTS users -> Already exists (no error)\n");

            // 4. Complete idempotent setup script
            logger.info("4. Complete Idempotent Setup Script:");
            logger.info("   " + "-".repeat(60));

            final String setupScript = """
                    CREATE DATABASE IF NOT EXISTS prod_db;
                    CREATE SCHEMA IF NOT EXISTS prod_db.app;
                    CREATE TABLE IF NOT EXISTS customers (
                        id INTEGER,
                        name VARCHAR,
                        email VARCHAR
                    );
                    CREATE VIEW IF NOT EXISTS active_customers AS
                        SELECT * FROM customers WHERE email IS NOT NULL;
                    CREATE FUNCTION IF NOT EXISTS validate_email(email VARCHAR)
                        RETURNS BOOLEAN AS 'email LIKE ''%@%''';
                    CREATE WAREHOUSE IF NOT EXISTS etl_wh;
                    """;

            logger.info("   Running setup script (first time):");
            for (String sql : setupScript.split(";")) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    engine.execute(sql);
                    final String shortSql = sql.length() > 50 ? sql.substring(0, 47) + "..." : sql;
                    logger.info("     \u2713 " + shortSql);
                }
            }

            logger.info("\n   Running setup script (second time - idempotent):");
            for (String sql : setupScript.split(";")) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    engine.execute(sql);
                    final String shortSql = sql.length() > 50 ? sql.substring(0, 47) + "..." : sql;
                    logger.info("     \u2713 " + shortSql);
                }
            }
            logger.info("   No errors!");

            // 5. Use case: CI/CD deployments
            logger.info("\n5. Use Case: CI/CD Deployment Scripts:");
            logger.info("   " + "-".repeat(60));

            final String deploymentScript = """
                    -- Safe to run on every deployment
                    CREATE DATABASE IF NOT EXISTS staging_db;
                    CREATE SCHEMA IF NOT EXISTS staging_db.api;
                    CREATE TABLE IF NOT EXISTS api_logs (
                        timestamp TIMESTAMP,
                        endpoint VARCHAR,
                        status_code INTEGER,
                        response_time INTEGER
                    );
                    CREATE VIEW IF NOT EXISTS slow_requests AS
                        SELECT * FROM api_logs WHERE response_time > 1000;
                    """;

            logger.info("   Deploying to staging environment:");
            for (String sql : deploymentScript.split(";")) {
                sql = sql.trim();
                if (!sql.isEmpty() && !sql.startsWith("--")) {
                    engine.execute(sql);
                    final String shortSql = sql.length() > 45 ? sql.substring(0, 42) + "..." : sql;
                    logger.info("     \u2713 " + shortSql);
                }
            }
            logger.info("   Deployment completed successfully!");

            // 6. Combining CREATE IF NOT EXISTS with DROP IF EXISTS
            logger.info("\n6. Safe Schema Migration Pattern:");
            logger.info("   " + "-".repeat(60));

            final String migrationScript = """
                    -- Safe migration: replace old objects with new ones
                    DROP VIEW IF EXISTS old_dashboard;
                    CREATE VIEW IF NOT EXISTS new_dashboard AS
                        SELECT id, name FROM users WHERE created_at > '2024-01-01';

                    DROP TABLE IF EXISTS temp_staging;
                    CREATE TABLE IF NOT EXISTS data_staging (
                        id INTEGER,
                        data VARCHAR,
                        loaded_at TIMESTAMP
                    );
                    """;

            logger.info("   Running migration:");
            for (String sql : migrationScript.split(";")) {
                sql = sql.trim();
                if (!sql.isEmpty() && !sql.startsWith("--")) {
                    engine.execute(sql);
                    final String shortSql = sql.length() > 50 ? sql.substring(0, 47) + "..." : sql;
                    logger.info("     \u2713 " + shortSql);
                }
            }
            logger.info("   Migration completed!\n");

            // 7. Benefits summary
            logger.info("7. Benefits of IF NOT EXISTS:");
            logger.info("   " + "-".repeat(60));
            logger.info("   \u2713 Scripts can be run multiple times safely");
            logger.info("   \u2713 No need to check for object existence first");
            logger.info("   \u2713 Ideal for CI/CD pipelines");
            logger.info("   \u2713 Simplifies environment setup");
            logger.info("   \u2713 Reduces error handling code");
            logger.info("   \u2713 Makes scripts truly idempotent\n");

            // Cleanup
            engine.execute("DROP DATABASE IF EXISTS prod_db");
            engine.execute("DROP DATABASE IF EXISTS staging_db");
            engine.execute("DROP DATABASE IF EXISTS app_db");
            engine.shutdown();

            logger.info("Example completed successfully!");

        } catch (final Exception e) {
            logger.error("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
