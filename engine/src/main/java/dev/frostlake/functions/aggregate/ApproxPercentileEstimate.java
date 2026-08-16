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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;

/**
 * APPROX_PERCENTILE_ESTIMATE(state, percentile): the value at a fraction of an
 * APPROX_PERCENTILE_ACCUMULATE or APPROX_PERCENTILE_COMBINE state, as a FLOAT — the same number
 * APPROX_PERCENTILE gives over the values the state was built from (see {@link PercentileDigest} for
 * the weighted reading of a hand-written state). The fraction is a constant between 0 and 1, judged
 * at compile time; a NULL state, a NULL fraction or an empty state is NULL, and an argument that is
 * not a state refuses with {@code First argument of the function must be an object which maps the key
 * 'state' to an array}. The scalar half of the accumulate family, so it lives beside it.
 */
public class ApproxPercentileEstimate extends BuiltInFunction {

    public ApproxPercentileEstimate() {
        super("APPROX_PERCENTILE_ESTIMATE", NumericType.DOUBLE);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object state = args.isEmpty() ? null : args.get(0);
        final Object fraction = args.size() > 1 ? args.get(1) : null;
        if (state == null || fraction == null) {
            return null;
        }
        final PercentileDigest digest = PercentileDigest.parse(state);
        final double percentile = fraction instanceof Number
            ? ((Number) fraction).doubleValue() : new BigDecimal(String.valueOf(fraction).trim()).doubleValue();
        return digest.estimate(percentile);
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
