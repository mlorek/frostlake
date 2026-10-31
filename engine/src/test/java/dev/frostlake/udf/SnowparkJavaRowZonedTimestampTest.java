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
 * A TIMESTAMP_LTZ and a TIMESTAMP_TZ cell as a Snowpark Java Row hands them to a handler (live-verified): the account
 * runs the handler's JVM in the session's zone, so an LTZ prints its instant there and a TZ its own wall clock,
 * toLocalDateTime() and toList() read the instant in the session's zone, and the instant itself is the cell's; a
 * TIMESTAMP_NTZ's, a DATE's and a TIME's text is their wall clock, in Los Angeles, UTC and Tokyo. A Row prints a FLOAT,
 * a long whole number, a binary and a NULL binary as Snowpark does.
 */
public class SnowparkJavaRowZonedTimestampTest extends MeasuredCellsTestSupport {

    /** Each kind of timestamp cell in America/Los_Angeles. */
    @Test
    public void inLosAngeles() {
        engine.execute("CREATE OR REPLACE PROCEDURE jrow2(q VARCHAR, w VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, String q, String w) "
            + "{\n    Row[] rows = s.sql(q).collect();\n    Row r = rows[0];\n    try {\n      String out = \"?\";\n   "
            + "   if (w.equals(\"ts\")) out = r.getTimestamp(0) + \"|\" + r.getTimestamp(0).getTime();\n      if "
            + "(w.equals(\"str\")) out = r.toString();\n      if (w.equals(\"cls\")) out = String.valueOf(r.get(0));\n "
            + "     if (w.equals(\"zone\")) out = java.util.TimeZone.getDefault().getID();\n      if "
            + "(w.equals(\"ldt\")) out = String.valueOf(r.getTimestamp(0).toLocalDateTime());\n      if "
            + "(w.equals(\"date\")) out = r.getDate(0) + \"|\" + r.getDate(0).getTime();\n      if "
            + "(w.equals(\"time\")) out = r.getTime(0) + \"|\" + r.getTime(0).getTime();\n      if "
            + "(w.equals(\"list\")) out = String.valueOf(r.toList());\n      return out.replace('-', '/');\n    } "
            + "catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n "
            + " }\n}\n$$");
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'",
                "Statement executed successfully."},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'str')",
                "Row[2024/01/02 10:20:30.0]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'cls')",
                "2024/01/02 10:20:30.0"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'ldt')",
                "2024/01/02T10:20:30"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'ts')",
                "2024/01/02 10:20:30.0|1704219630000"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'str')",
                "Row[2024/01/02 10:20:30.0]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'cls')",
                "2024/01/02 10:20:30.0"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'ldt')",
                "2024/01/02T10:20:30"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'ts')",
                "2024/01/02 10:20:30.0|1704187230000"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'str')",
                "Row[2024/01/02 10:20:30.0]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'cls')",
                "2024/01/02 10:20:30.0"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'ldt')",
                "2024/01/02T01:20:30"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30.123456789''::TIMESTAMP_NTZ(9)', 'str')",
                "Row[2024/01/02 10:20:30.123456789]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30.5 -05:00''::TIMESTAMP_TZ', 'ts')",
                "2024/01/02 10:20:30.5|1704208830500"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30.5 -05:00''::TIMESTAMP_TZ', 'str')",
                "Row[2024/01/02 10:20:30.5]"},
            {"CALL jrow2('SELECT ''2024-07-02 10:20:30''::TIMESTAMP_LTZ', 'ts')",
                "2024/07/02 10:20:30.0|1719940830000"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'list')",
                "[2024/01/02 10:20:30.0]"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }

    /** Each kind of timestamp cell in UTC. */
    @Test
    public void inUtc() {
        engine.execute("CREATE OR REPLACE PROCEDURE jrow2(q VARCHAR, w VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, String q, String w) "
            + "{\n    Row[] rows = s.sql(q).collect();\n    Row r = rows[0];\n    try {\n      String out = \"?\";\n   "
            + "   if (w.equals(\"ts\")) out = r.getTimestamp(0) + \"|\" + r.getTimestamp(0).getTime();\n      if "
            + "(w.equals(\"str\")) out = r.toString();\n      if (w.equals(\"cls\")) out = String.valueOf(r.get(0));\n "
            + "     if (w.equals(\"zone\")) out = java.util.TimeZone.getDefault().getID();\n      if "
            + "(w.equals(\"ldt\")) out = String.valueOf(r.getTimestamp(0).toLocalDateTime());\n      if "
            + "(w.equals(\"date\")) out = r.getDate(0) + \"|\" + r.getDate(0).getTime();\n      if "
            + "(w.equals(\"time\")) out = r.getTime(0) + \"|\" + r.getTime(0).getTime();\n      if "
            + "(w.equals(\"list\")) out = String.valueOf(r.toList());\n      return out.replace('-', '/');\n    } "
            + "catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n "
            + " }\n}\n$$");
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'UTC'",
                "Statement executed successfully."},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'str')",
                "Row[2024/01/02 10:20:30.0]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'cls')",
                "2024/01/02 10:20:30.0"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'ldt')",
                "2024/01/02T10:20:30"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'ts')",
                "2024/01/02 10:20:30.0|1704190830000"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'str')",
                "Row[2024/01/02 10:20:30.0]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'cls')",
                "2024/01/02 10:20:30.0"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'ldt')",
                "2024/01/02T10:20:30"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'ts')",
                "2024/01/02 10:20:30.0|1704187230000"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'str')",
                "Row[2024/01/02 10:20:30.0]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'cls')",
                "2024/01/02 10:20:30.0"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'ldt')",
                "2024/01/02T09:20:30"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30.123456789''::TIMESTAMP_NTZ(9)', 'str')",
                "Row[2024/01/02 10:20:30.123456789]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30.5 -05:00''::TIMESTAMP_TZ', 'ts')",
                "2024/01/02 10:20:30.5|1704208830500"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30.5 -05:00''::TIMESTAMP_TZ', 'str')",
                "Row[2024/01/02 10:20:30.5]"},
            {"CALL jrow2('SELECT ''2024-07-02 10:20:30''::TIMESTAMP_LTZ', 'ts')",
                "2024/07/02 10:20:30.0|1719915630000"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'list')",
                "[2024/01/02 10:20:30.0]"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }

    /** Each kind of timestamp cell in Asia/Tokyo. */
    @Test
    public void inTokyo() {
        engine.execute("CREATE OR REPLACE PROCEDURE jrow2(q VARCHAR, w VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, String q, String w) "
            + "{\n    Row[] rows = s.sql(q).collect();\n    Row r = rows[0];\n    try {\n      String out = \"?\";\n   "
            + "   if (w.equals(\"ts\")) out = r.getTimestamp(0) + \"|\" + r.getTimestamp(0).getTime();\n      if "
            + "(w.equals(\"str\")) out = r.toString();\n      if (w.equals(\"cls\")) out = String.valueOf(r.get(0));\n "
            + "     if (w.equals(\"zone\")) out = java.util.TimeZone.getDefault().getID();\n      if "
            + "(w.equals(\"ldt\")) out = String.valueOf(r.getTimestamp(0).toLocalDateTime());\n      if "
            + "(w.equals(\"date\")) out = r.getDate(0) + \"|\" + r.getDate(0).getTime();\n      if "
            + "(w.equals(\"time\")) out = r.getTime(0) + \"|\" + r.getTime(0).getTime();\n      if "
            + "(w.equals(\"list\")) out = String.valueOf(r.toList());\n      return out.replace('-', '/');\n    } "
            + "catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n "
            + " }\n}\n$$");
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'",
                "Statement executed successfully."},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'str')",
                "Row[2024/01/02 10:20:30.0]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'cls')",
                "2024/01/02 10:20:30.0"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ', 'ldt')",
                "2024/01/02T10:20:30"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'ts')",
                "2024/01/02 10:20:30.0|1704158430000"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'str')",
                "Row[2024/01/02 10:20:30.0]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'cls')",
                "2024/01/02 10:20:30.0"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'ldt')",
                "2024/01/02T10:20:30"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'ts')",
                "2024/01/02 10:20:30.0|1704187230000"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'str')",
                "Row[2024/01/02 10:20:30.0]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'cls')",
                "2024/01/02 10:20:30.0"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'ldt')",
                "2024/01/02T18:20:30"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30.123456789''::TIMESTAMP_NTZ(9)', 'str')",
                "Row[2024/01/02 10:20:30.123456789]"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30.5 -05:00''::TIMESTAMP_TZ', 'ts')",
                "2024/01/02 10:20:30.5|1704208830500"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30.5 -05:00''::TIMESTAMP_TZ', 'str')",
                "Row[2024/01/02 10:20:30.5]"},
            {"CALL jrow2('SELECT ''2024-07-02 10:20:30''::TIMESTAMP_LTZ', 'ts')",
                "2024/07/02 10:20:30.0|1719883230000"},
            {"CALL jrow2('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'list')",
                "[2024/01/02 10:20:30.0]"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }

    /** The text of a row of a FLOAT, a whole number, a text and binaries. */
    @Test
    public void rowTexts() {
        engine.execute("CREATE OR REPLACE PROCEDURE jrow2(q VARCHAR, w VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, String q, String w) "
            + "{\n    Row[] rows = s.sql(q).collect();\n    Row r = rows[0];\n    try {\n      String out = \"?\";\n   "
            + "   if (w.equals(\"ts\")) out = r.getTimestamp(0) + \"|\" + r.getTimestamp(0).getTime();\n      if "
            + "(w.equals(\"str\")) out = r.toString();\n      if (w.equals(\"cls\")) out = String.valueOf(r.get(0));\n "
            + "     if (w.equals(\"zone\")) out = java.util.TimeZone.getDefault().getID();\n      if "
            + "(w.equals(\"ldt\")) out = String.valueOf(r.getTimestamp(0).toLocalDateTime());\n      if "
            + "(w.equals(\"date\")) out = r.getDate(0) + \"|\" + r.getDate(0).getTime();\n      if "
            + "(w.equals(\"time\")) out = r.getTime(0) + \"|\" + r.getTime(0).getTime();\n      if "
            + "(w.equals(\"list\")) out = String.valueOf(r.toList());\n      return out.replace('-', '/');\n    } "
            + "catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n "
            + " }\n}\n$$");
        assertCells(new String[][] {
            {"CALL jrow2('SELECT 1.5::FLOAT', 'str')",
                "Row[1.5]"},
            {"CALL jrow2('SELECT 1e20::FLOAT', 'str')",
                "Row[1.0E20]"},
            {"CALL jrow2('SELECT 12345678901', 'str')",
                "Row[12345678901]"},
            {"CALL jrow2('SELECT ''a\"b''', 'str')",
                "Row[\"a\"b\"]"},
            {"CALL jrow2('SELECT TO_BINARY('''', ''HEX'')', 'str')",
                "Row[Binary()]"},
            {"CALL jrow2('SELECT TO_BINARY(''FF80'', ''HEX'')', 'str')",
                "Row[Binary(/1,/128)]"},
            {"CALL jrow2('SELECT NULL::BINARY', 'str')",
                "Row[null]"},
            {"CALL jrow2('SELECT 1::FLOAT', 'str')",
                "Row[1.0]"},
            {"CALL jrow2('SELECT -0.5::NUMBER(3,1)', 'str')",
                "Row[/0.5]"},
        });
    }

    /** Dates, times and instants read back, equality and order, in Tokyo and Los Angeles. */
    @Test
    public void datesTimesAndInstants() {
        engine.execute("CREATE OR REPLACE PROCEDURE jrow3(q VARCHAR, w VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, String q, String w) "
            + "{\n    Row[] rows = s.sql(q).collect();\n    Row r = rows[0];\n    try {\n      String out = \"?\";\n   "
            + "   if (w.equals(\"cls\")) out = r.get(0).getClass().getName();\n      if (w.equals(\"inst\")) out = "
            + "String.valueOf(r.getTimestamp(0).toInstant());\n      if (w.equals(\"ld\")) out = "
            + "String.valueOf(r.getDate(0).toLocalDate());\n      if (w.equals(\"lt\")) out = "
            + "String.valueOf(r.getTime(0).toLocalTime());\n      if (w.equals(\"eq\")) out = r.equals(rows[1]) + "
            + "\"|\" + r.getTimestamp(0).equals(rows[1].getTimestamp(0));\n      if (w.equals(\"cmp\")) out = "
            + "String.valueOf(Integer.signum(r.getTimestamp(0).compareTo(rows[1].getTimestamp(0))));\n      if "
            + "(w.equals(\"str\")) out = r.toString();\n      return out.replace('-', '/');\n    } catch (Throwable t) "
            + "{\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$");
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'",
                "Statement executed successfully."},
            {"CALL jrow3('SELECT ''2024-01-02''::DATE', 'ld')",
                "2024/01/02"},
            {"CALL jrow3('SELECT ''10:20:30.5''::TIME(3)', 'lt')",
                "10:20:30"},
            {"CALL jrow3('SELECT ''10:20:30.5''::TIME(3)', 'str')",
                "Row[10:20:30]"},
            {"CALL jrow3('SELECT ''1960-01-02''::DATE', 'str')",
                "Row[1960/01/02]"},
            {"CALL jrow3('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ', 'inst')",
                "2024/01/02T01:20:30Z"},
            {"CALL jrow3('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ', 'inst')",
                "2024/01/02T09:20:30Z"},
            {"CALL jrow3('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ UNION ALL SELECT ''2024-01-02 "
                + "10:20:30''::TIMESTAMP_NTZ', 'eq')",
                "true|true"},
            {"CALL jrow3('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ UNION ALL SELECT ''2024-01-02 "
                + "11:20:30''::TIMESTAMP_LTZ', 'cmp')",
                "/1"},
            {"CALL jrow3('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ UNION ALL SELECT ''2024-01-02 10:20:30 "
                + "+02:00''::TIMESTAMP_TZ', 'cmp')",
                "1"},
            {"CALL jrow3('SELECT ''2024-03-10 02:30:00''::TIMESTAMP_NTZ', 'str')",
                "Row[2024/03/10 02:30:00.0]"},
            {"ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'",
                "Statement executed successfully."},
            {"CALL jrow3('SELECT ''0001-01-01 00:00:00''::TIMESTAMP_NTZ', 'str')",
                "Row[0001/01/01 00:00:00.0]"},
            {"CALL jrow3('SELECT ''9999-12-31 23:59:59.999''::TIMESTAMP_NTZ', 'str')",
                "Row[9999/12/31 23:59:59.999]"},
            {"CALL jrow3('SELECT ''2024-01-02 10:20:30.000001''::TIMESTAMP_TZ', 'str')",
                "Row[2024/01/02 10:20:30.000001]"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }
}
