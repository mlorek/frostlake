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
 * A frame no rows can make is refused when the statement compiles, ahead of every other rule of its window: a
 * start past its end, an UNBOUNDED FOLLOWING start or UNBOUNDED PRECEDING end, and a ROWS offset that is no
 * whole number are "Invalid window frame" at the bound that offends; two intervals the wrong way round are an
 * invalid interval frame range. AVG over any frame but the running one declares three more decimals than its
 * argument (live-verified).
 */
public class WindowFrameValidityTest extends MeasuredCellsTestSupport {

    @Test
    public void impossibleFramesAreRefused() {
        try {
            assertAnswer(
                "Table EV successfully created.",
                "CREATE OR REPLACE TABLE ev (v INT, d DATE, n NUMBER(5,1))");
            assertAnswer(
                "3",
                "INSERT INTO ev VALUES (1, '2024-01-01', 1.0), (2, '2024-01-02', 2.5), (4, '2024-01-04', 3.0)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 63 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND 1.5 FOLLOWING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 71 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND INTERVAL '1 day' "
                    + "FOLLOWING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1.0 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1e0 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS UNBOUNDED FOLLOWING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED FOLLOWING AND CURRENT ROW) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 63 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND UNBOUNDED PRECEDING) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND 2 PRECEDING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "1|null ;; 2|null ;; 4|1",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 2 PRECEDING AND 2 PRECEDING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "1|2 ;; 2|4 ;; 4|null",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 1 FOLLOWING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND CURRENT ROW) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND 2 PRECEDING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "1|1 ;; 2|2 ;; 4|4",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS CURRENT ROW) AS s FROM ev ORDER BY v");
            assertAnswer(
                "1|1 ;; 2|2 ;; 4|4",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 0 PRECEDING AND 0 FOLLOWING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 0 FOLLOWING AND 0 PRECEDING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "1|6 ;; 2|4 ;; 4|null",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND UNBOUNDED FOLLOWING) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 48 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND 2 PRECEDING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 48 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN 2 FOLLOWING AND 1 FOLLOWING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 48 | Invalid interval frame: 'RANGE "
                    + "BETWEEN INTERVAL '1 day' PRECEDING AND INTERVAL '2 days' PRECEDING' has an invalid interval "
                    + "frame range.",
                "SELECT v, SUM(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '1 day' PRECEDING AND INTERVAL '2 days' "
                    + "PRECEDING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE UNBOUNDED FOLLOWING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "1|2 ;; 2|null ;; 4|null",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN 1 FOLLOWING AND 1 FOLLOWING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "1|null ;; 2|1 ;; 4|2",
                "SELECT v, SUM(v) OVER (ORDER BY n RANGE BETWEEN 1.5 PRECEDING AND 0.5 PRECEDING) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 48 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY n RANGE BETWEEN 0.5 PRECEDING AND 1.5 PRECEDING) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "1|1 ;; 2|3 ;; 4|6",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE 2 PRECEDING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 2 at position 39 | Invalid window frame",
                "SELECT v,\n  SUM(v) OVER (ORDER BY v ROWS BETWEEN 2 FOLLOWING AND 1 FOLLOWING) AS s FROM ev");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 48 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN 1 FOLLOWING AND 1 PRECEDING) AS s FROM ev WHERE "
                    + "1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 51 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v, v RANGE BETWEEN 1 FOLLOWING AND 1 PRECEDING) AS s FROM ev");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 50 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY 'a' RANGE BETWEEN 1 FOLLOWING AND 1 PRECEDING) AS s FROM ev");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ROWS BETWEEN 2 FOLLOWING AND 1 FOLLOWING) AS s FROM ev");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 2 FOLLOWING AND 1 FOLLOWING) AS s FROM ev WHERE 1 "
                    + "= 0");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1.5 PRECEDING AND CURRENT ROW) AS s FROM ev WHERE "
                    + "1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: | ORDER BY clause is invalid: The ORDER BY clause for window "
                    + "frame 'RANGE BETWEEN 1 PRECEDING AND CURRENT ROW' must have a numeric, date, or timestamp "
                    + "expression.",
                "SELECT v, SUM(v) OVER (ORDER BY 'a' RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 51 | Window frame 'RANGE BETWEEN 1 "
                    + "PRECEDING AND CURRENT ROW' allows only one ORDER BY expression.",
                "SELECT v, SUM(v) OVER (ORDER BY v, v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid RANGE BETWEEN frame 'RANGE BETWEEN INTERVAL '1 day' "
                    + "PRECEDING AND CURRENT ROW': numeric ORDER BY clause only allows numeric window frame "
                    + "boundaries.",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN INTERVAL '1 day' PRECEDING AND CURRENT ROW) AS s "
                    + "FROM ev WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 48 | Invalid interval frame: 'INTERVAL "
                    + "'-1 day' PRECEDING' has an invalid interval frame range.",
                "SELECT v, SUM(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '-1 day' PRECEDING AND CURRENT ROW) AS "
                    + "s FROM ev WHERE 1 = 0");
        } finally {
            answer(
                "DROP TABLE IF EXISTS ev");
        }
    }

    @Test
    public void averageWidthFollowsTheFrame() {
        try {
            assertAnswer(
                "Table AV successfully created.",
                "CREATE OR REPLACE TABLE av (v INT, n NUMBER(10,2))");
            assertAnswer(
                "3",
                "INSERT INTO av VALUES (1, 1.25), (2, 2.5), (4, 4.75)");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(v) OVER () AS a FROM av");
            assertShape(
                "A:NUMBER(38,6)",
                "SELECT AVG(v) OVER (ORDER BY v) AS a FROM av");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(v) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM av");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM av");
            assertShape(
                "A:NUMBER(25,5)",
                "SELECT AVG(n) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM av");
            assertShape(
                "A:NUMBER(38,6)",
                "SELECT AVG(v) AS a FROM av");
            assertAnswer(
                "1.000 ;; 1.500 ;; 3.000",
                "SELECT AVG(v) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM av ORDER BY v");
            assertAnswer(
                "1.000000 ;; 1.500000 ;; 2.333333",
                "SELECT AVG(v) OVER (ORDER BY v) AS a FROM av ORDER BY v");
            assertAnswer(
                "1.25000 ;; 1.87500 ;; 3.62500",
                "SELECT AVG(n) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM av ORDER BY v");
            assertAnswer(
                "1.000 ;; 3.000 ;; 3.000",
                "SELECT AVG(v) OVER (PARTITION BY v > 1) AS a FROM av ORDER BY v");
            assertAnswer(
                "Table EV successfully created.",
                "CREATE OR REPLACE TABLE ev (v INT, n NUMBER(10,2), m NUMBER(5,1), d DATE, f FLOAT)");
            assertAnswer(
                "3",
                "INSERT INTO ev VALUES (1, 1.25, 1.0, '2024-01-01', 1.5), (2, 2.5, 2.5, '2024-01-02', 2.0), (4, "
                    + "4.75, 3.0, '2024-01-04', 4.25)");
            assertShape(
                "A:NUMBER(25,5)",
                "SELECT AVG(n) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM ev");
            assertShape(
                "A:NUMBER(20,4)",
                "SELECT AVG(m) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM ev");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(v) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND 1 FOLLOWING) AS a FROM ev");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(v) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS a FROM ev");
            assertShape(
                "A:NUMBER(38,6)",
                "SELECT AVG(v) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS a FROM ev");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '1 day' PRECEDING AND CURRENT ROW) AS a "
                    + "FROM ev");
            assertShape(
                "A:NUMBER(22,2)",
                "SELECT SUM(n) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM ev");
            assertShape(
                "A:NUMBER(25,5)",
                "SELECT AVG(n) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM ev");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(v) OVER (ORDER BY v RANGE 1 PRECEDING) AS a FROM ev");
            assertShape(
                "A:NUMBER(38,13)",
                "SELECT AVG(v::NUMBER(38,10)) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS a "
                    + "FROM ev");
            assertAnswer(
                "1|1.25000 ;; 2|1.87500 ;; 4|4.75000",
                "SELECT v, AVG(n) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "1|1.0000 ;; 2|1.7500 ;; 4|3.0000",
                "SELECT v, AVG(m) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "1|1.500 ;; 2|2.000 ;; 4|4.000",
                "SELECT v, AVG(v) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND 1 FOLLOWING) AS a FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "1|1.000 ;; 2|1.500 ;; 4|4.000",
                "SELECT v, AVG(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '1 day' PRECEDING AND CURRENT ROW) AS a "
                    + "FROM ev ORDER BY v");
            assertAnswer(
                "1|1.0000000000000 ;; 2|1.5000000000000 ;; 4|4.0000000000000",
                "SELECT v, AVG(v::NUMBER(38,10)) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS a "
                    + "FROM ev ORDER BY v");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(v) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND CURRENT ROW) AS a FROM ev");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(v) OVER (ORDER BY v RANGE BETWEEN 0 PRECEDING AND CURRENT ROW) AS a FROM ev");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(v) OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS a "
                    + "FROM ev");
        } finally {
            answer(
                "DROP TABLE IF EXISTS av");
            answer(
                "DROP TABLE IF EXISTS ev");
        }
    }
}
