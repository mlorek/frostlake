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

/** Accumulator for {@link BoolXorAgg}. */
public class BoolXorAccumulator implements AggregateFunction.Accumulator {
    private long trueCount = 0;
    private boolean hasValue = false;

    @Override
    public void accumulate(final Object v) {
        if (v == null) return;
        hasValue = true;
        if (BoolOrAgg.isTruthy(v)) trueCount++;
    }

    /** TRUE when EXACTLY ONE input is true — three trues are FALSE on the account, not a parity. */
    @Override
    public Object getResult() { return hasValue ? (trueCount == 1) : null; }

    @Override
    public void reset() {
        trueCount = 0;
        hasValue = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final BoolXorAccumulator o = (BoolXorAccumulator) other;
        if (o.hasValue) {
            hasValue = true;
            trueCount += o.trueCount;
        }
    }
}
