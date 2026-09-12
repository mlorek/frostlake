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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the AVERAGING aggregates declare, and what any aggregate declares when it is spelled over a
 * WINDOW. Three widening steps, all different and none of them derivable from the arithmetic — they
 * were measured one width at a time:
 *
 * <pre>
 *   AVG(NUMBER(p,s))        NUMBER(min(38, p+18), s+6)
 *   MEDIAN(NUMBER(p,s))     NUMBER(min(38, p+3),  s+3)
 *   VARIANCE(NUMBER(p,s))   NUMBER(38, min(12, 2s+6))     the scale SQUARES, then stops at twelve
 *   STDDEV and its kin      FLOAT                          even over an exact input
 * </pre>
 *
 * <p>Frostlake had AVG and SUM right and everything else wrong: MEDIAN was a fixed NUMBER(38,3)
 * whatever it averaged, and the VARIANCE and the two extra STDDEV spellings were not typed at all —
 * they fell through to the VARCHAR(16777216) placeholder.
 *
 * <p>Over a WINDOW nothing was typed either: a windowed SUM, AVG or COUNT reported the BIGINT(38,0)
 * aggregate placeholder and a windowed MIN, MAX, LAG or FIRST_VALUE reported VARCHAR. Every aggregate
 * declares over a window what it declares as an aggregate — with ONE exception, which is the whole
 * reason this file has a section for it.
 *
 * <p><b>AVG forks on the shape of its OVER clause.</b> A BARE ORDER BY — the one spelling that leaves
 * the default RANGE frame in force — gives AVG the aggregate's own width; every other spelling, an
 * explicit frame included, adds fifteen digits and three decimals instead of eighteen and six. Nothing
 * else forks: a windowed SUM is the same width either way.
 *
 * <p>The FLOAT family is compared by NAME only, as everywhere else: the driver reports no precision or
 * scale for it.
 */
