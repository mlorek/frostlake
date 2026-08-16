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

/** VECTOR_AVG — every expectation measured on a real Snowflake account. */
public class VectorAvgTest extends BaseVectorFunctionTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE avg_rows (id INTEGER, v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO avg_rows SELECT 1, " + V123);
        engine.execute("INSERT INTO avg_rows SELECT 2, " + V456);
        engine.execute("INSERT INTO avg_rows SELECT 3, NULL");
    }

    @Test
    public void averagesElementWiseAndDividesByTheNonNullCount() {
        // Live: over [1,2,3], [4,5,6] and a NULL row => [2.5,3.5,4.5] — divided by 2, not 3, so the
        // NULL row is skipped rather than counted as a zero vector.
        assertVector("[2.5,3.5,4.5]", "SELECT VECTOR_AVG(v) FROM avg_rows");
    }

    @Test
    public void meanElementsAreNarrowedToFloat32() {
        engine.execute("CREATE TABLE avg_cross (v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO avg_cross SELECT [1,9,3]::VECTOR(FLOAT,3)");
        engine.execute("INSERT INTO avg_cross SELECT [4,5,-6]::VECTOR(FLOAT,3)");
        engine.execute("INSERT INTO avg_cross SELECT [7,2,0]::VECTOR(FLOAT,3)");
        // Live: [4.0,5.3333335,-1.0]. The middle element is 16/3 computed in float64 and then narrowed
        // to a float32 element — the float64 rendering would be 5.333333333333333.
        assertVector("[4.0,5.3333335,-1.0]", "SELECT VECTOR_AVG(v) FROM avg_cross");
    }

    @Test
    public void intVectorsAverageToAFloatVector() {
        engine.execute("CREATE TABLE avg_int (v VECTOR(INT,3))");
        engine.execute("INSERT INTO avg_int SELECT [1,9,3]::VECTOR(INT,3)");
        engine.execute("INSERT INTO avg_int SELECT [4,5,-6]::VECTOR(INT,3)");
        // Live: [2.5,7.0,-1.5] with SYSTEM$TYPEOF VECTOR(FLOAT, 3) — VECTOR_AVG is the one aggregate
        // that always widens INT to FLOAT.
        assertVector("[2.5,7.0,-1.5]", "SELECT VECTOR_AVG(v) FROM avg_int");
    }

    @Test
    public void emptyAndAllNullGroupsAreNull() {
        assertNull(scalar("SELECT VECTOR_AVG(v) FROM avg_rows WHERE id = 99"));
        assertNull(scalar("SELECT VECTOR_AVG(v) FROM avg_rows WHERE id = 3"));
    }

    @Test
    public void singleRowGroupIsThatRow() {
        // Live: VECTOR_AVG over one row is that row's vector.
        assertVector("[1.0,2.0,3.0]", "SELECT VECTOR_AVG(v) FROM avg_rows WHERE id = 1");
    }

    @Test
    public void nonVectorArgumentIsRejected() {
        assertRejected("SELECT VECTOR_AVG(1)");
        assertRejected("SELECT VECTOR_AVG(id) FROM avg_rows");
    }
}
