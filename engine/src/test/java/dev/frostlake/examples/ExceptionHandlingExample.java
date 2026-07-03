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
 * Example demonstrating exception handling in Snowflake SQL scripting.
 *
 * Snowflake supports exception handling in BEGIN/END blocks using the EXCEPTION clause.
 * This allows you to catch and handle errors that occur during execution.
 */
public class ExceptionHandlingExample {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandlingExample.class);

    @Test
    public void demonstrateBasicExceptionHandling() {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");
            engine.execute("CREATE TABLE employees (id INTEGER PRIMARY KEY, name VARCHAR)");
            engine.execute("CREATE TABLE error_log (operation VARCHAR, error_message VARCHAR)");

            logger.info("Example 1: Basic exception handling with OTHER");
            logger.info("=".repeat(50));

            // Try to insert duplicate primary key - exception will be caught
            engine.execute("""
                BEGIN
                    INSERT INTO employees VALUES (1, 'Alice');
                    INSERT INTO employees VALUES (1, 'Bob');
                EXCEPTION
                    WHEN OTHER THEN
                        INSERT INTO error_log VALUES ('Insert', 'Duplicate key error');
                END;
                """);

            ResultSet rs = engine.executeQuery("SELECT * FROM employees");
            logger.info("Employees table: {} rows", rs.getRows().size());
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Employee: id={}, name={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1));
            }

            rs = engine.executeQuery("SELECT * FROM error_log");
            logger.info("Error log: {} entries", rs.getRows().size());
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Error: operation={}, message={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1));
            }

            logger.info("");
            logger.info("Example 2: Exception handling with multiple statements in handler");
            logger.info("=".repeat(50));

            engine.execute("TRUNCATE TABLE employees");
            engine.execute("TRUNCATE TABLE error_log");

            // Handler with multiple statements
            engine.execute("""
                BEGIN
                    INSERT INTO employees VALUES (10, 'Charlie');
                    INSERT INTO employees VALUES (10, 'Diana');
                EXCEPTION
                    WHEN OTHER THEN
                        INSERT INTO error_log VALUES ('Batch insert', 'Error in batch');
                        INSERT INTO employees VALUES (11, 'Diana');
                END;
                """);

            rs = engine.executeQuery("SELECT * FROM employees ORDER BY id");
            logger.info("Employees table after recovery: {} rows", rs.getRows().size());
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Employee: id={}, name={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1));
            }

            logger.info("");
            logger.info("Example 3: Nested exception handling");
            logger.info("=".repeat(50));

            engine.execute("TRUNCATE TABLE employees");
            engine.execute("TRUNCATE TABLE error_log");

            // Nested blocks with exception handlers
            engine.execute("""
                BEGIN
                    INSERT INTO employees VALUES (1, 'Alice');

                    BEGIN
                        INSERT INTO employees VALUES (2, 'Bob');
                        INSERT INTO employees VALUES (2, 'Charlie');
                    EXCEPTION
                        WHEN OTHER THEN
                            INSERT INTO error_log VALUES ('Inner block', 'Inner error');
                            INSERT INTO employees VALUES (3, 'Charlie');
                    END;

                    INSERT INTO employees VALUES (4, 'Diana');
                EXCEPTION
                    WHEN OTHER THEN
                        INSERT INTO error_log VALUES ('Outer block', 'Outer error');
                END;
                """);

            rs = engine.executeQuery("SELECT * FROM employees ORDER BY id");
            logger.info("Employees table after nested handling: {} rows", rs.getRows().size());
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Employee: id={}, name={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1));
            }

            rs = engine.executeQuery("SELECT * FROM error_log");
            logger.info("Error log: {} entries", rs.getRows().size());
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Error: operation={}, message={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1));
            }

            logger.info("");
            logger.info("All examples completed successfully!");

        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void demonstrateExceptionHandlingUseCase() {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");
            engine.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, customer_id INTEGER, amount NUMBER)");
            engine.execute("CREATE TABLE failed_orders (id INTEGER, customer_id INTEGER, error VARCHAR)");

            logger.info("Use Case: Robust batch processing with error handling");
            logger.info("=".repeat(50));

            // Process multiple orders, logging failures without stopping
            engine.execute("""
                BEGIN
                    INSERT INTO orders VALUES (1, 100, 50.00);
                EXCEPTION
                    WHEN OTHER THEN
                        INSERT INTO failed_orders VALUES (1, 100, 'Failed to insert order');
                END;
                """);

            engine.execute("""
                BEGIN
                    INSERT INTO orders VALUES (2, 200, 75.50);
                EXCEPTION
                    WHEN OTHER THEN
                        INSERT INTO failed_orders VALUES (2, 200, 'Failed to insert order');
                END;
                """);

            // This one will fail (duplicate key)
            engine.execute("""
                BEGIN
                    INSERT INTO orders VALUES (1, 300, 100.00);
                EXCEPTION
                    WHEN OTHER THEN
                        INSERT INTO failed_orders VALUES (1, 300, 'Duplicate order ID');
                END;
                """);

            engine.execute("""
                BEGIN
                    INSERT INTO orders VALUES (3, 400, 25.00);
                EXCEPTION
                    WHEN OTHER THEN
                        INSERT INTO failed_orders VALUES (3, 400, 'Failed to insert order');
                END;
                """);

            ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM orders");
            logger.info("Successfully processed orders: {}", rs.getRows().get(0).getValue(0));

            rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM failed_orders");
            logger.info("Failed orders: {}", rs.getRows().get(0).getValue(0));

            rs = engine.executeQuery("SELECT * FROM failed_orders");
            for (int i = 0; i < rs.getRows().size(); i++) {
                logger.info("  Failed order: id={}, customer_id={}, error={}",
                    rs.getRows().get(i).getValue(0),
                    rs.getRows().get(i).getValue(1),
                    rs.getRows().get(i).getValue(2));
            }

        } finally {
            engine.shutdown();
        }
    }
}
