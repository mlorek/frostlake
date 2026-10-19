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
 * A star call's misplaced RENAME or REPLACE one bracket deeper in a clause, in a window, in a CASE, in a derived
 * table's clause, in a compared subquery and before a trailing comma: the lines the account stacks after the
 * keyword's own (live-verified).
 */
public class StarModifierDeeperPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void oneBracketDeeperInAClause() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE FY (id INT)");
        answer(
            "INSERT INTO FY VALUES (5)");
        answer(
            "SELECT CASE WHEN HASH(* REPLACE (1 AS id)) = 1 THEN 1 END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 49 unexpected '1'. | syntax error line 1 at position 70 unexpected 'y'.",
            "SELECT 1 FROM FZ WHERE CASE WHEN HASH(* REPLACE (1 AS id)) = 1 THEN x y END");
        answer(
            "SELECT CAST(HASH(* REPLACE x y) AS INT) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 49 unexpected '('.",
            "SELECT SUM(id) OVER (PARTITION BY HASH(* REPLACE (1 AS id))) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 45 unexpected '('.",
            "SELECT SUM(id) OVER (ORDER BY HASH(* REPLACE (1 AS id))) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 49 unexpected 'x'.",
            "SELECT SUM(id) OVER (PARTITION BY HASH(* REPLACE x)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 69 unexpected '1'. | syntax error line 1 at position 77 unexpected ')'.",
            "SELECT 1 FROM FZ QUALIFY ROW_NUMBER() OVER (ORDER BY HASH(* REPLACE (1 AS id))) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 73 unexpected '1'. | syntax error line 1 at position 81 unexpected ')'.",
            "SELECT 1 FROM FZ QUALIFY ROW_NUMBER() OVER (PARTITION BY HASH(* REPLACE (1 AS id))) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 69 unexpected 'id'. | syntax error line 1 at position 72 unexpected ')'.",
            "SELECT 1 FROM FZ QUALIFY ROW_NUMBER() OVER (ORDER BY HASH(* REPLACE (id))) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 70 unexpected ')'.",
            "SELECT 1 FROM FZ QUALIFY ROW_NUMBER() OVER (ORDER BY HASH(* REPLACE x)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 70 unexpected '1'. | syntax error line 1 at position 78 unexpected ')'.",
            "SELECT 1 FROM FZ ORDER BY ROW_NUMBER() OVER (ORDER BY HASH(* REPLACE (1 AS id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'REPLACE'.",
            "SELECT a FROM (SELECT 1 a FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'REPLACE'.",
            "SELECT a FROM (SELECT 1 a FROM FZ ORDER BY HASH(* REPLACE (1 AS id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 52 unexpected '1'. | syntax error line 1 at position 60 unexpected ')'.",
            "WITH c AS (SELECT 1 a FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1) SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 70 unexpected '1'. | syntax error line 1 at position 78 unexpected ')'.",
            "SELECT 1 FROM FZ WHERE EXISTS (SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 70 unexpected 'id'. | syntax error line 1 at position 73 unexpected ')'.",
            "SELECT 1 FROM FZ WHERE EXISTS (SELECT 1 FROM FZ WHERE HASH(* REPLACE (id)) = 1)");
        answer(
            "SELECT 1 FROM FZ WHERE EXISTS (SELECT 1 FROM FZ WHERE HASH(* REPLACE x) = 1)");
        answer(
            "SELECT 1 FROM FZ WHERE id = (SELECT MAX(id) FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 50 unexpected '1'. | syntax error line 1 at position 58 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY ROLLUP (HASH(* REPLACE (1 AS id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 48 unexpected '1'. | syntax error line 1 at position 56 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY CUBE (HASH(* REPLACE (1 AS id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 58 unexpected '1'. | syntax error line 1 at position 67 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY GROUPING SETS ((HASH(* REPLACE (1 AS id))))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 50 unexpected 'id'. | syntax error line 1 at position 53 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY ROLLUP (HASH(* REPLACE (id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 51 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY ROLLUP (HASH(* REPLACE x))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 44 unexpected '<EOF>'.",
            "SELECT 1 FROM FZ ORDER BY HASH(* REPLACE x),");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 42 unexpected '1'. | syntax error line 1 at position 52 unexpected "
                + "'<EOF>'.",
            "SELECT 1 FROM FZ ORDER BY HASH(* REPLACE (1 AS id)),");
        answer(
            "SELECT a FROM (SELECT HASH(* REPLACE (1 AS id)) a FROM FZ)");
        answer(
            "SELECT a FROM (SELECT HASH(* REPLACE (1, 2)) a FROM FZ)");
        answer(
            "SELECT a FROM (SELECT HASH(* REPLACE (1)) a FROM FZ) WHERE x y");
        answer(
            "SELECT a FROM (SELECT HASH(* REPLACE 'x') a FROM FZ) WHERE a b");
        answer(
            "SELECT a FROM (SELECT HASH(* REPLACE AS k) a FROM FZ) WHERE x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 43 unexpected 'FROM'. | syntax error line 1 at position 60 unexpected "
                + "'y'.",
            "SELECT a FROM (SELECT HASH(* REPLACE, 1) a FROM FZ) WHERE x y");
        answer(
            "WITH c AS (SELECT HASH(* REPLACE (SELECT 1)) h FROM FZ) SELECT * FROM c");
        answer(
            "SELECT a FROM (SELECT {* REPLACE x} a FROM FZ) WHERE a b");
        answer(
            "WITH c AS (SELECT {* REPLACE (1 AS id)} h FROM FZ) SELECT * FROM c");
    }

    @Test
    public void superGroupsAndWindows() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE FY (id INT)");
        answer(
            "INSERT INTO FY VALUES (5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 53 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY ROLLUP (HASH(* REPLACE x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 49 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY CUBE (HASH(* REPLACE x))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 58 unexpected 'id'. | syntax error line 1 at position 62 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY GROUPING SETS ((HASH(* REPLACE (id))))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 60 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY GROUPING SETS ((HASH(* REPLACE x)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 57 unexpected '1'. | syntax error line 1 at position 65 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY GROUPING SETS (HASH(* REPLACE (1 AS id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 49 unexpected 'id'. | syntax error line 1 at position 57 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY ROLLUP (HASH(* RENAME (id AS x)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 56 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY ROLLUP (HASH(* RENAME id AS x))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 61 unexpected '1'. | syntax error line 1 at position 69 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY ROLLUP (HASH(* EXCLUDE id REPLACE (1 AS id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 45 unexpected 'x'.",
            "SELECT SUM(id) OVER (ORDER BY HASH(* REPLACE x)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 45 unexpected '('.",
            "SELECT SUM(id) OVER (ORDER BY HASH(* REPLACE (id))) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 49 unexpected '('.",
            "SELECT SUM(id) OVER (PARTITION BY HASH(* REPLACE (id))) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 49 unexpected '('.",
            "SELECT SUM(id) OVER (PARTITION BY HASH(* REPLACE (1 AS id)) ORDER BY id) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 48 unexpected '('.",
            "SELECT SUM(id) OVER (PARTITION BY HASH(* RENAME (id AS x))) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 74 unexpected ')'.",
            "SELECT 1 FROM FZ QUALIFY ROW_NUMBER() OVER (PARTITION BY HASH(* REPLACE x)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 73 unexpected 'id'. | syntax error line 1 at position 76 unexpected ')'.",
            "SELECT 1 FROM FZ QUALIFY ROW_NUMBER() OVER (PARTITION BY HASH(* REPLACE (id))) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 71 unexpected ')'.",
            "SELECT 1 FROM FZ ORDER BY ROW_NUMBER() OVER (ORDER BY HASH(* REPLACE x))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 70 unexpected 'id'. | syntax error line 1 at position 73 unexpected ')'.",
            "SELECT 1 FROM FZ ORDER BY ROW_NUMBER() OVER (ORDER BY HASH(* REPLACE (id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 74 unexpected '1'. | syntax error line 1 at position 82 unexpected ')'.",
            "SELECT 1 FROM FZ ORDER BY ROW_NUMBER() OVER (PARTITION BY HASH(* REPLACE (1 AS id)))");
    }

    @Test
    public void enclosedQueriesAndSubqueries() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE FY (id INT)");
        answer(
            "INSERT INTO FY VALUES (5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 59 unexpected ')'.",
            "SELECT a FROM (SELECT 1 a FROM FZ WHERE HASH(* REPLACE (id)) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'REPLACE'.",
            "SELECT a FROM (SELECT 1 a FROM FZ WHERE HASH(* REPLACE x) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 62 unexpected ')'.",
            "SELECT a FROM (SELECT 1 a FROM FZ ORDER BY HASH(* REPLACE (id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'REPLACE'.",
            "SELECT a FROM (SELECT 1 a FROM FZ ORDER BY HASH(* REPLACE x))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'REPLACE'.",
            "SELECT a FROM (SELECT 1 a FROM FZ GROUP BY HASH(* REPLACE (1 AS id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'REPLACE'.",
            "SELECT a FROM (SELECT 1 a FROM FZ HAVING HASH(* REPLACE (1 AS id)) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'REPLACE'.",
            "SELECT a FROM (SELECT 1 a FROM FZ QUALIFY HASH(* REPLACE (1 AS id)) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 52 unexpected 'id'. | syntax error line 1 at position 55 unexpected ')'.",
            "WITH c AS (SELECT 1 a FROM FZ WHERE HASH(* REPLACE (id)) = 1) SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 54 unexpected '='.",
            "WITH c AS (SELECT 1 a FROM FZ WHERE HASH(* REPLACE x) = 1) SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 55 unexpected '1'. | syntax error line 1 at position 63 unexpected ')'.",
            "WITH c AS (SELECT 1 a FROM FZ ORDER BY HASH(* REPLACE (1 AS id))) SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 74 unexpected '1'. | syntax error line 1 at position 82 unexpected ')'.",
            "SELECT 1 FROM FZ WHERE NOT EXISTS (SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 78 unexpected ')'.",
            "SELECT 1 FROM FZ WHERE id IN (SELECT id FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 73 unexpected ')'.",
            "SELECT 1 FROM FZ WHERE id IN (SELECT id FROM FZ WHERE HASH(* REPLACE (id)) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 75 unexpected ')'.",
            "SELECT 1 FROM FZ WHERE id IN (SELECT id FROM FZ WHERE HASH(* REPLACE x) = 1)");
        answer(
            "SELECT 1 FROM FZ WHERE id = (SELECT MAX(id) FROM FZ WHERE HASH(* REPLACE (id)) = 1)");
        answer(
            "SELECT 1 FROM FZ WHERE id = (SELECT MAX(id) FROM FZ WHERE HASH(* REPLACE x) = 1)");
        answer(
            "SELECT 1 FROM FZ WHERE id > ALL (SELECT id FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1)");
        answer(
            "SELECT 1 FROM FZ WHERE (SELECT MAX(id) FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1) = id");
        answer(
            "SELECT (SELECT MAX(id) FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1) FROM FZ");
    }

    @Test
    public void trailingCommasCasesAndCasts() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE FY (id INT)");
        answer(
            "INSERT INTO FY VALUES (5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 42 unexpected 'id'. | syntax error line 1 at position 47 unexpected "
                + "'<EOF>'.",
            "SELECT 1 FROM FZ ORDER BY HASH(* REPLACE (id)),");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 44 unexpected '<EOF>'.",
            "SELECT 1 FROM FZ GROUP BY HASH(* REPLACE x),");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 42 unexpected '1'. | syntax error line 1 at position 52 unexpected "
                + "'<EOF>'.",
            "SELECT 1 FROM FZ GROUP BY HASH(* REPLACE (1 AS id)),");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 48 unexpected '<EOF>'.",
            "SELECT 1 FROM FZ WHERE HASH(* REPLACE x) = 1 AND");
        answer(
            "SELECT CASE WHEN HASH(* REPLACE x) = 1 THEN 1 END FROM FZ");
        answer(
            "SELECT CASE WHEN HASH(* REPLACE (id)) = 1 THEN 1 END FROM FZ");
        answer(
            "SELECT CASE HASH(* REPLACE (1 AS id)) WHEN 1 THEN 1 END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 43 unexpected '('.",
            "SELECT CASE WHEN 1 = 1 THEN HASH(* REPLACE (1 AS id)) END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 49 unexpected '1'.",
            "SELECT 1 FROM FZ WHERE CASE WHEN HASH(* REPLACE (1 AS id)) = 1 THEN 1 END = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'REPLACE'.",
            "SELECT 1 FROM FZ WHERE CASE WHEN HASH(* REPLACE x) = 1 THEN 1 END = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 28 unexpected '1'. | syntax error line 1 at position 35 unexpected ')'.",
            "SELECT CAST(HASH(* REPLACE (1 AS id)) AS INT) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "SELECT CAST(HASH(* REPLACE x) AS INT) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 22 unexpected 'x'.",
            "SELECT HASH(* REPLACE x y)::INT FROM FZ");
    }

    @Test
    public void aDerivedTablesOrACtesItem() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE FY (id INT)");
        answer(
            "INSERT INTO FY VALUES (5)");
        answer(
            "SELECT a FROM (SELECT HASH(* REPLACE (id)) a FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 37 unexpected 'x'.",
            "SELECT a FROM (SELECT HASH(* REPLACE x) a FROM FZ)");
        answer(
            "SELECT a FROM (SELECT HASH(* REPLACE (1 AS id, 2 AS b)) a FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 37 unexpected '('.",
            "SELECT a FROM (SELECT HASH(* REPLACE ()) a FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 37 unexpected '('.",
            "SELECT a FROM (SELECT HASH(* REPLACE (x y)) a FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 37 unexpected '('.",
            "SELECT a FROM (SELECT HASH(* REPLACE (1 AS)) a FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 37 unexpected '('.",
            "SELECT a FROM (SELECT HASH(* REPLACE (SELECT 1)) a FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 33 unexpected '('. | syntax error line 1 at position 34 unexpected '1'. "
                + "| syntax error line 1 at position 42 unexpected ')'.",
            "WITH c AS (SELECT HASH(* REPLACE (1 AS id)) h FROM FZ) SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 33 unexpected 'x'.",
            "WITH c AS (SELECT HASH(* REPLACE x) h FROM FZ) SELECT * FROM c");
        answer(
            "SELECT a FROM (SELECT {* REPLACE (1 AS id)} a FROM FZ)");
        answer(
            "SELECT a FROM (SELECT {* REPLACE (id)} a FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 29 unexpected 'x'.",
            "WITH c AS (SELECT {* REPLACE x} h FROM FZ) SELECT * FROM c");
    }
}
