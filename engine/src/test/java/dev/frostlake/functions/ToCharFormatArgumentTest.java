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
 * TO_CHAR's and TO_VARCHAR's format must be a text: the bare word NULL and a value of any other type are
 * refused while the statement compiles, by the name the call is written with, and a NULL text answers NULL. A
 * BOOLEAN and the bare word NULL take no format at all, which is told as a count of arguments (live-verified).
 */
public class ToCharFormatArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void theBareWordANumberAndANullText() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1, NULL)");
        assertAnswer(
            "null",
            "SELECT TO_CHAR(1, NULL::VARCHAR)");
        assertAnswer(
            "null",
            "SELECT TO_CHAR(1, s) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_VARCHAR' needs to be a string",
            "SELECT TO_VARCHAR(1, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR('2024-01-02'::DATE, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_CHAR(NULL, NULL)] expected 1, got 2",
            "SELECT TO_CHAR(NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1.5, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(n, NULL) FROM tn");
        assertAnswer(
            "null",
            "SELECT TO_CHAR(a, s) FROM tn");
        assertAnswer(
            "null",
            "SELECT TO_CHAR('2024-01-02'::DATE, s) FROM tn");
        assertAnswer(
            "   1",
            "SELECT TO_CHAR(1, '999')");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1, 5)");
        assertAnswer(
            "null",
            "SELECT TO_CHAR(1, NULL || 'a')");
    }

    @Test
    public void everyOtherTypeAndWhatTakesNoFormat() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        answer(
            "CREATE OR REPLACE TABLE ta (arr ARRAY, v VARIANT, b BOOLEAN, d DATE)");
        answer(
            "INSERT INTO ta SELECT ARRAY_CONSTRUCT(1, 2), PARSE_JSON('[1]'), TRUE, '2024-01-02'::DATE");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1, a) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1, TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1, '2024-01-02'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1, PARSE_JSON('\"9\"'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_CHAR(NULL, 'x')] expected 1, got 2",
            "SELECT TO_CHAR(NULL, 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_VARCHAR(NULL, NULL)] expected 1, got 2",
            "SELECT TO_VARCHAR(NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(TO_BINARY('ab', 'UTF-8'), NULL)");
        assertAnswer(
            "null",
            "SELECT TO_CHAR(TO_BINARY('ab', 'UTF-8'), s) FROM tn");
        assertAnswer(
            "null",
            "SELECT TO_CHAR(n, s) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1, b) FROM ta");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1, v) FROM ta");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1, NULL) FROM tn WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(d, NULL) FROM ta");
        assertAnswer(
            "null",
            "SELECT TO_CHAR(d, s) FROM ta, tn");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(1, NULL::INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_CHAR' needs to be a string",
            "SELECT TO_CHAR(NULL::INT, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_CHAR(TRUE, NULL)] expected 1, got 2",
            "SELECT TO_CHAR(TRUE, NULL)");
    }

    @Test
    public void aBooleanSubject() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_CHAR(TRUE, 'x')] expected 1, got 2",
            "SELECT TO_CHAR(TRUE, 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_CHAR(\"values\".B, NULL)] expected 1, got 2",
            "SELECT TO_CHAR(b, NULL) FROM (SELECT TRUE AS b)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_VARCHAR(FALSE, 'TM9')] expected 1, got 2",
            "SELECT TO_VARCHAR(FALSE, 'TM9')");
        assertAnswer(
            "true",
            "SELECT TO_CHAR(TRUE)");
        assertAnswer(
            "REFUSED Bad output format model 'ab' for FIXED: invalid numeric format keyword: 'ab'",
            "SELECT TO_CHAR(1, 'a' || 'b')");
    }
}
