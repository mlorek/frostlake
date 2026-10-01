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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A correlated subquery live cannot evaluate is refused while the statement compiles, even where no row reaches it,
 * when a constant decides that its branch of a conditional is taken: IFF's and a searched CASE's chosen branch,
 * NVL2's, and the operands of COALESCE, NVL and IFNULL up to the first that folds to a value. A branch a constant
 * leaves untaken is never judged. NULLIF and ZEROIFNULL evaluate every argument, so a subquery in one is judged
 * like any other (all live-verified).
 */
public class ConstantConditionSubqueryJudgementTest extends BaseDatabaseTest {

    private static final String SUBQUERY = "(SELECT v FROM g WHERE g.id = fz.id)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    private int rowCount(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        return result.getRowCount();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String unsupportedAt(final int position) {
        return "SQL compilation error:\nUnsupported subquery type cannot be evaluated at line 1, position " + position;
    }

    @Test
    public void aTakenBranchIsJudgedWithTheStatement() {
        assertEquals(unsupportedAt(26), refusal("SELECT id, IFF(1 = 0, 1, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(26), refusal("SELECT id, IFF(FALSE, 1, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(25), refusal("SELECT id, IFF(NULL, 1, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(30), refusal("SELECT id, IFF('a' = 'b', 1, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(40),
            refusal("SELECT id, CASE WHEN 1 = 0 THEN 1 ELSE " + SUBQUERY + " END AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(50),
            refusal("SELECT id, CASE WHEN FALSE THEN 1 WHEN TRUE THEN " + SUBQUERY + " END AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(33), refusal("SELECT id, CASE WHEN 1 = 1 THEN " + SUBQUERY + " END AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(22), refusal("SELECT id, NVL(NULL, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(25), refusal("SELECT id, IFNULL(NULL, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(33), refusal("SELECT id, COALESCE(NULL, NULL, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(26), refusal("SELECT id, NVL2(NULL, 1, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(20), refusal("SELECT id, NVL2(1, " + SUBQUERY + ", 2) AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(36),
            refusal("SELECT id, IFF(1 = 0, 1, IFF(TRUE, " + SUBQUERY + ", 2)) AS x FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(19), refusal("SELECT id, NULLIF(" + SUBQUERY + ", 1) AS x FROM fz WHERE 1 = 0"));
    }

    @Test
    public void anUntakenBranchIsNeverJudged() {
        assertEquals(0, rowCount("SELECT id, IFF(1 = 1, 1, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(0, rowCount("SELECT id, IFF(TRUE, 1, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(0, rowCount("SELECT id, IFF(1 = 0, " + SUBQUERY + ", 1) AS x FROM fz WHERE 1 = 0"));
        assertEquals(0, rowCount("SELECT id, CASE WHEN 1 = 1 THEN 1 ELSE " + SUBQUERY + " END AS x FROM fz WHERE 1 = 0"));
        assertEquals(0, rowCount("SELECT id, COALESCE(1, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(0, rowCount("SELECT id, COALESCE(NULL, 2, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(0, rowCount("SELECT id, NVL(1, " + SUBQUERY + ") AS x FROM fz WHERE 1 = 0"));
        // A condition no constant decides stays with the row: the statistics settle it here.
        assertEquals(2, rowCount("SELECT id, IFF(id > 0, 1, " + SUBQUERY + ") AS x FROM fz ORDER BY id"));
        assertEquals(0, rowCount("SELECT id, CASE WHEN id > 0 THEN 1 ELSE " + SUBQUERY + " END AS x FROM fz WHERE 1 = 0"));
    }
}
