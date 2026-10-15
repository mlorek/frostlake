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
 * A correlated subquery over a derived outer relation that computes over, groups or joins its table, and over two
 * relations in the FROM: what the account judges before any row (live-verified).
 */
public class DerivedOuterRelationJudgementTest extends MeasuredCellsTestSupport {

    @Test
    public void aDerivedRelationOfItsOwnShape() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        answer(
            "INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
        answer(
            "CREATE OR REPLACE TABLE one (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO one VALUES (5, TRUE)");
        answer(
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id + 0 AS id FROM fz WHERE 1 = 0) "
                + "fz");
        answer(
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz GROUP BY id HAVING 1 = "
                + "0) fz");
        answer(
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT fz.id FROM fz JOIN one ON FALSE) fz");
        answer(
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz WHERE 1 = 0) d, (SELECT * "
                + "FROM one) o");
        assertAnswer(
            "",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT 5 AS id FROM fz WHERE 1 = 0) fz");
        answer(
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id AS id FROM fz WHERE 1 = 0) fz");
        assertAnswer(
            "",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT -id AS id FROM fz WHERE 1 = 0) fz");
        answer(
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id::INT AS id FROM fz WHERE 1 = 0) "
                + "fz");
        answer(
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT ABS(id) AS id FROM fz WHERE 1 = 0) "
                + "fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 12",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT MAX(id) AS id FROM fz WHERE 1 = 0) "
                + "fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 12",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT DISTINCT id FROM fz WHERE 1 = 0) fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 12",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz WHERE 1 = 0 ORDER BY "
                + "id) fz");
        answer(
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz WHERE 1 = 0 UNION ALL "
                + "SELECT id FROM fz WHERE 1 = 0) fz");
        answer(
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz GROUP BY id) fz WHERE 1 "
                + "= 0");
        assertAnswer(
            "",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id + 0 AS id FROM fz) fz WHERE 1 = "
                + "0");
        assertAnswer(
            "",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT fz.id FROM fz JOIN one ON fz.id = "
                + "one.id) fz WHERE 1 = 0");
        answer(
            "WITH fz AS (SELECT id + 0 AS id FROM test_schema.fz WHERE 1 = 0) SELECT id, (SELECT v FROM g WHERE "
                + "g.id = fz.id) AS x FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 12",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id, b FROM fz WHERE 1 = 0) fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 12",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT b, id FROM fz WHERE 1 = 0) fz");
        answer(
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id AS id, id + 1 AS k FROM fz "
                + "WHERE 1 = 0) fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 77 | found QUALIFY clause but no window "
                + "function.",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz QUALIFY 1 = 0) fz");
        assertAnswer(
            "",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM (SELECT id FROM fz) WHERE "
                + "1 = 0) fz");
    }

    @Test
    public void twoRelationsInTheFrom() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        answer(
            "INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
        answer(
            "CREATE OR REPLACE TABLE one (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO one VALUES (5, TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 15",
            "SELECT fz.id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz, one WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 15",
            "SELECT fz.id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz JOIN one ON fz.id = one.id WHERE 1 = "
                + "0");
        answer(
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz WHERE 1 = 0) d JOIN one o ON "
                + "TRUE");
        assertAnswer(
            "",
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz) d, (SELECT * FROM one) o "
                + "WHERE 1 = 0");
        answer(
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz WHERE 1 = 0) d, one o");
        answer(
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM one o, (SELECT * FROM fz WHERE 1 = 0) d");
        assertAnswer(
            "",
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id + o.id) FROM (SELECT * FROM fz WHERE 1 = 0) d, "
                + "(SELECT * FROM one) o");
    }
}
