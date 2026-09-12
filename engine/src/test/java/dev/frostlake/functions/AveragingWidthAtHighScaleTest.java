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
 * The averaging family's declared widths follow the input's SCALE, and at a high scale they run out
 * of room. Live-verified, width by width:
 *
 * <ul>
 *   <li>AVG grows the scale by six decimals, never past twelve and never below the input's own, and
 *       the precision by twelve plus that growth, capped at thirty-eight — NUMBER(20,10) averages as
 *       NUMBER(34,12), NUMBER(38,20) as NUMBER(38,20). A cumulative window average is the same.</li>
 *   <li>VARIANCE and VAR_POP always take thirty-eight digits, at twice the scale plus six, capped at
 *       twelve and floored at the input's own.</li>
 *   <li>MEDIAN and PERCENTILE_CONT add three decimals to both width and scale — NUMBER(30,8) is
 *       NUMBER(33,11) — and the whole-partition window AVG adds fifteen digits and three decimals.
 *       Three decimals past thirty-eight is no NUMBER at all, so those are refused at COMPILE time:
 *       "Invalid intermediate datatype: NUMBER(41,40)." over a NUMBER(38,37).</li>
 *   <li>At NUMBER(38,35) the three decimals still fit the declaration, NUMBER(38,38), but no value of
 *       one or more fits that, so the refusal moves to ROW time: "Number out of representable range:
 *       type FIXED[SB16](38,38){nullable}, value 3". MEDIAN names its result; the window AVG names the
 *       first input that cannot be widened, at the input's own scale.</li>
 * </ul>
 *
 * <p>Frostlake used to keep every member at the input's width past scale twelve and to answer the
 * refused cells, or to leave the row-time refusal as an empty cell.
 *
 * <p>NOT COVERED: SYSTEM$TYPEOF over the (38,35) members, where live types the call without computing
 * it while Frostlake computes and refuses.
 */
public class AveragingWidthAtHighScaleTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE aw (c30_8 NUMBER(30,8), c38_20 NUMBER(38,20),"
            + " c15_15 NUMBER(15,15), c38_37 NUMBER(38,37), c38_35 NUMBER(38,35), c36_36 NUMBER(36,36),"
            + " c20_10 NUMBER(20,10), c10_2 NUMBER(10,2), c38_0 NUMBER(38,0), c5_3 NUMBER(5,3),"
            + " c26_20 NUMBER(26,20), c38_6 NUMBER(38,6), c38_12 NUMBER(38,12), c38_32 NUMBER(38,32))");
        engine.execute("INSERT INTO aw SELECT 2.5, 2.5, 0.5, 1.5, 2.5, 0.5, 2.5, 2.5, 2, 2.5, 2.5, 2.5, 2.5, 2.5");
        engine.execute("INSERT INTO aw SELECT 3.5, 3.5, 0.25, 2.5, 3.5, 0.25, 3.5, 3.5, 3, 3.5, 3.5, 3.5, 3.5, 3.5");
    }

    /** The first cell's text with the storage tag dropped, or the refusal on one line. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return String.valueOf(rs.getValue(0)).replaceAll("\\[SB[0-9]+\\]", "");
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", "|");
        }
    }

    private String typeOf(final String expression) {
        return outcome("SELECT SYSTEM$TYPEOF(" + expression + ") FROM aw LIMIT 1");
    }

    private String textOf(final String expression) {
        return outcome("SELECT TO_VARCHAR(" + expression + ") FROM aw LIMIT 1");
    }

    private static String invalidIntermediate(final int precision, final int scale) {
        return "SQL compilation error: |Invalid intermediate datatype: NUMBER(" + precision + "," + scale + ").";
    }

    private static String doesNotFit(final String value) {
        return "Number out of representable range: type FIXED[SB16](38,38){nullable}, value " + value;
    }

    /** ★ Six more decimals to a ceiling of twelve, twelve more digits, capped at thirty-eight. */
    @Test
    public void averageGrowsTheScaleBySixToACeilingOfTwelve() {
        assertEquals("NUMBER(38,12)", typeOf("AVG(c30_8)"));
        assertEquals("NUMBER(38,20)", typeOf("AVG(c38_20)"));
        assertEquals("NUMBER(27,15)", typeOf("AVG(c15_15)"));
        assertEquals("NUMBER(38,37)", typeOf("AVG(c38_37)"));
        assertEquals("NUMBER(38,35)", typeOf("AVG(c38_35)"));
        assertEquals("NUMBER(38,36)", typeOf("AVG(c36_36)"));
        assertEquals("NUMBER(34,12)", typeOf("AVG(c20_10)"));
        assertEquals("NUMBER(28,8)", typeOf("AVG(c10_2)"));
        assertEquals("NUMBER(38,6)", typeOf("AVG(c38_0)"));
        assertEquals("NUMBER(23,9)", typeOf("AVG(c5_3)"));
        assertEquals("NUMBER(38,20)", typeOf("AVG(c26_20)"));
        assertEquals("NUMBER(38,12)", typeOf("AVG(c38_6)"));
        assertEquals("NUMBER(38,12)", typeOf("AVG(c38_12)"));
        assertEquals("NUMBER(38,32)", typeOf("AVG(c38_32)"));
    }

    /** A cumulative window average declares the aggregate's width. */
    @Test
    public void aCumulativeAverageDeclaresTheAggregatesWidth() {
        assertEquals("NUMBER(34,12)", typeOf("AVG(c20_10) OVER (ORDER BY c20_10)"));
        assertEquals("NUMBER(23,9)", typeOf("AVG(c5_3) OVER (ORDER BY c5_3)"));
        assertEquals("NUMBER(27,15)", typeOf("AVG(c15_15) OVER (ORDER BY c15_15)"));
        assertEquals("NUMBER(38,37)", typeOf("AVG(c38_37) OVER (ORDER BY c38_37)"));
        assertEquals("NUMBER(38,35)", typeOf("AVG(c38_35) OVER (ORDER BY c38_35)"));
        assertEquals("NUMBER(38,36)", typeOf("AVG(c36_36) OVER (ORDER BY c36_36)"));
        assertEquals("NUMBER(38,6)", typeOf("AVG(c38_0) OVER (ORDER BY c38_0)"));
    }

    /** ★ Thirty-eight digits at twice the scale plus six, capped at twelve, floored at the input's. */
    @Test
    public void varianceKeepsThirtyEightDigits() {
        assertEquals("NUMBER(38,12)", typeOf("VARIANCE(c30_8)"));
        assertEquals("NUMBER(38,20)", typeOf("VARIANCE(c38_20)"));
        assertEquals("NUMBER(38,15)", typeOf("VARIANCE(c15_15)"));
        assertEquals("NUMBER(38,37)", typeOf("VARIANCE(c38_37)"));
        assertEquals("NUMBER(38,35)", typeOf("VAR_POP(c38_35)"));
        assertEquals("NUMBER(38,36)", typeOf("VAR_POP(c36_36)"));
        assertEquals("NUMBER(38,12)", typeOf("VARIANCE(c20_10)"));
        assertEquals("NUMBER(38,10)", typeOf("VARIANCE(c10_2)"));
        assertEquals("NUMBER(38,6)", typeOf("VAR_POP(c38_0)"));
        assertEquals("NUMBER(38,12)", typeOf("VARIANCE(c5_3)"));
        assertEquals("NUMBER(38,20)", typeOf("VAR_POP(c26_20)"));
        assertEquals("NUMBER(38,32)", typeOf("VARIANCE(c38_32)"));
        assertEquals("FLOAT[DOUBLE]", typeOf("STDDEV(c38_37)"), "the deviations stay approximate");
        assertEquals("FLOAT[DOUBLE]", typeOf("STDDEV_POP(c38_35)"));
    }

    /** ★ Three more decimals in both width and scale, capped at thirty-eight digits. */
    @Test
    public void medianAndPercentileContAddThreeDecimals() {
        assertEquals("NUMBER(33,11)", typeOf("MEDIAN(c30_8)"));
        assertEquals("NUMBER(38,23)", typeOf("MEDIAN(c38_20)"));
        assertEquals("NUMBER(18,18)", typeOf("MEDIAN(c15_15)"));
        assertEquals("NUMBER(23,13)", typeOf("MEDIAN(c20_10)"));
        assertEquals("NUMBER(13,5)", typeOf("MEDIAN(c10_2)"));
        assertEquals("NUMBER(38,3)", typeOf("MEDIAN(c38_0)"));
        assertEquals("NUMBER(8,6)", typeOf("MEDIAN(c5_3)"));
        assertEquals("NUMBER(29,23)", typeOf("MEDIAN(c26_20)"));
        assertEquals("NUMBER(38,9)", typeOf("MEDIAN(c38_6)"));
        assertEquals("NUMBER(38,15)", typeOf("MEDIAN(c38_12)"));
        assertEquals("NUMBER(38,35)", typeOf("MEDIAN(c38_32)"));
        assertEquals("NUMBER(33,11)", typeOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY c30_8)"));
        assertEquals("NUMBER(18,18)", typeOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY c15_15)"));
        assertEquals("NUMBER(8,6)", typeOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY c5_3)"));
        assertEquals("NUMBER(38,35)", typeOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY c38_32)"));
        assertEquals("NUMBER(30,8)", typeOf("PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY c30_8)"),
            "the discrete percentile keeps the input's width");
        assertEquals("NUMBER(38,37)", typeOf("PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY c38_37)"));
    }

    /** ★ Fifteen more digits and three more decimals for the whole-partition average. */
    @Test
    public void theWholePartitionAverageAddsFifteenDigitsAndThreeDecimals() {
        assertEquals("NUMBER(38,11)", typeOf("AVG(c30_8) OVER ()"));
        assertEquals("NUMBER(38,23)", typeOf("AVG(c38_20) OVER ()"));
        assertEquals("NUMBER(30,18)", typeOf("AVG(c15_15) OVER ()"));
        assertEquals("NUMBER(35,13)", typeOf("AVG(c20_10) OVER ()"));
        assertEquals("NUMBER(25,5)", typeOf("AVG(c10_2) OVER ()"));
        assertEquals("NUMBER(38,3)", typeOf("AVG(c38_0) OVER ()"));
        assertEquals("NUMBER(20,6)", typeOf("AVG(c5_3) OVER ()"));
        assertEquals("NUMBER(38,23)", typeOf("AVG(c26_20) OVER ()"));
        assertEquals("NUMBER(38,9)", typeOf("AVG(c38_6) OVER ()"));
        assertEquals("NUMBER(38,15)", typeOf("AVG(c38_12) OVER ()"));
        assertEquals("NUMBER(38,35)", typeOf("AVG(c38_32) OVER ()"));
        assertEquals("NUMBER(38,35)", typeOf("AVG(c38_32) OVER (PARTITION BY c38_0)"));
    }

    /** The values are written at the declared scale, and the rounding is at that scale. */
    @Test
    public void theValuesAreWrittenAtTheDeclaredScale() {
        assertEquals("3.000000000000", textOf("AVG(c20_10)"));
        assertEquals("3.000000000000", textOf("AVG(c30_8)"));
        assertEquals("3.00000000000000000000", textOf("AVG(c38_20)"));
        assertEquals("0.375000000000000", textOf("AVG(c15_15)"));
        assertEquals("0.375000000000000000000000000000000000", textOf("AVG(c36_36)"));
        assertEquals("2.500000", textOf("AVG(c38_0)"));
        assertEquals("3.000000000", textOf("AVG(c5_3)"));
        assertEquals("0.50000000000000000000", textOf("VARIANCE(c38_20)"));
        assertEquals("0.2500000000", textOf("VAR_POP(c10_2)"));
        assertEquals("0.031250000000000000000000000000000000", textOf("VARIANCE(c36_36)"));
        assertEquals("0.500000000000", textOf("VARIANCE(c5_3)"));
        assertEquals("3.00000000000", textOf("MEDIAN(c30_8)"));
        assertEquals("3.00000000000000000000000", textOf("MEDIAN(c38_20)"));
        assertEquals("0.375000000000000000", textOf("MEDIAN(c15_15)"));
        assertEquals("2.500", textOf("MEDIAN(c38_0)"));
        assertEquals("3.000000", textOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY c5_3)"));
        assertEquals("3.0000000000000", textOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY c20_10)"));
        assertEquals("2.50000000", textOf("PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY c30_8)"));
        assertEquals("3.00000000000000000000000", textOf("AVG(c38_20) OVER ()"));
        assertEquals("3.0000000000000", textOf("AVG(c20_10) OVER ()"));
        assertEquals("2.500", textOf("AVG(c38_0) OVER ()"));
        assertEquals("3.000000", textOf("AVG(c5_3) OVER ()"));
        assertEquals("2.0000000000000000000000000000000000000", textOf("AVG(c38_37)"));
        assertEquals("0.5000000000000000000000000000000000000", textOf("VARIANCE(c38_37)"));
    }

    /** ★ Three decimals past thirty-eight is no NUMBER, and the refusal is at compile time. */
    @Test
    public void threeDecimalsPastThirtyEightIsRefusedAtCompileTime() {
        assertEquals(invalidIntermediate(41, 40), outcome("SELECT MEDIAN(c38_37) FROM aw"));
        assertEquals(invalidIntermediate(41, 40),
            outcome("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY c38_37) FROM aw"));
        assertEquals(invalidIntermediate(41, 40), outcome("SELECT AVG(c38_37) OVER () FROM aw"));
        assertEquals(invalidIntermediate(41, 40), typeOf("MEDIAN(c38_37)"), "the type is the refusal");
        assertEquals(invalidIntermediate(41, 40), typeOf("AVG(c38_37) OVER ()"));
        assertEquals(invalidIntermediate(39, 39), outcome("SELECT MEDIAN(c36_36) FROM aw"),
            "the named width is the input's plus three, not thirty-eight plus three");
        assertEquals(invalidIntermediate(39, 39),
            outcome("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY c36_36) FROM aw"));
        assertEquals(invalidIntermediate(41, 39), outcome("SELECT AVG(c36_36) OVER () FROM aw"),
            "while the window average names its fifteen-digit width, capped");
        assertEquals(invalidIntermediate(41, 40), outcome("SELECT MEDIAN(c38_37) FROM aw WHERE FALSE"),
            "a compile-time refusal fires over zero rows");
        assertEquals(invalidIntermediate(41, 40), outcome("SELECT AVG(c38_37) OVER () FROM aw WHERE FALSE"));
        assertEquals(invalidIntermediate(41, 40), outcome("SELECT c38_0, MEDIAN(c38_37) FROM aw GROUP BY c38_0"));
    }

    /** ★ A declared NUMBER(38,38) is legal, so a value of one or more is refused at row time. */
    @Test
    public void aValueTheDeclaredTypeCannotHoldIsRefusedAtRowTime() {
        assertEquals(doesNotFit("3"), outcome("SELECT MEDIAN(c38_35) FROM aw"));
        assertEquals(doesNotFit("3"), textOf("MEDIAN(c38_35)"));
        assertEquals(doesNotFit("3"), outcome("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY c38_35) FROM aw"));
        assertEquals(doesNotFit("2.50000000000000000000000000000000000"), outcome("SELECT AVG(c38_35) OVER () FROM aw"),
            "the window average names the first input that cannot be widened, at its own scale");
        assertEquals(doesNotFit("2.50000000000000000000000000000000000"), textOf("AVG(c38_35) OVER ()"));
        assertEquals("3.00000000000000000000000000000000000", textOf("AVG(c38_35)"),
            "the aggregate itself keeps the input's scale and fits");
        assertEquals("2.50000000000000000000000000000000000", textOf("PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY c38_35)"));
    }
}
