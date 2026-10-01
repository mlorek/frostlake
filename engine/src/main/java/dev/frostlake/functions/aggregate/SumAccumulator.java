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

package dev.frostlake.functions.aggregate;

import dev.frostlake.executor.AggregateRangeRefusal;
import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.values.DayTimeInterval;
import dev.frostlake.values.VariantValue;
import java.math.BigDecimal;

/**
 * Accumulator for {@link Sum}.
 *
 * <p>Exact inputs sum exactly. A FLOAT input — by its runtime class, or by the argument's DECLARED type
 * when its values arrive in an exact carrier — puts the whole sum on the double path, compensated the way
 * the account adds doubles; a VARIANT input does the same (live: SUM of PARSE_JSON('1') and
 * PARSE_JSON('2') is 3.0, SYSTEM$TYPEOF FLOAT). Every input feeds the double sum in order regardless,
 * so the answer is the same whichever input first decides the tier.
 */
public class SumAccumulator implements AggregateFunction.Accumulator, ApproximateAwareAccumulator {
    private BigDecimal sum = BigDecimal.ZERO;
    /** The running total of day-time interval inputs, or null while none has arrived. */
    private DayTimeInterval intervals;
    private boolean hasValue = false;
    private boolean anyVariant = false;
    private boolean anyApproximate = false;
    private final CompensatedSum approximate = new CompensatedSum();

    @Override
    public void setApproximateArgument(final boolean declaredApproximate) {
        anyApproximate = anyApproximate || declaredApproximate;
    }

    @Override
    public void accumulate(final Object value) {
        if (value instanceof DayTimeInterval) {
            // Day-time intervals add as intervals: SUM(ts - ts2) is the interval of the summed spans.
            intervals = intervals == null ? DayTimeInterval.ofSeconds(((DayTimeInterval) value).seconds())
                : intervals.plus((DayTimeInterval) value);
            hasValue = true;
            return;
        }
        if (value != null) {
            if (value instanceof VariantValue) {
                anyVariant = true;
            }
            if (AggregateNumerics.isApproximateInput(value)) {
                anyApproximate = true;
            }
            approximate.add(NumericAggregateInput.asDouble(value));
            if (!anyVariant && !anyApproximate) {
                sum = sum.add(new BigDecimal(value.toString()));
                requireSumFits(sum);
            }
            hasValue = true;
        }
    }

    /**
     * An exact SUM whose RAW running total leaves the signed 128-bit carrier is refused as live refuses
     * it — "Value overflow in a SUM aggregate", no prefix, at the row that crosses the window: a total
     * reaching 1e38 or 2^127 - 1 answers, 2^127 or -2^128 does not, a scaled column is judged by its
     * raw, and a total that would come back inside on a later row is refused all the same
     * (live-verified). The same sentence covers AVG, which sums the same way.
     *
     * @param total the running total
     */
    static void requireSumFits(final BigDecimal total) {
        if (total.precision() > 38 && NumericRangeRefusal.outsideSb16Window(total.unscaledValue())) {
            throw new AggregateRangeRefusal("Value overflow in a SUM aggregate");
        }
    }

    // SUM over no non-null input rows is NULL in Snowflake, not zero. A double sum is read with its last
    // compensation applied, as the aggregate reads it (see PartialFloatSum).
    @Override
    public Object getResult() {
        if (!hasValue) {
            return null;
        }
        if (intervals != null) {
            return intervals;
        }
        return anyVariant || anyApproximate ? Double.valueOf(approximate.correctedValue()) : sum;
    }

    @Override
    public void reset() {
        intervals = null;
        sum = BigDecimal.ZERO;
        hasValue = false;
        anyVariant = false;
        anyApproximate = false;
        approximate.reset();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final SumAccumulator o = (SumAccumulator) other;
        if (o.intervals != null) {
            intervals = intervals == null ? o.intervals : intervals.plus(o.intervals);
        }
        sum = sum.add(o.sum);
        requireSumFits(sum);
        if (o.approximate.isSeeded()) {
            approximate.add(o.approximate.value());
        }
        hasValue = hasValue || o.hasValue;
        anyVariant = anyVariant || o.anyVariant;
        anyApproximate = anyApproximate || o.anyApproximate;
    }
}
