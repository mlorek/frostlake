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
 * What a VARIANT argument is once a Java handler hands its {@code Variant} back unchanged, what a built one
 * answers, and a procedure's {@code Variant} argument and result. A number, a BOOLEAN, a BINARY, a DATE and
 * an OBJECT come back as they went; a TIME comes back to the second and any timestamp as a TIMESTAMP_NTZ; a
 * text that spells a number comes back as the text of that number, one that spells an object as the OBJECT
 * and one that spells {@code null} as a JSON null. The session's TIMEZONE is set by the tests and unset
 * whatever the assertions make of the statements between. Every answer is live-verified.
 */
public class SnowparkVariantResultTest extends MeasuredCellsTestSupport {

    @Test
    public void aVariantHandedBackIsTheValueItWasMadeAs() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function RV successfully created.",
                "CREATE OR REPLACE FUNCTION rv(x VARIANT) RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' PACKAGES "
                    + "= ('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } static String msg(C c) { try { Object o = "
                    + "c.get(); return \"OK:\" + (o == null ? \"NULL\" : String.valueOf(o)); } catch (Throwable e) { "
                    + "return e.getClass().getSimpleName() + \":\" + e.getMessage(); } } static String hex(byte[] b) "
                    + "{ if (b == null) return \"NULL\"; StringBuilder s = new StringBuilder(); for (byte v : b) "
                    + "s.append(String.format(\"%02x\", v)); return s.toString(); } public static Variant f(Variant "
                    + "x) { return x; } } $$");
            assertAnswer(
                "INTEGER|1",
                "SELECT TYPEOF(rv(1)), TO_JSON(rv(1))");
            assertAnswer(
                "DECIMAL|1.5",
                "SELECT TYPEOF(rv(1.5)), TO_JSON(rv(1.5))");
            assertAnswer(
                "DOUBLE|1.500000000000000e+00",
                "SELECT TYPEOF(rv(1.5::FLOAT)), TO_JSON(rv(1.5::FLOAT))");
            assertAnswer(
                "BOOLEAN|true",
                "SELECT TYPEOF(rv(TRUE::VARIANT)), TO_JSON(rv(TRUE::VARIANT))");
            assertAnswer(
                "VARCHAR|\"s\"",
                "SELECT TYPEOF(rv(TO_VARIANT('s'))), TO_JSON(rv(TO_VARIANT('s')))");
            assertAnswer(
                "DATE|\"2024-03-05\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05'::DATE))), TO_JSON(rv(TO_VARIANT('2024-03-05'::DATE)))");
            assertAnswer(
                "TIME|\"10:11:12\"",
                "SELECT TYPEOF(rv(TO_VARIANT('10:11:12.123456789'::TIME))), "
                    + "TO_JSON(rv(TO_VARIANT('10:11:12.123456789'::TIME)))");
            answer(
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.123456789'::TIMESTAMP_NTZ))), "
                    + "TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.123456789'::TIMESTAMP_NTZ)))");
            answer(
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))), "
                    + "TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ)))");
            answer(
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.345 +02:00'::TIMESTAMP_TZ))), "
                    + "TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.345 +02:00'::TIMESTAMP_TZ)))");
            assertAnswer(
                "BINARY|\"FF01\"",
                "SELECT TYPEOF(rv(TO_VARIANT(TO_BINARY('FF01', 'HEX')))), TO_JSON(rv(TO_VARIANT(TO_BINARY('FF01', "
                    + "'HEX'))))");
            assertAnswer(
                "OBJECT|{\"a\":1}",
                "SELECT TYPEOF(rv(PARSE_JSON('{\"a\":1}'))), TO_JSON(rv(PARSE_JSON('{\"a\":1}')))");
            assertAnswer(
                "ARRAY|[1,2]",
                "SELECT TYPEOF(rv(PARSE_JSON('[1,2]'))), TO_JSON(rv(PARSE_JSON('[1,2]')))");
            assertAnswer(
                "NULL_VALUE|null",
                "SELECT TYPEOF(rv(PARSE_JSON('null'))), TO_JSON(rv(PARSE_JSON('null')))");
            assertAnswer(
                "DOUBLE|NaN",
                "SELECT TYPEOF(rv(TO_VARIANT('NaN'::FLOAT))), TO_JSON(rv(TO_VARIANT('NaN'::FLOAT)))");
            assertAnswer(
                "DOUBLE|1.000000000000000e+30",
                "SELECT TYPEOF(rv(TO_VARIANT(1e30::FLOAT))), TO_JSON(rv(TO_VARIANT(1e30::FLOAT)))");
            assertAnswer(
                "DECIMAL|12345678901234567890.5",
                "SELECT TYPEOF(rv(TO_VARIANT(12345678901234567890.5))), "
                    + "TO_JSON(rv(TO_VARIANT(12345678901234567890.5)))");
            assertAnswer(
                "INTEGER|100",
                "SELECT TYPEOF(rv(TO_VARIANT(100))), TO_JSON(rv(TO_VARIANT(100)))");
            assertAnswer(
                "DECIMAL|1.5",
                "SELECT TYPEOF(rv(TO_VARIANT(1.50))), TO_JSON(rv(TO_VARIANT(1.50)))");
            assertAnswer(
                "NULL_VALUE|null",
                "SELECT TYPEOF(rv(TO_VARIANT('null'))), TO_JSON(rv(TO_VARIANT('null')))");
            assertAnswer(
                "OBJECT|{\"a\":1}",
                "SELECT TYPEOF(rv(TO_VARIANT('{\"a\":1}'))), TO_JSON(rv(TO_VARIANT('{\"a\":1}')))");
            assertAnswer(
                "DOUBLE|Infinity",
                "SELECT TYPEOF(rv(TO_VARIANT('inf'::FLOAT))), TO_JSON(rv(TO_VARIANT('inf'::FLOAT)))");
            assertAnswer(
                "Function BT successfully created.",
                "CREATE OR REPLACE FUNCTION bt(n NUMBER) RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } static String msg(C c) { try { Object o = "
                    + "c.get(); return \"OK:\" + (o == null ? \"NULL\" : String.valueOf(o)); } catch (Throwable e) { "
                    + "return e.getClass().getSimpleName() + \":\" + e.getMessage(); } } static String hex(byte[] b) "
                    + "{ if (b == null) return \"NULL\"; StringBuilder s = new StringBuilder(); for (byte v : b) "
                    + "s.append(String.format(\"%02x\", v)); return s.toString(); } public static Variant f(int n) { "
                    + "switch (n) { case 1: return new Variant(new java.sql.Date(86400000L)); case 2: return new "
                    + "Variant(new java.sql.Time(3600000L)); case 3: return new Variant(new "
                    + "java.sql.Timestamp(1500L)); case 4: return new Variant(\"  7 \"); case 5: return new "
                    + "Variant((Object) 1.5); case 6: return new Variant((Object) true); case 7: return new "
                    + "Variant((Object) \"t\"); case 8: return new Variant(\"NULL\"); case 9: return new "
                    + "Variant(\"null dummy\"); case 10: return new Variant(new "
                    + "java.math.BigDecimal(\"12345678901234567890.5\")); case 11: return new Variant(new "
                    + "java.math.BigDecimal(\"3\")); case 12: return new Variant(1e20); case 13: return new "
                    + "Variant(Double.POSITIVE_INFINITY); case 14: return new Variant(7.0); case 15: return new "
                    + "Variant(new java.math.BigDecimal(\"1E+3\")); case 16: return new Variant(new "
                    + "java.sql.Time(90000000L)); case 17: return new Variant(java.util.Arrays.<Object>asList(new "
                    + "java.sql.Date(0L), new byte[] {1}, 1.5f, 2L)); default: return null; } } } $$");
            assertAnswer(
                "DATE|\"1970-01-02\"|1970-01-02",
                "SELECT TYPEOF(bt(1)), TO_JSON(bt(1)), bt(1)::VARCHAR");
            assertAnswer(
                "TIME|\"01:00:00\"|01:00:00",
                "SELECT TYPEOF(bt(2)), TO_JSON(bt(2)), bt(2)::VARCHAR");
            answer(
                "SELECT TYPEOF(bt(3)), TO_JSON(bt(3)), bt(3)::VARCHAR");
            assertAnswer(
                "VARCHAR|\"7\"|7",
                "SELECT TYPEOF(bt(4)), TO_JSON(bt(4)), bt(4)::VARCHAR");
            assertAnswer(
                "VARCHAR|\"1.5\"|1.5",
                "SELECT TYPEOF(bt(5)), TO_JSON(bt(5)), bt(5)::VARCHAR");
            assertAnswer(
                "VARCHAR|\"true\"|true",
                "SELECT TYPEOF(bt(6)), TO_JSON(bt(6)), bt(6)::VARCHAR");
            assertAnswer(
                "VARCHAR|\"t\"|t",
                "SELECT TYPEOF(bt(7)), TO_JSON(bt(7)), bt(7)::VARCHAR");
            assertAnswer(
                "VARCHAR|\"NULL\"|NULL",
                "SELECT TYPEOF(bt(8)), TO_JSON(bt(8)), bt(8)::VARCHAR");
            assertAnswer(
                "NULL_VALUE|null|null",
                "SELECT TYPEOF(bt(9)), TO_JSON(bt(9)), bt(9)::VARCHAR");
            assertAnswer(
                "DECIMAL|12345678901234567890.5|12345678901234567890.5",
                "SELECT TYPEOF(bt(10)), TO_JSON(bt(10)), bt(10)::VARCHAR");
            assertAnswer(
                "INTEGER|3|3",
                "SELECT TYPEOF(bt(11)), TO_JSON(bt(11)), bt(11)::VARCHAR");
            assertAnswer(
                "DOUBLE|1.000000000000000e+20|1e+20",
                "SELECT TYPEOF(bt(12)), TO_JSON(bt(12)), bt(12)::VARCHAR");
            assertAnswer(
                "DOUBLE|Infinity|inf",
                "SELECT TYPEOF(bt(13)), TO_JSON(bt(13)), bt(13)::VARCHAR");
            assertAnswer(
                "DOUBLE|7.000000000000000e+00|7",
                "SELECT TYPEOF(bt(14)), TO_JSON(bt(14)), bt(14)::VARCHAR");
            assertAnswer(
                "TIME|\"01:00:00\"|01:00:00",
                "SELECT TYPEOF(bt(16)), TO_JSON(bt(16)), bt(16)::VARCHAR");
            assertAnswer(
                "ARRAY|[0,\"AQ==\",1.5,2]|[0,\"AQ==\",1.5,2]",
                "SELECT TYPEOF(bt(17)), TO_JSON(bt(17)), bt(17)::VARCHAR");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aProceduresVariantArgumentAndResult() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function PV successfully created.",
                "CREATE OR REPLACE PROCEDURE pv(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.types.Variant; class "
                    + "H { public String run(Session s, Variant x) { return x.asMap().get(\"a\").asString() + \"|\" "
                    + "+ x.asJsonString() + \"|\" + x.asMap().size(); } } $$");
            assertAnswer(
                "x|{\"a\":\"x\",\"n\":1}|2",
                "CALL pv(PARSE_JSON('{\"a\":\"x\",\"n\":1}'))");
            assertAnswer(
                "Function PW successfully created.",
                "CREATE OR REPLACE PROCEDURE pw(n NUMBER) RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.types.Variant; class "
                    + "H { public Variant run(Session s, long n) { if (n == 1) return new "
                    + "Variant(java.util.Arrays.<Object>asList(1, \"b\")); if (n == 2) return new Variant(\"7\"); if "
                    + "(n == 3) return new Variant(1.5); if (n == 4) return new Variant(true); if (n == 5) return "
                    + "new Variant(new java.sql.Date(86400000L)); if (n == 6) return new Variant(new byte[] {(byte) "
                    + "255, 1}); if (n == 7) return new Variant(\"s\"); return null; } } $$");
            assertAnswer(
                "[1,\"b\"]",
                "CALL pw(1)");
            assertAnswer(
                "7",
                "CALL pw(2)");
            assertAnswer(
                "1.5",
                "CALL pw(3)");
            assertAnswer(
                "true",
                "CALL pw(4)");
            assertAnswer(
                "1970-01-02",
                "CALL pw(5)");
            assertAnswer(
                "FF01",
                "CALL pw(6)");
            assertAnswer(
                "s",
                "CALL pw(7)");
            assertAnswer(
                "null",
                "CALL pw(8)");
            assertAnswer(
                "Function PK successfully created.",
                "CREATE OR REPLACE PROCEDURE pk(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.types.Variant; class "
                    + "H { public String run(Session s, Variant x) { return x == null ? \"JAVANULL\" : "
                    + "x.asJsonNode().getNodeType() + \"/\" + x.asJsonNode().numberType() + \"|\" + "
                    + "x.asJsonString(); } } $$");
            assertAnswer(
                "NUMBER/BIG_DECIMAL|1",
                "CALL pk(1)");
            assertAnswer(
                "NUMBER/DOUBLE|1.5",
                "CALL pk(1.5::FLOAT)");
            assertAnswer(
                "STRING/null|\"s\"",
                "CALL pk(TO_VARIANT('s'))");
            assertAnswer(
                "NUMBER/INT|12",
                "CALL pk(TO_VARIANT('12'))");
            assertAnswer(
                "BOOLEAN/null|true",
                "CALL pk(TRUE::VARIANT)");
            assertAnswer(
                "STRING/null|\"2024-03-05\"",
                "CALL pk(TO_VARIANT('2024-03-05'::DATE))");
            assertAnswer(
                "NULL/null|null",
                "CALL pk(PARSE_JSON('null'))");
            assertAnswer(
                "JAVANULL",
                "CALL pk(NULL)");
            assertAnswer(
                "BINARY/null|\"ff01\"",
                "CALL pk(TO_VARIANT(TO_BINARY('FF01', 'HEX')))");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
