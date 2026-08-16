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

/** Accumulator for {@link BoolOrAgg}. */
public class BoolOrAccumulator implements AggregateFunction.Accumulator {
    private boolean result = false;
    private boolean hasValue = false;

    @Override
    public void accumulate(final Object v) {
        if (v == null) return;
        hasValue = true;
        result = result || BoolOrAgg.isTruthy(v);
    }

    @Override
    public Object getResult() { return hasValue ? result : null; }

    @Override
    public void reset() {
        result = false;
        hasValue = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final BoolOrAccumulator o = (BoolOrAccumulator) other;
        if (o.hasValue) {
            hasValue = true;
            result = result || o.result;
        }
    }
}
