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
import dev.frostlake.types.VariantType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mode extends AggregateFunction {
    public Mode() { super("MODE", VariantType.VARIANT); }

    @Override
    public Accumulator createAccumulator() { return new ModeAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    private static class ModeAccumulator implements Accumulator {
        private final Map<String, Long> freq = new HashMap<>();
        private final Map<String, Object> originals = new HashMap<>();

        @Override
        public void accumulate(final Object v) {
            if (v == null) return;
            String key = v.toString(); freq.merge(key, 1L, Long::sum); originals.putIfAbsent(key, v);
        }

        @Override
        public Object getResult() {
            return freq.entrySet().stream().max(Map.Entry.comparingByValue())
                .map((final var e) -> originals.get(e.getKey())).orElse(null);
        }

        @Override
        public void reset() { freq.clear(); originals.clear(); }

        @Override
        public void merge(final Accumulator other) {
            ModeAccumulator o = (ModeAccumulator) other;
            o.freq.forEach((final var k, final var v) -> freq.merge(k, v, Long::sum)); originals.putAll(o.originals);
        }
    }
}
