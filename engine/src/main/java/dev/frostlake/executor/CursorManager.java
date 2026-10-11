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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Owns the named-cursor registry for {@link ProceduralExecutor}: declaration and lookup. Cursor execution
 * (open/fetch/close) stays in ProceduralExecutor, which orchestrates the query executor and scope variables.
 *
 * <p>A cursor's name belongs to the block that declares it (live-verified): the registry keeps one map for each
 * running block of the body that is running, the innermost block's first. A block nested in another may declare
 * the name again for itself — the outer cursor is hidden while the inner block runs and keeps its position — and
 * a body run from a block, a called procedure's or a dynamic text's, starts with none of its caller's cursors:
 * the caller's are set aside until it ends.
 */
public class CursorManager {

    private final Deque<Map<String, Cursor>> scopes = new ArrayDeque<>();

    /** The cursor a name stands for where the running block is: its own, else the nearest enclosing block's. */
    public Cursor getCursor(final String name) {
        final String canonical = name.toUpperCase(Locale.ROOT);
        for (final Map<String, Cursor> scope : scopes) {
            final Cursor cursor = scope.get(canonical);
            if (cursor != null) {
                return cursor;
            }
        }
        return null;
    }

    /** Declares a cursor in the running block, in place of one this block declared under the name before. */
    public void registerCursor(final String name, final Cursor cursor) {
        if (scopes.isEmpty()) {
            scopes.push(new HashMap<String, Cursor>());
        }
        scopes.peek().put(name.toUpperCase(Locale.ROOT), cursor);
    }

    /** A block starts: the cursors it declares are its own. */
    public void enterBlock() {
        scopes.push(new HashMap<String, Cursor>());
    }

    /** The innermost block ends, and its cursors with it. */
    public void exitBlock() {
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
    }

    /**
     * Sets every cursor aside for a body that runs as a frame of its own, which sees none of them.
     *
     * @return the cursors set aside, to be handed back to {@link #restore}
     */
    public List<Map<String, Cursor>> setAside() {
        final List<Map<String, Cursor>> caller = new ArrayList<>(scopes);
        scopes.clear();
        return caller;
    }

    /**
     * Puts back the cursors {@link #setAside()} set aside.
     *
     * @param caller the value that call returned
     */
    public void restore(final List<Map<String, Cursor>> caller) {
        scopes.clear();
        for (final Map<String, Cursor> scope : caller) {
            scopes.addLast(scope);
        }
    }

    /** The names of the cursors in sight of the running block. */
    public Set<String> saveCursorNames() {
        final Set<String> names = new HashSet<>();
        for (final Map<String, Cursor> scope : scopes) {
            names.addAll(scope.keySet());
        }
        return names;
    }

    /** Removes from the running block the cursors it declared since {@code savedNames} was taken. */
    public void restoreCursors(final Set<String> savedNames) {
        if (scopes.isEmpty()) {
            return;
        }
        final Iterator<String> it = scopes.peek().keySet().iterator();
        while (it.hasNext()) {
            if (!savedNames.contains(it.next())) {
                it.remove();
            }
        }
    }

    public void clear() {
        scopes.clear();
    }
}
