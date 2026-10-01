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
import dev.frostlake.functions.MultiArgumentAccumulator;
import dev.frostlake.values.BinaryValue;

import java.util.List;

/**
 * {@code HLL_ACCUMULATE}'s per-group state: the same sketch {@code APPROX_COUNT_DISTINCT} counts with,
 * handed back as the BINARY state rather than as a number. A group that saw no value at all answers an
 * empty BINARY (live-verified).
 */
public final class HllStateAccumulator implements AggregateFunction.Accumulator, MultiArgumentAccumulator {

    private final HllAccumulator counted = new HllAccumulator();
    private boolean sawValue;

    @Override
    public void accumulate(final List<Object> argumentValues) {
        for (final Object member : argumentValues) {
            if (member == null) {
                return;
            }
        }
        sawValue |= !argumentValues.isEmpty();
        counted.accumulate(argumentValues);
    }

    @Override
    public void accumulate(final Object value) {
        if (value == null) {
            return;
        }
        sawValue = true;
        counted.accumulate(value);
    }

    @Override
    public Object getResult() {
        return sawValue ? counted.sketch().toBinary() : BinaryValue.of(new byte[0]);
    }

    @Override
    public void reset() {
        counted.reset();
        sawValue = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final HllStateAccumulator source = (HllStateAccumulator) other;
        counted.merge(source.counted);
        sawValue |= source.sawValue;
    }
}
