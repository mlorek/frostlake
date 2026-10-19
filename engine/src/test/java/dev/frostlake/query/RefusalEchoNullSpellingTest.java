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
 * How a refusal's echo spells a NULL. The invalid-type sentence prints an untyped NULL as the plan types it,
 * in its own spellings — the word upper-cased, a conversion to the NULL type written COERCE:
 * (SYSTEM$NULL_TO_FIXED(NVL(COERCE(NULL AS NULL), NULL))) + 1. GETVARIABLE's constant-name sentence leaves
 * every NULL bare, IFF(1 = 1, null, 'x'), and a name that folds to a NULL or to a text is no refusal at all
 * (live-verified).
 */
public class RefusalEchoNullSpellingTest extends MeasuredCellsTestSupport {

    @Test
    public void theInvalidTypeSentence() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NVL(COERCE(NULL AS "
                + "NULL), NULL))) + 1)] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NVL(NULL, NULL) + 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NULL)) + 1)] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL + 1) FROM rt");
        answer(
            "SELECT TO_OBJECT(COALESCE(NULL, NULL) + 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NEGATE(SYSTEM$NULL_TO_FIXED(NVL(COERCE(NULL "
                + "AS NULL), NULL))))] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(-NVL(NULL, NULL)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type "
                + "[TO_OBJECT((SYSTEM$NULL_TO_FIXED(IFF(BOOLEAN_TO_ROWINDEX(TRUE), COERCE(NULL AS NULL), NULL))) + "
                + "1)] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(IFF(TRUE, NULL, NULL) + 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NULLIF(COERCE(NULL AS "
                + "NULL), NULL))) + 1)] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULLIF(NULL, NULL) + 1) FROM rt");
        answer(
            "SELECT TO_OBJECT(NVL2(NULL, NULL, NULL) + 1) FROM rt");
        answer(
            "SELECT TO_OBJECT(CASE WHEN TRUE THEN NULL END + 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(ABS(SYSTEM$NULL_TO_FIXED(NULL)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(ABS(NULL)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(RT.N + (SYSTEM$NULL_TO_FIXED(NULL)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(n + NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NVL(SYSTEM$NULL_TO_FIXED(NULL), 1))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NVL(NULL, 1)) FROM rt");
        answer(
            "SELECT TO_OBJECT(NVL(NULL, NULL) || 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((NVL(RT.N, SYSTEM$NULL_TO_FIXED(NULL))) + "
                + "1)] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NVL(n, NULL) + 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_TEXT(NULL)) || 'a')] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL || 'a') FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(RT.G || (SYSTEM$NULL_TO_TEXT(NULL)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(g || NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NULL)) = 1)] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL = 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_BOOLEAN(NULL)) AND TRUE)] "
                + "for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL AND TRUE) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(RT.F + (SYSTEM$NULL_TO_REAL(NULL)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(f + NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NULL)) + "
                + "(SYSTEM$NULL_TO_FIXED(NULL)))] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL::INT + NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NULL)) * "
                + "(SYSTEM$NULL_TO_FIXED(NULL)))] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL * NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(GREATEST(SYSTEM$NULL_TO_FIXED(NULL), 1))] "
                + "for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(GREATEST(NULL, 1)) FROM rt");
        answer(
            "SELECT TO_OBJECT(DECODE(1, 1, NULL) + 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(UPPER(SYSTEM$NULL_TO_TEXT(NULL)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(UPPER(NULL)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(LENGTH(SYSTEM$NULL_TO_TEXT(NULL)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(LENGTH(NULL)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NVL(SYSTEM$NULL_TO_TEXT(NULL), 'a'))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NVL(NULL, 'a')) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NULL)) + 1.5)] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL + 1.5) FROM rt");
        answer(
            "SELECT TO_OBJECT(NULL / 2) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NEGATE(SYSTEM$NULL_TO_FIXED(NULL)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(-NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(IFF(RT.N > 0, "
                + "COERCE(NULL AS NULL), NULL))) + 1)] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(IFF(n > 0, NULL, NULL) + 1) FROM rt");
        answer(
            "SELECT TO_OBJECT(COALESCE(NULL, NULL, NULL) + 1) FROM rt");
        answer(
            "SELECT TO_OBJECT(CASE WHEN n > 0 THEN NULL ELSE NULL END + 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NULL)) + "
                + "(SYSTEM$NULL_TO_FIXED(NULL)))] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL + NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(1 + (SYSTEM$NULL_TO_FIXED(NULL)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(1 + NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NVL(COERCE(NULL AS "
                + "NULL), NULL))) * 2)] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NVL(NULL, NULL) * 2) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NVL(COERCE(NULL AS "
                + "NULL), NULL))) = 1)] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NVL(NULL, NULL) = 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(IFF(BOOLEAN_TO_ROWINDEX(TRUE), "
                + "SYSTEM$NULL_TO_FIXED(NULL), 1))] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(IFF(TRUE, NULL, 1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(IFF(BOOLEAN_TO_ROWINDEX(TRUE), 1, "
                + "SYSTEM$NULL_TO_FIXED(NULL)))] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(IFF(TRUE, 1, NULL)) FROM rt");
        answer(
            "SELECT TO_OBJECT(COALESCE(NULL, 1)) FROM rt");
        answer(
            "SELECT TO_OBJECT(COALESCE(n, NULL)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NULLIF(RT.N, SYSTEM$NULL_TO_FIXED(NULL)))] "
                + "for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULLIF(n, NULL)) FROM rt");
        assertAnswer(
            "",
            "SELECT TO_OBJECT(NULLIF(NULL, 1)) FROM rt");
        answer(
            "SELECT TO_OBJECT(NVL2(n, NULL, NULL) + 1) FROM rt");
        answer(
            "SELECT TO_OBJECT(NVL2(NULL, 1, NULL)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(CONCAT(SYSTEM$NULL_TO_TEXT(NULL), 'a'))] "
                + "for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(CONCAT(NULL, 'a')) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(ROUND(SYSTEM$NULL_TO_FIXED(NULL)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(ROUND(NULL)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(RT.N52 + (SYSTEM$NULL_TO_FIXED(NULL)))] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(n52 + NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_TEXT(NULL)) || "
                + "(SYSTEM$NULL_TO_TEXT(NULL)))] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL::VARCHAR || NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(ZEROIFNULL(SYSTEM$NULL_TO_FIXED(NULL)))] "
                + "for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(ZEROIFNULL(NULL)) FROM rt");
        answer(
            "SELECT TO_OBJECT(NULLIFZERO(NULL)) FROM rt");
        assertAnswer(
            "",
            "SELECT TO_OBJECT(NVL(NULL, NULL)) FROM rt");
        assertAnswer(
            "",
            "SELECT TO_OBJECT(IFF(TRUE, NULL, NULL)) FROM rt");
        answer(
            "SELECT TO_OBJECT(TO_NUMBER(NULL)) FROM rt");
        answer(
            "SELECT TO_OBJECT(TO_DATE(NULL)) FROM rt");
        answer(
            "SELECT TO_OBJECT(NULL IS NULL) FROM rt");
        answer(
            "SELECT TO_OBJECT(NOT NULL) FROM rt");
        answer(
            "SELECT OBJECT_KEYS(NVL(NULL, NULL) + 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(19,0)] for parameter "
                + "'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER(NULL + 1) FROM rt");
        assertAnswer(
            "",
            "SELECT ARRAY_SIZE(NULL + 1) FROM rt");
        assertAnswer(
            "",
            "SELECT TO_ARRAY(NULL + 1) FROM rt");
        assertAnswer(
            "",
            "SELECT AS_OBJECT(NULL + 1) FROM rt");
        assertAnswer(
            "",
            "SELECT IS_INTEGER(NVL(NULL, NULL) + 1) FROM rt");
        assertAnswer(
            "",
            "SELECT GET(NULL + 1, 0) FROM rt");
        answer(
            "SELECT OBJECT_KEYS(NVL(NULL, 1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NVL(COERCE(NULL AS "
                + "NULL), NULL))) + 1)] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NVL(NULL, NULL) + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NULL)) + 1)] for "
                + "parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL + 1)");
    }

