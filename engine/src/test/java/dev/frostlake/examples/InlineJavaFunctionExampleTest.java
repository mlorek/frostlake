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

public class InlineJavaFunctionExampleTest {
    private static final Logger logger = LoggerFactory.getLogger(InlineJavaFunctionExampleTest.class);

    @Test
    public void demonstrateInlineJavaFunctions() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== Inline Java User-Defined Functions ===");

            // Example 1: Simple Java function with no parameters
            logger.info("\n1. Simple Java function with no parameters:");
            final String simpleFunction = """
            CREATE OR REPLACE FUNCTION jfn()
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'JavaFn.fn'
            AS $$
            class JavaFn{
              public static String fn(){
                return "hello";
              }
            }
            $$
            """;
            engine.execute(simpleFunction);
            logger.info("Created function: jfn()");
            logger.info("Returns: hello");
            logger.info("Usage: SELECT jfn()");

            // Example 2: Java function with single parameter
            logger.info("\n2. Java function with single String parameter:");
            final String greetFunction = """
            CREATE OR REPLACE FUNCTION greet(name STRING)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Greeter.greet'
            AS $$
            class Greeter {
              public static String greet(String name) {
                return "Hello, " + name + "!";
              }
            }
            $$
            """;
            engine.execute(greetFunction);
            logger.info("Created function: greet(name STRING)");
            logger.info("Usage: SELECT greet('World')");
            logger.info("Returns: Hello, World!");

            // Example 3: Java function with integer parameter
            logger.info("\n3. Java function with integer parameter:");
            final String doubleFunction = """
            CREATE OR REPLACE FUNCTION double_value(x INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVA
            HANDLER = 'MathOps.doubleValue'
            AS $$
            class MathOps {
              public static int doubleValue(int x) {
                return x * 2;
              }
            }
            $$
            """;
            engine.execute(doubleFunction);
            logger.info("Created function: double_value(x INTEGER)");
            logger.info("Usage: SELECT double_value(21)");
            logger.info("Returns: 42");

            // Example 4: Java function with multiple parameters
            logger.info("\n4. Java function with multiple parameters:");
            final String addFunction = """
            CREATE OR REPLACE FUNCTION add_numbers(a INTEGER, b INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVA
            HANDLER = 'Calculator.add'
            AS $$
            class Calculator {
              public static int add(int a, int b) {
                return a + b;
              }
            }
            $$
            """;
            engine.execute(addFunction);
            logger.info("Created function: add_numbers(a INTEGER, b INTEGER)");
            logger.info("Usage: SELECT add_numbers(10, 32)");
            logger.info("Returns: 42");

            // Example 5: Java function with boolean return type
            logger.info("\n5. Java function with boolean return type:");
            final String isEvenFunction = """
            CREATE OR REPLACE FUNCTION is_even(n INTEGER)
            RETURNS BOOLEAN
            LANGUAGE JAVA
            HANDLER = 'NumberUtils.isEven'
            AS $$
            class NumberUtils {
              public static boolean isEven(int n) {
                return n % 2 == 0;
              }
            }
            $$
            """;
            engine.execute(isEvenFunction);
            logger.info("Created function: is_even(n INTEGER)");
            logger.info("Usage in WHERE clause: SELECT * FROM table WHERE is_even(value)");
            logger.info("Returns: true/false");

            // Example 6: Java function with complex logic
            logger.info("\n6. Java function with complex logic (factorial):");
            final String factorialFunction = """
            CREATE OR REPLACE FUNCTION factorial(n INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVA
            HANDLER = 'MathUtils.factorial'
            AS $$
            class MathUtils {
              public static int factorial(int n) {
                if (n <= 1) {
                  return 1;
                }
                int result = 1;
                for (int i = 2; i <= n; i++) {
                  result *= i;
                }
                return result;
              }
            }
            $$
            """;
            engine.execute(factorialFunction);
            logger.info("Created function: factorial(n INTEGER)");
            logger.info("Usage: SELECT factorial(5)");
            logger.info("Returns: 120 (5! = 5*4*3*2*1)");

