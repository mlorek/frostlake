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
 * NTH_VALUE's n is a whole number written out, or a text that spells one: the bare word NULL, a zero, a
 * fraction, a BOOLEAN and anything the plan would have to work out are refused while the statement compiles,
 * over a table with no row too, and a negative n when a row is read (live-verified).
 */
public class NthValueNTest extends MeasuredCellsTestSupport {

    @Test
    public void nullZeroAndWhatIsNoConstant() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [null] for function 'NTH_VALUE', parameter 2: n in "
                + "nth_value cannot be null",
            "SELECT NTH_VALUE(1, NULL) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function NTH_VALUE needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT NTH_VALUE(1, NULL::INT) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function NTH_VALUE needs to be constant, found 'TN.N'",
            "SELECT NTH_VALUE(a, n) OVER (ORDER BY a) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [0] for function 'NTH_VALUE', parameter 2: N (i.e., "
                + "second argument) cannot be zero.",
            "SELECT NTH_VALUE(1, 0) OVER (ORDER BY 1)");
        assertAnswer(
            "1",
            "SELECT NTH_VALUE(1, 1) OVER (ORDER BY 1)");
        assertAnswer(
            "null",
            "SELECT NTH_VALUE(NULL, 1) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function NTH_VALUE needs to be constant, found 'TN.A'",
            "SELECT NTH_VALUE(a, a) OVER (ORDER BY a) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function NTH_VALUE needs to be constant, found "
                + "'CAST(1.5 AS NUMBER(18,0))'",
            "SELECT NTH_VALUE(1, 1.5) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function NTH_VALUE needs to be constant, found '1 + "
                + "(SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT NTH_VALUE(1, 1 + NULL) OVER (ORDER BY 1)");
    }

    @Test
    public void textsFractionsAndFromFirst() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        answer(
            "CREATE OR REPLACE TABLE ta (arr ARRAY, v VARIANT, b BOOLEAN, d DATE)");
        answer(
            "INSERT INTO ta SELECT ARRAY_CONSTRUCT(1, 2), PARSE_JSON('[1]'), TRUE, '2024-01-02'::DATE");
        assertAnswer(
            "1",
            "SELECT NTH_VALUE(1, 1.0) OVER (ORDER BY 1)");
        assertAnswer(
            "1",
            "SELECT NTH_VALUE(1, '1') OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function NTH_VALUE needs to be constant, found '1 + 1'",
            "SELECT NTH_VALUE(1, 1 + 1) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: Second argument of the function must be a positive "
                + "integer.",
            "SELECT NTH_VALUE(1, -1) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NTH_VALUE': (NUMBER(1,0), BOOLEAN)",
            "SELECT NTH_VALUE(1, TRUE) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [null] for function 'NTH_VALUE', parameter 2: n in "
                + "nth_value cannot be null",
            "SELECT NTH_VALUE(a, NULL) OVER (ORDER BY a) FROM tn WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [0] for function 'NTH_VALUE', parameter 2: N (i.e., "
                + "second argument) cannot be zero.",
            "SELECT NTH_VALUE(a, 0) OVER (ORDER BY a) FROM tn WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [0] for function 'NTH_VALUE', parameter 2: N (i.e., "
                + "second argument) cannot be zero.",
            "SELECT NTH_VALUE(1, 0.0) OVER (ORDER BY 1)");
        assertAnswer(
            "null",
            "SELECT NTH_VALUE(1, 2) FROM LAST OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [null] for function 'NTH_VALUE', parameter 2: n in "
                + "nth_value cannot be null",
            "SELECT NTH_VALUE(1, NULL) FROM FIRST OVER (ORDER BY 1)");
    }

    @Test
    public void aTextNAndBrackets() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "null",
            "SELECT NTH_VALUE(1, '1.5') OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function NTH_VALUE needs to be constant, found "
                + "'TO_NUMBER('a', 18, 0)'",
            "SELECT NTH_VALUE(1, 'a') OVER (ORDER BY 1)");
        assertAnswer(
            "null",
            "SELECT NTH_VALUE(1, '2') OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [0] for function 'NTH_VALUE', parameter 2: N (i.e., "
                + "second argument) cannot be zero.",
            "SELECT NTH_VALUE(1, '0') OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: Second argument of the function must be a positive "
                + "integer.",
            "SELECT NTH_VALUE(1, '-1') OVER (ORDER BY 1)");
        assertAnswer(
            "null",
            "SELECT NTH_VALUE(1, 2.0) OVER (ORDER BY 1)");
        assertAnswer(
            "2 ;; 2",
            "SELECT NTH_VALUE(a, 2) OVER (ORDER BY a) FROM (SELECT 1 AS a UNION ALL SELECT 2)");
        assertAnswer(
            "2 ;; 2",
            "SELECT NTH_VALUE(a, 2.0) OVER (ORDER BY a ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) "
                + "FROM (SELECT 1 AS a UNION ALL SELECT 2) ORDER BY 1");
        assertAnswer(
            "2 ;; 2",
            "SELECT NTH_VALUE(a, '2') OVER (ORDER BY a ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) "
                + "FROM (SELECT 1 AS a UNION ALL SELECT 2) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function NTH_VALUE needs to be constant, found "
                + "'CAST(-1.5 AS NUMBER(18,0))'",
            "SELECT NTH_VALUE(1, -1.5) OVER (ORDER BY 1)");
        assertAnswer(
            "",
            "SELECT NTH_VALUE(a, -1) OVER (ORDER BY a) FROM tn WHERE 1 = 0");
        assertAnswer(
            "1",
            "SELECT NTH_VALUE(1, (1)) OVER (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function NTH_VALUE needs to be constant, found "
                + "'CAST(1 AS NUMBER(38,0))'",
            "SELECT NTH_VALUE(1, 1::INT) OVER (ORDER BY 1)");
    }
}
