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
 * An arithmetic operation, a comparison or a || with a constant that folds to NULL is NULL whatever its other
 * operand, so a subquery there is neither judged nor run (live-verified).
 */
public class FoldedNullBesideSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void underAFilterThatKeepsNoRow() {
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
            "",
            "SELECT id, NULL + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "",
            "SELECT id, NULL::INT + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) + NULL AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "",
            "SELECT id, IFF(FALSE, 1, NULL) + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "",
            "SELECT id, NULLIF(1, 1) + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 40",
            "SELECT id, CASE WHEN TRUE THEN 1 END + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 16",
            "SELECT id, 0 * (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 16",
            "SELECT id, 1 + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 12",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "",
            "SELECT id, NULL || (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 27",
            "SELECT id, COALESCE(NULL, (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, CASE WHEN FALSE THEN 1 END + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NULL + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "",
            "SELECT id, CASE WHEN FALSE THEN 1 END * (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "",
            "SELECT id, CASE WHEN FALSE THEN 1 END = (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "",
            "SELECT id, ABS(CASE WHEN FALSE THEN 1 END + (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz WHERE "
                + "1 = 0");
        assertAnswer(
            "",
            "SELECT id, CASE WHEN FALSE THEN 1 ELSE NULL END + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz "
                + "WHERE 1 = 0");
        assertAnswer(
            "",
            "SELECT id, CASE WHEN 1 = 0 THEN 1 END + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 37",
            "SELECT id, CASE WHEN b THEN 1 END + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0");
    }

    @Test
    public void overTheRows() {
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
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NULL - (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NULL / (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NULL < (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NULL <> (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) * NULL AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id) || NULL AS x FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 20",
            "SELECT id, fz.id + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 21",
            "SELECT id, NULL AND (SELECT v FROM g WHERE g.id = fz.id) > 0 AS x FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 20",
            "SELECT id, NULL OR (SELECT v FROM g WHERE g.id = fz.id) > 0 AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, CASE WHEN NULL THEN 1 END + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, COALESCE(NULL, NULL) + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NVL(NULL, NULL) + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 27",
            "SELECT id, NULLIF(1, 2) + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, (NULL + 1) + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NULL + 1 + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, 1 + NULL + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, 1 + (NULL + (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, -NULL + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NULL::VARCHAR || (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, TO_NUMBER(NULL) + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, ABS(NULL) + (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 29",
            "SELECT id, NULL IS NULL AND (SELECT v FROM g WHERE g.id = fz.id) > 0 AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NULL IN ((SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz ORDER BY id");
        answer(
            "SELECT id, NULL BETWEEN 1 AND (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        answer(
            "SELECT id, GREATEST(NULL, (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|2 ;; 7|2",
            "SELECT id, IFF(NULL = (SELECT v FROM g WHERE g.id = fz.id), 1, 2) AS x FROM fz ORDER BY id");
        assertAnswer(
            "",
            "SELECT id FROM fz WHERE NULL = (SELECT v FROM g WHERE g.id = fz.id)");
        assertAnswer(
            "5",
            "SELECT id FROM fz WHERE NULL + (SELECT v FROM g WHERE g.id = fz.id) > 0 OR id = 5 ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NULL + (SELECT MAX(v) FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, NULL + (SELECT v FROM g) AS x FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | Invalid argument types for function "
                + "'+': (NULL, ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT id, NULL + (SELECT 1, 2) AS x FROM fz ORDER BY id");
        assertAnswer(
            "null",
            "SELECT NULL + (SELECT v FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'NOSUCH'",
            "SELECT NULL + (SELECT nosuch FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCHTABLE' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCHTABLE.",
            "SELECT NULL + (SELECT v FROM nosuchtable)");
    }
}
