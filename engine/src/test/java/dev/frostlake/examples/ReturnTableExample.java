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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReturnTableExample {

    /** Static helpers only — never instantiated. */
    private ReturnTableExample() {
    }
    private static final Logger logger = LoggerFactory.getLogger(ReturnTableExample.class);

    public static void main(final String[] args) {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== RETURN TABLE Examples ===\n");

            // Create sample data
            logger.info("1. Creating sample data");
            engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary INTEGER)");
            engine.execute("INSERT INTO employees VALUES (1, 'Alice', 70000)");
            engine.execute("INSERT INTO employees VALUES (2, 'Bob', 80000)");
            engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 90000)");
            logger.info("   Created 3 employees\n");

            // Example 1: Simple RETURN TABLE
            logger.info("2. Using RETURN TABLE with EXECUTE IMMEDIATE");
            final ResultSet rs1 = engine.executeQuery("""
                DECLARE
                  res RESULTSET;
                  select_statement VARCHAR;
                BEGIN
                  select_statement := 'select * from employees';
                  res := EXECUTE IMMEDIATE select_statement;
                  RETURN TABLE(res);
                END;
                """);

            logger.info("   Returned " + rs1.getRowCount() + " rows:");
            for (final var row : rs1.getRows()) {
                logger.info("     ID: " + row.getValue(0) +
                                 ", Name: " + row.getValue(1) +
                                 ", Salary: " + row.getValue(2));
            }
            logger.info("");

            // Example 2: Dynamic query with filtering
            logger.info("3. Dynamic Query with Filtering");
            final ResultSet rs2 = engine.executeQuery("""
                DECLARE
                  res RESULTSET;
                  query_text VARCHAR;
                BEGIN
                  query_text := 'SELECT * FROM employees WHERE salary >= 75000';
                  res := EXECUTE IMMEDIATE query_text;
                  RETURN TABLE(res);
                END;
                """);

            logger.info("   Employees with salary >= 75000:");
            for (final var row : rs2.getRows()) {
                logger.info("     " + row.getValue(1) + ": $" + row.getValue(2));
            }
            logger.info("");

            logger.info("=== RETURN TABLE Demo Complete ===");
            logger.info("\nKey Points:");
            logger.info("✅ EXECUTE IMMEDIATE result must be assigned to a RESULTSET variable");
            logger.info("✅ TABLE() function takes the RESULTSET variable as argument");
            logger.info("✅ RETURN TABLE returns the full result set from BEGIN/END block");
            logger.info("✅ Enables dynamic query construction and execution");

        } finally {
            engine.shutdown();
        }
    }
}
