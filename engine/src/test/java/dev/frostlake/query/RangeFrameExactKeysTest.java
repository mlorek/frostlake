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
 * A RANGE frame with a numeric offset compares its keys exactly: a NUMBER key on the frame's edge is in it, keys
 * past 2^53 neither merge nor reach past their offset, and the current row's peers are the rows of its exact
 * key. AVG over such a frame declares three more decimals than its argument, truncated (live-verified).
 */
public class RangeFrameExactKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void keysCompareExactly() {
        try {
            assertAnswer(
                "Table RR successfully created.",
                "CREATE OR REPLACE TABLE rr (o NUMBER(5,1), v INT)");
            assertAnswer(
                "5",
                "INSERT INTO rr VALUES (0.7, 8), (0.8, 16), (1.0, 1), (1.1, 2), (1.2, 4)");
            assertAnswer(
                "0.7|8|24 ;; 0.8|24|16 ;; 1.0|1|3 ;; 1.1|3|6 ;; 1.2|6|4",
                "SELECT o, SUM(v) OVER (ORDER BY o RANGE BETWEEN 0.1 PRECEDING AND CURRENT ROW) p01, SUM(v) OVER "
                    + "(ORDER BY o RANGE BETWEEN CURRENT ROW AND 0.1 FOLLOWING) f01 FROM rr ORDER BY o");
            assertAnswer(
                "0.7|8|24 ;; 0.8|24|16 ;; 1.0|1|3 ;; 1.1|3|6 ;; 1.2|6|4",
                "SELECT r1.o, (SELECT SUM(v) FROM rr r2 WHERE r2.o BETWEEN r1.o - 0.1 AND r1.o) p01, (SELECT "
                    + "SUM(v) FROM rr r2 WHERE r2.o BETWEEN r1.o AND r1.o + 0.1) f01 FROM rr r1 ORDER BY o");
            assertAnswer(
                "Table BIGK successfully created.",
                "CREATE OR REPLACE TABLE bigk (o NUMBER(38,0), v INT)");
            assertAnswer(
                "4",
                "INSERT INTO bigk VALUES (9007199254740993, 1), (9007199254740994, 2), (9007199254740995, 4), "
                    + "(9007199254740997, 8)");
            assertAnswer(
                "9007199254740993|1 ;; 9007199254740994|3 ;; 9007199254740995|6 ;; 9007199254740997|8",
                "SELECT o, SUM(v) OVER (ORDER BY o RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) p1 FROM bigk ORDER "
                    + "BY o");
            assertAnswer(
                "9007199254740993|1 ;; 9007199254740994|3 ;; 9007199254740995|6 ;; 9007199254740997|8",
                "SELECT b1.o, (SELECT SUM(v) FROM bigk b2 WHERE b2.o BETWEEN b1.o - 1 AND b1.o) p1 FROM bigk b1 "
                    + "ORDER BY o");
            assertAnswer(
                "0.7|24 ;; 0.8|16 ;; 1.0|3 ;; 1.1|6 ;; 1.2|4",
                "SELECT o, SUM(v) OVER (ORDER BY o DESC RANGE BETWEEN 0.1 PRECEDING AND CURRENT ROW) p01 FROM rr "
                    + "ORDER BY o");
            assertAnswer(
                "Table W successfully created.",
                "CREATE OR REPLACE TABLE w (g VARCHAR, v NUMBER(5,1))");
            assertAnswer(
                "6",
                "INSERT INTO w VALUES ('a', 1), ('a', 2), ('a', 2), ('a', 4), ('a', 10), ('a', NULL)");
            assertAnswer(
                "1.0|1.0000 ;; 2.0|1.6666 ;; 2.0|1.6666 ;; 4.0|4.0000 ;; 10.0|10.0000 ;; null|null",
                "SELECT v, AVG(v) OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS a FROM w ORDER "
                    + "BY v");
        } finally {
            answer(
                "DROP TABLE IF EXISTS rr");
            answer(
                "DROP TABLE IF EXISTS bigk");
            answer(
                "DROP TABLE IF EXISTS w");
        }
    }
}
