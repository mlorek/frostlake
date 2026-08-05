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
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * LISTAGG(&lt;expr&gt; [, &lt;delimiter&gt;]) — concatenate values into a single string. The optional
 * delimiter defaults to the empty string (Snowflake semantics) and, when present, is applied by the
 * executor via {@link ListAggAccumulator#setDelimiter}.
 */
public class ListAgg extends AggregateFunction {
    public ListAgg() {
        super("LISTAGG", StringType.VARCHAR);
    }

    @Override
    public Accumulator createAccumulator() {
        return new ListAggAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }

    /**
     * LISTAGG joins its input as text, so neither position takes a semi-structured value. Live
     * {@code LISTAGG(o)} is "Invalid argument types for function 'LISTAGG': (OBJECT)",
     * {@code LISTAGG(a)} names ARRAY, {@code LISTAGG(s, o)} names the DELIMITER position, and the
     * {@code DISTINCT} and {@code WITHIN GROUP (ORDER BY …)} forms reject identically — while
     * {@code LISTAGG(v)} over a VARIANT is accepted even when the VARIANT holds an object.
     *
     * <p>Declared here rather than by extending {@code TextArgumentFunction} only because an
     * aggregate already extends {@link AggregateFunction}; the rule is the same one.
     */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
