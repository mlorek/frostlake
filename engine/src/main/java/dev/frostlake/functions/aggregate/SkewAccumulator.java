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
import java.util.ArrayList;
import java.util.List;

/** Accumulator for {@link Skew}. */
public class SkewAccumulator implements AggregateFunction.Accumulator, DecfloatAwareAccumulator {
    /** The values of a DECFLOAT argument, whose skewness is a DECFLOAT of 38 digits (see DecfloatMoments). */
    private final DecfloatMoments decfloatMoments = new DecfloatMoments();
    private boolean decfloat;
    private final List<Double> values = new ArrayList<>();

    @Override
    public void setDecfloatArgument(final boolean decfloat) {
        this.decfloat = decfloat;
    }

    @Override
    public void accumulate(final Object v) {
        if (decfloat) {
            decfloatMoments.add(v);
            return;
        }
        // A text reads as a cast to FLOAT reads it, a hexadecimal number included (see NumericAggregateInput).
        if (v != null) values.add(NumericAggregateInput.asApproximate(v));
    }

    @Override
    public Object getResult() {
        if (decfloat) {
            return decfloatMoments.heldSkewness();
        }
        final int n = values.size();
        if (n < 3) return null;
        final double sum3 = AggregateNumerics.standardizedMomentSum(values, 3);
        return sum3 * n / ((n - 1.0) * (n - 2.0));
    }

    @Override
    public void reset() {
        decfloatMoments.reset();
        values.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        decfloatMoments.merge(((SkewAccumulator) other).decfloatMoments);
        values.addAll(((SkewAccumulator) other).values);
    }
}
