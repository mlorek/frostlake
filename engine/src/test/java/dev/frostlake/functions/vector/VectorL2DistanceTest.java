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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** VECTOR_L2_DISTANCE — every expectation measured on a real Snowflake account. */
public class VectorL2DistanceTest extends BaseVectorFunctionTest {

    @Test
    public void euclideanDistanceOfKnownVectors() {
        // Live: VECTOR_L2_DISTANCE([1,2,3],[4,5,6]) => 5.196152422706632 (= sqrt(27))
        assertFloat64(5.196152422706632, "SELECT VECTOR_L2_DISTANCE(" + V123 + ", " + V456 + ")");
        // Live: the same value for the VECTOR(INT,3) pair — the result stays FLOAT.
        assertFloat64(5.196152422706632, "SELECT VECTOR_L2_DISTANCE(" + I123 + ", " + I456 + ")");
        // Live: a vector against itself is 0.0
        assertFloat64(0.0, "SELECT VECTOR_L2_DISTANCE(" + V123 + ", " + V123 + ")");
        // Live: against the zero vector it is sqrt(14) = 3.7416573867739413
        assertFloat64(3.7416573867739413, "SELECT VECTOR_L2_DISTANCE([0,0,0]::VECTOR(FLOAT,3), " + V123 + ")");
    }

    @Test
    public void float32ElementsFeedFloat64Arithmetic() {
        // Live: over [0.1,0.2,0.3] and [0.4,0.5,0.6] => 0.5196152500135338 (not the exact-decimal
        // 0.5196152422706632), because the ELEMENTS are float32 while the arithmetic is float64.
        assertFloat64(0.5196152500135338, "SELECT VECTOR_L2_DISTANCE([0.1,0.2,0.3]::VECTOR(FLOAT,3), "
            + "[0.4,0.5,0.6]::VECTOR(FLOAT,3))");
    }

    @Test
    public void typedNullYieldsSqlNull() {
        // Live: VECTOR_L2_DISTANCE(NULL::VECTOR(FLOAT,3), v) => SQL NULL.
        assertNull(scalar("SELECT VECTOR_L2_DISTANCE(NULL::VECTOR(FLOAT,3), " + V123 + ")"));
    }

    @Test
    public void worksInWhereAndOrderBy() {
        engine.execute("CREATE TABLE l2_rows (id INTEGER, v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO l2_rows SELECT 1, " + V123);
        engine.execute("INSERT INTO l2_rows SELECT 2, " + V456);
        // Live: the WHERE form returns only row 1, and the ORDER BY form returns 1 then 2.
        assertEquals(1L, ((Number) scalar(
            "SELECT id FROM l2_rows WHERE VECTOR_L2_DISTANCE(v, " + V123 + ") < 1")).longValue());
        assertEquals(1L, ((Number) scalar(
            "SELECT id FROM l2_rows ORDER BY VECTOR_L2_DISTANCE(v, " + V123 + ")")).longValue());
    }

    @Test
    public void nestedVectorResultsStillTypeCheck() {
        // Live: VECTOR_TRUNC narrows the STATIC type to VECTOR(FLOAT, 2), so truncating both operands
        // is legal and gives sqrt(9+9) = 4.242640687119285 …
        assertFloat64(4.242640687119285, "SELECT VECTOR_L2_DISTANCE(VECTOR_TRUNC(" + V123
            + ", 2), VECTOR_TRUNC(" + V456 + ", 2))");
        // … while truncating only ONE side is the dimension mismatch, rejected at compile time.
        assertRejected("SELECT VECTOR_L2_DISTANCE(VECTOR_TRUNC(" + V123 + ", 2), " + V456 + ")");
    }

    @Test
    public void mismatchedOrNonVectorArgumentsAreRejected() {
        // Live: "Invalid argument types for function 'VECTOR_L2_DISTANCE': (VECTOR(FLOAT, 3),
        // VECTOR(INT, 3))" for mixed element types, and the same shape for the other three.
        assertRejected("SELECT VECTOR_L2_DISTANCE(" + V123 + ", " + I123 + ")");
        assertRejected("SELECT VECTOR_L2_DISTANCE([1,2]::VECTOR(FLOAT,2), " + V123 + ")");
        assertRejected("SELECT VECTOR_L2_DISTANCE([1,2,3], " + V123 + ")");
        assertRejected("SELECT VECTOR_L2_DISTANCE(" + V123 + ", NULL)");
    }
}
