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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Median extends AggregateFunction {
    public Median() { super("MEDIAN", NumericType.DOUBLE); }

    @Override
    public Accumulator createAccumulator() { return new MedianAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    /**
     * MEDIAN orders its input against a numeric accumulator, and refuses a semi-structured value with
     * a message shape of its own: live, {@code MEDIAN(o)} is "incompatible types: [OBJECT]
     * and [NUMBER(9,0)]" (SQLSTATE 42846, vendor code 1010 — not the 42P13 argument-type list SUM
     * uses), {@code MEDIAN(a)} names ARRAY and a structured column names its whole type. It fires on
     * an EMPTY input and inside a GROUP BY alike, while {@code MEDIAN(v)} over a VARIANT is accepted.
     */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.INCOMPATIBLE_TYPES;
    }

    private static class MedianAccumulator implements Accumulator {
        private final List<Double> values = new ArrayList<>();

        @Override
        public void accumulate(final Object v) {
            if (v != null) values.add(new BigDecimal(v.toString()).doubleValue());
        }

        @Override
        public Object getResult() {
            if (values.isEmpty()) return null;
            List<Double> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            int n = sorted.size();
            return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        }

        @Override
        public void reset() { values.clear(); }

        @Override
        public void merge(final Accumulator other) { values.addAll(((MedianAccumulator) other).values); }
    }
}
