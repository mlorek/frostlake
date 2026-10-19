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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A correlated aggregate subquery's value for each combination of keys of its correlating equalities, computed once
 * over the inner relation grouped by its keys (see {@link dev.frostlake.executor.EqualityCorrelation}), so each outer
 * row looks its value up instead of running the subquery again. Like {@link PreparedKeySet}, it answers only a probe
 * whose values are each of the one kind its column's keys hold (see {@link CorrelationKeys}). A probe no key equals
 * reads the aggregate over no rows, kept once the first such probe computed it.
 */
public final class PreparedKeyedValues {

    private final Map<List<String>, Object> values = new HashMap<>();
    private final String[] kinds;
    private boolean emptyKnown;
    private Object emptyValue;

    private PreparedKeyedValues(final int width) {
        kinds = new String[width];
    }

    /**
     * @param rows  one row for each combination of keys, the keys first and the value after them; a NULL key,
     *              which no probe equals, is left out
     * @param width how many keys a row holds
     * @return the values by key
     */
    public static PreparedKeyedValues build(final List<Row> rows, final int width) {
        final PreparedKeyedValues keyed = new PreparedKeyedValues(width);
        for (final Row row : rows) {
            final List<Object> key = PreparedKeySet.keyOf(row, width);
            if (key != null && KeyColumnKinds.note(keyed.kinds, key)) {
                keyed.values.put(CorrelationKeys.canonical(key), row.getValue(width));
            }
        }
        return keyed;
    }

    /**
     * Whether the values settle a probe: each of its values of the kind its column's keys hold.
     *
     * @param probe the outer row's keys, none NULL
     * @return whether {@link #holds} and {@link #valueOf} answer it
     */
    public boolean answers(final List<Object> probe) {
        return KeyColumnKinds.answers(kinds, probe);
    }

    /**
     * @param probe an answered probe
     * @return whether some inner keys equal it
     */
    public boolean holds(final List<Object> probe) {
        return values.containsKey(CorrelationKeys.canonical(probe));
    }

    /**
     * @param probe an answered probe some inner keys equal
     * @return the aggregate's value over the rows of those keys, as its cell holds it
     */
    public Object valueOf(final List<Object> probe) {
        return values.get(CorrelationKeys.canonical(probe));
    }

    /** @return whether the aggregate's value over no rows is known yet */
    public boolean knowsEmptyValue() {
        return emptyKnown;
    }

    /** @return the aggregate's value over no rows, once known */
    public Object emptyValue() {
        return emptyValue;
    }

    /**
     * Keeps the aggregate's value over no rows, as the subquery computed it for an outer row no inner key equals.
     *
     * @param value the value
     */
    public void recordEmptyValue(final Object value) {
        emptyValue = value;
        emptyKnown = true;
    }
}
