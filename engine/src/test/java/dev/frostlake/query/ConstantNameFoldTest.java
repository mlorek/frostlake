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
 * GETVARIABLE's name is a constant only where the plan folds it: a NULL condition settles an IFF or a CASE, a TRUE
 * or FALSE one settles nothing — {@code IFF(TRUE, 'a', 'b')} is refused, echoed {@code IFF(CAST(TRUE AS BOOLEAN),
 * 'a', 'b')} — and a simple CASE or a DECODE settles by its first match with a default after it. The refusal echoes
 * the name as folded: a NULL before a value is dropped and the value cast to the conditional's type, {@code CAST('x'
 * AS VARCHAR(134217728))}, a value before a last NULL is {@code ENSURE_NULLABLE('x')}, ZEROIFNULL is its constant
 * at its declared width and UPPER(NULL) the NULL itself (live-verified).
 */
public class ConstantNameFoldTest extends MeasuredCellsTestSupport {

    @Test
    public void aTrueOrFalseConditionSettlesNothing() {
        assertCells(new String[][] {
            {"SELECT GETVARIABLE(IFF(TRUE, 'a', 'b'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(CAST(TRUE AS BOOLEAN), 'a', 'b')'"},
            {"SELECT GETVARIABLE(CASE WHEN TRUE THEN 'a' ELSE 'b' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 'a', 'b')'"},
            {"SELECT GETVARIABLE(CASE 1 WHEN 1 THEN 'a' ELSE 'b' END)", "null"},
            {"SELECT GETVARIABLE(CASE 'x' WHEN 'x' THEN 'a' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ENSURE_NULLABLE('a') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(IFF(FALSE, 'a', 'b'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(CAST(FALSE AS BOOLEAN), 'a', 'b')'"},
            {"SELECT GETVARIABLE(IFF(1 = 1, 'a', 'b'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(1 = 1, 'a', 'b')'"},
            {"SELECT GETVARIABLE(CASE WHEN 1 = 1 THEN 'a' ELSE 'b' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE_FLATTENED(1 = 1, 'a', 'b')'"},
            {"SELECT GETVARIABLE(CASE WHEN TRUE THEN 'a' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 'a', null)'"},
            {"SELECT GETVARIABLE(IFF(TRUE, 'a', NULL))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(CAST(TRUE AS BOOLEAN), 'a', null)'"},
            {"SELECT GETVARIABLE(DECODE(1, 1, 'a', 'b'))", "null"},
            {"SELECT GETVARIABLE(NVL2('x', 'a', 'b'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF('x' IS NOT NULL, 'a', 'b')'"},
            {"SELECT GETVARIABLE(IFF(TRUE, 'a', UPPER('b')))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(CAST(TRUE AS BOOLEAN), 'a', 'B')'"},
        });
    }

