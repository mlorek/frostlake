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

import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** VECTOR_SUM — every expectation measured on a real Snowflake account. */
public class VectorSumTest extends BaseVectorFunctionTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sum_rows (id INTEGER, v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO sum_rows SELECT 1, " + V123);
        engine.execute("INSERT INTO sum_rows SELECT 2, " + V456);
        engine.execute("INSERT INTO sum_rows SELECT 3, NULL");
    }

    @Test
    public void sumsElementWiseSkippingNullRows() {
        // Live: over [1,2,3], [4,5,6] and a NULL row, VECTOR_SUM is [5.0,7.0,9.0].
        assertVector("[5.0,7.0,9.0]", "SELECT VECTOR_SUM(v) FROM sum_rows");
    }

    @Test
    public void reducesEachDimensionIndependently() {
        engine.execute("CREATE TABLE sum_cross (v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO sum_cross SELECT [1,9,3]::VECTOR(FLOAT,3)");
        engine.execute("INSERT INTO sum_cross SELECT [4,5,-6]::VECTOR(FLOAT,3)");
        engine.execute("INSERT INTO sum_cross SELECT [7,2,0]::VECTOR(FLOAT,3)");
        // Live: deliberately CROSSING rows (no row is the winner everywhere) => [12.0,16.0,-3.0].
        assertVector("[12.0,16.0,-3.0]", "SELECT VECTOR_SUM(v) FROM sum_cross");
    }

    @Test
    public void intVectorsKeepTheIntElementType() {
        engine.execute("CREATE TABLE sum_int (v VECTOR(INT,3))");
        engine.execute("INSERT INTO sum_int SELECT [1,9,3]::VECTOR(INT,3)");
        engine.execute("INSERT INTO sum_int SELECT [4,5,-6]::VECTOR(INT,3)");
        // Live: [5,14,-3] with SYSTEM$TYPEOF VECTOR(INT, 3) — the element type is NOT widened to FLOAT.
        assertVector("[5,14,-3]", "SELECT VECTOR_SUM(v) FROM sum_int");
    }

    @Test
    public void emptyAndAllNullGroupsAreNull() {
        // Live: a group with no non-NULL row is SQL NULL, not a zero vector.
        assertNull(scalar("SELECT VECTOR_SUM(v) FROM sum_rows WHERE id = 99"));
        assertNull(scalar("SELECT VECTOR_SUM(v) FROM sum_rows WHERE id = 3"));
    }

    @Test
    public void singleRowGroupIsThatRow() {
        // Live: VECTOR_SUM over one row is that row's vector.
        assertVector("[1.0,2.0,3.0]", "SELECT VECTOR_SUM(v) FROM sum_rows WHERE id = 1");
    }

    @Test
    public void groupByKeepsPerGroupResults() {
        // Live: grouping by id yields each row's own vector, and NULL for the NULL row's group.
        final ResultSet grouped =
            engine.executeQuery("SELECT id, VECTOR_SUM(v) FROM sum_rows GROUP BY id ORDER BY id");
        assertEquals(3, grouped.getRows().size());
        assertVectorValue("[1.0,2.0,3.0]", grouped.getRows().get(0).getValue(1), "group id = 1");
        assertVectorValue("[4.0,5.0,6.0]", grouped.getRows().get(1).getValue(1), "group id = 2");
        assertNull(grouped.getRows().get(2).getValue(1));
    }

    @Test
    public void nonVectorArgumentIsRejected() {
        // Live: "Invalid argument types for function 'VECTOR_SUM': (NUMBER(1,0))" and "… (ARRAY)".
        assertRejected("SELECT VECTOR_SUM(1)");
        assertRejected("SELECT VECTOR_SUM(id) FROM sum_rows");
    }
}
