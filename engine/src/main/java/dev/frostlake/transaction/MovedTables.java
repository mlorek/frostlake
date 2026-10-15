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

package dev.frostlake.transaction;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tables whose storage moved to another key — a rename, a move, a schema's or a database's rename, a swap — by the
 * key each had and the key it has now. An open transaction's buffered changes follow the table they were made to,
 * whatever another session calls it meanwhile, as the account applies them to that table at COMMIT (live-verified).
 */
final class MovedTables {

    private MovedTables() {
    }

    /**
     * Moves each entry of a map keyed by a moved table to the table's new key, all at once, so two tables trading
     * keys trade their entries. A null value moves as any other.
     *
     * @param entries the map, changed in place
     * @param moved   the old key of each moved table, mapped to its new one
     * @param <V>     the entries' type
     */
    static <V> void rekey(final Map<String, V> entries, final Map<String, String> moved) {
        final Map<String, V> movedEntries = new LinkedHashMap<>();
        final Iterator<Map.Entry<String, V>> each = entries.entrySet().iterator();
        while (each.hasNext()) {
            final Map.Entry<String, V> entry = each.next();
            final String target = moved.get(entry.getKey());
            if (target != null) {
                movedEntries.put(target, entry.getValue());
                each.remove();
            }
        }
        entries.putAll(movedEntries);
    }

    /** The moves as a lock entry keys a table: upper-cased. */
    static Map<String, String> upperCased(final Map<String, String> moved) {
        final Map<String, String> upper = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : moved.entrySet()) {
            upper.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue().toUpperCase(Locale.ROOT));
        }
        return upper;
    }
}
