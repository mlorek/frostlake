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
 * What a {@code Variant} a Java handler builds and hands back is as a VARIANT: an {@code int}, a {@code long},
 * a {@code short} and a {@code BigInteger} an INTEGER, a {@code double} and a {@code float} a DOUBLE, a
 * {@code BigDecimal} a DECIMAL without its trailing zeros, a {@code boolean} a BOOLEAN, bytes a BINARY, a
 * {@code java.sql} value a DATE, a TIME or a TIMESTAMP_NTZ, a list, an array and a map an ARRAY or an OBJECT.
 * One built from a text is the OBJECT, the ARRAY or the JSON null the text spells and a VARCHAR otherwise -
 * {@code new Variant("7")} is the text 7 - and a Java {@code null} is a SQL NULL. The session's TIMEZONE is
 * set by the test and unset whatever the assertions make of the statements between. Every answer is
 * live-verified.
 */
public class SnowparkVariantBuildTest extends MeasuredCellsTestSupport {

    @Test
    public void aBuiltVariantIsTheValueItWasMadeAs() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function BN successfully created.",
                "CREATE OR REPLACE FUNCTION bn(n NUMBER) RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } public static Variant f(int n) { switch (n) { "
                    + "case 1: return new Variant(1); case 2: return new Variant(1.5); case 3: return new "
                    + "Variant(1.5f); case 4: return new Variant(true); case 5: return new Variant(7L); case 6: "
                    + "return new Variant((short) 3); case 7: return new Variant(new "
                    + "java.math.BigDecimal(\"1.50\")); case 8: return new Variant(java.math.BigInteger.TEN); case "
                    + "9: return new Variant(Double.NaN); case 10: return new Variant(100.0); default: return null; "
                    + "} } } $$");
            assertAnswer(
                "INTEGER|1|false",
                "SELECT TYPEOF(bn(1)), TO_JSON(bn(1)), bn(1) IS NULL");
            assertAnswer(
                "DOUBLE|1.500000000000000e+00|false",
                "SELECT TYPEOF(bn(2)), TO_JSON(bn(2)), bn(2) IS NULL");
            assertAnswer(
                "DOUBLE|1.500000000000000e+00|false",
                "SELECT TYPEOF(bn(3)), TO_JSON(bn(3)), bn(3) IS NULL");
            assertAnswer(
                "BOOLEAN|true|false",
                "SELECT TYPEOF(bn(4)), TO_JSON(bn(4)), bn(4) IS NULL");
            assertAnswer(
                "INTEGER|7|false",
                "SELECT TYPEOF(bn(5)), TO_JSON(bn(5)), bn(5) IS NULL");
            assertAnswer(
                "INTEGER|3|false",
                "SELECT TYPEOF(bn(6)), TO_JSON(bn(6)), bn(6) IS NULL");
            assertAnswer(
                "DECIMAL|1.5|false",
                "SELECT TYPEOF(bn(7)), TO_JSON(bn(7)), bn(7) IS NULL");
            assertAnswer(
                "INTEGER|10|false",
                "SELECT TYPEOF(bn(8)), TO_JSON(bn(8)), bn(8) IS NULL");
            assertAnswer(
                "DOUBLE|NaN|false",
                "SELECT TYPEOF(bn(9)), TO_JSON(bn(9)), bn(9) IS NULL");
            assertAnswer(
                "DOUBLE|1.000000000000000e+02|false",
                "SELECT TYPEOF(bn(10)), TO_JSON(bn(10)), bn(10) IS NULL");
            assertAnswer(
                "null|null|true",
                "SELECT TYPEOF(bn(11)), TO_JSON(bn(11)), bn(11) IS NULL");
            assertAnswer(
                "Function BS successfully created.",
                "CREATE OR REPLACE FUNCTION bs(n NUMBER) RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } public static Variant f(int n) { switch (n) { "
                    + "case 1: return new Variant(\"s\"); case 2: return new Variant(\"{\\\"a\\\":1}\"); case 3: "
                    + "return new Variant(\"[1,2]\"); case 4: return new Variant(\"7\"); case 5: return new "
                    + "Variant(\"true\"); case 6: return new Variant(\"null\"); case 7: return new Variant(\"\"); "
                    + "case 8: return new Variant(\"{bad\"); case 9: return new Variant(\"1.5\"); case 10: return "
                    + "new Variant(\"\\\"q\\\"\"); default: return null; } } } $$");
            assertAnswer(
                "VARCHAR|\"s\"|false",
                "SELECT TYPEOF(bs(1)), TO_JSON(bs(1)), bs(1) IS NULL");
            assertAnswer(
                "OBJECT|{\"a\":1}|false",
                "SELECT TYPEOF(bs(2)), TO_JSON(bs(2)), bs(2) IS NULL");
            assertAnswer(
                "ARRAY|[1,2]|false",
                "SELECT TYPEOF(bs(3)), TO_JSON(bs(3)), bs(3) IS NULL");
            assertAnswer(
                "VARCHAR|\"7\"|false",
                "SELECT TYPEOF(bs(4)), TO_JSON(bs(4)), bs(4) IS NULL");
            assertAnswer(
                "VARCHAR|\"true\"|false",
                "SELECT TYPEOF(bs(5)), TO_JSON(bs(5)), bs(5) IS NULL");
            assertAnswer(
                "NULL_VALUE|null|false",
                "SELECT TYPEOF(bs(6)), TO_JSON(bs(6)), bs(6) IS NULL");
            assertAnswer(
                "VARCHAR|\"\"|false",
                "SELECT TYPEOF(bs(7)), TO_JSON(bs(7)), bs(7) IS NULL");
            assertAnswer(
                "VARCHAR|\"{bad\"|false",
                "SELECT TYPEOF(bs(8)), TO_JSON(bs(8)), bs(8) IS NULL");
            assertAnswer(
                "VARCHAR|\"1.5\"|false",
                "SELECT TYPEOF(bs(9)), TO_JSON(bs(9)), bs(9) IS NULL");
            assertAnswer(
                "VARCHAR|\"q\"|false",
                "SELECT TYPEOF(bs(10)), TO_JSON(bs(10)), bs(10) IS NULL");
            assertAnswer(
                "Function BC successfully created.",
                "CREATE OR REPLACE FUNCTION bc(n NUMBER) RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } public static Variant f(int n) { "
                    + "java.util.Map<String, Object> m = new java.util.HashMap<>(); m.put(\"k\", 1); m.put(\"s\", "
                    + "\"t\"); switch (n) { case 1: return new Variant(java.util.Arrays.<Object>asList(1, \"b\", "
                    + "null, 1.5)); case 2: return new Variant(new Object[] {1, \"b\"}); case 3: return new "
                    + "Variant((Object) m); case 4: return new Variant(new byte[] {(byte) 255, 1}); case 5: return "
                    + "new Variant((Object) 5); case 6: return new Variant((Object) new Variant(1)); case 7: return "
                    + "new Variant(java.util.Arrays.<Object>asList(new Variant(\"x\"), "
                    + "java.util.Arrays.<Object>asList(1))); default: return null; } } } $$");
            assertAnswer(
                "ARRAY|[1,\"b\",null,1.5]",
                "SELECT TYPEOF(bc(1)), TO_JSON(bc(1))");
            assertAnswer(
                "ARRAY|[1,\"b\"]",
                "SELECT TYPEOF(bc(2)), TO_JSON(bc(2))");
            assertAnswer(
                "OBJECT|{\"k\":1,\"s\":\"t\"}",
                "SELECT TYPEOF(bc(3)), TO_JSON(bc(3))");
            assertAnswer(
                "BINARY|\"FF01\"",
                "SELECT TYPEOF(bc(4)), TO_JSON(bc(4))");
            assertAnswer(
                "VARCHAR|\"5\"",
                "SELECT TYPEOF(bc(5)), TO_JSON(bc(5))");
            assertAnswer(
                "ARRAY|[\"x\",[1]]",
                "SELECT TYPEOF(bc(7)), TO_JSON(bc(7))");
            assertAnswer(
                "Function BT successfully created.",
                "CREATE OR REPLACE FUNCTION bt(n NUMBER) RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } public static Variant f(int n) { switch (n) { "
                    + "case 1: return new Variant(new java.sql.Date(86400000L)); case 2: return new Variant(new "
                    + "java.sql.Time(3600000L)); case 3: return new Variant(new java.sql.Timestamp(0L)); default: "
                    + "return null; } } } $$");
            assertAnswer(
                "DATE",
                "SELECT TYPEOF(bt(1))");
            assertAnswer(
                "TIME",
                "SELECT TYPEOF(bt(2))");
            assertAnswer(
                "TIMESTAMP_NTZ",
                "SELECT TYPEOF(bt(3))");
            assertAnswer(
                "Function FVAR successfully created.",
                "CREATE OR REPLACE FUNCTION fvar(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') AS $$ import "
                    + "com.snowflake.snowpark_java.types.Variant; class H { public static String f(Variant x) { "
                    + "return x.asMap().get(\"a\").asString(); } } $$");
            assertAnswer(
                "x",
                "SELECT fvar(PARSE_JSON('{\"a\": \"x\"}'))");
            assertAnswer(
                "Function FVL successfully created.",
                "CREATE OR REPLACE FUNCTION fvl() RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { public static Variant f() { return new Variant(java.util.Arrays.<Object>asList(1, "
                    + "2)); } } $$");
            assertAnswer(
                "[1,2]",
                "SELECT fvl()");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
