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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the two ORDERED percentiles declare. Both fell through to the VARCHAR(16777216) placeholder,
 * and they are typed from the column they ORDER BY rather than from the fraction they are handed:
 *
 * <pre>
 *   PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY NUMBER(p,s))   NUMBER(min(38, p+3), s+3)
 *   PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY NUMBER(p,s))   NUMBER(p, s) — unchanged
 *   either, over a FLOAT                                       FLOAT
 * </pre>
 *
 * <p>THE SPLIT IS ABOUT WHAT THEY RETURN. PERCENTILE_DISC hands back an actual value from the input, so
 * it hands back its type too; PERCENTILE_CONT interpolates BETWEEN two of them and widens exactly as
 * MEDIAN does — which is no coincidence, MEDIAN being PERCENTILE_CONT(0.5). The two agree cell for cell
 * across all seven NUMBER widths here, and {@code query/AveragingWidthTest} pins the rule from the
 * MEDIAN side.
 *
 * <p>The FRACTION changes nothing: {@code PERCENTILE_CONT(0)}, {@code (1)}, {@code (0.25)} and
 * {@code (0.123456789)} over the same column all declare the same width. Nor does the ORDER BY's
 * direction. An ORDER BY over an EXPRESSION types from that expression, so the rule composes.
 *
 * <p>WHY IT NEEDED PLUMBING RATHER THAN A RULE: the width comes from the WITHIN GROUP expression, and
 * the AST node carried only the fraction — the clause is on the call's parse tree but nothing read it.
 * {@code FunctionCallExpression.describeWithinGroup} now records it at build, the way the OVER clause's
 * shape is recorded for a window call.
 *
 * <p>NOT ASSERTED HERE: the VALUES. A percentile that interpolates comes back at the width the
 * arithmetic produced rather than at the scale the column declares — {@code 2.0} where live prints
 * {@code 2.00000} — and that is true of MEDIAN today, so it is neither new nor confined to these two.
 * The cells where the two DO agree are asserted below, and the rest is tracked separately.
 */
public class PercentileWidthTest extends BaseDatabaseTest {

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

    /** The first row's first value. */
    private String valueOf(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM aw");
        return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
    }

    private String cont(final String col) {
        return typeOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY " + col + ")");
    }

    private String disc(final String col) {
        return typeOf("PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY " + col + ")");
    }

    /** The CONTINUOUS percentile adds three digits and three decimals, capped at thirty-eight. */
    @Test
    public void theContinuousPercentileWidensLikeMedian() {
        assertEquals("NUMBER(38,3)", cont("n380"), "38+3 caps at 38, the scale still moves");
        assertEquals("NUMBER(13,5)", cont("n102"));
        assertEquals("NUMBER(8,7)", cont("n54"));
        assertEquals("NUMBER(5,4)", cont("n21"));
        assertEquals("NUMBER(27,3)", cont("n240"));
        assertEquals("NUMBER(33,8)", cont("n305"));
        assertEquals("NUMBER(4,4)", cont("n11"), "a scale that already equals the precision");
        assertEquals("FLOAT", cont("fl"));
    }

    /** And MEDIAN declares the same thing, being PERCENTILE_CONT(0.5) under another name. */
    @Test
    public void medianDeclaresExactlyTheSame() {
        assertEquals(typeOf("MEDIAN(n380)"), cont("n380"));
        assertEquals(typeOf("MEDIAN(n102)"), cont("n102"));
        assertEquals(typeOf("MEDIAN(n54)"), cont("n54"));
        assertEquals(typeOf("MEDIAN(n305)"), cont("n305"));
        assertEquals(typeOf("MEDIAN(fl)"), cont("fl"));
    }

    /** The DISCRETE percentile returns an input value, so it returns the input's type. */
    @Test
    public void theDiscretePercentileHandsTheTypeBack() {
        assertEquals("NUMBER(38,0)", disc("n380"));
        assertEquals("NUMBER(10,2)", disc("n102"));
        assertEquals("NUMBER(5,4)", disc("n54"));
        assertEquals("NUMBER(2,1)", disc("n21"));
        assertEquals("NUMBER(24,0)", disc("n240"));
        assertEquals("NUMBER(30,5)", disc("n305"));
        assertEquals("NUMBER(1,1)", disc("n11"));
        assertEquals("FLOAT", disc("fl"));
    }

    /** The FRACTION is not part of the width, and neither is the ORDER BY's direction. */
    @Test
    public void neitherTheFractionNorTheDirectionChangesIt() {
        assertEquals("NUMBER(13,5)", typeOf("PERCENTILE_CONT(0) WITHIN GROUP (ORDER BY n102)"));
        assertEquals("NUMBER(13,5)", typeOf("PERCENTILE_CONT(1) WITHIN GROUP (ORDER BY n102)"));
        assertEquals("NUMBER(13,5)", typeOf("PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY n102)"));
        assertEquals("NUMBER(13,5)",
            typeOf("PERCENTILE_CONT(0.123456789) WITHIN GROUP (ORDER BY n102)"));
        assertEquals("NUMBER(13,5)", typeOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102 DESC)"));
        assertEquals("NUMBER(10,2)", typeOf("PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY n102)"));
        assertEquals("NUMBER(10,2)", typeOf("PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n102 DESC)"));
    }

    /** An ORDER BY over an EXPRESSION types from the expression, so the rule composes. */
    @Test
    public void anOrderedExpressionTypesFromTheExpression() {
        assertEquals("NUMBER(14,5)", typeOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102 * 2)"),
            "n102 * 2 is NUMBER(11,2), and the rule adds three and three to that");
        assertEquals("NUMBER(15,7)",
            typeOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n380::NUMBER(12,4))"));
    }

    /** APPROX_PERCENTILE is the approximate one, and says so whatever it is given. */
    @Test
    public void approxPercentileIsAlwaysFloat() {
        assertEquals("FLOAT", typeOf("APPROX_PERCENTILE(n102, 0.5)"));
        assertEquals("FLOAT", typeOf("APPROX_PERCENTILE(n380, 0.5)"));
        assertEquals("FLOAT", typeOf("APPROX_PERCENTILE(fl, 0.5)"));
    }

    /** The neighbours that share the WITHIN GROUP clause must not move. */
    @Test
    public void listaggAndModeAreUnchanged() {
        assertEquals("VARCHAR", typeOf("LISTAGG(n380, ',') WITHIN GROUP (ORDER BY n102)"));
        assertEquals("VARCHAR", typeOf("LISTAGG(n380, ',')"));
        assertEquals("NUMBER(10,2)", typeOf("MODE(n102)"),
            "MODE also returns an input value, and was already typed that way");
    }

    /** The VALUES that agree with live — the FLOAT family and a scale the arithmetic already lands on. */
    @Test
    public void theValuesThatAgree() {
        assertEquals("2.5", valueOf("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY fl)"));
        assertEquals("2.5", valueOf("PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY fl)"));
        assertEquals("2.5", valueOf("MEDIAN(fl)"));
        assertEquals("2.2", valueOf("PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n21)"),
            "the DISCRETE percentile hands a row's value back untouched, so it needs no re-scaling");
    }
}
