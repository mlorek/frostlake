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
 * A comma in a MATCH_RECOGNIZE pattern's bracket that is no PERMUTE list, or a reserved word opening a bracket, is
 * the first line; in a bracket inside the pattern's own, live reads on as if the clause's definitions began there:
 * the word after the comma a definition's symbol, a token where its AS is due reported, a ')' taken as the
 * clause's closing parenthesis, then the relation's alias or another relation (all live-verified).
 */
public class MatchRecognizePatternCommaTest extends MeasuredCellsTestSupport {

    @Test
    public void commasAndReservedWords() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 60 unexpected ')'. | syntax error line 1 at position 64 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, hi) lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ','. | syntax "
                + "error line 1 at position 62 unexpected ')'. | syntax error line 1 at position 63 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (((lo, hi))) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 60 unexpected ')'. | syntax error line 1 at position 61 unexpected '|'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, hi)|lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 60 unexpected ')'. | syntax error line 1 at position 61 unexpected '+'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, hi)+) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 61 unexpected 'lo'. | syntax error line 1 at position 64 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, hi lo)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 60 unexpected ','. | syntax error line 1 at position 64 unexpected ')'. | "
                + "syntax error line 1 at position 65 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, hi, lo)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 60 unexpected ')'. | syntax error line 1 at position 65 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, hi), lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo,)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ','. | syntax "
                + "error line 1 at position 63 unexpected ')'. | syntax error line 1 at position 64 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (lo (lo, hi)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected ','.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (lo, (hi)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ','.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo), hi) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ','. | syntax "
                + "error line 1 at position 62 unexpected ')'. | syntax error line 1 at position 66 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (zz(lo, hi) lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 60 unexpected ')'. | syntax error line 1 at position 62 unexpected '('.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, hi) (lo)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 65 unexpected ')'. | syntax error line 1 at position 74 unexpected 'lo'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, any) lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, 1)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected ','. | syntax "
                + "error line 1 at position 65 unexpected ')'. | syntax error line 1 at position 66 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo | hi, lo)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ','. | syntax "
                + "error line 1 at position 62 unexpected ')'. | syntax error line 1 at position 66 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (((lo, hi)) lo) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 61 unexpected 'lo'. | syntax error line 1 at position 67 unexpected ')'. |"
                + " syntax error line 1 at position 68 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, hi lo, lo)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ','. | syntax "
                + "error line 1 at position 63 unexpected ')'. | syntax error line 1 at position 64 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo hi, lo)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax "
                + "error line 1 at position 60 unexpected ')'. | syntax error line 1 at position 61 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, hi)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((lo, any)) DEFINE lo AS v < 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'any'. | syntax "
                + "error line 1 at position 61 unexpected ')'. | syntax error line 1 at position 62 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ((any, lo)) DEFINE lo AS v < 13)");
    }
}
