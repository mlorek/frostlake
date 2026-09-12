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
 * A digit run that OPENS an exponent and then stops — {@code 1e}, {@code 12E}, {@code 1.5e},
 * {@code 1e+} — is a malformed NUMBER, not a number beside an identifier. Frostlake lexed
 * {@code SELECT 1e FROM t} as the integer 1 followed by the identifier {@code e}, read that as an
 * alias, and answered a column named E: SQL a real account refuses, accepted silently.
 *
 * <p>The distinction is ADJACENCY, and the spaced forms are the control that proves it — {@code
 * SELECT 1 e FROM t} really does name the column E and must keep doing so. Two more cells fix the
 * boundary from the other side:
 *
 * <pre>
 *   SELECT 1d FROM t     names the column D — only e and E open an exponent, no other letter does
 *   SELECT 1e1e FROM t   answers 10 named E — the number takes 1e1 and the trailing e is the alias
 * </pre>
 *
 * <p>The fix is a lexer token no parser rule uses, so every occurrence is a syntax error. Longest
 * match is what keeps the valid forms whole: FLOAT_LITERAL still takes {@code 1e5} and {@code 1.5e5}
 * entire, and in {@code 1e1e} it takes {@code 1e1} and leaves the {@code e}.
 *
 * <p>The REFUSAL WORDING is asserted in {@code parser/MalformedNumberRefusalTest}, not here. Its first
 * sentence — which names the character FOLLOWING the malformed number by its DECIMAL CODE, at that
 * character's own position — now matches live exactly. Its second sentence, an ordinary syntax error
 * that live stacks on only some statements, does not; that half is a model of live's parser recovery
 * and is tracked on its own. This file keeps asserting the FACT of the refusal, which is what it is
 * about, so a wording change cannot silently turn one of these cells into an acceptance.
 */
public class TruncatedExponentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ex (i INT)");
        engine.execute("INSERT INTO ex VALUES (7)");
    }

    /** The first column's name and first row's value, joined, or the refusal. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.getColumns().get(0).getName() + "="
                + (rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>");
        } catch (final RuntimeException refused) {
            return "ERR " + String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Whether the statement is refused as a compilation error. */
    private void refused(final String sql) {
        final String answer = outcome(sql);
        assertTrue(answer.startsWith("ERR SQL compilation error:"), sql + " => " + answer);
    }

    /** The reported shape, and its every spelling. */
    @Test
    public void aTruncatedExponentIsRefused() {
        refused("SELECT 1e FROM ex");
        refused("SELECT 1E FROM ex");
        refused("SELECT 12e FROM ex");
        refused("SELECT 0e FROM ex");
        refused("SELECT 1.5e FROM ex");
    }

    /** A sign with no digits after it is truncated just the same. */
    @Test
    public void aDanglingSignIsRefusedToo() {
        refused("SELECT 1e+ FROM ex");
        refused("SELECT 1e- FROM ex");
        refused("SELECT 1.5e+ FROM ex");
    }

    /** It is refused wherever it is written, not only where an alias could follow. */
    @Test
    public void everyPositionRefusesIt() {
        refused("SELECT 1e AS a FROM ex");
        refused("SELECT i FROM ex WHERE 1e = 1");
        refused("SELECT i FROM ex WHERE i = 1e");
        refused("SELECT (1e) FROM ex");
        refused("SELECT 1e + 1 FROM ex");
        refused("SELECT 1e");
        refused("SELECT 1ea FROM ex");
    }

    /** Every VALID exponent still parses and still computes. */
    @Test
    public void theValidExponentsAreUnchanged() {
        assertEquals("1E5=100000", outcome("SELECT 1e5 FROM ex"));
        assertEquals("1.5E5=150000", outcome("SELECT 1.5e5 FROM ex"));
        assertEquals("1E-5=0.00001", outcome("SELECT 1e-5 FROM ex"));
    }

    /**
     * And the SPACED forms, which are what the malformed ones were being mistaken for. These are the
     * cells that stop the fix from being "refuse a digit followed by a letter".
     */
    @Test
    public void aSpacedIdentifierIsStillAnAlias() {
        assertEquals("E=1", outcome("SELECT 1 e FROM ex"));
        assertEquals("E=1", outcome("SELECT 1 E FROM ex"));
        assertEquals("E=1", outcome("SELECT 1 AS e FROM ex"));
        assertEquals("D=1", outcome("SELECT 1d FROM ex"),
            "no other letter opens an exponent, so this one is still a number and an alias");
        assertEquals("E=10", outcome("SELECT 1e1e FROM ex"),
            "the number takes 1e1 and the trailing e is the alias");
    }
}
