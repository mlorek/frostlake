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

public class BitXorAgg extends AggregateFunction {
    public BitXorAgg() { super("BITXOR_AGG", NumericType.BIGINT); }

    @Override
    public Accumulator createAccumulator() { return new BitXorAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    private static class BitXorAccumulator implements Accumulator {
        private long result = 0L; private boolean hasValue = false;

        @Override
        public void accumulate(final Object v) {
            if (v == null) return; hasValue = true;
            result ^= new BigDecimal(v.toString()).longValue();
        }

        @Override
        public Object getResult() { return hasValue ? result : null; }

        @Override
        public void reset() { result = 0L; hasValue = false; }

        @Override
        public void merge(final Accumulator other) {
            BitXorAccumulator o = (BitXorAccumulator) other;
            if (o.hasValue) { hasValue = true; result ^= o.result; }
        }
    }
}
