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
 * UNIFORM's bounds are judged left to right before its generator is read: a NULL bound is refused when the
 * row is read, a first bound above the second is refused with the two told at the scale they are drawn at, a
 * bound that is no constant is refused while the statement compiles, and a NULL generator beside sound bounds
 * answers NULL (live-verified).
 */
public class UniformBoundsTest extends MeasuredCellsTestSupport {

    @Test
    public void nullBoundsAndANullGenerator() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: b must not be NULL",
            "SELECT UNIFORM(1, NULL, 1)");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: a must not be NULL",
            "SELECT UNIFORM(NULL, 1, 1)");
        assertAnswer(
            "null",
            "SELECT UNIFORM(1, 2, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function UNIFORM needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT UNIFORM(1, NULL::INT, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function UNIFORM needs to be constant, found 'TN.N'",
            "SELECT UNIFORM(1, n, 1) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function UNIFORM needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT UNIFORM(NULL::INT, 2, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function UNIFORM needs to be constant, found 'TN.N'",
            "SELECT UNIFORM(n, 2, 1) FROM tn");
        assertAnswer(
            "null",
            "SELECT UNIFORM(1, 2, NULL::INT)");
        assertAnswer(
            "null",
            "SELECT UNIFORM(1, 2, n) FROM tn");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: a must not be NULL",
            "SELECT UNIFORM(NULL, NULL, 1)");
        assertAnswer(
            "1",
            "SELECT UNIFORM(1, 1, 1)");
    }

    @Test
    public void overAnEmptyTableAndBesideOtherFaults() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        answer(
            "CREATE OR REPLACE TABLE ta (arr ARRAY, v VARIANT, b BOOLEAN, d DATE)");
        answer(
            "INSERT INTO ta SELECT ARRAY_CONSTRUCT(1, 2), PARSE_JSON('[1]'), TRUE, '2024-01-02'::DATE");
        assertAnswer(
            "",
            "SELECT UNIFORM(1, NULL, 1) FROM tn WHERE 1 = 0");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: a must not be NULL",
            "SELECT UNIFORM(NULL, 2, NULL)");
        assertAnswer(
            "",
            "SELECT UNIFORM(1, 2, NULL) FROM tn WHERE 1 = 0");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT UNIFORM('a', 2, NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: 2, 1. Reason: a must be smaller than or equal to b",
            "SELECT UNIFORM(2, 1, NULL)");
        assertAnswer(
            "null",
            "SELECT UNIFORM(1, 2, n) FROM tn");
    }

    @Test
    public void theBoundsOutOfOrder() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: 2, 1. Reason: a must be smaller than or equal to b",
            "SELECT UNIFORM(2, 1, 1)");
        assertAnswer(
            "REFUSED Invalid parameter value: 2, 1. Reason: a must be smaller than or equal to b",
            "SELECT UNIFORM(2, 1, RANDOM())");
        assertAnswer(
            "REFUSED Invalid parameter value: 15, 10. Reason: a must be smaller than or equal to b",
            "SELECT UNIFORM(1.5, 1, 1)");
        assertAnswer(
            "null",
            "SELECT UNIFORM(1, 1, NULL)");
        assertAnswer(
            "",
            "SELECT UNIFORM(2, 1, 1) FROM tn WHERE 1 = 0");
    }
}
