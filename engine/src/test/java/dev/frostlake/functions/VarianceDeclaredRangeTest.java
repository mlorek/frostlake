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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The variance family over whole NUMBERs holds its sums in signed 128-bit carriers. A value whose square leaves its
 * carrier is refused naming the square, and so is {@code n·Σx²} when the statistic is read; a running sum that
 * leaves its carrier is a SUM aggregate's overflow, which a window with an ORDER BY reports as its intermediate
 * result instead, SUM's own sums included; and a statistic inside the carriers but past its declared NUMBER(38, s) is
 * refused by that type. A series with a fraction is held exactly. It was answered with 39 to 77 integer digits, and a
 * table created from it stored them.
 */
public class VarianceDeclaredRangeTest extends MeasuredCellsTestSupport {

    private static final String SUM_OVERFLOW = "REFUSED Value overflow in a SUM aggregate";
    private static final String WINDOW_OVERFLOW =
        "REFUSED Numeric value is out of range, error: Window function intermediate result too large";
    private static final String CARRIER =
        "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value ";
    private static final String DECLARED =
        "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value ";
    private static final String AVERAGED =
        "REFUSED Number out of representable range: type FIXED[SB16](38,3){nullable}, value ";

    @BeforeEach
    public void createSeries() {
        engine.execute("CREATE OR REPLACE TABLE vdr (o INT, x NUMBER(38,0), y NUMBER(38,0))");
        engine.execute("""
            INSERT INTO vdr VALUES (1, 10000000000000000000, 100000000000000000),
              (2, -10000000000000000000, -100000000000000000), (3, 3, 3), (4, 1, 1), (5, 20000000000000000000, 5),
              (6, 100000000000000000000, 6)""");
    }

    @Test
    public void aSumPastItsCarrierOverflowsTheAggregate() {
        assertCells(new String[][] {
            {"SELECT VARIANCE(x) FROM vdr WHERE o <= 2", SUM_OVERFLOW},
            {"SELECT VAR_POP(x) FROM vdr WHERE o <= 2", SUM_OVERFLOW},
            {"SELECT STDDEV(x) FROM vdr WHERE o <= 2", SUM_OVERFLOW},
            {"SELECT SUM(x * x) FROM vdr WHERE o <= 2", SUM_OVERFLOW},
            // A window without an ORDER BY sums its partition as the aggregate does.
            {"SELECT VAR_POP(x) OVER () FROM vdr WHERE o <= 2", SUM_OVERFLOW},
            {"SELECT o, VARIANCE(x) OVER (PARTITION BY o <= 2) FROM vdr WHERE o <= 4", SUM_OVERFLOW},
            {"SELECT o, SUM(x * x) OVER (PARTITION BY o <= 2) FROM vdr WHERE o <= 4", SUM_OVERFLOW}});
    }

    @Test
    public void anOrderedWindowsSumPastItsCarrierIsTheIntermediateResult() {
        final String[] frames = {"ORDER BY o", "ORDER BY o ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW",
            "ORDER BY o ROWS BETWEEN 1 PRECEDING AND CURRENT ROW", "ORDER BY o ROWS BETWEEN CURRENT ROW AND UNBOUNDED"
            + " FOLLOWING", "ORDER BY o RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING",
            "ORDER BY o RANGE BETWEEN 1 PRECEDING AND CURRENT ROW", "PARTITION BY o <= 2 ORDER BY o"};
        for (final String frame : frames) {
            assertAnswer(WINDOW_OVERFLOW, "SELECT o, VARIANCE(x) OVER (" + frame + ") FROM vdr WHERE o <= 4");
            assertAnswer(WINDOW_OVERFLOW, "SELECT o, SUM(x * x) OVER (" + frame + ") FROM vdr WHERE o <= 4");
        }
        assertCells(new String[][] {
            {"SELECT o, STDDEV(x) OVER (ORDER BY o ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) FROM vdr WHERE o <= 4",
                WINDOW_OVERFLOW},
            {"SELECT o, SUM(x * x) OVER (ORDER BY o ROWS BETWEEN 2 PRECEDING AND 1 PRECEDING) FROM vdr WHERE o <= 4",
                WINDOW_OVERFLOW},
            // A cumulative AVG takes its sums before any quotient, so their overflow is met first.
            {"SELECT o, AVG(x * x) OVER (ORDER BY o) FROM vdr WHERE o <= 4", WINDOW_OVERFLOW},
            // A sliding or a whole-partition AVG reads each value into its sum's carrier first.
            {"SELECT o, AVG(x * x) OVER (ORDER BY o ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM vdr WHERE o <= 4",
                AVERAGED + "100000000000000000000000000000000000000"},
            {"SELECT o, AVG(x * x) OVER () FROM vdr WHERE o <= 4",
                AVERAGED + "100000000000000000000000000000000000000"}});
    }

    @Test
    public void aSquarePastItsCarrierIsRefusedNamingIt() {
        assertCells(new String[][] {
            {"SELECT VARIANCE(x) FROM vdr WHERE o = 5", CARRIER + "4e+38"},
            {"SELECT VAR_POP(x) FROM vdr WHERE o = 6", CARRIER + "1e+40"},
            // n·Σx² is held the same way once the statistic is read.
            {"SELECT VAR_SAMP(x) FROM vdr WHERE o IN (1, 3)", CARRIER + "2e+38"},
            {"SELECT VARIANCE_POP(x) FROM vdr WHERE o IN (1, 3)", CARRIER + "2e+38"}});
    }

    @Test
    public void aStatisticPastItsDeclaredTypeIsRefusedByIt() {
        assertCells(new String[][] {
            {"SELECT VARIANCE(y) FROM vdr WHERE o <= 2", DECLARED + "2e+34"},
            {"SELECT VAR_POP(y) FROM vdr WHERE o <= 2", DECLARED + "1e+34"},
            {"SELECT o, VARIANCE(y) OVER (ORDER BY o ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM vdr WHERE o <= 4",
                DECLARED + "2e+34"}});
    }

    @Test
    public void aSeriesWithAFractionIsHeldExactly() {
        engine.execute("CREATE OR REPLACE TABLE vdf (f NUMBER(38,13), a NUMBER(38,12))");
        engine.execute("INSERT INTO vdf VALUES (10000000, 13000000), (10000000, -13000000)");
        assertCells(new String[][] {
            {"SELECT VAR_POP(f) FROM vdf WHERE a > 0", "0E-13"},
            {"SELECT VARIANCE(a) FROM vdf", "338000000000000.000000000000"}});
    }

    @Test
    public void withinTheCarriersTheStatisticAnswers() {
        assertCells(new String[][] {
            {"SELECT VAR_POP(x) FROM vdr WHERE o IN (3, 4)", "1.000000"},
            {"SELECT VARIANCE(x) FROM vdr WHERE o = 1", "null"},
            {"SELECT VARIANCE(x::FLOAT) FROM vdr WHERE o <= 2", "2.0E38"}});
    }
}
