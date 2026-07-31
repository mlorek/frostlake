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
import dev.frostlake.types.VectorElementType;
import dev.frostlake.types.VectorType;

import java.util.List;

/**
 * {@code VECTOR_MAX(v)} — the element-wise MAXIMUM of a group's vectors, keeping the element type: over the rows {@code [1,9,3]}, {@code [4,5,-6]} and {@code [7,2,0]} it is {@code [7.0,9.0,3.0]} (live-verified), i.e. each dimension is maximised on its own.
 *
 * <p>See {@link VectorAggregateAccumulator} for the live-verified element-wise semantics, the NULL-row
 * handling and the result's element type. The nominal return type declared here is a placeholder: the
 * real one depends on the ARGUMENT and is computed by the expression layer's type inference.
 */
public class VectorMax extends AggregateFunction {

    public VectorMax() {
        super("VECTOR_MAX", new VectorType(VectorElementType.FLOAT, 1));
    }

    @Override
    public Accumulator createAccumulator() {
        return new VectorAggregateAccumulator(VectorAggregateKind.MAX, getName());
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
}
