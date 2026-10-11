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
 * The zone a {@code Variant}'s dates, times and timestamps are read in. A handler runs in the session's time
 * zone: a {@code java.sql} value is held as its instant's wall clock there - a DATE argument, an instant at
 * UTC midnight, is the day before in a zone behind UTC, and comes back as that day - and a text is read back
 * as a wall clock of that zone. A TIME is held to the second; a timestamp's text drops its fraction's trailing
 * zeros down to one digit; a timestamp handed back is a TIMESTAMP_NTZ at its instant's UTC wall clock. The
 * session's TIMEZONE is set by the tests and unset whatever the assertions make of the statements between.
 * Every answer is live-verified.
 */
public class SnowparkVariantZoneTest extends MeasuredCellsTestSupport {

    @Test
    public void aTemporalValueIsItsTextInTheSessionsZone() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function TM successfully created.",
                "CREATE OR REPLACE FUNCTION tm(n NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } static String msg(C c) { try { Object o = "
                    + "c.get(); return \"OK:\" + (o == null ? \"NULL\" : String.valueOf(o)); } catch (Throwable e) { "
                    + "return e.getClass().getSimpleName() + \":\" + e.getMessage(); } } static String hex(byte[] b) "
                    + "{ if (b == null) return \"NULL\"; StringBuilder s = new StringBuilder(); for (byte v : b) "
                    + "s.append(String.format(\"%02x\", v)); return s.toString(); } static String kind(Variant v) { "
                    + "return v.asJsonNode().getNodeType() + \"/\" + v.asJsonNode().numberType(); } public static "
                    + "String f(int n) { switch (n) { case 1: return msg(() -> new Variant(new "
                    + "java.sql.Date(0L)).asShort()); case 2: return msg(() -> new Variant(new "
                    + "java.sql.Date(0L)).asLong()); case 3: return msg(() -> new Variant(new "
                    + "java.sql.Date(0L)).asBigInteger()); case 4: return msg(() -> new Variant(new "
                    + "java.sql.Date(0L)).asFloat()); case 5: return msg(() -> new Variant(new "
                    + "java.sql.Date(0L)).asDouble()); case 6: return msg(() -> new Variant(new "
                    + "java.sql.Date(0L)).asBigDecimal()); case 7: return msg(() -> new Variant(new "
                    + "java.sql.Date(0L)).asBinary()); case 8: return msg(() -> new Variant(new "
                    + "java.sql.Date(0L)).asTime()); case 9: return msg(() -> new Variant(new "
                    + "java.sql.Date(0L)).asBoolean()); case 10: return msg(() -> new Variant(new "
                    + "java.math.BigDecimal(\"1.5\")).asBoolean()); case 11: return msg(() -> new "
                    + "Variant(1.5f).asBoolean()); case 12: return msg(() -> new "
                    + "Variant(java.util.Arrays.<Object>asList(1)).asInt()); case 13: return msg(() -> new "
                    + "Variant((Object) java.util.Collections.singletonMap(\"k\", 1)).asInt()); case 14: return "
                    + "msg(() -> new Variant(new java.sql.Time(0L)).asInt()); case 15: return msg(() -> new "
                    + "Variant(new java.sql.Timestamp(0L)).asInt()); case 16: return msg(() -> new Variant((short) "
                    + "1).asBoolean()); case 17: return msg(() -> hex(new Variant(\"ff\").asBinary())); case 18: "
                    + "return msg(() -> hex(new Variant(\"ff01ab\").asBinary())); case 19: return msg(() -> hex(new "
                    + "Variant(\"zz\").asBinary())); case 20: return msg(() -> hex(new Variant(\"FF\").asBinary())); "
                    + "case 21: return msg(() -> new Variant(new java.sql.Timestamp(1500L)).asString().replace('-', "
                    + "'/')); case 22: return msg(() -> new Variant((Object) java.util.Collections.singletonMap(1, "
                    + "2)).asJsonString()); case 23: return msg(() -> new Variant((Object) "
                    + "java.util.Arrays.<Object>asList(1, 2)).asInt()); case 24: return msg(() -> new Variant(new "
                    + "java.util.ArrayList<Object>()).asJsonString()); case 25: return msg(() -> new "
                    + "Variant((Object) java.util.Collections.singletonMap(\"k\", new Variant(1))).asJsonString()); "
                    + "case 26: return msg(() -> new Variant(java.util.Arrays.<Object>asList(new java.sql.Date(0L), "
                    + "new byte[] {1}, 1.5f, 2L, new java.math.BigDecimal(\"1.50\"))).asJsonString()); case 27: "
                    + "return msg(() -> new Variant(new java.sql.Timestamp(1500L)).asTimestamp().getNanos()); case "
                    + "28: return msg(() -> new Variant(new java.sql.Time(3600123L)).asString()); case 29: return "
                    + "msg(() -> new Variant(1.5f).asJsonString()); case 30: return msg(() -> new "
                    + "Variant(0.1f).asDouble()); case 31: return msg(() -> new Variant(0.1f).asString()); case 32: "
                    + "return msg(() -> new Variant(1e20).asLong()); case 33: return msg(() -> new "
                    + "Variant(Double.NaN).asJsonString()); case 34: return msg(() -> new "
                    + "Variant(Double.NaN).asString()); case 35: return msg(() -> new Variant(Double.NaN).asInt()); "
                    + "case 36: return msg(() -> new Variant(\"[1,2]\").asList().get(0).asInt()); case 37: return "
                    + "msg(() -> new Variant(\"{\\\"a\\\":[1]}\").asMap().get(\"a\").asList().size()); case 38: "
                    + "return msg(() -> new Variant(java.util.Arrays.<Object>asList(1, null)).asList().get(1)); case "
                    + "39: return msg(() -> new Variant(new java.math.BigDecimal(\"1E+3\")).asString()); case 40: "
                    + "return msg(() -> new Variant(new java.math.BigDecimal(\"1E+3\")).asJsonString()); case 41: "
                    + "return msg(() -> new Variant(100).asMap()); case 42: return msg(() -> new Variant(new "
                    + "java.sql.Date(0L)).asString().replace('-', '/')); default: return null; } } } $$");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to FixedNumber is not "
                    + "supported",
                "SELECT tm(1)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to FixedNumber is not "
                    + "supported",
                "SELECT tm(2)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to FixedNumber is not "
                    + "supported",
                "SELECT tm(3)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to RealNumber is not "
                    + "supported",
                "SELECT tm(4)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to RealNumber is not "
                    + "supported",
                "SELECT tm(5)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to RealNumber is not "
                    + "supported",
                "SELECT tm(6)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to Binary is not "
                    + "supported",
                "SELECT tm(7)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to Time is not "
                    + "supported",
                "SELECT tm(8)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Date to Boolean is not "
                    + "supported",
                "SELECT tm(9)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to Boolean is "
                    + "not supported",
                "SELECT tm(10)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of RealNumber to Boolean is "
                    + "not supported",
                "SELECT tm(11)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Array to FixedNumber is not "
                    + "supported",
                "SELECT tm(12)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Object to FixedNumber is "
                    + "not supported",
                "SELECT tm(13)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Time to FixedNumber is not "
                    + "supported",
                "SELECT tm(14)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of Timestamp to FixedNumber is "
                    + "not supported",
                "SELECT tm(15)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to Boolean is "
                    + "not supported",
                "SELECT tm(16)");
            assertAnswer(
                "OK:ff",
                "SELECT tm(17)");
            assertAnswer(
                "OK:ff01ab",
                "SELECT tm(18)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Failed to convert zz to Binary. Only Hex string is "
                    + "supported.",
                "SELECT tm(19)");
            assertAnswer(
                "OK:ff",
                "SELECT tm(20)");
            assertAnswer(
                "OK:1970/01/01 00:00:01.5",
                "SELECT tm(21)");
            assertAnswer(
                "OK:{\"1\":2}",
                "SELECT tm(22)");
            assertAnswer(
                "OK:0",
                "SELECT tm(23)");
            assertAnswer(
                "OK:[]",
                "SELECT tm(24)");
            assertAnswer(
                "OK:{\"k\":1}",
                "SELECT tm(25)");
            assertAnswer(
                "OK:[0,\"AQ==\",1.5,2,1.5]",
                "SELECT tm(26)");
            assertAnswer(
                "OK:500000000",
                "SELECT tm(27)");
            assertAnswer(
                "OK:01:00:00",
                "SELECT tm(28)");
            assertAnswer(
                "OK:1.5",
                "SELECT tm(29)");
            assertAnswer(
                "OK:0.10000000149011612",
                "SELECT tm(30)");
            assertAnswer(
                "OK:0.1",
                "SELECT tm(31)");
            assertShapedAnswer(
                "OK:<ID19>",
                "SELECT tm(32)");
            assertAnswer(
                "OK:\"NaN\"",
                "SELECT tm(33)");
            assertAnswer(
                "OK:NaN",
                "SELECT tm(34)");
            assertAnswer(
                "OK:0",
                "SELECT tm(35)");
            assertAnswer(
                "OK:1",
                "SELECT tm(36)");
            assertAnswer(
                "OK:1",
                "SELECT tm(37)");
            assertAnswer(
                "OK:null",
                "SELECT tm(38)");
            assertAnswer(
                "OK:1E+3",
                "SELECT tm(39)");
            assertAnswer(
                "OK:1E+3",
                "SELECT tm(40)");
            assertAnswer(
                "UncheckedIOException:java.io.IOException: Failed to convert 100 to Map",
                "SELECT tm(41)");
            assertAnswer(
                "OK:1970/01/01",
                "SELECT tm(42)");
            assertAnswer(
                "Function TX successfully created.",
                "CREATE OR REPLACE FUNCTION tx(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES "
                    + "= ('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } static String msg(C c) { try { Object o = "
                    + "c.get(); return \"OK:\" + (o == null ? \"NULL\" : String.valueOf(o)); } catch (Throwable e) { "
                    + "return e.getClass().getSimpleName() + \":\" + e.getMessage(); } } static String hex(byte[] b) "
                    + "{ if (b == null) return \"NULL\"; StringBuilder s = new StringBuilder(); for (byte v : b) "
                    + "s.append(String.format(\"%02x\", v)); return s.toString(); } static String kind(Variant v) { "
                    + "return v.asJsonNode().getNodeType() + \"/\" + v.asJsonNode().numberType(); } public static "
                    + "String f(Variant x) { return String.join(\"|\", g(() -> kind(x)), g(() -> "
                    + "x.asString().replace('-', '/').replace(':', ';')), g(() -> x.asJsonString().replace('-', "
                    + "'/').replace(':', ';')), msg(() -> x.asTimestamp().getTime()), msg(() -> x.asList().size())); "
                    + "} } $$");
            answer(
                "SELECT tx(TO_VARIANT('2024-03-05 10:11:12.123456789'::TIMESTAMP_NTZ))");
            answer(
                "SELECT tx(TO_VARIANT('2024-03-05 10:11:12'::TIMESTAMP_NTZ(0)))");
            answer(
                "SELECT tx(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))");
            answer(
                "SELECT tx(TO_VARIANT('2024-03-05 10:11:12.345 +02:00'::TIMESTAMP_TZ))");
            answer(
                "SELECT tx(TO_VARIANT('2024-03-05 10:11:12.100'::TIMESTAMP_NTZ))");
            assertAnswer(
                "STRING/null|10;11;12|\"10;11;12\"|UncheckedIOException:java.io.IOException: Conversion from "
                    + "Variant of Time to Timestamp is not supported|UncheckedIOException:java.io.IOException: "
                    + "Failed to convert 10:11:12 to Array",
                "SELECT tx(TO_VARIANT('10:11:12.5'::TIME))");
            assertAnswer(
                "STRING/null|2024/03/05|\"2024/03/05\"|UncheckedIOException:java.io.IOException: Conversion from "
                    + "Variant of Date to Timestamp is not supported|UncheckedIOException:java.io.IOException: "
                    + "Failed to convert 2024-03-05 to Array",
                "SELECT tx(TO_VARIANT('2024-03-05'::DATE))");
            assertAnswer(
                "STRING/null|[1,undefined,\"x\"]|\"[1,undefined,\\\"x\\\"]\"|IllegalArgumentException:Timestamp "
                    + "format must be yyyy-mm-dd hh:mm:ss[.fffffffff]|UncheckedIOException:java.io.IOException: "
                    + "Failed to convert [1,undefined,\"x\"] to Array",
                "SELECT tx(ARRAY_CONSTRUCT(1, NULL, 'x'))");
            assertAnswer(
                "STRING/null|{\"a\";[1,undefined]}|\"{\\\"a\\\";[1,"
                    + "undefined]}\"|IllegalArgumentException:Timestamp format must be yyyy-mm-dd "
                    + "hh:mm:ss[.fffffffff]|UncheckedIOException:java.io.IOException: Failed to convert {\"a\":[1,"
                    + "undefined]} to Array",
                "SELECT tx(OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(1, NULL)))");
            assertAnswer(
                "NUMBER/INT|7|7|OK:7|UncheckedIOException:java.io.IOException: Failed to convert 7 to Array",
                "SELECT tx(TO_VARIANT('7 8'))");
            assertAnswer(
                "NUMBER/DOUBLE|100.0|100.0|OK:100|UncheckedIOException:java.io.IOException: Failed to convert "
                    + "100.0 to Array",
                "SELECT tx(TO_VARIANT('1e2'))");
            assertAnswer(
                "OBJECT/null|{\"i\";\"10;11;12\",\"t\";\"2024/03/05 10;11;12.345\"}|{\"i\";\"10;11;12\","
                    + "\"t\";\"2024/03/05 10;11;12.345\"}|IllegalArgumentException:Timestamp format must be "
                    + "yyyy-mm-dd hh:mm:ss[.fffffffff]|UncheckedIOException:java.io.IOException: Failed to convert  "
                    + "to Array",
                "SELECT tx(OBJECT_CONSTRUCT('t', '2024-03-05 10:11:12.345'::TIMESTAMP_NTZ, 'i', '10:11:12'::TIME))");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            answer(
                "SELECT tx(TO_VARIANT('2024-03-05 10:11:12.123456789'::TIMESTAMP_NTZ))");
            answer(
                "SELECT tx(TO_VARIANT('2024-03-05 10:11:12'::TIMESTAMP_NTZ(0)))");
            answer(
                "SELECT tx(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))");
            answer(
                "SELECT tx(TO_VARIANT('2024-03-05 10:11:12.345 +02:00'::TIMESTAMP_TZ))");
            answer(
                "SELECT tx(TO_VARIANT('2024-03-05 10:11:12.100'::TIMESTAMP_NTZ))");
            assertAnswer(
                "STRING/null|02;11;12|\"02;11;12\"|UncheckedIOException:java.io.IOException: Conversion from "
                    + "Variant of Time to Timestamp is not supported|UncheckedIOException:java.io.IOException: "
                    + "Failed to convert 02:11:12 to Array",
                "SELECT tx(TO_VARIANT('10:11:12.5'::TIME))");
            assertAnswer(
                "STRING/null|2024/03/04|\"2024/03/04\"|UncheckedIOException:java.io.IOException: Conversion from "
                    + "Variant of Date to Timestamp is not supported|UncheckedIOException:java.io.IOException: "
                    + "Failed to convert 2024-03-04 to Array",
                "SELECT tx(TO_VARIANT('2024-03-05'::DATE))");
            assertAnswer(
                "OK:1969/12/31 16:00:01.5",
                "SELECT tm(21)");
            assertAnswer(
                "OK:17:00:00",
                "SELECT tm(28)");
            assertAnswer(
                "OK:1969/12/31",
                "SELECT tm(42)");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aTemporalValueComesBackThroughTheSessionsZone() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
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
                    + "s.append(String.format(\"%02x\", v)); return s.toString(); } static String kind(Variant v) { "
                    + "return v.asJsonNode().getNodeType() + \"/\" + v.asJsonNode().numberType(); } public static "
                    + "Variant f(Variant x) { return x; } } $$");
            assertAnswer(
                "Function BZ successfully created.",
                "CREATE OR REPLACE FUNCTION bz(n NUMBER) RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } static String msg(C c) { try { Object o = "
                    + "c.get(); return \"OK:\" + (o == null ? \"NULL\" : String.valueOf(o)); } catch (Throwable e) { "
                    + "return e.getClass().getSimpleName() + \":\" + e.getMessage(); } } static String hex(byte[] b) "
                    + "{ if (b == null) return \"NULL\"; StringBuilder s = new StringBuilder(); for (byte v : b) "
                    + "s.append(String.format(\"%02x\", v)); return s.toString(); } static String kind(Variant v) { "
                    + "return v.asJsonNode().getNodeType() + \"/\" + v.asJsonNode().numberType(); } public static "
                    + "Variant f(int n) { switch (n) { case 1: return new Variant(new java.sql.Timestamp(1500L)); "
                    + "case 2: return new Variant(new java.sql.Date(86400000L)); case 3: return new Variant(new "
                    + "java.sql.Time(3600000L)); case 4: return new Variant(\"2024-03-05\"); case 5: return new "
                    + "Variant(\"10:11:12\"); default: return null; } } } $$");
            assertAnswer(
                "TIMESTAMP_NTZ|\"1970/01/01 00;00;01.500\"",
                "SELECT TYPEOF(bz(1)), REPLACE(REPLACE(TO_JSON(bz(1)), '-', '/'), ':', ';')");
            assertAnswer(
                "DATE|\"1970/01/01\"",
                "SELECT TYPEOF(bz(2)), REPLACE(REPLACE(TO_JSON(bz(2)), '-', '/'), ':', ';')");
            assertAnswer(
                "TIME|\"01;00;00\"",
                "SELECT TYPEOF(bz(3)), REPLACE(REPLACE(TO_JSON(bz(3)), '-', '/'), ':', ';')");
            assertAnswer(
                "DATE|\"2024/03/04\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05'::DATE))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('2024-03-05'::DATE))), '-', '/'), ':', ';')");
            assertAnswer(
                "TIMESTAMP_NTZ|\"2024/03/05 10;11;12.345\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ))), '-', '/'), "
                    + "':', ';')");
            assertAnswer(
                "TIMESTAMP_NTZ|\"2024/03/05 18;11;12.345\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))), '-', '/'), "
                    + "':', ';')");
            assertAnswer(
                "TIMESTAMP_NTZ|\"2024/03/05 08;11;12.345\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.345 +02:00'::TIMESTAMP_TZ))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.345 +02:00'::TIMESTAMP_TZ))), '-', "
                    + "'/'), ':', ';')");
            assertAnswer(
                "TIME|\"10;11;12\"",
                "SELECT TYPEOF(rv(TO_VARIANT('10:11:12.5'::TIME))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('10:11:12.5'::TIME))), '-', '/'), ':', ';')");
            assertAnswer(
                "TIMESTAMP_NTZ|\"2024/03/05 10;11;12.123\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.123456789'::TIMESTAMP_NTZ))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.123456789'::TIMESTAMP_NTZ))), '-', "
                    + "'/'), ':', ';')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "TIMESTAMP_NTZ|\"1970/01/01 00;00;01.500\"",
                "SELECT TYPEOF(bz(1)), REPLACE(REPLACE(TO_JSON(bz(1)), '-', '/'), ':', ';')");
            assertAnswer(
                "DATE|\"1970/01/02\"",
                "SELECT TYPEOF(bz(2)), REPLACE(REPLACE(TO_JSON(bz(2)), '-', '/'), ':', ';')");
            assertAnswer(
                "TIME|\"01;00;00\"",
                "SELECT TYPEOF(bz(3)), REPLACE(REPLACE(TO_JSON(bz(3)), '-', '/'), ':', ';')");
            assertAnswer(
                "DATE|\"2024/03/05\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05'::DATE))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('2024-03-05'::DATE))), '-', '/'), ':', ';')");
            assertAnswer(
                "TIMESTAMP_NTZ|\"2024/03/05 10;11;12.345\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ))), '-', '/'), "
                    + "':', ';')");
            assertAnswer(
                "TIMESTAMP_NTZ|\"2024/03/05 10;11;12.345\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))), '-', '/'), "
                    + "':', ';')");
            assertAnswer(
                "TIMESTAMP_NTZ|\"2024/03/05 08;11;12.345\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.345 +02:00'::TIMESTAMP_TZ))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.345 +02:00'::TIMESTAMP_TZ))), '-', "
                    + "'/'), ':', ';')");
            assertAnswer(
                "TIME|\"10;11;12\"",
                "SELECT TYPEOF(rv(TO_VARIANT('10:11:12.5'::TIME))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('10:11:12.5'::TIME))), '-', '/'), ':', ';')");
            assertAnswer(
                "TIMESTAMP_NTZ|\"2024/03/05 10;11;12.123\"",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.123456789'::TIMESTAMP_NTZ))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('2024-03-05 10:11:12.123456789'::TIMESTAMP_NTZ))), '-', "
                    + "'/'), ':', ';')");
            assertAnswer(
                "VARCHAR|\"[1,undefined,\\\"x\\\"]\"",
                "SELECT TYPEOF(rv(ARRAY_CONSTRUCT(1, NULL, 'x'))), REPLACE(REPLACE(TO_JSON(rv(ARRAY_CONSTRUCT(1, "
                    + "NULL, 'x'))), '-', '/'), ':', ';')");
            assertAnswer(
                "VARCHAR|\"{\\\"a\\\";[1,undefined]}\"",
                "SELECT TYPEOF(rv(OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(1, NULL)))), "
                    + "REPLACE(REPLACE(TO_JSON(rv(OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(1, NULL)))), '-', '/'), ':', "
                    + "';')");
            assertAnswer(
                "VARCHAR|\"2024/03/05\"",
                "SELECT TYPEOF(bz(4)), REPLACE(REPLACE(TO_JSON(bz(4)), '-', '/'), ':', ';')");
            assertAnswer(
                "VARCHAR|\"10;11;12\"",
                "SELECT TYPEOF(bz(5)), REPLACE(REPLACE(TO_JSON(bz(5)), '-', '/'), ':', ';')");
            assertAnswer(
                "VARCHAR|\"7\"",
                "SELECT TYPEOF(rv(TO_VARIANT('7 8'))), REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('7 8'))), '-', '/'), "
                    + "':', ';')");
            assertAnswer(
                "VARCHAR|\"100.0\"",
                "SELECT TYPEOF(rv(TO_VARIANT('1e2'))), REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('1e2'))), '-', '/'), "
                    + "':', ';')");
            assertAnswer(
                "VARCHAR|\"\"",
                "SELECT TYPEOF(rv(TO_VARIANT(''))), REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT(''))), '-', '/'), ':', "
                    + "';')");
            assertAnswer(
                "VARCHAR|\"12\"",
                "SELECT TYPEOF(rv(TO_VARIANT('12'))), REPLACE(REPLACE(TO_JSON(rv(TO_VARIANT('12'))), '-', '/'), "
                    + "':', ';')");
            assertAnswer(
                "null|null",
                "SELECT TYPEOF(rv(NULL)), REPLACE(REPLACE(TO_JSON(rv(NULL)), '-', '/'), ':', ';')");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
