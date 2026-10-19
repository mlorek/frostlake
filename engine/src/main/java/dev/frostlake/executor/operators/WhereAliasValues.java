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

package dev.frostlake.executor.operators;

import dev.frostlake.storage.Row;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The select-list alias values a WHERE computed for the rows it kept, by row identity, so the projection shows the
 * value the predicate read instead of computing the item again. A volatile item — RANDOM(), a sequence's NEXTVAL,
 * SEQ4() — is then computed once for such a row, and that one value is both filtered on and shown, as it is over a
 * single relation on the account (live-verified, through ORDER BY, LIMIT, DISTINCT and an ungrouped HAVING alike).
 * Over a join, or under a QUALIFY, the account computes the item again for the projection, and no values are
 * carried there.
 */
public final class WhereAliasValues {

    private final Map<Row, Map<String, Object>> byRow = new IdentityHashMap<>();

    /** Drops every value kept, before the plan filters its rows again. */
    public void clear() {
        byRow.clear();
    }

    /**
     * Drops what was kept for a row the predicate did not keep.
     *
     * @param row the row
     */
    public void forget(final Row row) {
        byRow.remove(row);
    }

    /**
     * Keeps the alias values computed for a row the predicate kept.
     *
     * @param row    the kept row
     * @param values its computed aliases, by upper-cased alias name
     */
    public void keep(final Row row, final Map<String, Object> values) {
        byRow.put(row, values);
    }

    /**
     * The values kept for {@code row}, or null when none were.
     *
     * @param row the row
     * @return its computed aliases, by upper-cased alias name
     */
    public Map<String, Object> keptFor(final Row row) {
        return byRow.get(row);
    }

    /**
     * Whether a value was kept for {@code alias} on {@code row}.
     *
     * @param row   the row
     * @param alias the alias, upper-cased
     * @return true when the WHERE computed it for that row
     */
    public boolean holds(final Row row, final String alias) {
        final Map<String, Object> values = byRow.get(row);
        return values != null && values.containsKey(alias);
    }

    /**
     * The value kept for {@code alias} on {@code row}; read only after {@link #holds} said there is one.
     *
     * @param row   the row
     * @param alias the alias, upper-cased
     * @return the value the WHERE computed
     */
    public Object valueOf(final Row row, final String alias) {
        return byRow.get(row).get(alias);
    }
}
