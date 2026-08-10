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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.ObjectType;

import java.util.List;

/**
 * {@code PROJECTION_CONSTRAINT(ALLOW => <boolean>)} — what a PROJECTION POLICY's body returns. It is
 * an ordinary callable function, not policy-only syntax: live answers
 * {@code {"allow":true,"enforcement":"FAIL"}} for a bare {@code SELECT PROJECTION_CONSTRAINT(ALLOW =&gt;
 * TRUE)} (unlike AGGREGATION_CONSTRAINT, which refuses to be called outside a policy body).
 *
 * <p>The argument MUST be named — the positional call is refused, in a query and inside a policy body
 * alike — and that refusal is raised by the evaluator, which is where argument names are still known.
 */
public class ProjectionConstraint extends BuiltInFunction {

    public ProjectionConstraint() {
        super("PROJECTION_CONSTRAINT", new ObjectType());
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
        final Object allow = args.get(0);
        // The enforcement cell is always FAIL on the accounts this was measured against: the constraint
        // fails the query rather than dropping the column.
        return "{\"allow\":" + (Boolean.TRUE.equals(allow) || "true".equalsIgnoreCase(String.valueOf(allow)))
            + ",\"enforcement\":\"FAIL\"}";
    }
}
