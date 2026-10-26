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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Accumulator for {@link Kurtosis}. */
public class KurtosisAccumulator
        implements AggregateFunction.Accumulator, DecfloatAwareAccumulator, DeclaredArgumentAccumulator {
    /** The fractional digits of the answer's type, NUMBER(38,12). */
    private static final int SCALE = 12;

    /** The values of a DECFLOAT argument, whose kurtosis is a DECFLOAT of 38 digits (see DecfloatMoments). */
    private final DecfloatMoments decfloatMoments = new DecfloatMoments();
    private boolean decfloat;

    /** Whether the argument is a FLOAT or a text, over which the kurtosis is a FLOAT (live-verified). */
    private boolean approximate;

    private final List<Double> values = new ArrayList<>();

    @Override
    public void setDecfloatArgument(final boolean decfloat) {
        this.decfloat = decfloat;
    }

    @Override
    public void setDeclaredArgumentType(final DataType declared) {
        approximate = NumericType.isApproximate(declared) || declared instanceof StringType;
    }

    @Override
    public void accumulate(final Object v) {
        if (decfloat) {
            decfloatMoments.add(v);
            return;
        }
        // A text reads as a decimal number, and one that spells none is refused (see NumericAggregateInput).
        if (v != null) values.add(NumericAggregateInput.asDouble(v));
    }

    @Override
    public Object getResult() {
        if (decfloat) {
            return decfloatMoments.heldKurtosis();
        }
        final int n = values.size();
        if (n < 4) return null;
        double total = 0;
        for (final Double value : values) {
            total += value.doubleValue();
        }
        final double mean = total / n;
        double squares = 0;
        for (final Double value : values) {
            squares += Math.pow(value.doubleValue() - mean, 2);
        }
        final double variance = squares / (n - 1);
        // A series that does not vary divides by its zero deviation, whatever its type (live-verified).
        if (variance == 0) {
            throw new RuntimeException("Division by zero");
        }
        final double stddev = Math.sqrt(variance);
        double sum4 = 0;
        for (final Double value : values) {
            sum4 += Math.pow((value.doubleValue() - mean) / stddev, 4);
        }
        final double kurtosis = (double) n * (n + 1) / ((n - 1) * (n - 2) * (n - 3)) * sum4
            - 3.0 * (n - 1) * (n - 1) / ((n - 2) * (n - 3));
        if (Double.isNaN(kurtosis) || Double.isInfinite(kurtosis)) {
            return null;
        }
        if (approximate) {
            return kurtosis;
        }
        // The answer is a NUMBER of twelve fractional digits.
        return BigDecimal.valueOf(kurtosis).setScale(SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public void reset() {
        decfloatMoments.reset();
        values.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        decfloatMoments.merge(((KurtosisAccumulator) other).decfloatMoments);
        values.addAll(((KurtosisAccumulator) other).values);
    }
}
