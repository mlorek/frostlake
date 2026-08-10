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

/** VECTOR_NORMALIZE — every expectation measured on a real Snowflake account. */
public class VectorNormalizeTest extends BaseVectorFunctionTest {

    @Test
    public void unitVectorElementsAreFloat32() {
        // THE precision test for vector RESULTS. Live: VECTOR_NORMALIZE([1,2,3]::VECTOR(FLOAT,3)) is
        // [0.26726124,0.5345225,0.80178374] — the float64 quotients NARROWED to float32 elements. The
        // float64 rendering would be [0.2672612419124244,…], wrong from the 8th digit on.
        assertVector("[0.26726124,0.5345225,0.80178374]", "SELECT VECTOR_NORMALIZE(" + V123 + ")");
        // Live: VECTOR_NORMALIZE([1,1,1]) is [0.57735026,…] — float32 of 1/sqrt(3), whose float64
        // value is 0.5773502691896258.
        assertVector("[0.57735026,0.57735026,0.57735026]", "SELECT VECTOR_NORMALIZE([1,1,1]::VECTOR(FLOAT,3))");
        // Live: negative components keep their sign — [-1,-2,-2] normalizes to
        // [-0.33333334,-0.6666667,-0.6666667].
        assertVector("[-0.33333334,-0.6666667,-0.6666667]", "SELECT VECTOR_NORMALIZE([-1,-2,-2]::VECTOR(FLOAT,3))");
    }

    @Test
    public void magnitudeIsComputedInFloat64() {
        // Live: VECTOR_NORMALIZE([1e-30,2e-30,2e-30]::VECTOR(FLOAT,3)) is
        // [0.33333334,0.6666667,0.6666667]. Float32 arithmetic could not produce that at all — the
        // squares underflow to zero there — so the magnitude is accumulated in float64. The same
        // property at a literal-expressible magnitude:
        assertVector("[0.33333334,0.6666667,0.6666667]", "SELECT VECTOR_NORMALIZE([0.00000000000000000001,0.00000000000000000002,"
                + "0.00000000000000000002]::VECTOR(FLOAT,3))");
    }

    @Test
    public void intVectorNormalizesToAFloatVector() {
        // Live: SYSTEM$TYPEOF(VECTOR_NORMALIZE([1,2,3]::VECTOR(INT,3))) is VECTOR(FLOAT, 3), and the
        // value is the same unit vector as for the FLOAT input.
        assertVector("[0.26726124,0.5345225,0.80178374]", "SELECT VECTOR_NORMALIZE(" + I123 + ")");
    }

    @Test
    public void zeroVectorNormalizesToZeroNotNaN() {
        // Live: VECTOR_NORMALIZE([0,0,0]::VECTOR(FLOAT,3)) is [0.0,0.0,0.0] — NOT NaN, unlike
        // VECTOR_COSINE_SIMILARITY against a zero vector.
        assertVector("[0.0,0.0,0.0]", "SELECT VECTOR_NORMALIZE([0,0,0]::VECTOR(FLOAT,3))");
    }

    @Test
    public void typedNullYieldsSqlNull() {
        // Live: VECTOR_NORMALIZE(NULL::VECTOR(FLOAT,3)) is SQL NULL, while the untyped NULL below is
        // a compile error — the difference the strict typing exists for.
        assertNull(scalar("SELECT VECTOR_NORMALIZE(NULL::VECTOR(FLOAT,3))"));
    }

    @Test
    public void nonVectorArgumentsAreRejected() {
        // Live: "Invalid argument types for function 'VECTOR_NORMALIZE': (ARRAY)" and "… (NULL)".
        assertRejected("SELECT VECTOR_NORMALIZE([1,2,3])");
        assertRejected("SELECT VECTOR_NORMALIZE(NULL)");
        assertRejected("SELECT VECTOR_NORMALIZE(1)");
        assertRejected("SELECT VECTOR_NORMALIZE('abc')");
    }
}
