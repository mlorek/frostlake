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
 * EXISTS over a subquery that reads the outer row through an equality and through other conjuncts as well
 * (live-verified): {@code EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j > a.v)} answers as the subquery run for
 * each outer row does, whether the residual compares, ranges, matches a pattern, reads the outer row alone or
 * computes over both rows, under NOT EXISTS and in a select item alike. The inner rows are kept once by key and each
 * outer row tests the residual over the rows of its own key. A fault in a residual's column is raised under EXISTS
 * over any inner row, and met under NOT EXISTS, or beside an outer filter on the key, only over the keys it reads.
 */
public class CorrelatedResidualExistsTest extends MeasuredCellsTestSupport {

    private void tables() {
        engine.execute("CREATE OR REPLACE TABLE ka (id INT, v INT, w INT, pat VARCHAR, s VARCHAR)");
        engine.execute("INSERT INTO ka VALUES (1, 5, 1, 'a%', '3'), (2, 10, 2, '%z', '20'), (3, NULL, 1, NULL, "
            + "'x'), (4, 0, 2, 'b_', '0'), (5, 7, NULL, '%', NULL), (NULL, 1, 1, 'a%', '1')");
        engine.execute("CREATE OR REPLACE TABLE kb (k INT, j INT, j2 INT, name VARCHAR, s VARCHAR, c VARCHAR "
            + "COLLATE 'en-ci')");
        engine.execute("INSERT INTO kb VALUES (1, 6, 1, 'abc', '4', 'A'), (1, 2, 1, 'xyz', '2', 'b'), (2, 10, 2, "
            + "'qz', '25', 'B'), (2, 11, 3, 'zz', '5', 'c'), (3, 1, 1, 'bb', '9', 'a'), (4, NULL, 2, 'bc', NULL, "
            + "'D'), (4, -1, 2, 'b1', '1', 'd'), (NULL, 100, 1, 'a', '100', 'a'), (6, 1, 1, 'q', '1', 'q')");
        engine.execute("CREATE OR REPLACE TABLE kz (k INT, j INT, z INT)");
        engine.execute("INSERT INTO kz VALUES (1, 6, 1), (2, 12, 2), (6, 1, 0), (7, 3, 0)");
    }

    @Test
    public void anEqualityBesideAResidualAnswersAsTheSubqueryRunForEachRow() {
        tables();
        assertAnswer("1 ;; 2",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j > a.v) ORDER BY 1");
        assertAnswer("3 ;; 4 ;; 5 ;; null",
            "SELECT a.id FROM ka a WHERE NOT EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j > a.v) ORDER BY "
                + "1");
        assertAnswer("1 ;; 2",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j BETWEEN a.v AND a.v "
                + "+ 2) ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND a.v > 1 AND b.j >= a.v) "
                + "ORDER BY 1");
        assertAnswer("4",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE a.id = b.k AND b.j + b.k > a.v * 2) "
                + "ORDER BY 1");
        assertAnswer("2 ;; 4",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.s > a.v) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.name LIKE a.pat) "
                + "ORDER BY 1");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false ;; null|false",
            "SELECT a.id, EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j < a.v) FROM ka a ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j <> 0 AND b.j <> "
                + "a.v) ORDER BY 1");
        assertAnswer("1",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j2 = a.w AND b.j > "
                + "a.v) ORDER BY 1");
        assertAnswer(unsupportedAt(28),
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND ABS(b.j - a.v) < 2) "
                + "ORDER BY 1");
        assertAnswer("",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.c = a.pat) ORDER BY 1");
        assertAnswer(unsupportedAt(28),
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND CASE WHEN a.v > 5 THEN "
                + "b.j > 0 ELSE b.j < 0 END) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3 ;; 4",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND (b.j > a.v OR b.j2 = "
                + "a.w)) ORDER BY 1");
        assertAnswer("",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND a.s = b.s) ORDER BY 1");
        assertAnswer("2",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j > a.v AND b.j2 > "
                + "a.w) ORDER BY 1");
        assertAnswer(unsupportedAt(28),
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND 10 / (b.j - a.v) > 0) "
                + "ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j > a.v AND 10 / b.j2 "
                + "> 0) ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT b.j FROM kb b WHERE b.k = a.id AND b.j > a.v) ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b JOIN kb b2 ON b2.k = b.k WHERE b.k = a.id "
                + "AND b2.j > a.v) ORDER BY 1");
        assertAnswer("2",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j > (a.v)) AND a.id > "
                + "1 ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.j > a.v + 0.5) ORDER "
                + "BY 1");
        assertAnswer("REFUSED Numeric value 'x' is not recognized",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.id AND b.s::INT > a.s::INT) "
                + "ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j > a.v AND 10 / b.z "
                + "> 0) ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) ORDER "
                + "BY 1");
        assertAnswer(unsupportedAt(28),
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j > a.v / b.z) ORDER "
                + "BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j > 10 / (a.v - 5)) "
                + "ORDER BY 1");
        assertAnswer("2 ;; 3 ;; 4 ;; 5 ;; null",
            "SELECT a.id FROM ka a WHERE NOT EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) "
                + "ORDER BY 1");
        assertAnswer("1",
            "SELECT a.id FROM ka a WHERE a.id < 3 AND EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z "
                + "> a.v) ORDER BY 1");
    }
}
