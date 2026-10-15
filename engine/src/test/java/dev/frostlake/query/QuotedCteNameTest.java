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

import static dev.frostlake.query.QueryAnswers.assertCells;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A CTE's name is an identifier: a quoted one keeps its case and is found only by the same quoted name, an unquoted
 * one folds to upper case and is found by any spelling of that; a CTE shadows a table of its name, the first of two
 * CTEs of one name wins, and a CTE that reads itself under a name that is not all upper case is refused
 * (live-verified).
 */
public class QuotedCteNameTest extends BaseDatabaseTest {

    @Test
    public void aQuotedNameFindsItsOwnCte() {
        assertCells(engine, new String[][] {
            {"WITH \"c\" AS (SELECT 1 AS x) SELECT * FROM \"c\"", "1"},
            {"WITH \"My Cte\" AS (SELECT 1 AS x) SELECT * FROM \"My Cte\"", "1"},
            {"WITH \"c\" AS (SELECT 1 AS x), c AS (SELECT 2 AS x) SELECT (SELECT x FROM \"c\") AS lower_c,"
                + " (SELECT x FROM c) AS bare_c", "1, 2"},
            {"WITH c AS (SELECT 1 AS x) SELECT * FROM \"C\"", "1"},
            {"WITH \"C\" AS (SELECT 1 AS x) SELECT * FROM c", "1"},
            {"WITH \"c\" AS (SELECT 1 AS x) SELECT \"c\".x FROM \"c\"", "1"},
            {"WITH \"c\" AS (SELECT 1 AS x) SELECT t.x FROM \"c\" t", "1"},
            {"WITH \"a b\" AS (SELECT 1 AS x), d AS (SELECT x + 1 AS y FROM \"a b\") SELECT * FROM d", "2"},
            {"WITH \"c\" AS (SELECT 1 AS x) SELECT * FROM \"c\" JOIN \"c\" c2 ON TRUE", "1, 1"},
            {"WITH \"c\" AS (SELECT 1 AS x), \"C\" AS (SELECT 2 AS x) SELECT (SELECT x FROM \"c\"),"
                + " (SELECT x FROM \"C\"), (SELECT x FROM c)", "1, 2, 2"},
            {"WITH c AS (SELECT 1 AS x), c AS (SELECT 2 AS x) SELECT * FROM c", "1"},
            {"WITH \"c\" AS (SELECT 1 AS x), \"c\" AS (SELECT 2 AS x) SELECT * FROM \"c\"", "1"},
        });
    }

    @Test
    public void anotherSpellingIsAnotherName() {
        final String[][] cells = {
            {"WITH \"c\" AS (SELECT 1 AS x) SELECT * FROM c", "SQL compilation error:|Object 'C' does not exist"},
            {"WITH c AS (SELECT 1 AS x) SELECT * FROM \"c\"", "SQL compilation error:|Object '\"c\"' does not exist"},
            {"WITH \"c\" AS (SELECT 1 AS x) SELECT c.x FROM \"c\"",
                "SQL compilation error: error line 1 at position 35|invalid identifier 'C.X'"},
        };
        for (final String[] cell : cells) {
            final String answer = QueryAnswers.answer(engine, cell[0]);
            assertTrue(answer.startsWith(cell[1]), cell[0] + " => " + answer);
        }
    }

    @Test
    public void aCteShadowsATableOfItsName() {
        engine.execute("CREATE TABLE \"c\" (x INT)");
        engine.execute("INSERT INTO \"c\" VALUES (9)");
        assertCells(engine, new String[][] {
            {"WITH \"c\" AS (SELECT 1 AS x) SELECT * FROM \"c\"", "1"},
            {"WITH c AS (SELECT 1 AS x) SELECT * FROM \"c\"", "9"},
        });
    }

    @Test
    public void aSelfReadingCteNeedsAnUpperCaseName() {
        assertCells(engine, new String[][] {
            {"WITH RECURSIVE \"r\" (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM \"r\" WHERE n < 3) SELECT n FROM \"r\""
                + " ORDER BY n", "SQL compilation error:|'r' is an invalid Recursive CTE"},
            {"WITH \"r\" (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM \"r\" WHERE n < 3) SELECT n FROM \"r\""
                + " ORDER BY n", "SQL compilation error:|'r' is an invalid Recursive CTE"},
            {"WITH RECURSIVE \"My R\" (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM \"My R\" WHERE n < 3) SELECT n"
                + " FROM \"My R\" ORDER BY n", "SQL compilation error:|'My R' is an invalid Recursive CTE"},
            {"WITH RECURSIVE \"r\" (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM \"r\" WHERE n < 3) SELECT 1",
                "SQL compilation error:|'r' is an invalid Recursive CTE"},
            {"WITH RECURSIVE \"R\" (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM \"R\" WHERE n < 3) SELECT n FROM \"R\""
                + " ORDER BY n", "1 | 2 | 3"},
            {"WITH RECURSIVE \"R\" (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT n FROM r"
                + " ORDER BY n", "1 | 2 | 3"},
            {"WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT n FROM \"R\""
                + " ORDER BY n", "1 | 2 | 3"},
            {"WITH RECURSIVE \"c\" AS (SELECT 1 AS x) SELECT * FROM \"c\"", "1"},
        });
    }
}
