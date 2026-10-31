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
 * A cast of an array with a missing element to a VECTOR - an ARRAY_CONSTRUCT or a bracket array holding NULL, an
 * ARRAY_INSERT past the end, a VARIANT or a column holding such an array - is refused as "Cannot convert an array
 * with missing elements to a vector", after a wrong length and before any element is judged; a JSON null is an
 * element, judged as one (all live-verified).
 */
public class VectorMissingElementTest extends MeasuredCellsTestSupport {

    @Test
    public void missingElements() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Table VA52 successfully created.", "CREATE OR REPLACE TABLE va52 (a ARRAY)");
        assertAnswer("1", "INSERT INTO va52 SELECT [1,2,NULL]");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "SELECT [1,2,NULL]::VECTOR(FLOAT,3)");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "SELECT VECTOR_TRUNC([1,2,NULL]::VECTOR(FLOAT,3), -2)");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "SELECT ARRAY_CONSTRUCT(1, 2, NULL)::VECTOR(FLOAT,3)");
        assertAnswer("REFUSED Array-like value being cast to a float vector has elements that are not real numbers",
            "SELECT PARSE_JSON('[1,2,null]')::VECTOR(FLOAT,3)");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "SELECT [1,2,NULL]::VECTOR(INT,3)");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "SELECT [1,'a',NULL]::VECTOR(FLOAT,3)");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "SELECT [NULL,'a',1]::VECTOR(FLOAT,3)");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "SELECT TO_VARIANT([1,2,NULL])::VECTOR(FLOAT,3)");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "SELECT a::VECTOR(FLOAT,3) FROM va52");
        assertAnswer("REFUSED Vector value being cast to a vector is not an array or vector, or has incorrect "
                + "dimension or element type",
            "SELECT [1,2,3,NULL]::VECTOR(FLOAT,3)");
        assertAnswer("REFUSED Vector value being cast to a vector is not an array or vector, or has incorrect "
                + "dimension or element type",
            "SELECT [1,NULL]::VECTOR(FLOAT,3)");
        assertAnswer("[1.000000,2.000000]", "SELECT ARRAY_CONSTRUCT_COMPACT(1,2,NULL)::VECTOR(FLOAT,2)");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "SELECT CAST([1,2,NULL] AS VECTOR(FLOAT,3))");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "SELECT ARRAY_INSERT([1,2], 3, 4)::VECTOR(FLOAT,4)");
        assertAnswer("Table VV52 successfully created.", "CREATE OR REPLACE TABLE vv52 (v VECTOR(FLOAT,3))");
        assertAnswer("REFUSED Cannot convert an array with missing elements to a vector. Compact the array to a dense "
                + "representation first using ARRAY_COMPACT.",
            "INSERT INTO vv52 SELECT [1,2,NULL]::VECTOR(FLOAT,3)");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(FLOAT, 3) but got ARRAY for column V",
            "INSERT INTO vv52 SELECT [1,2,NULL]");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
