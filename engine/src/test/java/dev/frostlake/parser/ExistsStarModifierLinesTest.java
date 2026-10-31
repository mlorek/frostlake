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
 * A star's misplaced RENAME or REPLACE in the WHERE of an EXISTS's query, the EXISTS compared after it: live closes the
 * star's call at the keyword, takes the call's ')' for the query's, and refuses the comparison an EXISTS takes none of,
 * ahead of the ')' left over (live-verified).
 */
public class ExistsStarModifierLinesTest extends MeasuredCellsTestSupport {

    /** EXISTS and NOT EXISTS queries whose WHERE compares a star call holding each kind of misplaced modifier. */
    @Test
    public void theComparisonAfterTheExistsIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE)");
        assertCells(new String[][] {
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 70 unexpected '1'. | syntax error line 1 at position 78 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 70 unexpected 'id'. | syntax error line 1 at position 73 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE x) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 72 unexpected '='."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE x y) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 74 unexpected '='."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id), 1) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 70 unexpected '1'. | syntax error line 1 at position 78 unexpected ','."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* RENAME id AS k) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 77 unexpected '='."},
            {"SELECT 1 FROM fz WHERE EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'REPLACE'."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 74 unexpected '1'. | syntax error line 1 at position 82 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (id)) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 74 unexpected 'id'. | syntax error line 1 at position 77 unexpected ')'."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE x) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 76 unexpected '='."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE x y) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 78 unexpected '='."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id), 1) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 74 unexpected '1'. | syntax error line 1 at position 82 unexpected ','."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* RENAME id AS k) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 81 unexpected '='."},
            {"SELECT 1 FROM fz WHERE NOT EXISTS (SELECT 1 FROM fz WHERE HASH(* REPLACE) = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'REPLACE'."},
        });
    }
}
