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

import dev.frostlake.metastore.SqlObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A network rule, network policy, password policy or secret: a named object with no behaviour of its own, holding
 * the properties it was created or altered with. A property's value is a {@link String}, a {@link Long}, a
 * {@link Boolean} or a list of strings; a property that was never set is absent.
 */
public class SecurityObject extends SqlObject {

    private final SecurityObjectKind kind;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    /**
     * @param kind the object's kind
     * @param name the object's canonical name
     */
    public SecurityObject(final SecurityObjectKind kind, final String name) {
        super(name);
        this.kind = kind;
    }

    /**
     * @param kind the object's kind
     * @param name the object's canonical name
     * @param createdTime when the object was created
     */
    public SecurityObject(final SecurityObjectKind kind, final String name, final Instant createdTime) {
        super(name, createdTime);
        this.kind = kind;
    }

    /** The object's kind. */
    public SecurityObjectKind getKind() {
        return kind;
    }

    @Override
    public String getObjectType() {
        return kind.keywords();
    }

    /** A property's value, or null when it is not set. */
    public synchronized Object getProperty(final String property) {
        return properties.get(property);
    }

    /** Sets a property, or removes it when the value is null. */
    public synchronized void setProperty(final String property, final Object value) {
        if (value == null) {
            properties.remove(property);
        } else {
            properties.put(property, value);
        }
    }

    /** Every property that is set, in the order they were first set. */
    public synchronized Map<String, Object> getProperties() {
        return new LinkedHashMap<>(properties);
    }

    /** A property as text — a list joined with commas — or null when it is not set. */
    public synchronized String text(final String property) {
        final Object value = properties.get(property);
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return String.join(",", list(property));
        }
        return value.toString();
    }

    /** A list property's items, empty when it is not set. */
    public synchronized List<String> list(final String property) {
        final Object value = properties.get(property);
        if (!(value instanceof List)) {
            return Collections.<String>emptyList();
        }
        final List<String> items = new ArrayList<>();
        for (final Object item : (List<?>) value) {
            items.add(String.valueOf(item));
        }
        return items;
    }
}
