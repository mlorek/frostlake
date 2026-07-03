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
 * Example demonstrating Common Table Expressions (CTEs) with WITH clause
 */
public class CTEExample {
    private static final Logger logger = LoggerFactory.getLogger(CTEExample.class);

    public static void main(final String[] args) {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            logger.info("=== Common Table Expressions (CTE) Examples ===\n");

            // Setup test data
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, dept_id INTEGER, salary INTEGER)");
            engine.execute("INSERT INTO employees VALUES (1, 'Alice', 10, 50000)");
            engine.execute("INSERT INTO employees VALUES (2, 'Bob', 20, 60000)");
            engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 10, 55000)");
            engine.execute("INSERT INTO employees VALUES (4, 'Diana', 30, 70000)");
            engine.execute("INSERT INTO employees VALUES (5, 'Eve', 20, 65000)");

            engine.execute("CREATE TABLE departments (id INTEGER, name VARCHAR)");
            engine.execute("INSERT INTO departments VALUES (10, 'Engineering')");
            engine.execute("INSERT INTO departments VALUES (20, 'Sales')");
            engine.execute("INSERT INTO departments VALUES (30, 'Marketing')");

            logger.info("Test data created\n");

            // Example 1: Simple CTE
            logger.info("1. Simple CTE - High Earners:");
            logger.info("   {}", "-".repeat(60));

            ResultSet result1 = engine.executeQuery("""
                WITH high_earners AS (
                    SELECT * FROM employees WHERE salary > 55000
                )
                SELECT name, salary FROM high_earners ORDER BY salary DESC
                """);

            logger.info("   High earners (salary > 55000):");
            for (final Row row : result1.getRows()) {
                logger.info("   - {}: ${}", row.getValue(0), row.getValue(1));
            }
            logger.info("");

            // Example 2: CTE with JOIN
            logger.info("2. CTE with JOIN:");
            logger.info("   {}", "-".repeat(60));

            ResultSet result2 = engine.executeQuery("""
                WITH eng_employees AS (
                    SELECT * FROM employees WHERE dept_id = 10
                )
                SELECT e.name, d.name as dept_name, e.salary
                FROM eng_employees e
                JOIN departments d ON e.dept_id = d.id
                """);

            logger.info("   Engineering employees:");
            for (final Row row : result2.getRows()) {
                logger.info("   - {} ({}): ${}", row.getValue(0), row.getValue(1), row.getValue(2));
            }
            logger.info("");

            // Example 3: Multiple CTEs
            logger.info("3. Multiple CTEs:");
            logger.info("   {}", "-".repeat(60));

            ResultSet result3 = engine.executeQuery("""
                WITH
                    engineering AS (
                        SELECT * FROM employees WHERE dept_id = 10
                    ),
                    high_salary AS (
                        SELECT * FROM employees WHERE salary >= 60000
                    )
                SELECT e.name as eng_name, h.name as high_earner_name, h.salary
                FROM engineering e
                CROSS JOIN high_salary h
                """);

            logger.info("   Engineering employees paired with high earners:");
            for (final Row row : result3.getRows()) {
                logger.info("   - {} paired with {} (${}) ", row.getValue(0), row.getValue(1), row.getValue(2));
            }
            logger.info("");

            // Example 4: CTE with Aggregation
            logger.info("4. CTE with Aggregation:");
            logger.info("   {}", "-".repeat(60));

            ResultSet result4 = engine.executeQuery("""
                WITH dept_stats AS (
                    SELECT dept_id, COUNT(*) as emp_count, AVG(salary) as avg_salary
                    FROM employees
                    GROUP BY dept_id
                )
                SELECT d.name, s.emp_count, s.avg_salary
                FROM dept_stats s
                JOIN departments d ON s.dept_id = d.id
                ORDER BY s.avg_salary DESC
                """);

            logger.info("   Department statistics:");
            for (final Row row : result4.getRows()) {
                logger.info("   - {}: {} employees, avg salary: ${}",
                    row.getValue(0),
                    row.getValue(1),
                    String.format("%.2f", ((Number) row.getValue(2)).doubleValue()));
            }
            logger.info("");

            // Example 5: CTE with UNION
            logger.info("5. CTE with UNION:");
            logger.info("   {}", "-".repeat(60));

            ResultSet result5 = engine.executeQuery("""
                WITH
                    top_engineering AS (
                        SELECT name, salary FROM employees
                        WHERE dept_id = 10
                        ORDER BY salary DESC
                        LIMIT 1
                    ),
                    top_sales AS (
                        SELECT name, salary FROM employees
                        WHERE dept_id = 20
                        ORDER BY salary DESC
                        LIMIT 1
                    )
                SELECT 'Engineering' as dept, name, salary FROM top_engineering
                UNION ALL
                SELECT 'Sales' as dept, name, salary FROM top_sales
                """);

            logger.info("   Top earner from each department:");
            for (final Row row : result5.getRows()) {
                logger.info("   - {} department: {} (${}) ", row.getValue(0), row.getValue(1), row.getValue(2));
            }
            logger.info("");

            // Example 6: CTE referenced multiple times
            logger.info("6. CTE Referenced Multiple Times:");
            logger.info("   {}", "-".repeat(60));

            ResultSet result6 = engine.executeQuery("""
                WITH high_salary AS (
                    SELECT * FROM employees WHERE salary >= 60000
                )
                SELECT h1.name as employee1, h2.name as employee2, h1.salary as salary1, h2.salary as salary2
                FROM high_salary h1
                CROSS JOIN high_salary h2
                WHERE h1.id < h2.id
                """);

            logger.info("   High earner pairs:");
            for (final Row row : result6.getRows()) {
                logger.info("   - {} (${}) and {} (${})",
                    row.getValue(0), row.getValue(2),
                    row.getValue(1), row.getValue(3));
            }
            logger.info("");

            logger.info("=== All CTE Examples Completed Successfully! ===");

        } catch (final Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
        } finally {
            engine.shutdown();
        }
    }
}
