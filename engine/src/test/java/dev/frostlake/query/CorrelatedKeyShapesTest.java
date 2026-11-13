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
 * Correlated subqueries the engine answers from inner rows computed once and keyed by the correlating equality, as
 * the subquery run for each outer row answers them (live-verified): an IN, a NOT IN and a scalar aggregate beside a
 * residual condition, keys compared under a column's collation, an explicit COLLATE and two incompatible collations,
 * a VARIANT key against a number, and a text key against a number. A residual's column computed over the inner rows
 * raises its fault under EXISTS, IN and a select item, whatever the row's key, unless the outer WHERE bounds the key
 * with constants; under NOT EXISTS it is computed over the keys the outer table holds and the fault is never met.
 */
public class CorrelatedKeyShapesTest extends MeasuredCellsTestSupport {

    private void tables() {
        engine.execute("CREATE OR REPLACE TABLE ka (id INT, v INT, w INT, pat VARCHAR, s VARCHAR)");
        engine.execute("INSERT INTO ka VALUES (1, 5, 1, 'a%', '3'), (2, 10, 2, '%z', '20'), (3, NULL, 1, NULL, "
            + "'x'), (4, 0, 2, 'b_', '0'), (5, 7, NULL, '%', NULL), (NULL, 1, 1, 'a%', '1')");
        engine.execute("CREATE OR REPLACE TABLE kb (k INT, j INT, j2 INT, name VARCHAR, s VARCHAR)");
        engine.execute("INSERT INTO kb VALUES (1, 6, 1, 'abc', '4'), (1, 2, 1, 'xyz', '2'), (2, 10, 2, 'qz', "
            + "'25'), (2, 11, 3, 'zz', '5'), (3, 1, 1, 'bb', '9'), (4, NULL, 2, 'bc', NULL), (4, -1, 2, 'b1', "
            + "'1'), (NULL, 100, 1, 'a', '100'), (6, 1, 1, 'q', '1'), (2, NULL, 2, 'n', NULL)");
        engine.execute("CREATE OR REPLACE TABLE kz (k INT, j INT, z INT)");
        engine.execute("INSERT INTO kz VALUES (1, 6, 1), (2, 12, 2), (6, 1, 0), (7, 3, 0)");
        engine.execute("CREATE OR REPLACE TABLE kz2 (k INT, j INT, z INT)");
        engine.execute("INSERT INTO kz2 VALUES (1, 6, 1), (1, 3, 0), (2, 12, 2)");
        engine.execute("CREATE OR REPLACE TABLE ko (id VARCHAR, v INT)");
        engine.execute("INSERT INTO ko VALUES ('a', 1), ('B', 2), ('c ', 3), ('D', 0), (NULL, 5)");
        engine.execute("CREATE OR REPLACE TABLE kci (k VARCHAR COLLATE 'en-ci', j INT)");
        engine.execute("INSERT INTO kci VALUES ('A', 5), ('b', 1), ('C', 9), ('d', 2), ('a', 0)");
        engine.execute("CREATE OR REPLACE TABLE kcs (k VARCHAR COLLATE 'en-cs', j INT)");
        engine.execute("INSERT INTO kcs VALUES ('A', 5), ('b', 1)");
        engine.execute("CREATE OR REPLACE TABLE koc (id VARCHAR COLLATE 'en-ci', v INT)");
        engine.execute("INSERT INTO koc VALUES ('a', 1), ('B', 2), ('x', 3)");
        engine.execute("CREATE OR REPLACE TABLE kv (k VARIANT, j INT)");
        engine.execute("INSERT INTO kv SELECT PARSE_JSON('1'), 5 UNION ALL SELECT TO_VARIANT('2'), 6 UNION ALL "
            + "SELECT PARSE_JSON('3.0'), 7 UNION ALL SELECT TO_VARIANT('a'), 8");
    }

