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

import dev.frostlake.executor.expressions.VariantNumbers;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;

/** Accumulator for {@link BitOrAgg}. */
public class BitOrAccumulator implements AggregateFunction.Accumulator {
    private long result = 0L;
    private boolean hasValue = false;

    @Override
    public void accumulate(final Object v) {
        if (v == null) return;
        hasValue = true;
        // A VARIANT member reads as its number, a boolean as 1 / 0; anything else fails the cast to
        // FIXED, as live's integer aggregates say (BITOR_AGG over {"x":1}).
        result |= (v instanceof VariantValue
            ? VariantNumbers.numberOf((VariantValue) v, VariantNumbers.FIXED).longValue()
            : new BigDecimal(v.toString()).longValue());
    }

    @Override
    public Object getResult() { return hasValue ? result : null; }

    @Override
    public void reset() {
        result = 0L;
        hasValue = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final BitOrAccumulator o = (BitOrAccumulator) other;
        if (o.hasValue) {
            hasValue = true;
            result |= o.result;
        }
    }
}
