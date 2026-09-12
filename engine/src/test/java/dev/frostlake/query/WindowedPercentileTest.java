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
 * PERCENTILE_CONT / PERCENTILE_DISC with an OVER clause.
 *
 * <p>★ IT ANSWERED THE FRACTION. The window path fed the accumulator the call's FIRST ARGUMENT once per
 * row — which for a percentile is the fraction — so every row of every partition came back 0.5. The
 * WITHIN GROUP clause, where the values actually live, was never read: a windowed call becomes a
 * different AST node from a plain one, and neither the percentile evaluator nor the clause's own
 * plumbing was on that path.
 *
 * <p>★ THE FRAME QUESTION HAD A THIRD ANSWER. It could have been "the frame applies" or "WITHIN GROUP
 * always wins", and it is neither: live REFUSES an OVER clause that carries an ORDER BY or a frame at
 * all — "Cumulative window frame unsupported for function PERCENTILE_CONT" — because WITHIN GROUP has
 * already ordered the values and a cumulative frame would ask for a second ordering. So the only legal
 * spellings are {@code OVER ()} and {@code OVER (PARTITION BY …)}, and the values are always the whole
 * partition's.
 *
 * <p>The refusal's ANCHOR is the frame where one is written and the OVER keyword otherwise — it points
 * at the narrowest clause that is wrong, which is the same rule live's other frame refusals follow.
 *
 * <p>The TYPE fell out of the value, as expected: once the node carries its WITHIN GROUP key, the
 * existing rule types it — the interpolating form widens by three and the picking form hands its
 * column's type straight back, the same split the non-windowed spelling has.
 */
