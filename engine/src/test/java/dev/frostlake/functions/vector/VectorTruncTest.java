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

/**
 * VECTOR_TRUNC and its alias VECTOR_TRUNCATE — every expectation measured on a real Snowflake account
 *.
 */
public class VectorTruncTest extends BaseVectorFunctionTest {

    @Test
    public void keepsTheFirstNDimensions() {
        // Live: VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 2) => [1.0,2.0], SYSTEM$TYPEOF VECTOR(FLOAT, 2)
        assertVector("[1.0,2.0]", "SELECT VECTOR_TRUNC(" + V123 + ", 2)");
        // Live: VECTOR_TRUNCATE is the same function under its other name.
        assertVector("[1.0,2.0]", "SELECT VECTOR_TRUNCATE(" + V123 + ", 2)");
    }

    @Test
    public void elementTypeIsPreserved() {
        // Live: VECTOR_TRUNC([1,2,3]::VECTOR(INT,3), 2) => [1,2] with SYSTEM$TYPEOF VECTOR(INT, 2) —
        // truncation does NOT convert an INT vector to FLOAT.
        assertVector("[1,2]", "SELECT VECTOR_TRUNC(" + I123 + ", 2)");
        assertVector("[1,2]", "SELECT VECTOR_TRUNCATE(" + I123 + ", 2)");
    }

    @Test
    public void boundaryDimensions() {
        // Live: n = 0 is the empty vector [] and n = the full dimension is the unchanged vector.
        assertVector("[]", "SELECT VECTOR_TRUNC(" + V123 + ", 0)");
        assertVector("[1.0,2.0,3.0]", "SELECT VECTOR_TRUNC(" + V123 + ", 3)");
    }

    @Test
    public void integralDecimalDimensionIsAccepted() {
        // Live: VECTOR_TRUNC(v, 2.0) works (=> [1.0,2.0]) while 2.5 is rejected below.
        assertVector("[1.0,2.0]", "SELECT VECTOR_TRUNC(" + V123 + ", 2.0)");
    }

    @Test
    public void typedNullVectorYieldsSqlNull() {
        // Live: VECTOR_TRUNC(NULL::VECTOR(FLOAT,3), 2) => SQL NULL.
        assertNull(scalar("SELECT VECTOR_TRUNC(NULL::VECTOR(FLOAT,3), 2)"));
    }

    @Test
    public void dimensionBeyondTheVectorIsRejected() {
        // Live: "Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the
        // dimension of the provided vector (3)."
        assertRejected("SELECT VECTOR_TRUNC(" + V123 + ", 4)");
        assertRejected("SELECT VECTOR_TRUNCATE(" + V123 + ", 9)");
        // A negative dimension also fails on the account (there it surfaces as an internal error).
        assertRejected("SELECT VECTOR_TRUNC(" + V123 + ", -1)");
    }

    @Test
    public void nonConstantDimensionIsRejected() {
        engine.execute("CREATE TABLE trunc_rows (n INTEGER, v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO trunc_rows SELECT 2, " + V123);
        // Live: "argument … to function VECTOR_TRUNC needs to be constant" for a column reference, an
        // arithmetic expression, a non-integral literal and NULL.
        assertRejected("SELECT VECTOR_TRUNC(v, n) FROM trunc_rows");
        assertRejected("SELECT VECTOR_TRUNC(" + V123 + ", 1+1)");
        assertRejected("SELECT VECTOR_TRUNC(" + V123 + ", 2.5)");
        assertRejected("SELECT VECTOR_TRUNC(" + V123 + ", NULL)");
    }

    @Test
    public void nonVectorFirstArgumentIsRejected() {
        // Live: "Invalid argument types for function 'VECTOR_TRUNC': (ARRAY, NUMBER(1,0))"
        assertRejected("SELECT VECTOR_TRUNC([1,2,3], 2)");
        assertRejected("SELECT VECTOR_TRUNC(NULL, 2)");
    }
}
