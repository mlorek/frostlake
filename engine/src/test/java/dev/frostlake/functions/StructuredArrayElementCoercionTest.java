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
 * The element ARRAY_APPEND, ARRAY_PREPEND and ARRAY_INSERT add to a structured ARRAY takes the array's element type
 * (live-verified). It is cast as the call reads it: a NUMBER element at the array's scale, a text into a number, a
 * number into a text. The call answers the array's structured type. A static type that does not bind to the element
 * type is refused while the statement compiles. A text that reads as no number is refused in the cast's own sentence,
 * even where it stops a CTAS or an INSERT.
 */
public class StructuredArrayElementCoercionTest extends MeasuredCellsTestSupport {

    @Test
    public void anAddedElementTakesTheArraysElementType() {
        assertCells(new String[][] {
            {"SELECT ARRAY_APPEND([1,2]::ARRAY(INT), 2.7) AS c, c[2], SYSTEM$TYPEOF(c), SYSTEM$TYPEOF(c[2])",
                "[1,2,3]|3|ARRAY(NUMBER(38,0))[LOB]|NUMBER(38,0)[SB16]"},
            {"SELECT ARRAY_APPEND([1.5]::ARRAY(DOUBLE), '2.5') AS c, c[1], c[1] + 1, SYSTEM$TYPEOF(c[1])",
                "[1.500000000000000e+00,2.500000000000000e+00]|2.5|3.5|FLOAT[DOUBLE]"},
            {"SELECT ARRAY_APPEND([1.5, 3]::ARRAY(NUMBER(10,2)), 'zz')",
                "REFUSED Numeric value 'zz' is not recognized"},
            {"SELECT ARRAY_APPEND([1.5, 3]::ARRAY(NUMBER(10,2)), CURRENT_DATE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(NUMBER(10,2)), DATE)"},
            {"SELECT ARRAY_APPEND([1,2]::ARRAY(INT), '3') AS c, SYSTEM$TYPEOF(c[2])", "[1,2,3]|NUMBER(38,0)[SB16]"},
            {"SELECT ARRAY_APPEND(['a']::ARRAY(VARCHAR), 5) AS c, SYSTEM$TYPEOF(c[1])", "[\"a\",\"5\"]|VARCHAR[LOB]"},
            {"SELECT ARRAY_APPEND([1,2]::ARRAY(INT), [3])",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(NUMBER(38,0)), ARRAY)"},
            {"SELECT ARRAY_APPEND([1,2]::ARRAY(INT), 3::VARIANT) AS c, SYSTEM$TYPEOF(c)",
                "[1,2,3]|ARRAY(NUMBER(38,0))[LOB]"},
            {"SELECT ARRAY_APPEND([1,2]::ARRAY(INT), 'zz'::VARIANT) AS c",
                "REFUSED Failed to cast variant value \"zz\" to FIXED"},
            {"SELECT ARRAY_APPEND([1,2]::ARRAY(INT), NULL) AS c, SYSTEM$TYPEOF(c)",
                "[1,2,undefined]|ARRAY(NUMBER(38,0))[LOB]"},
            {"SELECT ARRAY_APPEND([1,2]::ARRAY(INT), TRUE) AS c",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(NUMBER(38,0)), BOOLEAN)"},
            {"SELECT ARRAY_APPEND(['2024-01-01']::ARRAY(DATE), '2024-02-01') AS c, SYSTEM$TYPEOF(c[1])",
                "[\"2024-01-01\",\"2024-02-01\"]|DATE[SB4]"},
            {"SELECT ARRAY_APPEND([1,2]::ARRAY(NUMBER(3,0)), 1000) AS c", "[1,2,1000]"},
            {"SELECT ARRAY_APPEND([1,2]::ARRAY(NUMBER(3,1)), 1.25) AS c", "[1,2,1.3]"},
            {"SELECT ARRAY_PREPEND([1,2]::ARRAY(INT), 2.7) AS c, SYSTEM$TYPEOF(c)", "[3,1,2]|ARRAY(NUMBER(38,0))[LOB]"},
            {"SELECT ARRAY_PREPEND([1,2]::ARRAY(INT), 'zz')", "REFUSED Numeric value 'zz' is not recognized"},
            {"SELECT ARRAY_PREPEND([1,2]::ARRAY(INT), CURRENT_DATE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_PREPEND': (ARRAY(NUMBER(38,0)), DATE)"},
            {"SELECT ARRAY_INSERT([1,2]::ARRAY(INT), 1, 2.7) AS c, SYSTEM$TYPEOF(c)",
                "[1,3,2]|ARRAY(NUMBER(38,0))[LOB]"},
            {"SELECT ARRAY_INSERT([1,2]::ARRAY(INT), 1, 'zz')", "REFUSED Numeric value 'zz' is not recognized"},
            {"SELECT ARRAY_INSERT([1.5]::ARRAY(DOUBLE), 0, '2.5') AS c, c[0], SYSTEM$TYPEOF(c[0])",
                "[2.500000000000000e+00,1.500000000000000e+00]|2.5|FLOAT[DOUBLE]"},
            {"SELECT ARRAY_INSERT([1,2]::ARRAY(INT), 5, 3) AS c", "[1,2,undefined,undefined,undefined,3]"},
        });
        assertShape("C:ARRAY, P:ARRAY, I:ARRAY, D:ARRAY",
            "SELECT ARRAY_APPEND([1,2]::ARRAY(INT), 2.7) AS c, ARRAY_PREPEND([1,2]::ARRAY(INT), 2) AS p, "
                + "ARRAY_INSERT([1,2]::ARRAY(INT), 0, 2) AS i, ARRAY_APPEND([1.5]::ARRAY(DOUBLE), '2.5') AS d");
        assertShape("E:NUMBER(38,0), F:NUMBER(38,0)",
            "SELECT ARRAY_APPEND([1,2]::ARRAY(INT), 2.7)[2] AS e, ARRAY_PREPEND([1,2]::ARRAY(INT), 2)[0] AS "
                + "f");
    }

