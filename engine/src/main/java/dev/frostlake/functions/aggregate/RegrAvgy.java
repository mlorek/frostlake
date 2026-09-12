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

/** REGR_AVGY(y, x) — average of the dependent variable y over non-null (y, x) pairs (y dependent, x independent). See {@link RegrAccumulator}. */
public class RegrAvgy extends AggregateFunction {
    public RegrAvgy() { super("REGR_AVGY", NumericType.DOUBLE); }

    @Override
    public Accumulator createAccumulator() { return new RegrAccumulator(RegrKind.AVGY); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /**
     * Only the Y — the FIRST argument — is converted to a DOUBLE under live's null guard, so only it is
     * refused outside the numbers, the strings and VARIANT; the X is merely null-checked
     * (live-verified). See {@link SemiStructuredRejection#DOUBLE_CONVERSION_PARAMETER}.
     */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.DOUBLE_CONVERSION_PARAMETER : SemiStructuredRejection.NONE;
    }

    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.DOUBLE_CONVERSION_PARAMETER : SemiStructuredRejection.NONE;
    }

    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.DOUBLE_CONVERSION_PARAMETER : SemiStructuredRejection.NONE;
    }

    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.DOUBLE_CONVERSION_PARAMETER : SemiStructuredRejection.NONE;
    }
}
