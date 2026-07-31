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
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;

public class BitAndAgg extends AggregateFunction {
    public BitAndAgg() { super("BITAND_AGG", NumericType.BIGINT); }

    @Override
    public Accumulator createAccumulator() { return new BitAndAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    /**
     * The bitwise aggregates read their input as an integer. Live, {@code BITAND_AGG(o)}
     * over an OBJECT column is "Invalid argument types for function 'BITAND_AGG': (OBJECT)", and
     * {@code BITOR_AGG(a)} names ARRAY — while {@code BITOR_AGG(v)} over a VARIANT holding numbers
     * returned 3.
     */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    private static class BitAndAccumulator implements Accumulator {
        private long result = -1L; private boolean hasValue = false;

        @Override
        public void accumulate(final Object v) {
            if (v == null) return; hasValue = true;
            result &= new BigDecimal(v.toString()).longValue();
        }

        @Override
        public Object getResult() { return hasValue ? result : null; }

        @Override
        public void reset() { result = -1L; hasValue = false; }

        @Override
        public void merge(final Accumulator other) {
            BitAndAccumulator o = (BitAndAccumulator) other;
            if (o.hasValue) { hasValue = true; result &= o.result; }
        }
    }
}
