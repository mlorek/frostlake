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
 * A star call's RENAME or REPLACE in the item of a derived table's or a CTE's query, read as the item's alias: after
 * a derived table's bracket a lone name or an aliased literal stacks the bracket's ')', a list the token after its
 * first comma, a lone literal or a text nothing of its own and the statement around is read on; AS and a name
 * stack the call's ')', a braced name its '}'; a CTE's bracketed query stacks the token after it, and a CTE's
 * braced REPLACE its first token and the '}' (all live-verified).
 */
public class DerivedStarModifierLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void itemsOfDerivedTablesAndCtes() {
        engine.execute("CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 37 unexpected '('. | syntax error line 1 at position 45 unexpected "
                + "')'.",
            "SELECT a FROM (SELECT HASH(* REPLACE (1 AS id)) a FROM FZ)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 37 unexpected '('. | syntax error line 1 at position 41 unexpected "
                + "'2'.",
            "SELECT a FROM (SELECT HASH(* REPLACE (1, 2)) a FROM FZ)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 37 unexpected '('. | syntax error line 1 at position 61 unexpected "
                + "'y'.",
            "SELECT a FROM (SELECT HASH(* REPLACE (1)) a FROM FZ) WHERE x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 37 unexpected ''x''. | syntax error line 1 at position 61 "
                + "unexpected 'b'.",
            "SELECT a FROM (SELECT HASH(* REPLACE 'x') a FROM FZ) WHERE a b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 37 unexpected 'AS'. | syntax error line 1 at position 41 "
                + "unexpected ')'.",
            "SELECT a FROM (SELECT HASH(* REPLACE AS k) a FROM FZ) WHERE x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 33 unexpected '('. | syntax error line 1 at position 43 unexpected "
                + "')'.",
            "WITH c AS (SELECT HASH(* REPLACE (SELECT 1)) h FROM FZ) SELECT * FROM c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 33 unexpected 'x'. | syntax error line 1 at position 34 unexpected "
                + "'}'.",
            "SELECT a FROM (SELECT {* REPLACE x} a FROM FZ) WHERE a b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 29 unexpected '('. | syntax error line 1 at position 30 unexpected "
                + "'1'. | syntax error line 1 at position 38 unexpected '}'.",
            "WITH c AS (SELECT {* REPLACE (1 AS id)} h FROM FZ) SELECT * FROM c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 18 unexpected '('.",
            "SELECT (SELECT MAX(id) FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1) FROM FZ");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 37 unexpected '('. | syntax error line 1 at position 40 unexpected "
                + "')'.",
            "SELECT a FROM (SELECT HASH(* REPLACE (id)) a FROM FZ)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 37 unexpected '('. | syntax error line 1 at position 47 unexpected "
                + "'2'.",
            "SELECT a FROM (SELECT HASH(* REPLACE (1 AS id, 2 AS b)) a FROM FZ)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 33 unexpected '('. | syntax error line 1 at position 41 unexpected "
                + "')'.",
            "SELECT a FROM (SELECT {* REPLACE (1 AS id)} a FROM FZ)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 33 unexpected '('. | syntax error line 1 at position 36 unexpected "
                + "')'.",
            "SELECT a FROM (SELECT {* REPLACE (id)} a FROM FZ)");
    }
}
