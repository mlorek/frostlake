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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ANSI named WINDOW clause — {@code … OVER w … WINDOW w AS (…)} — which NEITHER engine supports.
 *
 * <p>★ MEASURED, AND DELIBERATELY NOT MATCHED. Both engines refuse every spelling as a syntax error;
 * they disagree only on which token the refusal ANCHORS on. Live points at the window NAME, both at
 * the {@code OVER w} and again at the {@code WINDOW w}; Frostlake's grammar consumes the name and
 * trips on whatever follows it:
 *
 * <pre>
 *   SELECT AVG(n) OVER w FROM aw WINDOW w AS (PARTITION BY a)
 *       live  position 22 unexpected 'w'.        position 39 unexpected 'w'.
 *       FL    position 24 unexpected 'FROM'.     position 45 unexpected 'PARTITION'.
 * </pre>
 *
 * <p>Matching it means teaching the grammar to expect a parenthesis immediately after OVER and to
 * report the name — a new parser decision in the hottest window rule, for a feature neither engine
 * offers and nobody has asked for. The trade is bad, so the divergence is RECORDED here instead: these
 * cells assert what both engines do agree on, which is that every spelling is refused and refused as a
 * SYNTAX error on line 1. A future round reading this file cannot mistake Frostlake's anchor for
 * correct.
 *
 * <p>Frostlake also stacks MORE lines than live on some of these, which is the other half of the same
 * decision: the error listener's stacking is left alone for the reasons the LIMIT-slot work settled.
 *
 * <p>WINDOW is a perfectly good table ALIAS on both engines, which is the one cell here that agrees
 * exactly — and the reason the keyword cannot simply be reserved.
 */
public class NamedWindowClauseTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE nw (a INT, n102 NUMBER(10,2))");
        engine.execute("INSERT INTO nw VALUES (1, 1.00), (2, 2.00), (3, 8.00)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Both engines refuse it as a syntax error; only the anchor differs. */
    private void refusedAsSyntaxError(final String sql) {
        final String message = answer(sql);
        assertTrue(message.startsWith("SQL compilation error:|syntax error line 1 at position "),
            message);
    }

    /** ★ The full ANSI spelling, refused by both. */
    @Test
    public void thefullNamedWindowSpellingIsRefused() {
        refusedAsSyntaxError("SELECT AVG(n102) OVER w FROM nw"
            + " WINDOW w AS (ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)");
        refusedAsSyntaxError("SELECT AVG(n102) OVER w FROM nw WINDOW w AS (PARTITION BY a)");
        refusedAsSyntaxError("SELECT ROW_NUMBER() OVER w FROM nw WINDOW w AS (ORDER BY a)");
    }

    /** Either half alone is refused too — the reference without the clause, and the clause alone. */
    @Test
    public void eitherHalfAloneIsRefused() {
        refusedAsSyntaxError("SELECT AVG(n102) OVER w FROM nw");
        refusedAsSyntaxError("SELECT AVG(n102) OVER () FROM nw WINDOW w AS (PARTITION BY a)");
    }

    /** A parenthesized name, and a list of two named windows, are refused as well. */
    @Test
    public void theremainingSpellingsAreRefused() {
        refusedAsSyntaxError("SELECT AVG(n102) OVER (w) FROM nw WINDOW w AS (PARTITION BY a)");
        refusedAsSyntaxError("SELECT AVG(n102) OVER w1 FROM nw"
            + " WINDOW w1 AS (PARTITION BY a), w2 AS (PARTITION BY n102)");
    }

    /** ★ WINDOW is a legal table ALIAS on both engines — the keyword cannot be reserved. */
    @Test
    public void windowIsAlegalTableAlias() {
        assertEquals("ACCEPTED: 1 2 3", answer("SELECT a FROM nw WINDOW ORDER BY a"));
    }

    /** The INLINE window spelling, which is what both engines actually support, still runs. */
    @Test
    public void theinlineSpellingStillRuns() {
        assertEquals("ACCEPTED: 1 2 3",
            answer("SELECT ROW_NUMBER() OVER (ORDER BY a) FROM nw ORDER BY a"));
    }
}
