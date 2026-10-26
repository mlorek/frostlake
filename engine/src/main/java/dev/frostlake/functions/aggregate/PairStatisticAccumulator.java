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
 * A statistic over (y, x) pairs - CORR, COVAR_POP, COVAR_SAMP and the REGR family - told whether EITHER argument is
 * declared a DECFLOAT: it then answers a DECFLOAT of 38 digits computed from the pairs as they were evaluated (see
 * {@link DecfloatPairStatistics}), and the executor feeds them through {@link #accumulateDecfloat} instead of as
 * doubles.
 */
public interface PairStatisticAccumulator extends DecfloatAwareAccumulator {

    /**
     * @return whether the statistic is held as a DECFLOAT
     */
    boolean isDecfloat();

    /**
     * Accumulate one pair neither side of which is NULL, each side as it was evaluated.
     *
     * @param y the first argument's value
     * @param x the second argument's value
     */
    void accumulateDecfloat(Object y, Object x);
}
