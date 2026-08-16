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
import java.math.BigDecimal;

/** Accumulator for {@link VarPop}. */
public class VarPopAccumulator implements AggregateFunction.Accumulator {
    private long n = 0;
    private double sum = 0;
    private double sumSq = 0;

    @Override
    public void accumulate(final Object v) {
        if (v == null) return;
        final double d = new BigDecimal(v.toString()).doubleValue();
        n++;
        sum += d;
        sumSq += d * d;
    }

    @Override
    public Object getResult() {
        if (n == 0) return null;
        return sumSq / n - (sum / n) * (sum / n);
    }

    @Override
    public void reset() {
        n = 0;
        sum = 0;
        sumSq = 0;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final VarPopAccumulator o = (VarPopAccumulator) other;
        n += o.n;
        sum += o.sum;
        sumSq += o.sumSq;
    }
}
