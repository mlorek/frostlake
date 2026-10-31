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
 * A Snowpark Java Row's TIMESTAMP_LTZ and TIMESTAMP_TZ cells read as instants whatever the session's TIMEZONE,
 * in America/Los_Angeles and in UTC (all live-verified).
 */
public class SnowparkRowSessionZoneTest extends MeasuredCellsTestSupport {

    @Test
    public void zonedCellsInLosAngeles() {
        engine.execute("CREATE OR REPLACE PROCEDURE java_ts(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION "
            + "= '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, String q) {\n    "
            + "Row r = s.sql(q).collect()[0];\n    try {\n      return \"T:\" + r.getTimestamp(0).getTime();\n   "
            + " } catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + "
            + "t.getMessage();\n    }\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE java_schema(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
            + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
            + "String q) {\n    return s.sql(q).schema().toString();\n  }\n}\n$$");
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertAnswer("T:1704219630000", "CALL java_ts('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ')");
        assertAnswer("T:1704187230000", "CALL java_ts('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ')");
        assertAnswer("T:1719940830000", "CALL java_ts('SELECT ''2024-07-02 10:20:30''::TIMESTAMP_LTZ')");
    }

    @Test
    public void cellsInUtc() {
        engine.execute("CREATE OR REPLACE PROCEDURE java_ts(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION "
            + "= '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, String q) {\n    "
            + "Row r = s.sql(q).collect()[0];\n    try {\n      return \"T:\" + r.getTimestamp(0).getTime();\n   "
            + " } catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + "
            + "t.getMessage();\n    }\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE java_schema(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
            + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
            + "String q) {\n    return s.sql(q).schema().toString();\n  }\n}\n$$");
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertAnswer("T:1704190830000", "CALL java_ts('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_LTZ')");
        assertAnswer("T:1704190830000", "CALL java_ts('SELECT ''2024-01-02 10:20:30''::TIMESTAMP_NTZ')");
        assertAnswer("T:1704187230000", "CALL java_ts('SELECT ''2024-01-02 10:20:30 +01:00''::TIMESTAMP_TZ')");
        assertAnswer("T:1719915630000", "CALL java_ts('SELECT ''2024-07-02 10:20:30''::TIMESTAMP_LTZ')");
    }
}
