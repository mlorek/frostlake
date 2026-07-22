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
 * Example demonstrating INFORMATION_SCHEMA system views
 */
public class InformationSchemaExample {
    private static final Logger logger = LoggerFactory.getLogger(InformationSchemaExample.class);

    public static void main(final String[] args) {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            logger.info("=== INFORMATION_SCHEMA System Views ===\n");

            // Setup
            engine.execute("DROP DATABASE IF EXISTS demo_db");
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");
            engine.execute("CREATE SCHEMA analytics");
            engine.execute("USE SCHEMA PUBLIC");

            // Create tables
            engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary DECIMAL, dept_id INTEGER)");
            engine.execute("CREATE TABLE departments (id INTEGER, name VARCHAR, budget DECIMAL)");

            // Create a view
            engine.execute("CREATE VIEW high_earners AS SELECT * FROM employees WHERE salary > 100000");

            // 1. Query all databases
            logger.info("1. Query INFORMATION_SCHEMA.DATABASES:");
            logger.info("   {}", "-".repeat(60));
            ResultSet databases = engine.executeQuery("SELECT DATABASE_NAME FROM INFORMATION_SCHEMA.DATABASES ORDER BY DATABASE_NAME");
            for (final Row row : databases.getRows()) {
                logger.info("   - Database: {}", row.getValue(0));
            }
            logger.info("");

            // 2. Query all schemas in current database
            logger.info("2. Query INFORMATION_SCHEMA.SCHEMATA:");
            logger.info("   {}", "-".repeat(60));
            ResultSet schemas = engine.executeQuery(
                "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE CATALOG_NAME = 'DEMO_DB'"
            );
            for (final Row row : schemas.getRows()) {
                logger.info("   - Schema: {}", row.getValue(0));
            }
            logger.info("");

            // 3. Query all tables
            logger.info("3. Query INFORMATION_SCHEMA.TABLES:");
            logger.info("   {}", "-".repeat(60));
            ResultSet tables = engine.executeQuery(
                "SELECT TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_CATALOG = 'DEMO_DB' AND TABLE_SCHEMA = 'PUBLIC'"
            );
            for (final Row row : tables.getRows()) {
                logger.info("   - {}: {}", row.getValue(0), row.getValue(1));
            }
            logger.info("");

            // 4. Query columns for a specific table
            logger.info("4. Query INFORMATION_SCHEMA.COLUMNS for employees table:");
            logger.info("   {}", "-".repeat(60));
            ResultSet columns = engine.executeQuery("""
                SELECT COLUMN_NAME, DATA_TYPE, ORDINAL_POSITION
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'EMPLOYEES'
                ORDER BY ORDINAL_POSITION
                """);
            for (final Row row : columns.getRows()) {
                logger.info("   Column {}: {} ({})", row.getValue(2), row.getValue(0), row.getValue(1));
            }
            logger.info("");

            // 5. Query all views
            logger.info("5. Query INFORMATION_SCHEMA.VIEWS:");
            logger.info("   {}", "-".repeat(60));
            ResultSet views = engine.executeQuery(
                "SELECT TABLE_NAME, VIEW_DEFINITION FROM INFORMATION_SCHEMA.VIEWS"
            );
            for (final Row row : views.getRows()) {
                logger.info("   - View: {}", row.getValue(0));
                logger.info("     Definition: {}", row.getValue(1));
            }
            logger.info("");

            // 6. Join TABLES and COLUMNS to show table structure
            logger.info("6. Join TABLES and COLUMNS for comprehensive view:");
            logger.info("   {}", "-".repeat(60));
            ResultSet tableStructure = engine.executeQuery("""
                SELECT t.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
                FROM INFORMATION_SCHEMA.TABLES t
                INNER JOIN INFORMATION_SCHEMA.COLUMNS c
                  ON t.TABLE_NAME = c.TABLE_NAME
                WHERE t.TABLE_TYPE = 'BASE TABLE' AND t.TABLE_CATALOG = 'DEMO_DB'
                """);
            String currentTable = null;
            for (final Row row : tableStructure.getRows()) {
                String tableName = (String) row.getValue(0);
                if (!tableName.equals(currentTable)) {
                    if (currentTable != null) logger.info("");
                    logger.info("   Table: {}", tableName);
                    currentTable = tableName;
                }
                logger.info("     - {} ({})", row.getValue(1), row.getValue(2));
            }
            logger.info("");

            // 7. Count objects by type
            logger.info("7. Object counts:");
            logger.info("   {}", "-".repeat(60));
            ResultSet dbCount = engine.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.DATABASES");
            logger.info("   - Databases: {}", dbCount.getRows().get(0).getValue(0));

            ResultSet tableCount = engine.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'");
            logger.info("   - Tables: {}", tableCount.getRows().get(0).getValue(0));

            ResultSet viewCount = engine.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS");
            logger.info("   - Views: {}", viewCount.getRows().get(0).getValue(0));
            logger.info("");

            // 8. Benefits
            logger.info("8. INFORMATION_SCHEMA Benefits:");
            logger.info("   {}", "-".repeat(60));
            logger.info("   ✓ Standardized metadata access across databases");
            logger.info("   ✓ Query database structure using SQL");
            logger.info("   ✓ Integrate with application introspection");
            logger.info("   ✓ Generate documentation automatically");
            logger.info("   ✓ Compatible with Snowflake SQL dialect");
            logger.info("   ✓ Read-only system views (cannot be modified)");
            logger.info("   ✓ Always up-to-date with current catalog state");
            logger.info("   ✓ INFORMATION_SCHEMA exists in every database\n");

            engine.execute("DROP DATABASE demo_db");
            engine.shutdown();

            logger.info("=== Example Complete ===");

        } catch (final Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
        }
    }
}
