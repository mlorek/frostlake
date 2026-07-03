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
import java.math.RoundingMode;
import java.util.List;

public class Avg extends AggregateFunction {
    public Avg() {
        super("AVG", NumericType.DOUBLE);
    }

    @Override
    public Accumulator createAccumulator() {
        return new AvgAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    private static class AvgAccumulator implements Accumulator {
        private BigDecimal sum = BigDecimal.ZERO;
        private long count = 0;

        @Override
        public void accumulate(final Object value) {
            if (value != null) {
                sum = sum.add(new BigDecimal(value.toString()));
                count++;
            }
        }

        @Override
        public Object getResult() {
            if (count == 0) return null;
            return sum.divide(BigDecimal.valueOf(count), 10, RoundingMode.HALF_UP);
        }

        @Override
        public void reset() { sum = BigDecimal.ZERO; count = 0; }

        @Override
        public void merge(final Accumulator other) {
            AvgAccumulator otherAvg = (AvgAccumulator) other;
            sum = sum.add(otherAvg.sum);
            count += otherAvg.count;
        }
    }
}
