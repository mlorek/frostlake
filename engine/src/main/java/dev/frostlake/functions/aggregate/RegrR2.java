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
 * REGR_R2(y, x) — coefficient of determination (R squared) over non-null (y, x) pairs (y dependent, x independent). See
 * {@link RegrAccumulator}. REGR_R2 squares both of its inputs, so it reports the same internal multiplication the
 * moment aggregates do and names the OFFENDING type on both sides whichever position it arrived in: live {@code
 * REGR_R2(o, n)} and {@code REGR_R2(n, o)} are each "Invalid argument types for function '*': (OBJECT, OBJECT)", and a
 * BOOLEAN, a temporal, a BINARY or a VECTOR is listed twice the same way (live-verified).
 */
public class RegrR2 extends StatisticalAggregate {
    public RegrR2() {
        super("REGR_R2", NumericType.DOUBLE, 2, SemiStructuredRejection.MULTIPLY_OPERANDS);
    }

    @Override
    public Accumulator createAccumulator() {
        return new RegrAccumulator(RegrKind.R2);
    }
}
