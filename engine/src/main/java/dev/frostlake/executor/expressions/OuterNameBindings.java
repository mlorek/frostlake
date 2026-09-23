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

import dev.frostlake.executor.AmbiguousColumnException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * How a subquery's names for the outer row are bound. A column is bound under its canonical spelling, and the
 * binding map records which spellings those are, so a lookup that folds a reference's case onto a bound key —
 * which the other names a lateral scope binds, earlier items' aliases among them, still allow — does not reach
 * an outer column by a spelling it does not carry: over a column {@code V}, {@code "v"} is live's
 * {@code invalid identifier '"v"'}. A bare name two relations of the outer row share, joined otherwise than by
 * USING or NATURAL, is bound as ambiguous, and reading it is live's {@code ambiguous column name 'V'}.
 */
final class OuterNameBindings {

    /** The value a bare name is bound to when two relations of the outer row carry it. */
    static final Object AMBIGUOUS = new Object() {
        @Override
        public String toString() {
            return "ambiguous outer name";
        }
    };

    /** The key the outer columns' canonical spellings are recorded under; no identifier spells it. */
    private static final String SPELLINGS = "\u0000outer column spellings";

    private OuterNameBindings() {
    }

    /**
     * Record the canonical spellings of outer columns bound in {@code bindings}, beside those a row further out
     * recorded there.
     *
     * @param bindings the binding map
     * @param columns  the columns' canonical names
     */
    static void recordColumns(final Map<String, Object> bindings, final Set<String> columns) {
        final Set<String> recorded = new HashSet<>(columns);
        final Object earlier = bindings.get(SPELLINGS);
        if (earlier instanceof Set) {
            for (final Object name : (Set<?>) earlier) {
                recorded.add(String.valueOf(name));
            }
        }
        bindings.put(SPELLINGS, recorded);
    }

    /**
     * Whether folding a reference's case would reach an outer column by a spelling the column does not carry: the
     * reference's column part has lower-case letters, and the map records its upper-cased form, and not the
     * reference's own, as an outer column's spelling.
     *
     * @param bindings the binding map the reference is looked up in
     * @param key      the reference's key as written: its column, or its qualifier, a dot and its column
     * @return true where the folded lookup must not answer
     */
    static boolean foldMisses(final Map<String, Object> bindings, final String key) {
        final Object recorded = bindings.get(SPELLINGS);
        if (!(recorded instanceof Set)) {
            return false;
        }
        final String column = key.substring(key.lastIndexOf('.') + 1);
        final String folded = column.toUpperCase();
        return !folded.equals(column) && ((Set<?>) recorded).contains(folded) && !((Set<?>) recorded).contains(column);
    }

    /**
     * A bound value as a read of it answers: the value itself, or live's ambiguity refusal for a bare name two
     * relations of the outer row share, which quotes the column's own spelling — {@code 'V'} for a column V,
     * {@code 'v'} for a column "v".
     *
     * @param bound the value the map binds
     * @param key   the key the value is bound under: the column's canonical spelling
     * @return the value
     */
    static Object read(final Object bound, final String key) {
        if (bound == AMBIGUOUS) {
            throw new AmbiguousColumnException(key);
        }
        return bound;
    }
}
