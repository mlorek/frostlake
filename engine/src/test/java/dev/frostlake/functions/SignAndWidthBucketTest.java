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
 * Two functions that declared a flat INTEGER where live derives a width from an argument, and they
 * derive it from DIFFERENT arguments:
 *
 * <pre>
 *   SIGN(x)                        NUMBER(2,0) over an exact x, FLOAT over anything else
 *   WIDTH_BUCKET(v, min, max, n)   NUMBER(max(2, digits(n)), 0) — the BUCKET COUNT decides
 * </pre>
 *
 * <p>SIGN taking two digits for a value that is only ever -1, 0 or 1 is not waste: the sign takes a
 * digit of its own. WIDTH_BUCKET's overflow bucket does NOT buy one — 99 buckets can answer 100 and
 * still declares two digits — so the width follows the count's own digits under the two-digit floor
 * every NUMBER carries.
 *
 * <p>The count is read from any CONSTANT expression, which is a WIDER rule than the neighbouring
 * functions follow. BASE64_ENCODE's line length and the rounding family's scale are read as literals
 * and nothing else, so {@code 0 - 1} is unreadable to them; {@code 500+500} declares four digits here.
 * A string literal is read too, and a fractional one rounds. Only a count the ROW decides falls back,
 * and it falls all the way to thirty-eight.
 *
 * <p>Two VALUE bugs came out with the widths. A REVERSED range answered 0 for everything, and a NULL
 * bucket count threw a NullPointerException.
 */
