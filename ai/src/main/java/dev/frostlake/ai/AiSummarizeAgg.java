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
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * AI_SUMMARIZE_AGG(text) — one summary across every row of the group, rather than one summary per
 * row. An empty group, and a group whose every value is NULL, summarize to NULL.
 *
 * <p>Registered under the bare name only: Snowflake has no {@code SNOWFLAKE.CORTEX.AI_SUMMARIZE_AGG}.
 */
public class AiSummarizeAgg extends AggregateFunction {

    public AiSummarizeAgg(final String name) {
        super(name, StringType.VARCHAR);
    }

    @Override
    public Accumulator createAccumulator() {
        return new AiSummarizeAggAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return null;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
