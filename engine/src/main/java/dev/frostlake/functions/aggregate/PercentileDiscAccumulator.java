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
import dev.frostlake.values.VariantJsonNulls;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator for {@link PercentileDisc}, delegating to
 * {@link AggregateNumerics#percentileDisc(Iterable, double)}. It PICKS an input rather than computing
 * one, so the value is handed back as it arrived and keeps its column's own scale — collapsing it to a
 * double lost that, turning a NUMBER(10,2)'s 2.00 into 2.0. A VARCHAR or VARIANT key is the one
 * exception: live converts it to a whole number, and the declaration says so
 * ({@link CoercedNumericArgumentAccumulator}).
 */
public class PercentileDiscAccumulator implements AggregateFunction.Accumulator, CoercedNumericArgumentAccumulator {
    private final List<Object> values = new ArrayList<>();
    private double percentile;
    private boolean coercedArgument;

    public PercentileDiscAccumulator(final double p) { this.percentile = p; }

    /** The fraction, for the window path where the accumulator is built before the call is read. */
    public void setPercentile(final double p) { this.percentile = p; }

    @Override
    public void setCoercedNumericArgument(final boolean coerced) {
        this.coercedArgument = coerced;
    }

    /** The JSON nulls of a VARIANT argument, each sorting after every number (see AggregateNumerics). */
    private int jsonNulls;

    /** One of them, handed back when a place they take is picked. */
    private Object jsonNull;

    @Override
    public void accumulate(final Object v) {
        if (VariantJsonNulls.isJsonNull(v)) {
            jsonNulls++;
            jsonNull = v;
        } else if (v != null) {
            values.add(v);
        }
    }

    @Override
    public Object getResult() {
        final double upwards = upwardsFraction();
        return coercedArgument
            ? AggregateNumerics.percentileDiscOverCoerced(values, upwards, descending ? 0 : jsonNulls)
            : AggregateNumerics.percentileDisc(values, upwards, jsonNull, descending ? 0 : jsonNulls);
    }

    /** Whether the group is ordered downwards: the value picked is then counted from the greatest. */
    private boolean descending;

    /**
     * @param ordersDown whether WITHIN GROUP orders the values DESC
     */
    public void setDescending(final boolean ordersDown) {
        this.descending = ordersDown;
    }

    /** The fraction that picks, counting upwards, the value the given one picks in the group's own order. */
    private double upwardsFraction() {
        int present = 0;
        for (final Object value : values) {
            if (value != null) {
                present++;
            }
        }
        if (!descending || present == 0) {
            return percentile;
        }
        final int fromTheTop = Math.max(1, (int) Math.ceil(percentile * present));
        return (present - fromTheTop + 0.5) / present;
    }

    @Override
    public void reset() {
        values.clear();
        jsonNulls = 0;
        jsonNull = null;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final PercentileDiscAccumulator merged = (PercentileDiscAccumulator) other;
        values.addAll(merged.values);
        jsonNulls += merged.jsonNulls;
        if (merged.jsonNull != null) {
            jsonNull = merged.jsonNull;
        }
    }
}
