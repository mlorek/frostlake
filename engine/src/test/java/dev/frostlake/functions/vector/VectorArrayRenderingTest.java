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

package dev.frostlake.functions.vector;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A VECTOR read as an ARRAY holds its elements as DOUBLEs, a FLOAT vector's each the double its 32-bit element
 * widens to and an INT vector's whole numbers (live-verified): they render, convert and compare as every other
 * DOUBLE inside a VARIANT does — {@code 1.000000000000000e+00} in the JSON result, a bare NaN and Infinity — and an
 * ARRAY cast back to a VECTOR converts, compares and is refused as a VECTOR from any other source is.
 */
public class VectorArrayRenderingTest extends MeasuredCellsTestSupport {

    @Test
    public void aVectorReadAsAnArrayHoldsDoubles() {
        engine.execute("CREATE OR REPLACE TABLE vn (v VECTOR(FLOAT, 2))");
        engine.execute("INSERT INTO vn SELECT [1, 2]::VECTOR(FLOAT, 2)");
        engine.execute("CREATE OR REPLACE TABLE vi (v VECTOR(INT, 2))");
        engine.execute("INSERT INTO vi SELECT [1, 2]::VECTOR(INT, 2)");
        assertCells(new String[][] {
            {"SELECT v::ARRAY FROM vn", "[1.000000000000000e+00,2.000000000000000e+00]"},
            {"SELECT TYPEOF(GET(v::ARRAY, 0)) FROM vn", "DOUBLE"},
            {"SELECT v::ARRAY::VARCHAR FROM vn", "[1.000000000000000e+00,2.000000000000000e+00]"},
            {"SELECT TO_JSON(v::ARRAY) FROM vn", "[1.000000000000000e+00,2.000000000000000e+00]"},
            {"SELECT GET(v::ARRAY, 0) FROM vn", "1.0"},
            {"SELECT GET(v::ARRAY, 0)::VARCHAR FROM vn", "1"},
            {"SELECT GET(v::ARRAY, 0)::FLOAT FROM vn", "1.0"},
            {"SELECT [1, 'NaN'::FLOAT]::VECTOR(FLOAT, 2)::ARRAY", "[1.000000000000000e+00,NaN]"},
            {"SELECT TYPEOF(GET([1, 'NaN'::FLOAT]::VECTOR(FLOAT, 2)::ARRAY, 1))", "DOUBLE"},
            {"SELECT TO_JSON([1, 'NaN'::FLOAT]::VECTOR(FLOAT, 2)::ARRAY)", "[1.000000000000000e+00,NaN]"},
            {"SELECT [1, 'inf'::FLOAT]::VECTOR(FLOAT, 2)::ARRAY", "[1.000000000000000e+00,Infinity]"},
            {"SELECT [1, '-inf'::FLOAT]::VECTOR(FLOAT, 2)::ARRAY::VARCHAR", "[1.000000000000000e+00,-Infinity]"},
            {"SELECT [-0.0, 1.5]::VECTOR(FLOAT, 2)::ARRAY", "[0.000000000000000e+00,1.500000000000000e+00]"},
            {"SELECT [0.1, -2.25]::VECTOR(FLOAT, 2)::ARRAY", "[1.000000014901161e-01,-2.250000000000000e+00]"},
            {"SELECT [0.1, -2.25]::VECTOR(FLOAT, 2)::ARRAY::VARCHAR", "[1.000000014901161e-01,-2.250000000000000e+00]"},
            {"SELECT [1e20, 1e-7]::VECTOR(FLOAT, 2)::ARRAY", "[1.000000020040877e+20,1.000000011686097e-07]"},
            {"SELECT [1e20, 1e-7]::VECTOR(FLOAT, 2)::ARRAY::VARCHAR", "[1.000000020040877e+20,1.000000011686097e-07]"},
            {"SELECT [123456789, 3.4e38]::VECTOR(FLOAT, 2)::ARRAY::VARCHAR",
                "[1.234567920000000e+08,3.399999952144364e+38]"},
            {"SELECT v::ARRAY FROM vi", "[1,2]"},
            {"SELECT TYPEOF(GET(v::ARRAY, 0)) FROM vi", "INTEGER"},
            {"SELECT v::ARRAY::VARCHAR FROM vi", "[1,2]"},
            {"SELECT TO_ARRAY(v) FROM vn", "[1.000000000000000e+00,2.000000000000000e+00]"},
            {"SELECT TYPEOF(GET(TO_ARRAY(v), 0)) FROM vn", "DOUBLE"},
            {"SELECT ARRAY_SIZE(v::ARRAY), v::ARRAY[1] FROM vn", "2|2.0"},
            {"SELECT v::ARRAY = [1, 2] FROM vn", "true"},
            {"SELECT ARRAY_CONTAINS(1::VARIANT, v::ARRAY), ARRAY_CONTAINS(1.0::FLOAT::VARIANT, v::ARRAY) FROM vn",
                "true|true"},
            {"SELECT f.value, TYPEOF(f.value) FROM vn, LATERAL FLATTEN(v::ARRAY) f ORDER BY f.index",
                "1.0|DOUBLE ;; 2.0|DOUBLE"},
            {"SELECT OBJECT_CONSTRUCT('v', v::ARRAY) FROM vn", "{\"v\":[1.000000000000000e+00,2.000000000000000e+00]}"},
            {"SELECT v::ARRAY::VARIANT FROM vn", "[1.000000000000000e+00,2.000000000000000e+00]"},
            {"SELECT TYPEOF(v::ARRAY::VARIANT) FROM vn", "ARRAY"},
            {"SELECT v::VARIANT FROM vn", "[1.000000000000000e+00,2.000000000000000e+00]"},
            {"SELECT v::VARCHAR FROM vn",
                "REFUSED SQL compilation error: | invalid type [CAST(VN.V AS VARCHAR(134217728))] for parameter "
                + "'TO_VARCHAR'"},
        });
    }

    @Test
    public void aVectorFromAnyArrayConvertsAlike() {
        engine.execute("CREATE OR REPLACE TABLE vd (v VECTOR(FLOAT, 2), w VECTOR(INT, 2), a ARRAY)");
        engine.execute("INSERT INTO vd SELECT [1, 2.5]::VECTOR(FLOAT, 2), [1, 2]::VECTOR(INT, 2), [1, 2.5]");
        assertCells(new String[][] {
            {"SELECT w FROM vd", "[1,2]"},
            {"SELECT [1, 2]::VECTOR(INT, 2)", "[1,2]"},
            {"SELECT a::VECTOR(INT, 2) FROM vd",
                "REFUSED Array-like value being cast to an integer vector has elements that are not integers"},
            {"SELECT a::VECTOR(FLOAT, 2)::VARCHAR FROM vd",
                "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(VD.A) AS VARCHAR(134217728))] for "
                + "parameter 'TO_VARCHAR'"},
            {"SELECT v::VARCHAR FROM vd",
                "REFUSED SQL compilation error: | invalid type [CAST(VD.V AS VARCHAR(134217728))] for parameter "
                + "'TO_VARCHAR'"},
            {"SELECT TYPEOF(a::VECTOR(FLOAT, 2)::VARIANT) FROM vd", "VECTOR"},
            {"SELECT a::VECTOR(FLOAT, 2) = v FROM vd", "true"},
            {"SELECT VECTOR_L2_DISTANCE(a::VECTOR(FLOAT, 2), v) FROM vd", "0.0"},
            {"CREATE OR REPLACE TABLE vd2 AS SELECT a::VECTOR(FLOAT, 2) AS x FROM vd",
                "Table VD2 successfully created."},
        });
    }
}
