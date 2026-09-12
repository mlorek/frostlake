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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A windowed AVG's scale, which the OVER clause's SHAPE decides.
 *
 * <p>★ A CUMULATIVE window averages at the aggregate's own width; every other windowed shape is three
 * decimals narrower:
 *
 * <pre>
 *   AVG(n)                                         grouped     NUMBER(28,8)   8 decimals
 *   AVG(n) OVER (ORDER BY k)                       cumulative  NUMBER(28,8)   8
 *   AVG(n) OVER (ORDER BY k RANGE BETWEEN …)       cumulative  NUMBER(28,8)   8
 *   AVG(n) OVER ()                                             NUMBER(25,5)   5
 *   AVG(n) OVER (PARTITION BY k)                               NUMBER(25,5)   5
 *   AVG(n) OVER (ORDER BY k ROWS BETWEEN …)                    NUMBER(25,5)   5
 * </pre>
 *
 * <p>★ THE FRAME KEYWORD DECIDES IT, NOT THE SPAN. A RANGE frame covering the whole partition stays
 * cumulative; the ROWS spelling of the same span narrows. Both were measured, and both were wrong here
 * before — Frostlake declared (25,5) for the RANGE shape while its VALUE already carried 8 decimals.
 *
 * <p>★ THE NARROWING TRUNCATES. Seven rows summing to 1.00 average to 0.142857142857…, which is
 * {@code 0.14285} at five decimals and would be {@code 0.14286} if rounded half-up. The last digit is
 * the whole test: a rounding implementation passes every other cell here and fails this one.
 *
 * <p>Asserted through TO_VARCHAR, the surface both engines share — {@code getValue()} hands back the
 * raw object, whose text is the JVM's rather than the account's.
 */
public class WindowAvgScaleTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE wa (n380 NUMBER(38,0), n102 NUMBER(10,2))");
        engine.execute("INSERT INTO wa VALUES (1, 1.00)");
        engine.execute("INSERT INTO wa VALUES (2, 2.00)");
        engine.execute("INSERT INTO wa VALUES (3, 4.00)");
        // Seven rows summing to 1.00 — the rounding separator.
        engine.execute("CREATE OR REPLACE TABLE wr (k NUMBER(38,0), n102 NUMBER(10,2))");
        engine.execute("INSERT INTO wr VALUES (1, 1.00), (2, 0.00), (3, 0.00), (4, 0.00),"
            + " (5, 0.00), (6, 0.00), (7, 0.00)");
    }

    /** The first row, through the engine's own text path. */
    private String text(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** A cumulative window keeps the aggregate's eight decimals. */
    @Test
    public void aCumulativeWindowKeepsEightDecimals() {
        assertEquals("1.00000000",
            text("SELECT TO_VARCHAR(AVG(n102) OVER (ORDER BY n380)) FROM wa ORDER BY n380"));
        assertEquals("1.00000000", text("SELECT TO_VARCHAR(AVG(n102)) FROM wa GROUP BY n102"
            + " ORDER BY n102"));
    }

    /** ★ A RANGE frame is CUMULATIVE — the frame keyword, not the span, decides. */
    @Test
    public void aRangeFrameIsStillCumulative() {
        assertEquals("1.00000000",
            text("SELECT TO_VARCHAR(AVG(n102) OVER (ORDER BY n380"
                + " RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)) FROM wa ORDER BY n380"));
    }

    /** Every other windowed shape is three decimals narrower. */
    @Test
    public void theOtherWindowShapesAreThreeDecimalsNarrower() {
        assertEquals("2.33333", text("SELECT TO_VARCHAR(AVG(n102) OVER ()) FROM wa ORDER BY n380"));
        assertEquals("1.00000",
            text("SELECT TO_VARCHAR(AVG(n102) OVER (PARTITION BY n380)) FROM wa ORDER BY n380"));
        assertEquals("1.50000",
            text("SELECT TO_VARCHAR(AVG(n102) OVER (ORDER BY n380"
                + " ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING)) FROM wa ORDER BY n380"));
        assertEquals("1.00000",
            text("SELECT TO_VARCHAR(AVG(n102) OVER (PARTITION BY n380 ORDER BY n380"
                + " ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)) FROM wa ORDER BY n380"));
    }

    /**
     * ★ THE NARROWING TRUNCATES. 1.00/7 = 0.142857142857…, so five decimals give 0.14285 truncated and
     * 0.14286 rounded. The cumulative shape keeps eight and agrees either way, which is why this cell
     * needs the non-cumulative one to separate them.
     */
    @Test
    public void theNarrowingTruncatesRatherThanRounds() {
        assertEquals("0.14285", text("SELECT TO_VARCHAR(AVG(n102) OVER ()) FROM wr ORDER BY k"));
        assertEquals("0.14285714", text("SELECT TO_VARCHAR(AVG(n102)) FROM wr"));
    }

    /** SUM is untouched by the shape — it keeps the input's own scale either way. */
    @Test
    public void sumIsUnaffectedByTheShape() {
        assertEquals("7.00", text("SELECT TO_VARCHAR(SUM(n102) OVER ()) FROM wa ORDER BY n380"));
        assertEquals("1.00",
            text("SELECT TO_VARCHAR(SUM(n102) OVER (ORDER BY n380)) FROM wa ORDER BY n380"));
    }
}
