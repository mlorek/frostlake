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
 * A call's star argument takes one modifier, an EXCLUDE or an ILIKE; a second is a syntax error, read as live reads
 * it: the call closed before it, so an EXCLUDE is refused and then the token after it, an ILIKE and then the token
 * after its pattern. A RENAME and a REPLACE are refused there whatever stands before them, and a select item's star
 * takes modifiers of different kinds (all live-verified).
 */
public class StarArgumentOneModifierTest extends MeasuredCellsTestSupport {

    @Test
    public void aStarArgumentTakesOneModifier() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE lo (o OBJECT, a ARRAY, v VARIANT, bn BINARY)");
        assertCells(new String[][] {
            {"SELECT HASH(* EXCLUDE (id) EXCLUDE (b)) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'EXCLUDE'. | "
                + "syntax error line 1 at position 35 unexpected '('."},
            {"SELECT HASH(* ILIKE 'i%' EXCLUDE (b)) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'EXCLUDE'. | "
                + "syntax error line 1 at position 33 unexpected '('."},
            {"SELECT HASH(* EXCLUDE (b) ILIKE 'i%') FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'ILIKE'. | syntax "
                + "error line 1 at position 36 unexpected ')'."},
            {"SELECT HASH(1, * EXCLUDE (id) EXCLUDE (b)) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'EXCLUDE'. | "
                + "syntax error line 1 at position 38 unexpected '('."},
            {"SELECT HASH(* EXCLUDE id RENAME b AS c) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 32 unexpected 'b'."},
            {"SELECT HASH(* RENAME b AS c REPLACE (1 AS id)) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 21 unexpected 'b'."},
            {"SELECT HASH(fz.* EXCLUDE (id) EXCLUDE (b)) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'EXCLUDE'. | "
                + "syntax error line 1 at position 38 unexpected '('."},
            {"SELECT COUNT(* EXCLUDE (id) EXCLUDE (b)) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'EXCLUDE'. | "
                + "syntax error line 1 at position 36 unexpected '('."},
            {"SELECT HASH(* REPLACE (1 AS id) RENAME b AS c) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 22 unexpected '('."},
            {"SELECT HASH(* ILIKE 'i%' REPLACE (1 AS id)) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 33 unexpected '('."},
            {"SELECT * EXCLUDE (id) EXCLUDE (b) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'EXCLUDE'."},
            {"SELECT * EXCLUDE (id) RENAME b AS c FROM fz", "true ;; false"},
            {"BEGIN SELECT HASH(* EXCLUDE (id) EXCLUDE (b)) FROM fz; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'EXCLUDE'. | "
                + "syntax error line 1 at position 41 unexpected '('. | syntax error line 1 at position 43 "
                + "unexpected ')'."},
            {"SELECT OBJECT_CONSTRUCT(* EXCLUDE (id) EXCLUDE (b)) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'EXCLUDE'. | "
                + "syntax error line 1 at position 47 unexpected '('."},
            {"SELECT HASH(DISTINCT * EXCLUDE (id) EXCLUDE (b)) FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'EXCLUDE'. | "
                + "syntax error line 1 at position 44 unexpected '('."},
        });
    }
}
