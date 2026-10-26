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

import dev.frostlake.executor.DeferredFault;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DecimalText;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A membership index over a subquery's first-column values, built once for an uncorrelated
 * {@code value IN (subquery)} so each outer row is an O(1) probe instead of an O(subqueryRows) linear
 * scan — turning the membership test from O(outer × sub) into O(outer + sub).
 *
 * <p>It answers only a probe of the kind every member is, where hashing is the comparison's own: two Numbers
 * compare numerically and scale-insensitively (like {@code BigDecimal.compareTo} — so {@code 5}, {@code 5L},
 * {@code 5.0} are equal), and two texts by their characters. Any other pair converts as {@code value = member}
 * converts — a number against text reads the text as a number — so {@link #answers} turns such a probe back to
 * the caller's scan. The three-valued NULL rules live in the CALLER: it never probes a NULL, and turns a miss
 * into UNKNOWN when {@link #hasNull()} says the set held a NULL member.
 */
public class PreparedInSet {

    private final Set<String> numericKeys = new HashSet<>();
    private final Set<String> stringKeys = new HashSet<>();
    private boolean containsNull;
    /** Whether every member that is not NULL is a Number. */
    private boolean onlyNumbers = true;
    /** Whether every member that is not NULL is a text. */
    private boolean onlyTexts = true;

    public static PreparedInSet build(final List<Row> rows) {
        final PreparedInSet set = new PreparedInSet();
        for (final Row row : rows) {
            final Object v = DeferredFault.read(row.getValue(0));
            if (v == null) {
                set.containsNull = true;
                continue;
            }
            set.stringKeys.add(v.toString());
            set.onlyNumbers = set.onlyNumbers && v instanceof Number;
            set.onlyTexts = set.onlyTexts && v instanceof String;
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

    /**
     * Whether the index settles a probe: a number against members that are all numbers, a text against members
     * that are all texts. Any other probe is compared member by member, as the comparison converts it.
     *
     * @param value the probe, not NULL
     * @return whether {@link #contains} answers it
     */
    public boolean answers(final Object value) {
        return value instanceof Number ? onlyNumbers : value instanceof String && onlyTexts;
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
            return DecimalText.parse(number.toString()).stripTrailingZeros().toPlainString();
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
