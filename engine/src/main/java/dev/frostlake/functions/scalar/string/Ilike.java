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

import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.LikeMatcher;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.BooleanType;

import java.util.List;

/**
 * ILIKE(subject, pattern) — the function-call form of the {@code subject ILIKE pattern} operator
 * (case-INsensitive SQL wildcard match). NULL subject or pattern yields NULL.
 */
public class Ilike extends BuiltInFunction {
    public Ilike() { super("ILIKE", new BooleanType()); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) {
            return null;
        }
        return LikeMatcher.evaluateLike(args.get(0), args.get(1), BinaryOperator.ILIKE, '\\');
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
