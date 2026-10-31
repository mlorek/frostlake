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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The lines after a comma in a MATCH_RECOGNIZE pattern's own bracket - a second comma, a bracket holding one, a
 * reserved word before the bracket's close - and after a comma opening the pattern's own bracket or one inside
 * it (all live-verified).
 */
public class MatchRecognizeCommaLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void commaLines() {
        engine.execute("CREATE OR REPLACE TABLE s681 (id INT, v INT)");
        engine.execute("INSERT INTO s681 VALUES (1, 10), (2, 12)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ','. | syntax "
                + "error line 1 at position 62 unexpected ')'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN ((, lo)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ','. | syntax "
                + "error line 1 at position 61 unexpected ','.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN ((, lo, hi)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ','. | syntax "
                + "error line 1 at position 63 unexpected 'hi'. | syntax error line 1 at position 65 unexpected ')'. |"
                + " syntax error line 1 at position 74 unexpected 'lo'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN ((, lo) hi) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ','. | syntax "
                + "error line 1 at position 65 unexpected ')'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN ((, lo hi)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','. | syntax "
                + "error line 1 at position 66 unexpected ')'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (lo, hi, lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','. | syntax "
                + "error line 1 at position 70 unexpected ')'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (lo, hi, lo, hi) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','. | syntax "
                + "error line 1 at position 72 unexpected 'lo'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (lo, any) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (lo, any hi) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (lo, hi+) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (lo, hi lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','. | syntax "
                + "error line 1 at position 67 unexpected ')'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (lo, (hi, lo)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (lo,) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 60 unexpected ')'. | syntax error line 1 at position 69 unexpected 'lo'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (, lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ','. | syntax "
                + "error line 1 at position 63 unexpected ')'. | syntax error line 1 at position 68 unexpected ')'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, hi), lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','. | syntax "
                + "error line 1 at position 64 unexpected 'any'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (lo, hi, any) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'any'.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (any, lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','.",
            "SELECT * FROM s681 MATCH_RECOGNIZE(ORDER BY id PATTERN (lo, hi) lo DEFINE lo AS v < 13)");
    }

    @Test
    public void aPermuteOfAReservedWordOverTwoDefinitions() {
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 178 unexpected ','. | syntax "
                + "error line 1 at position 182 unexpected ','. | syntax error line 1 at position 184 unexpected "
                + "'any'. | syntax error line 1 at position 188 unexpected ')'.",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (PERMUTE(lo, hi, "
                + "any)) DEFINE lo AS v < 13, hi AS v >= 13)");
    }
}
