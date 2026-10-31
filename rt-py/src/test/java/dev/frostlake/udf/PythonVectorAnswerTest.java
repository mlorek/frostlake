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
 * A Python handler's VECTOR answer is a list or what answers tolist() of the declared length, each element
 * converted by int(), which truncates, or float(), a FLOAT held to 32 bits; a tuple or a text is no vector, a None
 * element is refused, and a conversion's own failure is "Invalid element in returned vector" (all live-verified).
 */
public class PythonVectorAnswerTest extends MeasuredCellsTestSupport {

    @Test
    public void aVectorAnswerIsConvertedElementByElement() {
        assertAnswer("Function VI successfully created.",
            "CREATE OR REPLACE FUNCTION vi(k INT) RETURNS VECTOR(INT,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
                + "HANDLER = 'h' AS $$\nimport array\ndef h(k):\n    return [[1, 2], (3, 4), "
                + "memoryview(array.array('i', [5, 6])), [1, 2, 3], [1.5, 2], 'abc', None, [2.5, -1.5], [1.7, -1.7], "
                + "[0.5, -0.5], ['1', '2'], [None, 1], [True, False], [[1], [2]], [1], {'a': 1}, b'ab', range(1, 3), "
                + "array.array('d', [1.5, 2.5]), [1e20, 1], [float('nan'), 1], [2147483648, 1], [3.99999, "
                + "-3.99999]][k]\n$$");
        assertAnswer("Function VF successfully created.",
            "CREATE OR REPLACE FUNCTION vf(k INT) RETURNS VECTOR(FLOAT,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
                + "HANDLER = 'h' AS $$\nimport array\ndef h(k):\n    return [[1, 2], [1.5, '2.5'], [None, 1.0], (1.0,"
                + " 2.0), [1.0], [True, 2], [float('inf'), 1], [1e300, 1], memoryview(array.array('f', [1.5, "
                + "2.5]))][k]\n$$");
        assertAnswer("[1,2]", "SELECT vi(0)");
        assertAnswer("REFUSED Vectors returned from Python must be lists or array-like in function VI with handler h",
            "SELECT vi(1)");
        assertAnswer("[5,6]", "SELECT vi(2)");
        assertAnswer("REFUSED Returned list dimension, 3, does not match the expected vector dimension, 2 in function "
                + "VI with handler h",
            "SELECT vi(3)");
        assertAnswer("[1,2]", "SELECT vi(4)");
        assertAnswer("REFUSED Vectors returned from Python must be lists or array-like in function VI with handler h",
            "SELECT vi(5)");
        assertAnswer("null", "SELECT vi(6)");
        assertAnswer("[2,-1]", "SELECT vi(7)");
        assertAnswer("[1,-1]", "SELECT vi(8)");
        assertAnswer("[0,0]", "SELECT vi(9)");
        assertAnswer("[1,2]", "SELECT vi(10)");
        assertAnswer("REFUSED Vector elements cannot be 'None' in function VI with handler h", "SELECT vi(11)");
        assertAnswer("[1,0]", "SELECT vi(12)");
        answer("SELECT vi(13)");
        assertAnswer("REFUSED Returned list dimension, 1, does not match the expected vector dimension, 2 in function "
                + "VI with handler h",
            "SELECT vi(14)");
        assertAnswer("REFUSED Vectors returned from Python must be lists or array-like in function VI with handler h",
            "SELECT vi(15)");
        assertAnswer("REFUSED Vectors returned from Python must be lists or array-like in function VI with handler h",
            "SELECT vi(16)");
        assertAnswer("REFUSED Vectors returned from Python must be lists or array-like in function VI with handler h",
            "SELECT vi(17)");
        assertAnswer("[1,2]", "SELECT vi(18)");
        assertAnswer("REFUSED Invalid element in returned vector: Python Interpreter Error: | OverflowError: Python "
                + "int too large to convert to C long in function VI with handler h",
            "SELECT vi(19)");
        assertAnswer("REFUSED Invalid element in returned vector: Python Interpreter Error: | ValueError: cannot "
                + "convert float NaN to integer in function VI with handler h",
            "SELECT vi(20)");
        assertAnswer("REFUSED Invalid element in returned vector: OverflowError: int too big to convert to int32 in "
                + "function VI with handler h",
            "SELECT vi(21)");
        assertAnswer("[3,-3]", "SELECT vi(22)");
        assertAnswer("[1.000000,2.000000]", "SELECT vf(0)");
        assertAnswer("[1.500000,2.500000]", "SELECT vf(1)");
        assertAnswer("REFUSED Vector elements cannot be 'None' in function VF with handler h", "SELECT vf(2)");
        assertAnswer("REFUSED Vectors returned from Python must be lists or array-like in function VF with handler h",
            "SELECT vf(3)");
        assertAnswer("REFUSED Returned list dimension, 1, does not match the expected vector dimension, 2 in function "
                + "VF with handler h",
            "SELECT vf(4)");
        assertAnswer("[1.000000,2.000000]", "SELECT vf(5)");
        answer("SELECT vf(6)");
        answer("SELECT vf(7)");
        assertAnswer("[1.500000,2.500000]", "SELECT vf(8)");
        assertAnswer("VECTOR(INT, 2)[LOB]|VECTOR(FLOAT, 2)[LOB]", "SELECT SYSTEM$TYPEOF(vi(0)), SYSTEM$TYPEOF(vf(0))");
    }
}
