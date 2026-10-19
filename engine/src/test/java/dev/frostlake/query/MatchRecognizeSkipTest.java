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
 * Where the next match is looked for. AFTER MATCH SKIP PAST LAST ROW, the default, starts after the match;
 * TO NEXT ROW starts at its second row, so matches overlap; TO FIRST or TO LAST of a symbol starts at that
 * symbol's row. A symbol some path of the pattern does without is refused as the statement compiles, and one
 * that names the match's own first row is refused as it runs. Every answer is live-verified.
 */
public class MatchRecognizeSkipTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        engine.execute("INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
    }

    @Test
    public void theRowTheNextMatchStartsAt() {
        assertAnswer(
            "1|1|3 ;; 2|2|4 ;; 3|3|5 ;; 4|4|6 ;; 5|5|7 ;; 6|6|8",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l AFTER MATCH SKIP TO NEXT ROW PATTERN (a a a) DEFINE a AS v > 0) ORDER BY mn");
        assertAnswer(
            "1|1|3 ;; 2|4|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l AFTER MATCH SKIP PAST LAST ROW PATTERN (a a a) DEFINE a AS v > 0) ORDER BY mn");
        assertAnswer(
            "1|1|3 ;; 2|2|4 ;; 3|3|5 ;; 4|4|6 ;; 5|5|7 ;; 6|6|8",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l AFTER MATCH SKIP TO FIRST b PATTERN (a b b) DEFINE a AS v > 0, b AS v > 0) "
                + "ORDER BY mn");
        assertAnswer(
            "1|1|3 ;; 2|3|5 ;; 3|5|7",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l AFTER MATCH SKIP TO LAST b PATTERN (a b b) DEFINE a AS v > 0, b AS v > 0) ORDER "
                + "BY mn");
        assertAnswer(
            "1|1|3 ;; 2|3|5 ;; 3|5|7",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l AFTER MATCH SKIP TO b PATTERN (a b b) DEFINE a AS v > 0, b AS v > 0) ORDER BY mn");
        assertAnswer(
            "1|1|null ;; 2|2|UP ;; 3|3|null ;; 4|4|null ;; 5|5|UP ;; 6|5|UP ;; 6|6|UP ;; 7|7|null ;; 8|8|UP",
            "SELECT id, mn, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, CLASSIFIER() "
                + "AS cl ALL ROWS PER MATCH AFTER MATCH SKIP TO NEXT ROW PATTERN (up*) DEFINE up AS v > LAG(v)) "
                + "ORDER BY mn, id");
    }

    @Test
    public void aSymbolTheSkipCannotUseIsRefused() {
        assertAnswer(
            "REFUSED AFTER MATCH SKIP TO variable 'A' points to the beginning of the current match, which "
                + "will cause an infinite loop",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn AFTER MATCH SKIP TO "
                + "FIRST a PATTERN (a b) DEFINE a AS v > 0, b AS v > 0) ORDER BY mn");
        assertAnswer(
            "REFUSED SQL compilation error: | Not all paths of the pattern generate at least one match of the "
                + "AFTER MATCH SKIP pattern variable 'C'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn AFTER MATCH SKIP TO c "
                + "PATTERN (a b | a c) DEFINE a AS v > 0, b AS v > 0, c AS v < 0) ORDER BY mn");
        assertAnswer(
            "REFUSED SQL compilation error: | Not all paths of the pattern generate at least one match of the "
                + "AFTER MATCH SKIP pattern variable 'ZZ'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn AFTER MATCH SKIP TO zz "
                + "PATTERN (a b) DEFINE a AS v > 0, b AS v > 0) ORDER BY mn");
        assertAnswer(
            "REFUSED SQL compilation error: | Not all paths of the pattern generate at least one match of the "
                + "AFTER MATCH SKIP pattern variable 'B'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn AFTER MATCH SKIP TO "
                + "LAST b PATTERN (a b*) DEFINE a AS TRUE, b AS TRUE) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED AFTER MATCH SKIP TO variable 'A' points to the beginning of the current match, which "
                + "will cause an infinite loop",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f AFTER "
                + "MATCH SKIP TO LAST a PATTERN (a) DEFINE a AS TRUE) ORDER BY 1, 2");
    }
}