    @Test
    public void theNameIsEchoedAsThePlanFoldsIt() {
        assertCells(new String[][] {
            {"SELECT GETVARIABLE(IFF(NULL, 'a', 'b'))", "null"},
            {"SELECT GETVARIABLE(CASE WHEN NULL THEN 'a' ELSE 'b' END)", "null"},
            {"SELECT GETVARIABLE(CASE WHEN FALSE THEN 'a' ELSE 'b' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE_FLATTENED(CAST(FALSE AS BOOLEAN), 'a', 'b')'"},
            {"SELECT GETVARIABLE(IFF(TRUE, NULL, NULL))", "null"},
            {"SELECT GETVARIABLE(CASE 1 WHEN 1 THEN 'a' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ENSURE_NULLABLE('a') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(CASE 1 WHEN 2 THEN 'a' ELSE 'b' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE 1 WHEN 2 THEN 'a' ELSE 'b' END'"},
            {"SELECT GETVARIABLE(CASE 1 WHEN 2 THEN 'a' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE 1 WHEN 2 THEN 'a' ELSE null END'"},
            {"SELECT GETVARIABLE(DECODE(1, 1, 'a'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ENSURE_NULLABLE('a') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(DECODE(1, 2, 'a'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'DECODE(1, 2, 'a', null)'"},
            {"SELECT GETVARIABLE(DECODE(1, 2, 'a', 'b'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'DECODE(1, 2, 'a', 'b')'"},
            {"SELECT GETVARIABLE(NVL(NULL, NULL))", "null"},
            {"SELECT GETVARIABLE(COALESCE('x', 'y', NULL))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFNULL('x', CAST(ENSURE_NULLABLE('y') AS VARCHAR(134217728)))'"},
            {"SELECT GETVARIABLE(NVL(NULL, 'x') || 'y')",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'(CAST('x' AS VARCHAR(134217728))) || 'y''"},
            {"SELECT GETVARIABLE(UPPER(NVL(NULL, 'x')))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'UPPER(CAST('x' AS VARCHAR(134217728)))'"},
            {"SELECT GETVARIABLE(IFF(TRUE, 'a', LOWER('B')))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(CAST(TRUE AS BOOLEAN), 'a', 'b')'"},
            {"SELECT GETVARIABLE(CASE WHEN TRUE THEN UPPER('a') END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 'A', null)'"},
            {"SELECT GETVARIABLE(ZEROIFNULL(1.5))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(CAST(1.5 AS NUMBER(3,1)) AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(ZEROIFNULL(123))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(123 AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NVL(NULL, 'x' || 'y'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST('xy' AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(COALESCE(NULL, NULL, NULL, 'x', 'y'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(IFNULL('x', 'y') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(COALESCE(NULL, 'x', NULL))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ENSURE_NULLABLE('x') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NVL2(NULL, 'a', 'b'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(null IS NOT NULL, 'a', 'b')'"},
            {"SELECT GETVARIABLE(NVL2('x', NULL, 'b'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF('x' IS NOT NULL, null, 'b')'"},
            {"SELECT GETVARIABLE(IFF(TRUE, 'a', 'b') || 'c')",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'(IFF(CAST(TRUE AS BOOLEAN), 'a', 'b')) || 'c''"},
            {"SELECT GETVARIABLE(CASE WHEN TRUE THEN 'a' WHEN FALSE THEN 'b' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 'a', CAST(FALSE AS BOOLEAN), 'b', null)'"},
            {"SELECT GETVARIABLE(CASE WHEN FALSE THEN 'a' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE_FLATTENED(CAST(FALSE AS BOOLEAN), 'a', null)'"},
            {"SELECT GETVARIABLE(IFF(NOT TRUE, 'a', 'b'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(CAST(NOT(TRUE) AS BOOLEAN), 'a', 'b')'"},
            {"SELECT GETVARIABLE(IFNULL('x', NULL))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ENSURE_NULLABLE('x') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(ZEROIFNULL(NULL) + 1)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST((CAST(0 AS NUMBER(2,0))) + 1 AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NVL(NULL, 1.5))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(1.5 AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(COALESCE(NULL, TRUE))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(TRUE AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(IFF(1 = 1, LOWER(NULL), 'x'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(1 = 1, null, 'x')'"},
            {"SELECT GETVARIABLE(NVL(LOWER(NULL), 'x'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST('x' AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NVL(TRIM(NULL), 'x'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST('x' AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(CASE 'x' WHEN 'y' THEN 'a' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE 'x' WHEN 'y' THEN 'a' ELSE null END'"},
            {"SELECT GETVARIABLE(CASE 'x' WHEN 'x' THEN 'a' WHEN 'y' THEN 'b' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ENSURE_NULLABLE('a') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(CASE NULL WHEN 'x' THEN 'a' ELSE 'b' END)", "null"},
        });
    }

    @Test
    public void aFoldedChoiceKeepsItsType() {
        engine.execute("CREATE OR REPLACE TABLE dd (d DATE, t TIME)");
        engine.execute("INSERT INTO dd VALUES ('2024-03-15', '10:20:30')");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(NVL(NULL, 'x')), SYSTEM$TYPEOF(CASE 1 WHEN 1 THEN 'a' END), "
                + "SYSTEM$TYPEOF(IFNULL('y', NULL))",
                "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(NULL, 'x', 'y')), SYSTEM$TYPEOF(DECODE(1, 1, 'a'))",
                "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]"},
            {"SELECT GETVARIABLE(NVL(NULL, 'x'::VARCHAR(10)))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST('x' AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NVL(NULL, 'abc'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST('abc' AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(IFF(NULL, 'a', 'b') || 'c')", "null"},
            {"SELECT GETVARIABLE(DECODE(1, 1, 'a', 2, 'b'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ENSURE_NULLABLE('a') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(DECODE(1, 2, 'a', 1, 'b', 'c'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'DECODE(1, 2, 'a', 'b')'"},
            {"SELECT GETVARIABLE(CASE 1 WHEN 2 THEN 'a' WHEN 1 THEN 'b' ELSE 'c' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE 1 WHEN 2 THEN 'a' ELSE 'b' END'"},
            {"SELECT GETVARIABLE(CASE WHEN NULL THEN 'a' WHEN TRUE THEN 'b' END)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 'b', null)'"},
            {"SELECT GETVARIABLE(DECODE(NULL, NULL, 'a', 'b'))", "null"},
            {"SELECT GETVARIABLE(DECODE('x', 'x', NULL))", "null"},
            {"SELECT GETVARIABLE(NVL2(NULL, 'a', NULL))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(null IS NOT NULL, 'a', null)'"},
            {"SELECT GETVARIABLE(NULLIF('a', 'b'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'NULLIF('a', 'b')'"},
            {"SELECT GETVARIABLE(ZEROIFNULL(NULL))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(CAST(0 AS NUMBER(2,0)) AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(UPPER(NULL))", "null"},
        });
    }
}
