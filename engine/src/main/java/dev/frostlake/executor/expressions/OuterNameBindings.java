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
import dev.frostlake.metastore.model.TableColumn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How a subquery's names for the outer row are bound. A column is bound under its canonical spelling, and the
 * binding map records which spellings those are, so a lookup that folds a reference's case onto a bound key —
 * which the other names a lateral scope binds, earlier items' aliases among them, still allow — does not reach
 * an outer column by a spelling it does not carry: over a column {@code V}, {@code "v"} is live's
 * {@code invalid identifier '"v"'}. A bare name two relations of the outer row share, joined otherwise than by
 * USING or NATURAL, is bound as ambiguous, and reading it is live's {@code ambiguous column name 'V'}. The map
 * also records the outer columns that carry a collation under the keys their values are bound by, so a comparison
 * inside the subquery reads an outer column under the column's collation, as a comparison beside it does.
 */
public final class OuterNameBindings {

    /** The value a bare name is bound to when two relations of the outer row carry it. */
    static final Object AMBIGUOUS = new Object() {
        @Override
        public String toString() {
            return "ambiguous outer name";
        }
    };

    /**
     * Bind a bare name two relations of the outer row carry as ambiguous: reading it is live's {@code ambiguous
     * column name}.
     *
     * @param bindings the bindings
     * @param key      the name, as the column spells it
     */
    public static void bindAmbiguous(final Map<String, Object> bindings, final String key) {
        bindings.put(key, AMBIGUOUS);
    }

    /** The key the outer columns' canonical spellings are recorded under; no identifier spells it. */
    private static final String SPELLINGS = "\u0000outer column spellings";

    /** The key the collated outer columns are recorded under, by the keys their values are bound by. */
    private static final String COLLATED = "\u0000outer collated columns";

    private OuterNameBindings() {
    }

    /**
     * Whether {@code bindings} records a collated column of a row further out, which the keys a nearer row binds
     * must shadow.
     *
     * @param bindings the binding map
     * @return whether a collated column is recorded
     */
    static boolean recordsCollations(final Map<String, Object> bindings) {
        return bindings.get(COLLATED) instanceof Map;
    }

    /**
     * Record the collated columns a row binds, beside those a row further out recorded under keys this row does not
     * bind.
     *
     * @param bindings the binding map
     * @param collated this row's collated columns, by the keys their values are bound by
     * @param bound    every key this row binds, or null where no row further out recorded a collated column
     */
    public static void recordCollations(final Map<String, Object> bindings, final Map<String, TableColumn> collated,
                                 final Set<String> bound) {
        final Object earlier = bindings.get(COLLATED);
        if (!(earlier instanceof Map) && collated.isEmpty()) {
            return;
        }
        final Map<String, TableColumn> recorded = new HashMap<>();
        if (earlier instanceof Map) {
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) earlier).entrySet()) {
                final String key = String.valueOf(entry.getKey());
                if ((bound == null || !bound.contains(key)) && entry.getValue() instanceof TableColumn) {
                    recorded.put(key, (TableColumn) entry.getValue());
                }
            }
        }
        recorded.putAll(collated);
        bindings.put(COLLATED, recorded);
    }

    /**
     * The collated outer column a key reads, or null: the key exactly, else its upper-cased form where folding
     * reaches the column by a spelling it carries.
     *
     * @param bindings the binding map, or null
     * @param key      the reference's key as written: its column, or its qualifier, a dot and its column
     * @return the column, or null
     */
    static TableColumn collatedColumn(final Map<String, Object> bindings, final String key) {
        if (bindings == null || !(bindings.get(COLLATED) instanceof Map)) {
            return null;
        }
        final Map<?, ?> recorded = (Map<?, ?>) bindings.get(COLLATED);
        final Object exact = recorded.get(key);
        if (exact instanceof TableColumn) {
            return (TableColumn) exact;
        }
        final Object folded = recorded.get(key.toUpperCase(Locale.ROOT));
        return folded instanceof TableColumn && !foldMisses(bindings, key) ? (TableColumn) folded : null;
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
        final String folded = column.toUpperCase(Locale.ROOT);
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
