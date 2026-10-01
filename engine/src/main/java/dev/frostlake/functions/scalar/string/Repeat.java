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

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * REPEAT(s, n): the string {@code n} times over, NULL when either argument is NULL. The account plans
 * it as {@code LPAD('', n * LENGTH(s), s)}, which is what judges a BINARY argument — see
 * {@link SemiStructuredRejection#REPEAT_REWRITE_OPERANDS}.
 */
public class Repeat extends TextArgumentFunction {
    public Repeat() { super("REPEAT", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        // A NULL count answers NULL as a NULL string does (live: REPEAT('ab', NULL) is NULL).
        if (args.get(0) == null || args.get(1) == null) return null;
        final String s = args.get(0).toString();
        final int n = ((Number) args.get(1)).intValue();
        if (n <= 0) return "";
        return s.repeat(n);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /** A BINARY in either position is judged as the LPAD the call is planned as (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.REPEAT_REWRITE_OPERANDS;
    }

    /**
     * A predicate count is judged as the product the call is planned with, '*': (BOOLEAN, NUMBER(18,0))
     * (live-verified); a predicate string is refused as any text function refuses one.
     */
    @Override
    public SemiStructuredRejection predicateRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.REPEAT_REWRITE_OPERANDS : SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /** A BOOLEAN count is judged as the product the call is planned with, like a predicate one (live-verified). */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.REPEAT_REWRITE_OPERANDS : SemiStructuredRejection.NONE;
    }
}
