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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * What an inline Java function's numeric parameter takes. The argument becomes the parameter's SQL type first:
 * a NUMBER parameter rounds a fraction away, half away from zero, and reads a text, so an {@code int} handler
 * is handed 2 for 1.99 and 42 for {@code '42'}. A value outside the Java type's range is refused, never
 * wrapped: {@code Number out of representable range: type int32, value 3000000000}. A SQL NULL is refused
 * for a primitive type and is {@code null} for a boxed one. A {@code BigDecimal} holds the parameter's
 * declared scale, a {@code double} the nearest value. A pairing the types do not allow - a NUMBER with
 * decimals for an {@code int}, a NUMBER for a {@code double} - is refused when the function is created.
 * Every answer is live-verified.
 */
public class JavaIntegerParameterTest extends MeasuredCellsTestSupport {

    @Test
    public void anIntegerParameterIsNeverWrappedOrTruncated() {
        assertAnswer(
            "Function FI successfully created.",
            "CREATE OR REPLACE FUNCTION fi(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H { "
                + "public static int f(int x) { return x; } } $$");
        assertAnswer(
            "7",
            "SELECT fi(7)");
        assertAnswer(
            "REFUSED Number out of representable range: type int32, value 3000000000 in function FI with handler "
                + "H.f",
            "SELECT fi(3000000000)");
        assertAnswer(
            "REFUSED Number out of representable range: type int32, value 2147483648 in function FI with handler "
                + "H.f",
            "SELECT fi(2147483648)");
        assertAnswer(
            "2147483647",
            "SELECT fi(2147483647)");
        assertAnswer(
            "-2147483648",
            "SELECT fi(-2147483648)");
        assertAnswer(
            "REFUSED Number out of representable range: type int32, value -2147483649 in function FI with handler "
                + "H.f",
            "SELECT fi(-2147483649)");
        assertAnswer(
            "2",
            "SELECT fi(1.99)");
        assertAnswer(
            "-2",
            "SELECT fi(-1.5)");
        assertAnswer(
            "3",
            "SELECT fi(2.5)");
        assertAnswer(
            "2",
            "SELECT fi(1.5::FLOAT)");
        assertAnswer(
            "1",
            "SELECT fi(1.4)");
        assertAnswer(
            "42",
            "SELECT fi('42')");
        assertAnswer(
            "5",
            "SELECT fi('4.6')");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of int in function FI with handler H.f",
            "SELECT fi(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FI': (BOOLEAN)",
            "SELECT fi(TRUE)");
        assertAnswer(
            "REFUSED Numeric value 'abc' is not recognized",
            "SELECT fi('abc')");
        assertAnswer(
            "Function FL successfully created.",
            "CREATE OR REPLACE FUNCTION fl(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H { "
                + "public static long f(long x) { return x; } } $$");
        assertAnswer(
            "REFUSED Number out of representable range: type int64, value 99999999999999999999999 in function FL "
                + "with handler H.f",
            "SELECT fl(99999999999999999999999)");
        assertShapedAnswer(
            "<ID19>",
            "SELECT fl(9223372036854775807)");
        assertShapedAnswer(
            "REFUSED Number out of representable range: type int64, value <ID19> in function FL with handler H.f",
            "SELECT fl(9223372036854775808)");
        assertAnswer(
            "2",
            "SELECT fl(1.5)");
        assertAnswer(
            "Function FSH successfully created.",
            "CREATE OR REPLACE FUNCTION fsh(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ public static short f(short x) { return x; } } $$");
        assertAnswer(
            "REFUSED Number out of representable range: type int16, value 40000 in function FSH with handler H.f",
            "SELECT fsh(40000)");
        assertAnswer(
            "32767",
            "SELECT fsh(32767)");
        assertAnswer(
            "REFUSED Number out of representable range: type int16, value -32769 in function FSH with handler H.f",
            "SELECT fsh(-32769)");
        assertAnswer(
            "2",
            "SELECT fsh(1.5)");
        assertAnswer(
            "Function FBIGINT successfully created.",
            "CREATE OR REPLACE FUNCTION fbigint(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class H { public static java.math.BigInteger f(java.math.BigInteger x) { return "
                + "x.add(java.math.BigInteger.ONE); } } $$");
        assertAnswer(
            "3",
            "SELECT fbigint(1.5)");
        assertAnswer(
            "99999999999999999999999999999999999999",
            "SELECT fbigint(99999999999999999999999999999999999998)");
        assertAnswer(
            "3",
            "SELECT fbigint(2.4)");
        assertAnswer(
            "Function FBIGDEC successfully created.",
            "CREATE OR REPLACE FUNCTION fbigdec(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class H { public static String f(java.math.BigDecimal x) { return x.toString() + \"/\" + "
                + "x.scale(); } } $$");
        assertAnswer(
            "2/0",
            "SELECT fbigdec(1.5)");
        assertAnswer(
            "7/0",
            "SELECT fbigdec(7)");
        assertAnswer(
            "2/0",
            "SELECT fbigdec(1.50::NUMBER(10,2))");
        assertAnswer(
            "2/0",
            "SELECT fbigdec(1.5::FLOAT)");
        assertAnswer(
            "Function FN2 successfully created.",
            "CREATE OR REPLACE FUNCTION fn2(x NUMBER(10,2)) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class H { public static String f(java.math.BigDecimal x) { return x.toString() + \"/\" + "
                + "x.scale(); } } $$");
        assertAnswer(
            "1.50/2",
            "SELECT fn2(1.5)");
        assertAnswer(
            "7.00/2",
            "SELECT fn2(7)");
        assertAnswer(
            "1.01/2",
            "SELECT fn2(1.005)");
        assertAnswer(
            "2.56/2",
            "SELECT fn2('2.555')");
        assertAnswer(
            "REFUSED Cannot encode number with a non-zero scale as an int in function FN2I with handler H.f",
            "CREATE OR REPLACE FUNCTION fn2i(x NUMBER(10,2)) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class H { public static int f(int x) { return x; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FN2I.",
            "SELECT fn2i(7)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FN2I.",
            "SELECT fn2i(1.5)");
        assertAnswer(
            "Function FD successfully created.",
            "CREATE OR REPLACE FUNCTION fd(x FLOAT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H { "
                + "public static String f(double x) { return String.valueOf(x); } } $$");
        assertAnswer(
            "1.0",
            "SELECT fd(1)");
        assertAnswer(
            "1.5",
            "SELECT fd(1.5)");
        assertAnswer(
            "2.5",
            "SELECT fd('2.5')");
        assertAnswer(
            "1.2345678901234567E19",
            "SELECT fd(12345678901234567890)");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of double in function FD with handler H.f",
            "SELECT fd(NULL)");
        assertAnswer(
            "Function FFL successfully created.",
            "CREATE OR REPLACE FUNCTION ffl(x FLOAT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ public static String f(float x) { return String.valueOf(x); } } $$");
        assertAnswer(
            "1.1",
            "SELECT ffl(1.1)");
        assertAnswer(
            "Infinity",
            "SELECT ffl(1e39::FLOAT)");
        assertAnswer(
            "REFUSED Snowflake type FIXED[SB16](38,0){nullable} is not supported as input to argument at index 0 "
                + "with Java type double in function FID with handler H.f",
            "CREATE OR REPLACE FUNCTION fid(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ public static String f(double x) { return String.valueOf(x); } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FID.",
            "SELECT fid(7)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FID.",
            "SELECT fid(1.5)");
        assertAnswer(
            "Function FBOX successfully created.",
            "CREATE OR REPLACE FUNCTION fbox(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public static String f(Integer x) { return String.valueOf(x); } } $$");
        assertAnswer(
            "7",
            "SELECT fbox(7)");
        assertAnswer(
            "null",
            "SELECT fbox(NULL)");
        assertAnswer(
            "REFUSED Number out of representable range: type int32, value 3000000000 in function FBOX with "
                + "handler H.f",
            "SELECT fbox(3000000000)");
        assertAnswer(
            "2",
            "SELECT fbox(1.5)");
        assertAnswer(
            "Function FLBOX successfully created.",
            "CREATE OR REPLACE FUNCTION flbox(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public static String f(Long x) { return String.valueOf(x); } } $$");
        assertAnswer(
            "null",
            "SELECT flbox(NULL)");
        assertAnswer(
            "2",
            "SELECT flbox(1.5)");
        assertAnswer(
            "REFUSED Unsupported argument type: byte in function FBYTE with handler H.f",
            "CREATE OR REPLACE FUNCTION fbyte(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public static byte f(byte x) { return x; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FBYTE.",
            "SELECT fbyte(1)");
        assertAnswer(
            "Function FRI successfully created.",
            "CREATE OR REPLACE FUNCTION fri(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ public static long f(int x) { return x * 2; } } $$");
        assertAnswer(
            "2000000000",
            "SELECT fri(1000000000)");
        assertAnswer(
            "Function FRS successfully created.",
            "CREATE OR REPLACE FUNCTION frs(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ public static short f(int x) { return (short) x; } } $$");
        answer(
            "SELECT frs(5), TYPEOF(frs(5)::VARIANT)");
        assertAnswer(
            "REFUSED Snowflake type FIXED[SB16](38,0){nullable} is not supported for Java return type double in "
                + "function FRD with handler H.f",
            "CREATE OR REPLACE FUNCTION frd(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ public static double f(int x) { return x + 0.5; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FRD.",
            "SELECT frd(1)");
    }
}
