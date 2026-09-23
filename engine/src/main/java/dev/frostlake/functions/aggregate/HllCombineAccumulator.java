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
import dev.frostlake.values.BinaryValue;

/**
 * {@code HLL_COMBINE}'s per-group state: the union of the states folded in, or an empty BINARY when the
 * group held none.
 */
public final class HllCombineAccumulator implements AggregateFunction.Accumulator {

    private HllSketch combined;

    @Override
    public void accumulate(final Object value) {
        final HllSketch incoming = HllStates.sketchOf(value);
        if (incoming == null) {
            return;
        }
        combined = combined == null ? incoming : combined.merge(incoming);
    }

    @Override
    public Object getResult() {
        return combined == null ? BinaryValue.of(new byte[0]) : combined.toBinary();
    }

    @Override
    public void reset() {
        combined = null;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final HllSketch incoming = ((HllCombineAccumulator) other).combined;
        if (incoming == null) {
            return;
        }
        combined = combined == null ? incoming : combined.merge(incoming);
    }
}
