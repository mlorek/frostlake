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

/** REGR_R2(y, x) — coefficient of determination (R squared) over non-null (y, x) pairs (y dependent, x independent). See {@link RegrAccumulator}. */
public class RegrR2 extends AggregateFunction {
    public RegrR2() { super("REGR_R2", NumericType.DOUBLE); }

    @Override
    public Accumulator createAccumulator() { return new RegrAccumulator(RegrKind.R2); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /**
     * REGR_R2 squares both of its inputs, so it reports the same internal multiplication the moment
     * aggregates do and names the OFFENDING type on both sides whichever position it arrived in: live
     * {@code REGR_R2(o, n)} and {@code REGR_R2(n, o)} are each "Invalid argument types for
     * function '*': (OBJECT, OBJECT)". The rest of the REGR family is deliberately NOT declared —
     * {@code REGR_COUNT}, {@code REGR_AVGX} and {@code REGR_SXX} ACCEPT an OBJECT in the y position
     * live, and {@code REGR_SLOPE} / {@code REGR_INTERCEPT} / {@code REGR_AVGY} / {@code REGR_SYY}
     * fail through a desugared TO_DOUBLE plan Frostlake does not have.
     */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.MULTIPLY_OPERANDS;
    }
}
