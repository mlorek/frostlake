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
 * Which of several correlated subqueries the plan cannot evaluate a statement is refused for. One refused for
 * its own aggregate over the outer row and its columns outranks every other shape wherever it stands, and of
 * several such the last is reported; of the other shapes — a LIMIT, a query inside reading the outer row, an
 * aggregate of the outer row alone — the first, the WHERE's before the select list's (live-verified).
 */
public class SeveralRefusedSubqueriesTest extends MeasuredCellsTestSupport {

    @Test
    public void anAggregateShapeBesideALimit() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g), (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)), (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)), (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g), f1((SELECT v FROM g WHERE g.id > fz.id LIMIT 1)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g) FROM fz WHERE (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > "
                + "0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25",
            "SELECT id FROM fz WHERE (SELECT MAX(fz.id + g.v) FROM g) > 0 ORDER BY (SELECT v FROM g WHERE g.id > "
                + "fz.id LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25",
            "SELECT id FROM fz WHERE (SELECT MAX(fz.id + g.v) FROM g) > 0 AND (SELECT v FROM g WHERE g.id > fz.id "
                + "LIMIT 1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 61",
            "SELECT id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) = (SELECT MAX(fz.id + g.v) FROM g) ORDER "
                + "BY (SELECT v FROM g WHERE g.id > fz.id LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 54",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), (SELECT MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 59",
            "SELECT ABS((SELECT v FROM g WHERE g.id > fz.id LIMIT 1)), (SELECT MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 58",
            "SELECT f1((SELECT v FROM g WHERE g.id > fz.id LIMIT 1)), (SELECT MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 67",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz WHERE (SELECT MAX(fz.id + g.v) FROM g) > "
                + "0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 83",
            "SELECT id FROM fz WHERE (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0 ORDER BY (SELECT MAX(fz.id "
                + "+ g.v) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 78",
            "SELECT id FROM fz WHERE (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0 AND (SELECT MAX(fz.id + "
                + "g.v) FROM g) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "115",
            "SELECT id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) = (SELECT v FROM g WHERE g.id > fz.id "
                + "LIMIT 1) ORDER BY (SELECT MAX(fz.id + g.v) FROM g)");
    }

    @Test
    public void anAggregateShapeBesideADepth() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g), (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g "
                + "WHERE g.id = 5) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)), (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) "
                + "FROM g WHERE g.id = 5) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)), (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) "
                + "FROM g WHERE g.id = 5) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g), f1((SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM "
                + "g WHERE g.id = 5)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g) FROM fz WHERE (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = "
                + "fz.id) FROM g WHERE g.id = 5) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25",
            "SELECT id FROM fz WHERE (SELECT MAX(fz.id + g.v) FROM g) > 0 ORDER BY (SELECT (SELECT g2.v FROM g g2 "
                + "WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25",
            "SELECT id FROM fz WHERE (SELECT MAX(fz.id + g.v) FROM g) > 0 AND (SELECT (SELECT g2.v FROM g g2 "
                + "WHERE g2.id = fz.id) FROM g WHERE g.id = 5) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 61",
            "SELECT id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) = (SELECT MAX(fz.id + g.v) FROM g) ORDER "
                + "BY (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 84",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5), (SELECT MAX(fz.id "
                + "+ g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 89",
            "SELECT ABS((SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)), (SELECT "
                + "MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 88",
            "SELECT f1((SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)), (SELECT "
                + "MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5), f1((SELECT "
                + "MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 97",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) FROM fz WHERE "
                + "(SELECT MAX(fz.id + g.v) FROM g) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "113",
            "SELECT id FROM fz WHERE (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) > "
                + "0 ORDER BY (SELECT MAX(fz.id + g.v) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "108",
            "SELECT id FROM fz WHERE (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) > "
                + "0 AND (SELECT MAX(fz.id + g.v) FROM g) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "145",
            "SELECT id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) = (SELECT (SELECT g2.v FROM g g2 WHERE "
                + "g2.id = fz.id) FROM g WHERE g.id = 5) ORDER BY (SELECT MAX(fz.id + g.v) FROM g)");
    }

    @Test
    public void aLimitBesideADepth() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = "
                + "fz.id) FROM g WHERE g.id = 5) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT v FROM g WHERE g.id > fz.id LIMIT 1)), (SELECT (SELECT g2.v FROM g g2 WHERE g2.id "
                + "= fz.id) FROM g WHERE g.id = 5) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT v FROM g WHERE g.id > fz.id LIMIT 1)), (SELECT (SELECT g2.v FROM g g2 WHERE g2.id "
                + "= fz.id) FROM g WHERE g.id = 5) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), f1((SELECT (SELECT g2.v FROM g g2 WHERE g2.id = "
                + "fz.id) FROM g WHERE g.id = 5)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25",
            "SELECT id FROM fz WHERE (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0 ORDER BY (SELECT (SELECT "
                + "g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25",
            "SELECT id FROM fz WHERE (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0 AND (SELECT (SELECT g2.v "
                + "FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 61",
            "SELECT id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) = (SELECT v FROM g WHERE g.id > fz.id "
                + "LIMIT 1) ORDER BY (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5), (SELECT v FROM g "
                + "WHERE g.id > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)), (SELECT v "
                + "FROM g WHERE g.id > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)), (SELECT v "
                + "FROM g WHERE g.id > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5), f1((SELECT v FROM "
                + "g WHERE g.id > fz.id LIMIT 1)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 97",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) FROM fz WHERE "
                + "(SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 61",
            "SELECT id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) = (SELECT (SELECT g2.v FROM g g2 WHERE "
                + "g2.id = fz.id) FROM g WHERE g.id = 5) ORDER BY (SELECT v FROM g WHERE g.id > fz.id LIMIT 1)");
    }

    @Test
    public void threeShapesAndEachAlone() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g), (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), (SELECT "
                + "(SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g), (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g "
                + "WHERE g.id = 5), (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 54",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), (SELECT MAX(fz.id + g.v) FROM g), (SELECT "
                + "(SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "130",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = "
                + "fz.id) FROM g WHERE g.id = 5), (SELECT MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 84",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5), (SELECT MAX(fz.id "
                + "+ g.v) FROM g), (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "130",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5), (SELECT v FROM g "
                + "WHERE g.id > fz.id LIMIT 1), (SELECT MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT v FROM g WHERE g.id > fz.id LIMIT 1)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)) FROM fz");
    }

    @Test
    public void twoOfOneShape() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        answer(
            "CREATE OR REPLACE FUNCTION f2(x INT) RETURNS INT AS 'x + 2'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), (SELECT g.id FROM g WHERE g.v > fz.id LIMIT 1) "
                + "FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 42",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g), (SELECT MIN(fz.id + g.id) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = "
                + "fz.id) FROM g WHERE g.id = 5), (SELECT g.id FROM g WHERE g.v > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz ORDER BY (SELECT g.id FROM g WHERE g.v > "
                + "fz.id LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) "
                + "= (SELECT g.id FROM g WHERE g.v > fz.id LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25",
            "SELECT id FROM fz WHERE (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0 QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY id) = (SELECT g.id FROM g WHERE g.v > fz.id LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT G.ID AS \"ID\" FROM G AS G WHERE G.V > CORRELATION(FZ.ID) "
                + "LIMIT 1 OFFSET 0)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id HAVING (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0 ORDER BY "
                + "(SELECT g.id FROM g WHERE g.v > fz.id LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz GROUP BY id HAVING (SELECT g.id FROM g "
                + "WHERE g.v > fz.id LIMIT 1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25",
            "SELECT id FROM fz WHERE (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0 GROUP BY id HAVING (SELECT "
                + "g.id FROM g WHERE g.v > fz.id LIMIT 1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25",
            "SELECT id FROM fz WHERE (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0 AND (SELECT g.id FROM g "
                + "WHERE g.v > fz.id LIMIT 1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25",
            "SELECT id FROM fz WHERE (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0 OR (SELECT g.id FROM g "
                + "WHERE g.v > fz.id LIMIT 1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g) FROM fz WHERE (SELECT MIN(fz.id + g.id) FROM g) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 71",
            "SELECT id FROM fz WHERE (SELECT MAX(fz.id + g.v) FROM g) > 0 ORDER BY (SELECT MIN(fz.id + g.id) FROM "
                + "g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 66",
            "SELECT id FROM fz WHERE (SELECT MAX(fz.id + g.v) FROM g) > 0 AND (SELECT MIN(fz.id + g.id) FROM g) > "
                + "0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g) FROM fz ORDER BY (SELECT MIN(fz.id + g.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 42",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g), (SELECT MIN(fz.id + g.id) FROM g) FROM fz WHERE (SELECT v "
                + "FROM g WHERE g.id > fz.id LIMIT 1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "103",
            "SELECT id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) = (SELECT MAX(fz.id + g.v) FROM g) ORDER "
                + "BY (SELECT MIN(fz.id + g.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT v FROM g WHERE g.id > fz.id LIMIT 1)), f2((SELECT g.id FROM g WHERE g.v > fz.id "
                + "LIMIT 1)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F2",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)), f2((SELECT MIN(fz.id + g.id) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F2",
            "SELECT f1((SELECT v FROM g WHERE g.id > fz.id LIMIT 1)), f2((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
    }

    @Test
    public void anAggregateOfTheOuterRowAlone() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        answer(
            "CREATE OR REPLACE FUNCTION f2(x INT) RETURNS INT AS 'x + 2'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT SUM(fz.id) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT SUM(fz.id) FROM g), (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), (SELECT SUM(fz.id) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 36",
            "SELECT (SELECT SUM(fz.id) FROM g), (SELECT MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g), (SELECT SUM(fz.id) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT SUM(fz.id) FROM g), (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE "
                + "g.id = 5) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5), (SELECT "
                + "SUM(fz.id) FROM g) FROM fz");
        assertAnswer(
            "F1 successfully dropped.",
            "DROP FUNCTION IF EXISTS f1(INT)");
        assertAnswer(
            "F2 successfully dropped.",
            "DROP FUNCTION IF EXISTS f2(INT)");
    }
}
