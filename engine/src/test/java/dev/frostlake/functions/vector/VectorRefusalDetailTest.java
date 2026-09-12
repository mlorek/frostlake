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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The details of the VECTOR refusals, and of the date/time-component refusal beside them. VECTOR_TRUNC
 * compiles a dimension of -1 and fails each row it reads, and positions a dimension too large at the
 * dimension; a vector cast to another vector type is refused at the cast, and a source with no conversion
 * to a vector is an unsupported data type, over an empty table too. A whole-day unit asked of a TIME, or a
 * word that is no unit, is refused on one line after the prefix. Each expected answer is the account's own.
 */
public class VectorRefusalDetailTest extends BaseDatabaseTest {

    /** How each row fails under a dimension of -1; the account's incident number follows it. */
    private static final String ROW_FAILURE =
        "SQL execution internal error:|Processing aborted due to error 300010:2086363262";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE vec_store (v VECTOR(FLOAT,3))");
        engine.execute("CREATE TABLE vec_rows (v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO vec_rows SELECT [1,2,3]::VECTOR(FLOAT,3)");
        engine.execute("CREATE TABLE fam (g VARCHAR(10), n NUMBER(5,0), a NUMBER(10,2), f FLOAT, b BOOLEAN, d DATE,"
            + " tm TIME, ts TIMESTAMP_NTZ, v VARIANT, o OBJECT, ar ARRAY, tl TIMESTAMP_LTZ, tz TIMESTAMP_TZ)");
        engine.execute("CREATE TABLE ft (d DATE, ts TIMESTAMP_NTZ, tm TIME, t3 TIME(3))");
        engine.execute("INSERT INTO ft SELECT '2020-03-04'::DATE, '2020-03-04 10:20:30'::TIMESTAMP_NTZ,"
            + " '10:20:30'::TIME, '10:20:30'::TIME(3)");
        engine.execute("CREATE TABLE rt (b BOOLEAN)");
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

