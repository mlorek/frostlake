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

package dev.frostlake.functions.scalar.vector;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.VectorValue;

import java.util.List;

/**
 * {@code VECTOR_L2_DISTANCE(v1, v2)} — the Euclidean distance {@code sqrt(Σ(a−b)²)}, as a FLOAT
 * (float64).
 *
 * <p>Live-verified: {@code VECTOR_L2_DISTANCE([1,2,3], [4,5,6])} is
 * {@code 5.196152422706632} (= √27) for {@code VECTOR(FLOAT,3)} and for {@code VECTOR(INT,3)} alike,
 * {@code SYSTEM$TYPEOF} of the result is {@code FLOAT[DOUBLE]}, a vector against itself is {@code 0.0},
 * and a typed NULL operand yields SQL NULL. Over float32 elements the account keeps full float64
 * precision in the answer: {@code [0.1,0.2,0.3]} to {@code [0.4,0.5,0.6]} is {@code 0.5196152500135338}.
 */
public class VectorL2Distance extends BuiltInFunction {

    public VectorL2Distance() {
        super("VECTOR_L2_DISTANCE", NumericType.DOUBLE);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final VectorValue left = VectorFunctionHelper.vectorArgument(getName(), args, 0);
        final VectorValue right = VectorFunctionHelper.vectorArgument(getName(), args, 1);
        if (left == null || right == null) {
            return null;
        }
        VectorFunctionHelper.requireSameType(getName(), args, left, right);
        double squares = 0.0;
        for (int i = 0; i < left.dimension(); i++) {
            final double difference = left.element(i) - right.element(i);
            squares += difference * difference;
        }
        return Double.valueOf(Math.sqrt(squares));
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }
}
