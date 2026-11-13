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

/**
 * Accumulator for {@link CountIf}: the rows whose condition holds, and NULL over no rows at all — an empty input or an
 * empty window frame — where COUNT answers 0 (live-verified).
 */
public class CountIfAccumulator implements AggregateFunction.Accumulator {
    private long count = 0;
    private boolean seen;

    @Override
    public void accumulate(final Object v) {
        seen = true;
        if (BoolOrAgg.isTruthy(v)) {
            count++;
        }
    }

    @Override
    public Object getResult() {
        return seen ? Long.valueOf(count) : null;
    }

    @Override
    public void reset() {
        count = 0;
        seen = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final CountIfAccumulator merged = (CountIfAccumulator) other;
        count += merged.count;
        seen |= merged.seen;
    }
}
