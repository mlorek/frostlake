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
import dev.frostlake.types.VectorElementType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.VectorValue;

import java.util.List;

/**
 * {@code VECTOR_NORMALIZE(v)} — the unit vector {@code v / |v|}, always a {@code VECTOR(FLOAT, n)}.
 *
 * <p>Live-verified:
 * <ul>
 *   <li>{@code VECTOR_NORMALIZE([1,2,3]::VECTOR(FLOAT,3))} is
 *       {@code [0.26726124,0.5345225,0.80178374]} — the quotients computed in float64 and then NARROWED
 *       to float32 elements, not the float64 {@code 0.2672612419124244};</li>
 *   <li>the arithmetic really is float64: {@code [1e-30,2e-30,2e-30]} normalizes to
 *       {@code [0.33333334,0.6666667,0.6666667]}, which float32 could not compute (the squares
 *       underflow there);</li>
 *   <li>an INT vector normalizes to a FLOAT one — {@code SYSTEM$TYPEOF} of
 *       {@code VECTOR_NORMALIZE([1,2,3]::VECTOR(INT,3))} is {@code VECTOR(FLOAT, 3)};</li>
 *   <li>the ZERO vector is NOT a division by zero: {@code VECTOR_NORMALIZE([0,0,0]::VECTOR(FLOAT,3))}
 *       is {@code [0.0,0.0,0.0]}, not {@code NaN}.</li>
 * </ul>
 */
public class VectorNormalize extends BuiltInFunction {

    public VectorNormalize() {
        super("VECTOR_NORMALIZE", new VectorType(VectorElementType.FLOAT, 1));
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final VectorValue source = VectorFunctionHelper.vectorArgument(getName(), args, 0);
        if (source == null) {
            return null;
        }
        double squares = 0.0;
        for (int i = 0; i < source.dimension(); i++) {
            squares += source.element(i) * source.element(i);
        }
        final double magnitude = Math.sqrt(squares);
        final double[] unit = new double[source.dimension()];
        for (int i = 0; i < source.dimension(); i++) {
            unit[i] = magnitude == 0.0 ? source.element(i) : source.element(i) / magnitude;
        }
        return VectorValue.of(VectorElementType.FLOAT, unit);
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
