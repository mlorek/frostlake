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
 * A GENERATOR bound must fold while the statement compiles, and the account folds only some calls: ABS, FLOOR,
 * MOD, the conversions, the date parts and the NULL-taking functions fold, where LENGTH, DATEDIFF, SQRT, TRUNC,
 * CEIL, ROUND, POWER, BITAND, ARRAY_SIZE, REGEXP_COUNT, HASH, SIGN, LN, EXP, LOG and CHARINDEX, a date subtracted
 * from a date, and a sum over a value folded late (ZEROIFNULL(NULL), a constant call times zero) leave the bound no
 * constant: argument 1 to function GENERATOR needs to be constant (all live-verified).
 */
public class GeneratorBoundFoldingTest extends MeasuredCellsTestSupport {

    @Test
    public void aBoundFoldsOnlyAsTheAccountFolds() {
        engine.execute("CREATE OR REPLACE TABLE gn (n INT)");
        engine.execute("INSERT INTO gn VALUES (2), (3)");
        engine.execute("SET gvar = 3");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'LENGTH('abc')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LENGTH('abc')))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DATEDIFF(day, '2024-01-01'::DATE, "
            + "'2024-01-03'::DATE)))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DATEDIFF(day, CURRENT_DATE, CURRENT_DATE) + 2))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => CURRENT_DATE - CURRENT_DATE + 2))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => SQRT(9)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'TRUNC(2.9)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TRUNC(2.9)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CEIL(1.1)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => CEIL(1.1)))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => POWER(2, 2)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'BITAND(7, 3)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => BITAND(7, 3)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'ARRAY_SIZE(ARRAY_CONSTRUCT(1, 2))'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ARRAY_SIZE(ARRAY_CONSTRUCT(1, 2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'REGEXP_COUNT('aaa', 'a')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => REGEXP_COUNT('aaa', 'a')))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => MOD(ABS(HASH(1)), 3) + 1))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LENGTH(CURRENT_DATABASE()) * 0 + 2))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'0 + 2'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DAYOFMONTH(CURRENT_DATE) * 0 + 2))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'2 + 0'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ABS(-2) + ZEROIFNULL(NULL)))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION ALL SELECT "
            + "2))))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1 + 2))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2 * 1.5))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ABS(-3)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3::INT))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => '3'::NUMBER))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TO_NUMBER('3')))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LEAST(2, 3)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => GREATEST(1, 2) + NVL(NULL, 1)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => EXTRACT(day FROM '2024-01-03'::DATE)))");
        assertAnswer("2",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => YEAR(CURRENT_DATE) - YEAR(CURRENT_DATE) + 2))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => IFF(CURRENT_USER() IS NULL, 1, 2)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => CASE WHEN 1 = 1 THEN 2 ELSE 3 END))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => COALESCE(NULL, 2)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM gn)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'ROUND(2.4)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ROUND(2.4)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => FLOOR(2.7)))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => SIGN(-3) + 3))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LN(1) + 2))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => EXP(0) + 1))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LOG(10, 100)))");
        assertAnswer("1", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => MOD(7, 3)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DIV0(4, 2)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULLIF(3, 4)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ZEROIFNULL(2)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NVL2(1, 2, 3)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DECODE(1, 1, 2, 3)))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LEN('ab')))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CHARINDEX('b', 'abc')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => CHARINDEX('b', 'abc')))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TO_DECIMAL('3', 10, 0)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => CAST('3' AS INT)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TRY_CAST('3' AS INT)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3::FLOAT))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2.5::INT))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ABS(-2) + 0))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ABS(-2) + ABS(0)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1 + 1 + 1))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => -(-2)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => +2))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2 + 0))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => IFNULL(NULL, 2)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NVL(2, 3)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ABS(2) * 1))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2 * ABS(1)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'0 + 2'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ZEROIFNULL(NULL) + 2))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TO_NUMBER('2') + 0))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DATE_PART(day, '2024-01-03'::DATE)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DAY('2024-01-03'::DATE)))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LENGTH(UUID_STRING()) - 34))");
        answer("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NORMAL(2, 0, RANDOM())))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'UNIFORM(1, 3, RANDOM())'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => UNIFORM(1, 3, RANDOM())))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'SEQ4()'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => SEQ4()))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'RANDOM()'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => RANDOM()))");
        assertAnswer("0", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL))");
        assertAnswer("0", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2 + NULL))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => $gvar))");
        answer("SELECT gn.n FROM gn, LATERAL (SELECT 1 AS one FROM TABLE(GENERATOR(ROWCOUNT => gn.n))) ORDER BY 1");
        answer("SELECT (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => gn.n))) FROM gn ORDER BY 1");
        answer("SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x.n) FROM gn x WHERE x.n <= gn.n)))");
        assertAnswer("6", "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM gn)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'A.N + B.N'",
            "SELECT COUNT(*) FROM gn a, gn b, TABLE(GENERATOR(ROWCOUNT => a.n + b.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'GN.N'",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NVL(NULL, gn.n)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'NULLIF(GN.N, GN.N)'",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NULLIF(gn.n, gn.n)))");
        assertAnswer("REFUSED SQL compilation error: | argument 2 to function GENERATOR needs to be constant, found "
                + "'LENGTH('a')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2, TIMELIMIT => LENGTH('a')))");
    }
}
