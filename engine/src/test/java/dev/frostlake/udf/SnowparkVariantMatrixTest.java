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
 * Every constructor of the Snowpark {@code Variant} against every reader, as a Java handler meets them. A
 * value answers by what it was made as: one made from a text - read as JSON by its first value where it is
 * JSON, and itself otherwise - answers every reader; every value answers as a text; a number answers as a
 * whole or a real number and as a timestamp of that many milliseconds; a boolean answers as a number; a date,
 * a time, a timestamp, bytes, a list and a map answer as their own kind alone. A reading that is refused
 * raises {@code Conversion from Variant of Date to FixedNumber is not supported}, and {@code asMap} and
 * {@code asList} over anything else {@code Failed to convert s to Map}. Two values are equal when they hold
 * the same value, whatever either was made as. The session's TIMEZONE is set by the tests and unset whatever
 * the assertions make of the statements between. Every answer is live-verified.
 */
public class SnowparkVariantMatrixTest extends MeasuredCellsTestSupport {

    @Test
    public void everyConstructorAgainstEveryReader() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function MX successfully created.",
                "CREATE OR REPLACE FUNCTION mx(n NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } static String msg(C c) { try { Object o = "
                    + "c.get(); return \"OK:\" + (o == null ? \"NULL\" : String.valueOf(o)); } catch (Throwable e) { "
                    + "return e.getClass().getSimpleName() + \":\" + e.getMessage(); } } static String hex(byte[] b) "
                    + "{ if (b == null) return \"NULL\"; StringBuilder s = new StringBuilder(); for (byte v : b) "
                    + "s.append(String.format(\"%02x\", v)); return s.toString(); } static String kind(Variant v) { "
                    + "return v.asJsonNode().getNodeType() + \"/\" + v.asJsonNode().numberType(); } static Variant "
                    + "make(int n) throws Exception { switch (n) { case 1: return new Variant(1.5f); case 2: return "
                    + "new Variant(1.5); case 3: return new Variant(7L); case 4: return new Variant(1); case 5: "
                    + "return new Variant((short) 3); case 6: return new Variant(true); case 7: return new "
                    + "Variant(false); case 8: return new Variant(new java.math.BigDecimal(\"1.50\")); case 9: "
                    + "return new Variant(java.math.BigInteger.TEN); case 10: return new Variant(\"s\"); case 11: "
                    + "return new Variant(\"7\"); case 12: return new Variant(\"true\"); case 13: return new "
                    + "Variant(\"null\"); case 14: return new Variant(\"{\\\"a\\\":1}\"); case 15: return new "
                    + "Variant(\"[1,2]\"); case 16: return new Variant(new byte[] {(byte) 255, 1}); case 17: return "
                    + "new Variant(new java.sql.Time(3600000L)); case 18: return new Variant(new "
                    + "java.sql.Date(86400000L)); case 19: return new Variant(new java.sql.Timestamp(1500L)); case "
                    + "20: return new Variant(java.util.Arrays.<Object>asList(1, \"b\", null)); case 21: return new "
                    + "Variant(new Object[] {1, \"b\"}); case 22: return new Variant((Object) "
                    + "java.util.Collections.singletonMap(\"k\", 1)); case 23: return new Variant((Object) 5); case "
                    + "24: return new Variant((Object) \"text\"); case 25: return new Variant(0); case 26: return "
                    + "new Variant(0.0); case 27: return new Variant(\"2024-03-05\"); case 28: return new "
                    + "Variant(\"10:11:12\"); case 29: return new Variant(\"2024-03-05 10:11:12.345\"); case 30: "
                    + "return new Variant(\"ff01\"); case 31: return new Variant(\"1.5\"); case 32: return new "
                    + "Variant(1e20); case 33: return new Variant(\"null dummy\"); case 34: return new "
                    + "Variant(\"NULL\"); case 35: return new Variant((Object) null); case 36: return new "
                    + "Variant(java.util.Arrays.<Object>asList(java.util.Collections.singletonMap(\"k\", 1))); case "
                    + "37: return new Variant(\"  7 \"); case 38: return new Variant(\"1e2\"); case 39: return new "
                    + "Variant(\"7 8\"); case 40: return new Variant((String) null); case 41: return new "
                    + "Variant(\"\"); case 42: return new Variant(\"\\\"q\\\"\"); case 43: return new "
                    + "Variant((Object) 1.5); case 44: return new Variant((Object) true); case 45: return new "
                    + "Variant(-1); case 46: return new Variant(2.5); case 47: return new Variant(new "
                    + "java.math.BigDecimal(\"12345678901234567890.5\")); case 48: return new Variant((Object) new "
                    + "int[] {1, 2}); case 49: return new Variant(\"TRUE\"); case 50: return new Variant(\"/w==\"); "
                    + "default: return null; } } public static String f(int n) { Variant v0; try { v0 = make(n); } "
                    + "catch (Throwable e) { return \"NEW-EX:\" + e.getClass().getSimpleName(); } if (v0 == null) "
                    + "return \"NULLV\"; final Variant v = v0; return String.join(\"|\", g(() -> v.asString()), g(() "
                    + "-> v.asJsonString()), g(() -> v.asInt()), g(() -> v.asLong()), g(() -> v.asShort()), g(() -> "
                    + "v.asFloat()), g(() -> v.asDouble()), g(() -> v.asBigDecimal()), g(() -> v.asBigInteger()), "
                    + "g(() -> v.asBoolean()), g(() -> hex(v.asBinary())), g(() -> v.asDate().getTime()), g(() -> "
                    + "v.asTime().getTime()), g(() -> v.asTimestamp().getTime()), g(() -> v.asArray().length), g(() "
                    + "-> v.asList().size()), g(() -> v.asMap().size()), g(() -> kind(v)), g(() -> v.toString())); } "
                    + "} $$");
            assertAnswer(
                "1.5|1.5|1|1|1|1.5|1.5|1.5|1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/FLOAT|1.5",
                "SELECT mx(1)");
            assertAnswer(
                "1.5|1.5|1|1|1|1.5|1.5|1.5|1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/DOUBLE|1.5",
                "SELECT mx(2)");
            assertAnswer(
                "7|7|7|7|7|7.0|7.0|7|7|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|7|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/LONG|7",
                "SELECT mx(3)");
            assertAnswer(
                "1|1|1|1|1|1.0|1.0|1|1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/INT|1",
                "SELECT mx(4)");
            assertAnswer(
                "3|3|3|3|3|3.0|3.0|3|3|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|3|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/INT|3",
                "SELECT mx(5)");
            assertAnswer(
                "true|true|1|1|1|1.0|1.0|1|1|true|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|BOOLEAN/null|true",
                "SELECT mx(6)");
            assertAnswer(
                "false|false|0|0|0|0.0|0.0|0|0|false|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|BOOLEAN/null|false",
                "SELECT mx(7)");
            assertAnswer(
                "1.50|1.50|1|1|1|1.5|1.5|1.50|1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/BIG_DECIMAL|1.50",
                "SELECT mx(8)");
            assertAnswer(
                "10|10|10|10|10|10.0|10.0|10|10|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|10|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/BIG_INTEGER|10",
                "SELECT mx(9)");
            assertAnswer(
                "s|\"s\"|0|0|0|0.0|0.0|0|0|false|EX:UncheckedIOException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|s",
                "SELECT mx(10)");
            assertAnswer(
                "7|7|7|7|7|7.0|7.0|7|7|true|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|7|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/INT|7",
                "SELECT mx(11)");
            assertAnswer(
                "true|true|1|1|1|1.0|1.0|1|1|true|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|BOOLEAN/null|true",
                "SELECT mx(12)");
            assertAnswer(
                "null|null|0|0|0|0.0|0.0|0|0|false|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NULL/null|null",
                "SELECT mx(13)");
            assertAnswer(
                "{\"a\":1}|{\"a\":1}|0|0|0|0.0|0.0|0|0|false|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|1|OBJECT/null|{\"a\":1}",
                "SELECT mx(14)");
            assertAnswer(
                "[1,2]|[1,"
                    + "2]|0|0|0|0.0|0.0|0|0|false|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|2|2|EX:UncheckedIOException|ARRAY/null|[1,2]",
                "SELECT mx(15)");
            assertAnswer(
                "ff01|\"ff01\"|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|ff01|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|BINARY/null|ff01",
                "SELECT mx(16)");
            assertAnswer(
                "01:00:00|\"01:00:00\"|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|3600000|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|01:00:00",
                "SELECT mx(17)");
            assertAnswer(
                "1970-01-02|\"1970-01-02\"|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|86400000|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|1970-01-02",
                "SELECT mx(18)");
            answer(
                "SELECT mx(19)");
            assertAnswer(
                "[1,\"b\",null]|[1,\"b\","
                    + "null]|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|3|3|EX:UncheckedIOException|ARRAY/null|[1,\"b\",null]",
                "SELECT mx(20)");
            assertAnswer(
                "[1,\"b\"]|[1,"
                    + "\"b\"]|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|2|2|EX:UncheckedIOException|ARRAY/null|[1,\"b\"]",
                "SELECT mx(21)");
            assertAnswer(
                "{\"k\":1}|{\"k\":1}|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|1|OBJECT/null|{\"k\":1}",
                "SELECT mx(22)");
            assertAnswer(
                "5|5|5|5|5|5.0|5.0|5|5|true|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|5|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/INT|5",
                "SELECT mx(23)");
            assertAnswer(
                "text|\"text\"|0|0|0|0.0|0.0|0|0|false|b5ec6d|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|text",
                "SELECT mx(24)");
            assertAnswer(
                "0|0|0|0|0|0.0|0.0|0|0|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|0|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/INT|0",
                "SELECT mx(25)");
            assertAnswer(
                "0.0|0.0|0|0|0|0.0|0.0|0.0|0|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|0|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/DOUBLE|0.0",
                "SELECT mx(26)");
            assertAnswer(
                "2024-03-05|\"2024-03-05\"|0|0|0|0.0|0.0|0|0|false|EX:UncheckedIOException|1709596800000|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|2024-03-05",
                "SELECT mx(27)");
            assertAnswer(
                "10:11:12|\"10:11:12\"|0|0|0|0.0|0.0|0|0|false|EX:UncheckedIOException|EX:IllegalArgumentException|36672000|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|10:11:12",
                "SELECT mx(28)");
            answer(
                "SELECT mx(29)");
            assertAnswer(
                "ff01|\"ff01\"|0|0|0|0.0|0.0|0|0|false|7dfd35|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|ff01",
                "SELECT mx(30)");
            assertAnswer(
                "1.5|1.5|1|1|1|1.5|1.5|1.5|1|false|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/DOUBLE|1.5",
                "SELECT mx(31)");
            assertShapedAnswer(
                "1.0E20|1.0E20|2147483647|<ID19>|-1|1.0E20|1.0E20|1.0E+20|100000000000000000000|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|<ID19>|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/DOUBLE|1.0E20",
                "SELECT mx(32)");
            assertAnswer(
                "null|null|0|0|0|0.0|0.0|0|0|false|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NULL/null|null",
                "SELECT mx(33)");
            assertAnswer(
                "NULL|\"NULL\"|0|0|0|0.0|0.0|0|0|false|3542cb|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|NULL",
                "SELECT mx(34)");
            assertAnswer(
                "null|null|0|0|0|0.0|0.0|0|0|false|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NULL/null|null",
                "SELECT mx(35)");
            assertAnswer(
                "[{\"k\":1}]|[{\"k\":1}]|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|1|1|EX:UncheckedIOException|ARRAY/null|[{\"k\":1}]",
                "SELECT mx(36)");
            assertAnswer(
                "7|7|7|7|7|7.0|7.0|7|7|true|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|7|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/INT|7",
                "SELECT mx(37)");
            assertAnswer(
                "100.0|100.0|100|100|100|100.0|100.0|100.0|100|false|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|100|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/DOUBLE|100.0",
                "SELECT mx(38)");
            assertAnswer(
                "7|7|7|7|7|7.0|7.0|7|7|true|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|7|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/INT|7",
                "SELECT mx(39)");
            assertAnswer(
                "EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException|EX:NullPointerException",
                "SELECT mx(40)");
            assertAnswer(
                "||0|0|0|0.0|0.0|0|0|false|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|MISSING/null|",
                "SELECT mx(41)");
            assertAnswer(
                "q|\"q\"|0|0|0|0.0|0.0|0|0|false|EX:UncheckedIOException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|q",
                "SELECT mx(42)");
            assertAnswer(
                "1.5|1.5|1|1|1|1.5|1.5|1.5|1|false|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/DOUBLE|1.5",
                "SELECT mx(43)");
            assertAnswer(
                "true|true|1|1|1|1.0|1.0|1|1|true|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|BOOLEAN/null|true",
                "SELECT mx(44)");
            assertAnswer(
                "-1|-1|-1|-1|-1|-1.0|-1.0|-1|-1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|-1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/INT|-1",
                "SELECT mx(45)");
            assertAnswer(
                "2.5|2.5|2|2|2|2.5|2.5|2.5|2|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|2|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/DOUBLE|2.5",
                "SELECT mx(46)");
            assertShapedAnswer(
                "12345678901234567890.5|12345678901234567890.5|-350287150|-<ID19>|2770|1.2345679E19|1.2345678901234567E19|12345678901234567890.5|12345678901234567890|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|-<ID19>|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/BIG_DECIMAL|12345678901234567890.5",
                "SELECT mx(47)");
            assertAnswer(
                "[1,2]|[1,"
                    + "2]|0|0|0|0.0|0.0|0|0|false|NULL|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|2|2|EX:UncheckedIOException|ARRAY/null|[1,2]",
                "SELECT mx(48)");
            assertAnswer(
                "TRUE|\"TRUE\"|0|0|0|0.0|0.0|0|0|false|4d1504|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|TRUE",
                "SELECT mx(49)");
            assertAnswer(
                "/w==|\"/w==\"|0|0|0|0.0|0.0|0|0|false|ff|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null|/w==",
                "SELECT mx(50)");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aRefusedReadingNamesBothKinds() {
        assertAnswer(
            "Function MM successfully created.",
            "CREATE OR REPLACE FUNCTION mm(n NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; class H "
                + "{ interface C { Object get() throws Exception; } static String g(C c) { try { Object o = c.get(); "
                + "return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return \"EX:\" + "
                + "e.getClass().getSimpleName(); } } static String msg(C c) { try { Object o = c.get(); return "
                + "\"OK:\" + (o == null ? \"NULL\" : String.valueOf(o)); } catch (Throwable e) { return "
                + "e.getClass().getSimpleName() + \":\" + e.getMessage(); } } static String hex(byte[] b) { if (b == "
                + "null) return \"NULL\"; StringBuilder s = new StringBuilder(); for (byte v : b) "
                + "s.append(String.format(\"%02x\", v)); return s.toString(); } public static String f(int n) { "
                + "switch (n) { case 1: return msg(() -> new Variant(1.5).asBoolean()); case 2: return msg(() -> new "
                + "Variant(true).asDate()); case 3: return msg(() -> new Variant(\"s\").asMap()); case 4: return "
                + "msg(() -> new Variant(\"s\").asList()); case 5: return msg(() -> new Variant(new "
                + "java.sql.Date(0L)).asInt()); case 6: return msg(() -> new Variant(new byte[] {1}).asInt()); case "
                + "7: return msg(() -> hex(new Variant(\"s\").asBinary())); case 8: return msg(() -> new "
                + "Variant((Object) new Variant(1))); case 9: return msg(() -> new Variant(\"s\").asDate()); case "
                + "10: return msg(() -> new Variant(1).asMap()); case 11: return msg(() -> new "
                + "Variant(java.util.Arrays.<Object>asList(1)).asMap()); case 12: return msg(() -> new "
                + "Variant((Object) java.util.Collections.singletonMap(\"k\", 1)).asList()); case 13: return msg(() "
                + "-> new Variant(1).asTime()); case 14: return msg(() -> new Variant(new "
                + "java.sql.Time(0L)).asTimestamp()); case 15: return msg(() -> new Variant(new "
                + "java.sql.Timestamp(0L)).asDate()); case 16: return msg(() -> new Variant(1.5).asBinary()); case "
                + "17: return msg(() -> new Variant(true).asArray()); case 18: return msg(() -> new Variant((String) "
                + "null)); case 19: return msg(() -> new Variant((Object) null)); case 20: return msg(() -> new "
                + "Variant(new byte[] {1}).asBoolean()); case 21: return msg(() -> new "
                + "Variant(java.math.BigInteger.TEN).asBoolean()); case 22: return msg(() -> new "
                + "Variant(7L).asBoolean()); case 23: return msg(() -> new Variant(new "
                + "java.sql.Timestamp(0L)).asTime()); case 24: return msg(() -> new Variant(new "
                + "java.sql.Date(0L)).asTimestamp()); case 25: return msg(() -> new Variant(true).asTimestamp()); "
                + "default: return null; } } } $$");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of RealNumber to Boolean is not "
                + "supported",
            "SELECT mm(1)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of Boolean to Date is not supported",
            "SELECT mm(2)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Failed to convert s to Map",
            "SELECT mm(3)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Failed to convert s to Array",
            "SELECT mm(4)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to FixedNumber is not "
                + "supported",
            "SELECT mm(5)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of Binary to FixedNumber is not "
                + "supported",
            "SELECT mm(6)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Failed to convert s to Binary. Only Hex string is "
                + "supported.",
            "SELECT mm(7)");
        assertAnswer(
            "IllegalArgumentException:No serializer found for class com.snowflake.snowpark_java.types.Variant and "
                + "no properties discovered to create BeanSerializer (to avoid exception, disable "
                + "SerializationFeature.FAIL_ON_EMPTY_BEANS)",
            "SELECT mm(8)");
        assertAnswer(
            "IllegalArgumentException:null",
            "SELECT mm(9)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Failed to convert 1 to Map",
            "SELECT mm(10)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Failed to convert  to Map",
            "SELECT mm(11)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Failed to convert  to Array",
            "SELECT mm(12)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to Time is not "
                + "supported",
            "SELECT mm(13)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of Time to Timestamp is not "
                + "supported",
            "SELECT mm(14)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of Timestamp to Date is not "
                + "supported",
            "SELECT mm(15)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of RealNumber to Binary is not "
                + "supported",
            "SELECT mm(16)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Failed to convert true to Array",
            "SELECT mm(17)");
        assertAnswer(
            "NullPointerException:null",
            "SELECT mm(18)");
        assertAnswer(
            "OK:null",
            "SELECT mm(19)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of Binary to Boolean is not "
                + "supported",
            "SELECT mm(20)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to Boolean is not "
                + "supported",
            "SELECT mm(21)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to Boolean is not "
                + "supported",
            "SELECT mm(22)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of Timestamp to Time is not "
                + "supported",
            "SELECT mm(23)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to Timestamp is not "
                + "supported",
            "SELECT mm(24)");
        assertAnswer(
            "UncheckedIOException:java.io.IOException: Conversion from Variant of Boolean to Timestamp is not "
                + "supported",
            "SELECT mm(25)");
    }

    @Test
    public void valuesAreEqualByWhatTheyHold() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function MQ successfully created.",
                "CREATE OR REPLACE FUNCTION mq() RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } static String msg(C c) { try { Object o = "
                    + "c.get(); return \"OK:\" + (o == null ? \"NULL\" : String.valueOf(o)); } catch (Throwable e) { "
                    + "return e.getClass().getSimpleName() + \":\" + e.getMessage(); } } static String hex(byte[] b) "
                    + "{ if (b == null) return \"NULL\"; StringBuilder s = new StringBuilder(); for (byte v : b) "
                    + "s.append(String.format(\"%02x\", v)); return s.toString(); } static String kind(Variant v) { "
                    + "return v.asJsonNode().getNodeType() + \"/\" + v.asJsonNode().numberType(); } public static "
                    + "String f() { return String.join(\"|\", g(() -> new Variant(1).equals(new Variant(1))), g(() "
                    + "-> new Variant(1).equals(new Variant(1L))), g(() -> new Variant(1).equals(new "
                    + "Variant(\"1\"))), g(() -> new Variant(1).equals(new Variant(1.0))), g(() -> new "
                    + "Variant(\"a\").equals(new Variant(\"a\"))), g(() -> new Variant(\"a\").equals(\"a\")), g(() "
                    + "-> new Variant(1).equals(null)), g(() -> new Variant(1).hashCode() == new "
                    + "Variant(1).hashCode()), g(() -> new Variant(\"{\\\"a\\\":1}\").equals(new Variant(\"{ "
                    + "\\\"a\\\" : 1 }\"))), g(() -> new Variant(1).equals(new Variant(true))), g(() -> new "
                    + "Variant(\"1\").equals(new Variant(\"1\"))), g(() -> new Variant(new "
                    + "java.sql.Date(0L)).equals(new Variant(\"1970-01-01\"))), g(() -> new Variant(1.5).equals(new "
                    + "Variant(1.5f))), g(() -> new Variant(new byte[] {1}).equals(new Variant(new byte[] {1}))), "
                    + "g(() -> new Variant(java.util.Arrays.<Object>asList(1)).equals(new Variant(\"[1]\"))), g(() "
                    + "-> new Variant(java.util.Arrays.<Object>asList(1)).equals(new Variant(new Object[] {1}))), "
                    + "g(() -> new Variant(1).equals(new Variant(new java.math.BigDecimal(\"1\")))), g(() -> new "
                    + "Variant(new java.math.BigDecimal(\"1.0\")).equals(new Variant(new "
                    + "java.math.BigDecimal(\"1.00\"))))); } } $$");
            assertAnswer(
                "true|false|true|false|true|false|false|true|true|false|true|true|false|true|true|true|false|true",
                "SELECT mq()");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
