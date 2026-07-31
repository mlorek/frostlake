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

/**
 * The VECTOR VALUE itself, as the function family depends on it: the cast's conversion rules and
 * errors, 32-bit element widths, display text, column storage round-trip, and the ordinary SQL
 * operations (equality, GROUP BY, DISTINCT, ORDER BY, CASE / COALESCE / UNION, UDF bodies). Every
 * expectation is measured on a real Snowflake account.
 */
public class VectorValueSemanticsTest extends BaseVectorFunctionTest {

    @Test
    public void castRendersFloat32AndIntElements() {
        // Live: [1,2,3]::VECTOR(FLOAT,3) displays as [1.0,2.0,3.0] and the INT form as [1,2,3].
        assertVector("[1.0,2.0,3.0]", "SELECT " + V123);
        assertVector("[1,2,3]", "SELECT " + I123);
        assertVector("[1.1,2.2,3.3]", "SELECT [1.1,2.2,3.3]::VECTOR(FLOAT,3)");
        // Live: [3.14159265358979,0,0]::VECTOR(FLOAT,3) is [3.1415927,0.0,0.0] — pi at float32 width.
        assertVector("[3.1415927,0.0,0.0]", "SELECT [3.14159265358979,0,0]::VECTOR(FLOAT,3)");
    }

    @Test
    public void intElementsAre32BitAndWrap() {
        // Live: [2147483647,0,0] is kept, [2147483648,0,0] becomes [-2147483648,0,0], and
        // [9007199254740993,0,0] (2^53+1) becomes [1,0,0] — the low 32 bits.
        assertVector("[2147483647,0,0]", "SELECT [2147483647,0,0]::VECTOR(INT,3)");
        assertVector("[-2147483648,0,0]", "SELECT [2147483648,0,0]::VECTOR(INT,3)");
        assertVector("[1,0,0]", "SELECT [9007199254740993,0,0]::VECTOR(INT,3)");
    }

    @Test
    public void castConversionErrors() {
        // Live: "Vector value being cast to a vector is not an array or vector, or has incorrect
        // dimension or element type" for a wrong-length array …
        assertRejected("SELECT [1,2]::VECTOR(FLOAT,3)");
        // … "Array-like value being cast to a float vector has elements that are not real numbers" …
        assertRejected("SELECT ['a','b','c']::VECTOR(FLOAT,3)");
        assertRejected("SELECT [true,false,true]::VECTOR(FLOAT,3)");
        // … and "…to an integer vector has elements that are not integers" for fractional elements.
        assertRejected("SELECT [1.7,2.2,3.9]::VECTOR(INT,3)");
        // Live: whole-valued decimals DO convert to an INT vector.
        assertVector("[1,2,3]", "SELECT [1.0,2.0,3.0]::VECTOR(INT,3)");
    }

    @Test
    public void aVectorCastsOnlyToItsOwnType() {
        // Live: the identity cast is the unchanged vector, while a different element type or dimension
        // is "Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))".
        assertVector("[1.0,2.0,3.0]", "SELECT " + V123 + "::VECTOR(FLOAT,3)");
        assertRejected("SELECT " + V123 + "::VECTOR(INT,3)");
        assertRejected("SELECT " + V123 + "::VECTOR(FLOAT,2)");
    }

    @Test
    public void typeofReportsTheBareFamilyName() {
        // Live: TYPEOF([1,2,3]::VECTOR(FLOAT,3)) is 'VECTOR'.
        assertEquals("VECTOR", scalar("SELECT TYPEOF(" + V123 + ")"));
    }

