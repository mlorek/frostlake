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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A scalar subquery in a select item that returns two columns is refused for its column count only once every
 * name in it resolves (live-verified): an unknown name in its WHERE, GROUP BY, ORDER BY or HAVING is named
 * first, at its place in the statement, as one in its select list is.
 */
public class MultiColumnSubqueryNameTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (1, 10), (5, 50)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED " + rs.getRowCount();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String badName(final int position) {
        return "SQL compilation error: error line 1 at position " + position + "|invalid identifier 'NOSUCH'";
    }

    /** A name in any clause of the subquery outranks its column count. */
    @Test
    public void aNameInAnyClauseComesBeforeTheColumnCount() {
        assertEquals(badName(41), answer("SELECT id, (SELECT fz.id, 1 FROM G WHERE nosuch = 1) AS x FROM FZ ORDER BY id"));
        assertEquals(badName(44), answer("SELECT id, (SELECT fz.id, 1 FROM G GROUP BY nosuch) AS x FROM FZ ORDER BY id"));
        assertEquals(badName(44), answer("SELECT id, (SELECT fz.id, 1 FROM G ORDER BY nosuch) AS x FROM FZ ORDER BY id"));
        assertEquals(badName(42), answer("SELECT id, (SELECT fz.id, 1 FROM G HAVING nosuch = 1) AS x FROM FZ ORDER BY id"));
    }

    /** In its select list too, whichever item it is. */
    @Test
    public void aNameInTheSelectListComesFirst() {
        assertEquals(badName(19), answer("SELECT id, (SELECT nosuch, 1) AS x FROM FZ ORDER BY id"));
        assertEquals(badName(22), answer("SELECT id, (SELECT 1, nosuch) AS x FROM FZ ORDER BY id"));
    }

    /** With every name resolving, the column count is what is refused. */
    @Test
    public void theColumnCountIsRefusedWhenTheNamesResolve() {
        assertEquals("SQL compilation error: error line 1 at position 12|Unsupported: Scalar subquery with multi-column SELECT clause.",
            answer("SELECT id, (SELECT fz.id, 1 FROM G) AS x FROM FZ ORDER BY id"));
    }
}
