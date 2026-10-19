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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * ARRAY_CAT over two structured arrays answers the type that unifies their elements - numbers to FLOAT beside an
 * approximate one and otherwise to NUMBER(38, s) of the larger scale, an OBJECT's fields in turn - each element
 * converted to it, and refuses arrays that do not unify, a text beside a number, a nesting of another depth, an
 * OBJECT of other fields, naming the second argument against the first's type; a semi-structured second array is
 * an invalid argument type (all live-verified).
 */
public class ArrayCatStructuredTest extends MeasuredCellsTestSupport {

    @Test
    public void structuredArraysJoinUnderTheTypeThatHoldsBoth() {
        engine.execute("CREATE OR REPLACE TABLE act (arr ARRAY(ARRAY(INT)), o ARRAY(OBJECT(a INT)))");
        engine.execute("INSERT INTO act SELECT [[1]]::ARRAY(ARRAY(INT)), [{'a':1}]::ARRAY(OBJECT(a INT))");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT('zz')) AS ARRAY(ARRAY(VARCHAR(134217728))))' of type "
                + "[ARRAY(ARRAY(VARCHAR(134217728)))] into expected type [ARRAY(ARRAY(NUMBER(38,0)))]",
            "SELECT ARRAY_CAT(arr, [['zz']]::ARRAY(ARRAY(VARCHAR))) AS x FROM act");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('a', 'zz')) AS ARRAY(OBJECT(a VARCHAR(134217728))))' of "
                + "type [ARRAY(OBJECT(a VARCHAR(134217728)))] into expected type [ARRAY(OBJECT(a NUMBER(38,0)))]",
            "SELECT ARRAY_CAT(o, [{'a':'zz'}]::ARRAY(OBJECT(a VARCHAR))) AS y FROM act");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT('zz')) AS ARRAY(ARRAY(VARCHAR(134217728))))' of type "
                + "[ARRAY(ARRAY(VARCHAR(134217728)))] into expected type [ARRAY(ARRAY(NUMBER(38,0)))]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CAT(arr, [['zz']]::ARRAY(ARRAY(VARCHAR)))) FROM act");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('a', 'zz')) AS ARRAY(OBJECT(a VARCHAR(134217728))))' of "
                + "type [ARRAY(OBJECT(a VARCHAR(134217728)))] into expected type [ARRAY(OBJECT(a NUMBER(38,0)))]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CAT(o, [{'a':'zz'}]::ARRAY(OBJECT(a VARCHAR)))) FROM act");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT('zz')) AS ARRAY(ARRAY(VARCHAR(134217728))))' of type "
                + "[ARRAY(ARRAY(VARCHAR(134217728)))] into expected type [ARRAY(ARRAY(NUMBER(38,0)))]",
            "CREATE OR REPLACE TABLE act2 AS SELECT ARRAY_CAT(arr, [['zz']]::ARRAY(ARRAY(VARCHAR))) AS x FROM act");
        assertAnswer("[[1.000000000000000e+00],[2.600000000000000e+00]]",
            "SELECT ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [[2.6]]::ARRAY(ARRAY(DOUBLE))) AS d");
        assertAnswer("ARRAY(ARRAY(FLOAT))[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [[2.6]]::ARRAY(ARRAY(DOUBLE))))");
        assertAnswer("2.6", "SELECT ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [[2.6]]::ARRAY(ARRAY(DOUBLE)))[1][0]");
        assertAnswer("FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [[2.6]]::ARRAY(ARRAY(DOUBLE)))[1][0])");
        assertAnswer("2.6", "SELECT ARRAY_CAT(o, [{'a':2.6}]::ARRAY(OBJECT(a DOUBLE)))[1]['a'] FROM act");
        assertAnswer("ARRAY(OBJECT(a FLOAT))[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CAT(o, [{'a':2.6}]::ARRAY(OBJECT(a DOUBLE)))) FROM act");
        assertAnswer("[1.000000000000000e+00,2.600000000000000e+00]",
            "SELECT ARRAY_CAT([1]::ARRAY(INT), [2.6]::ARRAY(DOUBLE))");
        assertAnswer("ARRAY(FLOAT)[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_CAT([1]::ARRAY(INT), [2.6]::ARRAY(DOUBLE)))");
        assertAnswer("2.6", "SELECT ARRAY_CAT([1]::ARRAY(INT), [2.6]::ARRAY(DOUBLE))[1]");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT('7')) AS ARRAY(ARRAY(VARCHAR(134217728))))' of type "
                + "[ARRAY(ARRAY(VARCHAR(134217728)))] into expected type [ARRAY(ARRAY(NUMBER(38,0)))]",
            "SELECT ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [['7']]::ARRAY(ARRAY(VARCHAR)))");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT('7')) AS ARRAY(ARRAY(VARCHAR(134217728))))' of type "
                + "[ARRAY(ARRAY(VARCHAR(134217728)))] into expected type [ARRAY(ARRAY(NUMBER(38,0)))]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [['7']]::ARRAY(ARRAY(VARCHAR))))");
        assertAnswer("[[1],[2]]", "SELECT ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [[2]]::ARRAY(ARRAY(NUMBER(10,2))))");
        assertAnswer("ARRAY(ARRAY(NUMBER(38,2)))[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [[2]]::ARRAY(ARRAY(NUMBER(10,2)))))");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1)) AS ARRAY(ARRAY(NUMBER(38,0))))' of type "
                + "[ARRAY(ARRAY(NUMBER(38,0)))] into expected type [ARRAY(ARRAY(VARCHAR(134217728)))]",
            "SELECT ARRAY_CAT([['a']]::ARRAY(ARRAY(VARCHAR)), [[1]]::ARRAY(ARRAY(INT)))");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1)) AS ARRAY(ARRAY(NUMBER(38,0))))' of type "
                + "[ARRAY(ARRAY(NUMBER(38,0)))] into expected type [ARRAY(ARRAY(VARCHAR(134217728)))]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CAT([['a']]::ARRAY(ARRAY(VARCHAR)), [[1]]::ARRAY(ARRAY(INT))))");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter 'CAST(ARRAY_CONSTRUCT(1) AS "
                + "ARRAY(NUMBER(38,0)))' of type [ARRAY(NUMBER(38,0))] into expected type [ARRAY(ARRAY(NUMBER(38,0)))]",
            "SELECT ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [1]::ARRAY(INT))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_CAT': (ARRAY(ARRAY(NUMBER(38,0))), ARRAY)",
            "SELECT ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [[1]])");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for "
                + "function 'ARRAY_CAT': (ARRAY(ARRAY(NUMBER(38,0))), ARRAY)",
            "SELECT SYSTEM$TYPEOF(ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [[1]]))");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('b', 1)) AS ARRAY(OBJECT(b NUMBER(38,0))))' of type "
                + "[ARRAY(OBJECT(b NUMBER(38,0)))] into expected type [ARRAY(OBJECT(a NUMBER(38,0)))]",
            "SELECT ARRAY_CAT([{'a':1}]::ARRAY(OBJECT(a INT)), [{'b':1}]::ARRAY(OBJECT(b INT)))");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter "
                + "'CAST(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('a', 1, 'b', 2)) AS ARRAY(OBJECT(a NUMBER(38,0), b "
                + "NUMBER(38,0))))' of type [ARRAY(OBJECT(a NUMBER(38,0), b NUMBER(38,0)))] into expected type "
                + "[ARRAY(OBJECT(a NUMBER(38,0)))]",
            "SELECT ARRAY_CAT([{'a':1}]::ARRAY(OBJECT(a INT)), [{'a':1,'b':2}]::ARRAY(OBJECT(a INT, b INT)))");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter 'CAST(ARRAY_CONSTRUCT('x') AS "
                + "ARRAY(VARCHAR(134217728)))' of type [ARRAY(VARCHAR(134217728))] into expected type "
                + "[ARRAY(NUMBER(38,0))]",
            "SELECT ARRAY_CAT([1]::ARRAY(INT), ['x']::ARRAY(VARCHAR))");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter 'CAST(ARRAY_CONSTRUCT('x') AS "
                + "ARRAY(VARCHAR(134217728)))' of type [ARRAY(VARCHAR(134217728))] into expected type "
                + "[ARRAY(NUMBER(38,0))]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CAT([1]::ARRAY(INT), ['x']::ARRAY(VARCHAR)))");
    }
}
