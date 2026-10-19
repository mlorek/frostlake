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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The inner keys of a correlated EXISTS, computed once for the statement, so each outer row's keys are a probe of a
 * hash instead of the subquery run again (see {@link dev.frostlake.executor.EqualityCorrelation}). It answers only a
 * probe whose every value is of the one kind the members of its column hold (see {@link CorrelationKeys}); any other
 * probe is left to the subquery. An inner row with a NULL key equals no outer row, and a probe with a NULL key is the
 * caller's to answer.
 */
public final class PreparedKeySet {

    private final Set<List<String>> keys = new HashSet<>();
    /** Each column's kind: null while no member is seen, "" once two kinds or a kind no hash settles are. */
    private final String[] kinds;

    private PreparedKeySet(final int width) {
        kinds = new String[width];
    }

    /**
     * @param rows  the inner rows' keys, one column for each equality
     * @param width how many keys a row holds
     * @return the keys
     */
    public static PreparedKeySet build(final List<Row> rows, final int width) {
        final PreparedKeySet set = new PreparedKeySet(width);
        for (final Row row : rows) {
            final List<Object> key = keyOf(row, width);
            if (key != null && set.note(key)) {
                set.keys.add(CorrelationKeys.canonical(key));
            }
        }
        return set;
    }

    /**
     * Whether the set settles a probe: each of its values of the kind its column's members hold.
     *
     * @param probe the outer row's keys, none NULL
     * @return whether {@link #contains} answers it
     */
    public boolean answers(final List<Object> probe) {
        return KeyColumnKinds.answers(kinds, probe);
    }

    /**
     * @param probe an answered probe
     * @return whether some inner row has these keys
     */
    public boolean contains(final List<Object> probe) {
        return keys.contains(CorrelationKeys.canonical(probe));
    }

    /** Notes a member's kinds, and whether it can be hashed at all. */
    private boolean note(final List<Object> key) {
        return KeyColumnKinds.note(kinds, key);
    }

    /** A row's first {@code width} values, or null when one is NULL. */
    static List<Object> keyOf(final Row row, final int width) {
        final List<Object> key = new ArrayList<>(width);
        for (int i = 0; i < width; i++) {
            final Object value = DeferredFault.read(row.getValue(i));
            if (value == null) {
                return null;
            }
            key.add(value);
        }
        return key;
    }
}
