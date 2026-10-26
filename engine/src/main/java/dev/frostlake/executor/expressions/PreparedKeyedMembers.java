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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The members a correlated IN's subquery answers for each combination of keys of its correlating equalities,
 * computed once over the inner relation (see {@link dev.frostlake.executor.EqualityCorrelation}), so each outer row
 * compares its value with the members of its own keys instead of running the subquery again. Like
 * {@link PreparedKeySet}, it answers only outer keys whose values are each of the one kind its column's keys hold (see
 * {@link CorrelationKeys}). The members are read as the index is built, in the order the query answers them: a fault
 * a member's cell holds raises then, whichever outer row's keys its row has, as the account computes the members over
 * every inner row the other conjuncts keep.
 */
public final class PreparedKeyedMembers {

    private final Map<List<String>, List<Object>> members = new HashMap<>();
    private final String[] kinds;
    private final ExpressionCollation collation;

    private PreparedKeyedMembers(final int width, final ExpressionCollation collation) {
        kinds = new String[width];
        this.collation = collation;
    }

    /**
     * @param rows      one row for each row the subquery keeps, the keys first and the member after them; a row with a
     *                  NULL key, which no outer key equals, is left out
     * @param width     how many keys a row holds
     * @param collation the collation the members' column carries
     * @return the members by key
     */
    public static PreparedKeyedMembers build(final List<Row> rows, final int width,
                                             final ExpressionCollation collation) {
        final PreparedKeyedMembers keyed = new PreparedKeyedMembers(width, collation);
        for (final Row row : rows) {
            final List<Object> key = PreparedKeySet.keyOf(row, width);
            if (key != null && KeyColumnKinds.note(keyed.kinds, key)) {
                final List<String> form = CorrelationKeys.canonical(key);
                List<Object> held = keyed.members.get(form);
                if (held == null) {
                    held = new ArrayList<>();
                    keyed.members.put(form, held);
                }
                held.add(DeferredFault.read(row.getValue(width)));
            }
        }
        return keyed;
    }

    /**
     * Whether the members settle an outer row's keys: each of their values of the kind its column's keys hold.
     *
     * @param keys the outer row's keys, none NULL
     * @return whether {@link #membersOf} answers it
     */
    public boolean answers(final List<Object> keys) {
        return KeyColumnKinds.answers(kinds, keys);
    }

    /**
     * @param keys answered keys
     * @return the members of the rows whose keys equal it, none where no row's do
     */
    public List<Object> membersOf(final List<Object> keys) {
        final List<Object> held = members.get(CorrelationKeys.canonical(keys));
        return held == null ? Collections.emptyList() : held;
    }

    /** @return the collation the members' column carries */
    public ExpressionCollation collation() {
        return collation;
    }
}
