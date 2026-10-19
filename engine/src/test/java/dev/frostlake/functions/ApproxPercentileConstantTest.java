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
 * The percentile APPROX_PERCENTILE takes. It is the second argument written, named or not, and it is judged
 * after the argument count and before the refusal of named arguments: a number written out, between 0 and 1
 * inclusive. A number outside that is refused by its value, a text, a NULL and a BOOLEAN as no numeric value,
 * and anything computed - a column, a sum, a cast, a call - as no constant, echoed as the plan holds it:
 * {@code RT2.N}, {@code 0.5 + 0.1}, {@code CAST(0.5 AS FLOAT)}. So {@code APPROX_PERCENTILE(p => 0.5, x => n)}
 * is refused for its percentile, the column. Every answer is live-verified.
 */
public class ApproxPercentileConstantTest extends MeasuredCellsTestSupport {

    @Test
    public void theSecondArgumentWritten() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT)");
        answer(
            "INSERT INTO rt VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE rt2 (n INT)");
        answer(
            "INSERT INTO rt2 VALUES (1), (NULL), (3)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [RT2.N] for function 'APPROX_PERCENTILE', parameter "
                + "2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(p => 0.5, x => n) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function APPROX_PERCENTILE does not "
                + "support named arguments",
            "SELECT APPROX_PERCENTILE(x => n, p => 0.5) OVER () FROM rt2");
        assertAnswer(
            "2.0 ;; 2.0 ;; 2.0",
            "SELECT APPROX_PERCENTILE(n, 0.5) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function APPROX_PERCENTILE does not "
                + "support named arguments",
            "SELECT APPROX_PERCENTILE(expr => n, percentile => 0.5) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function APPROX_PERCENTILE does not "
                + "support named arguments",
            "SELECT APPROX_PERCENTILE(x => n, p => 0.5) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [RT2.N] for function 'APPROX_PERCENTILE', parameter "
                + "2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(p => 0.5, x => n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function APPROX_PERCENTILE does not "
                + "support named arguments",
            "SELECT APPROX_PERCENTILE(n, x => 0.5) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [2] for function 'APPROX_PERCENTILE', parameter 2: "
                + "Percentile must be between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(x => n, p => 2) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [RT2.N] for function 'APPROX_PERCENTILE', parameter "
                + "2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, n) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [2] for function 'APPROX_PERCENTILE', parameter 2: "
                + "Percentile must be between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, 2) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [RT2.N] for function 'APPROX_PERCENTILE', parameter "
                + "2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function APPROX_PERCENTILE does not "
                + "support named arguments",
            "SELECT APPROX_PERCENTILE(p => 0.5, x => 1) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function APPROX_PERCENTILE does not "
                + "support named arguments",
            "SELECT APPROX_PERCENTILE(a => 0.5, b => 0.25) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[APPROX_PERCENTILE(RT2.N)], expected 2, got 1",
            "SELECT APPROX_PERCENTILE(x => n) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[APPROX_PERCENTILE(RT2.N, 0.5, 1)] expected 2, got 3",
            "SELECT APPROX_PERCENTILE(x => n, p => 0.5, q => 1) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function APPROX_COUNT_DISTINCT does not "
                + "support named arguments",
            "SELECT APPROX_COUNT_DISTINCT(x => n) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function PERCENTILE_CONT does not "
                + "support named arguments",
            "SELECT PERCENTILE_CONT(p => 0.5) WITHIN GROUP (ORDER BY n) OVER () FROM rt2");
        assertAnswer(
            "2.000 ;; 2.000 ;; 2.000",
            "SELECT MEDIAN(x => n) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AVG(RT2.N, 1)] expected 1, got 2",
            "SELECT AVG(x => n, y => 1) OVER () FROM rt2");
    }

    @Test
    public void aNumberWrittenOut() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT)");
        answer(
            "INSERT INTO rt VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE rt2 (n INT)");
        answer(
            "INSERT INTO rt2 VALUES (1), (NULL), (3)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [0.5 + 0.1] for function 'APPROX_PERCENTILE', "
                + "parameter 2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, 0.5 + 0.1) FROM rt2");
        assertAnswer(
            "3.0",
            "SELECT APPROX_PERCENTILE(n, 1) FROM rt2");
        assertAnswer(
            "1.0",
            "SELECT APPROX_PERCENTILE(n, 0) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [-0.1] for function 'APPROX_PERCENTILE', parameter 2: "
                + "Percentile must be between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, -0.1) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [1.01] for function 'APPROX_PERCENTILE', parameter 2: "
                + "Percentile must be between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, 1.01) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value ['0.5'] for function 'APPROX_PERCENTILE', parameter "
                + "2: Percentile must be a numeric value.",
            "SELECT APPROX_PERCENTILE(n, '0.5') FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [null] for function 'APPROX_PERCENTILE', parameter 2: "
                + "Percentile must be a numeric value.",
            "SELECT APPROX_PERCENTILE(n, NULL) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [RT2.N * 0] for function 'APPROX_PERCENTILE', "
                + "parameter 2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, n * 0) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [TRUE] for function 'APPROX_PERCENTILE', parameter 2: "
                + "Percentile must be a numeric value.",
            "SELECT APPROX_PERCENTILE(n, TRUE) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(0.5 AS FLOAT)] for function 'APPROX_PERCENTILE',"
                + " parameter 2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, 0.5::FLOAT) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [ABS(-0.5)] for function 'APPROX_PERCENTILE', "
                + "parameter 2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, ABS(-0.5)) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [2] for function 'APPROX_PERCENTILE', parameter 2: "
                + "Percentile must be between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, 2) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [RT2.N] for function 'APPROX_PERCENTILE', parameter "
                + "2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(n, n) OVER (PARTITION BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function PERCENTILE_CONT needs to be constant, found "
                + "'RT2.N'",
            "SELECT PERCENTILE_CONT(n) WITHIN GROUP (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED Invalid parameter value: 2. Reason: percentile must be between 0 and 1",
            "SELECT PERCENTILE_CONT(2) WITHIN GROUP (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function PERCENTILE_DISC needs to be constant, found "
                + "'RT2.N'",
            "SELECT PERCENTILE_DISC(n) WITHIN GROUP (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: percentile must be between 0 and 1",
            "SELECT PERCENTILE_DISC(-1) WITHIN GROUP (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[APPROX_PERCENTILE(RT2.N)], expected 2, got 1",
            "SELECT APPROX_PERCENTILE(n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[APPROX_PERCENTILE(RT2.N, 0.5, 1)] expected 2, got 3",
            "SELECT APPROX_PERCENTILE(n, 0.5, 1) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [RT2.N] for function 'APPROX_PERCENTILE', parameter "
                + "2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT APPROX_PERCENTILE(x => n, p => n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[APPROX_PERCENTILE(RT2.N, 0.5, RT2.N)] expected 2, got 3",
            "SELECT APPROX_PERCENTILE(x => n, p => 0.5, q => n) FROM rt2");
    }
}
