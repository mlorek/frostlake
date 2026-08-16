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

public class PercentileCont extends AggregateFunction {
    public PercentileCont() { super("PERCENTILE_CONT", NumericType.DOUBLE); }

    @Override
    public Accumulator createAccumulator() { return new PercentileContAccumulator(0.5); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /**
     * The FRACTION argument is a plain number, and refuses a semi-structured value with the ordinary
     * argument-type list: live, {@code PERCENTILE_CONT(o) WITHIN GROUP (ORDER BY n)} is
     * "Invalid argument types for function 'PERCENTILE_CONT': (OBJECT)". The value being ordered sits
     * in the WITHIN GROUP clause rather than the argument list and refuses with a DIFFERENT sentence
     * ("incompatible types: [OBJECT] and [NUMBER(9,0)]"), which is applied where that clause is read.
     */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

}
