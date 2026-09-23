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
 * SPACE(n): {@code n} blanks. The account plans it as {@code LPAD('', n, ' ')}, which is what judges a
 * BINARY count — see {@link SemiStructuredRejection#SPACE_REWRITE_OPERANDS}.
 */
public class Space extends TextArgumentFunction {
    public Space() { super("SPACE", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final int n = ((Number) args.get(0)).intValue();
        return n <= 0 ? "" : " ".repeat(n);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

    /** A BINARY count is judged as the LPAD the call is planned as (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.SPACE_REWRITE_OPERANDS;
    }

    /** So is a predicate count: SPACE(1 = 1) is 'LPAD': (VARCHAR(1), BOOLEAN, VARCHAR(1)) (live-verified). */
    @Override
    public SemiStructuredRejection predicateRejection(final int position) {
        return SemiStructuredRejection.SPACE_REWRITE_OPERANDS;
    }

    /** A BOOLEAN count is judged as the LPAD the call is planned as, like a predicate one (live-verified). */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return SemiStructuredRejection.SPACE_REWRITE_OPERANDS;
    }
}
