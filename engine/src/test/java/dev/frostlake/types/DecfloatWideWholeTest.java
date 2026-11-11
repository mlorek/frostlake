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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A whole DECFLOAT whose plain form needs more than 38 digits is read at its significant digits and its exponent, as
 * the account's driver reads it - {@code 1E+40}, {@code 8.666666666666666666666666666666666667E+40} - wherever it comes
 * from: a literal, arithmetic, a statistic, a column, a sort, a grouping or a join; one of at most 38 digits is
 * written out in full (live-verified).
 */
public class DecfloatWideWholeTest extends MeasuredCellsTestSupport {

    @Test
    public void aWideWholeDecfloatKeepsItsExponent() {
        engine.execute("CREATE OR REPLACE TABLE r908 (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO r908 VALUES (1e20, 3), (2e20, 5), (5e20, 4)");
        engine.execute("CREATE OR REPLACE TABLE d908 (d DECFLOAT, k INT)");
        engine.execute("INSERT INTO d908 VALUES (DECFLOAT '1e40', 1), (DECFLOAT '10e39', 2), (DECFLOAT '9.99e50', "
            + "3), (DECFLOAT '1e20', 4), (DECFLOAT '-2e39', 5)");
        assertCells(new String[][] {
            {"SELECT REGR_SXX(y, x) FROM r908", "8.666666666666666666666666666666666667E+40"},
            {"SELECT REGR_SXX(y, x)::VARCHAR FROM r908", "8.666666666666666666666666666666666667e40"},
            {"SELECT VAR_POP(x) * 3 FROM r908", "8.6666666666666666666666666666666666667E+40"},
            {"SELECT DECFLOAT '1e40', DECFLOAT '1.5e45', DECFLOAT '-2e39', DECFLOAT '1e38', DECFLOAT '1e37'",
                "1E+40|1.5E+45|-2E+39|1E+38|10000000000000000000000000000000000000"},
            {"SELECT DECFLOAT '123456789012345678901234567890123456789', DECFLOAT '1e20', DECFLOAT '12e36'",
                "1.2345678901234567890123456789012345679E+38|100000000000000000000|12000000000000000000000000000000"
                + "000000"},
            {"SELECT DECFLOAT '1e40' + 1, DECFLOAT '1e40' * 2, DECFLOAT '1e40' / 4, DECFLOAT '1e40' - DECFLOAT '1e40'",
                "1E+40|2E+40|2.5E+39|0"},
            {"SELECT TO_DECFLOAT('10000000000000000000000000000000000000000'), TO_DECFLOAT(1e40::FLOAT)",
                "1E+40|1.0000000000000000303786028427003666891E+40"},
            {"SELECT d, k FROM d908 ORDER BY k",
                "1E+40|1 ;; 1E+40|2 ;; 9.99E+50|3 ;; 100000000000000000000|4 ;; -2E+39|5"},
            {"SELECT d FROM d908 ORDER BY d", "-2E+39 ;; 100000000000000000000 ;; 1E+40 ;; 1E+40 ;; 9.99E+50"},
            {"SELECT COUNT(DISTINCT d), COUNT(*) FROM d908", "4|5"},
            {"SELECT d, COUNT(*) FROM d908 GROUP BY d ORDER BY d",
                "-2E+39|1 ;; 100000000000000000000|1 ;; 1E+40|2 ;; 9.99E+50|1"},
            {"SELECT SUM(d), MAX(d), MIN(d) FROM d908", "9.990000000180000000000000000001E+50|9.99E+50|-2E+39"},
            {"SELECT a.k, b.k FROM d908 a JOIN d908 b ON a.d = b.d WHERE a.k < b.k ORDER BY 1, 2", "1|2"},
            {"SELECT d = DECFLOAT '1e40', d::VARCHAR FROM d908 ORDER BY k",
                "true|1e40 ;; true|1e40 ;; false|9.99e50 ;; false|100000000000000000000 ;; false|-2e39"},
            {"SELECT TO_VARIANT(DECFLOAT '1e40'), ARRAY_CONSTRUCT(DECFLOAT '1e40')::VARCHAR", "1e40|[\"1e40\"]"},
            {"SELECT (DECFLOAT '1e40')::NUMBER(38, 0)",
                "REFUSED Decfloat out of representable range, operation: TO_NUMBER(1e40, 38, 0)"},
            {"SELECT (DECFLOAT '1e37')::NUMBER(38, 0), (DECFLOAT '1e40')::FLOAT",
                "10000000000000000000000000000000000000|1.0E40"},
            {"SELECT SYSTEM$TYPEOF(DECFLOAT '1e40'), DECFLOAT '1e40' > DECFLOAT '9e39'",
                "DECFLOAT(38)[DECFLOAT18]|true"},
            {"SELECT IFF(k = 1, DECFLOAT '1e40', DECFLOAT '1e39') FROM d908 WHERE k <= 2 ORDER BY k", "1E+40 ;; 1E+39"},
        });
    }
}
