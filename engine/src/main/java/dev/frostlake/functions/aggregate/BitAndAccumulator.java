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
import dev.frostlake.functions.scalar.math.BitwiseOperand;
import dev.frostlake.values.VariantValue;

import java.math.BigInteger;

/** Accumulator for {@link BitAndAgg}. */
public class BitAndAccumulator implements AggregateFunction.Accumulator {
    private BigInteger result = BigInteger.ONE.negate();
    private boolean hasValue = false;

    @Override
    public void accumulate(final Object v) {
        if (v == null) return;
        hasValue = true;
        // A VARIANT member reads as its number, a boolean as 1 / 0; anything else fails the cast to
        // FIXED, as live's integer aggregates say (BITOR_AGG over {"x":1}).
        result = result.and(BitAggregateOperand.of(v instanceof VariantValue
            ? VariantNumbers.numberOf((VariantValue) v, VariantNumbers.FIXED) : v));
    }

    @Override
    public Object getResult() { return hasValue ? BitwiseOperand.result(result) : null; }

    @Override
    public void reset() {
        result = BigInteger.ONE.negate();
        hasValue = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final BitAndAccumulator o = (BitAndAccumulator) other;
        if (o.hasValue) {
            hasValue = true;
            result = result.and(o.result);
        }
    }
}
