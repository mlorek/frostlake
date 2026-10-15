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
 * RANDSTR's length is judged before its seed is read: a NULL one and a negative one are refused when the row
 * is read, a text is read as the number it spells and a fraction goes to the nearest whole number, where a
 * NULL seed answers NULL (live-verified).
 */
public class RandStrLengthTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullLengthAndANullSeed() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: length must not be NULL",
            "SELECT RANDSTR(NULL, 1)");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: length must not be NULL",
            "SELECT RANDSTR(NULL::INT, 1)");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: length must not be NULL",
            "SELECT RANDSTR(n, 1) FROM tn");
        assertAnswer(
            "null",
            "SELECT RANDSTR(1, NULL)");
        assertAnswer(
            "null",
            "SELECT RANDSTR(1, NULL::INT)");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: length must not be NULL",
            "SELECT RANDSTR(NULL, NULL)");
        assertAnswer(
            "null",
            "SELECT RANDSTR(1, n) FROM tn");
        assertAnswer(
            "3",
            "SELECT LENGTH(RANDSTR(3, 1))");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: length must not be negative",
            "SELECT RANDSTR(-1, 1)");
    }

    @Test
    public void fractionsTextsAndAnEmptyTable() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        answer(
            "CREATE OR REPLACE TABLE ta (arr ARRAY, v VARIANT, b BOOLEAN, d DATE)");
        answer(
            "INSERT INTO ta SELECT ARRAY_CONSTRUCT(1, 2), PARSE_JSON('[1]'), TRUE, '2024-01-02'::DATE");
        assertAnswer(
            "REFUSED Invalid parameter value: -2. Reason: length must not be negative",
            "SELECT RANDSTR(-1.5, 1)");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT RANDSTR('a', 1)");
        assertAnswer(
            "",
            "SELECT RANDSTR(0, 1)");
        assertAnswer(
            "",
            "SELECT RANDSTR(NULL, 1) FROM tn WHERE 1 = 0");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: length must not be negative",
            "SELECT RANDSTR(-1, NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: length must not be NULL",
            "SELECT RANDSTR(n, n) FROM tn");
        assertAnswer(
            "REFUSED Invalid parameter value: -2. Reason: length must not be negative",
            "SELECT RANDSTR(-2, 1) FROM tn");
    }

    @Test
    public void aTextThatSpellsTheLength() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "true",
            "SELECT RANDSTR('2', 1) = RANDSTR(2, 1)");
        assertAnswer(
            "3",
            "SELECT LENGTH(RANDSTR(2.5, 1))");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: length must not be negative",
            "SELECT RANDSTR('-1', 1)");
    }
}
