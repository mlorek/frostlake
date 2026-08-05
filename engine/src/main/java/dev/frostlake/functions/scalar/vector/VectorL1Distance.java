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
 * {@code VECTOR_L1_DISTANCE(v1, v2)} — the Manhattan distance {@code Σ|a−b|}, as a FLOAT (float64).
 *
 * <p>Live-verified: {@code VECTOR_L1_DISTANCE([1,2,3], [4,5,6])} is {@code 9.0} for both
 * {@code VECTOR(FLOAT,3)} and {@code VECTOR(INT,3)} operands. The 32-bit ELEMENTS feed 64-bit
 * arithmetic — over {@code [0.1,0.2,0.3]} and {@code [0.4,0.5,0.6]} the account returns
 * {@code 0.9000000134110451}, which is EXACTLY the float64 sum of the three float64-widened float32
 * differences; a float32 accumulation would have rounded to {@code 0.90000004}.
 */
public class VectorL1Distance extends BuiltInFunction {

    public VectorL1Distance() {
        super("VECTOR_L1_DISTANCE", NumericType.DOUBLE);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final VectorValue left = VectorFunctionHelper.vectorArgument(getName(), args, 0);
        final VectorValue right = VectorFunctionHelper.vectorArgument(getName(), args, 1);
        if (left == null || right == null) {
            return null;
        }
        VectorFunctionHelper.requireSameType(getName(), args, left, right);
        double distance = 0.0;
        for (int i = 0; i < left.dimension(); i++) {
            distance += Math.abs(left.element(i) - right.element(i));
        }
        return Double.valueOf(distance);
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
