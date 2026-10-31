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
package dev.frostlake.geo;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A geospatial cell of a Snowpark Java Row as toList() holds it, the Scala surface's class of its kind where get hands
 * the Java surface's, and the GEOMETRY under getVariant and a GEOGRAPHY's row text (live-verified).
 */
public class GeoSnowparkRowListTest extends MeasuredCellsTestSupport {

    /** A GEOGRAPHY and a GEOMETRY cell through toList, getVariant and toString. */
    @Test
    public void geospatialList() {
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
            {"CALL jrow('SELECT TO_GEOGRAPHY(''POINT(1 2)'')', 'tolist')",
                "com.snowflake.snowpark.types.Geography;"},
            {"CALL jrow('SELECT TO_GEOMETRY(''POINT(1 2)'')', 'tolist')",
                "com.snowflake.snowpark.types.Geometry;"},
            {"CALL jrow('SELECT TO_GEOMETRY(''POINT(1 2)'')', 'var')",
                "{\"coordinates\":[1.0,2.0],\"type\":\"Point\"}"},
            {"CALL jrow('SELECT TO_GEOGRAPHY(''POINT(1 2)'')', 'str')",
                "Row[{\"coordinates\":[1,2],\"type\":\"Point\"}]"},
        });
    }
}
