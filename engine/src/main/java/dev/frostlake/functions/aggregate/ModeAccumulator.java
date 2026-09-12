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

import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.functions.AggregateFunction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MODE's accumulator: the most frequent non-NULL value, a tie going to the value seen FIRST.
 *
 * <p>Live documents a tie as "one of them" and hands back whichever value its hash table lists
 * first — a choice that shifts with the value's family, the plan and the insertion order
 * (live-verified: {1.50, 2.50} is 1.50 in either order, {2, 4} the second inserted, a VARIANT {2, 4}
 * 4 in either order, a DATE pair the later date, a two-row table's BOOLEAN the first inserted, and
 * (7, 8, 9, 10, 11) is 9 while its reverse is 8). No rule reproduces that, so the engine keeps the one
 * deterministic choice a reader can predict; a test can pin a tie only as membership in the tied set.
 *
 * <p>Equal values share a count however they are carried — 1.50 and 1.5, a Long and a BigDecimal, a
 * VARIANT by its text — and the value handed back is the first one's own carrier.
 */
public class ModeAccumulator implements AggregateFunction.Accumulator {

    private final Map<Object, Long> counts = new LinkedHashMap<>();
    private final Map<Object, Object> originals = new LinkedHashMap<>();

    @Override
    public void accumulate(final Object value) {
        if (value == null) {
            return;
        }
        count(ValueComparisons.canonicalGroupKeyValue(value), value, 1L);
    }

    @Override
    public Object getResult() {
        Object best = null;
        long bestCount = 0L;
        for (final Map.Entry<Object, Long> entry : counts.entrySet()) {
            if (entry.getValue().longValue() > bestCount) {
                bestCount = entry.getValue().longValue();
                best = entry.getKey();
            }
        }
        return best == null ? null : originals.get(best);
    }

    @Override
    public void reset() {
        counts.clear();
        originals.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final ModeAccumulator theirs = (ModeAccumulator) other;
        for (final Map.Entry<Object, Long> entry : theirs.counts.entrySet()) {
            count(entry.getKey(), theirs.originals.get(entry.getKey()), entry.getValue().longValue());
        }
    }

    private void count(final Object key, final Object value, final long by) {
        final Long seen = counts.get(key);
        counts.put(key, Long.valueOf(seen == null ? by : seen.longValue() + by));
        if (seen == null) {
            originals.put(key, value);
        }
    }
}
