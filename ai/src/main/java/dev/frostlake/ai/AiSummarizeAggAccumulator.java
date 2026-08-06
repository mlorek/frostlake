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

package dev.frostlake.ai;

import dev.frostlake.functions.AggregateFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Gathers a group's texts and summarizes them in one call at the end. Holding the rows rather than
 * summarizing incrementally is what makes the answer a summary of the GROUP: a running summary would
 * weight the first rows far more heavily than the last.
 */
class AiSummarizeAggAccumulator implements AggregateFunction.Accumulator {

    private final List<String> collected = new ArrayList<>();

    @Override
    public void accumulate(final Object value) {
        if (value != null) {
            collected.add(String.valueOf(value));
        }
    }

    @Override
    public Object getResult() {
        if (collected.isEmpty()) {
            return null;
        }
        return CortexAggregation.answer("Summarize the following texts as a single short summary.",
            collected);
    }

    @Override
    public void reset() {
        collected.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        if (other instanceof AiSummarizeAggAccumulator) {
            collected.addAll(((AiSummarizeAggAccumulator) other).collected);
        }
    }
}
