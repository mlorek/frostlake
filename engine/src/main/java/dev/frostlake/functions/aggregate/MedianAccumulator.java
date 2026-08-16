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
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator for {@link Median}, delegating to {@link AggregateNumerics#median(Iterable)} so the
 * interpolating percentiles share one typing rule. The values are kept AS THEY ARRIVE rather than as
 * doubles: the median's scale is the input's plus three, and a value collapsed to a double has no
 * scale left to derive it from. A VARCHAR or VARIANT argument is told apart by DECLARATION
 * ({@link CoercedNumericArgumentAccumulator}) and takes live's whole-number rule instead.
 */
public class MedianAccumulator implements AggregateFunction.Accumulator, ApproximateAwareAccumulator,
    CoercedNumericArgumentAccumulator {
    private final List<Object> values = new ArrayList<>();
    private boolean approximateArgument;
    private boolean coercedArgument;

    @Override
    public void setApproximateArgument(final boolean approximate) {
        this.approximateArgument = approximate;
    }

    @Override
    public void setCoercedNumericArgument(final boolean coerced) {
        this.coercedArgument = coerced;
    }

    @Override
    public void accumulate(final Object v) {
        if (v != null) values.add(v);
    }

    @Override
    public Object getResult() {
        return coercedArgument
            ? AggregateNumerics.medianOverCoerced(values)
            : AggregateNumerics.median(values, approximateArgument);
    }

    @Override
    public void reset() { values.clear(); }

    @Override
    public void merge(final AggregateFunction.Accumulator other) { values.addAll(((MedianAccumulator) other).values); }
}
