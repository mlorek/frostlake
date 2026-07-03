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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.VariantType;

import java.math.BigDecimal;
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
        Object subject = args.get(0);
        int pairs = (args.size() - 1) / 2;
        for (int i = 0; i < pairs; i++) {
            Object search = args.get(1 + i * 2);
            Object result = args.get(2 + i * 2);
            if (nullSafeEqual(subject, search)) return result;
        }
        // default: last arg when count is even (subject + odd number of remaining = default present)
        if (args.size() % 2 == 0) return args.get(args.size() - 1);
        return null;
    }

    private boolean nullSafeEqual(final Object a, final Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        }
        return a.toString().equals(b.toString());
    }

    @Override public int getMinArgCount() { return 3; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
