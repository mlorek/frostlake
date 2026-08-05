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
import dev.frostlake.values.VectorValue;

/**
 * The element-wise vector aggregates' accumulator, shared by {@link VectorSum}, {@link VectorAvg},
 * {@link VectorMin} and {@link VectorMax}.
 *
 * <p>Live-verified over the deliberately CROSSING rows {@code [1,9,3]}, {@code [4,5,-6]} and
 * {@code [7,2,0]} — where a row-wise winner would give a different answer than an element-wise one:
 * {@code VECTOR_MIN} is {@code [1.0,2.0,-6.0]} and {@code VECTOR_MAX} is {@code [7.0,9.0,3.0]}, so each
 * DIMENSION is reduced independently. {@code VECTOR_SUM} is {@code [12.0,16.0,-3.0]} and
 * {@code VECTOR_AVG} {@code [4.0,5.3333335,-1.0]} — the float64 mean {@code 16/3} narrowed to a float32
 * element.
 *
 * <p>NULL rows are SKIPPED and do not count towards the average ({@code VECTOR_AVG} over
 * {@code [1,2,3]}, {@code [4,5,6]} and a NULL row is {@code [2.5,3.5,4.5]}, i.e. divided by 2), and a
 * group with no non-NULL row is SQL NULL rather than a zero vector.
 *
 * <p>The result's element type follows the INPUT for SUM / MIN / MAX ({@code VECTOR_SUM} over a
 * {@code VECTOR(INT,3)} column is {@code [5,14,-3]}, {@code SYSTEM$TYPEOF} {@code VECTOR(INT, 3)}) and
 * is always FLOAT for AVG ({@code VECTOR_AVG} over the same column is {@code [2.5,7.0,-1.5]},
 * {@code VECTOR(FLOAT, 3)}).
 */
public class VectorAggregateAccumulator implements AggregateFunction.Accumulator {

    private final VectorAggregateKind kind;
    private final String functionName;
    private VectorElementType elementType;
    private double[] accumulated;
    private long rows;

    public VectorAggregateAccumulator(final VectorAggregateKind kind, final String functionName) {
        this.kind = kind;
        this.functionName = functionName;
    }

    @Override
    public void accumulate(final Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof VectorValue)) {
            throw new RuntimeException("Invalid argument types for function '" + functionName + "': (ARRAY)");
        }
        final VectorValue vector = (VectorValue) value;
        if (accumulated == null) {
            elementType = vector.getElementType();
            accumulated = vector.elements();
            rows = 1;
            return;
        }
        if (vector.dimension() != accumulated.length || vector.getElementType() != elementType) {
            throw new RuntimeException("Invalid argument types for function '" + functionName + "': ("
                + vector.type().getName() + ")");
        }
        for (int i = 0; i < accumulated.length; i++) {
            accumulated[i] = combine(accumulated[i], vector.element(i));
        }
        rows++;
    }

    private double combine(final double running, final double next) {
        switch (kind) {
            case MIN:
                return Math.min(running, next);
            case MAX:
                return Math.max(running, next);
            default:
                return running + next;   // SUM and AVG both accumulate the total, in float64
        }
    }

    @Override
    public Object getResult() {
        if (accumulated == null) {
            return null;
        }
        if (kind != VectorAggregateKind.AVG) {
            return VectorValue.of(elementType, accumulated);
        }
        final double[] mean = new double[accumulated.length];
        for (int i = 0; i < accumulated.length; i++) {
            mean[i] = accumulated[i] / rows;
        }
        return VectorValue.of(VectorElementType.FLOAT, mean);
    }

    @Override
    public void reset() {
        elementType = null;
        accumulated = null;
        rows = 0;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final VectorAggregateAccumulator o = (VectorAggregateAccumulator) other;
        if (o.accumulated == null) {
            return;
        }
        if (accumulated == null) {
            elementType = o.elementType;
            accumulated = o.accumulated.clone();
            rows = o.rows;
            return;
        }
        for (int i = 0; i < accumulated.length && i < o.accumulated.length; i++) {
            accumulated[i] = combine(accumulated[i], o.accumulated[i]);
        }
        rows += o.rows;
    }
}
