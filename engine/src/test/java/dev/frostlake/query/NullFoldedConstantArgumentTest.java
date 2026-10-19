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
 * A call that needs a constant judges an argument that folds to NULL by how it folds. GETVARIABLE takes a
 * name the plan carries to NULL - a conversion of a NULL, a sign or arithmetic over one, the calls it folds, a
 * conversion to a number that fails, a conditional over NULLs or one a literal settles - and refuses a
 * comparison as a BOOLEAN. RANDOM, UNIFORM and BASE64_ENCODE's line length and alphabet refuse an untyped
 * NULL the plan converts, a call folding to NULL that a condition tests included, and echo it from the
 * plan. Every answer is live-verified.
 */
public class NullFoldedConstantArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void theNamesGetVariableFolds() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE d (k INT)");
        engine.execute(
            "INSERT INTO d VALUES (1), (2)");
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        engine.execute(
            "CREATE OR REPLACE VIEW v2 (x) AS SELECT n + 1 FROM t");
        engine.execute(
            "SET one = 1");
        engine.execute(
            "SET half = 0.5");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(+NULL)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(-NULL)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL + 1)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(ABS(NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL::INT)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL::VARCHAR)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NVL(NULL, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(TRY_TO_NUMBER('abc'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(TRY_CAST('abc' AS INT))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(+TRY_TO_NUMBER('abc'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(TRY_TO_NUMBER('abc') + 1)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(CONCAT(NULL, 'a'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(UPPER(NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(DATEADD(day, 1, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL || 'a')");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE('a' || NULL)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(COALESCE(NULL, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(IFF(TRUE, NULL, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(IFF(NULL, 'a', 'b'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULLIF(NULL, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(CASE WHEN NULL THEN 'a' END)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(DECODE(NULL, NULL, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(LEAST(NULL, 'a'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(GREATEST(NULL, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(TO_VARCHAR(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(TRY_CAST('x' AS DATE) AS VARCHAR(134217728))'",
            "SELECT GETVARIABLE(TRY_TO_DATE('x'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(1 + 1 AS VARCHAR(134217728))'",
            "SELECT GETVARIABLE(1 + 1)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE('A' || 'B')");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(UPPER('a'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(LENGTH(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GETVARIABLE': (BOOLEAN)",
            "SELECT GETVARIABLE(NULL = NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GETVARIABLE': (BOOLEAN)",
            "SELECT GETVARIABLE(NOT NULL)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL AND TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GETVARIABLE': (BOOLEAN)",
            "SELECT GETVARIABLE(NULL IS NULL)");
        assertAnswer(
            "1",
            "SELECT GETVARIABLE('ONE')");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(LOWER('ONE'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(TRIM(NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(SUBSTR(NULL, 1))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL::DATE)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(TO_DATE(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NULL and "
                + "NUMBER(38,0)",
            "SELECT GETVARIABLE(TRY_TO_NUMBER(NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL * 2)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL / 2)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL || NULL)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(LPAD(NULL, 3))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NULL::BOOLEAN)");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(IFNULL(NULL, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(NVL(NULL::INT, NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(CAST(NULL AS VARCHAR(5)))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE((NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(((NULL + 1)))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(ABS(-NULL))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(ROUND(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(TRY_CAST('x' AS BOOLEAN) AS VARCHAR(134217728))'",
            "SELECT GETVARIABLE(TRY_TO_BOOLEAN('x'))");
    }

    @Test
    public void theConstantsAGeneratorTakes() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE d (k INT)");
        engine.execute(
            "INSERT INTO d VALUES (1), (2)");
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        engine.execute(
            "CREATE OR REPLACE VIEW v2 (x) AS SELECT n + 1 FROM t");
        engine.execute(
            "SET one = 1");
        engine.execute(
            "SET half = 0.5");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'IFF(SYSTEM$NULL_TO_ROWINDEX(NVL(CAST(null AS NULL), null)), 1, 2)'",
            "SELECT RANDOM(IFF(NVL(NULL, NULL), 1, 2)) FROM rt");
        assertAnswer(
            "",
            "SELECT RANDOM(IFF(NULL, 1, 2)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'IFF(SYSTEM$NULL_TO_ROWINDEX(IFNULL(CAST(CAST(null AS NULL) AS NULL), null)), 1, 2)'",
            "SELECT RANDOM(IFF(COALESCE(NULL, NULL), 1, 2)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'IFF(SYSTEM$NULL_TO_ROWINDEX(NULLIF(CAST(null AS NULL), null)), 1, 2)'",
            "SELECT RANDOM(IFF(NULLIF(NULL, NULL), 1, 2)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'IFF(SYSTEM$NULL_TO_ROWINDEX(IFF(CAST(TRUE AS BOOLEAN), CAST(null AS NULL), null)), 1, 2)'",
            "SELECT RANDOM(IFF(IFF(TRUE, NULL, NULL), 1, 2)) FROM rt");
        assertAnswer(
            "",
            "SELECT RANDOM(NVL2(NVL(NULL, NULL), 1, 2)) FROM rt");
        assertAnswer(
            "",
            "SELECT RANDOM(NVL2(NULL, 1, 2)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'CASE_FLATTENED(SYSTEM$NULL_TO_ROWINDEX(NVL(CAST(null AS NULL), null)), 1, 2)'",
            "SELECT RANDOM(CASE WHEN NVL(NULL, NULL) THEN 1 ELSE 2 END) FROM rt");
        assertAnswer(
            "",
            "SELECT RANDOM(CASE WHEN NULL THEN 1 ELSE 2 END) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'IFF(CAST(SYSTEM$NULL_TO_BOOLEAN(null) AS BOOLEAN), 1, 2)'",
            "SELECT RANDOM(IFF(NULL::BOOLEAN, 1, 2)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'IFF(CAST(NVL(SYSTEM$NULL_TO_BOOLEAN(null), TRUE) AS BOOLEAN), 1, 2)'",
            "SELECT RANDOM(IFF(NVL(NULL, TRUE), 1, 2)) FROM rt");
        assertAnswer(
            "",
            "SELECT RANDOM(IFF(NVL(NULL, NULL) IS NULL, 1, 2)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'IFF(NOT(SYSTEM$NULL_TO_ROWINDEX(NVL(CAST(null AS NULL), null))), 1, 2)'",
            "SELECT RANDOM(IFF(NOT NVL(NULL, NULL), 1, 2)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'IFF(CAST((SYSTEM$NULL_TO_BOOLEAN(NVL(CAST(null AS NULL), null))) AND TRUE AS BOOLEAN), 1, 2)'",
            "SELECT RANDOM(IFF(NVL(NULL, NULL) AND TRUE, 1, 2)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'IFF(SYSTEM$NULL_TO_ROWINDEX(NVL(CAST(null AS NULL), null)), 1, 2)'",
            "SELECT RANDOM(IFF(NVL(NULL, NULL), 1, 2)) IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(IFNULL(CAST(CAST(null AS NULL) AS NULL), null))) + 1'",
            "SELECT RANDOM(COALESCE(NULL, NULL) + 1) FROM rt");
        assertAnswer(
            "",
            "SELECT RANDOM(NVL(NULL, NULL)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'DECODE(SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null)), 1, 2, 3)'",
            "SELECT RANDOM(DECODE(NVL(NULL, NULL), 1, 2, 3)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null))) + 1'",
            "SELECT BASE64_ENCODE('a', NVL(NULL, NULL) + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(null)) + 1'",
            "SELECT BASE64_ENCODE('a', NULL + 1)");
        assertAnswer(
            "null",
            "SELECT BASE64_ENCODE('a', NULL)");
        assertAnswer(
            "null",
            "SELECT BASE64_ENCODE('a', NVL(NULL, NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'ABS(SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT BASE64_ENCODE('a', ABS(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT BASE64_ENCODE('a', NULL::INT)");
        assertAnswer(
            "YQ | ==",
            "SELECT BASE64_ENCODE('a', 1 + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(IFNULL(CAST(CAST(null AS NULL) AS NULL), null))) + 1'",
            "SELECT BASE64_ENCODE('a', COALESCE(NULL, NULL) + 1)");
        assertAnswer(
            "null",
            "SELECT BASE64_ENCODE('a', IFF(TRUE, NULL, NULL))");
        assertAnswer(
            "YQ | ==",
            "SELECT BASE64_ENCODE('a', IFF(NULL, 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'IFF(SYSTEM$NULL_TO_ROWINDEX(NVL(CAST(null AS NULL), null)), 1, 2)'",
            "SELECT BASE64_ENCODE('a', IFF(NVL(NULL, NULL), 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'NEGATE(SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT BASE64_ENCODE('a', -NULL)");
        assertAnswer(
            "null",
            "SELECT BASE64_ENCODE('a', YEAR(NULL))");
        assertAnswer(
            "null",
            "SELECT BASE64_ENCODE('abc', 4, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function BASE64_ENCODE needs to be constant, found "
                + "'(SYSTEM$NULL_TO_TEXT(null)) || 'x''",
            "SELECT BASE64_ENCODE('abc', 4, NULL || 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function BASE64_ENCODE needs to be constant, found "
                + "'UPPER(SYSTEM$NULL_TO_TEXT(null))'",
            "SELECT BASE64_ENCODE('abc', 4, UPPER(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_DECODE_STRING needs to be constant, "
                + "found '(SYSTEM$NULL_TO_TEXT(null)) || 'x''",
            "SELECT BASE64_DECODE_STRING('YQ==', NULL || 'x')");
        assertAnswer(
            "null",
            "SELECT BASE64_DECODE_STRING('YQ==', NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function UNIFORM needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(null)) + 1'",
            "SELECT UNIFORM(1, NULL + 1, RANDOM(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function UNIFORM needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null))) + 1'",
            "SELECT UNIFORM(NVL(NULL, NULL) + 1, 10, RANDOM(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function UNIFORM needs to be constant, found "
                + "'IFF(SYSTEM$NULL_TO_ROWINDEX(NVL(CAST(null AS NULL), null)), 5, 6)'",
            "SELECT UNIFORM(1, IFF(NVL(NULL, NULL), 5, 6), RANDOM(1)) IS NOT NULL");
    }

    @Test
    public void theEchoOfARefusedArgument() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE d (k INT)");
        engine.execute(
            "INSERT INTO d VALUES (1), (2)");
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        engine.execute(
            "CREATE OR REPLACE VIEW v2 (x) AS SELECT n + 1 FROM t");
        engine.execute(
            "SET one = 1");
        engine.execute(
            "SET half = 0.5");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'(UNARY PLUS(SYSTEM$NULL_TO_FIXED(null))) + RT.N'",
            "SELECT BASE64_ENCODE('a', +NULL + n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(null)) + RT.N'",
            "SELECT BASE64_ENCODE('a', NULL + n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'(NEGATE(SYSTEM$NULL_TO_FIXED(null))) + RT.N'",
            "SELECT BASE64_ENCODE('a', -NULL + n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'(ABS(SYSTEM$NULL_TO_FIXED(null))) + RT.N'",
            "SELECT BASE64_ENCODE('a', ABS(NULL) + n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(null)) + RT.N'",
            "SELECT BASE64_ENCODE('a', NULL::INT + n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null))) + RT.N'",
            "SELECT BASE64_ENCODE('a', NVL(NULL, NULL) + n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'RT.N + (SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT BASE64_ENCODE('a', n + NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'RT.N'",
            "SELECT BASE64_ENCODE('a', n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'RT.N + 1'",
            "SELECT BASE64_ENCODE('a', n + 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'IFF(RT.N > 0, SYSTEM$NULL_TO_FIXED(null), 1)'",
            "SELECT BASE64_ENCODE('a', IFF(n > 0, NULL, 1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'IFNULL(SYSTEM$NULL_TO_FIXED(null), RT.N)'",
            "SELECT BASE64_ENCODE('a', COALESCE(NULL, n)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function BASE64_ENCODE needs to be constant, found "
                + "'(SYSTEM$NULL_TO_TEXT(null)) || RT.G'",
            "SELECT BASE64_ENCODE('a', 4, NULL || g) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_DECODE_STRING needs to be constant, "
                + "found '(SYSTEM$NULL_TO_TEXT(null)) || RT.G'",
            "SELECT BASE64_DECODE_STRING('YQ==', NULL || g) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'RT.N52 + (SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT BASE64_ENCODE('a', n52 + NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'RT.F + (SYSTEM$NULL_TO_REAL(null))'",
            "SELECT BASE64_ENCODE('a', f + NULL) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(SYSTEM$NULL_TO_FIXED(NULL))] for parameter "
                + "'TO_OBJECT'",
            "SELECT TO_OBJECT(NULL::INT) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(1 + 1)] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(1 + 1) FROM rt");
        assertAnswer(
            "",
            "SELECT TO_OBJECT(NVL(NULL, NULL)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(TO_NUMBER(RT.V))] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT(v::INT) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function GETVARIABLE needs to be constant, found "
                + "'NULLIF('a', 'a')'",
            "SELECT GETVARIABLE(NULLIF('a', 'a'))");
        assertAnswer(
            "null",
            "SELECT GETVARIABLE(DECODE(1, 1, NULL, 'x'))");
    }
}
