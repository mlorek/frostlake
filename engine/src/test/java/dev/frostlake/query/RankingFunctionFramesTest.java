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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * The frames the ranking and navigation functions take. ROW_NUMBER, NTILE, CUME_DIST, PERCENT_RANK, LAG, LEAD and
 * the two event counters stand beside the whole-partition frame and the frame an ORDER BY brings, and no other:
 * a ROWS frame with one edge unbounded is a cumulative frame and with none a sliding one, and a RANGE frame is
 * echoed in the account's own spelling, each refused at the frame while the statement compiles - ahead of the
 * call's own count and types, a missing relation and an unknown name, and behind a frame that has no ORDER BY.
 * RANK and DENSE_RANK refuse the same RANGE frames. NTILE's count is a number or what spells one, rounded half
 * away from zero, and at least 1; RATIO_TO_REPORT reads a text as its number and refuses one that spells none.
 * A RANGE frame with an offset gives a row whose key is NULL the rows that share it. Every answer is
 * live-verified.
 */
public class RankingFunctionFramesTest extends MeasuredCellsTestSupport {

    @Test
    public void rankingFunctionsAndTheirArguments() {
        engine.execute(
            "CREATE OR REPLACE TABLE w (g VARCHAR, v NUMBER(5,1))");
        engine.execute(
            "INSERT INTO w VALUES ('a', 1), ('a', 2), ('a', 2), ('a', 4), ('b', 10), ('b', NULL)");
        try {
            assertAnswer(
                "a|1.0|1 ;; a|2.0|2 ;; a|2.0|2 ;; a|4.0|4 ;; b|10.0|1 ;; b|null|2",
                "SELECT g, v, RANK() OVER (PARTITION BY g ORDER BY v) AS r FROM w ORDER BY g, v");
            assertAnswer(
                "a|1.0|1 ;; a|2.0|2 ;; a|2.0|2 ;; a|4.0|3 ;; b|10.0|1 ;; b|null|2",
                "SELECT g, v, DENSE_RANK() OVER (PARTITION BY g ORDER BY v) AS r FROM w ORDER BY g, v");
            assertAnswer(
                "a|1.0|0.0 ;; a|2.0|0.3333333333333333 ;; a|2.0|0.3333333333333333 ;; a|4.0|1.0 ;; b|10.0|0.0 ;; "
                    + "b|null|1.0",
                "SELECT g, v, PERCENT_RANK() OVER (PARTITION BY g ORDER BY v) AS r FROM w ORDER BY g, v");
            assertAnswer(
                "a|1.0|0.25 ;; a|2.0|0.75 ;; a|2.0|0.75 ;; a|4.0|1.0 ;; b|10.0|0.5 ;; b|null|1.0",
                "SELECT g, v, CUME_DIST() OVER (PARTITION BY g ORDER BY v) AS r FROM w ORDER BY g, v");
            assertAnswer(
                "a|1.0|1 ;; a|2.0|1 ;; a|2.0|2 ;; a|4.0|2 ;; b|10.0|1 ;; b|null|2",
                "SELECT g, v, NTILE(2) OVER (PARTITION BY g ORDER BY v) AS r FROM w ORDER BY g, v");
            assertAnswer(
                "a|1.0|1 ;; a|2.0|1 ;; a|2.0|2 ;; a|4.0|3 ;; b|10.0|1 ;; b|null|2",
                "SELECT g, v, NTILE(3) OVER (PARTITION BY g ORDER BY v) AS r FROM w ORDER BY g, v");
            assertAnswer(
                "a|1.0|0.1111111 ;; a|2.0|0.2222222 ;; a|2.0|0.2222222 ;; a|4.0|0.4444444 ;; b|10.0|1.0000000 ;; "
                    + "b|null|null",
                "SELECT g, v, RATIO_TO_REPORT(v) OVER (PARTITION BY g) AS r FROM w ORDER BY g, v");
            assertAnswer(
                "a|1.0|0.0526316 ;; a|2.0|0.1052632 ;; a|2.0|0.1052632 ;; a|4.0|0.2105263 ;; b|10.0|0.5263158 ;; "
                    + "b|null|null",
                "SELECT g, v, RATIO_TO_REPORT(v) OVER () AS r FROM w ORDER BY g, v");
            assertAnswer(
                "REFUSED SQL compilation error: | Window function type [RANK] requires ORDER BY in window "
                    + "specification.",
                "SELECT RANK() OVER () FROM w");
            assertAnswer(
                "REFUSED Invalid parameter value: 0. Reason: NTILE argument must be at least 1",
                "SELECT NTILE(0) OVER (ORDER BY v) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                    + "[NTILE()], expected 1, got 0",
                "SELECT NTILE() OVER (ORDER BY v) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[RANK(1)] expected 0, got 1",
                "SELECT RANK(1) OVER (ORDER BY v) FROM w");
            assertAnswer(
                "REFUSED Numeric value 'a' is not recognized",
                "SELECT RATIO_TO_REPORT(g) OVER () FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Sliding window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: | Missing window specification for function [RANK()].",
                "SELECT RANK() FROM w");
            assertAnswer(
                "a|1.0|4 ;; a|2.0|2 ;; a|2.0|2 ;; a|4.0|1 ;; b|10.0|1 ;; b|null|2",
                "SELECT g, v, RANK() OVER (PARTITION BY g ORDER BY v DESC NULLS LAST) AS r FROM w ORDER BY g, v");
            assertAnswer(
                "REFUSED Invalid parameter value: 0. Reason: NTILE argument must be at least 1",
                "SELECT NTILE(NULL) OVER (ORDER BY v) FROM w");
        } finally {
            engine.execute(
                "DROP TABLE IF EXISTS w");
        }
    }

