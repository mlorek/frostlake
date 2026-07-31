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

/** VECTOR_INNER_PRODUCT — every expectation measured on a real Snowflake account. */
public class VectorInnerProductTest extends BaseVectorFunctionTest {

    @Test
    public void dotProductOfKnownVectors() {
        // Live: VECTOR_INNER_PRODUCT([1,2,3],[4,5,6]) => 32.0 (4+10+18)
        assertFloat64(32.0, "SELECT VECTOR_INNER_PRODUCT(" + V123 + ", " + V456 + ")");
        // Live: the VECTOR(INT,3) pair also returns 32.0, and SYSTEM$TYPEOF stays FLOAT[DOUBLE] —
        // INT operands do NOT make the result an integer.
        assertFloat64(32.0, "SELECT VECTOR_INNER_PRODUCT(" + I123 + ", " + I456 + ")");
    }

    @Test
    public void intElementsAreAccumulatedInFloat64() {
        // Live: VECTOR_INNER_PRODUCT([2147483647,0,0]::VECTOR(INT,3), [2,0,0]::VECTOR(INT,3)) is
        // 4.294967294E9 — well past 32-bit range, so the product is not computed in the element width.
        assertFloat64(4294967294.0, "SELECT VECTOR_INNER_PRODUCT([2147483647,0,0]::VECTOR(INT,3), "
            + "[2,0,0]::VECTOR(INT,3))");
    }

    @Test
    public void float32ElementsFeedFloat64Arithmetic() {
        // Live: [0.1,0.2,0.3]·[0.4,0.5,0.6] => 0.32000001698732405 (the exact-decimal answer is 0.32).
        assertFloat64(0.32000001698732405, "SELECT VECTOR_INNER_PRODUCT([0.1,0.2,0.3]::VECTOR(FLOAT,3), "
            + "[0.4,0.5,0.6]::VECTOR(FLOAT,3))");
    }

    @Test
    public void typedNullYieldsSqlNull() {
        assertNull(scalar("SELECT VECTOR_INNER_PRODUCT(NULL::VECTOR(FLOAT,3), " + V123 + ")"));
    }

    @Test
    public void mismatchedOrNonVectorArgumentsAreRejected() {
        // Live: "Invalid argument types for function 'VECTOR_INNER_PRODUCT': (VECTOR(FLOAT, 3), NULL)"
        assertRejected("SELECT VECTOR_INNER_PRODUCT(" + V123 + ", NULL)");
        assertRejected("SELECT VECTOR_INNER_PRODUCT(" + V123 + ", " + I123 + ")");
        assertRejected("SELECT VECTOR_INNER_PRODUCT([1,2]::VECTOR(FLOAT,2), " + V123 + ")");
        assertRejected("SELECT VECTOR_INNER_PRODUCT([1,2,3], " + V123 + ")");
    }
}
