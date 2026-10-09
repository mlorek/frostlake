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
 * Where a MATCH_RECOGNIZE clause stands. It reads a table, a view, a common table expression, a subquery or a
 * table function, takes an alias before it and one after — the one after names the relation it answers, with
 * a column list when written — and the relation is a source like any other: joined, filtered, grouped, read
 * through a view or inserted. The source's own name and alias are no names of the relation, and inside the
 * clause they qualify only its PARTITION BY and ORDER BY. MATCH_RECOGNIZE is no reserved word: a table, a
 * column and an alias may carry the name. Every answer is live-verified.
 */
public class MatchRecognizeSourcesTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE em (id INT, v INT)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE n (id INT, k VARCHAR, v INT)");
        engine.execute("INSERT INTO n VALUES (1, 'x', NULL), (2, 'x', 3), (3, 'y', 3), (NULL, 'y', 4), (5, 'x', NULL)");
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        engine.execute("INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
        engine.execute("CREATE OR REPLACE VIEW sv AS SELECT * FROM s WHERE id <= 4");
    }

    @Test
    public void theWordIsAName() {
        assertAnswer(
            "1",
            "SELECT 1 AS match_recognize");
        assertAnswer(
            "Table MATCH_RECOGNIZE successfully created.",
            "CREATE OR REPLACE TABLE match_recognize (match_recognize INT)");
        assertAnswer(
            "",
            "SELECT match_recognize FROM match_recognize");
        assertAnswer(
            "5 ;; 7",
            "SELECT match_recognize.id FROM fz match_recognize ORDER BY 1");
        assertAnswer(
            "5 ;; 7",
            "SELECT match_recognize.id FROM fz AS match_recognize ORDER BY 1");
        assertAnswer(
            "1",
            "WITH match_recognize AS (SELECT 1 AS x) SELECT x FROM match_recognize");
    }

    @Test
    public void theSourcesItReads() {
        assertAnswer(
            "5",
            "WITH c AS (SELECT * FROM s WHERE id > 3) SELECT * FROM c MATCH_RECOGNIZE(ORDER BY id MEASURES "
                + "COUNT(*) AS n PATTERN (a+) DEFINE a AS TRUE)");
        assertAnswer(
            "4",
            "SELECT * FROM sv MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS n PATTERN (a+) DEFINE a AS TRUE)");
        assertAnswer(
            "5",
            "SELECT m.n FROM (SELECT * FROM s WHERE id > 3) x MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) "
                + "AS n PATTERN (a+) DEFINE a AS TRUE) m");
        assertAnswer(
            "3",
            "SELECT * FROM (SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "LAST(v) AS v, LAST(id) AS id PATTERN (strt up+) DEFINE up AS v > LAG(v))) "
                + "MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS n PATTERN (a+) DEFINE a AS TRUE)");
        assertAnswer(
            "3",
            "SELECT * FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(3, 1, 2))) MATCH_RECOGNIZE(ORDER BY value "
                + "MEASURES COUNT(*) AS n PATTERN (a+) DEFINE a AS TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 63 | invalid identifier 'X'",
            "SELECT * FROM (VALUES (1), (2)) v (x) MATCH_RECOGNIZE(ORDER BY x MEASURES COUNT(*) AS n PATTERN "
                + "(a+) DEFINE a AS TRUE)");
        assertAnswer(
            "",
            "SELECT * FROM em MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS n PATTERN (a*) DEFINE a AS TRUE)");
        assertAnswer(
            "8",
            "SELECT c FROM s AT (OFFSET => 0) MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) "
                + "DEFINE a AS TRUE)");
        assertAnswer(
            "8",
            "SELECT c FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS "
                + "TRUE) SAMPLE (100)");
    }

    @Test
    public void theRelationIsASource() {
        assertAnswer(
            "8|5 ;; 8|7",
            "SELECT m.n, fz.id FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS n PATTERN (a+) DEFINE "
                + "a AS TRUE) m JOIN fz ON fz.id < m.n ORDER BY 2");
        assertAnswer(
            "8|5 ;; 8|7",
            "SELECT m.n, fz.id FROM fz JOIN s MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS n PATTERN (a+) "
                + "DEFINE a AS TRUE) m ON fz.id < m.n ORDER BY 2");
        assertAnswer(
            "8|5 ;; 8|7",
            "SELECT m.n, fz.id FROM fz, s MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS n PATTERN (a+) "
                + "DEFINE a AS TRUE) m ORDER BY 2");
        assertAnswer(
            "4|2 ;; 5|2 ;; 6|2",
            "SELECT id, mn FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ALL ROWS PER "
                + "MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) WHERE mn = 2 ORDER BY id");
        assertAnswer(
            "1|2 ;; 2|3 ;; 3|2",
            "SELECT mn, COUNT(*) FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ALL ROWS "
                + "PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) GROUP BY mn ORDER BY mn");
        assertAnswer(
            "57|3",
            "WITH m AS (SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, LAST(v) AS "
                + "lv PATTERN (strt up+) DEFINE up AS v > LAG(v))) SELECT SUM(lv), COUNT(*) FROM m");
        assertAnswer(
            "View MV successfully created.",
            "CREATE OR REPLACE VIEW mv AS SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() "
                + "AS mn, LAST(v) AS lv PATTERN (strt up+) DEFINE up AS v > LAG(v))");
        assertAnswer(
            "1|20 ;; 2|31 ;; 3|6",
            "SELECT * FROM mv ORDER BY 1");
        assertAnswer(
            "2 ;; 6 ;; 8",
            "SELECT id FROM s WHERE v IN (SELECT lv FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES LAST(v) AS lv "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v))) ORDER BY id");
        assertAnswer(
            "Table MI successfully created.",
            "CREATE OR REPLACE TABLE mi (mn INT, lv INT)");
        assertAnswer(
            "3",
            "INSERT INTO mi SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "LAST(v) AS lv PATTERN (strt up+) DEFINE up AS v > LAG(v))");
        assertAnswer(
            "1|20 ;; 2|31 ;; 3|6",
            "SELECT * FROM mi ORDER BY 1");
        assertAnswer(
            "1|2 ;; 2|2",
            "SELECT o.id, m.c FROM s o, LATERAL (SELECT c FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES "
                + "COUNT(*) AS c PATTERN (a+) DEFINE a AS v > 20)) m WHERE o.id <= 2 ORDER BY 1, 2");
        assertAnswer(
            "31 ;; 20",
            "SELECT lv FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, LAST(v) AS lv "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY lv DESC LIMIT 2");
        assertAnswer(
            "1|20 ;; 2|31 ;; 3|6",
            "SELECT m.* FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, LAST(v) AS lv "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) m ORDER BY 1");
    }

    @Test
    public void aliases() {
        assertAnswer(
            "8",
            "SELECT m.c FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS "
                + "TRUE) AS m");
        assertAnswer(
            "8",
            "SELECT m.c FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS TRUE) m");
        assertAnswer(
            "8",
            "SELECT c FROM s AS x MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS "
                + "TRUE)");
        assertAnswer(
            "8",
            "SELECT c FROM s x MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS TRUE)");
        assertAnswer(
            "REFUSED Unsupported feature 'Correlated pattern definitions'.",
            "SELECT c FROM s AS x MATCH_RECOGNIZE(ORDER BY x.id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a "
                + "AS x.v > 0)");
        assertAnswer(
            "REFUSED Unsupported feature 'Correlated pattern definitions'.",
            "SELECT c FROM s MATCH_RECOGNIZE(ORDER BY s.id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS "
                + "s.v > 0)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 106 | invalid identifier 'X.C'",
            "SELECT c FROM s x MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS "
                + "TRUE) WHERE x.c = 8");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'X.C'",
            "SELECT x.c FROM s x MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'S.C'",
            "SELECT s.c FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | invalid identifier 'X.C'",
            "SELECT m.c, x.c FROM s x MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE "
                + "a AS TRUE) m");
        assertAnswer(
            "8",
            "SELECT k FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS "
                + "TRUE) m (k)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'S.ID'",
            "SELECT s.id, s.mn FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ALL ROWS PER "
                + "MATCH PATTERN (a) DEFINE a AS v > 30) ORDER BY 1");
    }

    @Test
    public void partitionAndOrder() {
        assertAnswer(
            "2|x|3|1 ;; 3|y|3|2 ;; null|y|4|3 ;; 1|x|null|4 ;; 5|x|null|5",
            "SELECT id, k, v, msq FROM n MATCH_RECOGNIZE(ORDER BY v, id MEASURES MATCH_SEQUENCE_NUMBER() AS "
                + "msq ALL ROWS PER MATCH PATTERN (a+) DEFINE a AS TRUE) ORDER BY msq");
        assertAnswer(
            "5|x|null|1 ;; 1|x|null|2 ;; null|y|4|3 ;; 3|y|3|4 ;; 2|x|3|5",
            "SELECT id, k, v, msq FROM n MATCH_RECOGNIZE(ORDER BY v DESC, id DESC MEASURES "
                + "MATCH_SEQUENCE_NUMBER() AS msq ALL ROWS PER MATCH PATTERN (a+) DEFINE a AS TRUE) ORDER BY msq");
        assertAnswer(
            "1|x|null|1 ;; 5|x|null|2 ;; 2|x|3|3 ;; 3|y|3|4 ;; null|y|4|5",
            "SELECT id, k, v, msq FROM n MATCH_RECOGNIZE(ORDER BY v NULLS FIRST, id NULLS FIRST MEASURES "
                + "MATCH_SEQUENCE_NUMBER() AS msq ALL ROWS PER MATCH PATTERN (a+) DEFINE a AS TRUE) ORDER BY msq");
        assertAnswer(
            "8",
            "SELECT c FROM s AS x MATCH_RECOGNIZE(ORDER BY x.id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a "
                + "AS TRUE)");
        assertAnswer(
            "1",
            "SELECT c FROM s AS x MATCH_RECOGNIZE(PARTITION BY x.id MEASURES COUNT(*) AS c PATTERN (a+) "
                + "DEFINE a AS TRUE) ORDER BY 1 LIMIT 1");
        assertAnswer(
            "8",
            "SELECT c FROM s MATCH_RECOGNIZE(MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS TRUE)");
        assertAnswer(
            "1|8,5,4 ;; 2|3,6,1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY v % 3, id DESC MEASURES MATCH_NUMBER() AS mn, "
                + "LISTAGG(id, ',') AS ids PATTERN (a{3}) DEFINE a AS TRUE) ORDER BY 1");
        assertAnswer(
            "8",
            "SELECT c FROM s MATCH_RECOGNIZE(ORDER BY 1 MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS TRUE)");
    }
}
