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

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.NumericType;

/**
 * REGR_SXX(y, x) — sum of squares of x deviations over non-null (y, x) pairs (y dependent, x independent). See {@link
 * RegrAccumulator}. Only the X — the SECOND argument — is squared, so only it is refused outside the numbers, the
 * strings and VARIANT, a VECTOR as a BOOLEAN is, as the DOUBLE conversion live plans under a null guard on the Y
 * (live-verified). See {@link SemiStructuredRejection#DOUBLE_CONVERSION_PARAMETER}.
 */
public class RegrSxx extends StatisticalAggregate {
    public RegrSxx() {
        super("REGR_SXX", NumericType.DOUBLE, 2, SemiStructuredRejection.DOUBLE_CONVERSION_PARAMETER, 1);
    }

    @Override
    public Accumulator createAccumulator() {
        return new RegrAccumulator(RegrKind.SXX);
    }
}
