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

package dev.frostlake.functions.scalar.conditional;

import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.executor.expressions.ExpressionArithmetic;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.CollationMatching;
import dev.frostlake.types.VariantType;

import java.util.List;

/**
 * DECODE(expr, search1, result1 [, search2, result2 ...] [, default])
 * Like a searched CASE: compares expr to each search value and returns the matching result.
 * NULL-safe: NULL == NULL is true.
 */
public class Decode extends BuiltInFunction {
    public Decode() { super("DECODE", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.isEmpty()) return null;
        final Object subject = args.get(0);
        final int pairs = (args.size() - 1) / 2;
        for (int i = 0; i < pairs; i++) {
            final Object search = args.get(1 + i * 2);
            final Object result = args.get(2 + i * 2);
            if (nullSafeEqual(subject, search)) return result;
        }
        // default: last arg when count is even (subject + odd number of remaining = default present)
        if (args.size() % 2 == 0) return args.get(args.size() - 1);
        return null;
    }

    /** Under a collation a search value matches what the collation calls equal to the subject. */
    @Override
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        if (collation == null || args.isEmpty()) {
            return evaluate(args);
        }
        final Object subject = args.get(0);
        final int pairs = (args.size() - 1) / 2;
        for (int i = 0; i < pairs; i++) {
            final Object search = args.get(1 + i * 2);
            if (subject == null && search == null) {
                return args.get(2 + i * 2);
            }
            if (CollationMatching.equalUnder(collation, subject, search)) {
                return args.get(2 + i * 2);
            }
        }
        return args.size() % 2 == 0 ? args.get(args.size() - 1) : null;
    }

    /**
     * DECODE's own NULL rule sits on top of the ordinary {@code =} comparison: it is the one place
     * where NULL matches NULL, and where a NULL beside a value is simply no match rather than
     * UNKNOWN. Everything below that is plain SQL value equality, shared with the operator — a
     * hand-rolled numeric-or-text test used to stand here, and it missed every mixed-family pair the
     * operator handles, a DATE beside a TIMESTAMP among them.
     */
    private boolean nullSafeEqual(final Object a, final Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return ExpressionArithmetic.equals(a, b);
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
