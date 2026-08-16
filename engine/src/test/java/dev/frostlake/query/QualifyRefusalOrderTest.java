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
 * WHICH complaint a broken QUALIFY makes first.
 *
 * <p>★ A NAME IS SETTLED BEFORE THE CLAUSE IS JUDGED. Live resolves what a reference MEANS before it
 * says anything about the clause the reference sits in, so an unresolvable column in QUALIFY outranks
 * both the no-window refusal and the grouped select-list rule. Frostlake ran the clause rules first, so
 * a typo in QUALIFY came back as "found QUALIFY clause but no window function" — a true statement about
 * a query nobody wrote, anchored on the wrong word.
 *
 * <p>★ THE EARLY SITE COULD NOT KNOW. The no-window check ran before the FROM was even resolved, which
 * is why the identifier check could not precede it there. It now waits for the deferred site on every
 * query with a FROM, and only the FROM-less shape — which returns before that site is reached — is
 * still judged early.
 *
 * <p>The order this pins, from the outside in: the RELATION, then NAMES, then the unknown-function
 * scan, then the grouped select-list rule, then the clause's own no-window rule. Each of those was
 * measured against the ones beside it; the aggregate deferral in particular predates this and is
 * asserted here so it cannot be collapsed into the new one by accident.
 *
 * <p>Left for its own task: with the SAME bad name in the select list and in QUALIFY, live anchors on
 * the select list's occurrence and Frostlake on QUALIFY's — the sentence agrees, the position does not.
 */
