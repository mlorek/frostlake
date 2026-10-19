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
 * A relation whose query projects two columns of one name — a derived table, a common table expression — is
 * read whole by a star and by position, but a reference to the repeated name is ambiguous in every clause,
 * qualified or bare. Names match exactly, so a quoted lower-case name beside an upper-case one repeats
 * nothing. An alias list written after a relation is another matter: a name it gives twice reads the first
 * column of that name, while the column list of a common table expression repeats as a query's own items do.
 * Every answer is live-verified.
 */
public class RepeatedColumnNameTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    @Test
    public void aReferenceToARepeatedNameIsAmbiguous() {
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT t.a FROM (SELECT 1 a, 2 a) t");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT a FROM (SELECT 1 a, 2 a) t");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT MAX(a) FROM (SELECT 1 a, 2 a) t");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT 1 FROM (SELECT 1 a, 2 a) t WHERE a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT 1 FROM (SELECT 1 a, 2 a) t ORDER BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT * FROM (SELECT 1 a, 2 a) t ORDER BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT COUNT(*) FROM (SELECT 1 a, 2 a) t GROUP BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT fz.id FROM (SELECT 5 a, 2 a) t JOIN fz ON fz.id = t.a");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "WITH c AS (SELECT 1 a, 2 a) SELECT a FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT q.x FROM (SELECT t.a AS x FROM (SELECT 1 a, 2 a) t) q");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT id FROM (SELECT fz.id, g.id FROM fz JOIN g ON fz.id = g.id) t");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT (SELECT a FROM (SELECT 1 a, 2 a) t)");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT a FROM (SELECT 1 a, 2 a, 3 a) t");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            """
                SELECT 1,
                t.a
                FROM (SELECT 1 a, 2 a) t""");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "WITH c AS (SELECT 1 a, 2 a) SELECT c.a FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT * FROM (SELECT 1 a, 2 a) t WHERE t.a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT t.id FROM (SELECT fz.id, g.id FROM fz JOIN g ON fz.id = g.id) t");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "SELECT 1 WHERE EXISTS (SELECT a FROM (SELECT 1 a, 2 a) t)");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "WITH c AS (SELECT a FROM (SELECT 1 a, 2 a) t) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "UPDATE g SET v = t.a FROM (SELECT 1 a, 2 a) t WHERE g.id = 5");
    }

    @Test
    public void aStarAPositionAndTheOtherColumnsStillRead() {
        assertAnswer(
            "1|2",
            "SELECT t.* FROM (SELECT 1 a, 2 a) t");
        assertAnswer(
            "1|2",
            "SELECT * FROM (SELECT 1 a, 2 a) t ORDER BY 1");
        assertAnswer(
            "1|2",
            "WITH c AS (SELECT 1 a, 2 a) SELECT * FROM c");
        assertAnswer(
            "9",
            "SELECT z FROM (SELECT 1 a, 2 a, 9 z) t");
        assertAnswer(
            "5|5",
            "SELECT * FROM (SELECT fz.id, g.id FROM fz JOIN g ON fz.id = g.id) t");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM (SELECT 1 a, 2 a) t");
    }

    @Test
    public void namesMatchExactly() {
        assertAnswer(
            "1|2",
            "SELECT \"x\", \"X\" FROM (SELECT 1 \"x\", 2 \"X\") t");
        assertAnswer(
            "2",
            "SELECT x FROM (SELECT 1 \"x\", 2 \"X\") t");
        assertAnswer(
            "1",
            "SELECT a FROM (SELECT 1 a, 2 \"a\") t");
        assertAnswer(
            "2",
            "SELECT \"a\" FROM (SELECT 1 a, 2 \"a\") t");
    }

    @Test
    public void anAliasListThatRepeatsANameReadsItsFirstColumn() {
        assertAnswer(
            "1",
            "SELECT a FROM (VALUES (1, 2)) v (a, a)");
        assertAnswer(
            "1",
            "SELECT a FROM (SELECT 1, 2) t (a, a)");
        assertAnswer(
            "1",
            "SELECT t.a FROM (SELECT 1 x, 2 x) t (a, a)");
        assertAnswer(
            "1|2",
            "SELECT a, b FROM (SELECT 1 x, 2 x) t (a, b)");
        assertAnswer(
            "5|true",
            "SELECT * FROM fz AS q (x, x) WHERE x = 5");
        assertAnswer(
            "1",
            "SELECT a FROM (SELECT 1 x) q, LATERAL FLATTEN(ARRAY_CONSTRUCT(7)) f (a, a)");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'A'",
            "WITH c (a, a) AS (SELECT 1, 2) SELECT a FROM c");
        assertAnswer(
            "1|2",
            "WITH c (a, a) AS (SELECT 1, 2) SELECT * FROM c");
    }

    @Test
    public void anOutputNameRepeatedThroughAnAlias() {
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'X'",
            "SELECT id AS x, id * -1 AS x FROM fz GROUP BY x");
        assertAnswer(
            "7|false|-7",
            "SELECT *, id * -1 AS id FROM fz WHERE id > 5");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'X'",
            "SELECT b AS x, COUNT(*) AS x FROM fz GROUP BY b HAVING x > 0");
    }
}
