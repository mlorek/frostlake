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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A geospatial cell a Java handler collects through Snowpark, as the account hands it over: a GEOGRAPHY as
 * {@code com.snowflake.snowpark_java.types.Geography} over its GeoJSON with sorted keys, a GEOMETRY as
 * {@code Geometry} with every number written with fifteen decimals and an exponent, getString and getVariant
 * reading the text, and getGeometry over a GEOGRAPHY (or getGeography over a GEOMETRY) failing the cast to the
 * class Snowpark holds the cell as. The answers are the account's (live-verified); the geospatial functions
 * need this module, so the test runs on the embedded engine alone.
 */
public class GeoSnowparkRowTest extends BaseDatabaseTest {

    private String call(final String query, final String how) {
        return String.valueOf(engine.executeQuery("CALL geo_cell('" + query + "', '" + how + "')")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void aGeospatialCellIsHandedOverAsSnowparkReadsIt() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE geo_cell(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA \
            RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$
            import com.snowflake.snowpark_java.*;
            import com.snowflake.snowpark_java.types.*;
            class H {
              public static String run(Session s, String q, String how) {
                Row r = s.sql(q).collect()[0];
                try {
                  switch (how) {
                    case "get": Object o = r.get(0); return "O:" + o.getClass().getName() + "=" + o;
                    case "getGeography": Geography g = r.getGeography(0); return "G:" + g;
                    case "getGeometry": Geometry m = r.getGeometry(0); return "M:" + m;
                    case "getString": return "S:" + r.getString(0);
                    case "getVariant": return "V:" + r.getVariant(0);
                    case "getLong": return "L:" + r.getLong(0);
                    case "toList": return "T:" + r.toList();
                    case "row": return "R:" + r;
                    default: return "?";
                  }
                } catch (Exception e) {
                  String m = String.valueOf(e.getMessage());
                  int p = m.indexOf(" (");
                  return "E:" + e.getClass().getName() + ": " + (p < 0 ? m : m.substring(0, p));
                }
              }
            }
            $$""");
        assertEquals("G:{\"coordinates\":[1,2],\"type\":\"Point\"}",
            call("SELECT TO_GEOGRAPHY(''POINT(1 2)'')", "getGeography"));
        assertEquals("O:com.snowflake.snowpark_java.types.Geography={\"coordinates\":[1,2],\"type\":\"Point\"}",
            call("SELECT TO_GEOGRAPHY(''POINT(1 2)'')", "get"));
        assertEquals("S:{\"coordinates\":[1,2],\"type\":\"Point\"}",
            call("SELECT TO_GEOGRAPHY(''POINT(1 2)'')", "getString"));
        assertEquals("M:{\"coordinates\":[1.000000000000000e+00,2.000000000000000e+00],\"type\":\"Point\"}",
            call("SELECT TO_GEOMETRY(''POINT(1 2)'')", "getGeometry"));
        assertEquals("O:com.snowflake.snowpark_java.types.Geometry={\"coordinates\":[1.000000000000000e+00,2.0000000"
                + "00000000e+00],\"type\":\"Point\"}",
            call("SELECT TO_GEOMETRY(''POINT(1 2)'')", "get"));
        assertEquals("E:java.lang.ClassCastException: class com.snowflake.snowpark.types.Geography cannot be cast "
                + "to class com.snowflake.snowpark.types.Geometry",
            call("SELECT TO_GEOGRAPHY(''POINT(1 2)'')", "getGeometry"));
        assertEquals("E:java.lang.ClassCastException: class com.snowflake.snowpark.types.Geometry cannot be cast t"
                + "o class com.snowflake.snowpark.types.Geography",
            call("SELECT TO_GEOMETRY(''POINT(1 2)'')", "getGeography"));
        assertEquals("R:Row[{\"coordinates\":[1,2],\"type\":\"Point\"}]",
            call("SELECT TO_GEOGRAPHY(''POINT(1 2)'')", "row"));
        assertEquals("V:{\"coordinates\":[1,2],\"type\":\"Point\"}",
            call("SELECT TO_GEOGRAPHY(''POINT(1 2)'')", "getVariant"));
        assertEquals("S:{\"coordinates\":[1.000000000000000e+00,2.000000000000000e+00],\"type\":\"Point\"}",
            call("SELECT TO_GEOMETRY(''POINT(1 2)'')", "getString"));
        assertEquals("T:[{\"coordinates\":[1,2],\"type\":\"Point\"}]",
            call("SELECT TO_GEOGRAPHY(''POINT(1 2)'')", "toList"));
        assertEquals("E:com.snowflake.snowpark.SnowparkClientException: Error Code: 0400, Error message: Cannot ca"
                + "st com.snowflake.snowpark.types.Geography({\"coordinates\":[1,2],\"type\":\"Point\"}) to Long.",
            call("SELECT TO_GEOGRAPHY(''POINT(1 2)'')", "getLong"));
        assertEquals("S:{\"coordinates\":[[0,0],[1.5,2]],\"type\":\"LineString\"}",
            call("SELECT TO_GEOGRAPHY(''LINESTRING(0 0, 1.5 2)'')", "getString"));
        assertEquals("S:{\"coordinates\":[[0.000000000000000e+00,0.000000000000000e+00],[1.500000000000000e+00,2.000"
                + "000000000000e+00]],\"type\":\"LineString\"}",
            call("SELECT TO_GEOMETRY(''LINESTRING(0 0, 1.5 2)'')", "getString"));
        assertEquals("S:{\"coordinates\":[-1.250000000000000e+00,1.000000000000000e+20],\"type\":\"Point\"}",
            call("SELECT TO_GEOMETRY(''POINT(-1.25 1e20)'')", "getString"));
    }
}
