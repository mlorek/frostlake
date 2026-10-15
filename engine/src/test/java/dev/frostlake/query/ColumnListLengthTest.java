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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A CTE's column list names exactly the columns its query produces, recursive or not and whether or not the
 * statement reads it; a FROM item's alias list may rename fewer columns than the item has, never more. Either
 * mismatch is refused by the relation's name (live-verified).
 */
public class ColumnListLengthTest extends BaseDatabaseTest {

    private static String mismatch(final String relation) {
        return "SQL compilation error:|Mismatch between number of columns produced by '" + relation
            + "' and the number of aliases specified.";
    }

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE a (k INT, v INT)");
        engine.execute("INSERT INTO a VALUES (1, 10)");
    }

    @Test
    public void aCteListOfAnotherLengthIsRefused() {
        assertCells(engine, new String[][] {
            {"WITH c (p) AS (SELECT k, v FROM a) SELECT * FROM c", mismatch("C")},
            {"WITH c (p) AS (SELECT k, v FROM a) SELECT k FROM c", mismatch("C")},
            {"WITH c (p, q, r) AS (SELECT k, v FROM a) SELECT * FROM c", mismatch("C")},
            {"WITH c (p) AS (SELECT * FROM a) SELECT * FROM c", mismatch("C")},
            {"WITH RECURSIVE c (n, m) AS (SELECT 1 UNION ALL SELECT n + 1 FROM c WHERE n < 3) SELECT * FROM c",
                mismatch("C")},
            {"WITH RECURSIVE c (n) AS (SELECT 1, 2 UNION ALL SELECT n + 1, 2 FROM c WHERE n < 3) SELECT * FROM c",
                mismatch("C")},
            {"WITH \"c\" (p) AS (SELECT 1, 2) SELECT * FROM \"c\"", mismatch("c")},
            {"WITH c (p) AS (SELECT 1, 2) SELECT 1", mismatch("C")},
            {"WITH c (p, q) AS (SELECT 1 AS k, 2 AS v), e (z) AS (SELECT 1, 2) SELECT * FROM c", mismatch("E")},
            {"WITH c (p, q) AS (SELECT k, v FROM a) SELECT * FROM c", "1, 10"},
        });
    }

    @Test
    public void aFromItemListMayRenameFewerColumnsButNotMore() {
        assertCells(engine, new String[][] {
            {"SELECT * FROM (SELECT k, v FROM a) d (p, q, r)", mismatch("D")},
            {"SELECT * FROM a d (p, q, r)", mismatch("D")},
            {"SELECT * FROM (VALUES (1, 2)) v (p, q, r)", mismatch("V")},
            {"SELECT * FROM TABLE(FLATTEN(INPUT => [1])) f (a, b, c, d, e, f, g)", mismatch("F")},
            {"SELECT * FROM (SELECT 1 AS k, 2 AS v) AS \"d\" (p, q, r)", mismatch("d")},
            {"SELECT * FROM (SELECT k, v FROM a) d (p)", "1, 10"},
            {"SELECT p, v FROM (SELECT k, v FROM a) d (p)", "1, 10"},
            {"SELECT * FROM a d (p)", "1, 10"},
            {"SELECT * FROM (VALUES (1, 2)) v (p)", "1, 2"},
        });
    }
}
