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
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;

public class Sum extends AggregateFunction {
    public Sum() {
        super("SUM", NumericType.NUMBER);
    }

    @Override
    public Accumulator createAccumulator() {
        return new SumAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    private static class SumAccumulator implements Accumulator {
        private BigDecimal sum = BigDecimal.ZERO;
        private boolean hasValue = false;

        @Override
        public void accumulate(final Object value) {
            if (value != null) {
                sum = sum.add(new BigDecimal(value.toString()));
                hasValue = true;
            }
        }

        // SUM over no non-null input rows is NULL in Snowflake, not zero.
        @Override
        public Object getResult() { return hasValue ? sum : null; }

        @Override
        public void reset() {
            sum = BigDecimal.ZERO;
            hasValue = false;
        }

        @Override
        public void merge(final Accumulator other) {
            final SumAccumulator o = (SumAccumulator) other;
            sum = sum.add(o.sum);
            hasValue = hasValue || o.hasValue;
        }
    }
}
