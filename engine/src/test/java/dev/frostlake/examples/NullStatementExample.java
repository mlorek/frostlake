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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example demonstrating the use of NULL statement in Snowflake SQL scripting.
 *
 * In Snowflake, the NULL statement is a no-op (no operation) statement that does nothing.
 * It's useful in procedural code when you need a statement syntactically but don't want
 * to perform any action.
 */
public class NullStatementExample {

    private static final Logger logger = LoggerFactory.getLogger(NullStatementExample.class);

    @Test
    public void demonstrateNullStatement() {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");
            engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, active BOOLEAN)");

            // Example 1: Simple NULL statement
            logger.info("Example 1: Simple NULL statement");
            engine.execute("NULL;");
            logger.info("NULL statement executed (does nothing)\n");

            // Example 2: NULL statement in a sequence
            logger.info("Example 2: NULL in a sequence of statements");
            engine.execute("INSERT INTO employees VALUES (1, 'Alice', true)");
            engine.execute("NULL;"); // Does nothing
            engine.execute("INSERT INTO employees VALUES (2, 'Bob', true)");
            logger.info("Inserted 2 employees with NULL statement in between\n");

            // Example 3: NULL statement in BEGIN/END block
            logger.info("Example 3: NULL in BEGIN/END block");
            engine.execute("""
                BEGIN
                    INSERT INTO employees VALUES (3, 'Charlie', false);
                    NULL;
                    INSERT INTO employees VALUES (4, 'Diana', true);
                END;
                """);
            logger.info("BEGIN/END block executed with NULL statement\n");

            // Example 4: Multiple NULL statements
            logger.info("Example 4: Multiple NULL statements");
            engine.execute("""
                BEGIN
                    NULL;
                    NULL;
                    NULL;
                END;
                """);
            logger.info("Multiple NULL statements executed (all do nothing)\n");

            // Example 5: NULL without semicolon
            logger.info("Example 5: NULL without semicolon");
            engine.execute("""
                BEGIN
                    NULL;
                END;
                """);
            logger.info("NULL statement without semicolon executed\n");

            logger.info("All examples completed successfully!");
            logger.info("Total employees in table: " +
                engine.executeQuery("SELECT COUNT(*) FROM employees")
                    .getRows().get(0).getValue(0));

        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void demonstrateNullStatementUseCase() {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");
            engine.execute("CREATE TABLE audit_log (id INTEGER, message VARCHAR)");

            logger.info("Use Case: NULL statement in conditional logic");
            logger.info("=========================================\n");

            // Simulating conditional logic where NULL might be used
            // In a real scenario, this might be inside an IF statement where
            // one branch needs to do nothing

            logger.info("Processing records...");

            // Record 1: Do something
            engine.execute("INSERT INTO audit_log VALUES (1, 'Processing record 1')");
            logger.info("Record 1: Action taken");

            // Record 2: Do nothing (NULL statement)
            engine.execute("NULL;");
            logger.info("Record 2: No action needed (NULL statement)");

            // Record 3: Do something
            engine.execute("INSERT INTO audit_log VALUES (3, 'Processing record 3')");
            logger.info("Record 3: Action taken");

            logger.info("\nAudit log entries: " +
                engine.executeQuery("SELECT COUNT(*) FROM audit_log")
                    .getRows().get(0).getValue(0));

        } finally {
            engine.shutdown();
        }
    }
}
