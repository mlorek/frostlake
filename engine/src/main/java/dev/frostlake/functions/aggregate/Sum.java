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
import dev.frostlake.types.NumericType;

import java.util.List;

public class Sum extends AggregateFunction {
    public Sum() {
        super("SUM", NumericType.NUMBER);
    }

    @Override
    public Accumulator createAccumulator() {
        return new SumAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

    /**
     * SUM adds numbers, and a semi-structured value is not one. Live, {@code SUM(o)} over
     * an OBJECT column is "Invalid argument types for function 'SUM': (OBJECT)" (SQLSTATE 42P13),
     * {@code SUM(a)} names ARRAY, and the DISTINCT and windowed forms reject identically — while
     * Frostlake used to answer {@code 0.0}, a plausible number a caller could go on to average.
     * A structured column names its whole type: "(OBJECT(x VARCHAR(16777216)))".
     *
     * <p>Declared here rather than by extending {@code NumericArgumentFunction} only because an
     * aggregate already extends {@link AggregateFunction}; the rule is the same one.
     */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

}
