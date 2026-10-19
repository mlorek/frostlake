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
 * A Snowpark Row, as a Java handler reads one. A cell arrives in the class Snowpark hands over for its type — a
 * NUMBER of no scale a Long, one with a scale a BigDecimal, a FLOAT a Double, a DATE, a TIME and a TIMESTAMP the
 * java.sql classes, a BINARY its bytes, a VARIANT, an ARRAY and an OBJECT their JSON text — and an accessor reads
 * it by a cast: getString over a number, getBoolean over a text and getDecimal over a whole number fail the
 * handler with the cast's own exception. getInt, getLong, getShort and getByte read a whole number their type
 * can hold, getDouble and getFloat a whole number or a FLOAT, and any other cell is "Cannot cast …"; a NULL is
 * null to getString and no value to the rest (live-verified).
 */
public class SnowparkRowAccessorTest extends MeasuredCellsTestSupport {

    @Test
    public void textAndWholeNumberAccessors() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE java_cell(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
                    + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                    + "String q, String how) {\n    Row r = s.sql(q).collect()[0];\n    try {\n      switch (how) "
                    + "{\n        case \"getString\": return \"S:\" + r.getString(0);\n        case \"getInt\": "
                    + "return \"I:\" + r.getInt(0);\n        case \"getLong\": return \"L:\" + r.getLong(0);\n       "
                    + " case \"getShort\": return \"H:\" + r.getShort(0);\n        case \"getByte\": return \"Y:\" + "
                    + "r.getByte(0);\n        case \"getDouble\": return \"D:\" + r.getDouble(0);\n        case "
                    + "\"getFloat\": return \"F:\" + r.getFloat(0);\n        case \"getBoolean\": return \"B:\" + "
                    + "r.getBoolean(0);\n        case \"getDecimal\": return \"N:\" + r.getDecimal(0);\n        case "
                    + "\"getDate\": return \"DT:\" + r.getDate(0);\n        case \"getTime\": return \"TM:\" + "
                    + "r.getTime(0);\n        case \"getTimestamp\": return \"TS:\" + r.getTimestamp(0);\n        "
                    + "case \"getBinary\": return \"X:\" + java.util.Arrays.toString(r.getBinary(0));\n        case "
                    + "\"getVariant\": return \"V:\" + r.getVariant(0);\n        case \"get\": Object o = r.get(0); "
                    + "return \"O:\" + (o == null ? \"null\" : o.getClass().getName() + \"=\" + o);\n        case "
                    + "\"isNullAt\": return \"Z:\" + r.isNullAt(0);\n        default: return \"?\";\n      }\n    } "
                    + "catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + "
                    + "t.getMessage();\n    }\n  }\n}\n$$");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT 1', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class "
                    + "java.lang.String (java.math.BigDecimal and java.lang.String are in module java.base of loader "
                    + "'bootstrap')",
                "CALL java_cell('SELECT 1.5', 'getString')");
            assertAnswer(
                "S:a",
                "CALL java_cell('SELECT ''a''', 'getString')");
            assertAnswer(
                "S:12",
                "CALL java_cell('SELECT ''12''', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Boolean cannot be cast to class java.lang.String "
                    + "(java.lang.Boolean and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT TRUE', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Date cannot be cast to class java.lang.String "
                    + "(java.sql.Date is in module java.sql of loader 'platform'; java.lang.String is in module "
                    + "java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Time cannot be cast to class java.lang.String "
                    + "(java.sql.Time is in module java.sql of loader 'platform'; java.lang.String is in module "
                    + "java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Timestamp cannot be cast to class "
                    + "java.lang.String (java.sql.Timestamp is in module java.sql of loader 'platform'; "
                    + "java.lang.String is in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getString')");
            assertAnswer(
                "S:[65]",
                "CALL java_cell('SELECT TO_BINARY(''41'', ''HEX'')', 'getString')");
            assertAnswer(
                "S:{\"a\":1}",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class java.lang.String "
                    + "(java.lang.Double and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getString')");
            assertAnswer(
                "S:null",
                "CALL java_cell('SELECT NULL', 'getString')");
            assertAnswer(
                "I:1",
                "CALL java_cell('SELECT 1', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Int.",
                "CALL java_cell('SELECT 1.5', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(a) to Int.",
                "CALL java_cell('SELECT ''a''', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(12) to Int.",
                "CALL java_cell('SELECT ''12''', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Boolean(true) to Int.",
                "CALL java_cell('SELECT TRUE', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Date(2024-01-02) to Int.",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Time(10:20:30) to Int.",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getInt')");
            answer(
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Int.",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Double(1.5) to Int.",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getInt')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell('SELECT NULL', 'getInt')");
            assertAnswer(
                "L:1",
                "CALL java_cell('SELECT 1', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Long.",
                "CALL java_cell('SELECT 1.5', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(a) to Long.",
                "CALL java_cell('SELECT ''a''', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(12) to Long.",
                "CALL java_cell('SELECT ''12''', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Boolean(true) to Long.",
                "CALL java_cell('SELECT TRUE', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Date(2024-01-02) to Long.",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Time(10:20:30) to Long.",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getLong')");
            answer(
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Long.",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Double(1.5) to Long.",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getLong')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell('SELECT NULL', 'getLong')");
            assertAnswer(
                "H:1",
                "CALL java_cell('SELECT 1', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Short.",
                "CALL java_cell('SELECT 1.5', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(a) to Short.",
                "CALL java_cell('SELECT ''a''', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(12) to Short.",
                "CALL java_cell('SELECT ''12''', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Boolean(true) to Short.",
                "CALL java_cell('SELECT TRUE', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Date(2024-01-02) to Short.",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Time(10:20:30) to Short.",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getShort')");
            answer(
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Short.",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Double(1.5) to Short.",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getShort')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell('SELECT NULL', 'getShort')");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS java_cell(VARCHAR, VARCHAR)");
        }
    }

    @Test
    public void byteRealAndBooleanAccessors() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE java_cell(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
                    + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                    + "String q, String how) {\n    Row r = s.sql(q).collect()[0];\n    try {\n      switch (how) "
                    + "{\n        case \"getString\": return \"S:\" + r.getString(0);\n        case \"getInt\": "
                    + "return \"I:\" + r.getInt(0);\n        case \"getLong\": return \"L:\" + r.getLong(0);\n       "
                    + " case \"getShort\": return \"H:\" + r.getShort(0);\n        case \"getByte\": return \"Y:\" + "
                    + "r.getByte(0);\n        case \"getDouble\": return \"D:\" + r.getDouble(0);\n        case "
                    + "\"getFloat\": return \"F:\" + r.getFloat(0);\n        case \"getBoolean\": return \"B:\" + "
                    + "r.getBoolean(0);\n        case \"getDecimal\": return \"N:\" + r.getDecimal(0);\n        case "
                    + "\"getDate\": return \"DT:\" + r.getDate(0);\n        case \"getTime\": return \"TM:\" + "
                    + "r.getTime(0);\n        case \"getTimestamp\": return \"TS:\" + r.getTimestamp(0);\n        "
                    + "case \"getBinary\": return \"X:\" + java.util.Arrays.toString(r.getBinary(0));\n        case "
                    + "\"getVariant\": return \"V:\" + r.getVariant(0);\n        case \"get\": Object o = r.get(0); "
                    + "return \"O:\" + (o == null ? \"null\" : o.getClass().getName() + \"=\" + o);\n        case "
                    + "\"isNullAt\": return \"Z:\" + r.isNullAt(0);\n        default: return \"?\";\n      }\n    } "
                    + "catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + "
                    + "t.getMessage();\n    }\n  }\n}\n$$");
            assertAnswer(
                "Y:1",
                "CALL java_cell('SELECT 1', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Byte.",
                "CALL java_cell('SELECT 1.5', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(a) to Byte.",
                "CALL java_cell('SELECT ''a''', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(12) to Byte.",
                "CALL java_cell('SELECT ''12''', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Boolean(true) to Byte.",
                "CALL java_cell('SELECT TRUE', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Date(2024-01-02) to Byte.",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Time(10:20:30) to Byte.",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getByte')");
            answer(
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Byte.",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Double(1.5) to Byte.",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getByte')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell('SELECT NULL', 'getByte')");
            assertAnswer(
                "D:1.0",
                "CALL java_cell('SELECT 1', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Double.",
                "CALL java_cell('SELECT 1.5', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(a) to Double.",
                "CALL java_cell('SELECT ''a''', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(12) to Double.",
                "CALL java_cell('SELECT ''12''', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Boolean(true) to Double.",
                "CALL java_cell('SELECT TRUE', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Date(2024-01-02) to Double.",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Time(10:20:30) to Double.",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getDouble')");
            answer(
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Double.",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getDouble')");
            assertAnswer(
                "D:1.5",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getDouble')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell('SELECT NULL', 'getDouble')");
            assertAnswer(
                "F:1.0",
                "CALL java_cell('SELECT 1', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Float.",
                "CALL java_cell('SELECT 1.5', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(a) to Float.",
                "CALL java_cell('SELECT ''a''', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(12) to Float.",
                "CALL java_cell('SELECT ''12''', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Boolean(true) to Float.",
                "CALL java_cell('SELECT TRUE', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Date(2024-01-02) to Float.",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.sql.Time(10:20:30) to Float.",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getFloat')");
            answer(
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Float.",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getFloat')");
            assertAnswer(
                "F:1.5",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getFloat')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell('SELECT NULL', 'getFloat')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.Boolean "
                    + "(java.lang.Long and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT 1', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class "
                    + "java.lang.Boolean (java.math.BigDecimal and java.lang.Boolean are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell('SELECT 1.5', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Boolean "
                    + "(java.lang.String and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''a''', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Boolean "
                    + "(java.lang.String and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''12''', 'getBoolean')");
            assertAnswer(
                "B:true",
                "CALL java_cell('SELECT TRUE', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Date cannot be cast to class java.lang.Boolean "
                    + "(java.sql.Date is in module java.sql of loader 'platform'; java.lang.Boolean is in module "
                    + "java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Time cannot be cast to class java.lang.Boolean "
                    + "(java.sql.Time is in module java.sql of loader 'platform'; java.lang.Boolean is in module "
                    + "java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Timestamp cannot be cast to class "
                    + "java.lang.Boolean (java.sql.Timestamp is in module java.sql of loader 'platform'; "
                    + "java.lang.Boolean is in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class [B cannot be cast to class java.lang.Boolean ([B and "
                    + "java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT TO_BINARY(''41'', ''HEX'')', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Boolean "
                    + "(java.lang.String and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class java.lang.Boolean "
                    + "(java.lang.Double and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getBoolean')");
            assertAnswer(
                "E:java.lang.NullPointerException: Value at index 0 is null",
                "CALL java_cell('SELECT NULL', 'getBoolean')");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS java_cell(VARCHAR, VARCHAR)");
        }
    }

    @Test
    public void decimalAndTemporalAccessors() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE java_cell(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
                    + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                    + "String q, String how) {\n    Row r = s.sql(q).collect()[0];\n    try {\n      switch (how) "
                    + "{\n        case \"getString\": return \"S:\" + r.getString(0);\n        case \"getInt\": "
                    + "return \"I:\" + r.getInt(0);\n        case \"getLong\": return \"L:\" + r.getLong(0);\n       "
                    + " case \"getShort\": return \"H:\" + r.getShort(0);\n        case \"getByte\": return \"Y:\" + "
                    + "r.getByte(0);\n        case \"getDouble\": return \"D:\" + r.getDouble(0);\n        case "
                    + "\"getFloat\": return \"F:\" + r.getFloat(0);\n        case \"getBoolean\": return \"B:\" + "
                    + "r.getBoolean(0);\n        case \"getDecimal\": return \"N:\" + r.getDecimal(0);\n        case "
                    + "\"getDate\": return \"DT:\" + r.getDate(0);\n        case \"getTime\": return \"TM:\" + "
                    + "r.getTime(0);\n        case \"getTimestamp\": return \"TS:\" + r.getTimestamp(0);\n        "
                    + "case \"getBinary\": return \"X:\" + java.util.Arrays.toString(r.getBinary(0));\n        case "
                    + "\"getVariant\": return \"V:\" + r.getVariant(0);\n        case \"get\": Object o = r.get(0); "
                    + "return \"O:\" + (o == null ? \"null\" : o.getClass().getName() + \"=\" + o);\n        case "
                    + "\"isNullAt\": return \"Z:\" + r.isNullAt(0);\n        default: return \"?\";\n      }\n    } "
                    + "catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + "
                    + "t.getMessage();\n    }\n  }\n}\n$$");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Long and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell('SELECT 1', 'getDecimal')");
            assertAnswer(
                "N:1.5",
                "CALL java_cell('SELECT 1.5', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.String and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell('SELECT ''a''', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.String and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell('SELECT ''12''', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Boolean cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Boolean and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell('SELECT TRUE', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Date cannot be cast to class java.math.BigDecimal "
                    + "(java.sql.Date is in module java.sql of loader 'platform'; java.math.BigDecimal is in module "
                    + "java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Time cannot be cast to class java.math.BigDecimal "
                    + "(java.sql.Time is in module java.sql of loader 'platform'; java.math.BigDecimal is in module "
                    + "java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Timestamp cannot be cast to class "
                    + "java.math.BigDecimal (java.sql.Timestamp is in module java.sql of loader 'platform'; "
                    + "java.math.BigDecimal is in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class [B cannot be cast to class java.math.BigDecimal ([B and "
                    + "java.math.BigDecimal are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT TO_BINARY(''41'', ''HEX'')', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.String and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Double and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getDecimal')");
            assertAnswer(
                "N:null",
                "CALL java_cell('SELECT NULL', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.sql.Date "
                    + "(java.lang.Long is in module java.base of loader 'bootstrap'; java.sql.Date is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT 1', 'getDate')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class java.sql.Date "
                    + "(java.math.BigDecimal is in module java.base of loader 'bootstrap'; java.sql.Date is in "
                    + "module java.sql of loader 'platform')",
                "CALL java_cell('SELECT 1.5', 'getDate')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.sql.Date "
                    + "(java.lang.String is in module java.base of loader 'bootstrap'; java.sql.Date is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''a''', 'getDate')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.sql.Date "
                    + "(java.lang.String is in module java.base of loader 'bootstrap'; java.sql.Date is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''12''', 'getDate')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Boolean cannot be cast to class java.sql.Date "
                    + "(java.lang.Boolean is in module java.base of loader 'bootstrap'; java.sql.Date is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT TRUE', 'getDate')");
            assertAnswer(
                "DT:2024-01-02",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getDate')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Time cannot be cast to class java.sql.Date "
                    + "(java.sql.Time and java.sql.Date are in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getDate')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Timestamp cannot be cast to class java.sql.Date "
                    + "(java.sql.Timestamp and java.sql.Date are in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getDate')");
            assertAnswer(
                "E:java.lang.ClassCastException: class [B cannot be cast to class java.sql.Date ([B is in module "
                    + "java.base of loader 'bootstrap'; java.sql.Date is in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT TO_BINARY(''41'', ''HEX'')', 'getDate')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.sql.Date "
                    + "(java.lang.String is in module java.base of loader 'bootstrap'; java.sql.Date is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getDate')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class java.sql.Date "
                    + "(java.lang.Double is in module java.base of loader 'bootstrap'; java.sql.Date is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getDate')");
            assertAnswer(
                "DT:null",
                "CALL java_cell('SELECT NULL', 'getDate')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.sql.Time "
                    + "(java.lang.Long is in module java.base of loader 'bootstrap'; java.sql.Time is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT 1', 'getTime')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class java.sql.Time "
                    + "(java.math.BigDecimal is in module java.base of loader 'bootstrap'; java.sql.Time is in "
                    + "module java.sql of loader 'platform')",
                "CALL java_cell('SELECT 1.5', 'getTime')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.sql.Time "
                    + "(java.lang.String is in module java.base of loader 'bootstrap'; java.sql.Time is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''a''', 'getTime')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.sql.Time "
                    + "(java.lang.String is in module java.base of loader 'bootstrap'; java.sql.Time is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''12''', 'getTime')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Boolean cannot be cast to class java.sql.Time "
                    + "(java.lang.Boolean is in module java.base of loader 'bootstrap'; java.sql.Time is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT TRUE', 'getTime')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Date cannot be cast to class java.sql.Time "
                    + "(java.sql.Date and java.sql.Time are in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getTime')");
            assertAnswer(
                "TM:10:20:30",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getTime')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Timestamp cannot be cast to class java.sql.Time "
                    + "(java.sql.Timestamp and java.sql.Time are in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getTime')");
            assertAnswer(
                "E:java.lang.ClassCastException: class [B cannot be cast to class java.sql.Time ([B is in module "
                    + "java.base of loader 'bootstrap'; java.sql.Time is in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT TO_BINARY(''41'', ''HEX'')', 'getTime')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.sql.Time "
                    + "(java.lang.String is in module java.base of loader 'bootstrap'; java.sql.Time is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getTime')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class java.sql.Time "
                    + "(java.lang.Double is in module java.base of loader 'bootstrap'; java.sql.Time is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getTime')");
            assertAnswer(
                "TM:null",
                "CALL java_cell('SELECT NULL', 'getTime')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.sql.Timestamp "
                    + "(java.lang.Long is in module java.base of loader 'bootstrap'; java.sql.Timestamp is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell('SELECT 1', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class "
                    + "java.sql.Timestamp (java.math.BigDecimal is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT 1.5', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.sql.Timestamp (java.lang.String is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''a''', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.sql.Timestamp (java.lang.String is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''12''', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Boolean cannot be cast to class "
                    + "java.sql.Timestamp (java.lang.Boolean is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT TRUE', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Date cannot be cast to class java.sql.Timestamp "
                    + "(java.sql.Date and java.sql.Timestamp are in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Time cannot be cast to class java.sql.Timestamp "
                    + "(java.sql.Time and java.sql.Timestamp are in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getTimestamp')");
            answer(
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class [B cannot be cast to class java.sql.Timestamp ([B is in "
                    + "module java.base of loader 'bootstrap'; java.sql.Timestamp is in module java.sql of loader "
                    + "'platform')",
                "CALL java_cell('SELECT TO_BINARY(''41'', ''HEX'')', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.sql.Timestamp (java.lang.String is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class "
                    + "java.sql.Timestamp (java.lang.Double is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getTimestamp')");
            assertAnswer(
                "TS:null",
                "CALL java_cell('SELECT NULL', 'getTimestamp')");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS java_cell(VARCHAR, VARCHAR)");
        }
    }

    @Test
    public void binaryVariantAndUntypedAccess() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE java_cell(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
                    + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                    + "String q, String how) {\n    Row r = s.sql(q).collect()[0];\n    try {\n      switch (how) "
                    + "{\n        case \"getString\": return \"S:\" + r.getString(0);\n        case \"getInt\": "
                    + "return \"I:\" + r.getInt(0);\n        case \"getLong\": return \"L:\" + r.getLong(0);\n       "
                    + " case \"getShort\": return \"H:\" + r.getShort(0);\n        case \"getByte\": return \"Y:\" + "
                    + "r.getByte(0);\n        case \"getDouble\": return \"D:\" + r.getDouble(0);\n        case "
                    + "\"getFloat\": return \"F:\" + r.getFloat(0);\n        case \"getBoolean\": return \"B:\" + "
                    + "r.getBoolean(0);\n        case \"getDecimal\": return \"N:\" + r.getDecimal(0);\n        case "
                    + "\"getDate\": return \"DT:\" + r.getDate(0);\n        case \"getTime\": return \"TM:\" + "
                    + "r.getTime(0);\n        case \"getTimestamp\": return \"TS:\" + r.getTimestamp(0);\n        "
                    + "case \"getBinary\": return \"X:\" + java.util.Arrays.toString(r.getBinary(0));\n        case "
                    + "\"getVariant\": return \"V:\" + r.getVariant(0);\n        case \"get\": Object o = r.get(0); "
                    + "return \"O:\" + (o == null ? \"null\" : o.getClass().getName() + \"=\" + o);\n        case "
                    + "\"isNullAt\": return \"Z:\" + r.isNullAt(0);\n        default: return \"?\";\n      }\n    } "
                    + "catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + "
                    + "t.getMessage();\n    }\n  }\n}\n$$");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class [B (java.lang.Long "
                    + "and [B are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT 1', 'getBinary')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class [B "
                    + "(java.math.BigDecimal and [B are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT 1.5', 'getBinary')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class [B "
                    + "(java.lang.String and [B are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''a''', 'getBinary')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class [B "
                    + "(java.lang.String and [B are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''12''', 'getBinary')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Boolean cannot be cast to class [B "
                    + "(java.lang.Boolean and [B are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT TRUE', 'getBinary')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Date cannot be cast to class [B (java.sql.Date is "
                    + "in module java.sql of loader 'platform'; [B is in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getBinary')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Time cannot be cast to class [B (java.sql.Time is "
                    + "in module java.sql of loader 'platform'; [B is in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getBinary')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Timestamp cannot be cast to class [B "
                    + "(java.sql.Timestamp is in module java.sql of loader 'platform'; [B is in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getBinary')");
            assertAnswer(
                "X:[65]",
                "CALL java_cell('SELECT TO_BINARY(''41'', ''HEX'')', 'getBinary')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class [B "
                    + "(java.lang.String and [B are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getBinary')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class [B "
                    + "(java.lang.Double and [B are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getBinary')");
            assertAnswer(
                "X:null",
                "CALL java_cell('SELECT NULL', 'getBinary')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT 1', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class "
                    + "java.lang.String (java.math.BigDecimal and java.lang.String are in module java.base of loader "
                    + "'bootstrap')",
                "CALL java_cell('SELECT 1.5', 'getVariant')");
            assertAnswer(
                "V:a",
                "CALL java_cell('SELECT ''a''', 'getVariant')");
            assertAnswer(
                "V:12",
                "CALL java_cell('SELECT ''12''', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Boolean cannot be cast to class java.lang.String "
                    + "(java.lang.Boolean and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT TRUE', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Date cannot be cast to class java.lang.String "
                    + "(java.sql.Date is in module java.sql of loader 'platform'; java.lang.String is in module "
                    + "java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Time cannot be cast to class java.lang.String "
                    + "(java.sql.Time is in module java.sql of loader 'platform'; java.lang.String is in module "
                    + "java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.sql.Timestamp cannot be cast to class "
                    + "java.lang.String (java.sql.Timestamp is in module java.sql of loader 'platform'; "
                    + "java.lang.String is in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'getVariant')");
            assertAnswer(
                "V:[65]",
                "CALL java_cell('SELECT TO_BINARY(''41'', ''HEX'')', 'getVariant')");
            assertAnswer(
                "V:{\"a\":1}",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class java.lang.String "
                    + "(java.lang.Double and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell('SELECT 1.5::FLOAT', 'getVariant')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell('SELECT NULL', 'getVariant')");
            assertAnswer(
                "O:java.lang.Long=1",
                "CALL java_cell('SELECT 1', 'get')");
            assertAnswer(
                "O:java.math.BigDecimal=1.5",
                "CALL java_cell('SELECT 1.5', 'get')");
            assertAnswer(
                "O:java.lang.String=a",
                "CALL java_cell('SELECT ''a''', 'get')");
            assertAnswer(
                "O:java.lang.String=12",
                "CALL java_cell('SELECT ''12''', 'get')");
            assertAnswer(
                "O:java.lang.Boolean=true",
                "CALL java_cell('SELECT TRUE', 'get')");
            assertAnswer(
                "O:java.sql.Date=2024-01-02",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'get')");
            assertAnswer(
                "O:java.sql.Time=10:20:30",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'get')");
            answer(
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'get')");
            assertAnswer(
                "O:java.lang.String={\"a\":1}",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'get')");
            assertAnswer(
                "O:java.lang.Double=1.5",
                "CALL java_cell('SELECT 1.5::FLOAT', 'get')");
            assertAnswer(
                "O:null",
                "CALL java_cell('SELECT NULL', 'get')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT 1', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT 1.5', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT ''a''', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT ''12''', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT TRUE', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT ''2024-01-02''::DATE', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT ''10:20:30''::TIME', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT TO_BINARY(''41'', ''HEX'')', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT PARSE_JSON(''{\"a\":1}'')', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell('SELECT 1.5::FLOAT', 'isNullAt')");
            assertAnswer(
                "Z:true",
                "CALL java_cell('SELECT NULL', 'isNullAt')");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS java_cell(VARCHAR, VARCHAR)");
        }
    }

    @Test
    public void wholeNumbersTheirTypeCannotHold() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE java_cell2(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
                    + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                    + "String q, String how) {\n    Row r = s.sql(q).collect()[0];\n    try {\n      switch (how) "
                    + "{\n        case \"getString\": return \"S:\" + r.getString(0);\n        case \"getInt\": "
                    + "return \"I:\" + r.getInt(0);\n        case \"getLong\": return \"L:\" + r.getLong(0);\n       "
                    + " case \"getShort\": return \"H:\" + r.getShort(0);\n        case \"getByte\": return \"Y:\" + "
                    + "r.getByte(0);\n        case \"getDouble\": return \"D:\" + r.getDouble(0);\n        case "
                    + "\"getFloat\": return \"F:\" + r.getFloat(0);\n        case \"getBoolean\": return \"B:\" + "
                    + "r.getBoolean(0);\n        case \"getDecimal\": return \"N:\" + r.getDecimal(0);\n        case "
                    + "\"getTimestamp\": return \"TS:\" + (r.getTimestamp(0) == null ? \"null\" : \"\" + "
                    + "r.getTimestamp(0).getTime());\n        case \"getVariant\": return \"V:\" + "
                    + "r.getVariant(0);\n        case \"get\": Object o = r.get(0); return \"O:\" + (o == null ? "
                    + "\"null\" : o.getClass().getName() + \"=\" + (o instanceof java.sql.Timestamp ? \"\" + "
                    + "((java.sql.Timestamp) o).getTime() : \"\" + o));\n        case \"isNullAt\": return \"Z:\" + "
                    + "r.isNullAt(0);\n        case \"size\": return \"N:\" + r.size();\n        case \"toString\": "
                    + "return \"T:\" + r.toString();\n        default: return \"?\";\n      }\n    } catch "
                    + "(Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    "
                    + "}\n  }\n}\n$$");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 1.00', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 3000000000', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 300', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 70000', 'getString')");
            assertAnswer(
                "S:[1,2]",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getString')");
            assertAnswer(
                "S:{\"a\":1}",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class java.lang.String "
                    + "(java.lang.Double and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 2::FLOAT', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class "
                    + "java.lang.String (java.math.BigDecimal and java.lang.String are in module java.base of loader "
                    + "'bootstrap')",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getString')");
            assertAnswer(
                "S:null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getString')");
            assertAnswer(
                "S:\"1\"",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 123456789012345678', 'getString')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT -1', 'getString')");
            assertAnswer(
                "I:1",
                "CALL java_cell2('SELECT 1.00', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Long(3000000000) to Int.",
                "CALL java_cell2('SELECT 3000000000', 'getInt')");
            assertAnswer(
                "I:300",
                "CALL java_cell2('SELECT 300', 'getInt')");
            assertAnswer(
                "I:70000",
                "CALL java_cell2('SELECT 70000', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String([1,2]) to Int.",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Int.",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Double(2.0) to Int.",
                "CALL java_cell2('SELECT 2::FLOAT', 'getInt')");
            assertAnswer(
                "I:1",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Int.",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getInt')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(\"1\") to Int.",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getInt')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Long(123456789012345678) to Int.",
                "CALL java_cell2('SELECT 123456789012345678', 'getInt')");
            assertAnswer(
                "I:-1",
                "CALL java_cell2('SELECT -1', 'getInt')");
            assertAnswer(
                "L:1",
                "CALL java_cell2('SELECT 1.00', 'getLong')");
            assertAnswer(
                "L:3000000000",
                "CALL java_cell2('SELECT 3000000000', 'getLong')");
            assertAnswer(
                "L:300",
                "CALL java_cell2('SELECT 300', 'getLong')");
            assertAnswer(
                "L:70000",
                "CALL java_cell2('SELECT 70000', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String([1,2]) to Long.",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Long.",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Double(2.0) to Long.",
                "CALL java_cell2('SELECT 2::FLOAT', 'getLong')");
            assertAnswer(
                "L:1",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Long.",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getLong')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getLong')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(\"1\") to Long.",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getLong')");
            assertAnswer(
                "L:123456789012345678",
                "CALL java_cell2('SELECT 123456789012345678', 'getLong')");
            assertAnswer(
                "L:-1",
                "CALL java_cell2('SELECT -1', 'getLong')");
            assertAnswer(
                "H:1",
                "CALL java_cell2('SELECT 1.00', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Long(3000000000) to Short.",
                "CALL java_cell2('SELECT 3000000000', 'getShort')");
            assertAnswer(
                "H:300",
                "CALL java_cell2('SELECT 300', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Long(70000) to Short.",
                "CALL java_cell2('SELECT 70000', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String([1,2]) to Short.",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Short.",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Double(2.0) to Short.",
                "CALL java_cell2('SELECT 2::FLOAT', 'getShort')");
            assertAnswer(
                "H:1",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Short.",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getShort')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(\"1\") to Short.",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getShort')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Long(123456789012345678) to Short.",
                "CALL java_cell2('SELECT 123456789012345678', 'getShort')");
            assertAnswer(
                "H:-1",
                "CALL java_cell2('SELECT -1', 'getShort')");
            assertAnswer(
                "Y:1",
                "CALL java_cell2('SELECT 1.00', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Long(3000000000) to Byte.",
                "CALL java_cell2('SELECT 3000000000', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Long(300) to Byte.",
                "CALL java_cell2('SELECT 300', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Long(70000) to Byte.",
                "CALL java_cell2('SELECT 70000', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String([1,2]) to Byte.",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Byte.",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Double(2.0) to Byte.",
                "CALL java_cell2('SELECT 2::FLOAT', 'getByte')");
            assertAnswer(
                "Y:1",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Byte.",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getByte')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(\"1\") to Byte.",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getByte')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.Long(123456789012345678) to Byte.",
                "CALL java_cell2('SELECT 123456789012345678', 'getByte')");
            assertAnswer(
                "Y:-1",
                "CALL java_cell2('SELECT -1', 'getByte')");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS java_cell2(VARCHAR, VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS java_has(VARCHAR)");
        }
    }

    @Test
    public void realNumbersDecimalsAndZonedTimestamps() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE java_cell2(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
                    + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                    + "String q, String how) {\n    Row r = s.sql(q).collect()[0];\n    try {\n      switch (how) "
                    + "{\n        case \"getString\": return \"S:\" + r.getString(0);\n        case \"getInt\": "
                    + "return \"I:\" + r.getInt(0);\n        case \"getLong\": return \"L:\" + r.getLong(0);\n       "
                    + " case \"getShort\": return \"H:\" + r.getShort(0);\n        case \"getByte\": return \"Y:\" + "
                    + "r.getByte(0);\n        case \"getDouble\": return \"D:\" + r.getDouble(0);\n        case "
                    + "\"getFloat\": return \"F:\" + r.getFloat(0);\n        case \"getBoolean\": return \"B:\" + "
                    + "r.getBoolean(0);\n        case \"getDecimal\": return \"N:\" + r.getDecimal(0);\n        case "
                    + "\"getTimestamp\": return \"TS:\" + (r.getTimestamp(0) == null ? \"null\" : \"\" + "
                    + "r.getTimestamp(0).getTime());\n        case \"getVariant\": return \"V:\" + "
                    + "r.getVariant(0);\n        case \"get\": Object o = r.get(0); return \"O:\" + (o == null ? "
                    + "\"null\" : o.getClass().getName() + \"=\" + (o instanceof java.sql.Timestamp ? \"\" + "
                    + "((java.sql.Timestamp) o).getTime() : \"\" + o));\n        case \"isNullAt\": return \"Z:\" + "
                    + "r.isNullAt(0);\n        case \"size\": return \"N:\" + r.size();\n        case \"toString\": "
                    + "return \"T:\" + r.toString();\n        default: return \"?\";\n      }\n    } catch "
                    + "(Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    "
                    + "}\n  }\n}\n$$");
            assertAnswer(
                "D:1.0",
                "CALL java_cell2('SELECT 1.00', 'getDouble')");
            assertAnswer(
                "D:3.0E9",
                "CALL java_cell2('SELECT 3000000000', 'getDouble')");
            assertAnswer(
                "D:300.0",
                "CALL java_cell2('SELECT 300', 'getDouble')");
            assertAnswer(
                "D:70000.0",
                "CALL java_cell2('SELECT 70000', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String([1,2]) to Double.",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Double.",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getDouble')");
            assertAnswer(
                "D:2.0",
                "CALL java_cell2('SELECT 2::FLOAT', 'getDouble')");
            assertAnswer(
                "D:1.0",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Double.",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getDouble')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getDouble')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(\"1\") to Double.",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getDouble')");
            assertAnswer(
                "D:1.2345678901234568E17",
                "CALL java_cell2('SELECT 123456789012345678', 'getDouble')");
            assertAnswer(
                "D:-1.0",
                "CALL java_cell2('SELECT -1', 'getDouble')");
            assertAnswer(
                "F:1.0",
                "CALL java_cell2('SELECT 1.00', 'getFloat')");
            assertAnswer(
                "F:3.0E9",
                "CALL java_cell2('SELECT 3000000000', 'getFloat')");
            assertAnswer(
                "F:300.0",
                "CALL java_cell2('SELECT 300', 'getFloat')");
            assertAnswer(
                "F:70000.0",
                "CALL java_cell2('SELECT 70000', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String([1,2]) to Float.",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String({\"a\":1}) to Float.",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getFloat')");
            assertAnswer(
                "F:2.0",
                "CALL java_cell2('SELECT 2::FLOAT', 'getFloat')");
            assertAnswer(
                "F:1.0",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.math.BigDecimal(1.5) to Float.",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getFloat')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getFloat')");
            assertAnswer(
                "E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot cast "
                    + "java.lang.String(\"1\") to Float.",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getFloat')");
            assertAnswer(
                "F:1.23456791E17",
                "CALL java_cell2('SELECT 123456789012345678', 'getFloat')");
            assertAnswer(
                "F:-1.0",
                "CALL java_cell2('SELECT -1', 'getFloat')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.Boolean "
                    + "(java.lang.Long and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 1.00', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.Boolean "
                    + "(java.lang.Long and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 3000000000', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.Boolean "
                    + "(java.lang.Long and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 300', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.Boolean "
                    + "(java.lang.Long and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 70000', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Boolean "
                    + "(java.lang.String and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Boolean "
                    + "(java.lang.String and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class java.lang.Boolean "
                    + "(java.lang.Double and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 2::FLOAT', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.Boolean "
                    + "(java.lang.Long and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class "
                    + "java.lang.Boolean (java.math.BigDecimal and java.lang.Boolean are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getBoolean')");
            assertAnswer(
                "E:java.lang.NullPointerException: Value at index 0 is null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Boolean "
                    + "(java.lang.String and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.Boolean "
                    + "(java.lang.Long and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 123456789012345678', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.Boolean "
                    + "(java.lang.Long and java.lang.Boolean are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT -1', 'getBoolean')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Long and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT 1.00', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Long and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT 3000000000', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Long and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT 300', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Long and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT 70000', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.String and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.String and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Double and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT 2::FLOAT', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Long and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getDecimal')");
            assertAnswer(
                "N:1.5",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getDecimal')");
            assertAnswer(
                "N:null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.String and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Long and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT 123456789012345678', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class "
                    + "java.math.BigDecimal (java.lang.Long and java.math.BigDecimal are in module java.base of "
                    + "loader 'bootstrap')",
                "CALL java_cell2('SELECT -1', 'getDecimal')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.sql.Timestamp "
                    + "(java.lang.Long is in module java.base of loader 'bootstrap'; java.sql.Timestamp is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell2('SELECT 1.00', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.sql.Timestamp "
                    + "(java.lang.Long is in module java.base of loader 'bootstrap'; java.sql.Timestamp is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell2('SELECT 3000000000', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.sql.Timestamp "
                    + "(java.lang.Long is in module java.base of loader 'bootstrap'; java.sql.Timestamp is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell2('SELECT 300', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.sql.Timestamp "
                    + "(java.lang.Long is in module java.base of loader 'bootstrap'; java.sql.Timestamp is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell2('SELECT 70000', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.sql.Timestamp (java.lang.String is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.sql.Timestamp (java.lang.String is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getTimestamp')");
            assertAnswer(
                "TS:1704183630000",
                "CALL java_cell2('SELECT ''2024-01-02 10:20:30 +02:00''::TIMESTAMP_TZ', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class "
                    + "java.sql.Timestamp (java.lang.Double is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell2('SELECT 2::FLOAT', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.sql.Timestamp "
                    + "(java.lang.Long is in module java.base of loader 'bootstrap'; java.sql.Timestamp is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class "
                    + "java.sql.Timestamp (java.math.BigDecimal is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getTimestamp')");
            assertAnswer(
                "TS:null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                    + "java.sql.Timestamp (java.lang.String is in module java.base of loader 'bootstrap'; "
                    + "java.sql.Timestamp is in module java.sql of loader 'platform')",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.sql.Timestamp "
                    + "(java.lang.Long is in module java.base of loader 'bootstrap'; java.sql.Timestamp is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell2('SELECT 123456789012345678', 'getTimestamp')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.sql.Timestamp "
                    + "(java.lang.Long is in module java.base of loader 'bootstrap'; java.sql.Timestamp is in module "
                    + "java.sql of loader 'platform')",
                "CALL java_cell2('SELECT -1', 'getTimestamp')");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS java_cell2(VARCHAR, VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS java_has(VARCHAR)");
        }
    }

    @Test
    public void variantsTheRowsSizeAndItsPrint() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE java_cell2(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
                    + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                    + "String q, String how) {\n    Row r = s.sql(q).collect()[0];\n    try {\n      switch (how) "
                    + "{\n        case \"getString\": return \"S:\" + r.getString(0);\n        case \"getInt\": "
                    + "return \"I:\" + r.getInt(0);\n        case \"getLong\": return \"L:\" + r.getLong(0);\n       "
                    + " case \"getShort\": return \"H:\" + r.getShort(0);\n        case \"getByte\": return \"Y:\" + "
                    + "r.getByte(0);\n        case \"getDouble\": return \"D:\" + r.getDouble(0);\n        case "
                    + "\"getFloat\": return \"F:\" + r.getFloat(0);\n        case \"getBoolean\": return \"B:\" + "
                    + "r.getBoolean(0);\n        case \"getDecimal\": return \"N:\" + r.getDecimal(0);\n        case "
                    + "\"getTimestamp\": return \"TS:\" + (r.getTimestamp(0) == null ? \"null\" : \"\" + "
                    + "r.getTimestamp(0).getTime());\n        case \"getVariant\": return \"V:\" + "
                    + "r.getVariant(0);\n        case \"get\": Object o = r.get(0); return \"O:\" + (o == null ? "
                    + "\"null\" : o.getClass().getName() + \"=\" + (o instanceof java.sql.Timestamp ? \"\" + "
                    + "((java.sql.Timestamp) o).getTime() : \"\" + o));\n        case \"isNullAt\": return \"Z:\" + "
                    + "r.isNullAt(0);\n        case \"size\": return \"N:\" + r.size();\n        case \"toString\": "
                    + "return \"T:\" + r.toString();\n        default: return \"?\";\n      }\n    } catch "
                    + "(Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    "
                    + "}\n  }\n}\n$$");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 1.00', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 3000000000', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 300', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 70000', 'getVariant')");
            assertAnswer(
                "V:[1,2]",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'getVariant')");
            assertAnswer(
                "V:{\"a\":1}",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class java.lang.String "
                    + "(java.lang.Double and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 2::FLOAT', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class "
                    + "java.lang.String (java.math.BigDecimal and java.lang.String are in module java.base of loader "
                    + "'bootstrap')",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'getVariant')");
            assertAnswer(
                "E:java.lang.NullPointerException: null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'getVariant')");
            assertAnswer(
                "V:1",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT 123456789012345678', 'getVariant')");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                    + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
                "CALL java_cell2('SELECT -1', 'getVariant')");
            assertAnswer(
                "O:java.lang.Long=1",
                "CALL java_cell2('SELECT 1.00', 'get')");
            assertAnswer(
                "O:java.lang.Long=3000000000",
                "CALL java_cell2('SELECT 3000000000', 'get')");
            assertAnswer(
                "O:java.lang.Long=300",
                "CALL java_cell2('SELECT 300', 'get')");
            assertAnswer(
                "O:java.lang.Long=70000",
                "CALL java_cell2('SELECT 70000', 'get')");
            assertAnswer(
                "O:java.lang.String=[1,2]",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'get')");
            assertAnswer(
                "O:java.lang.String={\"a\":1}",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'get')");
            assertAnswer(
                "O:java.lang.Double=2.0",
                "CALL java_cell2('SELECT 2::FLOAT', 'get')");
            assertAnswer(
                "O:java.lang.Long=1",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'get')");
            assertAnswer(
                "O:java.math.BigDecimal=1.5",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'get')");
            assertAnswer(
                "O:null",
                "CALL java_cell2('SELECT NULL::NUMBER', 'get')");
            assertAnswer(
                "O:java.lang.String=\"1\"",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'get')");
            assertAnswer(
                "O:java.lang.Long=123456789012345678",
                "CALL java_cell2('SELECT 123456789012345678', 'get')");
            assertAnswer(
                "O:java.lang.Long=-1",
                "CALL java_cell2('SELECT -1', 'get')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT 1.00', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT 3000000000', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT 300', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT 70000', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT ''2024-01-02 10:20:30 +02:00''::TIMESTAMP_TZ', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT 2::FLOAT', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'isNullAt')");
            assertAnswer(
                "Z:true",
                "CALL java_cell2('SELECT NULL::NUMBER', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT 123456789012345678', 'isNullAt')");
            assertAnswer(
                "Z:false",
                "CALL java_cell2('SELECT -1', 'isNullAt')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT 1.00', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT 3000000000', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT 300', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT 70000', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT ''2024-01-02 10:20:30 +02:00''::TIMESTAMP_TZ', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT 2::FLOAT', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT NULL::NUMBER', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT 123456789012345678', 'size')");
            assertAnswer(
                "N:1",
                "CALL java_cell2('SELECT -1', 'size')");
            assertAnswer(
                "T:Row[1]",
                "CALL java_cell2('SELECT 1.00', 'toString')");
            assertAnswer(
                "T:Row[3000000000]",
                "CALL java_cell2('SELECT 3000000000', 'toString')");
            assertAnswer(
                "T:Row[300]",
                "CALL java_cell2('SELECT 300', 'toString')");
            assertAnswer(
                "T:Row[70000]",
                "CALL java_cell2('SELECT 70000', 'toString')");
            assertAnswer(
                "T:Row[\"[1,2]\"]",
                "CALL java_cell2('SELECT ARRAY_CONSTRUCT(1, 2)', 'toString')");
            assertAnswer(
                "T:Row[\"{\"a\":1}\"]",
                "CALL java_cell2('SELECT OBJECT_CONSTRUCT(''a'', 1)', 'toString')");
            answer(
                "CALL java_cell2('SELECT ''2024-01-02 10:20:30 +02:00''::TIMESTAMP_TZ', 'toString')");
            answer(
                "CALL java_cell2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'toString')");
            assertAnswer(
                "T:Row[2.0]",
                "CALL java_cell2('SELECT 2::FLOAT', 'toString')");
            assertAnswer(
                "T:Row[1]",
                "CALL java_cell2('SELECT 1::NUMBER(38, 0)', 'toString')");
            assertAnswer(
                "T:Row[1.5]",
                "CALL java_cell2('SELECT 1.5::NUMBER(10, 1)', 'toString')");
            assertAnswer(
                "T:Row[null]",
                "CALL java_cell2('SELECT NULL::NUMBER', 'toString')");
            assertAnswer(
                "T:Row[\"\"1\"\"]",
                "CALL java_cell2('SELECT ''1''::VARIANT', 'toString')");
            assertAnswer(
                "T:Row[123456789012345678]",
                "CALL java_cell2('SELECT 123456789012345678', 'toString')");
            assertAnswer(
                "T:Row[-1]",
                "CALL java_cell2('SELECT -1', 'toString')");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS java_cell2(VARCHAR, VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS java_has(VARCHAR)");
        }
    }

    @Test
    public void theMethodsARowHas() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE java_cell2(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
                    + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
                    + "String q, String how) {\n    Row r = s.sql(q).collect()[0];\n    try {\n      switch (how) "
                    + "{\n        case \"getString\": return \"S:\" + r.getString(0);\n        case \"getInt\": "
                    + "return \"I:\" + r.getInt(0);\n        case \"getLong\": return \"L:\" + r.getLong(0);\n       "
                    + " case \"getShort\": return \"H:\" + r.getShort(0);\n        case \"getByte\": return \"Y:\" + "
                    + "r.getByte(0);\n        case \"getDouble\": return \"D:\" + r.getDouble(0);\n        case "
                    + "\"getFloat\": return \"F:\" + r.getFloat(0);\n        case \"getBoolean\": return \"B:\" + "
                    + "r.getBoolean(0);\n        case \"getDecimal\": return \"N:\" + r.getDecimal(0);\n        case "
                    + "\"getTimestamp\": return \"TS:\" + (r.getTimestamp(0) == null ? \"null\" : \"\" + "
                    + "r.getTimestamp(0).getTime());\n        case \"getVariant\": return \"V:\" + "
                    + "r.getVariant(0);\n        case \"get\": Object o = r.get(0); return \"O:\" + (o == null ? "
                    + "\"null\" : o.getClass().getName() + \"=\" + (o instanceof java.sql.Timestamp ? \"\" + "
                    + "((java.sql.Timestamp) o).getTime() : \"\" + o));\n        case \"isNullAt\": return \"Z:\" + "
                    + "r.isNullAt(0);\n        case \"size\": return \"N:\" + r.size();\n        case \"toString\": "
                    + "return \"T:\" + r.toString();\n        default: return \"?\";\n      }\n    } catch "
                    + "(Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    "
                    + "}\n  }\n}\n$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:3: error: cannot find symbol | class H { "
                    + "public static String run(Session s, String q) { Row r = s.sql(q).collect()[0]; try { Object o "
                    + "= r.toArray(); return \"ok:\" + (o == null ? \"null\" : o.getClass().getName() + \"=\" + o); "
                    + "} catch (Throwable t) { return \"E:\" + t.getClass().getName() + \": \" + t.getMessage(); } } "
                    + "} |                                                                                           "
                    + "                 ^ |   symbol:   method toArray() |   location: variable r of type Row | 1 "
                    + "error |  in function JAVA_HAS with handler H.run",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = r.toArray(); return \"ok:\" + (o == null ? "
                    + "\"null\" : o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" + "
                    + "t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JAVA_HAS.",
                "CALL java_has('SELECT 1')");
            assertAnswer(
                "Function JAVA_HAS successfully created.",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = r.toList(); return \"ok:\" + (o == null ? "
                    + "\"null\" : o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" + "
                    + "t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "Function JAVA_HAS successfully created.",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = r.getObject(0); return \"ok:\" + (o == null ? "
                    + "\"null\" : o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" + "
                    + "t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:3: error: cannot find symbol | class H { "
                    + "public static String run(Session s, String q) { Row r = s.sql(q).collect()[0]; try { Object o "
                    + "= r.getStruct(0); return \"ok:\" + (o == null ? \"null\" : o.getClass().getName() + \"=\" + "
                    + "o); } catch (Throwable t) { return \"E:\" + t.getClass().getName() + \": \" + t.getMessage(); "
                    + "} } } |                                                                                       "
                    + "                     ^ |   symbol:   method getStruct(int) |   location: variable r of type "
                    + "Row | 1 error |  in function JAVA_HAS with handler H.run",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = r.getStruct(0); return \"ok:\" + (o == null ? "
                    + "\"null\" : o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" + "
                    + "t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "Function JAVA_HAS successfully created.",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = r.getList(0); return \"ok:\" + (o == null ? "
                    + "\"null\" : o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" + "
                    + "t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.util.List "
                    + "(java.lang.String and java.util.List are in module java.base of loader 'bootstrap')",
                "CALL java_has('SELECT ARRAY_CONSTRUCT(1, 2)')");
            assertAnswer(
                "Function JAVA_HAS successfully created.",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = r.getMap(0); return \"ok:\" + (o == null ? "
                    + "\"null\" : o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" + "
                    + "t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.util.Map "
                    + "(java.lang.String and java.util.Map are in module java.base of loader 'bootstrap')",
                "CALL java_has('SELECT OBJECT_CONSTRUCT(''a'', 1)')");
            assertAnswer(
                "Function JAVA_HAS successfully created.",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = r.getListOfVariant(0); return \"ok:\" + (o == "
                    + "null ? \"null\" : o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" "
                    + "+ t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "ok:java.util.ArrayList=[1, 2]",
                "CALL java_has('SELECT ARRAY_CONSTRUCT(1, 2)')");
            assertAnswer(
                "Function JAVA_HAS successfully created.",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = r.getMapOfVariant(0); return \"ok:\" + (o == null "
                    + "? \"null\" : o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" + "
                    + "t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "ok:java.util.HashMap={a=1}",
                "CALL java_has('SELECT OBJECT_CONSTRUCT(''a'', 1)')");
            assertAnswer(
                "Function JAVA_HAS successfully created.",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = r.clone(); return \"ok:\" + (o == null ? \"null\" "
                    + ": o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" + "
                    + "t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "ok:com.snowflake.snowpark_java.Row=Row[1]",
                "CALL java_has('SELECT 1')");
            assertAnswer(
                "Function JAVA_HAS successfully created.",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = Row.create(1, \"a\"); return \"ok:\" + (o == null "
                    + "? \"null\" : o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" + "
                    + "t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "ok:com.snowflake.snowpark_java.Row=Row[1,\"a\"]",
                "CALL java_has('SELECT 1')");
            assertAnswer(
                "Function JAVA_HAS successfully created.",
                "CREATE OR REPLACE PROCEDURE java_has(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                    + "com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s, String q) { Row "
                    + "r = s.sql(q).collect()[0]; try { Object o = new Row(new Object[] {1}); return \"ok:\" + (o == "
                    + "null ? \"null\" : o.getClass().getName() + \"=\" + o); } catch (Throwable t) { return \"E:\" "
                    + "+ t.getClass().getName() + \": \" + t.getMessage(); } } }\n$$");
            assertAnswer(
                "ok:com.snowflake.snowpark_java.Row=Row[1]",
                "CALL java_has('SELECT 1')");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS java_cell2(VARCHAR, VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS java_has(VARCHAR)");
        }
    }
}
