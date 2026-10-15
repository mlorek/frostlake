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
 * LISTAGG(DISTINCT x, delimiter) drops repeated values in a query without GROUP BY as it does grouped, with or
 * without WITHIN GROUP, beside other aggregates and under a HAVING (live-verified).
 */
public class ListAggDistinctDelimiterTest extends MeasuredCellsTestSupport {

    @Test
    public void distinctWithADelimiter() {
        try {
            assertAnswer(
                "Table T successfully created.",
                "CREATE OR REPLACE TABLE t (g VARCHAR, h VARCHAR)");
            assertAnswer(
                "5",
                "INSERT INTO t VALUES ('a', 'p'), ('a', 'q'), ('b', 'p'), ('b', NULL), (NULL, 'p')");
            assertAnswer(
                "a,b",
                "SELECT LISTAGG(DISTINCT g, ',') FROM t");
            assertAnswer(
                "b,a",
                "SELECT LISTAGG(DISTINCT g, ',') WITHIN GROUP (ORDER BY g DESC) FROM t");
            assertAnswer(
                "ab",
                "SELECT LISTAGG(DISTINCT g) FROM t");
            assertAnswer(
                "p|a,b ;; q|a ;; null|b",
                "SELECT h, LISTAGG(DISTINCT g, ',') WITHIN GROUP (ORDER BY g) FROM t GROUP BY h ORDER BY h");
            assertAnswer(
                "[\"b\",\"a\"]",
                "SELECT ARRAY_AGG(DISTINCT g) WITHIN GROUP (ORDER BY g DESC) FROM t");
            assertAnswer(
                "a|b",
                "SELECT LISTAGG(DISTINCT g, '|') WITHIN GROUP (ORDER BY g) FROM t WHERE g IS NOT NULL");
            assertAnswer(
                "p, q",
                "SELECT LISTAGG(DISTINCT h, ', ') WITHIN GROUP (ORDER BY h) FROM t");
            assertAnswer(
                "5|a,b",
                "SELECT COUNT(*), LISTAGG(DISTINCT g, ',') WITHIN GROUP (ORDER BY g) FROM t");
            assertAnswer(
                "a,b",
                "SELECT LISTAGG(DISTINCT g, ',') WITHIN GROUP (ORDER BY g) FROM t HAVING COUNT(*) > 1");
            assertAnswer(
                "a,a,b,b",
                "SELECT LISTAGG(g, ',') WITHIN GROUP (ORDER BY g) FROM t");
            assertAnswer(
                "a,b",
                "SELECT LISTAGG(DISTINCT g, ',') WITHIN GROUP (ORDER BY g) OVER () FROM t ORDER BY 1 LIMIT 1");
            assertAnswer(
                "A,B",
                "SELECT LISTAGG(DISTINCT UPPER(g), ',') WITHIN GROUP (ORDER BY UPPER(g)) FROM t");
        } finally {
            answer(
                "DROP TABLE IF EXISTS t");
        }
    }
}
