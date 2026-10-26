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

package dev.frostlake.functions.window;

/**
 * What a variance-family aggregate reads off a frame's FLOAT moments (see {@link FloatFrameMoments}): the sample or
 * the population variance, or its square root. The sample statistics have no answer under two values and the
 * population ones none under one, as the grouped aggregates have none.
 */
public enum MomentStatistic {
    SAMPLE_VARIANCE(true, false),
    POPULATION_VARIANCE(false, false),
    SAMPLE_DEVIATION(true, true),
    POPULATION_DEVIATION(false, true);

    private final boolean sample;
    private final boolean root;

    MomentStatistic(final boolean sample, final boolean root) {
        this.sample = sample;
        this.root = root;
    }

    /**
     * The statistic an aggregate's name stands for.
     *
     * @param name the upper-cased aggregate name
     * @return the statistic, or null for an aggregate that is none of the family
     */
    public static MomentStatistic named(final String name) {
        switch (name) {
            case "VARIANCE":
            case "VAR_SAMP":
            case "VARIANCE_SAMP":
                return SAMPLE_VARIANCE;
            case "VAR_POP":
            case "VARIANCE_POP":
                return POPULATION_VARIANCE;
            case "STDDEV":
            case "STDDEV_SAMP":
                return SAMPLE_DEVIATION;
            case "STDDEV_POP":
                return POPULATION_DEVIATION;
            default:
                return null;
        }
    }

    /**
     * The statistic of one row's frame.
     *
     * @param moments the partition's frame moments
     * @param row the row's position in the window's order
     * @return the statistic as a double, or null when the frame holds too few values
     */
    public Double of(final FloatFrameMoments moments, final int row) {
        if (moments.count(row) < (sample ? 2 : 1)) {
            return null;
        }
        final double variance = sample ? moments.sampleVariance(row) : moments.populationVariance(row);
        return Double.valueOf(root ? Math.sqrt(variance) : variance);
    }
}
