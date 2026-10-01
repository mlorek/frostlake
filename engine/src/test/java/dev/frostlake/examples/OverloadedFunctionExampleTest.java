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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Java functions overloaded by argument count and by argument types: each call reaches the overload its
 * arguments match, a DROP names one overload by its signature, CREATE OR REPLACE replaces one overload, and a
 * second CREATE of a signature is refused. Every cell is live-verified.
 */
public class OverloadedFunctionExampleTest extends BaseDatabaseTest {

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Every row, its cells joined by a space, the rows by " | ". */
    private String rows(final String sql) {
        final StringBuilder text = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            text.append(text.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                text.append(i > 0 ? " " : "").append(row.getValue(i));
            }
        }
        return text.toString();
    }

    @Test
    public void demonstrateOverloadedFunctions() {
        // Overloading by argument count.
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
        assertEquals("Hello!", answer("SELECT greet()"));
        assertEquals("Hello, Ann!", answer("SELECT greet('Ann')"));
        assertEquals("Hello, Ann Lee!", answer("SELECT greet('Ann', 'Lee')"));

        // Overloading by argument type.
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
        assertEquals("[STRING]: x [INTEGER]: 7", rows("SELECT format('x'), format(7)"));

        // A DROP names one overload by its signature.
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
            HANDLER = 'Calculator.sum'
            AS $$
            class Calculator {
              public static int sum(int x, int y) {
                return x + y;
              }
            }
            $$
            """);
        assertEquals("8 9", rows("SELECT calculate(4), calculate(4, 5)"));
        engine.execute("DROP FUNCTION calculate(INTEGER)");
        engine.execute("DROP FUNCTION calculate(INTEGER, INTEGER)");

        // CREATE OR REPLACE replaces one overload.
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
        assertEquals("15", answer("SELECT multiply(5)"));

        // The same types in another order are another overload.
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
        assertEquals("a:1 2-b", rows("SELECT combine('a', 1), combine(2, 'b')"));

        // A signature cannot be created twice.
        final String validate = """
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
            """;
        engine.execute(validate);
        assertEquals("SQL compilation error:|Object 'VALIDATE' already exists.", answer(validate));

        // Overloads over a table's columns.
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
        engine.execute("INSERT INTO test_data VALUES (1, 'Alice'), (2, 'Bob')");
        assertEquals("INT:1 STR:Alice | INT:2 STR:Bob",
            rows("SELECT display(id) || ' ' || display(name) FROM test_data ORDER BY id"));
    }
}
