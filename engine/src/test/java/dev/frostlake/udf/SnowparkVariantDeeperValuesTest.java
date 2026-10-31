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
 * A Snowpark Variant built from a Java value nested in a list or a map holds what the account's serializer
 * writes: a java.sql.Time its own text, a bean its properties, a BigDecimal its digits, a null a null; a Row's
 * getVariant reads each column type's value, and asMap over a SQL NULL fails as the handler's runtime fails
 * (all live-verified).
 */
public class SnowparkVariantDeeperValuesTest extends MeasuredCellsTestSupport {

    @Test
    public void nestedValues() {
        engine.execute("CREATE OR REPLACE FUNCTION jv653(k INT) RETURNS VARIANT LANGUAGE JAVA PACKAGES = "
            + "('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$\nimport "
            + "com.snowflake.snowpark_java.types.Variant;\nimport java.util.Arrays;\nimport "
            + "java.util.Collections;\nclass H {\n  public static Variant f(int k) {\n    switch (k) {\n      "
            + "case 1: return new Variant((Object) Arrays.asList(java.sql.Timestamp.valueOf(\"2024-01-02 "
            + "03:04:05.123\")));\n      case 2: return new Variant((Object) Collections.singletonMap(\"t\", "
            + "java.sql.Time.valueOf(\"03:04:05\")));\n      case 3: return new Variant((Object) "
            + "Collections.singletonMap(\"k\", Arrays.asList(new Variant(1), new Variant(\"x\"))));\n      case "
            + "4: return new Variant((Object) Arrays.asList(java.sql.Date.valueOf(\"2024-01-02\")));\n      case "
            + "5: return new Variant((Object) new B());\n      case 6: return new Variant((Object) "
            + "Arrays.asList(new byte[] {1, 2}));\n      case 7: return new Variant((Object) "
            + "Collections.singletonMap(\"d\", new java.math.BigDecimal(\"1.50\")));\n      case 8: return new "
            + "Variant((Object) Arrays.asList((Object) null, \"a\"));\n      default: return new "
            + "Variant((Object) Collections.singletonMap(\"n\", Arrays.asList(Collections.singletonMap(\"m\", "
            + "1))));\n    }\n  }\n}\nclass B {\n  public int getA() { return 1; }\n  public String getS() { "
            + "return \"s\"; }\n}\n$$");
        assertAnswer("OBJECT|{\"t\":\"03:04:05\"}", "SELECT TYPEOF(jv653(2)), jv653(2)::VARCHAR");
        assertAnswer("OBJECT|{\"a\":1,\"s\":\"s\"}", "SELECT TYPEOF(jv653(5)), jv653(5)::VARCHAR");
        assertAnswer("ARRAY|[\"AQI=\"]", "SELECT TYPEOF(jv653(6)), jv653(6)::VARCHAR");
        assertAnswer("OBJECT|{\"d\":1.5}", "SELECT TYPEOF(jv653(7)), jv653(7)::VARCHAR");
        assertAnswer("ARRAY|[null,\"a\"]", "SELECT TYPEOF(jv653(8)), jv653(8)::VARCHAR");
        assertAnswer("OBJECT|{\"n\":[{\"m\":1}]}", "SELECT TYPEOF(jv653(9)), jv653(9)::VARCHAR");
    }

