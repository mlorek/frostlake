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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What CEIL, FLOOR, ROUND, TRUNC and ABS DECLARE, which follows the argument rather than being fixed,
 * and the second argument all four rounders take. Frostlake declared a flat INTEGER or NUMBER(38,0)
 * for the lot, and refused the scale argument outright on CEIL and FLOOR.
 *
 * <pre>
 *   the rounders, target scale s (0 when none is written)
 *       s &gt;= the input's own scale   the input type UNCHANGED
 *       otherwise                     NUMBER(min(38, p + 1 + max(0, -s - (p - si))), max(s, 0))
 *   ABS                               NUMBER(min(38, max(p, si + 2)), si)
 * </pre>
 *
 * <p>The single extra digit is for the carry a round-up can produce, and it is only spent when
 * decimals are actually being dropped — {@code CEIL(<NUMBER(37,0)>)} stays NUMBER(37,0) rather than
 * growing to 38, which is the cell that pins the rule. A NEGATIVE scale spends more, but not until it
 * reaches past the integer digits the input already has, because until then the value cannot outgrow
 * the input's range.
 *
 * <p>The scale argument is read as a LITERAL, not as a value — the same rule BASE64_ENCODE's line
 * length follows. {@code CEIL(n, 1)} declares a scale of one; {@code CEIL(n, 1+0)} cannot be read
 * while compiling and falls back to a fixed eighteen digits with the input's own scale, even though
 * the two round identically.
 */
