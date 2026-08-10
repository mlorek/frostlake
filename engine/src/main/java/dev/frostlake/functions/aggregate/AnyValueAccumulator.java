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

/** Accumulator for {@link AnyValue}. */
public class AnyValueAccumulator implements AggregateFunction.Accumulator {
    private Object value = null;
    private boolean set = false;

    @Override
    public void accumulate(final Object v) {
        if (!set && v != null) {
            value = v;
            set = true;
        }
    }

    @Override
    public Object getResult() { return value; }

    @Override
    public void reset() {
        value = null;
        set = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final AnyValueAccumulator o = (AnyValueAccumulator) other;
        if (!set && o.set) {
            value = o.value;
            set = true;
        }
    }
}