public class WindowedPercentileTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE wp (a INT, g INT, n102 NUMBER(10,2), fl FLOAT)");
        engine.execute("INSERT INTO wp VALUES (1, 1, 1.00, 1.5), (2, 1, 2.00, 2.5),"
            + " (3, 1, 8.00, 8.5), (4, 2, 4.00, 4.5)");
        engine.execute("CREATE OR REPLACE TABLE wp1 (a INT, n102 NUMBER(10,2))");
        engine.execute("INSERT INTO wp1 VALUES (1, 5.00)");
    }

    /** Every row's every column, joined. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder();
            while (rs.next()) {
                if (all.length() > 0) {
                    all.append(",");
                }
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

    private String cumulativeFrame(final int position, final String function) {
        return "SQL compilation error: error line 1 at position " + position
            + "|Cumulative window frame unsupported for function " + function;
    }

    /** ★ The partition's percentile, where every row used to answer the fraction. */
    @Test
    public void apartitionedPercentileAnswersThePartitionsValue() {
        assertEquals("1/2.00000,2/2.00000,3/2.00000,4/4.00000",
            answer("SELECT a, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102)"
                + " OVER (PARTITION BY g) FROM wp ORDER BY a"));
        assertEquals("1/2.00,2/2.00,3/2.00,4/4.00",
            answer("SELECT a, PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n102)"
                + " OVER (PARTITION BY g) FROM wp ORDER BY a"));
    }

    /** A bare OVER () is one partition of every row. */
    @Test
    public void abareOverIsOnePartition() {
        assertEquals("1/3.00000,2/3.00000,3/3.00000,4/3.00000",
            answer("SELECT a, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102) OVER ()"
                + " FROM wp ORDER BY a"),
            "the median of 1, 2, 4 and 8 interpolates to 3");
    }

    /** ★ The FRACTION is read from the argument, and the two families split on it as they should. */
    @Test
    public void thefractionIsReadFromTheArgument() {
        assertEquals("1/1.50000,2/1.50000,3/1.50000,4/4.00000",
            answer("SELECT a, PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY n102)"
                + " OVER (PARTITION BY g) FROM wp ORDER BY a"),
            "a quarter of the way from 1.00 to 2.00");
        assertEquals("1/1.00,2/1.00,3/1.00,4/4.00",
            answer("SELECT a, PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY n102)"
                + " OVER (PARTITION BY g) FROM wp ORDER BY a"),
            "and the picking form takes an actual value instead");
    }

    /** ★ An OVER carrying an ORDER BY is REFUSED — the WITHIN GROUP already ordered the values. */
    @Test
    public void anOverWithAnOrderByIsRefused() {
        assertEquals(cumulativeFrame(60, "PERCENTILE_CONT"),
            answer("SELECT a, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102)"
                + " OVER (PARTITION BY g ORDER BY a) FROM wp ORDER BY a"));
        assertEquals(cumulativeFrame(60, "PERCENTILE_CONT"),
            answer("SELECT a, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102)"
                + " OVER (ORDER BY a) FROM wp ORDER BY a"),
            "with no PARTITION either");
        assertEquals(cumulativeFrame(60, "PERCENTILE_DISC"),
            answer("SELECT a, PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n102)"
                + " OVER (ORDER BY a) FROM wp ORDER BY a"),
            "and the sentence names whichever percentile it was");
    }

    /** ★ With an explicit FRAME the anchor moves to the frame, not the OVER. */
    @Test
    public void anExplicitFrameAnchorsOnTheFrame() {
        assertEquals(cumulativeFrame(92, "PERCENTILE_CONT"),
            answer("SELECT a, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102)"
                + " OVER (PARTITION BY g ORDER BY a ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)"
                + " FROM wp ORDER BY a"));
    }

    /** A FLOAT key takes the double tier here as it does everywhere else. */
    @Test
    public void afloatKeyTakesTheDoubleTier() {
        assertEquals("1/2.5,2/2.5,3/2.5,4/4.5",
            answer("SELECT a, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY fl)"
                + " OVER (PARTITION BY g) FROM wp ORDER BY a"));
    }

    /** The ORDER BY's DIRECTION inside WITHIN GROUP changes nothing — the values are sorted anyway. */
    @Test
    public void thewithinGroupDirectionChangesNothing() {
        assertEquals("1/2.00000,2/2.00000,3/2.00000,4/4.00000",
            answer("SELECT a, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102 DESC)"
                + " OVER (PARTITION BY g) FROM wp ORDER BY a"));
    }

    /** A partition of ONE row is that row, presented at the declared scale. */
    @Test
    public void apartitionOfOneRowIsThatRow() {
        assertEquals("1/5.00000",
            answer("SELECT a, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102) OVER ()"
                + " FROM wp1 ORDER BY a"));
    }

    /** The declared type of one column of a DESCRIBEd relation, read by name. */
    private String declaredType(final String relation, final String column) {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE " + relation);
        while (rs.next()) {
            if (column.equalsIgnoreCase(String.valueOf(rs.getValue(0)))) {
                return String.valueOf(rs.getValue(1));
            }
        }
        return "<no such column>";
    }

    /** ★ The DECLARED type follows the WITHIN GROUP key, not the fraction. */
    @Test
    public void thedeclaredTypeFollowsTheKey() {
        engine.execute("CREATE OR REPLACE TABLE wp_t AS SELECT a,"
            + " PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102) OVER (PARTITION BY g) c,"
            + " PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n102) OVER (PARTITION BY g) d FROM wp");
        assertEquals("NUMBER(13,5)", declaredType("wp_t", "c"),
            "the interpolating form widens the key by three, as its non-windowed spelling does");
        assertEquals("NUMBER(10,2)", declaredType("wp_t", "d"),
            "and the picking form hands the key's own type back");
    }

    /** The MEDIAN window form, which was already right, must not move. */
    @Test
    public void themedianWindowFormIsUntouched() {
        assertEquals("1/2.00000,2/2.00000,3/2.00000,4/4.00000",
            answer("SELECT a, MEDIAN(n102) OVER (PARTITION BY g) FROM wp ORDER BY a"));
    }
}
