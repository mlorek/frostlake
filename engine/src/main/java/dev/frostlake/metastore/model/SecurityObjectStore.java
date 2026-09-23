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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The network rules, network policies, password policies and secrets of one container — a schema, or the account
 * — keyed by kind and exact canonical name, and, in the account's store, which password and network policies are
 * attached to the account and to users.
 */
public final class SecurityObjectStore {

    private final Map<SecurityObjectKind, Map<String, SecurityObject>> objects = new EnumMap<>(SecurityObjectKind.class);
    private final Map<String, String> attachments = new ConcurrentHashMap<>();

    /** An empty store. */
    public SecurityObjectStore() {
        for (final SecurityObjectKind kind : SecurityObjectKind.values()) {
            objects.put(kind, new ConcurrentHashMap<String, SecurityObject>());
        }
    }

    /** The object of this kind and exact name, or null. */
    public SecurityObject get(final SecurityObjectKind kind, final String name) {
        return name == null ? null : objects.get(kind).get(name);
    }

    /** Adds an object, replacing any of the same kind and name. */
    public void add(final SecurityObject object) {
        objects.get(object.getKind()).put(object.getName(), object);
    }

    /** Removes the object of this kind and exact name, answering it, or null when there was none. */
    public SecurityObject remove(final SecurityObjectKind kind, final String name) {
        return objects.get(kind).remove(name);
    }

    /** Every object of a kind. */
    public List<SecurityObject> list(final SecurityObjectKind kind) {
        return new ArrayList<>(objects.get(kind).values());
    }

    /** What is attached under a key, e.g. the password policy of a user, or null. */
    public String attachment(final String key) {
        return attachments.get(key);
    }

    /** Records an attachment. */
    public void attach(final String key, final String value) {
        attachments.put(key, value);
    }

    /** Removes an attachment. */
    public void detach(final String key) {
        attachments.remove(key);
    }

    /** Every attachment. */
    public Map<String, String> attachments() {
        return new HashMap<>(attachments);
    }
}
