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

/**
 * AVG(expr) — see {@link AvgAccumulator} / {@link AggregateNumerics#avg(Iterable)} for the live-verified
 * Snowflake result typing (fixed-point inputs → BigDecimal scale = max input scale + 6, HALF_UP; any
 * double/VARIANT input → double; empty → NULL).
 */
public class Avg extends AggregateFunction {
    public Avg() {
        super("AVG", NumericType.DOUBLE);
    }

    @Override
    public Accumulator createAccumulator() {
        return new AvgAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    /**
     * AVG refuses a semi-structured value exactly as {@link Sum} does — live,
     * {@code AVG(DISTINCT o)} and {@code AVG(o) OVER (…)} are "Invalid argument types for function
     * 'AVG': (OBJECT)". The BARE {@code AVG(o)} form reports 'SUM' live, because Snowflake desugars
     * the average into a sum over a count before it type-checks; Frostlake reports the name written,
     * matching live in two of its three forms and never inventing a plan it does not have.
     */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
