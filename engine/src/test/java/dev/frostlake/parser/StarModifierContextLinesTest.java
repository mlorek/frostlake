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
 * The syntax-error lines past a star modifier's keyword, {@code HASH(* REPLACE …)} or {@code RENAME}, where the call
 * stands as a CASE's condition, as a CASE's operand, in a subquery compared by {@code =} or as an EXISTS query's item
 * (live-verified): each place reads the modifier's remainder its own way, a bare keyword, a word, two words, a
 * RENAME, a bracketed value with an alias, with a comma after it, and a bracketed name.
 */
public class StarModifierContextLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theLinesPastAStarModifiersKeyword() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE)");
        assertCells(new String[][] {
            {"SELECT CASE WHEN HASH(* REPLACE (1 AS id)) = 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 35 unexpected 'AS'. | syntax error line 1 at position 40 "
                + "unexpected ')'."},
            {"SELECT CASE WHEN HASH(* REPLACE (id)) = 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 36 unexpected ')'. | syntax error line 1 at position 42 "
                + "unexpected 'THEN'."},
            {"SELECT CASE WHEN HASH(* REPLACE x) = 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 32 unexpected 'x'. | syntax error line 1 at position 33 "
                + "unexpected ')'."},
            {"SELECT CASE WHEN HASH(* REPLACE x y) = 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 32 unexpected 'x'. | syntax error line 1 at position 34 "
                + "unexpected 'y'."},
            {"SELECT CASE WHEN HASH(* REPLACE (1 AS id), 1) = 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 35 unexpected 'AS'. | syntax error line 1 at position 40 "
                + "unexpected ')'."},
            {"SELECT CASE WHEN HASH(* RENAME id AS k) = 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 31 unexpected 'id'. | syntax error line 1 at position 34 unexpected 'AS'."},
            {"SELECT CASE WHEN HASH(* REPLACE) = 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'."},
            {"SELECT CASE HASH(* REPLACE (1 AS id)) WHEN 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 28 unexpected '1'. | syntax error line 1 at position 35 "
                + "unexpected ')'."},
            {"SELECT CASE HASH(* REPLACE (id)) WHEN 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 28 unexpected 'id'. | syntax error line 1 at position 30 "
                + "unexpected ')'."},
            {"SELECT CASE HASH(* REPLACE x) WHEN 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 28 unexpected ')'."},
            {"SELECT CASE HASH(* REPLACE x y) WHEN 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 29 unexpected 'y'."},
            {"SELECT CASE HASH(* REPLACE (1 AS id), 1) WHEN 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 28 unexpected '1'. | syntax error line 1 at position 35 "
                + "unexpected ')'."},
            {"SELECT CASE HASH(* RENAME id AS k) WHEN 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 29 unexpected 'AS'."},
            {"SELECT CASE HASH(* REPLACE) WHEN 1 THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'REPLACE'."},
            {"SELECT CASE WHEN TRUE THEN HASH(* REPLACE (1 AS id)) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 42 unexpected '('."},
            {"SELECT CASE WHEN TRUE THEN HASH(* REPLACE (id)) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 42 unexpected '('."},
            {"SELECT CASE WHEN TRUE THEN HASH(* REPLACE x) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 42 unexpected 'x'."},
            {"SELECT CASE WHEN TRUE THEN HASH(* REPLACE x y) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 42 unexpected 'x'."},
            {"SELECT CASE WHEN TRUE THEN HASH(* REPLACE (1 AS id), 1) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 42 unexpected '('."},
            {"SELECT CASE WHEN TRUE THEN HASH(* RENAME id AS k) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 41 unexpected 'id'."},
            {"SELECT CASE WHEN TRUE THEN HASH(* REPLACE) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'REPLACE'."},
            {"SELECT CASE WHEN TRUE THEN 1 ELSE HASH(* REPLACE (1 AS id)) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 49 unexpected '('."},
            {"SELECT CASE WHEN TRUE THEN 1 ELSE HASH(* REPLACE (id)) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 49 unexpected '('."},
            {"SELECT CASE WHEN TRUE THEN 1 ELSE HASH(* REPLACE x) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 49 unexpected 'x'."},
            {"SELECT CASE WHEN TRUE THEN 1 ELSE HASH(* REPLACE x y) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 49 unexpected 'x'."},
            {"SELECT CASE WHEN TRUE THEN 1 ELSE HASH(* REPLACE (1 AS id), 1) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 49 unexpected '('."},
            {"SELECT CASE WHEN TRUE THEN 1 ELSE HASH(* RENAME id AS k) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 48 unexpected 'id'."},
            {"SELECT CASE WHEN TRUE THEN 1 ELSE HASH(* REPLACE) END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'REPLACE'."},
            {"SELECT CASE WHEN HASH(* REPLACE (1 AS id)) THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 35 unexpected 'AS'. | syntax error line 1 at position 40 "
                + "unexpected ')'."},
            {"SELECT CASE WHEN HASH(* REPLACE (id)) THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 36 unexpected ')'."},
            {"SELECT CASE WHEN HASH(* REPLACE x) THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 32 unexpected 'x'. | syntax error line 1 at position 33 "
                + "unexpected ')'."},
            {"SELECT CASE WHEN HASH(* REPLACE x y) THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 32 unexpected 'x'. | syntax error line 1 at position 34 "
                + "unexpected 'y'."},
            {"SELECT CASE WHEN HASH(* REPLACE (1 AS id), 1) THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 35 unexpected 'AS'. | syntax error line 1 at position 40 "
                + "unexpected ')'."},
            {"SELECT CASE WHEN HASH(* RENAME id AS k) THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 31 unexpected 'id'. | syntax error line 1 at position 34 unexpected 'AS'."},
            {"SELECT CASE WHEN HASH(* REPLACE) THEN 1 END FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 40 unexpected 'id'. | syntax error line 1 at position 76 unexpected "
                + "'AS'. | syntax error line 1 at position 82 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE (id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 40 unexpected 'id'. | syntax error line 1 at position 77 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE x) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 40 unexpected 'id'. | syntax error line 1 at position 73 unexpected 'x'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE x y) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 40 unexpected 'id'. | syntax error line 1 at position 73 unexpected 'x'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE (1 AS id), 1) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 40 unexpected 'id'. | syntax error line 1 at position 76 unexpected "
                + "'AS'. | syntax error line 1 at position 82 unexpected ','."},
            {"SELECT 1 FROM fz WHERE id = (SELECT MAX(id) FROM fz WHERE HASH(* RENAME id AS k) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 40 unexpected 'id'. | syntax error line 1 at position 72 unexpected 'id'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 40 unexpected 'id'. | syntax error line 1 at position 72 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT HASH(* REPLACE (1 AS id)) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 54 unexpected 'AS'. "
                + "| syntax error line 1 at position 60 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT HASH(* REPLACE (id)) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 55 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT HASH(* REPLACE x) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 51 unexpected 'x'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT HASH(* REPLACE x y) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 51 unexpected 'x'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT HASH(* REPLACE (1 AS id), 1) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 54 unexpected 'AS'. "
                + "| syntax error line 1 at position 60 unexpected ','."},
            {"SELECT 1 FROM fz WHERE id = (SELECT HASH(* RENAME id AS k) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 50 unexpected 'id'."},
            {"SELECT 1 FROM fz WHERE id = (SELECT HASH(* REPLACE) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 50 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id IN (SELECT id FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 78 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id IN (SELECT id FROM fz WHERE HASH(* REPLACE (id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 73 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id IN (SELECT id FROM fz WHERE HASH(* REPLACE x) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 75 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id IN (SELECT id FROM fz WHERE HASH(* REPLACE x y) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 77 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id IN (SELECT id FROM fz WHERE HASH(* REPLACE (1 AS id), 1) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 78 unexpected ','."},
            {"SELECT 1 FROM fz WHERE id IN (SELECT id FROM fz WHERE HASH(* RENAME id AS k) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 80 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE id IN (SELECT id FROM fz WHERE HASH(* REPLACE) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 73 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 70 unexpected '1'. | syntax error line 1 at position 78 "
                + "unexpected ')'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 70 unexpected 'id'. | syntax error line 1 at position 73 "
                + "unexpected ')'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE x) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 72 unexpected '='."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE x y) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 74 unexpected '='."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id), 1) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 70 unexpected '1'. | syntax error line 1 at position 78 "
                + "unexpected ','."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* RENAME id AS k) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 77 unexpected '='."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 70 unexpected '1'. | syntax error line 1 at position 78 "
                + "unexpected ')'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (id)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 70 unexpected 'id'. | syntax error line 1 at position 73 "
                + "unexpected ')'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE x))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 71 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE x y))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 73 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id), 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 70 unexpected '1'. | syntax error line 1 at position 78 "
                + "unexpected ','."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* RENAME id AS k))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 76 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT HASH(* REPLACE (1 AS id)) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 53 unexpected '('."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT HASH(* REPLACE (id)) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 53 unexpected '('."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT HASH(* REPLACE x) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 53 unexpected 'x'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT HASH(* REPLACE x y) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 53 unexpected 'x'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT HASH(* REPLACE (1 AS id), 1) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 53 unexpected '('."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT HASH(* RENAME id AS k) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 52 unexpected 'id'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT HASH(* REPLACE) FROM fz)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'REPLACE'."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 74 unexpected '1'. | syntax error line 1 at position 82 "
                + "unexpected ')'."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 74 unexpected 'id'. | syntax error line 1 at position 77 "
                + "unexpected ')'."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE x) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 76 unexpected '='."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE x y) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 78 unexpected '='."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id), 1) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 74 unexpected '1'. | syntax error line 1 at position 82 "
                + "unexpected ','."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* RENAME id AS k) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 81 unexpected '='."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'."},
            {"SELECT (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
            {"SELECT (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE (id)) = 1) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
            {"SELECT (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE x) = 1) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
            {"SELECT (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE x y) = 1) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
            {"SELECT (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE (1 AS id), 1) = 1) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
            {"SELECT (SELECT MAX(id) FROM fz WHERE HASH(* RENAME id AS k) = 1) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
            {"SELECT (SELECT MAX(id) FROM fz WHERE HASH(* REPLACE) = 1) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
        });
    }
}
