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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What frame a MEDIAN, a MODE or a PERCENTILE may be windowed over. Each of them ranks its input
 * before it answers, so a frame showing it a moving subset of the partition asks for an ordering it
 * cannot give — and there are THREE refusals for the shapes that do, not one.
 *
 * <p>★ THE COUNT OF UNBOUNDED EDGES DECIDES THE WORD. Two unbounded edges is the whole partition and
 * is answered; exactly one is "Cumulative"; neither is "Sliding". Not which edge, and not ROWS against
 * RANGE — {@code BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING} is cumulative just as
 * {@code BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW} is. A one-sided frame's missing edge is CURRENT
 * ROW, which is bounded, so {@code ROWS UNBOUNDED PRECEDING} counts one and {@code ROWS 1 PRECEDING}
 * counts none.
 *
 * <p>★ THE RANGE SPELLING OF THE WHOLE-PARTITION FRAME IS THE ODD ONE OUT. Live elides a range that
 * covers everything and is then left holding an ORDER BY it cannot use, which is a different sentence
 * entirely — with no position, and echoing the call from the resolved plan.
 *
 * <p>★ THE TWO FAMILIES SIT ON OPPOSITE SIDES OF NAME RESOLUTION. The cumulative and sliding refusals
 * outrank everything, including a FROM that names nothing; the echoing one loses to an unknown
 * relation and to a bad name in the very call it would print.
 *
 * <p>★ IT IS PER-FUNCTION. {@code SUM(n)} over any of these frames answers, sliding window and all.
 *
 * <p>NOT PINNED HERE: MODE's VALUE over a tie, which live itself answers two ways.
 */
public class WindowFrameShapeRefusalTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE wf (n NUMBER(10,2), g NUMBER(2,0))");
        engine.execute("INSERT INTO wf VALUES (1.00, 1), (2.00, 1), (4.00, 2)");
    }

    /** One statement's refusal, or its rows joined, with line breaks shown as bars. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder answered = new StringBuilder("ACCEPTED");
            while (rs.next()) {
                answered.append(' ').append(String.valueOf(rs.getValue(0)));
            }
            return answered.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String frameRefusal(final String word, final String function, final int position) {
        return "SQL compilation error: error line 1 at position " + position + "|" + word
            + " window frame unsupported for function " + function;
    }

    /** {@code MEDIAN(n) OVER (ORDER BY n <frame>)} over the seeded table. */
    private String median(final String frame) {
        return outcome("SELECT MEDIAN(n) OVER (ORDER BY n " + frame + ") FROM wf");
    }

    @Test
    void aFrameCoveringTheWholePartitionIsAnswered() {
        assertEquals("ACCEPTED 2.00000 2.00000 2.00000",
            median("ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING"));
        assertEquals("ACCEPTED 1.50000 1.50000 4.00000",
            outcome("SELECT MEDIAN(n) OVER (PARTITION BY g ORDER BY n ROWS BETWEEN UNBOUNDED"
                + " PRECEDING AND UNBOUNDED FOLLOWING) FROM wf ORDER BY g, n"));
        assertEquals("ACCEPTED 2.00000 2.00000 2.00000",
            outcome("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n) OVER (ORDER BY n ROWS"
                + " BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM wf"));
        assertEquals("ACCEPTED 2.00 2.00 2.00",
            outcome("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n) OVER (ORDER BY n ROWS"
                + " BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM wf"));
        // MODE's input is a three-way tie here and live answers it two ways, so only the shape holds.
        assertTrue(outcome("SELECT MODE(n) OVER (ORDER BY n ROWS BETWEEN UNBOUNDED PRECEDING AND"
            + " UNBOUNDED FOLLOWING) FROM wf").startsWith("ACCEPTED "));
    }

    @Test
    void oneUnboundedEdgeIsACumulativeFrame() {
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 34),
            median("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"));
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 34),
            median("ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING"));
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 34),
            median("ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING"));
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 34),
            median("ROWS BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING"));
        // One-sided, and its missing edge is CURRENT ROW — so this one counts one, not two.
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 34), median("ROWS UNBOUNDED PRECEDING"));
    }

    @Test
    void neitherEdgeUnboundedIsASlidingFrame() {
        assertEquals(frameRefusal("Sliding", "MEDIAN", 34),
            median("ROWS BETWEEN 1 PRECEDING AND CURRENT ROW"));
        assertEquals(frameRefusal("Sliding", "MEDIAN", 34), median("ROWS 1 PRECEDING"));
    }

    @Test
    void aRangeFrameIsCumulativeWhicheverEdgeIsBounded() {
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 34),
            median("RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"));
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 34),
            median("RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING"));
    }

    @Test
    void anOrderByWithNoFrameIsCumulativeAtTheOverKeyword() {
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 17),
            outcome("SELECT MEDIAN(n) OVER (ORDER BY n) FROM wf"));
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 17),
            outcome("SELECT MEDIAN(n) OVER (PARTITION BY g ORDER BY n) FROM wf"));
        assertEquals(frameRefusal("Cumulative", "MODE", 15),
            outcome("SELECT MODE(n) OVER (ORDER BY n) FROM wf"));
        assertEquals(frameRefusal("Cumulative", "PERCENTILE_CONT", 54),
            outcome("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n) OVER (ORDER BY n) FROM wf"));
        assertEquals(frameRefusal("Cumulative", "PERCENTILE_DISC", 54),
            outcome("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n) OVER (ORDER BY n) FROM wf"));
        // A specification with NO ordering has no frame to complain about, implicit or written.
        assertEquals("ACCEPTED 2.00000 2.00000 2.00000",
            outcome("SELECT MEDIAN(n) OVER () FROM wf"));
        assertEquals("ACCEPTED 1.50000 1.50000 4.00000",
            outcome("SELECT MEDIAN(n) OVER (PARTITION BY g) FROM wf ORDER BY g, n"));
    }

    @Test
    void aWholePartitionRangeFrameIsTheOrderBySentenceInstead() {
        assertEquals("SQL compilation error:|Aggregate window function with order by clause is not"
                + " supported: [MEDIAN(WF.N) OVER (ORDER BY WF.N ASC NULLS LAST)].",
            median("RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING"));
        assertEquals("SQL compilation error:|Aggregate window function with order by clause is not"
                + " supported: [MEDIAN(WF.N) OVER (PARTITION BY WF.G ORDER BY WF.N ASC NULLS LAST)].",
            outcome("SELECT MEDIAN(n) OVER (PARTITION BY g ORDER BY n RANGE BETWEEN UNBOUNDED"
                + " PRECEDING AND UNBOUNDED FOLLOWING) FROM wf"));
        assertEquals("SQL compilation error:|Aggregate window function with order by clause is not"
                + " supported: [MEDIAN(X.N) OVER (ORDER BY X.N ASC NULLS LAST)].",
            outcome("SELECT MEDIAN(x.n) OVER (ORDER BY x.n RANGE BETWEEN UNBOUNDED PRECEDING AND"
                + " UNBOUNDED FOLLOWING) FROM wf x"));
        // The percentiles print their FRACTION — the plan echo carries no WITHIN GROUP clause.
        assertEquals("SQL compilation error:|Aggregate window function with order by clause is not"
                + " supported: [PERCENTILE_CONT(0.5) OVER (ORDER BY WF.N ASC NULLS LAST)].",
            outcome("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n) OVER (ORDER BY n RANGE"
                + " BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM wf"));
        assertEquals("SQL compilation error:|Aggregate window function with order by clause is not"
                + " supported: [MODE(WF.N) OVER (ORDER BY WF.N ASC NULLS LAST)].",
            outcome("SELECT MODE(n) OVER (ORDER BY n RANGE BETWEEN UNBOUNDED PRECEDING AND"
                + " UNBOUNDED FOLLOWING) FROM wf"));
    }

    @Test
    void theTwoFamiliesSitOnOppositeSidesOfNameResolution() {
        // The echoing sentence is built from a resolved plan, so every name in the call speaks first.
        assertEquals("SQL compilation error:|Object 'NOSUCHTABLE' does not exist or not authorized.",
            outcome("SELECT MEDIAN(n) OVER (ORDER BY n RANGE BETWEEN UNBOUNDED PRECEDING AND"
                + " UNBOUNDED FOLLOWING) FROM nosuchtable"));
        assertEquals("SQL compilation error: error line 1 at position 14|invalid identifier 'ZZ'",
            outcome("SELECT MEDIAN(zz) OVER (ORDER BY zz RANGE BETWEEN UNBOUNDED PRECEDING AND"
                + " UNBOUNDED FOLLOWING) FROM wf"));
        assertEquals("SQL compilation error: error line 1 at position 32|invalid identifier 'ZZ'",
            outcome("SELECT MEDIAN(n) OVER (ORDER BY zz RANGE BETWEEN UNBOUNDED PRECEDING AND"
                + " UNBOUNDED FOLLOWING) FROM wf"));
        assertEquals("SQL compilation error: error line 1 at position 36|invalid identifier 'ZZ'",
            outcome("SELECT MEDIAN(n) OVER (PARTITION BY zz ORDER BY n RANGE BETWEEN UNBOUNDED"
                + " PRECEDING AND UNBOUNDED FOLLOWING) FROM wf"));
        // The cumulative and sliding ones outrank all three.
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 17),
            outcome("SELECT MEDIAN(n) OVER (ORDER BY n) FROM nosuchtable"));
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 18),
            outcome("SELECT MEDIAN(zz) OVER (ORDER BY zz) FROM wf"));
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 17),
            outcome("SELECT MEDIAN(n) OVER (ORDER BY zz) FROM wf"));
        assertEquals(frameRefusal("Sliding", "MEDIAN", 36),
            outcome("SELECT MEDIAN(zz) OVER (ORDER BY zz ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)"
                + " FROM wf"));
    }

    @Test
    void everyOtherAggregateKeepsItsFrames() {
        assertEquals("ACCEPTED 7.00 7.00 7.00",
            outcome("SELECT SUM(n) OVER (ORDER BY n RANGE BETWEEN UNBOUNDED PRECEDING AND"
                + " UNBOUNDED FOLLOWING) FROM wf"));
        assertEquals("ACCEPTED 1.00 3.00 6.00",
            outcome("SELECT SUM(n) OVER (ORDER BY n ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)"
                + " FROM wf ORDER BY n"));
    }

    @Test
    void theRefusalReachesAWindowWrittenInQualify() {
        assertEquals(frameRefusal("Cumulative", "MEDIAN", 35),
            outcome("SELECT n FROM wf QUALIFY MEDIAN(n) OVER (ORDER BY n) > 0"));
    }
}
