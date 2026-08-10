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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/** VECTOR_L1_DISTANCE — every expectation measured on a real Snowflake account. */
public class VectorL1DistanceTest extends BaseVectorFunctionTest {

    @Test
    public void manhattanDistanceOfKnownVectors() {
        // Live: VECTOR_L1_DISTANCE([1,2,3],[4,5,6]) => 9.0 (3+3+3)
        assertFloat64(9.0, "SELECT VECTOR_L1_DISTANCE(" + V123 + ", " + V456 + ")");
        // Live: the VECTOR(INT,3) pair gives the same 9.0
        assertFloat64(9.0, "SELECT VECTOR_L1_DISTANCE(" + I123 + ", " + I456 + ")");
        assertFloat64(0.0, "SELECT VECTOR_L1_DISTANCE(" + V123 + ", " + V123 + ")");
    }

    @Test
    public void float32ElementsAreAccumulatedInFloat64() {
        // THE precision test. Live: VECTOR_L1_DISTANCE([0.1,0.2,0.3],[0.4,0.5,0.6]) is
        // 0.9000000134110451, which is EXACTLY the float64 sum of the three float64-widened float32
        // differences. Accumulating in float32 instead would round the answer to 0.90000004.
        assertFloat64(0.9000000134110451, "SELECT VECTOR_L1_DISTANCE([0.1,0.2,0.3]::VECTOR(FLOAT,3), "
            + "[0.4,0.5,0.6]::VECTOR(FLOAT,3))");
    }

    @Test
    public void elementsThemselvesAreFloat32() {
        // Live: VECTOR_L1_DISTANCE([3.14159265358979,0,0], [0,0,0]) is 3.1415927410125732 — the
        // float64 value OF the float32 pi, not the float64 pi 3.14159265358979.
        assertFloat64(3.1415927410125732, "SELECT VECTOR_L1_DISTANCE([3.14159265358979,0,0]::VECTOR(FLOAT,3), "
            + "[0,0,0]::VECTOR(FLOAT,3))");
    }

    @Test
    public void typedNullYieldsSqlNull() {
        assertNull(scalar("SELECT VECTOR_L1_DISTANCE(" + V123 + ", NULL::VECTOR(FLOAT,3))"));
    }

    @Test
    public void mismatchedOrNonVectorArgumentsAreRejected() {
        // Live: "Invalid argument types for function 'VECTOR_L1_DISTANCE': (ARRAY, VECTOR(FLOAT, 3))"
        assertRejected("SELECT VECTOR_L1_DISTANCE([1,2,3], " + V123 + ")");
        assertRejected("SELECT VECTOR_L1_DISTANCE(" + V123 + ", " + I123 + ")");
        assertRejected("SELECT VECTOR_L1_DISTANCE([1,2]::VECTOR(FLOAT,2), " + V123 + ")");
        assertRejected("SELECT VECTOR_L1_DISTANCE(NULL, " + V123 + ")");
    }
}
