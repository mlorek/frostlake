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
import dev.frostlake.types.ArrayType;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * APPROX_TOP_K_ESTIMATE(state [, k]): the k most frequent [value, count] pairs of an
 * APPROX_TOP_K_ACCUMULATE or APPROX_TOP_K_COMBINE state, as APPROX_TOP_K would list them (k defaults
 * to 1 and is a constant between 1 and 100000, judged at compile time). A NULL state is NULL; an
 * argument that is not a state refuses with {@code Invalid parameter value: ApproxTopK state.
 * Reason: invalid type}. The scalar half of the accumulate family, so it lives beside it.
 */
public class ApproxTopKEstimate extends BuiltInFunction {

    public ApproxTopKEstimate() {
        super("APPROX_TOP_K_ESTIMATE", ArrayType.ARRAY);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object state = args.isEmpty() ? null : args.get(0);
        if (state == null) {
            return null;
        }
        final JsonNode node = TopKSummary.stateNode(state);
        final TopKSummary summary = new TopKSummary(Integer.MAX_VALUE);
        summary.absorb(node);
        return TopKSummary.pairsOf(summary.top(items(args)));
    }

    private static int items(final List<Object> args) {
        if (args.size() < 2 || args.get(1) == null) {
            return 1;
        }
        final Object written = args.get(1);
        if (written instanceof Number) {
            return ((Number) written).intValue();
        }
        final BigDecimal parsed = ConstantArgumentTexts.integralConstant(String.valueOf(written));
        return parsed == null ? 1 : parsed.intValue();
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }
}
