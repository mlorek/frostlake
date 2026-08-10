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
 * Over a GROUPED query, a window call settles nothing about its own references: its ARGUMENTS and its
 * OVER keys alike must be grouped, aggregated, a SELECT alias, or constant. A window is computed after
 * the grouping, so it can only see what the grouping left.
 *
 * <p>Frostlake used to let the OVER keys through — the grouped walk stopped at a window call, treating
 * it as settled — and refused one shape live accepts, because a window ARGUMENT was judged against the
 * projected shape rather than the relation the rows come from.
 *
 * <pre>
 *   OVER (ORDER BY c)          GROUP BY a   [G.C] is not a valid group by expression
 *   OVER (PARTITION BY c …)    GROUP BY a   the same
 *   LAG(c) OVER (ORDER BY a)   GROUP BY a   the same — an ARGUMENT counts too
 *   SUM(b) OVER (ORDER BY a)   GROUP BY a   [G.B] — a WINDOWED aggregate is not an aggregate here
 *   LAG(MAX(c)) OVER (…)       GROUP BY a   reads: the nested aggregate settles its own subtree
 * </pre>
 *
 * <p>Note the sentence: live answers with the BRACKETED, positionless form even under an explicit
 * GROUP BY, where an ordinary ungrouped select item gets the positioned "in select clause is neither an
 * aggregate nor in the group by clause" instead.
 */
public class GroupedWindowKeyTest extends BaseDatabaseTest {

    private static final String NOT_GROUPED = "is not a valid group by expression";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE g (a INT, b INT, c INT)");
        engine.execute("INSERT INTO g VALUES (1, 10, 100), (1, 20, 200), (2, 30, 300)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private int rowCount(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        int count = 0;
        while (rs.next()) {
            count++;
        }
        return count;
    }

    /** An OVER key naming an ungrouped column is refused, in ORDER BY and in PARTITION BY alike. */
    @Test
    public void anUngroupedOverKeyIsRefused() {
        assertTrue(refusal("SELECT a, ROW_NUMBER() OVER (ORDER BY c) r FROM g GROUP BY a")
            .contains("[G.C] " + NOT_GROUPED),
            refusal("SELECT a, ROW_NUMBER() OVER (ORDER BY c) r FROM g GROUP BY a"));
        assertTrue(refusal("SELECT a, ROW_NUMBER() OVER (PARTITION BY c ORDER BY a) r FROM g GROUP BY a")
            .contains("[G.C] " + NOT_GROUPED),
            refusal("SELECT a, ROW_NUMBER() OVER (PARTITION BY c ORDER BY a) r FROM g GROUP BY a"));
    }

    /** So is an ungrouped ARGUMENT — including under a windowed aggregate, which does not count. */
    @Test
    public void anUngroupedArgumentIsRefused() {
        assertTrue(refusal("SELECT a, LAG(c) OVER (ORDER BY a) r FROM g GROUP BY a")
            .contains("[G.C] " + NOT_GROUPED),
            refusal("SELECT a, LAG(c) OVER (ORDER BY a) r FROM g GROUP BY a"));
        assertTrue(refusal("SELECT a, SUM(b) OVER (ORDER BY a) r FROM g GROUP BY a")
            .contains("[G.B] " + NOT_GROUPED),
            refusal("SELECT a, SUM(b) OVER (ORDER BY a) r FROM g GROUP BY a"));
    }

    /** QUALIFY's own windows are held to the same rule. */
    @Test
    public void qualifysWindowsAreHeldToTheSameRule() {
        assertTrue(refusal("SELECT a, SUM(b) s FROM g GROUP BY a"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY c) = 1").contains("[G.C] " + NOT_GROUPED),
            refusal("SELECT a, SUM(b) s FROM g GROUP BY a"
                + " QUALIFY ROW_NUMBER() OVER (ORDER BY c) = 1"));
    }

    /** Everything the grouping DOES leave available still reads. */
    @Test
    public void everyGroupedFormStillReads() {
        assertEquals(2, rowCount("SELECT a, ROW_NUMBER() OVER (ORDER BY a) r FROM g GROUP BY a"));
        assertEquals(2, rowCount("SELECT a, SUM(b) s, ROW_NUMBER() OVER (ORDER BY s) r FROM g GROUP BY a"));
        assertEquals(2, rowCount("SELECT a, ROW_NUMBER() OVER (ORDER BY MAX(c)) r FROM g GROUP BY a"));
        assertEquals(2, rowCount("SELECT a, LAG(a) OVER (ORDER BY a) r FROM g GROUP BY a"));
        assertEquals(2, rowCount("SELECT a, ROW_NUMBER() OVER (ORDER BY a + 1) r FROM g GROUP BY a"));
        assertEquals(2, rowCount("SELECT a, ROW_NUMBER() OVER (ORDER BY 1) r FROM g GROUP BY a"));
    }

    /** An aggregate NESTED in a window argument reads it from the rows behind the grouping. */
    @Test
    public void anAggregateNestedInAWindowArgumentReads() {
        assertEquals(2, rowCount("SELECT a, LAG(MAX(c)) OVER (ORDER BY a) r FROM g GROUP BY a"));
    }
}
