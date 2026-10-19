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
 * What the readers of the {@code Variant} a Java handler takes for a VARIANT argument answer, by the kind of
 * value the argument holds: {@code asString}, {@code asJsonString}, the number readers, {@code asBoolean},
 * {@code asMap}, {@code asList}, {@code asArray}, {@code asBinary}, {@code asDate}, {@code asTime},
 * {@code asTimestamp}, {@code equals} and {@code hashCode}. A reader the value's kind does not answer raises
 * an {@code UncheckedIOException}; one that finds no value of its kind in a text answers 0 or false. The
 * session's TIMEZONE is set by the test and unset whatever the assertions make of the statements between.
 * Every answer is live-verified.
 */
public class SnowparkVariantReadTest extends MeasuredCellsTestSupport {

    @Test
    public void anArgumentAnswersItsReaders() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function VS successfully created.",
                "CREATE OR REPLACE FUNCTION vs(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES "
                    + "= ('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } public static String f(Variant x) { return "
                    + "String.join(\"|\", g(() -> x.toString()), g(() -> x.asString()), g(() -> x.asJsonString()), "
                    + "g(() -> x.asInt()), g(() -> x.asLong()), g(() -> x.asDouble()), g(() -> x.asBigDecimal()), "
                    + "g(() -> x.asBoolean()), g(() -> x.asBigInteger()), g(() -> x.asShort()), g(() -> "
                    + "x.asFloat())); } } $$");
            assertAnswer(
                "{\"a\":\"x\",\"n\":1}|{\"a\":\"x\",\"n\":1}|{\"a\":\"x\",\"n\":1}|0|0|0.0|0|false|0|0|0.0",
                "SELECT vs(PARSE_JSON('{\"a\":\"x\",\"n\":1}'))");
            assertAnswer(
                "1.5|1.5|1.5|1|1|1.5|1.5|EX:UncheckedIOException|1|1|1.5",
                "SELECT vs(1.5)");
            assertAnswer(
                "7|7|7|7|7|7.0|7|EX:UncheckedIOException|7|7|7.0",
                "SELECT vs(7)");
            assertAnswer(
                "s|s|\"s\"|0|0|0.0|0|false|0|0|0.0",
                "SELECT vs(TO_VARIANT('s'))");
            assertAnswer(
                "12|12|12|12|12|12.0|12|true|12|12|12.0",
                "SELECT vs(TO_VARIANT('12'))");
            assertAnswer(
                "true|true|true|1|1|1.0|1|true|1|1|1.0",
                "SELECT vs(TRUE::VARIANT)");
            assertAnswer(
                "[1,\"b\",null]|[1,\"b\",null]|[1,\"b\",null]|0|0|0.0|0|false|0|0|0.0",
                "SELECT vs(PARSE_JSON('[1,\"b\",null]'))");
            assertAnswer(
                "null|null|null|0|0|0.0|0|false|0|0|0.0",
                "SELECT vs(PARSE_JSON('null'))");
            assertAnswer(
                "2024-03-05|2024-03-05|\"2024-03-05\"|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vs(TO_VARIANT('2024-03-05'::DATE))");
            assertAnswer(
                "ff01|ff01|\"ff01\"|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vs(TO_VARIANT(TO_BINARY('FF01', 'HEX')))");
            assertAnswer(
                "10:11:12|10:11:12|\"10:11:12\"|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vs(TO_VARIANT('10:11:12'::TIME))");
            answer(
                "SELECT vs(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ))");
            assertShapedAnswer(
                "12345678901234567890|12345678901234567890|12345678901234567890|-350287150|-<ID19>|1.2345678901234567E19|12345678901234567890|EX:UncheckedIOException|12345678901234567890|2770|1.2345679E19",
                "SELECT vs(TO_VARIANT(12345678901234567890))");
            assertAnswer(
                "1.5|1.5|1.5|1|1|1.5|1.5|EX:UncheckedIOException|1|1|1.5",
                "SELECT vs(TO_VARIANT(1.5::FLOAT))");
            assertAnswer(
                "true|true|true|1|1|1.0|1|true|1|1|1.0",
                "SELECT vs(TO_VARIANT('true'))");
            assertAnswer(
                "1|1|1|1|1|1.0|1|EX:UncheckedIOException|1|1|1.0",
                "SELECT vs(TO_VARIANT(1))");
            assertAnswer(
                "[]|[]|[]|0|0|0.0|0|false|0|0|0.0",
                "SELECT vs(PARSE_JSON('[]'))");
            assertAnswer(
                "{}|{}|{}|0|0|0.0|0|false|0|0|0.0",
                "SELECT vs(PARSE_JSON('{}'))");
            assertAnswer(
                "|||0|0|0.0|0|false|0|0|0.0",
                "SELECT vs(TO_VARIANT(''))");
            assertAnswer(
                "Function VC successfully created.",
                "CREATE OR REPLACE FUNCTION vc(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES "
                    + "= ('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } public static String f(Variant x) { return "
                    + "String.join(\"|\", g(() -> new java.util.TreeMap<String, Variant>(x.asMap()).toString()), "
                    + "g(() -> x.asMap().size()), g(() -> x.asList().toString()), g(() -> x.asList().size()), g(() "
                    + "-> x.asArray().length), g(() -> x.asArray()[0].getClass().getSimpleName()), g(() -> "
                    + "x.asList().get(0).asString()), g(() -> x.asMap().get(\"a\").asString()), g(() -> "
                    + "x.asMap().get(\"zz\"))); } } $$");
            assertAnswer(
                "{a=x, "
                    + "n=1}|2|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|x|NULL",
                "SELECT vc(PARSE_JSON('{\"a\":\"x\",\"n\":1}'))");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vc(1.5)");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vc(7)");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vc(TO_VARIANT('s'))");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vc(TO_VARIANT('12'))");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vc(TRUE::VARIANT)");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|[1, b, "
                    + "null]|3|3|Variant|1|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vc(PARSE_JSON('[1,\"b\",null]'))");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vc(PARSE_JSON('null'))");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|[]|0|0|EX:ArrayIndexOutOfBoundsException|EX:ArrayIndexOutOfBoundsException|EX:UncheckedIOException|EX:UncheckedIOException",
                "SELECT vc(PARSE_JSON('[]'))");
            assertAnswer(
                "{}|0|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:NullPointerException|NULL",
                "SELECT vc(PARSE_JSON('{}'))");
            assertAnswer(
                "Function VO successfully created.",
                "CREATE OR REPLACE FUNCTION vo(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES "
                    + "= ('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; "
                    + "class H { interface C { Object get() throws Exception; } static String g(C c) { try { Object "
                    + "o = c.get(); return o == null ? \"NULL\" : String.valueOf(o); } catch (Throwable e) { return "
                    + "\"EX:\" + e.getClass().getSimpleName(); } } public static String f(Variant x) { return "
                    + "String.join(\"|\", g(() -> x.asBinary().length), g(() -> x.asBinary()[0]), g(() -> "
                    + "x.asDate().getTime()), g(() -> x.asTime().getTime()), g(() -> x.asTimestamp().getTime()), "
                    + "g(() -> x.equals(new Variant(1))), g(() -> x.hashCode() == new Variant(1).hashCode()), g(() "
                    + "-> x.equals(x)), g(() -> x.getClass().getName())); } } $$");
            assertAnswer(
                "EX:NullPointerException|EX:NullPointerException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(PARSE_JSON('{\"a\":\"x\",\"n\":1}'))");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|1|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(1.5)");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|7|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(7)");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(TO_VARIANT('s'))");
            assertAnswer(
                "EX:NullPointerException|EX:NullPointerException|EX:IllegalArgumentException|EX:IllegalArgumentException|12|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(TO_VARIANT('12'))");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(TRUE::VARIANT)");
            assertAnswer(
                "EX:NullPointerException|EX:NullPointerException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(PARSE_JSON('[1,\"b\",null]'))");
            assertAnswer(
                "EX:NullPointerException|EX:NullPointerException|EX:IllegalArgumentException|EX:IllegalArgumentException|EX:IllegalArgumentException|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(PARSE_JSON('null'))");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|1709596800000|EX:UncheckedIOException|EX:UncheckedIOException|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(TO_VARIANT('2024-03-05'::DATE))");
            assertAnswer(
                "2|-1|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(TO_VARIANT(TO_BINARY('FF01', 'HEX')))");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|36672000|EX:UncheckedIOException|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(TO_VARIANT('10:11:12'::TIME))");
            assertAnswer(
                "EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|EX:UncheckedIOException|1709633472345|false|false|true|com.snowflake.snowpark_java.types.Variant",
                "SELECT vo(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ))");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
