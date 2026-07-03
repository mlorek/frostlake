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

public class ReturnTypeParametersExample {
    private static final Logger logger = LoggerFactory.getLogger(ReturnTypeParametersExample.class);

    @Test
    public void demonstrateReturnTypeParameters() {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== Return Type Parameters in Functions and Procedures ===");

            // Example 1: VARCHAR with length specification
            logger.info("\n1. VARCHAR with length specification:");
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
            logger.info("Created: get_user_name() RETURNS VARCHAR(100)");
            logger.info("VARCHAR(100) specifies maximum length of 100 characters");
            logger.info("Usage: SELECT get_user_name()");

            // Example 2: DECIMAL with precision and scale
            logger.info("\n2. DECIMAL with precision and scale:");
            engine.execute("""
            CREATE FUNCTION calculate_price()
            RETURNS DECIMAL(10,2)
            LANGUAGE JAVA
            HANDLER = 'PriceCalculator.calculate'
            AS $$
            class PriceCalculator {
              public static double calculate() {
                return 99.99;
              }
            }
            $$
            """);
            logger.info("Created: calculate_price() RETURNS DECIMAL(10,2)");
            logger.info("DECIMAL(10,2) means:");
            logger.info("  - Total precision: 10 digits");
            logger.info("  - Scale: 2 decimal places");
            logger.info("  - Range: -99999999.99 to 99999999.99");

            // Example 3: NUMBER with precision only
            logger.info("\n3. NUMBER with precision only:");
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
            logger.info("Created: get_population() RETURNS NUMBER(15)");
            logger.info("NUMBER(15) specifies 15 digits of precision");
            logger.info("Suitable for large integer values");

            // Example 4: TIMESTAMP with precision
            logger.info("\n4. TIMESTAMP with precision:");
            engine.execute("""
            CREATE FUNCTION get_current_time()
            RETURNS TIMESTAMP(9)
            LANGUAGE JAVA
            HANDLER = 'TimeGetter.getTime'
            AS $$
            class TimeGetter {
              public static String getTime() {
                return "2024-01-01 12:00:00.123456789";
              }
            }
            $$
            """);
            logger.info("Created: get_current_time() RETURNS TIMESTAMP(9)");
            logger.info("TIMESTAMP(9) supports nanosecond precision (9 decimal places)");
            logger.info("Precision values: 0-9 (seconds to nanoseconds)");

            // Example 5: TIMESTAMP_NTZ with precision
            logger.info("\n5. TIMESTAMP_NTZ with precision:");
            engine.execute("""
            CREATE FUNCTION get_event_time()
            RETURNS TIMESTAMP_NTZ(6)
            LANGUAGE JAVA
            HANDLER = 'EventTimeGetter.getTime'
            AS $$
            class EventTimeGetter {
              public static String getTime() {
                return "2024-01-01 12:00:00.123456";
              }
            }
            $$
            """);
            logger.info("Created: get_event_time() RETURNS TIMESTAMP_NTZ(6)");
            logger.info("TIMESTAMP_NTZ(6) is timezone-naive with microsecond precision");

            // Example 6: Procedure with return type parameters
            logger.info("\n6. Procedure with return type parameters:");
            engine.execute("""
            CREATE PROCEDURE format_data()
            RETURNS VARCHAR(255)
            LANGUAGE JAVA
            HANDLER = 'DataFormatter.format'
            AS $$
            class DataFormatter {
              public static String format() {
                return "Formatted data output";
              }
            }
            $$
            """);
            logger.info("Created: format_data() RETURNS VARCHAR(255)");
            logger.info("Procedures also support type parameters in RETURNS clause");

            // Example 7: Overloaded functions with different return type precisions
            logger.info("\n7. Overloaded functions with different return precisions:");
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
            logger.info("Created two overloads:");
            logger.info("  to_text(INTEGER) RETURNS VARCHAR(10)");
            logger.info("  to_text(INTEGER, INTEGER) RETURNS VARCHAR(50)");
            logger.info("Each overload can have different return type precision");

            // Example 8: CREATE OR REPLACE with changed precision
            logger.info("\n8. Updating return type precision with OR REPLACE:");
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
            logger.info("Initial: get_code() RETURNS VARCHAR(5)");

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
            logger.info("Updated: get_code() RETURNS VARCHAR(20)");
            logger.info("OR REPLACE allows changing return type precision");

            // Example 9: Backward compatibility - no type parameters
            logger.info("\n9. Backward compatibility - no type parameters:");
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
            logger.info("Created: legacy_func() RETURNS STRING");
            logger.info("Type parameters are OPTIONAL - base types still work");

            logger.info("\n=== Summary ===");
            logger.info("Return type parameters provide:");
            logger.info("  - VARCHAR(n): String length constraints");
            logger.info("  - DECIMAL(p,s): Numeric precision and scale");
            logger.info("  - NUMBER(p): Integer precision");
            logger.info("  - TIMESTAMP(p): Time precision (0-9)");
            logger.info("  - TIMESTAMP_NTZ(p): Timezone-naive time precision");
            logger.info("  - Support in both FUNCTION and PROCEDURE");
            logger.info("  - Compatible with overloaded functions");
            logger.info("  - Changeable with CREATE OR REPLACE");
            logger.info("  - Optional - base types work without parameters");
            logger.info("");
            logger.info("Common precision values:");
            logger.info("  - VARCHAR: typically 50-255 for names, 4000+ for text");
            logger.info("  - DECIMAL: (10,2) for currency, (18,4) for financial");
            logger.info("  - TIMESTAMP: (0) seconds, (3) milliseconds, (6) microseconds, (9) nanoseconds");

        } finally {
            engine.shutdown();
        }
    }
}
