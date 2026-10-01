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
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReturnTypeParametersTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(ReturnTypeParametersTest.class);

    /** A CREATE that must be refused, with the refusal's text. */
    private String refusalOf(final String sql) {
        return assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void testFunctionReturnsVarcharWithLength() throws SQLException {
        logger.info("Testing function with VARCHAR(100) return type");

        statement.execute("""
        CREATE FUNCTION get_text()
        RETURNS VARCHAR(100)
        LANGUAGE JAVA
        HANDLER = 'TextGetter.getText'
        AS $$
        class TextGetter {
          public static String getText() {
            return "Hello World";
          }
        }
        $$
        """);

        final ResultSet rs = statement.executeQuery("SELECT get_text()");
        rs.next();
        assertEquals("Hello World", rs.getString(1));
    }

    /** A double handler cannot carry a DECIMAL result, so CREATE refuses it naming the storage type. */
    @Test
    public void testFunctionReturnsDecimalWithPrecisionAndScale() throws SQLException {
        logger.info("Testing function with DECIMAL(10,2) return type");

        final String refusal = refusalOf("""
        CREATE FUNCTION get_price()
        RETURNS DECIMAL(10,2)
        LANGUAGE JAVA
        HANDLER = 'PriceGetter.getPrice'
        AS $$
        class PriceGetter {
          public static double getPrice() {
            return 99.99;
          }
        }
        $$
        """);
        assertTrue(refusal.contains("Snowflake type FIXED[SB16](10,2){nullable} is not supported for Java return "
            + "type double in function GET_PRICE with handler PriceGetter.getPrice"), refusal);
    }

    @Test
    public void testFunctionReturnsNumberWithPrecision() throws SQLException {
        logger.info("Testing function with NUMBER(5) return type");

        statement.execute("""
        CREATE FUNCTION get_count()
        RETURNS NUMBER(5)
        LANGUAGE JAVA
        HANDLER = 'Counter.getCount'
        AS $$
        class Counter {
          public static int getCount() {
            return 12345;
          }
        }
        $$
        """);

        final ResultSet rs = statement.executeQuery("SELECT get_count()");
        rs.next();
        assertEquals(12345, rs.getInt(1));
    }

    /** A String handler cannot carry a TIMESTAMP result. */
    @Test
    public void testFunctionReturnsTimestampWithPrecision() throws SQLException {
        logger.info("Testing function with TIMESTAMP(9) return type");

        final String refusal = refusalOf("""
        CREATE FUNCTION get_timestamp()
        RETURNS TIMESTAMP(9)
        LANGUAGE JAVA
        HANDLER = 'TimestampGetter.getTimestamp'
        AS $$
        class TimestampGetter {
          public static String getTimestamp() {
            return "2024-01-01 12:00:00.123456789";
          }
        }
        $$
        """);
        assertTrue(refusal.contains("Snowflake type TIMESTAMP_NTZ[SB16](0,9){nullable} is not supported for Java "
            + "return type String in function GET_TIMESTAMP with handler TimestampGetter.getTimestamp"), refusal);
    }

    /** A TIMESTAMP_NTZ(6) is stored in eight bytes, which the refusal names. */
    @Test
    public void testFunctionReturnsTimestampNtzWithPrecision() throws SQLException {
        logger.info("Testing function with TIMESTAMP_NTZ(6) return type");

        final String refusal = refusalOf("""
        CREATE FUNCTION get_timestamp_ntz()
        RETURNS TIMESTAMP_NTZ(6)
        LANGUAGE JAVA
        HANDLER = 'TimestampNtzGetter.getTimestamp'
        AS $$
        class TimestampNtzGetter {
          public static String getTimestamp() {
            return "2024-01-01 12:00:00.123456";
          }
        }
        $$
        """);
        assertTrue(refusal.contains("Snowflake type TIMESTAMP_NTZ[SB8](0,6){nullable} is not supported for Java "
            + "return type String in function GET_TIMESTAMP_NTZ with handler TimestampNtzGetter.getTimestamp"),
            refusal);
    }

    @Test
    public void testProcedureReturnsVarcharWithLength() throws SQLException {
        logger.info("Testing procedure with VARCHAR(50) return type");

        statement.execute("""
        CREATE PROCEDURE get_message()
        RETURNS VARCHAR(50)
        LANGUAGE JAVA
        PACKAGES = ('com.snowflake:snowpark:latest')
        HANDLER = 'MessageGetter.getMessage'
        AS $$
        import com.snowflake.snowpark_java.Session;
        class MessageGetter {
          public static String getMessage(Session session) {
            return "Hello from procedure";
          }
        }
        $$
        """);

        // Procedures can be called but we just verify creation succeeded
        statement.execute("DROP PROCEDURE get_message()");
    }

    /** A procedure's handler is judged the same way: a double cannot carry its DECIMAL result. */
    @Test
    public void testProcedureReturnsDecimalWithPrecisionAndScale() throws SQLException {
        logger.info("Testing procedure with DECIMAL(15,4) return type");

        final String refusal = refusalOf("""
        CREATE PROCEDURE calculate_total()
        RETURNS DECIMAL(15,4)
        LANGUAGE JAVA
        PACKAGES = ('com.snowflake:snowpark:latest')
        HANDLER = 'Calculator.calculateTotal'
        AS $$
        import com.snowflake.snowpark_java.Session;
        class Calculator {
          public static double calculateTotal(Session session) {
            return 1234.5678;
          }
        }
        $$
        """);
        assertTrue(refusal.contains("Snowflake type FIXED[SB16](15,4){nullable} is not supported for Java return "
            + "type double in function CALCULATE_TOTAL with handler Calculator.calculateTotal"), refusal);
    }

    @Test
    public void testOverloadedFunctionsWithDifferentReturnTypePrecisions() throws SQLException {
        logger.info("Testing overloaded functions with different return type precisions");

        statement.execute("""
        CREATE FUNCTION format_number(val INTEGER)
        RETURNS VARCHAR(10)
        LANGUAGE JAVA
        HANDLER = 'Formatter1.format'
        AS $$
        class Formatter1 {
          public static String format(int val) {
            return String.valueOf(val);
          }
        }
        $$
        """);

        statement.execute("""
        CREATE FUNCTION format_number(val INTEGER, decimals INTEGER)
        RETURNS VARCHAR(20)
        LANGUAGE JAVA
        HANDLER = 'Formatter2.format'
        AS $$
        class Formatter2 {
          public static String format(int val, int decimals) {
            return val + "." + "0".repeat(decimals);
          }
        }
        $$
        """);

        final ResultSet rs1 = statement.executeQuery("SELECT format_number(123)");
        rs1.next();
        assertEquals("123", rs1.getString(1));

        final ResultSet rs2 = statement.executeQuery("SELECT format_number(123, 2)");
        rs2.next();
        assertEquals("123.00", rs2.getString(1));
    }

    @Test
    public void testCreateOrReplaceWithDifferentReturnTypePrecision() throws SQLException {
        logger.info("Testing CREATE OR REPLACE with different return type precision");

        statement.execute("""
        CREATE FUNCTION get_version()
        RETURNS VARCHAR(10)
        LANGUAGE JAVA
        HANDLER = 'Version1.get'
        AS $$
        class Version1 {
          public static String get() {
            return "v1.0";
          }
        }
        $$
        """);

        // Replace with longer VARCHAR
        statement.execute("""
        CREATE OR REPLACE FUNCTION get_version()
        RETURNS VARCHAR(50)
        LANGUAGE JAVA
        HANDLER = 'Version2.get'
        AS $$
        class Version2 {
          public static String get() {
            return "v2.0 with longer version string";
          }
        }
        $$
        """);

        final ResultSet rs = statement.executeQuery("SELECT get_version()");
        rs.next();
        assertEquals("v2.0 with longer version string", rs.getString(1));
    }

    @Test
    public void testFunctionWithoutTypeParameters() throws SQLException {
        logger.info("Testing function without type parameters (backward compatibility)");

        statement.execute("""
        CREATE FUNCTION simple_func()
        RETURNS STRING
        LANGUAGE JAVA
        HANDLER = 'SimpleFunc.run'
        AS $$
        class SimpleFunc {
          public static String run() {
            return "simple";
          }
        }
        $$
        """);

        final ResultSet rs = statement.executeQuery("SELECT simple_func()");
        rs.next();
        assertEquals("simple", rs.getString(1));
    }
}
