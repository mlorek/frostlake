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
 * OBJECT_KEYS reads an OBJECT or a VARIANT: a value declared a number, a text, an ARRAY, a BOOLEAN, a date or a binary
 * is refused by its type at the call while the statement compiles, {@code Invalid argument types for function
 * 'OBJECT_KEYS': (NUMBER(1,0))}, over an empty table and inside SYSTEM$TYPEOF too (live-verified).
 */
public class ObjectKeysArgumentTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aValueThatHoldsNoObjectIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE rk (n INT, g VARCHAR(5), v VARIANT, a ARRAY, o OBJECT)");
        assertCells(new String[][] {
            {"SELECT OBJECT_KEYS(1), SYSTEM$TYPEOF(OBJECT_KEYS(1))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(1,0))"},
            {"SELECT OBJECT_KEYS('a'), SYSTEM$TYPEOF(OBJECT_KEYS('a'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (VARCHAR(1))"},
            {"SELECT OBJECT_KEYS(ARRAY_CONSTRUCT(1)), SYSTEM$TYPEOF(OBJECT_KEYS(ARRAY_CONSTRUCT(1)))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (ARRAY)"},
            {"SELECT OBJECT_KEYS(PARSE_JSON('[1]'))", "REFUSED Failed to cast variant value [1] to OBJECT"},
            {"SELECT OBJECT_KEYS(PARSE_JSON('{\"b\":1,\"a\":2}'))", "[\"a\",\"b\"]"},
            {"SELECT OBJECT_KEYS(OBJECT_CONSTRUCT('a', 1))", "[\"a\"]"},
            {"SELECT OBJECT_KEYS(NULL)", "null"},
            {"SELECT OBJECT_KEYS(1.5)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(2,1))"},
            {"SELECT OBJECT_KEYS(TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (BOOLEAN)"},
            {"SELECT OBJECT_KEYS(CURRENT_DATE())",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (DATE)"},
            {"SELECT OBJECT_KEYS(TO_BINARY('AB', 'HEX'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (BINARY(67108864))"},
            {"SELECT OBJECT_KEYS(1::VARIANT)", "REFUSED Failed to cast variant value 1 to OBJECT"},
            {"SELECT OBJECT_KEYS(NULL::INT)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(38,0))"},
            {"SELECT OBJECT_KEYS(NULL::VARIANT)", "null"},
            {"SELECT OBJECT_KEYS(1.5::FLOAT)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (FLOAT)"},
            {"SELECT OBJECT_KEYS(1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[OBJECT_KEYS(1, 2)] expected 1, got 2"},
            {"SELECT OBJECT_KEYS()",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[OBJECT_KEYS()], expected 1, got 0"},
            {"SELECT OBJECT_KEYS(n) FROM rk",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(38,0))"},
            {"SELECT OBJECT_KEYS(g) FROM rk",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (VARCHAR(5))"},
            {"SELECT OBJECT_KEYS(v) FROM rk", ""},
            {"SELECT OBJECT_KEYS(a) FROM rk",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (ARRAY)"},
            {"SELECT OBJECT_KEYS(o) FROM rk", ""},
            {"SELECT 1 FROM rk WHERE ARRAY_SIZE(OBJECT_KEYS(n)) > 0",
                "REFUSED SQL compilation error: error line 1 at position 34 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(38,0))"},
            {"SELECT SYSTEM$TYPEOF(OBJECT_KEYS(1))",
                "REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(1,0))"},
            {"SELECT OBJECT_KEYS(NVL(NULL, NULL) + 1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(19,0))"},
            {"SELECT OBJECT_KEYS(NULL + 1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(19,0))"},
            {"SELECT OBJECT_KEYS(NULLIFZERO(NULL))", "null"},
            {"SELECT OBJECT_KEYS(TO_OBJECT(NULL))", "null"},
            {"SELECT OBJECT_KEYS(ARRAY_CONSTRUCT())",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (ARRAY)"},
            {"SELECT OBJECT_KEYS(OBJECT_CONSTRUCT())", "[]"},
            {"SELECT OBJECT_KEYS(TO_VARIANT(1))", "REFUSED Failed to cast variant value 1 to OBJECT"},
            {"SELECT OBJECT_KEYS(1) FROM rk",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(1,0))"},
        });
    }
}
