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
 * A GENERATOR bound calling a function the account does not fold is no constant under any of the function's names:
 * TIMEDIFF and TIMESTAMPDIFF are refused as DATEDIFF is, echoed as DATE_DIFFDATEINDAYS, and POSITION, written with
 * a comma or with IN, as CHARINDEX is, echoed as POSITION('c', 'abc'), under EXPLAIN too (all live-verified).
 */
public class GeneratorBoundAliasTest extends MeasuredCellsTestSupport {

    @Test
    public void aliasesAreNotFolded() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'DATE_DIFFDATEINDAYS('2024-01-01', '2024-01-04')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DATEDIFF(day, '2024-01-01'::DATE, '2024-01-04'::DATE)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'DATE_DIFFDATEINDAYS('2024-01-01', '2024-01-04')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TIMEDIFF(day, '2024-01-01'::DATE, '2024-01-04'::DATE)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'DATE_DIFFDATEINDAYS('2024-01-01', '2024-01-04')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TIMESTAMPDIFF(day, '2024-01-01'::DATE, "
                + "'2024-01-04'::DATE)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CHARINDEX('c', 'abc')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => CHARINDEX('c', 'abc')))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'POSITION('c', 'abc')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => POSITION('c', 'abc')))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'POSITION('c', 'abc')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => POSITION('c' IN 'abc')))");
        assertAnswer("REFUSED SQL compilation error: | argument 2 to function GENERATOR needs to be constant, found "
                + "'DATE_DIFFDATEINSECONDS('2024-01-01', '2024-01-01')'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1, TIMELIMIT => TIMEDIFF(second, '2024-01-01'::DATE, "
                + "'2024-01-01'::DATE)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => FLOOR(3.5)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(CAST(DATE_DIFFDATEINDAYS('2024-01-01', '2024-01-04') AS NUMBER(9,0))) + 1'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TIMEDIFF(day, '2024-01-01'::DATE, '2024-01-04'::DATE) "
                + "+ 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'DATE_DIFFDATEINDAYS('2024-01-01', '2024-01-04')'",
            "EXPLAIN SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TIMEDIFF(day, '2024-01-01'::DATE, "
                + "'2024-01-04'::DATE)))");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
