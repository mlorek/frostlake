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

package dev.frostlake.udf;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InlineJavaFunctionTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(InlineJavaFunctionTest.class);

    @Test
    public void testCreateInlineJavaFunction() throws SQLException {
        logger.info("Testing CREATE FUNCTION with inline Java code");

        final String createFunction = """
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

        statement.execute(createFunction);

        final ResultSet rs = statement.executeQuery("SELECT jfn()");
        rs.next();
        final String result = rs.getString(1);
        assertEquals("hello", result);
    }

    @Test
    public void testJavaFunctionWithStringParameter() throws SQLException {
        logger.info("Testing Java function with String parameter");

        final String createFunction = """
        CREATE OR REPLACE FUNCTION greet(name STRING)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'Greeter.greet'
        AS $$
        class Greeter {
          public static String greet(String name) {
            return "Hello, " + name;
          }
        }
        $$
        """;

        statement.execute(createFunction);

        final ResultSet rs = statement.executeQuery("SELECT greet('World')");
        rs.next();
        final String result = rs.getString(1);
        assertEquals("Hello, World", result);
    }

    @Test
    public void testJavaFunctionWithIntegerParameter() throws SQLException {
        logger.info("Testing Java function with Integer parameter");

        final String createFunction = """
        CREATE OR REPLACE FUNCTION double_value(x INTEGER)
        RETURNS INTEGER
        LANGUAGE JAVA
        HANDLER = 'Math.doubleValue'
        AS $$
        class Math {
          public static int doubleValue(int x) {
            return x * 2;
          }
        }
        $$
        """;

        statement.execute(createFunction);

        final ResultSet rs = statement.executeQuery("SELECT double_value(5)");
        rs.next();
        final int result = rs.getInt(1);
        assertEquals(10, result);
    }

    @Test
    public void testJavaFunctionWithMultipleParameters() throws SQLException {
        logger.info("Testing Java function with multiple parameters");

        final String createFunction = """
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

        statement.execute(createFunction);

        final ResultSet rs = statement.executeQuery("SELECT add_numbers(10, 20)");
        rs.next();
        final int result = rs.getInt(1);
        assertEquals(30, result);
    }

    @Test
    public void testJavaFunctionInWhereClause() throws SQLException {
        logger.info("Testing Java function in WHERE clause");

        final String createFunction = """
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

        statement.execute(createFunction);

        statement.execute("CREATE TABLE numbers (id INTEGER, value INTEGER)");
        statement.execute("INSERT INTO numbers VALUES (1, 10)");
        statement.execute("INSERT INTO numbers VALUES (2, 15)");
        statement.execute("INSERT INTO numbers VALUES (3, 20)");

        final ResultSet rs = statement.executeQuery("SELECT id, value FROM numbers WHERE is_even(value)");
        int count = 0;
        while (rs.next()) {
            count++;
            final int value = rs.getInt(2);
            assertEquals(0, value % 2);
        }
        assertEquals(2, count);
    }

    @Test
    public void testJavaFunctionInSelectList() throws SQLException {
        logger.info("Testing Java function in SELECT list");

        final String createFunction = """
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

        statement.execute(createFunction);

        statement.execute("CREATE TABLE people (first_name STRING, last_name STRING)");
        statement.execute("INSERT INTO people VALUES ('John', 'Doe')");
        statement.execute("INSERT INTO people VALUES ('Jane', 'Smith')");

        // ORDER BY pins the row order: an unordered SELECT's order is not promised (a live
        // account returns these two rows either way round).
        final ResultSet rs = statement.executeQuery(
            "SELECT format_name(first_name, last_name) as formatted FROM people ORDER BY last_name");
        rs.next();
        final String result1 = rs.getString(1);
        assertEquals("Doe, John", result1);
        rs.next();
        final String result2 = rs.getString(1);
        assertEquals("Smith, Jane", result2);
    }

    @Test
    public void testJavaFunctionWithComplexLogic() throws SQLException {
        logger.info("Testing Java function with complex logic");

        final String createFunction = """
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

        statement.execute(createFunction);

        final ResultSet rs = statement.executeQuery("SELECT factorial(5)");
        rs.next();
        final int result = rs.getInt(1);
        assertEquals(120, result);
    }

    @Test
    public void testJavaFunctionReturnsNull() throws SQLException {
        logger.info("Testing Java function returning null");

        final String createFunction = """
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

        statement.execute(createFunction);

        final ResultSet rs = statement.executeQuery("SELECT get_nullable()");
        rs.next();
        final String result = rs.getString(1);
        assertEquals(null, result);
    }

    @Test
    public void testMultipleJavaFunctions() throws SQLException {
        logger.info("Testing multiple Java functions");

        final String createFunction1 = """
        CREATE OR REPLACE FUNCTION func1()
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'Class1.method1'
        AS $$
        class Class1 {
          public static String method1() {
            return "function1";
          }
        }
        $$
        """;

        final String createFunction2 = """
        CREATE OR REPLACE FUNCTION func2()
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'Class2.method2'
        AS $$
        class Class2 {
          public static String method2() {
            return "function2";
          }
        }
        $$
        """;

        statement.execute(createFunction1);
        statement.execute(createFunction2);

        final ResultSet rs = statement.executeQuery("SELECT func1(), func2()");
        rs.next();
        assertEquals("function1", rs.getString(1));
        assertEquals("function2", rs.getString(2));
    }

    @Test
    public void testJavaFunctionWithCombinedBuiltInFunction() throws SQLException {
        logger.info("Testing Java function combined with built-in function");

        final String createFunction = """
        CREATE OR REPLACE FUNCTION add_prefix(text STRING)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'StringUtils.addPrefix'
        AS $$
        class StringUtils {
          public static String addPrefix(String text) {
            return "PREFIX_" + text;
          }
        }
        $$
        """;

        statement.execute(createFunction);

        final ResultSet rs = statement.executeQuery("SELECT UPPER(add_prefix('test'))");
        rs.next();
        final String result = rs.getString(1);
        assertEquals("PREFIX_TEST", result);
    }
}
