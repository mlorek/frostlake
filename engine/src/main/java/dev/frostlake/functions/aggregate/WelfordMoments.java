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

/**
 * The running count, mean and sum of squared deviations of a double series — Welford's update,
 * merged with Chan's formula — which the variance family uses over EXACT inputs. Those answers are
 * presented at a declared scale, which a double carrying the running mean reaches without the
 * cancellation a sum of squares suffers: live, VARIANCE over the NUMBERs 100000000, 100000001 and
 * 100000002 is 1.000000.
 *
 * <p>Over FLOATs the account reads sums instead, digit for digit — see {@link SquaredSumMoments}.
 */
public final class WelfordMoments {
    private long count;
    private double mean;
    private double m2;

    /** Fold one value in. */
    public void add(final double value) {
        count++;
        final double delta = value - mean;
        mean += delta / count;
        m2 += delta * (value - mean);
    }

    /** Fold another series' moments in (Chan's parallel form). */
    public void merge(final WelfordMoments other) {
        if (other.count == 0) {
            return;
        }
        if (count == 0) {
            count = other.count;
            mean = other.mean;
            m2 = other.m2;
            return;
        }
        final double delta = other.mean - mean;
        final long total = count + other.count;
        m2 += other.m2 + delta * delta * ((double) count * other.count / total);
        mean += delta * other.count / total;
        count = total;
    }

    public long count() {
        return count;
    }

    /** The sample variance (denominator n - 1); the caller checks there are two rows at least. */
    public double sampleVariance() {
        return Math.max(0.0, m2 / (count - 1));
    }

    /** The population variance (denominator n); the caller checks there is a row at least. */
    public double populationVariance() {
        return Math.max(0.0, m2 / count);
    }

    public void reset() {
        count = 0;
        mean = 0.0;
        m2 = 0.0;
    }
}
