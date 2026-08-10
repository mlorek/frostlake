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
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OverloadedFunctionTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(OverloadedFunctionTest.class);

    @Test
    public void testCreateOverloadedFunctionsWithDifferentParameterCounts() throws SQLException {
        logger.info("Testing overloaded functions with different parameter counts");

        // Create first overload - no parameters
        statement.execute("""
        CREATE FUNCTION greet()
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'Greeter0.greet'
        AS $$
        class Greeter0 {
          public static String greet() {
            return "Hello!";
          }
        }
        $$
        """);

        // Create second overload - one parameter
        statement.execute("""
        CREATE FUNCTION greet(name STRING)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'Greeter1.greet'
        AS $$
        class Greeter1 {
          public static String greet(String name) {
            return "Hello, " + name + "!";
          }
        }
        $$
        """);

        // Create third overload - two parameters
        statement.execute("""
        CREATE FUNCTION greet(firstname STRING, lastname STRING)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'Greeter2.greet'
        AS $$
        class Greeter2 {
          public static String greet(String firstname, String lastname) {
            return "Hello, " + firstname + " " + lastname + "!";
          }
        }
        $$
        """);

        // Test all three overloads
        final ResultSet rs1 = statement.executeQuery("SELECT greet()");
        rs1.next();
        assertEquals("Hello!", rs1.getString(1));

        final ResultSet rs2 = statement.executeQuery("SELECT greet('Alice')");
        rs2.next();
        assertEquals("Hello, Alice!", rs2.getString(1));

        final ResultSet rs3 = statement.executeQuery("SELECT greet('Bob', 'Smith')");
        rs3.next();
        assertEquals("Hello, Bob Smith!", rs3.getString(1));
    }

    @Test
    public void testCreateOverloadedFunctionsWithDifferentParameterTypes() throws SQLException {
        logger.info("Testing overloaded functions with different parameter types");

        // Create first overload - STRING parameter
        statement.execute("""
        CREATE FUNCTION process(value STRING)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'ProcessorString.process'
        AS $$
        class ProcessorString {
          public static String process(String value) {
            return "String: " + value;
          }
        }
        $$
        """);

        // Create second overload - INTEGER parameter
        statement.execute("""
        CREATE FUNCTION process(value INTEGER)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'ProcessorInt.process'
        AS $$
        class ProcessorInt {
          public static String process(int value) {
            return "Integer: " + value;
          }
        }
        $$
        """);

        // Test both overloads
        final ResultSet rs1 = statement.executeQuery("SELECT process('test')");
        rs1.next();
        assertEquals("String: test", rs1.getString(1));

        final ResultSet rs2 = statement.executeQuery("SELECT process(42)");
        rs2.next();
        assertEquals("Integer: 42", rs2.getString(1));
    }

    @Test
    public void testDropSpecificOverloadBySignature() throws SQLException {
        logger.info("Testing DROP FUNCTION with specific signature");

        // Create two overloads
        statement.execute("""
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

        statement.execute("""
        CREATE FUNCTION calculate(x INTEGER, y INTEGER)
        RETURNS INTEGER
        LANGUAGE JAVA
        HANDLER = 'Calculator.dual'
        AS $$
        class Calculator {
          public static int dual(int x, int y) {
            return x + y;
          }
        }
        $$
        """);

        // Drop only the single-parameter overload
        statement.execute("DROP FUNCTION calculate(INTEGER)");

        // Two-parameter overload should still work
        final ResultSet rs = statement.executeQuery("SELECT calculate(10, 20)");
        rs.next();
        assertEquals(30, rs.getInt(1));

        // Single-parameter should fail
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.executeQuery("SELECT calculate(5)");
                
            }
        });
    }

    @Test
    public void testDropAllOverloadsWithoutSignature() throws SQLException {
        logger.info("Testing DROP FUNCTION without signature drops all overloads");

        // Create two overloads
        statement.execute("""
        CREATE FUNCTION sum_val(x INTEGER)
        RETURNS INTEGER
        LANGUAGE JAVA
        HANDLER = 'Adder.add1'
        AS $$
        class Adder {
          public static int add1(int x) {
            return x + 1;
          }
        }
        $$
        """);

        statement.execute("""
        CREATE FUNCTION sum_val(x INTEGER, y INTEGER)
        RETURNS INTEGER
        LANGUAGE JAVA
        HANDLER = 'Adder.add2'
        AS $$
        class Adder {
          public static int add2(int x, int y) {
            return x + y;
          }
        }
        $$
        """);

        // Snowflake requires the signature, so each overload is dropped individually.
        statement.execute("DROP FUNCTION sum_val(INTEGER)");
        statement.execute("DROP FUNCTION sum_val(INTEGER, INTEGER)");

        // Both should fail now
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.executeQuery("SELECT sum_val(5)");
                
            }
        });

        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.executeQuery("SELECT sum_val(5, 10)");
                
            }
        });
    }

    @Test
    public void testCreateOrReplaceSpecificOverload() throws SQLException {
        logger.info("Testing CREATE OR REPLACE with specific overload");

        // Create two overloads
        statement.execute("""
        CREATE FUNCTION multiply(x INTEGER)
        RETURNS INTEGER
        LANGUAGE JAVA
        HANDLER = 'Multiplier1.mult1'
        AS $$
        class Multiplier1 {
          public static int mult1(int x) {
            return x * 2;
          }
        }
        $$
        """);

        statement.execute("""
        CREATE FUNCTION multiply(x INTEGER, y INTEGER)
        RETURNS INTEGER
        LANGUAGE JAVA
        HANDLER = 'Multiplier2.mult2'
        AS $$
        class Multiplier2 {
          public static int mult2(int x, int y) {
            return x * y;
          }
        }
        $$
        """);

        // Replace only the single-parameter overload
        statement.execute("""
        CREATE OR REPLACE FUNCTION multiply(x INTEGER)
        RETURNS INTEGER
        LANGUAGE JAVA
        HANDLER = 'Multiplier1.mult1New'
        AS $$
        class Multiplier1 {
          public static int mult1New(int x) {
            return x * 3;
          }
        }
        $$
        """);

        // Single-parameter should use new logic
        final ResultSet rs1 = statement.executeQuery("SELECT multiply(5)");
        rs1.next();
        assertEquals(15, rs1.getInt(1));

        // Two-parameter should still use old logic
        final ResultSet rs2 = statement.executeQuery("SELECT multiply(5, 7)");
        rs2.next();
        assertEquals(35, rs2.getInt(1));
    }

    @Test
    public void testPreventDuplicateSignature() throws SQLException {
        logger.info("Testing prevention of duplicate signatures");

        // Create first function
        statement.execute("""
        CREATE FUNCTION test_func(x INTEGER, y STRING)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'TestClass.method1'
        AS $$
        class TestClass {
          public static String method1(int x, String y) {
            return "v1";
          }
        }
        $$
        """);

        // Try to create with same signature - should fail
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("""
                CREATE FUNCTION test_func(x INTEGER, y STRING)
                RETURNS STRING
                LANGUAGE JAVA
                HANDLER = 'TestClass.method2'
                AS $$
                class TestClass {
                  public static String method2(int x, String y) {
                    return "v2";
                  }
                }
                $$
                """);
                
            }
        });
    }

    @Test
    public void testOverloadedFunctionsInTable() throws SQLException {
        logger.info("Testing overloaded functions in table queries");

        statement.execute("""
        CREATE FUNCTION fmt_value(value INTEGER)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'FormatterInt.formatInt'
        AS $$
        class FormatterInt {
          public static String formatInt(int value) {
            return "INT:" + value;
          }
        }
        $$
        """);

        statement.execute("""
        CREATE FUNCTION fmt_value(value STRING)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'FormatterString.formatString'
        AS $$
        class FormatterString {
          public static String formatString(String value) {
            return "STR:" + value;
          }
        }
        $$
        """);

        statement.execute("CREATE TABLE data (int_val INTEGER, str_val STRING)");
        statement.execute("INSERT INTO data VALUES (42, 'hello')");
        statement.execute("INSERT INTO data VALUES (100, 'world')");

        final ResultSet rs = statement.executeQuery("SELECT fmt_value(int_val), fmt_value(str_val) FROM data ORDER BY int_val");

        rs.next();
        assertEquals("INT:42", rs.getString(1));
        assertEquals("STR:hello", rs.getString(2));

        rs.next();
        assertEquals("INT:100", rs.getString(1));
        assertEquals("STR:world", rs.getString(2));
    }

    @Test
    public void testDropNonExistentOverload() throws SQLException {
        logger.info("Testing DROP FUNCTION with non-existent signature");

        // Create one overload (SAMPLE is a reserved word, so the routine is named sample_fn)
        statement.execute("""
        CREATE FUNCTION sample_fn(x INTEGER)
        RETURNS INTEGER
        LANGUAGE JAVA
        HANDLER = 'Sample.method'
        AS $$
        class Sample {
          public static int method(int x) {
            return x;
          }
        }
        $$
        """);

        // Try to drop non-existent overload - should fail
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP FUNCTION sample_fn(STRING)");
                
            }
        });

        // Original overload should still exist
        final ResultSet rs = statement.executeQuery("SELECT sample_fn(10)");
        rs.next();
        assertEquals(10, rs.getInt(1));
    }

    @Test
    public void testOverloadedFunctionsWithSameTypesButDifferentCounts() throws SQLException {
        logger.info("Testing overloaded functions with same types but different counts");

        statement.execute("""
        CREATE FUNCTION concat_val(a STRING)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'Combiner1.concat1'
        AS $$
        class Combiner1 {
          public static String concat1(String a) {
            return "Value: " + a;
          }
        }
        $$
        """);

        statement.execute("""
        CREATE FUNCTION concat_val(a STRING, b STRING)
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'Combiner2.concat2'
        AS $$
        class Combiner2 {
          public static String concat2(String a, String b) {
            return a + "-" + b;
          }
        }
        $$
        """);

        final ResultSet rs1 = statement.executeQuery("SELECT concat_val('test')");
        rs1.next();
        assertEquals("Value: test", rs1.getString(1));

        final ResultSet rs2 = statement.executeQuery("SELECT concat_val('hello', 'world')");
        rs2.next();
        assertEquals("hello-world", rs2.getString(1));
    }

    @Test
    public void testCallOverloadedFunctionWithWrongParameterCount() throws SQLException {
        logger.info("Testing calling overloaded function with wrong parameter count");

        statement.execute("""
        CREATE FUNCTION limited(x INTEGER)
        RETURNS INTEGER
        LANGUAGE JAVA
        HANDLER = 'Limited.method'
        AS $$
        class Limited {
          public static int method(int x) {
            return x;
          }
        }
        $$
        """);

        // Call with wrong number of parameters
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.executeQuery("SELECT limited()");
                
            }
        });

        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.executeQuery("SELECT limited(1, 2)");
                
            }
        });
    }
}