    @Test
    public void ntileCountsAndRatioArguments() {
        engine.execute(
            "CREATE OR REPLACE TABLE w (g VARCHAR, v NUMBER(5,1))");
        engine.execute(
            "INSERT INTO w VALUES ('a', 1), ('a', 2), ('a', 2), ('a', 4), ('b', 10), ('b', NULL)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT CUME_DIST() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 33 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT NTILE(2) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 37 | Sliding window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Sliding window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 26 | Window frame requires an ORDER BY "
                    + "clause.",
                "SELECT RATIO_TO_REPORT(v) OVER (ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM w");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|1 ;; 10.0|1 ;; null|1",
                "SELECT v, NTILE(1) OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|2 ;; 4.0|2 ;; 10.0|3 ;; null|4",
                "SELECT v, NTILE(4) OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|3 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, NTILE(10) OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|1 ;; 10.0|1 ;; null|1",
                "SELECT v, NTILE(1.4) OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE(1.5) OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|1 ;; 10.0|1 ;; null|1",
                "SELECT v, NTILE(0.5) OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "REFUSED Invalid parameter value: 0. Reason: NTILE argument must be at least 1",
                "SELECT v, NTILE(0.4) OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "REFUSED Invalid parameter value: -1. Reason: NTILE argument must be at least 1",
                "SELECT v, NTILE(-1) OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE('2') OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT v, NTILE('x') OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 10 | Invalid argument types for function "
                    + "'NTILE': (BOOLEAN)",
                "SELECT v, NTILE(TRUE) OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE(1 + 1) OVER (ORDER BY v) FROM w ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[NTILE(2, 3)] expected 1, got 2",
                "SELECT NTILE(2, 3) OVER (ORDER BY v) FROM w");
            assertAnswer(
                "1.0|0.16666666666666666 ;; 2.0|0.16666666666666666 ;; 2.0|0.16666666666666666 ;; "
                    + "4.0|0.16666666666666666 ;; 10.0|0.16666666666666666 ;; null|0.16666666666666666",
                "SELECT v, RATIO_TO_REPORT('2') OVER () FROM w ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 10 | Invalid argument types for function "
                    + "'SUM': (BOOLEAN)",
                "SELECT v, RATIO_TO_REPORT(TRUE) OVER () FROM w ORDER BY v");
            assertAnswer(
                "1.0|null ;; 2.0|null ;; 2.0|null ;; 4.0|null ;; 10.0|null ;; null|null",
                "SELECT v, RATIO_TO_REPORT(v * 0) OVER () FROM w ORDER BY v");
            assertAnswer(
                "1.0|null ;; 2.0|null ;; 2.0|null ;; 4.0|null ;; 10.0|null ;; null|null",
                "SELECT v, RATIO_TO_REPORT(NULL) OVER () FROM w ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                    + "[SUM()], expected 1, got 0",
                "SELECT RATIO_TO_REPORT() OVER () FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[SUM(W.V, 1)] expected 1, got 2",
                "SELECT RATIO_TO_REPORT(v, 1) OVER () FROM w");
            assertAnswer(
                "a|0.4736842 ;; b|0.5263158",
                "SELECT g, RATIO_TO_REPORT(SUM(v)) OVER () FROM w GROUP BY g ORDER BY g");
            assertAnswer(
                "REFUSED SQL compilation error: | Window function type [CUME_DIST] requires ORDER BY in window "
                    + "specification.",
                "SELECT CUME_DIST() OVER () FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: | Window function type [PERCENT_RANK] requires ORDER BY in window "
                    + "specification.",
                "SELECT PERCENT_RANK() OVER () FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: | Window function type [DENSE_RANK] requires ORDER BY in window "
                    + "specification.",
                "SELECT DENSE_RANK() OVER () FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: | Window function type [NTILE] requires ORDER BY in window "
                    + "specification.",
                "SELECT NTILE(2) OVER () FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[CUME_DIST(1)] expected 0, got 1",
                "SELECT CUME_DIST(1) OVER (ORDER BY v) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: | Missing window specification for function [DENSE_RANK()].",
                "SELECT DENSE_RANK() FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: | Window function type [RANK] requires ORDER BY in window "
                    + "specification.",
                "SELECT RANK() OVER (PARTITION BY g) FROM w");
        } finally {
            engine.execute(
                "DROP TABLE IF EXISTS w");
        }
    }

    @Test
    public void whichFramesEachFunctionTakes() {
        engine.execute(
            "CREATE OR REPLACE TABLE w (g VARCHAR, v NUMBER(5,1))");
        engine.execute(
            "INSERT INTO w VALUES ('a', 1), ('a', 2), ('a', 2), ('a', 4), ('b', 10), ('b', NULL)");
        try {
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS "
                    + "r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, RANK() OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) AS r FROM w ORDER BY "
                    + "v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, RANK() OVER (ORDER BY v RANGE UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|3 ;; 10.0|4 ;; null|5",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|3 ;; 10.0|4 ;; null|5",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                    + "FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|3 ;; 10.0|4 ;; null|5",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|3 ;; 10.0|4 ;; null|5",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                    + "FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 23 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function DENSE_RANK",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function DENSE_RANK",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|3 ;; 10.0|4 ;; null|5",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|3 ;; 10.0|4 ;; null|5",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|3 ;; 10.0|4 ;; null|5",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function DENSE_RANK",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|3 ;; 10.0|4 ;; null|5",
                "SELECT v, DENSE_RANK() OVER (ORDER BY v RANGE UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Cumulative window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|3 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                    + "FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Cumulative window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Sliding window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Sliding window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|3 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|3 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                    + "FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Cumulative window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Sliding window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Cumulative window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Sliding window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Cumulative window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Cumulative window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Sliding window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN 0 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Sliding window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|3 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Cumulative window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|0.16666666666666666 ;; 2.0|0.5 ;; 2.0|0.5 ;; 4.0|0.6666666666666666 ;; "
                    + "10.0|0.8333333333333334 ;; null|1.0",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) "
                    + "AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Cumulative window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Sliding window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Sliding window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|0.16666666666666666 ;; 2.0|0.5 ;; 2.0|0.5 ;; 4.0|0.6666666666666666 ;; "
                    + "10.0|0.8333333333333334 ;; null|1.0",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|0.16666666666666666 ;; 2.0|0.5 ;; 2.0|0.5 ;; 4.0|0.6666666666666666 ;; "
                    + "10.0|0.8333333333333334 ;; null|1.0",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                    + "FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Cumulative window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Sliding window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Cumulative window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Sliding window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Cumulative window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Cumulative window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Sliding window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS BETWEEN 0 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Sliding window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|0.16666666666666666 ;; 2.0|0.5 ;; 2.0|0.5 ;; 4.0|0.6666666666666666 ;; "
                    + "10.0|0.8333333333333334 ;; null|1.0",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Cumulative window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|0.0 ;; 2.0|0.2 ;; 2.0|0.2 ;; 4.0|0.6 ;; 10.0|0.8 ;; null|1.0",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                    + "FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Cumulative window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Sliding window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Sliding window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|0.0 ;; 2.0|0.2 ;; 2.0|0.2 ;; 4.0|0.6 ;; 10.0|0.8 ;; null|1.0",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS "
                    + "r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|0.0 ;; 2.0|0.2 ;; 2.0|0.2 ;; 4.0|0.6 ;; 10.0|0.8 ;; null|1.0",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                    + "FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Cumulative window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS "
                    + "r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Sliding window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Cumulative window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Sliding window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Cumulative window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Cumulative window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Sliding window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS BETWEEN 0 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 | Sliding window frame unsupported "
                    + "for function PERCENT_RANK",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|0.0 ;; 2.0|0.2 ;; 2.0|0.2 ;; 4.0|0.6 ;; 10.0|0.8 ;; null|1.0",
                "SELECT v, PERCENT_RANK() OVER (ORDER BY v RANGE UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Cumulative window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS "
                    + "r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Cumulative window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) "
                    + "AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Cumulative window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Cumulative window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Cumulative window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Cumulative window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS BETWEEN 0 PRECEDING AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 51 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (PARTITION BY g ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS "
                    + "r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED Invalid parameter value: 0. Reason: NTILE argument must be at least 1",
                "SELECT v, NTILE(NULL) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|2 ;; 4.0|2 ;; 10.0|3 ;; null|3",
                "SELECT v, NTILE(2.5) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|1 ;; 10.0|1 ;; null|1",
                "SELECT v, NTILE(1e0) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED Invalid parameter value: 0. Reason: NTILE argument must be at least 1",
                "SELECT v, NTILE(-0.4) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED Invalid parameter value: -1. Reason: NTILE argument must be at least 1",
                "SELECT v, NTILE(-0.5) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE('1.5') OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED Numeric value '' is not recognized",
                "SELECT v, NTILE('') OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE(2::FLOAT) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE(TO_VARIANT(2)) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 10 | Invalid argument types for function "
                    + "'NTILE': (DATE)",
                "SELECT v, NTILE('2024-01-01'::DATE) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|3 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, NTILE(99999999999) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|2 ;; 2.0|2 ;; 2.0|2 ;; 4.0|3 ;; 10.0|3 ;; null|3",
                "SELECT v, NTILE(2) OVER (ORDER BY v) + 1 AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED Numeric value 'a' is not recognized",
                "SELECT v, RATIO_TO_REPORT('a') OVER () AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 10 | Invalid argument types for function "
                    + "'SUM': (DATE)",
                "SELECT v, RATIO_TO_REPORT('2024-01-01'::DATE) OVER () AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|0.05263157894736842 ;; 2.0|0.10526315789473684 ;; 2.0|0.10526315789473684 ;; "
                    + "4.0|0.21052631578947367 ;; 10.0|0.5263157894736842 ;; null|null",
                "SELECT v, RATIO_TO_REPORT(TO_VARIANT(v)) OVER () AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 10 | Invalid argument types for function "
                    + "'SUM': (ARRAY)",
                "SELECT v, RATIO_TO_REPORT([1]) OVER () AS r FROM w ORDER BY v, r");
        } finally {
            engine.execute(
                "DROP TABLE IF EXISTS w");
        }
    }

    @Test
    public void rangeFramesAndPrecedence() {
        engine.execute(
            "CREATE OR REPLACE TABLE w (g VARCHAR, v NUMBER(5,1))");
        engine.execute(
            "INSERT INTO w VALUES ('a', 1), ('a', 2), ('a', 2), ('a', 4), ('b', 10), ('b', NULL)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v range between 1 preceding and current row) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN 1 PRECEDING AND 1 "
                    + "FOLLOWING window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN 2 PRECEDING AND 1 "
                    + "PRECEDING window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN UNBOUNDED PRECEDING "
                    + "AND 1 FOLLOWING window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN 1 PRECEDING AND "
                    + "UNBOUNDED FOLLOWING window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN 1.5 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN 1.5 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE  BETWEEN  1  PRECEDING  AND  CURRENT  ROW) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Sliding window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS BETWEEN 2 PRECEDING AND 1 PRECEDING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|3 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v rows between unbounded preceding and unbounded "
                    + "following) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | RANGE BETWEEN CURRENT ROW AND 1 "
                    + "FOLLOWING window frame unsupported for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v range between 1 preceding and current row) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN 1 PRECEDING AND 1 "
                    + "FOLLOWING window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN 2 PRECEDING AND 1 "
                    + "PRECEDING window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN UNBOUNDED PRECEDING "
                    + "AND 1 FOLLOWING window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN 1 PRECEDING AND "
                    + "UNBOUNDED FOLLOWING window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN 1.5 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN 1.5 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE  BETWEEN  1  PRECEDING  AND  CURRENT  ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v ROWS BETWEEN 2 PRECEDING AND 1 PRECEDING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|2 ;; null|2",
                "SELECT v, NTILE(2) OVER (ORDER BY v rows between unbounded preceding and unbounded following) AS "
                    + "r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | RANGE BETWEEN CURRENT ROW AND 1 "
                    + "FOLLOWING window frame unsupported for function NTILE",
                "SELECT v, NTILE(2) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND 1 FOLLOWING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v range between 1 preceding and current row) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN 1 PRECEDING AND 1 "
                    + "FOLLOWING window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN 2 PRECEDING AND 1 "
                    + "PRECEDING window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN UNBOUNDED PRECEDING "
                    + "AND 1 FOLLOWING window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN 1 PRECEDING AND "
                    + "UNBOUNDED FOLLOWING window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN 1.5 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN 1.5 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE  BETWEEN  1  PRECEDING  AND  CURRENT  ROW) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Sliding window frame unsupported "
                    + "for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS BETWEEN 2 PRECEDING AND 1 PRECEDING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | RANGE BETWEEN CURRENT ROW AND 1 "
                    + "FOLLOWING window frame unsupported for function CUME_DIST",
                "SELECT v, CUME_DIST() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v range between 1 preceding and current row) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN 1 PRECEDING AND 1 "
                    + "FOLLOWING window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN 2 PRECEDING AND 1 "
                    + "PRECEDING window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN UNBOUNDED PRECEDING "
                    + "AND 1 FOLLOWING window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN 1 PRECEDING AND "
                    + "UNBOUNDED FOLLOWING window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN 1.5 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN 1.5 PRECEDING AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE  BETWEEN  1  PRECEDING  AND  CURRENT  ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 2 PRECEDING AND 1 PRECEDING) AS r FROM w ORDER BY "
                    + "v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
                "SELECT v, RANK() OVER (ORDER BY v rows between unbounded preceding and unbounded following) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN CURRENT ROW AND 1 "
                    + "FOLLOWING window frame unsupported for function RANK",
                "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND 1 FOLLOWING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | Sliding window frame unsupported "
                    + "for function LAG",
                "SELECT v, LAG(v) OVER (ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | Cumulative window frame unsupported "
                    + "for function LAG",
                "SELECT v, LAG(v) OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|null ;; 2.0|1.0 ;; 2.0|2.0 ;; 4.0|2.0 ;; 10.0|4.0 ;; null|10.0",
                "SELECT v, LAG(v) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|null ;; 2.0|1.0 ;; 2.0|2.0 ;; 4.0|2.0 ;; 10.0|4.0 ;; null|10.0",
                "SELECT v, LAG(v) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function LAG",
                "SELECT v, LAG(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 35 | Sliding window frame unsupported "
                    + "for function LEAD",
                "SELECT v, LEAD(v) OVER (ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 35 | Cumulative window frame unsupported "
                    + "for function LEAD",
                "SELECT v, LEAD(v) OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|2.0 ;; 2.0|2.0 ;; 2.0|4.0 ;; 4.0|10.0 ;; 10.0|null ;; null|null",
                "SELECT v, LEAD(v) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS "
                    + "r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|2.0 ;; 2.0|2.0 ;; 2.0|4.0 ;; 4.0|10.0 ;; 10.0|null ;; null|null",
                "SELECT v, LEAD(v) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 35 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function LEAD",
                "SELECT v, LEAD(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "1.0|1.0 ;; 2.0|1.0 ;; 2.0|2.0 ;; 4.0|2.0 ;; 10.0|4.0 ;; null|10.0",
                "SELECT v, FIRST_VALUE(v) OVER (ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1.0 ;; 2.0|1.0 ;; 2.0|1.0 ;; 4.0|1.0 ;; 10.0|1.0 ;; null|1.0",
                "SELECT v, FIRST_VALUE(v) OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1.0 ;; 2.0|1.0 ;; 2.0|1.0 ;; 4.0|1.0 ;; 10.0|1.0 ;; null|1.0",
                "SELECT v, FIRST_VALUE(v) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                    + "FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1.0 ;; 2.0|1.0 ;; 2.0|1.0 ;; 4.0|1.0 ;; 10.0|1.0 ;; null|1.0",
                "SELECT v, FIRST_VALUE(v) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS "
                    + "r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1.0 ;; 2.0|1.0 ;; 2.0|1.0 ;; 4.0|4.0 ;; 10.0|10.0 ;; null|null",
                "SELECT v, FIRST_VALUE(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 57 | Sliding window frame unsupported "
                    + "for function CONDITIONAL_TRUE_EVENT",
                "SELECT v, CONDITIONAL_TRUE_EVENT(v > 1) OVER (ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY "
                    + "v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 57 | Cumulative window frame unsupported "
                    + "for function CONDITIONAL_TRUE_EVENT",
                "SELECT v, CONDITIONAL_TRUE_EVENT(v > 1) OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|0 ;; 2.0|1 ;; 2.0|2 ;; 4.0|3 ;; 10.0|4 ;; null|4",
                "SELECT v, CONDITIONAL_TRUE_EVENT(v > 1) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND "
                    + "UNBOUNDED FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|0 ;; 2.0|1 ;; 2.0|2 ;; 4.0|3 ;; 10.0|4 ;; null|4",
                "SELECT v, CONDITIONAL_TRUE_EVENT(v > 1) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND "
                    + "CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 57 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function CONDITIONAL_TRUE_EVENT",
                "SELECT v, CONDITIONAL_TRUE_EVENT(v > 1) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT "
                    + "ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 33 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT NTILE(0) OVER (ORDER BY v ROWS 1 PRECEDING) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT NTILE(TRUE) OVER (ORDER BY v ROWS 1 PRECEDING) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 33 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT NTILE(2) OVER (ORDER BY v ROWS 1 PRECEDING) FROM nope");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT NTILE(2) OVER (ORDER BY nope ROWS 1 PRECEDING) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 16 | Window frame requires an ORDER BY "
                    + "clause.",
                "SELECT NTILE(2) OVER (ROWS 1 PRECEDING) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 20 | Window frame requires an ORDER BY "
                    + "clause.",
                "SELECT ROW_NUMBER() OVER (PARTITION BY g ROWS 1 PRECEDING) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOPE' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOPE.",
                "SELECT NTILE(0) OVER (ORDER BY v) FROM nope");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOPE' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOPE.",
                "SELECT NTILE(TRUE) OVER (ORDER BY v) FROM nope");
            assertAnswer(
                "",
                "SELECT NTILE(0) OVER (ORDER BY v) FROM w WHERE 1 = 0");
            assertAnswer(
                "",
                "SELECT NTILE('x') OVER (ORDER BY v) FROM w WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Window function type [NTILE] requires ORDER BY in window "
                    + "specification.",
                "SELECT NTILE(0) OVER () FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Sliding window frame unsupported "
                    + "for function NTILE",
                "SELECT NTILE(2, 3) OVER (ORDER BY v ROWS 1 PRECEDING) FROM w");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Sliding window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v ROWS 1 PRECEDING) + 1 AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 54 | Sliding window frame unsupported "
                    + "for function ROW_NUMBER",
                "SELECT v FROM w QUALIFY ROW_NUMBER() OVER (ORDER BY v ROWS 1 PRECEDING) = 1");
            assertAnswer(
                "",
                "SELECT v, RATIO_TO_REPORT('a') OVER () FROM w WHERE 1 = 0");
            assertAnswer(
                "REFUSED Numeric value 'a' is not recognized",
                "SELECT v, RATIO_TO_REPORT(g) OVER () FROM w ORDER BY v");
            assertAnswer(
                "REFUSED Numeric value '' is not recognized",
                "SELECT v, RATIO_TO_REPORT('') OVER () FROM w ORDER BY v");
            assertAnswer(
                "REFUSED Numeric value 'a' is not recognized",
                "SELECT v, RATIO_TO_REPORT(IFF(v > 2, 'a', '1')) OVER () FROM w ORDER BY v");
        } finally {
            engine.execute(
                "DROP TABLE IF EXISTS w");
        }
    }

    @Test
    public void framesOverANullKey() {
        engine.execute(
            "CREATE OR REPLACE TABLE w (g VARCHAR, v NUMBER(5,1))");
        engine.execute(
            "INSERT INTO w VALUES ('a', 1), ('a', 2), ('a', 2), ('a', 4), ('b', 10), ('b', NULL), ('b', NULL)");
        try {
            assertAnswer(
                "1.0|1 ;; 2.0|3 ;; 2.0|3 ;; 4.0|1 ;; 10.0|1 ;; null|2 ;; null|2",
                "SELECT v, COUNT(*) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "1.0|1.0 ;; 2.0|5.0 ;; 2.0|5.0 ;; 4.0|4.0 ;; 10.0|10.0 ;; null|null ;; null|null",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "1.0|3 ;; 2.0|3 ;; 2.0|3 ;; 4.0|1 ;; 10.0|1 ;; null|2 ;; null|2",
                "SELECT v, COUNT(*) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "1.0|3 ;; 2.0|2 ;; 2.0|2 ;; 4.0|1 ;; 10.0|1 ;; null|2 ;; null|2",
                "SELECT v, COUNT(*) OVER (ORDER BY v DESC RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|3 ;; 2.0|3 ;; 4.0|1 ;; 10.0|1 ;; null|2 ;; null|2",
                "SELECT v, COUNT(*) OVER (ORDER BY v NULLS FIRST RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r "
                    + "FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|3 ;; 2.0|3 ;; 2.0|3 ;; 4.0|1 ;; 10.0|1 ;; null|2 ;; null|2",
                "SELECT v, COUNT(*) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND 2 FOLLOWING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "1.0|null ;; 2.0|1.0 ;; 2.0|1.0 ;; 4.0|2.0 ;; 10.0|null ;; null|null ;; null|null",
                "SELECT v, MAX(v) OVER (ORDER BY v RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "1.0|0 ;; 2.0|1 ;; 2.0|1 ;; 4.0|3 ;; 10.0|4 ;; null|7 ;; null|7",
                "SELECT v, COUNT(*) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "1.0|1.0 ;; 2.0|1.0 ;; 2.0|1.0 ;; 4.0|4.0 ;; 10.0|10.0 ;; null|null ;; null|null",
                "SELECT v, FIRST_VALUE(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1.0 ;; 2.0|2.0 ;; 2.0|2.0 ;; 4.0|4.0 ;; 10.0|10.0 ;; null|null ;; null|null",
                "SELECT v, LAST_VALUE(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|3 ;; 2.0|3 ;; 4.0|1 ;; 10.0|1 ;; null|0 ;; null|0",
                "SELECT v, COUNT(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w ORDER "
                    + "BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 55 | Sliding window frame unsupported "
                    + "for function CONDITIONAL_TRUE_EVENT",
                "SELECT v, CONDITIONAL_CHANGE_EVENT(v) OVER (ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY v, "
                    + "r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 55 | Cumulative window frame unsupported "
                    + "for function CONDITIONAL_TRUE_EVENT",
                "SELECT v, CONDITIONAL_CHANGE_EVENT(v) OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 55 | RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW window frame unsupported for function CONDITIONAL_TRUE_EVENT",
                "SELECT v, CONDITIONAL_CHANGE_EVENT(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT "
                    + "ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|0 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|3 ;; null|3 ;; null|3",
                "SELECT v, CONDITIONAL_CHANGE_EVENT(v) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND "
                    + "CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|0 ;; 2.0|1 ;; 2.0|1 ;; 4.0|2 ;; 10.0|3 ;; null|3 ;; null|3",
                "SELECT v, CONDITIONAL_CHANGE_EVENT(v) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND "
                    + "UNBOUNDED FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 57 | Cumulative window frame unsupported "
                    + "for function CONDITIONAL_TRUE_EVENT",
                "SELECT v, CONDITIONAL_TRUE_EVENT(v > 1) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND UNBOUNDED "
                    + "FOLLOWING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1.0 ;; 2.0|1.0 ;; 2.0|2.0 ;; 4.0|2.0 ;; 10.0|4.0 ;; null|10.0 ;; null|null",
                "SELECT v, NTH_VALUE(v, 1) OVER (ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Sliding window frame unsupported "
                    + "for function LAG",
                "SELECT v, LAG(v, 1, 0) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 35 | Cumulative window frame unsupported "
                    + "for function LEAD",
                "SELECT v, LEAD(v) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r FROM "
                    + "w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function LAG",
                "SELECT v, LAG(v) OVER (ORDER BY v RANGE CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|3 ;; 4.0|4 ;; 10.0|1 ;; null|2 ;; null|3",
                "SELECT v, ROW_NUMBER() OVER (PARTITION BY g ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND "
                    + "CURRENT ROW) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 31 | RANGE BETWEEN CURRENT ROW AND "
                    + "CURRENT ROW window frame unsupported for function RANK",
                "SELECT v FROM w QUALIFY RANK() OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND UNBOUNDED "
                    + "FOLLOWING) = 1");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 38 | RANGE BETWEEN 1 PRECEDING AND 1 "
                    + "FOLLOWING window frame unsupported for function RANK",
                "SELECT v, 1 + RANK() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r FROM w "
                    + "ORDER BY v, r");
            assertAnswer(
                "1.0|3 ;; 2.0|2 ;; 2.0|3 ;; 4.0|2 ;; 10.0|1 ;; null|1 ;; null|1",
                "SELECT v, NTILE(3) OVER (ORDER BY v DESC RANGE UNBOUNDED PRECEDING) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|1 ;; 4.0|1 ;; 10.0|2 ;; null|2 ;; null|2",
                "SELECT v, NTILE(1.5::FLOAT) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|2 ;; 4.0|2 ;; 10.0|3 ;; null|3 ;; null|4",
                "SELECT v, NTILE(3.5) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|3 ;; 4.0|4 ;; 10.0|5 ;; null|6 ;; null|7",
                "SELECT v, NTILE(7) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|2 ;; 2.0|3 ;; 4.0|4 ;; 10.0|5 ;; null|6 ;; null|7",
                "SELECT v, NTILE(8) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
            assertAnswer(
                "1.0|1 ;; 2.0|1 ;; 2.0|2 ;; 4.0|2 ;; 10.0|3 ;; null|4 ;; null|5",
                "SELECT v, NTILE(5) OVER (ORDER BY v) AS r FROM w ORDER BY v, r");
        } finally {
            engine.execute(
                "DROP TABLE IF EXISTS w");
        }
    }
}
