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

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The parameters a container (a database or a schema) sets on itself, by upper-case name, each as the text
 * {@code SHOW PARAMETERS} reports. A parameter absent here is inherited: a schema's from its database, a
 * database's from the account default.
 */
public final class ObjectParameters {

    private final Map<String, String> values = new TreeMap<>();

    /** The value set on the object, or null when it inherits the parameter. */
    public synchronized String get(final String name) {
        return values.get(name.toUpperCase(Locale.ROOT));
    }

    /** Whether the object sets the parameter itself. */
    public synchronized boolean isSet(final String name) {
        return values.containsKey(name.toUpperCase(Locale.ROOT));
    }

    /** Sets the parameter on the object. */
    public synchronized void set(final String name, final String value) {
        values.put(name.toUpperCase(Locale.ROOT), value);
    }

    /** Removes the object's own setting, so the parameter is inherited again. */
    public synchronized void unset(final String name) {
        values.remove(name.toUpperCase(Locale.ROOT));
    }

    /** Every parameter the object sets itself, by upper-case name — a copy. */
    public synchronized Map<String, String> entries() {
        return new TreeMap<>(values);
    }

    /** Takes every setting of another object, as a clone does. */
    public synchronized void copyFrom(final ObjectParameters other) {
        final Map<String, String> copied;
        synchronized (other) {
            copied = new TreeMap<>(other.values);
        }
        values.clear();
        values.putAll(copied);
    }
}
