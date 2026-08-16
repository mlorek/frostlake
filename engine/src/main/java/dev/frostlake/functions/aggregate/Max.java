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
import dev.frostlake.types.VariantType;

import java.util.List;

public class Max extends AggregateFunction {
    public Max() {
        super("MAX", VariantType.VARIANT);
    }

    @Override
    public Accumulator createAccumulator() {
        return new MaxAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    /** A UUID has no ordering the account will take: it refuses the argument type. */
    @Override
    public SemiStructuredRejection uuidRejection(final int position) {
        return SemiStructuredRejection.UNSUPPORTED_ARGUMENT_TYPE;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

}
