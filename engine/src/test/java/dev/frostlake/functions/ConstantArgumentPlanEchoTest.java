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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a refusal re-prints an argument from the plan, alike in the constant-argument sentence of RANDOM, the arity
 * sentence of ABS and the predicate sentence of a WHERE: a searched CASE as CASE_FLATTENED and a simple one with its
 * values converted, a missing ELSE and every bare NULL as the typed null of the family it is read as, a date part
 * call as EXTRACT, a scalar subquery as its SELECT, a niladic call bare as an operand, and LOCALTIMESTAMP and
 * LOCALTIME by their own names. Each expected answer is the account's own.
 */
public class ConstantArgumentPlanEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rt (n INT, f FLOAT, g VARCHAR(5), d DATE, b BOOLEAN, ts TIMESTAMP_NTZ)");
    }

    /** The refusal as one line, each line break as |, or ACCEPTED. */
    private String answer(final String sql) {
        try {
            engine.executeQuery(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void aCaseIsFlattenedOrKeepsItsSimpleShape() {
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(RT.N > 0, 1, 2)'",
            answer("SELECT RANDOM(CASE WHEN n > 0 THEN 1 ELSE 2 END) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(CASE_FLATTENED(RT.N > 0, 1, 2), 1)] expected 1, got 2",
            answer("SELECT ABS(CASE WHEN n > 0 THEN 1 ELSE 2 END, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [CASE_FLATTENED(RT.N > 0, 1, 2)]",
            answer("SELECT 1 FROM rt WHERE CASE WHEN n > 0 THEN 1 ELSE 2 END"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 1, SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(CASE WHEN TRUE THEN 1 END) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 1, SYSTEM$NULL_TO_FIXED(null)), 1)] expected 1, got 2",
            answer("SELECT ABS(CASE WHEN TRUE THEN 1 END, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 1, SYSTEM$NULL_TO_FIXED(null))]",
            answer("SELECT 1 FROM rt WHERE CASE WHEN TRUE THEN 1 END"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE 1 WHEN 1 THEN 5 ELSE SYSTEM$NULL_TO_FIXED(null) END'",
            answer("SELECT RANDOM(CASE 1 WHEN 1 THEN 5 END) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(CASE 1 WHEN 1 THEN 5 ELSE SYSTEM$NULL_TO_FIXED(null) END, 1)] expected 1, got 2",
            answer("SELECT ABS(CASE 1 WHEN 1 THEN 5 END, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [CASE 1 WHEN 1 THEN 5 ELSE SYSTEM$NULL_TO_FIXED(null) END]",
            answer("SELECT 1 FROM rt WHERE CASE 1 WHEN 1 THEN 5 END"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(RT.N > 0, 'a', SYSTEM$NULL_TO_TEXT(null))'",
            answer("SELECT RANDOM(CASE WHEN n > 0 THEN 'a' END) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(CASE_FLATTENED(RT.N > 0, 'a', SYSTEM$NULL_TO_TEXT(null)), 1)] expected 1, got 2",
            answer("SELECT ABS(CASE WHEN n > 0 THEN 'a' END, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(134217728)] for predicate [CASE_FLATTENED(RT.N > 0, 'a', SYSTEM$NULL_TO_TEXT(null))]",
            answer("SELECT 1 FROM rt WHERE CASE WHEN n > 0 THEN 'a' END"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(RT.N > 0, RT.N, SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(CASE WHEN n > 0 THEN n END) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(CASE_FLATTENED(RT.N > 0, RT.N, SYSTEM$NULL_TO_FIXED(null)), 1)] expected 1, got 2",
            answer("SELECT ABS(CASE WHEN n > 0 THEN n END, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(38,0)] for predicate [CASE_FLATTENED(RT.N > 0, RT.N, SYSTEM$NULL_TO_FIXED(null))]",
            answer("SELECT 1 FROM rt WHERE CASE WHEN n > 0 THEN n END"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE RT.N WHEN CAST(1 AS NUMBER(38,0)) THEN 'x' ELSE 'y' END'",
            answer("SELECT RANDOM(CASE n WHEN 1 THEN 'x' ELSE 'y' END) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(CASE RT.N WHEN CAST(1 AS NUMBER(38,0)) THEN 'x' ELSE 'y' END, 1)] expected 1, got 2",
            answer("SELECT ABS(CASE n WHEN 1 THEN 'x' ELSE 'y' END, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(1)] for predicate [CASE RT.N WHEN CAST(1 AS NUMBER(38,0)) THEN 'x' ELSE 'y' END]",
            answer("SELECT 1 FROM rt WHERE CASE n WHEN 1 THEN 'x' ELSE 'y' END"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(RT.N > 0, 1, RT.N < 0, 2, 3)'",
            answer("SELECT RANDOM(CASE WHEN n > 0 THEN 1 WHEN n < 0 THEN 2 ELSE 3 END) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(CAST(RT.B AS BOOLEAN), 1, 2)'",
            answer("SELECT RANDOM(CASE WHEN b THEN 1 ELSE 2 END) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(RT.N > 0, 1, SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(CASE WHEN n > 0 THEN 1 ELSE NULL END) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(RT.N > 0, RT.F, SYSTEM$NULL_TO_REAL(null))'",
            answer("SELECT RANDOM(CASE WHEN n > 0 THEN f END) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE RT.G WHEN 'a' THEN 1 ELSE SYSTEM$NULL_TO_FIXED(null) END'",
            answer("SELECT RANDOM(CASE g WHEN 'a' THEN 1 END) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE RT.N WHEN CAST(1 AS NUMBER(38,0)) THEN 1 WHEN CAST(2 AS NUMBER(38,0)) THEN 2 ELSE SYSTEM$NULL_TO_FIXED(null) END'",
            answer("SELECT RANDOM(CASE n WHEN 1 THEN 1 WHEN 2 THEN 2 END) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(RT.N > 0, SYSTEM$NULL_TO_FIXED(null), 1)'",
            answer("SELECT RANDOM(CASE WHEN n > 0 THEN NULL ELSE 1 END) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(RT.N > 0, 1.5, SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(CASE WHEN n > 0 THEN 1.5 END) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CASE_FLATTENED(RT.N > 0, 1, SYSTEM$NULL_TO_FIXED(null))) + 1'",
            answer("SELECT RANDOM(CASE WHEN n > 0 THEN 1 END + 1) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '1 + (CASE_FLATTENED(RT.N > 0, 1, 2))'",
            answer("SELECT RANDOM(1 + CASE WHEN n > 0 THEN 1 ELSE 2 END) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'ABS(CASE_FLATTENED(RT.N > 0, 1, SYSTEM$NULL_TO_FIXED(null)))'",
            answer("SELECT RANDOM(ABS(CASE WHEN n > 0 THEN 1 END)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CASE_FLATTENED(RT.N > 0, CAST(null AS NULL), null)'",
            answer("SELECT RANDOM(CASE WHEN n > 0 THEN NULL END) FROM rt"));
    }

    @Test
    public void anUntypedNullIsItsParametersTypedNull() {
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SYSTEM$NULL_TO_VARIANT(null)'",
            answer("SELECT RANDOM(TO_VARIANT(NULL)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(SYSTEM$NULL_TO_VARIANT(null), 1)] expected 1, got 2",
            answer("SELECT ABS(TO_VARIANT(NULL), 1) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SYSTEM$NULL_TO_FIXED(null)'",
            answer("SELECT RANDOM(TO_NUMBER(NULL)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(SYSTEM$NULL_TO_FIXED(null), 1)] expected 1, got 2",
            answer("SELECT ABS(TO_NUMBER(NULL), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(38,0)] for predicate [SYSTEM$NULL_TO_FIXED(null)]",
            answer("SELECT 1 FROM rt WHERE TO_NUMBER(NULL)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'ABS(SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(ABS(NULL)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(ABS(SYSTEM$NULL_TO_FIXED(null)), 1)] expected 1, got 2",
            answer("SELECT ABS(ABS(NULL), 1) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(SYSTEM$NULL_TO_FIXED(null)) + 1'",
            answer("SELECT RANDOM(NULL + 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS((SYSTEM$NULL_TO_FIXED(null)) + 1, 1)] expected 1, got 2",
            answer("SELECT ABS(NULL + 1, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(19,0)] for predicate [(SYSTEM$NULL_TO_FIXED(null)) + 1]",
            answer("SELECT 1 FROM rt WHERE NULL + 1"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'LENGTH(SYSTEM$NULL_TO_TEXT(null))'",
            answer("SELECT RANDOM(LENGTH(NULL)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(LENGTH(SYSTEM$NULL_TO_TEXT(null)), 1)] expected 1, got 2",
            answer("SELECT ABS(LENGTH(NULL), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(18,0)] for predicate [LENGTH(SYSTEM$NULL_TO_TEXT(null))]",
            answer("SELECT 1 FROM rt WHERE LENGTH(NULL)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CONCAT(SYSTEM$NULL_TO_TEXT(null), '5')'",
            answer("SELECT RANDOM(CONCAT(NULL, '5')) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(CONCAT(SYSTEM$NULL_TO_TEXT(null), '5'), 1)] expected 1, got 2",
            answer("SELECT ABS(CONCAT(NULL, '5'), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(134217728)] for predicate [CONCAT(SYSTEM$NULL_TO_TEXT(null), '5')]",
            answer("SELECT 1 FROM rt WHERE CONCAT(NULL, '5')"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFNULL(SYSTEM$NULL_TO_FIXED(null), 5)'",
            answer("SELECT RANDOM(COALESCE(NULL, 5)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(IFNULL(SYSTEM$NULL_TO_FIXED(null), 5), 1)] expected 1, got 2",
            answer("SELECT ABS(COALESCE(NULL, 5), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [IFNULL(SYSTEM$NULL_TO_FIXED(null), 5)]",
            answer("SELECT 1 FROM rt WHERE COALESCE(NULL, 5)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'NVL(SYSTEM$NULL_TO_FIXED(null), 5)'",
            answer("SELECT RANDOM(NVL(NULL, 5)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(NVL(SYSTEM$NULL_TO_FIXED(null), 5), 1)] expected 1, got 2",
            answer("SELECT ABS(NVL(NULL, 5), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [NVL(SYSTEM$NULL_TO_FIXED(null), 5)]",
            answer("SELECT 1 FROM rt WHERE NVL(NULL, 5)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'GREATEST(SYSTEM$NULL_TO_FIXED(null), 1)'",
            answer("SELECT RANDOM(GREATEST(NULL, 1)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(GREATEST(SYSTEM$NULL_TO_FIXED(null), 1), 1)] expected 1, got 2",
            answer("SELECT ABS(GREATEST(NULL, 1), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [GREATEST(SYSTEM$NULL_TO_FIXED(null), 1)]",
            answer("SELECT 1 FROM rt WHERE GREATEST(NULL, 1)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DECODE(SYSTEM$NULL_TO_FIXED(null), 1, 2, 3)'",
            answer("SELECT RANDOM(DECODE(NULL, 1, 2, 3)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(DECODE(SYSTEM$NULL_TO_FIXED(null), 1, 2, 3), 1)] expected 1, got 2",
            answer("SELECT ABS(DECODE(NULL, 1, 2, 3), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [DECODE(SYSTEM$NULL_TO_FIXED(null), 1, 2, 3)]",
            answer("SELECT 1 FROM rt WHERE DECODE(NULL, 1, 2, 3)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'ZEROIFNULL(SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(ZEROIFNULL(NULL)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(ZEROIFNULL(SYSTEM$NULL_TO_FIXED(null)), 1)] expected 1, got 2",
            answer("SELECT ABS(ZEROIFNULL(NULL), 1) FROM rt"));
    }

    @Test
    public void anUntypedNullIsItsParametersTypedNull2() {
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(2,0)] for predicate [ZEROIFNULL(SYSTEM$NULL_TO_FIXED(null))]",
            answer("SELECT 1 FROM rt WHERE ZEROIFNULL(NULL)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF(CAST((SYSTEM$NULL_TO_BOOLEAN(null)) AND TRUE AS BOOLEAN), 1, 2)'",
            answer("SELECT RANDOM(IFF(NULL AND TRUE, 1, 2)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(IFF(CAST((SYSTEM$NULL_TO_BOOLEAN(null)) AND TRUE AS BOOLEAN), 1, 2), 1)] expected 1, got 2",
            answer("SELECT ABS(IFF(NULL AND TRUE, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [IFF(CAST((SYSTEM$NULL_TO_BOOLEAN(null)) AND TRUE AS BOOLEAN), 1, 2)]",
            answer("SELECT 1 FROM rt WHERE IFF(NULL AND TRUE, 1, 2)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SYSTEM$NULL_TO_VARIANT(null)'",
            answer("SELECT RANDOM(NULL::VARIANT) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(SYSTEM$NULL_TO_VARIANT(null), 1)] expected 1, got 2",
            answer("SELECT ABS(NULL::VARIANT, 1) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found ''abc' || (SYSTEM$NULL_TO_TEXT(null))'",
            answer("SELECT RANDOM('abc' || NULL) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS('abc' || (SYSTEM$NULL_TO_TEXT(null)), 1)] expected 1, got 2",
            answer("SELECT ABS('abc' || NULL, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(134217728)] for predicate ['abc' || (SYSTEM$NULL_TO_TEXT(null))]",
            answer("SELECT 1 FROM rt WHERE 'abc' || NULL"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '1 + (SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(1 + NULL) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(1 + (SYSTEM$NULL_TO_FIXED(null)), 1)] expected 1, got 2",
            answer("SELECT ABS(1 + NULL, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(19,0)] for predicate [1 + (SYSTEM$NULL_TO_FIXED(null))]",
            answer("SELECT 1 FROM rt WHERE 1 + NULL"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'UPPER(SYSTEM$NULL_TO_TEXT(null))'",
            answer("SELECT RANDOM(UPPER(NULL)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'ROUND(SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(ROUND(NULL)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(SYSTEM$NULL_TO_FIXED(null)) - 1'",
            answer("SELECT RANDOM(NULL - 1) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.F + (SYSTEM$NULL_TO_REAL(null))'",
            answer("SELECT RANDOM(f + NULL) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(SYSTEM$NULL_TO_TEXT(null)) || (SYSTEM$NULL_TO_TEXT(null))'",
            answer("SELECT RANDOM(NULL || NULL) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'GREATEST(SYSTEM$NULL_TO_TEXT(null), 'a')'",
            answer("SELECT RANDOM(GREATEST(NULL, 'a')) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'NVL(SYSTEM$NULL_TO_TEXT(null), 'x')'",
            answer("SELECT RANDOM(NVL(NULL, 'x')) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DECODE(SYSTEM$NULL_TO_TEXT(null), 'a', 1, SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(DECODE(NULL, 'a', 1)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SYSTEM$NULL_TO_FIXED(null)'",
            answer("SELECT RANDOM(TO_DECIMAL(NULL)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'LEAST(SYSTEM$NULL_TO_FIXED(null), 2)'",
            answer("SELECT RANDOM(LEAST(NULL, 2)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SUBSTR(SYSTEM$NULL_TO_TEXT(null), 1)'",
            answer("SELECT RANDOM(SUBSTR(NULL, 1)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(SYSTEM$NULL_TO_FIXED(null)) + (SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(NULL + NULL) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFNULL(SYSTEM$NULL_TO_FIXED(null), 5)'",
            answer("SELECT RANDOM(IFNULL(NULL, 5)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFNULL(SYSTEM$NULL_TO_FIXED(null), IFNULL(5, 6))'",
            answer("SELECT RANDOM(COALESCE(NULL, 5, 6)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'NVL(RT.N, SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(NVL(n, NULL)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'GREATEST(RT.N, SYSTEM$NULL_TO_FIXED(null), 2)'",
            answer("SELECT RANDOM(GREATEST(n, NULL, 2)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CONCAT('a', SYSTEM$NULL_TO_TEXT(null), 'b')'",
            answer("SELECT RANDOM(CONCAT('a', NULL, 'b')) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'LOWER(SYSTEM$NULL_TO_TEXT(null))'",
            answer("SELECT RANDOM(LOWER(NULL)) FROM rt"));
    }

    @Test
    public void anUntypedNullIsItsParametersTypedNull3() {
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'TRIM(SYSTEM$NULL_TO_TEXT(null))'",
            answer("SELECT RANDOM(TRIM(NULL)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'REPLACE(SYSTEM$NULL_TO_TEXT(null), 'a', 'b')'",
            answer("SELECT RANDOM(REPLACE(NULL, 'a', 'b')) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CEIL(SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(CEIL(NULL)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'FLOOR(SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(FLOOR(NULL)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SIGN(SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(SIGN(NULL)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SQRT(SYSTEM$NULL_TO_REAL(null))'",
            answer("SELECT RANDOM(SQRT(NULL)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SYSTEM$NULL_TO_FIXED(null)'",
            answer("SELECT RANDOM(TO_NUMBER(NULL, 10, 2)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'NULLIF(RT.G, 'a')'",
            answer("SELECT RANDOM(NULLIF(g, 'a')) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'NULLIF(1, RT.N)'",
            answer("SELECT RANDOM(NULLIF(1, n)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'NULLIF(RT.N, SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(NULLIF(n, NULL)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF(RT.N > 0, SYSTEM$NULL_TO_FIXED(null), 1)'",
            answer("SELECT RANDOM(IFF(n > 0, NULL, 1)) FROM rt"));
    }

    @Test
    public void aDatePartCallIsAnExtraction() {
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(day from CURRENT_DATE())'",
            answer("SELECT RANDOM(DAYOFMONTH(CURRENT_DATE)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(EXTRACT(day from CURRENT_DATE()), 1)] expected 1, got 2",
            answer("SELECT ABS(DAYOFMONTH(CURRENT_DATE), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(2,0)] for predicate [EXTRACT(day from CURRENT_DATE())]",
            answer("SELECT 1 FROM rt WHERE DAYOFMONTH(CURRENT_DATE)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(year from CURRENT_DATE())'",
            answer("SELECT RANDOM(YEAR(CURRENT_DATE)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(EXTRACT(year from CURRENT_DATE()), 1)] expected 1, got 2",
            answer("SELECT ABS(YEAR(CURRENT_DATE), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(4,0)] for predicate [EXTRACT(year from CURRENT_DATE())]",
            answer("SELECT 1 FROM rt WHERE YEAR(CURRENT_DATE)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(month from RT.D)'",
            answer("SELECT RANDOM(MONTH(d)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(EXTRACT(month from RT.D), 1)] expected 1, got 2",
            answer("SELECT ABS(MONTH(d), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(2,0)] for predicate [EXTRACT(month from RT.D)]",
            answer("SELECT 1 FROM rt WHERE MONTH(d)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(day from RT.D)'",
            answer("SELECT RANDOM(DAY(d)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(hour from RT.TS)'",
            answer("SELECT RANDOM(HOUR(ts)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(minute from RT.TS)'",
            answer("SELECT RANDOM(MINUTE(ts)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(second from RT.TS)'",
            answer("SELECT RANDOM(SECOND(ts)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(week from RT.D)'",
            answer("SELECT RANDOM(WEEK(d)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(quarter from RT.D)'",
            answer("SELECT RANDOM(QUARTER(d)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(dayofweek from RT.D)'",
            answer("SELECT RANDOM(DAYOFWEEK(d)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(dayofyear from RT.D)'",
            answer("SELECT RANDOM(DAYOFYEAR(d)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(week from RT.D)'",
            answer("SELECT RANDOM(WEEKOFYEAR(d)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'EXTRACT(year_of_week from RT.D)'",
            answer("SELECT RANDOM(YEAROFWEEK(d)) FROM rt"));
    }

    @Test
    public void theOtherPlanSpellings() {
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)'",
            answer("SELECT RANDOM((SELECT 1)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS((SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL), 1)] expected 1, got 2",
            answer("SELECT ABS((SELECT 1), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)]",
            answer("SELECT 1 FROM rt WHERE (SELECT 1)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(1 AS FLOAT)) + PI()'",
            answer("SELECT RANDOM(1 + PI()) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS((CAST(1 AS FLOAT)) + PI(), 1)] expected 1, got 2",
            answer("SELECT ABS(1 + PI(), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [FLOAT] for predicate [(CAST(1 AS FLOAT)) + PI()]",
            answer("SELECT 1 FROM rt WHERE 1 + PI()"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CAST(LOCALTIMESTAMP() AS VARCHAR(134217728))'",
            answer("SELECT RANDOM(LOCALTIMESTAMP::VARCHAR) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(CAST(LOCALTIMESTAMP() AS VARCHAR(134217728)), 1)] expected 1, got 2",
            answer("SELECT ABS(LOCALTIMESTAMP::VARCHAR, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(134217728)] for predicate [CAST(LOCALTIMESTAMP() AS VARCHAR(134217728))]",
            answer("SELECT 1 FROM rt WHERE LOCALTIMESTAMP::VARCHAR"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF((SYSTEM$NULL_TO_FIXED(null)) = 1, CAST(null AS NULL), null)'",
            answer("SELECT RANDOM(NULLIF(NULL, 1)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(IFF((SYSTEM$NULL_TO_FIXED(null)) = 1, CAST(null AS NULL), null), 1)] expected 1, got 2",
            answer("SELECT ABS(NULLIF(NULL, 1), 1) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(2 AS FLOAT)) * PI()'",
            answer("SELECT RANDOM(2 * PI()) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS((CAST(2 AS FLOAT)) * PI(), 1)] expected 1, got 2",
            answer("SELECT ABS(2 * PI(), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [FLOAT] for predicate [(CAST(2 AS FLOAT)) * PI()]",
            answer("SELECT 1 FROM rt WHERE 2 * PI()"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'PI() + (CAST(1 AS FLOAT))'",
            answer("SELECT RANDOM(PI() + 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [ABS(PI() + (CAST(1 AS FLOAT)), 1)] expected 1, got 2",
            answer("SELECT ABS(PI() + 1, 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [FLOAT] for predicate [PI() + (CAST(1 AS FLOAT))]",
            answer("SELECT 1 FROM rt WHERE PI() + 1"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '1 + (ABS(RT.N))'",
            answer("SELECT RANDOM(1 + ABS(n)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.F + (ABS(RT.F))'",
            answer("SELECT RANDOM(f + ABS(f)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '1 + (LENGTH(RT.G))'",
            answer("SELECT RANDOM(1 + LENGTH(g)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N + (UNIFORM(1, 2, 3))'",
            answer("SELECT RANDOM(n + UNIFORM(1, 2, 3)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '1 + RANDOM()'",
            answer("SELECT RANDOM(1 + RANDOM()) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'PI() * PI()'",
            answer("SELECT RANDOM(PI() * PI()) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.F + PI()'",
            answer("SELECT RANDOM(f + PI()) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DECODE(RT.N, CAST(1 AS NUMBER(38,0)), 2, SYSTEM$NULL_TO_FIXED(null))'",
            answer("SELECT RANDOM(DECODE(n, 1, 2)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DECODE(RT.G, 'a', 'b', SYSTEM$NULL_TO_TEXT(null))'",
            answer("SELECT RANDOM(DECODE(g, 'a', 'b')) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DECODE(RT.N, CAST(1 AS NUMBER(38,0)), 2, 3)'",
            answer("SELECT RANDOM(DECODE(n, 1, 2, 3)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CAST(RT.N AS VARIANT)'",
            answer("SELECT RANDOM(TO_VARIANT(n)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CAST(RT.G AS VARIANT)'",
            answer("SELECT RANDOM(TO_VARIANT(g)) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'NULLIF(RT.N, 1)'",
            answer("SELECT RANDOM(NULLIF(n, 1)) FROM rt"));
    }

    @Test
    public void theOtherPlanSpellings2() {
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CAST(LOCALTIME() AS VARCHAR(134217728))'",
            answer("SELECT RANDOM(LOCALTIME::VARCHAR) FROM rt"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(ABS(RT.N)) + (ABS(RT.N))'",
            answer("SELECT RANDOM(ABS(n) + ABS(n)) FROM rt"));
    }
}
