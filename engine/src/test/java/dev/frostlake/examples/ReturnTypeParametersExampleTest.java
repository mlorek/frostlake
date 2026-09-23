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
 * Java routines whose RETURNS type carries type parameters — VARCHAR(100), DECIMAL(10,2), NUMBER(15),
 * TIMESTAMP(9) — each with a handler whose Java return type carries it: a String, a BigDecimal, a long, a
 * java.sql.Timestamp. A Java procedure's handler takes the Snowpark Session first. Every cell is live-verified.
 */
public class ReturnTypeParametersExampleTest extends BaseDatabaseTest {

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

    @Test
    public void demonstrateReturnTypeParameters() {
        engine.execute("""
            CREATE FUNCTION get_user_name()
            RETURNS VARCHAR(100)
            LANGUAGE JAVA
            HANDLER = 'UserNameGetter.getName'
            AS $$
            class UserNameGetter {
              public static String getName() {
                return "John Doe";
              }
            }
            $$
            """);
        assertEquals("John Doe", answer("SELECT get_user_name()"));

        engine.execute("""
            CREATE FUNCTION calculate_price()
            RETURNS DECIMAL(10,2)
            LANGUAGE JAVA
            HANDLER = 'PriceCalculator.calculate'
            AS $$
            import java.math.BigDecimal;
            class PriceCalculator {
              public static BigDecimal calculate() {
                return new BigDecimal("99.99");
              }
            }
            $$
            """);
        assertEquals("99.99", answer("SELECT calculate_price()"));

        engine.execute("""
            CREATE FUNCTION get_population()
            RETURNS NUMBER(15)
            LANGUAGE JAVA
            HANDLER = 'PopulationGetter.get'
            AS $$
            class PopulationGetter {
              public static long get() {
                return 8000000000L;
              }
            }
            $$
            """);
        assertEquals("8000000000", answer("SELECT get_population()"));

        engine.execute("""
            CREATE FUNCTION get_current_time()
            RETURNS TIMESTAMP(9)
            LANGUAGE JAVA
            HANDLER = 'TimeGetter.getTime'
            AS $$
            import java.sql.Timestamp;
            class TimeGetter {
              public static Timestamp getTime() {
                return Timestamp.valueOf("2024-01-01 12:00:00.123456789");
              }
            }
            $$
            """);
        engine.execute("""
            CREATE FUNCTION get_event_time()
            RETURNS TIMESTAMP_NTZ(6)
            LANGUAGE JAVA
            HANDLER = 'EventTimeGetter.getTime'
            AS $$
            import java.sql.Timestamp;
            class EventTimeGetter {
              public static Timestamp getTime() {
                return Timestamp.valueOf("2024-01-01 12:00:00.123456");
              }
            }
            $$
            """);

        // A Java procedure's handler takes the session first.
        assertEquals("Failed to find a public method named \"format\" with 1 arguments in function FORMAT_DATA"
            + " with handler DataFormatter.format", answer("""
            CREATE PROCEDURE format_data()
            RETURNS VARCHAR(255)
            LANGUAGE JAVA
            RUNTIME_VERSION = '11'
            PACKAGES = ('com.snowflake:snowpark:latest')
            HANDLER = 'DataFormatter.format'
            AS $$
            class DataFormatter {
              public static String format() {
                return "Formatted data output";
              }
            }
            $$
            """));
        engine.execute("""
            CREATE PROCEDURE format_data()
            RETURNS VARCHAR(255)
            LANGUAGE JAVA
            RUNTIME_VERSION = '11'
            PACKAGES = ('com.snowflake:snowpark:latest')
            HANDLER = 'DataFormatter.format'
            AS $$
            import com.snowflake.snowpark_java.Session;
            class DataFormatter {
              public static String format(Session session) {
                return "Formatted data output";
              }
            }
            $$
            """);
        assertEquals("Formatted data output", answer("CALL format_data()"));

        // Overloads with different return widths.
        engine.execute("""
            CREATE FUNCTION to_text(val INTEGER)
            RETURNS VARCHAR(10)
            LANGUAGE JAVA
            HANDLER = 'TextConverter1.convert'
            AS $$
            class TextConverter1 {
              public static String convert(int val) {
                return String.valueOf(val);
              }
            }
            $$
            """);
        engine.execute("""
            CREATE FUNCTION to_text(val INTEGER, width INTEGER)
            RETURNS VARCHAR(50)
            LANGUAGE JAVA
            HANDLER = 'TextConverter2.convert'
            AS $$
            class TextConverter2 {
              public static String convert(int val, int width) {
                return String.format("%" + width + "d", val);
              }
            }
            $$
            """);
        assertEquals("42", answer("SELECT to_text(42)"));
        assertEquals("   42", answer("SELECT to_text(42, 5)"));

        // CREATE OR REPLACE with a wider return type.
        engine.execute("""
            CREATE FUNCTION get_code()
            RETURNS VARCHAR(5)
            LANGUAGE JAVA
            HANDLER = 'CodeGetter1.get'
            AS $$
            class CodeGetter1 {
              public static String get() {
                return "ABC";
              }
            }
            $$
            """);
        engine.execute("""
            CREATE OR REPLACE FUNCTION get_code()
            RETURNS VARCHAR(20)
            LANGUAGE JAVA
            HANDLER = 'CodeGetter2.get'
            AS $$
            class CodeGetter2 {
              public static String get() {
                return "EXTENDED-CODE-12345";
              }
            }
            $$
            """);
        assertEquals("EXTENDED-CODE-12345", answer("SELECT get_code()"));

        // No type parameters at all.
        engine.execute("""
            CREATE FUNCTION legacy_func()
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'LegacyFunc.run'
            AS $$
            class LegacyFunc {
              public static String run() {
                return "legacy";
              }
            }
            $$
            """);
        assertEquals("legacy", answer("SELECT legacy_func()"));
    }
}
