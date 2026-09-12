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

public class StdDev extends AggregateFunction {
    public StdDev() {
        super("STDDEV", NumericType.DOUBLE);
    }

    @Override
    public Accumulator createAccumulator() {
        return new StdDevAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

    /**
     * The moment aggregates reach their internal sum of SQUARES before they reach any type check, so
     * live never names them: {@code STDDEV(o)} over an OBJECT column is "Invalid argument types for
     * function '*': (OBJECT, OBJECT)", listing the one argument twice. {@code STDDEV(a)}
     * is "(ARRAY, ARRAY)" and a structured column names its whole type on both sides. The DISTINCT
     * and windowed forms reject identically; {@code STDDEV(v)} over a VARIANT is accepted.
     */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.MULTIPLY_OPERANDS;
    }

    /**
     * A BOOLEAN, a temporal or a BINARY argument is refused at compile time in the multiplication's
     * name with the type twice — "Invalid argument types for function '*': (DATE, DATE)" — exactly as
     * a semi-structured one is: live reaches its internal sum of squares first (live-verified).
     */
    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return SemiStructuredRejection.MULTIPLY_OPERANDS;
    }

    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return SemiStructuredRejection.MULTIPLY_OPERANDS;
    }

    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.MULTIPLY_OPERANDS;
    }
}
