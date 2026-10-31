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
 * STDDEV(x). The moment aggregates reach their internal sum of SQUARES before they reach any type check, so live never
 * names them: {@code STDDEV(o)} over an OBJECT column is "Invalid argument types for function '*': (OBJECT, OBJECT)",
 * listing the one argument twice. {@code STDDEV(a)} is "(ARRAY, ARRAY)" and a structured column names its whole type on
 * both sides. A BOOLEAN, a temporal, a BINARY or a VECTOR argument is refused the same way — "(DATE, DATE)" — and the
 * DISTINCT and windowed forms reject identically; {@code STDDEV(v)} over a VARIANT is accepted (live-verified).
 */
public class StdDev extends StatisticalAggregate {
    public StdDev() {
        super("STDDEV", NumericType.DOUBLE, 1, SemiStructuredRejection.MULTIPLY_OPERANDS);
    }

    @Override
    public Accumulator createAccumulator() {
        return new SpreadAccumulator(true, true);
    }
}
