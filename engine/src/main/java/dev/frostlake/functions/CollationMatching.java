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

package dev.frostlake.functions;

import dev.frostlake.executor.expressions.CollationSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Text matching under a collation, for the functions that compare rather than merely carry one:
 * CONTAINS, STARTSWITH, ENDSWITH, POSITION, CHARINDEX, REPLACE, SPLIT and SPLIT_PART all look for a
 * substring the collation calls equal, not one that matches byte for byte.
 *
 * <p>A collation can call texts of DIFFERENT lengths equal (an accent-insensitive one folds a combining
 * mark away), so a match is looked for by comparing candidate substrings rather than by indexing — the
 * shortest substring at the earliest position wins, which is what an ordinary {@code indexOf} means.
 */
public final class CollationMatching {

    private CollationMatching() {
    }

    /**
     * Whether two values are equal under a collation, or by ordinary equality when there is none or
     * either side is not text.
     *
     * @param rules the collation, or null
     * @param left  one value
     * @param right the other
     * @return whether the collation calls them equal
     */
    public static boolean equalUnder(final CollationSpec rules, final Object left, final Object right) {
        if (rules == null || !(left instanceof String) || !(right instanceof String)) {
            return left != null && left.equals(right);
        }
        return rules.compare((String) left, (String) right) == 0;
    }

    /**
     * The first substring of {@code haystack} at or after {@code from} that the collation calls equal
     * to {@code needle}.
     *
     * @param rules    the collation
     * @param haystack the text searched
     * @param needle   the text looked for
     * @param from     the first index that may start a match
     * @return {@code {start, end}} of the match, or null when there is none
     */
    public static int[] findUnder(final CollationSpec rules, final String haystack, final String needle,
                                  final int from) {
        if (needle.isEmpty()) {
            return from <= haystack.length() ? new int[] {from, from} : null;
        }
        for (int start = Math.max(0, from); start <= haystack.length(); start++) {
            for (int end = start; end <= haystack.length(); end++) {
                if (rules.compare(haystack.substring(start, end), needle) == 0) {
                    return new int[] {start, end};
                }
            }
        }
        return null;
    }

    /**
     * Whether {@code haystack} holds a substring the collation calls equal to {@code needle}.
     *
     * @param rules    the collation
     * @param haystack the text searched
     * @param needle   the text looked for
     * @return whether it occurs
     */
    public static boolean containsUnder(final CollationSpec rules, final String haystack, final String needle) {
        return findUnder(rules, haystack, needle, 0) != null;
    }

    /**
     * Whether some prefix of {@code haystack} is equal to {@code prefix} under the collation.
     *
     * @param rules    the collation
     * @param haystack the text searched
     * @param prefix   the prefix looked for
     * @return whether it starts with it
     */
    public static boolean startsWithUnder(final CollationSpec rules, final String haystack, final String prefix) {
        for (int end = 0; end <= haystack.length(); end++) {
            if (rules.compare(haystack.substring(0, end), prefix) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether some suffix of {@code haystack} is equal to {@code suffix} under the collation.
     *
     * @param rules    the collation
     * @param haystack the text searched
     * @param suffix   the suffix looked for
     * @return whether it ends with it
     */
    public static boolean endsWithUnder(final CollationSpec rules, final String haystack, final String suffix) {
        for (int start = haystack.length(); start >= 0; start--) {
            if (rules.compare(haystack.substring(start), suffix) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every occurrence of {@code needle} in {@code haystack} replaced, matching under the collation.
     *
     * @param rules       the collation
     * @param haystack    the text
     * @param needle      the text replaced
     * @param replacement what replaces it
     * @return the result
     */
    public static String replaceUnder(final CollationSpec rules, final String haystack, final String needle,
                                      final String replacement) {
        if (needle.isEmpty()) {
            return haystack;
        }
        final StringBuilder out = new StringBuilder(haystack.length());
        int at = 0;
        while (at <= haystack.length()) {
            final int[] found = findUnder(rules, haystack, needle, at);
            if (found == null || found[1] == found[0]) {
                break;
            }
            out.append(haystack, at, found[0]).append(replacement);
            at = found[1];
        }
        return out.append(haystack.substring(Math.min(at, haystack.length()))).toString();
    }

    /**
     * {@code haystack} split on every occurrence of {@code separator}, matching under the collation.
     *
     * @param rules     the collation
     * @param haystack  the text split
     * @param separator the separator
     * @return the parts, in order
     */
    public static List<String> splitUnder(final CollationSpec rules, final String haystack,
                                          final String separator) {
        final List<String> parts = new ArrayList<>();
        if (separator.isEmpty()) {
            parts.add(haystack);
            return parts;
        }
        int at = 0;
        while (at <= haystack.length()) {
            final int[] found = findUnder(rules, haystack, separator, at);
            if (found == null || found[1] == found[0]) {
                break;
            }
            parts.add(haystack.substring(at, found[0]));
            at = found[1];
        }
        parts.add(haystack.substring(Math.min(at, haystack.length())));
        return parts;
    }
}
