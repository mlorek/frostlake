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

/**
 * A sorting, grouping or DISTINCT key that carries the collation it compares under. Wrapping the key
 * value — never the row's value — lets one comparator serve every key site: two texts equal under the
 * collation hash and compare alike, so they fall into one group and sort as one value, while the text
 * itself is kept verbatim for whoever reports it.
 *
 * <p>A group's REPORTED value is not this wrapper: values that tie under the collation are reported by
 * their binary minimum, which {@link #leastOf} settles.
 */
public final class CollatedKey implements Comparable<CollatedKey> {

    private final String text;
    private final CollationSpec rules;
    private final Object equality;

    private CollatedKey(final String text, final CollationSpec rules) {
        this.text = text;
        this.rules = rules;
        this.equality = rules.equalityKey(text);
    }

    /**
     * The key for a value under a collation: the value itself when there is no collation, or when it is
     * not text (a collation orders strings; everything else keeps its own order).
     *
     * @param value the key's value
     * @param rules the collation it compares under, or null for none
     * @return the value, or a collated key wrapping it
     */
    public static Object of(final Object value, final CollationSpec rules) {
        if (rules == null || !(value instanceof String)) {
            return value;
        }
        return new CollatedKey((String) value, rules);
    }

    /**
     * The text behind a key, so a wrapper never reaches a result.
     *
     * @param value a key value
     * @return its text when it is a collated key, otherwise the value unchanged
     */
    public static Object unwrap(final Object value) {
        return value instanceof CollatedKey ? ((CollatedKey) value).text : value;
    }

    /**
     * Which of two collation-equal values a group reports: the smaller by the raw text, since values
     * that tie under the collation are still told apart by their code points.
     *
     * @param first  one value, or null
     * @param second the other, or null
     * @return the one a group reports
     */
    public static Object leastOf(final Object first, final Object second) {
        if (first == null) {
            return second;
        }
        if (second == null || !(first instanceof String) || !(second instanceof String)) {
            return first;
        }
        return ((String) second).compareTo((String) first) < 0 ? second : first;
    }

    /**
     * The text this key carries.
     *
     * @return the text, verbatim
     */
    public String getText() {
        return text;
    }

    /**
     * The collation this key compares under.
     *
     * @return the rules
     */
    public CollationSpec rulesOf() {
        return rules;
    }

    @Override
    public int compareTo(final CollatedKey other) {
        return rules.compare(text, other.text);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CollatedKey)) {
            return false;
        }
        return equality.equals(((CollatedKey) other).equality);
    }

    @Override
    public int hashCode() {
        return equality.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