public class SignAndWidthBucketTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sw (n102 NUMBER(10,2), n54 NUMBER(5,4),"
            + " n380 NUMBER(38,0), i INT, fl FLOAT, n11 NUMBER(1,1), g VARCHAR, v VARIANT, cnt INT)");
        engine.execute("INSERT INTO sw SELECT -123.45, 0.1234, 12345, -7, -2.5, 0.5,"
            + " '3', TO_VARIANT(3), 5");
    }

    /** The declared type of the expression's result column, and the value it answers. */
    private String cell(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM sw");
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

    /** SIGN is two digits over an exact argument, whatever width that argument carries. */
    @Test
    public void signIsTwoDigitsOverAnyExactArgument() {
        assertEquals("NUMBER(2,0) = -1", cell("SIGN(n102)"));
        assertEquals("NUMBER(2,0) = 1", cell("SIGN(n54)"));
        assertEquals("NUMBER(2,0) = 1", cell("SIGN(n380)"));
        assertEquals("NUMBER(2,0) = -1", cell("SIGN(i)"));
        assertEquals("NUMBER(2,0) = 1", cell("SIGN(n11)"));
        assertEquals("NUMBER(2,0) = -1", cell("SIGN(n102 + 0)"));
        assertEquals("NUMBER(2,0) = 1", cell("SIGN(1)"));
        assertEquals("NUMBER(2,0) = 1", cell("SIGN(1.5)"));
        assertEquals("NUMBER(2,0) = 0", cell("SIGN(0)"));
        assertEquals("NUMBER(2,0) = 0", cell("SIGN(-0.0)"));
        assertEquals("NUMBER(2,0) = null", cell("SIGN(NULL)"));
    }

    /** And FLOAT over everything else — an approximate, a string, a VARIANT. */
    @Test
    public void signIsApproximateOverAnythingWithoutAWidth() {
        assertEquals("FLOAT = -1.0", cell("SIGN(fl)"));
        assertEquals("FLOAT = -1.0", cell("SIGN(CAST(n102 AS FLOAT))"));
        assertEquals("FLOAT = 1.0", cell("SIGN(g)"));
        assertEquals("FLOAT = 1.0", cell("SIGN(v)"));
    }

    /** WIDTH_BUCKET's width follows the bucket COUNT's digits, under a two-digit floor. */
    @Test
    public void theBucketCountDecidesTheWidth() {
        assertEquals("NUMBER(2,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10000000, 1)"));
        assertEquals("NUMBER(2,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10000000, 9)"));
        assertEquals("NUMBER(2,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10000000, 10)"));
        assertEquals("NUMBER(2,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10000000, 99)"),
            "99 buckets can answer 100, and still declares two digits");
        assertEquals("NUMBER(3,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10000000, 100)"));
        assertEquals("NUMBER(3,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10000000, 999)"));
        assertEquals("NUMBER(4,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10000000, 1000)"));
        assertEquals("NUMBER(4,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10000000, 9999)"));
        assertEquals("NUMBER(5,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10000000, 10000)"));
        assertEquals("NUMBER(8,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10000000, 99999999)"));
    }

    /** Any CONSTANT count is read — a folded expression, a string, a fraction — but not the row's. */
    @Test
    public void anyConstantCountIsRead() {
        assertEquals("NUMBER(4,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10, 500+500)"),
            "a folded expression, where the rounding family's scale would be unreadable");
        assertEquals("NUMBER(2,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10, 2+3)"));
        assertEquals("NUMBER(3,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10, '100')"), "a string spells one");
        assertEquals("NUMBER(3,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10, 100.7)"), "a fraction rounds");
        assertEquals("NUMBER(4,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10, 1000.0)"));
        assertEquals("NUMBER(2,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10, 5.0)"));
        assertEquals("NUMBER(2,0) = null", cell("WIDTH_BUCKET(n102, 0, 10, NULL)"));
        assertEquals("NUMBER(38,0) = 0", cell("WIDTH_BUCKET(n102, 0, 10, cnt)"),
            "only a count the ROW decides falls back, and it falls all the way");
    }

    /** An ordinary range: below is 0, above is count + 1, and the low end is IN the first bucket. */
    @Test
    public void anOrdinaryRangeBucketsUpwards() {
        assertEquals("NUMBER(2,0) = 1", cell("WIDTH_BUCKET(0, 0, 10, 5)"));
        assertEquals("NUMBER(2,0) = 2", cell("WIDTH_BUCKET(2, 0, 10, 5)"));
        assertEquals("NUMBER(2,0) = 3", cell("WIDTH_BUCKET(4, 0, 10, 5)"));
        assertEquals("NUMBER(2,0) = 3", cell("WIDTH_BUCKET(5, 0, 10, 5)"));
        assertEquals("NUMBER(2,0) = 6", cell("WIDTH_BUCKET(10, 0, 10, 5)"), "the high end is above");
        assertEquals("NUMBER(2,0) = 0", cell("WIDTH_BUCKET(-1, 0, 10, 5)"));
        assertEquals("NUMBER(2,0) = 6", cell("WIDTH_BUCKET(11, 0, 10, 5)"));
    }

    /** A REVERSED range buckets downwards, closed at the top, with the two ends swapped. */
    @Test
    public void aReversedRangeBucketsDownwards() {
        assertEquals("NUMBER(2,0) = 1", cell("WIDTH_BUCKET(10, 10, 0, 5)"), "the high end is IN now");
        assertEquals("NUMBER(2,0) = 1", cell("WIDTH_BUCKET(9, 10, 0, 5)"));
        assertEquals("NUMBER(2,0) = 2", cell("WIDTH_BUCKET(8, 10, 0, 5)"));
        assertEquals("NUMBER(2,0) = 3", cell("WIDTH_BUCKET(6, 10, 0, 5)"));
        assertEquals("NUMBER(2,0) = 4", cell("WIDTH_BUCKET(4, 10, 0, 5)"));
        assertEquals("NUMBER(2,0) = 5", cell("WIDTH_BUCKET(2, 10, 0, 5)"));
        assertEquals("NUMBER(2,0) = 6", cell("WIDTH_BUCKET(0, 10, 0, 5)"), "and the low end is below");
        assertEquals("NUMBER(2,0) = 6", cell("WIDTH_BUCKET(-5, 10, 0, 5)"));
        assertEquals("NUMBER(2,0) = 0", cell("WIDTH_BUCKET(11, 10, 0, 5)"));
        assertEquals("NUMBER(2,0) = 4", cell("WIDTH_BUCKET(2.5, 10, 0, 5)"));
        assertEquals("NUMBER(2,0) = 2", cell("WIDTH_BUCKET(7.5, 10, 0, 5)"));
    }

    /** A NULL anywhere makes the answer NULL, and a string value is read as the number it spells. */
    @Test
    public void nullsAndStringsInTheValuePosition() {
        assertEquals("NUMBER(2,0) = null", cell("WIDTH_BUCKET(NULL, 0, 10, 5)"));
        assertEquals("NUMBER(2,0) = null", cell("WIDTH_BUCKET(n102, NULL, 10, 5)"));
        assertEquals("NUMBER(2,0) = 2", cell("WIDTH_BUCKET(g, 0, 10, 5)"));
    }

    /**
     * A count that is not positive, and a range with equal ends, share one refusal — which prints the
     * bounds UNSCALED, at the widest scale the value and the bounds carry between them.
     */
    @Test
    public void aBadCountOrAnEmptyRangeIsRefused() {
        assertEquals("Invalid argument for width bucket function, want 0 (num_buckets) > 0"
            + " and 0 (min_value) != 1000 (max_value)", cell("WIDTH_BUCKET(n102, 0, 10, 0)"));
        assertEquals("Invalid argument for width bucket function, want -1 (num_buckets) > 0"
            + " and 0 (min_value) != 1000 (max_value)", cell("WIDTH_BUCKET(n102, 0, 10, -1)"));
        assertEquals("Invalid argument for width bucket function, want 0 (num_buckets) > 0"
            + " and 0 (min_value) != 10 (max_value)", cell("WIDTH_BUCKET(n380, 0, 10, 0)"),
            "a scale-0 value leaves the bounds as they were written");
        assertEquals("Invalid argument for width bucket function, want 0 (num_buckets) > 0"
            + " and 50 (min_value) != 1050 (max_value)", cell("WIDTH_BUCKET(n102, 0.5, 10.5, 0)"));
        assertEquals("Invalid argument for width bucket function, want 5 (num_buckets) > 0"
            + " and 500 (min_value) != 500 (max_value)", cell("WIDTH_BUCKET(n102, 5, 5, 5)"),
            "the count is fine; the range is empty");
        assertEquals("Invalid argument for width bucket function, want 5 (num_buckets) > 0"
            + " and 5 (min_value) != 5 (max_value)", cell("WIDTH_BUCKET(5, 5, 5, 5)"));
    }

    /** Both take exactly the arguments they take, with live's own counts. */
    @Test
    public void theArityIsExact() {
        assertEquals("SQL compilation error: error line 1 at position 7 too many arguments for"
            + " function [SIGN(SW.N102, 1)] expected 1, got 2", cell("SIGN(n102, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7 not enough arguments for"
            + " function [WIDTH_BUCKET(SW.N102, 0, 10)], expected 4, got 3",
            cell("WIDTH_BUCKET(n102, 0, 10)"));
    }
}
