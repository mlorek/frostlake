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
 * A Scala handler's semi-structured arguments, as a Java handler's are (live-verified): a String takes a VARIANT's JSON
 * as TO_JSON converts it, a non-finite DOUBLE quoted, an Array[String] element is a text's content, a DOUBLE as a FLOAT
 * prints and a BINARY's bytes read as UTF-8 text, and a java.util.Map value the same; a BINARY is an Array[Byte] and a
 * NUMBER a BigDecimal at its scale.
 */
public class ScalaHandlerVariantTextTest extends MeasuredCellsTestSupport {

    /** A function's VARIANT, ARRAY and OBJECT arguments as Scala texts. */
    @Test
    public void scalaTexts() {
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'",
                "Statement executed successfully."},
            {"CREATE OR REPLACE FUNCTION sb794(v BINARY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$\nobject H {\n  def f(v: Array[Byte]): String = if (v == null) \"null\" else "
                + "(v.getClass.getName + \":\" + v.mkString(\",\")).replace('-', '/')\n}\n$$",
                "Function SB794 successfully created."},
            {"SELECT sb794(TO_BINARY('ABCD', 'HEX'))",
                "[B:/85,/51"},
            {"CREATE OR REPLACE FUNCTION sn794(v NUMBER(10, 2)) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$\nobject H {\n  def f(v: java.math.BigDecimal): String = if (v == null) "
                + "\"null\" else (v.getClass.getName + \":\" + v).replace('-', '/')\n}\n$$",
                "Function SN794 successfully created."},
            {"SELECT sn794(12.5)",
                "java.math.BigDecimal:12.50"},
            {"CREATE OR REPLACE FUNCTION sv794(v VARIANT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$\nobject H {\n  def f(v: String): String = if (v == null) \"null\" else "
                + "(v).replace('-', '/')\n}\n$$",
                "Function SV794 successfully created."},
            {"ALTER SESSION SET TIMEZONE = 'UTC'",
                "Statement executed successfully."},
            {"SELECT sv794(TO_VARIANT('2024-01-02'::DATE))",
                "\"2024/01/02\""},
            {"SELECT sv794(TO_VARIANT(TO_BINARY('ABCD', 'HEX')))",
                "\"ABCD\""},
            {"SELECT sv794(TO_VARIANT('10:11:12.5'::TIME))",
                "\"10:11:12\""},
            {"SELECT sv794(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_NTZ))",
                "\"2024/01/02 10:11:12.000\""},
            {"SELECT sv794(TO_VARIANT('2024-01-02 10:11:12.123456789'::TIMESTAMP_NTZ(9)))",
                "\"2024/01/02 10:11:12.123\""},
            {"SELECT sv794(TO_VARIANT('2024-01-02 10:11:12 +01:00'::TIMESTAMP_TZ))",
                "\"2024/01/02 10:11:12.000 +0100\""},
            {"SELECT sv794(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_LTZ))",
                "\"2024/01/02 10:11:12.000 Z\""},
            {"SELECT sv794(TO_VARIANT('NaN'::FLOAT))",
                "\"NaN\""},
            {"SELECT sv794(TO_VARIANT(1.5))",
                "1.5"},
            {"SELECT sv794(PARSE_JSON('null'))",
                "null"},
            {"SELECT sv794(NULL)",
                "null"},
            {"SELECT sv794(ARRAY_CONSTRUCT('2024-01-02'::DATE, TO_BINARY('ABCD', 'HEX')))",
                "[\"2024/01/02\",\"ABCD\"]"},
            {"SELECT sv794(OBJECT_CONSTRUCT('d', '2024-01-02'::DATE, 't', '10:11:12'::TIME))",
                "{\"d\":\"2024/01/02\",\"t\":\"10:11:12\"}"},
            {"CREATE OR REPLACE FUNCTION sa794(v ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$\nobject H {\n  def f(v: Array[String]): String = if (v == null) \"null\" else "
                + "(v.map(x => \"[\" + x + \"]\").mkString).replace('-', '/')\n}\n$$",
                "Function SA794 successfully created."},
            {"SELECT sa794(ARRAY_CONSTRUCT('2024-01-02'::DATE, TO_BINARY('ABCD', 'HEX'), 1.5, 'x', NULL, "
                + "PARSE_JSON('null'), ARRAY_CONSTRUCT(1)))",
                "[2024/01/02][��][1.5][x][null][null][[1]]"},
            {"CREATE OR REPLACE FUNCTION so794(v OBJECT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$\nobject H {\n  def f(v: scala.collection.immutable.Map[String, String]): "
                + "String = if (v == null) \"null\" else (scala.collection.immutable.TreeMap(v.toSeq: "
                + "_*).toString).replace('-', '/')\n}\n$$",
                "Function SO794 successfully created."},
            {"CREATE OR REPLACE FUNCTION so2794(v OBJECT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$\nobject H {\n  def f(v: java.util.Map[String, String]): String = if (v == "
                + "null) \"null\" else (new java.util.TreeMap[String, String](v).toString).replace('-', '/')\n}\n$$",
                "Function SO2794 successfully created."},
            {"SELECT so2794(OBJECT_CONSTRUCT('d', '2024-01-02'::DATE, 'n', 1.5, 's', 'x'))",
                "{d=2024/01/02, n=1.5, s=x}"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }

    /** Strings, switch values, doubles and binaries as Scala texts. */
    @Test
    public void moreTexts() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION svs795(v VARIANT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$\nobject H {\n  def f(v: String): String = if (v == null) \"<null>\" else "
                + "v.replace('-', '/')\n}\n$$",
                "Function SVS795 successfully created."},
            {"CREATE OR REPLACE FUNCTION sva795(a ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$\nobject H {\n  def f(a: Array[String]): String = a.map(x => \"[\" + x + "
                + "\"]\").mkString.replace('-', '/')\n}\n$$",
                "Function SVA795 successfully created."},
            {"SELECT svs795('x')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SVS795': (VARCHAR(1))"},
            {"SELECT svs795(TO_VARIANT('x'))",
                "\"x\""},
            {"SELECT svs795(TO_VARIANT(TRUE))",
                "true"},
            {"SELECT svs795(TO_VARIANT('inf'::FLOAT))",
                "\"Infinity\""},
            {"SELECT svs795(PARSE_JSON('1e5'))",
                "1.000000000000000e+05"},
            {"SELECT sva795(ARRAY_CONSTRUCT(TO_BINARY('414243', 'HEX')))",
                "[ABC]"},
            {"SELECT sva795(ARRAY_CONSTRUCT(TO_BINARY('ABCD', 'HEX')))",
                "[��]"},
        });
    }

    /** Doubles as Array[String] elements. */
    @Test
    public void doubleElements() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION sva796(a ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$\nobject H {\n  def f(a: Array[String]): String = a.map(x => \"[\" + x + "
                + "\"]\").mkString.replace('-', '/')\n}\n$$",
                "Function SVA796 successfully created."},
            {"SELECT sva796(ARRAY_CONSTRUCT(1.0::FLOAT, 1e20::FLOAT, 'NaN'::FLOAT))",
                "[1][1e+20][NaN]"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }
}
