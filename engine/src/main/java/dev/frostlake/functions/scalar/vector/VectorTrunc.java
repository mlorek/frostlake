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
 * <p>A negative dimension compiles, read in 32 bits, and each row then sizes its result as four bytes an
 * element in 32 bits too: a negative size fails with the account's internal error (the incident number that
 * sentence carries on the account being left out here), a size past 16777216 bytes is too long to return,
 * and any other reads the empty vector — so -1 and -1073741825 fail, -1073741824 and -2147483647 read
 * {@code []}, and -1879048192 is "Cannot return value of length 1073741824 as it exceeds the maximum length
 * of 16777216". Over an empty table a dimension of {@code -1} answers no rows.
 */
public class VectorTrunc extends BuiltInFunction {

    /** The bytes an element takes when the account sizes a truncated vector. */
    private static final int BYTES_PER_ELEMENT = 4;

    /** The longest value a row may return. */
    private static final int MAX_VALUE_LENGTH = 16_777_216;

    /** How each row fails under a negative dimension whose size is negative. */
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
        if (requested < 0) {
            final int length = requested * BYTES_PER_ELEMENT;
            if (length < 0) {
                throw new RuntimeException(ROW_FAILURE);
            }
            if (length > MAX_VALUE_LENGTH) {
                throw new RuntimeException("Cannot return value of length " + length
                    + " as it exceeds the maximum length of " + MAX_VALUE_LENGTH);
            }
            return VectorValue.of(source.getElementType(), new double[0]);
        }
        if (requested > source.dimension()) {
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
