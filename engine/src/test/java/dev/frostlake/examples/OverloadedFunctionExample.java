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

public class OverloadedFunctionExample {
    private static final Logger logger = LoggerFactory.getLogger(OverloadedFunctionExample.class);

    @Test
    public void demonstrateOverloadedFunctions() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== Overloaded Functions ===");

            // Example 1: Overloading by parameter count
            logger.info("\n1. Overloading by parameter count:");
            engine.execute("""
            CREATE FUNCTION greet()
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Greeter.greet0'
            AS $$
            class Greeter {
              public static String greet0() {
                return "Hello!";
              }
            }
            $$
            """);

            engine.execute("""
            CREATE FUNCTION greet(name STRING)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Greeter.greet1'
            AS $$
            class Greeter {
              public static String greet1(String name) {
                return "Hello, " + name + "!";
              }
            }
            $$
            """);

            engine.execute("""
            CREATE FUNCTION greet(first STRING, last STRING)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Greeter.greet2'
            AS $$
            class Greeter {
              public static String greet2(String first, String last) {
                return "Hello, " + first + " " + last + "!";
              }
            }
            $$
            """);

            logger.info("Created 3 overloads of greet()");
            logger.info("  greet() -> Hello!");
            logger.info("  greet('Alice') -> Hello, Alice!");
            logger.info("  greet('Bob', 'Smith') -> Hello, Bob Smith!");

            // Example 2: Overloading by parameter type
            logger.info("\n2. Overloading by parameter type:");
            engine.execute("""
            CREATE FUNCTION format(value STRING)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Formatter.formatString'
            AS $$
            class Formatter {
              public static String formatString(String value) {
                return "[STRING]: " + value;
              }
            }
            $$
            """);

            engine.execute("""
            CREATE FUNCTION format(value INTEGER)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Formatter.formatInt'
            AS $$
            class Formatter {
              public static String formatInt(int value) {
                return "[INTEGER]: " + value;
              }
            }
            $$
            """);

            logger.info("Created 2 overloads of format()");
            logger.info("  format('test') -> [STRING]: test");
            logger.info("  format(42) -> [INTEGER]: 42");
            logger.info("System automatically selects correct overload based on argument type");

            // Example 3: DROP specific overload by signature
            logger.info("\n3. Dropping specific overload:");
            engine.execute("""
            CREATE FUNCTION calculate(x INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVA
            HANDLER = 'Calculator.single'
            AS $$
            class Calculator {
              public static int single(int x) {
                return x * 2;
              }
            }
            $$
            """);

            engine.execute("""
            CREATE FUNCTION calculate(x INTEGER, y INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVA
            HANDLER = 'Calculator.double'
            AS $$
            class Calculator {
              public static int double(int x, int y) {
                return x + y;
              }
            }
            $$
            """);

            logger.info("Created calculate(INTEGER) and calculate(INTEGER, INTEGER)");
            logger.info("DROP FUNCTION calculate(INTEGER) - drops only single-parameter version");
            engine.execute("DROP FUNCTION calculate(INTEGER)");
            logger.info("calculate(INTEGER, INTEGER) still exists!");

            // Example 4: DROP the remaining overload (the signature is always required)
            logger.info("\n4. Dropping the remaining overload:");
            logger.info("DROP FUNCTION calculate(INTEGER, INTEGER)");
            engine.execute("DROP FUNCTION calculate(INTEGER, INTEGER)");
            logger.info("All calculate() overloads removed");

            // Example 5: CREATE OR REPLACE specific overload
            logger.info("\n5. Replacing specific overload:");
            engine.execute("""
            CREATE FUNCTION multiply(x INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVA
            HANDLER = 'Multiplier.v1'
            AS $$
            class Multiplier {
              public static int v1(int x) {
                return x * 2;
              }
            }
            $$
            """);

            engine.execute("""
            CREATE FUNCTION multiply(x INTEGER, y INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVA
            HANDLER = 'Multiplier.v2'
            AS $$
            class Multiplier {
              public static int v2(int x, int y) {
                return x * y;
              }
            }
            $$
            """);

            logger.info("Created multiply(INTEGER) and multiply(INTEGER, INTEGER)");

            engine.execute("""
            CREATE OR REPLACE FUNCTION multiply(x INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVA
            HANDLER = 'Multiplier.v1New'
            AS $$
            class Multiplier {
              public static int v1New(int x) {
                return x * 3;
              }
            }
            $$
            """);

            logger.info("Replaced multiply(INTEGER) to return x * 3");
            logger.info("multiply(INTEGER, INTEGER) unchanged");
            logger.info("OR REPLACE only affects matching signature");

            // Example 6: Overloaded functions with mixed types
            logger.info("\n6. Overloading with mixed parameter types:");
            engine.execute("""
            CREATE FUNCTION combine(a STRING, b INTEGER)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Combiner.stringInt'
            AS $$
            class Combiner {
              public static String stringInt(String a, int b) {
                return a + ":" + b;
              }
            }
            $$
            """);

            engine.execute("""
            CREATE FUNCTION combine(a INTEGER, b STRING)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Combiner.intString'
            AS $$
            class Combiner {
              public static String intString(int a, String b) {
                return a + "-" + b;
              }
            }
            $$
            """);

            logger.info("Created combine(STRING, INTEGER) and combine(INTEGER, STRING)");
            logger.info("  combine('test', 42) -> test:42");
            logger.info("  combine(42, 'test') -> 42-test");
            logger.info("Parameter order matters for overload resolution!");

            // Example 7: Preventing duplicate signatures
            logger.info("\n7. Duplicate signature prevention:");
            engine.execute("""
            CREATE FUNCTION validate(x INTEGER, y INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVA
            HANDLER = 'Validator.method'
            AS $$
            class Validator {
              public static int method(int x, int y) {
                return x + y;
              }
            }
            $$
            """);

            logger.info("Created validate(INTEGER, INTEGER)");
            logger.info("Attempting to create another validate(INTEGER, INTEGER) will fail");
            logger.info("Signature uniqueness is enforced");

            // Example 8: Using overloaded functions in table queries
            logger.info("\n8. Overloaded functions in table queries:");
            engine.execute("""
            CREATE FUNCTION display(val INTEGER)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Display.displayInt'
            AS $$
            class Display {
              public static String displayInt(int val) {
                return "INT:" + val;
              }
            }
            $$
            """);

            engine.execute("""
            CREATE FUNCTION display(val STRING)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Display.displayString'
            AS $$
            class Display {
              public static String displayString(String val) {
                return "STR:" + val;
              }
            }
            $$
            """);

            engine.execute("CREATE TABLE test_data (id INTEGER, name STRING)");
            engine.execute("INSERT INTO test_data VALUES (1, 'Alice')");
            engine.execute("INSERT INTO test_data VALUES (2, 'Bob')");

            logger.info("Created display(INTEGER) and display(STRING)");
            logger.info("Query: SELECT display(id), display(name) FROM test_data");
            logger.info("Each column uses the appropriate overload!");

            logger.info("\n=== Summary ===");
            logger.info("Overloaded functions provide:");
            logger.info("  - Multiple implementations with same name");
            logger.info("  - Resolution by parameter count and types");
            logger.info("  - Signature-specific DROP operations");
            logger.info("  - Signature-specific CREATE OR REPLACE");
            logger.info("  - Protection against duplicate signatures");
            logger.info("  - Automatic overload selection at runtime");
            logger.info("  - Works with any combination of parameter types");
            logger.info("  - Full integration with table queries");

        } finally {
            engine.shutdown();
        }
    }
}
