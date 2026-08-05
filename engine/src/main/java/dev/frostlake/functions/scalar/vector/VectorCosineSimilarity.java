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
 * {@code VECTOR_COSINE_SIMILARITY(v1, v2)} — the cosine of the angle between two vectors,
 * {@code Σ(a·b) / (|a|·|b|)}, as a FLOAT (float64).
 *
 * <p>Live-verified: over {@code [1,2,3]} and itself it is {@code 1.0}, against
 * {@code [4,5,6]} it is {@code 0.9746318461970762} ({@code SYSTEM$TYPEOF} → {@code FLOAT[DOUBLE]}), the
 * same value for the {@code VECTOR(INT,3)} pair, and against the ZERO vector it is {@code NaN} (the
 * division by a zero magnitude is not special-cased). The float32 ELEMENTS still feed float64
 * arithmetic: over {@code [0.1,0.2,0.3]} and {@code [0.4,0.5,0.6]} the account returns
 * {@code 0.9746318467275483}, not the exact-decimal {@code 0.9746318461970762}.
 */
public class VectorCosineSimilarity extends BuiltInFunction {

    public VectorCosineSimilarity() {
        super("VECTOR_COSINE_SIMILARITY", NumericType.DOUBLE);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final VectorValue left = VectorFunctionHelper.vectorArgument(getName(), args, 0);
        final VectorValue right = VectorFunctionHelper.vectorArgument(getName(), args, 1);
        if (left == null || right == null) {
            return null;
        }
        VectorFunctionHelper.requireSameType(getName(), args, left, right);
        double dot = 0.0;
        double leftSquares = 0.0;
        double rightSquares = 0.0;
        for (int i = 0; i < left.dimension(); i++) {
            dot += left.element(i) * right.element(i);
            leftSquares += left.element(i) * left.element(i);
            rightSquares += right.element(i) * right.element(i);
        }
        return Double.valueOf(dot / (Math.sqrt(leftSquares) * Math.sqrt(rightSquares)));
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
