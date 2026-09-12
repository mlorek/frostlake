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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A GROUP BY key that RESOLVES — by 1-based ordinal or by SELECT-list alias — to an aggregate or
 * window select item is refused, echoing the item's OUTPUT NAME: the alias as written (canonical
 * for an unquoted one, verbatim for a quoted one), or the derived name for an unaliased item
 * (live-verified across the family). An aggregate anywhere in the item counts — {@code SUM(i)+1 AS
 * a} refuses as {@code [A]} — and the FROM-less twins behave identically.
 *
 * <p>★ THE SENTENCE RUNS AFTER THE GROUPED SELECT-LIST VALIDATION: a window item whose inner
 * references are NOT covered by the other keys is refused at the REFERENCE ({@code [GT.I]}), and
 * only a list that validates reaches the item-name sentence ({@code [W]}). An out-of-range ordinal
 * beats both, wherever in the key list it sits.
 *
 * <p>★ INSIDE ROLLUP / CUBE / GROUPING SETS THE ECHO IS THE CALL, NOT THE NAME: an aggregate
 * member reads {@code [SUM(GT.I)]} however the item spells it, and a window member gets the
 * window sentence — exactly what a call WRITTEN in the member gets.
 */
public class GroupByAggregateItemKeyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gt (i INT)");
        engine.execute("INSERT INTO gt VALUES (1), (2)");
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    @Test
    public void anAggregateItemKeyEchoesTheItemName() {
        assertEquals("SQL compilation error:\n[A] is not a valid group by expression",
            refusal("SELECT SUM(i) AS a FROM gt GROUP BY 1"));
        assertEquals("SQL compilation error:\n[A] is not a valid group by expression",
            refusal("SELECT SUM(i) AS a FROM gt GROUP BY a"));
        assertEquals("SQL compilation error:\n[C] is not a valid group by expression",
            refusal("SELECT COUNT(*) AS c FROM gt GROUP BY 1"));
    }

    @Test
    public void anUnaliasedItemEchoesItsDerivedName() {
        assertEquals("SQL compilation error:\n[SUM(I)] is not a valid group by expression",
            refusal("SELECT SUM(i) FROM gt GROUP BY 1"));
    }

    @Test
    public void aQuotedAliasEchoesVerbatim() {
        assertEquals("SQL compilation error:\n[lc] is not a valid group by expression",
            refusal("SELECT SUM(i) AS \"lc\" FROM gt GROUP BY 1"));
    }

    @Test
    public void anAggregateAnywhereInTheItemCounts() {
        assertEquals("SQL compilation error:\n[A] is not a valid group by expression",
            refusal("SELECT SUM(i)+1 AS a FROM gt GROUP BY 1"));
    }

    @Test
    public void theFromlessTwinsBehaveIdentically() {
        assertEquals("SQL compilation error:\n[A] is not a valid group by expression",
            refusal("SELECT SUM(1) AS a GROUP BY 1"));
        assertEquals("SQL compilation error:\n[A] is not a valid group by expression",
            refusal("SELECT SUM(1) AS a GROUP BY a"));
    }

    @Test
    public void aLegitimateKeyBesideTheAggregateOneDoesNotSaveIt() {
        assertEquals("SQL compilation error:\n[A] is not a valid group by expression",
            refusal("SELECT i, SUM(i) AS a FROM gt GROUP BY i, a"));
        assertEquals("SQL compilation error:\n[A] is not a valid group by expression",
            refusal("SELECT i, SUM(i) AS a FROM gt GROUP BY 1, 2"));
        assertEquals("SQL compilation error:\n[A] is not a valid group by expression",
            refusal("SELECT SUM(i) AS a, i FROM gt GROUP BY a, i"));
    }

    @Test
    public void anOutOfRangeOrdinalSpeaksFirstWhereverItSits() {
        assertEquals("SQL compilation error:\n[5] is not a valid group by expression",
            refusal("SELECT SUM(i) AS a FROM gt GROUP BY 1, 5"));
        assertEquals("SQL compilation error:\n[5] is not a valid group by expression",
            refusal("SELECT SUM(i) AS a FROM gt GROUP BY 5, 1"));
    }

    @Test
    public void aWrittenAggregateKeyKeepsThePlanEcho() {
        assertEquals("SQL compilation error:\n[SUM(GT.I)] is not a valid group by expression",
            refusal("SELECT SUM(i) AS a FROM gt GROUP BY SUM(i)"));
    }

    @Test
    public void aWindowItemKeyLosesToItsOwnUncoveredReference() {
        assertEquals("SQL compilation error:\n[GT.I] is not a valid group by expression",
            refusal("SELECT ROW_NUMBER() OVER (ORDER BY i) AS w FROM gt GROUP BY 1"));
        assertEquals("SQL compilation error:\n[GT.I] is not a valid group by expression",
            refusal("SELECT ROW_NUMBER() OVER (ORDER BY i) AS w FROM gt GROUP BY w"));
    }

    @Test
    public void aWindowItemKeyWithCoveredReferencesEchoesTheItemName() {
        assertEquals("SQL compilation error:\n[W] is not a valid group by expression",
            refusal("SELECT ROW_NUMBER() OVER (ORDER BY i) AS w, i FROM gt GROUP BY 1, 2"));
        assertEquals("SQL compilation error:\n[W] is not a valid group by expression",
            refusal("SELECT ROW_NUMBER() OVER (ORDER BY i) AS w, i FROM gt GROUP BY w, i"));
    }

    @Test
    public void aRollupMemberEchoesTheResolvedCall() {
        assertEquals("SQL compilation error:\n[SUM(GT.I)] is not a valid group by expression",
            refusal("SELECT SUM(i) AS a FROM gt GROUP BY ROLLUP(a)"));
        assertEquals("SQL compilation error:\n[SUM(GT.I)] is not a valid group by expression",
            refusal("SELECT SUM(i) AS a FROM gt GROUP BY ROLLUP(1)"));
        assertEquals("SQL compilation error:\n[SUM(GT.I)] is not a valid group by expression",
            refusal("SELECT SUM(i)+1 AS a FROM gt GROUP BY ROLLUP(a)"));
    }

    @Test
    public void aWindowRollupMemberGetsTheWindowSentence() {
        assertEquals("SQL compilation error:\nWindow function [ROW_NUMBER() OVER (ORDER BY GT.I ASC"
                + " NULLS LAST)] appears outside of SELECT, QUALIFY, and ORDER BY clauses.",
            refusal("SELECT ROW_NUMBER() OVER (ORDER BY i) AS w, i FROM gt GROUP BY ROLLUP(w, i)"));
    }

    @Test
    public void keysResolvingToPlainItemsStayAccepted() {
        final ResultSet plain = engine.executeQuery(
            "SELECT i, COUNT(*) FROM gt GROUP BY 1 ORDER BY i");
        assertEquals(2, plain.getRows().size());
        assertEquals("1", String.valueOf(plain.getRows().get(0).getValue(0)));
        final ResultSet rollup = engine.executeQuery(
            "SELECT i FROM gt GROUP BY ROLLUP(1) ORDER BY i NULLS LAST");
        assertEquals(3, rollup.getRows().size());
        assertEquals("1", String.valueOf(rollup.getRows().get(0).getValue(0)));
    }
}
