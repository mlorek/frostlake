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
 * {@code VECTOR_TRUNC(v, n)} (alias {@code VECTOR_TRUNCATE}) — the vector's FIRST {@code n} dimensions,
 * keeping the element type.
 *
 * <p>Live-verified: {@code VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 2)} is {@code [1.0,2.0]}
 * with {@code SYSTEM$TYPEOF} {@code VECTOR(FLOAT, 2)}, the INT vector truncates to {@code [1,2]} /
 * {@code VECTOR(INT, 2)}, {@code n = 0} is the empty {@code []}, {@code n} equal to the dimension is the
 * unchanged vector, and a typed NULL vector yields NULL. {@code n} is a COMPILE-time constant that may
 * not exceed the source dimension — {@code n = 9} over a 3-vector is "Requested truncation dimension 9
 * for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).", and a
 * column / an expression / a non-integral literal are all "needs to be constant". Those checks live in
 * the expression layer, where the STATIC types are known.
 *
 * <p>A dimension of {@code -1} is the one negative the account compiles: the statement is accepted,
 * over an empty table it answers no rows, and each row it does read fails with the account's internal
 * error, the incident number that sentence carries on the account being left out here.
 */
public class VectorTrunc extends BuiltInFunction {

    /** The negative dimension that compiles and then fails on every row it is asked of. */
    public static final int ROW_FAILING_DIMENSION = -1;

    /** How each row fails under {@link #ROW_FAILING_DIMENSION}. */
    private static final String ROW_FAILURE =
        "SQL execution internal error:\nProcessing aborted due to error 300010:2086363262.";

    public VectorTrunc() {
        super("VECTOR_TRUNC", new VectorType(VectorElementType.FLOAT, 1));
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final VectorValue source = VectorFunctionHelper.vectorArgument(getName(), args, 0);
        if (source == null || args.get(1) == null) {
            return null;
        }
        final int requested = ((Number) args.get(1)).intValue();
        if (requested == ROW_FAILING_DIMENSION) {
            throw new RuntimeException(ROW_FAILURE);
        }
        if (requested < 0 || requested > source.dimension()) {
            throw new RuntimeException("Requested truncation dimension " + requested + " for " + getName()
                + " should be less than or equal to the dimension of the provided vector ("
                + source.dimension() + ").");
        }
        final double[] kept = new double[requested];
        for (int i = 0; i < requested; i++) {
            kept[i] = source.element(i);
        }
        return VectorValue.of(source.getElementType(), kept);
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
