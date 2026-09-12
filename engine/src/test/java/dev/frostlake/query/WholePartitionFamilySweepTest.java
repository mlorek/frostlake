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
 * The whole-partition-only window family, swept name by name against the seven frame shapes. Every
 * member below answers a bare OVER, a PARTITION BY and the whole-partition ROWS frame, and refuses an
 * ORDER BY with no frame ("Cumulative … unsupported for function X" at the OVER keyword), a one-edge
 * frame ("Cumulative" at the frame), a two-edge frame ("Sliding") and — bar the two moments — the
 * whole-partition RANGE spelling ("Aggregate window function with order by clause is not supported",
 * the call echoed from the plan, no position). Live-verified:
 *
 * <ul>
 *   <li>the approximate family (APPROX_PERCENTILE, APPROX_COUNT_DISTINCT, HLL), LISTAGG with or
 *       without WITHIN GROUP, the correlation and regression family, ANY_VALUE, MIN_BY / MAX_BY,
 *       OBJECT_AGG, ARRAY_UNIQUE_AGG, HASH_AGG and the bitwise and boolean aggregates behave exactly as
 *       MEDIAN does;</li>
 *   <li>KURTOSIS and SKEW refuse the cumulative and sliding frames but ANSWER the RANGE spelling;</li>
 *   <li>SUM, AVG, VARIANCE, STDDEV, COUNT_IF, COUNT(DISTINCT) and ARRAY_AGG without WITHIN GROUP are
 *       exempt — every frame answers;</li>
 *   <li>two rules of their own, each sparing the whole-partition ROWS frame: a DISTINCT aggregate
 *       other than COUNT cannot be ordered or framed ("distinct cannot be used with a window frame or
 *       an order." at the OVER), and a WITHIN GROUP ordering outside the family cannot meet an OVER
 *       ordering ("WITHIN GROUP clause is not supported when the OVER clause contains an ORDER BY
 *       clause." at the OVER).</li>
 * </ul>
 *
 * <p>Frostlake used to key the refusals on four names and answer the rest over the subset.
 *
 * <p>NOT COVERED: APPROX_TOP_K and APPROX_PERCENTILE_ACCUMULATE, which Frostlake does not implement at
 * all (live answers them, and refuses their frames like the rest of the family).
 */
public class WholePartitionFamilySweepTest extends BaseDatabaseTest {

    private static final String[] FAMILY = {
        "MEDIAN(n)", "MODE(n)", "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n)",
        "APPROX_PERCENTILE(n, 0.5)", "APPROX_COUNT_DISTINCT(n)", "HLL(n)",
        "LISTAGG(s, ',') WITHIN GROUP (ORDER BY s)", "LISTAGG(s, ',')",
        "CORR(n, m)", "COVAR_POP(n, m)", "COVAR_SAMP(n, m)",
        "REGR_SLOPE(n, m)", "REGR_INTERCEPT(n, m)", "REGR_R2(n, m)", "REGR_COUNT(n, m)",
        "REGR_AVGX(n, m)", "REGR_AVGY(n, m)", "REGR_SXX(n, m)", "REGR_SXY(n, m)", "REGR_SYY(n, m)",
        "ANY_VALUE(n)", "MIN_BY(n, m)", "MAX_BY(n, m)", "OBJECT_AGG(s, n)", "ARRAY_UNIQUE_AGG(n)",
        "HASH_AGG(n)", "BITAND_AGG(g)", "BITOR_AGG(g)", "BITXOR_AGG(g)",
        "BOOLAND_AGG(b)", "BOOLOR_AGG(b)", "BOOLXOR_AGG(b)",
    };

    private static final String[] ECHOES = {
        "MEDIAN(WF.N)", "MODE(WF.N)", "PERCENTILE_CONT(0.5)",
        "APPROX_PERCENTILE(WF.N, 0.5)", "APPROX_COUNT_DISTINCT(WF.N)", "HLL(WF.N)",
        "LISTAGG(WF.S, ',')", "LISTAGG(WF.S, ',')",
        "CORR(WF.N, WF.M)", "COVAR_POP(WF.N, WF.M)", "COVAR_SAMP(WF.N, WF.M)",
        "REGR_SLOPE(WF.N, WF.M)", "REGR_INTERCEPT(WF.N, WF.M)", "REGR_R2(WF.N, WF.M)", "REGR_COUNT(WF.N, WF.M)",
        "REGR_AVGX(WF.N, WF.M)", "REGR_AVGY(WF.N, WF.M)", "REGR_SXX(WF.N, WF.M)", "REGR_SXY(WF.N, WF.M)", "REGR_SYY(WF.N, WF.M)",
        "ANY_VALUE(WF.N)", "MIN_BY(WF.N, WF.M)", "MAX_BY(WF.N, WF.M)", "OBJECT_AGG(WF.S, WF.N)", "ARRAY_UNIQUE_AGG(WF.N)",
        "HASH_AGG(WF.N)", "BITAND_AGG(WF.G)", "BITOR_AGG(WF.G)", "BITXOR_AGG(WF.G)",
        "BOOLAND_AGG(WF.B)", "BOOLOR_AGG(WF.B)", "BOOLXOR_AGG(WF.B)",
    };

    private static final String[] EXEMPT = {
        "SUM(n)", "AVG(n)", "VARIANCE(n)", "STDDEV(n)", "COUNT_IF(n > 1)", "COUNT(DISTINCT n)", "ARRAY_AGG(n)",
    };

    private static final String ROWS_WHOLE = "ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING";
    private static final String ROWS_CUMULATIVE = "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW";
    private static final String ROWS_SLIDING = "ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING";
    private static final String RANGE_WHOLE = "RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE wf (g INT, n NUMBER(10,2), m NUMBER(10,2), s VARCHAR(5), b BOOLEAN)");
        engine.execute("INSERT INTO wf VALUES (1, 1.5, 2.5, 'a', TRUE), (1, 2.5, 3.5, 'b', FALSE), (2, 3.5, 1.5, 'c', TRUE)");
    }

    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED " + rs.getRowCount();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String sql(final String call, final String over) {
        return "SELECT " + call + " " + over + " FROM wf";
    }

    private static int overAt(final String sql) {
        return sql.indexOf(" OVER ") + 1;
    }

    private static String frameRefusal(final String word, final String function, final int position) {
        return "SQL compilation error: error line 1 at position " + position + "|" + word
            + " window frame unsupported for function " + function;
    }

    private static String functionOf(final String call) {
        return call.substring(0, call.indexOf('('));
    }

    /** ★ Every member: the three legal shapes answer, the three ordered frames refuse. */
    @Test
    public void everyMemberRefusesTheMovingFrames() {
        for (final String call : FAMILY) {
            final String function = functionOf(call);
            assertTrue(outcome(sql(call, "OVER ()")).startsWith("ACCEPTED"), call);
            assertTrue(outcome(sql(call, "OVER (PARTITION BY g)")).startsWith("ACCEPTED"), call);
            assertTrue(outcome(sql(call, "OVER (ORDER BY n " + ROWS_WHOLE + ")")).startsWith("ACCEPTED"), call);
            final String ordered = sql(call, "OVER (ORDER BY n)");
            assertEquals(frameRefusal("Cumulative", function, overAt(ordered)), outcome(ordered), call);
            final String cumulative = sql(call, "OVER (ORDER BY n " + ROWS_CUMULATIVE + ")");
            assertEquals(frameRefusal("Cumulative", function, cumulative.indexOf(" ROWS BETWEEN") + 1),
                outcome(cumulative), call);
            final String sliding = sql(call, "OVER (ORDER BY n " + ROWS_SLIDING + ")");
            assertEquals(frameRefusal("Sliding", function, sliding.indexOf(" ROWS BETWEEN") + 1),
                outcome(sliding), call);
        }
    }

    /** ★ The RANGE spelling of the whole partition: refused with the plan's echo, bar the two moments. */
    @Test
    public void theWholeRangeFrameIsRefusedWithThePlansEcho() {
        for (int i = 0; i < FAMILY.length; i++) {
            final String call = FAMILY[i];
            assertEquals("SQL compilation error:|Aggregate window function with order by clause is not supported: ["
                + ECHOES[i] + " OVER (ORDER BY WF.N ASC NULLS LAST)].",
                outcome(sql(call, "OVER (ORDER BY n " + RANGE_WHOLE + ")")), call);
        }
        for (final String call : new String[] {"KURTOSIS(n)", "SKEW(n)"}) {
            final String function = functionOf(call);
            assertTrue(outcome(sql(call, "OVER (ORDER BY n " + RANGE_WHOLE + ")")).startsWith("ACCEPTED"),
                call + " answers the RANGE spelling");
            assertTrue(outcome(sql(call, "OVER ()")).startsWith("ACCEPTED"), call);
            final String ordered = sql(call, "OVER (ORDER BY n)");
            assertEquals(frameRefusal("Cumulative", function, overAt(ordered)), outcome(ordered), call);
            final String sliding = sql(call, "OVER (ORDER BY n " + ROWS_SLIDING + ")");
            assertEquals(frameRefusal("Sliding", function, sliding.indexOf(" ROWS BETWEEN") + 1),
                outcome(sliding), call);
        }
    }

    /** The exempt aggregates answer every frame. */
    @Test
    public void theExemptAggregatesAnswerEveryFrame() {
        for (final String call : EXEMPT) {
            for (final String over : new String[] {"OVER ()", "OVER (PARTITION BY g)", "OVER (ORDER BY n)",
                    "OVER (ORDER BY n " + ROWS_WHOLE + ")", "OVER (ORDER BY n " + ROWS_CUMULATIVE + ")",
                    "OVER (ORDER BY n " + ROWS_SLIDING + ")", "OVER (ORDER BY n " + RANGE_WHOLE + ")"}) {
                assertTrue(outcome(sql(call, over)).startsWith("ACCEPTED"), call + " " + over);
            }
        }
    }

    /** ★ DISTINCT cannot be ordered or framed, except under COUNT. */
    @Test
    public void aDistinctAggregateCannotBeOrderedOrFramed() {
        assertTrue(outcome(sql("SUM(DISTINCT n)", "OVER ()")).startsWith("ACCEPTED"));
        assertTrue(outcome(sql("SUM(DISTINCT n)", "OVER (PARTITION BY g)")).startsWith("ACCEPTED"));
        assertTrue(outcome(sql("SUM(DISTINCT n)", "OVER (ORDER BY n " + ROWS_WHOLE + ")")).startsWith("ACCEPTED"),
            "the whole-partition ROWS frame re-states the partition");
        for (final String over : new String[] {"OVER (ORDER BY n)", "OVER (ORDER BY n " + ROWS_CUMULATIVE + ")",
                "OVER (ORDER BY n " + ROWS_SLIDING + ")", "OVER (ORDER BY n " + RANGE_WHOLE + ")"}) {
            final String statement = sql("SUM(DISTINCT n)", over);
            assertEquals("SQL compilation error: error line 1 at position " + overAt(statement)
                + "|distinct cannot be used with a window frame or an order.", outcome(statement), over);
        }
        final String average = sql("AVG(DISTINCT n)", "OVER (ORDER BY n)");
        assertEquals("SQL compilation error: error line 1 at position " + overAt(average)
            + "|distinct cannot be used with a window frame or an order.", outcome(average));
        final String counted = sql("COUNT(DISTINCT n)", "OVER (ORDER BY n)");
        assertTrue(outcome(counted).startsWith("ACCEPTED"), "COUNT is the exception");
    }

    /** ★ A WITHIN GROUP ordering cannot meet an OVER ordering. */
    @Test
    public void aWithinGroupOrderingCannotMeetAnOverOrdering() {
        final String call = "ARRAY_AGG(n) WITHIN GROUP (ORDER BY n)";
        assertTrue(outcome(sql(call, "OVER ()")).startsWith("ACCEPTED"));
        assertTrue(outcome(sql(call, "OVER (PARTITION BY g)")).startsWith("ACCEPTED"));
        assertTrue(outcome(sql(call, "OVER (ORDER BY n " + ROWS_WHOLE + ")")).startsWith("ACCEPTED"),
            "the whole-partition ROWS frame re-states what the call does");
        for (final String over : new String[] {"OVER (ORDER BY n)", "OVER (ORDER BY n " + ROWS_SLIDING + ")",
                "OVER (ORDER BY n " + RANGE_WHOLE + ")"}) {
            final String statement = sql(call, over);
            assertEquals("SQL compilation error: error line 1 at position " + overAt(statement)
                + "|WITHIN GROUP clause is not supported when the OVER clause contains an ORDER BY clause.",
                outcome(statement), over);
        }
        final String listagg = sql("LISTAGG(s, ',') WITHIN GROUP (ORDER BY s)", "OVER (ORDER BY n)");
        assertEquals(frameRefusal("Cumulative", "LISTAGG", overAt(listagg)), outcome(listagg),
            "a family member is judged as a member first");
    }
}
