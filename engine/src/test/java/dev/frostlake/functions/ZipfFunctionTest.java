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
 * ZIPF(s, N, gen): an integer in 1..N drawn by Zipf's law, typed as wide as N's integer digits, one value per
 * generator value; its arguments are judged in the account's sentences before the generator is read, and a NULL
 * generator answers NULL (live-verified). The values drawn are the engine's own, so only their range and their
 * repetition are compared.
 */
public class ZipfFunctionTest extends MeasuredCellsTestSupport {

    @Test
    public void zipfDrawsWithinItsRangeAndJudgesItsArguments() {
        engine.execute("CREATE OR REPLACE TABLE zt AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 200))");
        assertCells(new String[][] {
            {"SELECT COUNT(DISTINCT ZIPF(1, 100, RANDOM(9))) > 5 FROM zt", "true"},
            {"SELECT MIN(ZIPF(1, 100, RANDOM(9))) >= 1, MAX(ZIPF(1, 100, RANDOM(9))) <= 100 FROM zt", "true|true"},
            {"SELECT SYSTEM$TYPEOF(ZIPF(1, 100, RANDOM()))", "NUMBER(3,0)[SB2]"},
        });
        assertShape("Z:NUMBER(3,0)", "SELECT ZIPF(1, 100, RANDOM()) AS z");
        assertCells(new String[][] {
            {"SELECT ZIPF(1, 10, 1) = ZIPF(1, 10, 1)", "true"},
            {"SELECT COUNT(DISTINCT ZIPF(1, 10, 5)) FROM zt", "1"},
            {"SELECT ZIPF(-1, 10, RANDOM())", "REFUSED Invalid parameter value: -1. Reason: alpha must be >= 0.0"},
            {"SELECT ZIPF(1, 0, RANDOM())", "REFUSED Invalid parameter value: 0. Reason: n must be >= 1"},
            {"SELECT ZIPF(1, -5, RANDOM())", "REFUSED Invalid parameter value: -5. Reason: n must be >= 1"},
            {"SELECT ZIPF(NULL, 10, RANDOM())",
                "REFUSED Invalid parameter value: NULL. Reason: alpha must not be NULL"},
            {"SELECT ZIPF(1, NULL, RANDOM())", "REFUSED Invalid parameter value: NULL. Reason: n must not be NULL"},
            {"SELECT ZIPF(1, 10, NULL)", "null"},
            {"SELECT ZIPF(1.5, 10, RANDOM()) BETWEEN 1 AND 10", "true"},
            {"SELECT ZIPF('1', 10, RANDOM()) BETWEEN 1 AND 10", "true"},
            {"SELECT ZIPF(1, 10)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[ZIPF(1, 10)], expected 3, got 2"},
            {"SELECT ZIPF(1, 10, 3, 4)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ZIPF(1, 10, 3, 4)] expected 3, got 4"},
            {"SELECT ZIPF(1, 1, RANDOM())", "1"},
        });
    }
}
