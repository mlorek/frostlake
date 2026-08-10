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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** VECTOR_COSINE_SIMILARITY — every expectation measured on a real Snowflake account. */
public class VectorCosineSimilarityTest extends BaseVectorFunctionTest {

    @Test
    public void similarityOfKnownVectors() {
        // Live: VECTOR_COSINE_SIMILARITY([1,2,3],[1,2,3]) => 1.0 (exactly, not 0.9999999999999999)
        assertFloat64(1.0, "SELECT VECTOR_COSINE_SIMILARITY(" + V123 + ", " + V123 + ")");
        // Live: ... ([1,2,3],[4,5,6]) => 0.9746318461970762
        assertFloat64(0.9746318461970762, "SELECT VECTOR_COSINE_SIMILARITY(" + V123 + ", " + V456 + ")");
    }

    @Test
    public void intVectorsGiveTheSameFloatResult() {
        // Live: the VECTOR(INT,3) pair returns the identical double — INT operands do not change it.
        assertFloat64(0.9746318461970762, "SELECT VECTOR_COSINE_SIMILARITY(" + I123 + ", " + I456 + ")");
        // Live: VECTOR_COSINE_SIMILARITY([3,4,0]::VECTOR(INT,3), itself) => 1.0
        assertFloat64(1.0, "SELECT VECTOR_COSINE_SIMILARITY([3,4,0]::VECTOR(INT,3), [3,4,0]::VECTOR(INT,3))");
    }

    @Test
    public void float32ElementsFeedFloat64Arithmetic() {
        // Live: over [0.1,0.2,0.3] and [0.4,0.5,0.6] the account returns 0.9746318467275483 — the
        // value obtained by widening the FLOAT32 elements and accumulating in float64. The
        // exact-decimal answer would be 0.9746318461970762.
        assertFloat64(0.9746318467275483, "SELECT VECTOR_COSINE_SIMILARITY([0.1,0.2,0.3]::VECTOR(FLOAT,3), "
            + "[0.4,0.5,0.6]::VECTOR(FLOAT,3))");
    }

    @Test
    public void zeroVectorIsNaNRatherThanAnError() {
        // Live: VECTOR_COSINE_SIMILARITY([0,0,0], [1,2,3]) => NaN (the zero magnitude is not guarded).
        assertTrue(Double.isNaN(number(
            "SELECT VECTOR_COSINE_SIMILARITY([0,0,0]::VECTOR(FLOAT,3), " + V123 + ")")),
            "a zero-magnitude operand divides by zero, as on the account");
    }

    @Test
    public void typedNullYieldsSqlNull() {
        // Live: VECTOR_COSINE_SIMILARITY(NULL::VECTOR(FLOAT,3), v) => SQL NULL.
        assertNull(scalar("SELECT VECTOR_COSINE_SIMILARITY(NULL::VECTOR(FLOAT,3), " + V123 + ")"));
    }

    @Test
    public void mismatchedOrNonVectorArgumentsAreRejected() {
        // Live: all four are the compile error "Invalid argument types for function
        // 'VECTOR_COSINE_SIMILARITY': (…)" — a dimension mismatch, mixed element types, a plain
        // ARRAY and an untyped NULL.
        assertRejected("SELECT VECTOR_COSINE_SIMILARITY([1,2]::VECTOR(FLOAT,2), " + V123 + ")");
        assertRejected("SELECT VECTOR_COSINE_SIMILARITY(" + V123 + ", " + I123 + ")");
        assertRejected("SELECT VECTOR_COSINE_SIMILARITY([1,2,3], " + V123 + ")");
        assertRejected("SELECT VECTOR_COSINE_SIMILARITY(" + V123 + ", NULL)");
    }

    @Test
    public void argumentTypesAreCheckedOverZeroRows() {
        engine.execute("CREATE TABLE cos_empty (v VECTOR(FLOAT,3))");
        // Live: the rejection is COMPILE-time, so it fires over an empty table too —
        // "SELECT ... FROM vv WHERE VECTOR_L2_DISTANCE(v, [1,2]::VECTOR(FLOAT,2)) < 1" errors on an
        // empty table on the account.
        assertRejected("SELECT VECTOR_COSINE_SIMILARITY(v, [1,2]::VECTOR(FLOAT,2)) FROM cos_empty");
    }
}
