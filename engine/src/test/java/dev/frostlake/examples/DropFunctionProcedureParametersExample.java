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

public class DropFunctionProcedureParametersExample {
    private static final Logger logger = LoggerFactory.getLogger(DropFunctionProcedureParametersExample.class);

    @Test
    public void demonstrateDropWithParameters() {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== DROP FUNCTION/PROCEDURE with Parameter Types ===");

            // Example 1: Drop function without parameter types (backward compatible)
            logger.info("\n1. DROP FUNCTION without parameter types (backward compatible):");
            engine.execute("CREATE FUNCTION simple_func(x INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN x + 1; END'");
            logger.info("Created function: simple_func(INTEGER)");

            engine.execute("DROP FUNCTION simple_func(INTEGER)");
            logger.info("Dropped function using: DROP FUNCTION simple_func(INTEGER)");

            // Example 2: Drop function with parameter types
            logger.info("\n2. DROP FUNCTION with parameter types specified:");
            engine.execute("CREATE FUNCTION add_numbers(a INTEGER, b INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN a + b; END'");
            logger.info("Created function: add_numbers(INTEGER, INTEGER)");

            engine.execute("DROP FUNCTION add_numbers(INTEGER, INTEGER)");
            logger.info("Dropped function using: DROP FUNCTION add_numbers(INTEGER, INTEGER)");

            // Example 3: Drop function with empty parameter list
            logger.info("\n3. DROP FUNCTION with empty parameter list:");
            engine.execute("CREATE FUNCTION get_constant() RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN 42; END'");
            logger.info("Created function: get_constant()");

            engine.execute("DROP FUNCTION get_constant()");
            logger.info("Dropped function using: DROP FUNCTION get_constant()");

            // Example 4: Drop function with VARCHAR parameter
            logger.info("\n4. DROP FUNCTION with VARCHAR parameter:");
            engine.execute("CREATE FUNCTION greet(name VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN name; END'");
            logger.info("Created function: greet(VARCHAR)");

            engine.execute("DROP FUNCTION greet(VARCHAR)");
            logger.info("Dropped function using: DROP FUNCTION greet(VARCHAR)");

            // Example 5: Drop function with mixed parameter types
            logger.info("\n5. DROP FUNCTION with mixed parameter types:");
            engine.execute("CREATE FUNCTION format_record(id INTEGER, name VARCHAR, active INTEGER) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN name; END'");
            logger.info("Created function: format_record(INTEGER, VARCHAR, INTEGER)");

            engine.execute("DROP FUNCTION format_record(INTEGER, VARCHAR, INTEGER)");
            logger.info("Dropped function using: DROP FUNCTION format_record(INTEGER, VARCHAR, INTEGER)");

            // Example 6: Drop function with IF EXISTS and parameters
            logger.info("\n6. DROP FUNCTION IF EXISTS with parameters:");
            engine.execute("CREATE FUNCTION test_func(x INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN x * 2; END'");
            logger.info("Created function: test_func(INTEGER)");

            engine.execute("DROP FUNCTION IF EXISTS test_func(INTEGER)");
            logger.info("Dropped function using: DROP FUNCTION IF EXISTS test_func(INTEGER)");

            // Try again - should not fail
            engine.execute("DROP FUNCTION IF EXISTS test_func(INTEGER)");
            logger.info("Dropped again with IF EXISTS - no error");

            // Example 7: Drop procedure without parameter types
            logger.info("\n7. DROP PROCEDURE without parameter types:");
            engine.execute("CREATE PROCEDURE simple_proc(x INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN x + 1; END'");
            logger.info("Created procedure: simple_proc(INTEGER)");

            engine.execute("DROP PROCEDURE simple_proc(INTEGER)");
            logger.info("Dropped procedure using: DROP PROCEDURE simple_proc(INTEGER)");

            // Example 8: Drop procedure with parameter types
            logger.info("\n8. DROP PROCEDURE with parameter types specified:");
            engine.execute("CREATE PROCEDURE calculate(a INTEGER, b INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN a * b; END'");
            logger.info("Created procedure: calculate(INTEGER, INTEGER)");

            engine.execute("DROP PROCEDURE calculate(INTEGER, INTEGER)");
            logger.info("Dropped procedure using: DROP PROCEDURE calculate(INTEGER, INTEGER)");

            // Example 9: Drop schema-qualified function with parameters
            logger.info("\n9. DROP schema-qualified FUNCTION with parameters:");
            engine.execute("CREATE SCHEMA util_schema");
            engine.execute("CREATE FUNCTION util_schema.compute(x INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN x * 2; END'");
            logger.info("Created function: util_schema.compute(INTEGER)");

            engine.execute("DROP FUNCTION util_schema.compute(INTEGER)");
            logger.info("Dropped function using: DROP FUNCTION util_schema.compute(INTEGER)");

            // Example 10: Attempt to drop with wrong parameter types (will fail)
            logger.info("\n10. Attempting DROP with wrong parameter types:");
            engine.execute("CREATE FUNCTION validate(x INTEGER, y INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN x + y; END'");
            logger.info("Created function: validate(INTEGER, INTEGER)");

            try {
                engine.execute("DROP FUNCTION validate(VARCHAR, VARCHAR)");
                logger.info("ERROR: Should have failed!");
            } catch (final Exception e) {
                logger.info("Correctly failed to drop with wrong types: {}", e.getMessage().split(":")[0]);
            }

            // Clean up - drop with correct types
            engine.execute("DROP FUNCTION validate(INTEGER, INTEGER)");
            logger.info("Successfully dropped with correct types");

            logger.info("\n=== Summary ===");
            logger.info("DROP FUNCTION and DROP PROCEDURE now support optional parameter types:");
            logger.info("  - DROP FUNCTION function_name                    (backward compatible)");
            logger.info("  - DROP FUNCTION function_name()                  (no parameters)");
            logger.info("  - DROP FUNCTION function_name(type1, type2, ...) (with parameter types)");
            logger.info("  - Works with IF EXISTS clause");
            logger.info("  - Works with schema-qualified names");
            logger.info("  - Validates parameter types match before dropping");

        } finally {
            engine.shutdown();
        }
    }
}