            // Example 7: Java function used in table operations
            logger.info("\n7. Java function used in table operations:");
            final String formatFunction = """
            CREATE OR REPLACE FUNCTION format_name(firstname STRING, lastname STRING)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'Formatter.formatName'
            AS $$
            class Formatter {
              public static String formatName(String firstname, String lastname) {
                return lastname + ", " + firstname;
              }
            }
            $$
            """;
            engine.execute(formatFunction);
            engine.execute("CREATE TABLE employees (first_name STRING, last_name STRING)");
            engine.execute("INSERT INTO employees VALUES ('John', 'Doe')");
            engine.execute("INSERT INTO employees VALUES ('Jane', 'Smith')");
            logger.info("Created function: format_name(firstname STRING, lastname STRING)");
            logger.info("Created table: employees");
            logger.info("Usage: SELECT format_name(first_name, last_name) FROM employees");
            logger.info("Returns: Doe, John / Smith, Jane");

            // Example 8: Java function combined with built-in functions
            logger.info("\n8. Java function combined with built-in functions:");
            final String prefixFunction = """
            CREATE OR REPLACE FUNCTION add_prefix(text STRING)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'StringOps.addPrefix'
            AS $$
            class StringOps {
              public static String addPrefix(String text) {
                return "PREFIX_" + text;
              }
            }
            $$
            """;
            engine.execute(prefixFunction);
            logger.info("Created function: add_prefix(text STRING)");
            logger.info("Usage: SELECT UPPER(add_prefix('test'))");
            logger.info("Returns: PREFIX_TEST");
            logger.info("Demonstrates: Java UDFs can be composed with built-in functions");

            // Example 9: OR REPLACE behavior
            logger.info("\n9. CREATE OR REPLACE function:");
            final String version1 = """
            CREATE OR REPLACE FUNCTION versioned_func()
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'VersionedClass.method'
            AS $$
            class VersionedClass {
              public static String method() {
                return "version 1";
              }
            }
            $$
            """;
            engine.execute(version1);
            logger.info("Created version 1: returns 'version 1'");

            final String version2 = """
            CREATE OR REPLACE FUNCTION versioned_func()
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'VersionedClass.method'
            AS $$
            class VersionedClass {
              public static String method() {
                return "version 2";
              }
            }
            $$
            """;
            engine.execute(version2);
            logger.info("Replaced with version 2: returns 'version 2'");
            logger.info("OR REPLACE allows updating function definitions");

            // Example 10: Function returning null
            logger.info("\n10. Java function returning null:");
            final String nullFunction = """
            CREATE OR REPLACE FUNCTION get_nullable()
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'NullHandler.getNullable'
            AS $$
            class NullHandler {
              public static String getNullable() {
                return null;
              }
            }
            $$
            """;
            engine.execute(nullFunction);
            logger.info("Created function: get_nullable()");
            logger.info("Returns: NULL");
            logger.info("Java UDFs can return null values");

            logger.info("\n=== Summary ===");
            logger.info("Inline Java UDFs provide:");
            logger.info("  - LANGUAGE JAVA specifies Java implementation");
            logger.info("  - HANDLER = 'ClassName.methodName' specifies the entry point");
            logger.info("  - AS $$ ... $$ contains the Java source code");
            logger.info("  - Code is compiled at runtime and cached");
            logger.info("  - Can accept multiple parameters of various types");
            logger.info("  - Can return STRING, INTEGER, BOOLEAN, or NULL");
            logger.info("  - Work in SELECT, WHERE, and all SQL contexts");
            logger.info("  - Can be combined with built-in functions");
            logger.info("  - Support CREATE OR REPLACE for updates");

        } finally {
            engine.shutdown();
        }
    }
}
