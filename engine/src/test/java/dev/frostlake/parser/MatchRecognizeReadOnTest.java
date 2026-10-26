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
 * The lines after the first fault of a MATCH_RECOGNIZE clause where one of its sub-clauses is due: a word read as
 * a definition's symbol and its value, a reserved word read past to the next word, an AS, a bracket or an ORDER
 * before the PATTERN read on as the query, a measure without its alias, and a PATTERN without its bracket (all
 * live-verified).
 */
public class MatchRecognizeReadOnTest extends MeasuredCellsTestSupport {

    @Test
    public void clauseShapes() {
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected ','. | syntax "
                + "error line 1 at position 82 unexpected 'ROW'. | syntax error line 1 at position 90 unexpected "
                + "'MATCH'. | syntax error line 1 at position 96 unexpected 'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER(), COUNT(*) ONE ROW PER MATCH PATTERN "
                + "(a b+) DEFINE b AS v > LAG(v))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'AS'. | syntax "
                + "error line 1 at position 71 unexpected 'MEASURES'.",
            "SELECT * FROM s MATCH_RECOGNIZE(PARTITION BY id % 2 AS odd ORDER BY id MEASURES COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 92 unexpected 'SHOW'. | syntax "
                + "error line 1 at position 97 unexpected 'EMPTY'. | syntax error line 1 at position 103 unexpected "
                + "'MATCHES'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ONE ROW PER MATCH SHOW EMPTY "
                + "MATCHES PATTERN (a+) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 103 unexpected 'ONE'. | syntax "
                + "error line 1 at position 107 unexpected 'ROW'. | syntax error line 1 at position 111 unexpected "
                + "'PER'. | syntax error line 1 at position 115 unexpected 'MATCH'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn AFTER MATCH SKIP TO NEXT ROW "
                + "ONE ROW PER MATCH PATTERN (a a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'MEASURES'. | "
                + "syntax error line 1 at position 71 unexpected 'MATCH_NUMBER'. | syntax error line 1 at position 86 "
                + "unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ONE ROW PER MATCH MEASURES MATCH_NUMBER() AS mn PATTERN (a "
                + "a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'PARTITION'. | "
                + "syntax error line 1 at position 54 unexpected 'BY'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PARTITION BY v MEASURES MATCH_NUMBER() AS mn PATTERN (a) "
                + "DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 86 unexpected 'PATTERN'. | "
                + "syntax error line 1 at position 94 unexpected '('. | syntax error line 1 at position 98 unexpected "
                + "'DEFINE'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (a) PATTERN (a) "
                + "DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'a'. | syntax "
                + "error line 1 at position 93 unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN a DEFINE a AS TRUE)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void readOnShapes() {
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'MEASURES'. | "
                + "syntax error line 1 at position 71 unexpected 'x'. | syntax error line 1 at position 78 unexpected "
                + "'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ONE ROW PER MATCH MEASURES x AS y PATTERN (a) DEFINE a AS "
                + "TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'MEASURES'. | "
                + "syntax error line 1 at position 76 unexpected 'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ONE ROW PER MATCH MEASURES AS y PATTERN (a) DEFINE a AS "
                + "TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'ALL'. | syntax "
                + "error line 1 at position 88 unexpected 'MATCH'. | syntax error line 1 at position 94 unexpected "
                + "'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id AFTER MATCH SKIP PAST LAST ROW ALL ROWS PER MATCH PATTERN "
                + "(a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 105 unexpected 'ONE'. | syntax "
                + "error line 1 at position 109 unexpected 'ROW'. | syntax error line 1 at position 113 unexpected "
                + "'PER'. | syntax error line 1 at position 117 unexpected 'MATCH'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn AFTER MATCH SKIP PAST LAST "
                + "ROW ONE ROW PER MATCH PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'AFTER'. | "
                + "syntax error line 1 at position 62 unexpected 'MATCH'. | syntax error line 1 at position 68 "
                + "unexpected 'SKIP'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (a) AFTER MATCH SKIP PAST LAST ROW DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'MEASURES'. | "
                + "syntax error line 1 at position 65 unexpected 'x'. | syntax error line 1 at position 72 unexpected "
                + "'DEFINE'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (a) MEASURES x AS y DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'PARTITION'. | "
                + "syntax error line 1 at position 54 unexpected 'BY'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PARTITION BY v PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'SHOW'. | syntax "
                + "error line 1 at position 67 unexpected 'EMPTY'. | syntax error line 1 at position 73 unexpected "
                + "'MATCHES'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ONE ROW PER MATCH SHOW EMPTY MATCHES PATTERN (a) DEFINE a "
                + "AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'OMIT'. | syntax "
                + "error line 1 at position 67 unexpected 'EMPTY'. | syntax error line 1 at position 73 unexpected "
                + "'MATCHES'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ONE ROW PER MATCH OMIT EMPTY MATCHES PATTERN (a) DEFINE a "
                + "AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'WITH'. | syntax "
                + "error line 1 at position 77 unexpected 'ROWS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ONE ROW PER MATCH WITH UNMATCHED ROWS PATTERN (a) DEFINE a "
                + "AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'zz'. | syntax "
                + "error line 1 at position 65 unexpected 'yy'. | syntax error line 1 at position 68 unexpected "
                + "'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ONE ROW PER MATCH zz yy PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'zz'. | syntax "
                + "error line 1 at position 73 unexpected 'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ONE ROW PER MATCH zz AS TRUE PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'a'. | syntax "
                + "error line 1 at position 65 unexpected 'AS'. | syntax error line 1 at position 83 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN a b DEFINE a AS TRUE, b AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'a'. | syntax "
                + "error line 1 at position 64 unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN a+ DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '^'. | syntax "
                + "error line 1 at position 65 unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN ^ a DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 16 | SELECT with no columns",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (PERMUTE(lo, hi)) DEFINE lo AS v < 13, hi AS v >= "
                + "13)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(PARTITION BY v AS odd MEASURES MATCH_NUMBER() AS mn PATTERN (a) DEFINE "
                + "a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'odd'. | syntax "
                + "error line 1 at position 51 unexpected 'ORDER'. | syntax error line 1 at position 63 unexpected "
                + "'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(PARTITION BY v odd ORDER BY id PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id AS o MEASURES MATCH_NUMBER() AS mn PATTERN (a) DEFINE a AS "
                + "TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected ','.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER(), COUNT(*) AS c ONE ROW PER MATCH "
                + "PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected ','. | syntax "
                + "error line 1 at position 78 unexpected 'ALL'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER(), COUNT(*) ALL ROWS PER MATCH "
                + "PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected ','. | syntax "
                + "error line 1 at position 86 unexpected '('.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER(), COUNT(*) PATTERN (a) DEFINE a AS "
                + "TRUE)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 16 | SELECT with no columns",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (a) DEFINE a AS TRUE) PATTERN");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 16 | SELECT with no columns",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (a) DEFINE a AS TRUE) MEASURES");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 16 | SELECT with no columns",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (a) DEFINE a AS TRUE) DEFINE");
        assertAnswer("",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ALL ROWS PER MATCH PATTERN (a) DEFINE a AS TRUE) m (x, y)");
        assertAnswer("",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ALL ROWS PER MATCH PATTERN (a) DEFINE a AS TRUE) AS PATTERN");
        assertAnswer("",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ALL ROWS PER MATCH PATTERN (a) DEFINE a AS TRUE) PARTITION");
        assertAnswer("",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ALL ROWS PER MATCH PATTERN (a) DEFINE a AS TRUE) ONE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected ','. | syntax "
                + "error line 1 at position 82 unexpected 'ROW'. | syntax error line 1 at position 90 unexpected "
                + "'MATCH'. | syntax error line 1 at position 96 unexpected 'AFTER'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER(), COUNT(*) ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'MEASURES'. | "
                + "syntax error line 1 at position 71 unexpected 'x'. | syntax error line 1 at position 76 unexpected "
                + "'PATTERN'. | syntax error line 1 at position 88 unexpected 'DEFINE'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ONE ROW PER MATCH MEASURES x, y PATTERN (a) DEFINE a AS "
                + "TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'PATTERN'. | "
                + "syntax error line 1 at position 64 unexpected 'a'. | syntax error line 1 at position 66 unexpected "
                + "'DEFINE'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (a) PATTERN a DEFINE a AS TRUE)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void moreReadOnShapes() {
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES FINAL v AS f ALL ROWS PER MATCH PATTERN (strt up+) "
                + "DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'DEFINE'.",
            "SELECT * FROM s  MATCH_RECOGNIZE(ORDER BY id MEASURES 1 AS m DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'DEFINE'. | "
                + "syntax error line 1 at position 91 unexpected 'PATTERN'. | syntax error line 1 at position 102 "
                + "unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn DEFINE a AS TRUE PATTERN (a))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'MEASURES'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (a) DEFINE a AS TRUE MEASURES MATCH_NUMBER() AS mn)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'ORDER'.",
            "SELECT * FROM s MATCH_RECOGNIZE(MEASURES MATCH_NUMBER() AS mn ORDER BY id PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'PATTERN'.",
            "SELECT * FROM s ORDER BY id PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'MEASURES'.",
            "SELECT * FROM s ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'ORDER'.",
            "SELECT * FROM s MATCH_RECOGNIZE(MEASURES MATCH_NUMBER() AS mn ORDER BY id zz yy PATTERN (a) DEFINE a AS "
                + "TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'ORDER'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ORDER BY id PATTERN (a) "
                + "DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 81 unexpected 'ORDER'.",
            "SELECT * FROM s MATCH_RECOGNIZE(MEASURES MATCH_NUMBER() AS mn ALL ROWS PER MATCH ORDER BY id PATTERN "
                + "(a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'ORDER'. | "
                + "syntax error line 1 at position 89 unexpected 'DEFINE'.",
            "SELECT * FROM s MATCH_RECOGNIZE(MEASURES MATCH_NUMBER() AS mn ORDER BY id, v PATTERN (a) DEFINE a AS "
                + "TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'ORDER'.",
            "SELECT * FROM s MATCH_RECOGNIZE(MEASURES MATCH_NUMBER() AS mn ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'ORDER'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ONE ROW PER MATCH ORDER BY id PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'ORDER'. | "
                + "syntax error line 1 at position 67 unexpected 'DEFINE'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (a) ORDER BY v DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'ORDER'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id AFTER MATCH SKIP PAST LAST ROW ORDER BY v PATTERN (a) "
                + "DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'PARTITION'. | "
                + "syntax error line 1 at position 72 unexpected 'BY'.",
            "SELECT * FROM s MATCH_RECOGNIZE(MEASURES MATCH_NUMBER() AS mn PARTITION BY v PATTERN (a) DEFINE a AS "
                + "TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'ORDER'.",
            "SELECT * FROM s MATCH_RECOGNIZE(MEASURES MATCH_NUMBER() AS mn ORDER zz PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'ALL'. | syntax "
                + "error line 1 at position 87 unexpected 'MATCH'. | syntax error line 1 at position 93 unexpected "
                + "'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id AFTER MATCH SKIP PAST LAST ROW ALL ROWS zz MATCH PATTERN "
                + "(a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'ALL'. | syntax "
                + "error line 1 at position 88 unexpected 'MATCH'. | syntax error line 1 at position 94 unexpected "
                + "'SHOW'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id AFTER MATCH SKIP PAST LAST ROW ALL ROWS PER MATCH SHOW "
                + "EMPTY MATCHES PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES FINAL v AS f g h PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'WITH'. | syntax "
                + "error line 1 at position 82 unexpected 'ROWS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn WITH zz ROWS PATTERN (a) "
                + "DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'WITH'. | syntax "
                + "error line 1 at position 89 unexpected 'ROWS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn WITH UNMATCHED ROWS PATTERN "
                + "(a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'ROW'. | syntax "
                + "error line 1 at position 82 unexpected 'MATCH'. | syntax error line 1 at position 88 unexpected "
                + "'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ROW PER MATCH PATTERN (a) "
                + "DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'ROW'. | syntax "
                + "error line 1 at position 81 unexpected 'MATCH'. | syntax error line 1 at position 87 unexpected "
                + "'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ROW zz MATCH PATTERN (a) "
                + "DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'FROM'. | syntax "
                + "error line 1 at position 82 unexpected 'yy'. | syntax error line 1 at position 85 unexpected "
                + "'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn FROM zz yy PATTERN (a) DEFINE "
                + "a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn AS zz yy PATTERN (a) DEFINE a "
                + "AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected 'zz'. | syntax "
                + "error line 1 at position 84 unexpected 'PATTERN'. | syntax error line 1 at position 96 unexpected "
                + "'DEFINE'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ALL zz yy PATTERN (a) DEFINE "
                + "a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected 'zz'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ALL ROWS PER zz PATTERN (a) "
                + "DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected 'ALL'. | syntax "
                + "error line 1 at position 107 unexpected 'MATCH'. | syntax error line 1 at position 113 unexpected "
                + "'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ALL ROWS PER MATCH AFTER MATCH SKIP PAST LAST ROW ALL ROWS "
                + "PER MATCH PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'zz'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn zz PATTERN (a) DEFINE a AS "
                + "TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'AS'. | syntax "
                + "error line 1 at position 74 unexpected 'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(PARTITION BY v AS odd ORDER BY id LIMIT 1 PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'AS'. | syntax "
                + "error line 1 at position 60 unexpected 'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id AS o ORDER BY v PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'AS'. | syntax "
                + "error line 1 at position 66 unexpected 'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(MEASURES FINAL v AS f ORDER BY id PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'AS'. | syntax "
                + "error line 1 at position 66 unexpected 'PATTERN'.",
            "SELECT * FROM s MATCH_RECOGNIZE(PARTITION BY v AS odd WHERE v > 1 PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'.",
            "SELECT * FROM s, b AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'a'. | syntax "
                + "error line 1 at position 63 unexpected 'AS'. | syntax error line 1 at position 82 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN a DEFINE a AS TRUE, b AS FALSE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'a'. | syntax "
                + "error line 1 at position 63 unexpected 'AS'. | syntax error line 1 at position 93 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN a DEFINE a AS TRUE, b AS FALSE, c AS TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'a'. | syntax "
                + "error line 1 at position 63 unexpected 'AS'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN a DEFINE a AS v > 1 AND v < 3)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
