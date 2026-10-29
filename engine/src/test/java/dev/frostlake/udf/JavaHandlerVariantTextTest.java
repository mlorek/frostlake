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
 * A Java handler's semi-structured arguments (live-verified). A VARIANT parameter declared as any class but a String or
 * a Variant is refused at CREATE. A String takes the value's JSON as TO_JSON converts it - a string quoted, a DATE held
 * too, a DOUBLE in the fifteen-decimal form - with a non-finite DOUBLE quoted into a string, a function's and a
 * procedure's alike. A String[] element and a Map value is a text's content, a DOUBLE as a FLOAT prints, a BINARY's
 * bytes read as UTF-8 text and a container's JSON. A TIME or a timestamp argument arrives cut to its parameter's
 * precision.
 */
public class JavaHandlerVariantTextTest extends MeasuredCellsTestSupport {

    /** Each class a VARIANT parameter is refused for. */
    @Test
    public void variantParameterClasses() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE jpo794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, Object v) {\n    "
                + "Object o = v;\n    return (o == null ? \"null\" : o.getClass().getName() + \":\" + o).replace('-', "
                + "'/');\n  }\n}\n$$",
                "REFUSED Unsupported argument type: java.lang.Object in function JPO794 with handler H.run"},
            {"CALL jpo794(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: | Unknown function JPO794."},
            {"CALL jpo794(TO_VARIANT(7))",
                "REFUSED SQL compilation error: | Unknown function JPO794."},
            {"CALL jpo794(TO_VARIANT('x'))",
                "REFUSED SQL compilation error: | Unknown function JPO794."},
            {"CALL jpo794(ARRAY_CONSTRUCT(1, 'a'))",
                "REFUSED SQL compilation error: | Unknown function JPO794."},
            {"CALL jpo794(TO_VARIANT('2024-01-02'::DATE))",
                "REFUSED SQL compilation error: | Unknown function JPO794."},
            {"CREATE OR REPLACE PROCEDURE jpm794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                + "java.util.Map<String, String> v) {\n    Object o = v;\n    return (o == null ? \"null\" : "
                + "o.getClass().getName() + \":\" + o).replace('-', '/');\n  }\n}\n$$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported as input to argument at index 0 "
                + "with Java type java.util.Map<String, String> in function JPM794 with handler H.run"},
            {"CALL jpm794(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: | Unknown function JPM794."},
            {"CALL jpm794(TO_VARIANT(7))",
                "REFUSED SQL compilation error: | Unknown function JPM794."},
            {"CALL jpm794(TO_VARIANT('x'))",
                "REFUSED SQL compilation error: | Unknown function JPM794."},
            {"CALL jpm794(ARRAY_CONSTRUCT(1, 'a'))",
                "REFUSED SQL compilation error: | Unknown function JPM794."},
            {"CALL jpm794(TO_VARIANT('2024-01-02'::DATE))",
                "REFUSED SQL compilation error: | Unknown function JPM794."},
            {"CREATE OR REPLACE PROCEDURE jpl794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                + "java.util.List<String> v) {\n    Object o = v;\n    return (o == null ? \"null\" : "
                + "o.getClass().getName() + \":\" + o).replace('-', '/');\n  }\n}\n$$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported as input to argument at index 0 "
                + "with Java type java.util.Map<String, String> in function JPL794 with handler H.run"},
            {"CALL jpl794(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: | Unknown function JPL794."},
            {"CALL jpl794(TO_VARIANT(7))",
                "REFUSED SQL compilation error: | Unknown function JPL794."},
            {"CALL jpl794(TO_VARIANT('x'))",
                "REFUSED SQL compilation error: | Unknown function JPL794."},
            {"CALL jpl794(ARRAY_CONSTRUCT(1, 'a'))",
                "REFUSED SQL compilation error: | Unknown function JPL794."},
            {"CALL jpl794(TO_VARIANT('2024-01-02'::DATE))",
                "REFUSED SQL compilation error: | Unknown function JPL794."},
            {"CREATE OR REPLACE PROCEDURE jpi794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, Integer v) {\n    "
                + "Object o = v;\n    return (o == null ? \"null\" : o.getClass().getName() + \":\" + o).replace('-', "
                + "'/');\n  }\n}\n$$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported as input to argument at index 0 "
                + "with Java type Integer in function JPI794 with handler H.run"},
            {"CALL jpi794(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: | Unknown function JPI794."},
            {"CALL jpi794(TO_VARIANT(7))",
                "REFUSED SQL compilation error: | Unknown function JPI794."},
            {"CALL jpi794(TO_VARIANT('x'))",
                "REFUSED SQL compilation error: | Unknown function JPI794."},
            {"CALL jpi794(ARRAY_CONSTRUCT(1, 'a'))",
                "REFUSED SQL compilation error: | Unknown function JPI794."},
            {"CALL jpi794(TO_VARIANT('2024-01-02'::DATE))",
                "REFUSED SQL compilation error: | Unknown function JPI794."},
            {"CREATE OR REPLACE PROCEDURE jpb794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                + "java.math.BigDecimal v) {\n    Object o = v;\n    return (o == null ? \"null\" : "
                + "o.getClass().getName() + \":\" + o).replace('-', '/');\n  }\n}\n$$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported as input to argument at index 0 "
                + "with Java type java.math.BigDecimal in function JPB794 with handler H.run"},
            {"CALL jpb794(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: | Unknown function JPB794."},
            {"CALL jpb794(TO_VARIANT(7))",
                "REFUSED SQL compilation error: | Unknown function JPB794."},
            {"CALL jpb794(TO_VARIANT('x'))",
                "REFUSED SQL compilation error: | Unknown function JPB794."},
            {"CALL jpb794(ARRAY_CONSTRUCT(1, 'a'))",
                "REFUSED SQL compilation error: | Unknown function JPB794."},
            {"CALL jpb794(TO_VARIANT('2024-01-02'::DATE))",
                "REFUSED SQL compilation error: | Unknown function JPB794."},
            {"CREATE OR REPLACE PROCEDURE jpy794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, byte[] v) {\n    "
                + "Object o = v;\n    return (o == null ? \"null\" : o.getClass().getName() + \":\" + o).replace('-', "
                + "'/');\n  }\n}\n$$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported as input to argument at index 0 "
                + "with Java type byte[] in function JPY794 with handler H.run"},
            {"CALL jpy794(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: | Unknown function JPY794."},
            {"CALL jpy794(TO_VARIANT(7))",
                "REFUSED SQL compilation error: | Unknown function JPY794."},
            {"CALL jpy794(TO_VARIANT('x'))",
                "REFUSED SQL compilation error: | Unknown function JPY794."},
            {"CALL jpy794(ARRAY_CONSTRUCT(1, 'a'))",
                "REFUSED SQL compilation error: | Unknown function JPY794."},
            {"CALL jpy794(TO_VARIANT('2024-01-02'::DATE))",
                "REFUSED SQL compilation error: | Unknown function JPY794."},
            {"CREATE OR REPLACE PROCEDURE jpd794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, java.sql.Date v) "
                + "{\n    Object o = v;\n    return (o == null ? \"null\" : o.getClass().getName() + \":\" + "
                + "o).replace('-', '/');\n  }\n}\n$$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported as input to argument at index 0 "
                + "with Java type java.sql.Date in function JPD794 with handler H.run"},
            {"CALL jpd794(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: | Unknown function JPD794."},
            {"CALL jpd794(TO_VARIANT(7))",
                "REFUSED SQL compilation error: | Unknown function JPD794."},
            {"CALL jpd794(TO_VARIANT('x'))",
                "REFUSED SQL compilation error: | Unknown function JPD794."},
            {"CALL jpd794(ARRAY_CONSTRUCT(1, 'a'))",
                "REFUSED SQL compilation error: | Unknown function JPD794."},
            {"CALL jpd794(TO_VARIANT('2024-01-02'::DATE))",
                "REFUSED SQL compilation error: | Unknown function JPD794."},
            {"CREATE OR REPLACE PROCEDURE jpa794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, String[] v) {\n    "
                + "Object o = v;\n    return (o == null ? \"null\" : o.getClass().getName() + \":\" + o).replace('-', "
                + "'/');\n  }\n}\n$$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported as input to argument at index 0 "
                + "with Java type String[] in function JPA794 with handler H.run"},
            {"CALL jpa794(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: | Unknown function JPA794."},
            {"CALL jpa794(TO_VARIANT(7))",
                "REFUSED SQL compilation error: | Unknown function JPA794."},
            {"CALL jpa794(TO_VARIANT('x'))",
                "REFUSED SQL compilation error: | Unknown function JPA794."},
            {"CALL jpa794(ARRAY_CONSTRUCT(1, 'a'))",
                "REFUSED SQL compilation error: | Unknown function JPA794."},
            {"CALL jpa794(TO_VARIANT('2024-01-02'::DATE))",
                "REFUSED SQL compilation error: | Unknown function JPA794."},
        });
    }

    /** A function's VARIANT, ARRAY and OBJECT arguments as Java texts. */
    @Test
    public void functionTexts() {
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'UTC'",
                "Statement executed successfully."},
            {"CREATE OR REPLACE FUNCTION jfs794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$\nclass H {\n  public static String f(String v) { return v == null ? \"null\" "
                + ": v.replace('-', '/'); }\n}\n$$",
                "Function JFS794 successfully created."},
            {"CREATE OR REPLACE FUNCTION jfv794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$\nimport "
                + "com.snowflake.snowpark_java.types.Variant;\nclass H {\n  public static String f(Variant v) {\n    "
                + "if (v == null) return \"null\";\n    return (v.toString() + \"|\" + v.asJsonString()).replace('-', "
                + "'/');\n  }\n}\n$$",
                "Function JFV794 successfully created."},
            {"CREATE OR REPLACE FUNCTION jfa794(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER "
                + "= 'H.f' AS $$\nclass H {\n  public static String f(String[] a) {\n    if (a == null) return "
                + "\"null\";\n    StringBuilder b = new StringBuilder();\n    for (String x : a) "
                + "b.append('[').append(x).append(']');\n    return b.toString().replace('-', '/');\n  }\n}\n$$",
                "Function JFA794 successfully created."},
            {"CREATE OR REPLACE FUNCTION jfo794(o OBJECT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER "
                + "= 'H.f' AS $$\nclass H {\n  public static String f(java.util.Map<String, String> o) {\n    if (o == "
                + "null) return \"null\";\n    return new java.util.TreeMap<String, String>(o).toString().replace('-', "
                + "'/');\n  }\n}\n$$",
                "Function JFO794 successfully created."},
            {"SELECT jfs794(TO_VARIANT('2024-01-02'::DATE))",
                "\"2024/01/02\""},
            {"SELECT jfs794(TO_VARIANT(TO_BINARY('ABCD', 'HEX')))",
                "\"ABCD\""},
            {"SELECT jfs794(TO_VARIANT('10:11:12.5'::TIME))",
                "\"10:11:12\""},
            {"SELECT jfs794(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_NTZ))",
                "\"2024/01/02 10:11:12.000\""},
            {"SELECT jfs794(TO_VARIANT('2024-01-02 10:11:12.123456789'::TIMESTAMP_NTZ(9)))",
                "\"2024/01/02 10:11:12.123\""},
            {"SELECT jfs794(TO_VARIANT('2024-01-02 10:11:12 +01:00'::TIMESTAMP_TZ))",
                "\"2024/01/02 10:11:12.000 +0100\""},
            {"SELECT jfs794(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_LTZ))",
                "\"2024/01/02 10:11:12.000 Z\""},
            {"SELECT jfs794(TO_VARIANT('NaN'::FLOAT))",
                "\"NaN\""},
            {"SELECT jfs794(TO_VARIANT(1.5))",
                "1.5"},
            {"SELECT jfs794(PARSE_JSON('null'))",
                "null"},
            {"SELECT jfs794(NULL)",
                "null"},
            {"SELECT jfs794(ARRAY_CONSTRUCT('2024-01-02'::DATE, TO_BINARY('ABCD', 'HEX')))",
                "[\"2024/01/02\",\"ABCD\"]"},
            {"SELECT jfs794(OBJECT_CONSTRUCT('d', '2024-01-02'::DATE, 't', '10:11:12'::TIME))",
                "{\"d\":\"2024/01/02\",\"t\":\"10:11:12\"}"},
            {"SELECT jfv794(TO_VARIANT('2024-01-02'::DATE))",
                "2024/01/02|\"2024/01/02\""},
            {"SELECT jfv794(TO_VARIANT(TO_BINARY('ABCD', 'HEX')))",
                "abcd|\"abcd\""},
            {"SELECT jfv794(TO_VARIANT('10:11:12.5'::TIME))",
                "10:11:12|\"10:11:12\""},
            {"SELECT jfv794(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_NTZ))",
                "2024/01/02 10:11:12.0|\"2024/01/02 10:11:12.0\""},
            {"SELECT jfv794(TO_VARIANT('2024-01-02 10:11:12.123456789'::TIMESTAMP_NTZ(9)))",
                "2024/01/02 10:11:12.123456789|\"2024/01/02 10:11:12.123456789\""},
            {"SELECT jfv794(TO_VARIANT('2024-01-02 10:11:12 +01:00'::TIMESTAMP_TZ))",
                "2024/01/02 09:11:12.0|\"2024/01/02 09:11:12.0\""},
            {"SELECT jfv794(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_LTZ))",
                "2024/01/02 10:11:12.0|\"2024/01/02 10:11:12.0\""},
            {"SELECT jfv794(TO_VARIANT('NaN'::FLOAT))",
                "NaN|\"NaN\""},
            {"SELECT jfv794(TO_VARIANT(1.5))",
                "1.5|1.5"},
            {"SELECT jfv794(PARSE_JSON('null'))",
                "null|null"},
            {"SELECT jfv794(NULL)",
                "null"},
            {"SELECT jfv794(ARRAY_CONSTRUCT('2024-01-02'::DATE, TO_BINARY('ABCD', 'HEX')))",
                "[\"2024/01/02\",\"ABCD\"]|[\"2024/01/02\",\"ABCD\"]"},
            {"SELECT jfv794(OBJECT_CONSTRUCT('d', '2024-01-02'::DATE, 't', '10:11:12'::TIME))",
                "{\"d\":\"2024/01/02\",\"t\":\"10:11:12\"}|{\"d\":\"2024/01/02\",\"t\":\"10:11:12\"}"},
            {"SELECT jfa794(ARRAY_CONSTRUCT('2024-01-02'::DATE, TO_BINARY('ABCD', 'HEX'), '10:11:12.5'::TIME, "
                + "'2024-01-02 10:11:12'::TIMESTAMP_NTZ, 1.5, 'x', NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(1), "
                + "OBJECT_CONSTRUCT('a', 1)))",
                "[2024/01/02][��][10:11:12][2024/01/02 10:11:12.000][1.5][x][null][null][[1]][{\"a\":1}]"},
            {"SELECT jfo794(OBJECT_CONSTRUCT_KEEP_NULL('j', PARSE_JSON('null'), 'q', NULL))",
                "{j=null, q=null}"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }

    /** Strings, doubles, binaries and members as Java texts, and a timestamp at its precision. */
    @Test
    public void moreTexts() {
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'UTC'",
                "Statement executed successfully."},
            {"CREATE OR REPLACE FUNCTION jfs795(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$\nclass H {\n  public static String f(String v) { return v == null ? "
                + "\"<null>\" : v.replace('-', '/'); }\n}\n$$",
                "Function JFS795 successfully created."},
            {"CREATE OR REPLACE FUNCTION jfa795(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER "
                + "= 'H.f' AS $$\nclass H {\n  public static String f(String[] a) {\n    StringBuilder b = new "
                + "StringBuilder();\n    for (String x : a) b.append('[').append(x == null ? \"<null>\" : "
                + "x).append(']');\n    return b.toString().replace('-', '/');\n  }\n}\n$$",
                "Function JFA795 successfully created."},
            {"CREATE OR REPLACE FUNCTION jfo795(o OBJECT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER "
                + "= 'H.f' AS $$\nclass H {\n  public static String f(java.util.Map<String, String> o) {\n    "
                + "StringBuilder b = new StringBuilder();\n    for (java.util.Map.Entry<String, String> e : "
                + "o.entrySet())\n      b.append(e.getKey()).append('=').append(e.getValue() == null ? \"<null>\" : "
                + "e.getValue()).append(';');\n    return b.toString().replace('-', '/');\n  }\n}\n$$",
                "Function JFO795 successfully created."},
            {"CREATE OR REPLACE FUNCTION jts795(t TIMESTAMP_NTZ(3), m TIME(3)) RETURNS VARCHAR LANGUAGE JAVA "
                + "RUNTIME_VERSION = '11' HANDLER = 'H.f' AS $$\nclass H {\n  public static String "
                + "f(java.sql.Timestamp t, java.sql.Time m) { return t.getNanos() + \"|\" + (m.getTime() % 1000); "
                + "}\n}\n$$",
                "Function JTS795 successfully created."},
            {"SELECT jfs795('x')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'JFS795': (VARCHAR(1))"},
            {"SELECT jfs795(TO_VARIANT('x'))",
                "\"x\""},
            {"SELECT jfs795(TO_VARIANT(1))",
                "1"},
            {"SELECT jfs795(TO_VARIANT(TRUE))",
                "true"},
            {"SELECT jfs795(PARSE_JSON('\"a\\\\nb\"'))",
                "\"a\\nb\""},
            {"SELECT jfs795(TO_VARIANT('é'))",
                "\"é\""},
            {"SELECT jfs795(TO_VARIANT('a\"b'))",
                "\"a\\\"b\""},
            {"SELECT jfs795(TO_VARIANT(1.0::FLOAT))",
                "1.000000000000000e+00"},
            {"SELECT jfs795(TO_VARIANT('inf'::FLOAT))",
                "\"Infinity\""},
            {"SELECT jfs795(TO_VARIANT('-inf'::FLOAT))",
                "\"/Infinity\""},
            {"SELECT jfs795(PARSE_JSON('1e5'))",
                "1.000000000000000e+05"},
            {"SELECT jfs795(PARSE_JSON('{\"b\":1,\"a\":2}'))",
                "{\"a\":2,\"b\":1}"},
            {"SELECT jfs795(TO_VARIANT(1e100::FLOAT))",
                "1.000000000000000e+100"},
            {"SELECT jfs795(TO_VARIANT(0.1::FLOAT))",
                "1.000000000000000e/01"},
            {"SELECT jfs795(PARSE_JSON('{\"k\":\"v\\\\u0001\"}'))",
                "{\"k\":\"v\\u0001\"}"},
            {"SELECT jfs795(TO_VARIANT(12.50::NUMBER(10, 2)))",
                "12.5"},
            {"SELECT jfs795(TO_VARIANT('ab\\tc'))",
                "\"ab\\tc\""},
            {"SELECT jfs795(ARRAY_CONSTRUCT('x', 1))",
                "[\"x\",1]"},
            {"SELECT jfs795(TO_VARIANT('2024-01-02'::DATE))",
                "\"2024/01/02\""},
            {"SELECT jfa795(ARRAY_CONSTRUCT(TO_BINARY('414243', 'HEX')))",
                "[ABC]"},
            {"SELECT jfa795(ARRAY_CONSTRUCT(TO_BINARY('E282AC', 'HEX')))",
                "[€]"},
            {"SELECT jfa795(ARRAY_CONSTRUCT(TO_BINARY('ABCD', 'HEX'), TO_BINARY('00', 'HEX')))",
                "[��][ ]"},
            {"SELECT jfa795(ARRAY_CONSTRUCT('2024-01-02 10:11:12 +01:00'::TIMESTAMP_TZ, '10:11:12.5'::TIME))",
                "[2024/01/02 10:11:12.000 +0100][10:11:12]"},
            {"SELECT jfa795(ARRAY_CONSTRUCT('a\"b', 'é', 1.0::FLOAT, 'NaN'::FLOAT))",
                "[a\"b][é][1][NaN]"},
            {"SELECT jfa795(ARRAY_CONSTRUCT(PARSE_JSON('null'), NULL, 'x'))",
                "[<null>][<null>][x]"},
            {"SELECT jfo795(OBJECT_CONSTRUCT('t', '10:11:12.5'::TIME))",
                "t=10:11:12;"},
            {"SELECT jfo795(OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(1, 'x')))",
                "a=[1,\"x\"];"},
            {"SELECT jfo795(OBJECT_CONSTRUCT('z', '2024-01-02 10:11:12 +01:00'::TIMESTAMP_TZ))",
                "z=2024/01/02 10:11:12.000 +0100;"},
            {"SELECT jfo795(OBJECT_CONSTRUCT('l', '2024-01-02 10:11:12'::TIMESTAMP_LTZ))",
                "l=2024/01/02 10:11:12.000 Z;"},
            {"SELECT jfo795(OBJECT_CONSTRUCT('f', 'NaN'::FLOAT, 'g', 1.0::FLOAT))",
                "f=NaN;g=1;"},
            {"SELECT jfo795(OBJECT_CONSTRUCT_KEEP_NULL('j', PARSE_JSON('null')))",
                "j=<null>;"},
            {"SELECT jts795('2024-01-02 10:11:12.123456789'::TIMESTAMP_NTZ(9), '10:11:12.9876'::TIME(4))",
                "123000000|987"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }

    /** A procedure's VARIANT as its String, timestamps at their precision, double elements. */
    @Test
    public void procedureTexts() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE jps796(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, String v) { return "
                + "v == null ? \"<null>\" : v.replace('-', '/'); }\n}\n$$",
                "Function JPS796 successfully created."},
            {"CALL jps796(TO_VARIANT(1.0::FLOAT))",
                "1.000000000000000e+00"},
            {"CALL jps796(TO_VARIANT('x'))",
                "\"x\""},
            {"CALL jps796(TO_VARIANT('2024-01-02'::DATE))",
                "\"2024/01/02\""},
            {"CALL jps796(TO_VARIANT('inf'::FLOAT))",
                "\"Infinity\""},
            {"CALL jps796(PARSE_JSON('[1.5e0, \"a\"]'))",
                "[1.500000000000000e+00,\"a\"]"},
            {"CREATE OR REPLACE PROCEDURE jpt796(t TIME(3), m TIMESTAMP_NTZ(3)) RETURNS VARCHAR LANGUAGE JAVA "
                + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, java.sql.Time t, "
                + "java.sql.Timestamp m) { return (t.getTime() % 1000) + \"|\" + m.getNanos(); }\n}\n$$",
                "Function JPT796 successfully created."},
            {"CALL jpt796('10:11:12.9876'::TIME(4), '2024-01-02 10:11:12.123456789'::TIMESTAMP_NTZ(9))",
                "987|123000000"},
            {"CREATE OR REPLACE FUNCTION sqt796(t TIME(3)) RETURNS VARCHAR AS 't::VARCHAR'",
                "Function SQT796 successfully created."},
            {"SELECT sqt796('10:11:12.9876'::TIME(4))",
                "10:11:12"},
            {"CREATE OR REPLACE FUNCTION jfa796(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER "
                + "= 'H.f' AS $$\nclass H {\n  public static String f(String[] a) {\n    StringBuilder b = new "
                + "StringBuilder();\n    for (String x : a) b.append('[').append(x == null ? \"<null>\" : "
                + "x).append(']');\n    return b.toString().replace('-', '/');\n  }\n}\n$$",
                "Function JFA796 successfully created."},
            {"SELECT jfa796(PARSE_JSON('[1e5, 1e100, 0.1, -0.0, 1.5e300, 123456789012345678]'))",
                "[100000][1e+100][0.1][0][1.5e+300][123456789012345678]"},
            {"SELECT jfa796(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1.0::FLOAT), OBJECT_CONSTRUCT('k', 2.5::FLOAT)))",
                "[[1.000000000000000e+00]][{\"k\":2.500000000000000e+00}]"},
            {"CREATE OR REPLACE FUNCTION jfo796(o OBJECT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER "
                + "= 'H.f' AS $$\nclass H {\n  public static String f(java.util.Map<String, String> o) {\n    return "
                + "new java.util.TreeMap<String, String>(o).toString().replace('-', '/');\n  }\n}\n$$",
                "Function JFO796 successfully created."},
            {"SELECT jfo796(OBJECT_CONSTRUCT('a', 1.0::FLOAT, 'b', 1e100::FLOAT, 'c', PARSE_JSON('1e5'), 'd', TRUE))",
                "{a=1, b=1e+100, c=100000, d=true}"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }
}
