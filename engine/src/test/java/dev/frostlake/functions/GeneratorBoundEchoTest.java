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
 * A GENERATOR bound the account does not fold is refused with its folded plan: a number literal a FLOAT function
 * converts as the FLOAT it folds to, NORMAL's mean and deviation alike, a text cast to DATE as its date, LEN as
 * LENGTH, and a call answering a machine integer cast to its NUMBER where arithmetic or ABS reads it
 * (all live-verified).
 */
public class GeneratorBoundEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void foldedEchoes() {
        engine.execute("CREATE OR REPLACE TABLE gn (n INT)");
        engine.execute("INSERT INTO gn VALUES (2), (3)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'SQRT(9.0)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => SQRT(9)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'SQRT(9.5)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => SQRT(9.5)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'POWER(2.0, 2.0)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => POWER(2, 2)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'POWER(2.5, 3.0)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => POWER(2.5, 3)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'LOG(10.0, 100.0)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LOG(10, 100)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(LN(1.0)) + 2.0'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LN(1) + 2))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(EXP(0.0)) + 1.0'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => EXP(0) + 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(EXP(1.0)) + 1.5'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => EXP(1) + 1.5))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'NORMAL(2.0, 0.0, RANDOM())'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NORMAL(2, 0, RANDOM())))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'NORMAL(2.5, 1.0, RANDOM())'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NORMAL(2.5, 1, RANDOM())))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(MOD(ABS(CAST(HASH(1) AS NUMBER(19,0))), 3)) + 1'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => MOD(ABS(HASH(1)), 3) + 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'ABS(CAST(HASH(1) AS NUMBER(19,0)))'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ABS(HASH(1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(CAST(SIGN(-3) AS NUMBER(2,0))) + 3'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => SIGN(-3) + 3))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(CAST(SIGN(2.5) AS NUMBER(2,0))) + 1'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => SIGN(2.5) + 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(CAST(LENGTH(UUID_STRING()) AS NUMBER(18,0))) - 34'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LENGTH(UUID_STRING()) - 34))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'((CAST(LENGTH('TEST_DB') AS NUMBER(18,0))) * 0) + 2'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LENGTH(CURRENT_DATABASE()) * 0 + 2))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'DATE_DIFFDATEINDAYS('2024-01-01', '2024-01-03')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DATEDIFF(day, '2024-01-01'::DATE, '2024-01-03'::DATE)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(CAST(DATE_DIFFDATEINDAYS('2024-01-01', '2024-01-03') AS NUMBER(9,0))) + 1'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DATEDIFF(day, '2024-01-01'::DATE, '2024-01-03'::DATE) "
                + "+ 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'LENGTH('ab')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LEN('ab')))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(CAST(LENGTH('ab') AS NUMBER(18,0))) + 1'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LEN('ab') + 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'LENGTH(UUID_STRING())'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LENGTH(UUID_STRING())))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'SQRT(CAST(LENGTH('abcd') AS FLOAT))'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => SQRT(LENGTH('abcd'))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(LN(2.5)) + 1.0'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LN(2.5) + 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CEIL(SQRT(4.0))'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => CEIL(SQRT(4))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'3.0 + (SQRT(4.0))'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3 + SQRT(4)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'GN.N + 1'",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => gn.n + 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'GN.N * 2'",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => gn.n * 2))");
    }
}
