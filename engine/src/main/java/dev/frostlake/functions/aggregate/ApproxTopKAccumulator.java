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

import java.util.List;

/**
 * The accumulator of {@link ApproxTopK}: a {@link TopKSummary} sized by the call's third argument,
 * answering the first k of its ranking. The limits come from the argument texts (so an empty group
 * is still sized as written) and, when no texts were given, from the first row's constant values.
 */
public class ApproxTopKAccumulator implements AggregateFunction.Accumulator, MultiArgumentAccumulator,
        ConstantArgumentsAccumulator {

    private int items = 1;
    private int counters = TopKSummary.DEFAULT_COUNTERS;
    private boolean configured;
    private TopKSummary summary;

    @Override
    public void setConstantArgumentTexts(final List<String> argumentTexts) {
        items = ConstantArgumentTexts.integer(argumentTexts, 1, 1);
        counters = ConstantArgumentTexts.integer(argumentTexts, 2, TopKSummary.DEFAULT_COUNTERS);
        configured = true;
    }

    @Override
    public void accumulate(final List<Object> argumentValues) {
        if (!configured) {
            if (argumentValues.size() > 1 && argumentValues.get(1) instanceof Number) {
                items = ((Number) argumentValues.get(1)).intValue();
            }
            if (argumentValues.size() > 2 && argumentValues.get(2) instanceof Number) {
                counters = ((Number) argumentValues.get(2)).intValue();
            }
            configured = true;
        }
        accumulate(argumentValues.isEmpty() ? null : argumentValues.get(0));
    }

    @Override
    public void accumulate(final Object value) {
        if (value != null) {
            summary().add(value);
        }
    }

    @Override
    public Object getResult() {
        return TopKSummary.pairsOf(summary().top(items));
    }

    @Override
    public void reset() {
        summary = null;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final TopKSummary theirs = ((ApproxTopKAccumulator) other).summary;
        if (theirs == null) {
            return;
        }
        final List<TopKCounter> ranked = theirs.ranked();
        for (int i = ranked.size() - 1; i >= 0; i--) {
            summary().merge(ranked.get(i).value(), ranked.get(i).count());
        }
        summary().fit(counters);
    }

    private TopKSummary summary() {
        if (summary == null) {
            summary = new TopKSummary(counters);
        }
        return summary;
    }
}