    @Test
    public void rowVariants() {
        engine.execute("CREATE OR REPLACE PROCEDURE jrv653(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION "
            + "= '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.Variant;\nclass H {\n  "
            + "public static String run(Session s, String q) {\n    try {\n      Variant v = "
            + "s.sql(q).collect()[0].getVariant(0);\n      return v == null ? \"null\" : v.toString() + \" | \" "
            + "+ v.asString();\n    } catch (Exception e) {\n      return \"E:\" + e.getClass().getName() + \": "
            + "\" + e.getMessage();\n    }\n  }\n}\n$$");
        assertAnswer("E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.String "
                + "(java.lang.Long and java.lang.String are in module java.base of loader 'bootstrap')",
            "CALL jrv653('SELECT 1')");
        assertAnswer("E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class "
                + "java.lang.String (java.math.BigDecimal and java.lang.String are in module java.base of loader "
                + "'bootstrap')",
            "CALL jrv653('SELECT 1.5')");
        assertAnswer("E:java.lang.ClassCastException: class java.lang.Double cannot be cast to class java.lang.String "
                + "(java.lang.Double and java.lang.String are in module java.base of loader 'bootstrap')",
            "CALL jrv653('SELECT 1.5::FLOAT')");
        assertAnswer("a | a", "CALL jrv653('SELECT ''a''')");
        assertAnswer("E:java.lang.ClassCastException: class java.lang.Boolean cannot be cast to class "
                + "java.lang.String (java.lang.Boolean and java.lang.String are in module java.base of loader "
                + "'bootstrap')",
            "CALL jrv653('SELECT TRUE')");
        assertAnswer("E:java.lang.ClassCastException: class java.sql.Date cannot be cast to class java.lang.String "
                + "(java.sql.Date is in module java.sql of loader 'platform'; java.lang.String is in module java.base "
                + "of loader 'bootstrap')",
            "CALL jrv653('SELECT ''2024-01-02''::DATE')");
        assertAnswer("E:java.lang.ClassCastException: class java.sql.Timestamp cannot be cast to class "
                + "java.lang.String (java.sql.Timestamp is in module java.sql of loader 'platform'; java.lang.String "
                + "is in module java.base of loader 'bootstrap')",
            "CALL jrv653('SELECT ''2024-01-02 03:04:05''::TIMESTAMP_NTZ')");
        assertAnswer("{\"a\":1} | {\"a\":1}", "CALL jrv653('SELECT PARSE_JSON(''{\"a\":1}'')')");
        assertAnswer("[1,2] | [1,2]", "CALL jrv653('SELECT [1, 2]')");
        assertAnswer("[10] | [10]", "CALL jrv653('SELECT TO_BINARY(''0A'', ''HEX'')')");
        assertAnswer("E:java.lang.NullPointerException: null", "CALL jrv653('SELECT NULL')");
        assertAnswer("E:java.lang.ClassCastException: class java.sql.Time cannot be cast to class java.lang.String "
                + "(java.sql.Time is in module java.sql of loader 'platform'; java.lang.String is in module java.base "
                + "of loader 'bootstrap')",
            "CALL jrv653('SELECT ''12:00:00''::TIME')");
    }

    @Test
    public void asMapOverNull() {
        engine.execute("CREATE OR REPLACE FUNCTION jm11_653(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION "
            + "= '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$\nimport "
            + "com.snowflake.snowpark_java.types.Variant;\nclass H {\n  public static String f(Variant x) {\n    "
            + "try {\n      return \"ok \" + x.asMap();\n    } catch (Exception e) {\n      return "
            + "e.getClass().getName() + \": \" + e.getMessage();\n    }\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jm17_653(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION "
            + "= '17' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$\nimport "
            + "com.snowflake.snowpark_java.types.Variant;\nclass H {\n  public static String f(Variant x) {\n    "
            + "try {\n      return \"ok \" + x.asMap();\n    } catch (Exception e) {\n      return "
            + "e.getClass().getName() + \": \" + e.getMessage();\n    }\n  }\n}\n$$");
        assertAnswer("java.lang.NullPointerException: Cannot invoke "
                + "\"com.snowflake.snowpark_java.types.Variant.asMap()\" because \"<parameter1>\" is null",
            "SELECT jm17_653(NULL)");
        assertAnswer("java.io.UncheckedIOException: java.io.IOException: Failed to convert null to Map",
            "SELECT jm11_653(PARSE_JSON('null'))");
        assertAnswer("java.io.UncheckedIOException: java.io.IOException: Failed to convert null to Map",
            "SELECT jm17_653(PARSE_JSON('null'))");
        assertAnswer("java.io.UncheckedIOException: java.io.IOException: Failed to convert  to Map",
            "SELECT jm11_653(PARSE_JSON('[1]'))");
        assertAnswer("java.io.UncheckedIOException: java.io.IOException: Failed to convert  to Map",
            "SELECT jm17_653(PARSE_JSON('[1]'))");
    }
}
