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
 * A name a column of the relation and a select alias both carry reads the column in GROUP BY, HAVING and QUALIFY,
 * as in WHERE: SELECT id, COUNT(*) AS id … GROUP BY id HAVING id > 5 groups and filters by the column, an ordinal
 * key naming the column item included, and a window's alias gives way to the column too. Two aliases of a name no
 * relation carries stay ambiguous there, ORDER BY reads the output columns first, a QUALIFY with no window
 * function anywhere is refused at its place, and a lateral table function reads a select alias written over a
 * qualified column (all live-verified).
 */
public class ColumnBeforeAliasTest extends MeasuredCellsTestSupport {

    @Test
    public void aRelationColumnAnswersItsNameBeforeAnAlias() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE sq (k INT, c VARCHAR)");
        engine.execute("INSERT INTO sq VALUES (1, 'a.b'), (2, 'c.d'), (3, 'e.f')");
        assertAnswer("5|1 ;; 7|1", "SELECT id, COUNT(*) AS id FROM fz GROUP BY id ORDER BY 1");
        assertAnswer("7|1", "SELECT id, COUNT(*) AS id FROM fz GROUP BY 1 HAVING id > 5");
        assertAnswer("7|1", "SELECT id, COUNT(*) AS id FROM fz GROUP BY 1 HAVING id + 0 > 5");
        assertAnswer("5|-5", "SELECT id, id * -1 AS id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) = 1");
        assertAnswer("7|-7", "SELECT id, id * -1 AS id FROM fz QUALIFY id > 5 AND ROW_NUMBER() OVER (ORDER BY b) = 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'X'",
            "SELECT b AS x, COUNT(*) AS x FROM fz GROUP BY 1 HAVING x > 0");
        assertAnswer("5|true ;; 7|false", "SELECT id AS b, b FROM fz GROUP BY b, id ORDER BY 1");
        assertAnswer("7|7", "SELECT id, SUM(id) AS id FROM fz GROUP BY id HAVING SUM(id) > 5 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT id, id + 1 AS id FROM fz GROUP BY id ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 27 | found QUALIFY clause but no window "
                + "function.",
            "SELECT id, 1 AS id FROM fz QUALIFY id = 7");
        assertAnswer("A.B|1|A ;; A.B|1|B ;; C.D|2|C ;; C.D|2|D ;; E.F|3|E ;; E.F|3|F",
            "SELECT UPPER(q.c) AS u, s.seq, s.value FROM sq q, TABLE(SPLIT_TO_TABLE(u, '.')) s ORDER BY 1, s.index");
        assertAnswer("a.b|a ;; a.b|b ;; c.d|c ;; c.d|d ;; e.f|e ;; e.f|f",
            "SELECT LOWER(q.c) AS u, f.value FROM sq q, LATERAL FLATTEN(INPUT => SPLIT(u, '.')) f ORDER BY 1, f.index");
        assertAnswer("a.b-x|a.b ;; a.b-x|x ;; c.d-x|c.d ;; c.d-x|x ;; e.f-x|e.f ;; e.f-x|x",
            "SELECT q.c || '-x' AS u, s.value FROM sq q, TABLE(SPLIT_TO_TABLE(u, '-')) s ORDER BY 1, s.index");
    }

    @Test
    public void anAliasAndAColumnOfOneName() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE), (7, TRUE)");
        assertAnswer("1 ;; 2", "SELECT COUNT(*) AS id FROM fz GROUP BY id ORDER BY 1");
        assertAnswer("2", "SELECT COUNT(*) AS id FROM fz GROUP BY id HAVING id > 5 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT id, COUNT(*) AS b FROM fz GROUP BY id HAVING b > 1");
        assertAnswer("50 ;; 70",
            "SELECT id * 10 AS id FROM fz QUALIFY ROW_NUMBER() OVER (PARTITION BY id ORDER BY b) = 1 ORDER BY 1");
        assertAnswer("70 ;; 70", "SELECT id * 10 AS id FROM fz WHERE id > 5 ORDER BY 1");
        assertAnswer("7|2", "SELECT id, COUNT(*) AS id FROM fz GROUP BY id HAVING COUNT(*) > 1 ORDER BY 1");
        assertAnswer("5|true ;; 7|false ;; 7|true", "SELECT id, b AS id FROM fz GROUP BY id, b ORDER BY 1, 2");
        assertAnswer("",
            "SELECT id, MAX(b) AS id FROM fz GROUP BY id QUALIFY id > 5 AND ROW_NUMBER() OVER (ORDER BY id) = 1");
        assertAnswer("REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT id + 0 AS id, COUNT(*) AS id FROM fz GROUP BY 1 HAVING id > 5");
    }

    @Test
    public void aSingleAliasOverAColumn() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE), (7, TRUE)");
        assertAnswer("-7", "SELECT id * -1 AS id FROM fz QUALIFY id > 5 AND ROW_NUMBER() OVER (ORDER BY b) = 1");
        assertAnswer("-7|false ;; -5|true",
            "SELECT id * -1 AS id, b FROM fz QUALIFY ROW_NUMBER() OVER (PARTITION BY b ORDER BY id) = 1 ORDER BY b");
        assertAnswer("7|2", "SELECT id, COUNT(*) AS id FROM fz GROUP BY 1 HAVING id > 5");
        assertAnswer("7|2", "SELECT id + 0 AS k, COUNT(*) AS id FROM fz GROUP BY 1 HAVING k > 5");
        assertAnswer("", "SELECT id, ROW_NUMBER() OVER (ORDER BY b) AS id FROM fz QUALIFY id = 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'X'",
            "SELECT b AS x, 1 AS x FROM fz QUALIFY x = 7");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | 'FZ.ID' in select clause is neither "
                + "an aggregate nor in the group by clause.",
            "SELECT id AS b FROM fz GROUP BY b");
        assertAnswer("-5", "SELECT id * -1 AS id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) = 1");
        assertAnswer("-7|2", "SELECT id * -1 AS id, COUNT(*) AS n FROM fz GROUP BY id HAVING id > 5");
        assertAnswer("REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT id * -1 AS id, COUNT(*) AS n FROM fz GROUP BY 1 HAVING id < 0");
    }
}
