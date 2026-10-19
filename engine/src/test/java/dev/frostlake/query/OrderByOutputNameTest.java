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
 * A name inside an ORDER BY key is read from the select list's output first. An alias stands for the
 * expression it names wherever the key reads it — an operand, an argument, a CASE branch, an aggregate's
 * argument, a window's own ORDER BY — and outranks a column of its name, which a qualified reference still
 * reads; so does the column a star's REPLACE or RENAME gives. An alias defined through earlier aliases is read
 * as the select list reads it, where a column outranks an alias. A subquery in the key sees no alias, and a
 * name two output columns carry is ambiguous. Every answer is live-verified.
 */
public class OrderByOutputNameTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE h (id INT, v INT)");
        engine.execute("INSERT INTO h VALUES (5, 1), (7, 2)");
    }

    @Test
    public void anAliasInsideAKeyIsTheExpressionItNames() {
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS x FROM fz ORDER BY x + 0");
        assertAnswer(
            "-5 ;; -7",
            "SELECT id * -1 AS x FROM fz ORDER BY ABS(x)");
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS x FROM fz ORDER BY x");
        assertAnswer(
            "7|-14 ;; 5|-10",
            "SELECT id AS x, id * -2 AS y FROM fz ORDER BY x + y");
        assertAnswer(
            "8|-7 ;; 6|-5",
            "SELECT id + 1 AS p, id * -1 AS x FROM fz ORDER BY x + 0");
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS x FROM fz ORDER BY CASE WHEN x < -5 THEN 0 ELSE 1 END");
        assertAnswer(
            "7 ;; 5",
            "SELECT id::VARCHAR AS x FROM fz ORDER BY LENGTH(x), x DESC");
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS x FROM fz ORDER BY 1 + 0, x");
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS x FROM fz ORDER BY x::VARCHAR DESC");
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS x FROM fz ORDER BY CAST(x AS VARCHAR) DESC");
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS x FROM fz ORDER BY x IN (-7) DESC");
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS x FROM fz ORDER BY IFF(x = -7, 0, 1)");
        assertAnswer(
            "-5 ;; -7",
            "SELECT id * -1 AS x FROM fz ORDER BY -x");
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS x FROM fz ORDER BY x IS NULL, x");
        assertAnswer(
            "-5 ;; -7",
            "SELECT id * -1 AS x FROM fz ORDER BY x BETWEEN -6 AND 0 DESC");
        assertAnswer(
            "7|1 ;; 5|1",
            "SELECT id, 1 AS one FROM fz ORDER BY one + id DESC");
        assertAnswer(
            "-7|7 ;; -5|5",
            "SELECT id * -1 AS x, id AS y FROM fz ORDER BY GREATEST(x, y * -2)");
        assertAnswer(
            "null ;; 7",
            "SELECT IFF(b, NULL, id) AS x FROM fz ORDER BY x + 0 NULLS FIRST");
        assertAnswer(
            "-7",
            "SELECT TOP 1 id * -1 AS x FROM fz ORDER BY x + 0");
        assertAnswer(
            "-7",
            "SELECT id * -1 AS x FROM fz ORDER BY x + 0 LIMIT 1");
        assertAnswer(
            "-7 ;; -5",
            "SELECT x AS y FROM (SELECT id * -1 AS x FROM fz) q ORDER BY y + x");
    }

    @Test
    public void anAliasOutranksAColumnOfItsName() {
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS id FROM fz ORDER BY id + 0");
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS id FROM fz ORDER BY ABS(id) DESC");
        assertAnswer(
            "-5 ;; -7",
            "SELECT id * -1 AS id FROM fz ORDER BY fz.id");
        assertAnswer(
            "-5 ;; -7",
            "SELECT id * -1 AS id FROM fz ORDER BY fz.id + 0");
        assertAnswer(
            "-5|true ;; -7|false",
            "SELECT id * -1 AS x, b FROM fz ORDER BY x + id * 2");
        assertAnswer(
            "-7 ;; -5",
            "SELECT fz.id * -1 AS v FROM fz JOIN h ON fz.id = h.id ORDER BY v + 0");
        assertAnswer(
            "-7 ;; -5",
            "SELECT fz.id * -1 AS v FROM fz JOIN h ON fz.id = h.id ORDER BY v");
        assertAnswer(
            "-5 ;; -7",
            "SELECT fz.id * -1 AS v FROM fz JOIN h ON fz.id = h.id ORDER BY h.v + 0");
        assertAnswer(
            "-7 ;; -5",
            "SELECT fz.id * -1 AS id FROM fz JOIN h ON fz.id = h.id ORDER BY id + 0");
        assertAnswer(
            "-5 ;; -7",
            "SELECT id * -1 AS x FROM fz ORDER BY CASE WHEN b THEN x ELSE id END");
        assertAnswer(
            "7 ;; 5",
            "SELECT fz.id FROM fz JOIN h ON fz.id = h.id ORDER BY id * -1");
    }

    @Test
    public void theColumnAStarRenamesOrReplaces() {
        assertAnswer(
            "7|false ;; 5|true",
            "SELECT * RENAME (id AS k) FROM fz ORDER BY k * -1");
        assertAnswer(
            "-7|false ;; -5|true",
            "SELECT * REPLACE (id * -1 AS id) FROM fz ORDER BY id + 0");
        assertAnswer(
            "7|false|-7 ;; 5|true|-5",
            "SELECT *, id * -1 AS x FROM fz ORDER BY x + 0");
        assertAnswer(
            "7|false|-7 ;; 5|true|-5",
            "SELECT *, id * -1 AS x FROM fz ORDER BY id DESC");
        assertAnswer(
            "7|false|-7 ;; 5|true|-5",
            "SELECT *, id * -1 AS id FROM fz ORDER BY fz.id DESC");
        assertAnswer(
            "7|false|-7 ;; 5|true|-5",
            "SELECT *, id * -1 AS id FROM fz ORDER BY 3");
    }

    @Test
    public void anAliasDefinedThroughEarlierAliases() {
        assertAnswer(
            "-7|-6 ;; -5|-4",
            "SELECT id * -1 AS x, x + 1 AS y FROM fz ORDER BY y + 0");
        assertAnswer(
            "-5|105 ;; -7|107",
            "SELECT id * -1 AS id, id + 100 AS y FROM fz ORDER BY y + 0");
        assertAnswer(
            "-7|-14|-13 ;; -5|-10|-9",
            "SELECT id * -1 AS x, x * 2 AS y, y + 1 AS z FROM fz ORDER BY z + 0");
        assertAnswer(
            "-7|-14 ;; -5|-10",
            "SELECT id * -1 AS x, x * 2 AS y FROM fz ORDER BY y + 0, id");
    }

    @Test
    public void groupedAndDistinctQueries() {
        assertAnswer(
            "true|6 ;; false|1",
            "SELECT b, COUNT(*) + IFF(b, 5, 0) AS c FROM fz GROUP BY b ORDER BY c * -1");
        assertAnswer(
            "false|1 ;; true|1",
            "SELECT NOT b AS nb, COUNT(*) AS c FROM fz GROUP BY b ORDER BY nb::INT + 0");
        assertAnswer(
            "false|7 ;; true|5",
            "SELECT b, SUM(id) AS s FROM fz GROUP BY b ORDER BY s + 0 DESC");
        assertAnswer(
            "-7 ;; -5",
            "SELECT DISTINCT id * -1 AS x FROM fz ORDER BY x + 0");
        assertAnswer(
            "false|7 ;; true|5",
            "SELECT b AS k, SUM(id) AS s FROM fz GROUP BY b ORDER BY s * -1, k");
        assertAnswer(
            "false|-7 ;; true|-5",
            "SELECT b, SUM(id) * -1 AS id FROM fz GROUP BY b ORDER BY id + 0");
        assertAnswer(
            "false|-7 ;; true|-5",
            "SELECT b, SUM(id) * -1 AS id FROM fz GROUP BY b ORDER BY id");
        assertAnswer(
            "false|-7 ;; true|-5",
            "SELECT b, id * -1 AS x FROM fz GROUP BY b, id ORDER BY MAX(x)");
    }

    @Test
    public void windowedQueries() {
        assertAnswer(
            "7|2 ;; 5|1",
            "SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM fz ORDER BY rn * -1");
        assertAnswer(
            "-7|1 ;; -5|2",
            "SELECT id * -1 AS id, ROW_NUMBER() OVER (ORDER BY b) AS rn FROM fz ORDER BY id + 0");
        assertAnswer(
            "7|12 ;; 5|12",
            "SELECT id, SUM(id) OVER () AS s FROM fz ORDER BY s - id");
        assertAnswer(
            "-7|2 ;; -5|1",
            "SELECT id * -1 AS x, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM fz ORDER BY x + rn");
        assertAnswer(
            "false|2 ;; true|1",
            "SELECT b, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM fz ORDER BY rn * id DESC");
        assertAnswer(
            "-5 ;; -7",
            "SELECT id * -1 AS x FROM fz ORDER BY ROW_NUMBER() OVER (ORDER BY x) DESC");
        assertAnswer(
            "-5 ;; -7",
            "SELECT id * -1 AS x FROM fz ORDER BY ROW_NUMBER() OVER (ORDER BY x + 0) DESC");
        assertAnswer(
            "-5 ;; -7",
            "SELECT id * -1 AS id FROM fz ORDER BY ROW_NUMBER() OVER (ORDER BY id) DESC");
    }

    @Test
    public void aWindowsOwnKeysReadAliasesAsTheSelectListDoes() {
        assertAnswer(
            "-7|1 ;; -5|2",
            "SELECT id * -1 AS x, ROW_NUMBER() OVER (ORDER BY x) AS rn FROM fz ORDER BY 1");
        assertAnswer(
            "-7|1 ;; -5|2",
            "SELECT id * -1 AS x, ROW_NUMBER() OVER (ORDER BY x + 0) AS rn FROM fz ORDER BY 1");
        assertAnswer(
            "-7|2 ;; -5|1",
            "SELECT id * -1 AS id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM fz ORDER BY 1");
        assertAnswer(
            "-7|2 ;; -5|1",
            "SELECT id * -1 AS id, ROW_NUMBER() OVER (ORDER BY id + 0) AS rn FROM fz ORDER BY 1");
        assertAnswer(
            "-7",
            "SELECT id * -1 AS x FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY x + 0) = 1");
        assertAnswer(
            "-7",
            "SELECT id * -1 AS x FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY x) = 1");
        assertAnswer(
            "-5",
            "SELECT id * -1 AS id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) = 1");
        assertAnswer(
            "-5",
            "SELECT id * -1 AS id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id + 0) = 1");
        assertAnswer(
            "-7",
            "SELECT id * -1 AS x FROM fz QUALIFY x + 0 = MIN(x) OVER ()");
    }

    @Test
    public void aSubqueryInAKeySeesNoAlias() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 45 | invalid identifier 'X'",
            "SELECT id * -1 AS x FROM fz ORDER BY (SELECT x)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 74 | invalid identifier 'X'",
            "SELECT id * -1 AS x FROM fz ORDER BY EXISTS (SELECT 1 FROM g WHERE g.id = x)");
        assertAnswer(
            "-5 ;; -7",
            "SELECT id * -1 AS x FROM fz ORDER BY x IN (SELECT -id FROM g) DESC");
        assertAnswer(
            "5|2 ;; 7|0",
            "SELECT id, (SELECT COUNT(*) FROM g WHERE g.id >= fz.id) AS x FROM fz ORDER BY x * -1");
    }

    @Test
    public void quotedAndUnknownNames() {
        assertAnswer(
            "-7 ;; -5",
            "SELECT id * -1 AS \"x\" FROM fz ORDER BY \"x\" + 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | invalid identifier 'X'",
            "SELECT id * -1 AS \"x\" FROM fz ORDER BY x + 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'NOPE'",
            "SELECT id * -1 AS x FROM fz ORDER BY nope + 0");
    }

    @Test
    public void aSetOperationsKeysReadItsOutput() {
        assertAnswer(
            "1 ;; 5 ;; 7",
            "SELECT id AS x FROM fz UNION ALL SELECT 1 ORDER BY x + 0");
        assertAnswer(
            "1 ;; -5 ;; -7",
            "SELECT id * -1 AS x FROM fz UNION ALL SELECT 1 ORDER BY x * -1");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT 1 a, 2 a UNION ALL SELECT 3, 4 ORDER BY a");
        assertAnswer(
            "3|4 ;; 1|2",
            "SELECT 1 a, 2 a UNION ALL SELECT 3, 4 ORDER BY 1 DESC");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT 1 a, 2 a UNION ALL SELECT 3, 4 ORDER BY a + 0");
        assertAnswer(
            "3|4 ;; 1|2",
            "SELECT 1 a, 2 b UNION ALL SELECT 3 c, 4 c ORDER BY a DESC");
    }

    @Test
    public void aNameTwoOutputColumnsCarryIsAmbiguous() {
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'X'",
            "SELECT id AS x, id * -1 AS x FROM fz ORDER BY x + 0");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'X'",
            "SELECT id AS x, id * -1 AS x FROM fz ORDER BY x");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT *, id * -1 AS id FROM fz ORDER BY id + 0");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT *, id * -1 AS id FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT id, id FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT *, id FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT *, id FROM fz ORDER BY id + 0");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT id, id * -1 AS id FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT *, * FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT fz.*, id * -1 AS id FROM fz ORDER BY id");
        assertAnswer(
            "7|false|7|false|7 ;; 5|true|5|true|5",
            "SELECT *, *, id AS z FROM fz ORDER BY z DESC");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'X'",
            "SELECT * FROM fz AS q (x, x) ORDER BY x");
    }

    @Test
    public void theColumnsAStarProjectsOverAJoin() {
        assertAnswer(
            "7 ;; 5",
            "SELECT a.id FROM h a JOIN h b USING (id) ORDER BY v DESC");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'V'",
            "SELECT * FROM h a JOIN h b USING (id) ORDER BY v DESC");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT * FROM fz JOIN h ON fz.id = h.id ORDER BY id");
        assertAnswer(
            "7|2 ;; 5|1",
            "SELECT * FROM h a NATURAL JOIN h b ORDER BY v DESC");
        assertAnswer(
            "7|2|2 ;; 5|1|1",
            "SELECT * FROM h a JOIN h b USING (id) ORDER BY id DESC");
        assertAnswer(
            "7|2|2 ;; 5|1|1",
            "SELECT * FROM h a JOIN h b USING (id) ORDER BY a.v DESC");
        assertAnswer(
            "7|1 ;; 5|1",
            "SELECT * EXCLUDE (v), 1 AS v FROM h ORDER BY v, id DESC");
        assertAnswer(
            "7|false|7|2 ;; 5|true|7|2 ;; 7|false|5|1 ;; 5|true|5|1",
            "SELECT * FROM fz, h ORDER BY v DESC, b");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT * FROM fz, h ORDER BY id");
    }
}