    @Test
    public void columnRoundTripKeepsTheVector() {
        engine.execute("CREATE TABLE vec_store (id INTEGER, v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO vec_store SELECT 1, " + V123);
        engine.execute("INSERT INTO vec_store SELECT 2, " + V456);
        engine.execute("INSERT INTO vec_store SELECT 3, NULL");
        // Live: the stored value reads back as the vector it was written as …
        assertVector("[1.0,2.0,3.0]", "SELECT v FROM vec_store WHERE id = 1");
        // … and every function still works over the stored column.
        assertFloat64(0.0, "SELECT VECTOR_L2_DISTANCE(v, " + V123 + ") FROM vec_store WHERE id = 1");
        assertVector("[0.26726124,0.5345225,0.80178374]", "SELECT VECTOR_NORMALIZE(v) FROM vec_store WHERE id = 1");
        assertVector("[1.0,2.0]", "SELECT VECTOR_TRUNC(v, 2) FROM vec_store WHERE id = 1");
        assertEquals(Boolean.TRUE, scalar("SELECT IS_VECTOR(v) FROM vec_store WHERE id = 1"));
        assertVector("[5.0,7.0,9.0]", "SELECT VECTOR_SUM(v) FROM vec_store");
        assertNull(scalar("SELECT v FROM vec_store WHERE id = 3"));
    }

    @Test
    public void vectorsCompareGroupAndDeduplicate() {
        engine.execute("CREATE TABLE vec_cmp (id INTEGER, v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO vec_cmp SELECT 1, " + V123);
        engine.execute("INSERT INTO vec_cmp SELECT 2, " + V456);
        engine.execute("INSERT INTO vec_cmp SELECT 3, " + V123);
        // Live: equality, GROUP BY, DISTINCT and ORDER BY all work over a vector column.
        assertEquals(2L, ((Number) scalar(
            "SELECT COUNT(*) FROM vec_cmp WHERE v = " + V123)).longValue());
        assertEquals(2L, ((Number) scalar(
            "SELECT COUNT(*) FROM (SELECT DISTINCT v FROM vec_cmp)")).longValue());
        final ResultSet grouped = engine.executeQuery(
            "SELECT COUNT(*) AS c FROM vec_cmp GROUP BY v ORDER BY c DESC");
        assertEquals(2, grouped.getRows().size());
        assertEquals(2L, ((Number) grouped.getRows().get(0).getValue(0)).longValue());
        assertVector("[1.0,2.0,3.0]", "SELECT v FROM vec_cmp ORDER BY v LIMIT 1");
    }

    @Test
    public void vectorsFlowThroughCaseCoalesceAndUnion() {
        // Live: all three keep the vector.
        assertVector("[1.0,2.0,3.0]", "SELECT COALESCE(NULL::VECTOR(FLOAT,3), " + V123 + ")");
        assertVector("[1.0,2.0,3.0]", "SELECT CASE WHEN TRUE THEN " + V123 + " ELSE NULL END");
        final ResultSet union = engine.executeQuery(
            "SELECT " + V123 + " AS v UNION ALL SELECT " + V456);
        assertEquals(2, union.getRows().size());
    }

    @Test
    public void vectorExpressionsAreRejectedInAValuesClause() {
        engine.execute("CREATE TABLE vec_values (v VECTOR(FLOAT,3))");
        // Live: "Invalid data type [VECTOR(FLOAT, 3)] in VALUES clause" — INSERT ... SELECT is the
        // supported route, exactly as for the semi-structured types.
        assertRejected("INSERT INTO vec_values VALUES (" + V123 + ")");
    }

    @Test
    public void vectorsWorkInsideUdfBodies() {
        engine.execute("CREATE FUNCTION vec_dist(a VECTOR(FLOAT,3), b VECTOR(FLOAT,3)) "
            + "RETURNS FLOAT AS 'VECTOR_L2_DISTANCE(a, b)'");
        engine.execute("CREATE FUNCTION vec_unit(a VECTOR(FLOAT,3)) "
            + "RETURNS VECTOR(FLOAT,3) AS 'VECTOR_NORMALIZE(a)'");
        // Live: the UDF returns the same values the built-ins do.
        assertFloat64(5.196152422706632, "SELECT vec_dist(" + V123 + ", " + V456 + ")");
        assertVector("[0.26726124,0.5345225,0.80178374]", "SELECT vec_unit(" + V123 + ")");
    }
}
