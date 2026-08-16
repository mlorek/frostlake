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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Which keyword words may be a BARE (AS-less) table alias — the widest name position there is, and the
 * one Frostlake was narrowest in: it kept a hand-curated list of about a hundred words while the column
 * position had grown to over four hundred, so {@code FROM t limit} was a syntax error where live runs it.
 *
 * <p>The rule is now "anything that may name a column, less the words that LEAD something in a FROM
 * clause". Those ELEVEN are the interesting half — each would swallow the clause it starts, and they
 * were measured one by one rather than reasoned about:
 *
 * <pre>
 *   FROM t left      refused live — LEFT starts a join, not an alias
 *   FROM t current_date   ACCEPTED live, though a COLUMN may not be called current_date
 *   FROM t break     ACCEPTED live — a scripting word is an ordinary name HERE
 * </pre>
 *
 * <p>The list held FIFTEEN until BREAK, CONTINUE, RAISE and RETURN were re-measured over a raw
 * connection. They are ordinary aliases, and `SELECT break.x FROM t break` resolves; the earlier
 * reading came from a harness that split `SELECT x FROM t break` in two before submitting it, so the
 * account was asked about a bare `break` and refused THAT. Standing alone as a statement they are
 * still refused — a different rule, and one that stayed correct.
 */
public class BareTableAliasKeywordTest extends BaseDatabaseTest {

    /** One word from each family the fix recovered, all measured legal as a bare alias. */
    private static final String[] ALLOWED = {
        "limit", "top", "auto", "primary", "foreign", "show", "merge", "call", "commit", "declare",
        "let", "loop", "while", "until", "open", "fetch", "int", "varchar", "float", "boolean",
        "variant", "object", "array", "task", "pipe", "sequence", "procedure", "function", "view",
        "materialized", "transient", "over", "within", "apply", "at", "asc", "desc", "end", "if",
        "true", "false", "no", "read", "write", "share", "usage", "monitor", "operate", "ownership",
        "current_date", "current_time", "current_timestamp", "current_user", "case", "cast",
        "constraint", "default", "when", "try_cast", "prior", "connect_by_root", "except",
        // The four scripting words: ordinary names here, measured raw.
        "break", "continue", "raise", "return",
    };

    /** The words that lead something in a FROM clause, and so cannot be a bare alias. */
    private static final String[] REFUSED = {
        "asof", "cross", "full", "inner", "join", "lateral", "left", "match_condition", "natural",
        "right", "using", "rlike", "into", "unique",
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE bta (x NUMBER)");
        engine.execute("INSERT INTO bta VALUES (7)");
    }

    /** Every recovered word aliases the table, and the alias is usable as a qualifier. */
    @Test
    public void everyRecoveredKeywordIsABareAlias() {
        for (final String word : ALLOWED) {
            final ResultSet rs = engine.executeQuery("SELECT x FROM bta " + word);
            rs.next();
            assertEquals(7, ((Number) rs.getValue("x")).intValue(), word + " failed as a bare alias");
        }
    }

    /** And the alias really names the table — a qualified reference through it resolves. */
    @Test
    public void theBareAliasCanQualifyAColumn() {
        final ResultSet rs = engine.executeQuery("SELECT limit.x FROM bta limit");
        rs.next();
        assertEquals(7, ((Number) rs.getValue("x")).intValue());
    }

    /** A word that leads something in the FROM clause is still refused, as live refuses it. */
    @Test
    public void aClauseLeadingKeywordIsNotABareAlias() {
        for (final String word : REFUSED) {
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT x FROM bta " + word);
                }
            }, word + " should not be usable as a bare alias");
        }
    }

    /** The AS spelling still works for the same words, which is a different rule in the grammar. */
    @Test
    public void theAsSpellingStillWorks() {
        final ResultSet rs = engine.executeQuery("SELECT x FROM bta AS auto");
        rs.next();
        assertEquals(7, ((Number) rs.getValue("x")).intValue());
    }

    /** RLIKE stays a FUNCTION name — removing it from the alias list must not remove the call. */
    @Test
    public void rlikeIsStillAFunction() {
        final ResultSet rs = engine.executeQuery("SELECT RLIKE('abc', 'a.*') AS c FROM bta");
        rs.next();
        assertEquals(Boolean.TRUE, rs.getValue("c"));
    }
}
