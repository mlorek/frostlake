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
import dev.frostlake.types.BooleanType;

import java.util.List;

public class BoolOrAgg extends AggregateFunction {
    public BoolOrAgg() { super("BOOLOR_AGG", new BooleanType()); }

    @Override
    public Accumulator createAccumulator() { return new BoolOrAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    private static class BoolOrAccumulator implements Accumulator {
        private boolean result = false; private boolean hasValue = false;

        @Override
        public void accumulate(final Object v) {
            if (v == null) return; hasValue = true; result = result || isTruthy(v);
        }

        @Override
        public Object getResult() { return hasValue ? result : null; }

        @Override
        public void reset() { result = false; hasValue = false; }

        @Override
        public void merge(final Accumulator other) {
            BoolOrAccumulator o = (BoolOrAccumulator) other;
            if (o.hasValue) { hasValue = true; result = result || o.result; }
        }
    }

    static boolean isTruthy(final Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        if (v instanceof String) { String s = ((String) v).trim().toUpperCase(); return s.equals("TRUE") || s.equals("1"); }
        return false;
    }
}
