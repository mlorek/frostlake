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

package dev.frostlake.executor;

import dev.frostlake.executor.procedural.Cursor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Owns the named-cursor registry for {@link ProceduralExecutor}: declaration, lookup, and the
 * save/restore of cursor names around nested blocks. Cursor execution (open/fetch/close) stays in
 * ProceduralExecutor, which orchestrates the query executor and scope variables.
 */
public class CursorManager {

    private final Map<String, Cursor> cursors = new HashMap<>();

    /** Live cursor map — for raw (already-cased) lookups by name during execution. */
    public Map<String, Cursor> cursors() {
        return cursors;
    }

    public Cursor getCursor(final String name) {
        return cursors.get(name.toUpperCase());
    }

    public void registerCursor(final String name, final Cursor cursor) {
        cursors.put(name.toUpperCase(), cursor);
    }

    /** Save the current set of cursor names so they can be restored after a nested block. */
    public Set<String> saveCursorNames() {
        return new HashSet<>(cursors.keySet());
    }

    /** Remove any cursors that were not present before the block entered. */
    public void restoreCursors(final Set<String> savedNames) {
        Iterator<String> it = cursors.keySet().iterator();
        while (it.hasNext()) {
            if (!savedNames.contains(it.next())) {
                it.remove();
            }
        }
    }

    public void clear() {
        cursors.clear();
    }
}
