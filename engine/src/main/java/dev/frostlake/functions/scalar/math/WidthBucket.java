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

package dev.frostlake.functions.scalar.math;

import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;

/**
 * WIDTH_BUCKET(value, min, max, count) — which of {@code count} equal buckets between {@code min} and
 * {@code max} the value falls in, 0 below the range and {@code count + 1} above it.
 *
 * <p>A REVERSED range is legal and buckets downwards: over {@code (10, 0, 5)} the buckets are
 * {@code (8,10]}, {@code (6,8]} … and the two out-of-range answers swap places, so a value above
 * {@code min} is 0 and one at or below {@code max} is {@code count + 1}. Frostlake answered 0 for
 * every reversed call, since it only ever compared the value against the arguments in the order they
 * were written.
 *
 * <p>A bucket count that is not positive, and a range whose ends are EQUAL, are the same refusal, and
 * it names all three numbers — with the two bounds printed as UNSCALED integers at the widest scale
 * among the value and the bounds, which is how Snowflake holds them:
 *
 * <pre>
 *   WIDTH_BUCKET(&lt;NUMBER(10,2)&gt;, 0, 10, 0)   … 0 (min_value) != 1000 (max_value)
 *   WIDTH_BUCKET(&lt;NUMBER(38,0)&gt;, 0, 10, 0)   … 0 (min_value) != 10 (max_value)
 *   WIDTH_BUCKET(&lt;NUMBER(10,2)&gt;, 0.5, 10.5, 0)  … 50 (min_value) != 1050 (max_value)
 * </pre>
 *
 * <p>Any NULL argument makes the answer NULL, the bucket count included. A FLOAT that is NaN or infinite takes
 * FLOAT arithmetic as the comparisons and the bucket's floor read it: a NaN value is in bucket 1, an infinite one at
 * the end it lies past, a NaN bound turns the range around, and a NaN or infinite count is out of the whole number's
 * range (live-verified).
 */
public class WidthBucket extends NumericArgumentFunction {
    public WidthBucket() { super("WIDTH_BUCKET", NumericType.INTEGER); }

    @Override
    public Object evaluate(final List<Object> args) {
        for (int i = 0; i < 4; i++) {
            if (args.get(i) == null) {
                return null;
            }
        }
        final long count = finite(args.get(3)) ? DecimalText.parse(args.get(3).toString()).longValue()
            : Random.floatSeed(((Double) args.get(3)).doubleValue(), "FIXED[SB8](18,0){not null}");
        if (!finite(args.get(0)) || !finite(args.get(1)) || !finite(args.get(2))) {
            return Long.valueOf(floatBucket(((Number) args.get(0)).doubleValue(), ((Number) args.get(1)).doubleValue(),
                ((Number) args.get(2)).doubleValue(), count));
        }
        final BigDecimal value = DecimalText.parse(args.get(0).toString());
        final BigDecimal low = DecimalText.parse(args.get(1).toString());
        final BigDecimal high = DecimalText.parse(args.get(2).toString());
        if (count <= 0 || low.compareTo(high) == 0) {
            throw new RuntimeException(refusal(count, low, high, value));
        }
        final boolean reversed = low.compareTo(high) > 0;
        if (reversed) {
            if (value.compareTo(low) > 0) {
                return Long.valueOf(0);
            }
            if (value.compareTo(high) <= 0) {
                return Long.valueOf(count + 1);
            }
            return Long.valueOf(bucketOf(low.subtract(value), low.subtract(high), count));
        }
        if (value.compareTo(low) < 0) {
            return Long.valueOf(0);
        }
        if (value.compareTo(high) >= 0) {
            return Long.valueOf(count + 1);
        }
        return Long.valueOf(bucketOf(value.subtract(low), high.subtract(low), count));
    }

    /** Whether an argument is a number that is not a NaN or infinite FLOAT. */
    private static boolean finite(final Object arg) {
        return !(arg instanceof Double) || Double.isFinite(((Double) arg).doubleValue());
    }

    /** The bucket in FLOAT arithmetic, for an argument that is NaN or infinite. */
    private static long floatBucket(final double value, final double low, final double high, final long count) {
        if (low < high) {
            if (value < low) {
                return 0;
            }
            if (value >= high) {
                return count + 1;
            }
            return (long) Math.floor(count * (value - low) / (high - low)) + 1;
        }
        if (value > low) {
            return 0;
        }
        if (value <= high) {
            return count + 1;
        }
        return (long) Math.floor(count * (low - value) / (low - high)) + 1;
    }

    /** How far into the range {@code offset} sits, in buckets, counting from one. */
    private static long bucketOf(final BigDecimal offset, final BigDecimal span, final long count) {
        return (long) Math.floor(count * offset.doubleValue() / span.doubleValue()) + 1;
    }

    /** Live's sentence, with the bounds unscaled at the widest scale the three numbers carry. */
    private static String refusal(final long count, final BigDecimal low, final BigDecimal high,
                                  final BigDecimal value) {
        final int scale = Math.max(value.scale(), Math.max(low.scale(), high.scale()));
        return "Invalid argument for width bucket function, want " + count
            + " (num_buckets) > 0 and " + low.setScale(scale).unscaledValue()
            + " (min_value) != " + high.setScale(scale).unscaledValue() + " (max_value)";
    }

    @Override
    public int getMinArgCount() { return 4; }
    @Override
    public int getMaxArgCount() { return 4; }
}
