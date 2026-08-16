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

/** VECTOR_MIN — every expectation measured on a real Snowflake account. */
public class VectorMinTest extends BaseVectorFunctionTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE min_rows (id INTEGER, v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO min_rows SELECT 1, " + V123);
        engine.execute("INSERT INTO min_rows SELECT 2, " + V456);
        engine.execute("INSERT INTO min_rows SELECT 3, NULL");
    }

    @Test
    public void minimumIsElementWiseNotRowWise() {
        engine.execute("CREATE TABLE min_cross (v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO min_cross SELECT [1,9,3]::VECTOR(FLOAT,3)");
        engine.execute("INSERT INTO min_cross SELECT [4,5,-6]::VECTOR(FLOAT,3)");
        engine.execute("INSERT INTO min_cross SELECT [7,2,0]::VECTOR(FLOAT,3)");
        // Live: [1.0,2.0,-6.0] — each element comes from a DIFFERENT row, so the reduction is per
        // dimension and not "the smallest row". Same-shaped rows would not have shown this.
        assertVector("[1.0,2.0,-6.0]", "SELECT VECTOR_MIN(v) FROM min_cross");
    }

    @Test
    public void skipsNullRows() {
        // Live: over [1,2,3], [4,5,6] and a NULL row => [1.0,2.0,3.0].
        assertVector("[1.0,2.0,3.0]", "SELECT VECTOR_MIN(v) FROM min_rows");
    }

    @Test
    public void intVectorsKeepTheIntElementType() {
        engine.execute("CREATE TABLE min_int (v VECTOR(INT,3))");
        engine.execute("INSERT INTO min_int SELECT [1,9,3]::VECTOR(INT,3)");
        engine.execute("INSERT INTO min_int SELECT [4,5,-6]::VECTOR(INT,3)");
        // Live: [1,5,-6] with SYSTEM$TYPEOF VECTOR(INT, 3).
        assertVector("[1,5,-6]", "SELECT VECTOR_MIN(v) FROM min_int");
    }

    @Test
    public void emptyAndAllNullGroupsAreNull() {
        assertNull(scalar("SELECT VECTOR_MIN(v) FROM min_rows WHERE id = 99"));
        assertNull(scalar("SELECT VECTOR_MIN(v) FROM min_rows WHERE id = 3"));
    }

    @Test
    public void singleRowGroupIsThatRow() {
        assertVector("[1.0,2.0,3.0]", "SELECT VECTOR_MIN(v) FROM min_rows WHERE id = 1");
    }

    @Test
    public void nonVectorArgumentIsRejected() {
        assertRejected("SELECT VECTOR_MIN(1)");
        assertRejected("SELECT VECTOR_MIN(id) FROM min_rows");
    }
}
