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

package dev.frostlake.metastore.model;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.NameKeys;
import dev.frostlake.metastore.NameTakenException;
import dev.frostlake.metastore.Named;
import dev.frostlake.metastore.SqlObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The objects of one kind a schema holds, by name: its streams, tasks, stages, tags and the rest, a store each. An
 * object is held under its name exactly as stored — an unquoted name upper-cased, a quoted one verbatim — so two
 * whose names differ only in case are two objects. A statement reaches one by that stored name exactly (the
 * {@code Exact} family); the folding family also answers a name in another case when exactly one stored name
 * matches it, for the engine's own Java callers (see {@link NameKeys#keyFor}). Another session's temporary object (a
 * {@link SessionScoped} one) is reached by neither family and listed by neither {@link #values}, while {@link #all}
 * holds it for the engine's own bookkeeping.
 *
 * @param <T> the kind of object
 */
public final class NamedObjects<T extends Named> {

    private final Map<String, T> byName = new ConcurrentHashMap<>();
    private final Schema home;
    private final String kind;

    /**
     * An empty store.
     *
     * @param home the schema the objects are in, which spells a missing one's name in full
     * @param kind the kind as a missing object's refusal names it: {@code Stream}, {@code Materialized view}
     */
    NamedObjects(final Schema home, final String kind) {
        this.home = home;
        this.kind = kind;
    }

    /**
     * Holds a new object; a name the kind already holds is refused: {@code Object 'S1' already exists.}
     *
     * @param object the object
     */
    public void add(final T object) {
        if (byName.putIfAbsent(object.getName(), object) != null) {
            throw taken(object.getName());
        }
    }

    /**
     * Holds an object, replacing any of its name, as CREATE OR REPLACE leaves it. Another session's temporary object
     * keeps its name: one session's object never replaces another's, and the name is refused as taken.
     *
     * @param object the object
     */
    public void put(final T object) {
        final T held = byName.get(object.getName());
        if (held != null && !reached(held)) {
            throw taken(object.getName());
        }
        byName.put(object.getName(), object);
    }

    /**
     * The object a name reaches, folding case as {@link NameKeys#keyFor} does.
     *
     * @param name the name as the caller spells it
     * @return the object, or null
     */
    public T find(final String name) {
        final T object = name == null ? null : byName.get(NameKeys.keyFor(byName, name));
        return object != null && reached(object) ? object : null;
    }

    /**
     * The object a name reaches, folding case; none is refused: {@code Stream 'DB.S.X' does not exist or not
     * authorized.}
     *
     * @param name the name as the caller spells it
     * @return the object
     */
    public T get(final String name) {
        final T object = find(name);
        if (object == null) {
            throw missing(name);
        }
        return object;
    }

    /**
     * Whether a name reaches an object, folding case.
     *
     * @param name the name as the caller spells it
     * @return whether it does
     */
    public boolean has(final String name) {
        return find(name) != null;
    }

    /**
     * The object of exactly this name, as a statement names one: a name that differs only in case is another
     * object's.
     *
     * @param name the canonical name
     * @return the object, or null
     */
    public T findExact(final String name) {
        final T object = name == null ? null : byName.get(name);
        return object != null && reached(object) ? object : null;
    }

    /**
     * The object of exactly this name; none is refused as {@link #get} refuses it.
     *
     * @param name the canonical name
     * @return the object
     */
    public T getExact(final String name) {
        final T object = findExact(name);
        if (object == null) {
            throw missing(name);
        }
        return object;
    }

    /**
     * Whether an object carries exactly this name.
     *
     * @param name the canonical name
     * @return whether one does
     */
    public boolean hasExact(final String name) {
        return findExact(name) != null;
    }

    /**
     * Removes the object a name reaches folding case, whichever session's it is.
     *
     * @param name the name as the caller spells it
     * @return the object removed, or null when the name reaches none
     */
    public T remove(final String name) {
        return name == null ? null : byName.remove(NameKeys.keyFor(byName, name));
    }

    /**
     * Drops the object a name reaches folding case, whichever session's it is; none is refused as {@link #get}
     * refuses it.
     *
     * @param name the name as the caller spells it
     * @return the object dropped
     */
    public T drop(final String name) {
        final T dropped = remove(name);
        if (dropped == null) {
            throw missing(name);
        }
        return dropped;
    }

    /**
     * Drops the object of exactly this name; none is refused as {@link #get} refuses it.
     *
     * @param name the canonical name
     * @return the object dropped
     */
    public T dropExact(final String name) {
        final T dropped = getExact(name);
        byName.remove(name, dropped);
        return dropped;
    }

    /**
     * Holds the object a name reaches folding case under a new name, kept as given; none is refused as {@link #get}
     * refuses it, and a new name another object of the kind holds is refused: {@code Object 'T2' already exists.}
     *
     * @param store   the store
     * @param oldName the name as the caller spells it
     * @param newName the new name, canonical
     * @param <S>     a kind a rename changes the name of
     */
    public static <S extends SqlObject> void rename(final NamedObjects<S> store, final String oldName,
                                                    final String newName) {
        final S object = store.get(oldName);
        final S holder = store.byName.get(newName);
        if (holder != null && holder != object) {
            throw new RuntimeException(SqlCompilationError.of(
                "Object '" + SqlIdentifiers.spellCanonical(newName) + "' already exists."));
        }
        store.byName.remove(NameKeys.keyFor(store.byName, oldName));
        object.rename(newName);
        store.byName.put(newName, object);
    }

    /**
     * The objects a statement here lists: every one but another session's temporary ones.
     *
     * @return the objects
     */
    public List<T> values() {
        final List<T> reached = new ArrayList<>();
        for (final T object : byName.values()) {
            if (reached(object)) {
                reached.add(object);
            }
        }
        return reached;
    }

    /**
     * Every object held, every session's temporary ones included: what the engine's own bookkeeping reads.
     *
     * @return the objects
     */
    public List<T> all() {
        return new ArrayList<>(byName.values());
    }

    /** Whether the current session reaches an object: any but another session's temporary one. */
    private boolean reached(final T object) {
        return !(object instanceof SessionScoped) || !SessionScoped.othersTemporary((SessionScoped) object);
    }

    private RuntimeException missing(final String name) {
        return new RuntimeException(SqlCompilationError.doesNotExist(kind, home.qualifiedName(name)));
    }

    private static NameTakenException taken(final String name) {
        return new NameTakenException(SqlCompilationError.of(
            "Object '" + SqlIdentifiers.spellCanonical(name) + "' already exists."));
    }
}
