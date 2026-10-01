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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.LikeMatcher;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.BooleanType;

import java.util.List;

/**
 * LIKE(subject, pattern [, escape]) — the function-call form of the {@code subject LIKE pattern [ESCAPE escape]}
 * operator (case-sensitive SQL wildcard match, {@code %} = any run, {@code _} = one char). Without an escape argument nothing is an escape. NULL subject, pattern or escape yields
 * NULL.
 */
public class Like extends BuiltInFunction {
    public Like() { super("LIKE", new BooleanType()); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) {
            return null;
        }
        if (args.size() < 3) {
            return LikeMatcher.evaluateLike(args.get(0), args.get(1), BinaryOperator.LIKE, null);
        }
        final Object escape = args.get(2);
        if (escape == null) {
            return null;
        }
        final String written = escape.toString();
        if (written.length() != 1) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value ['" + written + "'] for parameter 'escape'"));
        }
        return LikeMatcher.evaluateLike(args.get(0), args.get(1), BinaryOperator.LIKE,
            Character.valueOf(written.charAt(0)));
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }

    /** A BINARY is no text here: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /**
     * A predicate subject or pattern is refused by the argument types, as it is beside the operator
     * spelling: LIKE('x', 1 = 1) is 'LIKE': (VARCHAR(1), BOOLEAN) (live-verified).
     */
    @Override
    public SemiStructuredRejection predicateRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
