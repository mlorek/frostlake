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
 * The offset of LAG and LEAD is a whole number written out — with a sign, a fraction of zero or brackets. An
 * argument the plan cannot fold is refused as argument 2, and one it can fold that is no such number — the bare
 * word NULL, a fraction, a text, 1 + 1, ABS(1) — as argument 1, each echoed as the plan prints it; a BOOLEAN is
 * refused by the argument types (live-verified).
 */
public class LagLeadOffsetTest extends MeasuredCellsTestSupport {

    @Test
    public void nullAndWhatIsNoConstant() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT LAG(1, NULL) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LAG needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT LAG(1, NULL::INT) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LAG needs to be constant, found 'TN.N'",
            "SELECT LAG(a, n) OVER (ORDER BY a) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LEAD needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT LEAD(1, NULL) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LEAD needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT LEAD(1, NULL::INT) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LEAD needs to be constant, found 'TN.N'",
            "SELECT LEAD(a, n) OVER (ORDER BY a) FROM tn");
        assertAnswer(
            "null",
            "SELECT LAG(1, 1, NULL) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LAG needs to be constant, found 'TN.A'",
            "SELECT LAG(1, a) OVER (ORDER BY 1) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found 'CAST(1.5 AS "
                + "NUMBER(18,0))'",
            "SELECT LAG(1, 1.5) OVER (ORDER BY 1)");
        assertAnswer(
            "null",
            "SELECT LAG(1, -1) OVER (ORDER BY 1)");
        assertAnswer(
            "null",
            "SELECT LAG(NULL, 1) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LAG needs to be constant, found '1 + "
                + "(SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT LAG(1, 1 + NULL) OVER (ORDER BY 1)");
    }

    @Test
    public void whatFoldsAndIsNoWholeNumber() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        answer(
            "CREATE OR REPLACE TABLE ta (arr ARRAY, v VARIANT, b BOOLEAN, d DATE)");
        answer(
            "INSERT INTO ta SELECT ARRAY_CONSTRUCT(1, 2), PARSE_JSON('[1]'), TRUE, '2024-01-02'::DATE");
        assertAnswer(
            "null",
            "SELECT LAG(1, 2.0) OVER (ORDER BY 1)");
        assertAnswer(
            "null",
            "SELECT LAG(1, 1e0) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found "
                + "'TO_NUMBER('1', 18, 0)'",
            "SELECT LAG(1, '1') OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LAG': (NUMBER(1,0), BOOLEAN)",
            "SELECT LAG(1, TRUE) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found '1 + 1'",
            "SELECT LAG(1, 1 + 1) OVER (ORDER BY 1)");
        assertAnswer(
            "1",
            "SELECT LAG(1, 0) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT LAG(a, NULL) OVER (ORDER BY a) FROM tn WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LAG needs to be constant, found 'TN.N'",
            "SELECT LAG(a, n) OVER (ORDER BY a) FROM tn WHERE 1 = 0");
        assertAnswer(
            "null",
            "SELECT LAG(a, 1, n) OVER (ORDER BY a) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LEAD needs to be constant, found 'CAST(1.5 "
                + "AS NUMBER(18,0))'",
            "SELECT LEAD(1, 1.5) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LEAD needs to be constant, found 'TN.A'",
            "SELECT LEAD(a, a) OVER (ORDER BY a) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found 'CAST(-1.5 "
                + "AS NUMBER(18,0))'",
            "SELECT LAG(1, -1.5) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LAG needs to be constant, found "
                + "'SYSTEM$NULL_TO_TEXT(null)'",
            "SELECT LAG(1, NULL::VARCHAR) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LAG needs to be constant, found "
                + "'ABS(SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT LAG(1, ABS(NULL)) OVER (ORDER BY 1)");
    }

    @Test
    public void callsCastsAndSigns() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found "
                + "'TO_NUMBER('a', 18, 0)'",
            "SELECT LAG(1, 'a') OVER (ORDER BY 1)");
        assertAnswer(
            "null",
            "SELECT LAG(1, 1.0) OVER (ORDER BY 1)");
        assertAnswer(
            "null",
            "SELECT LEAD(1, -2.0) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "SELECT LAG(1, 1, 'x') OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found 'ABS(1)'",
            "SELECT LAG(1, ABS(1)) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found '1 * 1'",
            "SELECT LAG(1, 1 * 1) OVER (ORDER BY 1)");
        assertAnswer(
            "null",
            "SELECT LAG(1, (1)) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found 'UNARY "
                + "PLUS(1)'",
            "SELECT LAG(1, +1) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAG needs to be constant, found 'CAST(1 AS "
                + "NUMBER(38,0))'",
            "SELECT LAG(1, 1::INT) OVER (ORDER BY 1)");
    }
}
