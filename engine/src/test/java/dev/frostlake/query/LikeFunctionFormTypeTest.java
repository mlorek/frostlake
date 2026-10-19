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
 * The LIKE family matches text: an OBJECT, an ARRAY or a BINARY operand is refused by type at the operator, and at the
 * call when LIKE or ILIKE is called as a function — {@code Invalid argument types for function 'LIKE': (OBJECT,
 * VARCHAR(2))} — while a VARIANT is taken and a NULL pattern folds the match to NULL before any type is asked (all
 * live-verified).
 */
public class LikeFunctionFormTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void theLikeFamilyRefusesWhatIsNoText() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE lo (o OBJECT, a ARRAY, v VARIANT, bn BINARY)");
        assertCells(new String[][] {
            {"SELECT OBJECT_CONSTRUCT('a', 1) ILIKE 'i%'",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for function "
                + "'ILIKE': (OBJECT, VARCHAR(2))"},
            {"SELECT OBJECT_CONSTRUCT('a', 1) LIKE 'i%'",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for function "
                + "'LIKE': (OBJECT, VARCHAR(2))"},
            {"SELECT ARRAY_CONSTRUCT(1) ILIKE 'x'",
                "REFUSED SQL compilation error: error line 1 at position 26 | Invalid argument types for function "
                + "'ILIKE': (ARRAY, VARCHAR(1))"},
            {"SELECT {*} ILIKE 'i%' FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'ILIKE': (OBJECT, VARCHAR(2))"},
            {"SELECT {*} LIKE 'i%' FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE': (OBJECT, VARCHAR(2))"},
            {"SELECT TO_JSON({*} ILIKE 'i%') FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 19 | Invalid argument types for function "
                + "'ILIKE': (OBJECT, VARCHAR(2))"},
            {"SELECT TO_VARIANT(1) ILIKE 'i%'", "false"},
            {"SELECT OBJECT_CONSTRUCT('a', 1) NOT LIKE 'i%'",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'LIKE': (OBJECT, VARCHAR(2))"},
            {"SELECT OBJECT_CONSTRUCT('a', 1) NOT ILIKE 'i%'",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'ILIKE': (OBJECT, VARCHAR(2))"},
            {"SELECT OBJECT_CONSTRUCT('a', 1) LIKE ALL ('i%')",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for function "
                + "'LIKE_ALL': (OBJECT, NULL, VARCHAR(2))"},
            {"SELECT OBJECT_CONSTRUCT('a', 1) LIKE ANY ('i%', 'x')",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for function "
                + "'LIKE_ANY': (OBJECT, NULL, VARCHAR(2), VARCHAR(1))"},
            {"SELECT OBJECT_CONSTRUCT('a', 1) RLIKE 'i'",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for function "
                + "'RLIKE': (OBJECT, VARCHAR(1))"},
            {"SELECT OBJECT_CONSTRUCT('a', 1) REGEXP 'i'",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for function "
                + "'REGEXP': (OBJECT, VARCHAR(1))"},
            {"SELECT OBJECT_CONSTRUCT('a', 1) LIKE 'i%' ESCAPE '!'",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for function "
                + "'LIKE': (OBJECT, VARCHAR(2), VARCHAR(1))"},
            {"SELECT TO_BINARY('AB', 'HEX') LIKE 'x'",
                "REFUSED SQL compilation error: error line 1 at position 30 | Invalid argument types for function "
                + "'LIKE': (BINARY(67108864), VARCHAR(1))"},
            {"SELECT 'a' LIKE OBJECT_CONSTRUCT('a', 1)",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), OBJECT)"},
            {"SELECT OBJECT_CONSTRUCT('a', 1) ILIKE NULL", "null"},
            {"SELECT NULL::OBJECT LIKE 'x'",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'LIKE': (OBJECT, VARCHAR(1))"},
            {"SELECT [1, 2] LIKE '%'",
                "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'LIKE': (ARRAY, VARCHAR(1))"},
            {"SELECT o LIKE 'x', a ILIKE 'y' FROM lo",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function "
                + "'LIKE': (OBJECT, VARCHAR(1))"},
            {"SELECT v LIKE 'x' FROM lo", ""},
            {"SELECT 1 FROM lo WHERE a LIKE 'x'",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid argument types for function "
                + "'LIKE': (ARRAY, VARCHAR(1))"},
            {"SELECT id FROM fz WHERE {*} LIKE 'x'",
                "REFUSED SQL compilation error: | Use of * as a function argument is only allowed in the SELECT "
                + "clause."},
            {"SELECT bn LIKE 'x' FROM lo",
                "REFUSED SQL compilation error: error line 1 at position 10 | Invalid argument types for function "
                + "'LIKE': (BINARY(8388608), VARCHAR(1))"},
            {"SELECT LIKE(OBJECT_CONSTRUCT('a', 1), 'i%')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LIKE': (OBJECT, VARCHAR(2))"},
            {"SELECT ILIKE(ARRAY_CONSTRUCT(1), 'x')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ILIKE': (ARRAY, VARCHAR(1))"},
            {"SELECT OBJECT_CONSTRUCT('a', 1)::VARCHAR LIKE '%a%'", "true"},
        });
    }
}
