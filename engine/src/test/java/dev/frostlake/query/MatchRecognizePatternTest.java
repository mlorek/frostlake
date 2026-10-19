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
 * How a row pattern matches. A quantifier takes as many rows as it can, or as few when reluctant; a sequence
 * binds tighter than the bar, and of an alternation's parts the first that leads to a match wins whatever its
 * length; {@code ^} and {@code $} hold a match to the partition's first and last row; PERMUTE stands for its
 * parts in every order; and {@code {- … -}} matches rows the match does not answer. A symbol is named without
 * regard to case, and one with no definition reads every row. Every answer is live-verified.
 */
public class MatchRecognizePatternTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        engine.execute("INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
    }

    @Test
    public void quantifiersGreedyAndReluctant() {
        assertAnswer(
            "2|1|1|HI ;; 3|1|2|HI ;; 5|2|1|HI ;; 6|2|2|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (hi+) DEFINE lo "
                + "AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "2|1|1|HI ;; 3|2|1|HI ;; 5|3|1|HI ;; 6|4|1|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (hi+?) DEFINE "
                + "lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|2|1|HI ;; 3|2|2|HI ;; 4|2|3|LO ;; 5|3|1|HI ;; 6|3|2|HI ;; 7|3|3|LO ;; 8|4|1|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (hi* lo) DEFINE "
                + "lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|2|1|HI ;; 3|2|2|HI ;; 4|2|3|LO ;; 5|3|1|HI ;; 6|3|2|HI ;; 7|3|3|LO ;; 8|4|1|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (hi*? lo) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI ;; 4|2|1|LO ;; 5|2|2|HI ;; 7|3|1|LO ;; 8|4|1|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo hi?) DEFINE "
                + "lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 4|2|1|LO ;; 7|3|1|LO ;; 8|4|1|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo hi??) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "2|1|1|HI ;; 3|1|2|HI ;; 5|2|1|HI ;; 6|2|2|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (hi{2}) DEFINE "
                + "lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "2|1|1|HI ;; 3|1|2|HI ;; 5|2|1|HI ;; 6|2|2|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (hi{2,}) DEFINE "
                + "lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI ;; 4|2|1|LO ;; 5|2|2|HI ;; 7|3|1|LO ;; 8|4|1|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo hi{,1}) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "2|1|1|HI ;; 3|1|2|HI ;; 5|2|1|HI ;; 6|2|2|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (hi{1,2}) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "2|1|1|HI ;; 3|2|1|HI ;; 5|3|1|HI ;; 6|4|1|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (hi{1,2}?) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI ;; 4|2|1|LO ;; 5|2|2|HI ;; 7|3|1|LO ;; 8|3|2|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN ((lo hi?)+) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|8",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a{0} b) DEFINE a AS TRUE) ORDER BY 1, 2");
        assertAnswer(
            "1|1 ;; 2|1 ;; 3|1 ;; 4|1 ;; 5|1 ;; 6|1 ;; 7|1 ;; 8|1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a{2,1}) DEFINE a AS TRUE) ORDER BY 1, 2");
    }

    @Test
    public void alternationAndGrouping() {
        assertAnswer(
            "1|1|1|LO ;; 2|2|1|HI ;; 3|2|2|HI ;; 4|3|1|LO ;; 5|4|1|HI ;; 6|4|2|HI ;; 7|5|1|LO ;; 8|6|1|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo | hi hi) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "2|1|1|HI ;; 3|2|1|HI ;; 5|3|1|HI ;; 6|4|1|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (hi | hi hi) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI ;; 3|2|1|HI ;; 4|2|2|LO ;; 6|3|1|HI ;; 7|3|2|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo hi | hi lo) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "2|1|1|HI ;; 3|2|1|HI ;; 5|3|1|HI ;; 6|4|1|HI ;; 7|4|2|LO ;; 8|4|3|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo lo | hi lo "
                + "lo | hi) DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI ;; 4|2|1|LO ;; 5|2|2|HI ;; 7|3|1|LO ;; 8|3|2|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo hi | lo lo) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI ;; 4|2|1|LO ;; 5|2|2|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN ((lo hi)+) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "3|1|1|HI ;; 4|1|2|LO ;; 6|2|1|HI ;; 7|2|2|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN ((lo | hi) lo) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
    }

    @Test
    public void anchorsAndPermutations() {
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (^ lo hi) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (^ hi) DEFINE "
                + "lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "6|1|1|HI ;; 7|1|2|LO ;; 8|1|3|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (hi lo lo $) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "8|1|1|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo $) DEFINE "
                + "lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI ;; 3|2|1|HI ;; 4|2|2|LO ;; 6|3|1|HI ;; 7|3|2|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (PERMUTE(lo, "
                + "hi)) DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI ;; 3|1|3|HI ;; 4|1|4|LO",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (PERMUTE(lo, "
                + "hi){2}) DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
    }

    @Test
    public void exclusions() {
        assertAnswer(
            "2|1|1|HI ;; 3|1|2|HI ;; 5|2|1|HI ;; 6|2|2|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN ({- lo -} hi+) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN ({- hi -}) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 3|1|2|HI ;; 4|2|1|LO ;; 6|2|2|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo {- hi -} "
                + "hi) DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|3|1|3 ;; 2|3|4|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c, "
                + "FIRST(id) AS f, LAST(id) AS l PATTERN ({- lo -} hi+) DEFINE lo AS v < 13, hi AS v >= 13) "
                + "ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error:  | Exclusion syntax ({-...-}) not available with activated WITH "
                + "UNMATCHED ROWS option.",
            "SELECT id FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ALL ROWS PER MATCH "
                + "WITH UNMATCHED ROWS PATTERN ({- lo -} hi+) DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY 1");
        assertAnswer(
            "4|12|1|1|1|3|6 ;; 6|31|1|2|3|3|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, COUNT(*) AS c, FINAL COUNT(*) AS fc, FINAL LAST(id) AS li ALL "
                + "ROWS PER MATCH PATTERN (strt {- up -} up) DEFINE up AS v > LAG(v)) ORDER BY 1, 3, 4");
        assertAnswer(
            "1|null|UP|2|2 ;; 2|null|UP|6|6 ;; 3|null|UP|8|8",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl, LAST(id) AS li, id AS bare PATTERN (strt "
                + "{- up+ -}) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
    }

    @Test
    public void symbols() {
        assertAnswer(
            "2|1|1|X ;; 3|1|2|X ;; 4|1|3|X ;; 5|1|4|X ;; 6|1|5|X",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (x+) DEFINE x "
                + "AS v > 10, y AS v > 10) ORDER BY mn, msq, id");
        assertAnswer(
            "",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo hi lo) "
                + "DEFINE lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI ;; 4|2|1|LO ;; 5|2|2|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (LO Hi) DEFINE "
                + "lo AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "1|1|1|LO ;; 2|1|2|HI ;; 4|2|1|LO ;; 5|2|2|HI",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (\"lo\" hi) "
                + "DEFINE \"lo\" AS v < 13, hi AS v >= 13) ORDER BY mn, msq, id");
        assertAnswer(
            "B",
            "SELECT * FROM fz MATCH_RECOGNIZE(ORDER BY id MEASURES CLASSIFIER() AS c PATTERN (a b) DEFINE a "
                + "AS id = 5) ORDER BY 1");
        assertAnswer(
            "1 ;; 1",
            "SELECT * FROM fz MATCH_RECOGNIZE(ORDER BY id MEASURES 1 AS m PATTERN (a) DEFINE a AS TRUE, zz AS "
                + "FALSE)");
        assertAnswer(
            "1|2|8|3|3",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l, COUNT(dn.*) AS downs, COUNT(up.*) AS ups AFTER MATCH SKIP TO LAST up PATTERN "
                + "(strt dn+ up+ dn+ up+) DEFINE dn AS v < LAG(v), up AS v > LAG(v)) ORDER BY 1");
        assertAnswer(
            "1|1|5",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a b* c) DEFINE a AS v < 15, b AS v BETWEEN 12 AND 40 AND v <> 30, c AS "
                + "CASE WHEN v IN (30, 31) THEN TRUE ELSE FALSE END) ORDER BY 1");
    }

    @Test
    public void aClauseMissingAPartIsASyntaxError() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected ')'.",
            "SELECT * FROM fz MATCH_RECOGNIZE(ORDER BY id MEASURES 1 AS m PATTERN (a))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 100 unexpected '}'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a{}) DEFINE a AS TRUE) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 100 unexpected '*'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+*) DEFINE a AS TRUE) ORDER BY 1, 2");
    }
}
