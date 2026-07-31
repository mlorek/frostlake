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

/** IS_VECTOR — every expectation measured on a real Snowflake account. */
public class IsVectorTest extends BaseVectorFunctionTest {

    @Test
    public void vectorsAreTrue() {
        // Live: TRUE for both element types.
        assertEquals(Boolean.TRUE, scalar("SELECT IS_VECTOR(" + V123 + ")"));
        assertEquals(Boolean.TRUE, scalar("SELECT IS_VECTOR(" + I123 + ")"));
    }

    @Test
    public void otherVariantValuesAreFalse() {
        // Live: a plain ARRAY, an OBJECT, a PARSE_JSON array, a NUMBER and a BOOLEAN are all FALSE —
        // an array that merely LOOKS like a vector is not one.
        assertEquals(Boolean.FALSE, scalar("SELECT IS_VECTOR([1,2,3])"));
        assertEquals(Boolean.FALSE, scalar("SELECT IS_VECTOR({'a':1})"));
        assertEquals(Boolean.FALSE, scalar("SELECT IS_VECTOR(PARSE_JSON('[1,2,3]'))"));
        assertEquals(Boolean.FALSE, scalar("SELECT IS_VECTOR(TO_VARIANT([1,2,3]))"));
        assertEquals(Boolean.FALSE, scalar("SELECT IS_VECTOR(1)"));
        assertEquals(Boolean.FALSE, scalar("SELECT IS_VECTOR(TRUE)"));
    }

    @Test
    public void nullIsNullNotFalse() {
        // Live: IS_VECTOR(NULL) and IS_VECTOR(NULL::VECTOR(FLOAT,3)) are both SQL NULL — the trap that
        // makes this function unlike the rest of the IS_* family.
        assertNull(scalar("SELECT IS_VECTOR(NULL)"));
        assertNull(scalar("SELECT IS_VECTOR(NULL::VECTOR(FLOAT,3))"));
    }

    @Test
    public void nonVariantArgumentsAreRejected() {
        // Live: "Invalid argument types for function 'IS_VECTOR': (VARCHAR(3))" and the same for a
        // DATE — IS_VECTOR answers only for values VARIANT can hold.
        assertRejected("SELECT IS_VECTOR('abc')");
        assertRejected("SELECT IS_VECTOR('2020-01-01'::DATE)");
    }

    @Test
    public void tracksTheTypeThroughDerivedTablesAndColumns() {
        engine.execute("CREATE TABLE isvec_rows (id INTEGER, v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO isvec_rows SELECT 1, " + V123);
        engine.execute("INSERT INTO isvec_rows SELECT 2, NULL");
        // Live: TRUE for a stored vector, NULL for a NULL row.
        assertEquals(Boolean.TRUE, scalar("SELECT IS_VECTOR(v) FROM isvec_rows WHERE id = 1"));
        assertNull(scalar("SELECT IS_VECTOR(v) FROM isvec_rows WHERE id = 2"));
        // Live: a derived column and a CTE column stay vectors — both are TRUE on the account.
        assertEquals(Boolean.TRUE,
            scalar("SELECT IS_VECTOR(v) FROM (SELECT " + V123 + " AS v)"));
        assertEquals(Boolean.TRUE,
            scalar("WITH t AS (SELECT " + V123 + " AS v) SELECT IS_VECTOR(v) FROM t"));
    }
}
