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

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.ApproximateValues;

import java.math.BigDecimal;
import java.util.List;

/**
 * ABS(n) — the absolute value of the numeric input, computed exactly over {@link BigDecimal} so a
 * fixed-point argument keeps its scale. NULL yields NULL.
 */
public class Abs extends NumericArgumentFunction {
    /** Registers the function as {@code ABS} returning NUMBER. */
    public Abs() { super("ABS", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (ApproximateValues.isApproximate(args.get(0))) {
            // A double stays a double, and a NEGATIVE ZERO keeps its sign: ABS(-0.0::FLOAT) is -0 on
            // the account, which is what "negate when below zero" gives and Math.abs does not.
            final double value = ((Number) args.get(0)).doubleValue();
            return Double.valueOf(value < 0 ? -value : value);
        }
        final BigDecimal magnitude = new BigDecimal(args.get(0).toString()).abs();
        // The carrier is the only ceiling, as for the operators: ABS(-2^127) is the one exact value
        // past it and is refused in the raw-result form, while a 39-digit magnitude still inside the
        // window — ABS(-a - 1) over 38 nines, 2^127 - 1 — answers (live-verified).
        if (magnitude.precision() > 38 && NumericRangeRefusal.outsideSb16Window(magnitude.unscaledValue())) {
            throw new RuntimeException(NumericRangeRefusal.typedDouble("SB16", 38, 0, false,
                new BigDecimal(magnitude.unscaledValue())));
        }
        return magnitude;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
