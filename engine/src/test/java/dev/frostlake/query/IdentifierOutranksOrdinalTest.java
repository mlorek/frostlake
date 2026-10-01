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
 * Which refusal wins when a query carries BOTH an out-of-range positional key and a name that
 * resolves to nothing.
 *
 * <p>★ THE RULE IS PER CLAUSE, NOT A FLAT PRECEDENCE. An unresolvable identifier outranks the
 * position only where the reference is settled early — the SELECT list, the ORDER BY list, WHERE and
 * the GROUP BY list. In HAVING and QUALIFY it LOSES: those two are resolved after the position, so an
 * out-of-range ordinal beside a bad name in either of them reports the ordinal. Both directions are
 * measured, and the asymmetry is the whole content of this class — a single "names before positions"
 * rule gets HAVING and QUALIFY wrong.
 *
 * <p>★ AN UNKNOWN FUNCTION STILL LOSES to the position, which is why the identifier scan had to be
 * moved without dragging the function-name scan with it. The two controls below pin that: an unknown
 * function reports the ordinal, an unresolvable TABLE outranks everything.
 *
 * <p>★ EVERY ARM OF A SET OPERATION resolves before the position, each bad name reported at its own
 * arm's offset. Each arm validates the SAME statement's ordinals, so the range check waits for the
 * last arm rather than firing while the first one runs.
 *
 * <p>A dotted reference is echoed whole and upper-cased — {@code 'T.NOSUCHCOL'} for a bad column
 * under a good alias, {@code 'X.A'} for a good column under an alias that names no relation.
 */
public class IdentifierOutranksOrdinalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ord (a INT, b INT)");
        engine.execute("INSERT INTO ord VALUES (2, 20), (1, 10), (3, 30)");
    }

    /** The refusal, one line, or {@code accepted}. */
    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static final String ORDINAL = "SQL compilation error:|[9] is not a valid order by expression";

    /** The SELECT list and the ORDER BY list both settle their names before the position. */
    @Test
    public void aBadNameInTheSelectOrOrderByListOutranksThePosition() {
        assertEquals("SQL compilation error: error line 1 at position 7|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT nosuchcol FROM ord ORDER BY 9"));
        assertEquals("SQL compilation error: error line 1 at position 30|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT a FROM ord ORDER BY 9, nosuchcol"));
    }

    /** WHERE resolves early too. */
    @Test
    public void aBadNameInWhereOutranksThePosition() {
        assertEquals("SQL compilation error: error line 1 at position 24|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT a FROM ord WHERE nosuchcol = 1 ORDER BY 9"));
    }

    /** HAVING and QUALIFY are the exception: they are resolved AFTER the position and lose to it. */
    @Test
    public void aBadNameInHavingOrQualifyLosesToThePosition() {
        assertEquals(ORDINAL,
            refusal("SELECT a FROM ord GROUP BY a HAVING nosuchcol > 1 ORDER BY 9"));
        assertEquals(ORDINAL,
            refusal("SELECT a, ROW_NUMBER() OVER (ORDER BY a) rn FROM ord"
                + " QUALIFY nosuchcol > 1 ORDER BY 9"));
    }

    /** A dotted reference is echoed whole, whichever half of it is wrong. */
    @Test
    public void aQualifiedNameIsEchoedWholeAndStillOutranksThePosition() {
        assertEquals("SQL compilation error: error line 1 at position 7|invalid identifier 'T.NOSUCHCOL'",
            refusal("SELECT t.nosuchcol FROM ord t ORDER BY 9"));
        assertEquals("SQL compilation error: error line 1 at position 32|invalid identifier 'T.NOSUCHCOL'",
            refusal("SELECT a FROM ord t ORDER BY 9, t.nosuchcol"));
        assertEquals("SQL compilation error: error line 1 at position 7|invalid identifier 'X.A'",
            refusal("SELECT x.a FROM ord t ORDER BY 9"));
    }

    /** GROUP BY spells the same sentence with its own noun, and loses to a bad name the same way. */
    @Test
    public void theGroupByPositionHasItsOwnNoun() {
        assertEquals("SQL compilation error:|[9] is not a valid group by expression",
            refusal("SELECT a FROM ord GROUP BY 9"));
        assertEquals("SQL compilation error: error line 1 at position 7|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT nosuchcol FROM ord GROUP BY 9"));
        assertEquals("SQL compilation error: error line 1 at position 30|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT a FROM ord GROUP BY 9, nosuchcol"));
    }

    /** Every arm resolves first, and the bad one is reported at its own arm's offset. */
    @Test
    public void everySetOperationArmResolvesBeforeThePosition() {
        assertEquals("SQL compilation error: error line 1 at position 7|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT nosuchcol FROM ord UNION ALL SELECT a FROM ord ORDER BY 9"));
        assertEquals("SQL compilation error: error line 1 at position 35|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT a FROM ord UNION ALL SELECT nosuchcol FROM ord ORDER BY 9"));
        assertEquals(ORDINAL,
            refusal("SELECT a FROM ord UNION ALL SELECT b FROM ord ORDER BY 9"));
    }

    /**
     * The controls that must NOT move: an unknown FUNCTION loses to the position, an unresolvable
     * TABLE outranks it, and a bad name beats an unknown function whichever order they are written in.
     */
    @Test
    public void theFunctionAndRelationControlsAreUnchanged() {
        assertEquals(ORDINAL, refusal("SELECT nosuchfn(a) FROM ord ORDER BY 9"));
        assertEquals(ORDINAL, refusal("SELECT a FROM ord ORDER BY 9"));
        assertEquals(hinted("SQL compilation error:|Object 'NOSUCHTABLE' does not exist or not authorized."),
            refusal("SELECT a FROM nosuchtable ORDER BY 9"));
        assertEquals("SQL compilation error: error line 1 at position 7|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT nosuchcol, nosuchfn(a) FROM ord ORDER BY 9"));
        assertEquals("SQL compilation error: error line 1 at position 20|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT nosuchfn(a), nosuchcol FROM ord ORDER BY 9"));
    }
}
