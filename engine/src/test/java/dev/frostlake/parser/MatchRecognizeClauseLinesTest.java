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
 * The lines of a malformed MATCH_RECOGNIZE clause: with every sub-clause in its place the first fault alone, the
 * clause read on to its end; a sub-clause written out of its place refused at its keyword, each later one out of
 * place again once, and a PATTERN left last followed by the clause's ')' (all live-verified).
 */
public class MatchRecognizeClauseLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void clauseLines() {
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        engine.execute("INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES FINAL v AS f ALL ROWS PER MATCH PATTERN (strt up+) "
                + "DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'DEFINE'.",
            "SELECT * FROM s  MATCH_RECOGNIZE(ORDER BY id MEASURES 1 AS m DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 170 unexpected 'any'.",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (^ any+ $) DEFINE "
                + "lo AS v < 13, hi AS v >= 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 172 unexpected ')'.",
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo () hi) DEFINE "
                + "lo AS v < 13, hi AS v >= 13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'DEFINE'. | "
                + "syntax error line 1 at position 91 unexpected 'PATTERN'. | syntax error line 1 at position 102 "
                + "unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn DEFINE a AS TRUE PATTERN (a))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'MEASURES'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (a) DEFINE a AS TRUE MEASURES MATCH_NUMBER() AS mn)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'ORDER'.",
            "SELECT * FROM s MATCH_RECOGNIZE(MEASURES MATCH_NUMBER() AS mn ORDER BY id PATTERN (a) DEFINE a AS TRUE)");
    }
}