    @Test
    public void keyedShapesAnswerAsTheSubqueryRunForEachRow() {
        tables();
        assertAnswer("1",
            "SELECT a.id FROM ka a WHERE a.w IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND b.j > a.v) ORDER BY 1");
        assertAnswer("2 ;; 3 ;; 4 ;; 5 ;; null",
            "SELECT a.id FROM ka a WHERE a.w NOT IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND b.j > a.v) ORDER "
                + "BY 1");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false ;; null|false",
            "SELECT a.id, a.v IN (SELECT b.j FROM kb b WHERE b.k = a.id AND b.j2 >= a.w) FROM ka a ORDER BY 1");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; null|false",
            "SELECT a.id, a.w IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER BY 1");
        assertAnswer("1|true ;; 2|false ;; 3|null ;; 4|null ;; 5|true ;; null|true",
            "SELECT a.id, a.v NOT IN (SELECT b.j FROM kb b WHERE b.k = a.id AND b.j2 >= a.w) FROM ka a ORDER BY 1");
        assertAnswer("1|6 ;; 2|11 ;; 3|1 ;; 4|-1 ;; 5|null ;; null|null",
            "SELECT a.id, (SELECT MAX(b.j) FROM kb b WHERE b.k = a.id AND b.j2 >= a.w) FROM ka a ORDER BY 1");
        assertAnswer("1|1 ;; 2|1 ;; 3|0 ;; 4|0 ;; 5|0 ;; null|0",
            "SELECT a.id, (SELECT COUNT(*) FROM kb b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER BY 1");
        assertAnswer("1|8 ;; 2|10 ;; 3|1 ;; 4|-1 ;; 5|null ;; null|null",
            "SELECT a.id, (SELECT SUM(b.j) FROM kb b WHERE b.k = a.id AND b.j2 <= a.w) FROM ka a ORDER BY 1");
        assertAnswer("1|1 ;; 2|2 ;; 3|0 ;; 4|1 ;; 5|0 ;; null|0",
            "SELECT a.id, (SELECT COUNT(b.j) FROM kb b WHERE b.k = a.id AND b.name LIKE a.pat) FROM ka a ORDER "
                + "BY 1");
        assertAnswer("1 ;; 2",
            "SELECT a.id FROM ka a WHERE a.v < (SELECT MAX(b.j) FROM kb b WHERE b.k = a.id AND b.j2 >= a.w) "
                + "ORDER BY 1");
        assertAnswer("1|4.000000 ;; 2|11.000000 ;; 3|null ;; 4|-1.000000 ;; 5|null ;; null|null",
            "SELECT a.id, (SELECT AVG(b.j) FROM kb b WHERE b.k = a.id AND b.j <> a.v) FROM ka a ORDER BY 1");
        assertAnswer("B ;; D ;; a",
            "SELECT o.id FROM ko o WHERE EXISTS (SELECT 1 FROM kci c WHERE c.k = o.id) ORDER BY 1");
        assertAnswer("D ;; a",
            "SELECT o.id FROM ko o WHERE EXISTS (SELECT 1 FROM kci c WHERE c.k = o.id AND c.j > o.v) ORDER BY 1");
        assertAnswer("",
            "SELECT o.id FROM ko o WHERE o.v IN (SELECT c.j FROM kci c WHERE c.k = o.id) ORDER BY 1");
        assertAnswer("B|1 ;; D|2 ;; a|5 ;; c |null ;; null|null",
            "SELECT o.id, (SELECT MAX(c.j) FROM kci c WHERE c.k = o.id) FROM ko o ORDER BY 1");
        assertAnswer("a",
            "SELECT o.id FROM koc o WHERE EXISTS (SELECT 1 FROM kb b WHERE b.name = o.id) ORDER BY 1");
        assertAnswer("a ;; B",
            "SELECT o.id FROM koc o WHERE EXISTS (SELECT 1 FROM kci c WHERE c.k = o.id) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: Incompatible collations: 'en-ci' and 'en-cs'",
            "SELECT o.id FROM koc o WHERE EXISTS (SELECT 1 FROM kcs c WHERE c.k = o.id) ORDER BY 1");
        assertAnswer("a",
            "SELECT o.id FROM ko o WHERE EXISTS (SELECT 1 FROM kci c WHERE c.k = o.id COLLATE 'en-cs') ORDER BY 1");
        assertAnswer("1 ;; 3",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kv WHERE kv.k = a.id) ORDER BY 1");
        assertAnswer("",
            "SELECT a.id FROM ka a WHERE a.v IN (SELECT kv.j - 1 FROM kv WHERE kv.k = a.id) ORDER BY 1");
        assertAnswer("1|5 ;; 2|null ;; 3|7 ;; 4|null ;; 5|null ;; null|null",
            "SELECT a.id, (SELECT MAX(kv.j) FROM kv WHERE kv.k = a.id) FROM ka a ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'x' is not recognized",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kb b WHERE b.k = a.s) ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) ORDER "
                + "BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.j / b.z > a.v AND b.k = a.id) ORDER "
                + "BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE a.v > 0 AND EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > "
                + "a.v) ORDER BY 1");
        assertAnswer("1",
            "SELECT a.id FROM ka a WHERE a.id < 3 AND EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z "
                + "> a.v) ORDER BY 1");
        assertAnswer("1",
            "SELECT a.id FROM ka a WHERE a.id IN (1, 2) AND EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j "
                + "/ b.z > a.v) ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id, EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a ORDER BY 1");
        assertAnswer("2 ;; 3 ;; 4 ;; 5 ;; null",
            "SELECT a.id FROM ka a WHERE NOT EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) "
                + "ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) OR a.v "
                + "IS NULL ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz2 b WHERE b.k = a.id AND b.j / b.z > a.v) ORDER "
                + "BY 1");
        assertAnswer(unsupportedAt(28),
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz2 b WHERE b.k = a.id AND b.j > a.v + 10 / b.z) "
                + "ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND 10 / b.z > 0) ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j > a.v AND 10 / b.z "
                + "> 0) ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE a.w IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) "
                + "ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id, (SELECT MAX(b.j) FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT a.id FROM ka a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j > a.v) AND EXISTS "
                + "(SELECT 1 FROM kz c WHERE c.k = a.id AND c.j / c.z > a.v) ORDER BY 1");
    }
}
