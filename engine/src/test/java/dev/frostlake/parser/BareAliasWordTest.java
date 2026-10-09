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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The words a select item takes as its alias WITHOUT AS, measured word by word: CASE, CAST, CONSTRAINT, CROSS,
 * DEFAULT, INNER, JOIN and WHEN each name the item, although none of them may open or name a column in an
 * expression — so a CASE expression closed with END CASE is aliased CASE. TRIGGER and WHENEVER are words the
 * account keeps for itself wherever a name may stand, and OVER after an item's expression opens a window and is
 * never its alias (live-verified).
 */
public class BareAliasWordTest extends BaseDatabaseTest {

    private static final String[] ALIAS_WORDS = {
        "case", "cast", "constraint", "cross", "default", "inner", "join", "when",
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE bw (x NUMBER)");
        engine.execute("INSERT INTO bw VALUES (7)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private static String unexpected(final int position, final String token) {
        return "SQL compilation error:\nsyntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void eachWordIsABareSelectAlias() {
        for (final String word : ALIAS_WORDS) {
            final ResultSet fromTable = engine.executeQuery("SELECT x " + word + " FROM bw");
            assertTrue(fromTable.next());
            assertEquals(7, ((Number) fromTable.getValue(word)).intValue(), word);
            final ResultSet alone = engine.executeQuery("SELECT 1 " + word);
            assertTrue(alone.next());
            assertEquals(word.toUpperCase(), alone.getColumns().get(0).getName());
        }
        final ResultSet several = engine.executeQuery("SELECT 1 CASE, 2 WHEN, 3 CAST");
        assertTrue(several.next());
        assertEquals("1|2|3", several.getValue(0) + "|" + several.getValue(1) + "|" + several.getValue(2));
    }

    @Test
    public void aCaseExpressionMayBeAliasedCase() {
        final ResultSet alone = engine.executeQuery("SELECT CASE WHEN TRUE THEN 1 END CASE");
        assertTrue(alone.next());
        assertEquals("CASE", alone.getColumns().get(0).getName());
        assertEquals(1, ((Number) alone.getValue(0)).intValue());
        final ResultSet simple = engine.executeQuery("SELECT CASE x WHEN 7 THEN 'a' END CASE FROM bw");
        assertTrue(simple.next());
        assertEquals("a", simple.getValue(0));
        engine.execute("CREATE OR REPLACE VIEW bw_v AS SELECT CASE WHEN TRUE THEN 1 END CASE");
        final ResultSet view = engine.executeQuery("SELECT * FROM bw_v");
        assertTrue(view.next());
        assertEquals(1, ((Number) view.getValue(0)).intValue());
        engine.execute("CREATE OR REPLACE FUNCTION bw_u(x INT) RETURNS INT AS"
            + " $$ SELECT CASE WHEN x = 1 THEN 10 ELSE 20 END CASE $$");
        final ResultSet called = engine.executeQuery("SELECT bw_u(1)");
        assertTrue(called.next());
        assertEquals(10, ((Number) called.getValue(0)).intValue());
    }

    @Test
    public void theWordIsTheAliasWhereverItFollowsAnItem() {
        assertEquals(unexpected(15, "JOIN"), refusal("SELECT 1 INNER JOIN bw"));
        assertEquals(unexpected(15, "JOIN"), refusal("SELECT 1 CROSS JOIN bw"));
        assertEquals(unexpected(14, "bw"), refusal("SELECT 1 JOIN bw WHERE a b"));
        assertEquals(unexpected(14, "WHEN"), refusal("SELECT 1 CASE WHEN"));
        assertEquals(unexpected(14, "THEN"), refusal("SELECT 1 WHEN THEN"));
        assertEquals(unexpected(13, "("), refusal("SELECT 1 CAST(2 AS INT)"));
        assertEquals(unexpected(17, "("), refusal("SELECT 1 DEFAULT (2)"));
        assertEquals(unexpected(19, "u"), refusal("SELECT x OR y WHEN u"));
        assertEquals(unexpected(19, "u"), refusal("SELECT x OR y JOIN u"));
        assertEquals(unexpected(20, "u"), refusal("SELECT x OR y INNER u"));
        assertEquals(unexpected(19, "u"), refusal("SELECT x OR y CASE u"));
        final ResultSet named = engine.executeQuery("SELECT 1 JOIN FROM bw");
        assertEquals("JOIN", named.getColumns().get(0).getName());
        final ResultSet inner = engine.executeQuery("SELECT 1 INNER FROM bw");
        assertEquals("INNER", inner.getColumns().get(0).getName());
    }

    @Test
    public void triggerAndWheneverAreKeptWordsEverywhere() {
        assertEquals(unexpected(9, "TRIGGER"), refusal("SELECT 1 TRIGGER"));
        assertEquals(unexpected(9, "WHENEVER"), refusal("SELECT 1 WHENEVER"));
        assertEquals(unexpected(12, "TRIGGER"), refusal("SELECT 1 AS TRIGGER"));
        assertEquals(unexpected(12, "WHENEVER"), refusal("SELECT 1 AS WHENEVER"));
        assertEquals(unexpected(17, "TRIGGER"), refusal("SELECT * FROM bw TRIGGER"));
        assertEquals(unexpected(20, "WHENEVER"), refusal("SELECT * FROM bw AS WHENEVER"));
        assertEquals(unexpected(30, "TRIGGER"), refusal("CREATE OR REPLACE TABLE kw_c (TRIGGER INT)"));
        assertEquals(unexpected(24, "WHENEVER"), refusal("CREATE OR REPLACE TABLE WHENEVER (a INT)"));
        assertEquals(unexpected(7, "TRIGGER"), refusal("SELECT TRIGGER FROM (SELECT 1 AS \"TRIGGER\")"));
        assertEquals(unexpected(9, "WHENEVER"), refusal("SELECT s.WHENEVER FROM (SELECT 1 AS \"WHENEVER\") s"));
        final ResultSet quoted = engine.executeQuery("SELECT \"TRIGGER\" FROM (SELECT 1 AS \"TRIGGER\")");
        assertTrue(quoted.next());
        assertEquals(1, ((Number) quoted.getValue(0)).intValue());
    }

    @Test
    public void overIsNoBareSelectAlias() {
        assertTrue(refusal("SELECT 1 OVER").contains("syntax error"), "SELECT 1 OVER");
        assertTrue(refusal("SELECT x OVER FROM bw").contains("syntax error"), "SELECT x OVER FROM bw");
        final ResultSet withAs = engine.executeQuery("SELECT 1 AS over");
        assertEquals("OVER", withAs.getColumns().get(0).getName());
        final ResultSet table = engine.executeQuery("SELECT over.x FROM bw over");
        assertTrue(table.next());
        assertEquals(7, ((Number) table.getValue(0)).intValue());
    }

    @Test
    public void aTableNamedByAKeywordIsNamedOnceInItsStatus() {
        final ResultSet created = engine.executeQuery("CREATE OR REPLACE TABLE case (a INT)");
        assertTrue(created.next());
        assertEquals("Table CASE successfully created.", created.getValue(0));
        final ResultSet joined = engine.executeQuery("CREATE OR REPLACE TABLE join (a INT)");
        assertTrue(joined.next());
        assertEquals("Table JOIN successfully created.", joined.getValue(0));
        engine.execute("DROP TABLE case");
        engine.execute("DROP TABLE join");
    }
}
