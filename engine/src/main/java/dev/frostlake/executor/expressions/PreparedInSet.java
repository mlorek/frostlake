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

package dev.frostlake.executor.expressions;

import dev.frostlake.storage.Row;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A membership index over a subquery's first-column values, built once for an uncorrelated
 * {@code value IN (subquery)} so each outer row is an O(1) probe instead of an O(subqueryRows) linear
 * scan — turning the membership test from O(outer × sub) into O(outer + sub).
 *
 * <p>It reproduces exactly the equality used by {@code ExpressionEvaluatorVisitor.equals}: two Numbers
 * compare numerically and scale-insensitively (like {@code BigDecimal.compareTo} — so {@code 5},
 * {@code 5L}, {@code 5.0} are equal), while any comparison involving a non-Number compares the
 * operands' {@code toString()}. The three-valued NULL rules live in the CALLER: it never probes a
 * NULL, and turns a miss into UNKNOWN when {@link #hasNull()} says the set held a NULL member. To
 * honour both equality rules with one structure every
 * numeric value is indexed under a canonical numeric key <em>and</em> every value under its
 * {@code toString()}: a Number probe checks the numeric key (numeric-vs-numeric) or the string key
 * (numeric-vs-non-numeric), and a non-Number probe checks only the string key.
 */
public class PreparedInSet {

    private final Set<String> numericKeys = new HashSet<>();
    private final Set<String> stringKeys = new HashSet<>();
    private boolean containsNull;

    public static PreparedInSet build(final List<Row> rows) {
        final PreparedInSet set = new PreparedInSet();
        for (final Row row : rows) {
            final Object v = row.getValue(0);
            if (v == null) {
                set.containsNull = true;
                continue;
            }
            set.stringKeys.add(v.toString());
            if (v instanceof Number) {
                final String key = numericKey(v);
                if (key != null) {
                    set.numericKeys.add(key);
                }
            }
        }
        return set;
    }

    /** True when the indexed subquery column contained at least one NULL member. */
    public boolean hasNull() {
        return containsNull;
    }

    /** True iff {@code value} would equal some indexed value under the engine's IN equality. */
    public boolean contains(final Object value) {
        if (value == null) {
            return containsNull;
        }
        if (value instanceof Number) {
            final String key = numericKey(value);
            if (key != null && numericKeys.contains(key)) {
                return true;
            }
            return stringKeys.contains(value.toString());
        }
        return stringKeys.contains(value.toString());
    }

    /** Canonical key for numeric equality, or null for values BigDecimal can't parse (NaN/Infinity). */
    private static String numericKey(final Object number) {
        try {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
