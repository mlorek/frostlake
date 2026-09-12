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

/**
 * Shared accumulator for the REGR_* linear-regression family. All nine functions are derived from the same
 * running sums over the non-null (y, x) pairs — note Snowflake's argument order {@code REGR_xxx(y, x)}, with
 * the dependent variable y first and the independent variable x second. {@link #getResult()} returns the
 * member selected by {@link RegrKind}.
 *
 * <p>With {@code n} pairs and centered sums {@code Sxx = Σx² − (Σx)²/n}, {@code Syy}, {@code Sxy}:
 * COUNT = n; AVGX = Σx/n; AVGY = Σy/n; SXX = Sxx; SYY = Syy; SXY = Sxy; SLOPE = Sxy/Sxx;
 * INTERCEPT = AVGY − SLOPE·AVGX; R2 = NULL if Sxx=0, else 1 if Syy=0, else Sxy²/(Sxx·Syy) (= CORR²).
 */
public class RegrAccumulator implements AggregateFunction.Accumulator {

    private final RegrKind kind;
    private long n = 0;
    private double sumX = 0;
    private double sumY = 0;
    private double sumXX = 0;
    private double sumYY = 0;
    private double sumXY = 0;

    public RegrAccumulator(final RegrKind kind) {
        this.kind = kind;
    }

    /** The member this accumulator computes, which decides which side of a pair is read at all. */
    public RegrKind kind() {
        return kind;
    }

    @Override
    public void accumulate(final Object value) {
        // The executor drives two-argument aggregates through accumulate(y, x); the single-value form is unused.
    }

    /** Accumulate one non-null pair. Snowflake order: {@code y} is the dependent (first) arg, {@code x} the second. */
    public void accumulate(final double y, final double x) {
        n++;
        sumX += x;
        sumY += y;
        sumXX += x * x;
        sumYY += y * y;
        sumXY += x * y;
    }

    @Override
    public Object getResult() {
        if (kind == RegrKind.COUNT) {
            return n;
        }
        if (n == 0) {
            return null;
        }
        final double avgX = sumX / n;
        final double avgY = sumY / n;
        final double sxx = sumXX - sumX * sumX / n;
        final double syy = sumYY - sumY * sumY / n;
        final double sxy = sumXY - sumX * sumY / n;
        switch (kind) {
            case AVGX:
                return avgX;
            case AVGY:
                return avgY;
            case SXX:
                return sxx;
            case SYY:
                return syy;
            case SXY:
                return sxy;
            case SLOPE:
                return sxx == 0 ? null : sxy / sxx;
            case INTERCEPT:
                return sxx == 0 ? null : avgY - (sxy / sxx) * avgX;
            case R2:
                if (sxx == 0) {
                    return null;
                }
                if (syy == 0) {
                    return 1.0;
                }
                return (sxy * sxy) / (sxx * syy);
            default:
                return null;
        }
    }

    @Override
    public void reset() {
        n = 0;
        sumX = 0;
        sumY = 0;
        sumXX = 0;
        sumYY = 0;
        sumXY = 0;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final RegrAccumulator o = (RegrAccumulator) other;
        n += o.n;
        sumX += o.sumX;
        sumY += o.sumY;
        sumXX += o.sumXX;
        sumYY += o.sumYY;
        sumXY += o.sumXY;
    }
}
