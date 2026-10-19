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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * How a refusal echoes a cast into or out of a shape, and which of those casts are refused. In an invalid-type
 * sentence a cast whose source or target is a structured type - an ARRAY, an OBJECT or a MAP written with its
 * shape - is {@code STRUCTURED_CAST(x)}, its target never spelled, and a vector read into an array is {@code
 * FROM_VECTOR(x)}, whether a cast or TO_ARRAY reads it. TO_ARRAY, TO_OBJECT and TO_VARIANT over a value that is
 * one already are {@code identity(x)}, over a shaped one a STRUCTURED_CAST. A MAP and a vector convert to no
 * scalar, a vector to no OBJECT and to an ARRAY of its own elements' kind alone, and a MAP or a shaped OBJECT
 * wraps into no ARRAY: each is refused while the statement compiles. Every answer is live-verified.
 */
public class ShapedCastEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aCastIntoOrOutOfAShapeIsAStructuredCast() {
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1.5, 0.1234567, 3)))) AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT TO_ARRAY([1.5,0.1234567,3]::VECTOR(FLOAT,3))::ARRAY(FLOAT)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(TO_VECTOR(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1.5, 0.1234567, 3)))) AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT TO_ARRAY([1.5,0.1234567,3]::VECTOR(FLOAT,3))::VECTOR(FLOAT,3)::VARCHAR");
        assertAnswer(
            "[1,2,3]",
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))) AS VARCHAR(134217728))] "
                + "for parameter 'TO_VARCHAR'",
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))::ARRAY(INT)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)) AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT [1,2]::ARRAY(INT)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)) AS "
                + "NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT [1,2]::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)) AS DATE)] "
                + "for parameter 'TO_DATE'",
            "SELECT ARRAY_CONSTRUCT(1,2)::ARRAY(NUMBER)::DATE");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)) AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT OBJECT_CONSTRUCT('a', 1)::OBJECT(a INT)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)) AS "
                + "NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT OBJECT_CONSTRUCT('a', 1)::OBJECT(a INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)) AS "
                + "NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT OBJECT_CONSTRUCT('a', 1)::MAP(VARCHAR, INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)) AS "
                + "NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT CAST(CAST([1,2] AS ARRAY(INT)) AS NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT TRY_CAST([1,2]::ARRAY(INT) AS NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))) "
                + "AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))) "
                + "AS DATE)] for parameter 'TO_DATE'",
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))::DATE");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ABS': (ARRAY)",
            "SELECT ABS(TO_ARRAY([1,2,3]::VECTOR(INT,3)))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(TO_VECTOR(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))::VECTOR(INT,3)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT [1,2,3]::VECTOR(INT,3)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS NUMBER(38,"
                + "0))] for parameter 'TO_NUMBER'",
            "SELECT [1,2,3]::VECTOR(INT,3)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | Invalid argument types for function "
                + "'+': (ARRAY, NUMBER(1,0))",
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3)) + 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'UPPER': (ARRAY)",
            "SELECT UPPER(TO_ARRAY([1,2,3]::VECTOR(INT,3)))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT [1,2]::ARRAY(INT)::ARRAY(VARCHAR)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT [1,2]::ARRAY(INT)::ARRAY::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(identity(ARRAY_CONSTRUCT(1, 2))) "
                + "AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT [1,2]::ARRAY::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED Failed to cast variant value [1,2] to FIXED",
            "SELECT TO_VARIANT([1,2]::ARRAY(INT))::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(AS_ARRAY(identity(ARRAY_CONSTRUCT(1, 2)))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT AS_ARRAY(TO_VARIANT([1,2]))::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(TO_ARRAY(1)) AS NUMBER(38,0))] "
                + "for parameter 'TO_NUMBER'",
            "SELECT TO_ARRAY(1)::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_ARRAY('a') AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT TO_ARRAY('a')::NUMBER");
        assertAnswer(
            "[1.500000000000000e+00,1.234567016363144e-01,3.000000000000000e+00]",
            "SELECT TO_ARRAY([1.5,0.1234567,3]::VECTOR(FLOAT,3))::ARRAY(FLOAT)");
        assertAnswer(
            "[1,2,3]",
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))");
        assertAnswer(
            "ARRAY[LOB]|ARRAY[LOB]",
            "SELECT SYSTEM$TYPEOF(TO_ARRAY([1,2,3]::VECTOR(INT,3))), SYSTEM$TYPEOF(TO_ARRAY([1.5]::VECTOR(FLOAT,"
                + "1)))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))) "
                + "AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT [1,2,3]::VECTOR(INT,3)::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))) "
                + "AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT [1,2,3]::VECTOR(INT,3)::ARRAY::NUMBER");
        assertAnswer(
            "[1,2,3]",
            "SELECT [1,2,3]::VECTOR(INT,3)::ARRAY(INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(identity(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1,"
                + " 2, 3)))) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT TO_ARRAY(TO_ARRAY([1,2,3]::VECTOR(INT,3)))::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(ARRAY_SIZE(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))) AS DATE)] for parameter "
                + "'TO_DATE'",
            "SELECT ARRAY_SIZE(TO_ARRAY([1,2,3]::VECTOR(INT,3)))::DATE");
        answer(
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))[0]::DATE");
        answer(
            "SELECT GET(TO_ARRAY([1,2,3]::VECTOR(INT,3)), 0)::DATE");
        assertAnswer(
            "15253",
            "SELECT ARRAY_TO_STRING(TO_ARRAY([1,2,3]::VECTOR(INT,3)), 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter '5' of type [NUMBER(1,0)] into expected "
                + "type [ARRAY(NUMBER(38,0))]",
            "SELECT [1,2]::ARRAY(INT) = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter '5' of type [NUMBER(1,0)] into expected "
                + "type [ARRAY]",
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3)) = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter '5' of type [NUMBER(1,0)] into expected "
                + "type [ARRAY]",
            "SELECT IFF(TRUE, TO_ARRAY([1,2,3]::VECTOR(INT,3)), 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter '5' of type [NUMBER(1,0)] into expected "
                + "type [ARRAY]",
            "SELECT COALESCE(TO_ARRAY([1,2,3]::VECTOR(INT,3)), 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT OBJECT_CONSTRUCT('a', 1)::OBJECT(a INT)::OBJECT(a VARCHAR)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)) AS "
                + "DATE)] for parameter 'TO_DATE'",
            "SELECT {'a': 1}::OBJECT(a INT)::DATE");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)) AS "
                + "BOOLEAN)] for parameter 'TO_BOOLEAN'",
            "SELECT [1,2]::ARRAY(INT)::BOOLEAN");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))) "
                + "AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))::BOOLEAN");
    }

    @Test
    public void overColumns() {
        answer(
            "CREATE OR REPLACE TABLE vt (v VECTOR(INT,3), a ARRAY(INT), o OBJECT(a INT), p ARRAY)");
        answer(
            "INSERT INTO vt SELECT [1,2,3]::VECTOR(INT,3), [1,2]::ARRAY(INT), {'a': 1}::OBJECT(a INT), [1,2]");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(VT.V) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT TO_ARRAY(v)::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(FROM_VECTOR(VT.V)) AS NUMBER(38,"
                + "0))] for parameter 'TO_NUMBER'",
            "SELECT TO_ARRAY(v)::ARRAY(INT)::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(VT.A AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT a::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(VT.A) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT a::ARRAY(VARCHAR)::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(VT.O AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT o::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(VT.P) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT p::ARRAY(INT)::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(VT.V) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT v::ARRAY(INT)::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(VT.V) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT v::ARRAY::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(VT.V AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT v::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(VT.A) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT TO_ARRAY(a)::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(identity(VT.P) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT TO_ARRAY(p)::NUMBER FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'STRUCTURED_OBJECT'.",
            "SELECT TO_ARRAY(o)::NUMBER FROM vt");
        assertAnswer(
            "[1,2,3]|[1,2]|[1,2]",
            "SELECT TO_ARRAY(v), TO_ARRAY(a), TO_ARRAY(p) FROM vt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(X.V2 AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT x.v2::NUMBER FROM (SELECT TO_ARRAY(v) v2 FROM vt) x");
        answer(
            "SELECT TO_ARRAY(vt.v)::DATE FROM vt WHERE TO_ARRAY(v)::NUMBER = 1");
    }

    @Test
    public void conversionsOverContainersAndTheCastsRefused() {
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(identity(OBJECT_CONSTRUCT('a', "
                + "1))) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT TO_VARIANT({'a': 1})::OBJECT(a INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(identity(ARRAY_CONSTRUCT(1, 2))) "
                + "AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT TO_VARIANT([1,2])::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(TO_VARIANT(1)) AS NUMBER(38,0))] "
                + "for parameter 'TO_NUMBER'",
            "SELECT TO_VARIANT(1)::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(TO_VARIANT('a')) AS NUMBER(38,"
                + "0))] for parameter 'TO_NUMBER'",
            "SELECT TO_VARIANT('a')::ARRAY(VARCHAR)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(identity(OBJECT_CONSTRUCT('a', "
                + "1))) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT TO_OBJECT({'a': 1})::OBJECT(a INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT TO_OBJECT({'a': 1}::OBJECT(a INT))::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT {'a': 1}::OBJECT(a INT)::OBJECT::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT {'a': 1}::OBJECT(a INT)::MAP(VARCHAR, INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT {'a': 1}::MAP(VARCHAR, INT)::OBJECT::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT {'a': 1}::MAP(VARCHAR, INT)::OBJECT(a INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)))) AS NUMBER(38,0))] "
                + "for parameter 'TO_NUMBER'",
            "SELECT [1,2]::ARRAY(INT)::VARIANT::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED Failed to cast variant value [1,2] to FIXED",
            "SELECT [1,2]::ARRAY(INT)::VARIANT::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)) AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT {'a': 1}::MAP(VARCHAR, INT)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)) AS "
                + "DATE)] for parameter 'TO_DATE'",
            "SELECT {'a': 1}::MAP(VARCHAR, INT)::DATE");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)) AS "
                + "BOOLEAN)] for parameter 'TO_BOOLEAN'",
            "SELECT {'a': 1}::MAP(VARCHAR, INT)::BOOLEAN");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)) AS "
                + "FLOAT)] for parameter 'TO_DOUBLE'",
            "SELECT {'a': 1}::MAP(VARCHAR, INT)::FLOAT");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)) AS "
                + "BINARY(67108864))] for parameter 'TO_BINARY'",
            "SELECT {'a': 1}::MAP(VARCHAR, INT)::BINARY");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'MAP'.",
            "SELECT {'a': 1}::MAP(VARCHAR, INT)::ARRAY");
        assertAnswer(
            "{\"a\":1}",
            "SELECT {'a': 1}::MAP(VARCHAR, INT)::VARIANT");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS DATE)] "
                + "for parameter 'TO_DATE'",
            "SELECT [1,2,3]::VECTOR(INT,3)::DATE");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS BOOLEAN)] "
                + "for parameter 'TO_BOOLEAN'",
            "SELECT [1,2,3]::VECTOR(INT,3)::BOOLEAN");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS FLOAT)] "
                + "for parameter 'TO_DOUBLE'",
            "SELECT [1,2,3]::VECTOR(INT,3)::FLOAT");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS "
                + "BINARY(67108864))] for parameter 'TO_BINARY'",
            "SELECT [1,2,3]::VECTOR(INT,3)::BINARY");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS TIME(9))] "
                + "for parameter 'TO_TIME'",
            "SELECT [1,2,3]::VECTOR(INT,3)::TIME");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS "
                + "TIMESTAMP_NTZ(9))] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT [1,2,3]::VECTOR(INT,3)::TIMESTAMP");
        assertAnswer(
            "[1,2,3]",
            "SELECT [1,2,3]::VECTOR(INT,3)::VARIANT");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)) AS OBJECT)] "
                + "for parameter 'TO_OBJECT'",
            "SELECT [1,2,3]::VECTOR(INT,3)::OBJECT");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for function "
                + "'CAST': (VECTOR(INT, 3))",
            "SELECT [1,2,3]::VECTOR(INT,3)::VECTOR(FLOAT,3)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for function "
                + "'CAST': (VECTOR(INT, 3))",
            "SELECT [1,2,3]::VECTOR(INT,3)::ARRAY(VARCHAR)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for function "
                + "'CAST': (VECTOR(INT, 3))",
            "SELECT [1,2,3]::VECTOR(INT,3)::ARRAY(FLOAT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT TRY_CAST([1,2,3]::VECTOR(INT,3) AS NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)))] "
                + "for parameter 'TO_NUMBER'",
            "SELECT TRY_CAST({'a': 1}::MAP(VARCHAR, INT) AS NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_TO_NUMBER(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] "
                + "for parameter 'TO_NUMBER'",
            "SELECT TRY_TO_NUMBER([1,2,3]::VECTOR(INT,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_TO_NUMBER(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, "
                + "2)))] for parameter 'TO_NUMBER'",
            "SELECT TRY_TO_NUMBER([1,2]::ARRAY(INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_NUMBER(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)))] "
                + "for parameter 'TO_NUMBER'",
            "SELECT TO_NUMBER([1,2]::ARRAY(INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_NUMBER(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT TO_NUMBER([1,2,3]::VECTOR(INT,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, "
                + "3))))] for parameter 'TO_DATE'",
            "SELECT TO_DATE(TO_ARRAY([1,2,3]::VECTOR(INT,3)))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[TO_CHAR(STRUCTURED_CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))))] for parameter "
                + "'TO_CHAR'",
            "SELECT TO_CHAR(TO_ARRAY([1,2,3]::VECTOR(INT,3))::ARRAY(INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)))] "
                + "for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR([1,2]::ARRAY(INT))");
        answer(
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))[0]::DATE");
        assertAnswer(
            "REFUSED Failed to cast variant value 1 to DATE",
            "SELECT PARSE_JSON('1')::DATE");
        answer(
            "SELECT [1,2][0]::DATE");
        answer(
            "SELECT GET([1,2], 0)::DATE");
        assertAnswer(
            "REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARIANT(1)::DATE");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'STRUCTURED_OBJECT'.",
            "SELECT TO_ARRAY({'a': 1}::OBJECT(a INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'MAP'.",
            "SELECT TO_ARRAY({'a': 1}::MAP(VARCHAR, INT))");
        assertAnswer(
            "[{\"a\":1}]",
            "SELECT TO_ARRAY({'a': 1})");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'STRUCTURED_OBJECT'.",
            "SELECT TO_ARRAY({'a': 1}::OBJECT(a INT))::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ARRAY] for predicate "
                + "[CAST(CAST(ARRAY_CONSTRUCT(1, 2, 3) AS VECTOR(INT, 3)) AS ARRAY)]",
            "SELECT 1 WHERE TO_ARRAY([1,2,3]::VECTOR(INT,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ARRAY(NUMBER(38,0))] for predicate "
                + "[CAST(ARRAY_CONSTRUCT(1, 2) AS ARRAY(NUMBER(38,0)))]",
            "SELECT 1 WHERE [1,2]::ARRAY(INT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(CAST(CAST(ARRAY_CONSTRUCT(1, 2, 3) AS VECTOR(INT, 3)) AS ARRAY), 1)] expected 1, got 2",
            "SELECT ABS(TO_ARRAY([1,2,3]::VECTOR(INT,3)), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(CAST(ARRAY_CONSTRUCT(1, 2) AS ARRAY(NUMBER(38,0))), 1)] expected 1, got 2",
            "SELECT ABS([1,2]::ARRAY(INT), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(CAST(CAST(ARRAY_CONSTRUCT(1, 2, 3) AS VECTOR(INT, 3)) AS ARRAY(NUMBER(38,0))), 1)] expected "
                + "1, got 2",
            "SELECT ABS([1,2,3]::VECTOR(INT,3)::ARRAY(INT), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(ARRAY_CONSTRUCT(1, 2), 1)] expected 1, got 2",
            "SELECT ABS(TO_VARIANT([1,2]), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(CAST(CAST(ARRAY_CONSTRUCT(1, 2, 3) AS VECTOR(INT, 3)) AS ARRAY), 1)] expected 1, got 2",
            "SELECT ABS(TO_ARRAY(TO_ARRAY([1,2,3]::VECTOR(INT,3))), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(ARRAY_CONSTRUCT(1, 2), 1)] expected 1, got 2",
            "SELECT ABS(TO_ARRAY([1,2]), 1)");
        assertAnswer(
            "REFUSED Failed to cast variant value [1,2,3] to FIXED",
            "SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))::ARRAY(INT)::VARIANT::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(ARRAY_SIZE(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))) AS DATE)] for parameter "
                + "'TO_DATE'",
            "SELECT ARRAY_SIZE([1,2,3]::VECTOR(INT,3)::ARRAY(INT))::DATE");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ARRAY_SIZE(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, "
                + "2))) AS DATE)] for parameter 'TO_DATE'",
            "SELECT ARRAY_SIZE([1,2]::ARRAY(INT))::DATE");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ARRAY_SIZE(identity(ARRAY_CONSTRUCT(1, 2))) AS "
                + "DATE)] for parameter 'TO_DATE'",
            "SELECT ARRAY_SIZE(TO_VARIANT([1,2]))::DATE");
    }

    @Test
    public void otherShapedCastsAndThePlanEchoes() {
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'STRUCTURED_OBJECT'.",
            "SELECT {'a': 1}::OBJECT(a INT)::ARRAY");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'STRUCTURED_OBJECT'.",
            "SELECT {'a': 1}::OBJECT(a INT)::ARRAY::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_TO_NUMBER(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', "
                + "1)))] for parameter 'TO_NUMBER'",
            "SELECT TRY_TO_NUMBER({'a': 1}::MAP(VARCHAR, INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_TO_DATE(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for "
                + "parameter 'TO_DATE'",
            "SELECT TRY_TO_DATE([1,2,3]::VECTOR(INT,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_TO_BOOLEAN(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', "
                + "1)))] for parameter 'TO_BOOLEAN'",
            "SELECT TRY_TO_BOOLEAN({'a': 1}::MAP(VARCHAR, INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)))] "
                + "for parameter 'TO_DATE'",
            "SELECT TO_DATE({'a': 1}::MAP(VARCHAR, INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for "
                + "parameter 'TO_BOOLEAN'",
            "SELECT TO_BOOLEAN([1,2,3]::VECTOR(INT,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for "
                + "parameter 'TO_DOUBLE'",
            "SELECT TO_DOUBLE([1,2,3]::VECTOR(INT,3))");
        assertAnswer(
            "[1,2,3]",
            "SELECT [1,2,3]::VECTOR(INT,3)::ARRAY(NUMBER(10,2))");
        assertAnswer(
            "[1.500000000000000e+00,2.000000000000000e+00,3.000000000000000e+00]",
            "SELECT [1.5,2,3]::VECTOR(FLOAT,3)::ARRAY(FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for function "
                + "'CAST': (VECTOR(FLOAT, 3))",
            "SELECT [1.5,2,3]::VECTOR(FLOAT,3)::ARRAY(INT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for function "
                + "'CAST': (VECTOR(FLOAT, 3))",
            "SELECT [1.5,2,3]::VECTOR(FLOAT,3)::ARRAY(VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | incompatible types: [VECTOR] and [STRUCTURED_OBJECT]",
            "SELECT [1,2,3]::VECTOR(INT,3)::OBJECT(a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | incompatible types: [VECTOR] and [MAP]",
            "SELECT [1,2,3]::VECTOR(INT,3)::MAP(VARCHAR, INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | incompatible types: [VECTOR] and [MAP]",
            "SELECT [1,2,3]::VECTOR(INT,3)::MAP(VARCHAR, INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT([1,2,3]::VECTOR(INT,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1))) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT TO_OBJECT({'a': 1}::MAP(VARCHAR, INT))::NUMBER");
        assertAnswer(
            "{\"a\":1}",
            "SELECT TO_OBJECT({'a': 1}::MAP(VARCHAR, INT))");
        assertAnswer(
            "REFUSED Failed to cast variant value {\"a\":1} to FIXED",
            "SELECT TO_VARIANT({'a': 1}::MAP(VARCHAR, INT))::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)))) AS NUMBER(38,0))] "
                + "for parameter 'TO_NUMBER'",
            "SELECT TO_VARIANT([1,2]::ARRAY(INT))::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(STRUCTURED_CAST(STRUCTURED_CAST(OBJECT_CONSTRUCT('a', 1)))) AS NUMBER(38,"
                + "0))] for parameter 'TO_NUMBER'",
            "SELECT TO_VARIANT({'a': 1}::OBJECT(a INT))::OBJECT(a INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(TO_VARIANT(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT TO_VARIANT([1,2,3]::VECTOR(INT,3))::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(AS_OBJECT(identity(OBJECT_CONSTRUCT('a', 1)))) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT AS_OBJECT(TO_VARIANT({'a': 1}))::OBJECT(a INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(STRUCTURED_CAST(identity(TO_VARIANT(1))) AS "
                + "NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT TO_VARIANT(TO_VARIANT(1))::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(OBJECT_CONSTRUCT('a', 1), 1)] expected 1, got 2",
            "SELECT ABS(TO_VARIANT({'a': 1}), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(PARSE_JSON('1'), 1)] expected 1, got 2",
            "SELECT ABS(TO_VARIANT(PARSE_JSON('1')), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(OBJECT_CONSTRUCT('a', 1), 1)] expected 1, got 2",
            "SELECT ABS(TO_OBJECT({'a': 1}), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(CAST(CAST(ARRAY_CONSTRUCT(1, 2) AS ARRAY(NUMBER(38,0))) AS ARRAY), 1)] expected 1, got 2",
            "SELECT ABS(TO_ARRAY([1,2]::ARRAY(INT)), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(CAST(CAST(OBJECT_CONSTRUCT('a', 1) AS OBJECT(a NUMBER(38,0))) AS OBJECT), 1)] expected 1, "
                + "got 2",
            "SELECT ABS({'a': 1}::OBJECT(a INT)::OBJECT, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(CAST(CAST(ARRAY_CONSTRUCT(1, 2) AS ARRAY(NUMBER(38,0))) AS ARRAY), 1)] expected 1, got 2",
            "SELECT ABS([1,2]::ARRAY(INT)::ARRAY, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(CAST(CAST(ARRAY_CONSTRUCT(1, 2) AS ARRAY(NUMBER(38,0))) AS VARIANT), 1)] expected 1, got 2",
            "SELECT ABS(TO_VARIANT([1,2]::ARRAY(INT)), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARIANT] for predicate [ARRAY_CONSTRUCT(1, 2)]",
            "SELECT 1 WHERE TO_VARIANT([1,2])");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ARRAY] for predicate [ARRAY_CONSTRUCT(1, 2)]",
            "SELECT 1 WHERE TO_ARRAY([1,2])");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ARRAY(NUMBER(38,0))] for predicate "
                + "[CAST(CAST(ARRAY_CONSTRUCT(1, 2, 3) AS VECTOR(INT, 3)) AS ARRAY(NUMBER(38,0)))]",
            "SELECT 1 WHERE [1,2,3]::VECTOR(INT,3)::ARRAY(INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT [1,2,3]::VECTOR(INT,3)::ARRAY(INT)::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT [1,2,3]::VECTOR(INT,3)::ARRAY::ARRAY(INT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for "
                + "parameter 'TO_DATE'",
            "SELECT TRY_CAST([1,2,3]::VECTOR(INT,3) AS DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for "
                + "parameter 'TO_VARCHAR'",
            "SELECT TRY_CAST([1,2,3]::VECTOR(INT,3) AS VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types VECTOR(INT,"
                + " 3) and ARRAY",
            "SELECT TRY_CAST([1,2,3]::VECTOR(INT,3) AS ARRAY)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_CAST(TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TRY_CAST([1,2,3]::VECTOR(INT,3) AS OBJECT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1.5, 2, 3)) AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT [1.5,2,3]::VECTOR(FLOAT,3)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1.5, 2, 3)) AS "
                + "NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT [1.5,2,3]::VECTOR(FLOAT,3)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[CAST(STRUCTURED_CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1.5, 2, 3)))) AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "SELECT TO_ARRAY([1.5,2,3]::VECTOR(FLOAT,3))::ARRAY(FLOAT)::NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(TO_VECTOR(ARRAY_CONSTRUCT(1.5, 2, "
                + "3))) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT [1.5,2,3]::VECTOR(FLOAT,3)::ARRAY(FLOAT)::NUMBER");
    }
}
