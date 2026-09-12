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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a refusal over a VECTOR names the vector it was given. An invalid-type sentence prints a cast to
 * VECTOR as the conversion it was planned as — {@code TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))}, the array
 * literal as its constructor — a cast onto the vector's own type as {@code identity(x)}, a typed NULL as
 * {@code SYSTEM$NULL_TO_VECTOR(NULL)} and COALESCE as its IFNULL chain; an arity echo prints the
 * resolved {@code CAST(… AS VECTOR(FLOAT, 3))}. VECTOR_TRUNC numbers a non-constant dimension 1 and
 * names it as the whole number the plan converts it to when it is a constant expression, and numbers it
 * 2 and names it as written when it reads a column. Each expected answer is the account's own.
 */
public class VectorPlanEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE vec_store (v VECTOR(FLOAT,3))");
        engine.execute("CREATE TABLE trunc_rows (v VECTOR(FLOAT,3), n INT, x NUMBER(10,2), fl FLOAT, s VARCHAR(5), b BOOLEAN)");
    }

    /** The answer as one line: the rows a query returns, or its refusal with each line break as |. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** TO_VARCHAR over a vector cast quotes TO_VECTOR, identity and the typed null. */
    @Test
    public void anInvalidTypeSentenceNamesAVectorCastAsToVector() {
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR([1,2,3]::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(identity(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR([1,2,3]::VECTOR(FLOAT,3)::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(identity(identity(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR([1,2,3]::VECTOR(FLOAT,3)::VECTOR(FLOAT,3)::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(IFNULL(SYSTEM$NULL_TO_VECTOR(NULL), TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(COALESCE(NULL::VECTOR(FLOAT,3), [1,2,3]::VECTOR(FLOAT,3)))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(VECTOR_TRUNC(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)), 2))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 2))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(CAST([1,2,3] AS VECTOR(FLOAT,3)))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(ARRAY_CONSTRUCT(1.5, 2, 3)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR([1.5,2,3]::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR([1,2,3]::VECTOR(INT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(ARRAY_CONSTRUCT(1, -2, 3)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR([1,-2,3]::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(ARRAY_CONSTRUCT(-1, 2, 3)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR([-1,2,3]::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2.5, -3)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR([1, 2.5, -3]::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(1,2,3)::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(identity(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(1, 2, 3)::VECTOR(FLOAT, 3)::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(PARSE_JSON('[1,2,3]')))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(PARSE_JSON('[1,2,3]')::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(SYSTEM$NULL_TO_VECTOR(NULL))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(NULL::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)), 'x')] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR([1,2,3]::VECTOR(FLOAT,3), 'x')"));
        assertEquals("SQL compilation error:|invalid type [TO_CHAR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for parameter 'TO_CHAR'",
            answer("SELECT TO_CHAR([1,2,3]::VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            answer("SELECT CAST([1,2,3]::VECTOR(FLOAT,3) AS VARCHAR)"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            answer("SELECT [1,2,3]::VECTOR(FLOAT,3)::VARCHAR"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2)) AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            answer("SELECT CAST(ARRAY_CONSTRUCT(1,2)::VECTOR(INT,2) AS VARCHAR)"));
    }

    /** Over a column the same sentence names the column, qualified. */
    @Test
    public void aVectorColumnIsNamedByItsRelation() {
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(VEC_STORE.V)] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(v) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(VEC_STORE.V, 'x')] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(v, 'x') FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(identity(VEC_STORE.V))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(v::VECTOR(FLOAT,3)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(identity(identity(VEC_STORE.V)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(v::VECTOR(FLOAT,3)::VECTOR(FLOAT,3)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(IFNULL(VEC_STORE.V, VEC_STORE.V))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(COALESCE(v, v)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(IFNULL(VEC_STORE.V, IFNULL(VEC_STORE.V, VEC_STORE.V)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(COALESCE(v, v, v)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(IFNULL(VEC_STORE.V, SYSTEM$NULL_TO_VECTOR(NULL)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(COALESCE(v, NULL)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(IFNULL(SYSTEM$NULL_TO_VECTOR(NULL), VEC_STORE.V))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(COALESCE(NULL::VECTOR(FLOAT,3), v)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(IFNULL(VEC_STORE.V, IFNULL(SYSTEM$NULL_TO_VECTOR(NULL), VEC_STORE.V)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(COALESCE(v, NULL::VECTOR(FLOAT,3), v)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(IFNULL(VEC_STORE.V, VEC_STORE.V))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(IFNULL(v, v)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(NVL(VEC_STORE.V, VEC_STORE.V))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(NVL(v, v)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(NULLIF(VEC_STORE.V, VEC_STORE.V))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(NULLIF(v, v)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(VECTOR_TRUNC(VEC_STORE.V, 2))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(VECTOR_TRUNC(v, 2)) FROM vec_store"));
        assertEquals("SQL compilation error:|invalid type [CAST(VEC_STORE.V AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            answer("SELECT CAST(v AS VARCHAR) FROM vec_store"));
    }

    /** An arity echo resolves the conversion into a CAST, and drops one onto the vector's own type. */
    @Test
    public void anArityEchoPrintsTheResolvedVectorCast() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(ARRAY_CONSTRUCT(1, 2, 3) AS VECTOR(FLOAT, 3)), 1)] expected 1, got 2",
            answer("SELECT UPPER([1,2,3]::VECTOR(FLOAT,3), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(VEC_STORE.V, 1)] expected 1, got 2",
            answer("SELECT UPPER(v, 1) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(VEC_STORE.V, 1)] expected 1, got 2",
            answer("SELECT UPPER(v::VECTOR(FLOAT,3), 1) FROM vec_store"));
    }

    /** A dimension that is a constant but no whole-number literal. */
    @Test
    public void aConstantDimensionIsArgumentOneNamedAsAWholeNumber() {
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'CAST(2.5 AS NUMBER(9,0))'",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 2.5)"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found '1 + 1'",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 1+1)"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'SYSTEM$NULL_TO_FIXED(null)'",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), NULL)"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found '1 - 2'",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 1 - 2)"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'CAST(-2.5 AS NUMBER(9,0))'",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), -2.5)"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'TO_NUMBER('2.5', 9, 0)'",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), '2.5')"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'CAST(CAST(2.5 AS FLOAT) AS NUMBER(9,0))'",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 2.5::FLOAT)"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'CAST(CAST(2.5 AS NUMBER(3,1)) AS NUMBER(9,0))'",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), CAST(2.5 AS NUMBER(3,1)))"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'TO_NUMBER(PARSE_JSON('2'), 9, 0)'",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), PARSE_JSON('2'))"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'CAST(2.5 AS NUMBER(9,0))'",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 2.50)"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'CAST(2.5 AS NUMBER(9,0))'",
            answer("SELECT VECTOR_TRUNC(v, 2.5) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'TO_NUMBER('2', 9, 0)'",
            answer("SELECT VECTOR_TRUNC(v, '2') FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'CAST(2 AS NUMBER(38,0))'",
            answer("SELECT VECTOR_TRUNC(v, 2::INT) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'CAST(1.5 AS NUMBER(9,0))'",
            answer("SELECT VECTOR_TRUNC(v, 1.5e0) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'ABS(2)'",
            answer("SELECT VECTOR_TRUNC(v, ABS(2)) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'CAST(2.55 AS NUMBER(9,0))'",
            answer("SELECT VECTOR_TRUNC(v, 2.55) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found '1 * 2'",
            answer("SELECT VECTOR_TRUNC(v, 1 * 2) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'SYSTEM$NULL_TO_FIXED(null)'",
            answer("SELECT VECTOR_TRUNC(v, NULL) FROM trunc_rows"));
    }

    /** A dimension that reads a column. */
    @Test
    public void aDimensionReadFromTheRowIsArgumentTwoNamedAsWritten() {
        assertEquals("SQL compilation error:|argument 2 to function VECTOR_TRUNC needs to be constant, found 'TRUNC_ROWS.N'",
            answer("SELECT VECTOR_TRUNC(v, n) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 2 to function VECTOR_TRUNC needs to be constant, found 'TRUNC_ROWS.X'",
            answer("SELECT VECTOR_TRUNC(v, x) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 2 to function VECTOR_TRUNC needs to be constant, found 'TRUNC_ROWS.FL'",
            answer("SELECT VECTOR_TRUNC(v, fl) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 2 to function VECTOR_TRUNC needs to be constant, found 'TRUNC_ROWS.S'",
            answer("SELECT VECTOR_TRUNC(v, s) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 2 to function VECTOR_TRUNC needs to be constant, found 'TRUNC_ROWS.N + 1'",
            answer("SELECT VECTOR_TRUNC(v, n + 1) FROM trunc_rows"));
        assertEquals("SQL compilation error:|argument 2 to function VECTOR_TRUNC needs to be constant, found 'CAST(TRUNC_ROWS.N AS NUMBER(10,2))'",
            answer("SELECT VECTOR_TRUNC(v, n::NUMBER(10,2)) FROM trunc_rows"));
    }

    /** A BOOLEAN or a DATE is no dimension, and the refusal names the argument types. */
    @Test
    public void aBooleanOrDateDimensionIsAnArgumentTypeRefusal() {
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'VECTOR_TRUNC': (VECTOR(FLOAT, 3), BOOLEAN)",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), TRUE)"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'VECTOR_TRUNC': (VECTOR(FLOAT, 3), DATE)",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), TO_DATE('2020-01-01'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'VECTOR_TRUNC': (VECTOR(FLOAT, 3), BOOLEAN)",
            answer("SELECT VECTOR_TRUNC(v, b) FROM trunc_rows"));
    }

    /** A whole-number literal, however written, is a dimension. */
    @Test
    public void aWholeNumberDimensionIsAccepted() {
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC(v, 2.0) FROM trunc_rows"));
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC(v, (2)) FROM trunc_rows"));
    }
}