public class AveragingWidthTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE aw (n380 NUMBER(38,0), n102 NUMBER(10,2),"
            + " n54 NUMBER(5,4), n21 NUMBER(2,1), n240 NUMBER(24,0), n305 NUMBER(30,5),"
            + " n11 NUMBER(1,1), fl FLOAT)");
        engine.execute("INSERT INTO aw VALUES (1, 1.00, 0.0001, 1.1, 1, 1.00000, 0.1, 1.5),"
            + " (2, 2.00, 0.0002, 2.2, 2, 2.00000, 0.2, 2.5),"
            + " (4, 4.00, 0.0004, 4.4, 4, 4.00000, 0.4, 4.5)");
    }

    /** The declared type of the expression's result column, with parameters except for FLOAT. */
    private String typeOf(final String expr) {
        final DataType type =
            engine.executeQuery("SELECT " + expr + " FROM aw").getColumns().get(0).getDataType();
        if (type == null) {
            return "null";
        }
        if (type instanceof NumericType && !"FLOAT".equals(type.getName())) {
            return type.getName() + "(" + ((NumericType) type).getPrecision() + ","
                + ((NumericType) type).getScale() + ")";
        }
        return type.getName();
    }

    /** AVG adds eighteen digits and six decimals, and caps the precision at thirty-eight. */
    @Test
    public void averageWidensByEighteenAndSix() {
        assertEquals("NUMBER(38,6)", typeOf("AVG(n380)"));
        assertEquals("NUMBER(28,8)", typeOf("AVG(n102)"));
        assertEquals("NUMBER(23,10)", typeOf("AVG(n54)"));
        assertEquals("NUMBER(20,7)", typeOf("AVG(n21)"));
        assertEquals("NUMBER(19,7)", typeOf("AVG(n11)"));
        assertEquals("NUMBER(38,6)", typeOf("AVG(n240)"), "the precision caps, the scale does not");
        assertEquals("NUMBER(38,11)", typeOf("AVG(n305)"));
        assertEquals("FLOAT", typeOf("AVG(fl)"));
    }

    /** MEDIAN adds THREE and three, where it used to declare a fixed NUMBER(38,3). */
    @Test
    public void medianWidensByThreeAndThree() {
        assertEquals("NUMBER(38,3)", typeOf("MEDIAN(n380)"), "the one width the fixed answer matched");
        assertEquals("NUMBER(13,5)", typeOf("MEDIAN(n102)"));
        assertEquals("NUMBER(8,7)", typeOf("MEDIAN(n54)"));
        assertEquals("NUMBER(5,4)", typeOf("MEDIAN(n21)"));
        assertEquals("NUMBER(27,3)", typeOf("MEDIAN(n240)"));
        assertEquals("NUMBER(33,8)", typeOf("MEDIAN(n305)"));
        assertEquals("FLOAT", typeOf("MEDIAN(fl)"));
    }

    /** VARIANCE doubles the scale, adds six, and stops at twelve however wide the input. */
    @Test
    public void varianceSquaresTheScaleAndCapsAtTwelve() {
        assertEquals("NUMBER(38,6)", typeOf("VARIANCE(n380)"));
        assertEquals("NUMBER(38,8)", typeOf("VARIANCE(n11)"));
        assertEquals("NUMBER(38,10)", typeOf("VARIANCE(n102)"));
        assertEquals("NUMBER(38,12)", typeOf("VARIANCE(n54)"), "2*4+6 would be fourteen; it caps");
        assertEquals("NUMBER(38,12)", typeOf("VARIANCE(n305)"), "and stays capped at a wider input");
        assertEquals("NUMBER(38,10)", typeOf("VAR_POP(n102)"));
        assertEquals("NUMBER(38,10)", typeOf("VAR_SAMP(n102)"));
        assertEquals("FLOAT", typeOf("VARIANCE(fl)"));
    }

    /** The STDDEV spellings answer FLOAT whatever they were handed — the root is not exact. */
    @Test
    public void standardDeviationIsAlwaysFloat() {
        assertEquals("FLOAT", typeOf("STDDEV(n102)"));
        assertEquals("FLOAT", typeOf("STDDEV_POP(n102)"));
        assertEquals("FLOAT", typeOf("STDDEV_SAMP(n102)"));
        assertEquals("FLOAT", typeOf("STDDEV(fl)"));
    }

    /** SUM's own rule, which was already right and must stay so. */
    @Test
    public void sumIsUnchanged() {
        assertEquals("NUMBER(38,0)", typeOf("SUM(n380)"));
        assertEquals("NUMBER(22,2)", typeOf("SUM(n102)"));
        assertEquals("NUMBER(17,4)", typeOf("SUM(n54)"));
        assertEquals("NUMBER(18,0)", typeOf("COUNT(n380)"));
        assertEquals("FLOAT", typeOf("SUM(fl)"));
    }

    /** Over a window, every other aggregate declares exactly what it declares as an aggregate. */
    @Test
    public void aWindowedAggregateKeepsItsAggregateWidth() {
        assertEquals("NUMBER(22,2)", typeOf("SUM(n102) OVER ()"));
        assertEquals("NUMBER(38,0)", typeOf("SUM(n380) OVER ()"));
        assertEquals("NUMBER(22,2)", typeOf("SUM(n102) OVER (PARTITION BY n380)"));
        assertEquals("NUMBER(22,2)", typeOf("SUM(n102) OVER (ORDER BY n380)"),
            "SUM does not fork on the ORDER BY, where AVG does");
        assertEquals("NUMBER(18,0)", typeOf("COUNT(n380) OVER ()"));
        assertEquals("NUMBER(18,0)", typeOf("COUNT(*) OVER ()"), "and it needs no argument to type");
        assertEquals("NUMBER(13,5)", typeOf("MEDIAN(n102) OVER ()"));
        assertEquals("NUMBER(38,10)", typeOf("VARIANCE(n102) OVER ()"));
        assertEquals("FLOAT", typeOf("STDDEV(n102) OVER ()"));
    }

    /** The window functions that hand a value back declare that value's own type. */
    @Test
    public void aWindowedPassThroughDeclaresItsArgument() {
        assertEquals("NUMBER(10,2)", typeOf("MIN(n102) OVER ()"));
        assertEquals("NUMBER(5,4)", typeOf("MAX(n54) OVER ()"));
        assertEquals("FLOAT", typeOf("MIN(fl) OVER ()"));
        assertEquals("NUMBER(10,2)", typeOf("LAG(n102) OVER (ORDER BY n380)"));
        assertEquals("NUMBER(10,2)", typeOf("FIRST_VALUE(n102) OVER (ORDER BY n380)"));
        assertEquals("NUMBER(18,0)", typeOf("ROW_NUMBER() OVER (ORDER BY n380)"));
        assertEquals("NUMBER(18,0)", typeOf("RANK() OVER (ORDER BY n380)"));
    }

    /**
     * AVG's fork. A bare ORDER BY leaves the default RANGE frame in force and gives the aggregate's
     * width; everything else — no ORDER BY, a PARTITION BY alone, or an ORDER BY with a frame of its
     * own — adds fifteen and three instead.
     */
    @Test
    public void aWindowedAverageForksOnTheOverClause() {
        assertEquals("NUMBER(25,5)", typeOf("AVG(n102) OVER ()"));
        assertEquals("NUMBER(25,5)", typeOf("AVG(n102) OVER (PARTITION BY n380)"));
        assertEquals("NUMBER(28,8)", typeOf("AVG(n102) OVER (ORDER BY n380)"),
            "a BARE order by widens it the way the aggregate widens");
        assertEquals("NUMBER(28,8)", typeOf("AVG(n102) OVER (PARTITION BY n380 ORDER BY n380)"));
        assertEquals("NUMBER(25,5)", typeOf("AVG(n102) OVER (ORDER BY n380"
            + " ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)"),
            "an explicit frame puts it back");
        assertEquals("NUMBER(25,5)", typeOf("AVG(n102) OVER (ORDER BY n380"
            + " ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)"));
    }

    /** The fork on two more widths, so the rule is not read off one number. */
    @Test
    public void theForkHoldsAcrossWidths() {
        assertEquals("NUMBER(38,3)", typeOf("AVG(n380) OVER ()"));
        assertEquals("NUMBER(38,6)", typeOf("AVG(n380) OVER (ORDER BY n380)"));
        assertEquals("NUMBER(20,7)", typeOf("AVG(n54) OVER ()"));
        assertEquals("NUMBER(23,10)", typeOf("AVG(n54) OVER (ORDER BY n380)"));
        assertEquals("NUMBER(17,4)", typeOf("AVG(n21) OVER ()"));
        assertEquals("NUMBER(16,4)", typeOf("AVG(n11) OVER ()"));
        assertEquals("NUMBER(38,3)", typeOf("AVG(n240) OVER ()"), "the precision caps here too");
        assertEquals("NUMBER(38,8)", typeOf("AVG(n305) OVER ()"));
        assertEquals("FLOAT", typeOf("AVG(fl) OVER ()"));
    }
}
