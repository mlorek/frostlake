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
 * GET and the bracket index over an ARRAY refuse an index the plan folds to NULL by passing a written NULL on —
 * the bare word, a cast of it to a number, NULL + 1, TO_NUMBER(NULL) — over a table with no row too, where a
 * NULL read from a column or answered by a condition answers NULL, and so does a NULL text, an ARRAY that is
 * itself NULL, and a VARIANT or an OBJECT read with a NULL (live-verified).
 */
public class GetNullArrayIndexTest extends MeasuredCellsTestSupport {

    @Test
    public void theBareWordAndAColumn() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT GET(ARRAY_CONSTRUCT(1), NULL)");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT GET(ARRAY_CONSTRUCT(1), NULL::INT)");
        assertAnswer(
            "null",
            "SELECT GET(ARRAY_CONSTRUCT(1), n) FROM tn");
        assertAnswer(
            "null",
            "SELECT GET(OBJECT_CONSTRUCT('a', 1), NULL)");
        assertAnswer(
            "null",
            "SELECT GET(OBJECT_CONSTRUCT('a', 1), NULL::VARCHAR)");
        assertAnswer(
            "null",
            "SELECT GET(OBJECT_CONSTRUCT('a', 1), s) FROM tn");
        assertAnswer(
            "null",
            "SELECT GET(NULL, 0)");
        assertAnswer(
            "null",
            "SELECT GET(PARSE_JSON('[1]'), NULL)");
        assertAnswer(
            "null",
            "SELECT GET(PARSE_JSON('[1]'), n) FROM tn");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT ARRAY_CONSTRUCT(1)[NULL]");
        assertAnswer(
            "null",
            "SELECT ARRAY_CONSTRUCT(1)[n] FROM tn");
        assertAnswer(
            "1",
            "SELECT GET(ARRAY_CONSTRUCT(1), 0)");
    }

    @Test
    public void whatFoldsAndWhatDoesNot() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        answer(
            "CREATE OR REPLACE TABLE ta (arr ARRAY, v VARIANT, b BOOLEAN, d DATE)");
        answer(
            "INSERT INTO ta SELECT ARRAY_CONSTRUCT(1, 2), PARSE_JSON('[1]'), TRUE, '2024-01-02'::DATE");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT GET(arr, NULL) FROM ta");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT arr[NULL] FROM ta");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT GET(arr, NULL::INT) FROM ta");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT GET(ARRAY_CONSTRUCT(1), NULL + 1)");
        assertAnswer(
            "null",
            "SELECT GET(ARRAY_CONSTRUCT(1), NULL::VARCHAR)");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT ARRAY_CONSTRUCT(1)[NULL::INT]");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT GET(ARRAY_CONSTRUCT(1), TO_NUMBER(NULL))");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT GET(TO_ARRAY(1), NULL)");
        assertAnswer(
            "null",
            "SELECT GET(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1)), 0)[NULL]");
        assertAnswer(
            "null",
            "SELECT GET_IGNORE_CASE(ARRAY_CONSTRUCT(1), NULL)");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT GET(ARRAY_CONSTRUCT(1), NULL) FROM tn WHERE 1 = 0");
        assertAnswer(
            "null",
            "SELECT GET(v, NULL) FROM ta");
        assertAnswer(
            "null",
            "SELECT v[NULL] FROM ta");
        assertAnswer(
            "null",
            "SELECT GET(arr, n) FROM ta, tn");
        assertAnswer(
            "null",
            "SELECT GET(NULL::ARRAY, NULL)");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT GET(ARRAY_CONSTRUCT(), NULL)");
        assertAnswer(
            "null",
            "SELECT ARRAY_SLICE(arr, NULL, 1) FROM ta");
    }

    @Test
    public void aNullAConditionAnswers() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT GET(ARRAY_CONSTRUCT(1), 1 + NULL)");
        assertAnswer(
            "null",
            "SELECT GET(ARRAY_CONSTRUCT(1), NULLIF(1, 1))");
        assertAnswer(
            "null",
            "SELECT GET(ARRAY_CONSTRUCT(1), IFF(TRUE, NULL, 0))");
        assertAnswer(
            "REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position {1}.",
            "SELECT ARRAY_CONSTRUCT(1)[NULL + 1]");
    }
}
