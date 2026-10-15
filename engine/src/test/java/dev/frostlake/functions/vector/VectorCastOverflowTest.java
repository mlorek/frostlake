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
 * A cast to a VECTOR refuses an element too wide for its element type rather than storing a cut or infinite one:
 * past float32's greatest value for FLOAT, past 32 bits for INT. A FLOAT NaN or infinity is kept and stays readable.
 * A refused cast keeps its own sentence even where it stops an INSERT, outside the DML envelope (live-verified).
 */
public class VectorCastOverflowTest extends MeasuredCellsTestSupport {

    private static final String FLOAT_OVERFLOW =
        "REFUSED Array-like value being cast to a float vector has elements that overflow";
    private static final String INT_OVERFLOW =
        "REFUSED Array-like value being cast to an integer vector has elements that overflow";

    @Test
    public void anElementTooWideIsRefused() {
        assertCells(new String[][] {
            {"SELECT [1e39, 1]::VECTOR(FLOAT, 2)", FLOAT_OVERFLOW},
            {"SELECT [1, 3.5e38]::VECTOR(FLOAT, 2)", FLOAT_OVERFLOW},
            {"SELECT [3.4028235e38, 1]::VECTOR(FLOAT, 2)", FLOAT_OVERFLOW},
            {"SELECT [1, 2147483648]::VECTOR(INT, 2)", INT_OVERFLOW},
            {"SELECT [9007199254740993,0,0]::VECTOR(INT,3)", INT_OVERFLOW},
            {"SELECT [-2147483649, 0]::VECTOR(INT, 2)", INT_OVERFLOW},
            {"SELECT [2147483647, -2147483648]::VECTOR(INT, 2)", "[2147483647,-2147483648]"},
        });
    }

    @Test
    public void aRefusedCastIsNotWrappedInTheDmlEnvelope() {
        try {
            assertCells(new String[][] {
                {"CREATE OR REPLACE TABLE vn (id INT, v VECTOR(FLOAT, 2))", "Table VN successfully created."},
                {"CREATE OR REPLACE TABLE vi (v VECTOR(INT, 2))", "Table VI successfully created."},
                {"CREATE OR REPLACE TABLE src (a ARRAY)", "Table SRC successfully created."},
                {"INSERT INTO src SELECT [1e39, 1]", "1"},
                {"INSERT INTO vn SELECT 2, a::VECTOR(FLOAT, 2) FROM src", FLOAT_OVERFLOW},
                {"INSERT INTO vn SELECT 2, [1e39, 1]::VECTOR(FLOAT, 2)", FLOAT_OVERFLOW},
                {"INSERT INTO vn SELECT 2, ['a', 1]::VECTOR(FLOAT, 2)",
                    "REFUSED Array-like value being cast to a float vector has elements that are not real numbers"},
                {"INSERT INTO vn SELECT 2, [1, 2, 3]::VECTOR(FLOAT, 2)", "REFUSED Vector value being cast to a vector "
                    + "is not an array or vector, or has incorrect dimension or element type"},
                {"INSERT INTO vi SELECT [1.5, 1]::VECTOR(INT, 2)",
                    "REFUSED Array-like value being cast to an integer vector has elements that are not integers"},
                {"INSERT INTO vi SELECT [2147483648, 1]::VECTOR(INT, 2)", INT_OVERFLOW},
                {"SELECT COUNT(*) FROM vn", "0"},
            });
        } finally {
            engine.execute("DROP TABLE IF EXISTS vn");
            engine.execute("DROP TABLE IF EXISTS vi");
            engine.execute("DROP TABLE IF EXISTS src");
        }
    }

    @Test
    public void aNanOrAnInfiniteElementIsKept() {
        try {
            assertCells(new String[][] {
                {"SELECT [1, 'NaN'::FLOAT]::VECTOR(FLOAT, 2) IS NULL", "false"},
                {"SELECT VECTOR_L2_DISTANCE([1, 'NaN'::FLOAT]::VECTOR(FLOAT,2), [0,0]::VECTOR(FLOAT,2))", "NaN"},
                {"CREATE OR REPLACE TABLE vk (id INT, v VECTOR(FLOAT, 2))", "Table VK successfully created."},
                {"INSERT INTO vk SELECT 3, [1, 'NaN'::FLOAT]::VECTOR(FLOAT, 2)", "1"},
                {"SELECT COUNT(*), COUNT(v) FROM vk", "1|1"},
            });
            if (!isLiveSnowflake()) {
                // The text a non-finite element reads as; the JSON result format the account's tests read through
                // renders vectors in a fixed form of its own (see BaseVectorFunctionTest).
                assertAnswer("3|[1.000000,\"NaN\"]", "SELECT id, v FROM vk");
                assertAnswer("[1.000000,\"Infinity\"]", "SELECT [1, 'inf'::FLOAT]::VECTOR(FLOAT, 2)");
                assertAnswer("[1.000000,\"-Infinity\"]", "SELECT [1, '-inf'::FLOAT]::VECTOR(FLOAT, 2)");
            }
        } finally {
            engine.execute("DROP TABLE IF EXISTS vk");
        }
    }
}
