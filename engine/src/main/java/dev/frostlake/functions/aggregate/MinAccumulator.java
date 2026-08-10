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

/** Accumulator for {@link Min}. */
public class MinAccumulator implements AggregateFunction.Accumulator {
    private Comparable min = null;

    @Override
    @SuppressWarnings("unchecked")
    public void accumulate(final Object value) {
        if (value != null && value instanceof Comparable) {
            if (min == null || ((Comparable) value).compareTo(min) < 0) {
                min = (Comparable) value;
            }
        }
    }

    @Override
    public Object getResult() { return min; }

    @Override
    public void reset() { min = null; }

    @Override
    @SuppressWarnings("unchecked")
    public void merge(final AggregateFunction.Accumulator other) {
        final Comparable otherMin = ((MinAccumulator) other).min;
        if (otherMin != null && (min == null || otherMin.compareTo(min) < 0)) {
            min = otherMin;
        }
    }
}
