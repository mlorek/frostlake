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
package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * What a Java procedure's handler receives for the arguments of its CALL: each converted to its parameter's SQL
 * type as for any body, then handed over as the class the handler declares. A NUMBER is an {@code int}, a
 * {@code long}, a {@code short}, their boxes, a {@code BigDecimal} at the declared scale or a {@code BigInteger}
 * - rounded, and refused when the class cannot hold it ({@code Number out of representable range: type int32,
 * value 3000000000 in function JI with handler H.run}); a FLOAT a {@code double} or a {@code float}; a BOOLEAN a
 * {@code boolean}; any scalar a {@code String}; a DATE a {@code java.sql.Date} at midnight UTC, a TIME a
 * {@code java.sql.Time} and a TIMESTAMP a {@code java.sql.Timestamp} with its nanoseconds; an ARRAY a
 * {@code String[]}, an OBJECT a {@code Map} and a BINARY a {@code byte[]}. A NULL reaches a box or an object as
 * {@code null} and is refused for a primitive: {@code Cannot pass a NULL value as input to a Java type of long}.
 * Every answer is live-verified.
 */
public class JavaHandlerArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void eachParameterTypeAsTheHandlersClass() {
        assertAnswer(
            "Function JV_NUMBER_LONG successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_number_long(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, long a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=7",
            "CALL jv_number_long(7)");
        assertAnswer(
            "v=7",
            "CALL jv_number_long('7')");
        assertAnswer(
            "v=3",
            "CALL jv_number_long(2.5)");
        assertAnswer(
            "v=3",
            "CALL jv_number_long('2.5')");
        assertAnswer(
            "v=2",
            "CALL jv_number_long(2.4)");
        assertAnswer(
            "v=-3",
            "CALL jv_number_long(-2.5)");
        assertAnswer(
            "Function JV_NUMBER_INT successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_number_int(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, int a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=7",
            "CALL jv_number_int(7)");
        assertAnswer(
            "v=7",
            "CALL jv_number_int('7')");
        assertAnswer(
            "v=3",
            "CALL jv_number_int(2.5)");
        assertAnswer(
            "v=3",
            "CALL jv_number_int('2.5')");
        assertAnswer(
            "v=2",
            "CALL jv_number_int(2.4)");
        assertAnswer(
            "v=-3",
            "CALL jv_number_int(-2.5)");
        assertAnswer(
            "Function JV_NUMBER_JAVA_MATH_BIGDECIMAL successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_number_java_math_bigdecimal(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA "
                + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ "
                + "import com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                + "java.math.BigDecimal a) { return \"v=\" + a; } } $$");
        assertAnswer(
            "v=7",
            "CALL jv_number_java_math_bigdecimal(7)");
        assertAnswer(
            "v=7",
            "CALL jv_number_java_math_bigdecimal('7')");
        assertAnswer(
            "v=3",
            "CALL jv_number_java_math_bigdecimal(2.5)");
        assertAnswer(
            "v=3",
            "CALL jv_number_java_math_bigdecimal('2.5')");
        assertAnswer(
            "v=2",
            "CALL jv_number_java_math_bigdecimal(2.4)");
        assertAnswer(
            "v=-3",
            "CALL jv_number_java_math_bigdecimal(-2.5)");
        assertAnswer(
            "Function JV_NUMBER_LONG successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_number_long(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, Long a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=7",
            "CALL jv_number_long(7)");
        assertAnswer(
            "v=7",
            "CALL jv_number_long('7')");
        assertAnswer(
            "v=3",
            "CALL jv_number_long(2.5)");
        assertAnswer(
            "v=3",
            "CALL jv_number_long('2.5')");
        assertAnswer(
            "v=2",
            "CALL jv_number_long(2.4)");
        assertAnswer(
            "v=-3",
            "CALL jv_number_long(-2.5)");
        assertAnswer(
            "Function JV_NUMBER_10_2_JAVA_MATH_BIGDECIMAL successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_number_10_2_java_math_bigdecimal(a NUMBER(10,2)) RETURNS VARCHAR "
                + "LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = "
                + "'H.run' AS $$ import com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                + "java.math.BigDecimal a) { return \"v=\" + a; } } $$");
        assertAnswer(
            "v=1.25",
            "CALL jv_number_10_2_java_math_bigdecimal(1.25)");
        assertAnswer(
            "v=1.26",
            "CALL jv_number_10_2_java_math_bigdecimal(1.255)");
        assertAnswer(
            "v=1.25",
            "CALL jv_number_10_2_java_math_bigdecimal(1.245)");
        assertAnswer(
            "v=1.26",
            "CALL jv_number_10_2_java_math_bigdecimal('1.255')");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB8](10,2){not null}, value 123456789.12",
            "CALL jv_number_10_2_java_math_bigdecimal(123456789.12)");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL jv_number_10_2_java_math_bigdecimal('x')");
        assertAnswer(
            "REFUSED Snowflake type FIXED[SB16](10,2){nullable} is not supported as input to argument at index 0 "
                + "with Java type double in function JV_NUMBER_10_2_DOUBLE with handler H.run",
            "CREATE OR REPLACE PROCEDURE jv_number_10_2_double(a NUMBER(10,2)) RETURNS VARCHAR LANGUAGE JAVA "
                + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ "
                + "import com.snowflake.snowpark_java.*; class H { public static String run(Session s, double a) { "
                + "return \"v=\" + a; } } $$");
        assertAnswer(
            "Function JV_FLOAT_DOUBLE successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_float_double(a FLOAT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, double a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=1.0",
            "CALL jv_float_double(1)");
        assertAnswer(
            "v=1.5",
            "CALL jv_float_double(1.5)");
        assertAnswer(
            "v=1.5",
            "CALL jv_float_double('1.5')");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL jv_float_double('x')");
        assertAnswer(
            "v=NaN",
            "CALL jv_float_double('NaN')");
        assertAnswer(
            "v=Infinity",
            "CALL jv_float_double('inf')");
        assertAnswer(
            "Function JV_FLOAT_FLOAT successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_float_float(a FLOAT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, float a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=1.0",
            "CALL jv_float_float(1)");
        assertAnswer(
            "v=1.5",
            "CALL jv_float_float(1.5)");
        assertAnswer(
            "v=1.5",
            "CALL jv_float_float('1.5')");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL jv_float_float('x')");
        assertAnswer(
            "v=NaN",
            "CALL jv_float_float('NaN')");
        assertAnswer(
            "v=Infinity",
            "CALL jv_float_float('inf')");
        assertAnswer(
            "Function JV_VARCHAR_STRING successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_varchar_string(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
                + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ "
                + "import com.snowflake.snowpark_java.*; class H { public static String run(Session s, String a) { "
                + "return \"v=\" + a; } } $$");
        assertAnswer(
            "v=42",
            "CALL jv_varchar_string(42)");
        assertAnswer(
            "v=1.5",
            "CALL jv_varchar_string(1.50)");
        assertAnswer(
            "v=true",
            "CALL jv_varchar_string(TRUE)");
        assertAnswer(
            "v=2024-01-02",
            "CALL jv_varchar_string('2024-01-02'::DATE)");
        answer(
            "CALL jv_varchar_string(TO_TIMESTAMP_NTZ('2024-01-02 03:04:05'))");
        assertAnswer(
            "v=03:04:05",
            "CALL jv_varchar_string(TO_TIME('03:04:05'))");
        assertAnswer(
            "Function JV_BOOLEAN_BOOLEAN successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_boolean_boolean(a BOOLEAN) RETURNS VARCHAR LANGUAGE JAVA "
                + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ "
                + "import com.snowflake.snowpark_java.*; class H { public static String run(Session s, boolean a) { "
                + "return \"v=\" + a; } } $$");
        assertAnswer(
            "v=true",
            "CALL jv_boolean_boolean(TRUE)");
        assertAnswer(
            "v=false",
            "CALL jv_boolean_boolean(FALSE)");
        assertAnswer(
            "v=false",
            "CALL jv_boolean_boolean('off')");
        assertAnswer(
            "v=true",
            "CALL jv_boolean_boolean('true')");
        assertAnswer(
            "v=true",
            "CALL jv_boolean_boolean('yes')");
        assertAnswer(
            "REFUSED Boolean value 'maybe' is not recognized",
            "CALL jv_boolean_boolean('maybe')");
        assertAnswer(
            "Function JV_BOOLEAN_BOOLEAN successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_boolean_boolean(a BOOLEAN) RETURNS VARCHAR LANGUAGE JAVA "
                + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ "
                + "import com.snowflake.snowpark_java.*; class H { public static String run(Session s, Boolean a) { "
                + "return \"v=\" + a; } } $$");
        assertAnswer(
            "v=true",
            "CALL jv_boolean_boolean(TRUE)");
        assertAnswer(
            "v=false",
            "CALL jv_boolean_boolean(FALSE)");
        assertAnswer(
            "v=false",
            "CALL jv_boolean_boolean('off')");
        assertAnswer(
            "v=true",
            "CALL jv_boolean_boolean('true')");
        assertAnswer(
            "v=true",
            "CALL jv_boolean_boolean('yes')");
        assertAnswer(
            "REFUSED Boolean value 'maybe' is not recognized",
            "CALL jv_boolean_boolean('maybe')");
        assertAnswer(
            "Function JV_TIMESTAMP_NTZ_JAVA_SQL_TIMESTAMP successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_timestamp_ntz_java_sql_timestamp(a TIMESTAMP_NTZ) RETURNS VARCHAR "
                + "LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = "
                + "'H.run' AS $$ import com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                + "java.sql.Timestamp a) { return \"v=\" + a; } } $$");
        answer(
            "CALL jv_timestamp_ntz_java_sql_timestamp('2024-01-31 10:11:12')");
        answer(
            "CALL jv_timestamp_ntz_java_sql_timestamp('2024-01-31')");
        answer(
            "CALL jv_timestamp_ntz_java_sql_timestamp('2024-01-31'::DATE)");
        assertAnswer(
            "REFUSED Timestamp 'x' is not recognized",
            "CALL jv_timestamp_ntz_java_sql_timestamp('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JV_TIMESTAMP_NTZ_JAVA_SQL_TIMESTAMP': (NUMBER(10,0))",
            "CALL jv_timestamp_ntz_java_sql_timestamp(1706695872)");
        answer(
            "CALL jv_timestamp_ntz_java_sql_timestamp('2024-01-31 10:11:12 +02:00')");
        assertAnswer(
            "Function JV_VARIANT_STRING successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_variant_string(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA "
                + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ "
                + "import com.snowflake.snowpark_java.*; class H { public static String run(Session s, String a) { "
                + "return \"v=\" + a; } } $$");
        assertAnswer(
            "v=1",
            "CALL jv_variant_string(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JV_VARIANT_STRING': (VARCHAR(1))",
            "CALL jv_variant_string('a')");
        assertAnswer(
            "v=1.5",
            "CALL jv_variant_string(1.5)");
        assertAnswer(
            "v=true",
            "CALL jv_variant_string(TRUE)");
        assertAnswer(
            "v={\"a\":1}",
            "CALL jv_variant_string(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "v=[1,2]",
            "CALL jv_variant_string(ARRAY_CONSTRUCT(1, 2))");
        assertAnswer(
            "Function JV_ARRAY_STRINGS successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_array_strings(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION "
                + "= '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String[] a) { return "
                + "\"v=\" + java.util.Arrays.toString(a); } } $$");
        assertAnswer(
            "v=[1, 2]",
            "CALL jv_array_strings(ARRAY_CONSTRUCT(1, 2))");
        assertAnswer(
            "v=[1, 2]",
            "CALL jv_array_strings(PARSE_JSON('[1, 2]'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JV_ARRAY_STRINGS': (VARCHAR(6))",
            "CALL jv_array_strings('[1, 2]')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JV_ARRAY_STRINGS': (NUMBER(1,0))",
            "CALL jv_array_strings(1)");
        assertAnswer(
            "v=[{\"a\":1}]",
            "CALL jv_array_strings(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "v=null",
            "CALL jv_array_strings(NULL)");
        assertAnswer(
            "Function JV_BINARY_BYTES successfully created.",
            "CREATE OR REPLACE PROCEDURE jv_binary_bytes(a BINARY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION "
                + "= '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, byte[] a) { return "
                + "\"v=\" + java.util.Arrays.toString(a); } } $$");
        assertAnswer(
            "v=[-85, -51]",
            "CALL jv_binary_bytes(TO_BINARY('ABCD'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JV_BINARY_BYTES': (VARCHAR(4))",
            "CALL jv_binary_bytes('ABCD')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JV_BINARY_BYTES': (VARCHAR(2))",
            "CALL jv_binary_bytes('zz')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JV_BINARY_BYTES': (NUMBER(1,0))",
            "CALL jv_binary_bytes(1)");
    }

    @Test
    public void nullsWideNumbersAndTheOtherClasses() {
        assertAnswer(
            "Function JL successfully created.",
            "CREATE OR REPLACE PROCEDURE jl(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, long a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of long in function JL with handler H.run",
            "CALL jl(NULL)");
        assertAnswer(
            "v=9223372036854775807",
            "CALL jl(9223372036854775807)");
        assertAnswer(
            "REFUSED Number out of representable range: type int64, value 9223372036854775808 in function JL with "
                + "handler H.run",
            "CALL jl(9223372036854775808)");
        assertAnswer(
            "v=3000000000",
            "CALL jl(3000000000)");
        assertAnswer(
            "Function JI successfully created.",
            "CREATE OR REPLACE PROCEDURE ji(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, int a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of int in function JI with handler H.run",
            "CALL ji(NULL)");
        assertAnswer(
            "v=2147483647",
            "CALL ji(2147483647)");
        assertAnswer(
            "REFUSED Number out of representable range: type int32, value 3000000000 in function JI with handler "
                + "H.run",
            "CALL ji(3000000000)");
        assertAnswer(
            "REFUSED Number out of representable range: type int32, value -2147483649 in function JI with handler "
                + "H.run",
            "CALL ji(-2147483649)");
        assertAnswer(
            "Function JLB successfully created.",
            "CREATE OR REPLACE PROCEDURE jlb(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, Long a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=null",
            "CALL jlb(NULL)");
        assertAnswer(
            "v=7",
            "CALL jlb(7)");
        assertAnswer(
            "REFUSED Number out of representable range: type int64, value 9223372036854775808 in function JLB "
                + "with handler H.run",
            "CALL jlb(9223372036854775808)");
        assertAnswer(
            "Function JIB successfully created.",
            "CREATE OR REPLACE PROCEDURE jib(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, Integer a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=null",
            "CALL jib(NULL)");
        assertAnswer(
            "v=7",
            "CALL jib(7)");
        assertAnswer(
            "REFUSED Number out of representable range: type int32, value 3000000000 in function JIB with handler "
                + "H.run",
            "CALL jib(3000000000)");
        assertAnswer(
            "Function JSH successfully created.",
            "CREATE OR REPLACE PROCEDURE jsh(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, short a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=7",
            "CALL jsh(7)");
        assertAnswer(
            "REFUSED Number out of representable range: type int16, value 40000 in function JSH with handler H.run",
            "CALL jsh(40000)");
        assertAnswer(
            "REFUSED Snowflake type FIXED[SB16](38,0){nullable} is not supported as input to argument at index 0 "
                + "with Java type double in function JD0 with handler H.run",
            "CREATE OR REPLACE PROCEDURE jd0(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, double a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "Function JBD successfully created.",
            "CREATE OR REPLACE PROCEDURE jbd(a NUMBER(38,10)) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, java.math.BigDecimal "
                + "a) { return \"v=\" + a; } } $$");
        assertAnswer(
            "v=1.5000000000",
            "CALL jbd(1.5)");
        assertAnswer(
            "v=1.2345678901",
            "CALL jbd('1.23456789012345')");
        assertAnswer(
            "v=null",
            "CALL jbd(NULL)");
        assertAnswer(
            "Function JBI successfully created.",
            "CREATE OR REPLACE PROCEDURE jbi(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, java.math.BigInteger "
                + "a) { return \"v=\" + a; } } $$");
        assertAnswer(
            "v=7",
            "CALL jbi(7)");
        assertAnswer(
            "v=12345678901234567890123456789",
            "CALL jbi(12345678901234567890123456789)");
        assertAnswer(
            "Function JDB successfully created.",
            "CREATE OR REPLACE PROCEDURE jdb(a FLOAT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, Double a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=null",
            "CALL jdb(NULL)");
        assertAnswer(
            "v=1.5",
            "CALL jdb(1.5)");
        assertAnswer(
            "v=NaN",
            "CALL jdb('NaN')");
        assertAnswer(
            "Function JST successfully created.",
            "CREATE OR REPLACE PROCEDURE jst(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=null",
            "CALL jst(NULL)");
        assertAnswer(
            "v={\"a\":1}",
            "CALL jst(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "v=0.333333",
            "CALL jst(1/3)");
        assertAnswer(
            "Function JBO successfully created.",
            "CREATE OR REPLACE PROCEDURE jbo(a BOOLEAN) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, boolean a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of boolean in function JBO with handler "
                + "H.run",
            "CALL jbo(NULL)");
        assertAnswer(
            "v=false",
            "CALL jbo(0)");
        assertAnswer(
            "v=true",
            "CALL jbo(2.5)");
        assertAnswer(
            "Function JBB successfully created.",
            "CREATE OR REPLACE PROCEDURE jbb(a BOOLEAN) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, Boolean a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=null",
            "CALL jbb(NULL)");
        assertAnswer(
            "v=true",
            "CALL jbb(1)");
        assertAnswer(
            "Function JDT successfully created.",
            "CREATE OR REPLACE PROCEDURE jdt(a DATE) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, java.sql.Date a) { "
                + "return \"v=\" + (a == null ? null : a.getTime()); } } $$");
        assertAnswer(
            "v=null",
            "CALL jdt(NULL)");
        assertAnswer(
            "v=1706659200000",
            "CALL jdt('2024-01-31')");
        assertAnswer(
            "v=86400000",
            "CALL jdt('1970-01-02'::DATE)");
        assertAnswer(
            "Function JTM successfully created.",
            "CREATE OR REPLACE PROCEDURE jtm(a TIME) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, java.sql.Time a) { "
                + "return \"v=\" + (a == null ? null : a.getTime()); } } $$");
        assertAnswer(
            "v=null",
            "CALL jtm(NULL)");
        assertAnswer(
            "v=36672000",
            "CALL jtm('10:11:12')");
        assertAnswer(
            "Function JTS successfully created.",
            "CREATE OR REPLACE PROCEDURE jts(a TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, java.sql.Timestamp "
                + "a) { return \"v=\" + (a == null ? null : a.getTime() + \"/\" + a.getNanos()); } } $$");
        assertAnswer(
            "v=null",
            "CALL jts(NULL)");
        assertAnswer(
            "v=1706695872123/123456789",
            "CALL jts('2024-01-31 10:11:12.123456789')");
        assertAnswer(
            "Function JTZ successfully created.",
            "CREATE OR REPLACE PROCEDURE jtz(a TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, java.sql.Timestamp "
                + "a) { return \"v=\" + a.getTime(); } } $$");
        assertAnswer(
            "v=1706695872000",
            "CALL jtz('2024-01-31 10:11:12 +00:00')");
        assertAnswer(
            "REFUSED Unsupported argument type: java.time.LocalDate in function JDL with handler H.run",
            "CREATE OR REPLACE PROCEDURE jdl(a DATE) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, java.time.LocalDate "
                + "a) { return \"v=\" + a; } } $$");
        assertAnswer(
            "Function JDS successfully created.",
            "CREATE OR REPLACE PROCEDURE jds(a DATE) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=2024-01-31",
            "CALL jds('2024-01-31')");
        answer(
            "CREATE OR REPLACE PROCEDURE jvs(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, Variant a) { return "
                + "\"v=\" + a.asJsonString(); } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JVS.",
            "CALL jvs(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JVS.",
            "CALL jvs(1)");
        assertAnswer(
            "Function JAS successfully created.",
            "CREATE OR REPLACE PROCEDURE jas(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String[] a) { return "
                + "\"v=\" + a.length + java.util.Arrays.toString(a); } } $$");
        assertAnswer(
            "v=3[1, null, x]",
            "CALL jas(ARRAY_CONSTRUCT(1, NULL, 'x'))");
        assertAnswer(
            "v=1[1]",
            "CALL jas(PARSE_JSON('1'))");
        assertAnswer(
            "Function JOS successfully created.",
            "CREATE OR REPLACE PROCEDURE jos(a OBJECT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, java.util.Map<String,"
                + " String> a) { return \"v=\" + a; } } $$");
        assertAnswer(
            "v={a=1, b=x}",
            "CALL jos(OBJECT_CONSTRUCT('a', 1, 'b', 'x'))");
        assertAnswer(
            "Function JBY successfully created.",
            "CREATE OR REPLACE PROCEDURE jby(a BINARY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, byte[] a) { return "
                + "\"v=\" + (a == null ? null : a.length); } } $$");
        assertAnswer(
            "v=2",
            "CALL jby(TO_BINARY('ABCD'))");
        assertAnswer(
            "v=null",
            "CALL jby(NULL)");
        assertAnswer(
            "REFUSED Snowflake type TEXT[LOB](134217728){nullable} is not supported as input to argument at index "
                + "0 with Java type int in function JSN with handler H.run",
            "CREATE OR REPLACE PROCEDURE jsn(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, int a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "Function JNN successfully created.",
            "CREATE OR REPLACE PROCEDURE jnn(a NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String a) { return "
                + "\"v=\" + a; } } $$");
        assertAnswer(
            "v=7",
            "CALL jnn(7)");
    }
}
