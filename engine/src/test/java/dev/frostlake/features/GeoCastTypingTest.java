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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A cast to GEOGRAPHY or GEOMETRY: a NULL cast is typed as a geospatial column is - grouped, sorted, converted and
 * described as one - and every typed source a cast into a geospatial type is refused (all live-verified).
 */
public class GeoCastTypingTest extends MeasuredCellsTestSupport {

    @Test
    public void nullCastsAreTyped() {
        assertAnswer("VARIANT[LOB]|VARIANT[LOB]",
            "SELECT SYSTEM$TYPEOF(NULL::GEOMETRY), SYSTEM$TYPEOF(NULL::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOMETRY)] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT SYSTEM$TYPEOF('POINT(1 2)'::GEOMETRY), SYSTEM$TYPEOF(CAST('POINT(1 2)' AS GEOGRAPHY))");
        answer("SELECT NULL::GEOMETRY AS g");
        answer("SELECT NULL::GEOGRAPHY AS h");
        answer("SELECT CAST(NULL AS GEOGRAPHY) AS h");
        assertShape("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOMETRY)] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT 'POINT(1 2)'::GEOMETRY AS g");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOMETRY)] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT COUNT(*) FROM (SELECT 1 AS x) GROUP BY 'POINT(1 2)'::GEOMETRY");
        assertAnswer("REFUSED Expressions of type GEOGRAPHY cannot be used as GROUP BY keys",
            "SELECT COUNT(*) FROM (SELECT 1 AS x) GROUP BY NULL::GEOGRAPHY");
        assertAnswer("true", "SELECT NULL::GEOMETRY IS NULL");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TO_VARCHAR(UNDERLYING_TO_UDT(NULL))] for "
                + "parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR(NULL::GEOMETRY)");
    }

    @Test
    public void castsIntoGeospatialTypes() {
        engine.execute("CREATE OR REPLACE PROCEDURE java_schema(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
            + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
            + "String q) {\n    return s.sql(q).schema().toString();\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE TABLE gsrc (s VARCHAR)");
        engine.execute("INSERT INTO gsrc VALUES ('POINT(1 2)')");
        assertAnswer("StructType[StructField(G, GeometryType, Nullable = true), StructField(H, GeographyType, "
                + "Nullable = true)]",
            "CALL java_schema('SELECT NULL::GEOMETRY AS g, NULL::GEOGRAPHY AS h')");
        assertAnswer("StructType[StructField(H, GeographyType, Nullable = true)]",
            "CALL java_schema('SELECT CAST(NULL AS GEOGRAPHY) AS h')");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(1 AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT CAST(1 AS GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(PARSE_JSON('{\"type\":\"Point\","
                + "\"coordinates\":[1,2]}') AS GEOGRAPHY)] for parameter 'TO_GEOGRAPHY'",
            "SELECT CAST(PARSE_JSON('{\"type\":\"Point\",\"coordinates\":[1,2]}') AS GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(OBJECT_CONSTRUCT('type', 'Point', "
                + "'coordinates', ARRAY_CONSTRUCT(1, 2)) AS GEOMETRY)] for parameter 'TO_GEOMETRY'",
            "SELECT CAST(OBJECT_CONSTRUCT('type', 'Point', 'coordinates', ARRAY_CONSTRUCT(1, 2)) AS GEOMETRY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type "
                + "[CAST(TO_BINARY('0101000000000000000000F03F0000000000000040') AS GEOMETRY)] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT CAST(TO_BINARY('0101000000000000000000F03F0000000000000040') AS GEOMETRY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TRY_CAST('POINT(1 2)')] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT TRY_CAST('POINT(1 2)' AS GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(SYSTEM$NULL_TO_TEXT(NULL) AS GEOGRAPHY)] "
                + "for parameter 'TO_GEOGRAPHY'",
            "SELECT CAST(NULL::VARCHAR AS GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(GSRC.S AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT s::GEOGRAPHY FROM gsrc");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TO_VARCHAR(UNDERLYING_TO_UDT(NULL))] for "
                + "parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR(CAST(NULL AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(UNDERLYING_TO_UDT(NULL) AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT CAST(NULL::GEOMETRY AS VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'UPPER': (GEOGRAPHY)",
            "SELECT UPPER(NULL::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 22 | Invalid argument types for "
                + "function '+': (GEOMETRY, NUMBER(1,0))",
            "SELECT NULL::GEOMETRY + 1");
        assertAnswer("REFUSED Expressions of type GEOGRAPHY cannot be used as ORDER BY keys",
            "SELECT 1 AS x ORDER BY NULL::GEOGRAPHY");
        assertAnswer("REFUSED Expressions of type GEOMETRY cannot be used as GROUP BY keys",
            "SELECT COUNT(*) FROM (SELECT 1 AS x) GROUP BY CAST(NULL AS GEOMETRY)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 23 | Invalid argument types for "
                + "function '=': (GEOGRAPHY, GEOGRAPHY)",
            "SELECT NULL::GEOGRAPHY = NULL::GEOGRAPHY");
        assertAnswer("REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support GEOGRAPHY argument "
                + "type",
            "SELECT ARRAY_CONSTRUCT(NULL::GEOGRAPHY)");
        assertAnswer("null", "SELECT NULL::GEOGRAPHY::GEOGRAPHY");
        engine.execute("CREATE OR REPLACE TABLE gct AS SELECT NULL::GEOGRAPHY AS h, NULL::GEOMETRY AS g");
        assertAnswer("H|GEOGRAPHY|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; G|GEOMETRY|COLUMN|Y|null|N|N|"
                + "null|null|null|null|null|null",
            "DESCRIBE TABLE gct");
        assertAnswer("false|true", "SELECT NULL::GEOGRAPHY IS NOT NULL, CAST(NULL AS GEOMETRY) IS NULL");
        assertAnswer("1", "SELECT COUNT(*) FROM (SELECT NULL::GEOGRAPHY AS h)");
        assertAnswer("true", "SELECT IFF(TRUE, NULL::GEOGRAPHY, NULL) IS NULL");
    }
}
