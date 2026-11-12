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
 * ROUND, TRUNC, CEIL and FLOOR of a FLOAT whose scale is past its digits leave it as it is: a double of magnitude 2^52
 * or more has no fraction, so "if scale_expr is larger than the input expression scale, the function doesn't have
 * any effect" (documented, ROUND, whose example table rounds 135.135 to 50 places as 135.135). A scale past the
 * powers of ten a double holds exactly shifted the value back by an inexact one and answered a neighbour of it. Ties
 * round half away from zero, large values keep every digit, and the example table reads as the page prints it.
 */
public class RoundFloatPastItsDigitsTest extends MeasuredCellsTestSupport {

    @Test
    public void aScalePastTheValuesDigitsLeavesItAsItIs() {
        assertCells(new String[][] {
            {"SELECT ROUND(135.135::FLOAT, 50), TRUNC(135.135::FLOAT, 50), CEIL(135.135::FLOAT, 30), "
                + "FLOOR(-975.975::FLOAT, 40)", "135.135|135.135|135.135|-975.975"},
            {"SELECT ROUND(1e20::FLOAT), ROUND(-3e25::FLOAT), ROUND(1e20::FLOAT, 2), ROUND(1.5e19::FLOAT, -5)",
                "1.0E20|-3.0E25|1.0E20|1.5E19"},
            {"SELECT ROUND(1e17::FLOAT, 2), ROUND(123456789012.345::FLOAT, 8), ROUND(1.5::FLOAT, 20)",
                "1.0E17|1.23456789012345E11|1.5"},
            {"SELECT ROUND(1e300::FLOAT, 0), ROUND(1e19::FLOAT), ROUND(1e300::FLOAT, 5)", "1.0E300|1.0E19|1.0E300"},
            {"SELECT ROUND(-2.5::FLOAT), ROUND(-0.5::FLOAT), ROUND(-1.5::FLOAT), ROUND(2.5::FLOAT)",
                "-3.0|-1.0|-2.0|3.0"},
        });
    }

    @Test
    public void theDocumentsExampleTable() {
        engine.execute("CREATE TABLE rfp_ceiling (n FLOAT, scale INTEGER)");
        engine.execute("INSERT INTO rfp_ceiling (n, scale) VALUES (-975.975, -1), (-975.975, 0), (-975.975, 2), "
            + "(135.135, -2), (135.135, 0), (135.135, 1), (135.135, 3), (135.135, 50), (135.135, NULL)");
        assertAnswer("-975.975|-1|-980.0 ;; -975.975|0|-976.0 ;; -975.975|2|-975.98 ;; 135.135|-2|100.0 ;; "
            + "135.135|0|135.0 ;; 135.135|1|135.1 ;; 135.135|3|135.135 ;; 135.135|50|135.135 ;; 135.135|null|null",
            "SELECT n, scale, ROUND(n, scale) FROM rfp_ceiling ORDER BY n, scale NULLS LAST");
    }
}
