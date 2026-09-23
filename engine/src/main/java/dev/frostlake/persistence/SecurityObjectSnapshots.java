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
package dev.frostlake.persistence;

import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.SecurityObjectStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Saves and restores the network rules, network policies, password policies and secrets of one store. */
final class SecurityObjectSnapshots {

    private SecurityObjectSnapshots() {
    }

    /** Every object of a store, as snapshots. */
    static List<SecurityObjectSnapshot> save(final SecurityObjectStore store) {
        final List<SecurityObjectSnapshot> snapshots = new ArrayList<>();
        for (final SecurityObjectKind kind : SecurityObjectKind.values()) {
            for (final SecurityObject object : store.list(kind)) {
                final SecurityObjectSnapshot snapshot = new SecurityObjectSnapshot();
                snapshot.kind = kind.name();
                snapshot.name = object.getName();
                snapshot.owner = object.getOwner();
                snapshot.comment = object.getComment();
                snapshot.createdMillis = object.getCreatedTime().toEpochMilli();
                for (final Map.Entry<String, Object> property : object.getProperties().entrySet()) {
                    final Object value = property.getValue();
                    snapshot.properties.put(property.getKey(),
                        value instanceof List ? new ArrayList<String>(object.list(property.getKey())) : value);
                }
                snapshot.tags.putAll(object.getTagValues());
                snapshots.add(snapshot);
            }
        }
        return snapshots;
    }

    /** Adds the objects of snapshots, which may be absent from an older snapshot, to a store. */
    static void restore(final List<SecurityObjectSnapshot> snapshots, final SecurityObjectStore store) {
        if (snapshots == null) {
            return;
        }
        for (final SecurityObjectSnapshot snapshot : snapshots) {
            final SecurityObject object = new SecurityObject(SecurityObjectKind.valueOf(snapshot.kind), snapshot.name,
                Instant.ofEpochMilli(snapshot.createdMillis));
            if (snapshot.owner != null) {
                object.setOwner(snapshot.owner);
            }
            object.setComment(snapshot.comment);
            if (snapshot.properties != null) {
                for (final Map.Entry<String, Object> property : snapshot.properties.entrySet()) {
                    object.setProperty(property.getKey(), property.getValue());
                }
            }
            if (snapshot.tags != null) {
                for (final Map.Entry<String, String> tag : snapshot.tags.entrySet()) {
                    object.setTag(tag.getKey(), tag.getValue());
                }
            }
            store.add(object);
        }
    }

    /** Adds the attachments of a snapshot, which may be absent from an older snapshot, to a store. */
    static void restoreAttachments(final Map<String, String> attachments, final SecurityObjectStore store) {
        if (attachments == null) {
            return;
        }
        for (final Map.Entry<String, String> attachment : attachments.entrySet()) {
            store.attach(attachment.getKey(), attachment.getValue());
        }
    }
}
