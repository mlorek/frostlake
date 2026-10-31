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
 * COVAR_POP(y, x). Either argument outside the numbers, the strings and VARIANT is refused at compile time as the
 * DOUBLE conversion live plans under a null guard on the other argument, and a VECTOR as a BOOLEAN is (live-verified) —
 * see {@link SemiStructuredRejection#DOUBLE_CONVERSION_PARAMETER}.
 */
public class CovarPop extends StatisticalAggregate {
    public CovarPop() {
        super("COVAR_POP", NumericType.DOUBLE, 2, SemiStructuredRejection.DOUBLE_CONVERSION_PARAMETER);
    }

    @Override
    public Accumulator createAccumulator() {
        return new CovarAccumulator(false);
    }
}
