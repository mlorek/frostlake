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
 * {@code VECTOR_INNER_PRODUCT(v1, v2)} — the dot product {@code Σ(a·b)}, as a FLOAT (float64).
 *
 * <p>Live-verified: {@code VECTOR_INNER_PRODUCT([1,2,3], [4,5,6])} is {@code 32.0}, and the
 * {@code VECTOR(INT,3)} pair gives the same {@code 32.0} with {@code SYSTEM$TYPEOF} still
 * {@code FLOAT[DOUBLE]} — INT operands do NOT make the result an integer. The product is accumulated in
 * float64 over the 32-bit elements: {@code [0.1,0.2,0.3]·[0.4,0.5,0.6]} is {@code 0.32000001698732405},
 * and {@code [2147483647,0,0]·[2,0,0]} is {@code 4.294967294E9} rather than overflowing.
 */
public class VectorInnerProduct extends BuiltInFunction {

    public VectorInnerProduct() {
        super("VECTOR_INNER_PRODUCT", NumericType.DOUBLE);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final VectorValue left = VectorFunctionHelper.vectorArgument(getName(), args, 0);
        final VectorValue right = VectorFunctionHelper.vectorArgument(getName(), args, 1);
        if (left == null || right == null) {
            return null;
        }
        VectorFunctionHelper.requireSameType(getName(), args, left, right);
        double product = 0.0;
        for (int i = 0; i < left.dimension(); i++) {
            product += left.element(i) * right.element(i);
        }
        return Double.valueOf(product);
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
