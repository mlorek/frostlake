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
 * How a recursive CTE, and the recursive view that runs as one, types its columns from the anchor and the
 * recursive term together. A column the term passes through, or computes from itself at the anchor's own type,
 * keeps that type; one the term computes from itself at any other type is given its family's widest at once
 * — NUMBER(38) at the scale the branches share, a text at its full length — and so are numbers computed from
 * each other; one computed from nothing of the CTE's, or from other columns alone, is typed as a UNION types
 * it. A column holds no NULL when neither branch can put one there. Branches of families the account does not
 * bring together describe as the anchor and are refused when read. Every answer is live-verified.
 */
public class RecursiveColumnTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aColumnGrowsWithTheTermThatComputesItFromItself() {
        engine.execute("CREATE OR REPLACE TABLE rt1 (a INT, s VARCHAR(5), f FLOAT, d DATE)");
        engine.execute("INSERT INTO rt1 VALUES (1, 'a', 1.5, '2024-01-01')");
        engine.execute("CREATE OR REPLACE TABLE rt2 (a INT NOT NULL, s VARCHAR(5) NOT NULL)");
        engine.execute("INSERT INTO rt2 VALUES (1, 'a')");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv1 (n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv1 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv1");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv2 (s, d) AS SELECT 'a', 1 UNION ALL SELECT s || 'a', d + 1 FROM "
            + "rv2 WHERE d < 3");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "D|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv2");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv2");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv3 (n) AS SELECT a FROM rt1 UNION ALL SELECT n + 1 FROM rv3 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv3");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv3");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv4 (n) AS SELECT 1::NUMBER(5,0) UNION ALL SELECT n FROM rv4 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(5,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv4");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM rv4");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv5 (n) AS SELECT 1 UNION ALL SELECT n FROM rv5 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv5");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM rv5");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv6 (n) AS SELECT 1 UNION ALL SELECT n * 1.5 FROM rv6 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(38,1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv6");
        assertAnswer(
            "4",
            "SELECT COUNT(*) FROM rv6");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv7 (n) AS SELECT 1.5 UNION ALL SELECT n * 2 FROM rv7 WHERE n < 10");
        assertAnswer(
            "N|NUMBER(38,1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv7");
        assertAnswer(
            "4",
            "SELECT COUNT(*) FROM rv7");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv8 (d) AS SELECT '2024-01-01'::DATE UNION ALL SELECT DATEADD(day, "
            + "1, d) FROM rv8 WHERE d < '2024-01-03'");
        assertAnswer(
            "D|DATE|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv8");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv8");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv9 (n) AS SELECT 1 UNION ALL SELECT NULLIF(n + 1, 3) FROM rv9 "
            + "WHERE n < 3");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv9");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv9");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv10 (n, k) AS SELECT NULL, 1 UNION ALL SELECT k, k + 1 FROM rv10 "
            + "WHERE k < 3");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv10");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv10");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv11 (n, s) AS SELECT a, s FROM rt2 UNION ALL SELECT n + 1, s || "
            + "'x' FROM rv11 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "S|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv11");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv11");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv12 (s, k) AS SELECT s, 1 FROM rt1 UNION ALL SELECT s || 'x', k + "
            + "1 FROM rv12 WHERE k < 3");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv12");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv12");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv13 (f) AS SELECT f FROM rt1 UNION ALL SELECT f + 1 FROM rv13 "
            + "WHERE f < 3");
        assertAnswer(
            "F|FLOAT|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv13");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv13");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv14 (b, k) AS SELECT TRUE, 1 UNION ALL SELECT NOT b, k + 1 FROM "
            + "rv14 WHERE k < 3");
        assertAnswer(
            "B|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv14");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv14");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv15 (n) AS SELECT 1 UNION ALL SELECT (n + 1)::NUMBER(4,0) FROM "
            + "rv15 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv15");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM rv15");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv16 (n) AS SELECT 1::NUMBER(3,0) UNION ALL SELECT 5::NUMBER(10,2) "
            + "FROM rv16 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(10,2)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv16");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM rv16");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rv17 (s, k) AS SELECT 'a', 1 UNION ALL SELECT 'longer', k + 1 FROM "
            + "rv17 WHERE k < 2");
        assertAnswer(
            "S|VARCHAR(6)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rv17");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM rv17");
        assertAnswer(
            "Table RC successfully created.",
            "CREATE OR REPLACE TABLE rc AS WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r "
                + "WHERE n < 3) SELECT * FROM r");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE rc");
        assertAnswer(
            "Table RC2 successfully created.",
            "CREATE OR REPLACE TABLE rc2 AS WITH RECURSIVE r (s, k) AS (SELECT 'a', 1 UNION ALL SELECT s || "
                + "'a', k + 1 FROM r WHERE k < 3) SELECT * FROM r");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE rc2");
        assertShape(
            "N:NUMBER(38,0)",
            "WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r");
        assertShape(
            "S:VARCHAR(134217728), K:NUMBER(38,0)",
            "WITH RECURSIVE r (s, k) AS (SELECT 'a', 1 UNION ALL SELECT s || 'a', k + 1 FROM r WHERE k < 3) "
                + "SELECT * FROM r");
        assertShape(
            "S:VARCHAR(134217728), D:NUMBER(38,0)",
            "SELECT * FROM rv2");
        assertAnswer(
            "Table RC3 successfully created.",
            "CREATE OR REPLACE TABLE rc3 AS WITH r (n) AS (SELECT 1 UNION ALL SELECT 2) SELECT * FROM r");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE rc3");
    }

    @Test
    public void whatTheTermReadsDecidesHowFarTheColumnWidens() {
        engine.execute("CREATE OR REPLACE TABLE rt1 (a INT, s VARCHAR(5), f FLOAT, d DATE)");
        engine.execute("INSERT INTO rt1 VALUES (1, 'a', 1.5, '2024-01-01')");
        engine.execute("CREATE OR REPLACE TABLE wt (c NUMBER(10,2), s VARCHAR(5), i NUMBER(3,0) NOT NULL)");
        engine.execute("INSERT INTO wt VALUES (1.5, 'ab', 1)");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw1 (n) AS SELECT 1::NUMBER(3,0) UNION ALL SELECT w.c FROM rw1 JOIN "
            + "wt w ON TRUE WHERE n < 0");
        assertAnswer(
            "N|NUMBER(10,2)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw2 (s) AS SELECT 'a' UNION ALL SELECT w.s FROM rw2 JOIN wt w ON "
            + "TRUE WHERE rw2.s = 'zz'");
        assertAnswer(
            "S|VARCHAR(5)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw2");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw3 (n) AS SELECT 1 UNION ALL SELECT (n + 1)::NUMBER(1,0) FROM rw3 "
            + "WHERE n < 3");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw3");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw4 (n) AS SELECT 1 UNION ALL SELECT n::NUMBER(1,0) FROM rw4 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw4");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw5 (n) AS SELECT 1::NUMBER(5,0) UNION ALL SELECT n::NUMBER(3,0) "
            + "FROM rw5 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw5");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw6 (s) AS SELECT 'a' UNION ALL SELECT s::VARCHAR(10) FROM rw6 "
            + "WHERE s = 'zz'");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw6");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw7 (s) AS SELECT 'abc' UNION ALL SELECT LEFT(s, 1) FROM rw7 WHERE "
            + "s = 'zz'");
        assertAnswer(
            "S|VARCHAR(3)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw7");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw8 (s) AS SELECT 'a' UNION ALL SELECT UPPER(s) FROM rw8 WHERE s = 'zz'");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw8");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw9 (n) AS SELECT 1 UNION ALL SELECT n + 1.5::FLOAT FROM rw9 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw9");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw10 (n) AS SELECT 1::FLOAT UNION ALL SELECT n + 1 FROM rw10 WHERE n < 3");
        assertAnswer(
            "N|FLOAT|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw10");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw11 (n) AS SELECT 1 UNION ALL SELECT 10 FROM rw11 WHERE n < 2");
        assertAnswer(
            "N|NUMBER(2,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw11");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw12 (n) AS SELECT 1 UNION ALL SELECT n + 0 FROM rw12 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw12");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw13 (a, b) AS SELECT 1, 2 UNION ALL SELECT b, a FROM rw13 WHERE a < 0");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw13");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw14 (a, b) AS SELECT 1, 22 UNION ALL SELECT b, a FROM rw14 WHERE a < 0");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw14");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw15 (n, s) AS SELECT 1, 'a' UNION ALL SELECT LENGTH(s), s FROM "
            + "rw15 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(18,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "S|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw15");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw16 (n) AS SELECT 1 UNION ALL SELECT n / 2 FROM rw16 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,6)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw16");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw17 (n) AS SELECT a FROM rt1 UNION ALL SELECT n * 1.5 FROM rw17 "
            + "WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,1)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw17");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw18 (n) AS SELECT 1 UNION ALL SELECT CASE WHEN n < 2 THEN n + 1 "
            + "END FROM rw18 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw18");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw19 (n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rw19 WHERE n < 3 "
            + "UNION ALL SELECT n * 10 FROM rw19 WHERE n = 1");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw19");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw20 (t) AS SELECT '2024-01-01'::TIMESTAMP_NTZ(3) UNION ALL SELECT "
            + "DATEADD(day, 1, t) FROM rw20 WHERE t < '2024-01-02'");
        assertAnswer(
            "T|TIMESTAMP_NTZ(9)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw20");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw21 (v, k) AS SELECT PARSE_JSON('1'), 1 UNION ALL SELECT v, k + 1 "
            + "FROM rw21 WHERE k < 2");
        assertAnswer(
            "V|VARIANT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw21");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw22 (a, k) AS SELECT ARRAY_CONSTRUCT(1), 1 UNION ALL SELECT "
            + "ARRAY_APPEND(a, k), k + 1 FROM rw22 WHERE k < 2");
        assertAnswer(
            "A|ARRAY|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw22");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw23 (s, k) AS SELECT NULL, 1 UNION ALL SELECT 'ab', k + 1 FROM "
            + "rw23 WHERE k < 2");
        assertAnswer(
            "S|VARCHAR(2)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw23");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw24 (s, k) AS SELECT NULL, 1 UNION ALL SELECT s, k + 1 FROM rw24 "
            + "WHERE k < 2");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw24");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw25 (n) AS SELECT i FROM wt UNION ALL SELECT n FROM rw25 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(3,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw25");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw26 (n) AS SELECT i FROM wt UNION ALL SELECT n + 1 FROM rw26 WHERE "
            + "n < 3");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw26");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw27 (b, k) AS SELECT TRUE, 1 UNION ALL SELECT b AND TRUE, k + 1 "
            + "FROM rw27 WHERE k < 2");
        assertAnswer(
            "B|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw27");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw28 (b, k) AS SELECT TRUE, 1 UNION ALL SELECT b, k + 1 FROM rw28 "
            + "WHERE k < 2");
        assertAnswer(
            "B|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw28");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw29 (s, k) AS SELECT 'a', 1 UNION ALL SELECT 'b' || 'c', k + 1 "
            + "FROM rw29 WHERE k < 2");
        assertAnswer(
            "S|VARCHAR(2)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw29");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw30 (s, k) AS SELECT 'abcdefgh', 1 UNION ALL SELECT LEFT(s, 2) || "
            + "'x', k + 1 FROM rw30 WHERE k < 2");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw30");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw31 (n) AS SELECT 5 UNION ALL SELECT n - 1 FROM rw31 WHERE n > 3");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw31");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw32 (n) AS SELECT 1.25 UNION ALL SELECT n + 1 FROM rw32 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(38,2)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw32");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw33 (n) AS SELECT 1 UNION ALL SELECT ABS(n) FROM rw33 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw33");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rw34 (n) AS SELECT 1.5 UNION ALL SELECT ROUND(n) FROM rw34 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rw34");
        engine.execute("CREATE OR REPLACE VIEW nv1 AS SELECT TRUE AS b, NOT TRUE AS nb, 1 AS n, 1 + 1 AS m, NULLIF(1, 3) AS "
            + "x, 'a' || 'b' AS s, DATEADD(day, 1, '2024-01-01'::DATE) AS d, 1.5 * 2 AS p, LENGTH('a') AS l, "
            + "UPPER('a') AS u, 1 / 2 AS q, CASE WHEN 1 < 2 THEN 1 END AS c, LEFT('abc', 1) AS lf, 1::FLOAT + 1 "
            + "AS f, TRUE AND TRUE AS ba");
        assertAnswer(
            "B|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "NB|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "N|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "M|NUMBER(2,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "X|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "S|VARCHAR(2)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "D|DATE|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "P|NUMBER(3,1)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "L|NUMBER(18,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "U|VARCHAR(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "Q|NUMBER(7,6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "LF|VARCHAR(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "F|FLOAT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "BA|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW nv1");
        engine.execute("CREATE OR REPLACE VIEW nv2 AS SELECT i + 1 AS a, NOT (i > 1) AS b, s || 'x' AS c, i AS d, c * 2 AS "
            + "e, i > 1 AS g, i = 1 AND i < 5 AS h, NULLIF(i, 3) AS k, ABS(i) AS ab, i::VARCHAR AS tx FROM wt");
        assertAnswer(
            "A|NUMBER(4,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "B|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C|VARCHAR(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "D|NUMBER(3,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "E|NUMBER(11,2)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "G|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "H|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(3,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "AB|NUMBER(3,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "TX|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW nv2");
    }

    @Test
    public void aTypeTheTermKeepsIsTheColumns() {
        engine.execute("CREATE OR REPLACE TABLE wt (c NUMBER(10,2), s VARCHAR(5), i NUMBER(3,0) NOT NULL, j NUMBER(3,0) NOT "
            + "NULL, t VARCHAR(5) NOT NULL)");
        engine.execute("INSERT INTO wt VALUES (1.5, 'ab', 1, 2, 'cd')");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx1 (n) AS SELECT 1 UNION ALL SELECT -n FROM rx1 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx2 (n) AS SELECT 1 UNION ALL SELECT n % 5 FROM rx2 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx2");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx3 (n) AS SELECT 1 UNION ALL SELECT LEAST(n, 5) FROM rx3 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx3");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx4 (n) AS SELECT 1 UNION ALL SELECT IFF(n > 0, n, n) FROM rx4 "
            + "WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx4");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx5 (n) AS SELECT 1 UNION ALL SELECT CASE WHEN n > 0 THEN n ELSE n "
            + "END FROM rx5 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx5");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx6 (n) AS SELECT 1 UNION ALL SELECT FLOOR(n) FROM rx6 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx6");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx7 (n) AS SELECT 1 UNION ALL SELECT ABS(n)::NUMBER(1,0) FROM rx7 "
            + "WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx7");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx8 (n) AS SELECT 1 UNION ALL SELECT CAST(n AS NUMBER(1,0)) FROM "
            + "rx8 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx8");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx9 (n) AS SELECT 1 UNION ALL SELECT TO_NUMBER(n, 1, 0) FROM rx9 "
            + "WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx9");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx10 (n) AS SELECT 1 UNION ALL SELECT x.n FROM rx10 x WHERE x.n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx10");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx11 (n) AS SELECT 1 UNION ALL SELECT 5 FROM rx11 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx11");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx12 (n) AS SELECT 1 UNION ALL SELECT 1 FROM rx12 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx12");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx13 (n) AS SELECT 1 UNION ALL SELECT COALESCE(n, 0) FROM rx13 "
            + "WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx13");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx14 (a, b) AS SELECT i, j FROM wt UNION ALL SELECT b, a FROM rx14 "
            + "WHERE a < 0");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx14");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx15 (a, b) AS SELECT i, j FROM wt UNION ALL SELECT a, b FROM rx15 "
            + "WHERE a < 0");
        assertAnswer(
            "A|NUMBER(3,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(3,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx15");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx16 (a, b) AS SELECT 'x', 'y' UNION ALL SELECT b, a FROM rx16 "
            + "WHERE a = 'zz'");
        assertAnswer(
            "A|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx16");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx17 (a, b) AS SELECT t, t FROM wt UNION ALL SELECT b, a FROM rx17 "
            + "WHERE a = 'zz'");
        assertAnswer(
            "A|VARCHAR(5)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|VARCHAR(5)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx17");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx18 (n) AS SELECT i FROM wt UNION ALL SELECT ABS(n) FROM rx18 "
            + "WHERE n < 0");
        assertAnswer(
            "N|NUMBER(3,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx18");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx19 (n) AS SELECT i FROM wt UNION ALL SELECT -n FROM rx19 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(3,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx19");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx20 (n) AS SELECT i FROM wt UNION ALL SELECT w.j FROM rx20 JOIN wt "
            + "w ON TRUE WHERE n < 0");
        assertAnswer(
            "N|NUMBER(3,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx20");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx21 (n) AS SELECT i FROM wt UNION ALL SELECT (n + 1)::NUMBER(3,0) "
            + "FROM rx21 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(3,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx21");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx22 (n) AS SELECT i FROM wt UNION ALL SELECT (n + 1)::NUMBER(5,0) "
            + "FROM rx22 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx22");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx23 (s) AS SELECT 'a' UNION ALL SELECT LOWER(s) FROM rx23 WHERE s "
            + "= 'zz'");
        assertAnswer(
            "S|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx23");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx24 (s) AS SELECT 'a' UNION ALL SELECT TRIM(s) FROM rx24 WHERE s = 'zz'");
        assertAnswer(
            "S|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx24");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx25 (s) AS SELECT 'abc' UNION ALL SELECT SUBSTR(s, 1, 1) FROM rx25 "
            + "WHERE s = 'zz'");
        assertAnswer(
            "S|VARCHAR(3)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx25");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx26 (s) AS SELECT 'abc' UNION ALL SELECT REVERSE(s) FROM rx26 "
            + "WHERE s = 'zz'");
        assertAnswer(
            "S|VARCHAR(3)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx26");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx27 (s) AS SELECT 'a' UNION ALL SELECT (s || 'b')::VARCHAR(1) FROM "
            + "rx27 WHERE s = 'zz'");
        assertAnswer(
            "S|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx27");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx28 (s) AS SELECT 'a' UNION ALL SELECT IFF(TRUE, s, s) FROM rx28 "
            + "WHERE s = 'zz'");
        assertAnswer(
            "S|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx28");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx29 (s) AS SELECT t FROM wt UNION ALL SELECT TRIM(s) FROM rx29 "
            + "WHERE s = 'zz'");
        assertAnswer(
            "S|VARCHAR(5)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx29");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx30 (s) AS SELECT t FROM wt UNION ALL SELECT (s || "
            + "'x')::VARCHAR(5) FROM rx30 WHERE s = 'zz'");
        assertAnswer(
            "S|VARCHAR(5)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx30");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx31 (s) AS SELECT t FROM wt UNION ALL SELECT w.t FROM rx31 JOIN wt "
            + "w ON TRUE WHERE rx31.s = 'zz'");
        assertAnswer(
            "S|VARCHAR(5)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx31");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx32 (s) AS SELECT 'a' UNION ALL SELECT LENGTH(s) FROM rx32 WHERE s "
            + "= 'zz'");
        assertAnswer(
            "S|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx32");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx33 (n) AS SELECT 1 UNION ALL SELECT n || 'x' FROM rx33 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx33");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx34 (n) AS SELECT 1 UNION ALL SELECT '7' FROM rx34 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx34");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx35 (n) AS SELECT 1 UNION ALL SELECT n + 1.5::FLOAT FROM rx35 "
            + "WHERE n < 3");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx35");
        assertAnswer(
            "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'N' "
                + "in Recursive CTE 'RX35_RECURSIVE_CTE'",
            "SELECT n FROM rx35 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx36 (n) AS SELECT 1::FLOAT UNION ALL SELECT (n + 1)::NUMBER(5,0) "
            + "FROM rx36 WHERE n < 3");
        assertAnswer(
            "N|FLOAT|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx36");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx37 (d) AS SELECT '2024-01-01'::DATE UNION ALL SELECT "
            + "d::TIMESTAMP_NTZ FROM rx37 WHERE d < '2024-01-01'");
        assertAnswer(
            "D|DATE|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx37");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx38 (b) AS SELECT TRUE UNION ALL SELECT 1 FROM rx38 WHERE b = FALSE");
        assertAnswer(
            "B|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx38");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx39 (n) AS SELECT 1 UNION ALL SELECT 1.25 FROM rx39 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(3,2)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx39");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx40 (a, b) AS SELECT 1, 1 UNION ALL SELECT a + 1, b FROM rx40 "
            + "WHERE a < 2");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx40");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rx41 (a, b) AS SELECT 1, 1 UNION ALL SELECT a, a + 1 FROM rx41 "
            + "WHERE a < 0");
        assertAnswer(
            "A|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(2,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rx41");
        assertAnswer(
            "NUMBER(38,0)[SB16] ;; NUMBER(38,0)[SB16] ;; NUMBER(38,0)[SB16]",
            "WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT "
                + "SYSTEM$TYPEOF(n) FROM r ORDER BY n");
        assertAnswer(
            "NUMBER(1,0)[SB16]|1 ;; NUMBER(1,0)[SB16]|2 ;; NUMBER(1,0)[SB16]|3",
            "WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT (n + 1)::NUMBER(1,0) FROM r WHERE n < 3) "
                + "SELECT SYSTEM$TYPEOF(n), n FROM r ORDER BY n");
        assertAnswer(
            "abc ;; azzzz",
            "WITH RECURSIVE r (s, k) AS (SELECT 'abc', 1 UNION ALL SELECT LEFT(s, 1) || 'zzzz', k + 1 FROM r "
                + "WHERE k < 2) SELECT s FROM r ORDER BY k");
    }

    @Test
    public void aFunctionsOwnTypeAndColumnsComputedFromEachOther() {
        engine.execute("CREATE OR REPLACE TABLE wt (c NUMBER(10,2), s VARCHAR(5), i NUMBER(3,0) NOT NULL, j NUMBER(3,0) NOT "
            + "NULL, t VARCHAR(5) NOT NULL)");
        engine.execute("INSERT INTO wt VALUES (1.5, 'ab', 1, 2, 'cd')");
        engine.execute("CREATE OR REPLACE TABLE w1 (n NUMBER(1,0) NOT NULL)");
        engine.execute("INSERT INTO w1 VALUES (1)");
        assertAnswer(
            "NUMBER(2,0)[SB1]|NUMBER(2,0)[SB1]|NUMBER(1,0)[SB1]|NUMBER(1,0)[SB1]|NUMBER(1,0)[SB1]|NUMBER(2,0)[SB1]|NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(ABS(n)), SYSTEM$TYPEOF(n % 5), SYSTEM$TYPEOF(-n), SYSTEM$TYPEOF(FLOOR(n)), "
                + "SYSTEM$TYPEOF(LEAST(n, 5)), SYSTEM$TYPEOF(MOD(n, 5)), SYSTEM$TYPEOF(n) FROM (SELECT 1 AS n)");
        engine.execute("CREATE OR REPLACE VIEW sv AS SELECT ABS(n) a, n % 5 m, -n g, FLOOR(n) f, LEAST(n, 5) l, SIGN(n) sg, "
            + "ROUND(n) r, TRUNC(n) t, CEIL(n) c, n * 1 x, GREATEST(n, 5) gr, NVL(n, 0) nv FROM (SELECT 1 AS n)");
        assertAnswer(
            "A|NUMBER(2,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "M|NUMBER(2,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "G|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "F|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "L|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SG|NUMBER(2,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "R|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "T|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "X|NUMBER(2,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "GR|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "NV|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW sv");
        engine.execute("CREATE OR REPLACE VIEW sv2 AS SELECT ABS(n) a, n % 5 m, -n g, FLOOR(n) f, LEAST(n, 5) l, SIGN(n) sg, "
            + "ROUND(n) r, TRUNC(n) t, CEIL(n) c, n * 1 x, GREATEST(n, 5) gr, NVL(n, 0) nv FROM w1");
        assertAnswer(
            "A|NUMBER(2,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "M|NUMBER(2,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "G|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "F|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "L|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SG|NUMBER(2,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "R|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "T|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "X|NUMBER(2,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "GR|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "NV|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW sv2");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry1 (a, b) AS SELECT 1, 1 UNION ALL SELECT b, a FROM ry1 WHERE a < 0");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry2 (a, b) AS SELECT i, i FROM wt UNION ALL SELECT b, a FROM ry2 "
            + "WHERE a < 0");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry2");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry3 (a, b) AS SELECT 'x', 'yy' UNION ALL SELECT b, a FROM ry3 WHERE "
            + "a = 'zz'");
        assertAnswer(
            "A|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry3");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry4 (a, b) AS SELECT 1, 2 UNION ALL SELECT a, a FROM ry4 WHERE a < 0");
        assertAnswer(
            "A|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry4");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry5 (a, b) AS SELECT 1, 2 UNION ALL SELECT b, b FROM ry5 WHERE a < 0");
        assertAnswer(
            "A|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry5");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry6 (n) AS SELECT 5 UNION ALL SELECT ABS(n) FROM ry6 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry6");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry7 (n) AS SELECT 1 UNION ALL SELECT SIGN(n) FROM ry7 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry7");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry8 (n) AS SELECT 1 UNION ALL SELECT ROUND(n) FROM ry8 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry8");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry9 (n) AS SELECT 1 UNION ALL SELECT TRUNC(n) FROM ry9 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry9");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry10 (n) AS SELECT 1 UNION ALL SELECT CEIL(n) FROM ry10 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry10");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry11 (n) AS SELECT 1 UNION ALL SELECT MOD(n, 5) FROM ry11 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry11");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry12 (n) AS SELECT 1 UNION ALL SELECT GREATEST(n, 5) FROM ry12 "
            + "WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry12");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry13 (n) AS SELECT 1 UNION ALL SELECT NVL(n, 0) FROM ry13 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry13");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry14 (n) AS SELECT 1 UNION ALL SELECT (n % 5)::NUMBER(1,0) FROM "
            + "ry14 WHERE n < 0");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry14");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry15 (n) AS SELECT i FROM wt UNION ALL SELECT n % 5 FROM ry15 WHERE "
            + "n < 0");
        assertAnswer(
            "N|NUMBER(3,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry15");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry16 (n) AS SELECT n FROM w1 UNION ALL SELECT ABS(n) FROM ry16 "
            + "WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry16");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry17 (n) AS SELECT n FROM w1 UNION ALL SELECT n % 5 FROM ry17 WHERE "
            + "n < 0");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry17");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry18 (a, b) AS SELECT n, n FROM w1 UNION ALL SELECT b, a FROM ry18 "
            + "WHERE a < 0");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry18");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry19 (n) AS SELECT 1::NUMBER(1,0) UNION ALL SELECT ABS(n) FROM ry19 "
            + "WHERE n < 0");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry19");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry20 (a, b) AS SELECT 1::NUMBER(3,0), 2::NUMBER(3,0) UNION ALL "
            + "SELECT b, a FROM ry20 WHERE a < 0");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry20");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry21 (a, b, c) AS SELECT 1, 2, 3 UNION ALL SELECT b, c, a FROM ry21 "
            + "WHERE a < 0");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "C|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry21");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW ry22 (a, b, c) AS SELECT 'x', 'y', 'z' UNION ALL SELECT b, c, a "
            + "FROM ry22 WHERE a = 'q'");
        assertAnswer(
            "A|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "C|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW ry22");
    }

    @Test
    public void familiesThatMeetAndFamiliesThatAreRefused() {
        engine.execute("CREATE OR REPLACE TABLE wb (b BOOLEAN NOT NULL, t VARCHAR(5) NOT NULL, d DATE NOT NULL, v VARIANT "
            + "NOT NULL, f FLOAT NOT NULL, x VARCHAR(5))");
        engine.execute("INSERT INTO wb SELECT TRUE, 'cd', '2024-01-01', PARSE_JSON('1'), 1.5, NULL");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz1 (n) AS SELECT 1 UNION ALL SELECT n + 1.5::FLOAT FROM rz1 WHERE n < 3");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz1");
        assertAnswer(
            "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'N' "
                + "in Recursive CTE 'RZ1_RECURSIVE_CTE'",
            "SELECT * FROM rz1 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz2 (n) AS SELECT 1 UNION ALL SELECT (n + 1)::VARCHAR FROM rz2 "
            + "WHERE n < 3");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz2");
        assertAnswer(
            "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'N' "
                + "in Recursive CTE 'RZ2_RECURSIVE_CTE'",
            "SELECT * FROM rz2 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz3 (n) AS SELECT 1 UNION ALL SELECT '7' FROM rz3 WHERE n < 2");
        assertAnswer(
            "N|NUMBER(1,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz3");
        assertAnswer(
            "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'N' "
                + "in Recursive CTE 'RZ3_RECURSIVE_CTE'",
            "SELECT * FROM rz3 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz4 (s) AS SELECT 'a' UNION ALL SELECT LENGTH(s) + 1 FROM rz4 WHERE "
            + "s = 'a'");
        assertAnswer(
            "S|VARCHAR(1)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz4");
        assertAnswer(
            "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'S' "
                + "in Recursive CTE 'RZ4_RECURSIVE_CTE'",
            "SELECT * FROM rz4 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz5 (d) AS SELECT '2024-01-01'::DATE UNION ALL SELECT DATEADD(hour, "
            + "30, d::TIMESTAMP_NTZ) FROM rz5 WHERE d < '2024-01-02'");
        assertAnswer(
            "D|DATE|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz5");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz6 (d) AS SELECT '2024-01-01'::TIMESTAMP_NTZ UNION ALL SELECT "
            + "DATEADD(day, 1, d)::DATE FROM rz6 WHERE d < '2024-01-02'");
        assertAnswer(
            "D|TIMESTAMP_NTZ(9)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz6");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz7 (b) AS SELECT TRUE UNION ALL SELECT 0 FROM rz7 WHERE b");
        assertAnswer(
            "B|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz7");
        assertAnswer(
            "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'B' "
                + "in Recursive CTE 'RZ7_RECURSIVE_CTE'",
            "SELECT * FROM rz7 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz8 (n) AS SELECT 1 UNION ALL SELECT n > 5 FROM rz8 WHERE n = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'N' "
                + "in Recursive CTE 'RZ8_RECURSIVE_CTE'",
            "SELECT * FROM rz8 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz9 (n) AS SELECT 1::FLOAT UNION ALL SELECT (n + 1)::NUMBER(5,0) "
            + "FROM rz9 WHERE n < 3");
        assertAnswer(
            "N|FLOAT|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz9");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz10 (v) AS SELECT PARSE_JSON('1') UNION ALL SELECT 2 FROM rz10 "
            + "WHERE v = 1");
        assertAnswer(
            "V|VARIANT|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz10");
        assertAnswer(
            "1 ;; 2",
            "SELECT * FROM rz10 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz11 (n) AS SELECT 1 UNION ALL SELECT PARSE_JSON('2') FROM rz11 "
            + "WHERE n = 1");
        assertAnswer(
            "N|VARIANT|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz11");
        assertAnswer(
            "1 ;; 2",
            "SELECT * FROM rz11 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz12 (a, b) AS SELECT 1, 1 UNION ALL SELECT a + 1, a FROM rz12 "
            + "WHERE a < 3");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz12");
        assertAnswer(
            "1|1 ;; 2|1 ;; 3|2",
            "SELECT * FROM rz12 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz13 (b, k) AS SELECT b, 1 FROM wb UNION ALL SELECT b, k + 1 FROM "
            + "rz13 WHERE k < 2");
        assertAnswer(
            "B|BOOLEAN|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz13");
        assertAnswer(
            "true|1 ;; true|2",
            "SELECT * FROM rz13 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz14 (b, k) AS SELECT b, 1 FROM wb UNION ALL SELECT NOT b, k + 1 "
            + "FROM rz14 WHERE k < 2");
        assertAnswer(
            "B|BOOLEAN|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz14");
        assertAnswer(
            "false|2 ;; true|1",
            "SELECT * FROM rz14 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz15 (v, k) AS SELECT v, 1 FROM wb UNION ALL SELECT v, k + 1 FROM "
            + "rz15 WHERE k < 2");
        assertAnswer(
            "V|VARIANT|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz15");
        assertAnswer(
            "1|1 ;; 1|2",
            "SELECT * FROM rz15 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz16 (f) AS SELECT f FROM wb UNION ALL SELECT f + 1 FROM rz16 WHERE "
            + "f < 3");
        assertAnswer(
            "F|FLOAT|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz16");
        assertAnswer(
            "1.5 ;; 2.5 ;; 3.5",
            "SELECT * FROM rz16 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz17 (x, k) AS SELECT x, 1 FROM wb UNION ALL SELECT 'q', k + 1 FROM "
            + "rz17 WHERE k < 2");
        assertAnswer(
            "X|VARCHAR(5)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz17");
        assertAnswer(
            "q|2 ;; null|1",
            "SELECT * FROM rz17 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz18 (c, k) AS SELECT 1 > 0, 1 UNION ALL SELECT k > 5, k + 1 FROM "
            + "rz18 WHERE k < 2");
        assertAnswer(
            "C|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz18");
        assertAnswer(
            "false|2 ;; true|1",
            "SELECT * FROM rz18 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz19 (c, k) AS SELECT 1 IS NULL, 1 UNION ALL SELECT k IS NULL, k + "
            + "1 FROM rz19 WHERE k < 2");
        assertAnswer(
            "C|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz19");
        assertAnswer(
            "false|1 ;; false|2",
            "SELECT * FROM rz19 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz20 (d, k) AS SELECT CURRENT_DATE, 1 UNION ALL SELECT d, k + 1 "
            + "FROM rz20 WHERE k < 2");
        assertAnswer(
            "D|DATE|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz20");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz21 (n, k) AS SELECT '5'::NUMBER, 1 UNION ALL SELECT n, k + 1 FROM "
            + "rz21 WHERE k < 2");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz21");
        assertAnswer(
            "5|1 ;; 5|2",
            "SELECT * FROM rz21 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz22 (n, k) AS SELECT TRY_TO_NUMBER('5'), 1 UNION ALL SELECT n, k + "
            + "1 FROM rz22 WHERE k < 2");
        assertAnswer(
            "N|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz22");
        assertAnswer(
            "5|1 ;; 5|2",
            "SELECT * FROM rz22 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz23 (s, k) AS SELECT 'a', 1 UNION ALL SELECT CONCAT(s, 'b'), k + 1 "
            + "FROM rz23 WHERE k < 2");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz23");
        assertAnswer(
            "a|1 ;; ab|2",
            "SELECT * FROM rz23 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz24 (s, k) AS SELECT 'a', 1 UNION ALL SELECT TO_VARCHAR(k), k + 1 "
            + "FROM rz24 WHERE k < 2");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz24");
        assertAnswer(
            "1|2 ;; a|1",
            "SELECT * FROM rz24 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz25 (s, k) AS SELECT 'a', 1 UNION ALL SELECT REPLACE(s, 'a', 'b'), "
            + "k + 1 FROM rz25 WHERE k < 2");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz25");
        assertAnswer(
            "a|1 ;; b|2",
            "SELECT * FROM rz25 ORDER BY 1");
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW rz26 (s, k) AS SELECT 'a', 1 UNION ALL SELECT LPAD(s, 3, 'x'), k + "
            + "1 FROM rz26 WHERE k < 2");
        assertAnswer(
            "S|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "K|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW rz26");
        assertAnswer(
            "a|1 ;; xxa|2",
            "SELECT * FROM rz26 ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'N' "
                + "in Recursive CTE 'R'",
            "WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1.5::FLOAT FROM r WHERE n < 3) SELECT * FROM r");
        assertAnswer(
            "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'N' "
                + "in Recursive CTE 'R'",
            "WITH RECURSIVE r AS (SELECT 1 AS n UNION ALL SELECT n + 1.5::FLOAT FROM r WHERE n < 3) SELECT * "
                + "FROM r");
        assertShape(
            "S:VARCHAR(134217728), K:NUMBER(38,0)",
            "WITH RECURSIVE r (s, k) AS (SELECT 'a', 1 UNION ALL SELECT s || 'b', k + 1 FROM r WHERE k < 3) "
                + "SELECT s, k FROM r");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|NUMBER(38,0)[SB16] ;; VARCHAR(134217728)[LOB]|NUMBER(38,0)[SB16] ;; "
                + "VARCHAR(134217728)[LOB]|NUMBER(38,0)[SB16]",
            "WITH RECURSIVE r (s, k) AS (SELECT 'a', 1 UNION ALL SELECT s || 'b', k + 1 FROM r WHERE k < 3) "
                + "SELECT SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(k) FROM r ORDER BY k");
    }
}
