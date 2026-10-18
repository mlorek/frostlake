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
 * ARRAY_APPEND, ARRAY_PREPEND and ARRAY_INSERT over an ARRAY whose element type is itself structured
 * (live-verified): a structured element of its own kind is converted to the element type as a cast converts it -
 * {@code [2.6]::ARRAY(DOUBLE)} into an {@code ARRAY(ARRAY(INT))} is {@code [3]} - and a semi-structured ARRAY or
 * OBJECT, or a scalar, is refused while the statement compiles, {@code Invalid argument types for function
 * 'ARRAY_APPEND': (ARRAY(ARRAY(NUMBER(38,0))), ARRAY)} at the call. A CTAS stores the converted value.
 */
public class ArrayAppendStructuredElementTest extends MeasuredCellsTestSupport {

    @Test
    public void aStructuredElementIsConvertedOrRefused() {
        engine.execute("CREATE OR REPLACE TABLE sat (arr ARRAY(ARRAY(INT)), o ARRAY(OBJECT(a INT)))");
        engine.execute("INSERT INTO sat SELECT [[1]]::ARRAY(ARRAY(INT)), [{'a':1}]::ARRAY(OBJECT(a INT))");
        assertCells(new String[][] {
            {"SELECT ARRAY_APPEND(arr, ['zz']) FROM sat",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(ARRAY(NUMBER(38,0))), ARRAY)"},
            {"SELECT ARRAY_APPEND(o, {'a':'zz'}) FROM sat",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(OBJECT(a NUMBER(38,0))), OBJECT)"},
            {"SELECT ARRAY_APPEND(arr, [2.6]::ARRAY(DOUBLE)) FROM sat", "[[1],[3]]"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_APPEND(arr, [2.6]::ARRAY(DOUBLE))) FROM sat",
                "ARRAY(ARRAY(NUMBER(38,0)))[LOB]"},
            {"SELECT ARRAY_APPEND(o, {'a':2.6}::OBJECT(a DOUBLE)) FROM sat", "[{\"a\":1},{\"a\":3}]"},
            {"SELECT ARRAY_APPEND(arr, [2]) FROM sat",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(ARRAY(NUMBER(38,0))), ARRAY)"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_APPEND(arr, [2])) FROM sat",
                "REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(ARRAY(NUMBER(38,0))), ARRAY)"},
            {"SELECT ARRAY_APPEND(arr, [2]::ARRAY(INT)) FROM sat", "[[1],[2]]"},
            {"SELECT ARRAY_APPEND(o, {'a':2}) FROM sat",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(OBJECT(a NUMBER(38,0))), OBJECT)"},
            {"SELECT ARRAY_APPEND(o, {'a':2}::OBJECT(a INT)) FROM sat", "[{\"a\":1},{\"a\":2}]"},
            {"SELECT ARRAY_APPEND(arr, ['2']) FROM sat",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(ARRAY(NUMBER(38,0))), ARRAY)"},
            {"SELECT ARRAY_PREPEND(arr, ['zz']) FROM sat",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_PREPEND': (ARRAY(ARRAY(NUMBER(38,0))), ARRAY)"},
            {"SELECT ARRAY_INSERT(arr, 0, ['zz']) FROM sat",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_INSERT': (ARRAY(ARRAY(NUMBER(38,0))), NUMBER(1,0), ARRAY)"},
            {"SELECT ARRAY_APPEND(arr, NULL) FROM sat", "[[1],undefined]"},
            {"SELECT ARRAY_APPEND(arr, 5) FROM sat",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(ARRAY(NUMBER(38,0))), NUMBER(1,0))"},
            {"CREATE OR REPLACE TABLE sat2 AS SELECT ARRAY_APPEND(arr, [2.6]::ARRAY(DOUBLE)) AS x FROM sat",
                "Table SAT2 successfully created."},
            {"SELECT x, SYSTEM$TYPEOF(x) FROM sat2", "[[1],[3]]|ARRAY(ARRAY(NUMBER(38,0)))[LOB]"},
        });
    }
}
