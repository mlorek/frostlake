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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A star's REPLACE expressions are select-list names of their own: an unknown one is refused at its position,
 * where it stands in the list. A braced star has no REPLACE or RENAME in the grammar, so the keyword is a syntax
 * error and so is the token after it. Each expected answer is the account's own.
 */
public class StarReplacePositionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
    }

    /** The refusal as one line, each line break as |, or ACCEPTED. */
    private String answer(final String sql) {
        try {
            engine.executeQuery(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String invalidAt(final int line, final int position, final String name) {
        return "SQL compilation error: error line " + line + " at position " + position + "|invalid identifier '"
            + name + "'";
    }

    @Test
    public void anUnknownNameInAReplaceIsRefusedWhereItStands() {
        assertEquals(invalidAt(1, 18, "NOSUCH"), answer("SELECT * REPLACE (nosuch AS id) FROM fz"));
        assertEquals(invalidAt(1, 18, "NOSUCH"), answer("SELECT * REPLACE (nosuch + 1 AS id) FROM fz"));
        assertEquals(invalidAt(1, 22, "NOSUCH"), answer("SELECT id, * REPLACE (nosuch AS id) FROM fz"));
        assertEquals(invalidAt(1, 22, "NOSUCH"), answer("SELECT * REPLACE (1 / nosuch AS id) FROM fz"));
        assertEquals(invalidAt(1, 22, "NOSUCH"), answer("SELECT * REPLACE (ABS(nosuch) AS id) FROM fz"));
        assertEquals(invalidAt(1, 28, "NOSUCH"), answer("SELECT * REPLACE (id AS id, nosuch AS b) FROM fz"));
        assertEquals(invalidAt(1, 28, "NOSUCH"), answer("SELECT * EXCLUDE b REPLACE (nosuch AS id) FROM fz"));
        assertEquals(invalidAt(1, 29, "NOSUCH"), answer("SELECT * ILIKE 'i%' REPLACE (nosuch AS id) FROM fz"));
        assertEquals(invalidAt(2, 10, "NOSUCH"), answer("SELECT *\n REPLACE (nosuch AS id) FROM fz"));
        assertEquals(invalidAt(1, 18, "FZ.NOSUCH"), answer("SELECT * REPLACE (fz.nosuch AS id) FROM fz"));
        assertEquals(invalidAt(1, 18, "X.ID"), answer("SELECT * REPLACE (x.id AS id) FROM fz"));
    }

    @Test
    public void theReplaceNamesAreTheListsOwnInWrittenOrder() {
        assertEquals(invalidAt(1, 18, "NOSUCH"), answer("SELECT * REPLACE (nosuch AS id), nosuch2 FROM fz"));
        assertEquals(invalidAt(1, 7, "NOSUCH2"), answer("SELECT nosuch2, * REPLACE (nosuch AS id) FROM fz"));
        assertEquals(invalidAt(1, 22, "NOSUCH"), answer("SELECT * REPLACE (SUM(nosuch) AS id) FROM fz"));
        assertEquals(invalidAt(1, 40, "NOSUCH"),
            answer("SELECT COUNT(*) FROM (SELECT * REPLACE (nosuch AS id) FROM fz)"));
        assertEquals(invalidAt(1, 21, "NOSUCH"), answer("SELECT fz.* REPLACE (nosuch AS id) FROM fz"));
        assertEquals(invalidAt(1, 20, "NOSUCH"), answer("SELECT f.* REPLACE (nosuch AS id) FROM fz f"));
    }

    @Test
    public void aBracedStarsReplaceOrRenameIsASyntaxErrorTwice() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 10 unexpected 'REPLACE'.|"
                + "syntax error line 1 at position 18 unexpected '('.",
            answer("SELECT {* REPLACE (nosuch AS id)} FROM fz"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 10 unexpected 'REPLACE'.|"
                + "syntax error line 1 at position 18 unexpected '('.",
            answer("SELECT {* REPLACE (id AS id)} FROM fz"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 10 unexpected 'RENAME'.|"
                + "syntax error line 1 at position 17 unexpected '('.",
            answer("SELECT {* RENAME (id AS x)} FROM fz"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 10 unexpected 'RENAME'.|"
                + "syntax error line 1 at position 17 unexpected 'id'.",
            answer("SELECT {* RENAME id AS x} FROM fz"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'REPLACE'.|"
                + "syntax error line 1 at position 28 unexpected '('.",
            answer("SELECT {* EXCLUDE b REPLACE (1 AS id)} FROM fz"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 13 unexpected 'REPLACE'.|"
                + "syntax error line 1 at position 21 unexpected '('.",
            answer("SELECT {fz.* REPLACE (1 AS id)} FROM fz"));
    }
}
