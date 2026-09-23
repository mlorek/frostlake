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

import dev.frostlake.executor.StatementClock;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A schema's notebooks and Streamlit apps, keyed by kind and resolved name, and the ones dropped from it that
 * UNDROP can still restore (the most recently dropped first).
 */
public class AppObjectStore {

    private final Map<String, AppObject> live = new LinkedHashMap<>();
    private final List<AppObject> dropped = new ArrayList<>();

    private static String key(final AppObjectKind kind, final String name) {
        return kind.name() + "/" + name;
    }

    /** The live object of that kind and name, or null. */
    public synchronized AppObject get(final AppObjectKind kind, final String name) {
        return live.get(key(kind, name));
    }

    /** Adds an object, replacing any live one of the same kind and name. */
    public synchronized void put(final AppObject object) {
        live.put(key(object.getKind(), object.getName()), object);
    }

    /** Removes a live object without keeping it for UNDROP (a rename re-adds it). */
    public synchronized AppObject remove(final AppObjectKind kind, final String name) {
        return live.remove(key(kind, name));
    }

    /** Drops a live object, keeping it for UNDROP. */
    public synchronized void drop(final AppObject object) {
        live.remove(key(object.getKind(), object.getName()));
        object.setDroppedOn(LocalDateTime.ofInstant(StatementClock.instant(), ZoneId.systemDefault()));
        dropped.add(0, object);
    }

    /** The most recently dropped object of that kind and name, or null. */
    public synchronized AppObject lastDropped(final AppObjectKind kind, final String name) {
        for (final AppObject object : dropped) {
            if (object.getKind() == kind && object.getName().equals(name)) {
                return object;
            }
        }
        return null;
    }

    /** Restores a dropped object to the live set. */
    public synchronized void undrop(final AppObject object) {
        dropped.remove(object);
        object.setDroppedOn(null);
        live.put(key(object.getKind(), object.getName()), object);
    }

    /** The dropped objects UNDROP can still restore, the most recently dropped first. */
    public synchronized List<AppObject> droppedObjects() {
        return new ArrayList<>(dropped);
    }

    /** Puts back a dropped object a restored snapshot recorded, behind the more recent drops already back. */
    public synchronized void restoreDropped(final AppObject object) {
        dropped.add(object);
    }

    /** Every live object of a kind, in creation order. */
    public synchronized List<AppObject> all(final AppObjectKind kind) {
        final List<AppObject> out = new ArrayList<>();
        for (final AppObject object : live.values()) {
            if (object.getKind() == kind) {
                out.add(object);
            }
        }
        return out;
    }
}
