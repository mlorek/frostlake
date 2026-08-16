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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A QUOTED name resolves to itself and to nothing else. The bare reference already did; three other
 * lookups still folded a quoted part before looking it up, so each of these ACCEPTED SQL the account
 * refuses — the direction that matters most:
 *
 * <pre>
 *   SELECT "kw"."a" FROM kw               the qualified reference, either part quoted
 *   SELECT a FROM kw ORDER BY "a"         an ORDER BY key
 *   SELECT a AS "x" FROM kw ORDER BY x    an unquoted key against a QUOTED alias
 * </pre>
 *
 * <p>The echo has a rule of its own, measured spelling by spelling: a quoted name keeps its quotes only
 * when it NEEDS them. {@code "Q"} echoes as Q, {@code "A_B"} as A_B, {@code "$X"} as $X — all writable
 * without quotes — while {@code "qQ"}, {@code "1A"} and {@code "NO SUCH"} keep them.
 */
public class QuotedReferenceResolutionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE kw (a INT, b INT)");
        engine.execute("INSERT INTO kw VALUES (1, 2)");
    }

    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder("OK");
            while (rs.next()) {
                out.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private void refuses(final String sql, final String echoed) {
        final String answer = outcome(sql);
        assertTrue(answer.contains("invalid identifier '" + echoed + "'"), sql + " => " + answer);
    }

    /** A qualified reference resolves exactly, whichever part carries the quotes. */
    @Test
    public void aQualifiedReferenceResolvesExactly() {
        refuses("SELECT \"kw\".\"a\" FROM kw", "\"kw\".\"a\"");
        refuses("SELECT kw.\"a\" FROM kw", "KW.\"a\"");
        refuses("SELECT \"kw\".a FROM kw", "\"kw\".A");
        refuses("SELECT t.\"a\" FROM kw t", "T.\"a\"");
        refuses("SELECT \"t\".\"a\" FROM kw t", "\"t\".\"a\"");
    }

    /** The spellings that DO name the column still read, so the rule cannot have widened. */
    @Test
    public void theMatchingSpellingsStillRead() {
        assertEquals("OK 1", outcome("SELECT \"KW\".\"A\" FROM kw"));
        assertEquals("OK 1", outcome("SELECT \"T\".\"A\" FROM kw t"));
        assertEquals("OK 1", outcome("SELECT kw.a FROM kw"));
        assertEquals("OK 1", outcome("SELECT t.a FROM kw t"));
        assertEquals("OK 1", outcome("SELECT a FROM kw ORDER BY \"A\""));
        assertEquals("OK 1", outcome("SELECT a FROM kw ORDER BY a"));
    }

    /** An ORDER BY key is a reference like any other — quoted, it resolves exactly. */
    @Test
    public void anOrderByKeyResolvesExactly() {
        refuses("SELECT a FROM kw ORDER BY \"a\"", "\"a\"");
        refuses("SELECT a FROM kw ORDER BY \"b\"", "\"b\"");
        refuses("SELECT a FROM kw ORDER BY \"kw\".\"a\"", "\"kw\".\"a\"");
        refuses("SELECT a FROM kw t ORDER BY \"t\".\"a\"", "\"t\".\"a\"");
    }

    /** WHERE and GROUP BY take a qualified quoted key the same way. */
    @Test
    public void theOtherClausesResolveExactlyToo() {
        refuses("SELECT a FROM kw t WHERE \"t\".\"a\" = 1", "\"t\".\"a\"");
        refuses("SELECT a FROM kw GROUP BY \"kw\".\"a\"", "\"kw\".\"a\"");
        refuses("SELECT COUNT(*) FROM kw GROUP BY \"a\"", "\"a\"");
        refuses("SELECT a FROM kw WHERE \"a\" = 1", "\"a\"");
    }

    /** A QUOTED alias answers to its own spelling only — not to the folded one. */
    @Test
    public void aQuotedAliasAnswersOnlyToItself() {
        assertEquals("OK 1", outcome("SELECT a AS \"x\" FROM kw ORDER BY \"x\""));
        assertEquals("OK 1", outcome("SELECT a AS \"x\" FROM kw WHERE \"x\" = 1"));
        assertEquals("OK 1", outcome("SELECT a AS \"x\" FROM kw GROUP BY \"x\""));
        refuses("SELECT a AS \"x\" FROM kw ORDER BY x", "X");
        refuses("SELECT a AS \"x\" FROM kw ORDER BY X", "X");
        refuses("SELECT a AS \"x\" FROM kw ORDER BY \"X\"", "X");
        refuses("SELECT a AS \"x\" FROM kw GROUP BY x", "X");
    }

    /** And an UNQUOTED alias answers to the folded name, never to a lower-case quoted one. */
    @Test
    public void anUnquotedAliasAnswersToTheFoldedName() {
        assertEquals("OK 1", outcome("SELECT a AS x FROM kw ORDER BY x"));
        assertEquals("OK 1", outcome("SELECT a AS x FROM kw ORDER BY \"X\""));
        refuses("SELECT a AS x FROM kw ORDER BY \"x\"", "\"x\"");
    }

    /** The echo keeps the quotes only when the name could not have been written without them. */
    @Test
    public void theEchoKeepsOnlyTheQuotesItNeeds() {
        refuses("SELECT \"Q\" FROM kw", "Q");
        refuses("SELECT \"A_B\" FROM kw", "A_B");
        refuses("SELECT \"A1\" FROM kw", "A1");
        refuses("SELECT \"$X\" FROM kw", "$X");
        refuses("SELECT \"qQ\" FROM kw", "\"qQ\"");
        refuses("SELECT \"1A\" FROM kw", "\"1A\"");
        refuses("SELECT \"NO SUCH\" FROM kw", "\"NO SUCH\"");
        refuses("SELECT \"Abc\" FROM kw", "\"Abc\"");
        refuses("SELECT a FROM kw ORDER BY \"NO SUCH\"", "\"NO SUCH\"");
    }

    /** An aggregate, a window key and a QUALIFY key all resolve exactly as well. */
    @Test
    public void theWindowAndAggregateKeysResolveExactly() {
        refuses("SELECT SUM(\"a\") FROM kw", "\"a\"");
        refuses("SELECT \"a\" + 1 FROM kw", "\"a\"");
        refuses("SELECT COUNT(*) FROM kw GROUP BY a HAVING SUM(\"b\") > 0", "\"b\"");
        refuses("SELECT a, ROW_NUMBER() OVER (ORDER BY \"a\") FROM kw", "\"a\"");
        refuses("SELECT a FROM kw QUALIFY ROW_NUMBER() OVER (PARTITION BY \"a\" ORDER BY a) = 1",
            "\"a\"");
        refuses("SELECT MAX(a) FROM kw HAVING MAX(\"a\") > 0", "\"a\"");
    }
}
