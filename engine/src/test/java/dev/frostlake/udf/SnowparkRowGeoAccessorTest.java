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
 * A Snowpark Row's geospatial accessors over cells of other kinds, and the Geography and Geometry classes a handler
 * builds: getGeography and getGeometry cast the cell to the class Snowpark holds it as and fail naming it,
 * {@code class java.lang.Long cannot be cast to class com.snowflake.snowpark.types.Geography}, a NULL being no
 * value; fromGeoJSON keeps its text as given, unchecked, equal to another of the same text, and refuses a null one,
 * {@code Cannot create geography object from null input}; a Geometry has no asGeoJSON and a Geography no getString
 * (live-verified).
 */
public class SnowparkRowGeoAccessorTest extends MeasuredCellsTestSupport {

    @Test
    public void theAccessorsCastAndTheClassesKeepTheirText() {
        engine.execute("CREATE OR REPLACE PROCEDURE jr(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
            + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
            + "String q, String how) {\n    Row[] rows;\n    try {\n      rows = s.sql(q).collect();\n    "
            + "} catch (Throwable t) {\n      String st = t instanceof java.sql.SQLException ? \" Q\" + "
            + "((java.sql.SQLException) t).getSQLState() + \"/\" + ((java.sql.SQLException) "
            + "t).getErrorCode() : \"\";\n      return \"C:\" + t.getClass().getName() + st + \": \" + "
            + "t.getMessage();\n    }\n    Row r = rows[0];\n    try {\n      switch (how) {\n        case "
            + "\"get\": Object o = r.get(0); return \"O:\" + (o == null ? \"null\" : "
            + "o.getClass().getName() + \"=\" + o);\n        case \"getLong\": return \"L:\" + "
            + "r.getLong(0);\n        case \"getDecimal\": return \"N:\" + r.getDecimal(0);\n        case "
            + "\"row\": return \"R:\" + r;\n        case \"size\": return \"Z:\" + rows.length;\n        "
            + "default: return \"?\";\n      }\n    } catch (Throwable t) {\n      String m = "
            + "String.valueOf(t.getMessage());\n      int p = m.indexOf(\" (\");\n      return \"E:\" + "
            + "t.getClass().getName() + \": \" + (p < 0 ? m : m.substring(0, p));\n    }\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE jg(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
            + "$$\nimport com.snowflake.snowpark_java.*;\nimport "
            + "com.snowflake.snowpark_java.types.*;\nclass H {\n  public static String run(Session s, "
            + "String q, String how) {\n    Row r;\n    try {\n      r = s.sql(q).collect()[0];\n    } "
            + "catch (Throwable t) {\n      return \"C:\" + t.getClass().getName() + \": \" + "
            + "t.getMessage();\n    }\n    try {\n      switch (how) {\n        case \"get\": Object o = "
            + "r.get(0); return \"O:\" + (o == null ? \"null\" : o.getClass().getName() + \"=\" + o);\n    "
            + "    case \"getGeography\": Geography g = r.getGeography(0); return \"G:\" + g;\n        "
            + "case \"getGeometry\": Geometry m = r.getGeometry(0); return \"M:\" + m;\n        case "
            + "\"getString\": return \"S:\" + r.getString(0);\n        case \"row\": return \"R:\" + r;\n  "
            + "      default: return \"?\";\n      }\n    } catch (Throwable t) {\n      String m = "
            + "String.valueOf(t.getMessage());\n      int p = m.indexOf(\" (\");\n      return \"E:\" + "
            + "t.getClass().getName() + \": \" + (p < 0 ? m : m.substring(0, p));\n    }\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE TABLE jrn (n NUMBER(38,0), m NUMBER(20,0))");
        engine.execute("INSERT INTO jrn VALUES (12345678901234567890, 12345678901234567890)");
        assertCells(new String[][] {
            {"CALL jg('SELECT 1', 'getGeography')",
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class "
                + "com.snowflake.snowpark.types.Geography"},
            {"CALL jg('SELECT 1', 'getGeometry')",
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class "
                + "com.snowflake.snowpark.types.Geometry"},
            {"CALL jg('SELECT ''x''', 'getGeography')",
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                + "com.snowflake.snowpark.types.Geography"},
            {"CALL jg('SELECT NULL', 'getGeography')", "E:java.lang.NullPointerException: null"},
            {"CALL jg('SELECT NULL', 'getGeometry')", "E:java.lang.NullPointerException: null"},
            {"CALL jg('SELECT PARSE_JSON(''{\"a\":1}'')', 'getGeography')",
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class "
                + "com.snowflake.snowpark.types.Geography"},
            {"CALL jg('SELECT 1.5', 'getGeometry')",
                "E:java.lang.ClassCastException: class java.math.BigDecimal cannot be cast to class "
                + "com.snowflake.snowpark.types.Geometry"},
            {"CREATE OR REPLACE PROCEDURE js1() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geography.fromGeoJSON(p).asGeoJSON());\n    } catch (Throwable t) {\n      return "
                + "\"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$",
                "Function JS1 successfully created."},
            {"CALL js1()", "{\"type\":\"Point\",\"coordinates\":[1,2]}"},
            {"CREATE OR REPLACE PROCEDURE js2() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geography.fromGeoJSON(p).toString());\n    } catch (Throwable t) {\n      return "
                + "\"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$",
                "Function JS2 successfully created."},
            {"CALL js2()", "{\"type\":\"Point\",\"coordinates\":[1,2]}"},
            {"CREATE OR REPLACE PROCEDURE js3() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geometry.fromGeoJSON(p).toString());\n    } catch (Throwable t) {\n      return "
                + "\"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$",
                "Function JS3 successfully created."},
            {"CALL js3()", "{\"type\":\"Point\",\"coordinates\":[1,2]}"},
            {"CREATE OR REPLACE PROCEDURE js4() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geometry.fromGeoJSON(p).asGeoJSON());\n    } catch (Throwable t) {\n      return "
                + "\"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$",
                "REFUSED Error while compiling source: /InlineCode.java:8: error: cannot find symbol |       "
                + "return String.valueOf(Geometry.fromGeoJSON(p).asGeoJSON()); |                                     "
                + "               ^ |   symbol:   method asGeoJSON() |   location: class Geometry | 1 error |  in "
                + "function JS4 with handler H.run"},
            {"CALL js4()", "REFUSED SQL compilation error: | Unknown function JS4."},
            {"CREATE OR REPLACE PROCEDURE js5() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geography.fromGeoJSON(p).getString());\n    } catch (Throwable t) {\n      return "
                + "\"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$",
                "REFUSED Error while compiling source: /InlineCode.java:8: error: cannot find symbol |       "
                + "return String.valueOf(Geography.fromGeoJSON(p).getString()); |                                    "
                + "                 ^ |   symbol:   method getString() |   location: class Geography | 1 error |  in "
                + "function JS5 with handler H.run"},
            {"CALL js5()", "REFUSED SQL compilation error: | Unknown function JS5."},
            {"CREATE OR REPLACE PROCEDURE js6() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geography.fromGeoJSON(p).equals(Geography.fromGeoJSON(p)) + \"|\" + "
                + "(Geography.fromGeoJSON(p).hashCode() == Geography.fromGeoJSON(p).hashCode()));\n    } catch "
                + "(Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n "
                + " }\n}\n$$",
                "Function JS6 successfully created."},
            {"CALL js6()", "true|true"},
            {"CREATE OR REPLACE PROCEDURE js7() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geography.fromGeoJSON(null));\n    } catch (Throwable t) {\n      return \"E:\" + "
                + "t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$",
                "Function JS7 successfully created."},
            {"CALL js7()",
                "E:java.io.UncheckedIOException: java.io.IOException: Cannot create geography object from null input"},
            {"CREATE OR REPLACE PROCEDURE js8() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geometry.fromGeoJSON(p).equals(Geometry.fromGeoJSON(p)));\n    } catch (Throwable "
                + "t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$",
                "Function JS8 successfully created."},
            {"CALL js8()", "true"},
            {"CREATE OR REPLACE PROCEDURE js9() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geography.fromGeoJSON(\"not json\").asGeoJSON());\n    } catch (Throwable t) {\n   "
                + "   return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$",
                "Function JS9 successfully created."},
            {"CALL js9()", "not json"},
            {"CREATE OR REPLACE PROCEDURE js10() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geometry.fromGeoJSON(null));\n    } catch (Throwable t) {\n      return \"E:\" + "
                + "t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$",
                "Function JS10 successfully created."},
            {"CALL js10()",
                "E:java.io.UncheckedIOException: java.io.IOException: Cannot create geometry object from null input"},
            {"CREATE OR REPLACE PROCEDURE js11() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nimport com.snowflake.snowpark_java.types.*;\nclass H {\n  public "
                + "static String run(Session s) {\n    String p = "
                + "\"{\\\"type\\\":\\\"Point\\\",\\\"coordinates\\\":[1,2]}\";\n    try {\n      return "
                + "String.valueOf(Geography.fromGeoJSON(p).getClass().getName() + \"|\" + "
                + "Geometry.fromGeoJSON(p).getClass().getName());\n    } catch (Throwable t) {\n      return \"E:\" "
                + "+ t.getClass().getName() + \": \" + t.getMessage();\n    }\n  }\n}\n$$",
                "Function JS11 successfully created."},
            {"CALL js11()", "com.snowflake.snowpark_java.types.Geography|com.snowflake.snowpark_java.types.Geometry"},
        });
    }
}