    /** -1 compiles, however it is written, and each row read under it fails. */
    @Test
    public void aDimensionOfMinusOneCompilesAndFailsEachRow() {
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC(v, -1) FROM vec_store"));
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC(v, -(1)) FROM vec_store"));
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC(v, - 1) FROM vec_store"));
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC(v, (-1)) FROM vec_store"));
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC(v, -1.0) FROM vec_store"));
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC(v, -0) FROM vec_store"));
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC(v, -1) IS NULL FROM vec_store"));
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC(v, -1) FROM vec_store WHERE FALSE"));
        assertEquals("ACCEPTED:",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), -1) FROM vec_store"));
        assertEquals("ACCEPTED: []",
            answer("SELECT VECTOR_TRUNC(v, 0) FROM vec_rows"));
        assertEquals("ACCEPTED: []",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 0)"));
        assertEquals("ACCEPTED: [1.000000,2.000000,3.000000]",
            answer("SELECT VECTOR_TRUNC(v, 3) FROM vec_rows"));
        assertEquals("SQL compilation error:|argument 1 to function VECTOR_TRUNC needs to be constant, found 'CAST(-1.5 AS NUMBER(9,0))'",
            answer("SELECT VECTOR_TRUNC(v, -1.5) FROM vec_store"));
        final String everyRow = answer("SELECT VECTOR_TRUNC(v, -1) FROM vec_rows");
        assertTrue(everyRow.startsWith(ROW_FAILURE), everyRow);
        final String inAnExpression = answer("SELECT VECTOR_TRUNC(v, -1) IS NULL FROM vec_rows");
        assertTrue(inAnExpression.startsWith(ROW_FAILURE), inAnExpression);
        final String withoutFrom = answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), -1)");
        assertTrue(withoutFrom.startsWith(ROW_FAILURE), withoutFrom);
    }

    /** A dimension too large is refused at the dimension itself, wherever the call stands. */
    @Test
    public void aDimensionTooLargeIsPositionedAtTheDimension() {
        assertEquals("SQL compilation error: error line 1 at position 46|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 4)"));
        assertEquals("SQL compilation error: error line 1 at position 46|Requested truncation dimension 9 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 9)"));
        assertEquals("SQL compilation error: error line 1 at position 49|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT 1, VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 4)"));
        assertEquals("SQL compilation error: error line 1 at position 23|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT VECTOR_TRUNC(v, 4) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 22|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT VECTOR_TRUNC(v,4) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 24|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT VECTOR_TRUNC(v, (4)) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 23|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT VECTOR_TRUNC(v, 4.0) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 40|Requested truncation dimension 3 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (2).",
            answer("SELECT VECTOR_TRUNC(VECTOR_TRUNC(v, 2), 3) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 23|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT VECTOR_TRUNC(v, 4) FROM vec_store WHERE FALSE"));
        assertEquals("SQL compilation error: error line 1 at position 23|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT VECTOR_TRUNC(v, 4) FROM vec_rows WHERE FALSE"));
        assertEquals("SQL compilation error: error line 1 at position 46|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT 1 FROM vec_store WHERE VECTOR_TRUNC(v, 4) IS NULL"));
        assertEquals("SQL compilation error: error line 1 at position 49|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT 1 FROM vec_store ORDER BY VECTOR_TRUNC(v, 4)"));
        assertEquals("SQL compilation error: error line 1 at position 49|Requested truncation dimension 4 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            answer("SELECT 1 FROM vec_store GROUP BY VECTOR_TRUNC(v, 4)"));
    }

    /** A vector casts only to its own type, and the refusal points at the cast. */
    @Test
    public void aVectorCastToAnotherVectorTypeIsRefusedAtTheCast() {
        assertEquals("SQL compilation error: error line 1 at position 31|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT [1,2,3]::VECTOR(FLOAT,3)::VECTOR(INT,3)"));
        assertEquals("SQL compilation error: error line 1 at position 31|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT [1,2,3]::VECTOR(FLOAT,3)::VECTOR(FLOAT,4)"));
        assertEquals("SQL compilation error: error line 1 at position 48|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT [1,2,3]::VECTOR(FLOAT,3)::VECTOR(FLOAT,3)::VECTOR(INT,3)"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT CAST([1,2,3]::VECTOR(FLOAT,3) AS VECTOR(FLOAT,2))"));
        assertEquals("SQL compilation error: error line 1 at position 8|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT v::VECTOR(INT,3) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 8|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT v::VECTOR(FLOAT,4) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 11|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT 1, v::VECTOR(INT,3) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 10|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT (v)::VECTOR(INT,3) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 9|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT v :: VECTOR(INT,3) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 25|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT v::VECTOR(FLOAT,3)::VECTOR(INT,3) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT CAST(v AS VECTOR(INT,3)) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT CAST(v AS VECTOR(INT,3)) FROM vec_rows"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'TRY_CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT TRY_CAST(v AS VECTOR(INT,3)) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 31|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT v FROM vec_store WHERE v::VECTOR(INT,3) IS NULL"));
        assertEquals("SQL compilation error: error line 1 at position 31|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT 1 FROM vec_store WHERE v::VECTOR(INT,3) IS NULL"));
        assertEquals("SQL compilation error: error line 1 at position 19|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT TO_VARCHAR(v::VECTOR(INT,3)) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 14|Invalid argument types for function 'CAST': (VECTOR(FLOAT, 3))",
            answer("SELECT UPPER(v::VECTOR(INT,3), 1) FROM vec_store"));
        assertEquals("ACCEPTED:",
            answer("SELECT CAST(v AS VECTOR(FLOAT,3)) FROM vec_store"));
        assertEquals("ACCEPTED:",
            answer("SELECT TRY_CAST(v AS VECTOR(FLOAT,3)) FROM vec_store"));
    }

    /** Only a vector, an ARRAY or a VARIANT converts to a vector; any other source is an unsupported type. */
    @Test
    public void aSourceWithNoConversionToAVectorIsAnUnsupportedType() {
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT '[1,2,3]'::VECTOR(FLOAT,3)"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT '[1,2,3]'::VECTOR(INT,3)"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT CAST('[1,2,3]' AS VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT TRY_CAST('[1,2,3]' AS VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT '[1,2,3]'::VECTOR(FLOAT,3) FROM vec_store"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT g::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT 1, g::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT CAST(g AS VECTOR(FLOAT,3)) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT g::VARCHAR::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT COALESCE(g, 'x')::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT g::VECTOR(FLOAT,3) FROM fam WHERE FALSE"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT CAST(IFF(b, 'a', 'b') AS VECTOR(FLOAT,3)) FROM rt"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT TO_VARCHAR(g::VECTOR(FLOAT,3)) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'TEXT'.",
            answer("SELECT UPPER(g::VECTOR(FLOAT,3), 1) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'OBJECT'.",
            answer("SELECT {'a': 1}::VECTOR(FLOAT,3)"));
        assertEquals("SQL compilation error:|Unsupported data type 'OBJECT'.",
            answer("SELECT OBJECT_CONSTRUCT('a', 1)::VECTOR(FLOAT,3)"));
        assertEquals("SQL compilation error:|Unsupported data type 'OBJECT'.",
            answer("SELECT o::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'FIXED'.",
            answer("SELECT 1::VECTOR(FLOAT,3)"));
        assertEquals("SQL compilation error:|Unsupported data type 'FIXED'.",
            answer("SELECT 1.5::VECTOR(FLOAT,3)"));
        assertEquals("SQL compilation error:|Unsupported data type 'FIXED'.",
            answer("SELECT 1.5e0::VECTOR(FLOAT,3)"));
        assertEquals("SQL compilation error:|Unsupported data type 'FIXED'.",
            answer("SELECT TRY_CAST(1 AS VECTOR(FLOAT,3))"));
        assertEquals("SQL compilation error:|Unsupported data type 'FIXED'.",
            answer("SELECT n::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'FIXED'.",
            answer("SELECT a::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'REAL'.",
            answer("SELECT f::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'BOOLEAN'.",
            answer("SELECT TRUE::VECTOR(FLOAT,3)"));
        assertEquals("SQL compilation error:|Unsupported data type 'BOOLEAN'.",
            answer("SELECT b::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'DATE'.",
            answer("SELECT '2020-01-01'::DATE::VECTOR(FLOAT,3)"));
        assertEquals("SQL compilation error:|Unsupported data type 'DATE'.",
            answer("SELECT d::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'TIME'.",
            answer("SELECT tm::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'TIMESTAMP_NTZ'.",
            answer("SELECT ts::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'TIMESTAMP_LTZ'.",
            answer("SELECT tl::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'TIMESTAMP_TZ'.",
            answer("SELECT tz::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("SQL compilation error:|Unsupported data type 'BINARY'.",
            answer("SELECT TO_BINARY('0A')::VECTOR(FLOAT,3)"));
        assertEquals("ACCEPTED:",
            answer("SELECT ar::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("ACCEPTED:",
            answer("SELECT ar::VECTOR(INT,3) FROM fam"));
        assertEquals("ACCEPTED:",
            answer("SELECT v::VECTOR(FLOAT,3) FROM fam"));
        assertEquals("ACCEPTED: [1.000000,2.000000,3.000000]",
            answer("SELECT PARSE_JSON('[1,2,3]')::VECTOR(FLOAT,3)"));
        assertEquals("ACCEPTED: [1.000000,2.000000,3.000000]",
            answer("SELECT TO_VARIANT([1,2,3])::VECTOR(FLOAT,3)"));
        assertEquals("ACCEPTED: null",
            answer("SELECT NULL::VECTOR(FLOAT,3)"));
        assertEquals("Array-like value being cast to a float vector has elements that are not real numbers",
            answer("SELECT [1,2,NULL]::VECTOR(FLOAT,3)"));
        assertEquals("Vector value being cast to a vector is not an array or vector, or has incorrect dimension or element type",
            answer("SELECT []::VECTOR(FLOAT,3)"));
    }

    /** Beside a FROM clause, TO_VARCHAR's own refusal comes before the cast's value is ever read. */
    @Test
    public void toVarcharIsRefusedBeforeTheCastsValueIsRead() {
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, NULL)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR([1,2,NULL]::VECTOR(FLOAT,3)) FROM vec_store"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(ARRAY_CONSTRUCT(1, 2, null) AS VECTOR(FLOAT, 3)), 1)] expected 1, got 2",
            answer("SELECT UPPER([1,2,NULL]::VECTOR(FLOAT,3), 1)"));
    }

    /** A whole-day unit asked of a TIME is refused on one line, the ADD family naming the TIME's precision. */
    @Test
    public void aWholeDayUnitOverATimeIsRefusedOnOneLine() {
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD(day, 1, tm) FROM ft"));
        assertEquals("SQL compilation error: ['MONTH'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD(month, 1, tm) FROM ft"));
        assertEquals("SQL compilation error: ['WEEK'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD(week, 1, tm) FROM ft"));
        assertEquals("SQL compilation error: ['YEAR'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD(year, 1, tm) FROM ft"));
        assertEquals("SQL compilation error: ['DD'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD(dd, 1, tm) FROM ft"));
        assertEquals("SQL compilation error: ['day'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD('day', 1, tm) FROM ft"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD('DAY', 1, tm) FROM ft"));
        assertEquals("SQL compilation error: ['Day'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD('Day', 1, tm) FROM ft"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD(day, 1, tm::TIME) FROM ft"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(3).",
            answer("SELECT DATEADD(day, 1, tm::TIME(3)) FROM ft"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(0).",
            answer("SELECT DATEADD(day, 1, tm::TIME(0)) FROM ft"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(3).",
            answer("SELECT DATEADD(day, 1, t3) FROM ft"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD(day, 1, tm) FROM ft WHERE FALSE"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD(day, 1, '10:00:00'::TIME)"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD(day, 1, TO_TIME('10:00:00'))"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9).",
            answer("SELECT DATEADD(day, 1, TIME_FROM_PARTS(1, 2, 3))"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function TIMEADD and type TIME(9).",
            answer("SELECT TIMEADD(day, 1, tm) FROM ft"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component for function TIMESTAMPADD and type TIME(9).",
            answer("SELECT TIMESTAMPADD(day, 1, tm) FROM ft"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function DATEDIFF and type TIME.",
            answer("SELECT DATEDIFF(day, tm, tm) FROM ft"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function DATEDIFF and type TIME.",
            answer("SELECT DATEDIFF(day, t3, t3) FROM ft"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function DATEDIFF and type TIME.",
            answer("SELECT DATEDIFF(day, '10:00:00'::TIME, tm) FROM ft"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function TIMEDIFF and type TIME.",
            answer("SELECT TIMEDIFF(day, tm, tm) FROM ft"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function TIMESTAMPDIFF and type TIME.",
            answer("SELECT TIMESTAMPDIFF(day, tm, tm) FROM ft"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function DATE_TRUNC and type TIME.",
            answer("SELECT DATE_TRUNC('day', tm) FROM ft"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function DATE_TRUNC and type TIME.",
            answer("SELECT DATE_TRUNC('DAY', tm) FROM ft"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function DATE_TRUNC and type TIME.",
            answer("SELECT DATE_TRUNC(day, tm) FROM ft"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function DATE_TRUNC and type TIME.",
            answer("SELECT DATE_TRUNC('day', '10:00:00'::TIME)"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component for function TRUNC and type TIME.",
            answer("SELECT TRUNC(tm, 'day') FROM ft"));
        assertEquals("SQL compilation error:|invalid value [DAY] for parameter 'DATE_PART date/time part'",
            answer("SELECT DATE_PART(day, tm) FROM ft"));
    }

    /** A word that is no date/time unit is refused on one line too; DATE_PART keeps its own layout. */
    @Test
    public void aWordThatIsNoUnitIsRefusedOnOneLine() {
        assertEquals("SQL compilation error: ['ZZ'] is not a valid date/time component for function DATEADD.",
            answer("SELECT DATEADD(zz, 1, ts) FROM test_schema.ft"));
        assertEquals("SQL compilation error: ['zz'] is not a valid date/time component for function DATEADD.",
            answer("SELECT DATEADD('zz', 1, ts) FROM test_schema.ft"));
        assertEquals("SQL compilation error: ['WOY'] is not a valid date/time component for function DATEADD.",
            answer("SELECT DATEADD(woy, 1, ts) FROM test_schema.ft"));
        assertEquals("SQL compilation error: ['ZZ'] is not a valid date/time component for function DATEDIFF.",
            answer("SELECT DATEDIFF(zz, ts, ts) FROM test_schema.ft"));
        assertEquals("SQL compilation error: ['ZZ'] is not a valid date/time component for function DATE_TRUNC.",
            answer("SELECT DATE_TRUNC(zz, ts) FROM test_schema.ft"));
        assertEquals("SQL compilation error: ['zz'] is not a valid date/time component for function DATE_TRUNC.",
            answer("SELECT DATE_TRUNC('zz', d) FROM test_schema.ft"));
        assertEquals("SQL compilation error: ['ZZ'] is not a valid date/time component for function LAST_DAY.",
            answer("SELECT LAST_DAY(ts, zz) FROM test_schema.ft"));
        assertEquals("SQL compilation error:|invalid value [ZZ] for parameter 'DATE_PART date/time part'",
            answer("SELECT DATE_PART(zz, ts) FROM test_schema.ft"));
    }
}
