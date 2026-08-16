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
 * NULL as a row count and as an offset, in both the LIMIT and the ANSI FETCH spelling. It means
 * "no limit" / "no offset" — so the query answers in full rather than being refused.
 *
 * <p>★ NULL IS NOT LEGAL EVERYWHERE A NUMBER IS. A bare trailing {@code OFFSET NULL} with no FETCH
 * after it is a syntax error on both engines, and live anchors it at the END OF INPUT rather than on
 * the NULL — the run-to-EOF family. Widening the slot must not accidentally accept that shape, which
 * is why it is asserted here as a REFUSAL.
 *
 * <p>★ A STRING AND AN EXPRESSION STAY REFUSED, on both engines: {@code LIMIT '2'} and {@code LIMIT
 * 1+1} are live's own rules, not a Frostlake gap, so the slot was widened for NULL alone.
 *
 * <p>★ THE ALIAS AMBIGUITY IS PRESERVED, which is the thing this change could most easily have broken:
 * a bare {@code LIMIT} directly after the relation is the table's ALIAS on both engines, and
 * {@code FROM ls LIMIT LIMIT 2} reads as alias-then-clause. Both are asserted.
 *
 * <p>★ EXCEPT WHEN NULL FOLLOWS IT, and that is the one pairing where the CLAUSE wins:
 * {@code FROM ls LIMIT NULL} answers every row live, and used to be a syntax error here because the
 * alias reading was taken first and left the NULL with nowhere to go. Only that pairing changed —
 * the alias survives before ORDER BY, before another LIMIT, and at the end of the statement.
 *
 * <p>★ THE GAP BETWEEN THE WORDS DOES NOT MATTER: a newline or a comment between LIMIT and NULL reads
 * the same as a space, because the lexer skips both before the question is asked.
 */
public class LimitSlotNullTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ls (a INT)");
        engine.execute("INSERT INTO ls VALUES (1), (2), (3)");
    }

    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("accepted:");
            while (rs.next()) {
                all.append(String.valueOf(rs.getValue(0))).append(";");
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** NULL as a LIMIT count means "no limit". */
    @Test
    public void nullIsNoLimit() {
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM ls ORDER BY a LIMIT NULL"));
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM ls WHERE a > 0 LIMIT NULL"));
    }

    /** NULL as an OFFSET means "no offset" — the LIMIT still applies. */
    @Test
    public void nullIsNoOffset() {
        assertEquals("accepted:1;", outcome("SELECT a FROM ls ORDER BY a LIMIT 1 OFFSET NULL"));
    }

    /** Both slots of the ANSI spelling take it as well. */
    @Test
    public void theAnsiFetchSpellingTakesItInBothSlots() {
        assertEquals("accepted:1;2;3;",
            outcome("SELECT a FROM ls ORDER BY a FETCH FIRST NULL ROWS ONLY"));
        assertEquals("accepted:1;2;",
            outcome("SELECT a FROM ls ORDER BY a OFFSET NULL ROWS FETCH FIRST 2 ROWS ONLY"));
    }

    /** ★ A trailing OFFSET NULL with no FETCH stays a syntax error, anchored at the end of input. */
    @Test
    public void aTrailingOffsetNullIsStillRefused() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 39 unexpected '<EOF>'.",
            outcome("SELECT a FROM ls ORDER BY a OFFSET NULL"));
    }

    /** A string and an expression are refused on both engines — the slot was widened for NULL only. */
    @Test
    public void aStringAndAnExpressionStayRefused() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 23 unexpected ''2''.",
            outcome("SELECT a FROM ls LIMIT '2'"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 34 unexpected ''2''.",
            outcome("SELECT a FROM ls ORDER BY a LIMIT '2'"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 35 unexpected '+'.",
            outcome("SELECT a FROM ls ORDER BY a LIMIT 1+1"));
    }

    /** ★ The alias-vs-clause ambiguity this change must not disturb. */
    @Test
    public void aBareLimitIsStillATableAlias() {
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM ls LIMIT ORDER BY a"));
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM ls OFFSET ORDER BY a"));
        assertEquals("accepted:1;2;", outcome("SELECT a FROM ls LIMIT LIMIT 2"));
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM ls LIMIT"));
        assertEquals("accepted:null;null;null;", outcome("SELECT NULL FROM ls LIMIT"));
    }

    /**
     * ★ …but a NULL directly after it is the CLAUSE, whatever separates the two words. The alias
     * reading was winning here and leaving the NULL unparseable, which is the one shape the slot
     * widening could not reach on its own.
     */
    @Test
    public void aBareLimitNullIsTheClause() {
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM ls LIMIT NULL"));
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM ls limit null"));
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM ls LIMIT\nNULL"));
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM ls LIMIT /*c*/ NULL"));
        // …and it still composes with an OFFSET, which already worked because the trailing clause
        // gave the parser something to prefer the count reading for.
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM ls LIMIT NULL OFFSET NULL"));
        assertEquals("accepted:2;3;", outcome("SELECT a FROM ls ORDER BY a LIMIT NULL OFFSET 1"));
    }

    /** And the ordinary shapes still answer. */
    @Test
    public void theOrdinaryLimitsAreUnchanged() {
        assertEquals("accepted:1;2;", outcome("SELECT a FROM ls ORDER BY a LIMIT 2"));
        assertEquals("accepted:2;3;", outcome("SELECT a FROM ls ORDER BY a LIMIT 2 OFFSET 1"));
        assertEquals("accepted:1;2;", outcome("SELECT a FROM ls ORDER BY a FETCH FIRST 2 ROWS ONLY"));
    }
}
