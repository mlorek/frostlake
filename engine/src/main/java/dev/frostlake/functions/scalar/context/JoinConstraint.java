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
import dev.frostlake.types.BooleanType;

import java.util.List;

/**
 * {@code JOIN_CONSTRAINT(JOIN_REQUIRED => <boolean>)} — what a JOIN POLICY's body returns. Unlike its
 * two siblings it answers a plain BOOLEAN, and like PROJECTION_CONSTRAINT it is callable outside a
 * policy body (live answers {@code true} to a bare call).
 */
public class JoinConstraint extends BuiltInFunction {

    public JoinConstraint() {
        super("JOIN_CONSTRAINT", BooleanType.BOOLEAN);
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
        final Object required = args.get(0);
        if (required == null) {
            // Live refuses a NULL here rather than folding the call away (measured, both spellings).
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " Invalid argument for function JOIN_CONSTRAINT.");
        }
        return Boolean.TRUE.equals(required) || "true".equalsIgnoreCase(String.valueOf(required));
    }
}
