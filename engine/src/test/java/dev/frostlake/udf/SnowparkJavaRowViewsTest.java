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
 * What a Snowpark Java Row prints and hands over (live-verified): its toString() over a binary - Binary(65,66,67) - a
 * date, a time, numbers, texts, a switch value, the semi-structured and the structured cells, equals and hashCode
 * across rows, and the structured cells read as their texts, so getList, getMap and getObject fail the cast.
 */
public class SnowparkJavaRowViewsTest extends MeasuredCellsTestSupport {

    /** Each view of the first row a query answers. */
    @Test
    public void rowViews() {
        engine.execute("CREATE OR REPLACE PROCEDURE jrow(q VARCHAR, w VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, String q, String w) "
            + "{\n    Row[] rows = s.sql(q).collect();\n    Row r = rows[0];\n    try {\n      if (w.equals(\"str\")) "
            + "return r.toString();\n      if (w.equals(\"ts\")) return r.getTimestamp(0).getClass().getName() + \"|\" "
            + "+ r.getTimestamp(0) + \"|\" + r.getTimestamp(0).getTime();\n      if (w.equals(\"cls\")) return "
            + "r.get(0) == null ? \"null\" : r.get(0).getClass().getName();\n      if (w.equals(\"list\")) return "
            + "String.valueOf(r.getList(0));\n      if (w.equals(\"map\")) return String.valueOf(r.getMap(0));\n      "
            + "if (w.equals(\"obj\")) return String.valueOf(r.getObject(0));\n      if (w.equals(\"eq\")) return "
            + "r.equals(rows[1]) + \"|\" + (r.hashCode() == rows[1].hashCode());\n      if (w.equals(\"var\")) return "
            + "String.valueOf(r.getVariant(0));\n      if (w.equals(\"tolist\")) {\n        StringBuilder b = new "
            + "StringBuilder();\n        for (Object o : r.toList()) { b.append(o == null ? \"null\" : "
            + "o.getClass().getName()).append(';'); }\n        return b.toString();\n      }\n      return \"?\";\n    "
            + "} catch (Throwable t) {\n      return \"E:\" + t.getClass().getName() + \": \" + t.getMessage();\n    "
            + "}\n  }\n}\n$$");
        assertCells(new String[][] {
            {"CALL jrow('SELECT TO_BINARY(''414243'', ''HEX'')', 'str')",
                "Row[Binary(65,66,67)]"},
            {"CALL jrow('SELECT ''2024-01-02''::DATE', 'str')",
                "Row[2024-01-02]"},
            {"CALL jrow('SELECT ''10:20:30''::TIME', 'str')",
                "Row[10:20:30]"},
            {"CALL jrow('SELECT 1.5::NUMBER(4,2)', 'str')",
                "Row[1.50]"},
            {"CALL jrow('SELECT ''x''', 'str')",
                "Row[\"x\"]"},
            {"CALL jrow('SELECT TRUE', 'str')",
                "Row[true]"},
            {"CALL jrow('SELECT PARSE_JSON(''{\"a\":1}'')', 'str')",
                "Row[\"{\"a\":1}\"]"},
            {"CALL jrow('SELECT [1, 2]', 'str')",
                "Row[\"[1,2]\"]"},
            {"CALL jrow('SELECT {''a'': 1}', 'str')",
                "Row[\"{\"a\":1}\"]"},
            {"CALL jrow('SELECT NULL', 'str')",
                "Row[null]"},
            {"CALL jrow('SELECT 1, ''a'', NULL', 'str')",
                "Row[1,\"a\",null]"},
            {"CALL jrow('SELECT 1, ''a'' UNION ALL SELECT 1, ''a''', 'eq')",
                "true|true"},
            {"CALL jrow('SELECT 1 UNION ALL SELECT 2', 'eq')",
                "false|false"},
            {"CALL jrow('SELECT [1, 2]::ARRAY(NUMBER)', 'list')",
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.util.List "
                + "(java.lang.String and java.util.List are in module java.base of loader 'bootstrap')"},
            {"CALL jrow('SELECT [1, 2]', 'list')",
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.util.List "
                + "(java.lang.String and java.util.List are in module java.base of loader 'bootstrap')"},
            {"CALL jrow('SELECT {''a'': 1}::MAP(VARCHAR, NUMBER)', 'map')",
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.util.Map "
                + "(java.lang.String and java.util.Map are in module java.base of loader 'bootstrap')"},
            {"CALL jrow('SELECT {''a'': 1}', 'map')",
                "E:java.lang.ClassCastException: class java.lang.String cannot be cast to class java.util.Map "
                + "(java.lang.String and java.util.Map are in module java.base of loader 'bootstrap')"},
            {"CALL jrow('SELECT {''a'': 1, ''b'': ''x''}::OBJECT(a NUMBER, b VARCHAR)', 'str')",
                "Row[\"{\"a\":1,\"b\":\"x\"}\"]"},
            {"CALL jrow('SELECT [1, 2]::ARRAY(NUMBER)', 'str')",
                "Row[\"[1,2]\"]"},
            {"CALL jrow('SELECT {''a'': 1}::MAP(VARCHAR, NUMBER)', 'str')",
                "Row[\"{\"a\":1}\"]"},
            {"CALL jrow('SELECT [1, 2]::ARRAY(NUMBER)', 'cls')",
                "java.lang.String"},
            {"CALL jrow('SELECT {''a'': 1}::MAP(VARCHAR, NUMBER)', 'cls')",
                "java.lang.String"},
            {"CALL jrow('SELECT {''a'': 1, ''b'': ''x''}::OBJECT(a NUMBER, b VARCHAR)', 'cls')",
                "java.lang.String"},
        });
    }
}
