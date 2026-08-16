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
import dev.frostlake.types.ObjectType;

import java.util.List;

/**
 * APPROX_PERCENTILE_ACCUMULATE(expr): the {@link PercentileDigest} of a numeric input as an OBJECT
 * state, for APPROX_PERCENTILE_COMBINE to merge and APPROX_PERCENTILE_ESTIMATE to read. NULLs are
 * skipped and an empty input is the empty state. A DATE, TIME, TIMESTAMP, BOOLEAN or BINARY argument
 * is refused at compile time as an invalid argument type; a VARCHAR or VARIANT one is read at row time
 * and refused there when a value spells no number.
 */
public class ApproxPercentileAccumulate extends AggregateFunction {

    public ApproxPercentileAccumulate() {
        super("APPROX_PERCENTILE_ACCUMULATE", ObjectType.OBJECT);
    }

    @Override
    public Accumulator createAccumulator() {
        return new PercentileDigestAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return null;
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }

    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
