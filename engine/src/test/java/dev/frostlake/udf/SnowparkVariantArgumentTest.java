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
 * The {@code Variant} a Java handler receives for a VARIANT argument, by what the argument holds. An exact
 * number arrives as a decimal, a FLOAT as a double, a BOOLEAN as a boolean, a BINARY as bytes, and a DATE, a
 * TIME and a timestamp as their {@code java.sql} values - each answering as its own kind. A text, an OBJECT,
 * an ARRAY and a JSON null arrive as their text, read as JSON: a text spelling a number is that number, a text
 * spelling {@code null} a JSON null, and an array holding a SQL NULL, whose text is no JSON, the text. A
 * member of an OBJECT and an element of an ARRAY answer every reader, a JSON null among them as a value and a
 * missing key as a Java {@code null}. A SQL NULL arrives as a Java {@code null}. The session's TIMEZONE is set
 * by the test and unset whatever the assertions make of the statements between. Every answer is
 * live-verified.
 */
public class SnowparkVariantArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void anArgumentArrivesAsItsKind() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function AK successfully created.",
                "CREATE OR REPLACE FUNCTION ak(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES "
                    + "= ('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } static String msg(C c) { try { Object o = "
                    + "c.get(); return \"OK:\" + (o == null ? \"NULL\" : String.valueOf(o)); } catch (Throwable e) { "
                    + "return e.getClass().getSimpleName() + \":\" + e.getMessage(); } } static String hex(byte[] b) "
                    + "{ if (b == null) return \"NULL\"; StringBuilder s = new StringBuilder(); for (byte v : b) "
                    + "s.append(String.format(\"%02x\", v)); return s.toString(); } static String kind(Variant v) { "
                    + "return v.asJsonNode().getNodeType() + \"/\" + v.asJsonNode().numberType(); } public static "
                    + "String f(Variant x) { return String.join(\"|\", g(() -> kind(x)), g(() -> x.asJsonString()), "
                    + "msg(() -> x.asMap().size()), msg(() -> x.asList().size()), msg(() -> x.asBoolean()), msg(() "
                    + "-> x.asDate().getTime()), msg(() -> x.asTimestamp().getTime()), msg(() -> "
                    + "x.asTimestamp().getNanos())); } } $$");
            assertAnswer(
                "NUMBER/BIG_DECIMAL|1|UncheckedIOException:java.io.IOException: Failed to convert 1 to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert 1 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to "
                    + "Boolean is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "FixedNumber to Date is not supported|OK:1|OK:1000000",
                "SELECT ak(1)");
            assertAnswer(
                "NUMBER/BIG_DECIMAL|1.5|UncheckedIOException:java.io.IOException: Failed to convert 1.5 to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert 1.5 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to "
                    + "Boolean is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "FixedNumber to Date is not supported|OK:1|OK:1000000",
                "SELECT ak(1.5)");
            assertAnswer(
                "NUMBER/DOUBLE|1.5|UncheckedIOException:java.io.IOException: Failed to convert 1.5 to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert 1.5 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of RealNumber to "
                    + "Boolean is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "RealNumber to Date is not supported|OK:1|OK:1000000",
                "SELECT ak(1.5::FLOAT)");
            assertAnswer(
                "BOOLEAN/null|true|UncheckedIOException:java.io.IOException: Failed to convert true to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert true to "
                    + "Array|OK:true|UncheckedIOException:java.io.IOException: Conversion from Variant of Boolean to "
                    + "Date is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "Boolean to Timestamp is not supported|UncheckedIOException:java.io.IOException: Conversion "
                    + "from Variant of Boolean to Timestamp is not supported",
                "SELECT ak(TRUE::VARIANT)");
            assertAnswer(
                "STRING/null|\"s\"|UncheckedIOException:java.io.IOException: Failed to convert s to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert s to "
                    + "Array|OK:false|IllegalArgumentException:null|IllegalArgumentException:Timestamp format must "
                    + "be yyyy-mm-dd hh:mm:ss[.fffffffff]|IllegalArgumentException:Timestamp format must be "
                    + "yyyy-mm-dd hh:mm:ss[.fffffffff]",
                "SELECT ak(TO_VARIANT('s'))");
            assertAnswer(
                "STRING/null|\"2024-03-05\"|UncheckedIOException:java.io.IOException: Failed to convert "
                    + "2024-03-05 to Map|UncheckedIOException:java.io.IOException: Failed to convert 2024-03-05 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of Date to Boolean is "
                    + "not supported|OK:1709596800000|UncheckedIOException:java.io.IOException: Conversion from "
                    + "Variant of Date to Timestamp is not supported|UncheckedIOException:java.io.IOException: "
                    + "Conversion from Variant of Date to Timestamp is not supported",
                "SELECT ak(TO_VARIANT('2024-03-05'::DATE))");
            assertAnswer(
                "STRING/null|\"10:11:12\"|UncheckedIOException:java.io.IOException: Failed to convert 10:11:12 to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert 10:11:12 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of Time to Boolean is "
                    + "not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of Time to "
                    + "Date is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "Time to Timestamp is not supported|UncheckedIOException:java.io.IOException: Conversion from "
                    + "Variant of Time to Timestamp is not supported",
                "SELECT ak(TO_VARIANT('10:11:12.123456789'::TIME))");
            answer(
                "SELECT ak(TO_VARIANT('2024-03-05 10:11:12.123456789'::TIMESTAMP_NTZ))");
            answer(
                "SELECT ak(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))");
            answer(
                "SELECT ak(TO_VARIANT('2024-03-05 10:11:12.345 +02:00'::TIMESTAMP_TZ))");
            assertAnswer(
                "BINARY/null|\"ff01\"|UncheckedIOException:java.io.IOException: Failed to convert /wE= to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert /wE= to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of Binary to Boolean "
                    + "is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of Binary "
                    + "to Date is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "Binary to Timestamp is not supported|UncheckedIOException:java.io.IOException: Conversion "
                    + "from Variant of Binary to Timestamp is not supported",
                "SELECT ak(TO_VARIANT(TO_BINARY('FF01', 'HEX')))");
            assertAnswer(
                "OBJECT/null|{\"a\":1}|OK:1|UncheckedIOException:java.io.IOException: Failed to convert  to "
                    + "Array|OK:false|IllegalArgumentException:null|IllegalArgumentException:Timestamp format must "
                    + "be yyyy-mm-dd hh:mm:ss[.fffffffff]|IllegalArgumentException:Timestamp format must be "
                    + "yyyy-mm-dd hh:mm:ss[.fffffffff]",
                "SELECT ak(PARSE_JSON('{\"a\":1}'))");
            assertAnswer(
                "ARRAY/null|[1,2]|UncheckedIOException:java.io.IOException: Failed to convert  to "
                    + "Map|OK:2|OK:false|IllegalArgumentException:null|IllegalArgumentException:Timestamp format "
                    + "must be yyyy-mm-dd hh:mm:ss[.fffffffff]|IllegalArgumentException:Timestamp format must be "
                    + "yyyy-mm-dd hh:mm:ss[.fffffffff]",
                "SELECT ak(PARSE_JSON('[1,2]'))");
            assertAnswer(
                "NULL/null|null|UncheckedIOException:java.io.IOException: Failed to convert null to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert null to "
                    + "Array|OK:false|IllegalArgumentException:null|IllegalArgumentException:Timestamp format must "
                    + "be yyyy-mm-dd hh:mm:ss[.fffffffff]|IllegalArgumentException:Timestamp format must be "
                    + "yyyy-mm-dd hh:mm:ss[.fffffffff]",
                "SELECT ak(PARSE_JSON('null'))");
            assertAnswer(
                "NUMBER/DOUBLE|\"NaN\"|UncheckedIOException:java.io.IOException: Failed to convert NaN to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert NaN to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of RealNumber to "
                    + "Boolean is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "RealNumber to Date is not supported|OK:0|OK:0",
                "SELECT ak(TO_VARIANT('NaN'::FLOAT))");
            assertShapedAnswer(
                "NUMBER/DOUBLE|1.0E30|UncheckedIOException:java.io.IOException: Failed to convert 1.0E30 to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert 1.0E30 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of RealNumber to "
                    + "Boolean is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "RealNumber to Date is not supported|OK:<ID19>|OK:807000000",
                "SELECT ak(TO_VARIANT(1e30::FLOAT))");
            assertShapedAnswer(
                "NUMBER/BIG_DECIMAL|12345678901234567890.5|UncheckedIOException:java.io.IOException: Failed to "
                    + "convert 12345678901234567890.5 to Map|UncheckedIOException:java.io.IOException: Failed to "
                    + "convert 12345678901234567890.5 to Array|UncheckedIOException:java.io.IOException: Conversion "
                    + "from Variant of FixedNumber to Boolean is not "
                    + "supported|UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to "
                    + "Date is not supported|OK:-<ID19>|OK:274000000",
                "SELECT ak(TO_VARIANT(12345678901234567890.5))");
            assertAnswer(
                "NUMBER/BIG_DECIMAL|100|UncheckedIOException:java.io.IOException: Failed to convert 100 to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert 100 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to "
                    + "Boolean is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "FixedNumber to Date is not supported|OK:100|OK:100000000",
                "SELECT ak(TO_VARIANT(100))");
            assertAnswer(
                "NUMBER/BIG_DECIMAL|1.5|UncheckedIOException:java.io.IOException: Failed to convert 1.5 to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert 1.5 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to "
                    + "Boolean is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "FixedNumber to Date is not supported|OK:1|OK:1000000",
                "SELECT ak(TO_VARIANT(1.50))");
            assertAnswer(
                "NULL/null|null|UncheckedIOException:java.io.IOException: Failed to convert null to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert null to "
                    + "Array|OK:false|IllegalArgumentException:null|IllegalArgumentException:Timestamp format must "
                    + "be yyyy-mm-dd hh:mm:ss[.fffffffff]|IllegalArgumentException:Timestamp format must be "
                    + "yyyy-mm-dd hh:mm:ss[.fffffffff]",
                "SELECT ak(TO_VARIANT('null'))");
            assertAnswer(
                "OBJECT/null|{\"a\":1}|OK:1|UncheckedIOException:java.io.IOException: Failed to convert  to "
                    + "Array|OK:false|IllegalArgumentException:null|IllegalArgumentException:Timestamp format must "
                    + "be yyyy-mm-dd hh:mm:ss[.fffffffff]|IllegalArgumentException:Timestamp format must be "
                    + "yyyy-mm-dd hh:mm:ss[.fffffffff]",
                "SELECT ak(TO_VARIANT('{\"a\":1}'))");
            assertShapedAnswer(
                "NUMBER/DOUBLE|\"Infinity\"|UncheckedIOException:java.io.IOException: Failed to convert Infinity "
                    + "to Map|UncheckedIOException:java.io.IOException: Failed to convert Infinity to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of RealNumber to "
                    + "Boolean is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "RealNumber to Date is not supported|OK:<ID19>|OK:807000000",
                "SELECT ak(TO_VARIANT('inf'::FLOAT))");
            assertAnswer(
                "NUMBER/BIG_DECIMAL|0|UncheckedIOException:java.io.IOException: Failed to convert 0 to "
                    + "Map|UncheckedIOException:java.io.IOException: Failed to convert 0 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of FixedNumber to "
                    + "Boolean is not supported|UncheckedIOException:java.io.IOException: Conversion from Variant of "
                    + "FixedNumber to Date is not supported|OK:0|OK:0",
                "SELECT ak(TO_VARIANT(0))");
            assertAnswer(
                "STRING/null|\"1970-01-02\"|UncheckedIOException:java.io.IOException: Failed to convert "
                    + "1970-01-02 to Map|UncheckedIOException:java.io.IOException: Failed to convert 1970-01-02 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of Date to Boolean is "
                    + "not supported|OK:86400000|UncheckedIOException:java.io.IOException: Conversion from Variant "
                    + "of Date to Timestamp is not supported|UncheckedIOException:java.io.IOException: Conversion "
                    + "from Variant of Date to Timestamp is not supported",
                "SELECT ak(TO_VARIANT('1970-01-02'::DATE))");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            assertAnswer(
                "STRING/null|\"2024-03-04\"|UncheckedIOException:java.io.IOException: Failed to convert "
                    + "2024-03-04 to Map|UncheckedIOException:java.io.IOException: Failed to convert 2024-03-04 to "
                    + "Array|UncheckedIOException:java.io.IOException: Conversion from Variant of Date to Boolean is "
                    + "not supported|OK:1709539200000|UncheckedIOException:java.io.IOException: Conversion from "
                    + "Variant of Date to Timestamp is not supported|UncheckedIOException:java.io.IOException: "
                    + "Conversion from Variant of Date to Timestamp is not supported",
                "SELECT ak(TO_VARIANT('2024-03-05'::DATE))");
            answer(
                "SELECT ak(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ))");
            answer(
                "SELECT ak(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void membersAndElementsAnswerEveryReader() {
        assertAnswer(
            "Function SV successfully created.",
            "CREATE OR REPLACE FUNCTION sv(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; class H "
                + "{ interface C { Object get() throws Exception; } static String g(C c) { try { Object o = c.get(); "
                + "return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return \"EX:\" + "
                + "e.getClass().getSimpleName(); } } static String msg(C c) { try { Object o = c.get(); return "
                + "\"OK:\" + (o == null ? \"NULL\" : String.valueOf(o)); } catch (Throwable e) { return "
                + "e.getClass().getSimpleName() + \":\" + e.getMessage(); } } static String hex(byte[] b) { if (b == "
                + "null) return \"NULL\"; StringBuilder s = new StringBuilder(); for (byte v : b) "
                + "s.append(String.format(\"%02x\", v)); return s.toString(); } static String kind(Variant v) { "
                + "return v.asJsonNode().getNodeType() + \"/\" + v.asJsonNode().numberType(); } static String "
                + "one(Variant v) { if (v == null) return \"JAVANULL\"; return g(() -> kind(v)) + \":\" + g(() -> "
                + "v.asJsonString()) + \":\" + msg(() -> v.asBoolean()); } public static String f(Variant x) { "
                + "return String.join(\"|\", g(() -> one(x.asMap().get(\"n\"))), g(() -> one(x.asMap().get(\"s\"))), "
                + "g(() -> one(x.asMap().get(\"z\"))), g(() -> one(x.asMap().get(\"d\"))), g(() -> "
                + "one(x.asList().get(0))), g(() -> one(x.asList().get(1))), g(() -> one(x.asList().get(2))), g(() "
                + "-> x.asMap().keySet().toString()), g(() -> x.asMap().getClass().getSimpleName()), g(() -> "
                + "x.asList().getClass().getSimpleName())); } } $$");
        assertAnswer(
            "NUMBER/INT:1:OK:true|STRING/null:\"12\":OK:false|NULL/null:null:OK:false|NUMBER/DOUBLE:1.5:OK:false|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|[b, s, d, z, n]|HashMap|EX:UncheckedIOException",
            "SELECT sv(PARSE_JSON('{\"n\":1,\"d\":1.5,\"s\":\"12\",\"b\":true,\"z\":null}'))");
        assertAnswer(
            "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|NUMBER/INT:1:OK:true|STRING/null:\"b\":OK:false|NULL/null:null:OK:false|EX:UncheckedIOException|EX:UncheckedIOException|ArrayList",
            "SELECT sv(PARSE_JSON('[1,\"b\",null,1.5]'))");
        assertAnswer(
            "NUMBER/DOUBLE:1.5:OK:false|STRING/null:\"FF01\":OK:false|JAVANULL|STRING/null:\"2024-03-05\":OK:false|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|[s, d, n]|HashMap|EX:UncheckedIOException",
            "SELECT sv(OBJECT_CONSTRUCT('d', '2024-03-05'::DATE, 'n', 1.5::FLOAT, 's', TO_BINARY('FF01', 'HEX')))");
        assertAnswer(
            "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException",
            "SELECT sv(ARRAY_CONSTRUCT(1, NULL, 'x'))");
        assertAnswer(
            "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|STRING/null:\"2024-03-05\":OK:false|STRING/null:\"FF01\":OK:false|BOOLEAN/null:true:OK:true|EX:UncheckedIOException|EX:UncheckedIOException|ArrayList",
            "SELECT sv(ARRAY_CONSTRUCT('2024-03-05'::DATE, TO_BINARY('FF01', 'HEX'), TRUE))");
        assertAnswer(
            "JAVANULL|JAVANULL|NUMBER/INT:1:OK:true|JAVANULL|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|[a, z, m]|HashMap|EX:UncheckedIOException",
            "SELECT sv(PARSE_JSON('{\"z\":1,\"a\":2,\"m\":3}'))");
        assertAnswer(
            "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|ARRAY/null:[1]:OK:false|OBJECT/null:{\"k\":2}:OK:false|STRING/null:\"t\":OK:false|EX:UncheckedIOException|EX:UncheckedIOException|ArrayList",
            "SELECT sv(PARSE_JSON('[[1],{\"k\":2},\"t\"]'))");
    }
}
