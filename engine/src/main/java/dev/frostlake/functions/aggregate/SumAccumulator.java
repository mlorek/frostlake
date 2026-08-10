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
import dev.frostlake.values.VariantValue;
import java.math.BigDecimal;

/** Accumulator for {@link Sum}. */
public class SumAccumulator implements AggregateFunction.Accumulator {
    private BigDecimal sum = BigDecimal.ZERO;
    private boolean hasValue = false;
    // Live-verified: SUM over VARIANT values is DOUBLE (SUM of PARSE_JSON('1'), PARSE_JSON('2') is 3.0,
    // SYSTEM$TYPEOF FLOAT) — any VARIANT input flips the whole sum to a double result.
    private boolean anyVariant = false;

    @Override
    public void accumulate(final Object value) {
        if (value != null) {
            if (value instanceof VariantValue) {
                anyVariant = true;
            }
            sum = sum.add(new BigDecimal(value.toString()));
            hasValue = true;
        }
    }

    // SUM over no non-null input rows is NULL in Snowflake, not zero.
    @Override
    public Object getResult() {
        if (!hasValue) {
            return null;
        }
        return anyVariant ? Double.valueOf(sum.doubleValue()) : sum;
    }

    @Override
    public void reset() {
        sum = BigDecimal.ZERO;
        hasValue = false;
        anyVariant = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final SumAccumulator o = (SumAccumulator) other;
        sum = sum.add(o.sum);
        hasValue = hasValue || o.hasValue;
        anyVariant = anyVariant || o.anyVariant;
    }
}
