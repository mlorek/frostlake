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
 * The invalid-type sentence spells a NULL as the plan types it: a conversion of the bare word NULL is its target's
 * typed NULL, {@code SYSTEM$NULL_TO_FIXED(NULL)} for {@code TO_NUMBER(NULL)}, a NULL tested by IS NULL is
 * {@code COERCE(NULL AS NULL)}, NOT converts what is no condition, {@code NOT(TO_BOOLEAN(RT.N))}, a number beside
 * {@code ||} is {@code TO_CHAR(1)}, a division over a NULL rescales its dividend and NULLIFZERO is the NULLIF it
 * stands for; NULLIFZERO(NULL) is no value TO_OBJECT refuses at all. NVL2 is the IFF it stands for, a searched CASE
 * is CASE_FLATTENED, a COALESCE over NULLs alone its IFNULL chain in the NULL type and a DECODE converts its search
 * values to the subject's type, {@code COERCE(1 AS NUMBER(38,0))} (live-verified).
 */
public class InvalidTypeNullEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullIsEchoedAsThePlanTypesIt() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        assertCells(new String[][] {
            {"SELECT TO_OBJECT(NVL2(NULL, NULL, NULL) + 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(IFF(COERCE(NULL AS "
                + "NULL) IS NOT NULL, COERCE(NULL AS NULL), NULL))) + 1)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NVL2(NULL, 1, NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(IFF(COERCE(NULL AS NULL) IS NOT NULL, 1, "
                + "SYSTEM$NULL_TO_FIXED(NULL)))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NVL2(n, NULL, 1)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(IFF(RT.N IS NOT NULL, "
                + "SYSTEM$NULL_TO_FIXED(NULL), 1))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(CASE WHEN TRUE THEN NULL END + 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(CASE_FLATTENED(BOOL"
                + "EAN_TO_ROWINDEX(TRUE), COERCE(NULL AS NULL), NULL))) + 1)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(CASE WHEN n = 1 THEN NULL ELSE 2 END) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(CASE_FLATTENED(RT.N = 1, "
                + "SYSTEM$NULL_TO_FIXED(NULL), 2))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(COALESCE(NULL, NULL) + 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(IFNULL(COERCE(COERC"
                + "E(NULL AS NULL) AS NULL), NULL))) + 1)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(COALESCE(NULL, NULL, NULL) + 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(IFNULL(COERCE(COERC"
                + "E(NULL AS NULL) AS NULL), IFNULL(COERCE(NULL AS NULL), NULL)))) + 1)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(DECODE(1, 1, NULL) + 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(DECODE(1, 1, "
                + "COERCE(NULL AS NULL), NULL))) + 1)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(DECODE(n, 1, NULL, 2)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(DECODE(RT.N, COERCE(1 AS NUMBER(38,0)), "
                + "SYSTEM$NULL_TO_FIXED(NULL), 2))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NVL(NULL, NULL) || 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_TEXT(NVL(COERCE(NULL AS "
                + "NULL), NULL))) || (TO_CHAR(1)))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT('a' || 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT('a' || (TO_CHAR(1)))] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(g || 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(RT.G || (TO_CHAR(1)))] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(1 || 2) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((TO_CHAR(1)) || (TO_CHAR(2)))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULL / 2) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((FIXED_TO_FIXED(SYSTEM$NULL_TO_FIXED(NULL"
                + ") AS NUMBER(24,6)[UNKNOWN])) / 2)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULL / 2.5) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((FIXED_TO_FIXED(SYSTEM$NULL_TO_FIXED(NULL"
                + ") AS NUMBER(26,7)[UNKNOWN])) / 2.5)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(2 / NULL) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((FIXED_TO_FIXED(2 AS "
                + "NUMBER(7,6)[UNKNOWN])) / (SYSTEM$NULL_TO_FIXED(NULL)))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(n / NULL) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SCALED_ROUND_INT_DIVIDE(RT.N, "
                + "SYSTEM$NULL_TO_FIXED(NULL)))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULL * 2) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NULL)) * 2)] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_NUMBER(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_FIXED(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_DATE(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_DATE(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_DECIMAL(NULL, 5, 2)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_FIXED(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_TIMESTAMP(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_TIMESTAMP_NTZ(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_TIME(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_TIME(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_DOUBLE(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_REAL(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_BOOLEAN(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_BOOLEAN(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_VARCHAR(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_TEXT(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULL IS NULL) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(COERCE(NULL AS NULL) IS NULL)] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULL IS NOT NULL) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(COERCE(NULL AS NULL) IS NOT NULL)] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NOT NULL) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NOT(BOOLEAN_TO_ROWINDEX(NULL)))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NOT n) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NOT(TO_BOOLEAN(RT.N)))] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULLIFZERO(NULL)) FROM rt", ""},
            {"SELECT TO_OBJECT(NULLIFZERO(n)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NULLIF(RT.N, 0))] for parameter 'TO_OBJECT'"},
            {"SELECT OBJECT_KEYS(NVL(NULL, NULL) + 1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(19,0))"},
            {"SELECT OBJECT_KEYS(NULL + 1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (NUMBER(19,0))"},
            {"SELECT OBJECT_KEYS(NVL(NULL, NULL))", "null"},
            {"SELECT GETVARIABLE(COALESCE(NULL, 'x'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST('x' AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NVL(NULL, 'x'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST('x' AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(IFNULL(NULL, 'x'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST('x' AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NVL(UPPER(NULL), 'x'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST('x' AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NVL(NULL, 1))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(1 AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(COALESCE(NULL, 1))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(1 AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(COALESCE('x', NULL))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ENSURE_NULLABLE('x') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NVL('x', NULL))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ENSURE_NULLABLE('x') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NVL(1, NULL))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(ENSURE_NULLABLE(1) AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(COALESCE(NULL, 'x', 'y'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(IFNULL('x', 'y') AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(COALESCE(NULL, NULL, 'x'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST('x' AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(COALESCE('x', 'y'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFNULL('x', 'y')'"},
            {"SELECT GETVARIABLE(NVL('x', 'y'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'NVL('x', 'y')'"},
            {"SELECT GETVARIABLE(ZEROIFNULL(NULL))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(CAST(0 AS NUMBER(2,0)) AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(ZEROIFNULL(1))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(CAST(1 AS NUMBER(2,0)) AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(ZEROIFNULL(12))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(12 AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NULLIFZERO(0))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(NULLIF(0, 0) AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(NULLIFZERO(1))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(NULLIF(1, 0) AS VARCHAR(134217728))'"},
            {"SELECT GETVARIABLE(IFF(1 = 1, UPPER(NULL), 'x'))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(1 = 1, null, 'x')'"},
            {"SELECT GETVARIABLE(IFF(1 = 1, 'x', UPPER(NULL)))",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'IFF(1 = 1, 'x', null)'"},
        });
    }

    @Test
    public void conversionsAndConditionsAreSpelledAsPlanned() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        assertCells(new String[][] {
            {"SELECT TO_OBJECT(n / 2) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SCALED_ROUND_INT_DIVIDE(RT.N, 2))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(n52 / 2) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((FIXED_TO_FIXED(RT.N52 AS "
                + "NUMBER(11,8)[UNKNOWN])) / 2)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULL / n) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((FIXED_TO_FIXED(SYSTEM$NULL_TO_FIXED(NULL"
                + ") AS NUMBER(24,6)[UNKNOWN])) / RT.N)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULLIF(n, 0)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NULLIF(RT.N, 0))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NOT TRUE) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NOT(TRUE))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NOT (n = 1)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NOT(RT.N = 1))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(n IS NULL) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(RT.N IS NULL)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NOT f) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NOT(TO_BOOLEAN(RT.F)))] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NOT g) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NOT(TO_BOOLEAN(RT.G)))] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NOT v) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(NOT(TO_BOOLEAN(RT.V)))] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NOT NOT n) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(TO_BOOLEAN(RT.N))] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_NUMBER(NULL, 5)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_FIXED(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_TIMESTAMP_LTZ(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_TIMESTAMP_LTZ(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_TIMESTAMP_TZ(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_TIMESTAMP_TZ(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_DECIMAL(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_FIXED(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_NUMERIC(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_FIXED(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_CHAR(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_TEXT(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_BINARY(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_BINARY(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_ARRAY(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_ARRAY(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TRY_TO_NUMBER(NULL)) FROM rt",
                "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NULL "
                + "and NUMBER(38,0)"},
            {"SELECT TO_OBJECT(TO_DATE(NULL, 'YYYY')) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_DATE(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_NUMBER(n)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(identity(RT.N))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_TIMESTAMP_NTZ(NULL)) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_TIMESTAMP_NTZ(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_VARIANT(NULL)) FROM rt", ""},
            {"SELECT TO_OBJECT(TO_DOUBLE(NULL) + 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_REAL(NULL)) + "
                + "(TO_DOUBLE(1)))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_NUMBER(NULL) + 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_FIXED(NULL)) + 1)] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_BOOLEAN(NULL) AND TRUE) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_BOOLEAN(NULL)) AND "
                + "TRUE)] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TO_VARCHAR(NULL) || 'a') FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_TEXT(NULL)) || 'a')] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULL::INT) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_FIXED(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULL::VARCHAR) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_TEXT(NULL))] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(1.5 || 'a') FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((TO_CHAR(1.5)) || 'a')] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(f || 'a') FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((TO_CHAR(RT.F)) || 'a')] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(n52 || 'a') FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((TO_CHAR(RT.N52)) || 'a')] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(TRUE || 'a') FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((BOOLEAN_TO_TEXT(TRUE)) || 'a')] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(CONCAT(1, 'a')) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(CONCAT(TO_CHAR(1), 'a'))] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(n || g) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((TO_CHAR(RT.N)) || RT.G)] for parameter "
                + "'TO_OBJECT'"},
            {"SELECT TO_OBJECT(NULL || 1) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SYSTEM$NULL_TO_TEXT(NULL)) || "
                + "(TO_CHAR(1)))] for parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(CURRENT_DATE() || 'a') FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((TO_CHAR(CURRENT_DATE())) || 'a')] for "
                + "parameter 'TO_OBJECT'"},
            {"SELECT TO_OBJECT(1 || NULL) FROM rt",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT((TO_CHAR(1)) || "
                + "(SYSTEM$NULL_TO_TEXT(NULL)))] for parameter 'TO_OBJECT'"},
            {"SELECT ABS('a' || 1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS('a' || (CAST(1 AS VARCHAR(134217728))), 2)] expected 1, got 2"},
            {"SELECT ABS(1 || 2, 3)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS((CAST(1 AS VARCHAR(134217728))) || (CAST(2 AS VARCHAR(134217728))), 3)] expected 1, got 2"},
            {"SELECT LAST_QUERY_ID('a' || 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'TO_NUMBER('a' || (CAST(1 AS VARCHAR(134217728))), 18, 0)'"},
            {"SELECT GETVARIABLE(1 || 2)",
                "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'(CAST(1 AS VARCHAR(134217728))) || (CAST(2 AS VARCHAR(134217728)))'"},
            {"SELECT ABS(TO_NUMBER(NULL), 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(SYSTEM$NULL_TO_FIXED(null), 2)] expected 1, got 2"},
            {"SELECT ABS(NULL IS NULL, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(CAST(null AS NULL) IS NULL, 2)] expected 1, got 2"},
            {"SELECT ABS(NOT NULL, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(NOT(CAST(null AS BOOLEAN)), 2)] expected 1, got 2"},
            {"SELECT ABS(NULLIFZERO(n), 2) FROM rt",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(NULLIF(RT.N, 0), 2)] expected 1, got 2"},
            {"SELECT ABS(NOT n, 2) FROM rt",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(NOT(CAST(RT.N AS BOOLEAN)), 2)] expected 1, got 2"},
        });
    }
}
