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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.ObjectType;

import java.util.List;

/**
 * {@code AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => n)} — the minimum group size an AGGREGATION POLICY
 * imposes. Unlike PROJECTION_CONSTRAINT, live refuses to evaluate this one outside a policy body, so
 * a bare call answers that refusal here too.
 */
public class AggregationConstraint extends BuiltInFunction {

    public AggregationConstraint() {
        super("AGGREGATION_CONSTRAINT", new ObjectType());
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (!PolicyBodyScope.isInside()) {
            throw new RuntimeException(SqlCompilationError.of("The AGGREGATION_CONSTRAINT function can"
                + " only be called from a body of an aggregation constraint policy."));
        }
        return "{\"min_group_size\":" + String.valueOf(args.get(0)) + "}";
    }
}
