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
 * Casts into GEOGRAPHY and GEOMETRY from every kind of source: a TRY_CAST into a geospatial type is refused with
 * the geospatial parameter's sentence ahead of TRY_CAST's own argument-type one, and a constant geospatial value
 * casts to its own type, where a column's cast to it stays refused (all live-verified).
 */
public class GeoCastTargetTest extends MeasuredCellsTestSupport {

    @Test
    public void aCastIntoAGeospatialTypeFromEachSource() {
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT('POINT(1 2)'::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(PARSE_JSON('{\"type\":\"Point\","
                + "\"coordinates\":[1,2]}') AS GEOGRAPHY)] for parameter 'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(PARSE_JSON('{\"type\":\"Point\",\"coordinates\":[1,2]}')::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(OBJECT_CONSTRUCT('type', 'Point', "
                + "'coordinates', ARRAY_CONSTRUCT(1, 2)) AS GEOGRAPHY)] for parameter 'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(OBJECT_CONSTRUCT('type', 'Point', 'coordinates', [1, 2])::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type "
                + "[CAST(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX') AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX')::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(1 AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(1::GEOGRAPHY)");
        assertAnswer("null", "SELECT ST_ASWKT(NULL::GEOGRAPHY)");
        assertAnswer("POINT(1 2)", "SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(1 2)')::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(TO_GEOMETRY('POINT(1 2)') AS GEOGRAPHY)] "
                + "for parameter 'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(TO_GEOMETRY('POINT(1 2)')::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(ARRAY_CONSTRUCT(1, 2) AS GEOGRAPHY)] for "
                + "parameter 'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT([1, 2]::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(TRUE AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(TRUE::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOMETRY)] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT ST_ASWKT('POINT(1 2)'::GEOMETRY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(PARSE_JSON('{\"type\":\"Point\","
                + "\"coordinates\":[1,2]}') AS GEOMETRY)] for parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT(PARSE_JSON('{\"type\":\"Point\",\"coordinates\":[1,2]}')::GEOMETRY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(OBJECT_CONSTRUCT('type', 'Point', "
                + "'coordinates', ARRAY_CONSTRUCT(1, 2)) AS GEOMETRY)] for parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT(OBJECT_CONSTRUCT('type', 'Point', 'coordinates', [1, 2])::GEOMETRY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type "
                + "[CAST(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX') AS GEOMETRY)] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT ST_ASWKT(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX')::GEOMETRY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(1 AS GEOMETRY)] for parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT(1::GEOMETRY)");
        assertAnswer("null", "SELECT ST_ASWKT(NULL::GEOMETRY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(TO_GEOGRAPHY('POINT(1 2)') AS GEOMETRY)] "
                + "for parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(1 2)')::GEOMETRY)");
        assertAnswer("POINT(1 2)", "SELECT ST_ASWKT(TO_GEOMETRY('POINT(1 2)')::GEOMETRY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(ARRAY_CONSTRUCT(1, 2) AS GEOMETRY)] for "
                + "parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT([1, 2]::GEOMETRY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(TRUE AS GEOMETRY)] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT ST_ASWKT(TRUE::GEOMETRY)");
    }

    @Test
    public void aTryCastIntoAGeospatialTypeNamesTheGeospatialParameter() {
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(CAST('POINT(1 2)' AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TRY_CAST('POINT(1 2)')] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(TRY_CAST('POINT(1 2)' AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(PARSE_JSON('{\"type\":\"Point\","
                + "\"coordinates\":[1,2]}') AS GEOGRAPHY)] for parameter 'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(CAST(PARSE_JSON('{\"type\":\"Point\",\"coordinates\":[1,2]}') AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TRY_CAST(PARSE_JSON('{\"type\":\"Point\","
                + "\"coordinates\":[1,2]}'))] for parameter 'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(TRY_CAST(PARSE_JSON('{\"type\":\"Point\",\"coordinates\":[1,2]}') AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(OBJECT_CONSTRUCT('type', 'Point', "
                + "'coordinates', ARRAY_CONSTRUCT(1, 2)) AS GEOGRAPHY)] for parameter 'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(CAST(OBJECT_CONSTRUCT('type', 'Point', 'coordinates', [1, 2]) AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TRY_CAST(OBJECT_CONSTRUCT('type', 'Point', "
                + "'coordinates', ARRAY_CONSTRUCT(1, 2)))] for parameter 'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(TRY_CAST(OBJECT_CONSTRUCT('type', 'Point', 'coordinates', [1, 2]) AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type "
                + "[CAST(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX') AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(CAST(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX') AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type "
                + "[TRY_CAST(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX'))] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(TRY_CAST(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX') AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(1 AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(CAST(1 AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TRY_CAST(1)] for parameter 'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(TRY_CAST(1 AS GEOGRAPHY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOMETRY)] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT ST_ASWKT(CAST('POINT(1 2)' AS GEOMETRY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TRY_CAST('POINT(1 2)')] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT ST_ASWKT(TRY_CAST('POINT(1 2)' AS GEOMETRY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(PARSE_JSON('{\"type\":\"Point\","
                + "\"coordinates\":[1,2]}') AS GEOMETRY)] for parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT(CAST(PARSE_JSON('{\"type\":\"Point\",\"coordinates\":[1,2]}') AS GEOMETRY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TRY_CAST(PARSE_JSON('{\"type\":\"Point\","
                + "\"coordinates\":[1,2]}'))] for parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT(TRY_CAST(PARSE_JSON('{\"type\":\"Point\",\"coordinates\":[1,2]}') AS GEOMETRY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(OBJECT_CONSTRUCT('type', 'Point', "
                + "'coordinates', ARRAY_CONSTRUCT(1, 2)) AS GEOMETRY)] for parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT(CAST(OBJECT_CONSTRUCT('type', 'Point', 'coordinates', [1, 2]) AS GEOMETRY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TRY_CAST(OBJECT_CONSTRUCT('type', 'Point', "
                + "'coordinates', ARRAY_CONSTRUCT(1, 2)))] for parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT(TRY_CAST(OBJECT_CONSTRUCT('type', 'Point', 'coordinates', [1, 2]) AS GEOMETRY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type "
                + "[CAST(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX') AS GEOMETRY)] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT ST_ASWKT(CAST(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX') AS GEOMETRY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type "
                + "[TRY_CAST(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX'))] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT ST_ASWKT(TRY_CAST(TO_BINARY('0101000000000000000000F03F0000000000000040', 'HEX') AS GEOMETRY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(1 AS GEOMETRY)] for parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT(CAST(1 AS GEOMETRY))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TRY_CAST(1)] for parameter 'TO_GEOMETRY'",
            "SELECT ST_ASWKT(TRY_CAST(1 AS GEOMETRY))");
    }

    @Test
    public void aGeospatialColumnTakesItsValuesAsLiveDoes() {
        assertAnswer("Table GS successfully created.", "CREATE OR REPLACE TABLE gs (s VARCHAR, v VARIANT)");
        assertAnswer("1",
            "INSERT INTO gs SELECT 'POINT(1 2)', PARSE_JSON('{\"type\":\"Point\",\"coordinates\":[1,2]}')");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(GS.S AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(s::GEOGRAPHY) FROM gs");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(GS.V AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT ST_ASWKT(v::GEOGRAPHY) FROM gs");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(GS.S AS GEOMETRY)] for parameter "
                + "'TO_GEOMETRY'",
            "SELECT ST_ASWKT(s::GEOMETRY) FROM gs");
        assertAnswer("Table GG successfully created.", "CREATE OR REPLACE TABLE gg (g GEOGRAPHY, m GEOMETRY)");
        assertAnswer("1", "INSERT INTO gg (g, m) VALUES ('POINT(1 2)', 'POINT(3 4)')");
        assertAnswer("1", "INSERT INTO gg (g, m) SELECT s, s FROM gs");
        assertAnswer("1", "INSERT INTO gg (g) SELECT v FROM gs");
        assertAnswer("POINT(1 2)|POINT(3 4) ;; POINT(1 2)|POINT(1 2) ;; POINT(1 2)|null",
            "SELECT ST_ASWKT(g), ST_ASWKT(m) FROM gg ORDER BY 1");
        assertAnswer("3|0", "UPDATE gg SET g = 'POINT(5 6)'");
        assertAnswer("POINT(5 6) ;; POINT(5 6) ;; POINT(5 6)", "SELECT ST_ASWKT(g) FROM gg ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT 'POINT(1 2)'::GEOGRAPHY");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST(TO_GEOGRAPHY('POINT(1 2)') AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT TO_GEOGRAPHY('POINT(1 2)')::VARCHAR");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT SYSTEM$TYPEOF('POINT(1 2)'::GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT IFF(TRUE, 'POINT(1 2)'::GEOGRAPHY, NULL)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST('POINT(1 2)' AS GEOGRAPHY)] for parameter "
                + "'TO_GEOGRAPHY'",
            "SELECT 'POINT(1 2)'::GEOGRAPHY FROM gs WHERE FALSE");
    }
}