public class QualifyRefusalOrderTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE qo (a INT, b INT)");
        engine.execute("INSERT INTO qo VALUES (1, 10), (2, 20)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ");
                for (int c = 0; c < rs.getColumns().size(); c++) {
                    if (c > 0) {
                        all.append("/");
                    }
                    all.append(String.valueOf(rs.getValue(c)));
                }
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String badName(final int position, final String name) {
        return "SQL compilation error: error line 1 at position " + position
            + "|invalid identifier '" + name + "'";
    }

    private String noWindow(final int position) {
        return "SQL compilation error: error line 1 at position " + position
            + "|found QUALIFY clause but no window function.";
    }

    /** ★ A bad name in QUALIFY is named, where the clause used to complain about itself instead. */
    @Test
    public void abadNameInQualifyOutranksTheNoWindowRefusal() {
        assertEquals(badName(36, "NOSUCHCOL"),
            answer("SELECT a FROM qo GROUP BY a QUALIFY nosuchcol > 1"));
        assertEquals(badName(25, "NOSUCHCOL"),
            answer("SELECT a FROM qo QUALIFY nosuchcol > 1"),
            "ungrouped too — the deferral is not about aggregation");
    }

    /** ★ And it outranks the GROUPED SELECT-LIST rule that an aggregate in QUALIFY triggers. */
    @Test
    public void abadNameOutranksTheGroupedSelectListRule() {
        assertEquals(badName(25, "NOSUCHCOL"),
            answer("SELECT a FROM qo QUALIFY nosuchcol > 1 AND SUM(b) > 0"));
        assertEquals(badName(40, "NOSUCHCOL"),
            answer("SELECT a FROM qo GROUP BY a QUALIFY SUM(nosuchcol) > 0"),
            "inside an aggregate's arguments as much as beside it");
    }

    /** The anchor follows the reference, not the QUALIFY keyword. */
    @Test
    public void theAnchorFollowsTheReference() {
        assertEquals(badName(39, "NOSUCHCOL"),
            answer("SELECT a FROM qo GROUP BY a QUALIFY    nosuchcol > 1"));
        assertEquals(badName(46, "NOSUCHCOL"),
            answer("SELECT a FROM qo GROUP BY a QUALIFY a > 1 AND nosuchcol > 1"),
            "a later term is anchored where IT sits");
    }

    /** A qualified reference is named in full, both when the column is wrong and when the table is. */
    @Test
    public void aQualifiedReferenceIsNamedInFull() {
        assertEquals(badName(36, "QO.NOSUCHCOL"),
            answer("SELECT a FROM qo GROUP BY a QUALIFY qo.nosuchcol > 1"));
        assertEquals(badName(36, "NOSUCHTAB.A"),
            answer("SELECT a FROM qo GROUP BY a QUALIFY nosuchtab.a > 1"));
    }

    /** A missing RELATION still speaks before any of it. */
    @Test
    public void theRelationIsSettledFirst() {
        assertEquals("SQL compilation error:|Object 'NOSUCHTABLE' does not exist or not authorized.",
            answer("SELECT a FROM nosuchtable QUALIFY nosuchcol > 1"));
    }

    /** An unresolvable FUNCTION name also outranks the clause rule, which #390 had already settled. */
    @Test
    public void anUnknownFunctionAlsoOutranksIt() {
        assertEquals("SQL compilation error:|Unknown function NOSUCHFN.",
            answer("SELECT a FROM qo QUALIFY nosuchfn(a) > 1"));
        assertEquals("SQL compilation error:|Unknown function NOSUCHFN.",
            answer("SELECT a FROM qo GROUP BY a QUALIFY nosuchfn(a) > 1"));
    }

    /** ★ With every name resolving, the no-window refusal is what live says — unchanged, and pinned. */
    @Test
    public void aResolvableQualifyWithNoWindowStillSaysSo() {
        assertEquals(noWindow(17), answer("SELECT a FROM qo QUALIFY b > 1"));
        assertEquals(noWindow(28), answer("SELECT a FROM qo GROUP BY a QUALIFY a > 1"));
        assertEquals(noWindow(22), answer("SELECT a AS x FROM qo QUALIFY x > 1"),
            "a SELECT alias resolves, so the clause rule is reached");
        assertEquals(noWindow(33), answer("SELECT a AS x FROM qo GROUP BY a QUALIFY x > 1"));
    }

    /**
     * ★ THE AGGREGATE DEFERRAL PREDATES THIS AND MUST NOT COLLAPSE INTO IT. An aggregate in QUALIFY
     * makes the whole query grouped, so an ungrouped select list is judged first; with a GROUP BY the
     * list is fine and the clause rule is reached after all.
     */
    @Test
    public void theAggregateDeferralIsUnchanged() {
        assertEquals("SQL compilation error:|[QO.A] is not a valid group by expression",
            answer("SELECT a FROM qo QUALIFY SUM(b) > 0"));
        assertEquals(noWindow(28), answer("SELECT a FROM qo GROUP BY a QUALIFY SUM(b) > 0"));
    }

    /** Both refusals are COMPILE-time: an empty relation reaches each of them. */
    @Test
    public void anEmptyRelationReachesBoth() {
        assertEquals(badName(35, "NOSUCHCOL"),
            answer("SELECT a FROM qo WHERE 1=0 QUALIFY nosuchcol > 1"));
        assertEquals(noWindow(27), answer("SELECT a FROM qo WHERE 1=0 QUALIFY b > 1"));
    }

    /** A QUALIFY that is FINE is untouched, window and alias alike. */
    @Test
    public void aworkingQualifyIsUntouched() {
        assertEquals("ACCEPTED: 1", answer(
            "SELECT a FROM qo QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1"));
        assertEquals("ACCEPTED: 1/1", answer(
            "SELECT a AS x, ROW_NUMBER() OVER (ORDER BY a) r FROM qo QUALIFY r = 1"));
    }

    /** With a window present the clause is fine, so a bad name beside it is still named. */
    @Test
    public void abadNameBesideAwindowIsStillNamed() {
        assertEquals(badName(64, "NOSUCHCOL"), answer(
            "SELECT a FROM qo QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1 AND nosuchcol > 1"));
        assertEquals(badName(64, "NOSUCHCOL"), answer(
            "SELECT a AS x, ROW_NUMBER() OVER (ORDER BY a) r FROM qo QUALIFY nosuchcol = 1"));
    }
}
