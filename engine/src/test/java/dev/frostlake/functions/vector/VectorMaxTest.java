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

/** VECTOR_MAX — every expectation measured on a real Snowflake account. */
public class VectorMaxTest extends BaseVectorFunctionTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE max_rows (id INTEGER, v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO max_rows SELECT 1, " + V123);
        engine.execute("INSERT INTO max_rows SELECT 2, " + V456);
        engine.execute("INSERT INTO max_rows SELECT 3, NULL");
    }

    @Test
    public void maximumIsElementWiseNotRowWise() {
        engine.execute("CREATE TABLE max_cross (v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO max_cross SELECT [1,9,3]::VECTOR(FLOAT,3)");
        engine.execute("INSERT INTO max_cross SELECT [4,5,-6]::VECTOR(FLOAT,3)");
        engine.execute("INSERT INTO max_cross SELECT [7,2,0]::VECTOR(FLOAT,3)");
        // Live: [7.0,9.0,3.0] — again each element comes from a different row.
        assertVector("[7.0,9.0,3.0]", "SELECT VECTOR_MAX(v) FROM max_cross");
    }

    @Test
    public void skipsNullRows() {
        // Live: over [1,2,3], [4,5,6] and a NULL row => [4.0,5.0,6.0].
        assertVector("[4.0,5.0,6.0]", "SELECT VECTOR_MAX(v) FROM max_rows");
    }

    @Test
    public void intVectorsKeepTheIntElementType() {
        engine.execute("CREATE TABLE max_int (v VECTOR(INT,3))");
        engine.execute("INSERT INTO max_int SELECT [1,9,3]::VECTOR(INT,3)");
        engine.execute("INSERT INTO max_int SELECT [4,5,-6]::VECTOR(INT,3)");
        // Live: [4,9,3] with SYSTEM$TYPEOF VECTOR(INT, 3).
        assertVector("[4,9,3]", "SELECT VECTOR_MAX(v) FROM max_int");
    }

    @Test
    public void emptyAndAllNullGroupsAreNull() {
        assertNull(scalar("SELECT VECTOR_MAX(v) FROM max_rows WHERE id = 99"));
        assertNull(scalar("SELECT VECTOR_MAX(v) FROM max_rows WHERE id = 3"));
    }

    @Test
    public void singleRowGroupIsThatRow() {
        assertVector("[1.0,2.0,3.0]", "SELECT VECTOR_MAX(v) FROM max_rows WHERE id = 1");
    }

    @Test
    public void nonVectorArgumentIsRejected() {
        assertRejected("SELECT VECTOR_MAX(1)");
        assertRejected("SELECT VECTOR_MAX(id) FROM max_rows");
    }
}