public class RoundingFamilyWidthTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rw (n102 NUMBER(10,2), n54 NUMBER(5,4),"
            + " n380 NUMBER(38,0), n3810 NUMBER(38,10), i INT, fl FLOAT, n21 NUMBER(2,1),"
            + " n11 NUMBER(1,1), n370 NUMBER(37,0), a99 NUMBER(9,9), a22 NUMBER(2,2),"
            + " a10 NUMBER(1,0), a205 NUMBER(20,5))");
        engine.execute("INSERT INTO rw SELECT 123.45, 0.1234, 12345, 1.2345678901, 7, 2.5,"
            + " 1.5, 0.5, 5, 0.123456789, 0.25, 1, 1.25");
    }

    /** The declared type of the expression's result column, and the value it answers. */
    private String cell(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM rw");
            final DataType type = rs.getColumns().get(0).getDataType();
            final String named = type == null ? "null"
                : type instanceof NumericType && !NumericType.isApproximate(type)
                    ? type.getName() + "(" + ((NumericType) type).getPrecision() + ","
                        + ((NumericType) type).getScale() + ")"
                    : type.getName();
            return named + " = " + (rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>");
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Dropping decimals costs ONE digit, for the carry. */
    @Test
    public void droppingDecimalsCostsOneDigit() {
        assertEquals("NUMBER(11,0) = 124", cell("CEIL(n102)"));
        assertEquals("NUMBER(11,0) = 123", cell("FLOOR(n102)"));
        assertEquals("NUMBER(11,0) = 123", cell("ROUND(n102)"));
        assertEquals("NUMBER(11,0) = 123", cell("TRUNC(n102)"));
        assertEquals("NUMBER(6,0) = 1", cell("CEIL(n54)"));
        assertEquals("NUMBER(3,0) = 2", cell("CEIL(n21)"));
        assertEquals("NUMBER(2,0) = 1", cell("CEIL(n11)"));
        assertEquals("NUMBER(10,0) = 1", cell("CEIL(a99)"));
        assertEquals("NUMBER(3,0) = 1", cell("CEIL(a22)"));
        assertEquals("NUMBER(21,0) = 2", cell("CEIL(a205)"));
        assertEquals("NUMBER(38,0) = 2", cell("CEIL(n3810)"), "and caps at thirty-eight");
    }

    /** With no decimals to drop the type is left ALONE, which is what says the digit is for a carry. */
    @Test
    public void nothingToDropLeavesTheTypeAlone() {
        assertEquals("NUMBER(37,0) = 5", cell("CEIL(n370)"), "not 38 — there is nothing to round");
        assertEquals("NUMBER(38,0) = 12345", cell("CEIL(n380)"));
        assertEquals("NUMBER(38,0) = 7", cell("CEIL(i)"));
        assertEquals("NUMBER(1,0) = 1", cell("CEIL(a10)"));
        assertEquals("NUMBER(10,2) = 123.45", cell("CEIL(n102, 3)"), "a scale past the input's");
        assertEquals("NUMBER(10,2) = 123.45", cell("CEIL(n102, 38)"));
        assertEquals("NUMBER(38,0) = 12345", cell("CEIL(n380, 2)"));
    }

    /** A written scale becomes the RESULT's scale, and CEIL and FLOOR take one at all now. */
    @Test
    public void aWrittenScaleBecomesTheResultScale() {
        assertEquals("NUMBER(11,1) = 123.5", cell("CEIL(n102, 1)"));
        assertEquals("NUMBER(11,1) = 123.4", cell("FLOOR(n102, 1)"));
        assertEquals("NUMBER(11,1) = 123.5", cell("ROUND(n102, 1)"));
        assertEquals("NUMBER(11,1) = 123.4", cell("TRUNC(n102, 1)"));
        assertEquals("NUMBER(11,0) = 124", cell("CEIL(n102, 0)"));
        assertEquals("NUMBER(6,2) = 0.13", cell("CEIL(n54, 2)"));
        assertEquals("NUMBER(6,2) = 0.12", cell("FLOOR(n54, 2)"));
    }

    /** A NEGATIVE scale rounds to a power of ten, and buys digits only past the integer ones. */
    @Test
    public void aNegativeScaleCostsDigitsOnlyPastTheIntegerDigits() {
        assertEquals("NUMBER(11,0) = 130", cell("CEIL(n102, -1)"));
        assertEquals("NUMBER(11,0) = 120", cell("FLOOR(n102, -1)"));
        assertEquals("NUMBER(11,0) = 100000000", cell("CEIL(n102, -8)"),
            "eight integer digits, so eight costs nothing");
        assertEquals("NUMBER(12,0) = 1000000000", cell("CEIL(n102, -9)"), "the ninth does");
        assertEquals("NUMBER(13,0) = 10000000000", cell("CEIL(n102, -10)"));
        assertEquals("NUMBER(23,0) = 100000000000000000000", cell("CEIL(n102, -20)"));
        assertEquals("NUMBER(6,0) = 10", cell("CEIL(n54, -1)"), "one integer digit, so it costs");
        assertEquals("NUMBER(10,0) = 100000", cell("CEIL(n54, -5)"));
        assertEquals("NUMBER(38,0) = 100000", cell("CEIL(n3810, -5)"));
    }

    /** Shifting more than thirty-eight digits is refused, at row time and without a prefix. */
    @Test
    public void shiftingMoreThanThirtyEightDigitsIsRefused() {
        assertEquals("NUMBER(38,0) = 100000000000000000000000000000000000",
            cell("CEIL(n102, -35)"));
        assertEquals("NUMBER(38,0) = 1000000000000000000000000000000000000",
            cell("CEIL(n102, -36)"), "thirty-eight of span, still legal");
        assertEquals("Invalid parameter value: -37. Reason: Scale too large", cell("CEIL(n102, -37)"));
        assertEquals("Invalid parameter value: -38. Reason: Scale too large", cell("CEIL(n54, -38)"),
            "four decimals, so it runs out sooner");
    }

    /** A scale that is not an integral LITERAL cannot be read, and falls back to eighteen digits. */
    @Test
    public void anUnreadableScaleFallsBackToEighteenDigits() {
        assertEquals("NUMBER(18,2) = 123.50", cell("CEIL(n102, 1+0)"),
            "the same rounding as CEIL(n102, 1), a different declared type");
        assertEquals("NUMBER(18,2) = 123.45", cell("CEIL(n102, i)"));
        assertEquals("NUMBER(18,2) = 123.45", cell("CEIL(n102, 1.7)"), "a fraction is not integral");
        assertEquals("NUMBER(18,2) = 123.50", cell("CEIL(n102, '1')"), "nor is a string");
        assertEquals("NUMBER(18,4) = 0.1234", cell("CEIL(n54, i)"));
        assertEquals("NUMBER(18,1) = 1.5", cell("CEIL(n21, i)"));
        assertEquals("NUMBER(38,0) = 5", cell("CEIL(n370, i)"), "the eighteen is a FLOOR, not a cap");
        assertEquals("NUMBER(38,10) = 1.2345679000", cell("CEIL(n3810, i)"),
            "rounded at seven, declared at ten, so the value is padded back out");
    }

    /** An integral literal is still one when it is written with a decimal point or an exponent. */
    @Test
    public void anIntegralLiteralIsReadHoweverItIsSpelled() {
        assertEquals("NUMBER(10,2) = 123.45", cell("CEIL(n102, 2.0)"));
        assertEquals("NUMBER(11,1) = 123.5", cell("CEIL(n102, 1e0)"));
        assertEquals("NUMBER(11,0) = 130", cell("CEIL(n102, -(1))"));
    }

    /** ABS keeps its input, with the two-integer-digit floor a NUMBER always has. */
    @Test
    public void absKeepsItsInputWithATwoDigitFloor() {
        assertEquals("NUMBER(10,2) = 123.45", cell("ABS(n102)"));
        assertEquals("NUMBER(38,10) = 1.2345678901", cell("ABS(n3810)"));
        assertEquals("NUMBER(37,0) = 5", cell("ABS(n370)"));
        assertEquals("NUMBER(6,4) = 0.1234", cell("ABS(n54)"), "one integer digit, so it gains one");
        assertEquals("NUMBER(3,1) = 1.5", cell("ABS(n21)"));
        assertEquals("NUMBER(3,1) = 0.5", cell("ABS(n11)"), "none at all, so it gains two");
        assertEquals("NUMBER(11,9) = 0.123456789", cell("ABS(a99)"));
        assertEquals("NUMBER(4,2) = 0.25", cell("ABS(a22)"));
        assertEquals("NUMBER(2,0) = 1", cell("ABS(a10)"));
    }

    /** An APPROXIMATE input passes straight through, scale argument or not. */
    @Test
    public void anApproximateInputPassesThrough() {
        assertEquals("FLOAT = 3.0", cell("CEIL(fl)"));
        assertEquals("FLOAT = 2.0", cell("FLOOR(fl)"));
        assertEquals("FLOAT = 3.0", cell("ROUND(fl)"));
        assertEquals("FLOAT = 2.0", cell("TRUNC(fl)"));
        assertEquals("FLOAT = 2.5", cell("ABS(fl)"));
        assertEquals("FLOAT = 2.5", cell("CEIL(fl, 1)"));
    }

    /** A NULL scale, or a NULL rounding mode, makes the call NULL rather than meaning zero. */
    @Test
    public void aNullScaleOrModeMakesTheCallNull() {
        assertEquals("NUMBER(18,2) = null", cell("CEIL(n102, NULL)"));
        assertEquals("NUMBER(18,2) = null", cell("ROUND(n102, NULL)"));
        assertEquals("NUMBER(3,0) = null", cell("ROUND(n21, 0, NULL)"));
    }

    /** ROUND's third argument picks the tie-breaking rule, and the default rounds away from zero. */
    @Test
    public void theRoundingModeIsTheThirdArgument() {
        assertEquals("NUMBER(3,0) = 2", cell("ROUND(n21, 0, 'HALF_AWAY_FROM_ZERO')"));
        assertEquals("NUMBER(3,0) = 2", cell("ROUND(n21, 0, 'HALF_TO_EVEN')"));
        assertEquals("NUMBER(11,1) = 123.4", cell("ROUND(n102, 1, 'HALF_TO_EVEN')"));
    }

    /** Over a NUMBER, TRUNC's second argument is a SCALE — never a date part. */
    @Test
    public void truncOverANumberIsNeverTheDateForm() {
        assertEquals("NUMBER(18,2) = 123.40", cell("TRUNC(n102, '1')"));
        assertEquals("Numeric value 'MONTH' is not recognized", cell("TRUNC(n102, 'MONTH')"));
    }

    /** One argument past each maximum is refused, with live's own counts and its qualified echo. */
    @Test
    public void oneArgumentPastTheMaximumIsRefused() {
        assertEquals("SQL compilation error: error line 1 at position 7 too many arguments for"
            + " function [CEIL(RW.N102, 1, 1)] expected 2, got 3", cell("CEIL(n102, 1, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7 too many arguments for"
            + " function [FLOOR(RW.N102, 1, 1)] expected 2, got 3", cell("FLOOR(n102, 1, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7 too many arguments for"
            + " function [TRUNC(RW.N102, 1, 1)] expected 2, got 3", cell("TRUNC(n102, 1, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7 too many arguments for"
            + " function [ABS(RW.N102, 1)] expected 1, got 2", cell("ABS(n102, 1)"));
    }
}