    @Test
    public void aStoredStructuredArrayTakesItsElementsTyped() {
        engine.execute("CREATE OR REPLACE TABLE t (arr ARRAY(NUMBER(10,2)))");
        engine.execute("INSERT INTO t SELECT [1.5, 3]::ARRAY(NUMBER(10,2))");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE t2 AS SELECT ARRAY_APPEND(arr, 'zz') AS x FROM t",
                "REFUSED Numeric value 'zz' is not recognized"},
            {"CREATE OR REPLACE TABLE t3 AS SELECT ARRAY_APPEND(arr, '4.25') AS x FROM t",
                "Table T3 successfully created."},
        });
        assertShape("X:ARRAY", "SELECT x FROM t3");
        assertCells(new String[][] {
            {"SELECT x, x[2], SYSTEM$TYPEOF(x[2]) FROM t3", "[1.5,3,4.25]|4.25|NUMBER(10,2)[SB2]"},
            {"SELECT SUM(x[2]) FROM t3", "4.25"},
            {"CREATE OR REPLACE TABLE t4 AS SELECT ARRAY_APPEND(arr, 2.789) AS x FROM t",
                "Table T4 successfully created."},
            {"SELECT x FROM t4", "[1.5,3,2.79]"},
            {"INSERT INTO t SELECT ARRAY_APPEND(arr, 'zz') FROM t", "REFUSED Numeric value 'zz' is not recognized"},
            {"INSERT INTO t SELECT ARRAY_APPEND(arr, '7') FROM t", "1"},
            {"SELECT arr FROM t ORDER BY ARRAY_SIZE(arr)", "[1.5,3] ;; [1.5,3,7]"},
            {"SELECT ARRAY_APPEND(arr, x) FROM t, (SELECT 'zz' AS x)", "REFUSED Numeric value 'zz' is not recognized"},
            {"SELECT ARRAY_APPEND(arr, x) FROM t, (SELECT CURRENT_DATE AS x)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(NUMBER(10,2)), DATE)"},
        });
    }
}
