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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Which rows a match answers. A pattern that can match no row answers an empty match where it does: one row
 * under ALL ROWS PER MATCH, numbered like any match, whose measures count nothing and read NULL while its own
 * columns stand, and one row of NULLs and zero counts under ONE ROW PER MATCH. OMIT EMPTY MATCHES leaves the
 * row out and keeps the number; WITH UNMATCHED ROWS adds every row no match holds, its measures NULL. A NULL
 * PARTITION BY value is a partition of its own. Every answer is live-verified.
 */
public class MatchRecognizeRowsPerMatchTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE n (id INT, k VARCHAR, v INT)");
        engine.execute("INSERT INTO n VALUES (1, 'x', NULL), (2, 'x', 3), (3, 'y', 3), (NULL, 'y', 4), (5, 'x', NULL)");
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        engine.execute("INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
        engine.execute("CREATE OR REPLACE TABLE t (g VARCHAR, id INT, v INT, w FLOAT, d DATE, x VARCHAR)");
        engine.execute("""
            INSERT INTO t VALUES ('a', 1, 1, 1.5, '2024-01-01', 'p'), ('a', 2, 2, 2.5, '2024-01-02', 'q'),
            ('a', 3, NULL, NULL, NULL, NULL), ('a', 4, 4, 0.5, '2024-01-04', 'r'), ('b', 1, 9, 9.0, '2024-02-01', 's'),
            ('b', 2, 8, 8.0, '2024-02-02', 't'), (NULL, 1, 7, 7.0, '2024-03-01', 'u')""");
        engine.execute("CREATE OR REPLACE VIEW sv AS SELECT * FROM s WHERE id <= 4");
    }

    @Test
    public void anEmptyMatchAnswersOneRow() {
        assertAnswer(
            "1|10|1|null|0 ;; 2|20|2|UP|1 ;; 3|15|3|null|0 ;; 4|12|4|null|0 ;; 5|30|5|UP|1 ;; 6|31|5|UP|2 ;; "
                + "7|5|6|null|0 ;; 8|6|7|UP|1",
            "SELECT id, v, mn, cl, c FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "CLASSIFIER() AS cl, COUNT(*) AS c ALL ROWS PER MATCH PATTERN (up*) DEFINE up AS v > LAG(v)) "
                + "ORDER BY id");
        assertAnswer(
            "2|UP|1|2 ;; 5|UP|2|5 ;; 7|UP|1|8 ;; null|null|0|null ;; null|null|0|null ;; null|null|0|null ;; "
                + "null|null|0|null",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, CLASSIFIER() AS cl, "
                + "COUNT(*) AS c, FIRST(id) AS f PATTERN (up*) DEFINE up AS v > LAG(v)) ORDER BY mn");
        assertAnswer(
            "1|1|null ;; 2|2|UP ;; 3|3|null ;; 4|4|null ;; 5|5|UP ;; 6|5|UP ;; 7|6|null ;; 8|7|UP",
            "SELECT id, mn, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, CLASSIFIER() "
                + "AS cl ALL ROWS PER MATCH SHOW EMPTY MATCHES PATTERN (up*) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|10|1|1|1|null|0|null ;; 2|20|2|1|2|20|1|20 ;; 3|15|3|1|3|null|0|null ;; 4|12|4|1|4|null|0|null "
                + ";; 5|30|5|1|5|30|2|30 ;; 6|31|5|2|6|30|2|61 ;; 7|5|6|1|7|null|0|null ;; 8|6|7|1|8|6|1|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, id AS i, FIRST(v) AS f, FINAL COUNT(*) AS fc, SUM(v) AS sm "
                + "ALL ROWS PER MATCH PATTERN (up*) DEFINE up AS v > LAG(v)) ORDER BY 1, 3, 4");
        assertAnswer(
            "1|10|1|1|null ;; 2|20|2|1|null ;; 3|15|3|1|null ;; 4|12|4|1|null ;; 5|30|5|1|null ;; "
                + "6|31|6|1|null ;; 7|5|7|1|null ;; 8|6|8|1|null",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (up*?) DEFINE "
                + "up AS v > LAG(v)) ORDER BY 1, 3, 4");
        assertAnswer(
            "2|1|20|2|1|20 ;; 5|2|31|6|2|61 ;; 7|1|6|8|1|6 ;; null|null|null|null|0|null ;; "
                + "null|null|null|null|0|null ;; null|null|null|null|0|null ;; null|null|null|null|0|null",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, v AS bare, id + 0 AS e, COUNT(v) AS cv, SUM(v) AS sv PATTERN "
                + "(up*) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "a|null|0|null ;; a|null|0|null ;; a|null|0|null ;; a|null|0|null ;; b|1|2|2 ;; null|1|1|1",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "COUNT(*) AS c, LAST(id) AS li PATTERN (hi*) DEFINE hi AS v > 5) ORDER BY 1, 2, 3, 4");
    }

    @Test
    public void unmatchedRowsWhenAskedFor() {
        assertAnswer(
            "1|1|null ;; 2|2|UP ;; 3|3|null ;; 4|4|null ;; 5|5|UP ;; 6|5|UP ;; 7|6|null ;; 8|7|UP",
            "SELECT id, mn, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, CLASSIFIER() "
                + "AS cl ALL ROWS PER MATCH WITH UNMATCHED ROWS PATTERN (up*) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|null|null ;; 2|1|UP ;; 3|null|null ;; 4|null|null ;; 5|2|UP ;; 6|2|UP ;; 7|null|null ;; 8|3|UP",
            "SELECT id, mn, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, CLASSIFIER() "
                + "AS cl ALL ROWS PER MATCH WITH UNMATCHED ROWS PATTERN (up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "a|1|1|1|N ;; a|2|2|1|N ;; a|3|null|null|null ;; a|4|4|2|N ;; b|1|9|1|N ;; b|2|8|1|N ;; null|1|7|1|N",
            "SELECT g, id, v, mn, cl FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES "
                + "MATCH_NUMBER() AS mn, CLASSIFIER() AS cl ALL ROWS PER MATCH WITH UNMATCHED ROWS PATTERN (n+) "
                + "DEFINE n AS v IS NOT NULL AND NOT (v > 100)) ORDER BY g, id");
    }

    @Test
    public void partitions() {
        assertAnswer(
            "a|1|1|1|LO|1 ;; a|2|2|1|LO|2 ;; a|4|4|2|LO|1 ;; b|1|9|1|HI|1 ;; b|2|8|1|HI|2 ;; null|1|7|1|HI|1",
            "SELECT g, id, v, mn, cl, c FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES "
                + "MATCH_NUMBER() AS mn, CLASSIFIER() AS cl, COUNT(*) AS c ALL ROWS PER MATCH PATTERN (lo+ | "
                + "hi+) DEFINE lo AS v <= 5, hi AS v > 5) ORDER BY g, id");
        assertAnswer(
            "3|2 ;; 4|1 ;; null|2",
            "SELECT * FROM n MATCH_RECOGNIZE(PARTITION BY v ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) "
                + "DEFINE a AS TRUE) ORDER BY 1");
        assertAnswer(
            "1|2|UP ;; 2|3|UP ;; 3|2|UP",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl PATTERN (strt up+) DEFINE up AS v > "
                + "LAG(v)) ORDER BY 1, 2");
    }
}
