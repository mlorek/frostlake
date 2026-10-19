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

import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.values.Decfloats;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator for {@link Avg}, delegating the numeric result to {@link AggregateNumerics#avg(Iterable)} so
 * every AVG code path shares the same live-verified Snowflake typing: fixed-point inputs (Long/Integer/
 * BigDecimal) yield a BigDecimal with scale = (max input scale) + 6 rounded HALF_UP and trailing zeros kept
 * (AVG of 90 and 95 is exactly 92.500000), while any Double/Float/VARIANT input keeps the double average.
 * NULL inputs are ignored; no non-null input yields NULL.
 */
public class AvgAccumulator
        implements AggregateFunction.Accumulator, ApproximateAwareAccumulator, DecfloatAwareAccumulator {

    private final List<Object> values = new ArrayList<>();
    private boolean approximateArgument;
    /** Whether the argument is declared DECFLOAT: the average is then a DECFLOAT (see Decfloats). */
    private boolean decfloatArgument;

    /**
     * The argument's DECLARED type, which the values alone cannot say: a FLOAT column is stored
     * exactly here, so only this flag keeps its average on the double path. Without it live's
     * 2.333333333 came back 2.333333, padded to the scale an exact input would have earned.
     *
     * @param approximate whether the aggregated expression is declared FLOAT / DOUBLE / REAL
     */
    @Override
    public void setApproximateArgument(final boolean approximate) {
        this.approximateArgument = approximate;
    }

    @Override
    public void setDecfloatArgument(final boolean decfloat) {
        this.decfloatArgument = decfloat;
    }

    @Override
    public void accumulate(final Object value) {
        if (value != null) {
            values.add(value);
        }
    }

    @Override
    public Object getResult() {
        if (decfloatArgument) {
            // A DECFLOAT average is the exact sum over the count, to 38 significant digits (live-verified).
            if (values.isEmpty()) {
                return null;
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (final Object value : values) {
                sum = sum.add(Decfloats.of((Number) value, "AVG"));
            }
            return Decfloats.divide(sum, BigDecimal.valueOf(values.size()));
        }
        return AggregateNumerics.avg(values, approximateArgument);
    }

    @Override
    public void reset() {
        values.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        values.addAll(((AvgAccumulator) other).values);
    }
}
