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
import dev.frostlake.functions.MultiArgumentAccumulator;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects a group's texts together with the instruction they are to be answered under. Both arrive
 * per row — the instruction is the same on every one — so the last row's wins, which is the same
 * answer a constant argument gives on any row.
 */
class AiAggAccumulator implements AggregateFunction.Accumulator, MultiArgumentAccumulator {

    private final List<String> collected = new ArrayList<>();
    private String instruction;

    @Override
    public void accumulate(final List<Object> argumentValues) {
        if (argumentValues.size() > 1 && argumentValues.get(1) != null) {
            instruction = String.valueOf(argumentValues.get(1));
        }
        accumulate(argumentValues.isEmpty() ? null : argumentValues.get(0));
    }

    @Override
    public void accumulate(final Object value) {
        if (value != null) {
            collected.add(String.valueOf(value));
        }
    }

    @Override
    public Object getResult() {
        if (collected.isEmpty() || instruction == null) {
            return null;
        }
        return CortexAggregation.answer(instruction, collected);
    }

    @Override
    public void reset() {
        collected.clear();
        instruction = null;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        if (other instanceof AiAggAccumulator) {
            final AiAggAccumulator merged = (AiAggAccumulator) other;
            collected.addAll(merged.collected);
            if (instruction == null) {
                instruction = merged.instruction;
            }
        }
    }
}
