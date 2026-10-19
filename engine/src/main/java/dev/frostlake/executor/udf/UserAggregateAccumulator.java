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

package dev.frostlake.executor.udf;

import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.MultiArgumentAccumulator;

import java.util.ArrayList;
import java.util.List;

/**
 * One group of a user's aggregate function: the rows it reads are kept, every argument of each, and handed to the
 * function's handler once the group's value is asked for (see {@link UserAggregateFunction}).
 */
public final class UserAggregateAccumulator implements AggregateFunction.Accumulator, MultiArgumentAccumulator {

    private final UserAggregateFunction aggregate;
    private final List<List<Object>> rows = new ArrayList<>();
    private boolean answered;
    private Object answer;

    /**
     * @param aggregate the function the group is aggregated by
     */
    public UserAggregateAccumulator(final UserAggregateFunction aggregate) {
        this.aggregate = aggregate;
    }

    @Override
    public void accumulate(final Object value) {
        final List<Object> row = new ArrayList<>();
        row.add(value);
        rows.add(row);
        answered = false;
    }

    @Override
    public void accumulate(final List<Object> argumentValues) {
        rows.add(new ArrayList<>(argumentValues));
        answered = false;
    }

    @Override
    public Object getResult() {
        if (!answered) {
            answer = aggregate.aggregate(rows);
            answered = true;
        }
        return answer;
    }

    @Override
    public void reset() {
        rows.clear();
        answered = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        if (other instanceof UserAggregateAccumulator) {
            rows.addAll(((UserAggregateAccumulator) other).rows);
            answered = false;
        }
    }
}