    @Test
    public void aConstantNameThatIsNone() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(1 = 1, null, 'x')'",
            "SELECT GETVARIABLE(IFF(1 = 1, NULL, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(1 = 1, 'x', null)'",
            "SELECT GETVARIABLE(IFF(1 = 1, 'x', NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(IFF(1 = 1, null, 1) AS VARCHAR(134217728))'",
            "SELECT GETVARIABLE(IFF(1 = 1, NULL, 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(1 IS NOT NULL, null, 'x')'",
            "SELECT GETVARIABLE(NVL2(1, NULL, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CASE_FLATTENED(1 = 1, null, 'x')'",
            "SELECT GETVARIABLE(CASE WHEN 1 = 1 THEN NULL ELSE 'x' END)");
        answer(
            "SELECT GETVARIABLE(COALESCE(NULL, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'(IFF(1 = 1, null, 'x')) || 'y''",
            "SELECT GETVARIABLE(IFF(1 = 1, NULL, 'x') || 'y')");
        answer(
            "SELECT GETVARIABLE(NVL(NULL, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(null IS NOT NULL, 'a', null)'",
            "SELECT GETVARIABLE(NVL2(NULL, 'a', NULL))");
        answer(
            "SELECT GETVARIABLE(ZEROIFNULL(NULL))");
        answer(
            "SELECT GETVARIABLE(NULLIFZERO(0))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(HASH(null) AS VARCHAR(134217728))'",
            "SELECT GETVARIABLE(HASH(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ARRAY_SIZE(null) AS VARCHAR(134217728))'",
            "SELECT GETVARIABLE(ARRAY_SIZE(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'REPLACE(null, 'a', 'b')'",
            "SELECT GETVARIABLE(REPLACE(NULL, 'a', 'b'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NVL(NULL, NULL))");
        answer(
            "SELECT GETVARIABLE(COALESCE(NULL, NULL, 'x'))");
        answer(
            "SELECT GETVARIABLE(COALESCE('x', NULL))");
        answer(
            "SELECT GETVARIABLE(NVL('x', NULL))");
        answer(
            "SELECT GETVARIABLE(IFNULL(NULL, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'NULLIF('x', null)'",
            "SELECT GETVARIABLE(NULLIF('x', NULL))");
        answer(
            "SELECT GETVARIABLE(NULLIF(NULL, 'x'))");
        answer(
            "SELECT GETVARIABLE(ZEROIFNULL(1))");
        answer(
            "SELECT GETVARIABLE(NULLIFZERO(1))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULLIFZERO(NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(DECODE(1, 1, NULL, 'x'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(UPPER(NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL || 'x')");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(CONCAT(NULL, 'x'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(IFF(1 = 1, NULL, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(GREATEST(NULL, 'x'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(LENGTH(NULL))");
        answer(
            "SELECT GETVARIABLE(NVL(NULL, 1))");
        answer(
            "SELECT GETVARIABLE(COALESCE(NULL, 1))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NVL2(NULL, NULL, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(TO_CHAR(NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL::VARCHAR)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(CAST(NULL AS INT))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(ABS(NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(1 + NULL)");
        answer(
            "SELECT GETVARIABLE(COALESCE(NULL, 'x', 'y'))");
        answer(
            "SELECT GETVARIABLE(NVL(NULL, 'x') || 'y')");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(IFF(NULL, 'a', 'b'))");
        answer(
            "SELECT GETVARIABLE(IFF(1 = 1, UPPER(NULL), 'x'))");
        answer(
            "SELECT GETVARIABLE(NVL(UPPER(NULL), 'x'))");
        answer(
            "SELECT GETVARIABLE(ZEROIFNULL(NULL) + 1)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(CASE WHEN NULL THEN 'a' ELSE 'b' END)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(CASE NULL WHEN 1 THEN 'a' ELSE 'b' END)");
        answer(
            "SELECT GETVARIABLE(NVL(1, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(COALESCE(NULL, NULL))");
        answer(
            "SELECT GETVARIABLE(LOWER(NVL(NULL, 'X')))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(1 = 1, 'x', 'y')'",
            "SELECT GETVARIABLE(IFF(1 = 1, 'x', 'y'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'NVL('a', 'b')'",
            "SELECT GETVARIABLE(NVL('a', 'b'))");
    }
}
