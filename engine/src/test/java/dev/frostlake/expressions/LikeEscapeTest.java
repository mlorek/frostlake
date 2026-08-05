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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIKE ... ESCAPE with a custom escape character: the escape makes the following %, _ (or the escape
 * char itself) a literal. Previously the ESCAPE clause was dropped and evaluation always assumed the
 * default backslash, so a custom escape silently did nothing. A backslash escape must arrive as SQL
 * {@code '\\_'} with {@code ESCAPE '\\'} — a single {@code '\_'} is consumed by the string-literal
 * decode ({@code \_} &rarr; {@code _}) before LIKE ever sees it, exactly as in Snowflake.
 */
public class LikeEscapeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (id INTEGER)");
        engine.execute("INSERT INTO t VALUES (1)");
    }

    private boolean like(final String predicate) {
        return (Boolean) engine.executeQuery("SELECT (" + predicate + ") AS r FROM t").getRows().get(0).getValue(0);
    }

    @Test
    public void customEscapeMakesWildcardLiteral() {
        assertTrue(like("'a%b' LIKE 'a!%b' ESCAPE '!'"));
        assertFalse(like("'axb' LIKE 'a!%b' ESCAPE '!'"));
    }

    @Test
    public void customEscapeAfterLeadingWildcard() {
        assertTrue(like("'100%' LIKE '%!%' ESCAPE '!'"));
    }

    @Test
    public void escapedEscapeCharIsLiteral() {
        assertTrue(like("'a!b' LIKE 'a!!b' ESCAPE '!'"));
    }

    @Test
    public void singleBackslashIsConsumedByTheLiteralDecode() {
        // SQL 'a\_b' decodes to 'a_b' — the backslash never reaches LIKE, so _ stays a wildcard and
        // BOTH candidates match.
        assertTrue(like("'a_b' LIKE 'a\\_b'"));
        assertTrue(like("'axb' LIKE 'a\\_b'"));
    }

    @Test
    public void backslashEscapeNeedsTheDoubledForm() {
        // A literal underscore needs SQL 'a\\_b' ESCAPE '\\' (Java source: four backslashes): the
        // decode leaves pattern a\_b and escape \, so _ is literal.
        assertTrue(like("'a_b' LIKE 'a\\\\_b' ESCAPE '\\\\'"));
        assertFalse(like("'axb' LIKE 'a\\\\_b' ESCAPE '\\\\'"));
    }

    @Test
    public void plainWildcardsUnaffected() {
        assertTrue(like("'abc' LIKE 'a%'"));
        assertTrue(like("'C:x' LIKE 'C:%'"));
    }

    @Test
    public void notLikeHonorsEscape() {
        assertTrue(like("'axb' NOT LIKE 'a!%b' ESCAPE '!'"));
        assertFalse(like("'a%b' NOT LIKE 'a!%b' ESCAPE '!'"));
    }
}
