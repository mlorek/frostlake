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
import java.util.HashMap;
import java.util.Map;

/** Accumulator for {@link Mode}. */
public class ModeAccumulator implements AggregateFunction.Accumulator {
    private final Map<String, Long> freq = new HashMap<>();
    private final Map<String, Object> originals = new HashMap<>();

    @Override
    public void accumulate(final Object v) {
        if (v == null) return;
        final String key = v.toString();
        freq.merge(key, 1L, Long::sum);
        originals.putIfAbsent(key, v);
    }

    @Override
    public Object getResult() {
        String best = null;
        long bestCount = Long.MIN_VALUE;
        for (final Map.Entry<String, Long> entry : freq.entrySet()) {
            if (entry.getValue().longValue() > bestCount) {
                bestCount = entry.getValue().longValue();
                best = entry.getKey();
            }
        }
        return best == null ? null : originals.get(best);
    }

    @Override
    public void reset() {
        freq.clear();
        originals.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final ModeAccumulator o = (ModeAccumulator) other;
        for (final Map.Entry<String, Long> entry : o.freq.entrySet()) {
            final Long seen = freq.get(entry.getKey());
            freq.put(entry.getKey(), seen == null ? entry.getValue()
                : Long.valueOf(seen.longValue() + entry.getValue().longValue()));
        }
        originals.putAll(o.originals);
    }
}
