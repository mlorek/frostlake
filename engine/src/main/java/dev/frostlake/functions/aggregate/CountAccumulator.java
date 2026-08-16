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

/** Accumulator for {@link Count}. */
public class CountAccumulator implements AggregateFunction.Accumulator {
    private long count = 0;

    @Override
    public void accumulate(final Object value) {
        if (value != null) count++;
    }

    @Override
    public Object getResult() { return count; }

    @Override
    public void reset() { count = 0; }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        count += ((CountAccumulator) other).count;
    }
}
